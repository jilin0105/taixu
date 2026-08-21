package top.wkbin.taixu.runtime

import android.content.Context
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
    val rootfsDir: File get() {
        val installed = listInstalledDistroIds().firstOrNull() ?: "ubuntu"
        return rootfsDir(installed)
    }
    val stagingRootfsDir: File = File(baseDir, "rootfs.staging")
    val homeDir: File get() {
        val installed = listInstalledDistroIds().firstOrNull() ?: "ubuntu"
        return homeDir(installed)
    }
    val optDir: File = File(baseDir, "opt")
    val workspaceDir: File = File(baseDir, "workspace")
    val cacheDir: File = File(baseDir, "cache")
    val tmpDir: File = File(baseDir, "tmp")
    val logsDir: File = File(baseDir, "logs")
    val metadataDir: File = File(baseDir, "metadata")

    // ==================== 插件目录（按发行版隔离） ====================
    // v17 起插件本体/数据/命令与依赖 Runtime 均随发行版走：
    // linux-runtime/distros/<id>/opt/taixu/{tools,data,bin,runtimes,...}

    fun distroOptDir(distroId: String): File = File(distroDir(distroId), "opt")

    fun taixuRootDir(distroId: String): File = File(distroOptDir(distroId), "taixu")

    fun taixuRuntimesDir(distroId: String): File = File(taixuRootDir(distroId), "runtimes")

    fun taixuToolsDir(distroId: String): File = File(taixuRootDir(distroId), "tools")

    fun taixuDataDir(distroId: String): File = File(taixuRootDir(distroId), "data")

    fun taixuBinDir(distroId: String): File = File(taixuRootDir(distroId), "bin")

    fun ensureDistroDirectories(distroId: String) {
        val safeId = distroId.lowercase().trim()
        listOf(
            taixuRootDir(safeId),
            taixuRuntimesDir(safeId),
            taixuToolsDir(safeId),
            taixuDataDir(safeId),
            taixuBinDir(safeId),
        ).forEach { it.mkdirs() }
    }
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
            workspaceDir,
            cacheDir,
            tmpDir,
            logsDir,
            metadataDir,
        ).forEach { it.mkdirs() }
        listInstalledDistroIds().forEach(::ensureDistroDirectories)
    }

    fun cleanupStalePtyMarkers(distroId: String = "ubuntu") {
        val safeId = distroId.lowercase().trim()
        taixuRootDir(safeId)
            .listFiles()
            .orEmpty()
            .filter { it.name.startsWith(".pty-") }
            .forEach { it.delete() }
    }

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

    fun isRootfsInstalled(): Boolean = listInstalledDistroIds().isNotEmpty()

    private fun isUsableProot(file: File): Boolean =
        file.isFile && file.length() > MIN_PROOT_BYTES && (file.canExecute() || file == bundledProotFile)

    private fun isUsableNativeArtifact(file: File, minimumBytes: Long): Boolean =
        file.isFile && file.length() > minimumBytes && file.canRead()

    private companion object {
        const val MIN_PROOT_BYTES = 4096L
        const val MIN_PROOT_LOADER_BYTES = 4096L
        const val MIN_TALLOC_BYTES = 4096L
    }
}
