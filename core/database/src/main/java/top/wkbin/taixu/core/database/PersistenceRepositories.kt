package top.wkbin.taixu.core.database

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Stable persistence ports consumed by feature, harness, and runtime layers. */
interface AiModelRepository {
    fun observeAll(): Flow<List<AiModelEntity>>
    suspend fun findById(id: String): AiModelEntity?
    suspend fun activeModel(): AiModelEntity?
    suspend fun upsert(model: AiModelEntity)
    suspend fun clearActive()
    suspend fun setActive(id: String)
    suspend fun updateReasoning(id: String, mode: String?, effort: String?)
    suspend fun delete(id: String)
}

interface HarnessSessionRepository {
    fun observeAll(): Flow<List<HarnessSessionEntity>>
    suspend fun findById(id: String): HarnessSessionEntity?
    suspend fun upsert(session: HarnessSessionEntity)
    suspend fun touch(id: String, updatedAt: Long)
    suspend fun rename(id: String, title: String, updatedAt: Long)
    suspend fun setApprovalMode(id: String, approvalMode: String, updatedAt: Long)
    suspend fun deleteMessages(sessionId: String)
    suspend fun deleteSession(id: String)
    suspend fun countInRange(start: Long?, end: Long?): Int
    suspend fun listAll(): List<HarnessSessionEntity>
}

interface HarnessMessageRepository {
    fun observeForSession(sessionId: String): Flow<List<HarnessMessageEntity>>
    suspend fun listForSession(sessionId: String): List<HarnessMessageEntity>
    suspend fun insert(message: HarnessMessageEntity)
    suspend fun insertAll(messages: List<HarnessMessageEntity>)
    suspend fun deleteFromTimestamp(sessionId: String, createdAt: Long)
    suspend fun deleteById(id: String)
    suspend fun deleteByIds(ids: List<String>)
    suspend fun clear()
    suspend fun countInRange(start: Long?, end: Long?): Int
    suspend fun queryHeatmap(start: Long): List<StatsDayCountResult>
    suspend fun queryTopicRank(start: Long?, end: Long?, limit: Int = 20): List<StatsTopicRankResult>
    suspend fun listInRange(start: Long?, end: Long?): List<HarnessMessageEntity>
}

interface WorkspaceRepository {
    fun observeAll(): Flow<List<WorkspaceEntity>>
    suspend fun listAll(): List<WorkspaceEntity>
    suspend fun findByName(name: String): WorkspaceEntity?
    suspend fun upsert(workspace: WorkspaceEntity)
    suspend fun delete(name: String)
}

interface TerminalSessionRepository {
    fun observeAll(): Flow<List<TerminalSessionEntity>>
    suspend fun listAll(): List<TerminalSessionEntity>
    suspend fun nextOrder(): Int
    suspend fun upsert(session: TerminalSessionEntity)
    suspend fun delete(id: String)
    suspend fun deleteAll()
}

interface AgentContextRepository {
    suspend fun saveMemory(memory: AgentMemoryEntity)
    suspend fun getMemoryById(id: String): AgentMemoryEntity?
    suspend fun getMemoryByKey(key: String, scope: String): AgentMemoryEntity?
    suspend fun getMemoriesByScopes(scopes: List<String>): List<AgentMemoryEntity>
    fun observeAllMemories(): Flow<List<AgentMemoryEntity>>
    suspend fun searchMemories(query: String): List<AgentMemoryEntity>
    suspend fun deleteMemoryById(id: String)
    suspend fun deleteMemoryByKey(key: String, scope: String)
    suspend fun savePlan(plan: AgentPlanEntity)
    suspend fun getPlanBySession(sessionId: String): AgentPlanEntity?
    suspend fun getActivePlan(sessionId: String): AgentPlanEntity?
    suspend fun deletePlanBySession(sessionId: String)
    suspend fun saveScratchpad(scratchpad: AgentScratchpadEntity)
    suspend fun getScratchpad(sessionId: String, key: String): AgentScratchpadEntity?
    suspend fun listScratchpads(sessionId: String): List<AgentScratchpadEntity>
    suspend fun deleteScratchpad(sessionId: String, key: String)
    suspend fun clearScratchpads(sessionId: String)
}

@Singleton
class RoomAiModelRepository @Inject constructor(private val dao: AiModelDao) : AiModelRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun findById(id: String) = dao.findById(id)
    override suspend fun activeModel() = dao.activeModel()
    override suspend fun upsert(model: AiModelEntity) = dao.upsert(model)
    override suspend fun clearActive() = dao.clearActive()
    override suspend fun setActive(id: String) = dao.setActive(id)
    override suspend fun updateReasoning(id: String, mode: String?, effort: String?) = dao.updateReasoning(id, mode, effort)
    override suspend fun delete(id: String) = dao.delete(id)
}

