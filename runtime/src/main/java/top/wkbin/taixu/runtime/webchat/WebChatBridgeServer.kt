package top.wkbin.taixu.runtime.webchat

import android.content.Context
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.HarnessSessionRepository
import top.wkbin.taixu.core.database.AiModelRepository
import top.wkbin.taixu.core.database.WorkspaceRepository
import top.wkbin.taixu.core.datastore.SettingsDataStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

data class WebChatServerStatus(
    val isRunning: Boolean = false,
    val port: Int = DEFAULT_PORT,
    val localIp: String = "127.0.0.1",
    val pinCode: String = "",
    val activeConnections: Int = 0,
) {
    val accessUrl: String get() = "http://$localIp:$port"
}

@Singleton
class WebChatBridgeServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionDao: HarnessSessionRepository,
    private val aiModelDao: AiModelRepository,
    private val workspaceDao: WorkspaceRepository,
    private val settingsDataStore: SettingsDataStore,
    private val logger: AppLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var httpServer: HttpServer? = null
    private val executor = Executors.newFixedThreadPool(8)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _status = MutableStateFlow(WebChatServerStatus())
    val status: StateFlow<WebChatServerStatus> = _status.asStateFlow()

    private val sseEmitters = ConcurrentHashMap.newKeySet<HttpExchange>()

    /**
     * 启动局域网 WebChat HTTP/SSE 服务
     */
    fun start(port: Int = DEFAULT_PORT, pin: String? = null): Boolean {
        if (_status.value.isRunning) return true
        return try {
            val generatedPin = pin ?: generatePin()
            val server = HttpServer.create(InetSocketAddress(port), 0)
            server.executor = executor

            // 1. 静态资源路由（托管 assets/webchat 前端）
            server.createContext("/", StaticAssetHandler())

            // 2. API 路由
            server.createContext("/webchat/api/bootstrap", BootstrapHandler())
            server.createContext("/api/bootstrap", BootstrapHandler())
            server.createContext("/webchat/api/conversations", ConversationsHandler())
            server.createContext("/api/conversations", ConversationsHandler())
            server.createContext("/webchat/api/events", SseEventsHandler())
            server.createContext("/api/events", SseEventsHandler())
            server.createContext("/webchat/api/workspaces", WorkspacesHandler())
            server.createContext("/api/workspaces", WorkspacesHandler())

            server.start()
            httpServer = server

            val localIp = resolveLocalIp()
            _status.value = WebChatServerStatus(
                isRunning = true,
                port = port,
                localIp = localIp,
                pinCode = generatedPin,
            )
            logger.i("WebChat 局域网协作服务启动成功：http://$localIp:$port (PIN: $generatedPin)")
            true
        } catch (e: Exception) {
            logger.e("WebChat 服务启动失败", e)
            false
        }
    }

    /**
     * 停止局域网 WebChat 服务
     */
    fun stop() {
        try {
            sseEmitters.forEach { runCatching { it.close() } }
            sseEmitters.clear()
            httpServer?.stop(0)
            httpServer = null
            _status.value = _status.value.copy(isRunning = false)
            logger.i("WebChat 局域网协作服务已停止")
        } catch (e: Exception) {
            logger.e("WebChat 停止异常", e)
        }
    }

    /**
     * 向所有已连接的 Web 客户端广播实时事件
     */
    fun broadcastEvent(eventName: String, dataJson: String) {
        val payload = "event: $eventName\ndata: $dataJson\n\n".toByteArray(Charsets.UTF_8)
        val iterator = sseEmitters.iterator()
        while (iterator.hasNext()) {
            val exchange = iterator.next()
            try {
                exchange.responseBody.write(payload)
                exchange.responseBody.flush()
            } catch (_: Exception) {
                runCatching { exchange.close() }
                iterator.remove()
            }
        }
        _status.value = _status.value.copy(activeConnections = sseEmitters.size)
    }

    // ============================= HTTP Handlers =============================

    /**
     * 静态 Web 资源处理器：从 assets/webchat 加载 index.html 及 assets
     */
    private inner class StaticAssetHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val path = exchange.requestURI.path.removePrefix("/").trimStart('/')
                val assetPath = if (path.isBlank() || !hasFileExtension(path)) {
                    "webchat/index.html"
                } else {
                    "webchat/$path"
                }

                val stream: InputStream? = try {
                    context.assets.open(assetPath)
                } catch (_: Exception) {
                    context.assets.open("webchat/index.html")
                }

                if (stream == null) {
                    sendResponse(exchange, 404, "text/plain", "Asset Not Found".toByteArray())
                    return
                }

                val mime = getMimeType(assetPath)
                val bytes = stream.use { it.readBytes() }
                sendResponse(exchange, 200, mime, bytes)
            } catch (e: Exception) {
                logger.e("StaticAssetHandler failed", e)
                sendResponse(exchange, 500, "text/plain", (e.message ?: "Server Error").toByteArray())
            }
        }
    }

    /**
     * Bootstrap 基础信息
     */
    private inner class BootstrapHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            scope.launch {
                try {
                    val tokenParam = getQueryParam(exchange, "token") ?: getHeader(exchange, "Authorization")?.removePrefix("Bearer ")
                    val isAuthenticated = tokenParam.isNullOrBlank() || tokenParam == _status.value.pinCode

                    val sessions = sessionDao.listAll()
                    val models = aiModelDao.listAll()

                    val responseJson = buildJsonObject {
                        put("authenticated", isAuthenticated)
                        put("pinRequired", _status.value.pinCode.isNotBlank())
                        put("token", _status.value.pinCode)
                        put("appName", "太墟智枢 (TaiXu)")
                        put("version", "1.0.0")
                        putJsonArray("conversations") {
                            sessions.forEach { s ->
                                add(
                                    buildJsonObject {
                                        put("id", s.id)
                                        put("title", s.title)
                                        put("createdAt", s.createdAt)
                                        put("updatedAt", s.updatedAt)
                                        put("modelId", s.modelId ?: "")
                                        put("workspace", s.workspace)
                                    }
                                )
                            }
                        }
                        putJsonArray("models") {
                            models.forEach { m ->
                                add(
                                    buildJsonObject {
                                        put("id", m.id)
                                        put("name", m.displayName)
                                        put("provider", m.provider)
                                    }
                                )
                            }
                        }
                    }.toString()

                    sendResponse(exchange, 200, "application/json; charset=utf-8", responseJson.toByteArray(Charsets.UTF_8))
                } catch (e: Exception) {
                    sendResponse(exchange, 500, "application/json", "{\"error\":\"${e.message}\"}".toByteArray())
                }
            }
        }
    }

    /**
     * 会话列表与创建
     */
    private inner class ConversationsHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            scope.launch {
                try {
                    when (exchange.requestMethod.uppercase()) {
                        "GET" -> {
                            val sessions = sessionDao.listAll()
                            val arrayJson = buildJsonObject {
                                putJsonArray("conversations") {
                                    sessions.forEach { s ->
                                        add(
                                            buildJsonObject {
                                                put("id", s.id)
                                                put("title", s.title)
                                                put("createdAt", s.createdAt)
                                                put("updatedAt", s.updatedAt)
                                                put("modelId", s.modelId ?: "")
                                                put("workspace", s.workspace)
                                            }
                                        )
                                    }
                                }
                            }.toString()
                            sendResponse(exchange, 200, "application/json; charset=utf-8", arrayJson.toByteArray(Charsets.UTF_8))
                        }
                        "POST" -> {
                            val body = exchange.requestBody.bufferedReader().readText()
                            val newId = UUID.randomUUID().toString()
                            val now = System.currentTimeMillis()
                            val entity = top.wkbin.taixu.core.database.HarnessSessionEntity(
                                id = newId,
                                title = "新对话",
                                createdAt = now,
                                updatedAt = now,
                                modelId = null,
                            )
                            sessionDao.upsert(entity)
                            val resp = "{\"id\":\"$newId\",\"title\":\"新对话\",\"createdAt\":$now,\"updatedAt\":$now}"
                            sendResponse(exchange, 200, "application/json; charset=utf-8", resp.toByteArray(Charsets.UTF_8))
                        }
                        else -> sendResponse(exchange, 405, "text/plain", "Method Not Allowed".toByteArray())
                    }
                } catch (e: Exception) {
                    sendResponse(exchange, 500, "application/json", "{\"error\":\"${e.message}\"}".toByteArray())
                }
            }
        }
    }

    /**
     * SSE 实时事件流
     */
    private inner class SseEventsHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.responseHeaders.add("Cache-Control", "no-cache")
            exchange.responseHeaders.add("Connection", "keep-alive")
            exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
            exchange.sendResponseHeaders(200, 0)
            sseEmitters.add(exchange)
            _status.value = _status.value.copy(activeConnections = sseEmitters.size)
            // 发送初始 ping 保活
            val initData = "event: ping\ndata: {}\n\n".toByteArray(Charsets.UTF_8)
            exchange.responseBody.write(initData)
            exchange.responseBody.flush()
        }
    }

    /**
     * 工作区文件管理
     */
    private inner class WorkspacesHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            scope.launch {
                try {
                    val root = File("/data/data/${context.packageName}/files/workspaces")
                    val items = root.walkTopDown().maxDepth(2).map { f ->
                        buildJsonObject {
                            put("name", f.name)
                            put("path", f.relativeTo(root).path)
                            put("isDirectory", f.isDirectory)
                            put("size", f.length())
                        }
                    }.toList()

                    val resp = buildJsonObject {
                        putJsonArray("items") {
                            items.forEach { add(it) }
                        }
                    }.toString()
                    sendResponse(exchange, 200, "application/json; charset=utf-8", resp.toByteArray(Charsets.UTF_8))
                } catch (e: Exception) {
                    sendResponse(exchange, 500, "application/json", "{\"error\":\"${e.message}\"}".toByteArray())
                }
            }
        }
    }

    // ============================= 辅助方法 =============================

    private fun sendResponse(exchange: HttpExchange, code: Int, contentType: String, bytes: ByteArray) {
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type, Authorization")
        exchange.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.write(bytes)
        exchange.responseBody.close()
    }

    private fun getMimeType(path: String): String = when {
        path.endsWith(".html") -> "text/html; charset=utf-8"
        path.endsWith(".js") || path.endsWith(".mjs") -> "application/javascript; charset=utf-8"
        path.endsWith(".css") -> "text/css; charset=utf-8"
        path.endsWith(".json") -> "application/json; charset=utf-8"
        path.endsWith(".svg") -> "image/svg+xml"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".ico") -> "image/x-icon"
        else -> "application/octet-stream"
    }

    private fun hasFileExtension(path: String): Boolean = path.substringAfterLast('/', "").contains('.')

    private fun getQueryParam(exchange: HttpExchange, key: String): String? {
        val query = exchange.requestURI.query ?: return null
        return query.split('&')
            .map { it.split('=') }
            .firstOrNull { it.size == 2 && it[0] == key }
            ?.get(1)
    }

    private fun getHeader(exchange: HttpExchange, key: String): String? = exchange.requestHeaders.getFirst(key)

    private fun generatePin(): String = (100000..999999).random().toString()

    private fun resolveLocalIp(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var fallback = "127.0.0.1"
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress.orEmpty()
                        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            return host
                        }
                        fallback = host
                    }
                }
            }
            fallback
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }

    companion object {
        const val DEFAULT_PORT = 8899
    }
}
