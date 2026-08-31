package top.wkbin.taixu.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.put

class ContextWindowPolicyTest {
    @Test
    fun keepsRecentMessagesWithinBudget() {
        val messages = listOf(
            UserMessage("1", 1, "a".repeat(2_000)),
            AssistantText("2", 2, "b".repeat(2_000)),
            UserMessage("3", 3, "recent"),
        )

        // Budget must leave room after the input-fraction + output/schema reserves.
        val keepFrom = ContextWindowPolicy.computeKeepFromIndex(messages, budget = 18_000, systemTokens = 10)

        assertEquals(1, keepFrom)
    }

    @Test
    fun `exhausted budget retains only a minimal recent turn instead of all history`() {
        val messages = listOf(
            UserMessage("old-user", 1, "old request"),
            AssistantText("old-assistant", 2, "old answer"),
            UserMessage("latest-user", 3, "latest request"),
        )

        val keepFrom = ContextWindowPolicy.computeKeepFromIndex(messages, budget = 4_000, systemTokens = 4_000)

        assertEquals(2, keepFrom)
    }

    @Test
    fun `exhausted budget does not orphan the only tool result`() {
        val call = ToolCall("call", 1, HarnessTool.BASE, kotlinx.serialization.json.buildJsonObject {})
        val messages = listOf(call, ToolResult("result", 2, "call", true, "ok"))

        val keepFrom = ContextWindowPolicy.computeKeepFromIndex(messages, budget = 0, systemTokens = 0)

        assertEquals(0, keepFrom)
    }

    @Test
    fun `oversized system prompt is bounded`() {
        val fitted = ContextWindowPolicy.fitSystemPrompt("x".repeat(100_000), budget = 8_000)

        assertTrue(fitted.length < 100_000)
        assertTrue(fitted.contains("系统提示因上下文预算受限已截断"))
    }

    @Test
    fun compactsLongToolOutputWithHeadAndTail() {
        val compacted = ContextWindowPolicy.compactToolOutput(
            toolName = null,
            args = null,
            output = (1..10).joinToString("\n") { "line-$it" },
            success = true,
        )

        assertTrue(compacted.contains("line-1"))
        assertTrue(compacted.contains("line-10"))
        assertTrue(compacted.contains("已略去 5 行"))
    }

    @Test
    fun effectiveUsageReplacesCollapsedHistoryWithOneBoundedSummary() {
        val messages = buildList<HarnessMessage> {
            repeat(100) { index ->
                add(UserMessage("u-$index", index * 2L, "需求-$index " + "a".repeat(500)))
                add(AssistantText("a-$index", index * 2L + 1, "回复-$index " + "b".repeat(500)))
            }
            add(UserMessage("latest", 1_000, "最近问题"))
        }

        val usage = ContextWindowPolicy.estimateEffectiveUsage(
            messages = messages,
            budget = 18_000,
            systemTokens = 100,
            compactionEnabled = true,
        )

        assertTrue(usage.keepFromIndex > 0)
        assertTrue(usage.totalTokens < 18_000)
        assertTrue(usage.conversationTokens < messages.sumOf {
            when (it) {
                is UserMessage -> ContextWindowPolicy.estimateTokens(it.text)
                is AssistantText -> ContextWindowPolicy.estimateTokens(it.text)
                else -> 0
            }
        })
    }

    @Test
    fun keepBoundaryIncludesToolCallWhenItWouldOtherwiseStartAtResult() {
        val call = ToolCall(
            "call",
            2,
            HarnessTool.BASE,
            kotlinx.serialization.json.buildJsonObject {},
            reasoning = "x".repeat(4_000),
        )
        val messages = listOf(
            UserMessage("old", 1, "x".repeat(1_000)),
            call,
            ToolResult("result", 3, "call", true, "ok"),
            UserMessage("latest", 4, "now"),
        )

        val keepFrom = ContextWindowPolicy.computeKeepFromIndex(messages, budget = 18_000, systemTokens = 10)

        assertEquals(1, keepFrom)
        assertTrue(messages[keepFrom] is ToolCall)
    }

    @Test
    fun historySummaryKeepsStructuredFactsAndToolSpecificFailureContext() {
        val call = ToolCall(
            "call",
            2,
            HarnessTool.BASE,
            kotlinx.serialization.json.buildJsonObject { put("command", "./gradlew test") },
        )
        val summary = ContextWindowPolicy.buildHistorySummary(
            listOf(
                UserMessage("u", 1, "必须保持 core 模块纯 Kotlin，不能引入 Android 依赖"),
                AssistantText("a", 2, "决定采用滑动窗口方案，修改 /workspace/app/src/Main.kt"),
                call,
                ToolResult("r", 4, "call", false, "FAILURE: tests failed with a stack trace"),
            ),
        )

        assertTrue(summary.contains("用户硬约束"))
        assertTrue(summary.contains("关键决定"))
        assertTrue(summary.contains("涉及文件"))
        assertTrue(summary.contains("失败根因线索"))
        assertTrue(summary.contains("gradlew"))
    }
}
