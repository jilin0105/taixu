package top.wkbin.taixu.runtime.browser.js

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * JavaScript 注入与执行工具；协程视角封装 `WebView.evaluateJavascript`。
 *
 * 任何 evaluateJavascript 都受超时保护（默认 5s），避免 target 页面 JS 死循环卡住 UI 流。
 */
object JsEvaluator {

    suspend fun evaluate(view: WebView, script: String, timeoutMs: Long = 5_000L): String? {
        val main = Handler(Looper.getMainLooper())
        val deferred = CompletableDeferred<String?>()
        main.post {
            try {
                view.evaluateJavascript(script) { result ->
                    deferred.complete(result)
                }
            } catch (t: Throwable) {
                deferred.completeExceptionally(t)
            }
        }
        return try {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            // #12：超时放弃等待；WebView 无法中止已派发的 evaluateJavascript，页面内副作用可能仍执行，
            // 回调结果因 deferred 已超时而被丢弃（不写回任何共享状态）。
            android.util.Log.w("JsEvaluator", "evaluateJavascript 超时(${timeoutMs}ms)，页面内脚本可能仍在执行")
            null
        }
    }

    /** 全局 `JSON.stringify` 后注入 payload；模型仅看 base64-free 文本结果。 */
    suspend fun evaluateJson(view: WebView, jsExpression: String, timeoutMs: Long = 5_000L): String? {
        val wrapped = "(function(){ try { return JSON.stringify($jsExpression) } catch(e){ return '{\"error\":\"'+e.message+'\"}' } })()"
        return evaluate(view, wrapped, timeoutMs)
    }
}
