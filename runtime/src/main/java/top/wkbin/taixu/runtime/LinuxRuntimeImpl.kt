package top.wkbin.taixu.runtime

import android.os.Build
import android.os.StatFs
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.common.result.AppError
import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.core.common.result.ErrorCode
import top.wkbin.taixu.core.model.CpuArch
import top.wkbin.taixu.core.model.RuntimeState
import top.wkbin.taixu.runtime.proot.ProotCommandBuilder
import top.wkbin.taixu.runtime.pty.PtyManager
import top.wkbin.taixu.runtime.proot.ProotInstaller
import top.wkbin.taixu.runtime.rootfs.RootfsInstaller
import top.wkbin.taixu.runtime.shell.CommandResult
import top.wkbin.taixu.runtime.shell.LinuxSession
import top.wkbin.taixu.runtime.shell.ManagedProcess
import top.wkbin.taixu.runtime.shell.ProcessType
import top.wkbin.taixu.runtime.shell.ProcessRegistry
import top.wkbin.taixu.runtime.shell.SessionConfig
import top.wkbin.taixu.runtime.shell.ShellCommand
import top.wkbin.taixu.runtime.shell.ShellExecutor
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class LinuxRuntimeImpl @Inject constructor(
    private val pathManager: RuntimePathManager,
    private val prootInstaller: ProotInstaller,
    private val rootfsInstaller: RootfsInstaller,
    private val prootCommandBuilder: ProotCommandBuilder,
    private val ptyManager: PtyManager,
    private val shellExecutor: ShellExecutor,
    private val healthChecker: RuntimeHealthChecker,
    private val processRegistry: ProcessRegistry,
    private val logger: AppLogger,
) : LinuxRuntime {

    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.NotInitialized)
    override val state: StateFlow<RuntimeState> = _state.asStateFlow()

    private val initializeMutex = Mutex()

    override suspend fun initialize(request: RuntimeInstallRequest): AppResult<Unit> = initializeMutex.withLock {
        withContext(Dispatchers.IO) {
            if (_state.value is RuntimeState.Ready) {
                return@withContext AppResult.Success(Unit)
            }

            try {
            updateInitializing("detectArchitecture", 0f)
            val architecture = detectArchitecture()
            if (architecture != CpuArch.ARM64) {
                val message = "Unsupported CPU architecture: $architecture. Phase 1 supports ARM64 only."
                logger.e(message)
                val error = AppError(ErrorCode.UNSUPPORTED_ARCHITECTURE, message)
                _state.value = RuntimeState.Error(RuntimeArchitectureException(message))
                return@withContext AppResult.Failure(error)
            }

            updateInitializing("checkStorage", 0.05f)
            checkStorage()

            updateInitializing("createDirectories", 0.1f)
            pathManager.ensureDirectories()
            pathManager.cleanupStalePtyMarkers()

            updateInitializing("校验运行引擎", 0.15f, "校验 PRoot 主程序、外置 loader 与 ARM64 架构")
            val prootResult = prootInstaller.install()
            val prootError = prootResult.errorOrNull()
            if (prootError != null) {
                failInitialization(prootError)
                return@withContext AppResult.Failure(prootError)
            }

            val distribution = DistributionCatalog.require(request.distributionId)
            updateInitializing("下载 ${distribution.displayName}", 0.2f, "通过 proot-distro 5.7.0 OCI 机制下载 linux/arm64 镜像")
            val rootfsResult = rootfsInstaller.installOci(
                distribution,
                request.registryRoute,
            ) { progress -> updateDownloadProgress("下载 ${distribution.displayName}", 0.2f, 0.55f, progress) }
            val rootfsError = rootfsResult.errorOrNull()
            if (rootfsError != null) {
                failInitialization(rootfsError)
                return@withContext AppResult.Failure(rootfsError)
            }

            updateInitializing("configureRootfs", 0.75f)
            configureRootfs()

            updateInitializing("configureDns", 0.8f)
            configureDns()

            updateInitializing("configureEnvironment", 0.85f)
            configureEnvironment()

            updateInitializing("createWorkspace", 0.9f)
            createWorkspace()

            updateInitializing("runHealthCheck", 0.95f)
            val health = healthChecker.check()
            if (!health.isHealthy) {
                val message = "Runtime health check failed after initialization: ${health.detail.orEmpty()}"
                logger.e(message)
                val error = AppError(ErrorCode.INSTALLATION_FAILED, message)
                failInitialization(error)
                return@withContext AppResult.Failure(error)
            }

            _state.value = RuntimeState.Ready
            logger.i("Linux runtime initialized and ready")
            AppResult.Success(Unit)
            } catch (cancellation: CancellationException) {
                _state.value = RuntimeState.NotInitialized
                throw cancellation
            } catch (throwable: Throwable) {
                logger.e("Linux runtime initialization failed", throwable)
                _state.value = RuntimeState.Error(throwable)
                AppResult.Failure(
                    AppError(
                        code = when (throwable) {
                            is InsufficientStorageException -> ErrorCode.INSUFFICIENT_STORAGE
                            else -> ErrorCode.INSTALLATION_FAILED
                        },
                        message = throwable.message ?: "Linux runtime initialization failed",
                        cause = throwable,
                    ),
                )
            }
        }
    }

    override suspend fun restoreInstalledState(): Boolean = withContext(Dispatchers.IO) {
        // 恢复失败绝不能静默：任何一环失败都要留下日志与 Error 状态，
        // 否则用户会被直接扔回引导页"重新安装"，且无从排查原因。
        if (!pathManager.isRootfsInstalled()) {
            // 全新安装或 RootFS 缺失：正常走引导页，不算错误。
            logger.i("Restore skipped: rootfs marker or validator check failed (fresh install?)")
            return@withContext false
        }
        if (!pathManager.isProotInstalled()) {
            val message = "已安装的 Linux 环境无法恢复：PRoot 运行组件不完整（APK 内 native 库缺失或不可读）"
            logger.e("Restore failed: $message")
            _state.value = RuntimeState.Error(IllegalStateException(message))
            return@withContext false
        }
        val health = try {
            healthChecker.check()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            logger.e("Restore failed: health check threw", throwable)
            _state.value = RuntimeState.Error(throwable)
            return@withContext false
        }
        if (!health.isHealthy) {
            val message = "已安装的 Linux 环境健康检查未通过：${health.detail.orEmpty()}"
            logger.e("Restore failed: $message")
            _state.value = RuntimeState.Error(IllegalStateException(message))
            return@withContext false
        }
        _state.value = RuntimeState.Ready
        logger.i("Linux runtime restored from disk and ready")
        true
    }

    override suspend fun updateRootfs(): AppResult<Unit> = initializeMutex.withLock {
        withContext(Dispatchers.IO) {
            if (_state.value !is RuntimeState.Ready) {
                return@withContext AppResult.Failure(
                    AppError(ErrorCode.INSTALLATION_FAILED, "只有已就绪的 Linux Runtime 才能更新"),
                )
            }
            try {
                processRegistry.stopAll()
                updateInitializing("更新 RootFS", 0.05f, "保留 /root 和 /opt/taixu 用户数据")
                val rawVersion = pathManager.rootfsVersion().orEmpty()
                val installedId = when {
                    rawVersion.startsWith("oci-5.7.0-") -> rawVersion.substringAfter("oci-5.7.0-").substringBefore('-')
                    rawVersion.startsWith("oci-5.6.0-") -> rawVersion.substringAfter("oci-5.6.0-").substringBefore('-')
                    else -> rawVersion.substringAfter("oci-").substringBefore('-')
                }.takeIf { it.isNotBlank() } ?: "ubuntu"
                val distribution = DistributionCatalog.require(installedId)
                val result = rootfsInstaller.updateOci(
                    distribution,
                    RegistryRoute.AUTO,
                ) { progress -> updateDownloadProgress("更新 ${distribution.displayName}", 0.1f, 0.65f, progress) }
                result.errorOrNull()?.let { error ->
                    _state.value = RuntimeState.Error(error.cause ?: IllegalStateException(error.message))
                    return@withContext AppResult.Failure(error)
                }

                updateInitializing("configureRootfs", 0.75f)
                configureRootfs()
                configureDns()
                configureEnvironment()
                updateInitializing("runHealthCheck", 0.9f)
                val health = healthChecker.check()
                if (!health.isHealthy) {
                    rootfsInstaller.rollbackPendingUpdate()
                    _state.value = RuntimeState.Error(
                        IllegalStateException("更新后健康检查失败：${health.detail.orEmpty()}"),
                    )
                    return@withContext AppResult.Failure(
                        AppError(
                            ErrorCode.INSTALLATION_FAILED,
                            "RootFS 更新后的健康检查失败，已恢复旧版本",
                        ),
                    )
                }
                rootfsInstaller.finalizePendingUpdate()
                _state.value = RuntimeState.Ready
                AppResult.Success(Unit)
            } catch (cancellation: CancellationException) {
                rootfsInstaller.rollbackPendingUpdate()
                _state.value = RuntimeState.Error(IllegalStateException("RootFS 更新已取消，旧版本已恢复"))
                throw cancellation
            } catch (throwable: Throwable) {
                rootfsInstaller.rollbackPendingUpdate()
                logger.e("Linux runtime update failed", throwable)
                _state.value = RuntimeState.Error(throwable)
                AppResult.Failure(
                    AppError(ErrorCode.INSTALLATION_FAILED, throwable.message ?: "RootFS 更新失败", throwable),
                )
            }
        }
    }

    override suspend fun healthCheck(): RuntimeHealth = withContext(Dispatchers.IO) {
        healthChecker.check()
    }

    override suspend fun execute(command: ShellCommand): CommandResult {
        ensureReady()
        return shellExecutor.execute(
            command = prootCommandBuilder.build(
                prootBinary = pathManager.activeProotFile(),
                rootfsDir = pathManager.rootfsDir,
                workspaceDir = pathManager.workspaceDir,
                homeDir = pathManager.homeDir,
                optDir = pathManager.taixuRootDir,
                command = command,
            ),
            timeoutMs = command.timeoutMs,
        )
    }

    override suspend fun startSession(config: SessionConfig): LinuxSession {
        ensureReady()
        val markerId = UUID.randomUUID().toString()
        val markerFile = File(pathManager.taixuRootDir, ".pty-$markerId")
        val markerPath = "/opt/taixu/.pty-$markerId"
        return try {
            if (ptyManager.nativeAvailable) {
                // 真 PTY 后端：JNI forkpty 提供控制终端，resize 由会话内 ioctl 完成。
                ptyManager.openNative(
                    command = prootCommandBuilder.buildInteractive(
                        prootBinary = pathManager.activeProotFile(),
                        rootfsDir = pathManager.rootfsDir,
                        workspaceDir = pathManager.workspaceDir,
                        homeDir = pathManager.homeDir,
                        optDir = pathManager.taixuRootDir,
                        config = config,
                        nativePty = true,
                    ),
                    hostEnvironment = pathManager.hostProcessEnvironment(),
                    config = config,
                    cleanup = { markerFile.delete() },
                )
            } else {
                ptyManager.open(
                    command = prootCommandBuilder.buildInteractive(
                        prootBinary = pathManager.activeProotFile(),
                        rootfsDir = pathManager.rootfsDir,
                        workspaceDir = pathManager.workspaceDir,
                        homeDir = pathManager.homeDir,
                        optDir = pathManager.taixuRootDir,
                        config = config,
                        ptyMarker = markerPath,
                    ),
                    hostEnvironment = pathManager.hostProcessEnvironment(),
                    config = config,
                    resize = { columns, rows ->
                        resizePty(markerPath, columns, rows)
                    },
                    cleanup = { markerFile.delete() },
                )
            }
        } catch (throwable: Throwable) {
            markerFile.delete()
            throw throwable
        }
    }

    private suspend fun resizePty(markerPath: String, columns: Int, rows: Int) {
        val safeColumns = columns.coerceIn(20, 400)
        val safeRows = rows.coerceIn(5, 200)
        runCatching {
            shellExecutor.execute(
                command = prootCommandBuilder.build(
                    prootBinary = pathManager.activeProotFile(),
                    rootfsDir = pathManager.rootfsDir,
                    workspaceDir = pathManager.workspaceDir,
                    homeDir = pathManager.homeDir,
                    optDir = pathManager.taixuRootDir,
                    command = ShellCommand(
                        commandLine = "if test -s '$markerPath'; then " +
                            "stty -F \"\$(cat '$markerPath')\" cols $safeColumns rows $safeRows; " +
                            "fi",
                        timeoutMs = 2_000L,
                    ),
                ),
                timeoutMs = 2_000L,
            )
        }
    }

    override suspend fun startBackground(
        id: String,
        command: ShellCommand,
        toolId: String?,
        type: ProcessType,
    ): ManagedProcess {
        ensureReady()
        return processRegistry.start(id, command, toolId, type)
    }

    override suspend fun stopBackground(id: String): Boolean = processRegistry.stop(id)

    override fun listBackground(): List<ManagedProcess> = processRegistry.list()

    override suspend fun cleanupDeadBackground(): Int = processRegistry.cleanupDeadProcesses()

    override suspend fun shutdown() {
        processRegistry.stopAll()
        _state.value = RuntimeState.NotInitialized
        logger.i("Linux runtime shut down")
    }

    override fun rootfsPath(): File = pathManager.rootfsDir

    override fun rootfsVersion(): String? = pathManager.rootfsVersion()

    override fun workspacePath(): File = pathManager.workspaceDir

    private fun detectArchitecture(): CpuArch {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return CpuArch.fromBuildAbi(abi)
    }

    private fun checkStorage() {
        val filesPath = pathManager.baseDir.parentFile?.absolutePath
            ?: pathManager.baseDir.absolutePath
        val statFs = StatFs(filesPath)
        val availableBytes = statFs.availableBytes
        if (availableBytes < MIN_FREE_BYTES) {
            throw InsufficientStorageException(
                "Not enough free space. Required at least ${MIN_FREE_BYTES / (1024 * 1024)} MB, " +
                    "available ${availableBytes / (1024 * 1024)} MB",
            )
        }
    }

    private fun configureRootfs() {
        val etcDir = File(pathManager.rootfsDir, "etc")
        etcDir.mkdirs()
        File(etcDir, "taixu-runtime").writeText("taixu-runtime=0.1.0\n")
        File(pathManager.rootfsDir, "opt/taixu").mkdirs()
        pathManager.migratePersistentDirectories()
        stripSetuidBits()
        configureDpkgStatoverride()
        configureDpkgNoDoc()
        configureAptSettings()
        configureChinaMirrors()
        configurePipMirror()
        installAptStripSetuidHook()
        installPerlFixScript()
    }

    /**
     * 在 PRoot 沙箱中排除文档/手册页/区域语言包解包：
     * 1. 降低 50%~60% 的磁盘 I/O 和解包时间，极大避免 300s 安装超时；
     * 2. 规避 man 手册硬链接（如 perlthanks.1.gz 等）在 PRoot 下的 chown 报错。
     */
    private fun configureDpkgNoDoc() {
        val dpkgCfgDir = File(pathManager.rootfsDir, "etc/dpkg/dpkg.cfg.d")
        dpkgCfgDir.mkdirs()
        File(dpkgCfgDir, "01_taixu_nodoc").writeText(
            """
            # PRoot 性能与稳定性优化：跳过文档与手册文件以减少 I/O 并避免硬链接解包异常
            path-exclude /usr/share/doc/*
            path-exclude /usr/share/man/*
            path-exclude /usr/share/groff/*
            path-exclude /usr/share/info/*
            path-exclude /usr/share/locale/*
            path-exclude /usr/share/lintian/*
            path-exclude /usr/share/linda/*
            """.trimIndent() + "\n",
        )
    }

    /**
     * 配置 apt 超时重试以及非交互默认选项，避免后台安装任务被挂起。
     */
    private fun configureAptSettings() {
        val hookDir = File(pathManager.rootfsDir, "etc/apt/apt.conf.d")
        hookDir.mkdirs()
        File(hookDir, "99taixu-apt-config").writeText(
            """
            // 提高网络波动环境下的安装鲁棒性并默认使用非交互配置
            Acquire::Retries "3";
            Acquire::http::Timeout "60";
            Acquire::https::Timeout "60";
            DPkg::Options {
               "--force-confdef";
               "--force-confold";
            };
            """.trimIndent() + "\n",
        )
    }

    /**
     * 将沙箱内软件源切换到清华大学 TUNA 镜像站，加速国内 apt / pip 安装。
     * 仅对 apt 系发行版（debian / ubuntu / kali）生效；其余发行版保持官方源。
     */
    private fun configureChinaMirrors() {
        val osRelease = readOsRelease()
        if (osRelease.isEmpty()) {
            logger.i("China mirrors skipped: os-release not found")
            return
        }
        val id = osRelease["ID"] ?: osRelease["ID_LIKE"]
        val codename = osRelease["VERSION_CODENAME"]
        val sources = when (id) {
            "debian" -> codename?.let(::tunaDebianSources)
            "ubuntu" -> codename?.let(::tunaUbuntuSources)
            "kali" -> tunaKaliSources()
            else -> null
        }
        if (sources == null) {
            logger.i("China mirrors skipped for distro id=$id codename=$codename")
            return
        }
        val sourcesListDir = File(pathManager.rootfsDir, "etc/apt/sources.list.d")
        sourcesListDir.mkdirs()
        // 停用镜像自带官方源（改名为 apt 不识别的扩展名，可随时改回），避免与新源冲突。
        disableStockAptSources(sourcesListDir)
        File(sourcesListDir, "taixu-mirrors.list").writeText(sources + "\n")
        logger.i("China mirrors applied: TUNA apt sources for $id ($codename)")
    }

    /**
     * 停用 rootfs 自带的官方 apt 源：/etc/apt/sources.list 与 sources.list.d 下的
     * *.list / *.sources 全部改名为 *.taixu-disabled（apt 只识别 .list / .sources 后缀）。
     */
    private fun disableStockAptSources(sourcesListDir: File) {
        File(pathManager.rootfsDir, "etc/apt/sources.list").takeIf { it.isFile }
            ?.let { stock -> renameToDisabled(stock) }
        sourcesListDir.listFiles()?.forEach { entry ->
            val name = entry.name
            if (entry.isFile && (name.endsWith(".list") || name.endsWith(".sources")) &&
                name != "taixu-mirrors.list"
            ) {
                renameToDisabled(entry)
            }
        }
    }

    private fun renameToDisabled(file: File) {
        val disabled = File(file.parentFile, "${file.name}.taixu-disabled")
        if (!file.renameTo(disabled)) {
            logger.w("Failed to disable stock apt source: ${file.path}")
        }
    }

    private fun tunaDebianSources(codename: String): String = """
        # TaiXu: 清华大学 TUNA 镜像站（由官方源自动切换）
        deb https://mirrors.tuna.tsinghua.edu.cn/debian $codename main contrib non-free non-free-firmware
        deb https://mirrors.tuna.tsinghua.edu.cn/debian $codename-updates main contrib non-free non-free-firmware
        deb https://mirrors.tuna.tsinghua.edu.cn/debian-security $codename-security main contrib non-free non-free-firmware
    """.trimIndent()

    private fun tunaUbuntuSources(codename: String): String = """
        # TaiXu: 清华大学 TUNA 镜像站（由官方源自动切换）
        deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu $codename main restricted universe multiverse
        deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu $codename-updates main restricted universe multiverse
        deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu $codename-security main restricted universe multiverse
        deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu $codename-backports main restricted universe multiverse
    """.trimIndent()

    private fun tunaKaliSources(): String = """
        # TaiXu: 清华大学 TUNA 镜像站（由官方源自动切换）
        deb https://mirrors.tuna.tsinghua.edu.cn/kali kali-rolling main contrib non-free
    """.trimIndent()

    /**
     * 预置 pip 清华镜像配置（文件在 pip 安装后自动生效，未装 python 时无副作用）。
     */
    private fun configurePipMirror() {
        val config = File(pathManager.rootfsDir, "etc/pip.conf")
        config.parentFile?.mkdirs()
        config.writeText(
            """
            # TaiXu: 清华大学 TUNA PyPI 镜像
            [global]
            index-url = https://pypi.tuna.tsinghua.edu.cn/simple
            """.trimIndent() + "\n",
        )
    }

    /** 解析 rootfs 的 os-release（KEY=VALUE，值可能带引号），失败返回空 Map。 */
    private fun readOsRelease(): Map<String, String> {
        val file = File(pathManager.rootfsDir, "etc/os-release")
            .takeIf { it.isFile }
            ?: File(pathManager.rootfsDir, "usr/lib/os-release").takeIf { it.isFile }
            ?: return emptyMap()
        return runCatching {
            file.readLines().mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) return@mapNotNull null
                val key = line.substring(0, index).trim()
                val value = line.substring(index + 1).trim().trim('"', '\'')
                key to value
            }.toMap()
        }.onFailure { logger.w("Failed to parse os-release", it) }
            .getOrDefault(emptyMap())
    }

    /**
     * 预置 perl 硬链接自愈脚本，用于在 dpkg 因 perlthanks 等硬链接报错时一键修复。
     */
    private fun installPerlFixScript() {
        val binDir = File(pathManager.rootfsDir, "usr/local/sbin")
        binDir.mkdirs()
        val script = File(binDir, "taixu-fix-perl")
        script.writeText(
            """
            #!/bin/sh
            set -e
            echo "[TaiXu] Scanning for perl deb packages to patch hardlinks..."
            TMPDIR="${'$'}(mktemp -d /tmp/taixu-perl-fix.XXXXXX)"
            trap 'rm -rf "${'$'}TMPDIR"' EXIT INT TERM

            # 优先从 apt 缓存寻找 perl deb 包，若无则尝试下载
            PERL_DEB="${'$'}(ls -1 /var/cache/apt/archives/perl_*.deb 2>/dev/null | head -n 1 || true)"
            if [ -z "${'$'}PERL_DEB" ]; then
                echo "[TaiXu] Downloading perl package..."
                cd "${'$'}TMPDIR" && apt-get download perl || true
                PERL_DEB="${'$'}(ls -1 "${'$'}TMPDIR"/perl_*.deb 2>/dev/null | head -n 1 || true)"
            fi

            if [ -z "${'$'}PERL_DEB" ] || [ ! -f "${'$'}PERL_DEB" ]; then
                echo "[TaiXu] Perl deb package not found, attempting apt --fix-broken install..."
                apt-get --fix-broken install -y || true
                exit 0
            fi

            echo "[TaiXu] Patching ${'$'}PERL_DEB..."
            WORKDIR="${'$'}TMPDIR/repack"
            mkdir -p "${'$'}WORKDIR/DEBIAN"
            dpkg-deb -e "${'$'}PERL_DEB" "${'$'}WORKDIR/DEBIAN"
            dpkg-deb --fsys-tarfile "${'$'}PERL_DEB" | tar -C "${'$'}WORKDIR" -xf -

            # 将 usr/bin/perlthanks 等硬链接转换为符号链接
            if [ -f "${'$'}WORKDIR/usr/bin/perlthanks" ]; then
                rm -f "${'$'}WORKDIR/usr/bin/perlthanks"
                ln -s perlbug "${'$'}WORKDIR/usr/bin/perlthanks"
                echo "[TaiXu] Converted /usr/bin/perlthanks to symlink"
            fi

            dpkg-deb -b "${'$'}WORKDIR" "${'$'}TMPDIR/perl-patched.deb"
            dpkg -i --force-overwrite "${'$'}TMPDIR/perl-patched.deb"
            echo "[TaiXu] Perl patch installed successfully."
            """.trimIndent() + "\n",
        )
        runCatching {
            android.system.Os.chmod(script.absolutePath, 0x1ED) // 0755
        }
    }

    /**
     * PRoot 下 setuid 不生效（无真实 root），但 dpkg 解包含 setuid 文件的包时，
     * 对 setuid 的 *.dpkg-tmp 残留做 unlink 会失败（ENOENT），导致
     * util-linux/login/mount 等包升级卡死在 "unable to securely remove"。
     * 预防：初始化/更新 rootfs 后直接在宿主侧清掉全部文件的 setuid/setgid 位
     * （对功能零影响），并清理任何意外遗留的 *.dpkg-tmp 文件。
     */
    private fun stripSetuidBits() {
        var stripped = 0
        var failed = 0
        pathManager.rootfsDir.walkTopDown()
            .forEach { file ->
                if (file.name.endsWith(".dpkg-tmp")) {
                    file.delete()
                } else if (file.isFile) {
                    runCatching {
                        val mode = android.system.Os.lstat(file.absolutePath).st_mode
                        // 0xC00 = setuid(0x800)|setgid(0x400)；0x3FF = rwx+sticky，仅清除 s 位
                        if (mode and 0xC00 != 0) {
                            android.system.Os.chmod(file.absolutePath, mode and 0x3FF)
                            stripped++
                        }
                    }.onFailure { failed++ }
                }
            }
        logger.i("stripSetuidBits: cleared $stripped setuid/setgid bits, $failed failures")
    }

    /**
     * 在 dpkg 数据库中预置 statoverride，强制将 setuid 二进制覆写为 0755 普通权限。
     * 这样 dpkg 在 unpack deb 时直接以 0755 解包写入，不会尝试设置 4755 s位，从根源避免产生 setuid 临时文件。
     */
    private fun configureDpkgStatoverride() {
        val dpkgDir = File(pathManager.rootfsDir, "var/lib/dpkg")
        dpkgDir.mkdirs()
        val statoverrideFile = File(dpkgDir, "statoverride")
        val overrides = listOf(
            "/usr/bin/su",
            "/usr/bin/mount",
            "/usr/bin/umount",
            "/usr/bin/newgrp",
            "/usr/bin/gpasswd",
            "/usr/bin/passwd",
            "/usr/bin/chfn",
            "/usr/bin/chsh",
            "/usr/bin/expiry",
        )
        val existingLines = if (statoverrideFile.exists()) {
            statoverrideFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        } else {
            mutableSetOf()
        }
        overrides.forEach { path ->
            val entry = "root root 0755 $path"
            val pathSuffix = " $path"
            if (existingLines.none { it.endsWith(pathSuffix) }) {
                existingLines.add(entry)
            }
        }
        statoverrideFile.writeText(existingLines.sorted().joinToString("\n", postfix = "\n"))
    }

    /**
     * dpkg 每次安装新版本都会按包元数据把 setuid 位设回去，一次性清理只够第一次升级。
     * 通过 apt 的 DPkg::Pre-Invoke 和 DPkg::Post-Invoke 钩子：
     * 1. Pre-Invoke: 预先清理可能因异常中断残留的 *.dpkg-tmp 临时文件
     * 2. Post-Invoke: 安装完成后自动将新增或恢复的 setuid/setgid 文件再清一遍
     * 形成自维持：任何时刻磁盘上的文件都是非 setuid，升级永不卡死。
     */
    private fun installAptStripSetuidHook() {
        val hookDir = File(pathManager.rootfsDir, "etc/apt/apt.conf.d")
        hookDir.mkdirs()
        // 注意：find 谓词用 -perm /6000 形式（任一位命中），避免 \( \) 转义与 Kotlin 字符串冲突
        val findCommand = "find /usr /bin /sbin -xdev -type f -perm /6000 -exec chmod ug-s {} +"
        val cleanupTmp = "rm -f /bin/*.dpkg-tmp /usr/bin/*.dpkg-tmp /usr/sbin/*.dpkg-tmp /sbin/*.dpkg-tmp 2>/dev/null || true"
        File(hookDir, "99taixu-strip-setuid").writeText(
            "// PRoot 沙箱：setuid 不生效，保留会导致 dpkg 升级卡死（见 taixu-runtime 文档）。\n" +
                "DPkg::Pre-Invoke { \"$cleanupTmp\"; };\n" +
                "DPkg::Post-Invoke { \"$findCommand\"; };\n",
        )
    }

    private fun configureDns() {
        val etcDir = File(pathManager.rootfsDir, "etc")
        etcDir.mkdirs()
        File(etcDir, "resolv.conf").writeText(
            "nameserver 1.1.1.1\nnameserver 8.8.8.8\n",
        )
    }

    private fun configureEnvironment() {
        val profileDir = File(pathManager.rootfsDir, "etc/profile.d")
        profileDir.mkdirs()
        File(profileDir, "taixu-env.sh").writeText(
            """
            export LANG=C.UTF-8
            export PATH=/root/.local/bin:/opt/taixu/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            export HOME=/root
            """.trimIndent() + "\n",
        )
        // 1. 大型仓库的 git 浅克隆走移动网络代理时，HTTP/2 多路复用流容易在中途被
        //    截断，报 “bad object / remote did not send all necessary objects”。
        //    强制 HTTP/1.1 让每次传输走单连接，显著降低截断概率。
        // 2. PRoot 伪造 UID 0 与宿主 workspace 挂载点 UID 不一致时，Git 会触发
        //    dubious ownership 校验；配置 safe.directory = * 全局信任。
        pathManager.homeDir.mkdirs()
        File(pathManager.homeDir, ".gitconfig").writeText(
            """
            [safe]
            	directory = *
            [http]
            	version = HTTP/1.1
            """.trimIndent() + "\n",
        )
    }

    private fun createWorkspace() {
        pathManager.workspaceDir.mkdirs()
    }

    private fun ensureReady() {
        if (_state.value !is RuntimeState.Ready) {
            throw IllegalStateException("Linux runtime is not ready. Call initialize() first.")
        }
    }

    private fun updateInitializing(step: String, progress: Float, detail: String? = null) {
        _state.value = RuntimeState.Initializing(
            step = step,
            progress = progress.coerceIn(0f, 1f),
            detail = detail,
        )
    }

    private fun updateDownloadProgress(
        step: String,
        baseProgress: Float,
        progressSpan: Float,
        progress: DownloadProgress,
    ) {
        val fraction = progress.fraction ?: 0f
        val scaled = baseProgress + fraction * progressSpan
        val detail = if (progress.totalMegabytes != null) {
            "${progress.downloadedMegabytes} / ${progress.totalMegabytes} MB"
        } else {
            "已下载 ${progress.downloadedMegabytes} MB"
        }
        updateInitializing(step, scaled, detail)
    }

    private fun failInitialization(error: AppError) {
        _state.value = RuntimeState.Error(
            error.cause ?: IllegalStateException(error.message),
        )
    }

    private class RuntimeArchitectureException(message: String) : IllegalStateException(message)

    private class InsufficientStorageException(message: String) : IllegalStateException(message)

    companion object {
        const val MIN_FREE_BYTES = 600L * 1024L * 1024L
    }
}
