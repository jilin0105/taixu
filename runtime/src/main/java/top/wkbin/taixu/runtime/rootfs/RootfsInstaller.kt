package top.wkbin.taixu.runtime.rootfs

import com.github.luben.zstd.ZstdInputStream
import top.wkbin.taixu.core.common.files.SafeFileTree
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.common.result.AppError
import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.core.common.result.ErrorCode
import top.wkbin.taixu.runtime.DistributionSpec
import top.wkbin.taixu.runtime.DownloadProgress
import top.wkbin.taixu.runtime.RegistryRoute
import top.wkbin.taixu.runtime.RuntimePathManager
import java.io.File
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Singleton
class RootfsInstaller @Inject constructor(
    private val pathManager: RuntimePathManager,
    private val tarStreamExtractor: TarStreamExtractor,
    private val rootfsValidator: RootfsValidator,
    private val logger: AppLogger,
    private val ociRegistryClient: OciRegistryClient,
) {
    private var pendingUpdateBackup: File? = null
    private var pendingUpdateVersion: String? = null

    suspend fun installOci(
        distribution: DistributionSpec,
        route: RegistryRoute,
        onProgress: suspend (DownloadProgress) -> Unit = {},
    ): AppResult<File> = withContext(Dispatchers.IO) {
        recoverInterruptedUpdate()
        try {
            val staging = prepareStaging()
            val version = pullInto(distribution, route, staging, onProgress)
            rootfsValidator.validate(staging)
            if (pathManager.isRootfsInstalled()) preserveUserDirectories(pathManager.rootfsDir, staging)
            replaceRootfs(staging, retainBackup = false)
            markInstalled(version)
            logger.i("Installed ${distribution.imageReference} through OCI into ${pathManager.rootfsDir}")
            AppResult.Success(pathManager.rootfsDir)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            SafeFileTree.delete(pathManager.stagingRootfsDir)
            logger.e("Failed to install OCI rootfs", throwable)
            failure("OCI RootFS 安装失败", throwable)
        }
    }

    suspend fun updateOci(
        distribution: DistributionSpec,
        route: RegistryRoute,
        onProgress: suspend (DownloadProgress) -> Unit = {},
    ): AppResult<File> = withContext(Dispatchers.IO) {
        if (!pathManager.isRootfsInstalled()) return@withContext installOci(distribution, route, onProgress)
        try {
            val staging = prepareStaging()
            val version = pullInto(distribution, route, staging, onProgress)
            rootfsValidator.validate(staging)
            preserveUserDirectories(pathManager.rootfsDir, staging)
            replaceRootfs(staging, retainBackup = true)
            pendingUpdateVersion = version
            pathManager.rootfsUpdatePendingMarker().writeText("rootfs-version=$version\n")
            AppResult.Success(pathManager.rootfsDir)
        } catch (cancellation: CancellationException) {
            rollbackPendingUpdate()
            throw cancellation
        } catch (throwable: Throwable) {
            SafeFileTree.delete(pathManager.stagingRootfsDir)
            if (pendingUpdateBackup != null || pathManager.rootfsPreviousDir().exists()) {
                rollbackPendingUpdate()
            }
            logger.e("Failed to update OCI rootfs", throwable)
            failure("OCI RootFS 更新失败", throwable)
        }
    }

    suspend fun rollbackPendingUpdate(): Boolean = withContext(NonCancellable + Dispatchers.IO) {
        val backup = pendingUpdateBackup ?: pathManager.rootfsPreviousDir().takeIf { it.exists() }
            ?: return@withContext false
        val rootfs = pathManager.rootfsDir
        if (rootfs.exists()) {
            // 先把（损坏的）新目录移到一边，再恢复备份；任一步失败都不删任何数据。
            val broken = File(rootfs.parentFile, "rootfs.broken")
            SafeFileTree.delete(broken)
            if (!rootfs.renameTo(broken)) {
                throw IllegalStateException("无法移开当前 RootFS（旧版本仍保留在 ${backup.path}）")
            }
            if (!backup.renameTo(rootfs)) {
                // 尽力还原现场（broken 仍留在磁盘，可手动恢复）
                runCatching { broken.renameTo(rootfs) }
                throw IllegalStateException("无法恢复旧 RootFS（旧版本仍保留在 ${backup.path}）")
            }
            SafeFileTree.delete(broken)
        } else {
            check(backup.renameTo(rootfs)) { "无法恢复旧 RootFS（旧版本仍保留在 ${backup.path}）" }
        }
        pendingUpdateBackup = null
        pendingUpdateVersion = null
        pathManager.rootfsUpdatePendingMarker().delete()
        true
    }

    suspend fun finalizePendingUpdate() = withContext(NonCancellable + Dispatchers.IO) {
        pendingUpdateVersion?.let(::markInstalled)
        pendingUpdateBackup?.takeIf { it.exists() }?.let(SafeFileTree::delete)
        pendingUpdateBackup = null
        pendingUpdateVersion = null
        pathManager.rootfsUpdatePendingMarker().delete()
    }

    private fun prepareStaging(): File {
        pathManager.ensureDirectories()
        return pathManager.stagingRootfsDir.also {
            SafeFileTree.delete(it)
            it.mkdirs()
        }
    }

    private suspend fun pullInto(
        distribution: DistributionSpec,
        route: RegistryRoute,
        staging: File,
        onProgress: suspend (DownloadProgress) -> Unit,
    ): String = ociRegistryClient.pull(
        distribution,
        route,
        File(pathManager.cacheDir, "oci_layers"),
        onProgress,
        resetDestination = {
            SafeFileTree.delete(staging)
            staging.mkdirs()
        },
    ) { layer, mediaType ->
        layer.inputStream().use { raw ->
            val stream = when {
                mediaType.contains("zstd") -> ZstdInputStream(raw)
                mediaType.contains("gzip") -> GZIPInputStream(raw)
                else -> raw
            }
            stream.use { tarStreamExtractor.extract(it, staging, handleWhiteouts = true) }
        }
    }

    private fun replaceRootfs(staging: File, retainBackup: Boolean) {
        val rootfs = pathManager.rootfsDir
        val backup = pathManager.rootfsPreviousDir()
        SafeFileTree.delete(backup)
        if (rootfs.exists() && !rootfs.renameTo(backup)) error("无法暂存旧 RootFS")
        if (!staging.renameTo(rootfs)) {
            if (!backup.renameTo(rootfs)) {
                // 恢复失败时旧版本仍保留在 backup，绝不丢数据；日志明确位置便于人工恢复。
                logger.e("RootFS: 启用新版本失败，且恢复旧版本也失败（旧版本保留在 ${backup.path}）")
                error("无法启用新 RootFS，且恢复失败（旧版本保留在 ${backup.path}）")
            }
            error("无法启用新 RootFS")
        }
        if (retainBackup) pendingUpdateBackup = backup else SafeFileTree.delete(backup)
    }

    private fun preserveUserDirectories(oldRootfs: File, newRootfs: File) {
        listOf("root", "opt/taixu").forEach { relative ->
            val source = File(oldRootfs, relative)
            if (source.exists()) {
                val target = File(newRootfs, relative)
                SafeFileTree.delete(target)
                SafeFileTree.copy(source, target)
            }
        }
    }

    private fun markInstalled(version: String) {
        pathManager.metadataDir.mkdirs()
        pathManager.rootfsInstalledMarker().writeText("rootfs-version=$version\n")
    }

    private fun recoverInterruptedUpdate() {
        val backup = pathManager.rootfsPreviousDir()
        if (!backup.exists()) {
            pathManager.rootfsUpdatePendingMarker().delete()
            return
        }
        val rootfs = pathManager.rootfsDir
        if (rootfs.exists()) {
            val broken = File(rootfs.parentFile, "rootfs.broken")
            SafeFileTree.delete(broken)
            if (!rootfs.renameTo(broken)) {
                throw IllegalStateException(
                    "启动恢复中断的更新失败：无法移开当前 RootFS（旧版本保留在 ${backup.path}）",
                )
            }
            runCatching { SafeFileTree.delete(broken) }
        }
        check(backup.renameTo(rootfs)) { "无法恢复上一次未提交的 RootFS 更新（旧版本保留在 ${backup.path}）" }
        pathManager.rootfsUpdatePendingMarker().delete()
        logger.w("Recovered previous RootFS after an interrupted update")
    }

    private fun failure(prefix: String, throwable: Throwable): AppResult<File> = AppResult.Failure(
        AppError(ErrorCode.INSTALLATION_FAILED, "$prefix：${throwable.message ?: "未知错误"}", throwable),
    )
}
