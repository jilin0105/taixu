package top.wkbin.taixu.runtime.rootfs

import com.github.luben.zstd.ZstdInputStream
import org.tukaani.xz.XZInputStream
import top.wkbin.taixu.core.common.files.SafeFileTree
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.common.result.AppError
import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.core.common.result.ErrorCode
import top.wkbin.taixu.runtime.DistributionSpec
import top.wkbin.taixu.runtime.DownloadProgress
import top.wkbin.taixu.runtime.RegistryRoute
import top.wkbin.taixu.runtime.RuntimePathManager
import top.wkbin.taixu.runtime.RootfsUpdateInfo
import java.io.BufferedInputStream
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
    private val lxcImagesClient: LxcImagesClient,
) {
    private var pendingUpdateBackup: File? = null
    private var pendingUpdateVersion: String? = null
    private var pendingUpdateDigest: String? = null

    suspend fun checkForUpdate(
        distribution: DistributionSpec,
        route: RegistryRoute,
    ): RootfsUpdateInfo = withContext(Dispatchers.IO) {
        val latest = ociRegistryClient.resolve(distribution, route)
        val currentDigest = pathManager.rootfsDigest(distribution.id)
        RootfsUpdateInfo(
            distroId = distribution.id,
            imageReference = distribution.imageReference,
            currentVersion = pathManager.rootfsVersion(distribution.id),
            currentDigest = currentDigest,
            latestDigest = latest.digest,
            hasUpdate = currentDigest.isNullOrBlank() || !currentDigest.equals(latest.digest, ignoreCase = true),
        )
    }

    suspend fun installOci(
        distribution: DistributionSpec,
        route: RegistryRoute,
        onProgress: suspend (DownloadProgress) -> Unit = {},
    ): AppResult<File> = withContext(Dispatchers.IO) {
        val distroId = distribution.id.lowercase()
        val distroTargetDir = pathManager.rootfsDir(distroId)
        val staging = prepareStaging(distroId)
        recoverInterruptedUpdate(distroId)
        try {
            val image = pullInto(distribution, route, staging, onProgress)
            rootfsValidator.validate(staging)
            if (pathManager.isDistroInstalled(distroId)) preserveUserDirectories(distroTargetDir, staging)
            replaceRootfs(distroId, staging, retainBackup = false)
            markInstalled(distroId, image)
            pathManager.homeDir(distroId).mkdirs()
            logger.i("Installed ${distribution.imageReference} through OCI into $distroTargetDir")
            AppResult.Success(distroTargetDir)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            SafeFileTree.delete(pathManager.stagingRootfsDir(distroId))
            logger.e("Failed to install OCI rootfs for $distroId", throwable)
            failure("OCI RootFS ($distroId) 安装失败", throwable)
        }
    }

    suspend fun importArchive(distroId: String, archive: File): AppResult<File> = withContext(Dispatchers.IO) {
        val safeId = distroId.lowercase().trim()
        require(safeId.matches(Regex("[a-z0-9][a-z0-9_-]{0,31}"))) { "Linux 发行版 ID 无效" }
        require(archive.isFile) { "导入文件不存在" }
        val staging = prepareStaging(safeId)
        try {
            BufferedInputStream(archive.inputStream()).use { buffered ->
                buffered.mark(8)
                val magic = ByteArray(4)
                buffered.read(magic)
                buffered.reset()
                require(!(magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte())) {
                    "ZIP 无法可靠保留 Linux 权限与符号链接，请使用 tar、tar.gz、tar.xz 或 tar.zst"
                }
                val stream = when {
                    magic[0] == 0x1f.toByte() && magic[1] == 0x8b.toByte() -> GZIPInputStream(buffered)
                    magic[0] == 0x28.toByte() && magic[1] == 0xb5.toByte() -> ZstdInputStream(buffered)
                    magic[0] == 0xfd.toByte() && magic[1] == 0x37.toByte() -> XZInputStream(buffered)
                    else -> buffered
                }
                stream.use { tarStreamExtractor.extract(it, staging, handleWhiteouts = false) }
            }
            val actualRoot = normalizeImportedRoot(staging)
            if (actualRoot != staging) {
                val normalized = File(staging.parentFile, "rootfs.normalized")
                SafeFileTree.delete(normalized)
                check(actualRoot.renameTo(normalized)) { "无法整理导入 RootFS" }
                SafeFileTree.delete(staging)
                check(normalized.renameTo(staging)) { "无法提交导入 RootFS" }
            }
            rootfsValidator.validate(staging)
            val target = pathManager.rootfsDir(safeId)
            if (pathManager.isDistroInstalled(safeId)) preserveUserDirectories(target, staging)
            replaceRootfs(safeId, staging, retainBackup = false)
            markInstalled(safeId, OciRegistryClient.ImageInfo("local-import", "local-${archive.length()}-${archive.lastModified()}"))
            pathManager.homeDir(safeId).mkdirs()
            AppResult.Success(target)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            SafeFileTree.delete(staging)
            logger.e("Failed to import rootfs archive for $safeId", throwable)
            failure("导入 RootFS（$safeId）失败", throwable)
        }
    }

    private fun normalizeImportedRoot(staging: File): File {
        val children = staging.listFiles().orEmpty().filterNot { it.name == ".taixu" }
        if (children.size == 1 && children[0].isDirectory) {
            val nested = children[0]
            val hasRootMarker = File(nested, "etc/os-release").isFile || File(nested, "usr/lib/os-release").isFile
            if (hasRootMarker) return nested
        }
        return staging
    }

    suspend fun updateOci(
        distribution: DistributionSpec,
        route: RegistryRoute,
        onProgress: suspend (DownloadProgress) -> Unit = {},
    ): AppResult<File> = withContext(Dispatchers.IO) {
        val distroId = distribution.id.lowercase()
        val distroTargetDir = pathManager.rootfsDir(distroId)
        if (!pathManager.isDistroInstalled(distroId)) return@withContext installOci(distribution, route, onProgress)
        try {
            val staging = prepareStaging(distroId)
            val image = pullInto(distribution, route, staging, onProgress)
            rootfsValidator.validate(staging)
            preserveUserDirectories(distroTargetDir, staging)
            replaceRootfs(distroId, staging, retainBackup = true)
            pendingUpdateVersion = image.version
            pendingUpdateDigest = image.digest
            pathManager.rootfsUpdatePendingMarker(distroId).writeText(
                "rootfs-version=${image.version}\nrootfs-digest=${image.digest}\n",
            )
            AppResult.Success(distroTargetDir)
        } catch (cancellation: CancellationException) {
            rollbackPendingUpdate(distroId)
            throw cancellation
        } catch (throwable: Throwable) {
            SafeFileTree.delete(pathManager.stagingRootfsDir(distroId))
            if (pendingUpdateBackup != null || pathManager.rootfsPreviousDir(distroId).exists()) {
                rollbackPendingUpdate(distroId)
            }
            logger.e("Failed to update OCI rootfs for $distroId", throwable)
            failure("OCI RootFS ($distroId) 更新失败", throwable)
        }
    }

    suspend fun uninstallDistro(distroId: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        val safeId = distroId.lowercase().trim()
        val dir = pathManager.distroDir(safeId)
        if (!dir.exists()) {
            return@withContext AppResult.Success(Unit)
        }
        try {
            SafeFileTree.delete(dir)
            logger.i("Uninstalled distro $safeId from disk")
            AppResult.Success(Unit)
        } catch (e: Exception) {
            logger.e("Failed to uninstall distro $safeId", e)
            AppResult.Failure(AppError(ErrorCode.IO, "卸载系统失败：${e.message}", e))
        }
    }

    suspend fun rollbackPendingUpdate(distroId: String = "ubuntu"): Boolean = withContext(NonCancellable + Dispatchers.IO) {
        val backup = pendingUpdateBackup ?: pathManager.rootfsPreviousDir(distroId).takeIf { it.exists() }
            ?: return@withContext false
        val rootfs = pathManager.rootfsDir(distroId)
        if (rootfs.exists()) {
            val broken = File(rootfs.parentFile, "rootfs.broken")
            SafeFileTree.delete(broken)
            if (!rootfs.renameTo(broken)) {
                throw IllegalStateException("无法移开当前 RootFS（旧版本仍保留在 ${backup.path}）")
            }
            if (!backup.renameTo(rootfs)) {
                runCatching { broken.renameTo(rootfs) }
                throw IllegalStateException("无法恢复旧 RootFS（旧版本仍保留在 ${backup.path}）")
            }
            SafeFileTree.delete(broken)
        } else {
            check(backup.renameTo(rootfs)) { "无法恢复旧 RootFS（旧版本仍保留在 ${backup.path}）" }
        }
        pendingUpdateBackup = null
        pendingUpdateVersion = null
        pendingUpdateDigest = null
        pathManager.rootfsUpdatePendingMarker(distroId).delete()
        true
    }

    suspend fun finalizePendingUpdate(distroId: String = "ubuntu") = withContext(NonCancellable + Dispatchers.IO) {
        pendingUpdateVersion?.let {
            markInstalled(distroId, OciRegistryClient.ImageInfo(it, pendingUpdateDigest.orEmpty()))
        }
        pendingUpdateBackup?.takeIf { it.exists() }?.let(SafeFileTree::delete)
        pendingUpdateBackup = null
        pendingUpdateVersion = null
        pendingUpdateDigest = null
        pathManager.rootfsUpdatePendingMarker(distroId).delete()
    }

    private fun prepareStaging(distroId: String): File {
        pathManager.ensureDirectories()
        val staging = pathManager.stagingRootfsDir(distroId)
        SafeFileTree.delete(staging)
        staging.mkdirs()
        return staging
    }

    private suspend fun pullInto(
        distribution: DistributionSpec,
        route: RegistryRoute,
        staging: File,
        onProgress: suspend (DownloadProgress) -> Unit,
    ): OciRegistryClient.ImageInfo {
        val applyLayer: suspend (File, String) -> Unit = { layer, mediaType ->
            layer.inputStream().use { raw ->
                val stream = when {
                    mediaType.contains("zstd") -> ZstdInputStream(raw)
                    mediaType.contains("gzip") -> GZIPInputStream(raw)
                    mediaType.contains("xz") -> XZInputStream(raw)
                    else -> raw
                }
                stream.use { tarStreamExtractor.extract(it, staging, handleWhiteouts = true) }
            }
        }
        return try {
            ociRegistryClient.pull(
                distribution,
                route,
                File(pathManager.cacheDir, "oci_layers"),
                onProgress,
                resetDestination = {
                    SafeFileTree.delete(staging)
                    staging.mkdirs()
                },
                applyLayer,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ociFailure: Throwable) {
            if (route == RegistryRoute.OFFICIAL || !lxcImagesClient.supports(distribution.id)) {
                throw ociFailure
            }
            logger.w(
                "OCI routes exhausted for ${distribution.id}, falling back to TUNA lxc-images " +
                    "minimal rootfs (不含 buildpack-deps 工具链)",
                ociFailure,
            )
            SafeFileTree.delete(staging)
            staging.mkdirs()
            val version = lxcImagesClient.pull(
                distribution,
                File(pathManager.cacheDir, "lxc_images"),
                onProgress,
                applyLayer,
            )
            OciRegistryClient.ImageInfo(version, "lxc-$version")
        }
    }

    private fun replaceRootfs(distroId: String, staging: File, retainBackup: Boolean) {
        val rootfs = pathManager.rootfsDir(distroId)
        val backup = pathManager.rootfsPreviousDir(distroId)
        SafeFileTree.delete(backup)
        if (rootfs.exists() && !rootfs.renameTo(backup)) error("无法暂存旧 RootFS")
        if (!staging.renameTo(rootfs)) {
            if (!backup.renameTo(rootfs)) {
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

    private fun markInstalled(distroId: String, image: OciRegistryClient.ImageInfo) {
        pathManager.metadataDir(distroId).mkdirs()
        pathManager.rootfsInstalledMarker(distroId).writeText(
            "rootfs-version=${image.version}\nrootfs-digest=${image.digest}\n",
        )
    }

    private fun recoverInterruptedUpdate(distroId: String) {
        val backup = pathManager.rootfsPreviousDir(distroId)
        if (!backup.exists()) {
            pathManager.rootfsUpdatePendingMarker(distroId).delete()
            return
        }
        val rootfs = pathManager.rootfsDir(distroId)
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
        pathManager.rootfsUpdatePendingMarker(distroId).delete()
        logger.w("Recovered previous RootFS after an interrupted update for $distroId")
    }

    private fun failure(prefix: String, throwable: Throwable): AppResult<File> = AppResult.Failure(
        AppError(ErrorCode.INSTALLATION_FAILED, "$prefix：${throwable.message ?: "未知错误"}", throwable),
    )
}
