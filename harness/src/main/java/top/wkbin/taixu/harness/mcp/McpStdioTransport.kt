package top.wkbin.taixu.harness.mcp

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import top.wkbin.taixu.core.model.McpServerConfig
import top.wkbin.taixu.core.model.McpToolInfo
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.shell.LinuxSession
import top.wkbin.taixu.runtime.shell.SessionConfig

/** Reusable newline-framed JSON-RPC sessions for stateful STDIO MCP servers. */
@Singleton
class McpStdioTransport @Inject constructor(
    private val linuxRuntime: LinuxRuntime,
    private val json: Json,
) : McpTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connections = ConcurrentHashMap<String, Connection>()

    init {
        // 空闲回收：常驻 STDIO 进程（尤其 node/python 系）长期不释放会累积内存与 ptrace 负担。
        // 每分钟清扫一次，超过 IDLE_TIMEOUT 未活动的连接被关闭，下次调用按需重建。
        scope.launch {
            while (isActive) {
                delay(SWEEP_INTERVAL_MS)
                runCatching { sweepIdleOnce(System.currentTimeMillis()) }
            }
        }
    }

    override suspend fun check(server: McpServerConfig): Boolean = runCatching {
        connection(server).withInitialized { true }
    }.getOrDefault(false)

    override suspend fun discover(server: McpServerConfig): List<McpToolInfo> = connection(server).withInitialized {
        val response = request("tools/list", JsonObject(emptyMap()))
        val result = response.result?.let { json.decodeFromJsonElement(McpToolsListResponse.serializer(), it) }
            ?: error("MCP tools/list did not return a result")
        result.tools.map { dto -> dto.toInfo(server, json.encodeToString(JsonObject.serializer(), dto.inputSchema)) }
    }

    override suspend fun execute(server: McpServerConfig, toolName: String, arguments: JsonObject): Pair<Boolean, String> =
        connection(server).withInitialized {
            val params = json.encodeToJsonElement(McpCallToolParams.serializer(), McpCallToolParams(toolName, arguments))
            val response = request("tools/call", params)
            val result = response.result?.let { json.decodeFromJsonElement(McpCallToolResult.serializer(), it) }
                ?: error("MCP tools/call did not return a result")
            !result.isError to result.content.joinToString("\n") { it.text.orEmpty() }
                .ifBlank { if (result.isError) "执行失败" else "执行成功" }
        }

    /**
     * 关闭所有空闲超时的连接；返回关闭数量。可见性放宽给单元测试直接驱动。
     * 正在执行请求的连接因每次请求都会刷新 lastActivityMs，不会被误回收
     * （单请求超时上限 120s，远小于空闲阈值）。
     */
    internal suspend fun sweepIdleOnce(nowMs: Long): Int {
        var closed = 0
        val entries = connections.entries.toList()
        for ((id, connection) in entries) {
            if (nowMs - connection.lastActivityMs >= IDLE_TIMEOUT_MS) {
                if (connections.remove(id, connection)) {
                    connection.close()
                    closed++
                }
            }
        }
        return closed
    }

    /** 测试钩子：把所有连接的空闲时间拨回到指定时刻。 */
    internal fun rewindIdleForTest(ageMs: Long) {
        val target = System.currentTimeMillis() - ageMs
        connections.values.forEach { it.lastActivityMs = target }
    }

    private suspend fun connection(server: McpServerConfig): Connection {
        connections[server.id]?.takeIf { it.session.isAlive && it.fingerprint == fingerprint(server) }?.let {
            it.markActive()
            return it
        }
        connections.remove(server.id)?.close()
        val session = linuxRuntime.startSession(
            SessionConfig(
                workingDirectory = "/root",
                environment = server.env,
                commandLine = commandLine(server),
                allowSttyResize = false,
            ),
        )
        return Connection(session, fingerprint(server)).also { connection ->
            connections[server.id] = connection
            connection.startReader()
        }
    }

    private inner class Connection(val session: LinuxSession, val fingerprint: String) {
        private val mutex = Mutex()
        private val lines = Channel<String>(Channel.UNLIMITED)
        private var initialized = false
        private var readerJob: Job? = null

        /** 最近一次请求时间；internal 供测试直接构造"已空闲"状态。 */
        @Volatile
        internal var lastActivityMs: Long = System.currentTimeMillis()

        fun markActive() {
            lastActivityMs = System.currentTimeMillis()
        }

        fun startReader() {
            readerJob = scope.launch {
                val buffer = StringBuilder()
                session.output.collect { output ->
                    buffer.append(output.text)
                    while (true) {
                        val newline = buffer.indexOf("\n")
                        if (newline < 0) break
                        val line = buffer.substring(0, newline).trim()
                        buffer.delete(0, newline + 1)
                        if (line.startsWith("{")) lines.send(line)
                    }
                }
            }
        }

        suspend fun <T> withInitialized(block: suspend Connection.() -> T): T = mutex.withLock {
            markActive()
            if (!initialized) {
                val params = json.encodeToJsonElement(McpInitializeParams.serializer(), McpInitializeParams())
                val response = requestUnlocked("initialize", params)
                val result = response.result?.let { json.decodeFromJsonElement(McpInitializeResult.serializer(), it) }
                    ?: error("MCP initialize did not return a result")
                require(result.protocolVersion.isNotBlank())
                notifyUnlocked("notifications/initialized")
                initialized = true
            }
            block()
        }

        suspend fun request(method: String, params: kotlinx.serialization.json.JsonElement) = requestUnlocked(method, params)

        private suspend fun requestUnlocked(method: String, params: kotlinx.serialization.json.JsonElement): JsonRpcResponse {
            markActive()
            val id = UUID.randomUUID().toString()
            write(json.encodeToString(JsonRpcRequest.serializer(), JsonRpcRequest(id = id, method = method, params = params)))
            return withTimeout(REQUEST_TIMEOUT_MS) {
                while (true) {
                    val parsed = runCatching { json.decodeFromString(JsonRpcResponse.serializer(), lines.receive()) }.getOrNull()
                    if (parsed?.id == id) {
                        parsed.error?.let { error("MCP JSON-RPC ${it.code}: ${it.message}") }
                        return@withTimeout parsed
                    }
                }
                error("unreachable")
            }
        }

        private suspend fun notifyUnlocked(method: String) =
            write(json.encodeToString(JsonRpcNotification.serializer(), JsonRpcNotification(method = method)))

        private suspend fun write(payload: String) = session.write("$payload\n".toByteArray(Charsets.UTF_8))

        suspend fun close() {
            readerJob?.cancel()
            runCatching { session.close() }
        }
    }

    private fun commandLine(server: McpServerConfig): String {
        require(server.command.isNotBlank())
        return (listOf(server.command) + server.args).joinToString(" ", transform = ::shellQuote)
    }

    private fun fingerprint(server: McpServerConfig) = "${server.command}|${server.args}|${server.env}"
    private fun shellQuote(value: String) = "'${value.replace("'", "'\"'\"'")}'"

    companion object {
        private const val REQUEST_TIMEOUT_MS = 120_000L

        /** 空闲连接回收阈值与清扫周期。 */
        internal const val IDLE_TIMEOUT_MS = 10 * 60 * 1000L
        internal const val SWEEP_INTERVAL_MS = 60 * 1000L
    }
}
