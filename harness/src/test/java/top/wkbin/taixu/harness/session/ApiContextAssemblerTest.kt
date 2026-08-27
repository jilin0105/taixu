package top.wkbin.taixu.harness.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.common.logging.SensitiveDataRedactor
import top.wkbin.taixu.core.datastore.AgentPreferences
import top.wkbin.taixu.core.datastore.SettingsDataStore
import top.wkbin.taixu.core.database.AgentSkillRepository
import top.wkbin.taixu.core.database.AgentSubagentRepository
import top.wkbin.taixu.core.database.AppDatabase
import top.wkbin.taixu.core.database.RoomAgentContextRepository
import top.wkbin.taixu.core.database.RoomHarnessRuntimeRepository
import top.wkbin.taixu.core.security.SecretManager
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.CapabilityEvent
import top.wkbin.taixu.harness.HarnessTool
import top.wkbin.taixu.harness.ModelConfig
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolCallMode
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.harness.UserMessage
import top.wkbin.taixu.harness.WorkspaceFileAccess
import top.wkbin.taixu.core.tools.ToolRegistry
import top.wkbin.taixu.core.tools.ToolRepository
import top.wkbin.taixu.harness.compaction.CompactionManager
import top.wkbin.taixu.harness.prompt.PrivilegeSectionRenderer
import top.wkbin.taixu.harness.prompt.PromptAssetLoader
import top.wkbin.taixu.harness.prompt.SystemPromptBuilder

