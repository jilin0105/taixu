package top.wkbin.taixu.runtime

import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.core.model.RuntimeState
import top.wkbin.taixu.runtime.shell.CommandResult
import top.wkbin.taixu.runtime.shell.LinuxSession
import top.wkbin.taixu.runtime.shell.ManagedProcess
import top.wkbin.taixu.runtime.shell.ProcessType
import top.wkbin.taixu.runtime.shell.SessionConfig
import top.wkbin.taixu.runtime.shell.ShellCommand
import top.wkbin.taixu.runtime.shell.TerminalOutput
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

/** Small deterministic runtime double for JVM tool-contract tests. */
class FakeLinuxRuntime : LinuxRuntime {
    override val state = MutableStateFlow<RuntimeState>(RuntimeState.Ready)
    val commandResults = mutableMapOf<String, CommandResult>()
    val executedCommands = mutableListOf<String>()
    val executedShellCommands = mutableListOf<ShellCommand>()
    val sessions = mutableListOf<SessionConfig>()

    override suspend fun initialize(request: RuntimeInstallRequest): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun restoreInstalledState(): Boolean = false
    override suspend fun updateRootfs(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun healthCheck(): RuntimeHealth = RuntimeHealth(
        status = RuntimeHealthStatus.HEALTHY,
        osRelease = "Debian GNU/Linux",
        architecture = "aarch64",
        workspaceWritable = true,
    )

    override suspend fun execute(command: ShellCommand): CommandResult {
        executedCommands += command.commandLine
        executedShellCommands += command
        return commandResults[command.commandLine] ?: CommandResult(
            exitCode = 0,
            stdout = "",
            stderr = "",
            durationMs = 0,
        )
    }

    override suspend fun startSession(config: SessionConfig): LinuxSession {
        sessions += config
        return FakeSession()
    }

    override suspend fun startBackground(
        id: String,
        command: ShellCommand,
        toolId: String?,
        type: ProcessType,
    ): ManagedProcess = ManagedProcess(
        id,
        System.currentTimeMillis(),
        FakeSession(),
        toolId,
        null,
        type,
    )

    override suspend fun stopBackground(id: String): Boolean = true
    override fun listBackground(): List<ManagedProcess> = emptyList()
    override suspend fun cleanupDeadBackground(): Int = 0
    override suspend fun shutdown() = Unit
    override fun rootfsPath(): File = File("/fake/rootfs")
    override fun workspacePath(): File = File("/fake/workspace")

    private class FakeSession : LinuxSession {
        override val isAlive: Boolean = true
        override val output: Flow<TerminalOutput> = emptyFlow()
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun resize(columns: Int, rows: Int) = Unit
        override suspend fun interrupt() = Unit
        override suspend fun close() = Unit
    }
}
