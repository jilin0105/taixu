package top.wkbin.taixu.harness

import top.wkbin.taixu.core.database.HarnessSessionRepository
import top.wkbin.taixu.core.model.SubagentTaskSpec
import top.wkbin.taixu.core.model.AgentSubagent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.taixu.harness.session.LaneManager
import top.wkbin.taixu.harness.subagent.SubagentLaneRunner

/**
 * Subagent 子智能体任务编排器：
 * 负责解析主智能体的 invoke_subagent 请求，动态创建隔离的子会话，
 * 并发调度子智能体执行研究、编写或测试任务，最终汇聚输出结构化 Markdown。
 */
@Singleton
class SubagentOrchestrator @Inject constructor(
    private val sessionDao: HarnessSessionRepository,
    private val laneManager: LaneManager,
    private val laneRunner: SubagentLaneRunner,
    private val subagentRepository: top.wkbin.taixu.core.database.AgentSubagentRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun executeSubagents(
        args: JsonObject,
        parentSessionId: String,
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val parentSession = sessionDao.findById(parentSessionId)
        val workspace = parentSession?.workspace.orEmpty()
        val modelId = parentSession?.modelId
        val projectType = parentSession?.projectType.orEmpty()
        val specs = parseSubagentSpecs(args)
        if (specs.isEmpty()) {
            return@withContext false to "未解析到有效的 subagents 任务列表，请检查参数"
        }

        val profiles = subagentRepository.enabledProfiles()
        if (profiles.isEmpty()) {
            return@withContext false to "当前没有启用的子智能体角色，请先在 Agent 设置中添加或启用角色"
        }
        val parentLeaf = laneManager.get(parentSessionId, "main")?.leafId

        val results = specs.map { spec ->
            async {
                val profile = profiles.firstOrNull { configured ->
                    configured.id.equals(spec.role, ignoreCase = true) ||
                        configured.name.equals(spec.role, ignoreCase = true)
                }
                if (profile == null) {
                    return@async SubagentExecutionOutcome(
                        spec = spec,
                        subSessionId = "",
                        isSuccess = false,
                        summary = "角色 ${spec.role} 未配置或未启用。可用角色：${profiles.joinToString { it.id }}",
                        toolCallCount = 0,
                    )
                }
                val laneName = "subagent:${profile.id}:${java.util.UUID.randomUUID()}"
                laneManager.create(parentSessionId, laneName, parentLeaf)
                val prompt = buildSubagentPrompt(spec, profile, workspace)
                val laneResult = withTimeoutOrNull(180_000L) {
                    laneRunner.run(parentSessionId, laneName, prompt, workspace)
                }

                SubagentExecutionOutcome(
                    spec = spec,
                    subSessionId = laneName,
                    isSuccess = laneResult?.success == true,
                    summary = laneResult?.summary ?: "执行超时 (3 分钟)",
                    toolCallCount = laneResult?.toolCallCount ?: 0,
                )
            }
        }.awaitAll()

        val summaryMarkdown = buildSummaryMarkdown(results)
        val allSuccess = results.all { it.isSuccess }
        allSuccess to summaryMarkdown
    }

    private fun parseSubagentSpecs(args: JsonObject): List<SubagentTaskSpec> {
        val list = mutableListOf<SubagentTaskSpec>()
        val subagentsArray = args["subagents"]?.jsonArray
        if (subagentsArray != null) {
            for (elem in subagentsArray) {
                val obj = elem.jsonObject
                val taskName = obj["taskName"]?.jsonPrimitive?.content ?: "子任务"
                val role = obj["role"]?.jsonPrimitive?.content ?: "assistant"
                val prompt = obj["prompt"]?.jsonPrimitive?.content ?: continue
                list.add(SubagentTaskSpec(taskName, role, prompt))
            }
        } else {
            // 单个 subagent 调用兼容
            val prompt = args["prompt"]?.jsonPrimitive?.content
            val role = args["role"]?.jsonPrimitive?.content ?: "assistant"
            val taskName = args["taskName"]?.jsonPrimitive?.content ?: "任务"
            if (!prompt.isNullOrBlank()) {
                list.add(SubagentTaskSpec(taskName, role, prompt))
            }
        }
        return list.take(MAX_SUBAGENTS)
    }

    private fun buildSubagentPrompt(spec: SubagentTaskSpec, profile: AgentSubagent, workspace: String): String {
        return buildString {
            append("【子智能体任务指派】\n")
            append("角色定位：${profile.name} (${profile.id})\n")
            append("任务目标：${spec.taskName}\n")
            if (workspace.isNotBlank()) append("工作区：$workspace\n")
            append("\n角色专属指导：\n${profile.systemPrompt.trim()}\n")
            append("\n任务详情：\n${spec.prompt}\n\n")
            append("你是被主智能体派发的子智能体，禁止调用 invoke_subagent 或继续拆分子智能体。")
            append("请集中精力使用工具解决该特定任务，并在最后输出清晰简明的结论与发现。")
        }
    }

    private fun buildSummaryMarkdown(outcomes: List<SubagentExecutionOutcome>): String {
        return buildString {
            append("### 🤖 子智能体协同执行完成 (共 ${outcomes.size} 个任务)\n\n")
            outcomes.forEachIndexed { index, outcome ->
                val statusIcon = if (outcome.isSuccess) "✅" else "⚠️"
                append("#### ${index + 1}. $statusIcon 【${outcome.spec.taskName}】(角色: ${outcome.spec.role})\n")
                append("- **工具调用次数**：${outcome.toolCallCount} 次\n")
                append("- **子任务输出**：\n")
                append(outcome.summary.trim())
                append("\n\n")
            }
        }
    }

    private data class SubagentExecutionOutcome(
        val spec: SubagentTaskSpec,
        val subSessionId: String,
        val isSuccess: Boolean,
        val summary: String,
        val toolCallCount: Int,
    )

    private companion object {
        const val MAX_SUBAGENTS = 6
    }
}
