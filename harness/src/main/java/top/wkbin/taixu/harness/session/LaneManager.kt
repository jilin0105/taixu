package top.wkbin.taixu.harness.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import top.wkbin.taixu.core.database.HarnessLaneEntity
import top.wkbin.taixu.core.database.HarnessRuntimeRepository
import top.wkbin.taixu.harness.HarnessMessage

/** Public lane surface: create, inspect, navigate, and project shared conversation branches. */
@Singleton
class LaneManager @Inject constructor(
    private val repository: HarnessRuntimeRepository,
    private val treeStore: SessionTreeStore,
) {
    suspend fun create(sessionId: String, name: String, atEntryId: String? = null): HarnessLaneEntity {
        require(LANE_NAME.matches(name)) { "Invalid lane name: $name" }
        require(repository.findLane(sessionId, name) == null) { "Lane already exists: $name" }
        return repository.ensureLane(sessionId, name, atEntryId)
    }

    suspend fun get(sessionId: String, name: String): HarnessLaneEntity? = repository.findLane(sessionId, name)
    fun observe(sessionId: String): Flow<List<HarnessLaneEntity>> = repository.observeLanes(sessionId)
    suspend fun transcript(sessionId: String, name: String): List<HarnessMessage> = treeStore.load(sessionId, name)

    suspend fun navigate(sessionId: String, name: String, targetEntryId: String?) {
        val lane = repository.findLane(sessionId, name) ?: error("Unknown lane $name")
        check(lane.currentOperationId == null) { "Lane $name is busy" }
        repository.moveLane(sessionId, name, targetEntryId)
    }

    companion object {
        private val LANE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
    }
}
