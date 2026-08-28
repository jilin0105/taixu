package top.wkbin.taixu.harness.validation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.taixu.core.model.McpToolInfo
import top.wkbin.taixu.harness.ProviderClient
import top.wkbin.taixu.harness.mcp.McpToolApiName

/**
 * 工具参数执行前 JSON Schema 校验（模型参数 → 校验 → 审批 → 执行 链路的第二环）。
 *
 * 只实现工具定义实际用到的 JSON Schema 子集：required / type / enum /
 * minimum / maximum / pattern / anyOf(required) / items。未知关键字一律忽略，
 * 找不到 schema 的工具跳过校验（宽容失败，不阻断执行链路）。
 *
 * 校验失败不抛异常，而是返回可读的问题列表，由调用方写回 ToolResult
 * 让模型自我纠正。
 */
object ToolSchemaValidator {
    private val json = Json { ignoreUnknownKeys = true }

    /** 校验工具参数；空列表 = 通过。 */
    fun problemsFor(
        toolName: String,
        args: JsonObject,
        mcpTools: List<McpToolInfo> = emptyList(),
    ): List<String> {
        val schema = resolveSchema(toolName, mcpTools) ?: return emptyList()
        return validateObject(schema, args, prefix = "")
    }

    /** 直接对给定 schema 校验（供自定义 schema 场景与测试使用）。 */
    fun validate(schema: JsonObject, args: JsonObject): List<String> =
        validateObject(schema, args, prefix = "")

    private fun resolveSchema(toolName: String, mcpTools: List<McpToolInfo>): JsonObject? {
        if (toolName.startsWith("mcp__")) {
            val tool = mcpTools.firstOrNull { McpToolApiName.matches(it, toolName) } ?: return null
            if (tool.parametersJson.isBlank()) return null
            return runCatching { json.parseToJsonElement(tool.parametersJson) as? JsonObject }.getOrNull()
        }
        val apiName = when (toolName) {
            "history.search" -> "history_search"
            "history.read" -> "history_read"
            else -> toolName
        }
        return ProviderClient.TOOLS.firstOrNull { it.function.name == apiName }?.function?.parameters
    }

