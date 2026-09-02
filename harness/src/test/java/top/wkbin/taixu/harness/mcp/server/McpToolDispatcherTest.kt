package top.wkbin.taixu.harness.mcp.server

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.core.browser.BrowserDescriptor
import top.wkbin.taixu.core.browser.BrowserFamily
import top.wkbin.taixu.core.browser.BrowserPreferences
import top.wkbin.taixu.core.browser.PageSnapshot
import top.wkbin.taixu.core.model.ToolImageRef
import top.wkbin.taixu.runtime.browser.BrowserEngine
import top.wkbin.taixu.runtime.browser.BrowserEventBus
import top.wkbin.taixu.runtime.browser.BrowserSessionToken
import top.wkbin.taixu.runtime.browser.tools.BrowserMcpTools

class McpToolDispatcherTest {

    @Test
    fun `tools call uses standard MCP content envelope`() = runBlocking {
        val engine = FakeBrowserEngine()
        val dispatcher = dispatcher(engine)

        val result = dispatcher.dispatch("browser.current_url", JsonObject(emptyMap()))

        assertFalse(result.getValue("isError").jsonPrimitive.boolean)
        val content = result.getValue("content").jsonArray
        assertEquals("text", content.single().jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("https://example.test", content.single().jsonObject.getValue("text").jsonPrimitive.content)
    }

    @Test
    fun `missing tab targets current active tab`() = runBlocking {
        val engine = FakeBrowserEngine()
        val dispatcher = dispatcher(engine)

        dispatcher.dispatch(
            "browser.navigate",
            buildJsonObject { put("url", JsonPrimitive("https://openai.com")) },
        )

        assertEquals(engine.active.tabId, engine.lastNavigatedTab?.tabId)
    }

    @Test
    fun `tool list declares arguments and hides unimplemented file tools`() {
        val tools = dispatcher(FakeBrowserEngine()).listTools()
        val navigate = tools.single { it.getValue("name").jsonPrimitive.content == "browser.navigate" }
        val schema = navigate.getValue("inputSchema").jsonObject

        assertTrue(schema.getValue("required").jsonArray.any { it.jsonPrimitive.content == "url" })
        assertTrue(schema.getValue("properties").jsonObject.containsKey("url"))
        assertFalse(tools.any { it.getValue("name").jsonPrimitive.content.startsWith("browser.file_") })
    }

    private fun dispatcher(engine: FakeBrowserEngine): McpToolDispatcher {
        val tools = BrowserMcpTools(
            engines = emptyList(),
            engineSelector = { engine },
            prefs = BrowserPreferences.DEFAULT,
        )
        return McpToolDispatcher(tools)
    }
}

private class FakeBrowserEngine : BrowserEngine {
    override val eventBus = BrowserEventBus()
    override val descriptor = BrowserDescriptor(
        family = BrowserFamily.IN_APP,
        displayName = "fake",
        healthy = true,
        capabilities = emptySet(),
    )
    val active = BrowserSessionToken("active-tab", BrowserFamily.IN_APP, url = "https://example.test")
    var lastNavigatedTab: BrowserSessionToken? = null

    init {
        runBlocking { eventBus.publish(top.wkbin.taixu.runtime.browser.BrowserEvent.PageChanged(active.tabId, active.url, "Example")) }
    }

    override suspend fun openTab(url: String?, activate: Boolean) = active
    override suspend fun navigate(tab: BrowserSessionToken, url: String) { lastNavigatedTab = tab }
    override suspend fun snapshot(tab: BrowserSessionToken, maxElements: Int) =
        PageSnapshot(tab.tabId, active.url, "Example", emptyMap(), "fake", 0L)
    override suspend fun click(tab: BrowserSessionToken, ref: String, refSelectorLookup: suspend (BrowserSessionToken, String) -> String?) = true
    override suspend fun typeInto(tab: BrowserSessionToken, ref: String, text: String, refSelectorLookup: suspend (BrowserSessionToken, String) -> String?) = true
    override suspend fun press(tab: BrowserSessionToken, ref: String?, key: String, refSelectorLookup: suspend (BrowserSessionToken, String) -> String?) = true
    override suspend fun scroll(tab: BrowserSessionToken, deltaY: Int) = true
    override suspend fun screenshot(tab: BrowserSessionToken, prefs: BrowserPreferences): ToolImageRef? = null
    override suspend fun evaluate(tab: BrowserSessionToken, script: String): String? = ""
    override suspend fun pageSource(tab: BrowserSessionToken, maxBytes: Int) = ""
    override suspend fun back(tab: BrowserSessionToken) = true
    override suspend fun forward(tab: BrowserSessionToken) = true
    override suspend fun refresh(tab: BrowserSessionToken) = Unit
    override suspend fun closeTab(tab: BrowserSessionToken) = Unit
    override suspend fun listTabs() = listOf(active)
    override fun activeTab(): BrowserSessionToken = active
    override suspend fun setActiveTab(tab: BrowserSessionToken) = Unit
    override suspend fun cookiesGet(url: String?) = ""
    override suspend fun cookiesSet(url: String, headerLine: String) = Unit
    override suspend fun cookiesDelete(url: String, name: String) = Unit
    override suspend fun localGet(key: String): String? = null
    override suspend fun localSet(key: String, value: String) = Unit
    override suspend fun localDelete(key: String) = Unit
    override suspend fun localKeys() = emptyList<String>()
    override suspend fun sessionGet(key: String): String? = null
    override suspend fun sessionSet(key: String, value: String) = Unit
    override suspend fun sessionDelete(key: String) = Unit
    override suspend fun sessionKeys() = emptyList<String>()
    override suspend fun shutdown() = Unit
}
