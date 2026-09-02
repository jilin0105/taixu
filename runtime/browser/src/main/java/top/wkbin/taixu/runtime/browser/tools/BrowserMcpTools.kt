package top.wkbin.taixu.runtime.browser.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.wkbin.taixu.core.browser.BrowserCapability
import top.wkbin.taixu.core.browser.BrowserPreferences
import top.wkbin.taixu.core.browser.BrowserRisk
import top.wkbin.taixu.core.model.ToolImageRef
import top.wkbin.taixu.runtime.browser.BrowserEngine
import top.wkbin.taixu.runtime.browser.BrowserSessionToken

/**
 * mcp__browser__* 工具调度器。
 *
 * `tools/list` 的描述由 [list] 提供；`tools/call` 的入参由 [invoke] 翻译成 [BrowserEngine] 方法。
 *
 * 注：所有调用都是 `suspend`，由调用方在自己的协程里 await；浏览器能力列表由 [BrowserEngine.descriptor.capabilities] 预先判定。
 */
class BrowserMcpTools(
    private val engines: List<BrowserEngine>,
    private val engineSelector: (BrowserSessionToken) -> BrowserEngine?,
    private val prefs: BrowserPreferences,
) {
    fun list(): List<ToolSpec> = TOOLS

    suspend fun invoke(toolName: String, args: JsonObject): InvokeResult {
        val spec = TOOLS.firstOrNull { it.name == toolName }
            ?: return InvokeResult.error("未知工具：$toolName")
        val engine = pickEngine(explicitTokenOf(args)) ?: return InvokeResult.error("无浏览器引擎")
        return when (toolName) {
            "browser.open" -> {
                val url = args["url"]?.asString() ?: prefs.homeUrl
                val token = engine.openTab(url)
                InvokeResult.okMessage("opened tab=${token.tabId} url=${url}")
            }
            "browser.navigate" -> {
                val url = args["url"]?.asString().orEmpty()
                if (url.isBlank()) InvokeResult.error("缺少 url 参数") else {
                engine.navigate(tokenOf(args, engine), url); InvokeResult.okMessage("navigated=$url")
                }
            }
            "browser.back" -> InvokeResult.okMessage(if (engine.back(tokenOf(args, engine))) "ok" else "noop")
            "browser.forward" -> InvokeResult.okMessage(if (engine.forward(tokenOf(args, engine))) "ok" else "noop")
            "browser.refresh" -> { engine.refresh(tokenOf(args, engine)); InvokeResult.okMessage("refreshed") }
            "browser.list_tabs" -> InvokeResult.okMessage(
                engine.listTabs().joinToString("\n") { "${it.tabId}\t${it.url}\t${it.title}" }
            )
            "browser.close_tab" -> { engine.closeTab(tokenOf(args, engine)); InvokeResult.okMessage("closed") }
            "browser.snapshot" -> handleSnapshot(engine, args)
            "browser.click" -> handleWithRef(engine, args, ::click)
            "browser.type" -> handleType(engine, args)
            "browser.press" -> handlePress(engine, args)
            "browser.scroll" -> {
                val deltaY = args["deltaY"]?.asInt() ?: 300
                InvokeResult.okMessage(if (engine.scroll(tokenOf(args, engine), deltaY)) "scrolled=$deltaY" else "noop")
            }
            "browser.screenshot" -> InvokeResult.okImage("", engine.screenshot(tokenOf(args, engine), prefs))
            "browser.current_url" -> {
            val t = tokenOf(args, engine)
            InvokeResult.okMessage(engine.eventBus.urlOf(t.tabId) ?: t.url)
        }
            "browser.title" -> {
            val t = tokenOf(args, engine)
            InvokeResult.okMessage(engine.eventBus.titleOf(t.tabId) ?: t.title)
        }
            "browser.page_source" -> {
                val max = args["maxBytes"]?.asInt() ?: 60_000
                InvokeResult.okMessage(engine.pageSource(tokenOf(args, engine), max))
            }
            "browser.evaluate" -> {
                val exp = args["expression"]?.asString().orEmpty()
                when {
                    // 安全门禁：allowEvalJs 默认 false，未显式开启时不执行任何脚本
                    !prefs.allowEvalJs -> InvokeResult.error("evaluate 被禁用（allowEvalJs=false），请在浏览器设置中开启")
                    exp.isEmpty() -> InvokeResult.error("缺少 expression")
                    else -> engine.evaluate(tokenOf(args, engine), exp)?.let { InvokeResult.okMessage(it) }
                        // 引擎侧 evaluate 超时/失败返回 null：不能再伪装成成功（空串）
                        ?: InvokeResult.error("evaluation failed or timed out")
                }
            }
            "browser.console_list" -> InvokeResult.okMessage(
                engine.eventBus.console.value.joinToString("\n") { "[${it.level}] ${it.message}" }
            )
            "browser.console_clear" -> { engine.eventBus.clearConsole(); InvokeResult.okMessage("ok") }
            "browser.network_list" -> InvokeResult.okMessage(
                engine.eventBus.network.value.joinToString("\n") { "[${it.method}] ${it.url} (${it.statusCode})" }
            )
            "browser.cookies_get" -> {
            val t = tokenOf(args, engine)
            InvokeResult.okMessage(engine.cookiesGet(t, args["url"]?.asString()))
        }
            "browser.cookies_set" -> {
            val t = tokenOf(args, engine)
            engine.cookiesSet(t, args["url"]?.asString().orEmpty(), args["header"]?.asString().orEmpty())
            InvokeResult.okMessage("ok")
        }
            "browser.cookies_delete" -> {
            val t = tokenOf(args, engine)
            engine.cookiesDelete(t, args["url"]?.asString().orEmpty(), args["name"]?.asString().orEmpty())
            InvokeResult.okMessage("ok")
        }
            "browser.local_get", "browser.session_get" -> handleKvGet(engine, args, toolName.startsWith("browser.session"))
            "browser.local_set", "browser.session_set" -> handleKvSet(engine, args, toolName.startsWith("browser.session"))
            "browser.local_delete", "browser.session_delete" -> handleKvDelete(engine, args, toolName.startsWith("browser.session"))
            "browser.local_keys", "browser.session_keys" -> handleKvKeys(engine, args, toolName.startsWith("browser.session"))
            else -> InvokeResult.error("工具未实现：$toolName")
        }
    }

    private fun explicitTokenOf(args: JsonObject): BrowserSessionToken {
        val tabId = args["tab"]?.asString() ?: BrowserSessionToken.DEFAULT_TAB_ID
        return BrowserSessionToken(tabId = tabId, family = prefs.resolvedFamily)
    }

    private fun tokenOf(args: JsonObject, engine: BrowserEngine): BrowserSessionToken =
        args["tab"]?.asString()?.let { BrowserSessionToken(tabId = it, family = engine.family) }
            ?: engine.activeTab()
            ?: BrowserSessionToken.defaultTab(engine.family)

    private fun pickEngine(token: BrowserSessionToken): BrowserEngine? {
        return engineSelector(token) ?: engines.firstOrNull()
    }

    private suspend fun handleWithRef(
        engine: BrowserEngine,
        args: JsonObject,
        action: suspend (BrowserEngine, BrowserSessionToken, String) -> Boolean,
    ): InvokeResult {
        val ref = args["ref"]?.asString().orEmpty()
        if (ref.isBlank()) return InvokeResult.error("缺少 ref")
        val ok = action(engine, tokenOf(args, engine), ref)
        return if (ok) InvokeResult.okMessage("ok=$ref") else InvokeResult.error("failed=$ref")
    }

    private suspend fun click(engine: BrowserEngine, tab: BrowserSessionToken, ref: String): Boolean =
        engine.click(tab, ref) { _, r -> "[data-taixu-ref='$r']" }

    private suspend fun handleType(engine: BrowserEngine, args: JsonObject): InvokeResult {
        val ref = args["ref"]?.asString().orEmpty()
        val text = args["text"]?.asString().orEmpty()
        if (ref.isBlank() || text.isEmpty()) return InvokeResult.error("缺少 ref 或 text")
        val ok = engine.typeInto(tokenOf(args, engine), ref, text) { _, r -> "[data-taixu-ref='$r']" }
        return if (ok) InvokeResult.okMessage("typed=${text.length}") else InvokeResult.error("type-failed=$ref")
    }

    private suspend fun handlePress(engine: BrowserEngine, args: JsonObject): InvokeResult {
        val ref = args["ref"]?.asString()
        val key = args["key"]?.asString().orEmpty()
        if (key.isEmpty()) return InvokeResult.error("缺少 key")
        val ok = engine.press(tokenOf(args, engine), ref, key) { _, r -> ref?.let { "[data-taixu-ref='$r']" } ?: "" }
        return if (ok) InvokeResult.okMessage("pressed=$key") else InvokeResult.error("press-failed=$key")
    }

    private suspend fun handleSnapshot(engine: BrowserEngine, args: JsonObject): InvokeResult {
        val max = args["maxElements"]?.asInt() ?: 200
        val snap = engine.snapshot(tokenOf(args, engine), max)
        val refsArray = JsonArray(snap.refs.entries.map { (ref, r) ->
            buildJsonObject {
                put("ref", JsonPrimitive(ref))
                put("tag", JsonPrimitive(r.tag))
                r.type?.let { put("type", JsonPrimitive(it)) }
                r.role?.let { put("role", JsonPrimitive(it)) }
                r.name?.let { put("name", JsonPrimitive(it)) }
                r.text?.let { put("text", JsonPrimitive(it)) }
                r.placeholder?.let { put("placeholder", JsonPrimitive(it)) }
                r.ariaLabel?.let { put("ariaLabel", JsonPrimitive(it)) }
            }
        })
        return InvokeResult.okMessage(buildJsonObject {
            put("ok", JsonPrimitive(true))
            put("tab", JsonPrimitive(snap.tabId))
            put("url", JsonPrimitive(snap.url))
            put("title", JsonPrimitive(snap.title))
            put("domFingerprint", JsonPrimitive(snap.domFingerprint))
            put("interactiveCount", JsonPrimitive(snap.interactiveRefs.size))
            put("refs", refsArray)
        }.toString())
    }

    private suspend fun handleKvGet(engine: BrowserEngine, args: JsonObject, session: Boolean): InvokeResult {
        val key = args["key"]?.asString().orEmpty()
        if (key.isEmpty()) return InvokeResult.error("缺少 key")
        val tab = tokenOf(args, engine)
        val v = if (session) engine.sessionGet(tab, key) else engine.localGet(tab, key)
        return InvokeResult.okMessage(v ?: "")
    }

    private suspend fun handleKvSet(engine: BrowserEngine, args: JsonObject, session: Boolean): InvokeResult {
        val key = args["key"]?.asString().orEmpty()
        val v = args["value"]?.asString().orEmpty()
        if (key.isEmpty()) return InvokeResult.error("缺少 key")
        val tab = tokenOf(args, engine)
        if (session) engine.sessionSet(tab, key, v) else engine.localSet(tab, key, v)
        return InvokeResult.okMessage("ok")
    }

    private suspend fun handleKvDelete(engine: BrowserEngine, args: JsonObject, session: Boolean): InvokeResult {
        val key = args["key"]?.asString().orEmpty()
        if (key.isEmpty()) return InvokeResult.error("缺少 key")
        val tab = tokenOf(args, engine)
        if (session) engine.sessionDelete(tab, key) else engine.localDelete(tab, key)
        return InvokeResult.okMessage("ok")
    }

    private suspend fun handleKvKeys(engine: BrowserEngine, args: JsonObject, session: Boolean): InvokeResult =
        InvokeResult.okMessage(
            (if (session) engine.sessionKeys(tokenOf(args, engine)) else engine.localKeys(tokenOf(args, engine))).joinToString("\n")
        )

    private fun JsonElement?.asString(): String? = 
        (this as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() }

    private fun JsonElement?.asInt(): Int? = 
        (this as? JsonPrimitive)?.content?.toIntOrNull()


    data class ToolSpec(
        val name: String,
        val risk: BrowserRisk,
        val capability: BrowserCapability,
        val description: String,
        val inputSchema: JsonObject,
    )

    data class InvokeResult(
        val success: Boolean,
        val output: String,
        val imageAttachments: List<ToolImageRef> = emptyList(),
    ) {
        companion object {
            fun okMessage(message: String) = InvokeResult(success = true, output = message)
            fun okImage(message: String, image: ToolImageRef?) = InvokeResult(
                success = true,
                output = message,
                imageAttachments = listOfNotNull(image)
            )
            fun error(message: String) = InvokeResult(success = false, output = message)
        }
    }

    companion object {
        private fun schema(
            properties: Map<String, String> = emptyMap(),
            required: List<String> = emptyList(),
        ): JsonObject = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", buildJsonObject {
                properties.forEach { (name, type) ->
                    put(name, buildJsonObject { put("type", JsonPrimitive(type)) })
                }
            })
            if (required.isNotEmpty()) {
                put("required", JsonArray(required.map(::JsonPrimitive)))
            }
            put("additionalProperties", JsonPrimitive(false))
        }

        private val NO_ARGS = schema(mapOf("tab" to "string"))
        private val URL = schema(mapOf("url" to "string", "tab" to "string"), listOf("url"))
        private val REF = schema(mapOf("ref" to "string", "tab" to "string"), listOf("ref"))
        private val TOOLS: List<ToolSpec> = listOf(
            ToolSpec("browser.open", BrowserRisk.MEDIUM, BrowserCapability.OPEN, "打开新 tab；返回 tab ID", URL),
            ToolSpec("browser.navigate", BrowserRisk.MEDIUM, BrowserCapability.NAVIGATE, "当前或指定 tab 跳转到 URL", URL),
            ToolSpec("browser.back", BrowserRisk.LOW, BrowserCapability.NAVIGATE, "后退", NO_ARGS),
            ToolSpec("browser.forward", BrowserRisk.LOW, BrowserCapability.NAVIGATE, "前进", NO_ARGS),
            ToolSpec("browser.refresh", BrowserRisk.LOW, BrowserCapability.NAVIGATE, "刷新", NO_ARGS),
            ToolSpec("browser.list_tabs", BrowserRisk.LOW, BrowserCapability.LIST_TABS, "列出 tab", schema()),
            ToolSpec("browser.close_tab", BrowserRisk.LOW, BrowserCapability.CLOSE_TAB, "关闭当前或指定 tab", NO_ARGS),
            ToolSpec("browser.snapshot", BrowserRisk.LOW, BrowserCapability.SNAPSHOT, "提取 ref + 页面元信息", schema(mapOf("tab" to "string", "maxElements" to "integer"))),
            ToolSpec("browser.click", BrowserRisk.HIGH, BrowserCapability.CLICK, "按 snapshot ref 点击元素", REF),
            ToolSpec("browser.type", BrowserRisk.HIGH, BrowserCapability.TYPE, "按 snapshot ref 键入文本", schema(mapOf("ref" to "string", "text" to "string", "tab" to "string"), listOf("ref", "text"))),
            ToolSpec("browser.press", BrowserRisk.HIGH, BrowserCapability.PRESS, "向当前焦点或指定 ref 发送按键", schema(mapOf("ref" to "string", "key" to "string", "tab" to "string"), listOf("key"))),
            ToolSpec("browser.scroll", BrowserRisk.LOW, BrowserCapability.SCROLL, "滚动当前页", schema(mapOf("deltaY" to "integer", "tab" to "string"))),
            ToolSpec("browser.screenshot", BrowserRisk.LOW, BrowserCapability.SCREENSHOT, "截图当前页", NO_ARGS),
            ToolSpec("browser.current_url", BrowserRisk.LOW, BrowserCapability.PAGE_SOURCE, "取当前 URL", NO_ARGS),
            ToolSpec("browser.title", BrowserRisk.LOW, BrowserCapability.PAGE_SOURCE, "取当前 title", NO_ARGS),
            ToolSpec("browser.page_source", BrowserRisk.MEDIUM, BrowserCapability.PAGE_SOURCE, "页面 HTML", schema(mapOf("maxBytes" to "integer", "tab" to "string"))),
            ToolSpec("browser.evaluate", BrowserRisk.CRITICAL, BrowserCapability.EVALUATE_JS, "执行 JS", schema(mapOf("expression" to "string", "tab" to "string"), listOf("expression"))),
            ToolSpec("browser.console_list", BrowserRisk.LOW, BrowserCapability.CONSOLE_READ, "最近 console 日志", NO_ARGS),
            ToolSpec("browser.console_clear", BrowserRisk.MEDIUM, BrowserCapability.CONSOLE_READ, "清空 console", NO_ARGS),
            ToolSpec("browser.network_list", BrowserRisk.LOW, BrowserCapability.NETWORK_INTERCEPT, "已捕获网络请求", NO_ARGS),
            ToolSpec("browser.cookies_get", BrowserRisk.CRITICAL, BrowserCapability.COOKIES_RW, "读 Cookie", schema(mapOf("url" to "string", "tab" to "string"))),
            ToolSpec("browser.cookies_set", BrowserRisk.HIGH, BrowserCapability.COOKIES_RW, "写 Cookie", schema(mapOf("url" to "string", "header" to "string", "tab" to "string"), listOf("url", "header"))),
            ToolSpec("browser.cookies_delete", BrowserRisk.HIGH, BrowserCapability.COOKIES_RW, "删 Cookie", schema(mapOf("url" to "string", "name" to "string", "tab" to "string"), listOf("url", "name"))),
            ToolSpec("browser.local_get", BrowserRisk.HIGH, BrowserCapability.LOCAL_RW, "读 localStorage", schema(mapOf("key" to "string", "tab" to "string"), listOf("key"))),
            ToolSpec("browser.local_set", BrowserRisk.HIGH, BrowserCapability.LOCAL_RW, "写 localStorage", schema(mapOf("key" to "string", "value" to "string", "tab" to "string"), listOf("key", "value"))),
            ToolSpec("browser.local_delete", BrowserRisk.HIGH, BrowserCapability.LOCAL_RW, "删 localStorage", schema(mapOf("key" to "string", "tab" to "string"), listOf("key"))),
            ToolSpec("browser.local_keys", BrowserRisk.MEDIUM, BrowserCapability.LOCAL_RW, "列 localStorage keys", NO_ARGS),
            ToolSpec("browser.session_get", BrowserRisk.HIGH, BrowserCapability.SESSION_RW, "读 sessionStorage", schema(mapOf("key" to "string", "tab" to "string"), listOf("key"))),
            ToolSpec("browser.session_set", BrowserRisk.HIGH, BrowserCapability.SESSION_RW, "写 sessionStorage", schema(mapOf("key" to "string", "value" to "string", "tab" to "string"), listOf("key", "value"))),
            ToolSpec("browser.session_delete", BrowserRisk.HIGH, BrowserCapability.SESSION_RW, "删 sessionStorage", schema(mapOf("key" to "string", "tab" to "string"), listOf("key"))),
            ToolSpec("browser.session_keys", BrowserRisk.MEDIUM, BrowserCapability.SESSION_RW, "列 sessionStorage keys", NO_ARGS),
        )
    }
}



