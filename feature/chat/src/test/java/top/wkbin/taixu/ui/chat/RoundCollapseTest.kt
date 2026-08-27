package top.wkbin.taixu.ui.chat

import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.HarnessTool
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.harness.UserMessage

class RoundCollapseTest {

    private fun makeUser(id: String, text: String = "query") =
        UserMessage(id = id, createdAt = 1000L, text = text)

    private fun makeAssistant(id: String, text: String) =
        AssistantText(id = id, createdAt = 1000L, text = text)

    private fun makeToolCall(id: String, tool: HarnessTool = HarnessTool.READ) =
        ToolCall(id = id, createdAt = 1000L, tool = tool, args = buildJsonObject {})

    private fun makeToolResult(toolCallId: String, durationMs: Long? = 1000L) =
        ToolResult(
            id = "res_$toolCallId",
            createdAt = 1000L,
            toolCallId = toolCallId,
            success = true,
            output = "ok",
            durationMs = durationMs,
        )

    @Test
    fun `empty messages returns empty list`() {
        val items = projectChatMessages(emptyList())
        assertTrue(items.isEmpty())
    }

    @Test
    fun `round with 2 or fewer steps is never collapsed and shows no button`() {
        val messages = listOf(
            makeUser("u1"),
            makeToolCall("t1"),
            makeToolResult("t1"),
            makeToolCall("t2"),
            makeToolResult("t2"),
            makeAssistant("a1", "Done"),
            makeUser("u2"), // creates a new round so u1 becomes historical
            makeAssistant("a2", "Hello"),
        )
        val items = projectChatMessages(messages)
        // Check u1 round: should have u1, t1, t2, a1 (no CollapseButtonItem)
        val buttons = items.filterIsInstance<ChatRenderItem.CollapseButtonItem>()
        assertTrue("≤ 2 步的历史轮不应该有折叠按钮", buttons.isEmpty())

        val messageIds = items.filterIsInstance<ChatRenderItem.MessageItem>().map { it.message.id }
        assertEquals(listOf("u1", "t1", "t2", "a1", "u2", "a2"), messageIds)
    }

    @Test
    fun `historical round with more than 2 steps collapses to latest 2 steps with button`() {
        val messages = listOf(
            makeUser("u1"),
            makeToolCall("t1"),
            makeToolResult("t1", 1200L),
            makeToolCall("t2"),
            makeToolResult("t2", 800L),
            makeToolCall("t3"),
            makeToolResult("t3", 500L),
            makeToolCall("t4"),
            makeToolResult("t4", 300L),
            makeAssistant("a1", "Final answer"),
            makeUser("u2"), // u1 becomes historical round with 4 steps
            makeAssistant("a2", "Next response"),
        )
        val toolResults = mapOf(
            "t1" to makeToolResult("t1", 1200L),
            "t2" to makeToolResult("t2", 800L),
            "t3" to makeToolResult("t3", 500L),
            "t4" to makeToolResult("t4", 300L),
        )

        val items = projectChatMessages(messages, toolResults)

        // Find button for round u1
        val buttons = items.filterIsInstance<ChatRenderItem.CollapseButtonItem>()
        assertEquals(1, buttons.size)
        val btn = buttons.first()
        assertEquals("u1", btn.roundKey)
        assertEquals(2, btn.hiddenSteps) // 4 - 2 = 2 hidden
        assertEquals(4, btn.totalSteps)
        assertEquals(2000L, btn.hiddenDurationMs) // 1200 + 800 = 2000
        assertFalse(btn.isExpanded)

        // Visible messages: u1, [btn], t3, t4, a1, u2, a2
        val messageIds = items.filterIsInstance<ChatRenderItem.MessageItem>().map { it.message.id }
        assertEquals(listOf("u1", "t3", "t4", "a1", "u2", "a2"), messageIds)
    }

    @Test
    fun `terminal round is always expanded without button`() {
        val messages = listOf(
            makeUser("u1"),
            makeToolCall("t1"),
            makeToolCall("t2"),
            makeToolCall("t3"),
            makeAssistant("a1", "Processing"),
        )
        val items = projectChatMessages(messages)
        val buttons = items.filterIsInstance<ChatRenderItem.CollapseButtonItem>()
        assertTrue("末轮无论多少步都应保持摊开无按钮", buttons.isEmpty())

        val messageIds = items.filterIsInstance<ChatRenderItem.MessageItem>().map { it.message.id }
        assertEquals(listOf("u1", "t1", "t2", "t3", "a1"), messageIds)
    }

