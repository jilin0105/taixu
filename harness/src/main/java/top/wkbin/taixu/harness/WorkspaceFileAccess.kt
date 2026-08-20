package top.wkbin.taixu.harness

import top.wkbin.taixu.core.common.result.AppError
import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.core.common.result.ErrorCode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WorkspaceEntry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
)

/**
 * Harness 文件工具的受控访问层。
 *
 * 只允许在工作区根目录内操作：
 * - 拒绝 `..` 段、绝对路径逃逸、符号链接逃逸（canonical 校验）
 * - 写入使用临时文件 + 原子 rename，避免半写文件
 * - 读取限制单文件大小，防止把巨型文件灌进 LLM 上下文
 */
class WorkspaceFileAccess(
    private val root: File,
) {
    private val rootCanonical: File = root.absoluteFile.canonicalFile

    suspend fun list(path: String): AppResult<List<WorkspaceEntry>> = withContext(Dispatchers.IO) {
        try {
            val directory = resolveRequired(path)
            check(directory.isDirectory) { "不是目录：${display(path)}" }
            val entries = directory.listFiles().orEmpty()
                .map { WorkspaceEntry(it.name, it.isDirectory, if (it.isFile) it.length() else 0L) }
                .sortedWith(compareBy<WorkspaceEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
            AppResult.Success(entries)
        } catch (throwable: Throwable) {
            failure(path, throwable)
        }
    }

    suspend fun read(path: String): AppResult<String> = withContext(Dispatchers.IO) {
        try {
            val file = resolveRequired(path)
            check(file.isFile) { "不是文件：${display(path)}" }
            check(file.length() <= MAX_READ_BYTES) { "文件过大（${file.length()} 字节，上限 ${MAX_READ_BYTES}）" }
            AppResult.Success(file.readText(Charsets.UTF_8))
        } catch (throwable: Throwable) {
            failure(path, throwable)
        }
    }

    suspend fun write(path: String, content: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            require(content.length <= MAX_WRITE_BYTES) { "内容过长（${content.length} 字符，上限 ${MAX_WRITE_BYTES}）" }
            val file = resolveWritable(path)
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, ".${file.name}.tmp-${System.nanoTime()}")
            try {
                temporary.writeText(content, Charsets.UTF_8)
                if (!temporary.renameTo(file)) {
                    temporary.copyTo(file, overwrite = true)
                    temporary.delete()
                }
            } finally {
                temporary.delete()
            }
            AppResult.Success(Unit)
        } catch (throwable: Throwable) {
            failure(path, throwable)
        }
    }

    suspend fun edit(path: String, oldText: String, newText: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            require(oldText.isNotEmpty()) { "oldText 不能为空" }
            val file = resolveWritable(path)
            check(file.isFile) { "不是文件：${display(path)}" }
            val content = file.readText(Charsets.UTF_8)
            val first = content.indexOf(oldText)
            check(first >= 0) { "oldText 未找到" }
            check(first == content.lastIndexOf(oldText)) { "oldText 匹配多处，请提供更精确的上下文" }
            write(path, content.replaceRange(first, first + oldText.length, newText))
                .errorOrNull()?.let { throw it.cause ?: IllegalStateException(it.message) }
            AppResult.Success(Unit)
        } catch (throwable: Throwable) {
            failure(path, throwable)
        }
    }

    /** 解析路径并强制其位于工作区内；目录可以不存在（用于写入）。 */
    private fun resolveWritable(path: String): File {
        val resolved = resolve(path)
            ?: throw IllegalArgumentException("路径越界：${display(path)}")
        if (resolved.exists() && resolved.isDirectory) {
            throw IllegalArgumentException("是目录：${display(path)}")
        }
        return resolved
    }

    /** 解析路径，且要求目标存在。 */
    private fun resolveRequired(path: String): File {
        val resolved = resolve(path)
            ?: throw IllegalArgumentException("路径越界：${display(path)}")
        check(resolved.exists()) { "不存在：${display(path)}" }
        return resolved
    }

    private fun resolve(path: String): File? {
        val trimmed = path.trim()
        val segments = trimmed.split('/').filter { it.isNotEmpty() && it != "." }
        if (segments.any { it == ".." }) return null
        if (trimmed.startsWith("/") && segments.first() != "workspace") return null
        val relative = if (segments.firstOrNull() == "workspace") segments.drop(1) else segments
        var candidate = root
        for (segment in relative) {
            candidate = File(candidate, segment)
        }
        if (candidate == root) return root
        val canonical = candidate.canonicalFile
        return canonical.takeIf { isInside(rootCanonical, it) }
    }

    private fun isInside(root: File, candidate: File): Boolean =
        candidate.absolutePath == root.absolutePath ||
            candidate.absolutePath.startsWith(root.absolutePath + File.separator)

    private fun display(path: String): String =
        if (path.trim().startsWith("/")) path.trim() else "/workspace/${path.trim().trimStart('/')}"

    private fun failure(path: String, throwable: Throwable): AppResult<Nothing> =
        AppResult.Failure(AppError(ErrorCode.IO, "${display(path)}：${throwable.message}", throwable))

    companion object {
        const val MAX_READ_BYTES = 1 * 1024 * 1024L
        const val MAX_WRITE_BYTES = 1 * 1024 * 1024
    }
}
