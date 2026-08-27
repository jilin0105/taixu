package top.wkbin.taixu.harness

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.HarnessSessionRepository
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.wkbin.taixu.harness.validation.ToolSchemaValidator

import top.wkbin.taixu.core.datastore.AgentPreferences
import top.wkbin.taixu.harness.session.SessionTreeStore
import top.wkbin.taixu.harness.effects.RetryPolicy
import top.wkbin.taixu.harness.effects.ToolReplayPolicy
import top.wkbin.taixu.harness.operation.OperationCoordinator
import top.wkbin.taixu.harness.recovery.RecoveryManager
import top.wkbin.taixu.harness.recovery.RecoveryOutcome
import top.wkbin.taixu.harness.queue.PromptQueue
import top.wkbin.taixu.harness.queue.PromptQueueManager
import top.wkbin.taixu.harness.compaction.CompactionManager
import top.wkbin.taixu.harness.effects.DanglingToolCallPlanner
import top.wkbin.taixu.harness.events.AgentEventLogger
import top.wkbin.taixu.harness.events.CapabilityEventWriter
import top.wkbin.taixu.harness.projection.CurrentSessionTracker
import top.wkbin.taixu.harness.projection.SessionMessageProjector
import top.wkbin.taixu.harness.projection.SessionStateMirrors
import top.wkbin.taixu.harness.projection.ToolStatusDescriber
import top.wkbin.taixu.harness.session.ApiContextAssembler
import kotlin.time.Duration.Companion.milliseconds

