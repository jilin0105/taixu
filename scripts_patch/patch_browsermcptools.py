# -*- coding: utf-8 -*-
import re, io, sys

f = r"C:\Users\wangk\Desktop\LinuxAIRuntime\runtime\browser\src\main\java\top\wkbin\taixu\runtime\browser\tools\BrowserMcpTools.kt"
with io.open(f, 'r', encoding='utf-8') as fh:
    text = fh.read()

# normalize line endings for safe matching
text = text.replace('\r\n', '\n')

def repl(pattern, replacement, text, flags=re.MULTILINE | re.DOTALL):
    new = re.sub(pattern, replacement, text, flags=flags)
    if new == text:
        print("NO MATCH:", pattern[:70])
    else:
        print("OK:", pattern[:70])
    return new

# 1. remove runBlocking import
text = repl(r'import kotlinx\.coroutines\.runBlocking\n', '', text)

# 2. current_url / title per tab
text = repl(
    r'"browser\.current_url" -> InvokeResult\.okMessage\(engine\.eventBus\.url\.value\)\n\s*"browser\.title" -> InvokeResult\.okMessage\(engine\.eventBus\.title\.value\)',
    '"browser.current_url" -> {\n            val t = tokenOf(args, engine)\n            InvokeResult.okMessage(engine.eventBus.urlOf(t.tabId) ?: t.url)\n        }\n            "browser.title" -> {\n            val t = tokenOf(args, engine)\n            InvokeResult.okMessage(engine.eventBus.titleOf(t.tabId) ?: t.title)\n        }',
    text)

# 3. console_clear implementation
text = repl(
    r'"browser\.console_clear" -> \{ /\* bus is read-only outside; minimal impl \*/ InvokeResult\.okMessage\("ok"\) \}',
    '"browser.console_clear" -> { engine.eventBus.clearConsole(); InvokeResult.okMessage("ok") }',
    text)

# 4. cookies per tab
text = repl(
    r'"browser\.cookies_get" -> InvokeResult\.okMessage\(engine\.cookiesGet\(args\["url"\]\?\.asString\(\)\)\)\n\s*"browser\.cookies_set" -> \{ engine\.cookiesSet\(args\["url"\]\?\.asString\(\)\.orEmpty\(\), args\["header"\]\?\.asString\(\)\.orEmpty\(\)\); InvokeResult\.okMessage\("ok"\) \}\n\s*"browser\.cookies_delete" -> \{ engine\.cookiesDelete\(args\["url"\]\?\.asString\(\)\.orEmpty\(\), args\["name"\]\?\.asString\(\)\.orEmpty\(\)\); InvokeResult\.okMessage\("ok"\) \}',
    '"browser.cookies_get" -> {\n            val t = tokenOf(args, engine)\n            InvokeResult.okMessage(engine.cookiesGet(t, args["url"]?.asString()))\n        }\n            "browser.cookies_set" -> {\n            val t = tokenOf(args, engine)\n            engine.cookiesSet(t, args["url"]?.asString().orEmpty(), args["header"]?.asString().orEmpty())\n            InvokeResult.okMessage("ok")\n        }\n            "browser.cookies_delete" -> {\n            val t = tokenOf(args, engine)\n            engine.cookiesDelete(t, args["url"]?.asString().orEmpty(), args["name"]?.asString().orEmpty())\n            InvokeResult.okMessage("ok")\n        }',
    text)

# 5. handleWithRef -> suspend (no runBlocking)
text = repl(
    r'    private fun handleWithRef\(\n        engine: BrowserEngine,\n        args: JsonObject,\n        action: suspend \(BrowserEngine, BrowserSessionToken, String\) -> Boolean,\n    \): InvokeResult = runBlocking \{\n        val ref = args\["ref"\]\?\.asString\(\)\.orEmpty\(\)\n        if \(ref\.isBlank\(\)\) return@runBlocking InvokeResult\.error\("缺少 ref"\)\n        val ok = action\(engine, tokenOf\(args, engine\), ref\)\n        if \(ok\) InvokeResult\.okMessage\("ok=\$ref"\) else InvokeResult\.error\("failed=\$ref"\)\n    \}',
    '    private suspend fun handleWithRef(\n        engine: BrowserEngine,\n        args: JsonObject,\n        action: suspend (BrowserEngine, BrowserSessionToken, String) -> Boolean,\n    ): InvokeResult {\n        val ref = args["ref"]?.asString().orEmpty()\n        if (ref.isBlank()) return InvokeResult.error("缺少 ref")\n        val ok = action(engine, tokenOf(args, engine), ref)\n        if (ok) InvokeResult.okMessage("ok=$ref") else InvokeResult.error("failed=$ref")\n    }',
    text)

