package top.wkbin.taixu.harness.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import top.wkbin.taixu.core.database.HarnessLaneEntity
import top.wkbin.taixu.core.database.HarnessRuntimeRepository
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.UserMessage

enum class ConversationBranchKind { MAIN, BRANCH, SUBAGENT, HISTORY }

data class ConversationBranch(
    val id: String,
    val name: String,
    val leafId: String?,
    val depth: Int,
    val preview: String,
    val updatedAt: Long,
    val kind: ConversationBranchKind,
    val isCurrent: Boolean,
    val isBusy: Boolean,
    val faulted: Boolean,
    val toolCallCount: Int,
)

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

    /** Projects named lanes and otherwise-unreferenced tree leaves into a branch browser model. */
    suspend fun branches(sessionId: String): List<ConversationBranch> {
        val entries = repository.listEntries(sessionId)
        val lanes = repository.observeLanes(sessionId).first()
        val main = lanes.firstOrNull { it.name == SessionTreeStore.MAIN_LANE }
        val parentIds = entries.mapNotNullTo(mutableSetOf()) { it.parentId }
        val leafIds = entries.asSequence().map { it.id }.filter { it !in parentIds }.toList()
        val targets = (leafIds + lanes.mapNotNull { it.leafId } + listOfNotNull(main?.leafId)).distinct()

        return targets.mapIndexed { index, leafId ->
            val pathEntries = repository.branch(sessionId, leafId)
            val pathIds = pathEntries.mapTo(hashSetOf()) { it.id }
            val namedLane = lanes
                .filter { it.name != SessionTreeStore.MAIN_LANE && it.leafId in pathIds }
                .maxByOrNull { lane -> pathEntries.indexOfLast { it.id == lane.leafId } }
            val messages = treeStore.loadAt(sessionId, leafId)
            val kind = when {
                namedLane?.name?.startsWith(SUBAGENT_PREFIX) == true -> ConversationBranchKind.SUBAGENT
                namedLane?.name?.startsWith(BRANCH_PREFIX) == true -> ConversationBranchKind.BRANCH
                leafId == main?.leafId -> ConversationBranchKind.MAIN
                else -> ConversationBranchKind.HISTORY
            }
            val displayName = when (kind) {
                ConversationBranchKind.MAIN -> "主线"
                ConversationBranchKind.BRANCH -> namedLane?.name.orEmpty()
                    .removePrefix(BRANCH_PREFIX)
                    .substringAfter(':', "分支 ${index + 1}")
                    .replace('-', ' ')
                ConversationBranchKind.SUBAGENT -> namedLane?.name.orEmpty()
                    .removePrefix(SUBAGENT_PREFIX)
                    .substringBefore(':')
                    .ifBlank { "子智能体" }
                ConversationBranchKind.HISTORY -> "历史分支 ${index + 1}"
            }
            val preview = messages.asReversed().firstNotNullOfOrNull { message ->
                when (message) {
                    is AssistantText -> message.text.takeIf { it.isNotBlank() }
                    is UserMessage -> message.text.takeIf { it.isNotBlank() }
                    else -> null
                }
            }.orEmpty()
            ConversationBranch(
                // Always key by leaf: one named lane can sit on many leaf paths (siblings/descendants).
                id = namedLane?.let { "${it.name}@$leafId" } ?: "leaf:$leafId",
                name = displayName,
                leafId = leafId,
                depth = messages.size,
                preview = preview,
                updatedAt = pathEntries.lastOrNull()?.createdAt ?: namedLane?.updatedAt ?: 0L,
                kind = kind,
                isCurrent = leafId == main?.leafId,
                isBusy = namedLane?.currentOperationId != null || (leafId == main?.leafId && main.currentOperationId != null),
                faulted = namedLane?.faulted == true,
                toolCallCount = messages.count { it is ToolCall },
            )
        }.sortedWith(compareByDescending<ConversationBranch> { it.isCurrent }.thenByDescending { it.updatedAt })
    }

    suspend fun createConversationBranch(sessionId: String, displayName: String, atEntryId: String): HarnessLaneEntity {
        val slug = displayName.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-', '_').take(48)
            .ifBlank { "branch" }
        val laneName = "$BRANCH_PREFIX${java.util.UUID.randomUUID().toString().take(8)}:$slug"
        return create(sessionId, laneName, atEntryId)
    }

    suspend fun navigate(sessionId: String, name: String, targetEntryId: String?) {
        val lane = repository.findLane(sessionId, name) ?: error("Unknown lane $name")
        check(lane.currentOperationId == null) { "Lane $name is busy" }
        repository.moveLane(sessionId, name, targetEntryId)
    }

    companion object {
        private const val BRANCH_PREFIX = "branch:"
        private const val SUBAGENT_PREFIX = "subagent:"
        private val LANE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
    }
}
