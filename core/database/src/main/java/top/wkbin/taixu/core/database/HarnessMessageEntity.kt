package top.wkbin.taixu.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Harness 会话消息的持久化形式。消息以类型 + JSON 载荷存储，
 * 序列化格式与 [top.wkbin.taixu.harness.HarnessMessage] 一致。
 */
@Entity(
    tableName = "harness_messages",
    indices = [Index(value = ["sessionId", "createdAt"])],
)
data class HarnessMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val createdAt: Long,
    val type: String,
    val payloadJson: String,
)

@Dao
interface HarnessMessageDao {
    @Query("SELECT * FROM harness_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeForSession(sessionId: String): Flow<List<HarnessMessageEntity>>

    @Query("SELECT * FROM harness_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun listForSession(sessionId: String): List<HarnessMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: HarnessMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<HarnessMessageEntity>)

    @Query("DELETE FROM harness_messages WHERE sessionId = :sessionId AND createdAt >= :createdAt")
    suspend fun deleteFromTimestamp(sessionId: String, createdAt: Long)

    @Query("DELETE FROM harness_messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM harness_messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM harness_messages")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM harness_messages WHERE (:start IS NULL OR createdAt >= :start) AND (:end IS NULL OR createdAt < :end)")
    suspend fun countInRange(start: Long?, end: Long?): Int

    @Query("SELECT strftime('%Y-%m-%d', createdAt / 1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS count FROM harness_messages WHERE createdAt >= :start GROUP BY day ORDER BY day ASC")
    suspend fun queryHeatmap(start: Long): List<StatsDayCountResult>

    @Query("SELECT s.id AS id, s.title AS label, COUNT(m.id) AS count FROM harness_messages m JOIN harness_sessions s ON m.sessionId = s.id WHERE (:start IS NULL OR m.createdAt >= :start) AND (:end IS NULL OR m.createdAt < :end) GROUP BY s.id, s.title ORDER BY count DESC LIMIT :limit")
    suspend fun queryTopicRank(start: Long?, end: Long?, limit: Int = 20): List<StatsTopicRankResult>

    @Query("SELECT * FROM harness_messages WHERE (:start IS NULL OR createdAt >= :start) AND (:end IS NULL OR createdAt < :end) ORDER BY createdAt ASC")
    suspend fun listInRange(start: Long?, end: Long?): List<HarnessMessageEntity>
}

data class StatsDayCountResult(
    val day: String,
    val count: Int,
)

data class StatsTopicRankResult(
    val id: String,
    val label: String,
    val count: Int,
)
