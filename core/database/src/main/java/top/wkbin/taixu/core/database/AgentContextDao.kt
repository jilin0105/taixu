package top.wkbin.taixu.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentContextDao {

    // ========== 长期记忆 (Memory) ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMemory(memory: AgentMemoryEntity)

    @Query("SELECT * FROM agent_memories WHERE id = :id LIMIT 1")
    suspend fun getMemoryById(id: String): AgentMemoryEntity?

    @Query("SELECT * FROM agent_memories WHERE `key` = :key AND scope = :scope LIMIT 1")
    suspend fun getMemoryByKey(key: String, scope: String): AgentMemoryEntity?

    @Query("SELECT * FROM agent_memories WHERE scope IN (:scopes) ORDER BY updatedAt DESC")
    suspend fun getMemoriesByScopes(scopes: List<String>): List<AgentMemoryEntity>

    @Query("SELECT * FROM agent_memories ORDER BY updatedAt DESC")
    fun observeAllMemories(): Flow<List<AgentMemoryEntity>>

    @Query("SELECT * FROM agent_memories WHERE `key` LIKE '%' || :query || '%' OR `value` LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    suspend fun searchMemories(query: String): List<AgentMemoryEntity>

    @Query("DELETE FROM agent_memories WHERE id = :id")
    suspend fun deleteMemoryById(id: String)

    @Query("DELETE FROM agent_memories WHERE `key` = :key AND scope = :scope")
    suspend fun deleteMemoryByKey(key: String, scope: String)

    // ========== 任务规划 (Plan) ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlan(plan: AgentPlanEntity)

    @Query("SELECT * FROM agent_plans WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getPlanBySession(sessionId: String): AgentPlanEntity?

    @Query("SELECT * FROM agent_plans WHERE sessionId = :sessionId AND status = 'active' LIMIT 1")
    suspend fun getActivePlan(sessionId: String): AgentPlanEntity?

    @Query("DELETE FROM agent_plans WHERE sessionId = :sessionId")
    suspend fun deletePlanBySession(sessionId: String)

    // ========== 工作草稿 (Scratchpad) ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveScratchpad(scratchpad: AgentScratchpadEntity)

    @Query("SELECT * FROM agent_scratchpads WHERE sessionId = :sessionId AND `key` = :key LIMIT 1")
    suspend fun getScratchpad(sessionId: String, key: String): AgentScratchpadEntity?

    @Query("SELECT * FROM agent_scratchpads WHERE sessionId = :sessionId ORDER BY updatedAt DESC")
    suspend fun listScratchpads(sessionId: String): List<AgentScratchpadEntity>

    @Query("DELETE FROM agent_scratchpads WHERE sessionId = :sessionId AND `key` = :key")
    suspend fun deleteScratchpad(sessionId: String, key: String)

    @Query("DELETE FROM agent_scratchpads WHERE sessionId = :sessionId")
    suspend fun clearScratchpads(sessionId: String)
}
