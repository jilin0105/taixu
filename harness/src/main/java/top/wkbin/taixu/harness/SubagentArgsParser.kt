package top.wkbin.taixu.harness

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.taixu.core.model.SubagentTaskSpec

/**
 * 子智能体参数解析工具类：
 * 统一处理 `invoke_subagent` 的 `subagents` 列表参数以及单个任务调用的兼容结构。
 */
object SubagentArgsParser {
    private const val MAX_SUBAGENTS = 6

    fun parse(args: JsonObject, defaultTaskName: String = "子任务"): List<SubagentTaskSpec> {
        val list = mutableListOf<SubagentTaskSpec>()
        val subagentsArray = args["subagents"]?.jsonArray
        if (subagentsArray != null) {
            for (elem in subagentsArray) {
                val obj = elem.jsonObject
                val taskName = obj["taskName"]?.jsonPrimitive?.content ?: defaultTaskName
                val role = obj["role"]?.jsonPrimitive?.content ?: "assistant"
                val prompt = obj["prompt"]?.jsonPrimitive?.content ?: continue
                list.add(SubagentTaskSpec(taskName, role, prompt))
            }
        } else {
            // 单个 subagent 调用兼容
            val prompt = args["prompt"]?.jsonPrimitive?.content
            val role = args["role"]?.jsonPrimitive?.content ?: "assistant"
            val taskName = args["taskName"]?.jsonPrimitive?.content ?: defaultTaskName
            if (!prompt.isNullOrBlank()) {
                list.add(SubagentTaskSpec(taskName, role, prompt))
            }
        }
        return list.take(MAX_SUBAGENTS)
    }
}
