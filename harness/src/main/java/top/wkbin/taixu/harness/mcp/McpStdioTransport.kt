package top.wkbin.taixu.harness.mcp

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
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

    private suspend fun connection(server: McpServerConfig): Connection {
        connections[server.id]?.takeIf { it.session.isAlive && it.fingerprint == fingerprint(server) }?.let { return it }
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

        fun startReader() {
            scope.launch {
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
        suspend fun close() { runCatching { session.close() } }
    }

    private fun commandLine(server: McpServerConfig): String {
        require(server.command.isNotBlank())
        return (listOf(server.command) + server.args).joinToString(" ", transform = ::shellQuote)
    }

    private fun fingerprint(server: McpServerConfig) = "${server.command}|${server.args}|${server.env}"
    private fun shellQuote(value: String) = "'${value.replace("'", "'\"'\"'")}'"

    companion object { private const val REQUEST_TIMEOUT_MS = 120_000L }
}
