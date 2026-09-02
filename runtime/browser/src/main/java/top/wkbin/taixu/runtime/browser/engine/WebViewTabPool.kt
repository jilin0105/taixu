package top.wkbin.taixu.runtime.browser.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.wkbin.taixu.core.browser.BrowserFamily
import top.wkbin.taixu.runtime.browser.BrowserEventBus
import top.wkbin.taixu.runtime.browser.BrowserSessionToken
import top.wkbin.taixu.runtime.browser.BrowserEvent

/**
 * 浏览器 tab 池：线程安全地持有 WebView 实例与 token 的映射。WebView 必须主线程创建，
 * 所以 WebView 实例放在主线程 Handler 持有的 buckets 中。
 */
class WebViewTabPool(
    private val context: Context,
    private val eventBus: BrowserEventBus,
    private val desktopUserAgent: Boolean = false,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutex = Mutex()

    private data class Slot(val token: BrowserSessionToken, val view: WebView)

    private val byToken = ConcurrentHashMap<String, Slot>()
    private val _activeTab = MutableStateFlow<BrowserSessionToken?>(null)
    val activeTab: StateFlow<BrowserSessionToken?> = _activeTab.asStateFlow()

    suspend fun create(initialUrl: String?): BrowserSessionToken = mutex.withLock {
        val token = BrowserSessionToken(
            tabId = "t:" + System.nanoTime().toString(36),
            family = BrowserFamily.IN_APP,
            title = "",
            url = initialUrl ?: "about:blank",
        )
        withContext(Dispatchers.Main.immediate) {
            val view = AndroidWebViewFactory.create(context, desktopUserAgent)
            WebViewClients.attach(view, eventBus, token, this@WebViewTabPool)
            byToken[token.tabId] = Slot(token, view)
            initialUrl?.let {
                view.loadUrl(it)
            }
        }
        _activeTab.value = token
        eventBus.setActiveTab(token)
        token
    }

    suspend fun attach(token: BrowserSessionToken) = mutex.withLock {
        require(byToken.containsKey(token.tabId)) { "tab ${token.tabId} not found" }
        _activeTab.value = token
        eventBus.setActiveTab(token)
        mainHandler.post {
            byToken[token.tabId]?.view?.requestFocus()
        }
    }

    /** 取得主线程上的 WebView；UI / engine 实现需要 view 来 evaluateJavascript / loadUrl 等。 */
    fun viewOf(token: BrowserSessionToken): WebView? = byToken[token.tabId]?.view
    fun activeWebView(): WebView? = viewOf(_activeTab.value ?: return null)

    /** Switch back to default tab if closed. */
    suspend fun onClosed(token: BrowserSessionToken) = mutex.withLock {
        byToken.remove(token.tabId)
        if (_activeTab.value?.tabId == token.tabId) {
            _activeTab.value = byToken.values.firstOrNull()?.token
        }
        eventBus.setActiveTab(_activeTab.value)
        eventBus.resetForTab(token.tabId)
    }

    suspend fun shutdown() = mutex.withLock {
        val all = byToken.values.toList()
        byToken.clear()
        _activeTab.value = null
        eventBus.setActiveTab(null)
        all.forEach { slot ->
            mainHandler.post { slot.view.destroy() }
        }
    }

    fun list(): List<BrowserSessionToken> = byToken.values.map { it.token }

    /** 主线程 broadcast：提示 Network 层有事件。 */
    fun publishFromMain(event: BrowserEvent) { /* bridge used by clients */ }
}
