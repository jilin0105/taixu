package top.wkbin.taixu.harness.browser

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import top.wkbin.taixu.harness.mcp.server.McpServerRuntime
import top.wkbin.taixu.runtime.browser.BrowserRegistry
import top.wkbin.taixu.runtime.browser.BrowserRegistryImpl
import top.wkbin.taixu.runtime.browser.engine.AndroidInAppBrowserEngine
import top.wkbin.taixu.runtime.browser.engine.WebViewTabPool
import top.wkbin.taixu.core.browser.BrowserFamily

/**
 * 把 in-process 浏览器 MCP server 注入到 harness 流水线，并负责：
 *  - 在 ApplicationContext 上拉起 [AndroidInAppBrowserEngine] 注册到 [BrowserRegistry];
 *  - 按用户偏好（allowRemoteConnect / desktopUserAgent）在 loopback 或 0.0.0.0 端口上启动 [McpServerRuntime];
 *  - 桌面外接开启时生成 Bearer Token（McpAuthFilter 强制校验）。
 *
 * 调用时机：Application.onCreate 后由 AppScope 协程调用一次 [bootstrap]。
 */
@Singleton
class BrowserMcpBootstrap @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtime: McpServerRuntime,
    private val registry: BrowserRegistry,
    private val browserPrefs: top.wkbin.taixu.core.datastore.BrowserPreferences,
) {
    /** 注册引擎并启动 HTTP server；幂等。按用户偏好（#4）决定桌面外接：allowRemote 时绑定 0.0.0.0 并生成 Bearer Token。 */
    fun bootstrap(): Boolean {
        if (runtime.isRunning) return true
        val regImpl = registry as? BrowserRegistryImpl ?: return false
        val prefs = readPrefs()
        if (registry.get(BrowserFamily.IN_APP) == null) {
            val pool = WebViewTabPool(context, registry.eventBus, desktopUserAgent = prefs.desktopUserAgent)
            val engine = AndroidInAppBrowserEngine(context, registry.eventBus, pool)
            regImpl.registerEngine(engine)
        }
        val allowRemote = prefs.allowRemoteConnect
        val token = if (allowRemote) generateToken() else null
        val ok = runtime.start(loopbackOnly = !allowRemote, token = token, port = McpServerRuntime.defaultPort)
        if (ok) {
            val host = if (allowRemote) "0.0.0.0" else McpServerRuntime.loopbackHost
            Log.i(TAG, "BrowserMcpServer 已启动 http://$host:${runtime.port}/mcp" + (token?.let { " (Bearer token=$it)" } ?: ""))
        } else {
            Log.w(TAG, "BrowserMcpServer 启动失败（端口被占用）")
        }
        return ok
    }

    /** 启动时读一次真实偏好（IO 协程内调用，单次 DataStore first() 毫秒级；失败兜底 DEFAULT）。 */
    private fun readPrefs(): top.wkbin.taixu.core.browser.BrowserPreferences = runCatching {
        runBlocking {
            top.wkbin.taixu.core.browser.BrowserPreferences(
                defaultFamily = browserPrefs.defaultFamily().first(),
                homeUrl = browserPrefs.homeUrl().first(),
                coBrowsingEnabled = browserPrefs.coBrowsingEnabled().first(),
                allowRemoteConnect = browserPrefs.allowRemoteConnect().first(),
                allowEvalJs = browserPrefs.allowEvalJs().first(),
                desktopUserAgent = browserPrefs.desktopUserAgent().first(),
                maxCaptureBytes = browserPrefs.maxCaptureBytes().first(),
            )
        }
    }.getOrDefault(top.wkbin.taixu.core.browser.BrowserPreferences.DEFAULT)

    private fun generateToken(): String = java.util.UUID.randomUUID().toString().replace("-", "")

    fun stop() {
        try { runtime.stop() } catch (t: Throwable) { Log.w(TAG, "stop: ${t.message}") }
    }

    companion object {
        const val TAG = "TaiXuMcpBootstrap"
    }
}
