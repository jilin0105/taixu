package top.wkbin.taixu.runtime.webchat

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidHttpServerTest {

    @Test
    fun `serves a fixed length response over a socket`() {
        val port = ServerSocket(0).use { it.localPort }
        val server = AndroidHttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        server.createContext("/api/test") { exchange ->
            val body = "{\"ok\":true}".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.write(body)
            exchange.close()
        }

        try {
            server.start()
            val response = Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 5_000
                socket.getOutputStream().apply {
                    write("GET /api/test HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".toByteArray())
                    flush()
                }
                socket.getInputStream().bufferedReader().readText()
            }

            assertTrue(response.startsWith("HTTP/1.1 200 OK"))
            assertTrue(response.contains("Content-Length: 11"))
            assertTrue(response.endsWith("{\"ok\":true}"))
        } finally {
            server.stop(0)
        }
    }
}
