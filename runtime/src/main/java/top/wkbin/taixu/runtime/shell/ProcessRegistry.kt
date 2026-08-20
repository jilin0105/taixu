package top.wkbin.taixu.runtime.shell

import top.wkbin.taixu.runtime.RuntimePathManager
import top.wkbin.taixu.runtime.proot.ProotCommandBuilder
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ProcessType {
    TERMINAL,
    SERVICE,
    COMMAND,
    UNKNOWN,
}

data class ManagedProcess(
    val id: String,
    val startedAt: Long,
    val session: LinuxSession,
    val toolId: String? = null,
    val pid: Long? = session.pid,
    val type: ProcessType = ProcessType.UNKNOWN,
)

interface ProcessRegistry {
    suspend fun start(
        id: String,
        command: ShellCommand,
        toolId: String? = null,
        type: ProcessType = ProcessType.SERVICE,
    ): ManagedProcess
    suspend fun stop(id: String): Boolean
    suspend fun stopAll()
    suspend fun cleanupDeadProcesses(): Int
    fun list(): List<ManagedProcess>
}

@Singleton
class ProcessRegistryImpl @Inject constructor(
    private val pathManager: RuntimePathManager,
    private val prootCommandBuilder: ProotCommandBuilder,
) : ProcessRegistry {
    private val mutex = Mutex()
    private val processes = LinkedHashMap<String, ManagedProcess>()

    override suspend fun start(
        id: String,
        command: ShellCommand,
        toolId: String?,
        type: ProcessType,
    ): ManagedProcess = mutex.withLock {
        processes.remove(id)?.session?.close()
        val session = ProcessLinuxSession(
            prootCommandBuilder.build(
                prootBinary = pathManager.activeProotFile(),
                rootfsDir = pathManager.rootfsDir,
                workspaceDir = pathManager.workspaceDir,
                homeDir = pathManager.homeDir,
                optDir = pathManager.taixuRootDir,
                command = command,
            ),
            hostEnvironment = pathManager.hostProcessEnvironment(),
        )
        ManagedProcess(
            id = id,
            startedAt = System.currentTimeMillis(),
            session = session,
            toolId = toolId,
            pid = session.pid,
            type = type,
        ).also { processes[id] = it }
    }

    override suspend fun stop(id: String): Boolean = mutex.withLock {
        val process = processes.remove(id) ?: return@withLock false
        process.session.close()
        true
    }

    override suspend fun stopAll() = mutex.withLock {
        processes.values.forEach { it.session.close() }
        processes.clear()
    }

    override suspend fun cleanupDeadProcesses(): Int = mutex.withLock {
        val dead = processes.values.filter { !it.session.isAlive }
        dead.forEach { processes.remove(it.id) }
        dead.size
    }

    override fun list(): List<ManagedProcess> = processes.values.toList()
}
