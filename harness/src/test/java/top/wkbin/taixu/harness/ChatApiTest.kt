package top.wkbin.taixu.harness

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ChatApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = ChatApi(OkHttpClient(), Json { ignoreUnknownKeys = true })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun model(): ModelConfig = ModelConfig(
        name = "测试",
        provider = "OpenAI",
        model = "gpt-4o-mini",
        baseUrl = server.url("/v1").toString().removeSuffix("/"),
        apiKey = "sk-test-key",
    )

    @Test
    fun `parses plain text response`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"role":"assistant","content":"你好，我已经看完了"}}]}""",
            ),
        )
        val result = api.chat(model(), listOf(ApiMessage(role = "user", content = "hi")))
        assertEquals("你好，我已经看完了", result.content)
        assertFalse(result.hasToolCalls)
    }

    @Test
    fun `parses tool calls response`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
                    {"id":"call_1","type":"function","function":{"name":"base","arguments":"{\"command\":\"uname -m\"}"}}
                ]}}]}""",
            ),
        )
        val result = api.chat(model(), emptyList())
        assertTrue(result.hasToolCalls)
        val call = result.toolCalls.single()
        assertEquals("call_1", call.id)
        assertEquals("base", call.name)
        assertEquals("""{"command":"uname -m"}""", call.argumentsJson)
    }

    @Test
    fun `request sends bearer auth and tools schema`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"choices":[]}"""),
        )
        api.chat(model(), emptyList())
        val recorded = server.takeRequest()
        assertEquals("Bearer sk-test-key", recorded.getHeader("Authorization"))
        assertTrue(recorded.path == "/v1/chat/completions")
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"model\":\"gpt-4o-mini\""))
        assertTrue(body.contains("\"tools\""))
        assertTrue(body.contains("\"name\":\"read\""))
    }

    @Test
    fun `http error throws with code`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid key"}"""))
        val thrown = runCatching { runBlocking { api.chat(model(), emptyList()) } }.exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
        assertTrue(thrown!!.message!!.contains("401"))
    }

    @Test
    fun `streams content deltas and accumulates tool calls`() = runBlocking {
        val body = """
            data: {"choices":[{"delta":{"content":"你"}}]}
            data: {"choices":[{"delta":{"content":"好。"}}]}
            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"base","arguments":"{\"command\":\""}}]}}]}
            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"abc\"}"}}]}}]}
            data: [DONE]
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body))
        val deltas = mutableListOf<String>()
        val result = api.chatStream(model(), emptyList()) { deltas += it }
        assertEquals(listOf("你", "好。"), deltas)
        assertTrue(result.hasToolCalls)
        val call = result.toolCalls.single()
        assertEquals("base", call.name)
        assertEquals("""{"command":"abc"}""", call.argumentsJson)
    }
}
