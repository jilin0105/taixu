package top.wkbin.taixu.runtime.build

import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.ProjectType
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.runtime.bridge.adb.EmbeddedAdbManager
import top.wkbin.taixu.runtime.shell.ShellCommand
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn

data class StepDuration(
    val step: String,
    val durationMs: Long,
)

data class BuildRunProgress(
    val step: String,
    val progress: Float = 0f,
    val isRunning: Boolean = true,
    val isSuccess: Boolean? = null,
    val message: String? = null,
    val apkPath: String? = null,
    val logOutput: String = "",
    val suggestedSuiteId: String? = null,
    val stepDurations: List<StepDuration> = emptyList(),
    val totalDurationMs: Long? = null,
)

private const val MAX_LOG_CHARS = 60_000 // 日志上限60KB，超限丢弃旧行

/**
 * 工作区项目一键构建并安装运行到手机服务。
 */
@Singleton
class WorkspaceBuildRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val linuxRuntime: LinuxRuntime,
    private val embeddedAdbManager: EmbeddedAdbManager,
    private val assetSynchronizer: top.wkbin.taixu.runtime.scripts.RuntimeAssetSynchronizer,
    private val logger: AppLogger,
) {
    fun launchPackageInstaller(apkFile: File): Boolean {
        return runCatching {
            check(apkFile.isFile && apkFile.length() > 0L) { "APK 文件不存在或为空" }
            val stagedApk = stageApkForInstall(apkFile)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                stagedApk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                clipData = ClipData.newRawUri("APK", uri)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun stageApkForInstall(apkFile: File): File {
        val dir = File(context.cacheDir, "workspace-apk-installs").apply { mkdirs() }
        val now = System.currentTimeMillis()
        dir.listFiles()
            .orEmpty()
            .filter { it.isFile && now - it.lastModified() > 24 * 60 * 60 * 1000L }
            .forEach { it.delete() }
        val staged = File(dir, "${apkFile.nameWithoutExtension}-$now-${apkFile.length()}.apk")
        apkFile.inputStream().use { input -> staged.outputStream().use { output -> input.copyTo(output) } }
        check(staged.isFile && staged.length() == apkFile.length()) { "APK 临时副本不完整" }
        return staged
    }

    fun runProject(project: WorkspaceProject): Flow<BuildRunProgress> = channelFlow {
        // 确保每次构建前，沙箱内部的 Shell 资产脚本永远最新且无 BOM 污染
        runCatching {
            assetSynchronizer.syncAssetsToDistro(linuxRuntime.activeDistroId.value)
        }

        // channelFlow 的 Channel 保证多线程 send 的线程安全：
        // 构建输出回调运行在 ProcessShellExecutor 的 stdout/stderr 读取协程中，
        // 必须用 trySend 跨线程投递进度，禁止在回调里直接 emit。
        val progressChannel = this

        // 日志缓冲机制：批量flush + 旧日志丢弃，避免海量日志导致 Compose 卡顿
        val logs = StringBuilder()
        val logBuffer = mutableListOf<String>()
        var lastLogFlush = System.currentTimeMillis()
        fun flushLogBuffer() {
            if (logBuffer.isEmpty()) return
            for (line in logBuffer) logs.appendLine(line)
            logBuffer.clear()
            // 超限丢弃头部旧日志，只保留最近内容
            if (logs.length > MAX_LOG_CHARS) {
                val excess = logs.length - MAX_LOG_CHARS
                val cutIdx = logs.indexOf("\n", excess).let { if (it == -1) excess else it + 1 }
                logs.delete(0, cutIdx)
                logs.insert(0, "[...前面日志已丢弃...]\n")
            }
        }
        fun log(msg: String) {
            logBuffer.add(msg)
            val now = System.currentTimeMillis()
            // 缓冲区满 或 超过 400ms 未刷：批量写入并丢弃超限旧日志
            if (logBuffer.size >= 60 || now - lastLogFlush > 400) {
                flushLogBuffer()
                lastLogFlush = now
            }
        }

        log("[TaiXu Build Engine] 开始分析工程: ${project.name} (${project.projectType.displayName})")
        send(BuildRunProgress(step = "正在分析项目环境...", progress = 0.1f, logOutput = logs.toString()))

        when (project.projectType) {
            ProjectType.ANDROID -> {
                log("[TaiXu Build] Linux 路径: ${project.linuxPath}")
                send(BuildRunProgress(step = "正在预检 Android 构建环境...", progress = 0.15f, logOutput = logs.toString()))

                // 1. 预检 Android 构建工具链 (Java / Gradle)
                val probeJava = linuxRuntime.execute(ShellCommand(commandLine = "command -v java || test -x /usr/lib/jvm/java-17-openjdk-arm64/bin/java || test -d /usr/lib/jvm", timeoutMs = 5000L))
                val probeGradle = linuxRuntime.execute(ShellCommand(commandLine = "command -v gradle || test -x /opt/gradle-8.9/bin/gradle || test -x /usr/local/bin/gradle || test -f ${project.linuxPath}/gradlew || test -d /opt/gradle-8.9", timeoutMs = 5000L))
                if (!probeJava.isSuccess && !probeGradle.isSuccess) {
                    log("[TaiXu Build] ⚠️ 未检测到 Android 构建环境 (OpenJDK 17 / Gradle 8.9)")
                    log("[TaiXu Build] 💡 提示：请先在【插件与工具中心】中装配【Android & 移动全栈开发套件】")
                    send(
                        BuildRunProgress(
                            step = "缺少 Android 构建环境",
                            isRunning = false,
                            isSuccess = false,
                            message = "未检测到 Android 构建环境 (Gradle / OpenJDK 17)，请点击下方按钮一键安装开发环境套件包。",
                            logOutput = logs.toString(),
                            suggestedSuiteId = "android-suite",
                        )
                    )
                    return@channelFlow
                }

                log("[TaiXu Build] 执行 Gradle 编译 (assembleDebug)...")
                send(BuildRunProgress(step = "正在执行 Gradle 编译 (assembleDebug)...", progress = 0.3f, logOutput = logs.toString()))

                // 构建阶段时长追踪
                val buildStartTime = System.currentTimeMillis()
                val stepHistory = mutableListOf<StepDuration>()
                var lastStepTime = buildStartTime
                var previousStep = "正在执行 Gradle 构建任务..."
                fun recordStepDuration(newStep: String) {
                    val now = System.currentTimeMillis()
                    val duration = now - lastStepTime
                    if (duration > 100) {
                        stepHistory.add(StepDuration(previousStep, duration))
                    }
                    previousStep = newStep
                    lastStepTime = now
                }

                val buildCmd = "/bin/sh /opt/taixu/scripts/build_android.sh \"${project.linuxPath}\" assembleDebug"
                var lastEmitTime = System.currentTimeMillis()
                var currentStep = "正在执行 Gradle 构建任务..."
                var currentProgress = 0.35f

                val outcome = linuxRuntime.execute(
                    ShellCommand(
                        commandLine = buildCmd,
                        timeoutMs = 1800_000L, // 30 分钟充足超时，适配移动端首次下载海量依赖
                        onOutput = { chunk ->
                            log(chunk.trimEnd())
                            val lower = chunk.lowercase()
                            when {
                                lower.contains("downloading") || lower.contains("get ") || lower.contains("fetching") -> {
                                    if (currentStep != "正在拉取依赖资源库...") recordStepDuration("正在拉取依赖资源库...")
                                    currentStep = "正在拉取依赖资源库..."
                                    currentProgress = 0.4f
                                }
                                chunk.contains(":compileDebugKotlin") -> {
                                    if (currentStep != "正在编译 Kotlin / Compose 源码...") recordStepDuration("正在编译 Kotlin / Compose 源码...")
                                    currentStep = "正在编译 Kotlin / Compose 源码..."
                                    currentProgress = 0.55f
                                }
                                chunk.contains(":compileDebugJavaWithJavac") -> {
                                    if (currentStep != "正在编译 Java 源码...") recordStepDuration("正在编译 Java 源码...")
                                    currentStep = "正在编译 Java 源码..."
                                    currentProgress = 0.65f
                                }
                                chunk.contains(":dexBuilderDebug") || chunk.contains(":mergeExtDexDebug") || chunk.contains(":mergeLibDexDebug") -> {
                                    if (currentStep != "正在进行 Dex 字节码转换与优化...") recordStepDuration("正在进行 Dex 字节码转换与优化...")
                                    currentStep = "正在进行 Dex 字节码转换与优化..."
                                    currentProgress = 0.75f
                                }
                                chunk.contains(":packageDebug") -> {
                                    if (currentStep != "正在打包生成 APK...") recordStepDuration("正在打包生成 APK...")
                                    currentStep = "正在打包生成 APK..."
                                    currentProgress = 0.85f
                                }
                            }
                            // 限制最快 100ms 刷新一次 UI，避免海量日志高频触发 Compose 重组卡顿
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime > 100) {
                                lastEmitTime = now
                                progressChannel.trySend(
                                    BuildRunProgress(step = currentStep, progress = currentProgress, logOutput = logs.toString())
                                )
                            }
                        },
                    ),
                )

                if (!outcome.isSuccess) {
                    // The output callback is buffered; flush it before publishing
                    // failure so the dialog contains the actual Gradle error,
                    // rather than only the final daemon/cache lines.
                    flushLogBuffer()
                    val rawFailureLog = (outcome.stderr + "\n" + outcome.stdout).trim()
                    val diagnostic = rawFailureLog.lineSequence()
                        .filter { line ->
                            val lower = line.lowercase()
                            lower.contains("what went wrong") ||
                                lower.contains("execution failed") ||
                                lower.contains("error:") ||
                                lower.contains("failed with an exception") ||
                                lower.contains("could not")
                        }
                        .take(8)
                        .joinToString("\n")
                    val errLog = (diagnostic.ifBlank { rawFailureLog.takeLast(1600) }).takeLast(1600)
                    log("[TaiXu Build] ❌ Gradle 构建失败，Exit Code: ${outcome.exitCode}")
                    recordStepDuration("编译失败")
                    send(
                        BuildRunProgress(
                            step = "编译失败",
                            isRunning = false,
                            isSuccess = false,
                            message = errLog.ifBlank { "Gradle 构建失败 (exit code ${outcome.exitCode})" },
                            logOutput = logs.toString(),
                            stepDurations = stepHistory.toList(),
                            totalDurationMs = System.currentTimeMillis() - buildStartTime,
                        )
                    )
                    return@channelFlow
                }

                log("[TaiXu Build] ✅ Gradle 编译完成，耗时: ${outcome.durationMs}ms")
                log("[TaiXu Build] 检索 APK 产物...")
                send(BuildRunProgress(step = "编译成功，正在检索 APK 产物...", progress = 0.9f, logOutput = logs.toString()))

                val apkDir = File(project.path, "app/build/outputs/apk/debug")
                val candidateApk = if (apkDir.isDirectory) {
                    apkDir.listFiles()?.firstOrNull { it.extension.equals("apk", ignoreCase = true) }
                } else null
                val apkFile = candidateApk ?: File(project.path).walkTopDown().firstOrNull { it.isFile && it.extension.equals("apk", ignoreCase = true) && !it.name.contains("unaligned") }

                if (apkFile == null || !apkFile.exists()) {
                    log("[TaiXu Build] ❌ 未在 outputs 目录找到 APK 产物")
                    send(
                        BuildRunProgress(
                            step = "未找到生成的 APK 产物",
                            isRunning = false,
                            isSuccess = false,
                            message = "构建完成但未在 outputs 目录找到 APK",
                            logOutput = logs.toString(),
                        )
                    )
                    return@channelFlow
                }

                log("[TaiXu Build] 找到 APK: ${apkFile.absolutePath} (${apkFile.length() / 1024} KB)")

                // 导出到手机公共存储 Download 目录
                send(BuildRunProgress(step = "正在导出 APK 到手机下载目录...", progress = 0.93f, logOutput = logs.toString()))
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetApk = File(downloadDir, "${project.name}.apk")
                copyApkAtomically(apkFile, targetApk)
                log("[TaiXu Build] APK 已成功导出至: ${targetApk.absolutePath}")

                // 多通道安装调度：1. 无线 ADB 直装；2. 调起系统原生 PackageInstaller
                send(BuildRunProgress(step = "正在安装到手机...", progress = 0.97f, logOutput = logs.toString()))
                var installNotice = "APK 已导出至手机 Download/${targetApk.name}"
                val adbInstallResult = runCatching { embeddedAdbManager.installApk(targetApk) }
                if (adbInstallResult.isSuccess) {
                    log("[TaiXu Build] ✅ 通过内置 ADB 成功直装到手机！")
                    installNotice = "已通过内置 ADB 成功直装到手机！"
                    if (project.packageName.isNotBlank()) {
                        log("[TaiXu Build] 启动应用: ${project.packageName} ...")
                        embeddedAdbManager.executeShell("monkey -p ${project.packageName} -c android.intent.category.LAUNCHER 1")
                    }
                } else {
                    log("[TaiXu Build] 自动调起系统应用安装器 (PackageInstaller)...")
                    val installerLaunched = launchPackageInstaller(targetApk)
                    if (installerLaunched) {
                        installNotice = "已自动调起系统安装器，请在弹窗中点击【安装】"
                    }
                }

                flushLogBuffer()
                recordStepDuration("运行就绪")
                send(
                    BuildRunProgress(
                        step = "运行就绪",
                        progress = 1.0f,
                        isRunning = false,
                        isSuccess = true,
                        message = installNotice,
                        apkPath = targetApk.absolutePath,
                        logOutput = logs.toString(),
                        stepDurations = stepHistory.toList(),
                        totalDurationMs = System.currentTimeMillis() - buildStartTime,
                    )
                )
            }
            ProjectType.FLUTTER -> {
                log("[TaiXu Build] Flutter 跨平台编译，环境: PUB_HOSTED_URL=https://pub.flutter-io.cn")
                send(BuildRunProgress(step = "正在预检 Flutter 跨端开发环境...", progress = 0.15f, logOutput = logs.toString()))

                // 1. 预检 Flutter SDK
                val probeFlutter = linuxRuntime.execute(ShellCommand(commandLine = "command -v flutter || test -x /opt/flutter/bin/flutter || test -d /opt/flutter", timeoutMs = 5000L))
                if (!probeFlutter.isSuccess) {
                    log("[TaiXu Build] ⚠️ 未检测到 Flutter SDK 环境")
                    log("[TaiXu Build] 💡 提示：请先在【插件与工具中心】中装配【Android & 移动全栈开发套件 (含 Flutter)】")
                    send(
                        BuildRunProgress(
                            step = "缺少 Flutter 构建环境",
                            isRunning = false,
                            isSuccess = false,
                            message = "未检测到 Flutter SDK 环境，请点击下方按钮一键安装开发套件。",
                            logOutput = logs.toString(),
                            suggestedSuiteId = "flutter-suite",
                        )
                    )
                    return@channelFlow
                }

                log("[TaiXu Build] 执行 Flutter APK 构建...")
                send(BuildRunProgress(step = "正在执行 Flutter 构建 (flutter build apk)...", progress = 0.3f, logOutput = logs.toString()))

                val buildCmd = "/bin/sh /opt/taixu/scripts/build_flutter.sh \"${project.linuxPath}\" \"apk --debug\""
                var lastEmitTime = System.currentTimeMillis()

                val outcome = linuxRuntime.execute(
                    ShellCommand(
                        commandLine = buildCmd,
                        timeoutMs = 1800_000L,
                        onOutput = { chunk ->
                            log(chunk.trimEnd())
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime > 100) {
                                lastEmitTime = now
                                progressChannel.trySend(
                                    BuildRunProgress(step = "正在执行 Flutter 构建...", progress = 0.5f, logOutput = logs.toString())
                                )
                            }
                        },
                    ),
                )

                if (!outcome.isSuccess) {
                    flushLogBuffer()
                    val rawFailureLog = (outcome.stderr + "\n" + outcome.stdout).trim()
                    val diagnostic = rawFailureLog.lineSequence()
                        .filter { line ->
                            val lower = line.lowercase()
                            lower.contains("what went wrong") ||
                                lower.contains("execution failed") ||
                                lower.contains("error:") ||
                                lower.contains("failed with an exception") ||
                                lower.contains("could not")
                        }
                        .take(8)
                        .joinToString("\n")
                    val errLog = (diagnostic.ifBlank { rawFailureLog.takeLast(1600) }).takeLast(1600)
                    log("[TaiXu Build] ❌ Flutter 构建失败，Exit Code: ${outcome.exitCode}")
                    flushLogBuffer()
                    send(
                        BuildRunProgress(
                            step = "Flutter 编译失败",
                            isRunning = false,
                            isSuccess = false,
                            message = errLog.ifBlank { "Flutter 构建失败 (exit code ${outcome.exitCode})" },
                            logOutput = logs.toString(),
                            totalDurationMs = outcome.durationMs,
                        )
                    )
                    return@channelFlow
                }

                log("[TaiXu Build] ✅ Flutter 编译完成，耗时: ${outcome.durationMs}ms")
                send(BuildRunProgress(step = "编译成功，正在导出 APK...", progress = 0.8f, logOutput = logs.toString()))

                val apkDir = File(project.path, "build/app/outputs/flutter-apk")
                val candidateApk = if (apkDir.isDirectory) {
                    apkDir.listFiles()?.firstOrNull { it.extension.equals("apk", ignoreCase = true) }
                } else null
                val apkFile = candidateApk ?: File(project.path).walkTopDown().firstOrNull { it.isFile && it.extension.equals("apk", ignoreCase = true) }

                if (apkFile == null || !apkFile.exists()) {
                    log("[TaiXu Build] ❌ 未在 outputs 目录找到 Flutter APK 产物")
                    send(
                        BuildRunProgress(
                            step = "未找到生成的 Flutter APK 产物",
                            isRunning = false,
                            isSuccess = false,
                            message = "构建完成但未在 outputs 目录找到 APK",
                            logOutput = logs.toString(),
                        )
                    )
                    return@channelFlow
                }

                log("[TaiXu Build] 找到 Flutter APK: ${apkFile.absolutePath}")
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetApk = File(downloadDir, "${project.name}.apk")
                copyApkAtomically(apkFile, targetApk)
                log("[TaiXu Build] Flutter APK 已导出至: ${targetApk.absolutePath}")

                send(BuildRunProgress(step = "正在安装到手机...", progress = 0.95f, logOutput = logs.toString()))
                var installNotice = "Flutter APK 已导出至手机 Download/${targetApk.name}"
                val adbInstallResult = runCatching { embeddedAdbManager.installApk(targetApk) }
                if (adbInstallResult.isSuccess) {
                    log("[TaiXu Build] ✅ 通过内置 ADB 成功直装 Flutter App！")
                    installNotice = "已通过内置 ADB 成功直装 Flutter App 到手机！"
                } else {
                    log("[TaiXu Build] 自动调起系统应用安装器 (PackageInstaller)...")
                    val installerLaunched = launchPackageInstaller(targetApk)
                    if (installerLaunched) {
                        installNotice = "已自动调起系统安装器，请在弹窗中点击【安装】"
                    }
                }

                flushLogBuffer()
                send(
                    BuildRunProgress(
                        step = "运行就绪",
                        progress = 1.0f,
                        isRunning = false,
                        isSuccess = true,
                        message = installNotice,
                        apkPath = targetApk.absolutePath,
                        logOutput = logs.toString(),
                        totalDurationMs = outcome.durationMs,
                    )
                )
            }
            ProjectType.REVERSE -> {
                log("[TaiXu Build] APK 逆向工程，无编译流程；直接提供 jadx / apktool 分析指引")
                send(
                    BuildRunProgress(
                        step = "APK 逆向工程",
                        isRunning = false,
                        isSuccess = true,
                        message = "逆向工程无需编译。打开专属终端或对话 Agent，使用 jadx / apktool 对工程内 APK 进行解包反编译（详见工程内 REVERSE.md）",
                        logOutput = logs.toString(),
                    )
                )
            }
            ProjectType.GENERAL -> {
                log("[TaiXu Build] 通用工程，无默认 APK 打包流程")
                send(
                    BuildRunProgress(
                        step = "通用工程",
                        isRunning = false,
                        isSuccess = true,
                        message = "通用工程请在太墟终端中执行自定义命令或自定义构建脚本",
                        logOutput = logs.toString(),
                    )
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Write a complete APK before replacing the public download target. */
    private fun copyApkAtomically(source: File, target: File) {
        check(source.isFile && source.length() > 0L) { "APK 源文件不存在或为空：${source.absolutePath}" }
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.${System.nanoTime()}.part")
        try {
            source.inputStream().use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            check(temporary.length() == source.length()) {
                "APK 导出不完整：${temporary.length()} / ${source.length()} 字节"
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                check(target.length() == source.length()) { "APK 目标文件校验失败" }
                temporary.delete()
            }
            check(target.isFile && target.length() == source.length()) { "APK 导出目标无效" }
        } finally {
            temporary.delete()
        }
    }
}
