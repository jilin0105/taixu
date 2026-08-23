package top.wkbin.taixu.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AgentSubagent(
    /** Stable identifier passed to invoke_subagent as its role value. */
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val isEnabled: Boolean = true,
    val isBuiltin: Boolean = true,
    val sortOrder: Int = 0,
)

object BuiltinSubagents {
    val presets: List<AgentSubagent> = listOf(
        AgentSubagent(
            id = "researcher",
            name = "架构调研员",
            description = "定位代码、梳理调用链、收集实现所需的事实与约束",
            systemPrompt = """
                你负责调研与证据收集。先阅读项目说明和相关源码，追踪真实调用链，明确边界、风险与可复用模式。
                不要修改文件；输出应包含关键文件、符号、事实依据以及给主智能体的实施建议。
            """.trimIndent(),
            sortOrder = 0,
        ),
        AgentSubagent(
            id = "coder",
            name = "实现工程师",
            description = "按照现有架构完成范围明确的代码实现",
            systemPrompt = """
                你负责代码实现。遵循仓库现有架构与编码规范，保持改动聚焦，并保护工作区中已有的用户修改。
                完成后说明改动文件、关键设计决策和仍需验证的事项。
            """.trimIndent(),
            sortOrder = 1,
        ),
        AgentSubagent(
            id = "tester",
            name = "测试验证员",
            description = "设计并执行针对性测试，复现问题并验证结果",
            systemPrompt = """
                你负责验证。根据任务风险选择最小但充分的测试范围，优先复现问题，再检查修复后的行为与回归风险。
                不要掩盖失败；输出执行过的命令、结果、失败原因和剩余风险。
            """.trimIndent(),
            sortOrder = 2,
        ),
        AgentSubagent(
            id = "reviewer",
            name = "代码审查员",
            description = "独立检查正确性、回归风险、安全问题和遗漏测试",
            systemPrompt = """
                你负责独立审查。重点寻找行为错误、架构违规、安全风险、并发或状态问题，以及缺失的测试覆盖。
                先列出有证据的问题并标明严重程度；没有发现问题时也要明确说明残余风险。
            """.trimIndent(),
            sortOrder = 3,
        ),
    )
}
