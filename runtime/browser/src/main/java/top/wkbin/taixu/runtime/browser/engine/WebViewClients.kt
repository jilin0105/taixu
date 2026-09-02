package top.wkbin.taixu.runtime.browser.engine

import android.graphics.Bitmap
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.wkbin.taixu.runtime.browser.BrowserEvent
import top.wkbin.taixu.runtime.browser.BrowserEventBus
import top.wkbin.taixu.runtime.browser.BrowserSessionToken
import top.wkbin.taixu.runtime.browser.network.NetworkInterceptor
import top.wkbin.taixu.runtime.browser.snapshot.SnapshotBuilder

/**
 * 给 WebView 装好 [WebViewClient]/[WebChromeClient]，把所有事件经过 [scope] 派发到 [eventBus]。
 *
 * 注：WebView 回调线程就是主线程，所以用 [Dispatchers.Main.immediate] 直接发布。
 */
object WebViewClients {

    fun attach(
        view: WebView,
        eventBus: BrowserEventBus,
        token: BrowserSessionToken,
        pool: WebViewTabPool,
    ): WebView {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val networkInterceptor = NetworkInterceptor(token, eventBus, scope)
        val snapshotBuilder = SnapshotBuilder(token, eventBus, scope)

        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: android.webkit.WebResourceRequest,
            ): android.webkit.WebResourceResponse? {
                networkInterceptor.onRequestStart(request)
                return null
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                scope.launch {
                    eventBus.publish(BrowserEvent.PageChanged(token.tabId, url ?: "", view.title ?: ""))
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                scope.launch {
                    eventBus.publish(BrowserEvent.PageChanged(token.tabId, url ?: "", view.title ?: ""))
                    snapshotBuilder.refresh(view)
                }
            }
        }

        view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                val msg = message ?: return true
                scope.launch {
                    eventBus.publish(BrowserEvent.ConsoleLogged(token.tabId, msg.messageLevel().name, msg.message(), 0L))
                }
                return true
            }
        }
        return view
    }
}
