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
) {
    suspend fun execute(toolCall: ToolCall): ToolResult {
        val now = System.currentTimeMillis()
        val outcome = try {
            executeTool(toolCall.tool, toolCall.args)
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
            output = secretRedactor.redact(rawOutput).take(MAX_OUTPUT_LENGTH),
        )
    }

    private suspend fun executeTool(tool: HarnessTool, args: JsonObject): Pair<Boolean, String> = when (tool) {
        HarnessTool.READ -> {
            val path = requireString(args, "path")
            fileAccess.read(path).toToolOutput()
        }
        HarnessTool.WRITE -> {
            val path = requireString(args, "path")
            val content = requireString(args, "content")
            fileAccess.write(path, content).toToolOutput("已写入 $path")
        }
        HarnessTool.EDIT -> {
            val path = requireString(args, "path")
            val oldText = requireString(args, "oldText")
            val newText = requireString(args, "newText")
            fileAccess.edit(path, oldText, newText).toToolOutput("已修改 $path")
        }
        HarnessTool.BASE -> executeBase(args)
    }

    private suspend fun executeBase(args: JsonObject): Pair<Boolean, String> {
        val command = requireString(args, "command")
        require(command.length <= MAX_COMMAND_LENGTH) { "命令过长（${command.length} 字符，上限 $MAX_COMMAND_LENGTH）" }
        val cwd = args["cwd"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: DEFAULT_CWD
        val result = linuxRuntime.execute(
            ShellCommand(
                commandLine = command,
                workingDirectory = cwd,
                timeoutMs = BASE_TIMEOUT_MS,
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

    private fun AppResult<out Any>.toToolOutput(successMessage: String = ""): Pair<Boolean, String> = when (this) {
        is AppResult.Success -> true to successMessage.ifBlank { data.toString() }
        is AppResult.Failure -> false to (error.message ?: "操作失败")
    }

    private fun requireString(args: JsonObject, key: String): String {
        val value = args[key]?.jsonPrimitive?.content
        require(!value.isNullOrBlank()) { "缺少参数：$key" }
        require(value.length <= MAX_ARG_LENGTH) { "参数 $key 过长（${value.length} 字符，上限 $MAX_ARG_LENGTH）" }
        return value
    }

    companion object {
        const val BASE_TIMEOUT_MS = 5 * 60 * 1000L
        const val MAX_OUTPUT_LENGTH = 64 * 1024
        const val MAX_COMMAND_LENGTH = 32 * 1024
        const val MAX_ARG_LENGTH = 1024 * 1024
        const val DEFAULT_CWD = "/root"
    }
}
