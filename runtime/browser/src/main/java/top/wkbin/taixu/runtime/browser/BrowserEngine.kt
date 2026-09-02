package top.wkbin.taixu.runtime.browser

import top.wkbin.taixu.core.browser.BrowserFamily
import top.wkbin.taixu.core.browser.BrowserPreferences
import top.wkbin.taixu.core.browser.PageSnapshot
import top.wkbin.taixu.core.model.ToolImageRef
import android.webkit.WebView

/**
 * 浏览器引擎的统一操作接口。一类引擎（in-app WebView / External Chrome CT / Remote CDP）对应一个实现。
 *
 * 设计要点：
 * - 所有动作都是 suspend，**不**阻塞主线程；UI 只通过 StateFlow 订阅结果；
 * - `navigate / click / type / press` 等依赖 ref；模型给出 ref + 真实 selector 由 [runtime.browser.snapshot.RefResolver] 翻译；
 * - `click/type/press` 接收的 `refResolver` 回调用于回查 ref → selector；RefResolver 由宿主注入。
 */
interface BrowserEngine {
    val descriptor: top.wkbin.taixu.core.browser.BrowserDescriptor
    val eventBus: BrowserEventBus
    val family: BrowserFamily get() = descriptor.family

    /** New or activate a tab; returns the resulting token. */
    suspend fun openTab(url: String? = null, activate: Boolean = true): BrowserSessionToken
    suspend fun navigate(tab: BrowserSessionToken, url: String)
    suspend fun snapshot(tab: BrowserSessionToken, maxElements: Int = 200): PageSnapshot
    suspend fun click(
        tab: BrowserSessionToken,
        ref: String,
        refSelectorLookup: suspend (tab: BrowserSessionToken, ref: String) -> String?,
    ): Boolean
    suspend fun typeInto(
        tab: BrowserSessionToken,
        ref: String,
        text: String,
        refSelectorLookup: suspend (tab: BrowserSessionToken, ref: String) -> String?,
    ): Boolean
    suspend fun press(
        tab: BrowserSessionToken,
        ref: String?,
        key: String,
        refSelectorLookup: suspend (tab: BrowserSessionToken, ref: String) -> String?,
    ): Boolean
    suspend fun scroll(
        tab: BrowserSessionToken,
        deltaY: Int,
    ): Boolean
    suspend fun screenshot(tab: BrowserSessionToken, prefs: BrowserPreferences): ToolImageRef?
    suspend fun evaluate(tab: BrowserSessionToken, script: String): String?
    suspend fun pageSource(tab: BrowserSessionToken, maxBytes: Int = 60_000): String
    suspend fun back(tab: BrowserSessionToken): Boolean
    suspend fun forward(tab: BrowserSessionToken): Boolean
    suspend fun refresh(tab: BrowserSessionToken)
    suspend fun closeTab(tab: BrowserSessionToken)
    suspend fun listTabs(): List<BrowserSessionToken>
    fun activeTab(): BrowserSessionToken?
    suspend fun setActiveTab(tab: BrowserSessionToken)

    // Storage（均按指定 tab 操作，避免多 tab 时误操作 activeTab）
    suspend fun cookiesGet(tab: BrowserSessionToken, url: String?): String
    suspend fun cookiesSet(tab: BrowserSessionToken, url: String, headerLine: String)
    suspend fun cookiesDelete(tab: BrowserSessionToken, url: String, name: String)
    suspend fun localGet(tab: BrowserSessionToken, key: String): String?
    suspend fun localSet(tab: BrowserSessionToken, key: String, value: String)
    suspend fun localDelete(tab: BrowserSessionToken, key: String)
    suspend fun localKeys(tab: BrowserSessionToken): List<String>
    suspend fun sessionGet(tab: BrowserSessionToken, key: String): String?
    suspend fun sessionSet(tab: BrowserSessionToken, key: String, value: String)
    suspend fun sessionDelete(tab: BrowserSessionToken, key: String)
    suspend fun sessionKeys(tab: BrowserSessionToken): List<String>

    suspend fun shutdown()
}

/** Implemented only by an engine whose live view can be embedded in the Compose browser screen. */
interface InAppBrowserViewProvider {
    fun activeWebView(): WebView?
}

