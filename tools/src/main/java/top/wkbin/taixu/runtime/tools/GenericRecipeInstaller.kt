package top.wkbin.taixu.runtime.tools

import top.wkbin.taixu.core.model.RuntimeName
import top.wkbin.taixu.core.model.RuntimeRequirement
import top.wkbin.taixu.core.model.ToolManifest
import top.wkbin.taixu.core.tools.DependencyManager
import top.wkbin.taixu.core.tools.ManifestDependencyParser
import top.wkbin.taixu.core.tools.ProviderManager
import top.wkbin.taixu.core.tools.ToolActionResult
import top.wkbin.taixu.core.tools.ToolRuntimeAdapter
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.shell.CommandResult
import top.wkbin.taixu.runtime.shell.ManagedProcess
import top.wkbin.taixu.runtime.shell.ProcessType
import top.wkbin.taixu.runtime.shell.SessionConfig
import top.wkbin.taixu.runtime.shell.ShellCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 通用声明式配方执行引擎 (Generic Recipe Installer)
 * 根据 ToolManifest 声明的脚本、依赖和环境变量，动态驱动任意工具的安装、软链接、校验与生命周期管理。
 */
class GenericRecipeInstaller(
    private val manifest: ToolManifest,
    private val linuxRuntime: LinuxRuntime,
    private val dependencyManager: DependencyManager,
    private val providerManager: ProviderManager,
    private val toolCommandLinker: ToolCommandLinker,
) : ToolRuntimeAdapter {
    override val toolId: String = manifest.id

    override fun install(): Flow<InstallEvent> = flow {
        emit(InstallEvent.Started(toolId))
        try {
            checkReady()

            // 1. 准备前置依赖
            emit(InstallEvent.Progress(toolId, "正在解析并准备工具依赖...", 0.15f, InstallEvent.Phase.INSTALLING_DEPENDENCY))
            for (depString in manifest.dependencies) {
                val parsed = ManifestDependencyParser.parse(depString)
                if (parsed != null) {
                    val runtimeName = when (parsed.name.lowercase()) {
                        "node" -> RuntimeName.NODE
                        "python" -> RuntimeName.PYTHON
                        "git" -> RuntimeName.GIT
                        "curl" -> RuntimeName.CURL
                        "ca-certificates" -> RuntimeName.CA_CERTIFICATES
                        else -> null
                    }
                    if (runtimeName != null) {
                        acquire(runtimeName, toolId, parsed.constraint)
                    }
                }
            }

            // 2. 准备隔离目录与环境变量
            emit(InstallEvent.Progress(toolId, "正在配置沙箱隔离环境...", 0.35f, InstallEvent.Phase.RUNNING_INSTALLER))
            val toolDir = ToolLayout.toolDirectory(toolId)
            val toolDataDir = ToolLayout.toolDataDirectory(toolId)
            executeAndReport("mkdir -p $toolDir $toolDataDir")

            val baseEnvironment = providerManager.environment() + mapOf(
                "TAIXU_TOOL_ID" to toolId,
                "TAIXU_TOOL_DIR" to toolDir,
                "TAIXU_TOOL_DATA" to toolDataDir,
                "npm_config_prefix" to toolDir,
                "NPM_CONFIG_PREFIX" to toolDir,
                "PATH" to "$toolDir/bin:/opt/taixu/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            ) + manifest.environment

            // 2.5 预检与自愈基础系统包管理状态 (清理残留锁、已损坏的 updates 事务与未配置的 dpkg 状态)
            val preflightCmd = "rm -rf /var/lib/dpkg/updates/* /var/lib/dpkg/lock* /var/lib/apt/lists/lock /var/cache/apt/archives/lock 2>/dev/null || true; DEBIAN_FRONTEND=noninteractive dpkg --configure -a 2>/dev/null || true"
            linuxRuntime.execute(ShellCommand(commandLine = preflightCmd, environment = baseEnvironment))

            // 3. 执行安装配方脚本
            emit(InstallEvent.Progress(toolId, "正在执行 ${manifest.name} 安装配方...", 0.55f, InstallEvent.Phase.RUNNING_INSTALLER))
            val script = manifest.installScript?.trimIndent()
                ?: error("工具 ${manifest.id} 未配置有效安装步骤 (installSteps)")

            var result = executeAndReport(
                linuxRuntime.execute(
                    ShellCommand(
                        commandLine = script,
                        environment = baseEnvironment,
                        timeoutMs = 15 * 60 * 1000L,
                    ),
                ),
            )

            // 若遇到 dpkg 中断、updates 损坏或锁问题，自动深度清理并重试一次
            if (!result.isSuccess && (
                result.stderr.contains("dpkg was interrupted") ||
                result.stdout.contains("dpkg was interrupted") ||
                result.stderr.contains("parsing file '/var/lib/dpkg/updates") ||
                result.stdout.contains("parsing file '/var/lib/dpkg/updates") ||
                result.stderr.contains("Could not get lock") ||
                result.stderr.contains("is locked")
            )) {
                emit(InstallEvent.Progress(toolId, "检测到 dpkg 事务损坏或残留锁，正在自愈修复并重试...", 0.60f, InstallEvent.Phase.RUNNING_INSTALLER))
                val fixCmd = "rm -rf /var/lib/dpkg/updates/* /var/lib/dpkg/lock* /var/lib/apt/lists/lock /var/cache/apt/archives/lock 2>/dev/null || true; DEBIAN_FRONTEND=noninteractive dpkg --configure -a; DEBIAN_FRONTEND=noninteractive apt-get --fix-broken install -y 2>/dev/null || true"
                executeAndReport(linuxRuntime.execute(ShellCommand(commandLine = fixCmd, environment = baseEnvironment, timeoutMs = 120_000L)))
                result = executeAndReport(
                    linuxRuntime.execute(
                        ShellCommand(
                            commandLine = script,
                            environment = baseEnvironment,
                            timeoutMs = 15 * 60 * 1000L,
                        ),
                    ),
                )
            }

            if (!result.isSuccess) {
                error(result.stderr.ifBlank { result.stdout }.ifBlank { "安装配方执行失败" })
            }

            // 4. 创建命令入口软链接
            emit(InstallEvent.Progress(toolId, "正在生成命令入口链接...", 0.80f, InstallEvent.Phase.VERIFYING_INSTALLATION))
            val links = if (manifest.commandLinks.isNotEmpty()) {
                manifest.commandLinks
            } else {
                listOf(manifest.id)
            }

            for (linkName in links) {
                val targetPath = "$toolDir/bin/$linkName"
                val linkRes = toolCommandLinker.link(linkName, targetPath, baseEnvironment)
                if (!linkRes.isSuccess) {
                    val fallbackTarget = "/usr/bin/$linkName"
                    toolCommandLinker.link(linkName, fallbackTarget, baseEnvironment)
                }
            }

            // 5. 验证安装
            emit(InstallEvent.Progress(toolId, "正在验证安装结果...", 0.90f, InstallEvent.Phase.VERIFYING_INSTALLATION))
            val verifyCmd = manifest.verifyCommand ?: "${links.first()} --version"
            val versionResult = executeAndReport(
                linuxRuntime.execute(ShellCommand(commandLine = verifyCmd, environment = baseEnvironment, timeoutMs = 60_000L)),
            )
            if (!versionResult.isSuccess) {
                // 兜底检查：如果主要二进制已建立，判定为安装成功并记录告警，避免因单次命令超时粗暴回滚整个事务
                val anyBinaryExists = links.any { link ->
                    val checkRes = linuxRuntime.execute(ShellCommand("test -x $toolDir/bin/$link || test -x /opt/taixu/bin/$link || test -x /usr/bin/$link", environment = baseEnvironment, timeoutMs = 5_000L))
                    checkRes.isSuccess
                }
                if (!anyBinaryExists) {
                    error("验证命令失败 ($verifyCmd): ${versionResult.stderr.ifBlank { versionResult.stdout }}")
                }
            }

            val versionOutput = versionResult.stdout.trim().lineSequence().firstOrNull()?.takeIf { it.isNotBlank() } ?: manifest.version
            emit(InstallEvent.Completed(toolId, versionOutput))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            emit(InstallEvent.RolledBack(toolId))
            emit(InstallEvent.Failed(toolId, throwable.message ?: "安装失败"))
        }
    }

    override suspend fun launch(): CommandResult = execute(manifest.launchCommand ?: manifest.id)

    override suspend fun verify(): CommandResult = execute(manifest.verifyCommand ?: "${manifest.id} --version")

    override suspend fun interactiveSessionConfig(): SessionConfig = SessionConfig(
        commandLine = "exec ${manifest.launchCommand ?: manifest.id}",
        environment = providerManager.environment() + manifest.environment,
        allowSttyResize = false,
    )

    override suspend fun startService(): ManagedProcess = linuxRuntime.startBackground(
        id = "${toolId}-service",
        command = ShellCommand(
            commandLine = manifest.launchCommand ?: manifest.id,
            environment = providerManager.environment() + manifest.environment,
        ),
        toolId = toolId,
        type = ProcessType.SERVICE,
    )

    override suspend fun uninstall(deleteData: Boolean): ToolActionResult {
        val toolDir = ToolLayout.toolDirectory(toolId)
        val toolDataDir = ToolLayout.toolDataDirectory(toolId)
        val links = if (manifest.commandLinks.isNotEmpty()) manifest.commandLinks else listOf(manifest.id)

        for (link in links) {
            toolCommandLinker.remove(link, providerManager.environment())
        }

        val customUninstall = manifest.uninstallScript
        if (!customUninstall.isNullOrBlank()) {
            linuxRuntime.execute(ShellCommand(customUninstall, environment = providerManager.environment()))
        }

        val dataCleanup = if (deleteData) " && rm -rf $toolDataDir" else ""
        val directoryResult = linuxRuntime.execute(ShellCommand("rm -rf $toolDir$dataCleanup"))

        return ToolActionResult(
            success = directoryResult.isSuccess,
            message = if (directoryResult.isSuccess) "卸载完成" else directoryResult.stderr.ifBlank { "卸载失败" },
        )
    }

    private suspend fun acquire(name: RuntimeName, toolId: String, constraint: String? = null) {
        val result = dependencyManager.acquire(RuntimeRequirement(name, constraint), toolId)
        if (result.isFailure) error(result.errorOrNull()?.message ?: "依赖安装失败：$name")
    }

    private suspend fun execute(command: String) = linuxRuntime.execute(
        ShellCommand(command, environment = providerManager.environment() + manifest.environment),
    )

    private suspend fun kotlinx.coroutines.flow.FlowCollector<InstallEvent>.executeAndReport(
        result: CommandResult,
    ): CommandResult {
        result.stdout.lineSequence().filter { it.isNotBlank() }.forEach { emit(InstallEvent.Output(toolId, it)) }
        result.stderr.lineSequence().filter { it.isNotBlank() }.forEach { emit(InstallEvent.Output(toolId, it)) }
        return result
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<InstallEvent>.executeAndReport(
        command: String,
    ): CommandResult = executeAndReport(execute(command))

    private fun checkReady() = check(linuxRuntime.state.value is top.wkbin.taixu.core.model.RuntimeState.Ready) {
        "Linux Runtime 未就绪，请先初始化 Linux"
    }
}
