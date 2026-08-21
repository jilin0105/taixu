package top.wkbin.taixu.harness.mcp

import top.wkbin.taixu.core.datastore.SettingsDataStore
import top.wkbin.taixu.core.model.McpServerConfig
import top.wkbin.taixu.core.model.McpToolInfo
import top.wkbin.taixu.core.model.McpTransportType
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.shell.ShellCommand
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * MCP (Model Context Protocol) 核心管理器：
 * 负责管理 MCP 插件配置、协议握手与动态工具注册/执行。
 */
@Singleton
class McpManager @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val linuxRuntime: LinuxRuntime,
    private val httpClient: OkHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val toolsCache = ConcurrentHashMap<String, List<McpToolInfo>>()

    /** 获取当前所有已启用的 MCP 服务及其注册工具 */
    suspend fun getActiveMcpTools(): List<McpToolInfo> = withContext(Dispatchers.IO) {
        val servers = settingsDataStore.mcpServers.first().filter { it.isEnabled }
        val allTools = mutableListOf<McpToolInfo>()

        for (server in servers) {
            val cached = toolsCache[server.id]
            if (cached != null) {
                allTools.addAll(cached)
            } else {
                val discovered = discoverTools(server)
                toolsCache[server.id] = discovered
                allTools.addAll(discovered)
            }
        }
        allTools
    }

    /** 探测或拉取 MCP 服务的可用工具 */
    suspend fun discoverTools(server: McpServerConfig): List<McpToolInfo> = withContext(Dispatchers.IO) {
        try {
            when (server.transportType) {
                McpTransportType.STDIO -> discoverStdioTools(server)
                McpTransportType.SSE -> discoverSseTools(server)
            }
        } catch (e: Exception) {
            getPresetToolsForServer(server)
        }
    }

    /** 测试 MCP 服务连通性与工具探测 */
    suspend fun testServer(server: McpServerConfig): Result<List<McpToolInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val tools = when (server.transportType) {
                McpTransportType.STDIO -> discoverStdioTools(server)
                McpTransportType.SSE -> discoverSseTools(server)
            }
            toolsCache[server.id] = tools
            tools
        }
    }

    /** 执行 MCP 工具调用 */
    suspend fun executeTool(fullToolName: String, arguments: JsonObject): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        // 工具格式：mcp__<serverId>__<toolName>
        val parts = fullToolName.split("__")
        if (parts.size < 3 || parts[0] != "mcp") {
            return@withContext false to "无效的 MCP 工具名称：$fullToolName"
        }
        val serverId = parts[1]
        val targetToolName = parts.subList(2, parts.size).joinToString("__")

        val server = settingsDataStore.mcpServers.first().firstOrNull { it.id == serverId }
            ?: return@withContext false to "未找到 MCP 服务：$serverId"

        try {
            when (server.transportType) {
                McpTransportType.STDIO -> executeStdioTool(server, targetToolName, arguments)
                McpTransportType.SSE -> executeSseTool(server, targetToolName, arguments)
            }
        } catch (e: Exception) {
            false to "MCP 工具执行异常：${e.message ?: e::class.simpleName}"
        }
    }

    private suspend fun discoverStdioTools(server: McpServerConfig): List<McpToolInfo> {
        val rpcInit = json.encodeToString(
            JsonRpcRequest(
                id = "init-1",
                method = "initialize",
                params = json.encodeToJsonElement(McpInitializeParams()),
            )
        )
        val rpcList = json.encodeToString(
            JsonRpcRequest(
                id = "list-1",
                method = "tools/list",
                params = JsonObject(emptyMap()),
            )
        )
        val fullInput = "$rpcInit\n$rpcList\n"
        val cmdStr = "${server.command} ${server.args.joinToString(" ")}"

        val result = linuxRuntime.execute(
            ShellCommand(
                commandLine = "printf '%s' '$fullInput' | $cmdStr",
                workingDirectory = "/root",
                timeoutMs = 15000,
            )
        )

        val output = result.stdout
        val parsed = parseToolsFromStdioOutput(server, output)
        return if (parsed.isNotEmpty()) parsed else getPresetToolsForServer(server)
    }

    private fun parseToolsFromStdioOutput(server: McpServerConfig, output: String): List<McpToolInfo> {
        val tools = mutableListOf<McpToolInfo>()
        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("{") && trimmed.contains("\"result\"")) {
                runCatching {
                    val resp = json.decodeFromString<JsonRpcResponse>(trimmed)
                    val toolsList = resp.result?.let { json.decodeFromJsonElement<McpToolsListResponse>(it) }
                    toolsList?.tools?.forEach { toolDto ->
                        tools.add(
                            McpToolInfo(
                                serverId = server.id,
                                serverName = server.name,
                                name = toolDto.name,
                                description = toolDto.description,
                                parametersJson = json.encodeToString(toolDto.inputSchema),
                            )
                        )
                    }
                }
            }
        }
        return tools
    }

    private suspend fun executeStdioTool(server: McpServerConfig, toolName: String, args: JsonObject): Pair<Boolean, String> {
        val rpcCall = json.encodeToString(
            JsonRpcRequest(
                id = UUID.randomUUID().toString(),
                method = "tools/call",
                params = json.encodeToJsonElement(McpCallToolParams(name = toolName, arguments = args)),
            )
        )
        val cmdStr = "${server.command} ${server.args.joinToString(" ")}"

        val result = linuxRuntime.execute(
            ShellCommand(
                commandLine = "printf '%s\\n' '$rpcCall' | $cmdStr",
                workingDirectory = "/root",
                timeoutMs = 30000,
            )
        )

        if (!result.isSuccess) {
            return false to "MCP 进程异常退出 (exit ${result.exitCode})：${result.stderr.ifBlank { result.stdout }}"
        }

        val output = result.stdout
        val callResult = parseCallResult(output)
        return callResult ?: (true to output.ifBlank { "工具执行完成（无输出）" })
    }

    private fun parseCallResult(output: String): Pair<Boolean, String>? {
        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("{") && trimmed.contains("\"result\"")) {
                val parsed = runCatching {
                    val resp = json.decodeFromString<JsonRpcResponse>(trimmed)
                    val res = resp.result?.let { json.decodeFromJsonElement<McpCallToolResult>(it) }
                    res
                }.getOrNull()

                if (parsed != null) {
                    val textContent = parsed.content.joinToString("\n") { it.text.orEmpty() }
                    return !parsed.isError to textContent.ifBlank { "执行成功" }
                }
            }
        }
        return null
    }

    private suspend fun discoverSseTools(server: McpServerConfig): List<McpToolInfo> {
        val request = Request.Builder()
            .url(server.serverUrl.trimEnd('/') + "/tools")
            .get()
            .build()
        val response = httpClient.newCall(request).execute()
        val body = response.body.string()
        val list = json.decodeFromString<McpToolsListResponse>(body)
        return list.tools.map {
            McpToolInfo(
                serverId = server.id,
                serverName = server.name,
                name = it.name,
                description = it.description,
                parametersJson = json.encodeToString(it.inputSchema),
            )
        }
    }

    private suspend fun executeSseTool(server: McpServerConfig, toolName: String, args: JsonObject): Pair<Boolean, String> {
        val payload = json.encodeToString(
            JsonRpcRequest(
                id = UUID.randomUUID().toString(),
                method = "tools/call",
                params = json.encodeToJsonElement(McpCallToolParams(name = toolName, arguments = args)),
            )
        )
        val request = Request.Builder()
            .url(server.serverUrl.trimEnd('/') + "/call")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val response = httpClient.newCall(request).execute()
        val body = response.body.string()
        val rpcResp = json.decodeFromString<JsonRpcResponse>(body)
        val callRes = rpcResp.result?.let { json.decodeFromJsonElement<McpCallToolResult>(it) }
        val text = callRes?.content?.joinToString("\n") { it.text.orEmpty() } ?: body
        return !(callRes?.isError ?: false) to text
    }

    private fun getPresetToolsForServer(server: McpServerConfig): List<McpToolInfo> = when (server.id) {
        "mcp_sqlite" -> listOf(
            McpToolInfo(
                serverId = server.id,
                serverName = server.name,
                name = "read_query",
                description = "在 SQLite 数据库中执行 SELECT 检索语句并返回结构化数据",
                parametersJson = """{"type":"object","properties":{"query":{"type":"string","description":"SQL SELECT 查询语句"}},"required":["query"]}""",
            ),
            McpToolInfo(
                serverId = server.id,
                serverName = server.name,
                name = "write_query",
                description = "在 SQLite 数据库中执行 INSERT/UPDATE/DELETE/CREATE 修改语句",
                parametersJson = """{"type":"object","properties":{"query":{"type":"string","description":"SQL 写入或结构修改语句"}},"required":["query"]}""",
            ),
            McpToolInfo(
                serverId = server.id,
                serverName = server.name,
                name = "describe_table",
                description = "获取指定 SQLite 数据表的 Schema 字段定义",
                parametersJson = """{"type":"object","properties":{"table_name":{"type":"string","description":"数据表名称"}},"required":["table_name"]}""",
            ),
        )
        "mcp_fetch" -> listOf(
            McpToolInfo(
                serverId = server.id,
                serverName = server.name,
                name = "fetch_markdown",
                description = "抓取指定 HTTP/HTTPS 网页并转换为结构化 Markdown 文本",
                parametersJson = """{"type":"object","properties":{"url":{"type":"string","description":"目标网页 URL"}},"required":["url"]}""",
            ),
        )
        "mcp_git" -> listOf(
            McpToolInfo(
                serverId = server.id,
                serverName = server.name,
                name = "git_status",
                description = "获取当前 Git 工作区的分支状态与未提交修改",
                parametersJson = """{"type":"object","properties":{}}""",
            ),
            McpToolInfo(
                serverId = server.id,
                serverName = server.name,
                name = "git_log",
                description = "获取 Git 提交历史记录与 Commit 摘要",
                parametersJson = """{"type":"object","properties":{"limit":{"type":"integer","description":"展示的提交条数"}}}""",
            ),
            McpToolInfo(
                serverId = server.id,
                serverName = server.name,
                name = "git_diff",
                description = "获取当前工作区或指定分支的代码 Diff 差异",
                parametersJson = """{"type":"object","properties":{"target":{"type":"string","description":"比较目标（如 HEAD 或分支名）"}}}""",
            ),
        )
        else -> emptyList()
    }
}
