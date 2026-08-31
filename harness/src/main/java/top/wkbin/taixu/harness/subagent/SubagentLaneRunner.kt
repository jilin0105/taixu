package top.wkbin.taixu.harness.subagent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
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
    suspend fun run(sessionId: String, laneName: String, prompt: String, workspace: String): SubagentLaneResult {
        val user = top.wkbin.taixu.harness.UserMessage(UUID.randomUUID().toString(), now(), prompt)
        val operationId = operations.acceptRun(sessionId, user, laneName)
        var toolCalls = 0
        var finalText = ""
        return try {
            repeat(MAX_ROUNDS) { round ->
                val model = providerClient.resolveConfigured()
                val responseId = UUID.randomUUID().toString()
                operations.providerIntent(operationId, responseId, round, 1, 1)
                val text = StringBuilder()
                val result = providerClient.chatStream(model, providerMessages(sessionId, laneName), onReasoning = {}) {
                    text.append(it)
                }
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
                if (result.toolCalls.isEmpty()) {
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
                    operations.toolIntent(operationId, call, spec.argumentsJson, ToolReplayPolicy.forTool(tool, rawName), round)
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

    private suspend fun providerMessages(sessionId: String, laneName: String): List<ApiMessage> = buildList {
        val systemPrompt = context.getString(R.string.harness_prompt_subagent_lane_system)
        add(ApiMessage(role = "system", content = systemPrompt))
        treeStore.load(sessionId, laneName).forEach { message ->
            if (message !is CapabilityEvent) add(HarnessApiMapper.toApiMessage(message))
        }
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val MAX_ROUNDS = 12
    }
}
