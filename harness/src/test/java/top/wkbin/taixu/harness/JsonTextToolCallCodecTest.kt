package top.wkbin.taixu.harness

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTextToolCallCodecTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `extract parses single valid marker`() {
        val text = """前言 [[tool_call]]{"name":"read","arguments":{"path":"/a.txt"}}[[/tool_call]] 后记"""
        val calls = JsonTextToolCallCodec.extract(json, text)
        assertEquals(1, calls.size)
        assertEquals("read", calls[0].name)
        assertEquals("""{"path":"/a.txt"}""", calls[0].argumentsJson)
        assertTrue(calls[0].id.startsWith("json-"))
    }

    @Test
    fun `extract keeps order and skips invalid payloads`() {
        val text = """
            [[tool_call]]{"name":"edit","arguments":{}}[[/tool_call]]
            这段是普通文本 not a marker
            [[tool_call]]{"broken"[[/tool_call]]
            [[tool_call]]{"name":"","arguments":{}}[[/tool_call]]
            [[tool_call]]{"name":"base","arguments":{"command":"ls"}}[[/tool_call]]
        """.trimIndent()
        val calls = JsonTextToolCallCodec.extract(json, text)
        assertEquals(listOf("edit", "base"), calls.map { it.name })
    }

    @Test
    fun `missing arguments json defaults to empty object`() {
        val calls = JsonTextToolCallCodec.extract(json, """[[tool_call]]{"name":"write"}[[/tool_call]]""")
        assertEquals(1, calls.size)
        assertEquals("{}", calls[0].argumentsJson)
    }

    @Test
    fun `blank text yields no calls`() {
        assertTrue(JsonTextToolCallCodec.extract(json, "").isEmpty())
        assertTrue(JsonTextToolCallCodec.extract(json, "   ").isEmpty())
    }

    @Test
    fun `stripMarkers removes protocol markers only`() {
        val stripped = JsonTextToolCallCodec.stripMarkers(
            """
            你好[[tool_call]]{"name":"read","arguments":{}}[[/tool_call]]
            第二段内容
            """.trimIndent(),
        )
        assertEquals("你好\n第二段内容", stripped)
    }
}
