package top.wkbin.taixu.runtime.proot

import top.wkbin.taixu.runtime.shell.ShellCommand
import top.wkbin.taixu.runtime.shell.SessionConfig
import top.wkbin.taixu.runtime.EnvironmentResolver
import top.wkbin.taixu.core.model.StorageMountBinding
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class ProotCommandBuilderTest {

    @Test
    fun buildAddsQemuOnlyWhenAnExecutableEmulatorIsProvided() {
        val emulator = Files.createTempFile("qemu-x86_64", ".bin").toFile().apply {
            setExecutable(true)
        }
        try {
            val args = ProotCommandBuilder(EnvironmentResolver()).build(
                prootBinary = File("/p"),
                rootfsDir = File("/compat/rootfs"),
                workspaceDir = File("/w"),
                command = ShellCommand(commandLine = "true"),
                emulatorBinary = emulator,
            )
            val index = args.indexOf("-q")
            assert(index >= 0)
            assertEquals(emulator.absolutePath, args[index + 1])
        } finally {
            emulator.delete()
        }
    }

    @Test
    fun buildProducesProotArgsAndShellCommand() {
        val builder = ProotCommandBuilder(EnvironmentResolver())
        val args = builder.build(
            prootBinary = File("/data/data/app/bin/proot"),
            rootfsDir = File("/data/data/app/rootfs"),
            workspaceDir = File("/data/data/app/workspace"),
            command = ShellCommand(commandLine = "cat /etc/os-release"),
        )

        assertEquals(File("/data/data/app/bin/proot").absolutePath, args[0])
        assert(args.contains("--kill-on-exit"))
        assert(args.contains("--link2symlink"))
        assert(args.contains("-L"))
        assert(args.contains("--sysvipc"))
        assert(args.contains("--kernel-release=6.17.0-TaiXu"))
        assert(args.contains("--change-id=0:0"))
        val l2sBackingStore = File(File("/data/data/app/rootfs"), ".l2s").absolutePath
        assertBinding(args, "$l2sBackingStore:$l2sBackingStore")
        assertBinding(args, "/dev")
        assertBinding(args, "/proc")
        assertBinding(args, "/sys")
        assertBinding(args, "${File("/data/data/app/tmp").absolutePath}:/tmp")
        assertBinding(args, "${File("/data/data/app/workspace").absolutePath}:/workspace")
        assertBinding(args, "${File("/data/data/app/home").absolutePath}:/root")
        assertBinding(args, "${File("/data/data/app/opt/taixu").absolutePath}:/opt/taixu")
        val workingDirectoryIndex = args.indexOf("-w")
        assertEquals("/root", args[workingDirectoryIndex + 1])
        assertEquals("/bin/sh", args[workingDirectoryIndex + 2])
        assertEquals("-lc", args[workingDirectoryIndex + 3])
        assert(args[workingDirectoryIndex + 4].contains("export HOME='/root'"))
        assert(args[workingDirectoryIndex + 4].contains("export TMPDIR='/tmp'"))
        assert(args[workingDirectoryIndex + 4].contains("cat /etc/os-release"))
    }

    @Test
    fun buildIncludesCustomEnvironmentBeforeCommand() {
        val builder = ProotCommandBuilder(EnvironmentResolver())
        val args = builder.build(
            prootBinary = File("/p"),
            rootfsDir = File("/r"),
            workspaceDir = File("/w"),
            command = ShellCommand(
                commandLine = "echo hi",
                environment = mapOf("FOO" to "bar"),
            ),
        )

        assert(args.last().contains("export FOO='bar'"))
    }

    @Test
    fun interactiveBuildUsesAdapterCommandAndEnvironment() {
        val args = ProotCommandBuilder(EnvironmentResolver()).buildInteractive(
            prootBinary = File("/p"),
            rootfsDir = File("/r"),
            workspaceDir = File("/w"),
            config = SessionConfig(
                commandLine = "export PATH=/root/.local/bin:\$PATH && exec codex",
                environment = mapOf("OPENAI_API_KEY" to "secret"),
                allowSttyResize = false,
            ),
            ptyMarker = "/opt/taixu/.pty-12345678",
        )

        assert(args.last().contains("exec codex"))
        assert(args.last().contains("export OPENAI_API_KEY='secret'"))
        assert(args.last().contains("/root/.local/bin:\$PATH"))
        assert(args.last().contains("tty > /opt/taixu/.pty-12345678"))
        assert(args.contains("-L"))
        val l2sBackingStore = File(File("/r"), ".l2s").absolutePath
        assertBinding(args, "$l2sBackingStore:$l2sBackingStore")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafePtyMarker() {
        ProotCommandBuilder(EnvironmentResolver()).buildInteractive(
            prootBinary = File("/p"),
            rootfsDir = File("/r"),
            workspaceDir = File("/w"),
            config = SessionConfig(),
            ptyMarker = "/tmp/unsafe",
        )
    }

    @Test
    fun doesNotImplicitlyMountSharedStorage() {
        val args = ProotCommandBuilder(EnvironmentResolver()).build(
            prootBinary = File("/p"),
            rootfsDir = File("/r"),
            workspaceDir = File("/w"),
            command = ShellCommand(commandLine = "true"),
            mounts = emptyList(),
        )

        assert(args.none { it.contains(":/sdcard") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMountOverCriticalGuestPath() {
        ProotCommandBuilder(EnvironmentResolver()).build(
            prootBinary = File("/p"),
            rootfsDir = File("/r"),
            workspaceDir = File("/w"),
            command = ShellCommand(commandLine = "true"),
            mounts = listOf(
                StorageMountBinding("bad", "bad", "/storage/emulated/0", "/root", enabled = true),
            ),
        )
    }

    private fun assertBinding(args: List<String>, binding: String) {
        val index = args.indexOf(binding)
        assert(index > 0)
        assertEquals("-b", args[index - 1])
    }
}
