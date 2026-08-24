package top.wkbin.taixu.harness

import top.wkbin.taixu.core.database.HarnessMessageRepository
import top.wkbin.taixu.core.database.HarnessSessionRepository
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.model.SubagentTaskSpec
import top.wkbin.taixu.core.model.AgentSubagent
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
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

/**
 * Subagent 子智能体任务编排器：
 * 负责解析主智能体的 invoke_subagent 请求，动态创建隔离的子会话，
 * 并发调度子智能体执行研究、编写或测试任务，最终汇聚输出结构化 Markdown。
 */
@Singleton
class SubagentOrchestrator @Inject constructor(
    private val sessionDao: HarnessSessionRepository,
    private val messageDao: HarnessMessageRepository,
    private val harnessLoopProvider: Provider<HarnessLoop>,
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
        if (parentSession?.title?.startsWith(SUBAGENT_SESSION_PREFIX) == true) {
            return@withContext false to "子智能体会话禁止再次派发子智能体，请直接完成当前子任务"
        }

        val specs = parseSubagentSpecs(args)
        if (specs.isEmpty()) {
            return@withContext false to "未解析到有效的 subagents 任务列表，请检查参数"
        }

        val profiles = subagentRepository.enabledProfiles()
        if (profiles.isEmpty()) {
            return@withContext false to "当前没有启用的子智能体角色，请先在 Agent 设置中添加或启用角色"
        }
        val harnessLoop = harnessLoopProvider.get()

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
                val now = System.currentTimeMillis()
                val subSessionId = UUID.randomUUID().toString()
                val subSessionTitle = "$SUBAGENT_SESSION_PREFIX ${spec.taskName} (${profile.name})"
                val subSession = HarnessSessionEntity(
                    id = subSessionId,
                    title = subSessionTitle,
                    createdAt = now,
                    updatedAt = now,
                    modelId = modelId,
                    workspace = workspace,
                    projectType = projectType,
                    approvalMode = parentSession?.approvalMode ?: top.wkbin.taixu.core.model.ApprovalMode.ASSISTED.id,
                )
                sessionDao.upsert(subSession)

                // 启动子智能体
                val prompt = buildSubagentPrompt(spec, profile, workspace)
                harnessLoop.send(prompt, subSessionId)

                // 等待子智能体执行收尾（最长等待 3 分钟）
                val completed = withTimeoutOrNull(180_000L) {
                    while (true) {
                        val isRunning = harnessLoop.sessionRunStates.value[subSessionId] == top.wkbin.taixu.core.model.SessionRunState.RUNNING
                        if (!isRunning) break
                        kotlinx.coroutines.delay(500)
                    }
                    true
                } ?: false

                // 获取子智能体生成的最新回复和工具执行情况
                val messages = messageDao.listForSession(subSessionId)
                val lastAssistant = messages.lastOrNull { it.type == "assistant" }?.let {
                    runCatching { json.decodeFromString<AssistantText>(it.payloadJson) }.getOrNull()
                }
                val toolCallCount = messages.count { it.type == "tool_call" }

                SubagentExecutionOutcome(
                    spec = spec,
                    subSessionId = subSessionId,
                    isSuccess = completed && lastAssistant != null,
                    summary = lastAssistant?.text ?: if (!completed) "执行超时 (3 分钟)" else "无返回结果",
                    toolCallCount = toolCallCount,
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
        const val SUBAGENT_SESSION_PREFIX = "子任务:"
    }
}
