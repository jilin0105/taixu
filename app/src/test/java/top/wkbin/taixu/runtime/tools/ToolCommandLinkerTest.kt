package top.wkbin.taixu.runtime.tools

import top.wkbin.taixu.runtime.FakeLinuxRuntime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCommandLinkerTest {
    @Test
    fun createsStableBinShimForToolCommand() = runBlocking {
        val runtime = FakeLinuxRuntime()
        val linker = ToolCommandLinker(runtime)

        val result = linker.link(
            command = "openclaw",
            target = "/opt/taixu/tools/openclaw/bin/openclaw",
        )

        assertTrue(result.isSuccess)
        val command = runtime.executedCommands.single()
        assertTrue(command.contains("/opt/taixu/bin/openclaw"))
        assertTrue(command.contains("exec /opt/taixu/tools/openclaw/bin/openclaw \"\$@\""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafeCommandName() {
        runBlocking {
        ToolCommandLinker(FakeLinuxRuntime()).link("../openclaw", "/tmp/openclaw")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafeTargetPath() {
        runBlocking {
            ToolCommandLinker(FakeLinuxRuntime()).link("openclaw", "/tmp/unsafe path")
        }
    }
}
