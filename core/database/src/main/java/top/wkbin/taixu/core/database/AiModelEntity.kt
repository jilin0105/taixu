package top.wkbin.taixu.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Harness 可用的模型配置。密钥只存引用（Android Keystore 别名），
 * 明文 Key 永不落库。
 */
@Entity(tableName = "harness_models")
data class AiModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val provider: String,
    val model: String,
    val baseUrl: String = "",
    val apiKey: String = "",
    val secretRef: String = "",
    val isActive: Boolean = false,
    val createdAt: Long,
    /** 推理参数（null = 使用服务端默认）：温度，0.0 ~ 2.0。 */
    val temperature: Float? = null,
    /** 推理参数（null = 使用服务端默认）：单次回复最大 token 数。 */
    val maxTokens: Int? = null,
    /** 推理参数（null = 使用服务端默认）：核采样阈值，0.0 ~ 1.0。 */
    val topP: Float? = null,
    /** 推理开关：null = auto（跟随模型默认）；"disabled" / "enabled"。 */
    val reasoningMode: String? = null,
    /** 推理强度：null = 默认；"low" / "medium" / "high"。 */
    val reasoningEffort: String? = null,
)

@Dao
interface AiModelDao {
    @Query("SELECT * FROM harness_models ORDER BY isActive DESC, createdAt ASC")
    fun observeAll(): Flow<List<AiModelEntity>>

    @Query("SELECT * FROM harness_models WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AiModelEntity?

    @Query("SELECT * FROM harness_models WHERE isActive = 1 LIMIT 1")
    suspend fun activeModel(): AiModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(model: AiModelEntity)

    @Query("UPDATE harness_models SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE harness_models SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: String)

    @Query("DELETE FROM harness_models WHERE id = :id")
    suspend fun delete(id: String)
}
