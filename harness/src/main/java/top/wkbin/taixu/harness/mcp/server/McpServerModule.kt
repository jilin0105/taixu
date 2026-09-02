package top.wkbin.taixu.harness.mcp.server

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import top.wkbin.taixu.core.browser.BrowserPreferences
import top.wkbin.taixu.runtime.browser.BrowserEngine
import top.wkbin.taixu.runtime.browser.BrowserRegistry
import top.wkbin.taixu.runtime.browser.tools.BrowserMcpResources
import top.wkbin.taixu.runtime.browser.tools.BrowserMcpTools

/**
 * 装配进程内 MCP Server 相关的 Hilt 注入。
 *
 * - [BrowserMcpTools] / [BrowserMcpResources] 接受 `List<BrowserEngine>` 等运行时参数，Hilt 无法自动注入，
 *   由本模块集中构造，确保 [McpServerRuntime] 在容器内即可装配。
 */
@Module
@InstallIn(SingletonComponent::class)
object McpServerModule {

    @Provides
    @Singleton
    fun provideBrowserMcpResources(registry: BrowserRegistry): BrowserMcpResources =
        BrowserMcpResources(engineProvider = { runCatching { registry.getDefault() }.getOrNull() })

    @Provides
    @Singleton
    fun provideBrowserMcpTools(
        registry: BrowserRegistry,
        browserPrefs: top.wkbin.taixu.core.datastore.BrowserPreferences,
    ): BrowserMcpTools {
        // #14：启动时把 datastore 里的真实用户偏好映射为工具层快照（AppScope 首次注入、IO 协程内构造，
        // 单次 DataStore first() 毫秒级；读取失败兜底 DEFAULT，不阻塞启动）。
        val prefs = runCatching {
            runBlocking {
                BrowserPreferences(
                    defaultFamily = browserPrefs.defaultFamily().first(),
                    homeUrl = browserPrefs.homeUrl().first(),
                    coBrowsingEnabled = browserPrefs.coBrowsingEnabled().first(),
                    allowRemoteConnect = browserPrefs.allowRemoteConnect().first(),
                    allowEvalJs = browserPrefs.allowEvalJs().first(),
                    desktopUserAgent = browserPrefs.desktopUserAgent().first(),
                    maxCaptureBytes = browserPrefs.maxCaptureBytes().first(),
                )
            }
        }.getOrDefault(BrowserPreferences.DEFAULT)
        return BrowserMcpTools(
            engines = registry.collectEngines(),
            engineSelector = { token -> registry.get(token.family) },
            prefs = prefs,
        )
    }
}

/**
 * 返回 [BrowserRegistry] 当前注册的所有 [BrowserEngine]。`BrowserRegistry` 没有暴露 engines 列表，
 * 这里通过 `descriptors` + `get(family)` 推断健康状态后返回。
 */
private fun BrowserRegistry.collectEngines(): List<BrowserEngine> =
    descriptors.value.mapNotNull { get(it.family) }
