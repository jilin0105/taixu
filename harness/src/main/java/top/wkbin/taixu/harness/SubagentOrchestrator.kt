package top.wkbin.taixu.harness

import top.wkbin.taixu.core.database.HarnessSessionRepository
import top.wkbin.taixu.core.model.AgentSubagent
import top.wkbin.taixu.core.model.AgentSubagentIndexEntry
import top.wkbin.taixu.core.model.SubagentTaskSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import top.wkbin.taixu.harness.session.LaneManager
import top.wkbin.taixu.harness.subagent.SubagentLaneRunner
import top.wkbin.taixu.harness.prompt.PromptAssetLoader

internal class SubagentConcurrencyGate(
    maxParallelism: Int = DEFAULT_MAX_CONCURRENT_SUBAGENTS,
) {
    private val permits = Semaphore(maxParallelism.coerceAtLeast(1))

    suspend fun <T> withPermit(block: suspend () -> T): T = permits.withPermit { block() }
}

internal const val DEFAULT_MAX_CONCURRENT_SUBAGENTS = 3

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
    private val promptAssets: PromptAssetLoader,
) {
    /**
     * Application-wide budget: a three-agent fan-out should actually run three lanes at once,
     * while larger batches still queue to protect the shared API/PRoot/Room resources.
     */
    private val globalParallelism = SubagentConcurrencyGate()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun executeSubagents(
        args: JsonObject,
        parentSessionId: String,
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val parentSession = sessionDao.findById(parentSessionId)
        val workspace = parentSession?.workspace.orEmpty()
        val modelId = parentSession?.modelId
        val modelVariant = parentSession?.modelVariant
        val projectType = parentSession?.projectType.orEmpty()
        val specs = SubagentArgsParser.parse(args)
        if (specs.isEmpty()) {
            return@withContext false to "未解析到有效的 subagents 任务列表，请检查参数"
        }

        val profileIndex = subagentRepository.enabledIndex()
        if (profileIndex.isEmpty()) {
            return@withContext false to "当前没有启用的子智能体角色，请先在 Agent 设置中添加或启用角色"
        }
        val parentLeaf = laneManager.get(parentSessionId, "main")?.leafId

        val results = specs.map { spec ->
            async {
                globalParallelism.withPermit {
                    val profile = resolveProfile(spec, profileIndex)
                    if (profile == null) {
                        return@withPermit SubagentExecutionOutcome(
                            spec = spec,
                            subSessionId = "",
                            isSuccess = false,
                            summary = routingFailure(spec),
                            toolCallCount = 0,
                        )
                    }
                    val laneName = "subagent:${profile.id}:${java.util.UUID.randomUUID()}"
                    laneManager.create(parentSessionId, laneName, parentLeaf)
                    val prompt = buildSubagentPrompt(spec, profile, workspace)
                    val laneResult = withTimeoutOrNull(SUBAGENT_TIMEOUT_MS) {
                        laneRunner.run(parentSessionId, laneName, prompt, workspace, modelId, modelVariant)
                    }

                    SubagentExecutionOutcome(
                        spec = spec,
                        subSessionId = laneName,
                        isSuccess = laneResult?.success == true,
                        summary = laneResult?.summary ?: "执行超时 (${SUBAGENT_TIMEOUT_MS / 60_000} 分钟)",
                        toolCallCount = laneResult?.toolCallCount ?: 0,
                        resolvedProfileId = profile.id,
                        resolvedProfileName = profile.name,
                    )
                }
            }
        }.awaitAll()

        val summaryMarkdown = buildSummaryMarkdown(results)
        val anySuccess = results.any { it.isSuccess }
        anySuccess to summaryMarkdown
    }

    private suspend fun resolveProfile(
        spec: SubagentTaskSpec,
        profileIndex: List<AgentSubagentIndexEntry>,
    ): AgentSubagent? {
        val selected = if (spec.role.isNotBlank()) {
            profileIndex.firstOrNull { entry ->
                entry.id.equals(spec.role, ignoreCase = true) ||
                    entry.name.equals(spec.role, ignoreCase = true)
            }
        } else {
            SubagentProfileMatcher.match(profileIndex, spec.department, spec.agentQuery)
        } ?: return null
        return subagentRepository.findEnabledProfile(selected.id)
    }

    private fun routingFailure(spec: SubagentTaskSpec): String = if (spec.role.isNotBlank()) {
        "精确角色 ${spec.role} 未配置或未启用。可改用 department + agentQuery 让本地索引派发。"
    } else {
        "部门 ${spec.department} 中没有匹配 agentQuery=\"${spec.agentQuery}\" 的已启用角色。" +
            "请保留部门并改用 2–5 个更具体的英文专业关键词。"
    }

    private fun buildSubagentPrompt(spec: SubagentTaskSpec, profile: AgentSubagent, workspace: String): String {
        return promptAssets.render(
            "prompts/subagent_task.md",
            mapOf(
                "ROLE_NAME" to profile.name,
                "ROLE_ID" to profile.id,
                "TASK_NAME" to spec.taskName,
                "WORKSPACE_LINE" to workspace.takeIf { it.isNotBlank() }?.let { "工作区：$it" }.orEmpty(),
                "ROLE_PROMPT" to profile.systemPrompt.trim(),
                "TASK_PROMPT" to spec.prompt,
            ),
        )
    }

    private companion object {
        const val SUBAGENT_TIMEOUT_MS = 6 * 60 * 1000L
    }

    private fun buildSummaryMarkdown(outcomes: List<SubagentExecutionOutcome>): String {
        return buildString {
            val succeeded = outcomes.count { it.isSuccess }
            val batchStatus = when (succeeded) {
                outcomes.size -> "全部成功"
                0 -> "全部失败"
                else -> "部分成功"
            }
            append("### 🤖 子智能体协同执行完成 · $batchStatus ($succeeded/${outcomes.size})\n\n")
            outcomes.forEachIndexed { index, outcome ->
                val statusIcon = if (outcome.isSuccess) "✅" else "⚠️"
                val resolvedRole = if (outcome.resolvedProfileId != null) {
                    "${outcome.resolvedProfileName} · ${outcome.resolvedProfileId}"
                } else {
                    outcome.spec.role.ifBlank { "${outcome.spec.department} / ${outcome.spec.agentQuery}" }
                }
                append("#### ${index + 1}. $statusIcon 【${outcome.spec.taskName}】(角色: $resolvedRole)\n")
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
        val resolvedProfileId: String? = null,
        val resolvedProfileName: String? = null,
    )

}
