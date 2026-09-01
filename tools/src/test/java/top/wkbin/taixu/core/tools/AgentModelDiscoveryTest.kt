package top.wkbin.taixu.core.tools

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AgentModelDiscoveryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `html success response becomes controlled discovery error`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html")
                .setBody("<!doctype html><html><body>login</body></html>"),
        )

        val error = assertThrows(ModelDiscoveryResponseException::class.java) {
            runBlocking { discovery().discover(provider(), server.url("/v1").toString(), null) }
        }

        assertTrue(error.message?.contains("网页而不是 JSON") == true)
    }

    @Test
    fun `valid model response is still parsed`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[{\"id\":\"chat-model\"},{\"id\":\"text-embedding-3\"}]}"),
        )

        assertEquals(listOf("chat-model"), discovery().discover(provider(), server.url("/v1").toString(), null))
    }

    private fun discovery() = AgentModelDiscovery(OkHttpClient())

    private fun provider() = AgentProviderDefinition(
        id = "custom",
        name = "Custom",
        baseUrl = "",
        group = ProviderGroup.CUSTOM,
        apiKeyOptional = true,
    )
}
