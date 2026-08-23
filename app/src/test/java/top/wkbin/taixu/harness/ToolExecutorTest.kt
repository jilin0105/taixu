package top.wkbin.taixu.harness

import top.wkbin.taixu.core.security.SecretRedactor
import top.wkbin.taixu.core.network.DownloadEvent
import top.wkbin.taixu.core.network.DownloadRequest
import top.wkbin.taixu.core.network.FileDownloader
import top.wkbin.taixu.runtime.FakeLinuxRuntime
import top.wkbin.taixu.runtime.shell.CommandResult
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
    private lateinit var downloader: RecordingDownloader

    private fun toolCall(tool: HarnessTool, args: kotlinx.serialization.json.JsonObject) =
        ToolCall(UUID.randomUUID().toString(), 0L, tool, args)

    @Before
    fun setUp() {
        val root = temporaryFolder.newFolder("workspace")
        runtime = FakeLinuxRuntime()
        downloader = RecordingDownloader()
        executor = ToolExecutor(WorkspaceFileAccess(root), runtime, SecretRedactor(), downloader)
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

    @Test
    fun `download tool uses built in downloader and workspace destination`() = runBlocking {
        val result = executor.execute(toolCall(HarnessTool.DOWNLOAD, buildJsonObject {
            put("url", "https://example.com/archive.tar.gz")
            put("destination", "dist/archive.tar.gz")
            put("max_attempts", 2)
        }))

        assertTrue(result.success)
        assertTrue(result.output.contains("dist/archive.tar.gz"))
        assertTrue(result.output.contains("断点续传"))
        assertEquals("https://example.com/archive.tar.gz", downloader.request.url)
        assertEquals("payload", File(downloader.request.destination.absolutePath).readText())
    }

    @Test
    fun `download tool rejects workspace escape`() = runBlocking {
        val result = executor.execute(toolCall(HarnessTool.DOWNLOAD, buildJsonObject {
            put("url", "https://example.com/archive.tar.gz")
            put("destination", "../outside.tar.gz")
        }))

        assertFalse(result.success)
        assertTrue(result.output.contains("路径越界"))
    }

    @Test
    fun `download tool reports progress while download is active`() = runBlocking {
        val progress = mutableListOf<String>()
        executor.execute(
            toolCall(HarnessTool.DOWNLOAD, buildJsonObject {
                put("url", "https://example.com/archive.tar.gz")
                put("destination", "dist/archive.tar.gz")
            }),
            progressReporter = { progress += it },
        )

        assertTrue(progress.any { it.startsWith("下载中：") })
        assertTrue(progress.any { it.contains("100%") })
    }

    private class RecordingDownloader : FileDownloader {
        lateinit var request: DownloadRequest

        override fun download(request: DownloadRequest): Flow<DownloadEvent> = flow {
            this@RecordingDownloader.request = request
            request.destination.parentFile?.mkdirs()
            request.destination.writeText("payload")
            emit(DownloadEvent.Started)
            emit(DownloadEvent.Progress(7L, 7L))
            emit(DownloadEvent.Completed(request.destination))
        }
    }
}
