package top.wkbin.taixu.harness.mcp

import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import top.wkbin.taixu.core.model.McpServerConfig
import top.wkbin.taixu.core.model.McpToolInfo

/** Streamable HTTP/SSE MCP transport with strict endpoint and body-size policy. */
@Singleton
class McpHttpTransport @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) : McpTransport {
    override suspend fun check(server: McpServerConfig) = withContext(Dispatchers.IO) {
        runCatching { initialize(server); true }.getOrDefault(false)
    }

    override suspend fun discover(server: McpServerConfig): List<McpToolInfo> = withContext(Dispatchers.IO) {
        val response = request(initialize(server), "tools/list", JsonObject(emptyMap()))
        val result = response.result?.let { json.decodeFromJsonElement(McpToolsListResponse.serializer(), it) }
            ?: error("MCP tools/list did not return a result")
        result.tools.map { it.toInfo(server, json.encodeToString(JsonObject.serializer(), it.inputSchema)) }
    }

    override suspend fun execute(server: McpServerConfig, toolName: String, arguments: JsonObject) = withContext(Dispatchers.IO) {
        val params = json.encodeToJsonElement(McpCallToolParams.serializer(), McpCallToolParams(toolName, arguments))
        val response = request(initialize(server), "tools/call", params)
        val result = response.result?.let { json.decodeFromJsonElement(McpCallToolResult.serializer(), it) }
            ?: error("MCP tools/call did not return a result")
        !result.isError to result.content.joinToString("\n") { it.text.orEmpty() }
            .ifBlank { if (result.isError) "执行失败" else "执行成功" }
    }

    private fun initialize(server: McpServerConfig): Session {
        val endpoint = validatedMcpHttpEndpoint(server.serverUrl)
        val params = json.encodeToJsonElement(McpInitializeParams.serializer(), McpInitializeParams())
        val exchange = exchange(endpoint, "initialize", params, MCP_PROTOCOL_VERSION, null)
        val result = exchange.response.result?.let { json.decodeFromJsonElement(McpInitializeResult.serializer(), it) }
            ?: error("MCP initialize did not return a result")
        val session = Session(endpoint, exchange.sessionId, result.protocolVersion)
        notify(session)
        return session
    }

    private fun request(session: Session, method: String, params: JsonElement) =
        exchange(session.endpoint, method, params, session.protocolVersion, session.sessionId).response

    private fun exchange(endpoint: HttpUrl, method: String, params: JsonElement, protocol: String, sessionId: String?): Exchange {
        val id = UUID.randomUUID().toString()
        val payload = json.encodeToString(JsonRpcRequest.serializer(), JsonRpcRequest(id = id, method = method, params = params))
        val request = requestBuilder(endpoint, protocol, sessionId).post(payload.toRequestBody(JSON)).build()
        return client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "MCP HTTP ${response.code}" }
            val rpc = readResponse(response, id)
            rpc.error?.let { error("MCP JSON-RPC ${it.code}: ${it.message}") }
            Exchange(rpc, response.header("Mcp-Session-Id") ?: sessionId)
        }
    }

    private fun notify(session: Session) {
        val payload = json.encodeToString(JsonRpcNotification.serializer(), JsonRpcNotification(method = "notifications/initialized"))
        val request = requestBuilder(session.endpoint, session.protocolVersion, session.sessionId)
            .post(payload.toRequestBody(JSON)).build()
        client.newCall(request).execute().use { check(it.isSuccessful) { "MCP HTTP ${it.code}" } }
    }

    private fun requestBuilder(endpoint: HttpUrl, protocol: String, sessionId: String?) = Request.Builder()
        .url(endpoint).header("Accept", ACCEPT).header("MCP-Protocol-Version", protocol)
        .apply { sessionId?.let { header("Mcp-Session-Id", it) } }

    private fun readResponse(response: Response, requestId: String): JsonRpcResponse =
        if (response.header("Content-Type").orEmpty().lowercase().startsWith("text/event-stream")) {
            readSse(response, requestId)
        } else {
            json.decodeFromString(JsonRpcResponse.serializer(), readLimited(response))
        }

    private fun readSse(response: Response, requestId: String): JsonRpcResponse {
        val source = response.body.source()
        val data = mutableListOf<String>()
        var bytes = 0
        while (true) {
            val line = source.readUtf8Line() ?: break
            bytes += line.toByteArray().size + 1
            require(bytes <= MAX_BYTES) { "MCP response is too large" }
            if (line.isBlank()) {
                decodeSse(data, requestId)?.let { return it }
                data.clear()
            } else if (line.startsWith("data:")) data += line.removePrefix("data:").trimStart()
        }
        return decodeSse(data, requestId) ?: error("MCP SSE response did not contain request $requestId")
    }

    private fun decodeSse(lines: List<String>, id: String) =
        runCatching { json.decodeFromString(JsonRpcResponse.serializer(), lines.joinToString("\n")) }
            .getOrNull()?.takeIf { it.id == id }

    private fun readLimited(response: Response): String {
        val length = response.body.contentLength()
        require(length < 0 || length <= MAX_BYTES) { "MCP response is too large" }
        val output = ByteArrayOutputStream()
        response.body.byteStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                require(output.size() + read <= MAX_BYTES) { "MCP response is too large" }
                output.write(buffer, 0, read)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private data class Session(val endpoint: HttpUrl, val sessionId: String?, val protocolVersion: String)
    private data class Exchange(val response: JsonRpcResponse, val sessionId: String?)

    companion object {
        private const val ACCEPT = "application/json, text/event-stream"
        private const val MAX_BYTES = 4 * 1024 * 1024
        private val JSON = "application/json".toMediaType()
    }
}

internal fun isAllowedCleartextMcpHost(host: String): Boolean {
    if (host == "localhost" || host == "127.0.0.1" || host == "::1") return true
    val octets = host.split('.')
    if (octets.size != 4 || octets[0] != "192" || octets[1] != "168") return false
    return octets.all { it.toIntOrNull()?.let { value -> value in 0..255 } == true }
}

internal fun validatedMcpHttpEndpoint(baseUrl: String): HttpUrl {
    val url = baseUrl.trim().toHttpUrl()
    require(url.isHttps || isAllowedCleartextMcpHost(url.host)) {
        "明文 MCP 仅允许 localhost、回环地址或 192.168.* 局域网地址"
    }
    return url
}
