package top.wkbin.taixu.runtime.browser

import kotlinx.serialization.Serializable
import top.wkbin.taixu.core.browser.PageSnapshot
import top.wkbin.taixu.core.model.ToolImageRef

/**
 * 浏览器侧事件总线传输的事件（密封接口）。
 *
 * 设计要点：
 * - 全部事件不可变；
 * - 由 [BrowserEventBus] 统一 dispatch，订阅方可用 [kotlinx.coroutines.flow.StateFlow] 拿到最近一次状态；
 * - 跨模块：UI 订阅 [SnapshotUpdated] 实时显示 ref；harness 可订阅用于 session 摘要回灌。
 */
sealed interface BrowserEvent {
    val tabId: String
    val at: Long

    @Serializable
    data class PageChanged(
        override val tabId: String,
        val url: String,
        val title: String,
        override val at: Long = System.currentTimeMillis()
    ) : BrowserEvent

    @Serializable
    data class SnapshotUpdated(
        override val tabId: String,
        val snapshot: PageSnapshot,
        override val at: Long = System.currentTimeMillis()
    ) : BrowserEvent

    @Serializable
    data class ConsoleLogged(
        override val tabId: String,
        val level: String,
        val message: String,
        override val at: Long = System.currentTimeMillis()
    ) : BrowserEvent

    @Serializable
    data class NetworkCaptured(
        override val tabId: String,
        val request: CapturedRequest,
        override val at: Long = System.currentTimeMillis()
    ) : BrowserEvent

    @Serializable
    data class UserInteractionHappened(
        override val tabId: String,
        val ref: String,
        val kind: String,
        override val at: Long = System.currentTimeMillis()
    ) : BrowserEvent

    @Serializable
    data class ScreenshotSaved(
        override val tabId: String,
        val imageRef: ToolImageRef,
        override val at: Long = System.currentTimeMillis()
    ) : BrowserEvent
}

/** 单条捕获的网络请求（限制字段避免过度暴露）。 */
@Serializable
data class CapturedRequest(
    val id: String,
    val tabId: String,
    val url: String,
    val method: String,
    val statusCode: Int = 0,
    val requestHeaders: Map<String, String> = emptyMap(),
    val responseHeaders: Map<String, String> = emptyMap(),
    val startedAt: Long,
    val finishedAt: Long = 0L,
    val errorMessage: String = ""
)

@Serializable
data class ConsoleLine(
    val tabId: String,
    val level: String,
    val message: String,
    val at: Long = System.currentTimeMillis()
)
