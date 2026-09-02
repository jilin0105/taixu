package top.wkbin.taixu.runtime.browser.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import top.wkbin.taixu.core.browser.BrowserFamily
import top.wkbin.taixu.core.browser.BrowserPreferences
import top.wkbin.taixu.core.browser.PageSnapshot
import top.wkbin.taixu.core.model.ToolImageRef
import top.wkbin.taixu.runtime.browser.BrowserEngine
import top.wkbin.taixu.runtime.browser.BrowserEventBus
import top.wkbin.taixu.runtime.browser.BrowserSessionToken
import top.wkbin.taixu.runtime.browser.InAppBrowserViewProvider
import top.wkbin.taixu.runtime.browser.js.JsEvaluator
import top.wkbin.taixu.runtime.browser.screenshot.ScreenshotRecorder
import top.wkbin.taixu.runtime.browser.snapshot.SnapshotBuilder
import top.wkbin.taixu.runtime.browser.capabilities.EngineCapabilities
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray

/**
 * in-app WebView 引擎实现。所有 WebView 操作走主线程 [Handler]，协程侧用 [CompletableDeferred] 等待结果。
 */
class AndroidInAppBrowserEngine(
    private val context: Context,
    override val eventBus: BrowserEventBus,
    private val pool: WebViewTabPool,
) : BrowserEngine, InAppBrowserViewProvider {

    override val descriptor = top.wkbin.taixu.core.browser.BrowserDescriptor(
        family = BrowserFamily.IN_APP,
        displayName = "TaiXu In-App WebView",
        healthy = true,
        capabilities = EngineCapabilities.IN_APP,
        versionTag = "1.0",
        notes = "内置 WebView；通过 mcp__browser__* 调用。",
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val screenshotRecorder = ScreenshotRecorder(context)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val snapshotBuilders = HashMap<String, SnapshotBuilder>()

    private fun builderOf(token: BrowserSessionToken): SnapshotBuilder =
        snapshotBuilders.getOrPut(token.tabId) {
            SnapshotBuilder(token, eventBus, CoroutineScopeHolder.scope())
        }

    override suspend fun openTab(url: String?, activate: Boolean): BrowserSessionToken {
        val token = pool.create(url)
        snapshotBuilders[token.tabId] = SnapshotBuilder(token, eventBus, CoroutineScopeHolder.scope())
        if (activate) pool.attach(token)
        return token
    }

    override suspend fun navigate(tab: BrowserSessionToken, url: String) {
        postMain { pool.viewOf(tab)?.loadUrl(url) }
    }

    override suspend fun snapshot(tab: BrowserSessionToken, maxElements: Int): PageSnapshot {
        val view = pool.viewOf(tab) ?: error("tab ${tab.tabId} not found")
        val builder = builderOf(tab)
        // refresh 同步返回本次扫描结果，避免依赖全局 eventBus 的异步发布造成竞态
        return builder.refresh(view, maxElements)
            ?: PageSnapshot(tab.tabId, tab.url, "", emptyMap(), "empty", 0L)
    }

    override suspend fun click(
        tab: BrowserSessionToken,
        ref: String,
        refSelectorLookup: suspend (BrowserSessionToken, String) -> String?,
    ): Boolean {
        val selector = refSelectorLookup(tab, ref) ?: return false
        val js = JS.wrapSelectorClick(selector)
        val view = pool.viewOf(tab) ?: return false
        return JsEvaluator.evaluate(view, js)?.let { it == "true" } ?: false
    }

    override suspend fun typeInto(
        tab: BrowserSessionToken,
        ref: String,
        text: String,
        refSelectorLookup: suspend (BrowserSessionToken, String) -> String?,
    ): Boolean {
        val selector = refSelectorLookup(tab, ref) ?: return false
        val js = JS.wrapSelectorType(selector, text)
        val view = pool.viewOf(tab) ?: return false
        return JsEvaluator.evaluate(view, js)?.let { it == "true" } ?: false
    }

    override suspend fun press(
        tab: BrowserSessionToken,
        ref: String?,
        key: String,
        refSelectorLookup: suspend (BrowserSessionToken, String) -> String?,
    ): Boolean {
        val view = pool.viewOf(tab) ?: return false
        val js = if (ref == null) {
            JS.wrapActiveElementPress(key)
        } else {
            val selector = refSelectorLookup(tab, ref) ?: return false
            JS.wrapSelectorPress(selector, key)
        }
        return JsEvaluator.evaluate(view, js)?.let { it == "true" } ?: false
    }

    override suspend fun scroll(tab: BrowserSessionToken, deltaY: Int): Boolean {
        val view = pool.viewOf(tab) ?: return false
        return JsEvaluator.evaluate(view, JS.wrapScroll(deltaY))?.let { it == "true" } ?: false
    }

    override suspend fun screenshot(tab: BrowserSessionToken, prefs: BrowserPreferences): ToolImageRef? {
        val view = pool.viewOf(tab) ?: return null
        return screenshotRecorder.capture(view, tab.tabId)
    }

    override suspend fun evaluate(tab: BrowserSessionToken, script: String): String? {
        val view = pool.viewOf(tab) ?: return null
        return JsEvaluator.evaluate(view, script)
    }

    override suspend fun pageSource(tab: BrowserSessionToken, maxBytes: Int): String {
        val view = pool.viewOf(tab) ?: return ""
        val html = JsEvaluator.evaluate(view, "document.documentElement.outerHTML") ?: return ""
        return if (html.length > maxBytes) html.substring(0, maxBytes) + "\n[TRUNCATED]" else html
    }

    override suspend fun back(tab: BrowserSessionToken): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        postMain {
            val v = pool.viewOf(tab)
            val result = if (v != null && v.canGoBack()) { v.goBack(); true } else false
            deferred.complete(result)
        }
        return withTimeoutOrNull(2_000L) { deferred.await() } ?: false
    }

    override suspend fun forward(tab: BrowserSessionToken): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        postMain {
            val v = pool.viewOf(tab)
            val result = if (v != null && v.canGoForward()) { v.goForward(); true } else false
            deferred.complete(result)
        }
        return withTimeoutOrNull(2_000L) { deferred.await() } ?: false
    }

    override suspend fun refresh(tab: BrowserSessionToken) {
        postMain { pool.viewOf(tab)?.reload() }
    }

    override suspend fun closeTab(tab: BrowserSessionToken) {
        postMain { pool.viewOf(tab)?.destroy() }
        pool.onClosed(tab)
        snapshotBuilders.remove(tab.tabId)
    }

    override suspend fun listTabs(): List<BrowserSessionToken> = pool.list()

    override fun activeTab(): BrowserSessionToken? = pool.activeTab.value

    override fun activeWebView(): WebView? = pool.activeWebView()

    override suspend fun setActiveTab(tab: BrowserSessionToken) {
        pool.attach(tab)
    }

    override suspend fun cookiesGet(tab: BrowserSessionToken, url: String?): String {
        val view = pool.viewOf(tab) ?: return ""
        return JsEvaluator.evaluate(view, "document.cookie").orEmpty().trim('"')
    }

    override suspend fun cookiesSet(tab: BrowserSessionToken, url: String, headerLine: String) {
        val view = pool.viewOf(tab) ?: return
        val piece = headerLine.substringBefore(';')
        val name = piece.substringBefore('=').trim()
        val value = piece.substringAfter('=', missingDelimiterValue = "").trim()
        val js = "document.cookie = " + JS.q("$name=$value; path=/")
        JsEvaluator.evaluate(view, js)
    }

    override suspend fun cookiesDelete(tab: BrowserSessionToken, url: String, name: String) {
        val view = pool.viewOf(tab) ?: return
        val js = "document.cookie = " + JS.q("$name=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/")
        JsEvaluator.evaluate(view, js)
    }

    override suspend fun localGet(tab: BrowserSessionToken, key: String): String? {
        val view = pool.viewOf(tab) ?: return null
        return JsEvaluator.evaluate(view, "localStorage.getItem(" + JS.q(key) + ")")?.trim('"')?.takeIf { it.isNotEmpty() }
    }

    override suspend fun localSet(tab: BrowserSessionToken, key: String, value: String) {
        val view = pool.viewOf(tab) ?: return
        JsEvaluator.evaluate(view, "localStorage.setItem(" + JS.q(key) + ", " + JS.q(value) + ")")
    }

    override suspend fun localDelete(tab: BrowserSessionToken, key: String) {
        val view = pool.viewOf(tab) ?: return
        JsEvaluator.evaluate(view, "localStorage.removeItem(" + JS.q(key) + ")")
    }

    override suspend fun localKeys(tab: BrowserSessionToken): List<String> {
        val view = pool.viewOf(tab) ?: return emptyList()
        val raw = JsEvaluator.evaluate(view, "JSON.stringify(Object.keys(localStorage))") ?: return emptyList()
        val arr = runCatching { json.parseToJsonElement(raw.trim('"')).jsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
    }

    override suspend fun sessionGet(tab: BrowserSessionToken, key: String): String? {
        val view = pool.viewOf(tab) ?: return null
        return JsEvaluator.evaluate(view, "sessionStorage.getItem(" + JS.q(key) + ")")?.trim('"')?.takeIf { it.isNotEmpty() }
    }

    override suspend fun sessionSet(tab: BrowserSessionToken, key: String, value: String) {
        val view = pool.viewOf(tab) ?: return
        JsEvaluator.evaluate(view, "sessionStorage.setItem(" + JS.q(key) + ", " + JS.q(value) + ")")
    }

    override suspend fun sessionDelete(tab: BrowserSessionToken, key: String) {
        val view = pool.viewOf(tab) ?: return
        JsEvaluator.evaluate(view, "sessionStorage.removeItem(" + JS.q(key) + ")")
    }

    override suspend fun sessionKeys(tab: BrowserSessionToken): List<String> {
        val view = pool.viewOf(tab) ?: return emptyList()
        val raw = JsEvaluator.evaluate(view, "JSON.stringify(Object.keys(sessionStorage))") ?: return emptyList()
        val arr = runCatching { json.parseToJsonElement(raw.trim('"')).jsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
    }

    override suspend fun shutdown() {
        pool.shutdown()
        snapshotBuilders.clear()
    }

    private suspend inline fun postMain(crossinline block: () -> Unit) {
        val deferred = CompletableDeferred<Unit>()
        mainHandler.post {
            block(); deferred.complete(Unit)
        }
        // #12：超时后主线程 block 仍会照常执行（WebView/主线程无法撤销已派发的操作），
        // 调用方超时返回后不应重试同副作用操作，避免重复执行。
        val done = withTimeoutOrNull(2_000L) { deferred.await() } != null
        if (!done) {
            android.util.Log.w("TaiXuBrowserEngine", "postMain 超时（2s）：操作可能已在页面执行，请勿重试同参数操作")
        }
    }
}

/** JS 字符串与脚本片段构造：所有引号 / 换行 / Unicode 经 [q] 转义，避免 here-string 解析陷阱。 */
private object JS {

    fun q(value: String): String {
        val sb = StringBuilder("'")
        for (c in value) when (c) {
            '\\' -> sb.append("\\\\")
            '\'' -> sb.append("\\'")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            else -> sb.append(c)
        }
        sb.append('\'')
        return sb.toString()
    }

    fun wrapSelectorClick(selector: String): String =
        "(function(){var n=document.querySelector(${q(selector)});if(!n)return false;n.click();return true;})()"

    fun wrapSelectorType(selector: String, text: String): String =
        "(function(){var n=document.querySelector(${q(selector)});if(!n)return false;n.focus();n.value=${q(text)};n.dispatchEvent(new Event('input',{bubbles:true}));return true;})()"

    fun wrapActiveElementPress(key: String): String =
        "(function(){var k=${q(key)};var ev=new KeyboardEvent('keydown',{key:k,bubbles:true});document.activeElement.dispatchEvent(ev);return true;})()"

    fun wrapSelectorPress(selector: String, key: String): String =
        "(function(){var n=document.querySelector(${q(selector)});if(!n)return false;var k=${q(key)};var ev=new KeyboardEvent('keydown',{key:k,bubbles:true});n.dispatchEvent(ev);return true;})()"

    fun wrapScroll(deltaY: Int): String =
        "(function(){window.scrollBy(0,${deltaY});return true;})()"
}

/** 全局 main scope（用于 [BrowserEngine] 内嵌的 WebView 回调 publish 事件）。 */
private object CoroutineScopeHolder {
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.Main.immediate + kotlinx.coroutines.SupervisorJob()
    )
    fun scope() = scope
}
