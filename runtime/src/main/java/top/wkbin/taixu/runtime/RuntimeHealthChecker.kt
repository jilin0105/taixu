package top.wkbin.taixu.runtime

import top.wkbin.taixu.runtime.proot.ProotCommandBuilder
import top.wkbin.taixu.runtime.shell.ShellCommand
import top.wkbin.taixu.runtime.shell.ShellExecutor
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuntimeHealthChecker @Inject constructor(
    private val pathManager: RuntimePathManager,
    private val prootCommandBuilder: ProotCommandBuilder,
    private val shellExecutor: ShellExecutor,
) {
    suspend fun check(): RuntimeHealth {
        if (!pathManager.isProotInstalled()) {
            return RuntimeHealth(
                status = RuntimeHealthStatus.UNHEALTHY,
                detail = "PRoot 运行组件不完整：缺少或无法读取 APK 内的 ARM64 tracee loader",
            )
        }
        if (!pathManager.isRootfsInstalled()) {
            return RuntimeHealth(
                status = RuntimeHealthStatus.UNHEALTHY,
                detail = "Linux RootFS 校验失败：Bash、/bin/sh 或其 ARM64 ELF 解释器不完整",
            )
        }

        val osReleaseResult = runCatching { runCommand(ShellCommand("cat /etc/os-release")) }
            .getOrElse { failedCommandResult(it) }
        val architectureResult = runCatching { runCommand(ShellCommand("uname -m")) }
            .getOrElse { failedCommandResult(it) }
        val osRelease = osReleaseResult.stdout.trim()
        val architecture = architectureResult.stdout.trim()
        val healthMarker = ".taixu-health"
        val writeResult = runCatching {
            runCommand(
                ShellCommand("mkdir -p /workspace && echo taixu-health-ok > /workspace/$healthMarker"),
            )
        }.getOrElse { failedCommandResult(it) }
        val workspaceFile = File(pathManager.workspaceDir, healthMarker)
        val workspaceWritable = writeResult.isSuccess &&
            workspaceFile.isFile &&
            workspaceFile.readText().contains("taixu-health-ok")

        val healthy = osReleaseResult.isSuccess &&
            architectureResult.isSuccess &&
            osRelease.isNotBlank() &&
            architecture.isNotBlank() &&
            workspaceWritable

        return RuntimeHealth(
            status = if (healthy) RuntimeHealthStatus.HEALTHY else RuntimeHealthStatus.UNHEALTHY,
            osRelease = osRelease.ifBlank { null },
            architecture = architecture.ifBlank { null },
            workspaceWritable = workspaceWritable,
            detail = if (healthy) null else buildFailureDetail(
                osReleaseResult,
                architectureResult,
                writeResult,
            ),
        )
    }

    private fun buildFailureDetail(
        osRelease: top.wkbin.taixu.runtime.shell.CommandResult,
        architecture: top.wkbin.taixu.runtime.shell.CommandResult,
        workspace: top.wkbin.taixu.runtime.shell.CommandResult,
    ): String = listOfNotNull(
        osRelease.takeUnless { it.isSuccess }?.describeFailure("os-release"),
        architecture.takeUnless { it.isSuccess }?.describeFailure("uname"),
        workspace.takeUnless { it.isSuccess }?.describeFailure("workspace"),
    ).ifEmpty { listOf("Workspace is not writable") }.joinToString("; ")

    private fun top.wkbin.taixu.runtime.shell.CommandResult.describeFailure(
        label: String,
    ): String {
        val output = (stderr.ifBlank { stdout }).trim().replace(Regex("\\s+"), " ")
        if (output.contains("execve(") && output.contains("No such file or directory")) {
            return "$label exit=$exitCode: PRoot tracee loader 或 Guest ELF 解释器未能启动"
        }
        if (output.contains("the loader was not found", ignoreCase = true)) {
            return "$label exit=$exitCode: PRoot 外置 loader 缺失或不可执行"
        }
        val detail = output.take(240).ifBlank { "no diagnostic output" }
        return "$label exit=$exitCode: $detail"
    }

    private suspend fun runCommand(command: ShellCommand) = shellExecutor.execute(
        command = prootCommandBuilder.build(
            prootBinary = pathManager.activeProotFile(),
            rootfsDir = pathManager.rootfsDir,
            workspaceDir = pathManager.workspaceDir,
            tmpDir = pathManager.tmpDir,
            attachmentsDir = pathManager.attachmentsDir,
            command = command,
        ),
        timeoutMs = command.timeoutMs,
    )

    private fun failedCommandResult(throwable: Throwable) =
        top.wkbin.taixu.runtime.shell.CommandResult(
            exitCode = 126,
            stdout = "",
            stderr = throwable.message ?: throwable.javaClass.simpleName,
            durationMs = 0L,
        )
}
