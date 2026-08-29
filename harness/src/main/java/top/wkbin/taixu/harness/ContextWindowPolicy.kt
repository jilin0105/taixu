package top.wkbin.taixu.harness

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Pure context-budget and historical-folding policy used by the provider mapper and UI. */
object ContextWindowPolicy {
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

    /** Conservative multilingual estimate used when a provider tokenizer is unavailable. */
    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        var cjk = 0
        var ascii = 0
        var punctuation = 0
        text.forEach { ch ->
            when {
                ch.code in 0x2E80..0x9FFF || ch.code in 0xAC00..0xD7AF -> cjk++
                ch.isWhitespace() -> Unit
                ch.isLetterOrDigit() -> ascii++
                else -> punctuation++
            }
        }
        return (cjk / 1.8f + ascii / 2.5f + punctuation / 2.8f).toInt().coerceAtLeast(1)
    }

    /**
     * Estimate the payload after the same historical folding used by [HarnessLoop].
     * The transcript remains complete for the user, while this value represents the
     * next request's effective context and therefore must not sum the raw transcript.
     */
    fun estimateEffectiveUsage(
        messages: List<HarnessMessage>,
        budget: Int,
        systemTokens: Int,
        compactionEnabled: Boolean,
    ): EffectiveContextUsage {
        val keepFrom = if (compactionEnabled) {
            computeKeepFromIndex(messages, budget, systemTokens)
        } else {
            0
        }
        var conversationTokens = if (keepFrom > 0) {
            minOf(600, keepFrom * 30)
        } else {
            0
        }
        var toolTokens = 0
        messages.drop(keepFrom).forEach { message ->
            when (message) {
                is CapabilityEvent -> Unit
                is UserMessage -> {
                    conversationTokens += estimateTokens(message.text) + message.imageUrls.size * 1_000
                }
                is AssistantText -> {
                    conversationTokens += estimateTokens(message.text) + estimateTokens(message.reasoning.orEmpty())
                }
                is ToolCall -> {
                    toolTokens += estimateTokens(message.args.toString()) + estimateTokens(message.reasoning.orEmpty())
                }
                is ToolResult -> {
                    toolTokens += estimateTokens(message.output)
                }
            }
        }
        return EffectiveContextUsage(
            keepFromIndex = keepFrom,
            conversationTokens = conversationTokens,
            toolTokens = toolTokens,
            totalTokens = systemTokens + conversationTokens + toolTokens,
        )
    }

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
            if (used + tokens > limit) {
                return alignKeepFromIndex(messages, (index + 1).coerceIn(0, messages.size))
            }
            used += tokens
        }
        return 0
    }

    private fun alignKeepFromIndex(messages: List<HarnessMessage>, candidate: Int): Int {
        if (candidate !in messages.indices || messages[candidate] !is ToolResult) return candidate
        val result = messages[candidate] as ToolResult
        val callIndex = messages.indexOfLast { it is ToolCall && it.id == result.toolCallId }
        return if (callIndex in 0 until candidate) callIndex else candidate
    }

    fun foldMessageText(role: String, text: String): String =
        "[早期历史已折叠·$role] ${text.take(80).replace('\n', ' ')}…（内容过长，已省略，请依据最近轮次继续）"

    fun buildHistorySummary(
        messages: List<HarnessMessage>,
        toolCallDetails: Map<String, Pair<String, JsonObject>> = messages.filterIsInstance<ToolCall>().associate {
            it.id to ((it.rawToolName ?: HarnessApiMapper.apiName(it.tool)) to it.args)
        },
    ): String {
        if (messages.isEmpty()) return ""
        val firstRequest = messages.filterIsInstance<UserMessage>().firstOrNull()?.text
            ?.replace('\n', ' ')?.take(240)
        val recentRequests = messages.filterIsInstance<UserMessage>().takeLast(3)
            .map { it.text.replace('\n', ' ').take(160) }
        val toolStates = messages.filterIsInstance<ToolResult>().takeLast(8).map { result ->
            val name = toolCallDetails[result.toolCallId]?.first ?: "tool"
            val args = toolCallDetails[result.toolCallId]?.second
            val output = if (result.output.length > compactThresholdFor(name)) {
                compactToolOutput(name, args, result.output, result.success)
            } else {
                result.output
            }
            val command = args?.get("command")?.jsonPrimitive?.contentOrNull
                ?: args?.get("path")?.jsonPrimitive?.contentOrNull
            "$name:${if (result.success) "成功" else "失败"} " +
                command.orEmpty().take(180) + " " + output.replace('\n', ' ').take(360)
        }
        val lastAssistant = messages.filterIsInstance<AssistantText>().lastOrNull()?.text
            ?.replace('\n', ' ')?.take(240)
        val textMessages = messages.mapNotNull {
            when (it) {
                is UserMessage -> it.text
                is AssistantText -> it.text
                else -> null
            }
        }
        val constraints = textMessages.asSequence()
            .flatMap { it.lineSequence() }
            .filter { line -> CONSTRAINT_MARKERS.any { marker -> line.contains(marker, ignoreCase = true) } }
            .map { it.trim().replace(WHITESPACE_REGEX, " ").take(220) }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
            .takeLast(8)
        val decisions = textMessages.asSequence()
            .flatMap { it.lineSequence() }
            .filter { line -> DECISION_MARKERS.any { marker -> line.contains(marker, ignoreCase = true) } }
            .map { it.trim().replace(WHITESPACE_REGEX, " ").take(220) }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
            .takeLast(8)
        val files = textMessages.asSequence()
            .flatMap { text -> FILE_PATH_REGEX.findAll(text.take(4000)).map { it.value.take(180) } }
            .distinct()
            .toList()
            .takeLast(12)
        val failures = messages.filterIsInstance<ToolResult>().filter { !it.success }
            .takeLast(6)
            .map { it.output.replace('\n', ' ').take(220) }
        val unresolved = recentRequests.takeLast(2)
        return buildString {
            appendLine("[早期历史摘要，共折叠 ${messages.size} 条消息]")
            firstRequest?.takeIf { it.isNotBlank() }?.let { appendLine("初始目标：$it") }
            if (constraints.isNotEmpty()) appendLine("用户硬约束：${constraints.joinToString(" | ")}")
            if (decisions.isNotEmpty()) appendLine("关键决定：${decisions.joinToString(" | ")}")
            if (files.isNotEmpty()) appendLine("涉及文件：${files.joinToString(" | ")}")
            if (recentRequests.isNotEmpty()) appendLine("近期用户要求：${recentRequests.joinToString(" | ")}")
            if (toolStates.isNotEmpty()) appendLine("关键工具状态：${toolStates.joinToString(" | ")}")
            if (failures.isNotEmpty()) appendLine("失败根因线索：${failures.joinToString(" | ")}")
            if (unresolved.isNotEmpty()) appendLine("未解决事项：${unresolved.joinToString(" | ")}")
            lastAssistant?.takeIf { it.isNotBlank() }?.let { append("最近阶段结论：$it") }
        }.take(2_400)
    }

    private val CONSTRAINT_MARKERS = listOf("必须", "不得", "禁止", "不能", "严禁", "要求", "must", "never", "should not")
    private val DECISION_MARKERS = listOf("决定", "采用", "改为", "选择", "方案", "decision", "use", "采用")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val FILE_PATH_REGEX = Regex("(?:/workspace|/sdcard|[A-Za-z]:[\\\\/])[^\\s,，。；;，)\\]]+")
}

data class EffectiveContextUsage(
    val keepFromIndex: Int,
    val conversationTokens: Int,
    val toolTokens: Int,
    val totalTokens: Int,
)
