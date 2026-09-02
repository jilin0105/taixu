package top.wkbin.taixu.runtime.browser.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.View
import android.webkit.WebView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import top.wkbin.taixu.core.model.ToolImageRef

/**
 * 把当前 WebView 内容截图落盘到 `cacheDir/taixu-browser/screenshots/<tabId>/<ts>.png`。
 *
 * 实现策略：在主线程上 `view.draw(canvas)` 软渲到 ARGB_8888 Bitmap，然后切到 IO 线程压缩为 PNG。
 *
 * 注：MVP 不做 PixelCopy（API 24+ 可加），原因是它需要宿主 Activity Window；软渲对
 * 多数简单 HTML 页面已足够清晰，复杂动画场景 v1.1 升级。
 */
class ScreenshotRecorder(private val context: Context) {

    private val ioThread by lazy { HandlerThread("taixu-browser-screenshot").apply { start() } }
    private val ioHandler by lazy { Handler(ioThread.looper) }
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun capture(
        view: WebView,
        tabId: String,
        preferredWidth: Int? = null,
        preferredHeight: Int? = null,
        timeoutMs: Long = 8_000L,
    ): ToolImageRef? {
        val deferred = CompletableDeferred<Bitmap?>()
        mainHandler.post {
            val w = preferredWidth ?: view.width.takeIf { it > 0 } ?: 1080
            val h = preferredHeight ?: view.height.takeIf { it > 0 } ?: 1920
            // 离屏 / 从未 layout 的 WebView 先强制 measure+layout，避免软渲空白
            if (view.width <= 0 || view.height <= 0) {
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, w, h)
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bmp)
                view.draw(canvas)
                deferred.complete(bmp)
            } catch (t: Throwable) {
                bmp.recycle()
                deferred.complete(null)
            }
        }
        val bmp = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: return null
        val file = writePngAsync(tabId, bmp)
        return ToolImageRef(
            id = UUID.randomUUID().toString(),
            uri = file,
            mime = "image/png",
            width = bmp.width,
            height = bmp.height,
            caption = "browser screenshot tab=$tabId",
        )
    }

    private fun writePngAsync(tabId: String, bmp: Bitmap): String {
        val dir = File(context.cacheDir, "taixu-browser/screenshots/$tabId").apply { mkdirs() }
        val filename = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date()) + ".png"
        val file = File(dir, filename)
        val done = CompletableDeferred<String>()
        ioHandler.post {
            runCatching {
                FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
            }
            bmp.recycle()
            done.complete(file.absolutePath)
        }
        return runCatching { kotlinx.coroutines.runBlocking { done.await() } }.getOrDefault(file.absolutePath)
    }
}
