package top.wkbin.taixu.harness

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Pure context-budget and historical-folding policy used by the provider mapper. */
internal object ContextWindowPolicy {
    // Input budget reserves headroom for system prompt, tool/MCP schemas, completion
    // tokens and provider overhead instead of spending the whole model window on history.
    private const val INPUT_BUDGET_FRACTION = 0.75
    private const val RESERVED_OUTPUT_TOKENS = 8_192
    private const val TOOL_SCHEMA_RESERVE_TOKENS = 4_096

    /**
     * Compaction threshold (in characters) per tool type. `read`/`base` commonly
     * produce legitimately long output, so they get a higher bar; file mutations
     * and listings are compressed aggressively.
     */
    fun compactThresholdFor(toolName: String?): Int = when (toolName?.trim()?.lowercase()) {
        "read" -> 800
        "base", "download" -> 400
        "write", "edit" -> 200
        "process" -> 300
        else -> 240
    }

    /**
     * Tool-aware historical output compaction. Preserves the structurally important
     * parts of each tool family instead of applying one blind head/tail truncation.
     */
    fun compactToolOutput(toolName: String?, args: JsonObject?, output: String, success: Boolean): String {
        val statusLabel = if (success) "成功" else "失败"
        val header = "【历史执行结果·状态:$statusLabel】"
        val name = toolName?.trim()?.lowercase().orEmpty()
        val body = when (name) {
            "read" -> compactRead(args, output)
            "write", "edit" -> output.take(160) + "…[文件操作结果已压缩]"
            "process" -> compactList(output, 4, 2, "列表")
            "base", "download" -> compactCommand(output)
            else -> compactGeneric(output)
        }
        return "$header\n$body"
    }

    private fun compactRead(args: JsonObject?, output: String): String {
        val path = runCatching { args?.get("path")?.jsonPrimitive?.contentOrNull }.getOrNull()
        val pathHint = path?.let { "（文件: $it）" }.orEmpty()
        val lines = output.lines()
        return if (lines.size > 12) {
            lines.take(6).joinToString("\n") +
                "\n... [历史 read 输出已压缩，省略 ${lines.size - 10} 行]$pathHint ...\n" +
                lines.takeLast(4).joinToString("\n")
        } else {
            output.take(600)
        }
    }

    private fun compactCommand(output: String): String {
        val lines = output.lines()
        return if (lines.size > 10) {
            lines.take(4).joinToString("\n") +
                "\n... [历史命令输出已压缩，省略 ${lines.size - 8} 行] ...\n" +
                lines.takeLast(4).joinToString("\n")
        } else {
            output.take(500)
        }
    }

    private fun compactList(output: String, head: Int, tail: Int, label: String): String {
        val lines = output.lines()
        return if (lines.size > head + tail + 2) {
            lines.take(head).joinToString("\n") +
                "\n... [$label 已压缩，省略 ${lines.size - head - tail} 项] ...\n" +
                lines.takeLast(tail).joinToString("\n")
        } else {
            output.take(300)
        }
    }

    private fun compactGeneric(output: String): String {
        val lines = output.lines()
        return if (lines.size > 6) {
            lines.take(3).joinToString("\n") +
                "\n... [历史工具输出已压缩，已略去 ${lines.size - 5} 行日志] ...\n" +
                lines.takeLast(2).joinToString("\n")
        } else {
            output.take(180) + "... [已自动压缩]"
        }
    }

    fun estimateTokens(text: String): Int = (text.length / 2.5).toInt()

    fun computeKeepFromIndex(messages: List<HarnessMessage>, budget: Int, systemTokens: Int): Int {
        if (budget <= 0) return 0
        val limit = (budget * INPUT_BUDGET_FRACTION).toInt() -
            systemTokens - RESERVED_OUTPUT_TOKENS - TOOL_SCHEMA_RESERVE_TOKENS
        if (limit <= 0) return 0
        var used = 0
        for (index in messages.indices.reversed()) {
            val tokens = when (val message = messages[index]) {
                is CapabilityEvent -> 0
                is UserMessage -> estimateTokens(message.text) + message.imageUrls.size * 1_000
                is AssistantText -> estimateTokens(message.text) + estimateTokens(message.reasoning.orEmpty())
                is ToolResult -> estimateTokens(message.output)
                is ToolCall -> estimateTokens(message.args.toString()) + estimateTokens(message.reasoning.orEmpty())
            }
            if (used + tokens > limit) return (index + 1).coerceIn(0, messages.size)
            used += tokens
        }
        return 0
    }

    fun foldMessageText(role: String, text: String): String =
        "[早期历史已折叠·$role] ${text.take(80).replace('\n', ' ')}…（内容过长，已省略，请依据最近轮次继续）"
}