/** Agent 单次运行的结构化结果，外层据此设置会话状态，避免内部失败被误标为 COMPLETED。 */
private sealed interface RunResult {
    data object Completed : RunResult
    data object WaitingApproval : RunResult
    data object Cancelled : RunResult
    data class Failed(val message: String) : RunResult
}

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
    private val messageStore: SessionTreeStore,
    private val sessionDao: HarnessSessionRepository,
    private val settingsDataStore: AgentPreferences,
    private val json: Json,
    private val logger: AppLogger,
    private val approvalRepository: top.wkbin.taixu.core.database.AgentApprovalRepository,
    private val operationCoordinator: OperationCoordinator,
    private val recoveryManager: RecoveryManager,
    private val promptQueueManager: PromptQueueManager,
    private val sessionTracker: CurrentSessionTracker,
    private val stateMirrors: SessionStateMirrors,
    private val messageProjector: SessionMessageProjector,
    private val capabilityWriter: CapabilityEventWriter,
    private val agentEventLogger: AgentEventLogger,
    private val systemPromptBuilder: top.wkbin.taixu.harness.prompt.SystemPromptBuilder,
    private val contextAssembler: ApiContextAssembler,
    private val resumePolicy: top.wkbin.taixu.harness.approval.ApprovalResumePolicy,
) {
    private val loopScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val currentSessionId: StateFlow<String> get() = sessionTracker.currentSessionId

    private val sessionJobs = ConcurrentHashMap<String, Job>()
    private val sessionMutexes = ConcurrentHashMap<String, Mutex>()
    /** Sessions being deleted; reject new runs and skip pending drainage. */
    private val tombstonedSessions = ConcurrentHashMap.newKeySet<String>()
    private val recoveredSessions = ConcurrentHashMap.newKeySet<String>()

    private val _sessionPendingMessages = ConcurrentHashMap<String, MutableStateFlow<List<PendingMessage>>>()

    /** 全局所有会话的运行状态映射（供会话抽屉、状态点等观察）——委托给状态镜像器。 */
    val sessionRunStates: StateFlow<Map<String, SessionRunState>> get() = stateMirrors.sessionRunStates
    /** 全局各会话当前的动作描述状态。 */
    val sessionStatuses: StateFlow<Map<String, String>> get() = stateMirrors.sessionStatuses

    // ---- 当前前台聚焦会话的响应式镜像（全部委托给投影协作类） ----
    val messages: StateFlow<List<HarnessMessage>> get() = messageProjector.foregroundMessages

    val running: StateFlow<Boolean> get() = stateMirrors.running

    private val _workspace = MutableStateFlow("")
    /** 当前会话关联的工作区 Linux 路径（"" = 未关联）。 */
    val workspace: StateFlow<String> = _workspace.asStateFlow()

    private val _projectType = MutableStateFlow("")
    /** 当前会话显式选择的工程类型；空值表示由工作区内容自动识别。 */
    val projectType: StateFlow<String> = _projectType.asStateFlow()

    val error: StateFlow<String?> get() = stateMirrors.error

    /** 当前执行状态（供 UI / 后台通知显示进度）。运行结束或出错时置空。 */
    val status: StateFlow<String?> get() = stateMirrors.status

    /** 推理模型思考中（reasoning 正在流式上屏）。开始思考置 true，本回合结束时置 false。 */
    val thinkingLive: StateFlow<Boolean> get() = stateMirrors.thinkingLive

    private val _pendingMessages = MutableStateFlow<List<PendingMessage>>(emptyList())
    /**
     * 运行中排队等待发送的用户消息。当前任务结束后自动按序接续执行；
     * 用户点"停止"时清空。UI 可观察此列表展示排队状态。
     */
    val pendingMessages: StateFlow<List<PendingMessage>> = _pendingMessages.asStateFlow()

    private val _queuedPrompts = MutableStateFlow<List<QueuedPrompt>>(emptyList())
    /** Current session's durable queues, including steering and follow-up semantics. */
    val queuedPrompts: StateFlow<List<QueuedPrompt>> = _queuedPrompts.asStateFlow()

    private fun getOrCreatePendingFlow(sessId: String): MutableStateFlow<List<PendingMessage>> {
        return _sessionPendingMessages.getOrPut(sessId) { MutableStateFlow(emptyList()) }
    }

    private fun isSessionBusy(sessId: String): Boolean =
        sessionJobs[sessId]?.isActive == true ||
            stateMirrors.isWaitingApproval(sessId)

    /** 新建会话。workspace 为关联的工作区 Linux 路径（如 /workspace/proj），空串表示不关联。 */
    suspend fun newSession(title: String, workspace: String = "", projectType: String = ""): String {
        val id = UUID.randomUUID().toString()
        tombstonedSessions.remove(id)
        sessionTracker.setCurrent(id)
        _workspace.value = workspace
        _projectType.value = projectType
        sessionDao.upsert(
            HarnessSessionEntity(
                id = id,
                title = title.ifBlank { "新会话" },
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                modelId = null,
                workspace = workspace,
                projectType = projectType,
                approvalMode = approvalRepository.currentMode().id,
            ),
        )
        messageProjector.seedEmpty(id)
        messageProjector.resetForegroundProjection(emptyList())
        _sessionPendingMessages[id] = MutableStateFlow(emptyList())
        stateMirrors.ensureFlows(id)
        stateMirrors.setRunState(id, SessionRunState.IDLE)
        stateMirrors.setStatus(id, null)

        stateMirrors.resetForeground()
        _pendingMessages.value = emptyList()
        _queuedPrompts.value = emptyList()
        return id
    }

    /** 恢复已有会话的历史消息与工作区关联，不中断正在后台运行的任何会话。 */
    suspend fun loadSession(id: String) {
        sessionTracker.setCurrent(id)
        val sessionEntity = withContext(Dispatchers.IO) { sessionDao.findById(id) }
        _workspace.value = sessionEntity?.workspace.orEmpty()
        _projectType.value = sessionEntity?.projectType.orEmpty()

        val liveFlow = messageProjector.preparedForLoad(id)

        messageProjector.resetForegroundProjection(liveFlow.value)
        stateMirrors.setForegroundRunning(sessionJobs[id]?.isActive == true)
        stateMirrors.restoreForegroundError(id, stateMirrors.errorOf(id))
        stateMirrors.setStatus(id, stateMirrors.lastStatus(id))
        stateMirrors.setThinkingLive(id, stateMirrors.thinkingLiveOf(id))
        refreshPendingProjection(id)

        if (withContext(Dispatchers.IO) { approvalRepository.pendingNow(id).isNotEmpty() }) {
            stateMirrors.setRunState(id, SessionRunState.WAITING_APPROVAL)
            stateMirrors.setStatus(id, "等待用户批准")
        }

        stateMirrors.recordThinkingModeFromHistory(id, liveFlow.value)
        recoverSessionIfInterrupted(id, liveFlow)
    }

    /**
     * 应用进程重启后批量恢复所有被中断的 Agent 会话。
     *
     * 遍历数据库中所有会话，对存在未完成操作（活跃 operation）的会话执行恢复策略：
     * - 等待审批：保持 WAITING_APPROVAL 状态
     * - 工具中断 / 运行挂起：设为 IDLE 并提示用户"发送消息即可继续"
     *
     * 不会自动续跑，避免重复执行工具或产生额外模型费用。
     * 进程被杀后用户切回应用时，会话状态会被正确还原，而不是显示空白或"运行中"假死。
     *
     * @return 实际执行了恢复处理的会话数量
     */
    suspend fun recoverAllInterruptedSessions(): Int {
        val sessions = withContext(Dispatchers.IO) { sessionDao.listAll() }
        var recovered = 0
        for (session in sessions) {
            val hasActiveOperation = withContext(Dispatchers.IO) {
                operationCoordinator.active(session.id) != null
            }
            if (hasActiveOperation && recoverSessionIfInterrupted(session.id, null)) {
                recovered++
            }
        }
        return recovered
    }

    /**
     * 对单个会话执行中断恢复。从 loadSession 中提取，供批量恢复复用。
     *
     * @param id 会话 ID
     * @param existingLiveFlow loadSession 中已初始化的消息流；批量恢复时传 null，内部按需创建
     * @return 是否执行了非 Clean 的恢复处理
     */
    private suspend fun recoverSessionIfInterrupted(
        id: String,
        existingLiveFlow: MutableStateFlow<List<HarnessMessage>>?,
    ): Boolean {
        if (sessionJobs[id]?.isActive == true) return false
        if (!recoveredSessions.add(id)) return false

        val liveFlow = existingLiveFlow ?: messageProjector.preparedForLoad(id)

        return when (val recovery = recoveryManager.recoverSession(id)) {
            RecoveryOutcome.Clean -> false
            RecoveryOutcome.WaitingApproval -> {
                stateMirrors.setRunState(id, SessionRunState.WAITING_APPROVAL)
                stateMirrors.setStatus(id, "等待用户批准")
                true
            }
            is RecoveryOutcome.ToolInterrupted -> {
                val restored = messageProjector.loadHistory(id)
                messageProjector.replaceAll(id, restored)
                stateMirrors.setRunState(id, SessionRunState.IDLE)
                stateMirrors.setStatus(id, "上次工具执行被中断，发送消息即可继续")
                true
            }
            is RecoveryOutcome.Suspended -> {
                stateMirrors.setRunState(id, SessionRunState.IDLE)
                stateMirrors.setStatus(id, "上次运行已暂停（${recovery.reason}），发送消息即可重新开始")
                true
            }
        }
    }

    suspend fun renameSession(id: String, title: String) {
        sessionDao.rename(id, title, System.currentTimeMillis())
    }

    suspend fun deleteSession(id: String) {
        // Mark tombstoned first so finishRun on the dying job cannot drain pending
        // messages and start a fresh run after we have already begun cleanup.
        tombstonedSessions.add(id)
        _sessionPendingMessages[id]?.value = emptyList()
        sessionJobs[id]?.cancelAndJoin()
        _sessionPendingMessages.remove(id)
        sessionMutexes.remove(id)
        messageProjector.removeSession(id)
        stateMirrors.removeSession(id)

        messageStore.deleteSession(id)
        approvalRepository.deleteForSession(id)
        sessionDao.deleteSession(id)
        tombstonedSessions.remove(id)
        if (sessionTracker.currentSessionId.value == id) {
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
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (trimmed.isEmpty() && imageUrls.isEmpty()) return
        if (sessId.isBlank()) return

        val pending = PendingMessage(text = trimmed, imageUrls = imageUrls)
        startSessionRun(sessId, enqueueOnBusy = pending) {
            runLoop(sessId, pending.text, pending.imageUrls)
        }
        startForegroundServiceSafe()
    }

    fun steer(text: String, targetSessionId: String? = null, imageUrls: List<String> = emptyList()) {
        enqueueExplicit(PromptQueue.STEER, text, targetSessionId, imageUrls)
    }

    fun followUp(text: String, targetSessionId: String? = null, imageUrls: List<String> = emptyList()) {
        enqueueExplicit(PromptQueue.FOLLOW_UP, text, targetSessionId, imageUrls)
    }

    private fun enqueueExplicit(queue: PromptQueue, text: String, targetSessionId: String?, imageUrls: List<String>) {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        val trimmed = text.trim()
        if (sessId.isBlank() || (trimmed.isBlank() && imageUrls.isEmpty())) return
        if (!isSessionBusy(sessId)) {
            send(trimmed, sessId, imageUrls)
            return
        }
        loopScope.launch {
            promptQueueManager.enqueue(sessId, queue, PendingMessage(trimmed, imageUrls))
            refreshPendingProjection(sessId)
        }
    }

    /**
     * 重新生成最后一次回复
     */
    fun regenerateLast(targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (sessId.isBlank()) return

        startSessionRun(sessId) {
            val current = messageProjector.messagesFlow(sessId).value
            val lastUserIndex = current.indexOfLast { it is UserMessage }
            if (lastUserIndex < 0) return@startSessionRun RunResult.Completed
            val lastUserMessage = current[lastUserIndex] as UserMessage
            val toKeep = current.subList(0, lastUserIndex + 1)
            val liveFlow = messageProjector.messagesFlow(sessId)
            messageProjector.replaceAll(sessId, toKeep)
            messageStore.moveTo(sessId, lastUserMessage.id)
            runLoopInternal(sessId, startedAt = now())
        }
        startForegroundServiceSafe()
    }

    /** Rewinds before a tool call and asks the model to continue again on a preserved new branch. */
    fun retryToolCall(toolCallId: String, targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (sessId.isBlank()) return
        startSessionRun(sessId) {
            val current = messageProjector.messagesFlow(sessId).value
            val targetIndex = current.indexOfFirst { it.id == toolCallId && it is ToolCall }
            if (targetIndex < 0) return@startSessionRun RunResult.Completed
            val target = current[targetIndex]
            val updated = current.take(targetIndex)
            messageProjector.replaceAll(sessId, updated)
            messageStore.rewindBefore(sessId, target.id)
            runLoopInternal(sessId, startedAt = now())
        }
        startForegroundServiceSafe()
    }

    /** Moves the main conversation cursor to an existing immutable-tree leaf. */
    suspend fun activateBranch(leafId: String?, targetSessionId: String? = null): Boolean {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (sessId.isBlank() || isSessionBusy(sessId)) return false
        messageStore.moveTo(sessId, leafId)
        val history = messageProjector.loadHistory(sessId)
        messageProjector.replaceAll(sessId, history)
        return true
    }

    /**
     * 编辑并重发指定用户消息
     */
    fun truncateAndResend(userMessageId: String, newText: String, targetSessionId: String? = null) {
        val trimmed = newText.trim()
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (trimmed.isEmpty() || sessId.isBlank()) return

        startSessionRun(sessId) {
            val current = messageProjector.messagesFlow(sessId).value
            val targetIndex = current.indexOfFirst { it.id == userMessageId }
            if (targetIndex < 0) {
                return@startSessionRun runLoop(sessId, trimmed)
            }
            val targetMessage = current[targetIndex]
            val toKeep = current.subList(0, targetIndex)
            val liveFlow = messageProjector.messagesFlow(sessId)
            messageProjector.replaceAll(sessId, toKeep)
            messageStore.rewindBefore(sessId, targetMessage.id)
            runLoop(sessId, trimmed)
        }
        startForegroundServiceSafe()
    }

    /** Navigate the active branch to immediately before this message. */
    suspend fun deleteMessage(messageId: String, targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (isSessionBusy(sessId) || sessId.isBlank()) return
        val liveFlow = messageProjector.messagesFlow(sessId)
        val current = liveFlow.value
        val target = current.firstOrNull { it.id == messageId } ?: return
        val targetIndex = current.indexOf(target)
        val updated = current.take(targetIndex)
        messageProjector.replaceAll(sessId, updated)
        messageStore.rewindBefore(sessId, target.id)
    }

    private suspend fun repairDanglingToolCalls(sessId: String, interrupted: Boolean, workspace: String = "") {
        val actions = DanglingToolCallPlanner.plan(messageProjector.messagesFlow(sessId).value, interrupted)
        if (actions.isEmpty()) return
        actions.forEach { action ->
            val result = when (action) {
                is DanglingToolCallPlanner.Replay -> {
                    agentEventLogger.log(
                        sessId,
                        "ToolReplay",
                        "重放中断的只读工具 ${action.call.rawToolName ?: action.call.tool.name.lowercase()}",
                    )
                    try {
                        toolExecutor.execute(action.call, sessId, workspace)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        ToolResult(
                            id = newId(),
                            createdAt = now(),
                            toolCallId = action.call.id,
                            success = false,
                            output = "重放中断的只读工具失败：${friendly(throwable)}",
                        )
                    }
                }
                is DanglingToolCallPlanner.Stubbed -> ToolResult(
                    id = newId(),
                    createdAt = now(),
                    toolCallId = action.call.id,
                    success = false,
                    output = action.note,
                )
            }
            messageProjector.append(sessId, result)
        }
    }

    fun cancel(targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (sessId.isBlank()) return
        _sessionPendingMessages[sessId]?.value = emptyList()
        loopScope.launch {
            PromptQueue.entries.forEach { promptQueueManager.clear(sessId, it) }
            refreshPendingProjection(sessId)
        }
        stateMirrors.setStatus(sessId, "正在停止…")
        // Do NOT remove the job from the map here: cancellation is asynchronous, and
        // removing by key would let a dying job's finally later delete a *new* job.
        // finishRun removes only its own job via sessionJobs.remove(sessId, selfJob).
        sessionJobs[sessId]?.cancel()
        stateMirrors.setRunState(sessId, SessionRunState.IDLE)
        if (sessId == sessionTracker.foregroundId) {
            _pendingMessages.value = emptyList()
            stateMirrors.setForegroundRunning(false)
        }
    }

    /** 移除某会话排队中的消息 */
    fun removePendingMessage(index: Int, targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (sessId.isBlank()) return
        loopScope.launch {
            promptQueueManager.cancel(sessId, PromptQueue.NEXT_RUN, index)
            refreshPendingProjection(sessId)
        }
    }

    fun removeQueuedPrompt(queue: PromptQueue, index: Int, targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (sessId.isBlank()) return
        loopScope.launch {
            promptQueueManager.cancel(sessId, queue, index)
            refreshPendingProjection(sessId)
        }
    }

    /** 清空某会话全部排队消息 */
    fun clearPendingMessages(targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (sessId.isBlank()) return
        loopScope.launch {
            promptQueueManager.clear(sessId, PromptQueue.NEXT_RUN)
            refreshPendingProjection(sessId)
        }
    }

    private suspend fun enqueuePending(sessId: String, pending: PendingMessage) {
        promptQueueManager.enqueue(sessId, PromptQueue.NEXT_RUN, pending)
        refreshPendingProjection(sessId)
    }

    private suspend fun refreshPendingProjection(sessId: String) {
        val all = PromptQueue.entries.flatMap { queue ->
            promptQueueManager.list(sessId, queue).map { (id, message) -> QueuedPrompt(id, queue, message) }
        }.sortedBy { it.message.createdAt }
        val pending = all.filter { it.queue == PromptQueue.NEXT_RUN }.map { it.message }
        getOrCreatePendingFlow(sessId).value = pending
        if (sessId == sessionTracker.currentSessionId.value) {
            _pendingMessages.value = pending
            _queuedPrompts.value = all
        }
    }

    private suspend fun finishRun(sessId: String, job: Job) {
        // Only remove ourselves; never clobber a newer job that started after cancel().
        sessionJobs.remove(sessId, job)
        val waitingApproval = stateMirrors.onRunFinished(sessId)
        if (waitingApproval) return
        if (tombstonedSessions.contains(sessId)) return
        val (queueItemId, next) = promptQueueManager.first(sessId, PromptQueue.NEXT_RUN) ?: return
        val userMessage = UserMessage(newId(), now(), next.text, next.imageUrls)
        val operationId = operationCoordinator.acceptQueuedRun(sessId, queueItemId, userMessage)
        messageProjector.publishPersisted(sessId, userMessage)
        refreshPendingProjection(sessId)
        startSessionRun(sessId) { runLoopInternal(sessId, now(), operationId) }
    }

    fun clearError(targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (sessId.isBlank()) return
        stateMirrors.setError(sessId, null)
    }

    /**
     * Atomically check-and-occupy the session slot under a per-session Mutex,
     * then run [block] as the single active run. If the session is busy the
     * optional [enqueueOnBusy] message is queued for ordered execution.
     */
    private fun startSessionRun(
        sessId: String,
        enqueueOnBusy: PendingMessage? = null,
        block: suspend () -> RunResult,
    ) {
        if (tombstonedSessions.contains(sessId)) return
        loopScope.launch {
            val mutex = sessionMutexes.getOrPut(sessId) { Mutex() }
            var queueAfterUnlock: PendingMessage? = null
            mutex.withLock {
                if (tombstonedSessions.contains(sessId)) return@withLock
                if (isSessionBusy(sessId)) {
                    queueAfterUnlock = enqueueOnBusy
                    return@withLock
                }
                val job = launch(start = CoroutineStart.LAZY) {
                    executeSessionRun(sessId, block)
                }
                sessionJobs[sessId] = job
                job.start()
            }
            queueAfterUnlock?.let { enqueuePending(sessId, it) }
        }
    }

    /**
     * Claim the session slot unconditionally. Used by approval resumption, which
     * already holds an exclusive claim via claimPending() and is the legitimate
     * successor to a WAITING_APPROVAL run (which still reports busy).
     */
    private fun startClaimedSessionRun(sessId: String, block: suspend () -> RunResult) {
        if (tombstonedSessions.contains(sessId)) return
        loopScope.launch {
            val mutex = sessionMutexes.getOrPut(sessId) { Mutex() }
            mutex.withLock {
                if (tombstonedSessions.contains(sessId)) return@withLock
                val job = launch(start = CoroutineStart.LAZY) {
                    executeSessionRun(sessId, block)
                }
                sessionJobs[sessId] = job
                job.start()
            }
        }
    }

    private suspend fun executeSessionRun(sessId: String, block: suspend () -> RunResult) {
        val selfJob = requireNotNull(currentCoroutineContext()[Job])
        stateMirrors.setRunState(sessId, SessionRunState.RUNNING)
        stateMirrors.setError(sessId, null)
        try {
            when (val result = block()) {
                RunResult.Completed -> {
                    operationCoordinator.finish(sessId, "completed", messageProjector.messagesFlow(sessId).value.lastOrNull()?.id)
                    stateMirrors.setRunState(sessId, SessionRunState.COMPLETED)
                }
                RunResult.WaitingApproval -> stateMirrors.setRunState(sessId, SessionRunState.WAITING_APPROVAL)
                RunResult.Cancelled -> {
                    operationCoordinator.finish(sessId, "aborted")
                    stateMirrors.setRunState(sessId, SessionRunState.IDLE)
                }
                is RunResult.Failed -> {
                    operationCoordinator.finish(sessId, "failed", details = result.message)
                    stateMirrors.setError(sessId, result.message)
                    stateMirrors.setRunState(sessId, SessionRunState.FAILED)
                }
            }
        } catch (_: CancellationException) {
            withContext(NonCancellable) {
                repairDanglingToolCalls(sessId, interrupted = true)
                operationCoordinator.finish(sessId, "aborted", details = "cancelled")
            }
            logger.i("Harness loop cancelled for session $sessId")
            stateMirrors.setRunState(sessId, SessionRunState.IDLE)
        } catch (_: ApprovalPauseException) {
            stateMirrors.setRunState(sessId, SessionRunState.WAITING_APPROVAL)
        } catch (throwable: Throwable) {
            logger.e("Harness loop failed for session $sessId", throwable)
            stateMirrors.setError(sessId, throwable.message ?: "执行失败")
            runCatching { operationCoordinator.finish(sessId, "failed", details = throwable.message) }
            stateMirrors.setRunState(sessId, SessionRunState.FAILED)
        } finally {
            finishRun(sessId, selfJob)
        }
    }

    private suspend fun runLoop(sessId: String, userText: String, imageUrls: List<String> = emptyList()): RunResult {
        agentEventLogger.log(sessId, "UserPrompt", userText)
        val userMessage = UserMessage(id = newId(), createdAt = now(), text = userText, imageUrls = imageUrls)
        val operationId = operationCoordinator.acceptRun(sessId, userMessage)
        messageProjector.publishPersisted(sessId, userMessage)
        return runLoopInternal(sessId, startedAt = now(), operationId = operationId)
    }

    private suspend fun runLoopInternal(sessId: String, startedAt: Long, operationId: String? = null): RunResult {
        val activeOperationId = operationId ?: operationCoordinator.beginRun(sessId)
        val maxRounds = runCatching { settingsDataStore.maxToolRounds.first() }.getOrDefault(MAX_ROUNDS)
        val autoCwd = runCatching { settingsDataStore.autoWorkspaceCwd.first() }.getOrDefault(true)
        val sessionEntity = sessionDao.findById(sessId)
        val sessionWorkspace = sessionEntity?.workspace.orEmpty()
        // 悬空调用修复须带 workspace：SAFE 工具在此重放，读操作需要正确的工作目录
        repairDanglingToolCalls(sessId, interrupted = false, workspace = sessionWorkspace)

        val maxToolsPerRound = runCatching { settingsDataStore.maxToolsPerRound.first() }.getOrDefault(12)
        val maxConsecutiveFailures = runCatching { settingsDataStore.maxConsecutiveFailures.first() }.getOrDefault(8)
        val retryPolicy = RetryPolicy.NETWORK_DEFAULT
        var consecutiveFailures = 0

        var round = 0
        while (round < maxRounds) {
            drainSteeringMessages(sessId)
            stateMirrors.setStatus(sessId, "思考中")
            val model = try {
                providerClient.resolveConfigured()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                agentEventLogger.log(sessId, "ModelResolveError", "无法获取模型配置", throwable)
                return RunResult.Failed("无法获取模型配置：${friendly(throwable)}")
            }
            agentEventLogger.log(sessId, "ModelRequest", "Round=$round, Model=${model.name}, Provider=${model.provider}")
            val msgs = messageProjector.messagesFlow(sessId).value
            val latestUserMessage = msgs.filterIsInstance<UserMessage>().lastOrNull()
            val latestUserText = latestUserMessage?.text.orEmpty()
            val mentionedNames = MentionExtractor.parse(latestUserText)
            val effectiveModel = if (mentionedNames.isNotEmpty()) {
                val matchedTools = model.dynamicMcpTools.filter { tool ->
                    val sName = tool.serverName.lowercase()
                    val sId = tool.serverId.lowercase()
                    val tName = tool.name.lowercase()
                    sName in mentionedNames || sId in mentionedNames || tName in mentionedNames
                }
                if (matchedTools.isNotEmpty()) model.copy(dynamicMcpTools = matchedTools) else model
            } else {
                model
            }
            capabilityWriter.writeIfMentioned(sessId, latestUserMessage?.id.orEmpty(), mentionedNames, effectiveModel)
            val assistantId = newId()
            val assistantAt = now()
            val streamText = StringBuilder()
            val streamReasoning = StringBuilder()
            var streamed: ChatResult? = null
            var netRetry = 0
            while (streamed == null) {
                try {
                    operationCoordinator.providerIntent(
                        operationId = activeOperationId,
                        effectId = assistantId,
                        round = round,
                        attempt = netRetry + 1,
                        maxAttempts = retryPolicy.maxAttempts,
                    )
                    streamed = providerClient.chatStream(
                        effectiveModel,
                        contextAssembler.assemble(
                            sessId = sessId,
                            model = effectiveModel,
                            workspacePath = sessionWorkspace,
                            projectTypeOverride = sessionEntity?.projectType.orEmpty(),
                            thinkingMode = stateMirrors.requestThinkingMode(sessId),
                        ),
                        onReasoning = { chunk ->
                            streamReasoning.append(chunk)
                            stateMirrors.setThinkingLive(sessId, true)
                            stateMirrors.recordThinkingObserved(sessId)
                            // 纯内存投影（不落库），逐增量即时发布；重组频率由帧率自然兑底。
                            if (chunk.isNotEmpty()) {
                                messageProjector.streamReasoning(sessId, assistantId, assistantAt, streamReasoning.toString())
                            }
                        },
                    ) { chunk ->
                        stateMirrors.setStatus(sessId, "回复中")
                        streamText.append(chunk)
                        if (chunk.isNotEmpty()) {
                            messageProjector.streamText(sessId, assistantId, assistantAt, streamText.toString())
                        }
                    }
                    // 流式传输完毕，无条件刷新一次完整内容
                    if (streamReasoning.isNotEmpty()) {
                        messageProjector.streamReasoning(sessId, assistantId, assistantAt, streamReasoning.toString())
                    }
                    if (streamText.isNotEmpty()) {
                        messageProjector.streamText(sessId, assistantId, assistantAt, streamText.toString())
                    }
                } catch (cancellation: CancellationException) {
                    agentEventLogger.log(sessId, "Cancelled", "用户主动取消执行")
                    throw cancellation
                } catch (rateLimit: LlmRateLimitException) {
                    currentCoroutineContext().ensureActive()
                    if (rateLimit.quotaExhausted) {
                        stateMirrors.setThinkingLive(sessId, false)
                        agentEventLogger.log(sessId, "QuotaExhausted", rateLimit.message.orEmpty(), rateLimit)
                        // 移除空的流式气泡；错误通过 error state 展示，不写入消息历史，避免下一轮注入模型上下文
                        messageProjector.remove(sessId, assistantId)
                        val detail = rateLimit.message?.takeIf { it.isNotBlank() }?.let { "\n\n$it" }.orEmpty()
                        return RunResult.Failed("模型服务商额度已耗尽，无法继续执行。请充值、切换可用模型或更新 API Key。$detail")
                    }
                    netRetry++
                    if (netRetry > retryPolicy.maxRetries) throw rateLimit
                    stateMirrors.setThinkingLive(sessId, false)
                    val waitSeconds = rateLimit.retryAfterSeconds ?: (netRetry * RETRY_BACKOFF_SEC).coerceAtMost(60L)
                    stateMirrors.setStatus(sessId, "请求受限，${waitSeconds} 秒后自动重试（$netRetry/$MAX_STREAM_RETRIES）")
                    agentEventLogger.log(sessId, "RateLimitRetry", "限流退避 ${waitSeconds}s，重试 $netRetry/$MAX_STREAM_RETRIES", rateLimit)
                    streamText.clear()
                    streamReasoning.clear()
                    messageProjector.streamText(sessId, assistantId, assistantAt, "")
                    for (remaining in waitSeconds downTo 1L) {
                        currentCoroutineContext().ensureActive()
                        stateMirrors.setStatus(sessId, "请求受限，${remaining} 秒后自动重试（$netRetry/$MAX_STREAM_RETRIES）")
                        delay(1000L.milliseconds)
                    }
                } catch (io: IOException) {
                    currentCoroutineContext().ensureActive()
                    netRetry++
                    agentEventLogger.log(sessId, "NetworkRetry", "网络中断重试 $netRetry/$MAX_STREAM_RETRIES: ${io.message}", io)
                    if (netRetry > retryPolicy.maxRetries) throw io
                    stateMirrors.setThinkingLive(sessId, false)
                    stateMirrors.setStatus(sessId, "网络中断，重试中（$netRetry/$MAX_STREAM_RETRIES）")
                    streamText.clear()
                    streamReasoning.clear()
                    messageProjector.streamText(sessId, assistantId, assistantAt, "")
                    delay(retryPolicy.delayForRetry(netRetry).milliseconds)
                } catch (throwable: Throwable) {
                    stateMirrors.setThinkingLive(sessId, false)
                    agentEventLogger.log(sessId, "ModelError", "LLM 调用失败: ${throwable.message}", throwable)
                    if (streamText.isNotEmpty()) {
                        persistAssistant(
                            sessId,
                            assistantId,
                            assistantAt,
                            streamText.toString(),
                            streamReasoning.toString().ifBlank { null },
                            totalMs = now() - startedAt,
                            operationId = activeOperationId,
                            round = round,
                        )
                    } else {
                        // 移除空的流式气泡；错误通过 error state 展示，不写入消息历史
                        messageProjector.remove(sessId, assistantId)
                    }
                    return RunResult.Failed(friendly(throwable))
                }
            }
            val result = streamed
            val jsonMode = model.toolCallMode == ToolCallMode.JSON_TEXT
            val rawText = streamText.toString()
            // JSON 文本模式：从回复文本中解析 [[tool_call]]{...}[[/tool_call]] 标记，
            // 展示给用户与持久化时剥离标记（标记仅作为模型↔引擎的调用协议）
            val jsonCalls = if (jsonMode) JsonTextToolCallCodec.extract(json, rawText) else emptyList()
            val displayText = if (jsonMode) JsonTextToolCallCodec.stripMarkers(rawText) else rawText
            agentEventLogger.log(
                sessId,
                "ModelResponse",
                "TextLength=${rawText.length}, ReasoningLength=${result.reasoningContent?.length ?: 0}, ToolCallsCount=${result.toolCalls.size}, JsonTextCalls=${jsonCalls.size}",
            )
            if (displayText.isNotEmpty()) {
                persistAssistant(
                    sessId,
                    assistantId,
                    assistantAt,
                    displayText,
                    result.reasoningContent,
                    totalMs = if (result.toolCalls.isEmpty() && jsonCalls.isEmpty()) now() - startedAt else null,
                    operationId = activeOperationId,
                    round = round,
                    usage = result.usage,
                    model = effectiveModel,
                )
            } else {
                val usageEntity = result.usage.takeIf { it.hasData }?.let {
                    operationCoordinator.usageEntity(
                        sessionId = sessId,
                        operationId = activeOperationId,
                        entryId = null,
                        provider = effectiveModel.provider,
                        modelId = effectiveModel.model,
                        usage = it,
                    )
                }
                operationCoordinator.providerSettled(activeOperationId, null, usage = usageEntity, round = round)
            }
            stateMirrors.setThinkingLive(sessId, false)
            val allCalls = result.toolCalls + jsonCalls
            if (allCalls.isEmpty()) {
                val followUps = promptQueueManager.consume(sessId, PromptQueue.FOLLOW_UP)
                refreshPendingProjection(sessId)
                followUps.forEach { messageProjector.publishPersisted(sessId, it) }
                if (followUps.isEmpty()) return RunResult.Completed
                round++
                continue
            }
            // 单轮工具数上限：超出部分回填空结果并提示模型，避免一次性爆发失控。
            val effectiveCalls = if (allCalls.size > maxToolsPerRound) {
                val dropped = allCalls.size - maxToolsPerRound
                allCalls.drop(maxToolsPerRound).forEach { spec ->
                    messageProjector.append(
                        sessId,
                        ToolCall(
                            id = spec.id,
                            createdAt = now(),
                            tool = HarnessApiMapper.toolByName(spec.name),
                            args = buildJsonObject {},
                            reasoning = result.reasoningContent,
                            rawToolName = spec.name.trim(),
                        ),
                    )
                    messageProjector.append(
                        sessId,
                        ToolResult(
                            id = newId(),
                            createdAt = now(),
                            toolCallId = spec.id,
                            success = false,
                            output = "本回合工具调用数量（${allCalls.size}）超过单轮上限（$maxToolsPerRound），已跳过本次多余的 $dropped 个调用。" +
                                "请拆分任务、分步调用工具，避免一次性发起过多工具请求。",
                        ),
                    )
                }
                allCalls.take(maxToolsPerRound)
            } else {
                allCalls
            }
            var roundHadSuccess = false
            effectiveCalls.forEach { spec ->
                val parsedArgs = try {
                    json.parseToJsonElement(spec.argumentsJson) as? JsonObject
                        ?: throw IllegalArgumentException("参数不是 JSON 对象")
                } catch (parseError: Throwable) {
                    messageProjector.append(
                        sessId,
                        ToolCall(
                            id = spec.id,
                            createdAt = now(),
                            tool = HarnessApiMapper.toolByName(spec.name),
                            args = buildJsonObject {},
                            reasoning = result.reasoningContent,
                        ),
                    )
                    messageProjector.append(
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
                    messageProjector.append(
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
                    messageProjector.append(
                        sessId,
                        ToolResult(
                            id = newId(),
                            createdAt = now(),
                            toolCallId = spec.id,
                            success = false,
                            output = "未知工具：${spec.name}。可用工具包含 read / write / edit / base / process / invoke_subagent 以及已启用的 MCP 插件工具。",
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
                // 执行前 JSON Schema 校验：必填/枚举/范围/格式/组合约束。
                // 失败时写回可读问题清单，让模型按 schema 自我纠正，而不是带着坏参数进入执行层。
                val schemaProblems = ToolSchemaValidator.problemsFor(toolNameTrimmed, args, effectiveModel.dynamicMcpTools)
                if (schemaProblems.isNotEmpty()) {
                    messageProjector.append(
                        sessId,
                        ToolCall(
                            id = spec.id,
                            createdAt = now(),
                            tool = tool,
                            args = args,
                            reasoning = result.reasoningContent,
                            rawToolName = toolNameTrimmed,
                        ),
                    )
                    messageProjector.append(
                        sessId,
                        ToolResult(
                            id = newId(),
                            createdAt = now(),
                            toolCallId = spec.id,
                            success = false,
                            output = "工具参数校验未通过：${schemaProblems.joinToString("；")}。" +
                                "请按工具定义修正参数后重新调用，必填字段不可省略。",
                        ),
                    )
                    return@forEach
                }
                val toolCall = ToolCall(
                    // Preserve the provider protocol id across execution, approval,
                    // persistence and the subsequent tool result.
                    id = spec.id,
                    createdAt = now(),
                    tool = tool,
                    args = args,
                    reasoning = result.reasoningContent,
                    rawToolName = toolNameTrimmed,
                )
                agentEventLogger.log(sessId, "ToolCall", "Tool=${tool.name}, RawName=$toolNameTrimmed, Args=$args")
                operationCoordinator.toolIntent(
                    operationId = activeOperationId,
                    message = toolCall,
                    payloadJson = spec.argumentsJson,
                    replay = ToolReplayPolicy.forTool(tool, toolNameTrimmed),
                    round = round,
                )
                messageProjector.publishPersisted(sessId, toolCall)
                stateMirrors.setStatus(sessId, ToolStatusDescriber.describe(tool, args, toolNameTrimmed))
                val toolStart = now()
                val outcome = try {
                    toolExecutor.execute(
                        toolCall,
                        sessId,
                        sessionWorkspace,
                        progressReporter = { progress -> stateMirrors.setStatus(sessId, progress) },
                        operationId = activeOperationId,
                    )
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
                agentEventLogger.log(sessId, "ToolResult", "Tool=${tool.name}, Success=${outcome.success}, Duration=${duration}ms, Output=${outcome.output.take(300)}")
                if (outcome.awaitingApproval) {
                    operationCoordinator.waitingApproval(activeOperationId)
                    stateMirrors.setStatus(sessId, "等待用户批准")
                    throw ApprovalPauseException()
                }
                val settledOutcome = outcome.copy(durationMs = duration)
                operationCoordinator.toolSettled(activeOperationId, settledOutcome, round, toolName = toolCall.rawToolName ?: tool.name)
                messageProjector.publishPersisted(sessId, settledOutcome)
                if (outcome.success) roundHadSuccess = true
                touchSession(sessId)
            }
            // 连续失败熔断：当一轮内所有工具调用均失败时计数，连续超过阈值则主动终止，
            // 避免模型在"调用→失败→再调用"中死循环空转，浪费资源且无法自拔。
            if (effectiveCalls.isNotEmpty() && !roundHadSuccess) {
                consecutiveFailures++
                if (consecutiveFailures >= maxConsecutiveFailures) {
                    messageProjector.append(
                        sessId,
                        AssistantText(
                            id = newId(),
                            createdAt = now(),
                            text = "连续 $consecutiveFailures 轮工具调用均失败，已主动停止以避免陷入死循环。" +
                                "请检查：命令是否正确、工作区路径是否存在、依赖是否已安装，或简化任务后重试。",
                            totalMs = now() - startedAt,
                        ),
                    )
                    return RunResult.Failed("连续 $consecutiveFailures 轮工具调用均失败，已主动停止")
                }
            } else {
                consecutiveFailures = 0
            }
            round++
        }
        messageProjector.append(
            sessId,
            AssistantText(
                id = newId(),
                createdAt = now(),
                text = "已达到最大工具轮数（$maxRounds），请简化任务或分步进行。",
                totalMs = now() - startedAt,
            ),
        )
        return RunResult.Completed
    }

    private suspend fun drainSteeringMessages(sessId: String) {
        val queued = promptQueueManager.consume(sessId, PromptQueue.STEER)
        refreshPendingProjection(sessId)
        queued.forEach { message ->
            agentEventLogger.log(sessId, "SteeringMessage", message.text)
            messageProjector.publishPersisted(sessId, message)
        }
    }

    private fun friendly(throwable: Throwable): String =
        throwable.message?.take(200) ?: throwable::class.simpleName.orEmpty()

    private suspend fun persistAssistant(
        sessId: String,
        id: String,
        createdAt: Long,
        text: String,
        reasoning: String? = null,
        totalMs: Long? = null,
        operationId: String? = null,
        round: Int = 0,
        usage: ChatUsage? = null,
        model: ModelConfig? = null,
    ) {
        val message = AssistantText(
            id = id,
            createdAt = createdAt,
            text = text,
            reasoning = reasoning,
            totalMs = totalMs,
            modelId = model?.model,
            providerId = model?.provider,
            promptTokens = usage?.inputTokens?.takeIf { it > 0 }?.toInt(),
            completionTokens = usage?.outputTokens?.takeIf { it > 0 }?.toInt(),
            cachedTokens = usage?.cacheReadTokens?.takeIf { it > 0 }?.toInt(),
        )
        if (operationId != null) {
            val usageEntity = usage?.takeIf { it.hasData }?.let {
                operationCoordinator.usageEntity(
                    sessionId = sessId,
                    operationId = operationId,
                    entryId = id,
                    provider = model?.provider,
                    modelId = model?.model,
                    usage = it,
                )
            }
            operationCoordinator.providerSettled(operationId, message, usage = usageEntity, round = round)
        } else {
            messageStore.append(sessId, message)
        }
        messageProjector.publishPersisted(sessId, message)
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

    private fun newId(): String = UUID.randomUUID().toString()
    private fun now(): Long = System.currentTimeMillis()

    /** Approve or reject a frozen tool call, then resume the same Agent session. */
    fun resolveApproval(requestId: String, approved: Boolean) {
        logger.i("resolveApproval called: requestId=$requestId approved=$approved")
        loopScope.launch {
            val request = approvalRepository.find(requestId)
            if (request == null) {
                logger.w("resolveApproval: request not found: $requestId")
                return@launch
            }
            val sessId = request.sessionId
            if (request.status != top.wkbin.taixu.core.database.AgentApprovalRequestEntity.STATUS_PENDING) {
                logger.w("resolveApproval: request not pending (status=${request.status}), ignoring: $requestId")
                return@launch
            }
            logger.i("resolveApproval: waiting for prior session job, sessId=$sessId")
            // The original loop may still be unwinding after it persisted the request.
            // Wait for it before claiming the session slot.
            sessionJobs[sessId]?.takeIf { it.isActive }?.join()

            // —— 审批有效性校验：过期 / 参数摘要 / 工作区 / operation 归属 ——
            // 防止“用户批准的是旧参数、旧环境下的请求，实际执行的却是别的东西”。
            val verdict = resumePolicy.evaluate(request, approved)
            if (!approvalRepository.claimPending(request.id, verdict.claimStatus)) return@launch

            // Approval resumption is the legitimate successor to a WAITING_APPROVAL run;
            // claim the slot unconditionally (that state still reports busy to senders).
            startClaimedSessionRun(sessId) {
                var approvalResultPersisted = false
                try {
                    val result = if (verdict.isInvalid) {
                        ToolResult(
                            id = newId(),
                            createdAt = now(),
                            toolCallId = request.toolCallId,
                            success = false,
                            output = resumePolicy.invalidationResultMessage(verdict.invalidationReason.orEmpty()),
                        )
                    } else if (approved) {
                        val args = json.parseToJsonElement(request.argumentsJson) as? JsonObject
                            ?: error("审批参数不是 JSON 对象")
                        val tool = HarnessApiMapper.toolByName(request.toolName)
                        toolExecutor.execute(
                            ToolCall(request.toolCallId, request.createdAt, tool, args, rawToolName = request.toolName),
                            sessId,
                            request.workspace,
                            bypassApproval = true,
                            operationId = request.operationId,
                        )
                    } else {
                        ToolResult(
                            id = newId(),
                            createdAt = now(),
                            toolCallId = request.toolCallId,
                            success = false,
                            output = resumePolicy.rejectionResultMessage(),
                        )
                    }
                    val activeOperation = operationCoordinator.active(sessId)
                    if (activeOperation != null) {
                        operationCoordinator.toolSettled(activeOperation.id, result, round = 0, toolName = request.toolName)
                        messageProjector.publishPersisted(sessId, result)
                    } else {
                        messageProjector.append(sessId, result)
                    }
                    if (!verdict.isInvalid) {
                        approvalRepository.mark(
                            request.id,
                            resumePolicy.finalStatus(approved, result.success),
                        )
                    }
                    approvalResultPersisted = true
                    runLoopInternal(sessId, startedAt = now())
                } catch (cancellation: CancellationException) {
                    if (!approvalResultPersisted) {
                        withContext(NonCancellable) {
                            approvalRepository.mark(
                                request.id,
                                if (approved) top.wkbin.taixu.core.database.AgentApprovalRequestEntity.STATUS_FAILED
                                else top.wkbin.taixu.core.database.AgentApprovalRequestEntity.STATUS_REJECTED,
                            )
                            repairDanglingToolCalls(sessId, interrupted = true)
                        }
                    }
                    throw cancellation
                } catch (_: ApprovalPauseException) {
                    // 批准后继续循环，下一个工具又触发了审批门控——这是正常流程，不是失败。
                    // 外层 executeSessionRun 会把状态置为 WAITING_APPROVAL，等待用户下一次批准。
                    RunResult.WaitingApproval
                } catch (throwable: Throwable) {
                    logger.e("Approval resolution failed for request ${request.id}", throwable)
                    if (!approvalResultPersisted) {
                        approvalRepository.mark(request.id, top.wkbin.taixu.core.database.AgentApprovalRequestEntity.STATUS_FAILED)
                        messageProjector.append(
                            sessId,
                            ToolResult(
                                id = newId(),
                                createdAt = now(),
                                toolCallId = request.toolCallId,
                                success = false,
                                output = "批准操作执行失败：${friendly(throwable)}",
                            ),
                        )
                    }
                    RunResult.Failed(throwable.message ?: "审批操作执行失败：${throwable::class.simpleName}")
                }
            }
        }
    }

    companion object {
        const val MAX_ROUNDS = 200
        val KNOWN_TOOL_NAMES: Set<String> = HarnessTool.entries
            .map { HarnessApiMapper.apiName(it) }
            .toSet() + "subagent"
        const val MAX_STREAM_RETRIES = 5
        const val RETRY_BACKOFF_MS = 1_000L
        const val RETRY_BACKOFF_SEC = 2L

    }
}

private class ApprovalPauseException : RuntimeException()
