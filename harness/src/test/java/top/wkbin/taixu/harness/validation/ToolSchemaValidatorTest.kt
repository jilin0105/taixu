package top.wkbin.taixu.harness.validation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.core.model.McpToolInfo

class ToolSchemaValidatorTest {

    private fun args(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
        pairs.forEach { (key, value) ->
            when (value) {
                null -> put(key, kotlinx.serialization.json.JsonNull)
                is String -> put(key, value)
                is Int -> put(key, value)
                is Boolean -> put(key, value)
            }
        }
    }

    private fun schema(jsonText: String): JsonObject =
        Json.parseToJsonElement(jsonText) as JsonObject

    @Test
    fun `reports missing required fields`() {
        val problems = ToolSchemaValidator.problemsFor("write", args())
        assertTrue(problems.any { it.contains("path") && it.contains("缺少必填") })
        assertTrue(problems.any { it.contains("content") && it.contains("缺少必填") })
    }

    @Test
    fun `reports enum violation for process action`() {
        val problems = ToolSchemaValidator.problemsFor("process", args("action" to "restart"))
        assertTrue(problems.single().contains("不在允许范围内"))
        assertTrue(problems.single().contains("start/status/logs/list/stop"))
    }

    @Test
    fun `reports integer range violation`() {
        val problems = ToolSchemaValidator.problemsFor("history.search", args("query" to "关键词", "limit" to 99))
        assertTrue(problems.single().contains("超出允许范围"))
    }

    @Test
    fun `reports type mismatch with actual type name`() {
        val problems = ToolSchemaValidator.problemsFor("read", args("path" to 42))
        assertTrue(problems.single().contains("类型错误"))
        assertTrue(problems.single().contains("数字"))
    }

    @Test
    fun `reports pattern violation for process id`() {
        val problems = ToolSchemaValidator.problemsFor("process", args("action" to "start", "id" to "Bad Id!", "command" to "sleep 100"))
        assertTrue(problems.any { it.contains("格式不符合要求") && it.contains("id") })
    }

    @Test
    fun `enforces anyOf field combinations for history read`() {
        val problems = ToolSchemaValidator.problemsFor("history.read", args())
        assertTrue(problems.any { it.contains("message_id") && it.contains("index") && it.contains("至少提供其中一组") })
        // 提供任一组即通过
        assertTrue(ToolSchemaValidator.problemsFor("history.read", args("message_id" to "m-1")).isEmpty())
        assertTrue(ToolSchemaValidator.problemsFor("history.read", args("index" to 3)).isEmpty())
    }

    @Test
    fun `validates array items against item schema`() {
        val schema = schema(
            """{"type":"object","properties":{
                "steps":{"type":"array","items":{"type":"object",
                    "properties":{"id":{"type":"string"},"status":{"type":"string","enum":["pending","done"]}},
                    "required":["id","status"]}}},
                "required":["steps"]}""",
        )
        val args = buildJsonObject {
            put(
                "steps",
                kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject { put("id", "s1"); put("status", "pending") })
                    add(buildJsonObject { put("id", "s2"); put("status", "banana") })
                    add(buildJsonObject { put("id", "s3") })
                },
            )
        }
        val problems = ToolSchemaValidator.validate(schema, args)
        assertTrue(problems.any { it.contains("steps[1].status") && it.contains("不在允许范围内") })
        assertTrue(problems.any { it.contains("steps[2].status") && it.contains("缺少必填") })
    }

    @Test
    fun `accepts valid builtin tool arguments`() {
        assertTrue(ToolSchemaValidator.problemsFor("read", args("path" to "/workspace/a.kt", "offset" to 1, "limit" to 50)).isEmpty())
        assertTrue(ToolSchemaValidator.problemsFor("base", args("command" to "ls -la", "cwd" to "/root", "timeout_seconds" to 30)).isEmpty())
        assertTrue(
            ToolSchemaValidator.problemsFor(
                "download",
                args("url" to "https://example.com/a.zip", "destination" to "dist/a.zip", "max_attempts" to 3),
            ).isEmpty(),
        )
    }

    @Test
    fun `validates mcp tools against dynamic schema`() {
        val mcpTool = McpToolInfo(
            serverId = "srv",
            serverName = "测试服务",
            name = "search",
            description = "",
            parametersJson = """{"type":"object","properties":{"q":{"type":"string"}},"required":["q"]}""",
        )
        val problems = ToolSchemaValidator.problemsFor("mcp__srv__search", args(), listOf(mcpTool))
        assertTrue(problems.single().contains("缺少必填参数 q"))
        assertTrue(ToolSchemaValidator.problemsFor("mcp__srv__search", args("q" to "hello"), listOf(mcpTool)).isEmpty())
    }

    @Test
    fun `skips validation when schema unknown or empty`() {
        // 未知工具名：宽容放行
        assertTrue(ToolSchemaValidator.problemsFor("no_such_tool", args()).isEmpty())
        // MCP 工具不在动态列表中：放行
        assertTrue(ToolSchemaValidator.problemsFor("mcp__srv__search", args()).isEmpty())
        // 空 schema：无约束
        val emptySchemaTool = McpToolInfo("srv2", "服务", "tool", "", parametersJson = "")
        assertTrue(ToolSchemaValidator.problemsFor("mcp__srv2__tool", args("anything" to 1), listOf(emptySchemaTool)).isEmpty())
    }

    @Test
    fun `extra unknown arguments are tolerated`() {
        // schema 未声明的额外参数不报错（宽容策略）
        val problems = ToolSchemaValidator.problemsFor("read", args("path" to "a.kt", "extra" to "whatever"))
        assertTrue(problems.isEmpty())
    }
}
