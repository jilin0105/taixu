package top.wkbin.taixu.harness

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceFileAccessTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var root: File
    private lateinit var access: WorkspaceFileAccess

    @Before
    fun setUp() {
        root = temporaryFolder.newFolder("workspace")
        access = WorkspaceFileAccess(root)
    }

    private fun runTest(block: suspend () -> Unit) =
        kotlinx.coroutines.runBlocking { block() }

    @Test
    fun `write then read round trip`() = runTest {
        val write = access.write("notes.md", "hello world")
        assertTrue(write.isSuccess)
        val read = access.read("notes.md")
        assertTrue(read.isSuccess)
        assertEquals("hello world", read.getOrNull())
    }

    @Test
    fun `accepts workspace-prefixed linux path`() = runTest {
        access.write("/workspace/sub/a.txt", "content")
        val read = access.read("/workspace/sub/a.txt")
        assertEquals("content", read.getOrNull())
    }

    @Test
    fun `rejects parent traversal`() = runTest {
        val outside = temporaryFolder.newFile("secret.txt")
        outside.writeText("secret")
        assertFalse(access.read("../secret.txt").isSuccess)
        assertFalse(access.read("/workspace/../../secret.txt").isSuccess)
        assertFalse(access.read("sub/../../secret.txt").isSuccess)
    }

    @Test
    fun `rejects absolute paths outside workspace`() = runTest {
        assertFalse(access.read("/etc/passwd").isSuccess)
        assertFalse(access.read("/data/local/tmp/x").isSuccess)
        assertFalse(access.write("/tmp/pwned", "x").isSuccess)
    }

    @Test
    fun `rejects symlink escaping workspace`() = runTest {
        val outside = temporaryFolder.newFile("target.txt")
        outside.writeText("outside-data")
        val link = File(root, "escape")
        val created = try {
            Files.createSymbolicLink(link.toPath(), outside.toPath())
            true
        } catch (throwable: Throwable) {
            false // Windows 无开发者模式时无法创建符号链接，跳过
        }
        org.junit.Assume.assumeTrue("symlink creation unsupported", created)
        assertFalse(access.read("escape").isSuccess)
    }

    @Test
    fun `edit replaces unique occurrence`() = runTest {
        access.write("a.txt", "one two one")
        val result = access.edit("a.txt", "two", "TWO")
        assertTrue(result.isSuccess)
        assertEquals("one TWO one", access.read("a.txt").getOrNull())
    }

    @Test
    fun `edit rejects ambiguous match`() = runTest {
        access.write("a.txt", "same same")
        val result = access.edit("a.txt", "same", "other")
        assertFalse(result.isSuccess)
        assertEquals("same same", access.read("a.txt").getOrNull())
    }

    @Test
    fun `edit rejects missing text`() = runTest {
        access.write("a.txt", "abc")
        assertFalse(access.edit("a.txt", "xyz", "zzz").isSuccess)
    }

    @Test
    fun `read rejects oversized file`() = runTest {
        val big = File(root, "big.txt")
        big.writeText("x".repeat((WorkspaceFileAccess.MAX_READ_BYTES + 1).toInt()))
        assertFalse(access.read("big.txt").isSuccess)
    }

    @Test
    fun `write rejects oversized content`() = runTest {
        val huge = "x".repeat(WorkspaceFileAccess.MAX_WRITE_BYTES + 1)
        assertFalse(access.write("huge.txt", huge).isSuccess)
    }

    @Test
    fun `list sorts directories first`() = runTest {
        File(root, "b.txt").writeText("1")
        File(root, "a-dir").mkdirs()
        val entries = access.list("/workspace").getOrNull().orEmpty()
        assertEquals("a-dir", entries.first().name)
        assertTrue(entries.first().isDirectory)
    }

    @Test
    fun `write replaces existing file atomically`() = runTest {
        access.write("f.txt", "v1")
        access.write("f.txt", "v2")
        assertEquals("v2", access.read("f.txt").getOrNull())
    }
}
