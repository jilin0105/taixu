package top.wkbin.taixu.runtime

import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import top.wkbin.taixu.core.database.WorkspaceDao
import top.wkbin.taixu.core.database.WorkspaceEntity
import top.wkbin.taixu.runtime.rootfs.RootfsValidator
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceManagerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var workspaceDir: File
    private lateinit var pathManager: RuntimePathManager
    private lateinit var workspaceDao: FakeWorkspaceDao
    private lateinit var manager: WorkspaceManager

    @Before
    fun setUp() {
        val root = temporaryFolder.newFolder("linux-runtime")
        workspaceDir = File(root, "workspace").apply { mkdirs() }
        val validator = RootfsValidator(ElfInspector())
        val context = TestContext(root)
        pathManager = RuntimePathManager(context, validator)
        workspaceDao = FakeWorkspaceDao()
        manager = WorkspaceManager(pathManager, workspaceDao)
    }

    private fun runTest(block: suspend () -> Unit) = runBlocking { block() }

    @Test
    fun createProjectAndFileOperationsRoundTrip() = runTest {
        val proj = manager.createProject("demo").getOrNull()
        assertTrue(proj != null)
        assertEquals("demo", proj?.name)

        // 创建文件并写入
        val createRes = manager.createFile("demo", "main.py")
        assertTrue(createRes.isSuccess)

        val writeRes = manager.writeFile("demo", "main.py", "print('hello world')")
        assertTrue(writeRes.isSuccess)

        val readRes = manager.readFile("demo", "main.py")
        assertTrue(readRes.isSuccess)
        assertEquals("print('hello world')", readRes.getOrNull())

        // 创建子目录和子文件
        val dirRes = manager.createDirectory("demo", "src")
        assertTrue(dirRes.isSuccess)

        val subFileRes = manager.createFile("demo", "src/helper.py")
        assertTrue(subFileRes.isSuccess)

        // 列表排序验证：目录优先，随后按文件名排序
        val listRes = manager.listFiles("demo", "")
        assertTrue(listRes.isSuccess)
        val items = listRes.getOrNull().orEmpty()
        assertEquals(2, items.size)
        assertEquals("src", items[0].name)
        assertTrue(items[0].isDirectory)
        assertEquals("main.py", items[1].name)
        assertFalse(items[1].isDirectory)
        assertEquals("py", items[1].extension)

        // 重命名
        val renameRes = manager.renameItem("demo", "main.py", "app.py")
        assertTrue(renameRes.isSuccess)
        val readRenamed = manager.readFile("demo", "app.py")
        assertEquals("print('hello world')", readRenamed.getOrNull())

        // 删除
        val deleteFileRes = manager.deleteItem("demo", "app.py")
        assertTrue(deleteFileRes.isSuccess)
        assertFalse(manager.readFile("demo", "app.py").isSuccess)

        val deleteDirRes = manager.deleteItem("demo", "src")
        assertTrue(deleteDirRes.isSuccess)
    }

    @Test
    fun rejectsPathTraversalAndEscape() = runTest {
        manager.createProject("safe-proj")
        temporaryFolder.newFile("secret.txt").apply { writeText("top-secret") }

        // 尝试通过 .. 逃逸
        assertFalse(manager.readFile("safe-proj", "../secret.txt").isSuccess)
        assertFalse(manager.readFile("safe-proj", "/workspace/../secret.txt").isSuccess)
        assertFalse(manager.createFile("safe-proj", "../pwned.txt").isSuccess)
        assertFalse(manager.writeFile("safe-proj", "../pwned.txt", "evil").isSuccess)
    }

    @Test
    fun linksExistingInternalDirectoryWithoutDeletingItsFiles() = runTest {
        val existing = File(pathManager.workspaceDir, "existing-source").apply {
            mkdirs()
            resolve("keep.txt").writeText("keep")
        }

        val project = manager.createProject(
            name = "linked-project",
            storage = WorkspaceStorage.INTERNAL,
            directoryPath = "existing-source",
        ).getOrNull()

        assertEquals("/workspace/existing-source", project?.linuxPath)
        assertFalse(project?.ownsDirectory ?: true)
        assertEquals("keep", manager.readFile("linked-project", "keep.txt").getOrNull())

        assertTrue(manager.deleteProject("linked-project").isSuccess)
        assertTrue(existing.resolve("keep.txt").isFile)
    }

    private class TestContext(private val baseDir: File) : ContextWrapper(null) {
        override fun getFilesDir(): File = baseDir
        override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo().apply {
            nativeLibraryDir = File(baseDir, "lib").apply { mkdirs() }.absolutePath
        }
    }

    private class FakeWorkspaceDao : WorkspaceDao {
        private val list = mutableListOf<WorkspaceEntity>()
        private val flow = MutableStateFlow<List<WorkspaceEntity>>(emptyList())

        override fun observeAll(): Flow<List<WorkspaceEntity>> = flow

        override suspend fun listAll(): List<WorkspaceEntity> = list.toList()

        override suspend fun findByName(name: String): WorkspaceEntity? = list.firstOrNull { it.name == name }

        override suspend fun upsert(workspace: WorkspaceEntity) {
            list.removeAll { it.name == workspace.name }
            list.add(workspace)
            flow.value = list.toList()
        }

        override suspend fun delete(name: String) {
            list.removeAll { it.name == name }
            flow.value = list.toList()
        }
    }
}
