package top.wkbin.taixu.runtime

import android.content.Context
import top.wkbin.taixu.core.common.files.SafeFileTree
import top.wkbin.taixu.runtime.rootfs.RootfsValidator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuntimePathManager @Inject constructor(
    @ApplicationContext context: Context,
    private val rootfsValidator: RootfsValidator,
) {
    private val appFilesDir: File = context.filesDir
    val bundledProotFile: File = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
    private val nativeLibraryDir: File = File(context.applicationInfo.nativeLibraryDir)
    val bundledProotLoaderFile: File = File(nativeLibraryDir, "libproot-loader.so")
    val bundledProotLoader32File: File = File(nativeLibraryDir, "libproot-loader32.so")

    val baseDir: File = File(appFilesDir, "linux-runtime")
    val binDir: File = File(baseDir, "bin")
    val prootFile: File = File(binDir, "proot")
    val distrosDir: File = File(baseDir, "distros")
    val legacyRootfsDir: File = File(baseDir, "rootfs")
    val rootfsDir: File get() {
        val installed = listInstalledDistroIds().firstOrNull() ?: "ubuntu"
        val target = rootfsDir(installed)
        return if (target.exists()) target else legacyRootfsDir
    }
    val stagingRootfsDir: File = File(baseDir, "rootfs.staging")
    val legacyHomeDir: File = File(baseDir, "home")
    val homeDir: File get() {
        val installed = listInstalledDistroIds().firstOrNull() ?: "ubuntu"
        val target = homeDir(installed)
        return if (target.exists()) target else legacyHomeDir
    }
    val optDir: File = File(baseDir, "opt")
    val workspaceDir: File = File(baseDir, "workspace")
    val cacheDir: File = File(baseDir, "cache")
    val tmpDir: File = File(baseDir, "tmp")
    val logsDir: File = File(baseDir, "logs")
    val metadataDir: File = File(baseDir, "metadata")
    val taixuRootDir: File = File(optDir, "taixu")
    val taixuRuntimesDir: File = File(taixuRootDir, "runtimes")
    val taixuToolsDir: File = File(taixuRootDir, "tools")
    val taixuDataDir: File = File(taixuRootDir, "data")
    private val hostLibraries = mapOf(
        "libtalloc.so" to "libtalloc.so.2",
        "libandroid-shmem.so" to "libandroid-shmem.so",
    )

    // ==================== 多系统发行版路径解析 ====================

    fun distroDir(distroId: String): File = File(distrosDir, distroId.lowercase().trim())
    fun rootfsDir(distroId: String): File = File(distroDir(distroId), "rootfs")
    fun homeDir(distroId: String): File = File(distroDir(distroId), "home")
    fun metadataDir(distroId: String): File = File(distroDir(distroId), "metadata")
    fun stagingRootfsDir(distroId: String): File = File(distroDir(distroId), "rootfs.staging")
    fun rootfsPreviousDir(distroId: String): File = File(distroDir(distroId), "rootfs.previous")
    fun rootfsInstalledMarker(distroId: String): File = File(metadataDir(distroId), "rootfs.installed")
    fun rootfsUpdatePendingMarker(distroId: String): File = File(metadataDir(distroId), "rootfs.update.pending")

    fun isDistroInstalled(distroId: String): Boolean =
        rootfsInstalledMarker(distroId).isFile && rootfsValidator.isValid(rootfsDir(distroId))

    fun listInstalledDistroIds(): List<String> {
        if (!distrosDir.exists()) return emptyList()
        return distrosDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory && isDistroInstalled(it.name) }
            .map { it.name }
    }

    fun rootfsVersion(distroId: String): String? = rootfsInstalledMarker(distroId)
        .takeIf { it.isFile }
        ?.useLines { lines ->
            lines.firstOrNull()?.substringAfter("rootfs-version=", "")?.trim()
        }
        ?.takeIf { it.isNotBlank() }

    fun distroSizeBytes(distroId: String): Long {
        val dir = distroDir(distroId)
        if (!dir.exists()) return 0L
        return runCatching {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
    }

    /**
     * Android 10+ blocks execve() from writable app data for apps targeting API 29+.
     * The small Android-native PRoot fallback is therefore shipped as an extracted
     * native library, while the Debian Linux system remains an online download.
     */
    fun activeProotFile(): File = bundledProotFile.takeIf { isUsableProot(it) } ?: prootFile

    fun ensureDirectories() {
        listOf(
            baseDir,
            binDir,
            distrosDir,
            optDir,
            taixuRootDir,
            taixuRuntimesDir,
            taixuToolsDir,
            taixuDataDir,
            File(taixuRootDir, "bin"),
            workspaceDir,
            cacheDir,
            tmpDir,
            logsDir,
            metadataDir,
        ).forEach { it.mkdirs() }
    }

    fun cleanupStalePtyMarkers() {
        taixuToolsDir.parentFile
            ?.listFiles()
            .orEmpty()
            .filter { it.name.startsWith(".pty-") }
            .forEach { it.delete() }
    }

    /**
     * 老版本单 rootfs 向多系统目录拓扑平滑迁移：
     * 如果 linux-runtime/rootfs 存在且包含系统，将其平移至 linux-runtime/distros/<defaultDistroId>/
     */
    fun migrateLegacySingleDistro(defaultDistroId: String = "ubuntu") {
        ensureDirectories()
        val targetDistroDir = distroDir(defaultDistroId)
        val targetRootfs = rootfsDir(defaultDistroId)
        val targetHome = homeDir(defaultDistroId)
        val targetMeta = metadataDir(defaultDistroId)

        if (legacyRootfsDir.exists() && rootfsValidator.isValid(legacyRootfsDir) && !targetRootfs.exists()) {
            targetDistroDir.mkdirs()
            targetMeta.mkdirs()
            legacyRootfsDir.renameTo(targetRootfs)

            val legacyMarker = rootfsInstalledMarker()
            if (legacyMarker.exists()) {
                val targetMarker = rootfsInstalledMarker(defaultDistroId)
                legacyMarker.renameTo(targetMarker)
            } else {
                rootfsInstalledMarker(defaultDistroId).writeText("rootfs-version=legacy-migrated\n")
            }

            if (legacyHomeDir.exists() && !targetHome.exists()) {
                legacyHomeDir.renameTo(targetHome)
            }
        }
    }

    /** Move legacy in-rootfs user data to the persistent app-private bind mounts once. */
    fun migratePersistentDirectories(distroId: String = "ubuntu") {
        ensureDirectories()
        val rfs = rootfsDir(distroId)
        val hDir = homeDir(distroId)
        migrateMissingPersistentFiles(File(rfs, "root"), hDir)
        // Import data created before the project adopted the TaiXu name.
        migrateMissingPersistentFiles(File(optDir, LEGACY_OPT_NAME), taixuRootDir)
        migrateMissingPersistentFiles(File(rfs, "opt/$LEGACY_OPT_NAME"), taixuRootDir)
        migrateMissingPersistentFiles(File(rfs, "opt/taixu"), taixuRootDir)
    }

    private fun migrateMissingPersistentFiles(source: File, target: File) {
        if (!source.exists()) return
        target.mkdirs()
        val sourceRoot = source.canonicalFile
        val targetRoot = target.canonicalFile
        source.listFiles().orEmpty().forEach { child ->
            if (!isInside(sourceRoot, child.canonicalFile)) return@forEach
            val targetChild = File(target, child.name)
            if (!isInside(targetRoot, targetChild.canonicalFile)) return@forEach
            if (!targetChild.exists()) {
                SafeFileTree.copy(child, targetChild)
            }
        }
    }

    private fun isInside(root: File, candidate: File): Boolean =
        candidate.absolutePath == root.absolutePath ||
            candidate.absolutePath.startsWith(root.absolutePath + File.separator)

    /**
     * PRoot is an Android-native executable, but its Termux build keeps talloc as a
     * dynamically linked library. Android's APK native-library packaging only accepts
     * the `.so` suffix, so copy the bundled library to the exact SONAME expected by
     * the linker in the app-private runtime directory before launching PRoot.
     */
    fun hostProcessEnvironment(distroId: String = "ubuntu"): Map<String, String> {
        ensureDirectories()
        val rfs = rootfsDir(distroId)
        hostLibraries.forEach { (bundledName, hostName) ->
            val bundledFile = File(nativeLibraryDir, bundledName)
            val hostFile = File(binDir, hostName)
            if (bundledFile.isFile && bundledFile.length() > MIN_TALLOC_BYTES) {
                // Refresh tiny native dependencies after an APK update; equal
                // file length alone does not prove that an old SONAME copy matches.
                val needsCopy = !hostFile.isFile ||
                    hostFile.length() != bundledFile.length() ||
                    !bundledFile.readBytes().contentEquals(hostFile.readBytes())
                if (needsCopy) bundledFile.copyTo(hostFile, overwrite = true)
                hostFile.setReadable(true, false)
                hostFile.setWritable(true, true)
            }
        }
        return buildMap {
            put("PROOT_L2S_DIR", File(rfs, ".l2s").apply { mkdirs() }.absolutePath)
            if (isUsableNativeArtifact(bundledProotLoaderFile, MIN_PROOT_LOADER_BYTES)) {
                put("PROOT_LOADER", bundledProotLoaderFile.absolutePath)
            }
            if (isUsableNativeArtifact(bundledProotLoader32File, MIN_PROOT_LOADER_BYTES)) {
                put("PROOT_LOADER_32", bundledProotLoader32File.absolutePath)
            }
            put("TMPDIR", tmpDir.absolutePath)
            put("TMP", tmpDir.absolutePath)
            put("TEMP", tmpDir.absolutePath)
            put("PROOT_TMP_DIR", tmpDir.absolutePath)
            if (hostLibraries.values.any { File(binDir, it).isFile }) {
                put("LD_LIBRARY_PATH", binDir.absolutePath)
            }
        }
    }

    fun rootfsInstalledMarker(): File = File(metadataDir, "rootfs.installed")

    fun rootfsUpdatePendingMarker(): File = File(metadataDir, "rootfs.update.pending")

    fun rootfsPreviousDir(): File = File(baseDir, "rootfs.previous")

    fun rootfsVersion(): String? = rootfsInstalledMarker()
        .takeIf { it.isFile }
        ?.useLines { lines ->
            lines.firstOrNull()?.substringAfter("rootfs-version=", "")?.trim()
        }
        ?.takeIf { it.isNotBlank() }

    /** PRoot is usable only when both the tracer and its external ARM64 loader are in the APK. */
    fun isProotInstalled(): Boolean =
        isUsableProot(activeProotFile()) &&
            isUsableNativeArtifact(bundledProotLoaderFile, MIN_PROOT_LOADER_BYTES)

    fun isRootfsInstalled(): Boolean =
        listInstalledDistroIds().isNotEmpty() || (rootfsInstalledMarker().isFile && rootfsValidator.isValid(legacyRootfsDir))

    private fun isUsableProot(file: File): Boolean =
        file.isFile && file.length() > MIN_PROOT_BYTES && (file.canExecute() || file == bundledProotFile)

    private fun isUsableNativeArtifact(file: File, minimumBytes: Long): Boolean =
        file.isFile && file.length() > minimumBytes && file.canRead()

    private companion object {
        const val LEGACY_OPT_NAME = "linux" + "ai"
        const val MIN_PROOT_BYTES = 4096L
        const val MIN_PROOT_LOADER_BYTES = 4096L
        const val MIN_TALLOC_BYTES = 4096L
    }
}
