package top.wkbin.taixu.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWindowPolicyTest {
    @Test
    fun keepsRecentMessagesWithinBudget() {
        val messages = listOf(
            UserMessage("1", 1, "a".repeat(2_000)),
            AssistantText("2", 2, "b".repeat(2_000)),
            UserMessage("3", 3, "recent"),
        )

        val keepFrom = ContextWindowPolicy.computeKeepFromIndex(messages, budget = 1_000, systemTokens = 10)

        assertEquals(1, keepFrom)
    }

    @Test
    fun compactsLongToolOutputWithHeadAndTail() {
        val compacted = ContextWindowPolicy.compactToolOutput((1..10).joinToString("\n") { "line-$it" }, success = true)

        assertTrue(compacted.contains("line-1"))
        assertTrue(compacted.contains("line-10"))
        assertTrue(compacted.contains("已略去 5 行"))
    }
}
