package top.wkbin.taixu.runtime.proot

import top.wkbin.taixu.runtime.EnvironmentResolver
import top.wkbin.taixu.runtime.shell.ShellCommand
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProotCommandBuilder @Inject constructor(
    private val environmentResolver: EnvironmentResolver,
) {

    fun build(
        prootBinary: File,
        rootfsDir: File,
        workspaceDir: File,
        homeDir: File = File(rootfsDir.parentFile, "home"),
        optDir: File = File(rootfsDir.parentFile, "opt/taixu"),
        command: ShellCommand,
        mounts: List<top.wkbin.taixu.core.model.StorageMountBinding> = emptyList(),
    ): List<String> = buildList {
        add(prootBinary.absolutePath)
        add("--kill-on-exit")
        add("--link2symlink")
        add("--sysvipc")
        add("--kernel-release=$GUEST_KERNEL_RELEASE")
        add("--change-id=0:0")
        add("-r")
        add(rootfsDir.absolutePath)
        add("-b")
        add("/dev")
        add("-b")
        add("/proc")
        add("-b")
        add("/sys")
        add("-b")
        add("${File(rootfsDir.parentFile, "tmp").absolutePath}:/tmp")
        add("-b")
        add("${workspaceDir.absolutePath}:/workspace")
        add("-b")
        add("${homeDir.absolutePath}:/root")
        add("-b")
        add("${optDir.absolutePath}:/opt/taixu")
        addHostSystemBindings()
        addStorageMountBindings(mounts)
        add("-w")
        add(command.workingDirectory)
        add(GUEST_SHELL)
        add("-lc")
        add(
            shellCommand(
                commandLine = command.commandLine,
                environment = environmentResolver.merge(provider = command.environment),
            ),
        )
    }

    fun buildInteractive(
        prootBinary: File,
        rootfsDir: File,
        workspaceDir: File,
        homeDir: File = File(rootfsDir.parentFile, "home"),
        optDir: File = File(rootfsDir.parentFile, "opt/taixu"),
        config: top.wkbin.taixu.runtime.shell.SessionConfig,
        ptyMarker: String? = null,
        nativePty: Boolean = false,
        mounts: List<top.wkbin.taixu.core.model.StorageMountBinding> = emptyList(),
    ): List<String> = buildList {
        val columns = config.columns.coerceIn(20, 400)
        val rows = config.rows.coerceIn(5, 200)
        add(prootBinary.absolutePath)
        add("--kill-on-exit")
        add("--link2symlink")
        add("--sysvipc")
        add("--kernel-release=$GUEST_KERNEL_RELEASE")
        add("--change-id=0:0")
        add("-r")
        add(rootfsDir.absolutePath)
        add("-b")
        add("/dev")
        add("-b")
        add("/proc")
        add("-b")
        add("/sys")
        add("-b")
        add("${File(rootfsDir.parentFile, "tmp").absolutePath}:/tmp")
        add("-b")
        add("${workspaceDir.absolutePath}:/workspace")
        add("-b")
        add("${homeDir.absolutePath}:/root")
        add("-b")
        add("${optDir.absolutePath}:/opt/taixu")
        addHostSystemBindings()
        addStorageMountBindings(mounts)
        add("-w")
        add(config.workingDirectory)
        add(GUEST_SHELL)
        add("-lc")
        val interactiveCommand = config.commandLine.replace("'", "'\\\"'\\\"'")
        val environment = environmentResolver.merge(
            provider = config.environment,
            interactive = true,
        )
        if (nativePty) {
            // 真 PTY：App 侧 JNI master/slave 已提供控制终端与行规则，不再套
            // Debian `script` 包装，避免二次 PTY 与 stty 注入。
            add(shellCommand(commandLine = interactiveCommand, environment = environment))
        } else {
            val marker = ptyMarker?.also {
                require(PTY_MARKER.matches(it)) { "PTY marker path is invalid" }
            }
            val markerPrelude = marker?.let { "tty > $it; " }.orEmpty()
            add(
                shellCommand(
                    commandLine =
                        "if command -v script >/dev/null 2>&1; then " +
                            "exec script -qfec '$markerPrelude stty cols $columns rows $rows; " +
                            "$interactiveCommand' /dev/null; " +
                            "else $interactiveCommand; fi",
                    environment = environment,
                ),
            )
        }
    }

    private fun shellCommand(
        commandLine: String,
        environment: Map<String, String>,
    ): String {
        val exports = environment.entries.joinToString("; ") { (key, value) ->
            require(ENVIRONMENT_KEY.matches(key)) { "Invalid environment variable name: $key" }
            "export $key=${shellQuote(value)}"
        }
        return if (exports.isBlank()) commandLine else "$exports; $commandLine"
    }

    private fun shellQuote(value: String): String =
        "'${value.replace("'", "'\\\''")}'"

    /** Android host paths used by the PRoot tracer and Android linker. */
    private fun MutableList<String>.addHostSystemBindings() {
        listOf(
            "/apex",
            "/data/app",
            "/data/dalvik-cache",
            "/data/misc/apexdata/com.android.art/dalvik-cache",
            "/system",
            "/system_ext",
            "/vendor",
            "/product",
            "/odm",
            "/linkerconfig/com.android.art/ld.config.txt",
            "/linkerconfig/ld.config.txt",
            "/plat_property_contexts",
            "/property_contexts",
        ).forEach { path ->
            val hostPath = File(path)
            if (hostPath.exists() && hostPath.canRead()) {
                add("-b")
                add(path)
            }
        }
    }

    /** 宿主外部存储映射绑定 (如 /storage/emulated/0/Download -> /sdcard/Download) */
    private fun MutableList<String>.addStorageMountBindings(
        mounts: List<top.wkbin.taixu.core.model.StorageMountBinding>,
    ) {
        if (mounts.isNotEmpty()) {
            mounts.filter { it.enabled }.forEach { binding ->
                val hostDir = File(binding.hostPath)
                if (hostDir.exists()) {
                    add("-b")
                    add("${hostDir.absolutePath}:${binding.guestPath}")
                }
            }
        } else {
            // 默认自动探测并保障基础挂载
            val download = File("/storage/emulated/0/Download")
            if (download.exists()) {
                add("-b")
                add("${download.absolutePath}:/sdcard/Download")
            }
            val docs = File("/storage/emulated/0/Documents")
            if (docs.exists()) {
                add("-b")
                add("${docs.absolutePath}:/sdcard/Documents")
            }
            val sdcard = File("/storage/emulated/0")
            if (sdcard.exists()) {
                add("-b")
                add("${sdcard.absolutePath}:/sdcard")
            }
        }
    }

    private companion object {
        const val GUEST_SHELL = "/bin/sh"
        const val GUEST_KERNEL_RELEASE = "6.17.0-TaiXu"
        val PTY_MARKER = Regex("/opt/taixu/\\.pty-[A-Za-z0-9-]{8,64}")
        val ENVIRONMENT_KEY = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}
