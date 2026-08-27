package top.wkbin.taixu.runtime

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import top.wkbin.taixu.core.common.files.SafeFileTree
import top.wkbin.taixu.core.common.result.AppError
import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.core.common.result.ErrorCode
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StorageUsage(
    val rootfsBytes: Long,
    val runtimeBytes: Long,
    val toolBytes: Long,
    val dataBytes: Long,
    val workspaceBytes: Long,
    val cacheBytes: Long,
    val availableBytes: Long,
    val skillBytes: Long = 0L,
    val attachmentBytes: Long = 0L,
    val databaseBytes: Long = 0L,
    val appDataBytes: Long = 0L,
    val categories: List<StorageCategory> = emptyList(),
) {
    val totalManagedBytes: Long
        get() = rootfsBytes + runtimeBytes + toolBytes + dataBytes + workspaceBytes + cacheBytes + skillBytes + attachmentBytes + databaseBytes + appDataBytes
}

/** A non-overlapping storage bucket plus its optional drill-down entries. */
data class StorageCategory(
    val id: String,
    val name: String,
    val bytes: Long,
    val entries: List<StorageEntry> = emptyList(),
)

data class StorageEntry(
    val name: String,
    val detail: String = "",
    val bytes: Long,
)

@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pathManager: RuntimePathManager,
) {
    suspend fun inspect(): StorageUsage = withContext(Dispatchers.IO) {
        val distroIds = pathManager.listInstalledDistroIds()
        val rootfsEntries = distroIds.map { distroId ->
            StorageEntry(distroId, "Linux RootFS", sizeOf(pathManager.rootfsDir(distroId)))
        }
        val rootfsBytes = rootfsEntries.sumOf { it.bytes }
        // Keep this bucket exclusive: baseDir itself would double-count rootfs,
        // tools, data, workspace and cache in the UI.
        val runtimeBytes = listOf(
            pathManager.binDir,
            pathManager.homeDir,
            pathManager.stagingRootfsDir,
            pathManager.rootfsPreviousDir(),
            pathManager.tmpDir,
            pathManager.logsDir,
            pathManager.metadataDir,
        ).sumOf(::sizeOf) + distroIds.sumOf { sizeOf(pathManager.taixuRuntimesDir(it)) }
        val pluginEntries = distroIds.flatMap { distroId -> pluginEntries(distroId) }
        val toolBytes = pluginEntries.sumOf { it.bytes }
        val projectEntries = childEntries(pathManager.workspaceDir, "工作区项目")
        val workspaceBytes = projectEntries.sumOf { it.bytes }
        val skillsRoot = File(pathManager.attachmentsDir, "skills")
        val skillEntries = childEntries(skillsRoot, "自定义 Skill")
        val skillBytes = skillEntries.sumOf { it.bytes }
        val attachmentEntries = pathManager.attachmentsDir.listFiles().orEmpty()
            .filterNot { it.name == "skills" }
            .map { StorageEntry(it.name, "运行附件", sizeOf(it)) }
            .sortedByDescending { it.bytes }
        val attachmentBytes = attachmentEntries.sumOf { it.bytes }
        val cacheBytes = sizeOf(pathManager.cacheDir)
        // Room keeps conversations, messages and the rest of the app's relational data in the
        // same SQLite file. Include WAL/SHM separately so temporary growth is visible too.
        val databaseEntries = context.getDatabasePath("taixu.db").parentFile?.listFiles().orEmpty()
            .filter { it.name == "taixu.db" || it.name.startsWith("taixu.db-") }
            .map { StorageEntry(it.name, if (it.name == "taixu.db") "会话、消息与应用数据库" else "SQLite 辅助文件", sizeOf(it)) }
            .sortedByDescending { it.bytes }
        val databaseBytes = databaseEntries.sumOf { it.bytes }
        val appDataEntries = childEntries(File(context.filesDir, "datastore"), "应用偏好")
        val appDataBytes = appDataEntries.sumOf { it.bytes }
        val categories = listOf(
            StorageCategory("linux", "Linux 系统", rootfsBytes + runtimeBytes, rootfsEntries),
            StorageCategory("plugins", "插件", toolBytes, pluginEntries),
            StorageCategory("projects", "项目", workspaceBytes, projectEntries),
            StorageCategory("skills", "Skills", skillBytes, skillEntries),
            StorageCategory("database", "会话与数据库", databaseBytes, databaseEntries),
            StorageCategory("app_data", "应用数据", appDataBytes, appDataEntries),
            StorageCategory("cache", "下载缓存", cacheBytes),
            StorageCategory("attachments", "附件", attachmentBytes, attachmentEntries),
        )
        StorageUsage(
            rootfsBytes = rootfsBytes,
            runtimeBytes = runtimeBytes,
            toolBytes = toolBytes,
            dataBytes = 0L,
            workspaceBytes = workspaceBytes,
            cacheBytes = cacheBytes,
            availableBytes = availableBytes(),
            skillBytes = skillBytes,
            attachmentBytes = attachmentBytes,
            databaseBytes = databaseBytes,
            appDataBytes = appDataBytes,
            categories = categories,
        )
    }

    suspend fun clearCache(): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            pathManager.cacheDir.listFiles().orEmpty().forEach { file ->
                SafeFileTree.delete(file)
            }
            AppResult.Success(Unit)
        } catch (throwable: Throwable) {
            AppResult.Failure(
                AppError(
                    code = ErrorCode.IO,
                    message = throwable.message ?: "清理缓存失败",
                    cause = throwable,
                ),
            )
        }
    }

    fun hasEnoughSpace(requiredBytes: Long): Boolean = availableBytes() >= requiredBytes

    private fun availableBytes(): Long {
        val path = pathManager.baseDir.parentFile ?: pathManager.baseDir
        return StatFs(path.absolutePath).availableBytes
    }

    private fun sizeOf(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun childEntries(directory: File, detail: String): List<StorageEntry> = directory.listFiles()
        .orEmpty()
        .map { StorageEntry(it.name, detail, sizeOf(it)) }
        .sortedByDescending { it.bytes }

    /** Tool executable and state directories share an id and are one plugin in the UI. */
    private fun pluginEntries(distroId: String): List<StorageEntry> {
        val tools = pathManager.taixuToolsDir(distroId)
        val data = pathManager.taixuDataDir(distroId)
        return (tools.listFiles().orEmpty().map { it.name } + data.listFiles().orEmpty().map { it.name })
            .distinct()
            .map { id ->
                StorageEntry(
                    name = id,
                    detail = "$distroId · 程序与数据",
                    bytes = sizeOf(File(tools, id)) + sizeOf(File(data, id)),
                )
            }
            .sortedByDescending { it.bytes }
    }
}
