package top.wkbin.taixu.harness

import top.wkbin.taixu.core.database.AgentContextDao
import top.wkbin.taixu.core.database.AgentMemoryEntity
import top.wkbin.taixu.core.database.AgentPlanEntity
import top.wkbin.taixu.core.database.AgentScratchpadEntity
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AgentContextTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var fakeDao: FakeAgentContextDao
    private lateinit var executor: AgentContextExecutor

    @Before
    fun setUp() {
        fakeDao = FakeAgentContextDao()
        executor = AgentContextExecutor(fakeDao, json)
    }

    @Test
    fun testMemoryLifecycle() = runBlocking {
        val saveArgs = buildJsonObject {
            put("action", "save")
            put("key", "user_preferred_lang")
            put("value", "kotlin")
            put("kind", "preference")
            put("scope", "global")
        }
        val (saveOk, saveMsg) = executor.executeMemory(saveArgs, "session-1", "")
        assertTrue(saveOk)
        assertTrue(saveMsg.contains("已成功存储长期记忆"))

        val queryArgs = buildJsonObject {
            put("action", "query")
            put("query", "kotlin")
        }
        val (queryOk, queryMsg) = executor.executeMemory(queryArgs, "session-1", "")
        assertTrue(queryOk)
        assertTrue(queryMsg.contains("user_preferred_lang: kotlin"))

        val listArgs = buildJsonObject {
            put("action", "list")
        }
        val (listOk, listMsg) = executor.executeMemory(listArgs, "session-1", "")
        assertTrue(listOk)
        assertTrue(listMsg.contains("user_preferred_lang"))
    }

    @Test
    fun testPlanLifecycle() = runBlocking {
        val createArgs = buildJsonObject {
            put("action", "replace_active")
            put("goal", "重构网络层并添加单元测试")
            put("steps", json.parseToJsonElement("""[{"id":"1","title":"梳理接口","status":"in_progress"},{"id":"2","title":"编写用例","status":"pending"}]"""))
        }
        val (createOk, createMsg) = executor.executePlan(createArgs, "session-1")
        assertTrue(createOk)
        assertTrue(createMsg.contains("已成功创建/更新任务执行规划"))

        val getArgs = buildJsonObject {
            put("action", "get_active")
        }
        val (getOk, getMsg) = executor.executePlan(getArgs, "session-1")
        assertTrue(getOk)
        assertTrue(getMsg.contains("梳理接口"))

        val advanceArgs = buildJsonObject {
            put("action", "advance")
            put("status", "in_progress")
            put("steps", json.parseToJsonElement("""[{"id":"1","title":"梳理接口","status":"completed"},{"id":"2","title":"编写用例","status":"in_progress"}]"""))
        }
        val (advOk, advMsg) = executor.executePlan(advanceArgs, "session-1")
        assertTrue(advOk)
        assertTrue(advMsg.contains("completed"))
    }

    @Test
    fun testScratchpadLifecycle() = runBlocking {
        val saveArgs = buildJsonObject {
            put("action", "save")
            put("key", "blocker")
            put("value", "需要先安装 JDK 17")
        }
        val (saveOk, _) = executor.executeScratchpad(saveArgs, "session-1")
        assertTrue(saveOk)

        val getArgs = buildJsonObject {
            put("action", "get")
            put("key", "blocker")
        }
        val (getOk, getMsg) = executor.executeScratchpad(getArgs, "session-1")
        assertTrue(getOk)
        assertTrue(getMsg.contains("需要先安装 JDK 17"))

        val clearArgs = buildJsonObject {
            put("action", "clear")
        }
        val (clearOk, _) = executor.executeScratchpad(clearArgs, "session-1")
        assertTrue(clearOk)

        val listArgs = buildJsonObject {
            put("action", "list")
        }
        val (_, listMsg) = executor.executeScratchpad(listArgs, "session-1")
        assertTrue(listMsg.contains("当前无工作草稿记录"))
    }
}

private class FakeAgentContextDao : AgentContextDao {
    private val memories = ConcurrentHashMap<String, AgentMemoryEntity>()
    private val plans = ConcurrentHashMap<String, AgentPlanEntity>()
    private val scratchpads = ConcurrentHashMap<String, AgentScratchpadEntity>()

    override suspend fun saveMemory(memory: AgentMemoryEntity) {
        memories[memory.id] = memory
    }

    override suspend fun getMemoryById(id: String): AgentMemoryEntity? = memories[id]

    override suspend fun getMemoryByKey(key: String, scope: String): AgentMemoryEntity? =
        memories.values.find { it.key == key && it.scope == scope }

    override suspend fun getMemoriesByScopes(scopes: List<String>): List<AgentMemoryEntity> =
        memories.values.filter { it.scope in scopes }.sortedByDescending { it.updatedAt }

    override fun observeAllMemories(): Flow<List<AgentMemoryEntity>> =
        flowOf(memories.values.sortedByDescending { it.updatedAt })

    override suspend fun searchMemories(query: String): List<AgentMemoryEntity> =
        memories.values.filter { it.key.contains(query, ignoreCase = true) || it.value.contains(query, ignoreCase = true) }

    override suspend fun deleteMemoryById(id: String) {
        memories.remove(id)
    }

    override suspend fun deleteMemoryByKey(key: String, scope: String) {
        memories.values.removeAll { it.key == key && it.scope == scope }
    }

    override suspend fun savePlan(plan: AgentPlanEntity) {
        plans[plan.sessionId] = plan
    }

    override suspend fun getPlanBySession(sessionId: String): AgentPlanEntity? = plans[sessionId]

    override suspend fun getActivePlan(sessionId: String): AgentPlanEntity? =
        plans[sessionId]?.takeIf { it.status == "active" || it.status == "in_progress" }

    override suspend fun deletePlanBySession(sessionId: String) {
        plans.remove(sessionId)
    }

    override suspend fun saveScratchpad(scratchpad: AgentScratchpadEntity) {
        scratchpads["${scratchpad.sessionId}__${scratchpad.key}"] = scratchpad
    }

    override suspend fun getScratchpad(sessionId: String, key: String): AgentScratchpadEntity? =
        scratchpads["${sessionId}__${key}"]

    override suspend fun listScratchpads(sessionId: String): List<AgentScratchpadEntity> =
        scratchpads.values.filter { it.sessionId == sessionId }.sortedByDescending { it.updatedAt }

    override suspend fun deleteScratchpad(sessionId: String, key: String) {
        scratchpads.remove("${sessionId}__${key}")
    }

    override suspend fun clearScratchpads(sessionId: String) {
        scratchpads.keys.removeAll { it.startsWith("${sessionId}__") }
    }
}
