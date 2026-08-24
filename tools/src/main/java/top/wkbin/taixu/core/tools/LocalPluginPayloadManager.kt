package top.wkbin.taixu.core.tools

import top.wkbin.taixu.runtime.RuntimePathManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Copies an imported plugin payload into the distro-owned /opt/taixu tree. */
@Singleton
class LocalPluginPayloadManager @Inject constructor(
    private val registry: ToolRegistry,
    private val pathManager: RuntimePathManager,
) {
    suspend fun prepare(toolId: String, distroId: String): String? = withContext(Dispatchers.IO) {
        val source = registry.localPayloadRoot(toolId)?.takeIf { it.isDirectory } ?: return@withContext null
        val importsRoot = File(pathManager.taixuRootDir(distroId), "imports").apply { mkdirs() }
        val target = File(importsRoot, toolId)
        val canonicalRoot = importsRoot.canonicalFile
        require(target.canonicalFile.toPath().startsWith(canonicalRoot.toPath())) { "非法插件 ID：$toolId" }
        target.deleteRecursively()
        source.copyRecursively(target, overwrite = true)
        target.walkTopDown().forEach { file ->
            file.setReadable(true, false)
            if (file.isFile && file.extension.equals("sh", ignoreCase = true)) {
                normalizeShellScript(file)
            }
            if (file.isFile && (file.extension.equals("sh", ignoreCase = true) || "/bin/" in file.invariantSeparatorsPath)) {
                file.setExecutable(true, false)
            }
        }
        "/opt/taixu/imports/$toolId"
    }

    /** Windows-created ZIPs commonly carry BOM/CRLF and no executable bit. */
    private fun normalizeShellScript(file: File) {
        val original = file.readBytes()
        val withoutBom = if (
            original.size >= 3 &&
            original[0] == 0xEF.toByte() &&
            original[1] == 0xBB.toByte() &&
            original[2] == 0xBF.toByte()
        ) {
            original.copyOfRange(3, original.size)
        } else {
            original
        }
        var text = withoutBom.toString(Charsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        if (!text.startsWith("#!")) text = "#!/bin/sh\n$text"
        file.writeText(text, Charsets.UTF_8)
    }
}
