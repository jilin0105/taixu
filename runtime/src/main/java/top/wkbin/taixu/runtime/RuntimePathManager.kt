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
    val rootfsDir: File = File(baseDir, "rootfs")
    val stagingRootfsDir: File = File(baseDir, "rootfs.staging")
    val homeDir: File = File(baseDir, "home")
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
            rootfsDir,
            homeDir,
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
            File(rootfsDir, ".l2s"),
        ).forEach { it.mkdirs() }
    }

    fun cleanupStalePtyMarkers() {
        taixuToolsDir.parentFile
            ?.listFiles()
            .orEmpty()
            .filter { it.name.startsWith(".pty-") }
            .forEach { it.delete() }
    }

    /** Move legacy in-rootfs user data to the persistent app-private bind mounts once. */
    fun migratePersistentDirectories() {
        ensureDirectories()
        migrateMissingPersistentFiles(File(rootfsDir, "root"), homeDir)
        // Import data created before the project adopted the TaiXu name.
        migrateMissingPersistentFiles(File(optDir, LEGACY_OPT_NAME), taixuRootDir)
        migrateMissingPersistentFiles(File(rootfsDir, "opt/$LEGACY_OPT_NAME"), taixuRootDir)
        migrateMissingPersistentFiles(File(rootfsDir, "opt/taixu"), taixuRootDir)
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
    fun hostProcessEnvironment(): Map<String, String> {
        ensureDirectories()
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
            put("PROOT_L2S_DIR", File(rootfsDir, ".l2s").absolutePath)
            if (isUsableNativeArtifact(bundledProotLoaderFile, MIN_PROOT_LOADER_BYTES)) {
                // The Termux PRoot build deliberately keeps its tracee loader
                // outside the main binary. Without this override it searches
                // /data/data/com.termux/files/usr/libexec/proot/loader.
                put("PROOT_LOADER", bundledProotLoaderFile.absolutePath)
            }
            if (isUsableNativeArtifact(bundledProotLoader32File, MIN_PROOT_LOADER_BYTES)) {
                put("PROOT_LOADER_32", bundledProotLoader32File.absolutePath)
            }
            // Do not inherit Termux's TMPDIR when this app runs standalone.
            // PRoot uses it before entering the guest rootfs for its probes.
            put("TMPDIR", tmpDir.absolutePath)
            put("TMP", tmpDir.absolutePath)
            put("TEMP", tmpDir.absolutePath)
            // Termux PRoot uses this variable for its own host-side probes.
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
        rootfsInstalledMarker().isFile &&
            rootfsValidator.isValid(rootfsDir)

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
