package top.wkbin.taixu.core.common.logging

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Stores one local, redacted crash report; no network upload is performed. */
@Singleton
class CrashReporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secretRedactor: SensitiveDataRedactor,
) {
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            write(thread, throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun latestReport(): String? = File(context.filesDir, REPORT_PATH)
        .takeIf { it.isFile }
        ?.readText()

    private fun write(thread: Thread, throwable: Throwable) {
        runCatching {
            val report = buildString {
                appendLine("TaiXu crash report")
                appendLine("thread=${thread.name}")
                appendLine("time=${System.currentTimeMillis()}")
                appendLine(secretRedactor.redact(throwable.stackTraceToString()))
            }
            val file = File(context.filesDir, REPORT_PATH)
            file.parentFile?.mkdirs()
            file.writeText(report.take(MAX_REPORT_CHARS))
        }
    }

    private companion object {
        const val REPORT_PATH = "logs/crash/latest.txt"
        const val MAX_REPORT_CHARS = 128 * 1024
    }
}
