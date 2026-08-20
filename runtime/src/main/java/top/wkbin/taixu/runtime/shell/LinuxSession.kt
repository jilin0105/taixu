package top.wkbin.taixu.runtime.shell

import kotlinx.coroutines.flow.Flow

data class TerminalOutput(
    val stream: TerminalStream,
    val text: String,
)

enum class TerminalStream {
    STDOUT,
    STDERR,
    SYSTEM,
}

data class SessionConfig(
    val columns: Int = 80,
    val rows: Int = 24,
    val workingDirectory: String = "/root",
    val environment: Map<String, String> = emptyMap(),
    val commandLine: String = "/bin/bash -i",
    val allowSttyResize: Boolean = commandLine == "/bin/bash -i",
)

interface LinuxSession {
    val pid: Long? get() = null
    val isAlive: Boolean
    val output: Flow<TerminalOutput>

    suspend fun write(data: ByteArray)

    suspend fun resize(columns: Int, rows: Int)

    suspend fun interrupt()

    suspend fun close()
}
