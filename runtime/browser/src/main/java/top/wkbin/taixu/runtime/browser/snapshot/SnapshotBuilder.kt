package top.wkbin.taixu.runtime.browser.snapshot

import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.taixu.core.browser.PageSnapshot
import top.wkbin.taixu.core.browser.SnapshotRef
import top.wkbin.taixu.runtime.browser.BrowserEvent
import top.wkbin.taixu.runtime.browser.BrowserEventBus
import top.wkbin.taixu.runtime.browser.BrowserSessionToken
import top.wkbin.taixu.runtime.browser.js.JsEvaluator

private fun JsonPrimitive.textOrNull(): String? =
    if (this === JsonNull) null else content.takeIf { it.isNotEmpty() }

/**
 * 在 WebView 中注入可见交互元素扫描脚本，结果反序列化为 [PageSnapshot] 推到 [BrowserEventBus]。
 *
 * 注入脚本扫描：`<a> / <button> / <input> / <select> / <textarea>` 以及带 `role` / `tabindex` / `onclick` 的元素。
 * 模型只见 ref，由内部 selector 表承担"ref → 真实 selector"的回查。
 */
class SnapshotBuilder(
    private val token: BrowserSessionToken,
    private val eventBus: BrowserEventBus,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun refresh(view: WebView, maxElements: Int = 200): PageSnapshot? {
        val rawJs = JsEvaluator.evaluate(view, JS_EXTRACT) ?: return null
        val raw = unwrap(rawJs)
        val arr = runCatching { json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return null
        val refs = HashMap<String, SnapshotRef>()
        arr.forEachIndexed { idx, el ->
            if (maxElements in 1 until Int.MAX_VALUE && idx >= maxElements) return@forEachIndexed
            val obj: JsonObject = runCatching { el.jsonObject }.getOrNull() ?: return@forEachIndexed
            val ref = "e${idx + 1}"
            refs[ref] = SnapshotRef(
                ref = ref,
                tag = obj["tag"]?.jsonPrimitive?.textOrNull() ?: "",
                type = obj["type"]?.jsonPrimitive?.textOrNull(),
                role = obj["role"]?.jsonPrimitive?.textOrNull(),
                name = obj["name"]?.jsonPrimitive?.textOrNull(),
                text = obj["text"]?.jsonPrimitive?.textOrNull(),
                placeholder = obj["placeholder"]?.jsonPrimitive?.textOrNull(),
                ariaLabel = obj["aria"]?.jsonPrimitive?.textOrNull(),
                interactive = true,
            )
            ResolverRegistry.put(token.tabId, ref, selectorFromObject(obj, ref))
        }
        val snap = withContext(Dispatchers.Main.immediate) {
            PageSnapshot(
                tabId = token.tabId,
                url = view.url ?: "",
                title = view.title ?: "",
                refs = refs,
                domFingerprint = fingerprintOf(refs),
                createdAt = System.currentTimeMillis(),
            )
        }
        // 同步发布并返回本次结果：调用方（engine.snapshot）立即读到的就是本次扫描，消除时序竞态
        eventBus.publish(BrowserEvent.SnapshotUpdated(token.tabId, snap))
        return snap
    }

    private fun unwrap(raw: String): String {
        val s = raw.trim()
        if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            // evaluateJavascript 返回 JSON 字符串字面量：用 JSON 解码以正确处理 \uXXXX / \t 等转义
            return runCatching { json.parseToJsonElement(raw).jsonPrimitive.content }
                .getOrElse {
                    s.substring(1, s.length - 1)
                        .replace("\\\"", "\"")
                        .replace("\\n", "\n")
                        .replace("\\\\", "\\")
                }
        }
        return s
    }

    /** 基于 refs 内容生成稳定的 DOM 指纹，用于模型端判断页面是否变化。 */
    private fun fingerprintOf(refs: Map<String, SnapshotRef>): String {
        val canonical = refs.entries.sortedBy { it.key }
            .joinToString("|") { (k, v) -> "$k:${v.tag}:${v.type.orEmpty()}:${v.text.orEmpty()}" }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun selectorFromObject(obj: JsonObject, ref: String): String {
        val tag = obj["tag"]?.jsonPrimitive?.textOrNull() ?: "*"
        val id = obj["id"]?.jsonPrimitive?.textOrNull()?.takeIf { it.isNotBlank() }
        val name = obj["name"]?.jsonPrimitive?.textOrNull()?.takeIf { it.isNotBlank() }
        val placeholder = obj["placeholder"]?.jsonPrimitive?.textOrNull()?.takeIf { it.isNotBlank() }
        val ariaLabel = obj["aria"]?.jsonPrimitive?.textOrNull()?.takeIf { it.isNotBlank() }
        val text = obj["text"]?.jsonPrimitive?.textOrNull()?.takeIf { it.isNotBlank() }
        val parts = buildList {
            add(tag)
            id?.let { add("#$it") }
            ariaLabel?.let { add("[aria-label='${escapeAttr(it)}']") }
            name?.let { add("[name='${escapeAttr(it)}']") }
            placeholder?.let { add("[placeholder='${escapeAttr(it)}']") }
            add("[data-taixu-ref='$ref']")
            if (text != null && tag == "a") add(":contains('${escapeAttr(text.take(40))}')")
        }
        return parts.joinToString("")
    }

    private fun escapeAttr(s: String): String =
        s.replace("\\", "\\\\").replace("'", "\\'")

    /** tabId → (ref → selector). 跨协程并发读写，使用并发容器。 */
    private object ResolverRegistry {
        private val map =
            java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, String>>()
        fun put(tabId: String, ref: String, selector: String) {
            map.getOrPut(tabId) { java.util.concurrent.ConcurrentHashMap() }[ref] = selector
        }
        fun selector(tabId: String, ref: String): String? = map[tabId]?.get(ref)
        fun clear(tabId: String) { map.remove(tabId) }
    }

    fun resolve(tabId: String, ref: String): String? = ResolverRegistry.selector(tabId, ref)
    fun clear(tabId: String) = ResolverRegistry.clear(tabId)

    companion object {
        /** DOM scan: returns JSON array of visible interactive elements. */
        const val JS_EXTRACT = """
        (function(){
          try {
            var NODES='a,button,input,select,textarea,[role=button],[role=link],[role=textbox],[role=checkbox],[role=radio],[tabindex],[onclick]';
            var nodes=document.querySelectorAll(NODES);
            var out=[];
            var refIdx = 1;
            for (var i=0;i<nodes.length;i++){
              var el=nodes[i];
              var r=el.getBoundingClientRect();
              if (!r || (r.width===0 && r.height===0)) continue;
              var ref = 'e' + refIdx++;
              el.setAttribute('data-taixu-ref', ref);
              out.push({
                tag: el.tagName.toLowerCase(),
                type: el.getAttribute('type'),
                name: el.getAttribute('name'),
                id: el.id || null,
                role: el.getAttribute('role'),
                text: (el.innerText||el.value||'').slice(0,80) || null,
                placeholder: el.getAttribute('placeholder'),
                aria: el.getAttribute('aria-label')
              });
              if (out.length>=200) break;
            }
            return JSON.stringify(out);
          } catch(e){ return '[]'; }
        })();
        """
    }
}
