package top.wkbin.taixu.runtime.privilege

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.common.result.AppError
import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.core.common.result.ErrorCode
import top.wkbin.taixu.core.datastore.RuntimePreferences
import top.wkbin.taixu.core.model.ExecutionMode
import top.wkbin.taixu.core.model.PrivilegeCheckResult
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivilegeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: RuntimePreferences,
    private val logger: AppLogger,
) {

    /**
     * 探测并尝试获取目标运行模式的特权授权。
     */
    suspend fun checkAndAuthorize(mode: ExecutionMode): PrivilegeCheckResult = withContext(Dispatchers.IO) {
        when (mode) {
            ExecutionMode.PROOT -> {
                PrivilegeCheckResult.Authorized(
                    mode = ExecutionMode.PROOT,
                    details = "PRoot 用户态沙箱已就绪，无需额外系统特权。",
                )
            }

            ExecutionMode.ROOT -> {
                checkRootPrivilege()
            }

            ExecutionMode.SHIZUKU -> {
                checkShizukuPrivilege()
            }
        }
    }

    /**
     * 申请并切换到指定的运行模式。如果授权成功，自动持久化并释放特权能力。
     */
    suspend fun switchMode(mode: ExecutionMode): AppResult<PrivilegeCheckResult.Authorized> = withContext(Dispatchers.IO) {
        val check = checkAndAuthorize(mode)
        when (check) {
            is PrivilegeCheckResult.Authorized -> {
                settingsDataStore.setExecutionMode(mode)
                applyPrivilegeOptimizations(mode)
                logger.i("已成功切换至运行模式: ${mode.name} (${check.details})")
                AppResult.Success(check)
            }

            is PrivilegeCheckResult.Unauthorized -> {
                logger.w("切换至 ${mode.name} 失败: ${check.reason}")
                AppResult.Failure(
                    AppError(
                        code = ErrorCode.SECURITY,
                        message = check.reason,
                    ),
                )
            }

            is PrivilegeCheckResult.ServiceNotRunning -> {
                logger.w("切换至 ${mode.name} 失败: ${check.guidance}")
                AppResult.Failure(
                    AppError(
                        code = ErrorCode.UNKNOWN,
                        message = check.guidance,
                    ),
                )
            }
        }
    }

    /**
     * 探测 Root 权限（通过 su 执行流测试 UID 0）
     */
    private fun checkRootPrivilege(): PrivilegeCheckResult {
        return try {
            val process = ProcessBuilder("su", "-c", "id").start()
            val completed = process.waitFor(5, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return PrivilegeCheckResult.Unauthorized(
                    ExecutionMode.ROOT,
                    "请求 Root 授权超时，请在 Magisk / KernelSU / APatch 弹窗中允许授权。",
                )
            }

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            if (exitCode == 0 && output.contains("uid=0")) {
                PrivilegeCheckResult.Authorized(
                    ExecutionMode.ROOT,
                    "已获得 Root 权限 (UID 0: root)，已释放原生 Linux 与内核硬件加速能力！",
                )
            } else {
                PrivilegeCheckResult.Unauthorized(
                    ExecutionMode.ROOT,
                    "Root 授权未通过 (exit $exitCode): $output",
                )
            }
        } catch (e: Exception) {
            logger.e("检查 Root 权限发生异常", e)
            PrivilegeCheckResult.ServiceNotRunning(
                ExecutionMode.ROOT,
                "未在设备上检测到可用的 su 可执行程序。若已 Root，请检查是否在授权管理器中对太墟开启了授权。",
            )
        }
    }

    /**
     * 使用官方 Shizuku-API 进行 Binder 服务探测与权限检查
     */
    private fun checkShizukuPrivilege(): PrivilegeCheckResult {
        return try {
            // 1. 探测 Shizuku Binder 服务是否处于运行激活状态
            val isBinderAlive = Shizuku.pingBinder()
            if (!isBinderAlive) {
                return PrivilegeCheckResult.ServiceNotRunning(
                    ExecutionMode.SHIZUKU,
                    "Shizuku 服务未运行。请打开 Shizuku App 确保服务状态为“已运行”（可通过无线调试或 Root 启动）。",
                )
            }

            // 2. 检查应用是否已获得 Shizuku 权限
            val isGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

            if (isGranted) {
                PrivilegeCheckResult.Authorized(
                    ExecutionMode.SHIZUKU,
                    "Shizuku (v${Shizuku.getVersion()}) 授权成功，已解锁 ADB 级别特权及 Android 12+ 进程上限豁免能力！",
                )
            } else {
                // 发起 Shizuku 授权申请
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    PrivilegeCheckResult.Unauthorized(
                        ExecutionMode.SHIZUKU,
                        "请在 Shizuku 弹窗中允许太墟访问 ADB 特权服务。",
                    )
                } else {
                    runCatching { Shizuku.requestPermission(1001) }
                    PrivilegeCheckResult.Unauthorized(
                        ExecutionMode.SHIZUKU,
                        "已发起 Shizuku 授权请求，请在弹出的系统对话框中点击“允许”后再次点击切换。",
                    )
                }
            }
        } catch (e: Exception) {
            logger.e("检查 Shizuku 发生异常", e)
            PrivilegeCheckResult.ServiceNotRunning(
                ExecutionMode.SHIZUKU,
                "无法连接到 Shizuku 服务 (${e.message})。请检查 Shizuku 是否正常运行。",
            )
        }
    }

    /**
     * 在授权成功后应用系统级特权优化（如解除 Android 12+ 幽灵进程 32 限制等）
     */
    private fun applyPrivilegeOptimizations(mode: ExecutionMode) {
        when (mode) {
            ExecutionMode.ROOT -> {
                runCatching { executeViaRoot(PHANTOM_PROCESS_REMOVE_COMMAND) }
                    .onSuccess { result ->
                        if (!result.success) logger.w("通过 Root 解除幽灵进程限制失败: ${result.stderr}")
                    }
                    .onFailure {
                        logger.w("通过 Root 解除幽灵进程限制失败", it)
                    }
            }
            ExecutionMode.SHIZUKU -> {
                runCatching { executeViaShizuku(PHANTOM_PROCESS_REMOVE_COMMAND) }
                    .onSuccess { result ->
                        if (!result.success) logger.w("通过 Shizuku 解除幽灵进程限制失败: ${result.stderr}")
                    }
                    .onFailure {
                        logger.w("通过 Shizuku 解除幽灵进程限制失败", it)
                    }
            }
            ExecutionMode.PROOT -> Unit
        }
    }

    /**
     * 读取 Android 幽灵进程监控的实际系统值，而不是依赖应用内的“已执行”标记。
     * Android 12 引入该限制；不同系统版本/厂商可能采用数量上限或监控开关中的任一项。
     */
    suspend fun checkPhantomProcessLimit(): PhantomProcessLimitStatus = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return@withContext PhantomProcessLimitStatus(
                state = PhantomProcessLimitState.UNSUPPORTED,
                details = "Android 12 以下系统没有幽灵进程 32 个上限。",
            )
        }

        // 先走应用进程可读的系统 API。这样即使用户通过电脑 ADB 执行命令、当前使用 PRoot，
        // 只要 ROM 允许读取对应配置，页面仍能自行识别已经解除的状态。
        val directMonitoring = runCatching {
            Settings.Global.getString(context.contentResolver, "settings_enable_monitor_phantom_procs")
        }.getOrNull()
        val directStatus = if (directMonitoring != null) {
            parsePhantomProcessLimit("max=\nmonitor=$directMonitoring\n")
        } else {
            null
        }
        if (directStatus?.state == PhantomProcessLimitState.REMOVED) {
            return@withContext directStatus
        }

        val result = executeShellCommand(PHANTOM_PROCESS_QUERY_COMMAND)
        if (!result.success) {
            if (directStatus != null) return@withContext directStatus
            return@withContext PhantomProcessLimitStatus(
                state = PhantomProcessLimitState.UNAVAILABLE,
                details = result.stderr.ifBlank { "需要先启用并授权 Shizuku 或 Root 才能读取系统状态。" },
            )
        }

        parsePhantomProcessLimit(result.stdout)
    }

    /** 使用当前 Shizuku/Root 宿主权限解除 Android 12+ 幽灵进程限制。 */
    suspend fun removePhantomProcessLimit(): ShellExecResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return@withContext ShellExecResult(true, 0, "", "")
        }
        executeShellCommand(PHANTOM_PROCESS_REMOVE_COMMAND)
    }

    // ============================ HostBridge 支持 ============================

    /** 读取当前持久化的运行模式；读取失败时回退 PRoot。 */
    private suspend fun currentMode(): ExecutionMode = runCatching { settingsDataStore.executionMode.first() }
        .getOrDefault(ExecutionMode.PROOT)

    /**
     * 在宿主侧以当前特权模式执行 Shell 命令。
     * - SHIZUKU 模式：通过 Shizuku Binder 以 ADB 级别 (shell uid) 执行
     * - ROOT 模式：通过 su 以 root uid 执行
     * - PROOT 模式：不支持，返回错误
     *
     * 这是打破"循环权限依赖"的关键能力：
     * 沙箱内无法直接执行需要 shell/root 权限的 Android 命令（如 settings put、pm grant、appops set），
     * 但通过 HostBridge → PrivilegeManager.executeShellCommand 可以绕过沙箱限制，
     * 在宿主侧以特权身份执行。
     */
    suspend fun executeShellCommand(command: String): ShellExecResult = withContext(Dispatchers.IO) {
        val mode = currentMode()

        when (mode) {
            ExecutionMode.SHIZUKU -> executeViaShizuku(command)
            ExecutionMode.ROOT -> executeViaRoot(command)
            ExecutionMode.PROOT -> ShellExecResult(
                success = false,
                exitCode = -1,
                stdout = "",
                stderr = "当前运行模式 (PRoot) 不支持宿主 Shell 执行。请在设置中切换到 Shizuku 或 Root 模式。",
            )
        }
    }

    /**
     * 获取当前特权状态摘要（不执行命令，仅探测可用性）。
     */
    suspend fun getPrivilegeInfo(): PrivilegeInfo = withContext(Dispatchers.IO) {
        val shizukuAvailable = runCatching {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        val rootAvailable = runCatching {
            val process = ProcessBuilder("su", "-c", "echo ok").start()
            val completed = process.waitFor(3, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
        }.getOrDefault(false)

        val mode = runCatching { settingsDataStore.executionMode.first() }
            .getOrDefault(ExecutionMode.PROOT)

        PrivilegeInfo(
            mode = mode.id,
            shizukuAvailable = shizukuAvailable,
            rootAvailable = rootAvailable,
        )
    }

    /**
     * 通过 Shizuku 以 ADB 级别 (shell uid, UID 2000) 执行命令。
     */
    private fun executeViaShizuku(command: String): ShellExecResult {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            return ShellExecResult(false, -1, "", "Shizuku 服务未运行。请打开 Shizuku App 并确保服务已启动。")
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return ShellExecResult(false, -1, "", "Shizuku 未授权。请在 Shizuku App 中授予太墟访问权限。")
        }

        return try {
            // Shizuku.newProcess(cmd[], env[], dir) — 反射调用
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(
                null,
                arrayOf("/system/bin/sh", "-c", command),
                null,
                null,
            ) as Process

            val completed = process.waitFor(30, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return ShellExecResult(false, -1, "", "命令执行超时 (30s)")
            }

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()
            ShellExecResult(exitCode == 0, exitCode, stdout, stderr)
        } catch (e: Exception) {
            logger.e("Shizuku shell execution failed", e)
            ShellExecResult(false, -1, "", "Shizuku 执行失败: ${e.message}")
        }
    }

    /**
     * 通过 su 以 root uid (UID 0) 执行命令。
     */
    private fun executeViaRoot(command: String): ShellExecResult {
        return try {
            val process = ProcessBuilder("su", "-c", command).start()
            val completed = process.waitFor(30, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return ShellExecResult(false, -1, "", "命令执行超时 (30s)")
            }

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.exitValue()
            ShellExecResult(exitCode == 0, exitCode, stdout, stderr)
        } catch (e: Exception) {
            logger.e("Root shell execution failed", e)
            ShellExecResult(false, -1, "", "Root 执行失败: ${e.message}")
        }
    }

    companion object {
        /** 可在电脑终端直接执行，适用于未使用 Shizuku/Root 的设备。 */
        const val PHANTOM_PROCESS_ADB_COMMAND =
            "adb shell device_config put activity_manager max_phantom_processes 2147483647\n" +
                "adb shell settings put global settings_enable_monitor_phantom_procs false"

        private const val PHANTOM_PROCESS_REMOVE_COMMAND =
            "/system/bin/device_config put activity_manager max_phantom_processes 2147483647; MAX_EXIT=\$?; " +
                "/system/bin/settings put global settings_enable_monitor_phantom_procs false; MONITOR_EXIT=\$?; " +
                "if [ \"\$MAX_EXIT\" -eq 0 ] || [ \"\$MONITOR_EXIT\" -eq 0 ]; then exit 0; else exit 1; fi"

        private const val PHANTOM_PROCESS_QUERY_COMMAND =
            "MAX=\$(/system/bin/device_config get activity_manager max_phantom_processes 2>/dev/null); " +
                "MONITOR=\$(/system/bin/settings get global settings_enable_monitor_phantom_procs 2>/dev/null); " +
                "printf 'max=%s\\nmonitor=%s\\n' \"\$MAX\" \"\$MONITOR\""
    }
}

