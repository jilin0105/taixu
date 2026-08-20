package top.wkbin.taixu.runtime

import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.core.model.RuntimeState
import top.wkbin.taixu.runtime.shell.CommandResult
import top.wkbin.taixu.runtime.shell.LinuxSession
import top.wkbin.taixu.runtime.shell.ManagedProcess
import top.wkbin.taixu.runtime.shell.ProcessType
import top.wkbin.taixu.runtime.shell.SessionConfig
import top.wkbin.taixu.runtime.shell.ShellCommand
import java.io.File
import kotlinx.coroutines.flow.StateFlow

interface LinuxRuntime {
    val state: StateFlow<RuntimeState>

    suspend fun initialize(request: RuntimeInstallRequest = RuntimeInstallRequest("ubuntu")): AppResult<Unit>

    suspend fun restoreInstalledState(): Boolean

    suspend fun updateRootfs(): AppResult<Unit>

    suspend fun healthCheck(): RuntimeHealth

    suspend fun execute(command: ShellCommand): CommandResult

    suspend fun startSession(config: SessionConfig = SessionConfig()): LinuxSession

    suspend fun startBackground(
        id: String,
        command: ShellCommand,
        toolId: String? = null,
        type: ProcessType = ProcessType.SERVICE,
    ): ManagedProcess

    suspend fun stopBackground(id: String): Boolean
    fun listBackground(): List<ManagedProcess>
    suspend fun cleanupDeadBackground(): Int

    suspend fun shutdown()

    fun rootfsPath(): File

    fun rootfsVersion(): String? = null

    fun workspacePath(): File
}
