package top.wkbin.taixu.runtime

import android.os.StatFs
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
) {
    val totalManagedBytes: Long
        get() = rootfsBytes + runtimeBytes + toolBytes + dataBytes + workspaceBytes + cacheBytes
}

@Singleton
class StorageManager @Inject constructor(
    private val pathManager: RuntimePathManager,
) {
    suspend fun inspect(): StorageUsage = withContext(Dispatchers.IO) {
        val rootfsBytes = sizeOf(pathManager.rootfsDir)
        // Keep this bucket exclusive: baseDir itself would double-count rootfs,
        // tools, data, workspace and cache in the UI.
        val distroIds = pathManager.listInstalledDistroIds()
        val runtimeBytes = listOf(
            pathManager.binDir,
            pathManager.homeDir,
            pathManager.stagingRootfsDir,
            pathManager.rootfsPreviousDir(),
            pathManager.tmpDir,
            pathManager.logsDir,
            pathManager.metadataDir,
        ).sumOf(::sizeOf) + distroIds.sumOf { sizeOf(pathManager.taixuRuntimesDir(it)) }
        StorageUsage(
            rootfsBytes = rootfsBytes,
            runtimeBytes = runtimeBytes,
            toolBytes = distroIds.sumOf { sizeOf(pathManager.taixuToolsDir(it)) },
            dataBytes = distroIds.sumOf { sizeOf(pathManager.taixuDataDir(it)) },
            workspaceBytes = sizeOf(pathManager.workspaceDir),
            cacheBytes = sizeOf(pathManager.cacheDir),
            availableBytes = availableBytes(),
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
}
