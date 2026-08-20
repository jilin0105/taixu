package top.wkbin.taixu.harness

import top.wkbin.taixu.core.security.SecretRedactor
import top.wkbin.taixu.runtime.FakeLinuxRuntime
import top.wkbin.taixu.runtime.shell.CommandResult
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ToolExecutorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var runtime: FakeLinuxRuntime
    private lateinit var executor: ToolExecutor

    private fun toolCall(tool: HarnessTool, args: kotlinx.serialization.json.JsonObject) =
        ToolCall(UUID.randomUUID().toString(), 0L, tool, args)

    @Before
    fun setUp() {
        val root = temporaryFolder.newFolder("workspace")
        runtime = FakeLinuxRuntime()
        executor = ToolExecutor(WorkspaceFileAccess(root), runtime, SecretRedactor())
    }

    @Test
    fun `read tool returns file content`() = runBlocking {
        executor.execute(toolCall(HarnessTool.WRITE, buildJsonObject {
            put("path", "hello.txt")
            put("content", "hi")
        }))
        val result = executor.execute(toolCall(HarnessTool.READ, buildJsonObject {
            put("path", "hello.txt")
        }))
        assertTrue(result.success)
        assertEquals("hi", result.output)
    }

    @Test
    fun `edit tool modifies file`() = runBlocking {
        executor.execute(toolCall(HarnessTool.WRITE, buildJsonObject {
            put("path", "a.txt")
            put("content", "one two")
        }))
        val result = executor.execute(toolCall(HarnessTool.EDIT, buildJsonObject {
            put("path", "a.txt")
            put("oldText", "two")
            put("newText", "2")
        }))
        assertTrue(result.success)
        val read = executor.execute(toolCall(HarnessTool.READ, buildJsonObject {
            put("path", "a.txt")
        }))
        assertEquals("one 2", read.output)
    }

    @Test
    fun `read tool failure is structured not thrown`() = runBlocking {
        val result = executor.execute(toolCall(HarnessTool.READ, buildJsonObject {
            put("path", "/etc/passwd")
        }))
        assertFalse(result.success)
        assertTrue(result.output.isNotEmpty())
    }

    @Test
    fun `missing argument produces structured failure`() = runBlocking {
        val result = executor.execute(toolCall(HarnessTool.READ, buildJsonObject {}))
        assertFalse(result.success)
        assertTrue(result.output.contains("缺少参数"))
    }

    @Test
    fun `base tool runs command through runtime`() = runBlocking {
        runtime.commandResults["uname -m"] = CommandResult(0, "aarch64", "", 5)
        val result = executor.execute(toolCall(HarnessTool.BASE, buildJsonObject {
            put("command", "uname -m")
        }))
        assertTrue(result.success)
        assertTrue(result.output.contains("aarch64"))
        assertTrue(runtime.executedCommands.contains("uname -m"))
    }

    @Test
    fun `base tool reports non-zero exit`() = runBlocking {
        runtime.commandResults["false"] = CommandResult(1, "", "boom", 3)
        val result = executor.execute(toolCall(HarnessTool.BASE, buildJsonObject {
            put("command", "false")
        }))
        assertFalse(result.success)
        assertTrue(result.output.contains("exit 1"))
        assertTrue(result.output.contains("boom"))
    }

    @Test
    fun `base tool passes working directory`() = runBlocking {
        executor.execute(toolCall(HarnessTool.BASE, buildJsonObject {
            put("command", "pwd")
            put("cwd", "/workspace/proj")
        }))
        assertEquals("/workspace/proj", runtime.executedShellCommands.single().workingDirectory)
    }
}
