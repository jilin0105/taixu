package top.wkbin.taixu.harness.mcp

import top.wkbin.taixu.core.database.McpServerRepository
import top.wkbin.taixu.core.model.McpConnectionState
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * MCP (Model Context Protocol) 核心管理器：
 * 负责管理 MCP 插件配置、协议握手与动态工具注册/执行。
 */
@Singleton
class McpManager @Inject constructor(
    private val mcpServerRepository: McpServerRepository,
    private val linuxRuntime: LinuxRuntime,
    private val httpClient: OkHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val toolsCache = ConcurrentHashMap<String, List<McpToolInfo>>()

    /** 各 MCP 服务的实时连通性状态（按 server.id），供 UI 展示绿点/红点等。 */
    private val _connectionStates = MutableStateFlow<Map<String, McpConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, McpConnectionState>> = _connectionStates.asStateFlow()

    /**
     * 轻量连通性检测：仅执行一次初始化握手（不拉取工具列表），判断服务是否在线。
     * 成功返回 true，任何异常（进程启动失败 / HTTP 不可达 / 超时）返回 false。
     */
    suspend fun checkConnection(server: McpServerConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            when (server.transportType) {
                McpTransportType.STDIO -> checkStdio(server)
                McpTransportType.SSE -> checkSse(server)
            }
        } catch (e: Exception) {
            false
        }
    }

    /** 并发热探测当前所有已启用 MCP 服务的连通性，并更新 [connectionStates]。 */
    suspend fun refreshConnections() = withContext(Dispatchers.IO) {
        val servers = mcpServerRepository.servers.first()
        // 先统一进入检测中/未知态，避免旧结果残留造成闪烁。
        _connectionStates.value = servers.associate { server ->
            server.id to if (server.isEnabled) McpConnectionState.CHECKING else McpConnectionState.UNKNOWN
        }
        coroutineScope {
            servers.asSequence().filter { it.isEnabled }.map { server ->
                launch {
                    val online = checkConnection(server)
                    _connectionStates.update {
                        it + (server.id to if (online) McpConnectionState.ONLINE else McpConnectionState.OFFLINE)
                    }
                }
            }.toList().joinAll()
        }
    }

    /** 获取当前所有已启用的 MCP 服务及其注册工具 */
    suspend fun getActiveMcpTools(): List<McpToolInfo> = withContext(Dispatchers.IO) {
        val servers = mcpServerRepository.servers.first().filter { it.isEnabled }
        val allTools = mutableListOf<McpToolInfo>()

        for (server in servers) {
            val cached = toolsCache[server.id]
            if (cached != null) {
                allTools.addAll(cached)
            } else {
                try {
                    val discovered = discoverTools(server)
                    toolsCache[server.id] = discovered
                    allTools.addAll(discovered)
                    _connectionStates.update { it + (server.id to McpConnectionState.ONLINE) }
                } catch (_: Exception) {
                    toolsCache.remove(server.id)
                    _connectionStates.update { it + (server.id to McpConnectionState.OFFLINE) }
                }
            }
        }
        allTools
    }

    /** 探测或拉取 MCP 服务的可用工具 */
    suspend fun discoverTools(server: McpServerConfig): List<McpToolInfo> = withContext(Dispatchers.IO) {
        when (server.transportType) {
            McpTransportType.STDIO -> discoverStdioTools(server)
            McpTransportType.SSE -> discoverSseTools(server)
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
        }.onSuccess {
            _connectionStates.update { it + (server.id to McpConnectionState.ONLINE) }
        }.onFailure {
            toolsCache.remove(server.id)
            _connectionStates.update { it + (server.id to McpConnectionState.OFFLINE) }
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

        val server = mcpServerRepository.servers.first().firstOrNull { it.id == serverId && it.isEnabled }
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

    /** STDIO 连通性：向沙箱进程发送 initialize 握手，进程正常退出且 stdout 含 JSON-RPC result 即视为连通。 */
    private suspend fun checkStdio(server: McpServerConfig): Boolean {
        val rpcInit = initializeStdioRequest("ping-${UUID.randomUUID()}")
        val initialized = json.encodeToString(JsonRpcNotification(method = "notifications/initialized"))
        val cmdStr = stdioCommand(server)
        val result = linuxRuntime.execute(
            ShellCommand(
                commandLine = "printf '%s\\n%s\\n' ${shellQuote(rpcInit)} ${shellQuote(initialized)} | $cmdStr",
                workingDirectory = "/root",
                timeoutMs = 15000,
                environment = server.env,
            )
        )
        return result.isSuccess && hasJsonRpcResult(result.stdout)
    }

    /** HTTP 连通性：完成标准 MCP initialize 握手。 */
    private suspend fun checkSse(server: McpServerConfig): Boolean {
        initializeHttpSession(server)
        return true
    }

    /** 判断输出中是否存在一条可解码且含 ```result``` 的 JSON-RPC 响应。 */
    private fun hasJsonRpcResult(output: String): Boolean =
        output.lineSequence().any { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("{")) return@any false
            runCatching {
                json.decodeFromString<JsonRpcResponse>(trimmed).result != null
            }.getOrDefault(false)
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
        val initialized = json.encodeToString(JsonRpcNotification(method = "notifications/initialized"))
        val fullInput = "$rpcInit\n$initialized\n$rpcList\n"
        val cmdStr = stdioCommand(server)

        val result = linuxRuntime.execute(
            ShellCommand(
                commandLine = "printf '%s' ${shellQuote(fullInput)} | $cmdStr",
                workingDirectory = "/root",
                timeoutMs = 25000,
                environment = server.env,
            )
        )

        check(result.isSuccess) {
            "MCP STDIO 进程启动失败 (exit ${result.exitCode})：${result.stderr.ifBlank { result.stdout }.ifBlank { "无输出" }}"
        }
        val output = result.stdout
        val parsed = parseToolsFromStdioOutput(server, output)
        check(parsed.isNotEmpty()) { "MCP STDIO 未返回任何工具定义" }
        return parsed
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
        val rpcInit = initializeStdioRequest("init-${UUID.randomUUID()}")
        val initialized = json.encodeToString(JsonRpcNotification(method = "notifications/initialized"))
        val rpcCall = json.encodeToString(
            JsonRpcRequest(
                id = UUID.randomUUID().toString(),
                method = "tools/call",
                params = json.encodeToJsonElement(McpCallToolParams(name = toolName, arguments = args)),
            )
        )
        val cmdStr = stdioCommand(server)

        val result = linuxRuntime.execute(
            ShellCommand(
                commandLine = "printf '%s\\n%s\\n%s\\n' ${shellQuote(rpcInit)} ${shellQuote(initialized)} ${shellQuote(rpcCall)} | $cmdStr",
                workingDirectory = "/root",
                timeoutMs = 120000,
                environment = server.env,
            )
        )

        if (!result.isSuccess) {
            return false to "MCP 进程异常退出 (exit ${result.exitCode})：${result.stderr.ifBlank { result.stdout }}"
        }

        val output = result.stdout
        val callResult = parseCallResult(output)
        return callResult ?: (true to output.ifBlank { "工具执行完成（无输出）" })
    }

    private fun initializeStdioRequest(requestId: String): String = json.encodeToString(
        JsonRpcRequest(
            id = requestId,
            method = "initialize",
            params = json.encodeToJsonElement(McpInitializeParams()),
        )
    )

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
        val session = initializeHttpSession(server)
        val response = sendHttpRequest(
            session = session,
            method = "tools/list",
            params = JsonObject(emptyMap()),
        )
        val list = response.result?.let { json.decodeFromJsonElement<McpToolsListResponse>(it) }
            ?: error("MCP tools/list 未返回 result")
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
        val session = initializeHttpSession(server)
        val response = sendHttpRequest(
            session = session,
            method = "tools/call",
            params = json.encodeToJsonElement(McpCallToolParams(name = toolName, arguments = args)),
        )
        val callRes = response.result?.let { json.decodeFromJsonElement<McpCallToolResult>(it) }
            ?: error("MCP tools/call 未返回 result")
        val text = callRes.content.joinToString("\n") { it.text.orEmpty() }
        return !callRes.isError to text.ifBlank { if (callRes.isError) "执行失败" else "执行成功" }
    }

    private fun initializeHttpSession(server: McpServerConfig): McpHttpSession {
        val endpoint = validatedMcpHttpEndpoint(server.serverUrl)
        val requestId = "init-${UUID.randomUUID()}"
        val payload = json.encodeToString(
            JsonRpcRequest(
                id = requestId,
                method = "initialize",
                params = json.encodeToJsonElement(McpInitializeParams()),
            )
        )
        val exchange = executeHttpRpc(
            endpoint = endpoint,
            payload = payload,
            requestId = requestId,
            protocolVersion = MCP_PROTOCOL_VERSION,
            sessionId = null,
        )
        val initializeResult = exchange.response.result
            ?.let { json.decodeFromJsonElement<McpInitializeResult>(it) }
            ?: error("MCP initialize 未返回 result")
        require(initializeResult.protocolVersion.isNotBlank()) { "MCP initialize 未返回协议版本" }

        val session = McpHttpSession(
            endpoint = endpoint,
            sessionId = exchange.sessionId,
            protocolVersion = initializeResult.protocolVersion,
        )
        sendHttpNotification(session, JsonRpcNotification(method = "notifications/initialized"))
        return session
    }

    private fun sendHttpRequest(
        session: McpHttpSession,
        method: String,
        params: kotlinx.serialization.json.JsonElement,
    ): JsonRpcResponse {
        val requestId = UUID.randomUUID().toString()
        val payload = json.encodeToString(
            JsonRpcRequest(
                id = requestId,
                method = method,
                params = params,
            )
        )
        return executeHttpRpc(
            endpoint = session.endpoint,
            payload = payload,
            requestId = requestId,
            protocolVersion = session.protocolVersion,
            sessionId = session.sessionId,
        ).response
    }

    private fun executeHttpRpc(
        endpoint: HttpUrl,
        payload: String,
        requestId: String,
        protocolVersion: String,
        sessionId: String?,
    ): McpHttpExchange {
        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .header("Accept", MCP_HTTP_ACCEPT)
            .header("MCP-Protocol-Version", protocolVersion)
            .apply { sessionId?.let { header("Mcp-Session-Id", it) } }
            .build()

        return httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "MCP HTTP ${response.code}" }
            val rpcResponse = readHttpRpcResponse(response, requestId)
            rpcResponse.error?.let { error ->
                throw IllegalStateException("MCP JSON-RPC ${error.code}: ${error.message}")
            }
            McpHttpExchange(
                response = rpcResponse,
                sessionId = response.header("Mcp-Session-Id") ?: sessionId,
            )
        }
    }

    private fun sendHttpNotification(session: McpHttpSession, notification: JsonRpcNotification) {
        val payload = json.encodeToString(notification)
        val request = Request.Builder()
            .url(session.endpoint)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .header("Accept", MCP_HTTP_ACCEPT)
            .header("MCP-Protocol-Version", session.protocolVersion)
            .apply { session.sessionId?.let { header("Mcp-Session-Id", it) } }
            .build()
        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "MCP HTTP ${response.code}" }
        }
    }

    private fun readHttpRpcResponse(response: Response, requestId: String): JsonRpcResponse {
        val contentType = response.header("Content-Type").orEmpty().lowercase()
        return if (contentType.startsWith("text/event-stream")) {
            readSseRpcResponse(response, requestId)
        } else {
            val body = readBodyLimited(response)
            require(body.isNotBlank()) { "MCP HTTP 响应为空" }
            json.decodeFromString(body)
        }
    }

    private fun readSseRpcResponse(response: Response, requestId: String): JsonRpcResponse {
        val source = response.body.source()
        val dataLines = mutableListOf<String>()
        var consumedBytes = 0

        while (true) {
            val line = source.readUtf8Line() ?: break
            consumedBytes += line.toByteArray(Charsets.UTF_8).size + 1
            require(consumedBytes <= MAX_HTTP_RESPONSE_BYTES) { "MCP 响应超过大小限制" }

            if (line.isBlank()) {
                decodeSseData(dataLines, requestId)?.let { return it }
                dataLines.clear()
            } else if (line.startsWith("data:")) {
                dataLines += line.removePrefix("data:").trimStart()
            }
        }

        return decodeSseData(dataLines, requestId)
            ?: error("MCP SSE 未返回请求 $requestId 的 JSON-RPC 响应")
    }

    private fun decodeSseData(dataLines: List<String>, requestId: String): JsonRpcResponse? {
        if (dataLines.isEmpty()) return null
        return runCatching { json.decodeFromString<JsonRpcResponse>(dataLines.joinToString("\n")) }
            .getOrNull()
            ?.takeIf { it.id == requestId }
    }

    private fun stdioCommand(server: McpServerConfig): String {
        require(server.command.isNotBlank()) { "MCP 启动命令不能为空" }
        require('\u0000' !in server.command && '\n' !in server.command && '\r' !in server.command) {
            "MCP 启动命令包含非法字符"
        }
        return (listOf(server.command) + server.args).joinToString(" ", transform = ::shellQuote)
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\\"'\\\"'")}'"

    private fun readBodyLimited(response: okhttp3.Response): String {
        val body = response.body
        val advertised = body.contentLength()
        require(advertised < 0 || advertised <= MAX_HTTP_RESPONSE_BYTES) { "MCP 响应超过大小限制" }
        val bytes = body.byteStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                require(output.size() + read <= MAX_HTTP_RESPONSE_BYTES) { "MCP 响应超过大小限制" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private companion object {
        const val MCP_HTTP_ACCEPT = "application/json, text/event-stream"
        const val MAX_HTTP_RESPONSE_BYTES = 4 * 1024 * 1024
    }

    private data class McpHttpSession(
        val endpoint: HttpUrl,
        val sessionId: String?,
        val protocolVersion: String,
    )

    private data class McpHttpExchange(
        val response: JsonRpcResponse,
        val sessionId: String?,
    )
}

internal fun isAllowedCleartextMcpHost(host: String): Boolean {
    if (host == "localhost" || host == "127.0.0.1" || host == "::1") return true

    val octets = host.split('.')
    if (octets.size != 4 || octets[0] != "192" || octets[1] != "168") return false
    return octets.all { octet -> octet.toIntOrNull()?.let { it in 0..255 } == true }
}

internal fun validatedMcpHttpEndpoint(baseUrl: String): HttpUrl {
    val url = baseUrl.trim().toHttpUrl()
    require(url.isHttps || isAllowedCleartextMcpHost(url.host)) {
        "明文 MCP 仅允许 localhost、回环地址或 192.168.* 局域网地址"
    }
    return url
}
