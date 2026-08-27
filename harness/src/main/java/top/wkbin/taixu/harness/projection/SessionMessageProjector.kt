package top.wkbin.taixu.harness.projection

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.session.SessionTreeStore

/**
 * 会话消息投影的窄端口：写入与读取实时消息，无需感知前台镜像的实现细节。
 * 供 HarnessLoop 与 CapabilityEventWriter 等协作者使用。
 */
interface LiveMessagePort {
    suspend fun append(sessionId: String, message: HarnessMessage)
    suspend fun publishPersisted(sessionId: String, message: HarnessMessage)
    fun snapshot(sessionId: String): List<HarnessMessage>
}

/**
 * 实时消息投影器：维护每个会话的内存态消息流（含异步历史合并），
 * 并把前台聚焦会话的列表镜像到全局 [foregroundMessages] 流。
 *
 * 持久化本身由 [SessionTreeStore] 负责；本类只做"已提交内容的发布 + 流式上屏"。
 */
@Singleton
class SessionMessageProjector @Inject constructor(
    private val store: SessionTreeStore,
    private val tracker: CurrentSessionTracker,
) : LiveMessagePort {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val liveFlows = ConcurrentHashMap<String, MutableStateFlow<List<HarnessMessage>>>()

    private val _foregroundMessages = MutableStateFlow<List<HarnessMessage>>(emptyList())
    /** 当前前台聚焦会话的消息列表（供聊天界面观察）。 */
    val foregroundMessages: StateFlow<List<HarnessMessage>> = _foregroundMessages.asStateFlow()

    private fun mirrorIfForeground(sessionId: String, value: List<HarnessMessage>) {
        if (tracker.isForeground(sessionId)) {
            _foregroundMessages.value = value
        }
    }

    /**
     * 获取或创建会话的实时消息流。首次创建时异步合并持久化历史：
     * 历史读取期间新追加的消息不会被丢弃，而是按时间归并。
     */
    fun messagesFlow(sessionId: String): MutableStateFlow<List<HarnessMessage>> =
        liveFlows.getOrPut(sessionId) {
            val flow = MutableStateFlow<List<HarnessMessage>>(emptyList())
            scope.launch(Dispatchers.IO) {
                val history = history(sessionId)
                flow.update { current ->
                    if (current.isEmpty()) {
                        history
                    } else {
                        val historyIds = history.mapTo(mutableSetOf()) { it.id }
                        (history + current.filter { it.id !in historyIds })
                            .sortedBy { it.createdAt }
                    }
                }
                mirrorIfForeground(sessionId, flow.value)
            }
            flow
        }

    private suspend fun history(sessionId: String): List<HarnessMessage> =
        withContext(Dispatchers.IO) { store.load(sessionId) }

    /** 会话历史（活动分支），供分支切换 / 重生成等操作重建实时流。 */
    suspend fun loadHistory(sessionId: String): List<HarnessMessage> = history(sessionId)

    /**
     * loadSession 的预置路径：已有流直接复用；否则先读历史再创建，
     * 避免异步合并窗口期把刚切换的会话闪成空列表。
     */
    suspend fun preparedForLoad(sessionId: String): MutableStateFlow<List<HarnessMessage>> {
        return liveFlows[sessionId] ?: MutableStateFlow(loadHistory(sessionId)).also { created ->
            liveFlows.putIfAbsent(sessionId, created)
        }
    }

    /** 新建会话：无条件以空列表开局。 */
    fun seedEmpty(sessionId: String) {
        liveFlows[sessionId] = MutableStateFlow(emptyList())
    }

    /** 新建/加载会话后复位前台镜像为该会话当前值。 */
    fun resetForegroundProjection(value: List<HarnessMessage>) {
        _foregroundMessages.value = value
    }

    /** 整体替换某会话的实时消息（重生成 / 回退 / 分支切换等场景）。 */
    fun replaceAll(sessionId: String, messages: List<HarnessMessage>) {
        messagesFlow(sessionId).value = messages
        mirrorIfForeground(sessionId, messages)
    }

    fun removeSession(sessionId: String) {
        liveFlows.remove(sessionId)
    }

    override suspend fun append(sessionId: String, message: HarnessMessage) {
        store.append(sessionId, message)
        publishPersisted(sessionId, message)
    }

    override suspend fun publishPersisted(sessionId: String, message: HarnessMessage) {
        messagesFlow(sessionId).update { current ->
            if (current.any { it.id == message.id }) current.map { if (it.id == message.id) message else it }
            else current + message
        }
        mirrorIfForeground(sessionId, messagesFlow(sessionId).value)
    }

    override fun snapshot(sessionId: String): List<HarnessMessage> = messagesFlow(sessionId).value

    /** 移除一条仅存在于实时流中的消息（如出错后的空气泡）。 */
    fun remove(sessionId: String, messageId: String) {
        messagesFlow(sessionId).update { current -> current.filterNot { it.id == messageId } }
        mirrorIfForeground(sessionId, messagesFlow(sessionId).value)
    }

    /** 流式助手文本增量刷新：保留已有 reasoning。 */
    fun streamText(sessionId: String, id: String, createdAt: Long, text: String) {
        val flow = messagesFlow(sessionId)
        flow.update { current ->
            val existing = current.firstOrNull { it.id == id }
            val message = AssistantText(
                id = id,
                createdAt = createdAt,
                text = text,
                reasoning = (existing as? AssistantText)?.reasoning,
            )
            if (existing != null) current.map { if (it.id == id) message else it } else current + message
        }
        mirrorIfForeground(sessionId, flow.value)
    }

    /** 流式思考过程增量刷新：文本未就绪时先行生成占位气泡。 */
    fun streamReasoning(sessionId: String, id: String, createdAt: Long, reasoning: String) {
        val flow = messagesFlow(sessionId)
        flow.update { current ->
            val idx = current.indexOfFirst { it.id == id }
            if (idx >= 0) {
                val existing = current[idx]
                (existing as? AssistantText)?.let {
                    current.toMutableList().apply { this[idx] = it.copy(reasoning = reasoning) }
                } ?: (current + AssistantText(id = id, createdAt = createdAt, text = "", reasoning = reasoning))
            } else {
                current + AssistantText(id = id, createdAt = createdAt, text = "", reasoning = reasoning)
            }
        }
        mirrorIfForeground(sessionId, flow.value)
    }
}
