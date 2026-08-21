package top.wkbin.taixu.runtime.shell

import top.wkbin.taixu.runtime.RuntimePathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessShellExecutor @Inject constructor(
    private val pathManager: RuntimePathManager,
) : ShellExecutor {

    override suspend fun execute(
        command: List<String>,
        workingDirectory: File?,
        environment: Map<String, String>,
        timeoutMs: Long,
    ): CommandResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val process = ProcessBuilder(command)
            .apply {
                workingDirectory?.let { directory(it) }
                // Keep the PRoot host environment deterministic. In particular,
                // never inherit Termux's LD_PRELOAD or a stale PROOT_LOADER.
                environment().clear()
                environment().putAll(pathManager.hostProcessEnvironment())
                environment().putAll(environment)
            }
            .redirectErrorStream(false)
            .start()

        // 🌟 关键修复：主动关闭子进程的标准输入（stdin），防止任何交互式脚本/命令无限阻塞在等待键盘输入上
        runCatching { process.outputStream.close() }

        val stdoutDeferred = async(Dispatchers.IO) {
            readFully(process.inputStream)
        }
        val stderrDeferred = async(Dispatchers.IO) {
            readFully(process.errorStream)
        }

        try {
            val exitCode = withTimeout(timeoutMs) {
                runInterruptible(Dispatchers.IO) { process.waitFor() }
            }
            val (stdout, stderr) = listOf(stdoutDeferred, stderrDeferred).awaitAll().let { values ->
                values[0] to values[1]
            }
            CommandResult(
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr,
                durationMs = System.currentTimeMillis() - startedAt,
            )
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            process.destroyForcibly()
            // 等进程树真正消亡：PRoot 被强杀后，被 ptrace 的 npm/node 由内核
            // 异步清除，若立刻开始回滚删除目录，可能撞上仍在写入的残留进程。
            runInterruptible(Dispatchers.IO) {
                process.waitFor(PROCESS_TEARDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
            stdoutDeferred.cancel()
            stderrDeferred.cancel()
            CommandResult(
                exitCode = TIMEOUT_EXIT_CODE,
                stdout = "",
                stderr = "Command timed out after ${timeoutMs}ms",
                durationMs = System.currentTimeMillis() - startedAt,
            )
        } finally {
            process.destroy()
        }
    }

    private suspend fun readFully(stream: java.io.InputStream): String = try {
        stream.use { input ->
            val kept = ByteArrayOutputStream(MAX_CAPTURE_BYTES)
            val buffer = ByteArray(READ_BUFFER_BYTES)
            var totalBytes = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                totalBytes += read
                val remaining = MAX_CAPTURE_BYTES - kept.size()
                if (remaining > 0) kept.write(buffer, 0, minOf(read, remaining))
            }
            buildString {
                append(kept.toByteArray().toString(Charsets.UTF_8))
                if (totalBytes > kept.size()) {
                    append("\n\n[进程输出已截断：共 ")
                    append(totalBytes)
                    append(" 字节，仅保留前 ")
                    append(kept.size())
                    append(" 字节]")
                }
            }
        }
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (io: java.io.IOException) {
        // Timeout teardown closes the process streams from this thread while a
        // reader is blocked in read(); Android surfaces that as
        // InterruptedIOException. Treat it as EOF: the timeout already produced
        // the authoritative CommandResult, and the reader failure must not
        // override it through structured-concurrency propagation.
        ""
    }

    private companion object {
        const val TIMEOUT_EXIT_CODE = 124
        const val PROCESS_TEARDOWN_TIMEOUT_MS = 1_000L
        const val MAX_CAPTURE_BYTES = 4 * 1024 * 1024
        const val READ_BUFFER_BYTES = 16 * 1024
    }
}
