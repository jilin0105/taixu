package top.wkbin.taixu.harness

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * JSON 文本调用模式专用协议编解码：模型在回复文本中输出
 * `[[tool_call]]{"name":..,"arguments":..}[[/tool_call]]` 标记来发起工具调用。
 *
 * 标记仅作为模型↔引擎之间的调用协议；展示给用户和持久化前都必须剥离。
 */
object JsonTextToolCallCodec {
    private val CALL_PATTERN = Regex("""\[\[tool_call\]\](.*?)\[\[/tool_call\]\]""", RegexOption.DOT_MATCHES_ALL)
    private val STRIP_PATTERN = Regex("""\[\[tool_call\]\].*?\[\[/tool_call\]\]""", RegexOption.DOT_MATCHES_ALL)

    /** 从模型回复文本中解析全部合法的工具调用标记，非法或缺名的 payload 静默丢弃。 */
    fun extract(json: Json, text: String): List<ApiToolCallSpec> {
        if (text.isBlank()) return emptyList()
        return CALL_PATTERN.findAll(text).mapNotNull { match ->
            val payload = match.groupValues[1].trim()
            runCatching {
                val obj = json.parseToJsonElement(payload) as? JsonObject ?: return@runCatching null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (name.isBlank()) return@runCatching null
                // arguments 可能为空、缺失，或被模型写成字符串（如 "" / "{\"url\":..}"），统一归一化
                val argumentsJson = when (val raw = obj["arguments"]) {
                    null -> "{}"
                    is JsonObject -> raw.toString()
                    is JsonPrimitive -> raw.content.ifBlank { "{}" }
                    else -> "{}"
                }
                ApiToolCallSpec(
                    id = "json-" + UUID.randomUUID().toString(),
                    name = name,
                    argumentsJson = argumentsJson,
                )
            }.getOrNull()
        }.toList()
    }

    /** 从展示文本中剥离工具调用标记，只留下模型真正写给用户看的内容。 */
    fun stripMarkers(text: String): String =
        text.replace(STRIP_PATTERN, "").trim()
}
