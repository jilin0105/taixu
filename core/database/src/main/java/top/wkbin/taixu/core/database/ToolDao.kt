package top.wkbin.taixu.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolDao {

    @Query("SELECT * FROM tools ORDER BY name")
    fun observeAll(): Flow<List<ToolEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tool: ToolEntity)

    @Query("SELECT * FROM tools WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ToolEntity?

    @Query("UPDATE tools SET state = :state WHERE id = :id")
    suspend fun updateState(id: String, state: String)

    @Query("UPDATE tools SET state = :state, installedVersion = :installedVersion WHERE id = :id")
    suspend fun updateStateAndInstalledVersion(
        id: String,
        state: String,
        installedVersion: String?,
    )
}