# 6. click: no full re-scan, use data-taixu-ref directly
text = repl(
    r'    private suspend fun click\(engine: BrowserEngine, tab: BrowserSessionToken, ref: String\): Boolean =\n        engine\.click\(tab, ref\) \{ t, r -> engine\.snapshot\(t\)\.refOf\(r\)\?\.let \{ "\[data-taixu-ref=\'\\\$r\'\]" \} \?: "" \}',
    '    private suspend fun click(engine: BrowserEngine, tab: BrowserSessionToken, ref: String): Boolean =\n        engine.click(tab, ref) { _, r -> "[data-taixu-ref=\'$r\']" }',
    text)

# 7. handleType -> suspend
text = repl(
    r'    private fun handleType\(engine: BrowserEngine, args: JsonObject\): InvokeResult = runBlocking \{\n        val ref = args\["ref"\]\?\.asString\(\)\.orEmpty\(\)\n        val text = args\["text"\]\?\.asString\(\)\.orEmpty\(\)\n        if \(ref\.isBlank\(\) \|\| text\.isEmpty\(\)\) return@runBlocking InvokeResult\.error\("缺少 ref 或 text"\)\n        val ok = engine\.typeInto\(tokenOf\(args, engine\), ref, text\) \{ _, r -> "\[data-taixu-ref=\'\\\$r\'\]" \}\n        if \(ok\) InvokeResult\.okMessage\("typed=\$\{text\.length\}"\) else InvokeResult\.error\("type-failed=\$ref"\)\n    \}',
    '    private suspend fun handleType(engine: BrowserEngine, args: JsonObject): InvokeResult {\n        val ref = args["ref"]?.asString().orEmpty()\n        val text = args["text"]?.asString().orEmpty()\n        if (ref.isBlank() || text.isEmpty()) return InvokeResult.error("缺少 ref 或 text")\n        val ok = engine.typeInto(tokenOf(args, engine), ref, text) { _, r -> "[data-taixu-ref=\'$r\']" }\n        if (ok) InvokeResult.okMessage("typed=${text.length}") else InvokeResult.error("type-failed=$ref")\n    }',
    text)

# 8. handlePress -> suspend
text = repl(
    r'    private fun handlePress\(engine: BrowserEngine, args: JsonObject\): InvokeResult = runBlocking \{\n        val ref = args\["ref"\]\?\.asString\(\)\n        val key = args\["key"\]\?\.asString\(\)\.orEmpty\(\)\n        if \(key\.isEmpty\(\)\) return@runBlocking InvokeResult\.error\("缺少 key"\)\n        val ok = engine\.press\(tokenOf\(args, engine\), ref, key\) \{ _, r -> ref\?\.let \{ "\[data-taixu-ref=\'\\\$r\'\]" \} \?: "" \}\n        if \(ok\) InvokeResult\.okMessage\("pressed=\$key"\) else InvokeResult\.error\("press-failed=\$key"\)\n    \}',
    '    private suspend fun handlePress(engine: BrowserEngine, args: JsonObject): InvokeResult {\n        val ref = args["ref"]?.asString()\n        val key = args["key"]?.asString().orEmpty()\n        if (key.isEmpty()) return InvokeResult.error("缺少 key")\n        val ok = engine.press(tokenOf(args, engine), ref, key) { _, r -> ref?.let { "[data-taixu-ref=\'$r\']" } ?: "" }\n        if (ok) InvokeResult.okMessage("pressed=$key") else InvokeResult.error("press-failed=$key")\n    }',
    text)

# 9. handleKv* pass tab
text = repl(r'val v = if \(session\) engine\.sessionGet\(key\) else engine\.localGet\(key\)', 'val tab = tokenOf(args, engine)\n        val v = if (session) engine.sessionGet(tab, key) else engine.localGet(tab, key)', text)
text = repl(r'if \(session\) engine\.sessionSet\(key, v\) else engine\.localSet\(key, v\)', 'val tab = tokenOf(args, engine)\n        if (session) engine.sessionSet(tab, key, v) else engine.localSet(tab, key, v)', text)
text = repl(r'if \(session\) engine\.sessionDelete\(key\) else engine\.localDelete\(key\)', 'val tab = tokenOf(args, engine)\n        if (session) engine.sessionDelete(tab, key) else engine.localDelete(tab, key)', text)
text = repl(r'\(if \(session\) engine\.sessionKeys\(\) else engine\.localKeys\(\)\)', '(if (session) engine.sessionKeys(tokenOf(args, engine)) else engine.localKeys(tokenOf(args, engine)))', text)

with io.open(f, 'w', encoding='utf-8', newline='\n') as fh:
    fh.write(text)
print("DONE")
