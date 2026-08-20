package top.wkbin.taixu.core.tools

import top.wkbin.taixu.core.database.ToolDao
import top.wkbin.taixu.core.database.ToolEntity
import top.wkbin.taixu.core.model.ToolManifest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Tool persistence and registry boundary; UI layers do not access Room directly. */
@Singleton
class ToolRepository @Inject constructor(
    private val toolDao: ToolDao,
    private val toolRegistry: ToolRegistry,
) {
    fun observeTools(): Flow<List<ToolEntity>> = toolDao.observeAll()
    suspend fun findById(id: String): ToolEntity? = toolDao.findById(id)
    suspend fun upsert(tool: ToolEntity) = toolDao.upsert(tool)
    suspend fun updateState(id: String, state: String) = toolDao.updateState(id, state)
    suspend fun updateStateAndInstalledVersion(id: String, state: String, installedVersion: String?) =
        toolDao.updateStateAndInstalledVersion(id, state, installedVersion)
    fun manifests(): List<ToolManifest> = toolRegistry.load()
    fun manifest(id: String): ToolManifest? = manifests().firstOrNull { it.id == id }
}
