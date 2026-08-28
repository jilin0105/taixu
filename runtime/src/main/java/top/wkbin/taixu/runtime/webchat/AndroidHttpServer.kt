package top.wkbin.taixu.runtime.webchat

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Small HTTP/1.1 server backed only by Android-supported java.net APIs.
 *
 * Android does not ship the desktop-JDK `com.sun.net.httpserver` module. This
 * adapter intentionally exposes only the subset WebChat needs: prefix routes,
 * fixed-length responses, and an open-ended response body for SSE.
 */
internal class AndroidHttpServer private constructor(
    private val address: InetSocketAddress,
    private val backlog: Int,
) {
    private val contexts = ConcurrentHashMap<String, AndroidHttpHandler>()
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var running = false

    var executor: Executor = Executors.newCachedThreadPool()

    fun createContext(path: String, handler: AndroidHttpHandler) {
        require(path.startsWith('/')) { "HTTP context path must start with /" }
        contexts[path] = handler
    }

    @Synchronized
    fun start() {
        if (running) return
        val socket = ServerSocket().apply {
            reuseAddress = true
            bind(address, backlog.coerceAtLeast(DEFAULT_BACKLOG))
        }
        serverSocket = socket
        running = true
        Thread({ acceptConnections(socket) }, "taixu-webchat-accept").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun stop(delaySeconds: Int) {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        if (delaySeconds > 0) {
            // The WebChat caller always requests an immediate stop. The argument
            // is kept to mirror the old API and make that intent explicit.
        }
    }

    private fun acceptConnections(socket: ServerSocket) {
        while (running) {
            try {
                val client = socket.accept()
                executor.execute { handleClient(client) }
            } catch (_: SocketException) {
                if (running) continue
                break
            } catch (_: Exception) {
                if (!running) break
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = READ_TIMEOUT_MS
            val input = BufferedInputStream(socket.getInputStream())
            val requestLine = readHttpLine(input) ?: run {
                socket.close()
                return
            }
            val requestParts = requestLine.split(' ', limit = 3)
            if (requestParts.size < 2) {
                writeSimpleError(socket, 400, "Bad Request")
                return
            }

            val headers = AndroidHttpHeaders()
            var headerBytes = requestLine.length
            while (true) {
                val line = readHttpLine(input) ?: break
                headerBytes += line.length
                if (headerBytes > MAX_HEADER_BYTES) {
                    writeSimpleError(socket, 431, "Request Header Fields Too Large")
                    return
                }
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0) {
                    headers.add(line.substring(0, separator).trim(), line.substring(separator + 1).trim())
                }
            }

            val contentLength = headers.getFirst("Content-Length")?.toIntOrNull() ?: 0
            if (contentLength !in 0..MAX_BODY_BYTES) {
                writeSimpleError(socket, 413, "Payload Too Large")
                return
            }
            val body = ByteArray(contentLength)
            var offset = 0
            while (offset < body.size) {
                val count = input.read(body, offset, body.size - offset)
                if (count < 0) break
                offset += count
            }

            val target = requestParts[1]
            val path = target.substringBefore('?').ifBlank { "/" }
            val query = target.substringAfter('?', "").ifBlank { null }
            val handler = contexts.entries
                .asSequence()
                .filter { path.startsWith(it.key) }
                .maxByOrNull { it.key.length }
                ?.value

            if (handler == null) {
                writeSimpleError(socket, 404, "Not Found")
                return
            }

            val exchange = AndroidHttpExchange(
                socket = socket,
                requestMethod = requestParts[0],
                requestURI = AndroidHttpUri(path, query),
                requestHeaders = headers,
                requestBody = ByteArrayInputStream(body, 0, offset),
            )
            handler.handle(exchange)
        } catch (_: Exception) {
            runCatching { socket.close() }
        }
    }

    private fun writeSimpleError(socket: Socket, code: Int, message: String) {
        runCatching {
            val body = message.toByteArray(Charsets.UTF_8)
            val output = BufferedOutputStream(socket.getOutputStream())
            output.write("HTTP/1.1 $code $message\r\n".toByteArray(Charsets.US_ASCII))
            output.write("Content-Type: text/plain; charset=utf-8\r\n".toByteArray(Charsets.US_ASCII))
            output.write("Content-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
            output.write(body)
            output.flush()
        }
        runCatching { socket.close() }
    }

    private fun readHttpLine(input: InputStream): String? {
        val bytes = ArrayList<Byte>(128)
        while (bytes.size <= MAX_LINE_BYTES) {
            val value = input.read()
            if (value < 0) return if (bytes.isEmpty()) null else bytes.toByteArray().toString(Charsets.US_ASCII)
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes.add(value.toByte())
        }
        if (bytes.size > MAX_LINE_BYTES) throw IllegalArgumentException("HTTP line too long")
        return bytes.toByteArray().toString(Charsets.US_ASCII)
    }

    companion object {
        private const val DEFAULT_BACKLOG = 16
        private const val READ_TIMEOUT_MS = 15_000
        private const val MAX_LINE_BYTES = 8 * 1024
        private const val MAX_HEADER_BYTES = 32 * 1024
        private const val MAX_BODY_BYTES = 2 * 1024 * 1024

        fun create(address: InetSocketAddress, backlog: Int): AndroidHttpServer =
            AndroidHttpServer(address, backlog)
    }
}

internal fun interface AndroidHttpHandler {
    fun handle(exchange: AndroidHttpExchange)
}

internal data class AndroidHttpUri(
    val path: String,
    val query: String?,
)

internal class AndroidHttpHeaders {
    private val values = LinkedHashMap<String, MutableList<String>>()

    @Synchronized
    fun add(name: String, value: String) {
        val existingName = values.keys.firstOrNull { it.equals(name, ignoreCase = true) } ?: name
        values.getOrPut(existingName) { mutableListOf() }.add(value)
    }

    @Synchronized
    fun getFirst(name: String): String? =
        values.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

    @Synchronized
    internal fun entries(): List<Pair<String, String>> =
        values.flatMap { (name, entries) -> entries.map { name to it } }
}

internal class AndroidHttpExchange(
    private val socket: Socket,
    val requestMethod: String,
    val requestURI: AndroidHttpUri,
    val requestHeaders: AndroidHttpHeaders,
    val requestBody: InputStream,
) {
    val responseHeaders = AndroidHttpHeaders()
    private val output = BufferedOutputStream(socket.getOutputStream())
    val responseBody: OutputStream = output

    @Volatile
    private var responseStarted = false

    @Synchronized
    fun sendResponseHeaders(code: Int, responseLength: Long) {
        check(!responseStarted) { "Response headers already sent" }
        responseStarted = true
        output.write("HTTP/1.1 $code ${statusText(code)}\r\n".toByteArray(Charsets.US_ASCII))
        responseHeaders.entries().forEach { (name, value) ->
            output.write("$name: $value\r\n".toByteArray(Charsets.US_ASCII))
        }
        if (responseLength > 0 && responseHeaders.getFirst("Content-Length") == null) {
            output.write("Content-Length: $responseLength\r\n".toByteArray(Charsets.US_ASCII))
        }
        if (responseLength > 0 && responseHeaders.getFirst("Connection") == null) {
            output.write("Connection: close\r\n".toByteArray(Charsets.US_ASCII))
        }
        output.write("\r\n".toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    fun close() {
        runCatching { output.close() }
        runCatching { socket.close() }
    }

    private fun statusText(code: Int): String = when (code) {
        200 -> "OK"
        204 -> "No Content"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        413 -> "Payload Too Large"
        431 -> "Request Header Fields Too Large"
        500 -> "Internal Server Error"
        else -> "HTTP Response"
    }
}
