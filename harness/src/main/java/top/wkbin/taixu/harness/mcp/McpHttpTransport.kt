package top.wkbin.taixu.harness.mcp

import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.model.McpServerConfig
import top.wkbin.taixu.core.model.McpToolInfo
import kotlin.time.Duration.Companion.milliseconds

/**
 * 远程 MCP 传输：同时支持 Streamable HTTP（2025-03-26+）与 legacy HTTP+SSE（2024-11-05）。
 *
 * 连接策略：
 * - 会话按服务 id 复用，一次握手（initialize + notifications/initialized）后所有请求直接复用；
 * - 自动协商：URL 以 /sse 结尾优先尝试 legacy SSE，否则先试 Streamable HTTP，失败回落另一种；
 * - 请求失败且属于传输层故障（IO / HTTP 状态码 / 会话失效）时丢弃会话重建并重试一次，
 *   JSON-RPC 应用层错误不重试，避免工具副作用被重复执行。
 */
@Singleton
class McpHttpTransport @Inject constructor(
    client: OkHttpClient,
    private val json: Json,
    private val logger: AppLogger,
) : McpTransport {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = ConcurrentHashMap<String, HttpSession>()

    /** 连接失败冷却：服务 id → 冷却截止时间戳。冷却期内直接快速失败，不再空耗连接超时。 */
    private val downUntil = ConcurrentHashMap<String, Long>()

    // 握手/列表用短超时；工具调用可能耗时较长；SSE 长连接读流不设超时。
    // connectTimeout 单独收紧：本地端口无服务时（ECONNREFUSED 被系统延迟上报）也要快速失败。
    private val fastClient = client.newBuilder()
        .connectTimeout(FAST_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(FAST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()
    private val callClient = client.newBuilder()
        .connectTimeout(CALL_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()
    private val sseClient = client.newBuilder()
        .connectTimeout(CALL_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun check(server: McpServerConfig) = withContext(Dispatchers.IO) {
        // 设置页手动测试连接不受冷却限制
        runCatching { ensureSession(server, bypassCooldown = true); true }.getOrDefault(false)
    }

    override suspend fun discover(server: McpServerConfig): List<McpToolInfo> = withContext(Dispatchers.IO) {
        withRetryableSession(server) { session ->
            val response = exchange(session, "tools/list", JsonObject(emptyMap()))
            val result = response.result?.let { json.decodeFromJsonElement(McpToolsListResponse.serializer(), it) }
                ?: error("MCP tools/list did not return a result")
            result.tools.map { it.toInfo(server, json.encodeToString(JsonObject.serializer(), it.inputSchema)) }
        }
    }

    override suspend fun execute(server: McpServerConfig, toolName: String, arguments: JsonObject): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            withRetryableSession(server) { session ->
                val params = json.encodeToJsonElement(
                    McpCallToolParams.serializer(),
                    McpCallToolParams(toolName, arguments),
                )
                val response = exchange(session, "tools/call", params)
                val result = response.result?.let { json.decodeFromJsonElement(McpCallToolResult.serializer(), it) }
                    ?: error("MCP tools/call did not return a result")
                !result.isError to result.content.joinToString("\n") { it.text.orEmpty() }
                    .ifBlank { if (result.isError) "执行失败" else "执行成功" }
            }
        }

    // ---------- 会话管理 ----------

    private suspend fun <T> withRetryableSession(server: McpServerConfig, block: suspend (HttpSession) -> T): T {
        val session = ensureSession(server)
        return try {
            block(session)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (!isTransportFailure(t)) throw t
            logger.w("MCP[${server.name}] 会话失效（${t.message}），重建后重试一次")
            dropSession(server.id, session)
            block(ensureSession(server))
        }
    }

    private fun isTransportFailure(t: Throwable): Boolean =
        t is IOException || (t is IllegalStateException && t.message?.startsWith("MCP HTTP ") == true)

    private suspend fun ensureSession(server: McpServerConfig, bypassCooldown: Boolean = false): HttpSession {
        val url = server.serverUrl.trim()
        sessions[server.id]?.let { existing ->
            if (existing.serverUrl == url && existing.isOpen) return existing
            dropSession(server.id, existing)
        }
        // 冷却期内直接快速失败：上一轮刚整体握手失败，短时间内大概率仍是同一个故障
        // （进程未启动 / 端口不对），不值得每轮对话都空耗连接超时。
        val now = System.currentTimeMillis()
        if (!bypassCooldown) {
            val until = downUntil[server.id]
            if (until != null && now < until) {
                throw IllegalStateException(
                    "MCP[${server.name}] 连接冷却中（剩余 ${(until - now) / 1000}s），跳过本次连接；" +
                        "请确认服务端已启动后再在设置页手动测试",
                )
            }
        }
        val preferLegacy = runCatching {
            validatedMcpHttpEndpoint(url).encodedPath.trimEnd('/').endsWith("sse")
        }.getOrDefault(false)
        val attempts = if (preferLegacy) {
            listOf(TransportMode.LEGACY_SSE, TransportMode.STREAMABLE_HTTP)
        } else {
            listOf(TransportMode.STREAMABLE_HTTP, TransportMode.LEGACY_SSE)
        }
        val failures = mutableListOf<String>()
        for (mode in attempts) {
            val label = modeLabel(mode)
            try {
                val session = if (mode == TransportMode.STREAMABLE_HTTP) {
                    createStreamableSession(server)
                } else {
                    createLegacySession(server)
                }
                sessions[server.id] = session
                downUntil.remove(server.id)
                logger.i("MCP[${server.name}] 连接成功（$label）endpoint=${session.endpoint}")
                return session
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                failures += "$label: ${t.message ?: t::class.simpleName}"
                logger.w("MCP[${server.name}] $label 握手失败: ${t.message}", t)
            }
        }
        downUntil[server.id] = System.currentTimeMillis() + FAILURE_COOLDOWN_MS
        throw IllegalStateException("MCP HTTP 连接失败（${failures.joinToString("；")}）")
    }

    private fun dropSession(serverId: String, session: HttpSession) {
        sessions.remove(serverId, session)
        session.legacy?.close()
    }

    private suspend fun createStreamableSession(server: McpServerConfig): HttpSession {
        val endpoint = validatedMcpHttpEndpoint(server.serverUrl)
        val params = json.encodeToJsonElement(McpInitializeParams.serializer(), McpInitializeParams())
        val init = postStreamable(endpoint, sessionId = null, method = "initialize", params = params)
        val result = init.response.result?.let { json.decodeFromJsonElement(McpInitializeResult.serializer(), it) }
            ?: error("MCP initialize did not return a result")
        val session = HttpSession(
            mode = TransportMode.STREAMABLE_HTTP,
            endpoint = endpoint,
            sessionId = init.sessionId,
            protocolVersion = result.protocolVersion,
            serverUrl = server.serverUrl.trim(),
        )
        postNotification(session, "notifications/initialized")
        return session
    }

    private suspend fun createLegacySession(server: McpServerConfig): HttpSession {
        val sseUrl = validatedMcpHttpEndpoint(server.serverUrl)
        val channel = LegacySseChannel(sseUrl, sseClient, json, scope)
        try {
            val messageEndpoint = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) { channel.awaitEndpoint() }
                ?: error("Legacy SSE 握手超时：${HANDSHAKE_TIMEOUT_MS / 1000}s 内未收到 endpoint 事件")
            val params = json.encodeToJsonElement(McpInitializeParams.serializer(), McpInitializeParams())
            val init = postViaLegacy(channel, "initialize", params)
            val result = init.result?.let { json.decodeFromJsonElement(McpInitializeResult.serializer(), it) }
                ?: error("MCP initialize did not return a result")
            postViaLegacyNotification(channel, "notifications/initialized")
            return HttpSession(
                mode = TransportMode.LEGACY_SSE,
                endpoint = messageEndpoint,
                sessionId = null,
                protocolVersion = result.protocolVersion,
                serverUrl = server.serverUrl.trim(),
                legacy = channel,
            )
        } catch (t: Throwable) {
            channel.close()
            throw t
        }
    }

    // ---------- 请求执行 ----------

    private suspend fun exchange(session: HttpSession, method: String, params: JsonElement): JsonRpcResponse =
        when (session.mode) {
            TransportMode.STREAMABLE_HTTP -> postStreamable(
                endpoint = session.endpoint,
                sessionId = session.sessionId,
                method = method,
                params = params,
                longRunning = method == "tools/call",
            ).response
            TransportMode.LEGACY_SSE -> postViaLegacy(
                channel = session.legacy ?: error("Legacy SSE 会话已关闭"),
                method = method,
                params = params,
            )
        }

    private fun postStreamable(
        endpoint: HttpUrl,
        sessionId: String?,
        method: String,
        params: JsonElement,
        longRunning: Boolean = false,
    ): StreamableExchange {
        val id = UUID.randomUUID().toString()
        val payload = json.encodeToString(JsonRpcRequest.serializer(), JsonRpcRequest(id = id, method = method, params = params))
        val request = requestBuilder(endpoint, sessionId)
            .header("MCP-Protocol-Version", MCP_PROTOCOL_VERSION)
            .post(payload.toRequestBody(JSON))
            .build()
        val client = if (longRunning) callClient else fastClient
        return client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "MCP HTTP ${response.code}" }
            val rpc = readResponse(response, id)
            rpc.error?.let { error("MCP JSON-RPC ${it.code}: ${it.message}") }
            StreamableExchange(rpc, response.header("Mcp-Session-Id"))
        }
    }

    private fun postNotification(session: HttpSession, method: String) {
        val payload = json.encodeToString(JsonRpcNotification.serializer(), JsonRpcNotification(method = method))
        val request = requestBuilder(session.endpoint, session.sessionId)
            .header("MCP-Protocol-Version", session.protocolVersion)
            .post(payload.toRequestBody(JSON))
            .build()
        fastClient.newCall(request).execute().use { check(it.isSuccessful) { "MCP HTTP ${it.code}" } }
    }

    private suspend fun postViaLegacy(channel: LegacySseChannel, method: String, params: JsonElement): JsonRpcResponse {
        val id = UUID.randomUUID().toString()
        val payload = json.encodeToString(JsonRpcRequest.serializer(), JsonRpcRequest(id = id, method = method, params = params))
        val deferred = channel.register(id)
        return try {
            postLegacyPayload(channel, payload)
            withTimeoutOrNull(CALL_TIMEOUT_MS.milliseconds) { channel.await(id, deferred) }
                ?: error("MCP 响应超时（${CALL_TIMEOUT_MS / 1000}s）")
        } catch (t: Throwable) {
            channel.unregister(id)
            throw t
        }
    }

    private fun postViaLegacyNotification(channel: LegacySseChannel, method: String) {
        val payload = json.encodeToString(JsonRpcNotification.serializer(), JsonRpcNotification(method = method))
        postLegacyPayload(channel, payload)
    }

    private fun postLegacyPayload(channel: LegacySseChannel, payload: String) {
        val endpoint = channel.messageEndpoint ?: error("Legacy SSE 消息端点未就绪")
        val request = Request.Builder().url(endpoint).header("Accept", ACCEPT)
            .post(payload.toRequestBody(JSON)).build()
        fastClient.newCall(request).execute().use { check(it.isSuccessful) { "MCP HTTP ${it.code}" } }
    }

    private fun requestBuilder(endpoint: HttpUrl, sessionId: String?) = Request.Builder()
        .url(endpoint).header("Accept", ACCEPT)
        .apply { sessionId?.let { header("Mcp-Session-Id", it) } }

    // ---------- 响应解析 ----------

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
        val output = java.io.ByteArrayOutputStream()
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

    private fun modeLabel(mode: TransportMode) = when (mode) {
        TransportMode.STREAMABLE_HTTP -> "Streamable HTTP"
        TransportMode.LEGACY_SSE -> "Legacy SSE"
    }

    private enum class TransportMode { STREAMABLE_HTTP, LEGACY_SSE }

    private class StreamableExchange(val response: JsonRpcResponse, val sessionId: String?)

    private class HttpSession(
        val mode: TransportMode,
        val endpoint: HttpUrl,
        val sessionId: String?,
        val protocolVersion: String,
        val serverUrl: String,
        val legacy: LegacySseChannel? = null,
    ) {
        /** Streamable 会话视为一直可用（失效由传输层错误触发重建）；Legacy 会话看读流是否存活 */
        val isOpen: Boolean get() = legacy == null || legacy.isAlive
    }

    /**
     * legacy HTTP+SSE 通道：GET 打开长连接接收服务端事件，
     * 请求 POST 到握手时下发的消息端点，响应经长连接按 id 回流。
     */
    private class LegacySseChannel(
        private val url: HttpUrl,
        sseClient: OkHttpClient,
        private val json: Json,
        scope: CoroutineScope,
    ) {
        private val call = sseClient.newCall(
            Request.Builder().url(url).header("Accept", "text/event-stream").build(),
        )
        private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonRpcResponse>>()
        private val endpointDeferred = CompletableDeferred<HttpUrl>()

        @Volatile private var closed = false

        @Volatile var messageEndpoint: HttpUrl? = null
            private set

        val isAlive: Boolean get() = !closed && endpointDeferred.isCompleted

        private val reader = scope.launch { readLoop() }

        private suspend fun readLoop() {
            runCatching {
                call.execute().use { response ->
                    check(response.isSuccessful) { "MCP HTTP ${response.code}" }
                    var event = ""
                    val data = mutableListOf<String>()
                    val source = response.body.source()
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                            line.startsWith("data:") -> data += line.removePrefix("data:").trimStart()
                            line.isBlank() -> {
                                onEvent(event, data)
                                event = ""
                                data.clear()
                            }
                        }
                    }
                    error("SSE 连接已关闭")
                }
            }.onFailure { fail(it) }
        }

        private fun onEvent(event: String, data: List<String>) {
            if (data.isEmpty()) return
            if (event == "endpoint") {
                val resolved = url.resolve(data.first())
                if (resolved == null) {
                    endpointDeferred.completeExceptionally(
                        IllegalStateException("无法解析 endpoint 地址：${data.first()}"),
                    )
                } else {
                    messageEndpoint = resolved
                    endpointDeferred.complete(resolved)
                }
                return
            }
            val rpc = runCatching {
                json.decodeFromString(JsonRpcResponse.serializer(), data.joinToString("\n"))
            }.getOrNull() ?: return
            rpc.id?.let { key -> pending.remove(key)?.complete(rpc) }
        }

        fun register(id: String): CompletableDeferred<JsonRpcResponse> {
            val deferred = CompletableDeferred<JsonRpcResponse>()
            pending[id] = deferred
            return deferred
        }

        fun unregister(id: String) {
            pending.remove(id)
        }

        suspend fun awaitEndpoint(): HttpUrl = endpointDeferred.await()

        suspend fun await(id: String, deferred: CompletableDeferred<JsonRpcResponse>): JsonRpcResponse =
            try {
                deferred.await()
            } finally {
                pending.remove(id)
            }

        fun close() {
            if (closed) return
            closed = true
            call.cancel()
            reader.cancel()
        }

        private fun fail(t: Throwable) {
            closed = true
            endpointDeferred.completeExceptionally(t)
            pending.values.forEach { it.completeExceptionally(t) }
            pending.clear()
        }
    }

    companion object {
        private const val ACCEPT = "application/json, text/event-stream"
        private const val MAX_BYTES = 4 * 1024 * 1024
        private const val FAST_TIMEOUT_MS = 30_000L
        private const val CALL_TIMEOUT_MS = 120_000L
        private const val HANDSHAKE_TIMEOUT_MS = 20_000L

        /** TCP 连接超时：本地地址端口无服务时快速失败，避免 30s 级别的空等。 */
        private const val FAST_CONNECT_TIMEOUT_MS = 3_000L
        private const val CALL_CONNECT_TIMEOUT_MS = 10_000L

        /** 整体握手失败后的冷却时长：期间该服务的工具发现直接快速失败。 */
        private const val FAILURE_COOLDOWN_MS = 5 * 60_000L
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
