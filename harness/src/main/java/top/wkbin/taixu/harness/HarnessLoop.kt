package top.wkbin.taixu.harness

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.HarnessMessageDao
import top.wkbin.taixu.core.database.HarnessMessageEntity
import top.wkbin.taixu.core.database.HarnessSessionDao
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.model.SessionRunState
import java.io.IOException
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

import top.wkbin.taixu.core.datastore.SettingsDataStore
import top.wkbin.taixu.core.model.AgentSkill

/**
 * Harness 多智能体会话并发引擎：
 * 支持多会话后台并行运行、实时状态机追踪（就绪/运行中/完成/失败）、
 * 独立的流式消息队列与前台服务多通知分发。
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

    private val _currentSessionId = MutableStateFlow("")
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    private val sessionJobs = ConcurrentHashMap<String, Job>()

    private val _sessionRunStates = MutableStateFlow<Map<String, SessionRunState>>(emptyMap())
    /** 全局所有会话的运行状态映射（供会话抽屉、状态点等观察） */
    val sessionRunStates: StateFlow<Map<String, SessionRunState>> = _sessionRunStates.asStateFlow()

    private val _sessionStatuses = MutableStateFlow<Map<String, String>>(emptyMap())
    /** 全局各会话当前的动作描述状态 */
    val sessionStatuses: StateFlow<Map<String, String>> = _sessionStatuses.asStateFlow()

    private val _sessionLiveMessages = ConcurrentHashMap<String, MutableStateFlow<List<HarnessMessage>>>()
    private val _sessionPendingMessages = ConcurrentHashMap<String, MutableStateFlow<List<String>>>()
    private val _sessionThinkingLives = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()
    private val _sessionErrors = ConcurrentHashMap<String, MutableStateFlow<String?>>()
    private val sessionThinkingModes = ConcurrentHashMap<String, Boolean>()

    // ---- 当前前台聚焦会话的响应式状态镜像 ----
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

    private fun getOrCreateLiveMessages(sessId: String): MutableStateFlow<List<HarnessMessage>> {
        return _sessionLiveMessages.getOrPut(sessId) {
            val history = kotlinx.coroutines.runBlocking {
                messageDao.listForSession(sessId).mapNotNull { entity ->
                    runCatching {
                        json.decodeFromString(HarnessMessage.serializer(), entity.payloadJson)
                    }.getOrNull()
                }
            }
            MutableStateFlow(history)
        }
    }

    private fun getOrCreatePendingFlow(sessId: String): MutableStateFlow<List<String>> {
        return _sessionPendingMessages.getOrPut(sessId) { MutableStateFlow(emptyList()) }
    }

    private fun getOrCreateThinkingLiveFlow(sessId: String): MutableStateFlow<Boolean> {
        return _sessionThinkingLives.getOrPut(sessId) { MutableStateFlow(false) }
    }

    private fun getOrCreateErrorFlow(sessId: String): MutableStateFlow<String?> {
        return _sessionErrors.getOrPut(sessId) { MutableStateFlow(null) }
    }

    private fun setStatus(sessId: String, statusText: String?) {
        val updated = if (statusText.isNullOrBlank()) {
            _sessionStatuses.value - sessId
        } else {
            _sessionStatuses.value + (sessId to statusText)
        }
        _sessionStatuses.value = updated
        if (sessId == _currentSessionId.value) {
            _status.value = statusText
        }
    }

    private fun setError(sessId: String, errorText: String?) {
        getOrCreateErrorFlow(sessId).value = errorText
        if (sessId == _currentSessionId.value) {
            _error.value = errorText
        }
    }

    private fun setSessionState(sessId: String, state: SessionRunState) {
        _sessionRunStates.value = _sessionRunStates.value + (sessId to state)
        if (sessId == _currentSessionId.value) {
            _running.value = (state == SessionRunState.RUNNING)
        }
    }

    /** 新建会话。workspace 为关联的工作区 Linux 路径（如 /workspace/proj），空串表示不关联。 */
    suspend fun newSession(title: String, workspace: String = ""): String {
        val id = UUID.randomUUID().toString()
        _currentSessionId.value = id
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
        _sessionLiveMessages[id] = MutableStateFlow(emptyList())
        _sessionPendingMessages[id] = MutableStateFlow(emptyList())
        _sessionThinkingLives[id] = MutableStateFlow(false)
        _sessionErrors[id] = MutableStateFlow(null)
        setSessionState(id, SessionRunState.IDLE)
        setStatus(id, null)

        _messages.value = emptyList()
        _running.value = false
        _error.value = null
        _status.value = null
        _thinkingLive.value = false
        _pendingMessages.value = emptyList()
        return id
    }

    /** 恢复已有会话的历史消息与工作区关联，不中断正在后台运行的任何会话。 */
    suspend fun loadSession(id: String) {
        _currentSessionId.value = id
        val sessionEntity = sessionDao.findById(id)
        _workspace.value = sessionEntity?.workspace.orEmpty()

        val liveFlow = _sessionLiveMessages.getOrPut(id) {
            val history = try {
                messageDao.listForSession(id)
                    .mapNotNull { entity ->
                        runCatching {
                            json.decodeFromString(HarnessMessage.serializer(), entity.payloadJson)
                        }.getOrNull()
                    }
            } catch (throwable: Throwable) {
                logger.e("Failed to load history for session $id: ${throwable.message}", throwable)
                emptyList()
            }
            MutableStateFlow(history)
        }

        _messages.value = liveFlow.value
        _running.value = sessionJobs[id]?.isActive == true
        _error.value = _sessionErrors[id]?.value
        _status.value = _sessionStatuses.value[id]?.takeIf { it.isNotBlank() }
        _thinkingLive.value = _sessionThinkingLives[id]?.value ?: false
        _pendingMessages.value = _sessionPendingMessages[id]?.value ?: emptyList()

        sessionThinkingModes[id] = liveFlow.value.any { message ->
            (message as? AssistantText)?.reasoning != null || (message as? ToolCall)?.reasoning != null
        }
    }

    suspend fun renameSession(id: String, title: String) {
        sessionDao.rename(id, title, System.currentTimeMillis())
    }

    suspend fun deleteSession(id: String) {
        sessionJobs[id]?.cancel()
        sessionJobs.remove(id)
        _sessionLiveMessages.remove(id)
        _sessionPendingMessages.remove(id)
        _sessionThinkingLives.remove(id)
        _sessionErrors.remove(id)
        _sessionRunStates.value = _sessionRunStates.value - id
        _sessionStatuses.value = _sessionStatuses.value - id

        sessionDao.deleteMessages(id)
        sessionDao.deleteSession(id)
        if (_currentSessionId.value == id) {
            val remaining = sessionDao.observeAll().first()
            val nextSession = remaining.firstOrNull { it.id != id }
            if (nextSession != null) {
                loadSession(nextSession.id)
            } else {
                newSession("新会话")
            }
        }
    }

    fun send(text: String, targetSessionId: String? = null, imageUrls: List<String> = emptyList()) {
        val trimmed = text.trim()
        val sessId = targetSessionId?.ifBlank { null } ?: _currentSessionId.value
        if (trimmed.isEmpty() && imageUrls.isEmpty()) return
        if (sessId.isBlank()) return

        val pendingFlow = getOrCreatePendingFlow(sessId)
        val isRunning = sessionJobs[sessId]?.isActive == true
        if (isRunning) {
            pendingFlow.value = pendingFlow.value + trimmed
            if (sessId == _currentSessionId.value) {
                _pendingMessages.value = pendingFlow.value
            }
            return
        }

        val job = loopScope.launch {
            setSessionState(sessId, SessionRunState.RUNNING)
            setError(sessId, null)
            try {
                runLoop(sessId, trimmed, imageUrls)
                setSessionState(sessId, SessionRunState.COMPLETED)
            } catch (cancellation: CancellationException) {
                withContext(NonCancellable) {
                    repairDanglingToolCalls(sessId, interrupted = true)
                }
                logger.i("Harness loop cancelled for session $sessId")
                setSessionState(sessId, SessionRunState.IDLE)
            } catch (throwable: Throwable) {
                logger.e("Harness loop failed for session $sessId", throwable)
                setError(sessId, throwable.message ?: "执行失败")
                setSessionState(sessId, SessionRunState.FAILED)
            } finally {
                finishRun(sessId)
            }
        }
        sessionJobs[sessId] = job
        if (sessId == _currentSessionId.value) {
            _running.value = true
        }
        startForegroundServiceSafe()
    }

    /**
     * 重新生成最后一次回复
     */
    fun regenerateLast(targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: _currentSessionId.value
        if (sessionJobs[sessId]?.isActive == true || sessId.isBlank()) return
        val current = getOrCreateLiveMessages(sessId).value
        val lastUserIndex = current.indexOfLast { it is UserMessage }
        if (lastUserIndex < 0) return
        val lastUserMessage = current[lastUserIndex] as UserMessage

        val job = loopScope.launch {
            setSessionState(sessId, SessionRunState.RUNNING)
            setError(sessId, null)
            try {
                val toKeep = current.subList(0, lastUserIndex + 1)
                val liveFlow = getOrCreateLiveMessages(sessId)
                liveFlow.value = toKeep
                if (sessId == _currentSessionId.value) {
                    _messages.value = toKeep
                }
                messageDao.deleteFromTimestamp(sessId, lastUserMessage.createdAt + 1)
                runLoopInternal(sessId, startedAt = now())
                setSessionState(sessId, SessionRunState.COMPLETED)
            } catch (cancellation: CancellationException) {
                withContext(NonCancellable) {
                    repairDanglingToolCalls(sessId, interrupted = true)
                }
                logger.i("Harness loop cancelled for session $sessId")
                setSessionState(sessId, SessionRunState.IDLE)
            } catch (throwable: Throwable) {
                logger.e("Harness loop failed for session $sessId", throwable)
                setError(sessId, throwable.message ?: "执行失败")
                setSessionState(sessId, SessionRunState.FAILED)
            } finally {
                finishRun(sessId)
            }
        }
        sessionJobs[sessId] = job
        if (sessId == _currentSessionId.value) {
            _running.value = true
        }
        startForegroundServiceSafe()
    }

    /**
     * 编辑并重发指定用户消息
     */
    fun truncateAndResend(userMessageId: String, newText: String, targetSessionId: String? = null) {
        val trimmed = newText.trim()
        val sessId = targetSessionId?.ifBlank { null } ?: _currentSessionId.value
        if (trimmed.isEmpty() || sessionJobs[sessId]?.isActive == true || sessId.isBlank()) return
        val current = getOrCreateLiveMessages(sessId).value
        val targetIndex = current.indexOfFirst { it.id == userMessageId }
        if (targetIndex < 0) {
            send(trimmed, sessId)
            return
        }
        val targetMessage = current[targetIndex]

        val job = loopScope.launch {
            setSessionState(sessId, SessionRunState.RUNNING)
            setError(sessId, null)
            try {
                val toKeep = current.subList(0, targetIndex)
                val liveFlow = getOrCreateLiveMessages(sessId)
                liveFlow.value = toKeep
                if (sessId == _currentSessionId.value) {
                    _messages.value = toKeep
                }
                messageDao.deleteFromTimestamp(sessId, targetMessage.createdAt)
                runLoop(sessId, trimmed)
                setSessionState(sessId, SessionRunState.COMPLETED)
            } catch (cancellation: CancellationException) {
                withContext(NonCancellable) {
                    repairDanglingToolCalls(sessId, interrupted = true)
                }
                logger.i("Harness loop cancelled for session $sessId")
                setSessionState(sessId, SessionRunState.IDLE)
            } catch (throwable: Throwable) {
                logger.e("Harness loop failed for session $sessId", throwable)
                setError(sessId, throwable.message ?: "执行失败")
                setSessionState(sessId, SessionRunState.FAILED)
            } finally {
                finishRun(sessId)
            }
        }
        sessionJobs[sessId] = job
        if (sessId == _currentSessionId.value) {
            _running.value = true
        }
        startForegroundServiceSafe()
    }

    /**
     * 删除单条消息
     */
    suspend fun deleteMessage(messageId: String, targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: _currentSessionId.value
        if (sessionJobs[sessId]?.isActive == true || sessId.isBlank()) return
        val liveFlow = getOrCreateLiveMessages(sessId)
        val current = liveFlow.value
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
        val updated = current.filter { it.id !in idsToDelete }
        liveFlow.value = updated
        if (sessId == _currentSessionId.value) {
            _messages.value = updated
        }
        messageDao.deleteByIds(idsToDelete)
    }

    /**
     * 为所有尚无 ToolResult 的 ToolCall 补写一条中断占位结果并持久化
     */
    private suspend fun repairDanglingToolCalls(sessId: String, interrupted: Boolean) {
        val liveFlow = getOrCreateLiveMessages(sessId)
        val msgs = liveFlow.value
        val answeredIds = msgs.filterIsInstance<ToolResult>().mapTo(mutableSetOf()) { it.toolCallId }
        val dangling = msgs.filterIsInstance<ToolCall>().filter { it.id !in answeredIds }
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
            append(sessId, result)
        }
    }

    fun cancel(targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: _currentSessionId.value
        _sessionPendingMessages[sessId]?.value = emptyList()
        setStatus(sessId, "正在停止…")
        sessionJobs[sessId]?.cancel()
        sessionJobs.remove(sessId)
        setSessionState(sessId, SessionRunState.IDLE)
        if (sessId == _currentSessionId.value) {
            _pendingMessages.value = emptyList()
            _status.value = "正在停止…"
            _running.value = false
        }
    }

    /** 移除某会话排队中的消息 */
    fun removePendingMessage(index: Int, targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: _currentSessionId.value
        val pendingFlow = getOrCreatePendingFlow(sessId)
        val current = pendingFlow.value
        if (index in current.indices) {
            pendingFlow.value = current.filterIndexed { i, _ -> i != index }
            if (sessId == _currentSessionId.value) {
                _pendingMessages.value = pendingFlow.value
            }
        }
    }

    /** 清空某会话全部排队消息 */
    fun clearPendingMessages(targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: _currentSessionId.value
        getOrCreatePendingFlow(sessId).value = emptyList()
        if (sessId == _currentSessionId.value) {
            _pendingMessages.value = emptyList()
        }
    }

    private fun finishRun(sessId: String) {
        sessionJobs.remove(sessId)
        _sessionThinkingLives[sessId]?.value = false
        setStatus(sessId, null)
        if (sessId == _currentSessionId.value) {
            _running.value = false
            _status.value = null
            _thinkingLive.value = false
        }
        val pendingFlow = _sessionPendingMessages[sessId]
        val next = pendingFlow?.value?.firstOrNull()
        if (next != null) {
            pendingFlow.value = pendingFlow.value.drop(1)
            if (sessId == _currentSessionId.value) {
                _pendingMessages.value = pendingFlow.value
            }
            send(next, sessId)
        }
    }

    fun clearError(targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: _currentSessionId.value
        setError(sessId, null)
    }

    private suspend fun runLoop(sessId: String, userText: String, imageUrls: List<String> = emptyList()) {
        logAgentEvent(sessId, "UserPrompt", userText)
        append(sessId, UserMessage(id = newId(), createdAt = now(), text = userText, imageUrls = imageUrls))
        runLoopInternal(sessId, startedAt = now())
    }

    private suspend fun runLoopInternal(sessId: String, startedAt: Long) {
        repairDanglingToolCalls(sessId, interrupted = false)
        val maxRounds = runCatching { settingsDataStore.maxToolRounds.first() }.getOrDefault(MAX_ROUNDS)
        val autoCwd = runCatching { settingsDataStore.autoWorkspaceCwd.first() }.getOrDefault(true)
        val sessionEntity = sessionDao.findById(sessId)
        val sessionWorkspace = sessionEntity?.workspace.orEmpty()

        var round = 0
        while (round < maxRounds) {
            drainSteeringMessages(sessId)
            setStatus(sessId, "思考中")
            val model = try {
                providerClient.resolveConfigured()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                logAgentEvent(sessId, "ModelResolveError", "无法获取模型配置", throwable)
                appendFatal(sessId, "无法获取模型配置：${friendly(throwable)}", now() - startedAt)
                return
            }
            logAgentEvent(sessId, "ModelRequest", "Round=$round, Model=${model.name}, Provider=${model.provider}")
            val assistantId = newId()
            val assistantAt = now()
            val streamText = StringBuilder()
            val streamReasoning = StringBuilder()
            var streamed: ChatResult? = null
            var netRetry = 0
            while (streamed == null) {
                try {
                    streamed = providerClient.chatStream(
                        model,
                        apiMessages(sessId),
                        onReasoning = { chunk ->
                            streamReasoning.append(chunk)
                            sessionThinkingModes[sessId] = true
                            getOrCreateThinkingLiveFlow(sessId).value = true
                            if (sessId == _currentSessionId.value) {
                                _thinkingLive.value = true
                            }
                            streamAssistantReasoning(sessId, assistantId, assistantAt, streamReasoning.toString())
                        },
                    ) { chunk ->
                        setStatus(sessId, "回复中")
                        streamText.append(chunk)
                        streamAssistant(sessId, assistantId, assistantAt, streamText.toString())
                    }
                } catch (cancellation: CancellationException) {
                    logAgentEvent(sessId, "Cancelled", "用户主动取消执行")
                    throw cancellation
                } catch (io: IOException) {
                    currentCoroutineContext().ensureActive()
                    netRetry++
                    logAgentEvent(sessId, "NetworkRetry", "网络中断重试 $netRetry/$MAX_STREAM_RETRIES: ${io.message}", io)
                    if (netRetry > MAX_STREAM_RETRIES) throw io
                    getOrCreateThinkingLiveFlow(sessId).value = false
                    if (sessId == _currentSessionId.value) {
                        _thinkingLive.value = false
                    }
                    setStatus(sessId, "网络中断，重试中（$netRetry/$MAX_STREAM_RETRIES）")
                    streamText.clear()
                    streamReasoning.clear()
                    streamAssistant(sessId, assistantId, assistantAt, "")
                    delay(netRetry * RETRY_BACKOFF_MS)
                } catch (throwable: Throwable) {
                    getOrCreateThinkingLiveFlow(sessId).value = false
                    if (sessId == _currentSessionId.value) {
                        _thinkingLive.value = false
                    }
                    logAgentEvent(sessId, "ModelError", "LLM 调用失败: ${throwable.message}", throwable)
                    if (streamText.isNotEmpty()) {
                        persistAssistant(
                            sessId,
                            assistantId,
                            assistantAt,
                            streamText.toString(),
                            streamReasoning.toString().ifBlank { null },
                            totalMs = now() - startedAt,
                        )
                    } else {
                        appendFatal(sessId, "执行遇到问题，已中断：${friendly(throwable)}", now() - startedAt)
                    }
                    return
                }
            }
            val result = streamed
            logAgentEvent(
                sessId,
                "ModelResponse",
                "TextLength=${streamText.length}, ReasoningLength=${result.reasoningContent?.length ?: 0}, ToolCallsCount=${result.toolCalls.size}",
            )
            if (streamText.isNotEmpty()) {
                persistAssistant(
                    sessId,
                    assistantId,
                    assistantAt,
                    streamText.toString(),
                    result.reasoningContent,
                    totalMs = if (!result.hasToolCalls) now() - startedAt else null,
                )
            }
            getOrCreateThinkingLiveFlow(sessId).value = false
            if (sessId == _currentSessionId.value) {
                _thinkingLive.value = false
            }
            if (!result.hasToolCalls) return
            result.toolCalls.forEach { spec ->
                val parsedArgs = try {
                    json.parseToJsonElement(spec.argumentsJson) as? JsonObject
                        ?: throw IllegalArgumentException("参数不是 JSON 对象")
                } catch (parseError: Throwable) {
                    append(
                        sessId,
                        ToolCall(
                            id = spec.id,
                            createdAt = now(),
                            tool = HarnessApiMapper.toolByName(spec.name),
                            args = buildJsonObject {},
                            reasoning = result.reasoningContent,
                        ),
                    )
                    append(
                        sessId,
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
                val toolNameTrimmed = spec.name.trim()
                if (toolNameTrimmed.lowercase() !in KNOWN_TOOL_NAMES && !toolNameTrimmed.startsWith("mcp__")) {
                    append(
                        sessId,
                        ToolCall(
                            id = spec.id,
                            createdAt = now(),
                            tool = HarnessApiMapper.toolByName(spec.name),
                            args = buildJsonObject {},
                            reasoning = result.reasoningContent,
                            rawToolName = toolNameTrimmed,
                        ),
                    )
                    append(
                        sessId,
                        ToolResult(
                            id = newId(),
                            createdAt = now(),
                            toolCallId = spec.id,
                            success = false,
                            output = "未知工具：${spec.name}。可用工具包含 read / write / edit / base / invoke_subagent 以及已启用的 MCP 插件工具。",
                        ),
                    )
                    return@forEach
                }
                val tool = HarnessApiMapper.toolByName(spec.name)
                var args = parsedArgs
                if (tool == HarnessTool.BASE && autoCwd && sessionWorkspace.isNotBlank() && args["cwd"] == null) {
                    args = buildJsonObject {
                        put("cwd", sessionWorkspace)
                        args.forEach { (key, value) -> put(key, value) }
                    }
                }
                val toolCall = ToolCall(
                    id = newId(),
                    createdAt = now(),
                    tool = tool,
                    args = args,
                    reasoning = result.reasoningContent,
                    rawToolName = toolNameTrimmed,
                )
                logAgentEvent(sessId, "ToolCall", "Tool=${tool.name}, RawName=$toolNameTrimmed, Args=$args")
                append(sessId, toolCall)
                setStatus(sessId, describeToolCall(tool, args, toolNameTrimmed))
                val toolStart = now()
                val outcome = try {
                    toolExecutor.execute(toolCall, sessId)
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
                logAgentEvent(sessId, "ToolResult", "Tool=${tool.name}, Success=${outcome.success}, Duration=${duration}ms, Output=${outcome.output.take(300)}")
                append(sessId, outcome.copy(durationMs = duration))
                touchSession(sessId)
            }
            round++
        }
        append(
            sessId,
            AssistantText(
                id = newId(),
                createdAt = now(),
                text = "已达到最大工具轮数（$maxRounds），请简化任务或分步进行。",
                totalMs = now() - startedAt,
            ),
        )
    }

    private fun describeToolCall(tool: HarnessTool, args: JsonObject, rawToolName: String? = null): String {
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
            HarnessTool.SUBAGENT -> "正在派发并执行子智能体协同任务…"
            HarnessTool.MCP -> "正在调用 MCP 插件工具：${rawToolName ?: "mcp"}…"
        }
    }

    private suspend fun logAgentEvent(sessId: String, tag: String, message: String, throwable: Throwable? = null) {
        val enabled = runCatching { settingsDataStore.agentLoggingEnabled.first() }.getOrDefault(false)
        if (enabled) {
            logger.logAgent(sessId, tag, message, throwable)
        }
    }

    private suspend fun drainSteeringMessages(sessId: String) {
        val pendingFlow = getOrCreatePendingFlow(sessId)
        val queued = pendingFlow.value
        if (queued.isEmpty()) return
        pendingFlow.value = emptyList()
        if (sessId == _currentSessionId.value) {
            _pendingMessages.value = emptyList()
        }
        queued.forEach { text ->
            logAgentEvent(sessId, "SteeringMessage", text)
            append(sessId, UserMessage(id = newId(), createdAt = now(), text = text))
        }
    }

    private suspend fun appendFatal(sessId: String, text: String, totalMs: Long? = null) {
        append(sessId, AssistantText(id = newId(), createdAt = now(), text = text, totalMs = totalMs))
    }

    private fun friendly(throwable: Throwable): String =
        throwable.message?.take(200) ?: throwable::class.simpleName.orEmpty()

    private suspend fun buildSystemPrompt(workspacePath: String): String {
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
                "### [专精技能] " + skill.name + " (" + skill.category + ")\n" + skill.systemPrompt.trim()
            }
        } else ""

        val projectContext = loadProjectContext(workspacePath)
        val workspaceSection = if (workspacePath.isNotBlank()) {
            "\n\n当前工作区：" + workspacePath + "（base 命令默认在此目录执行；read/write/edit 的相对路径以此为根）"
        } else ""

        return template
            .replace("{{DISTRO_NAME}}", distroName)
            .replace("{{PKG_MANAGER}}", pkgManager)
            .replace("{{ACTIVE_SKILLS}}", skillSection)
            .trim() + workspaceSection + projectContext
    }

    private suspend fun loadProjectContext(workspacePath: String): String {
        if (workspacePath.isBlank()) return ""
        val sections = buildList {
            for (name in listOf("AGENTS.md", "CLAUDE.md", "README.md")) {
                val content = runCatching {
                    fileAccess.read("$workspacePath/$name".removePrefix("/")).getOrNull()
                }.getOrNull() ?: continue
                val trimmed = content.take(PROJECT_CONTEXT_MAX_BYTES)
                add(
                    "<project_instructions path=\"" + name + "\">\n" + trimmed +
                        (if (content.length > PROJECT_CONTEXT_MAX_BYTES) "\n…（文件过长已截断）" else "") +
                        "\n</project_instructions>",
                )
            }
        }
        if (sections.isEmpty()) return ""
        return "\n\n<project_context>\n当前工作区的项目说明与约定（自动加载，编码时务必遵守）：\n\n" +
            sections.joinToString("\n\n") + "\n</project_context>"
    }

    private suspend fun apiMessages(sessId: String): List<ApiMessage> {
        val compactionEnabled = runCatching { settingsDataStore.contextCompactionEnabled.first() }.getOrDefault(true)
        val threshold = runCatching { settingsDataStore.contextCompactionThreshold.first() }.getOrDefault(15)
        val sessionEntity = sessionDao.findById(sessId)
        val sessionWorkspace = sessionEntity?.workspace.orEmpty()
        val systemPrompt = buildSystemPrompt(sessionWorkspace)
        val thinkingMode = sessionThinkingModes[sessId] ?: false

        return buildList {
            add(ApiMessage(role = "system", content = systemPrompt))
            val msgs = getOrCreateLiveMessages(sessId).value
            val answeredIds = msgs.filterIsInstance<ToolResult>().mapTo(mutableSetOf()) { it.toolCallId }

            val userIndices = msgs.indices.filter { msgs[it] is UserMessage }
            val shouldCompact = compactionEnabled && userIndices.size > threshold

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
                    if (message is ToolCall && message.id !in answeredIds) {
                        i++
                        continue
                    }
                    val text = (message as? AssistantText)?.text
                    val reasoning = when (message) {
                        is AssistantText -> message.reasoning
                        is ToolCall -> message.reasoning
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

    private fun compactToolOutput(output: String, success: Boolean): String {
        val lines = output.lines()
        val summary = if (lines.size > 6) {
            val head = lines.take(3).joinToString("\n")
            val tail = lines.takeLast(2).joinToString("\n")
            head + "\n... [历史工具输出已压缩，已略去 " + (lines.size - 5) + " 行日志] ...\n" + tail
        } else {
            output.take(180) + "... [已自动压缩]"
        }
        return "【历史执行结果·状态:" + (if (success) "成功" else "失败") + "】\n" + summary
    }

    private fun sanitizeForStorage(message: HarnessMessage): HarnessMessage = when (message) {
        is ToolResult -> {
            if (message.output.length > MAX_STORAGE_STRING_LENGTH) {
                val head = message.output.take(STORAGE_KEEP_LENGTH)
                val tail = message.output.takeLast(STORAGE_KEEP_LENGTH)
                message.copy(
                    output = "$head\n\n... [工具输出过长（共 ${message.output.length} 字符），已截断保存] ...\n\n$tail",
                )
            } else {
                message
            }
        }
        is AssistantText -> {
            val text = if (message.text.length > MAX_STORAGE_STRING_LENGTH) {
                message.text.take(MAX_STORAGE_STRING_LENGTH) + "\n... [文本过长已截断]"
            } else {
                message.text
            }
            val reasoning = if ((message.reasoning?.length ?: 0) > MAX_STORAGE_STRING_LENGTH) {
                message.reasoning?.take(MAX_STORAGE_STRING_LENGTH) + "\n... [推理过程过长已截断]"
            } else {
                message.reasoning
            }
            message.copy(text = text, reasoning = reasoning)
        }
        is UserMessage -> {
            if (message.text.length > MAX_STORAGE_STRING_LENGTH) {
                message.copy(text = message.text.take(MAX_STORAGE_STRING_LENGTH) + "\n... [用户消息过长已截断]")
            } else {
                message
            }
        }
        is ToolCall -> message
    }

    private suspend fun append(sessId: String, message: HarnessMessage) {
        val liveFlow = getOrCreateLiveMessages(sessId)
        liveFlow.value = liveFlow.value + message
        if (sessId == _currentSessionId.value) {
            _messages.value = liveFlow.value
        }
        val safeMessage = sanitizeForStorage(message)
        runCatching {
            messageDao.insert(
                HarnessMessageEntity(
                    id = safeMessage.id,
                    sessionId = sessId,
                    createdAt = safeMessage.createdAt,
                    type = safeMessage::class.simpleName.orEmpty(),
                    payloadJson = json.encodeToString(HarnessMessage.serializer(), safeMessage),
                ),
            )
        }.onFailure { throwable ->
            logger.e("Failed to insert message into DB for session $sessId: ${throwable.message}", throwable)
        }
    }

    private fun streamAssistant(sessId: String, id: String, createdAt: Long, text: String) {
        val liveFlow = getOrCreateLiveMessages(sessId)
        val current = liveFlow.value
        val existing = current.firstOrNull { it.id == id }
        val reasoning = (existing as? AssistantText)?.reasoning
        val message = AssistantText(id = id, createdAt = createdAt, text = text, reasoning = reasoning)
        val updated = if (existing != null) {
            current.map { if (it.id == id) message else it }
        } else {
            current + message
        }
        liveFlow.value = updated
        if (sessId == _currentSessionId.value) {
            _messages.value = updated
        }
    }

    private fun streamAssistantReasoning(sessId: String, id: String, createdAt: Long, reasoning: String) {
        val liveFlow = getOrCreateLiveMessages(sessId)
        val current = liveFlow.value
        val idx = current.indexOfFirst { it.id == id }
        val updated = if (idx >= 0) {
            val existing = current[idx]
            (existing as? AssistantText)?.let {
                current.toMutableList().apply { this[idx] = it.copy(reasoning = reasoning) }
            } ?: (current + AssistantText(id = id, createdAt = createdAt, text = "", reasoning = reasoning))
        } else {
            current + AssistantText(id = id, createdAt = createdAt, text = "", reasoning = reasoning)
        }
        liveFlow.value = updated
        if (sessId == _currentSessionId.value) {
            _messages.value = updated
        }
    }

    private suspend fun persistAssistant(
        sessId: String,
        id: String,
        createdAt: Long,
        text: String,
        reasoning: String? = null,
        totalMs: Long? = null,
    ) {
        val message = AssistantText(id = id, createdAt = createdAt, text = text, reasoning = reasoning, totalMs = totalMs)
        val liveFlow = getOrCreateLiveMessages(sessId)
        val current = liveFlow.value
        val updated = if (current.any { it.id == id }) {
            current.map { if (it.id == id) message else it }
        } else {
            current + message
        }
        liveFlow.value = updated
        if (sessId == _currentSessionId.value) {
            _messages.value = updated
        }
        val safeMessage = sanitizeForStorage(message)
        runCatching {
            messageDao.insert(
                HarnessMessageEntity(
                    id = id,
                    sessionId = sessId,
                    createdAt = createdAt,
                    type = safeMessage::class.simpleName.orEmpty(),
                    payloadJson = json.encodeToString(HarnessMessage.serializer(), safeMessage),
                ),
            )
        }.onFailure { throwable ->
            logger.e("Failed to persist assistant message to DB: ${throwable.message}", throwable)
        }
    }

    private suspend fun touchSession(sessId: String) {
        sessionDao.touch(sessId, System.currentTimeMillis())
    }

    private fun startForegroundServiceSafe() {
        runCatching {
            val intent = Intent(context, Class.forName("top.wkbin.taixu.service.AgentForegroundService"))
                .setAction("top.wkbin.taixu.action.AGENT_START")
            context.startForegroundService(intent)
        }
    }

    private fun HarnessTool.apiName(): String = when (this) {
        HarnessTool.READ -> "read"
        HarnessTool.WRITE -> "write"
        HarnessTool.EDIT -> "edit"
        HarnessTool.BASE -> "base"
        HarnessTool.SUBAGENT -> "invoke_subagent"
        HarnessTool.MCP -> "mcp"
    }

    private fun newId(): String = UUID.randomUUID().toString()
    private fun now(): Long = System.currentTimeMillis()

    companion object {
        const val MAX_ROUNDS = 200
        val KNOWN_TOOL_NAMES = setOf("read", "write", "edit", "base", "invoke_subagent", "subagent")
        const val PROJECT_CONTEXT_MAX_BYTES = 16 * 1024
        const val MAX_STREAM_RETRIES = 5
        const val RETRY_BACKOFF_MS = 1_000L
        const val MAX_STATUS_ARG_LENGTH = 60
        const val MAX_STORAGE_STRING_LENGTH = 128 * 1024
        const val STORAGE_KEEP_LENGTH = 60 * 1024

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