    @Test
    fun `manual expand override shows all steps and collapse button`() {
        val messages = listOf(
            makeUser("u1"),
            makeToolCall("t1"),
            makeToolCall("t2"),
            makeToolCall("t3"),
            makeAssistant("a1", "Answer"),
            makeUser("u2"),
        )
        // Override u1 to expanded (true)
        val items = projectChatMessages(messages, expandedOverrides = mapOf("u1" to true))

        val buttons = items.filterIsInstance<ChatRenderItem.CollapseButtonItem>()
        assertEquals(1, buttons.size)
        val btn = buttons.first()
        assertTrue("手动展开后应显示收起按钮", btn.isExpanded)
        assertEquals(3, btn.totalSteps)

        val messageIds = items.filterIsInstance<ChatRenderItem.MessageItem>().map { it.message.id }
        assertEquals(listOf("u1", "t1", "t2", "t3", "a1", "u2"), messageIds)
    }

    @Test
    fun `manual collapse override on an expanded round collapses it`() {
        val messages = listOf(
            makeUser("u1"),
            makeToolCall("t1"),
            makeToolCall("t2"),
            makeToolCall("t3"),
            makeAssistant("a1", "Answer"),
            makeUser("u2"),
        )
        // Explicitly set u1 to collapsed (false)
        val items = projectChatMessages(messages, expandedOverrides = mapOf("u1" to false))

        val buttons = items.filterIsInstance<ChatRenderItem.CollapseButtonItem>()
        assertEquals(1, buttons.size)
        assertFalse(buttons.first().isExpanded)
        assertEquals(1, buttons.first().hiddenSteps)

        val messageIds = items.filterIsInstance<ChatRenderItem.MessageItem>().map { it.message.id }
        assertEquals(listOf("u1", "t2", "t3", "a1", "u2"), messageIds)
    }

    @Test
    fun `follow rule preserves first text, hides intermediate text of hidden tool, preserves final text`() {
        val messages = listOf(
            makeUser("u1"),
            makeAssistant("a_first", "Let me research this"), // First text before any tool
            makeToolCall("t1"),
            makeAssistant("a_mid1", "Found file 1"), // Mid text following hidden t1 -> should hide
            makeToolCall("t2"),
            makeAssistant("a_mid2", "Found file 2"), // Mid text following hidden t2 -> should hide
            makeToolCall("t3"),
            makeAssistant("a_mid3", "Checking file 3"), // Mid text following visible t3 -> should show
            makeToolCall("t4"),
            makeAssistant("a_final", "All done!"), // Final text in round -> always show
            makeUser("u2"),
        )

        val items = projectChatMessages(messages)
        val messageIds = items.filterIsInstance<ChatRenderItem.MessageItem>().map { it.message.id }

        // Expected: u1, a_first, t3, a_mid3, t4, a_final, u2
        assertEquals(
            listOf("u1", "a_first", "t3", "a_mid3", "t4", "a_final", "u2"),
            messageIds,
        )
    }

    @Test
    fun `initial round before first user message is properly grouped and handled`() {
        val messages = listOf(
            makeAssistant("a_init1", "Welcome!"),
            makeToolCall("t_init1"),
            makeToolCall("t_init2"),
            makeToolCall("t_init3"),
            makeAssistant("a_init2", "Initialized"),
            makeUser("u1"), // First user message turns initial round into historical
            makeAssistant("a1", "Hi there"),
        )
        val items = projectChatMessages(messages)

        val buttons = items.filterIsInstance<ChatRenderItem.CollapseButtonItem>()
        assertEquals(1, buttons.size)
        assertEquals("__initial_round__", buttons.first().roundKey)
        assertEquals(1, buttons.first().hiddenSteps)

        val messageIds = items.filterIsInstance<ChatRenderItem.MessageItem>().map { it.message.id }
        assertEquals(
            listOf("a_init1", "t_init2", "t_init3", "a_init2", "u1", "a1"),
            messageIds,
        )
    }
}
