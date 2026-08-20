package top.wkbin.taixu.core.tools

import top.wkbin.taixu.core.database.InstallLogDao
import top.wkbin.taixu.core.database.InstallLogEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Persistence boundary for redacted installation and verification logs. */
@Singleton
class InstallLogRepository @Inject constructor(
    private val dao: InstallLogDao,
) {
    suspend fun insert(log: InstallLogEntity) = dao.insert(log)
    suspend fun deleteForTool(toolId: String) = dao.deleteForTool(toolId)
    fun observeForTool(toolId: String): Flow<List<InstallLogEntity>> = dao.observeForTool(toolId)
}