/**
 * API 上下文组装器全栈集成测试：真实 Room（会话树 + 压缩树）+ 真实 DataStore 偏好 +
 * 打包内真实提示词资产。验证 NATIVE / JSON_TEXT 双协议、视觉剥离、思考回传标记、
 * 能力事件剔除、未应答调用的丢弃与压缩摘要注入。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiContextAssemblerTest {

    private lateinit var database: AppDatabase
    private lateinit var store: top.wkbin.taixu.harness.session.SessionTreeStore
    private lateinit var assembler: ApiContextAssembler
    private lateinit var compactionManager: CompactionManager
    private val tempDir = File(System.getProperty("java.io.tmpdir"), "taixu-assembler-${System.nanoTime()}")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val runtimeRepo = RoomHarnessRuntimeRepository(database.harnessRuntimeDao())
        val logger = AppLogger(context, SensitiveDataRedactor { it })
        val json = Json { ignoreUnknownKeys = true }
        val agentPrefs = AgentPreferences(SettingsDataStore(context, SecretManager()))

        compactionManager = CompactionManager(runtimeRepo, json)
        store = top.wkbin.taixu.harness.session.SessionTreeStore(runtimeRepo, json, logger)

        val builder = SystemPromptBuilder(
            context = context,
            settingsDataStore = agentPrefs,
            skillRepository = AgentSkillRepository(database.agentSkillDao()),
            toolRepository = ToolRepository(database.toolDao(), ToolRegistry(context, OkHttpClient(), logger)),
            agentContextDao = RoomAgentContextRepository(database.agentContextDao()),
            subagentRepository = AgentSubagentRepository(database.agentSubagentDao()),
            promptAssets = PromptAssetLoader(context),
            fileAccess = WorkspaceFileAccess(tempDir),
            privilegeRenderer = PrivilegeSectionRenderer { "" },
        )
        assembler = ApiContextAssembler(compactionManager, agentPrefs, builder)
    }

    @After
    fun tearDown() {
        database.close()
        tempDir.deleteRecursively()
    }

    private fun nativeModel(vision: Boolean = false, tokens: Int = 200_000) = ModelConfig(
        name = "n", provider = "p", model = "gpt-x",
        baseUrl = "https://example.com", apiKey = null,
        contextTokens = tokens,
        visionEnabled = vision,
    )

    private suspend fun push(sessionId: String, vararg messages: top.wkbin.taixu.harness.HarnessMessage) {
        messages.forEach { store.append(sessionId, it) }
    }

    private fun jsonTextModel(vision: Boolean = false) =
        nativeModel(vision).copy(toolCallMode = ToolCallMode.JSON_TEXT)

    // ---------- NATIVE 协议 ----------

    @Test
    fun `native injects system prompt and maps user and plain assistant`() = runBlocking {
        push(
            "s-native",
            UserMessage("u1", 1L, "看看文件"),
            AssistantText("a0", 2L, "好的"),
        )
        val out = assembler.assemble("s-native", nativeModel(), workspacePath = "")

        assertEquals("system", out[0].role)
        assertTrue(out[0].content!!.isNotBlank())
        assertEquals(2, out.size - 1)
        assertEquals("user", out[1].role)
        assertEquals("assistant", out[2].role)
        assertNull(out[2].tool_calls)
    }

    @Test
    fun `native pairs answered tool calls onto assistant and emits tool role`() = runBlocking {
        val call = ToolCall(
            id = "t1", createdAt = 3L, tool = HarnessTool.READ,
            args = buildJsonObject { }, rawToolName = "read",
        )
        push(
            "s-pair",
            UserMessage("u1", 1L, "读一下"),
            AssistantText("a0", 2L, "我来读"),
            call,
            ToolResult("r1", 4L, "t1", success = true, output = "内容"),
            AssistantText("a1", 5L, "结论"),
        )
        val out = assembler.assemble("s-pair", nativeModel(), "")

        val paired = out.first { it.role == "assistant" && !it.tool_calls.isNullOrEmpty() }
        assertEquals(listOf("t1"), paired.tool_calls!!.map { it.id })
        val toolMsg = out.last { it.role == "tool" }
        assertEquals("t1", toolMsg.tool_call_id)
        assertEquals("内容", toolMsg.content)
        assertEquals("结论", out.last { it.role == "assistant" }.content)
    }

    @Test
    fun `unanswered native tool calls are dropped silently`() = runBlocking {
        push(
            "s-orphan",
            UserMessage("u1", 1L, "x"),
            ToolCall("t9", 3L, HarnessTool.READ, buildJsonObject { }),
        )
        val out = assembler.assemble("s-orphan", nativeModel(vision = false), "")
        assertTrue(out.none { it.role == "tool" })
        assertTrue(out.none { !it.tool_calls.isNullOrEmpty() })
    }

    @Test
    fun `thinking mode backfills empty reasoning for reasoning-less assistant`() = runBlocking {
        push("s-think", UserMessage("u1", 1L, "hi"), AssistantText("a1", 2L, "hello"))
        val out = assembler.assemble("s-think", nativeModel(), "", thinkingMode = true)
        val assistant = out.last { it.role == "assistant" }
        assertEquals("", assistant.reasoning_content)

        val off = assembler.assemble("s-think", nativeModel(), "", thinkingMode = false)
        assertNull(off.last { it.role == "assistant" }.reasoning_content)
    }

    // ---------- JSON_TEXT 协议 ----------

    @Test
    fun `json text converts tool results into user text messages`() = runBlocking {
        val call = ToolCall(id = "j1", createdAt = 3L, tool = HarnessTool.BASE, args = buildJsonObject { }, rawToolName = "base")
        push(
            "s-json",
            UserMessage("u1", 1L, "跑命令"),
            AssistantText("a0", 2L, "模型叙述文本"),
            call,
            ToolResult("r1", 4L, "j1", success = true, output = "输出内容"),
        )
        val out = assembler.assemble("s-json", jsonTextModel(), "")
        assertTrue(out.none { it.role == "tool" })
        assertTrue(out.none { !it.tool_calls.isNullOrEmpty() })

        val asUser = out.filter { it.role == "user" }.last()
        assertTrue(asUser.content!!.contains("【工具 base 执行结果·成功】"))
        assertTrue(asUser.content!!.contains("输出内容"))

        val narration = out.first { it.role == "assistant" }
        assertEquals("模型叙述文本", narration.content)
    }

    // ---------- 视觉与能力事件 ----------

    @Test
    fun `images are stripped when vision disabled and kept when enabled`() = runBlocking {
        val images = listOf("https://img.example/a.png")
        push("s-vision", UserMessage("u1", 1L, "看图", imageUrls = images))
        val stripped = assembler.assemble("s-vision", nativeModel(vision = false), "")
        assertTrue(stripped.first { it.role == "user" }.imageUrls.isEmpty())

        val kept = assembler.assemble("s-vision", nativeModel(vision = true), "")
        assertEquals(images, kept.first { it.role == "user" }.imageUrls)
    }

    @Test
    fun `capability events never reach the provider payload`() = runBlocking {
        push(
            "s-cap",
            UserMessage("u1", 1L, "@skill"),
            CapabilityEvent("cap1", 2L, CapabilityEvent.Kind.SKILL, "技能", "详情"),
        )
        val out = assembler.assemble("s-cap", nativeModel(), "")
        assertFalse(out.any { it.content?.contains("详情") == true })
    }

    // ---------- 压缩摘要 ----------

    @Test
    fun `existing compaction summary is injected after the main system prompt`() = runBlocking {
        push("s-compact", UserMessage("u0", 1L, "早期历史，会被折叠进摘要"))
        val context = compactionManager.project("s-compact")
        compactionManager.compact("s-compact", context, keepFromIndex = 1)

        val expectedSummary = compactionManager.project("s-compact").summary!!
        val out = assembler.assemble("s-compact", nativeModel(), "")

        assertTrue(out.size >= 2)
        assertEquals(expectedSummary, out[1].content)
        assertFalse(out.any { it.role != "system" && it.content == expectedSummary })
    }

    @Test
    fun `pure chat skips even persisted history system prompts`() = runBlocking {
        push("s-pure2", UserMessage("u1", 1L, "你好"))
        val out = assembler.assemble("s-pure2", nativeModel().copy(pureChatMode = true), "/ws")
        assertTrue(out.none { it.role == "system" })
        assertEquals("你好", out.single().content)
    }
}
