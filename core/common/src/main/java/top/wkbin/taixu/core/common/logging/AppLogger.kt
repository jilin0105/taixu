package top.wkbin.taixu.core.common.logging

import android.util.Log
import android.content.Context
import top.wkbin.taixu.core.security.SecretRedactor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class AppLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secretRedactor: SecretRedactor,
) {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun d(message: String, throwable: Throwable? = null) = log("D", message, throwable) { text, error ->
        Log.d(TAG, text, error)
    }

    fun i(message: String, throwable: Throwable? = null) = log("I", message, throwable) { text, error ->
        Log.i(TAG, text, error)
    }

    fun w(message: String, throwable: Throwable? = null) = log("W", message, throwable) { text, error ->
        Log.w(TAG, text, error)
    }

    fun e(message: String, throwable: Throwable? = null) = log("E", message, throwable) { text, error ->
        Log.e(TAG, text, error)
    }

    private fun log(
        level: String,
        message: String,
        throwable: Throwable?,
        androidLog: (String, Throwable?) -> Unit,
    ) {
        val safeMessage = safe(message)
        val safeThrowable = safe(throwable)
        androidLog(safeMessage, safeThrowable)
        val stack = throwable?.let { secretRedactor.redact(it.stackTraceToString()) }.orEmpty()
        ioScope.launch {
            runCatching {
                val file = File(context.filesDir, LOG_PATH)
                file.parentFile?.mkdirs()
                synchronized(file) {
                    if (file.length() > MAX_LOG_BYTES) file.writeText("")
                    file.appendText(
                        "${System.currentTimeMillis()} [$level] $safeMessage" +
                            if (stack.isBlank()) "\n" else "\n$stack\n",
                    )
                }
            }
        }
    }

    fun logAgent(sessionId: String, tag: String, message: String, throwable: Throwable? = null) {
        val safeMessage = safe(message)
        val safeTag = safe(tag)
        val safeSessionId = safe(sessionId)
        val stack = throwable?.let { secretRedactor.redact(it.stackTraceToString()) }.orEmpty()
        Log.d(TAG, "[$safeTag][$safeSessionId] $safeMessage", throwable)
        ioScope.launch {
            runCatching {
                val file = getAgentLogFile()
                file.parentFile?.mkdirs()
                synchronized(file) {
                    if (file.length() > MAX_AGENT_LOG_BYTES) {
                        file.writeText("[LOG ROTATED at ${System.currentTimeMillis()}]\n")
                    }
                    val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)
                        .format(java.util.Date())
                    file.appendText(
                        "[$timestamp][$safeTag][session:$safeSessionId] $safeMessage" +
                            if (stack.isBlank()) "\n" else "\n$stack\n",
                    )
                }
            }
        }
    }

    fun getAgentLogFile(): File = File(context.filesDir, AGENT_LOG_PATH)

    fun readAgentLogs(maxChars: Int = 100_000): String = runCatching {
        val file = getAgentLogFile()
        if (!file.exists() || file.length() == 0L) return "暂无智能体本地日志"
        val text = file.readText(Charsets.UTF_8)
        if (text.length > maxChars) text.takeLast(maxChars) else text
    }.getOrDefault("读取日志失败")

    fun getAgentLogSizeBytes(): Long = runCatching {
        getAgentLogFile().takeIf { it.exists() }?.length() ?: 0L
    }.getOrDefault(0L)

    fun clearAgentLogs(): Boolean = runCatching {
        val file = getAgentLogFile()
        if (file.exists()) file.delete() else true
    }.getOrDefault(false)

    private fun safe(value: String): String = secretRedactor.redact(value)

    private fun safe(throwable: Throwable?): Throwable? = throwable?.let {
        val message = it.message.orEmpty()
        if (safe(message) == message) it else IllegalStateException(safe(message))
    }

    private companion object {
        const val TAG = "TaiXu"
        const val LOG_PATH = "logs/runtime.log"
        const val AGENT_LOG_PATH = "logs/agent_debug.log"
        const val MAX_LOG_BYTES = 1024L * 1024L
        const val MAX_AGENT_LOG_BYTES = 5 * 1024L * 1024L
    }
}
