package top.wkbin.taixu.core.tools

import top.wkbin.taixu.core.database.InstallLogEntity
import top.wkbin.taixu.core.database.InstallTaskEntity
import top.wkbin.taixu.core.database.ToolEntity
import top.wkbin.taixu.core.model.ToolManifest
import top.wkbin.taixu.core.model.ToolState
import top.wkbin.taixu.core.security.SecretRedactor
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.service.LocalServiceSpec
import top.wkbin.taixu.runtime.tools.InstallEvent
import top.wkbin.taixu.runtime.shell.LinuxSession
import top.wkbin.taixu.runtime.shell.ManagedProcess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class ToolInstallProgress(
    val toolId: String,
    val message: String,
    val progress: Float? = null,
    val terminal: Boolean = false,
)

private data class UninstallOutcome(
    val success: Boolean,
    val message: String,
)

@Singleton
class ToolManager @Inject constructor(
    private val toolRepository: ToolRepository,
    private val installLogRepository: InstallLogRepository,
    private val installTaskRepository: InstallTaskRepository,
    private val installTransactionManager: InstallTransactionManager,
    private val dependencyManager: DependencyManager,
    private val linuxRuntime: LinuxRuntime,
    private val secretRedactor: SecretRedactor,
    installerAdapters: Set<@JvmSuppressWildcards ToolRuntimeAdapter>,
) {
    private val installMutex = Mutex()
    private val transactionMutex = Mutex()
    private val installJobs = mutableMapOf<String, Job>()
    private val _installProgress = MutableStateFlow<Map<String, ToolInstallProgress>>(emptyMap())
    val installProgress: StateFlow<Map<String, ToolInstallProgress>> = _installProgress.asStateFlow()
    private val _verifications = MutableStateFlow<Map<String, ToolVerification>>(emptyMap())
    val verifications: StateFlow<Map<String, ToolVerification>> = _verifications.asStateFlow()
    private val installerById = installerAdapters.associateBy { it.toolId }

    fun observeTools(): Flow<List<ToolEntity>> = toolRepository.observeTools()

    fun isToolSupported(toolId: String): Boolean = toolId in installerById

    /** Service metadata comes from the signed/validated manifest, not the UI. */
    fun serviceSpec(toolId: String): LocalServiceSpec? {
        val manifest = toolRepository.manifest(toolId) ?: return null
        val port = manifest.servicePort ?: return null
        return LocalServiceSpec(
            serviceId = toolId,
            port = port,
            path = manifest.servicePath,
        )
    }

    suspend fun syncRegistry() {
        val liveInstallTools = installJobs.keys.toSet()
        installTaskRepository.listByState(TASK_RUNNING)
            .filter { it.toolId !in liveInstallTools }
            .forEach { task ->
            installTransactionManager.recover(
                toolId = task.toolId,
                preserveExisting = task.operation == OPERATION_UPDATE,
            )
            installTaskRepository.upsert(
                task.copy(
                    state = TASK_INTERRUPTED,
                    message = "检测到应用进程被中断，请重试",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            val interruptedTool = toolRepository.findById(task.toolId)
            val recoveredState = if (
                task.operation == OPERATION_UPDATE && interruptedTool?.installedVersion != null
            ) {
                ToolState.INSTALLED.name
            } else {
                ToolState.FAILED.name
            }
            toolRepository.updateState(task.toolId, recoveredState)
            installLogRepository.insert(
                InstallLogEntity(
                    toolId = task.toolId,
                    event = TASK_INTERRUPTED,
                    message = "${task.operation} 任务在应用进程终止后被标记为中断",
                ),
            )
        }
        toolRepository.manifests().forEach { manifest ->
            val existing = toolRepository.findById(manifest.id)
            toolRepository.upsert(manifest.toEntity(existing))
            if (existing?.state == ToolState.INSTALLING.name && manifest.id !in liveInstallTools) {
                // The process may have been killed while an installer was running.
                // Never leave a durable INSTALLING state that has no live Job behind it.
                toolRepository.updateState(manifest.id, ToolState.FAILED.name)
                installLogRepository.insert(
                    InstallLogEntity(
                        toolId = manifest.id,
                        event = "RECOVERED",
                        message = "检测到上次安装被中断，请重试",
                    ),
                )
            }
        }
        installTransactionManager.cleanupOrphans(liveInstallTools)
    }

    /** Install and update share the same transactional adapter path. */
    fun install(toolId: String): Flow<InstallEvent> = installInternal(toolId, OPERATION_INSTALL)

    private fun installInternal(toolId: String, operation: String): Flow<InstallEvent> = flow {
        require(toolId in installerById) { "暂不支持安装工具：$toolId" }
        requireManifestEnabled(toolId)
        val currentJob = coroutineContext[Job]
            ?: error("安装任务必须运行在协程中")
        val previousTool = toolRepository.findById(toolId)
        val preservePreviousInstall = operation == OPERATION_UPDATE &&
            previousTool?.installedVersion != null
        installMutex.withLock {
            check(toolId !in installJobs) { "工具正在安装：$toolId" }
            installJobs[toolId] = currentJob
            updateProgress(ToolInstallProgress(toolId, "准备安装", 0f))
            toolRepository.updateState(toolId, ToolState.INSTALLING.name)
            installTaskRepository.upsert(
                InstallTaskEntity(
                    toolId = toolId,
                    operation = operation,
                    state = TASK_RUNNING,
                    message = "任务开始",
                ),
            )
        }

        var cancelled = false
        var transaction: InstallTransaction? = null
        try {
            // apt, npm and shared runtime directories are global to the Linux
            // runtime. Keep one adapter transaction active at a time even when
            // the UI launches jobs for different tools.
            transactionMutex.withLock {
                if (operation == OPERATION_UPDATE) {
                    linuxRuntime.listBackground()
                        .filter { it.toolId == toolId }
                        .forEach { linuxRuntime.stopBackground(it.id) }
                }
                if (preservePreviousInstall) {
                    val current = requireAdapter(toolId).verify()
                    check(current.isSuccess) {
                        "更新前验证失败：${current.stderr.ifBlank { current.stdout }.trim()}"
                    }
                }
                transaction = installTransactionManager.begin(toolId, preservePreviousInstall)
                selectInstaller(toolId).collect { event ->
                    val safeEvent = event.redacted()
                    recordEvent(safeEvent)
                    updateFromEvent(safeEvent)
                    when (safeEvent) {
                        is InstallEvent.Completed -> toolRepository.updateStateAndInstalledVersion(
                            id = toolId,
                            state = ToolState.INSTALLED.name,
                            installedVersion = safeEvent.version?.trim()?.takeIf { it.isNotBlank() }
                                ?: toolRepository.findById(toolId)?.manifestVersion,
                        )
                        is InstallEvent.Failed -> {
                            transaction?.let { installTransactionManager.rollback(it) }
                            transaction = null
                            toolRepository.updateStateAndInstalledVersion(
                                id = toolId,
                                state = failureState(preservePreviousInstall, previousTool),
                                installedVersion = if (preservePreviousInstall) previousTool?.installedVersion else null,
                            )
                            if (!preservePreviousInstall) releaseRuntimeReferences(toolId)
                        }
                        else -> Unit
                    }
                    when (safeEvent) {
                        is InstallEvent.Completed -> updateTask(toolId, TASK_COMPLETED, "安装完成")
                        is InstallEvent.Failed -> updateTask(toolId, TASK_FAILED, safeEvent.message)
                        else -> Unit
                    }
                    if (safeEvent is InstallEvent.Completed) {
                        transaction?.let { installTransactionManager.commit(it) }
                        transaction = null
                    }
                    emit(safeEvent)
                }
            }
        } catch (throwable: CancellationException) {
            cancelled = true
            withContext(NonCancellable) {
                transaction?.let { tx ->
                    try {
                        installTransactionManager.rollback(tx)
                    } finally {
                        transaction = null
                    }
                }
                toolRepository.updateStateAndInstalledVersion(
                    id = toolId,
                    state = failureState(preservePreviousInstall, previousTool, cancelled = true),
                    installedVersion = if (preservePreviousInstall) previousTool?.installedVersion else null,
                )
                updateTask(toolId, TASK_CANCELLED, "用户取消安装")
                if (!preservePreviousInstall) releaseRuntimeReferences(toolId)
                val event = InstallEvent.Cancelled(toolId)
                recordEvent(event)
                updateFromEvent(event)
            }
            throw throwable
        } catch (throwable: Throwable) {
            val event = InstallEvent.Failed(
                toolId,
                secretRedactor.redact(throwable.message ?: "安装流程异常终止"),
            )
            withContext(NonCancellable) {
                transaction?.let { tx ->
                    try {
                        installTransactionManager.rollback(tx)
                    } finally {
                        transaction = null
                    }
                }
                toolRepository.updateStateAndInstalledVersion(
                    id = toolId,
                    state = failureState(preservePreviousInstall, previousTool),
                    installedVersion = if (preservePreviousInstall) previousTool?.installedVersion else null,
                )
                if (!preservePreviousInstall) releaseRuntimeReferences(toolId)
                updateTask(toolId, TASK_FAILED, event.message)
                recordEvent(event)
                updateFromEvent(event)
            }
            emit(event)
        } finally {
            withContext(NonCancellable) {
                installMutex.withLock {
                    if (installJobs[toolId] === currentJob) installJobs.remove(toolId)
                }
                if (!cancelled && _installProgress.value[toolId]?.terminal != true) {
                    // An adapter that ended without Completed/Failed is not a successful install.
                    transaction?.let { tx ->
                        try {
                            installTransactionManager.rollback(tx)
                        } finally {
                            transaction = null
                        }
                    }
                    toolRepository.updateStateAndInstalledVersion(
                        id = toolId,
                        state = failureState(preservePreviousInstall, previousTool),
                        installedVersion = if (preservePreviousInstall) previousTool?.installedVersion else null,
                    )
                    if (!preservePreviousInstall) releaseRuntimeReferences(toolId)
                    val event = InstallEvent.Failed(toolId, "安装流程未完成")
                    updateTask(toolId, TASK_FAILED, event.message)
                    recordEvent(event)
                    updateFromEvent(event)
                }
            }
        }
    }

    fun update(toolId: String): Flow<InstallEvent> = installInternal(toolId, OPERATION_UPDATE)

    fun cancelInstall(toolId: String) {
        installJobs[toolId]?.cancel(CancellationException("用户取消安装"))
    }

    suspend fun uninstall(toolId: String, deleteData: Boolean = false) {
        require(toolId in installerById) { "暂不支持卸载工具：$toolId" }
        installMutex.withLock {
            check(toolId !in installJobs) { "工具正在安装：$toolId" }
        }
        transactionMutex.withLock {
            linuxRuntime.listBackground()
                .filter { it.toolId == toolId }
                .forEach { linuxRuntime.stopBackground(it.id) }
            uninstallLocked(toolId, deleteData)
        }
    }

    private suspend fun uninstallLocked(toolId: String, deleteData: Boolean) {
        installTaskRepository.upsert(
            InstallTaskEntity(
                toolId = toolId,
                operation = OPERATION_UNINSTALL,
                state = TASK_RUNNING,
                message = "卸载任务开始",
            ),
        )
        try {
            val outcome = requireAdapter(toolId, requireEnabled = false).uninstall(deleteData).toUninstallOutcome()
            val event = if (outcome.success) "UNINSTALLED" else "UNINSTALL_FAILED"
            val safeMessage = secretRedactor.redact(outcome.message.ifBlank { "卸载完成" })
            toolRepository.updateStateAndInstalledVersion(
                id = toolId,
                state = if (outcome.success) ToolState.AVAILABLE.name else ToolState.FAILED.name,
                installedVersion = if (outcome.success) null else toolRepository.findById(toolId)?.installedVersion,
            )
            if (outcome.success) releaseRuntimeReferences(toolId)
            updateTask(toolId, if (outcome.success) TASK_COMPLETED else TASK_FAILED, safeMessage)
            installLogRepository.insert(InstallLogEntity(toolId = toolId, event = event, message = safeMessage))
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { updateTask(toolId, TASK_CANCELLED, "用户取消卸载") }
            throw cancellation
        } catch (throwable: Throwable) {
            val message = secretRedactor.redact(throwable.message ?: "卸载流程异常终止")
            updateTask(toolId, TASK_FAILED, message)
            throw throwable
        }
    }

    fun observeInstallLogs(toolId: String): Flow<List<InstallLogEntity>> =
        installLogRepository.observeForTool(toolId)

    suspend fun launch(toolId: String): top.wkbin.taixu.runtime.shell.CommandResult {
        requireInstalledTool(toolId)
        return requireAdapter(toolId).launch()
    }

    suspend fun startSession(toolId: String?, workingDirectory: String = "/root"): LinuxSession {
        val config = if (toolId.isNullOrBlank()) {
            null
        } else {
            requireInstalledTool(toolId)
            requireAdapter(toolId).interactiveSessionConfig()
        }
        return if (config == null) {
            linuxRuntime.startSession(top.wkbin.taixu.runtime.shell.SessionConfig(workingDirectory = workingDirectory))
        } else {
            linuxRuntime.startSession(config.copy(workingDirectory = workingDirectory))
        }
    }

    suspend fun verify(toolId: String): ToolVerification {
        require(toolId in installerById) { "暂不支持验证工具：$toolId" }
        requireManifestEnabled(toolId)
        val result = requireAdapter(toolId).verify()
        val safeStdout = secretRedactor.redact(result.stdout)
        val safeStderr = secretRedactor.redact(result.stderr)
        val verification = ToolVerification(
            toolId = toolId,
            healthy = result.isSuccess,
            version = safeStdout.trim().lineSequence().firstOrNull()?.takeIf { it.isNotBlank() },
            detail = if (result.isSuccess) {
                safeStdout.trim().ifBlank { "命令执行成功" }
            } else {
                safeStderr.ifBlank { safeStdout }.trim().ifBlank { "命令退出码 ${result.exitCode}" }
            },
        )
        _verifications.value = _verifications.value + (toolId to verification)
        installLogRepository.insert(
            InstallLogEntity(
                toolId = toolId,
                event = if (verification.healthy) "VERIFIED" else "VERIFY_FAILED",
                message = verification.detail,
            ),
        )
        if (verification.healthy) {
            val current = toolRepository.findById(toolId)
            toolRepository.updateStateAndInstalledVersion(
                id = toolId,
                state = ToolState.INSTALLED.name,
                installedVersion = current?.installedVersion
                    ?: current?.manifestVersion
                    ?: verification.version,
            )
        } else {
            toolRepository.updateState(toolId, ToolState.FAILED.name)
        }
        return verification
    }

    suspend fun startGateway(toolId: String): ManagedProcess {
        requireInstalledTool(toolId)
        return requireNotNull(requireAdapter(toolId).startService()) {
            "工具不提供后台服务：$toolId"
        }
    }

    private suspend fun requireInstalledTool(toolId: String): ToolEntity {
        val tool = toolRepository.findById(toolId)
            ?: error("工具不在当前清单中：$toolId")
        check(tool.state == ToolState.INSTALLED.name || tool.state == ToolState.UPDATE_AVAILABLE.name) {
            "工具尚未安装：${tool.name}"
        }
        return tool
    }

    private fun selectInstaller(toolId: String): Flow<InstallEvent> =
        checkNotNull(installerById[toolId]) { "没有找到工具安装器：$toolId" }.install()

    private fun requireAdapter(
        toolId: String,
        requireEnabled: Boolean = true,
    ): ToolRuntimeAdapter = checkNotNull(installerById[toolId]) { "暂不支持工具：$toolId" }.also {
        if (requireEnabled) {
            requireManifestEnabled(toolId)
        }
    }

    private fun requireManifestEnabled(toolId: String) {
        val manifest = toolRepository.manifest(toolId)
            ?: error("工具不在当前清单中：$toolId")
        check(manifest.enabled) { "工具已被 Registry 暂停：${manifest.name}" }
    }

    private suspend fun recordEvent(event: InstallEvent) {
        installLogRepository.insert(event.toLog())
    }

    private suspend fun updateTask(toolId: String, state: String, message: String) {
        val current = installTaskRepository.findByTool(toolId) ?: return
        installTaskRepository.upsert(
            current.copy(
                state = state,
                message = secretRedactor.redact(message),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun InstallEvent.redacted(): InstallEvent = when (this) {
        is InstallEvent.Progress -> copy(message = secretRedactor.redact(message))
        is InstallEvent.Output -> copy(line = secretRedactor.redact(line))
        is InstallEvent.Failed -> copy(message = secretRedactor.redact(message))
        else -> this
    }

    private fun updateFromEvent(event: InstallEvent) {
        val state = when (event) {
            is InstallEvent.Started -> ToolInstallProgress(event.toolId, "开始安装", 0f)
            is InstallEvent.Progress -> ToolInstallProgress(event.toolId, event.message, event.progress)
            is InstallEvent.Output -> ToolInstallProgress(event.toolId, event.line)
            is InstallEvent.Completed -> ToolInstallProgress(event.toolId, "安装完成", 1f, terminal = true)
            is InstallEvent.Failed -> ToolInstallProgress(event.toolId, event.message, terminal = true)
            is InstallEvent.RolledBack -> ToolInstallProgress(event.toolId, "安装失败，正在回滚")
            is InstallEvent.Cancelled -> ToolInstallProgress(event.toolId, "已取消安装", terminal = true)
        }
        updateProgress(state)
    }

    private fun updateProgress(progress: ToolInstallProgress) {
        _installProgress.value = _installProgress.value + (progress.toolId to progress)
    }

    private fun failureState(
        preservePreviousInstall: Boolean,
        previousTool: ToolEntity?,
        cancelled: Boolean = false,
    ): String = when {
        preservePreviousInstall -> previousTool?.state ?: ToolState.INSTALLED.name
        cancelled -> ToolState.AVAILABLE.name
        else -> ToolState.FAILED.name
    }

    private suspend fun releaseRuntimeReferences(toolId: String) {
        val dependencies = toolRepository.findById(toolId)?.dependencies.orEmpty()
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        dependencies.forEach { dependency ->
            val runtimeName = when (ManifestDependencyParser.parse(dependency)?.name) {
                "node" -> "node"
                "python" -> "python"
                "git" -> "git"
                "ca-certificates" -> "ca_certificates"
                "curl" -> "curl"
                else -> null
            }
            runtimeName?.let { dependencyManager.release(it, toolId) }
        }
    }

    private fun InstallEvent.toLog(): InstallLogEntity = when (this) {
        is InstallEvent.Started -> InstallLogEntity(toolId = toolId, event = "STARTED", message = "开始安装")
        is InstallEvent.Progress -> InstallLogEntity(
            toolId = toolId,
            event = "PROGRESS_${phase.name}",
            message = message,
        )
        is InstallEvent.Output -> InstallLogEntity(toolId = toolId, event = "OUTPUT", message = line)
        is InstallEvent.Completed -> InstallLogEntity(toolId = toolId, event = "COMPLETED", message = "安装完成${version?.let { "：$it" } ?: ""}")
        is InstallEvent.Failed -> InstallLogEntity(toolId = toolId, event = "FAILED", message = message)
        is InstallEvent.RolledBack -> InstallLogEntity(toolId = toolId, event = "ROLLED_BACK", message = "已回滚安装事务")
        is InstallEvent.Cancelled -> InstallLogEntity(toolId = toolId, event = "CANCELLED", message = "用户取消安装")
    }

    private suspend fun ToolManifest.toEntity(existing: ToolEntity?) = ToolEntity(
        id = id,
        name = name,
        description = description,
        dependencies = dependencies.joinToString(","),
        launchType = launchType,
        state = when {
            !enabled -> ToolState.DISABLED.name
            existing == null -> ToolState.AVAILABLE.name
            else -> {
                val installedVersion = existing.installedVersion
                val manifestLatest = latestVersion
                when {
                    existing.state == ToolState.DISABLED.name ->
                        if (installedVersion != null) ToolState.INSTALLED.name else ToolState.AVAILABLE.name
                    installedVersion != null &&
                        manifestLatest != null &&
                        manifestLatest.versionNumbers() != null &&
                        installedVersion.versionNumbers() != manifestLatest.versionNumbers() ->
                        ToolState.UPDATE_AVAILABLE.name
                    existing.state == ToolState.UPDATE_AVAILABLE.name -> ToolState.INSTALLED.name
                    else -> existing.state
                }
            }
        },
        manifestVersion = version,
        installedVersion = existing?.installedVersion,
        publisher = publisher,
        category = category,
        permissions = permissions.joinToString(","),
        homepage = homepage,
        updateStrategy = updateStrategy,
        latestVersion = latestVersion ?: version,
    )

    private fun String.versionNumbers(): List<Int>? = Regex("\\d+(?:\\.\\d+){0,3}")
        .find(this)
        ?.value
        ?.split('.')
        ?.map(String::toInt)

    private fun ToolActionResult.toUninstallOutcome(): UninstallOutcome =
        UninstallOutcome(success, message)

    private fun top.wkbin.taixu.runtime.shell.CommandResult.toUninstallOutcome(): UninstallOutcome =
        UninstallOutcome(isSuccess, stderr.ifBlank { stdout })

    private companion object {
        const val OPERATION_INSTALL = "INSTALL"
        const val OPERATION_UPDATE = "UPDATE"
        const val OPERATION_UNINSTALL = "UNINSTALL"
        const val TASK_RUNNING = "RUNNING"
        const val TASK_COMPLETED = "COMPLETED"
        const val TASK_FAILED = "FAILED"
        const val TASK_CANCELLED = "CANCELLED"
        const val TASK_INTERRUPTED = "INTERRUPTED"
    }

}
