package top.wkbin.taixu.harness.dual

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 模型无关的 Planner 决策协议解析器（Model-Agnostic Protocol Parser）。
 *
 * 核心目标：
 * 太墟支持任何 LLM（OpenAI, Anthropic Claude, Google Gemini, DeepSeek, 阿里通义千问, 智谱 GLM, 本地 Ollama 等）。
 * 本解析器专为多模型输出设计，具备高度容错：
 * 1. 优先提取 Markdown 代码块 ```json ... ```；
 * 2. 兜底提取前后带有杂质文本的裸 JSON 对象 `{ ... }`；
 * 3. 对小参数量或未严格遵循 JSON 的自然语言回复，支持中英文完成信号智能识别；
 * 4. 彻底解耦，不依赖特定厂商私有结构。
 */
object PlannerProtocolParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String, currentSteps: List<PlanStep>): PlannerDecision {
        val jsonPattern = Regex("""```(?:json)?\s*(\{.*?\})\s*```""", RegexOption.DOT_MATCHES_ALL)
        val match = jsonPattern.find(text)
        val rawJson = match?.groupValues?.get(1) ?: text.substringAfter("{", "").let {
            if (it.isNotBlank()) "{" + it.substringBeforeLast("}") + "}" else ""
        }

        val parsed = runCatching {
            json.parseToJsonElement(rawJson).jsonObject
        }.getOrNull()

        if (parsed == null) {
            val lower = text.lowercase()
            val isCompleted = listOf("已完成", "全部完成", "实现完毕", "任务完成", "completed", "all done", "finished", "all tasks are completed")
                .any { it in lower || it in text }
            return if (isCompleted) {
                PlannerDecision.Finish(finalReport = text, completedSteps = currentSteps)
            } else {
                val step = PlanStep(
                    id = "step_${currentSteps.size + 1}",
                    title = "执行下一步",
                    instruction = text.take(500),
                )
                PlannerDecision.ExecuteStep(step = step, updatedPlan = currentSteps + step)
            }
        }

        val action = parsed["action"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "EXECUTE_STEP"
        return when (action) {
            "FINISH" -> {
                val report = parsed["finalReport"]?.jsonPrimitive?.contentOrNull ?: text
                PlannerDecision.Finish(finalReport = report, completedSteps = currentSteps)
            }
            "REPLAN" -> {
                val reason = parsed["thought"]?.jsonPrimitive?.contentOrNull ?: "规划微调"
                PlannerDecision.Replan(reason = reason, newSteps = currentSteps)
            }
            else -> {
                val stepObj = parsed["step"] as? JsonObject
                val stepId = stepObj?.get("id")?.jsonPrimitive?.contentOrNull
                    ?: "step_${currentSteps.size + 1}"
                val title = stepObj?.get("title")?.jsonPrimitive?.contentOrNull ?: "工序 $stepId"
                val instruction = stepObj?.get("instruction")?.jsonPrimitive?.contentOrNull
                    ?: parsed["thought"]?.jsonPrimitive?.contentOrNull ?: text
                val expected = stepObj?.get("expectedOutcome")?.jsonPrimitive?.contentOrNull.orEmpty()
                val step = PlanStep(
                    id = stepId,
                    title = title,
                    instruction = instruction,
                    expectedOutcome = expected,
                )
                PlannerDecision.ExecuteStep(step = step, updatedPlan = currentSteps + step)
            }
        }
    }
}
