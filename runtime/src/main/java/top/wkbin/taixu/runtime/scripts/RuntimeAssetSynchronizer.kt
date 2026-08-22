package top.wkbin.taixu.runtime.scripts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import top.wkbin.taixu.runtime.RuntimePathManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🛠️ 太墟资产脚本同步器 (Runtime Asset Synchronizer)
 * 将 APK 内置 assets/scripts/ 下的标准化 Shell 脚本与 tools 自动提取并同步到
 * Linux 沙箱隔离目录 (/opt/taixu/scripts/ 与 /opt/taixu/tools/)，并赋予执行权限。
 */
@Singleton
class RuntimeAssetSynchronizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pathManager: RuntimePathManager,
) {
    /**
     * 同步指定发行版沙箱内的脚本与资产工具
     */
    suspend fun syncAssetsToDistro(distroId: String) = withContext(Dispatchers.IO) {
        val safeDistro = distroId.lowercase().trim()
        pathManager.ensureDistroDirectories(safeDistro)

        val scriptsTargetDir = pathManager.taixuScriptsDir(safeDistro)
        val toolsTargetDir = pathManager.taixuToolsDir(safeDistro)

        // 1. 同步 assets/scripts/ -> /opt/taixu/scripts/
        syncAssetFolder("scripts", scriptsTargetDir)

        // 2. 同步 assets/tools/ -> /opt/taixu/tools/
        syncAssetFolder("tools", toolsTargetDir)
    }

    private fun syncAssetFolder(assetSubDir: String, targetDir: File) {
        targetDir.mkdirs()
        val assetList = runCatching { context.assets.list(assetSubDir) }.getOrNull().orEmpty()
        for (filename in assetList) {
            val assetPath = "$assetSubDir/$filename"
            val targetFile = File(targetDir, filename)
            runCatching {
                val content = context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
                // 彻底剥离 UTF-8 BOM (\uFEFF) 并规范换行符为 Unix LF
                val cleanContent = content.removePrefix("\uFEFF").replace("\r\n", "\n")
                targetFile.writeText(cleanContent, Charsets.UTF_8)
                targetFile.setExecutable(true, false)
                targetFile.setReadable(true, false)
            }
        }
    }
}