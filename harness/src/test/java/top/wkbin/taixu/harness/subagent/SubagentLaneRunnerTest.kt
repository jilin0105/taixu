package top.wkbin.taixu.harness.subagent

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.HarnessTool
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.harness.UserMessage

class SubagentLaneRunnerTest {

    @Test
    fun `isolated messages omit parent history and dangling parent tool call`() {
        val messages = listOf(
            UserMessage("parent-user", 1L, "父任务"),
            AssistantText("parent-assistant", 2L, "父回复"),
            ToolCall("delegate", 3L, HarnessTool.SUBAGENT, JsonObject(emptyMap())),
            UserMessage("child-user", 4L, "子任务"),
            ToolResult("child-result", 5L, "read-call", true, "读取完成"),
        )

        val result = isolatedProviderMessages(messages, "子智能体系统提示", forceFinalAnswer = false)

        assertEquals(listOf("system", "user", "tool"), result.map { it.role })
        assertEquals("子任务", result[1].content)
        assertFalse(result.any { it.content == "父任务" || it.content == "父回复" })
        assertFalse(result.any { it.tool_calls?.any { call -> call.id == "delegate" } == true })
    }

    @Test
    fun `final round system prompt forces a direct conclusion`() {
        val result = isolatedProviderMessages(
            messages = listOf(UserMessage("child-user", 1L, "请分析")),
            systemPrompt = "子智能体系统提示",
            forceFinalAnswer = true,
        )

        assertTrue(result.first().content.orEmpty().contains("禁止继续调用工具"))
        assertTrue(result.first().content.orEmpty().contains("直接输出结论"))
    }
}
