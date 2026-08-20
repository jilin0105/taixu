package top.wkbin.taixu.runtime

import top.wkbin.taixu.core.common.files.SafeFileTree
import top.wkbin.taixu.core.common.result.AppError
import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.core.common.result.ErrorCode
import top.wkbin.taixu.core.database.WorkspaceDao
import top.wkbin.taixu.core.database.WorkspaceEntity
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class WorkspaceProject(
    val name: String,
    val path: String,
    val linuxPath: String,
    val sizeBytes: Long,
    val ownsDirectory: Boolean = true,
)

enum class WorkspaceStorage { INTERNAL, SHARED }

data class WorkspaceFileItem(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String = "",
)

/** 工作区：目录在 App 私有挂载点，元数据（路径/创建时间）存 Room。 */
@Singleton
class WorkspaceManager @Inject constructor(
    private val pathManager: RuntimePathManager,
    private val workspaceDao: WorkspaceDao,
) {
    fun observeProjects(): Flow<List<WorkspaceProject>> = workspaceDao.observeAll().map { entities ->
        entities.mapNotNull(::projectFromEntity)
    }

    suspend fun listProjects(): List<WorkspaceProject> = withContext(Dispatchers.IO) {
        pathManager.workspaceDir.mkdirs()
        // 目录为准；缺失的目录从 Room 补录
        val known = workspaceDao.listAll().associateBy { it.name }
        val knownPaths = known.values.mapNotNull { runCatching { File(it.path).canonicalPath }.getOrNull() }.toSet()
        val directories = pathManager.workspaceDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory && isValidProjectName(it.name) }
        directories.forEach { directory ->
            if (directory.name !in known && directory.canonicalPath !in knownPaths) {
                workspaceDao.upsert(
                    WorkspaceEntity(directory.name, directory.absolutePath, System.currentTimeMillis()),
                )
            }
        }
        val entities = workspaceDao.listAll().filter { it.name in known || File(it.path).isDirectory }
        entities
            .filter { entity -> File(entity.path).isDirectory }
            .sortedBy { it.name.lowercase() }
            .mapNotNull(::projectFromEntity)
    }

    suspend fun createProject(
        name: String,
        storage: WorkspaceStorage = WorkspaceStorage.INTERNAL,
        directoryPath: String = "",
    ): AppResult<WorkspaceProject> = withContext(Dispatchers.IO) {
        try {
            val safeName = name.trim()
            require(isValidProjectName(safeName)) { "名称需以文字或数字开头，只能包含文字、数字、点、下划线和短横线" }
            require(safeName != "sdcard") { "sdcard 是系统共享空间保留名称" }
            check(workspaceDao.findByName(safeName) == null) { "项目已存在：$safeName" }
            pathManager.workspaceDir.mkdirs()
            val base = when (storage) {
                WorkspaceStorage.INTERNAL -> pathManager.workspaceDir
                WorkspaceStorage.SHARED -> SHARED_STORAGE_ROOT
            }
            check(base.isDirectory || base.mkdirs()) { "关联空间不可用：${base.absolutePath}" }
            val prefix = if (storage == WorkspaceStorage.INTERNAL) "/workspace/" else "/sdcard/"
            val requested = directoryPath.trim().replace('\\', '/').removePrefix(prefix).trim('/')
            val relative = requested.ifBlank { safeName }
            require(relative.split('/').none { it.isBlank() || it == "." || it == ".." }) { "关联目录包含无效路径" }
            val directory = File(base, relative).canonicalFile
            check(isInside(base.canonicalFile, directory) && directory != base.canonicalFile) { "关联目录越界" }
            val duplicate = workspaceDao.listAll().any {
                it.name != safeName && runCatching { File(it.path).canonicalFile == directory }.getOrDefault(false)
            }
            check(!duplicate) { "该目录已关联其他工程" }
            val existed = directory.exists()
            check((existed && directory.isDirectory) || (!existed && directory.mkdirs())) { "无法创建或访问关联目录" }
            val ownsDirectory = storage == WorkspaceStorage.INTERNAL && !existed
            workspaceDao.upsert(
                WorkspaceEntity(safeName, directory.absolutePath, System.currentTimeMillis(), ownsDirectory),
            )
            AppResult.Success(projectFromEntity(workspaceDao.findByName(safeName)!!)!!)
        } catch (throwable: Throwable) {
            AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "创建项目失败", throwable))
        }
    }

    suspend fun deleteProject(name: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            require(isValidProjectName(name)) { "项目名称无效" }
            val entity = workspaceDao.findByName(name) ?: error("项目不存在：$name")
            val directory = File(entity.path)
            if (entity.ownsDirectory && directory.exists()) SafeFileTree.delete(directory)
            workspaceDao.delete(name)
            AppResult.Success(Unit)
        } catch (throwable: Throwable) {
            AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "删除项目失败", throwable))
        }
    }

    suspend fun linuxWorkingDirectory(name: String): String {
        if (name == "sdcard") return "/sdcard"
        require(isValidProjectName(name)) { "项目名称无效" }
        val entity = workspaceDao.findByName(name) ?: error("项目不存在：$name")
        check(File(entity.path).isDirectory) { "关联目录不存在：$name" }
        return linuxPathFor(File(entity.path))
    }

    /** 会话关联的工作区目录；返回 null 表示不关联。 */
    suspend fun workspaceForName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        return runCatching { linuxWorkingDirectory(name) }.getOrNull()
    }

    // ==================== 项目内文件管理 API ====================

    /** 列出项目指定相对路径下的所有文件与子目录（目录优先排序）。 */
    suspend fun listFiles(projectName: String, relativePath: String = ""): AppResult<List<WorkspaceFileItem>> =
        withContext(Dispatchers.IO) {
            try {
                val directory = resolveInProject(projectName, relativePath)
                val displayPath = displayPath(projectName, relativePath)
                check(directory.isDirectory) { "不是目录：$displayPath" }
                val projectRoot = getProjectRoot(projectName)
                val items = directory.listFiles().orEmpty()
                    .map { file ->
                        val rel = file.toRelativeString(projectRoot).replace(File.separatorChar, '/')
                        WorkspaceFileItem(
                            name = file.name,
                            relativePath = rel,
                            isDirectory = file.isDirectory,
                            sizeBytes = if (file.isFile) file.length() else 0L,
                            lastModified = file.lastModified(),
                            extension = if (file.isFile) file.extension.lowercase() else "",
                        )
                    }
                    .sortedWith(
                        compareBy<WorkspaceFileItem> { !it.isDirectory }
                            .thenBy { it.name.lowercase() },
                    )
                AppResult.Success(items)
            } catch (throwable: Throwable) {
                AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "读取文件列表失败", throwable))
            }
        }

    /** 读取文件内容（UTF-8，限制单文件最大读取大小）。 */
    suspend fun readFile(projectName: String, relativePath: String): AppResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val file = resolveInProject(projectName, relativePath)
                val displayPath = displayPath(projectName, relativePath)
                check(file.isFile) { "不是文件：$displayPath" }
                check(file.length() <= MAX_FILE_READ_BYTES) {
                    "文件过大（${file.length()} 字节，上限 ${MAX_FILE_READ_BYTES / 1024 / 1024} MB）"
                }
                AppResult.Success(file.readText(Charsets.UTF_8))
            } catch (throwable: Throwable) {
                AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "读取文件失败", throwable))
            }
        }

    /** 写入文件内容（原子临时文件替换）。 */
    suspend fun writeFile(projectName: String, relativePath: String, content: String): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                require(content.length <= MAX_FILE_WRITE_CHARS) {
                    "内容过长（${content.length} 字符，上限 $MAX_FILE_WRITE_CHARS）"
                }
                val file = resolveInProject(projectName, relativePath, allowMissing = true)
                if (file.exists() && file.isDirectory) {
                    throw IllegalArgumentException("目标是目录：${displayPath(projectName, relativePath)}")
                }
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
                AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "保存文件失败", throwable))
            }
        }

    /** 创建新文件（空文件）。 */
    suspend fun createFile(projectName: String, relativePath: String): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = resolveInProject(projectName, relativePath, allowMissing = true)
                check(!file.exists()) { "文件已存在：${file.name}" }
                file.parentFile?.mkdirs()
                check(file.createNewFile()) { "无法创建文件" }
                AppResult.Success(Unit)
            } catch (throwable: Throwable) {
                AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "创建文件失败", throwable))
            }
        }

    /** 创建新目录。 */
    suspend fun createDirectory(projectName: String, relativePath: String): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val dir = resolveInProject(projectName, relativePath, allowMissing = true)
                check(!dir.exists()) { "目录已存在：${dir.name}" }
                check(dir.mkdirs()) { "无法创建目录" }
                AppResult.Success(Unit)
            } catch (throwable: Throwable) {
                AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "创建目录失败", throwable))
            }
        }

    /** 重命名文件或目录。 */
    suspend fun renameItem(projectName: String, oldRelativePath: String, newName: String): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val safeNewName = newName.trim()
                require(safeNewName.isNotBlank() && !safeNewName.contains('/') && !safeNewName.contains('\\')) {
                    "新名称不合法"
                }
                val file = resolveInProject(projectName, oldRelativePath)
                val target = File(file.parentFile, safeNewName)
                check(!target.exists()) { "目标已存在：$safeNewName" }
                check(file.renameTo(target)) { "重命名失败" }
                AppResult.Success(Unit)
            } catch (throwable: Throwable) {
                AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "重命名失败", throwable))
            }
        }

    /** 删除文件或目录。 */
    suspend fun deleteItem(projectName: String, relativePath: String): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = resolveInProject(projectName, relativePath)
                val projectRoot = getProjectRoot(projectName)
                check(file.canonicalFile != projectRoot.canonicalFile) {
                    "不能通过此接口删除工作区根目录"
                }
                if (file.isDirectory) {
                    SafeFileTree.delete(file)
                } else {
                    check(file.delete()) { "删除文件失败" }
                }
                AppResult.Success(Unit)
            } catch (throwable: Throwable) {
                AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "删除失败", throwable))
            }
        }

    private suspend fun getProjectRoot(projectName: String): File {
        if (projectName == "sdcard") {
            val sdcard = File("/storage/emulated/0")
            if (sdcard.exists()) return sdcard
        }
        require(isValidProjectName(projectName)) { "项目名称无效：$projectName" }
        val entity = workspaceDao.findByName(projectName) ?: error("项目不存在：$projectName")
        val root = File(entity.path)
        check(root.isDirectory) { "关联目录不存在：$projectName" }
        return root
    }

    /**
     * 安全解析项目内相对路径：
     * - 过滤 `..` 与空段；
     * - 校验最终 canonical path 位于项目根目录内部；
     * - 防范跨工作区与系统越界。
     */
    private suspend fun resolveInProject(projectName: String, relativePath: String, allowMissing: Boolean = false): File {
        val root = getProjectRoot(projectName)
        val rootCanonical = root.canonicalFile
        val trimmed = relativePath.trim().removePrefix("/workspace/$projectName").removePrefix("/sdcard").removePrefix("/")
        val segments = trimmed.split('/', '\\').filter { it.isNotEmpty() && it != "." }
        if (segments.any { it == ".." }) {
            throw IllegalArgumentException("路径包含越界操作符 (..)")
        }
        var candidate = root
        for (segment in segments) {
            candidate = File(candidate, segment)
        }
        if (candidate == root) return root
        val canonical = candidate.canonicalFile
        if (!isInside(rootCanonical, canonical)) {
            throw IllegalArgumentException("路径越界：$relativePath")
        }
        if (!allowMissing && !candidate.exists()) {
            throw IllegalArgumentException("目标不存在：${displayPath(projectName, relativePath)}")
        }
        return candidate
    }

    private fun isInside(root: File, candidate: File): Boolean =
        candidate.absolutePath == root.absolutePath ||
            candidate.absolutePath.startsWith(root.absolutePath + File.separator)

    private suspend fun displayPath(projectName: String, relativePath: String): String =
        if (projectName == "sdcard") "/sdcard/${relativePath.trimStart('/')}"
        else "${linuxPathFor(getProjectRoot(projectName))}/${relativePath.trimStart('/')}"

    private fun projectFromEntity(entity: WorkspaceEntity): WorkspaceProject? {
        val directory = File(entity.path)
        if (!directory.isDirectory) return null
        return WorkspaceProject(
            name = entity.name,
            path = entity.path,
            linuxPath = linuxPathFor(directory),
            sizeBytes = sizeOf(directory),
            ownsDirectory = entity.ownsDirectory,
        )
    }

    private fun linuxPathFor(directory: File): String {
        val canonical = directory.canonicalFile
        val internal = pathManager.workspaceDir.canonicalFile
        val shared = SHARED_STORAGE_ROOT.canonicalFile
        return when {
            isInside(internal, canonical) -> "/workspace/${canonical.toRelativeString(internal).replace(File.separatorChar, '/')}"
            isInside(shared, canonical) -> "/sdcard/${canonical.toRelativeString(shared).replace(File.separatorChar, '/')}"
            else -> error("目录不在可关联空间内")
        }.trimEnd('/')
    }

    private fun sizeOf(file: File): Long = file.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun isValidProjectName(name: String): Boolean {
        if (name.isEmpty() || name.length > MAX_PROJECT_NAME_LENGTH) return false
        if (!name.first().isLetterOrDigit()) return false
        return name.drop(1).all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
    }

    companion object {
        const val MAX_PROJECT_NAME_LENGTH = 64
        const val MAX_FILE_READ_BYTES = 4 * 1024 * 1024L // 4 MB
        const val MAX_FILE_WRITE_CHARS = 4 * 1024 * 1024 // 4 M 字符
        val SHARED_STORAGE_ROOT: File = File("/storage/emulated/0")
    }
}
