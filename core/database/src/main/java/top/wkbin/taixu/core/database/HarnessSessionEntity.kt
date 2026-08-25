package top.wkbin.taixu.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import top.wkbin.taixu.core.model.ApprovalMode

/** Harness 会话：一条会话聚合一批消息，并记录使用的模型。 */
@Entity(tableName = "harness_sessions", indices = [Index(value = ["updatedAt"])])
data class HarnessSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val modelId: String?,
    val workspace: String = "",
    /** Explicit type selected for empty/imported workspaces; blank means auto-detect. */
    val projectType: String = "",
    /** Tool approval authority for this session; new sessions inherit the global default. */
    val approvalMode: String = ApprovalMode.ASSISTED.id,
)

@Dao
interface HarnessSessionDao {
    @Query("SELECT * FROM harness_sessions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<HarnessSessionEntity>>

    @Query("SELECT * FROM harness_sessions WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): HarnessSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: HarnessSessionEntity)

    @Query("UPDATE harness_sessions SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("UPDATE harness_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, title: String, updatedAt: Long)

    @Query("UPDATE harness_sessions SET approvalMode = :approvalMode, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setApprovalMode(id: String, approvalMode: String, updatedAt: Long)

    @Query("DELETE FROM harness_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessages(sessionId: String)

    @Query("DELETE FROM harness_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("SELECT COUNT(*) FROM harness_sessions WHERE (:start IS NULL OR createdAt >= :start) AND (:end IS NULL OR createdAt < :end)")
    suspend fun countInRange(start: Long?, end: Long?): Int

    @Query("SELECT * FROM harness_sessions")
    suspend fun listAll(): List<HarnessSessionEntity>
}
