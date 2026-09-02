package top.wkbin.taixu.runtime.browser.storage

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Cookie / LocalStorage / SessionStorage 控制面板。
 *
 * MVP 设计：所有操作同步过 [Handler] 到主线程；不缓存 view，调用方把 [WebView] 直接传进来。
 *
 * Cookie 由 [CookieManager] 静态托管，与 WebView 同进程共享；Local / Session 由每个
 * WebView 各自维护。
 */
class StorageController(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val cookieManager: CookieManager by lazy { CookieManager.getInstance().also { it.setAcceptCookie(true) } }

    // ===== Cookie =====

    suspend fun cookiesGet(url: String?): String {
        val deferred = CompletableDeferred<String>()
        mainHandler.post {
            val s = if (url.isNullOrBlank()) cookieManager.getCookie(url) ?: "" else cookieManager.getCookie(url) ?: ""
            deferred.complete(s)
        }
        return withTimeoutOrNull(2_000L) { deferred.await() }.orEmpty()
    }

    suspend fun cookiesSet(url: String, headerLine: String) {
        val deferred = CompletableDeferred<Unit>()
        mainHandler.post {
            cookieManager.setCookie(url, headerLine)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) cookieManager.flush()
            deferred.complete(Unit)
        }
        withTimeoutOrNull(2_000L) { deferred.await() }
    }

    suspend fun cookiesDelete(url: String, name: String) {
        val deferred = CompletableDeferred<Unit>()
        mainHandler.post {
            cookieManager.setCookie(url, "$name=; Path=/; Domain=${hostOf(url)}; Max-Age=0")
            runCatching { cookieManager.removeSessionCookies(null) }
            cookieManager.flush()
            deferred.complete(Unit)
        }
        withTimeoutOrNull(2_000L) { deferred.await() }
    }

    private fun hostOf(url: String): String = runCatching {
        java.net.URL(url).host.removePrefix("www.")
    }.getOrDefault("")

    // ===== Local / Session Storage（依附当前 WebView） =====

    suspend fun localGet(view: WebView, key: String): String? =
        evaluate(view, "try { return localStorage.getItem(${jsStr(key)}) } catch(e) { return null }")

    suspend fun localSet(view: WebView, key: String, value: String) {
        evaluate(view, "localStorage.setItem(${jsStr(key)}, ${jsStr(value)})")
    }

    suspend fun localDelete(view: WebView, key: String) {
        evaluate(view, "localStorage.removeItem(${jsStr(key)})")
    }

    suspend fun localKeys(view: WebView): List<String> {
        val raw = evaluate(view, "Object.keys(localStorage).map(k => JSON.stringify(k)).join(',')").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split("|||").filter { it.isNotBlank() }.map { unquote(it) }
    }

    suspend fun sessionGet(view: WebView, key: String): String? =
        evaluate(view, "try { return sessionStorage.getItem(${jsStr(key)}) } catch(e) { return null }")

    suspend fun sessionSet(view: WebView, key: String, value: String) {
        evaluate(view, "sessionStorage.setItem(${jsStr(key)}, ${jsStr(value)})")
    }

    suspend fun sessionDelete(view: WebView, key: String) {
        evaluate(view, "sessionStorage.removeItem(${jsStr(key)})")
    }

    private suspend fun evaluate(view: WebView, js: String, timeoutMs: Long = 2_000L): String? {
        val deferred = CompletableDeferred<String?>()
        mainHandler.post {
            try {
                view.evaluateJavascript(js) { result -> deferred.complete(result) }
            } catch (t: Throwable) {
                deferred.completeExceptionally(t)
            }
        }
        return withTimeoutOrNull(timeoutMs) { deferred.await() }
    }

    private fun unquote(s: String): String =
        s.trim().let { if (it.startsWith("\"") && it.endsWith("\"")) it.substring(1, it.length - 1) else it }
            .replace("\\\"", "\"").replace("\\\\", "\\")

    private fun jsStr(s: String): String = "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"
}

