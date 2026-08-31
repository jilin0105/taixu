package top.wkbin.taixu.harness.mcp

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import top.wkbin.taixu.core.model.McpServerConfig
import top.wkbin.taixu.core.model.McpToolInfo
import top.wkbin.taixu.core.model.RuntimeState
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.shell.LinuxSession
import top.wkbin.taixu.runtime.shell.SessionConfig
import kotlin.time.Duration.Companion.milliseconds

/** Reusable newline-framed JSON-RPC sessions for stateful STDIO MCP servers. */
@Singleton
class McpStdioTransport @Inject constructor(
    private val linuxRuntime: LinuxRuntime,
    private val json: Json,
) : McpTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connections = ConcurrentHashMap<String, Connection>()
    private val startupMutexes = ConcurrentHashMap<String, Mutex>()

    /** 连接失败冷却：服务 id → 冷却截止时间戳。避免每轮对话重复空耗沙箱启动超时。 */
    private val downUntil = ConcurrentHashMap<String, Long>()

    init {
        // 空闲回收：常驻 STDIO 进程（尤其 node/python 系）长期不释放会累积内存与 ptrace 负担。
        // 每分钟清扫一次，超过 IDLE_TIMEOUT 未活动的连接被关闭，下次调用按需重建。
        scope.launch {
            while (isActive) {
                delay(SWEEP_INTERVAL_MS.milliseconds)
                runCatching { sweepIdleOnce(System.currentTimeMillis()) }
            }
        }
    }

    override suspend fun check(server: McpServerConfig): Boolean = try {
        // 设置页手动检测连接不受冷却限制
        connection(server, bypassCooldown = true).withInitialized { true }
    } catch (cancellation: CancellationException) {
        discardConnection(server.id)
        throw cancellation
    } catch (_: Throwable) {
        discardConnection(server.id)
        false
    }

    override suspend fun discover(server: McpServerConfig): List<McpToolInfo> = try {
        connection(server).withInitialized {
            val response = request("tools/list", JsonObject(emptyMap()))
            val result = response.result?.let { json.decodeFromJsonElement(McpToolsListResponse.serializer(), it) }
                ?: error("MCP tools/list did not return a result")
            result.tools.map { dto -> dto.toInfo(server, json.encodeToString(JsonObject.serializer(), dto.inputSchema)) }
        }
    } catch (throwable: Throwable) {
        // 超时、取消或协议损坏后不能复用半初始化连接，否则下一次请求会继续消费旧响应。
        discardConnection(server.id)
        throw throwable
    }

    override suspend fun execute(server: McpServerConfig, toolName: String, arguments: JsonObject): Pair<Boolean, String> =
        try {
            connection(server).withInitialized {
                val params = json.encodeToJsonElement(McpCallToolParams.serializer(), McpCallToolParams(toolName, arguments))
                val response = request("tools/call", params)
                val result = response.result?.let { json.decodeFromJsonElement(McpCallToolResult.serializer(), it) }
                    ?: error("MCP tools/call did not return a result")
                !result.isError to result.content.joinToString("\n") { it.text.orEmpty() }
                    .ifBlank { if (result.isError) "执行失败" else "执行成功" }
            }
        } catch (throwable: Throwable) {
            discardConnection(server.id)
            throw throwable
        }

    /** 服务被禁用/删除或请求失败时立即释放常驻进程，不等待十分钟空闲回收。 */
    suspend fun closeConnection(serverId: String) {
        downUntil.remove(serverId)
        discardConnection(serverId)
    }

    private suspend fun discardConnection(serverId: String) {
        // 超时会先取消当前协程；清理必须在 NonCancellable 中完成，否则旧 PTY/响应队列仍会被复用。
        withContext(NonCancellable + Dispatchers.IO) {
            connections.remove(serverId)?.close()
        }
    }

    /**
     * 关闭所有空闲超时的连接；返回关闭数量。可见性放宽给单元测试直接驱动。
     * 正在执行请求的连接直接跳过：tools/call（如 codegraph 全量同步）可合法运行
     * 数分钟，不能按 lastActivityMs 误回收。
     */
    internal suspend fun sweepIdleOnce(nowMs: Long): Int {
        var closed = 0
        val entries = connections.entries.toList()
        for ((id, connection) in entries) {
            if (connection.inFlight) continue
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

    private suspend fun connection(server: McpServerConfig, bypassCooldown: Boolean = false): Connection =
        startupMutexes.getOrPut(server.id) { Mutex() }.withLock {
            connectionLocked(server, bypassCooldown)
        }

    private suspend fun connectionLocked(server: McpServerConfig, bypassCooldown: Boolean): Connection {
        connections[server.id]?.takeIf { it.session.isAlive && it.fingerprint == fingerprint(server) }?.let {
            it.markActive()
            return it
        }
        // MCP 单例可能早于 Onboarding 的运行时恢复创建。预热时沙箱未就绪不代表服务故障，
        // 不能因此写入三分钟冷却；运行时 Ready 后由下一次探测正常重试。
        if (linuxRuntime.state.value !is RuntimeState.Ready) {
            error("Linux 沙箱尚未就绪，暂不启动 MCP[${server.name}]")
        }
        val now = System.currentTimeMillis()
        if (!bypassCooldown) {
            val until = downUntil[server.id]
            if (until != null && now < until) {
                throw IllegalStateException(
                    "MCP[${server.name}] 沙箱会话拉起冷却中（剩余 ${(until - now) / 1000}s），跳过本次连接；" +
                        "请确认沙箱环境就绪后再在设置页手动测试",
                )
            }
        }
        connections.remove(server.id)?.close()
        // startSession 本身无内部超时，沙箱忙或 PRoot 异常时会无限挂起且不留任何日志；
        // 这里必须有界，让挂起在 10s 内转化为可被上层记录的失败。
        val session = try {
            withTimeoutOrNull(STARTUP_TIMEOUT_MS.milliseconds) {
                linuxRuntime.startSession(
                    SessionConfig(
                        workingDirectory = "/root",
                        environment = server.env,
                        commandLine = commandLine(server),
                        allowSttyResize = false,
                    ),
                )
            } ?: run {
                downUntil[server.id] = System.currentTimeMillis() + FAILURE_COOLDOWN_MS
                error("MCP 沙箱会话启动超时（${STARTUP_TIMEOUT_MS / 1000}s）：${server.command}")
            }
        } catch (t: Throwable) {
            downUntil[server.id] = System.currentTimeMillis() + FAILURE_COOLDOWN_MS
            throw t
        }
        downUntil.remove(server.id)
        return Connection(session, fingerprint(server)).also { connection ->
            connections[server.id] = connection
            connection.startReader()
        }
    }

    private inner class Connection(val session: LinuxSession, val fingerprint: String) {
        private val mutex = Mutex()

        /** 是否有请求正在执行（供空闲清扫跳过，避免误杀长调用）。 */
        val inFlight: Boolean get() = mutex.isLocked
        private val lines = Channel<String>(capacity = MAX_BUFFERED_LINES)
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
                try {
                    session.output.collect { output ->
                        val chunk = output.text
                        var start = 0
                        while (start < chunk.length) {
                            val newline = chunk.indexOf('\n', start)
                            val end = if (newline >= 0) newline else chunk.length
                            val partLength = end - start
                            require(buffer.length + partLength <= MAX_FRAME_CHARS) { "MCP STDIO frame is too large" }
                            buffer.append(chunk, start, end)
                            if (newline < 0) break
                            val line = buffer.toString().trim()
                            buffer.clear()
                            if (line.startsWith("{")) lines.send(line)
                            start = newline + 1
                        }
                    }
                } catch (t: Throwable) {
                    lines.close(t)
                    runCatching { session.close() }
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

        suspend fun request(method: String, params: kotlinx.serialization.json.JsonElement) =
            requestUnlocked(method, params, CALL_REQUEST_TIMEOUT_MS)

        private suspend fun requestUnlocked(
            method: String,
            params: kotlinx.serialization.json.JsonElement,
            timeoutMs: Long = REQUEST_TIMEOUT_MS,
        ): JsonRpcResponse {
            markActive()
            val id = UUID.randomUUID().toString()
            write(json.encodeToString(JsonRpcRequest.serializer(), JsonRpcRequest(id = id, method = method, params = params)))
            return withTimeout(timeoutMs.milliseconds) {
                var ignoredFrames = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val rawLine = lines.receive()
                    val element = withContext(Dispatchers.Default) {
                        runCatching { json.parseToJsonElement(rawLine) }.getOrNull()
                    }
                    if (element == null) {
                        ignoredFrames++
                        require(ignoredFrames <= MAX_IGNORED_FRAMES) { "MCP 输出了过多无效 JSON 行" }
                        continue
                    }
                    // PTY 会话的行规则可能回显请求原文、或透出服务器日志；带 method 字段的
                    // 行是请求/通知，不可能是响应，跳过以避免同 id 无 result 的误匹配。
                    if (element is JsonObject && element.containsKey("method")) {
                        ignoredFrames++
                        require(ignoredFrames <= MAX_IGNORED_FRAMES) { "MCP 输出了过多请求回显或通知" }
                        continue
                    }
                    val parsed = runCatching { json.decodeFromJsonElement(JsonRpcResponse.serializer(), element) }.getOrNull()
                    if (parsed?.id == id) {
                        parsed.error?.let { error("MCP JSON-RPC ${it.code}: ${it.message}") }
                        return@withTimeout parsed
                    }
                    ignoredFrames++
                    require(ignoredFrames <= MAX_IGNORED_FRAMES) { "MCP 未返回当前请求的响应（id=$id）" }
                }
                @Suppress("UNREACHABLE_CODE") error("unreachable")
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
        val argv = (listOf(server.command) + server.args).joinToString(" ", transform = ::shellQuote)
        // 会话建立在 PTY 上而非管道：默认行规则会回显写入的 JSON-RPC 请求（同 id、无 result，
        // 会被 reader 误判为响应导致 "MCP initialize did not return a result"）、把 \n 改写为
        // \r\n，且 canonical 模式单行上限 4096 字节会截断大参数请求。raw -echo 恢复管道语义，
        // 再用 exec 让服务器进程直接接管该 PTY。
        return "stty raw -echo; exec $argv"
    }

    private fun fingerprint(server: McpServerConfig) = "${server.command}|${server.args}|${server.env}"
    private fun shellQuote(value: String) = "'${value.replace("'", "'\"'\"'")}'"

    companion object {
        /** 沙箱会话拉起超时：正常秒级完成，超时说明沙箱侧挂起。 */
        internal const val STARTUP_TIMEOUT_MS = 3_500L

        /** 启动失败冷却时间：期间快速失败，避免每轮对话重复空耗超时。 */
        private const val FAILURE_COOLDOWN_MS = 3 * 60 * 1000L

        /** 连接/列表等轻量请求超时。 */
        private const val REQUEST_TIMEOUT_MS = 120_000L

        /** tools/call 超时：codegraph_sync 等全量索引在手机沙箱上可合法运行数分钟。 */
        private const val CALL_REQUEST_TIMEOUT_MS = 600_000L
        private const val MAX_BUFFERED_LINES = 64
        private const val MAX_FRAME_CHARS = 1 * 1024 * 1024
        private const val MAX_IGNORED_FRAMES = 256

        /** 空闲连接回收阈值与清扫周期。 */
        internal const val IDLE_TIMEOUT_MS = 10 * 60 * 1000L
        internal const val SWEEP_INTERVAL_MS = 60 * 1000L
    }
}