    private fun validateObject(schema: JsonObject, obj: JsonObject, prefix: String): List<String> {
        val problems = mutableListOf<String>()

        (schema["required"] as? JsonArray)?.forEach { element ->
            val name = (element as? JsonPrimitive)?.contentOrNull ?: return@forEach
            if (!obj.containsKey(name)) problems += "缺少必填参数 ${prefix}${name}"
        }

        (schema["anyOf"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { alternatives ->
            val satisfied = alternatives.any { alternative ->
                (alternative as? JsonObject)?.let { validateObject(it, obj, prefix).isEmpty() } == true
            }
            if (!satisfied) {
                val combos = alternatives.mapNotNull { alternative ->
                    (alternative as? JsonObject)?.get("required")?.let { required ->
                        (required as? JsonArray)
                            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                            ?.takeIf { it.isNotEmpty() }
                            ?.joinToString("/")
                    }
                }.filter { it.isNotBlank() }
                if (combos.isNotEmpty()) {
                    val hint = if (combos.size == 1) {
                        "至少提供 ${combos.single()}"
                    } else {
                        "${combos.joinToString(" 或 ")} 至少提供其中一组"
                    }
                    problems += "参数组合不满足要求：$hint"
                }
            }
        }

        (schema["properties"] as? JsonObject)?.forEach { (name, definition) ->
            val value = obj[name] ?: return@forEach
            val propertySchema = definition as? JsonObject ?: return@forEach
            problems += validateValue(propertySchema, value, label = "${prefix}${name}", prefix = "$prefix$name.")
        }

        // 多传/错传参数检测：模型把其他工具的参数（如 url/query）带进来时，明确指出
        // 本工具接受哪些参数，让模型一次修正到位，而不是反复以错误参数重试。
        val allowExtra = (schema["additionalProperties"] as? JsonPrimitive)?.contentOrNull == "true"
        (schema["properties"] as? JsonObject)?.let { properties ->
            val unknown = obj.keys.filter { it !in properties.keys }
            if (unknown.isNotEmpty() && !allowExtra) {
                val at = if (prefix.isEmpty()) "" else "（位于 $prefix 层）"
                problems += "不接受参数 ${unknown.joinToString("、")}$at" +
                    "（该工具可用参数: ${properties.keys.joinToString("、").ifEmpty { "无" }}）"
            }
        }
        return problems
    }

    private fun validateValue(schema: JsonObject, value: JsonElement, label: String, prefix: String): List<String> {
        val problems = mutableListOf<String>()
        val type = schema["type"]?.jsonPrimitive?.contentOrNull

        if (type != null && !matchesType(type, value)) {
            problems += "参数 $label 类型错误：应为 $type，实际是${typeName(value)}"
            return problems // 类型不对，后续约束没有意义
        }

        (schema["enum"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { allowed ->
            val content = (value as? JsonPrimitive)?.content
            if (allowed.none { (it as? JsonPrimitive)?.content == content }) {
                problems += "参数 $label 的值 \"$content\" 不在允许范围内：${allowed.joinToString("/") { (it as? JsonPrimitive)?.content.orEmpty() }}"
            }
        }

        if (type == "integer" || type == "number") {
            val number = (value as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
            if (number != null) {
                val min = schema["minimum"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val max = schema["maximum"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                when {
                    min != null && max != null && (number < min || number > max) ->
                        problems += "参数 $label 的值 ${formatNumber(number)} 超出允许范围 [${formatNumber(min)}, ${formatNumber(max)}]"
                    min != null && number < min ->
                        problems += "参数 $label 的值 ${formatNumber(number)} 小于最小允许值 ${formatNumber(min)}"
                    max != null && number > max ->
                        problems += "参数 $label 的值 ${formatNumber(number)} 大于最大允许值 ${formatNumber(max)}"
                }
            }
        }

        if (value is JsonPrimitive && value.isString) {
            schema["pattern"]?.jsonPrimitive?.contentOrNull?.let { pattern ->
                val matches = runCatching { Regex(pattern).containsMatchIn(value.content) }.getOrDefault(true)
                if (!matches) problems += "参数 $label 格式不符合要求（需匹配 $pattern）"
            }
        }

        if (value is JsonArray) {
            (schema["items"] as? JsonObject)?.let { itemSchema ->
                value.forEachIndexed { index, element ->
                    // prefix 形如 "steps." → 数组元素路径 "steps[0]."（去掉属性名后的点）
                    val itemPrefix = prefix.removeSuffix(".") + "[$index]."
                    when (element) {
                        is JsonObject -> problems += validateObject(itemSchema, element, itemPrefix)
                        else -> problems += validateValue(itemSchema, element, label = "${prefix.removeSuffix(".")}[$index]", prefix = itemPrefix)
                    }
                }
            }
        }
        return problems
    }

    private fun formatNumber(number: Double): String =
        if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()

    private fun matchesType(type: String, value: JsonElement): Boolean = when (type) {
        "object" -> value is JsonObject
        "array" -> value is JsonArray
        "string" -> value is JsonPrimitive && value.isString
        "boolean" -> value is JsonPrimitive && !value.isString && (value.content == "true" || value.content == "false")
        "integer" -> value is JsonPrimitive && !value.isString &&
            value.contentOrNull?.toDoubleOrNull()?.let { it % 1.0 == 0.0 } == true
        "number" -> value is JsonPrimitive && !value.isString && value.contentOrNull?.toDoubleOrNull() != null
        "null" -> value is JsonNull
        else -> true // 未知类型声明宽容放行
    }

    private fun typeName(value: JsonElement): String = when (value) {
        is JsonObject -> "对象"
        is JsonArray -> "数组"
        is JsonNull -> "null"
        is JsonPrimitive -> when {
            value.isString -> "字符串"
            value.content == "true" || value.content == "false" -> "布尔值"
            value.contentOrNull?.toDoubleOrNull() != null -> "数字"
            else -> "未知类型"
        }
    }
}
