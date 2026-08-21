package top.wkbin.taixu.harness

import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.core.security.SecretRedactor
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.shell.ShellCommand
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Harness 工具执行器：把 LLM 发出的 ToolCall 翻译成受控操作。
 *
 * - read / write / edit → [WorkspaceFileAccess]（工作区路径安全层）
 * - base → [LinuxRuntime.execute]（PRoot 沙箱内执行命令，带超时与输出截断）
 *
 * 任何工具失败都不会抛异常，而是以结构化的 [ToolResult] 返回给 HarnessLoop，
 * 由模型决定下一步（自我纠正）。
 */
@Singleton
class ToolExecutor @Inject constructor(
    private val fileAccess: WorkspaceFileAccess,
    private val linuxRuntime: LinuxRuntime,
    private val secretRedactor: SecretRedactor,
    private val subagentOrchestrator: SubagentOrchestrator? = null,
    private val mcpManager: top.wkbin.taixu.harness.mcp.McpManager? = null,
    private val contextExecutor: AgentContextExecutor? = null,
) {
    suspend fun execute(toolCall: ToolCall, sessionId: String = "", workspace: String = ""): ToolResult {
        val now = System.currentTimeMillis()
        val outcome = try {
            executeTool(toolCall.tool, toolCall.args, toolCall.rawToolName, sessionId, workspace)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            false to "工具执行异常：${throwable.message ?: throwable::class.simpleName}"
        }
        val (success, rawOutput) = outcome
        return ToolResult(
            id = UUID.randomUUID().toString(),
            createdAt = now,
            toolCallId = toolCall.id,
            success = success,
            output = secretRedactor.redact(truncateOutput(rawOutput)),
        )
    }

    /**
     * 输出超限时带元数据截断（pi 的 truncate 设计）：保留头部，并明确告知模型
     * 完整输出的规模与截断事实，引导其用 grep/head/tail 精准取段，
     * 而不是对静默截断的内容得出片面结论。
     */
    private fun truncateOutput(output: String): String {
        if (output.length <= MAX_OUTPUT_LENGTH) return output
        val kept = output.take(TRUNCATE_KEEP_LENGTH)
        val totalLines = output.count { it == '\n' } + 1
        val keptLines = kept.count { it == '\n' } + 1
        return buildString {
            append(kept)
            append("\n\n[输出已截断：完整输出共 ")
            append(totalLines)
            append(" 行 / ")
            append(output.length)
            append(" 字符，以上仅显示前 ")
            append(keptLines)
            append(" 行。需要其余部分请用 grep 过滤关键字、head/tail 取首尾、或 sed -n 'N,Mp' 取指定行段，不要原样重复执行同一命令。]")
        }
    }

    private suspend fun executeTool(
        tool: HarnessTool,
        args: JsonObject,
        rawToolName: String?,
        sessionId: String,
        workspace: String,
    ): Pair<Boolean, String> {
        val activeFileAccess = if (workspace.isNotBlank()) fileAccess.withBase(workspace) else fileAccess
        return when (tool) {
            HarnessTool.READ -> {
                val path = requireString(args, "path")
                val offset = args["offset"]?.jsonPrimitive?.content?.trim()?.toIntOrNull()
                val limit = args["limit"]?.jsonPrimitive?.content?.trim()?.toIntOrNull()
                activeFileAccess.read(path, offset, limit).toToolOutput()
            }
            HarnessTool.WRITE -> {
                val path = requireString(args, "path")
                val content = requireString(args, "content")
                activeFileAccess.write(path, content).toToolOutput("已写入 $path")
            }
            HarnessTool.EDIT -> {
                val path = requireString(args, "path")
                val oldText = requireString(args, "oldText")
                val newText = requireString(args, "newText")
                activeFileAccess.edit(path, oldText, newText).toToolOutput("已修改 $path")
            }
            HarnessTool.BASE -> executeBase(args, workspace)
            HarnessTool.MEMORY -> contextExecutor?.executeMemory(args, sessionId, "") ?: (false to "未初始化记忆执行器")
            HarnessTool.PLAN -> contextExecutor?.executePlan(args, sessionId) ?: (false to "未初始化计划执行器")
            HarnessTool.SCRATCHPAD -> contextExecutor?.executeScratchpad(args, sessionId) ?: (false to "未初始化草稿执行器")
            HarnessTool.SUBAGENT -> subagentOrchestrator?.executeSubagents(args, sessionId) ?: (false to "未初始化子智能体编排器")
            HarnessTool.MCP -> mcpManager?.executeTool(rawToolName ?: "mcp", args) ?: (false to "未初始化 MCP 管理器")
        }
    }

    private suspend fun executeBase(args: JsonObject, workspace: String): Pair<Boolean, String> {
        val command = requireString(args, "command")
        require(command.length <= MAX_COMMAND_LENGTH) { "命令过长（${command.length} 字符，上限 $MAX_COMMAND_LENGTH）" }
        val cwd = args["cwd"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: (if (workspace.isNotBlank()) (if (workspace.startsWith("/")) workspace else "/workspace/$workspace") else DEFAULT_CWD)
        val timeoutMs = args["timeout_seconds"]?.jsonPrimitive?.content?.toLongOrNull()?.let { it * 1000L }
            ?: BASE_TIMEOUT_MS
        val result = linuxRuntime.execute(
            ShellCommand(
                commandLine = command,
                workingDirectory = cwd,
                timeoutMs = timeoutMs,
            ),
        )
        val stdout = result.stdout.trim()
        val stderr = result.stderr.trim()
        val body = buildString {
            append("exit ${result.exitCode} · ${result.durationMs} ms")
            if (stdout.isNotEmpty()) append("\n$stdout")
            if (stderr.isNotEmpty()) append("\n$stderr")
        }
        return result.isSuccess to body
    }

    private fun AppResult<Any>.toToolOutput(successMessage: String = ""): Pair<Boolean, String> = when (this) {
        is AppResult.Success -> true to successMessage.ifBlank { data.toString() }
        is AppResult.Failure -> false to error.message
    }

    private fun requireString(args: JsonObject, key: String): String {
        val value = args[key]?.jsonPrimitive?.content
        require(!value.isNullOrBlank()) { "缺少参数：$key" }
        require(value.length <= MAX_ARG_LENGTH) { "参数 $key 过长（${value.length} 字符，上限 $MAX_ARG_LENGTH）" }
        return value
    }

    companion object {
        const val BASE_TIMEOUT_MS = 60 * 1000L
        const val MAX_OUTPUT_LENGTH = 64 * 1024
        const val TRUNCATE_KEEP_LENGTH = 60 * 1024
        const val MAX_COMMAND_LENGTH = 32 * 1024
        const val MAX_ARG_LENGTH = 1024 * 1024
        const val DEFAULT_CWD = "/root"
    }
}
