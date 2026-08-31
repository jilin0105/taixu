package top.wkbin.taixu.harness.subagent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import top.wkbin.taixu.harness.ApiMessage
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.CapabilityEvent
import top.wkbin.taixu.harness.HarnessApiMapper
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.HarnessTool
import top.wkbin.taixu.harness.ProviderClient
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolExecutor
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.harness.effects.ToolReplayPolicy
import top.wkbin.taixu.harness.operation.OperationCoordinator
import top.wkbin.taixu.harness.session.SessionTreeStore
import top.wkbin.taixu.harness.validation.ToolCallLoopDetector
import top.wkbin.taixu.harness.validation.ToolSchemaValidator
import top.wkbin.taixu.harness.R

data class SubagentLaneResult(
    val success: Boolean,
    val summary: String,
    val toolCallCount: Int,
)

/**
 * Headless lane interpreter used by subagents; it shares tree history but owns its operation.
 *
 * [ToolExecutor] 以 [Provider] 注入以打断 Hilt 依赖环：
 * ToolExecutor → SubagentOrchestrator → SubagentLaneRunner → ToolExecutor。
 * 工具只在 [run] 执行期才实际取用，构造期延迟解析是安全的。
 */
@Singleton
class SubagentLaneRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val providerClient: ProviderClient,
    private val toolExecutor: Provider<ToolExecutor>,
    private val treeStore: SessionTreeStore,
    private val operations: OperationCoordinator,
    private val json: Json,
) {
    suspend fun run(
        sessionId: String,
        laneName: String,
        prompt: String,
        workspace: String,
        modelId: String? = null,
        modelVariant: String? = null,
    ): SubagentLaneResult {
        val user = top.wkbin.taixu.harness.UserMessage(UUID.randomUUID().toString(), now(), prompt)
        val operationId = operations.acceptRun(sessionId, user, laneName)
        var toolCalls = 0
        var finalText = ""
        return try {
            val configuredModel = providerClient.resolveConfigured(modelId, modelVariant)
            val loopDetector = ToolCallLoopDetector()
            repeat(MAX_ROUNDS) { round ->
                val forceFinalAnswer = round == MAX_ROUNDS - 1
                val model = if (forceFinalAnswer) configuredModel.copy(pureChatMode = true) else configuredModel
                val responseId = UUID.randomUUID().toString()
                operations.providerIntent(operationId, responseId, round, 1, NETWORK_ATTEMPTS)
                val text = StringBuilder()
                val result = chatWithRetry(model, providerMessages(sessionId, laneName, forceFinalAnswer), text)
                val assistantText = text.toString().ifBlank { result.content.orEmpty() }
                val usageEntity = result.usage.takeIf { it.hasData }?.let {
                    operations.usageEntity(
                        sessionId = sessionId,
                        operationId = operationId,
                        entryId = responseId.takeIf { assistantText.isNotBlank() },
                        provider = model.provider,
                        modelId = model.model,
                        usage = it,
                    )
                }
                if (assistantText.isNotBlank()) {
                    val assistant = AssistantText(
                        id = responseId,
                        createdAt = now(),
                        text = assistantText,
                        reasoning = result.reasoningContent,
                        modelId = model.model,
                        providerId = model.provider,
                        promptTokens = result.usage.inputTokens.takeIf { it > 0 }?.toInt(),
                        completionTokens = result.usage.outputTokens.takeIf { it > 0 }?.toInt(),
                        cachedTokens = result.usage.cacheReadTokens.takeIf { it > 0 }?.toInt(),
                    )
                    operations.providerSettled(operationId, assistant, usage = usageEntity, round = round)
                    finalText = assistantText
                } else {
                    operations.providerSettled(operationId, null, usage = usageEntity, round = round)
                }
                // 最后一轮不向 Provider 暴露工具；即便兼容端仍返回了幻影 tool_call，
                // 也以当前已收集内容收敛，避免重新进入第 13 轮后被硬判失败。
                if (forceFinalAnswer || result.toolCalls.isEmpty()) {
                    operations.finish(sessionId, "completed", responseId, laneName = laneName)
                    return SubagentLaneResult(true, finalText.ifBlank { "子智能体已完成（无文本输出）" }, toolCalls)
                }

                for (spec in result.toolCalls) {
                    toolCalls++
                    val rawName = spec.name.trim()
                    val tool = HarnessApiMapper.toolByName(rawName)
                    val args = runCatching { json.parseToJsonElement(spec.argumentsJson) as JsonObject }.getOrElse {
                        val invalidCall = ToolCall(spec.id, now(), tool, JsonObject(emptyMap()), result.reasoningContent, rawName)
                        operations.toolIntent(
                            operationId,
                            invalidCall,
                            spec.argumentsJson,
                            ToolReplayPolicy.forTool(tool, rawName),
                            round,
                        )
                        val failed = ToolResult(UUID.randomUUID().toString(), now(), spec.id, false, "工具参数不是 JSON 对象：${it.message}")
                        operations.toolSettled(operationId, failed, round, toolName = rawName)
                        continue
                    }
                    val call = ToolCall(spec.id, now(), tool, args, result.reasoningContent, rawName)
                    val schemaProblems = ToolSchemaValidator.problemsFor(rawName, args, model.dynamicMcpTools)
                    if (schemaProblems.isNotEmpty()) {
                        operations.toolIntent(operationId, call, spec.argumentsJson, ToolReplayPolicy.forTool(tool, rawName), round)
                        val rejected = ToolResult(
                            UUID.randomUUID().toString(), now(), spec.id, false,
                            "工具参数校验未通过：${schemaProblems.joinToString("；")}。请修正参数后重新调用。",
                        )
                        operations.toolSettled(operationId, rejected, round, toolName = rawName)
                        continue
                    }
                    val loopVerdict = loopDetector.evaluate(rawName, args)
                    if (loopVerdict is ToolCallLoopDetector.LoopVerdict.Block) {
                        operations.toolIntent(operationId, call, spec.argumentsJson, ToolReplayPolicy.forTool(tool, rawName), round)
                        val blocked = ToolResult(
                            UUID.randomUUID().toString(), now(), spec.id, false,
                            "${loopVerdict.reason}\n\n${loopVerdict.guidance}",
                        )
                        operations.toolSettled(operationId, blocked, round, toolName = rawName)
                        continue
                    }
                    operations.toolIntent(operationId, call, spec.argumentsJson, ToolReplayPolicy.forTool(tool, rawName), round)
                    loopDetector.recordIntent(rawName, args)
                    val outcome = if (tool == HarnessTool.SUBAGENT) {
                        ToolResult(UUID.randomUUID().toString(), now(), call.id, false, "子智能体 Lane 禁止再次派发子智能体")
                    } else {
                        toolExecutor.get().execute(
                            call,
                            sessionId,
                            workspace,
                            allowApprovalRequest = false,
                            operationId = operationId,
                        )
                    }
                    operations.toolSettled(operationId, outcome, round, toolName = rawName)
                    loopDetector.recordSettled(rawName, args, outcome.success)
                }
            }
            operations.finish(sessionId, "failed", details = "max rounds", laneName = laneName)
            SubagentLaneResult(false, finalText.ifBlank { "达到子智能体最大工具轮数" }, toolCalls)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            // 结构化取消（用户停止或编排层超时）必须向上重抛；
            // 否则 lane operation 永远停留在 RUNNING，形成僵尸行。
            // finish 自身是挂起点，需在 NonCancellable 下落盘（与主循环清理链同一模式）。
            withContext(kotlinx.coroutines.NonCancellable) {
                operations.finish(sessionId, "aborted", details = "已取消", laneName = laneName)
            }
            throw cancellation
        } catch (throwable: Throwable) {
            operations.finish(sessionId, "failed", details = throwable.message, laneName = laneName)
            SubagentLaneResult(false, throwable.message ?: "子智能体执行失败", toolCalls)
        }
    }

    private suspend fun providerMessages(
        sessionId: String,
        laneName: String,
        forceFinalAnswer: Boolean,
    ): List<ApiMessage> = isolatedProviderMessages(
        messages = treeStore.load(sessionId, laneName),
        systemPrompt = context.getString(R.string.harness_prompt_subagent_lane_system),
        forceFinalAnswer = forceFinalAnswer,
    )

    private suspend fun chatWithRetry(
        model: top.wkbin.taixu.harness.ModelConfig,
        messages: List<ApiMessage>,
        text: StringBuilder,
    ): top.wkbin.taixu.harness.ChatResult {
        var lastFailure: IOException? = null
        repeat(NETWORK_ATTEMPTS) { attempt ->
            try {
                return providerClient.chatStream(model, messages, onReasoning = {}) { text.append(it) }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (failure: IOException) {
                lastFailure = failure
                text.clear()
                if (attempt + 1 < NETWORK_ATTEMPTS) delay(NETWORK_RETRY_DELAY_MS)
            }
        }
        throw requireNotNull(lastFailure)
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val MAX_ROUNDS = 12
        private const val NETWORK_ATTEMPTS = 2
        private const val NETWORK_RETRY_DELAY_MS = 1_000L
    }
}

internal fun isolatedProviderMessages(
    messages: List<HarnessMessage>,
    systemPrompt: String,
    forceFinalAnswer: Boolean,
): List<ApiMessage> = buildList {
    val finalInstruction = if (forceFinalAnswer) {
        "\n这是最后一轮。禁止继续调用工具，请根据已有结果直接输出结论；信息不完整时明确说明限制。"
    } else {
        ""
    }
    add(ApiMessage(role = "system", content = systemPrompt + finalInstruction))
    val taskStart = messages.indexOfLast { it is top.wkbin.taixu.harness.UserMessage }
        .takeIf { it >= 0 } ?: messages.size
    messages.drop(taskStart).forEach { message ->
        if (message !is CapabilityEvent) add(HarnessApiMapper.toApiMessage(message))
    }
}
