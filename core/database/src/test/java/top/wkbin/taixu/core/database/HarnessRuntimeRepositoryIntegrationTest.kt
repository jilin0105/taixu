package top.wkbin.taixu.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 真实 Room（in-memory）上的持久化事务集成测试：
 * 覆盖 entry tree 事务原子性、并发 append 的 leaf 守卫、branch 投影与级联删除。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HarnessRuntimeRepositoryIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: HarnessRuntimeDao
    private lateinit var repository: HarnessRuntimeRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.harnessRuntimeDao()
        repository = RoomHarnessRuntimeRepository(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entry(id: String, sessionId: String, parentId: String?) = HarnessEntryEntity(
        id = id,
        sessionId = sessionId,
        parentId = parentId,
        createdAt = System.nanoTime(),
        entryType = "message",
        customType = "user",
        payloadJson = "{}",
    )

    private fun lane(sessionId: String, name: String = "main", leafId: String? = null, currentOperationId: String? = null) = HarnessLaneEntity(
        sessionId = sessionId,
        name = name,
        leafId = leafId,
        currentOperationId = currentOperationId,
        updatedAt = System.currentTimeMillis(),
    )

    private fun operation(id: String, sessionId: String, status: String = "running") = HarnessOperationEntity(
        id = id,
        sessionId = sessionId,
        laneName = "main",
        kind = "run",
        status = status,
        phase = "checkpoint",
        startedAt = 0L,
        updatedAt = 0L,
        startLeafId = null,
        stateJson = "{}",
    )

    @Test
    fun `settleEffect atomically writes entry usage operation and lane`() = runBlocking {
        val sessionId = "s1"
        val op = operation("op-1", sessionId)
        dao.acceptOperation(entry("e1", sessionId, null), lane(sessionId, leafId = "e1"), op)

        val usage = HarnessUsageEntity(
            id = "u1", sessionId = sessionId, operationId = "op-1", entryId = "e2",
            provider = "deepseek", modelId = "deepseek-chat",
            inputTokens = 100, outputTokens = 40, createdAt = 1L,
        )
        repository.settleEffect(
            entry = entry("e2", sessionId, "e1"),
            usage = usage,
            operation = op.copy(status = "running", phase = "provider_settled"),
            lane = lane(sessionId, leafId = "e2", currentOperationId = "op-1"),
        )

        assertEquals("e2", repository.findLane(sessionId, "main")!!.leafId)
        assertNotNull(repository.findOperation("op-1"))
        assertEquals(2, repository.listEntries(sessionId).size)
        assertEquals(1, repository.listUsage(sessionId).size)
        assertEquals("provider_settled", repository.findOperation("op-1")!!.phase)
    }

    @Test
    fun `appendToLane rejects concurrent stale leaf writes`() = runBlocking {
        val sessionId = "s2"
        repository.ensureLane(sessionId, "main")

        // 两个协程同时基于同一 leaf 追加：只有一个成功，另一个被 leaf 一致性守卫拒绝
        val results = (1..2).map { index ->
            async(Dispatchers.IO) {
                runCatching {
                    repository.appendToLane(sessionId, "main", entry("race-$index", sessionId, null))
                }
            }
        }.awaitAll()

        val successes = results.count { it.isSuccess }
        assertEquals("并发 append 只允许一个成功", 1, successes)
    }

    @Test
    fun `branch projects only ancestors of lane leaf`() = runBlocking {
        val sessionId = "s3"
        // 直接插入 entries 构成分叉树（b 与 leaf 同为 root 的子节点）：
        // lane 分支/回退不经过 appendToLane 的单叉守卫，此处模拟已有分叉历史。
        val root = entry("root", sessionId, null)
        val a = entry("a", sessionId, "root")
        val b = entry("b", sessionId, "root")
        val leaf = entry("leaf", sessionId, "b")
        listOf(root, a, b, leaf).forEach { dao.insertEntry(it) }

        // lane 指向 leaf → 投影 root→b→leaf（a 在另一分支，不出现）
        repository.moveLane(sessionId, "main", "leaf")
        assertEquals(listOf("root", "b", "leaf"), repository.branch(sessionId, "leaf").map { it.id })

        // lane 移动到 b → 投影 root→b（回退丢弃 leaf 但历史保留）
        repository.moveLane(sessionId, "main", "b")
        assertEquals(listOf("root", "b"), repository.branch(sessionId, "b").map { it.id })
        assertEquals(4, repository.listEntries(sessionId).size)
    }

    @Test
    fun `finishOperation clears lane pointer and removes operation row`() = runBlocking {
        val sessionId = "s4"
        val op = operation("op-4", sessionId)
        dao.acceptOperation(entry("e1", sessionId, null), lane(sessionId, leafId = "e1", currentOperationId = "op-4"), op)

        repository.finishOperation(
            HarnessLaneResultEntity(sessionId, "main", "op-4", "completed", "e1", null, 2L),
            lane(sessionId, leafId = "e1", currentOperationId = null),
        )

        assertNull(repository.findOperation("op-4"))
        assertNull(repository.findLane(sessionId, "main")!!.currentOperationId)
        assertTrue(repository.listActiveOperations(sessionId).isEmpty())
    }

    @Test
    fun `deleteSessionData cascades across all runtime tables`() = runBlocking {
        val sessionId = "s5"
        val op = operation("op-5", sessionId)
        dao.acceptOperation(entry("e1", sessionId, null), lane(sessionId, leafId = "e1", currentOperationId = "op-5"), op)
        dao.insertUsage(
            HarnessUsageEntity(id = "u5", sessionId = sessionId, operationId = "op-5", entryId = null, provider = null, modelId = null, createdAt = 1L),
        )
        dao.insertQueueItem(
            HarnessQueueItemEntity(id = "q5", sessionId = sessionId, laneName = "main", operationId = "op-5", queueType = "next_run", createdAt = 1L, payloadJson = "{}"),
        )

        repository.deleteSessionData(sessionId)

        assertTrue(repository.listEntries(sessionId).isEmpty())
        assertTrue(repository.listUsage(sessionId).isEmpty())
        assertTrue(repository.listQueue(sessionId, "main", "next_run").isEmpty())
        assertNull(repository.findLane(sessionId, "main"))
        assertNull(repository.findOperation("op-5"))
    }

    @Test
    fun `consumeQueued atomically removes item and appends entry`() = runBlocking {
        val sessionId = "s6"
        repository.ensureLane(sessionId, "main")
        val queueItem = HarnessQueueItemEntity(
            id = "q6", sessionId = sessionId, laneName = "main", operationId = null,
            queueType = "next_run", createdAt = 1L, payloadJson = "{}",
        )
        dao.insertQueueItem(queueItem)

        val entry = entry("consumed", sessionId, null)
        repository.consumeQueued(queueItem.id, entry, lane(sessionId, leafId = "consumed"))

        assertTrue(repository.listQueue(sessionId, "main", "next_run").isEmpty())
        assertEquals(1, repository.listEntries(sessionId).size)
        assertEquals("consumed", repository.findLane(sessionId, "main")!!.leafId)
    }
}
