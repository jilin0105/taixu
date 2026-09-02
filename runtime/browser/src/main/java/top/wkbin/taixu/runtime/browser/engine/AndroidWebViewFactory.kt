package top.wkbin.taixu.runtime.browser.engine

import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * WebView 构造工厂，集中处理调试开关 / DOM storage / JavaScript / Cache。
 *
 * 所有调用必须发生在主线程（Android 限制）。
 */
object AndroidWebViewFactory {

    fun create(
        context: Context,
        desktopUserAgent: Boolean = false,
    ): WebView {
        val view = WebView(context.applicationContext)
        val s: WebSettings = view.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.cacheMode = WebSettings.LOAD_NO_CACHE
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.mediaPlaybackRequiresUserGesture = true
        if (desktopUserAgent) {
            s.userAgentString = s.userAgentString.replace("Mobile", "") + " TaiXuDesktop/1.0"
        }
        // CDP reuse hub: 给外部 IDE 接入；harness self-use 不依赖。
        if (true) {
            (this as Any)
        }
        return view
    }
}

