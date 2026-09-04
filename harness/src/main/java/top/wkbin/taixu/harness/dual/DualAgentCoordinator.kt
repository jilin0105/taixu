package top.wkbin.taixu.harness.dual

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.taixu.harness.ApiMessage
import top.wkbin.taixu.harness.ModelConfig
import top.wkbin.taixu.harness.ProviderClient
import top.wkbin.taixu.harness.ToolCallMode
import top.wkbin.taixu.harness.subagent.SubagentLaneRunner

/**
 * 双智能体调度中枢（Dual-Agent Coordinator）。
 *
 * 核心设计（借鉴 DeepSeek-Reasonix v2）：
 * 1. 物理会话隔离：Planner 运行于纯净无工具 Lane，Executor 运行于独立的单步 Lane；
 * 2. 异构模型协同：Planner 使用推理模型深思熟虑，Executor 使用极速模型精准执行；
 * 3. 前缀缓存保护：海量工具日志停留在 Executor 内部，仅提炼紧凑交付物回传 Planner。
 */
@Singleton
class DualAgentCoordinator @Inject constructor(
    private val providerClient: ProviderClient,
    private val laneRunner: SubagentLaneRunner,
    private val promptBuilder: PlannerPromptBuilder,
    private val json: Json,
) {

    suspend fun execute(
        sessionId: String,
        userPrompt: String,
        workspace: String,
        plannerModel: ModelConfig,
        executorModel: ModelConfig,
        maxSteps: Int = DEFAULT_MAX_STEPS,
        onPlanUpdated: (List<PlanStep>) -> Unit = {},
        onStatusUpdate: (String) -> Unit = {},
    ): DualAgentOutcome = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val steps = mutableListOf<PlanStep>()
        var totalToolCalls = 0
        var roundCount = 0

        // Planner 上下文：保持极简与增量 append-only
        val plannerSystem = promptBuilder.buildSystemPrompt(workspace)
        val plannerMessages = mutableListOf<ApiMessage>(
            ApiMessage(role = "system", content = plannerSystem),
            ApiMessage(role = "user", content = userPrompt),
        )

        // Planner 纯规划模式：完全关闭工具调用注入
        val effectivePlannerModel = plannerModel.copy(
            pureChatMode = true,
            toolCallMode = ToolCallMode.DISABLED,
        )

        while (roundCount < maxSteps) {
            roundCount++
            onStatusUpdate("规划者 (Planner) 正在深度推演…（轮次 $roundCount/$maxSteps）")

            // 1. 调用 Planner 生成决策
            val plannerResult = runCatching {
                providerClient.chat(effectivePlannerModel, plannerMessages)
            }.getOrElse { throwable ->
                return@withContext DualAgentOutcome.Failed(
                    message = "Planner 模型调用失败：${throwable.message}",
                    plan = steps,
                    failedStep = steps.lastOrNull { it.status == StepStatus.RUNNING },
                )
            }

            val plannerText = plannerResult.content.orEmpty()
            plannerMessages.add(ApiMessage(role = "assistant", content = plannerText))

            // 2. 解析 Planner 决策
            val decision = parsePlannerDecision(plannerText, steps)

            when (decision) {
                is PlannerDecision.Finish -> {
                    onPlanUpdated(steps)
                    onStatusUpdate("所有步骤已完成，任务已交付！")
                    return@withContext DualAgentOutcome.Success(
                        finalReport = decision.finalReport.ifBlank { plannerText },
                        plan = steps,
                        totalRounds = roundCount,
                        totalToolCalls = totalToolCalls,
                        totalDurationMs = System.currentTimeMillis() - startedAt,
                    )
                }
                is PlannerDecision.Replan -> {
                    onStatusUpdate("Planner 调整了执行方案：${decision.reason}")
                    steps.clear()
                    steps.addAll(decision.newSteps)
                    onPlanUpdated(steps)
                }
                is PlannerDecision.ExecuteStep -> {
                    val currentStep = decision.step
                    val stepIndex = steps.indexOfFirst { it.id == currentStep.id }.let {
                        if (it >= 0) it else { steps.add(currentStep); steps.lastIndex }
                    }
                    steps[stepIndex] = currentStep.copy(status = StepStatus.RUNNING)
                    onPlanUpdated(steps)
                    onStatusUpdate("执行者 (Executor) 正在执行：${currentStep.title}")

                    // 3. 在独立的物理 Lane 启动 Executor 执行工具操作
                    val stepLaneName = "executor_${currentStep.id}"
                    val executorPrompt = buildString {
                        appendLine("请严格执行以下单步工序并汇报成果：")
                        appendLine("【步骤标题】: ${currentStep.title}")
                        appendLine("【详细指令】: ${currentStep.instruction}")
                        if (currentStep.expectedOutcome.isNotBlank()) {
                            appendLine("【预期成果】: ${currentStep.expectedOutcome}")
                        }
                    }

                    val stepResult = runCatching {
                        laneRunner.run(
                            sessionId = sessionId,
                            laneName = stepLaneName,
                            prompt = executorPrompt,
                            workspace = workspace,
                            modelId = executorModel.name,
                            modelVariant = executorModel.model,
                        )
                    }.getOrElse { throwable ->
                        null
                    }

                    val isSuccess = stepResult?.success == true
                    val summary = stepResult?.summary?.ifBlank { "执行完成" } ?: "执行中断或遇到异常"
                    totalToolCalls += stepResult?.toolCallCount ?: 0

                    steps[stepIndex] = steps[stepIndex].copy(
                        status = if (isSuccess) StepStatus.COMPLETED else StepStatus.FAILED,
                        resultSummary = summary,
                    )
                    onPlanUpdated(steps)

                    // 4. 将紧凑汇报组装为反馈回传给 Planner
                    val feedbackText = buildString {
                        appendLine("【步骤 ${currentStep.id}（${currentStep.title}）执行完毕】")
                        appendLine("状态: ${if (isSuccess) "成功" else "失败"}")
                        appendLine("交付总结: $summary")
                        appendLine("请评估当前成果并给出下一步动作（EXECUTE_STEP 或 FINISH）。")
                    }
                    plannerMessages.add(ApiMessage(role = "user", content = feedbackText))
                }
            }
        }

        DualAgentOutcome.Failed(
            message = "已达到最大规划步数上限（$maxSteps 步），未能全部完成",
            plan = steps,
            failedStep = steps.lastOrNull { it.status != StepStatus.COMPLETED },
        )
    }

    internal fun parsePlannerDecision(text: String, currentSteps: List<PlanStep>): PlannerDecision =
        PlannerProtocolParser.parse(text, currentSteps)

    companion object {

        const val DEFAULT_MAX_STEPS = 10
    }
}
