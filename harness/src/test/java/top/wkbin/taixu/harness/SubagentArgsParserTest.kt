package top.wkbin.taixu.harness

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentArgsParserTest {
    @Test
    fun `parses an array and skips malformed elements`() {
        val args = jsonObject(
            """{
                "subagents": [
                    {"taskName":"实现","role":"coder","prompt":"写代码"},
                    "partial",
                    {"role":"tester","prompt":"跑测试"},
                    {"taskName":"缺少提示"}
                ]
            }""",
        )

        val result = SubagentArgsParser.parse(args, defaultTaskName = "子任务")

        assertEquals(2, result.size)
        assertEquals("实现", result[0].taskName)
        assertEquals("coder", result[0].role)
        assertEquals("子任务", result[1].taskName)
        assertEquals("tester", result[1].role)
    }

    @Test
    fun `accepts a single object in subagents`() {
        val args = jsonObject(
            """{"subagents":{"taskName":"调查","role":"researcher","prompt":"定位根因"}}""",
        )

        val result = SubagentArgsParser.parse(args)

        assertEquals(1, result.size)
        assertEquals("调查", result.single().taskName)
        assertEquals("researcher", result.single().role)
    }

    @Test
    fun `accepts legacy top-level arguments when nested value is incomplete`() {
        val args = jsonObject(
            """{"subagents":"partial","taskName":"兼容","role":"coder","prompt":"继续执行"}""",
        )

        val result = SubagentArgsParser.parse(args)

        assertEquals(1, result.size)
        assertEquals("继续执行", result.single().prompt)
    }

    @Test
    fun `returns empty for an interrupted malformed call`() {
        val args = jsonObject("""{"subagents":"partial"}""")

        assertTrue(SubagentArgsParser.parse(args).isEmpty())
    }

    private fun jsonObject(raw: String) = Json.parseToJsonElement(raw).jsonObject
}
