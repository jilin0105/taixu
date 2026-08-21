package top.wkbin.taixu.harness

import java.net.URI
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 推理开关：AUTO = 跟随模型服务端默认。 */
enum class ReasoningMode { AUTO, DISABLED, ENABLED }

/** 推理强度：null = 服务端默认。 */
enum class ReasoningEffort { LOW, MEDIUM, HIGH }

/**
 * 把统一的「推理开关 + 强度」配置翻译成各家 API 的参数格式。
 *
 * 各厂商字段差异：
 * - OpenAI（Chat Completions）：`reasoning_effort: none/low/medium/high`
 * - Gemini（OpenAI 兼容端点）：`thinking_config: {"thinkingBudget": 0..32768}`
 * - 智谱 GLM：`thinking: {"type": enabled/disabled, thinking_budget?}`
 * - 豆包（火山方舟）：`thinking: {"type": enabled/disabled}`
 * - OpenRouter：`reasoning: {"effort": none/low/medium/high}`
 * - Anthropic Claude（Messages API）：`thinking: {"type": enabled/disabled, budget_tokens}`
 *
 * 规则：
 * - AUTO：一律不注入任何参数（服务端默认）；
 * - 未知厂商（DeepSeek reasoner 等不支持调节的）保守不注入，避免未知字段 400。
 */
object ReasoningAdapter {

    /** OpenAI 兼容协议下，追加到请求体顶层的字段（空 Map = 不注入）。 */
    fun openAiFields(model: ModelConfig): Map<String, JsonElement> {
        if (model.reasoningMode == ReasoningMode.AUTO) return emptyMap()
        val host = hostOf(model.baseUrl)
        val provider = model.provider.lowercase()
        return when {
            isGemini(host, provider) -> geminiFields(model)
            isZhipu(host, provider) -> zhipuFields(model)
            isDoubao(host, provider) -> thinkingTypeFields(model)
            isOpenRouter(host, provider) -> openRouterFields(model)
            isOpenAiOfficial(host, provider) -> openAiOfficialFields(model)
            else -> emptyMap()
        }
    }

    /** Anthropic Messages API 的 thinking 参数（null = 不注入）。 */
    fun anthropicThinking(model: ModelConfig): JsonObject? {
        if (model.reasoningMode == ReasoningMode.AUTO) return null
        return if (model.reasoningMode == ReasoningMode.DISABLED) {
            buildJsonObject { put("type", "disabled") }
        } else {
            buildJsonObject {
                put("type", "enabled")
                // budget_tokens 必填；按强度映射并保证严格小于 max_tokens（Anthropic 硬性要求）
                val budget = model.reasoningEffort.budgetTokens()
                val maxTokens = model.maxTokens
                put(
                    "budget_tokens",
                    if (maxTokens != null) {
                        budget.coerceAtMost((maxTokens - 1024).coerceAtLeast(1024))
                    } else {
                        budget
                    },
                )
            }
        }
    }

    // ---------- 厂商判定 ----------

    private fun hostOf(baseUrl: String): String =
        runCatching { URI(baseUrl.trim()).host?.lowercase() }.getOrNull().orEmpty()

    private fun isGemini(host: String, provider: String) =
        host.contains("generativelanguage.googleapis.com") || provider.contains("gemini")

    private fun isZhipu(host: String, provider: String) =
        host.contains("bigmodel.cn") || provider.contains("智谱") || provider.contains("glm")

    private fun isDoubao(host: String, provider: String) =
        host.contains("volces.com") || provider.contains("豆包") || provider.contains("doubao")

    private fun isOpenRouter(host: String, provider: String) =
        host.contains("openrouter.ai") || provider.contains("openrouter")

    private fun isOpenAiOfficial(host: String, provider: String) =
        host == "api.openai.com" || provider.contains("openai")

    // ---------- 各厂商字段 ----------

    private fun geminiFields(model: ModelConfig): Map<String, JsonElement> =
        mapOf("thinking_config" to buildJsonObject {
            put("thinkingBudget", geminiBudget(model))
        })

    private fun zhipuFields(model: ModelConfig): Map<String, JsonElement> =
        mapOf("thinking" to buildJsonObject {
            put("type", if (model.reasoningMode == ReasoningMode.ENABLED) "enabled" else "disabled")
            if (model.reasoningMode == ReasoningMode.ENABLED) {
                model.reasoningEffort?.let { put("thinking_budget", it.glmBudget()) }
            }
        })

    private fun thinkingTypeFields(model: ModelConfig): Map<String, JsonElement> =
        mapOf("thinking" to buildJsonObject {
            put("type", if (model.reasoningMode == ReasoningMode.ENABLED) "enabled" else "disabled")
        })

    private fun openRouterFields(model: ModelConfig): Map<String, JsonElement> =
        mapOf("reasoning" to buildJsonObject {
            put(
                "effort",
                if (model.reasoningMode == ReasoningMode.DISABLED) {
                    "none"
                } else {
                    model.reasoningEffort?.openAiName() ?: "medium"
                },
            )
        })

    private fun openAiOfficialFields(model: ModelConfig): Map<String, JsonElement> =
        if (model.reasoningMode == ReasoningMode.DISABLED) {
            mapOf("reasoning_effort" to JsonPrimitive("none"))
        } else {
            model.reasoningEffort?.let { mapOf("reasoning_effort" to JsonPrimitive(it.openAiName())) }.orEmpty()
        }

    // ---------- 数值映射 ----------

    private fun ReasoningEffort.openAiName(): String = when (this) {
        ReasoningEffort.LOW -> "low"
        ReasoningEffort.MEDIUM -> "medium"
        ReasoningEffort.HIGH -> "high"
    }

    private fun ReasoningEffort?.budgetTokens(): Int = when (this) {
        ReasoningEffort.LOW -> 2048
        ReasoningEffort.MEDIUM -> 8192
        ReasoningEffort.HIGH -> 16384
        null -> 8192
    }

    private fun ReasoningEffort.glmBudget(): Int = when (this) {
        ReasoningEffort.LOW -> 1024
        ReasoningEffort.MEDIUM -> 2048
        ReasoningEffort.HIGH -> 4096
    }

    private fun geminiBudget(model: ModelConfig): Int = when {
        model.reasoningMode == ReasoningMode.DISABLED -> 0
        model.reasoningEffort == ReasoningEffort.LOW -> 1024
        model.reasoningEffort == ReasoningEffort.MEDIUM -> 8192
        model.reasoningEffort == ReasoningEffort.HIGH -> 16384
        else -> 8192
    }
}
