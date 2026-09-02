# -*- coding: utf-8 -*-
import io
f = r'C:\Users\wangk\Desktop\LinuxAIRuntime\runtime\browser\src\main\java\top\wkbin\taixu\runtime\browser\tools\BrowserMcpTools.kt'
t = io.open(f, encoding='utf-8').read().replace('\r\n', '\n')

pairs = [
('''    private suspend fun click(engine: BrowserEngine, tab: BrowserSessionToken, ref: String): Boolean =
        engine.click(tab, ref) { t, r -> engine.snapshot(t).refOf(r)?.let { "[data-taixu-ref='$r']" } ?: "" }''',
 '''    private suspend fun click(engine: BrowserEngine, tab: BrowserSessionToken, ref: String): Boolean =
        engine.click(tab, ref) { _, r -> "[data-taixu-ref='$r']" }'''),
('''    private fun handleType(engine: BrowserEngine, args: JsonObject): InvokeResult = runBlocking {
        val ref = args["ref"]?.asString().orEmpty()
        val text = args["text"]?.asString().orEmpty()
        if (ref.isBlank() || text.isEmpty()) return@runBlocking InvokeResult.error("缺少 ref 或 text")
        val ok = engine.typeInto(tokenOf(args, engine), ref, text) { _, r -> "[data-taixu-ref='$r']" }
        if (ok) InvokeResult.okMessage("typed=${text.length}") else InvokeResult.error("type-failed=$ref")
    }''',
 '''    private suspend fun handleType(engine: BrowserEngine, args: JsonObject): InvokeResult {
        val ref = args["ref"]?.asString().orEmpty()
        val text = args["text"]?.asString().orEmpty()
        if (ref.isBlank() || text.isEmpty()) return InvokeResult.error("缺少 ref 或 text")
        val ok = engine.typeInto(tokenOf(args, engine), ref, text) { _, r -> "[data-taixu-ref='$r']" }
        if (ok) InvokeResult.okMessage("typed=${text.length}") else InvokeResult.error("type-failed=$ref")
    }'''),
('''    private fun handlePress(engine: BrowserEngine, args: JsonObject): InvokeResult = runBlocking {
        val ref = args["ref"]?.asString()
        val key = args["key"]?.asString().orEmpty()
        if (key.isEmpty()) return@runBlocking InvokeResult.error("缺少 key")
        val ok = engine.press(tokenOf(args, engine), ref, key) { _, r -> ref?.let { "[data-taixu-ref='$r']" } ?: "" }
        if (ok) InvokeResult.okMessage("pressed=$key") else InvokeResult.error("press-failed=$key")
    }''',
 '''    private suspend fun handlePress(engine: BrowserEngine, args: JsonObject): InvokeResult {
        val ref = args["ref"]?.asString()
        val key = args["key"]?.asString().orEmpty()
        if (key.isEmpty()) return InvokeResult.error("缺少 key")
        val ok = engine.press(tokenOf(args, engine), ref, key) { _, r -> ref?.let { "[data-taixu-ref='$r']" } ?: "" }
        if (ok) InvokeResult.okMessage("pressed=$key") else InvokeResult.error("press-failed=$key")
    }'''),
]
for old, new in pairs:
    if old in t:
        t = t.replace(old, new)
        print("REPLACED")
    else:
        print("NOT FOUND:", old[:70])
io.open(f, 'w', encoding='utf-8', newline='\n').write(t)
print("DONE")
