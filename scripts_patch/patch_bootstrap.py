# -*- coding: utf-8 -*-
import io
f = r'C:\Users\wangk\Desktop\LinuxAIRuntime\harness\src\main\java\top\wkbin\taixu\harness\browser\BrowserMcpBootstrap.kt'
raw = io.open(f, 'rb').read()
print("BOM:", raw[:3] == b'\xef\xbb\xbf')
t = raw.decode('utf-8-sig').replace('\r\n', '\n')

def apply(old, new, label):
    global t
    if old in t:
        t = t.replace(old, new, 1)
        print("OK:", label)
    else:
        print("NOT FOUND:", label)

# 1. imports
apply('''import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import top.wkbin.taixu.core.model.McpServerConfig
import top.wkbin.taixu.core.model.McpTransportType
import top.wkbin.taixu.harness.mcp.server.McpServerRuntime
import top.wkbin.taixu.runtime.browser.BrowserRegistry
import top.wkbin.taixu.runtime.browser.BrowserRegistryImpl
import top.wkbin.taixu.runtime.browser.engine.AndroidInAppBrowserEngine
import top.wkbin.taixu.runtime.browser.engine.WebViewTabPool
import top.wkbin.taixu.core.browser.BrowserFamily''',
'''import android.content.Context
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
import top.wkbin.taixu.core.browser.BrowserFamily''',
"imports")

# 2. 构造参数 + 删除 builtInConfig + 重写 bootstrap
apply('''@Singleton
class BrowserMcpBootstrap @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtime: McpServerRuntime,
    private val registry: BrowserRegistry,
) {
    fun builtInConfig(
        port: Int = McpServerRuntime.defaultPort,
        token: String? = null,
        allowRemote: Boolean = false,
    ): McpServerConfig = McpServerConfig(
        id = "taixu-browser-builtin",
        name = "TaiXu Browser (Built-in)",
        description = "TaiXu 内置浏览器 MCP server（in-process）；通过 ${
            if (allowRemote) "0.0.0.0:$port/mcp" else "127.0.0.1:$port/mcp"
        } 暴露。",
        transportType = McpTransportType.SSE,
        serverUrl = "http://127.0.0.1:$port/mcp",
        isEnabled = true,
        isBuiltin = true,
        builtinRisk = "medium",
        bootstrapOrder = -100,
    )

    /** 注册引擎并启动 HTTP server；幂等。 */
    fun bootstrap(): Boolean {
        if (runtime.isRunning) return true
        val regImpl = registry as? BrowserRegistryImpl ?: return false
        if (registry.get(BrowserFamily.IN_APP) == null) {
            val pool = WebViewTabPool(context, registry.eventBus, desktopUserAgent = false)
            val engine = AndroidInAppBrowserEngine(context, registry.eventBus, pool)
            regImpl.registerEngine(engine)
        }
        val ok = runtime.start(loopbackOnly = true, token = null, port = McpServerRuntime.defaultPort)
        if (ok) {
            Log.i(TAG, "BrowserMcpServer 已启动 http://${McpServerRuntime.loopbackHost}:${runtime.port}/mcp")
        } else {
            Log.w(TAG, "BrowserMcpServer 启动失败（端口被占用）")
        }
        return ok
    }''',
'''@Singleton
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

    private fun generateToken(): String = java.util.UUID.randomUUID().toString().replace("-", "")''',
"constructor + bootstrap rewrite")

io.open(f, 'w', encoding='utf-8', newline='\n').write(t)
print("DONE")
