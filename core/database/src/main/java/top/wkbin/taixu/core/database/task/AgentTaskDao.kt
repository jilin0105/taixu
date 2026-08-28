package top.wkbin.taixu.core.database.task

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentTaskDao {
    @Query("SELECT * FROM agent_tasks ORDER BY updatedAt DESC")
    fun observeAllTasks(): Flow<List<AgentTaskEntity>>

    @Query("SELECT * FROM agent_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): AgentTaskEntity?

    @Query("SELECT * FROM agent_tasks WHERE status = :status ORDER BY updatedAt DESC")
    fun observeTasksByStatus(status: String): Flow<List<AgentTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: AgentTaskEntity)

    @Query("UPDATE agent_tasks SET status = :status, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTaskStatus(id: String, status: String, errorMessage: String?, updatedAt: Long)

    @Query("UPDATE agent_tasks SET progress = :progress, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTaskProgress(id: String, progress: Float, updatedAt: Long)

    @Query("DELETE FROM agent_tasks WHERE id = :id")
    suspend fun deleteTask(id: String)
}
