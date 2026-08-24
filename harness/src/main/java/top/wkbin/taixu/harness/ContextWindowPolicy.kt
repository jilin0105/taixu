package top.wkbin.taixu.harness

/** Pure context-budget and historical-folding policy used by the provider mapper. */
internal object ContextWindowPolicy {
    fun compactToolOutput(output: String, success: Boolean): String {
        val lines = output.lines()
        val summary = if (lines.size > 6) {
            lines.take(3).joinToString("\n") +
                "\n... [历史工具输出已压缩，已略去 ${lines.size - 5} 行日志] ...\n" +
                lines.takeLast(2).joinToString("\n")
        } else {
            output.take(180) + "... [已自动压缩]"
        }
        return "【历史执行结果·状态:${if (success) "成功" else "失败"}】\n$summary"
    }

    fun estimateTokens(text: String): Int = (text.length / 2.5).toInt()

    fun computeKeepFromIndex(messages: List<HarnessMessage>, budget: Int, systemTokens: Int): Int {
        if (budget <= 0) return 0
        val limit = (budget * 0.85).toInt() - systemTokens
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
