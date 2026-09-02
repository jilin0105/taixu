package top.wkbin.taixu.harness.mcp.server

import android.util.Log
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.wkbin.taixu.harness.mcp.MCP_PROTOCOL_VERSION

/**
 * 进程内 MCP server：单端口（默认 8787）上提供 POST /mcp 与 GET /mcp/stats、/mcp/health。
 *
 * 实现：Ktor 3.x + CIO engine（CIO 是纯 Kotlin 协程 HTTP server，不引入 Netty 重型运行时）。
 * 协议：JSON-RPC 2.0 + MCP 2024-11-05（initialize / tools/list / tools/call / resources/list / resources/read / ping）。
 */
@Singleton
class McpServerRuntime @Inject constructor(
    private val toolDispatcher: McpToolDispatcher,
    private val resourceDispatcher: McpResourceDispatcher,
) {
    @Volatile private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    @Volatile var currentToken: String? = null
        private set
    @Volatile var currentAllowRemote: Boolean = false
        private set
    @Volatile private var boundPort: Int = defaultPort

    val isRunning: Boolean get() = engine != null
    val port: Int get() = boundPort
    val host: String get() = if (currentAllowRemote) "0.0.0.0" else loopbackHost

    fun start(loopbackOnly: Boolean, token: String?, port: Int = defaultPort): Boolean {
        if (engine != null) return true
        val bindHost = if (loopbackOnly) loopbackHost else "0.0.0.0"
        currentAllowRemote = !loopbackOnly
        currentToken = token
        boundPort = port
        return try {
            val srv = embeddedServer(CIO, port = port, host = bindHost) {
                routing {
                    get("/mcp/health") {
                        call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    }
                    get("/mcp/stats") {
                        call.respondText(writeStatsJson(), ContentType.Application.Json)
                    }
                    post("/mcp") {
                        val auth = call.request.header("Authorization")
                        if (!McpAuthFilter.isAcceptable(
                                authHeader = auth,
                                loopback = !currentAllowRemote,
                                configuredToken = currentToken,
                            )
                        ) {
                            call.respondText(
                                """{"error":"unauthorized"}""",
                                ContentType.Application.Json,
                                HttpStatusCode.Unauthorized,
                            )
                            return@post
                        }
                        val body = call.receiveText()
                        val payload = handleJsonRpc(body)
                        call.respondText(payload, ContentType.Application.Json)
                    }
                    post("/mcp/sse") {
                        call.respondText(
                            """{"error":"sse-not-implemented"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.NotImplemented,
                        )
                    }
                }
            }
            srv.start(wait = false)
            engine = srv
            true
        } catch (t: Throwable) {
            Log.w(TAG, "start fail: ${'$'}{t.message}")
            engine = null
            false
        }
    }

    fun stop() {
        try { engine?.stop(100, 500) } catch (_: Throwable) {}
        engine = null
    }

    private suspend fun handleJsonRpc(body: String): String {
        val req = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: return jsonrpcError(null, -32700, "bad-json")
        val method = (req["method"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
        val id = (req["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        return when (method) {
            "initialize" -> jsonrpcOk(id, buildJsonObject {
                put("protocolVersion", kotlinx.serialization.json.JsonPrimitive(MCP_PROTOCOL_VERSION))
                put("serverInfo", buildJsonObject {
                    put("name", kotlinx.serialization.json.JsonPrimitive("TaiXu Browser MCP Server"))
                    put("version", kotlinx.serialization.json.JsonPrimitive("0.1.0"))
                })
                put("capabilities", buildJsonObject {
                    put("tools", kotlinx.serialization.json.JsonObject(emptyMap()))
                    put("resources", kotlinx.serialization.json.JsonObject(emptyMap()))
                })
            })
            "tools/list" -> jsonrpcOk(id, buildJsonObject {
                put("tools", kotlinx.serialization.json.JsonArray(toolDispatcher.listTools()))
            })
            "tools/call" -> {
                val params = req["params"] as? JsonObject
                val name = (params?.get("name") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                val args = (params?.get("arguments") as? JsonObject) ?: JsonObject(emptyMap())
                jsonrpcOk(id, toolDispatcher.dispatch(name, args))
            }
            "resources/list" -> jsonrpcOk(id, buildJsonObject {
                put("resources", kotlinx.serialization.json.JsonArray(resourceDispatcher.listResources()))
            })
            "resources/read" -> {
                val params = req["params"] as? JsonObject
                val uri = (params?.get("uri") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                val resource = resourceDispatcher.readResource(uri)
                    ?: return jsonrpcError(id, -32004, "not_found")
                jsonrpcOk(id, buildJsonObject {
                    put("contents", kotlinx.serialization.json.JsonArray(listOf(resource)))
                })
            }
            "ping" -> jsonrpcOk(id, kotlinx.serialization.json.JsonObject(emptyMap()))
            else -> jsonrpcError(id, -32601, "Method not found: " + method)
        }
    }

    private fun writeStatsJson(): String = buildJsonObject {
        put("running", kotlinx.serialization.json.JsonPrimitive(isRunning))
        put("bind", kotlinx.serialization.json.JsonPrimitive(host))
        put("allowRemote", kotlinx.serialization.json.JsonPrimitive(currentAllowRemote))
        put("tools", kotlinx.serialization.json.JsonPrimitive(toolDispatcher.listTools().size))
        put("port", kotlinx.serialization.json.JsonPrimitive(port))
    }.toString()

    private fun jsonrpcOk(id: String?, result: JsonElement): String = buildJsonObject {
        put("jsonrpc", kotlinx.serialization.json.JsonPrimitive("2.0"))
        id?.let { put("id", kotlinx.serialization.json.JsonPrimitive(it)) }
        put("result", result)
    }.toString()

    private fun jsonrpcError(id: String?, code: Int, message: String): String = buildJsonObject {
        put("jsonrpc", kotlinx.serialization.json.JsonPrimitive("2.0"))
        id?.let { put("id", kotlinx.serialization.json.JsonPrimitive(it)) }
        put("error", buildJsonObject {
            put("code", kotlinx.serialization.json.JsonPrimitive(code))
            put("message", kotlinx.serialization.json.JsonPrimitive(message))
        })
    }.toString()

    companion object {
        private const val TAG = "TaiXuMcpServer"
        const val defaultPort = 8787
        const val loopbackHost = "127.0.0.1"
    }
}

