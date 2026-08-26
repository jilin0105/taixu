package top.wkbin.taixu.harness.mcp

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import top.wkbin.taixu.core.database.McpServerRepository
import top.wkbin.taixu.core.model.McpConnectionState
import top.wkbin.taixu.core.model.McpServerConfig
import top.wkbin.taixu.core.model.McpToolInfo
import top.wkbin.taixu.core.model.McpTransportType

/** Thin MCP registry coordinator; transports own protocol and process details. */
@Singleton
class McpManager @Inject constructor(
    private val repository: McpServerRepository,
    private val stdio: McpStdioTransport,
    private val http: McpHttpTransport,
) {
    private val cache = ConcurrentHashMap<String, List<McpToolInfo>>()
    private val _connectionStates = MutableStateFlow<Map<String, McpConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, McpConnectionState>> = _connectionStates.asStateFlow()

    suspend fun checkConnection(server: McpServerConfig) = transport(server).check(server)

    suspend fun refreshConnections() {
        val servers = repository.servers.first()
        _connectionStates.value = servers.associate { it.id to if (it.isEnabled) McpConnectionState.CHECKING else McpConnectionState.UNKNOWN }
        coroutineScope {
            servers.filter { it.isEnabled }.map { server ->
                launch {
                    val state = if (checkConnection(server)) McpConnectionState.ONLINE else McpConnectionState.OFFLINE
                    _connectionStates.update { it + (server.id to state) }
                }
            }.joinAll()
        }
    }

    suspend fun getActiveMcpTools(): List<McpToolInfo> = repository.servers.first().filter { it.isEnabled }.flatMap { server ->
        cache[server.id] ?: runCatching { discoverTools(server) }
            .onSuccess { cache[server.id] = it; state(server.id, McpConnectionState.ONLINE) }
            .onFailure { cache.remove(server.id); state(server.id, McpConnectionState.OFFLINE) }
            .getOrDefault(emptyList())
    }

    suspend fun discoverTools(server: McpServerConfig) = transport(server).discover(server)

    suspend fun testServer(server: McpServerConfig): Result<List<McpToolInfo>> = runCatching { discoverTools(server) }
        .onSuccess { cache[server.id] = it; state(server.id, McpConnectionState.ONLINE) }
        .onFailure { cache.remove(server.id); state(server.id, McpConnectionState.OFFLINE) }

    suspend fun executeTool(fullToolName: String, arguments: JsonObject): Pair<Boolean, String> {
        if (!fullToolName.startsWith("mcp__")) return false to "无效的 MCP 工具名称：$fullToolName"
        val tool = getActiveMcpTools().firstOrNull { McpToolApiName.matches(it, fullToolName) }
            ?: return false to "未找到 MCP 工具：$fullToolName"
        val server = repository.servers.first().firstOrNull { it.id == tool.serverId && it.isEnabled }
            ?: return false to "未找到 MCP 服务：${tool.serverId}"
        return runCatching { transport(server).execute(server, tool.name, arguments) }
            .getOrElse { false to "MCP 工具执行异常：${it.message ?: it::class.simpleName}" }
    }

    private fun state(id: String, state: McpConnectionState) { _connectionStates.update { it + (id to state) } }
    private fun transport(server: McpServerConfig): McpTransport = when (server.transportType) {
        McpTransportType.STDIO -> stdio
        McpTransportType.SSE -> http
    }
}
