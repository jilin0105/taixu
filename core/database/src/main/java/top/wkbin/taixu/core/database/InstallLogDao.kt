package top.wkbin.taixu.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallLogDao {
    @Insert
    suspend fun insert(log: InstallLogEntity)

    @Query("SELECT * FROM install_logs WHERE toolId = :toolId ORDER BY createdAt DESC")
    fun observeForTool(toolId: String): Flow<List<InstallLogEntity>>
}