enum class PhantomProcessLimitState {
    REMOVED,
    ACTIVE,
    UNAVAILABLE,
    UNSUPPORTED,
}

data class PhantomProcessLimitStatus(
    val state: PhantomProcessLimitState,
    val maxPhantomProcesses: Long? = null,
    val monitoringEnabled: Boolean? = null,
    val details: String,
)

internal fun parsePhantomProcessLimit(output: String): PhantomProcessLimitStatus {
    val values = output.lineSequence()
        .mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) null else line.substring(0, separator).trim() to line.substring(separator + 1).trim()
        }
        .toMap()
    val max = values["max"]?.takeUnless { it.isBlank() || it.equals("null", true) }?.toLongOrNull()
    val monitoring = values["monitor"]
        ?.takeUnless { it.isBlank() || it.equals("null", true) }
        ?.let { raw ->
            when (raw.lowercase()) {
                "1", "true" -> true
                "0", "false" -> false
                else -> null
            }
        }
    val removed = max == Long.MAX_VALUE || (max != null && max >= Int.MAX_VALUE) || monitoring == false

    return if (removed) {
        PhantomProcessLimitStatus(
            state = PhantomProcessLimitState.REMOVED,
            maxPhantomProcesses = max,
            monitoringEnabled = monitoring,
            details = "已解除 Android 幽灵进程限制。",
        )
    } else {
        PhantomProcessLimitStatus(
            state = PhantomProcessLimitState.ACTIVE,
            maxPhantomProcesses = max,
            monitoringEnabled = monitoring,
            details = if (max == null && monitoring == null) {
                "仍使用系统默认限制（通常最多 32 个幽灵进程）。"
            } else {
                "系统仍在限制幽灵进程。"
            },
        )
    }
}

/** Shell 命令执行结果。 */
data class ShellExecResult(
    val success: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

/** 特权状态摘要。 */
data class PrivilegeInfo(
    val mode: String,
    val shizukuAvailable: Boolean,
    val rootAvailable: Boolean,
)
