package top.wkbin.taixu.harness

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.HarnessMessageDao
import top.wkbin.taixu.core.database.HarnessMessageEntity
import top.wkbin.taixu.core.database.HarnessSessionDao
import top.wkbin.taixu.core.database.HarnessSessionEntity
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

import top.wkbin.taixu.core.datastore.SettingsDataStore
import top.wkbin.taixu.core.model.AgentSkill
import kotlinx.coroutines.flow.first

/**
 * Harness 主循环：用户消息 → LLM（tool-calling）→ 执行工具 → 结果回传 → 循环，
 * 直到模型给出最终回复。会话与消息通过 Room 持久化，UI 观察 [messages]。
 *
 * 第一版单会话：sessionId 由 [startSession] 指定（新会话或恢复旧会话）。
 */
@Singleton
class HarnessLoop @Inject constructor(
    @ApplicationContext private val context: Context,
    private val providerClient: ProviderClient,
    private val toolExecutor: ToolExecutor,
    private val messageDao: HarnessMessageDao,
    private val sessionDao: HarnessSessionDao,
    private val settingsDataStore: SettingsDataStore,
    private val fileAccess: WorkspaceFileAccess,
    private val json: Json,
    private val logger: AppLogger,
) {
    private val loopScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sessionId: String = ""
    private var loopJob: Job? = null

    private val _messages = MutableStateFlow<List<HarnessMessage>>(emptyList())
    val messages: StateFlow<List<HarnessMessage>> = _messages.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _workspace = MutableStateFlow("")
    /** 当前会话关联的工作区 Linux 路径（"" = 未关联）。 */
    val workspace: StateFlow<String> = _workspace.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    /** 当前执行状态（供 UI / 后台通知显示进度）。运行结束或出错时置空。 */
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _thinkingLive = MutableStateFlow(false)
    /** 推理模型思考中（reasoning 正在流式上屏）。开始思考置 true，本回合结束时置 false。 */
    val thinkingLive: StateFlow<Boolean> = _thinkingLive.asStateFlow()

    private val _pendingMessages = MutableStateFlow<List<String>>(emptyList())
    /**
     * 运行中排队等待发送的用户消息。当前任务结束后自动按序接续执行；
     * 用户点"停止"时清空。UI 可观察此列表展示排队状态。
     */
    val pendingMessages: StateFlow<List<String>> = _pendingMessages.asStateFlow()

    /**
     * 当前模型是否处于思考模式（任一轮响应携带过 reasoning_content）。
     * DeepSeek 系 thinking 模式强制要求：请求历史中每条带 tool_calls 的 assistant
     * 消息都必须回传 reasoning_content 字段，缺失（哪怕是模型某轮没思考）会返回
     * HTTP 400 "The reasoning_content in the thinking mode must be passed back to the API"。
     * 因此检测到思考模式后，缺失的轮次以空字符串兜底；非思考模型则完全不下发该字段。
     */
    private var thinkingMode = false

    /** 新建会话。workspace 为关联的工作区 Linux 路径（如 /workspace/proj），空串表示不关联。 */
    suspend fun newSession(title: String, workspace: String = ""): String {
        val id = UUID.randomUUID().toString()
        sessionId = id
        _workspace.value = workspace
        sessionDao.upsert(
            HarnessSessionEntity(
                id = id,
                title = title.ifBlank { "新会话" },
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                modelId = null,
                workspace = workspace,
            ),
        )
        _messages.value = emptyList()
        _error.value = null
        _thinkingLive.value = false
        _pendingMessages.value = emptyList()
        return id
    }

    /** 恢复已有会话的历史消息与工作区关联。 */
    suspend fun loadSession(id: String) {
        sessionId = id
        _workspace.value = sessionDao.findById(id)?.workspace.orEmpty()
        val history = messageDao.listForSession(id)
            .mapNotNull { entity ->
                runCatching {
                    json.decodeFromString(HarnessMessage.serializer(), entity.payloadJson)
                }.getOrNull()
            }
        _messages.value = history
        _error.value = null
        _thinkingLive.value = false
        _pendingMessages.value = emptyList()
        // 从历史推断思考模式：旧会话（含升级前持久化的消息）只要出现过 reasoning 即视为思考模型，
        // 后续请求对缺失 reasoning 的轮次以空字符串兜底，避免 DeepSeek thinking 模式 400。
        thinkingMode = history.any { message ->
            (message as? AssistantText)?.reasoning != null || (message as? ToolCall)?.reasoning != null
        }
    }

    suspend fun renameSession(id: String, title: String) {
        sessionDao.rename(id, title, System.currentTimeMillis())
    }

    suspend fun deleteSession(id: String) {
        sessionDao.deleteMessages(id)
        sessionDao.deleteSession(id)
        if (sessionId == id) {
            val remaining = sessionDao.observeAll().first()
            val nextSession = remaining.firstOrNull { it.id != id }
            if (nextSession != null) {
                loadSession(nextSession.id)
            } else {
                newSession("新会话")
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || sessionId.isBlank()) return
        // 运行中：进入排队，当前任务结束后自动接续（见 finally 中的 drainPendingQueue）
        if (_running.value) {
            _pendingMessages.value = _pendingMessages.value + trimmed
            return
        }
        loopJob?.cancel()
        loopJob = loopScope.launch {
            _running.value = true
            _error.value = null
            try {
                runLoop(trimmed)
            } catch (cancellation: CancellationException) {
                // 用户主动停止：必须给所有还没等到结果的 ToolCall 补一条 ToolResult 落库。
                // 否则历史里存在"悬空"的 tool_calls（后面没有对应 tool 消息），
                // OpenAI 兼容 API 会在下一次请求直接返回 HTTP 400
                // "assistant message with 'tool_calls' must be followed by tool messages..."。
                // 取消中的协程连挂起调用都会抛 CancellationException，所以整个修复必须在 NonCancellable 里完成。
                withContext(NonCancellable) {
                    repairDanglingToolCalls(interrupted = true)
                }
                logger.i("Harness loop cancelled")
            } catch (throwable: Throwable) {
                logger.e("Harness loop failed", throwable)
                _error.value = throwable.message ?: "执行失败"
            } finally {
                finishRun()
            }
        }
    }

    /**
     * 重新生成最后一次回复：
     * 1. 找到最后一条 UserMessage；
     * 2. 移除其之后的所有消息（Room + 内存）；
     * 3. 重新执行 LLM 生成循环。
     */
    fun regenerateLast() {
        if (_running.value || sessionId.isBlank()) return
        val current = _messages.value
        val lastUserIndex = current.indexOfLast { it is UserMessage }
        if (lastUserIndex < 0) return
        val lastUserMessage = current[lastUserIndex] as UserMessage

        loopJob?.cancel()
        loopJob = loopScope.launch {
            _running.value = true
            _error.value = null
            try {
                val toKeep = current.subList(0, lastUserIndex + 1)
                _messages.value = toKeep
                messageDao.deleteFromTimestamp(sessionId, lastUserMessage.createdAt + 1)
                runLoopInternal(startedAt = now())
            } catch (cancellation: CancellationException) {
                withContext(NonCancellable) {
                    repairDanglingToolCalls(interrupted = true)
                }
                logger.i("Harness loop cancelled")
            } catch (throwable: Throwable) {
                logger.e("Harness loop failed", throwable)
                _error.value = throwable.message ?: "执行失败"
            } finally {
                finishRun()
            }
        }
    }

    /**
     * 编辑并重发指定用户消息：
     * 1. 找到目标 UserMessage；
     * 2. 删除该 UserMessage 及其之后的所有消息（Room + 内存）；
     * 3. 将 newText 作为新的 UserMessage 发送并执行循环。
     */
    fun truncateAndResend(userMessageId: String, newText: String) {
        val trimmed = newText.trim()
        if (trimmed.isEmpty() || _running.value || sessionId.isBlank()) return
        val current = _messages.value
        val targetIndex = current.indexOfFirst { it.id == userMessageId }
        if (targetIndex < 0) {
            send(trimmed)
            return
        }
        val targetMessage = current[targetIndex]

        loopJob?.cancel()
        loopJob = loopScope.launch {
            _running.value = true
            _error.value = null
            try {
                val toKeep = current.subList(0, targetIndex)
                _messages.value = toKeep
                messageDao.deleteFromTimestamp(sessionId, targetMessage.createdAt)
                runLoop(trimmed)
            } catch (cancellation: CancellationException) {
                withContext(NonCancellable) {
                    repairDanglingToolCalls(interrupted = true)
                }
                logger.i("Harness loop cancelled")
            } catch (throwable: Throwable) {
                logger.e("Harness loop failed", throwable)
                _error.value = throwable.message ?: "执行失败"
            } finally {
                finishRun()
            }
        }
    }

    /**
     * 删除单条消息：
     * 如果是 ToolCall，连带删除其对应的 ToolResult；
     * 如果是 ToolResult，连带删除对应的 ToolCall。
     */
    suspend fun deleteMessage(messageId: String) {
        if (_running.value || sessionId.isBlank()) return
        val current = _messages.value
        val target = current.firstOrNull { it.id == messageId } ?: return
        val idsToDelete = mutableListOf(messageId)
        if (target is ToolCall) {
            current.filterIsInstance<ToolResult>().filter { it.toolCallId == target.id }.forEach {
                idsToDelete.add(it.id)
            }
        } else if (target is ToolResult) {
            current.filterIsInstance<ToolCall>().filter { it.id == target.toolCallId }.forEach {
                idsToDelete.add(it.id)
            }
        }
        _messages.value = current.filter { it.id !in idsToDelete }
        messageDao.deleteByIds(idsToDelete)
    }

    /**
     * 为所有尚无 ToolResult 的 ToolCall 补写一条中断占位结果并持久化。
     * 修复两类来源的悬空 tool_calls：①本轮执行中被用户取消；②历史遗留的损坏会话。
     */
    private suspend fun repairDanglingToolCalls(interrupted: Boolean) {
        val msgs = _messages.value
        val answered = msgs.filterIsInstance<ToolResult>().mapTo(mutableSetOf()) { it.toolCallId }
        val dangling = msgs.filterIsInstance<ToolCall>().filter { it.id !in answered }
        if (dangling.isEmpty()) return
        val note = if (interrupted) "用户停止了本次执行，工具被中断。" else "工具结果缺失（历史中断），已补占位结果以继续会话。"
        dangling.forEach { call ->
            val result = ToolResult(
                id = newId(),
                createdAt = now(),
                toolCallId = call.id,
                success = false,
                output = note,
            )
            _messages.value = _messages.value + result
            messageDao.insert(
                HarnessMessageEntity(
                    id = result.id,
                    sessionId = sessionId,
                    createdAt = result.createdAt,
                    type = result::class.simpleName.orEmpty(),
                    payloadJson = json.encodeToString(HarnessMessage.serializer(), result),
                ),
            )
        }
    }

    fun cancel() {
        // 停止意味着终止一切：连同排队的后续消息一起清空。
        _pendingMessages.value = emptyList()
        _status.value = "正在停止…"
        loopJob?.cancel()
        loopJob = null
    }

    /** 移除一条排队中的消息（下标从 0 开始）。 */
    fun removePendingMessage(index: Int) {
        val current = _pendingMessages.value
        if (index in current.indices) {
            _pendingMessages.value = current.filterIndexed { i, _ -> i != index }
        }
    }

    /** 清空全部排队消息。 */
    fun clearPendingMessages() {
        _pendingMessages.value = emptyList()
    }

    /**
     * 单次运行的统一收尾：复位状态位；若队列中还有排队消息则自动接续下一条。
     * 在 loopJob 的 finally 中调用（运行协程本身即将退出，接续任务由 send() 重新起协程）。
     */
    private fun finishRun() {
        _running.value = false
        _status.value = null
        _thinkingLive.value = false
        loopJob = null
        val next = _pendingMessages.value.firstOrNull()
        if (next != null) {
            _pendingMessages.value = _pendingMessages.value.drop(1)
            send(next)
        }
    }

    fun clearError() {
        _error.value = null
    }

    private suspend fun runLoop(userText: String) {
        logAgentEvent("UserPrompt", userText)
        append(UserMessage(id = newId(), createdAt = now(), text = userText))
        runLoopInternal(startedAt = now())
    }

    private suspend fun runLoopInternal(startedAt: Long) {
        // 历史遗留的悬空 tool_calls（旧版本取消 bug 等造成）先自愈补齐，避免下一次请求 400
        repairDanglingToolCalls(interrupted = false)
        val maxRounds = runCatching { settingsDataStore.maxToolRounds.first() }.getOrDefault(MAX_ROUNDS)
        val autoCwd = runCatching { settingsDataStore.autoWorkspaceCwd.first() }.getOrDefault(true)
        var round = 0
        while (round < maxRounds) {
            // 中途转向（pi 的 steering）：运行期间用户排队的消息在下一轮推理前注入，
            // 让用户能实时纠偏正在执行的任务，而不是等整个任务跑完才被看到。
            drainSteeringMessages()
            _status.value = "思考中"
            val model = try {
                // 含最小配置校验：未配置模型/API Key 时直接返回明确错误，消息留在聊天里
                providerClient.resolveConfigured()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                logAgentEvent("ModelResolveError", "无法获取模型配置", throwable)
                appendFatal("无法获取模型配置：${friendly(throwable)}", now() - startedAt)
                return
            }
            logAgentEvent("ModelRequest", "Round=$round, Model=${model.name}, Provider=${model.provider}")
            val assistantId = newId()
            val assistantAt = now()
            val streamText = StringBuilder()
            // 推理增量单独累积：即使中途出错被 catch，也能把已收到的 reasoning 落库回传。
            val streamReasoning = StringBuilder()
            // 流式：每个内容增量立即更新 _messages（UI 实时显示），结束时持久化。
            // 网络类瞬时故障（IOException，如移动网络切换导致的 "software caused connection abort"）
            // 自动退避重试整个请求；HTTP 业务错误（IllegalStateException）不重试，走原有中断路径。
            var streamed: ChatResult? = null
            var netRetry = 0
            while (streamed == null) {
                try {
                    streamed = providerClient.chatStream(
                        model,
                        apiMessages(),
                        onReasoning = { chunk ->
                            streamReasoning.append(chunk)
                            thinkingMode = true
                            _thinkingLive.value = true
                            streamAssistantReasoning(assistantId, assistantAt, streamReasoning.toString())
                        },
                    ) { chunk ->
                        _status.value = "回复中"
                        streamText.append(chunk)
                        streamAssistant(assistantId, assistantAt, streamText.toString())
                    }
                } catch (cancellation: CancellationException) {
                    logAgentEvent("Cancelled", "用户主动取消执行")
                    throw cancellation
                } catch (io: IOException) {
                    // 取消（call.cancel() 关闭 socket）也会抛 IOException：先检查协程是否已被取消，
                    // 已取消则直接走取消路径，绝不能当成网络错误重试。
                    currentCoroutineContext().ensureActive()
                    netRetry++
                    logAgentEvent("NetworkRetry", "网络中断重试 $netRetry/$MAX_STREAM_RETRIES: ${io.message}", io)
                    if (netRetry > MAX_STREAM_RETRIES) throw io
                    _thinkingLive.value = false
                    _status.value = "网络中断，重试中（$netRetry/$MAX_STREAM_RETRIES）"
                    // 清空半段输出：重试成功后从头重新流式，同一 assistantId 原位覆盖 UI，不会重复
                    streamText.clear()
                    streamReasoning.clear()
                    streamAssistant(assistantId, assistantAt, "")
                    delay(netRetry * RETRY_BACKOFF_MS)
                } catch (throwable: Throwable) {
                    // LLM 调用失败：已流式的半段内容先落库（含已收到的 reasoning），再给出收尾消息，避免“没结论地中断”。
                    _thinkingLive.value = false
                    logAgentEvent("ModelError", "LLM 调用失败: ${throwable.message}", throwable)
                    if (streamText.isNotEmpty()) {
                        persistAssistant(
                            assistantId,
                            assistantAt,
                            streamText.toString(),
                            streamReasoning.toString().ifBlank { null },
                            totalMs = now() - startedAt,
                        )
                    } else {
                        appendFatal("执行遇到问题，已中断：${friendly(throwable)}", now() - startedAt)
                    }
                    return
                }
            }
            val result = streamed
            logAgentEvent(
                "ModelResponse",
                "TextLength=${streamText.length}, ReasoningLength=${result.reasoningContent?.length ?: 0}, ToolCallsCount=${result.toolCalls.size}",
            )
            if (streamText.isNotEmpty()) {
                persistAssistant(
                    assistantId,
                    assistantAt,
                    streamText.toString(),
                    result.reasoningContent,
                    // 本轮的最终回复（无后续工具调用）时记录总耗时；中间轮次不记
                    totalMs = if (!result.hasToolCalls) now() - startedAt else null,
                )
            }
            _thinkingLive.value = false
            if (!result.hasToolCalls) return
            result.toolCalls.forEach { spec ->
                // 参数必须是合法 JSON 对象。流式截断、模型笔误都可能产生解析失败的参数——
                // 静默降级成空参数执行会拿到错误结果且模型不自知（pi 的做法是直接报错让模型重发）。
                val parsedArgs = try {
                    json.parseToJsonElement(spec.argumentsJson) as? kotlinx.serialization.json.JsonObject
                        ?: throw IllegalArgumentException("参数不是 JSON 对象")
                } catch (parseError: Throwable) {
                    // 仍需落一条 ToolCall 保证历史配对合法（tool 消息必须跟在 tool_calls 之后）
                    append(
                        ToolCall(
                            id = spec.id,
                            createdAt = now(),
                            tool = HarnessApiMapper.toolByName(spec.name),
                            args = buildJsonObject {},
                            reasoning = result.reasoningContent,
                        ),
                    )
                    append(
                        ToolResult(
                            id = newId(),
                            createdAt = now(),
                            toolCallId = spec.id,
                            success = false,
                            output = "工具参数 JSON 解析失败（${friendly(parseError)}），参数可能被截断。" +
                                "请重新发起完整的工具调用，参数必须是合法的 JSON 对象。",
                        ),
                    )
                    return@forEach
                }
                // 未知工具名：直接回错误结果。绝不归入 base 盲执行——
                // 那会让模型以为命令真的跑过，基于幻觉结论继续推理。
                if (spec.name.trim().lowercase() !in KNOWN_TOOL_NAMES) {
                    append(
                        ToolCall(
                            id = spec.id,
                            createdAt = now(),
                            tool = HarnessApiMapper.toolByName(spec.name),
                            args = buildJsonObject {},
                            reasoning = result.reasoningContent,
                        ),
                    )
                    append(
                        ToolResult(
                            id = newId(),
                            createdAt = now(),
                            toolCallId = spec.id,
                            success = false,
                            output = "未知工具：${spec.name}。可用工具只有 read / write / edit / base。",
                        ),
                    )
                    return@forEach
                }
                val tool = HarnessApiMapper.toolByName(spec.name)
                var args = parsedArgs
                // base 工具默认在会话关联的工作区执行（cwd 未指定时，且用户启用了自动注入）
                if (tool == HarnessTool.BASE && autoCwd && _workspace.value.isNotBlank() && args["cwd"] == null) {
                    args = buildJsonObject {
                        put("cwd", _workspace.value)
                        args.forEach { (key, value) -> put(key, value) }
                    }
                }
                val toolCall = ToolCall(
                    id = newId(),
                    createdAt = now(),
                    tool = tool,
                    args = args,
                    reasoning = result.reasoningContent,
                )
                logAgentEvent("ToolCall", "Tool=${tool.name}, Args=$args")
                append(toolCall)
                _status.value = describeToolCall(tool, args)
                val toolStart = now()
                val outcome = try {
                    toolExecutor.execute(toolCall)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    ToolResult(
                        id = newId(),
                        createdAt = now(),
                        toolCallId = toolCall.id,
                        success = false,
                        output = "工具执行异常：${friendly(throwable)}",
                    )
                }
                val duration = now() - toolStart
                logAgentEvent("ToolResult", "Tool=${tool.name}, Success=${outcome.success}, Duration=${duration}ms, Output=${outcome.output.take(300)}")
                append(outcome.copy(durationMs = duration))
                touchSession()
            }
            round++
        }
        append(
            AssistantText(
                id = newId(),
                createdAt = now(),
                text = "已达到最大工具轮数（$maxRounds），请简化任务或分步进行。",
                totalMs = now() - startedAt,
            ),
        )
    }

    /**
     * 生成人话版工具执行状态（通知栏标题 / UI 状态条共用）：
     * 带上命令首行或文件路径，让用户一眼看到 Agent 当前在做什么。
     */
    private fun describeToolCall(tool: HarnessTool, args: JsonObject): String {
        fun arg(name: String): String? =
            runCatching { args[name]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }
        return when (tool) {
            HarnessTool.BASE -> {
                val command = arg("command")?.lineSequence()?.first()?.trim().orEmpty()
                if (command.isEmpty()) "执行命令" else "执行命令：${command.take(MAX_STATUS_ARG_LENGTH)}"
            }
            HarnessTool.READ -> arg("path")?.let { "读取文件：${it.takeLast(MAX_STATUS_ARG_LENGTH)}" } ?: "读取文件"
            HarnessTool.WRITE -> arg("path")?.let { "写入文件：${it.takeLast(MAX_STATUS_ARG_LENGTH)}" } ?: "写入文件"
            HarnessTool.EDIT -> arg("path")?.let { "编辑文件：${it.takeLast(MAX_STATUS_ARG_LENGTH)}" } ?: "编辑文件"
        }
    }

    private suspend fun logAgentEvent(tag: String, message: String, throwable: Throwable? = null) {
        val enabled = runCatching { settingsDataStore.agentLoggingEnabled.first() }.getOrDefault(false)
        if (enabled) {
            logger.logAgent(sessionId, tag, message, throwable)
        }
    }

    /** 把运行期间排队的用户消息注入为 UserMessage，模型下一轮即可看到并调整方向。 */
    private suspend fun drainSteeringMessages() {
        val queued = _pendingMessages.value
        if (queued.isEmpty()) return
        _pendingMessages.value = emptyList()
        queued.forEach { text ->
            logAgentEvent("SteeringMessage", text)
            append(UserMessage(id = newId(), createdAt = now(), text = text))
        }
    }


    /** 追加一条“出错了”的收尾消息，保证运行必然有结论。附带本轮总耗时。 */
    private suspend fun appendFatal(text: String, totalMs: Long? = null) {
        append(AssistantText(id = newId(), createdAt = now(), text = text, totalMs = totalMs))
    }

    private fun friendly(throwable: Throwable): String =
        throwable.message?.take(200) ?: throwable::class.simpleName.orEmpty()

    /** 动态组装系统提示词（含已启用的专精 Skill 库指导） */
    private suspend fun buildSystemPrompt(): String {
        val distroId = runCatching { settingsDataStore.selectedDistribution.first() }.getOrDefault("debian")
        val distroName = when (distroId.lowercase()) {
            "ubuntu" -> "Ubuntu 24.04 (Noble Numbat)"
            "debian" -> "Debian 12 (Bookworm)"
            "alpine" -> "Alpine Linux 3.19"
            "archlinux", "arch" -> "Arch Linux"
            "fedora" -> "Fedora 40"
            "void" -> "Void Linux"
            else -> "$distroId Linux"
        }
        val pkgManager = when (distroId.lowercase()) {
            "alpine" -> "apk add <package>"
            "archlinux", "arch" -> "pacman -S <package>"
            "fedora" -> "dnf install -y <package>"
            "void" -> "xbps-install -S -y <package>"
            else -> "apt-get install -y <package>"
        }
        val template = runCatching {
            context.assets.open("prompts/agent_system.md").bufferedReader().use { it.readText() }
        }.getOrDefault(FALLBACK_SYSTEM_PROMPT)

        val activeSkills = runCatching { settingsDataStore.activeSkills.first() }.getOrDefault(emptyList())
        val skillSection = if (activeSkills.isNotEmpty()) {
            "## 当前已启用的专精技能指导规则 (Active Skills)\n\n" + activeSkills.joinToString("\n\n") { skill ->
                """
                ### [专精技能] ${skill.name} (${skill.category})
                ${skill.systemPrompt.trim()}
                """.trimIndent()
            }
        } else ""

        // 项目上下文（pi 的 project context）：会话绑定工作区时自动加载项目级指令文件，
        // 让 Agent 直接掌握项目约定，而不是每轮靠 ls/read 猜。
        val projectContext = loadProjectContext()

        val workspaceSection = if (_workspace.value.isNotBlank()) {
            "\n\n当前工作区：${_workspace.value}（base 命令默认在此目录执行；read/write/edit 的相对路径以此为根）"
        } else ""

        return template
            .replace("{{DISTRO_NAME}}", distroName)
            .replace("{{PKG_MANAGER}}", pkgManager)
            .replace("{{ACTIVE_SKILLS}}", skillSection)
            .trim() + workspaceSection + projectContext
    }

    /**
     * 从工作区根目录加载项目级指令文件：AGENTS.md / CLAUDE.md / README.md。
     * 每个文件截断到 [PROJECT_CONTEXT_MAX_BYTES]，避免巨型 README 挤爆上下文。
     */
    private suspend fun loadProjectContext(): String {
        val workspace = _workspace.value
        if (workspace.isBlank()) return ""
        val sections = buildList {
            for (name in listOf("AGENTS.md", "CLAUDE.md", "README.md")) {
                val content = runCatching {
                    fileAccess.read("$workspace/$name".removePrefix("/")).getOrNull()
                }.getOrNull() ?: continue
                val trimmed = content.take(PROJECT_CONTEXT_MAX_BYTES)
                add(
                    "<project_instructions path=\"$name\">\n$trimmed" +
                        (if (content.length > PROJECT_CONTEXT_MAX_BYTES) "\n…（文件过长已截断）" else "") +
                        "\n</project_instructions>",
                )
            }
        }
        if (sections.isEmpty()) return ""
        return "\n\n<project_context>\n当前工作区的项目说明与约定（自动加载，编码时务必遵守）：\n\n" +
            sections.joinToString("\n\n") + "\n</project_context>"
    }

    /**
     * Harness 消息 → OpenAI 兼容 API 消息（含系统提示与上下文智能压缩）。
     *
     * 当历史轮数超出阈值时，自动对旧轮次的超长 ToolResult 进行安全剪裁，
     * 保持首尾完整性与合法 tool_call_id 配对，极大压缩 Token 占用。
     */
    private suspend fun apiMessages(): List<ApiMessage> {
        val compactionEnabled = runCatching { settingsDataStore.contextCompactionEnabled.first() }.getOrDefault(true)
        val threshold = runCatching { settingsDataStore.contextCompactionThreshold.first() }.getOrDefault(15)
        val systemPrompt = buildSystemPrompt()

        return buildList {
            add(ApiMessage(role = "system", content = systemPrompt))
            val msgs = _messages.value
            // 兜底：没有对应 ToolResult 的 ToolCall 不回传（不应出现，防御性跳过以免 API 400）
            val answeredIds = msgs.filterIsInstance<ToolResult>().mapTo(mutableSetOf()) { it.toolCallId }

            // 计算用户交互轮数（以 UserMessage 数量计）
            val userIndices = msgs.indices.filter { msgs[it] is UserMessage }
            val shouldCompact = compactionEnabled && userIndices.size > threshold

            // 如果触发压缩，保留最近 4 轮完整无损，此前的历史工具输出进行紧凑压缩
            val recentTurnCutoffIndex = if (shouldCompact && userIndices.size > 4) {
                userIndices[userIndices.size - 4]
            } else {
                msgs.size
            }

            var i = 0
            fun apiToolCall(tc: ToolCall) = ApiToolCall(
                id = tc.id,
                function = ApiFunctionCall(name = tc.tool.apiName(), arguments = tc.args.toString()),
            )
            while (i < msgs.size) {
                val message = msgs[i]
                if (message is AssistantText || message is ToolCall) {
                    // 悬空的 ToolCall（无对应结果）整条跳过，避免构造非法请求体
                    if (message is ToolCall && message.id !in answeredIds) {
                        i++
                        continue
                    }
                    // 按轮次合并：assistant 文本 + 该轮全部 tool_calls 合成一条 assistant 消息，
                    // 并把推理模型的 reasoning_content 一并带回（DeepSeek 要求原样回传）。
                    val text = (message as? AssistantText)?.text
                    val reasoning = when (message) {
                        is AssistantText -> message.reasoning
                        is ToolCall -> message.reasoning
                        else -> null
                    }
                    val toolCalls = mutableListOf<ApiToolCall>()
                    if (message is ToolCall) toolCalls.add(apiToolCall(message))
                    var j = i + 1
                    while (j < msgs.size && msgs[j] is ToolCall) {
                        val tc = msgs[j] as ToolCall
                        if (tc.id in answeredIds) toolCalls.add(apiToolCall(tc))
                        j++
                    }
                    add(
                        ApiMessage(
                            role = "assistant",
                            content = text,
                            reasoning_content = reasoning ?: if (thinkingMode) "" else null,
                            tool_calls = toolCalls.takeIf { it.isNotEmpty() },
                        ),
                    )
                    i = j
                } else if (message is ToolResult) {
                    // 上下文压缩：对旧轮次的长工具输出进行精简摘要（保留前后关键行，删除冗长中间日志）
                    val content = if (shouldCompact && i < recentTurnCutoffIndex && message.output.length > 240) {
                        compactToolOutput(message.output, message.success)
                    } else {
                        message.output
                    }
                    add(
                        ApiMessage(
                            role = "tool",
                            content = content,
                            tool_call_id = message.toolCallId,
                        ),
                    )
                    i++
                } else {
                    add(HarnessApiMapper.toApiMessage(message))
                    i++
                }
            }
        }
    }

    /** 压缩历史冗余工具日志 */
    private fun compactToolOutput(output: String, success: Boolean): String {
        val lines = output.lines()
        val summary = if (lines.size > 6) {
            val head = lines.take(3).joinToString("\n")
            val tail = lines.takeLast(2).joinToString("\n")
            "$head\n... [历史工具输出已压缩，已略去 ${lines.size - 5} 行日志] ...\n$tail"
        } else {
            output.take(180) + "... [已自动压缩]"
        }
        return "【历史执行结果·状态:${if (success) "成功" else "失败"}】\n$summary"
    }

    private suspend fun append(message: HarnessMessage) {
        _messages.value = _messages.value + message
        messageDao.insert(
            HarnessMessageEntity(
                id = message.id,
                sessionId = sessionId,
                createdAt = message.createdAt,
                type = message::class.simpleName.orEmpty(),
                payloadJson = json.encodeToString(HarnessMessage.serializer(), message),
            ),
        )
    }

    /** 流式：增量更新某条 AssistantText（仅内存，供 UI 实时显示）；保留已送达的 reasoning。 */
    private fun streamAssistant(id: String, createdAt: Long, text: String) {
        val current = _messages.value
        val existing = current.firstOrNull { it.id == id }
        val reasoning = (existing as? AssistantText)?.reasoning
        val message = AssistantText(id = id, createdAt = createdAt, text = text, reasoning = reasoning)
        _messages.value = if (existing != null) {
            current.map { if (it.id == id) message else it }
        } else {
            current + message
        }
    }

    /**
     * 流式：把推理模型的 reasoning 增量实时更新到在途的 AssistantText。
     * 若正文尚未开始（模型先思考后输出），先插入一条空正文占位消息，保证 UI 直接可见。
     */
    private fun streamAssistantReasoning(id: String, createdAt: Long, reasoning: String) {
        val current = _messages.value
        val idx = current.indexOfFirst { it.id == id }
        _messages.value = if (idx >= 0) {
            val existing = current[idx]
            (existing as? AssistantText)?.let {
                current.toMutableList().apply { this[idx] = it.copy(reasoning = reasoning) }
            } ?: current + AssistantText(id = id, createdAt = createdAt, text = "", reasoning = reasoning)
        } else {
            current + AssistantText(id = id, createdAt = createdAt, text = "", reasoning = reasoning)
        }
    }

    /** 流式结束：把最终的 AssistantText 落库（含推理模型的 reasoning_content 与本轮总耗时）。 */
    private suspend fun persistAssistant(
        id: String,
        createdAt: Long,
        text: String,
        reasoning: String? = null,
        totalMs: Long? = null,
    ) {
        val message = AssistantText(id = id, createdAt = createdAt, text = text, reasoning = reasoning, totalMs = totalMs)
        // 流式期间内存中已有同 id 的消息：原位替换（补上 totalMs），UI 无需重进会话即可显示
        _messages.value = if (_messages.value.any { it.id == id }) {
            _messages.value.map { if (it.id == id) message else it }
        } else {
            _messages.value + message
        }
        messageDao.insert(
            HarnessMessageEntity(
                id = id,
                sessionId = sessionId,
                createdAt = createdAt,
                type = message::class.simpleName.orEmpty(),
                payloadJson = json.encodeToString(HarnessMessage.serializer(), message),
            ),
        )
    }

    private suspend fun touchSession() {
        sessionDao.touch(sessionId, System.currentTimeMillis())
    }

    private fun toolByName(name: String): HarnessTool = when (name.trim().lowercase()) {
        "read" -> HarnessTool.READ
        "write" -> HarnessTool.WRITE
        "edit" -> HarnessTool.EDIT
        "base", "execute", "run" -> HarnessTool.BASE
        else -> HarnessTool.BASE // 未知工具统一归入 base 由执行层报错
    }

    private fun HarnessTool.apiName(): String = when (this) {
        HarnessTool.READ -> "read"
        HarnessTool.WRITE -> "write"
        HarnessTool.EDIT -> "edit"
        HarnessTool.BASE -> "base"
    }

    private fun newId(): String = UUID.randomUUID().toString()
    private fun now(): Long = System.currentTimeMillis()

    companion object {
        // 自定义工具轮次上限：不再限制为 8，保留一个极高的安全上限防止模型死循环调用工具。
        const val MAX_ROUNDS = 200

        /** 已知工具名（用于拒绝模型的幻觉工具调用）。 */
        val KNOWN_TOOL_NAMES = setOf("read", "write", "edit", "base")

        /** 项目上下文单文件加载上限（字符）。 */
        const val PROJECT_CONTEXT_MAX_BYTES = 16 * 1024

        /** 流式请求网络瞬时故障（IOException）自动重试上限，最多 5 次；HTTP 业务错误不重试。 */
        const val MAX_STREAM_RETRIES = 5

        /** 重试退避基数：第 n 次重试前等待 n × 1s（1s/2s/3s/4s/5s）。 */
        const val RETRY_BACKOFF_MS = 1_000L

        /** 通知/状态条里命令或路径的最大展示长度，超出截断。 */
        const val MAX_STATUS_ARG_LENGTH = 60

        val FALLBACK_SYSTEM_PROMPT = """
            你是太墟（TaiXu）内置的 Agent Harness——一个运行在 Android 私有 Linux 沙箱（Debian via PRoot）中的 AI 助手。你通过调用工具完成任务：读写用户工作区的文件、在 Linux 环境执行命令、安装软件、排查问题。

            可用工具与使用指南：

            1. read —— 读取文件内容
               用途：检查文件、查看当前状态、确认现状。
               指南：优先用 read 而不是 cat / sed。读取路径可用相对路径或以 /workspace/ 开头。若文件不存在或读取失败，用 base 的 ls / find 定位后再读。

            2. write —— 创建或完全覆盖文件
               用途：写新文件、整体重写。
               指南：只用于新文件或完整重写；若只想改其中一段，请用 edit。会自动创建父目录。

            3. edit —— 精确文本替换
               用途：修改已有文件的局部内容。
               指南：oldText 必须与文件原文逐字精确匹配且唯一。一次调用可传多个替换，但每个 oldText 都不能重叠或嵌套。oldText 尽量短而唯一；若匹配多处会失败——先 read 确认内容，或提供更多上下文再改。对尚未存在的新文件完全不适用，用 write。

            4. base —— 在 Debian Linux 沙箱中执行 shell 命令
               用途：安装软件（apt-get / npm / pip install）、运行脚本、查看系统状态（文件、进程、网络）、执行任意 bash。
               返回退出码、stdout、stderr。命令有超时与输出截断。若执行前需要某个目录，用参数 cwd 指定；当前会话关联了工作区时，默认在工作区目录执行。

            运行环境约束（PRoot 沙箱，务必遵守，不要浪费时间在注定失败的操作上）：
            - 你运行在 Android 设备上的 PRoot Debian 沙箱中：没有真正的 root 权限。chown/chgrp 改属主、mount、insmod、sysctl 大部分参数、设置 capabilities 等内核级操作会被静默忽略或失败——不要尝试，也不要因为命令返回成功就误以为生效。
            - 文件权限与属主由 PRoot 模拟。perl 等程序可能因“幽灵”硬链接报错：遇到时改用符号链接（ln -s）替代。锁文件（*.lock、groupadd 的锁机制）在沙箱里可能异常，必要时直接写配置文件或清理残留锁。
            - dpkg 升级含 setuid 文件的包（util-linux 的 su/mount/umount、login 的 newgrp 等）会卡死在 "unable to securely remove *.dpkg-tmp"：PRoot 下无法删除 setuid 的解包残留。已验证的解法：先 rm 所有 .dpkg-tmp 残留，再 chmod u-s,g-s 降级现存的 setuid 目标文件，然后 dpkg -i 重装。装完后文件会恢复 setuid 标记，下次大版本升级可能再卡，同样处理即可——不要反复重试 dpkg，也不要试图让 setuid 真正生效。
            - 没有 systemd：服务不会自启，systemctl 不可用。需要常驻进程时用 nohup 或前台运行，并告知用户。
            - /proc、/sys 部分内容反映的是宿主 Android 系统，不要据此判断 Debian 的状态。
            - 设备 CPU/IO 弱于服务器：编译、apt upgrade 等操作耗时长属正常现象；重操作前先告知用户预计耗时。
            - 遇到奇怪的错误（Bad substitution、dpkg -V 报缺文档、权限异常）优先怀疑是沙箱差异而非系统损坏；确认无实际影响后继续，不要反复重试同一命令，也不要试图“修复”沙箱本身。
            - 工具输出可能被截断：需要完整输出时用 grep/head/tail 截取关键部分，而不是重复执行。

            工作方式：
            - 需要信息时先 read / base 获取事实，不要凭空猜测或编造内容。
            - 每一步想清楚再动手；失败时读取错误输出并自我纠正（换路径、装依赖、重试）。
            - 尽量一次完成用户要求：安装后要验证（如 xxx --version），并汇报真实结果。
            - 用简洁中文汇报；不空话客套；绝不复述或暴露 API Key / Token 等机密。
            - 若不确定或需要用户确认，直接说明。
        """.trimIndent()
    }
}
