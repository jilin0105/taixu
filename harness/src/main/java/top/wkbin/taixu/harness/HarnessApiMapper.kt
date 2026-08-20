package top.wkbin.taixu.harness

/**
 * HarnessMessage ↔ OpenAI 兼容 API 消息的纯转换逻辑（无依赖，便于测试）。
 */
internal object HarnessApiMapper {
    fun toApiMessage(message: HarnessMessage): ApiMessage = when (message) {
        is UserMessage -> ApiMessage(role = "user", content = message.text)
        is AssistantText -> ApiMessage(
            role = "assistant",
            content = message.text,
            reasoning_content = message.reasoning,
        )
        is ToolCall -> ApiMessage(
            role = "assistant",
            content = null,
            reasoning_content = message.reasoning,
            tool_calls = listOf(
                ApiToolCall(
                    id = message.id,
                    function = ApiFunctionCall(
                        name = apiName(message.tool),
                        arguments = message.args.toString(),
                    ),
                ),
            ),
        )
        is ToolResult -> ApiMessage(
            role = "tool",
            content = message.output,
            tool_call_id = message.toolCallId,
        )
    }

    /** LLM 返回的函数名 → HarnessTool。未知工具统一归入 base 由执行层报错。 */
    fun toolByName(name: String): HarnessTool = when (name.trim().lowercase()) {
        "read" -> HarnessTool.READ
        "write" -> HarnessTool.WRITE
        "edit" -> HarnessTool.EDIT
        else -> HarnessTool.BASE
    }

    fun apiName(tool: HarnessTool): String = when (tool) {
        HarnessTool.READ -> "read"
        HarnessTool.WRITE -> "write"
        HarnessTool.EDIT -> "edit"
        HarnessTool.BASE -> "base"
    }
}