@Singleton
class RoomHarnessSessionRepository @Inject constructor(private val dao: HarnessSessionDao) : HarnessSessionRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun findById(id: String) = dao.findById(id)
    override suspend fun upsert(session: HarnessSessionEntity) = dao.upsert(session)
    override suspend fun touch(id: String, updatedAt: Long) = dao.touch(id, updatedAt)
    override suspend fun rename(id: String, title: String, updatedAt: Long) = dao.rename(id, title, updatedAt)
    override suspend fun setApprovalMode(id: String, approvalMode: String, updatedAt: Long) = dao.setApprovalMode(id, approvalMode, updatedAt)
    override suspend fun deleteMessages(sessionId: String) = dao.deleteMessages(sessionId)
    override suspend fun deleteSession(id: String) = dao.deleteSession(id)
    override suspend fun countInRange(start: Long?, end: Long?) = dao.countInRange(start, end)
    override suspend fun listAll() = dao.listAll()
}

@Singleton
class RoomHarnessMessageRepository @Inject constructor(private val dao: HarnessMessageDao) : HarnessMessageRepository {
    override fun observeForSession(sessionId: String) = dao.observeForSession(sessionId)
    override suspend fun listForSession(sessionId: String) = dao.listForSession(sessionId)
    override suspend fun insert(message: HarnessMessageEntity) = dao.insert(message)
    override suspend fun insertAll(messages: List<HarnessMessageEntity>) = dao.insertAll(messages)
    override suspend fun deleteFromTimestamp(sessionId: String, createdAt: Long) = dao.deleteFromTimestamp(sessionId, createdAt)
    override suspend fun deleteById(id: String) = dao.deleteById(id)
    override suspend fun deleteByIds(ids: List<String>) = dao.deleteByIds(ids)
    override suspend fun clear() = dao.clear()
    override suspend fun countInRange(start: Long?, end: Long?) = dao.countInRange(start, end)
    override suspend fun queryHeatmap(start: Long) = dao.queryHeatmap(start)
    override suspend fun queryTopicRank(start: Long?, end: Long?, limit: Int) = dao.queryTopicRank(start, end, limit)
    override suspend fun listInRange(start: Long?, end: Long?) = dao.listInRange(start, end)
}

@Singleton
class RoomWorkspaceRepository @Inject constructor(private val dao: WorkspaceDao) : WorkspaceRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun listAll() = dao.listAll()
    override suspend fun findByName(name: String) = dao.findByName(name)
    override suspend fun upsert(workspace: WorkspaceEntity) = dao.upsert(workspace)
    override suspend fun delete(name: String) = dao.delete(name)
}

@Singleton
class RoomTerminalSessionRepository @Inject constructor(private val dao: TerminalSessionDao) : TerminalSessionRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun listAll() = dao.listAll()
    override suspend fun nextOrder() = dao.nextOrder()
    override suspend fun upsert(session: TerminalSessionEntity) = dao.upsert(session)
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun deleteAll() = dao.deleteAll()
}

@Singleton
class RoomAgentContextRepository @Inject constructor(private val dao: AgentContextDao) : AgentContextRepository {
    override suspend fun saveMemory(memory: AgentMemoryEntity) = dao.saveMemory(memory)
    override suspend fun getMemoryById(id: String) = dao.getMemoryById(id)
    override suspend fun getMemoryByKey(key: String, scope: String) = dao.getMemoryByKey(key, scope)
    override suspend fun getMemoriesByScopes(scopes: List<String>) = dao.getMemoriesByScopes(scopes)
    override fun observeAllMemories() = dao.observeAllMemories()
    override suspend fun searchMemories(query: String) = dao.searchMemories(query)
    override suspend fun deleteMemoryById(id: String) = dao.deleteMemoryById(id)
    override suspend fun deleteMemoryByKey(key: String, scope: String) = dao.deleteMemoryByKey(key, scope)
    override suspend fun savePlan(plan: AgentPlanEntity) = dao.savePlan(plan)
    override suspend fun getPlanBySession(sessionId: String) = dao.getPlanBySession(sessionId)
    override suspend fun getActivePlan(sessionId: String) = dao.getActivePlan(sessionId)
    override suspend fun deletePlanBySession(sessionId: String) = dao.deletePlanBySession(sessionId)
    override suspend fun saveScratchpad(scratchpad: AgentScratchpadEntity) = dao.saveScratchpad(scratchpad)
    override suspend fun getScratchpad(sessionId: String, key: String) = dao.getScratchpad(sessionId, key)
    override suspend fun listScratchpads(sessionId: String) = dao.listScratchpads(sessionId)
    override suspend fun deleteScratchpad(sessionId: String, key: String) = dao.deleteScratchpad(sessionId, key)
    override suspend fun clearScratchpads(sessionId: String) = dao.clearScratchpads(sessionId)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PersistenceRepositoryModule {
    @Binds abstract fun bindAiModelRepository(impl: RoomAiModelRepository): AiModelRepository
    @Binds abstract fun bindHarnessSessionRepository(impl: RoomHarnessSessionRepository): HarnessSessionRepository
    @Binds abstract fun bindHarnessMessageRepository(impl: RoomHarnessMessageRepository): HarnessMessageRepository
    @Binds abstract fun bindWorkspaceRepository(impl: RoomWorkspaceRepository): WorkspaceRepository
    @Binds abstract fun bindTerminalSessionRepository(impl: RoomTerminalSessionRepository): TerminalSessionRepository
    @Binds abstract fun bindAgentContextRepository(impl: RoomAgentContextRepository): AgentContextRepository
}
