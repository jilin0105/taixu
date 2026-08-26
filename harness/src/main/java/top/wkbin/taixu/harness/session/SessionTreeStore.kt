package top.wkbin.taixu.harness.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.HarnessEntryEntity
import top.wkbin.taixu.core.database.HarnessRuntimeRepository
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.CapabilityEvent
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.harness.UserMessage

/** Serialization and active-branch projection for the immutable session tree. */
@Singleton
class SessionTreeStore @Inject constructor(
    private val repository: HarnessRuntimeRepository,
    private val json: Json,
    private val logger: AppLogger,
) {
    suspend fun ensureMainLane(sessionId: String) {
        repository.ensureLane(sessionId, MAIN_LANE)
    }

    suspend fun load(sessionId: String, laneName: String = MAIN_LANE): List<HarnessMessage> = runCatching {
        val lane = repository.ensureLane(sessionId, laneName)
        repository.branch(sessionId, lane.leafId).mapNotNull(::decode)
    }.onFailure { throwable ->
        logger.e("Failed to load harness branch for $sessionId/$laneName: ${throwable.message}", throwable)
    }.getOrDefault(emptyList())

    suspend fun append(sessionId: String, message: HarnessMessage, laneName: String = MAIN_LANE) {
        val lane = repository.ensureLane(sessionId, laneName)
        val entry = HarnessEntryEntity(
            id = message.id,
            sessionId = sessionId,
            parentId = lane.leafId,
            createdAt = message.createdAt,
            entryType = "message",
            customType = messageType(message),
            payloadJson = json.encodeToString(HarnessMessage.serializer(), message),
        )
        repository.appendToLane(sessionId, laneName, entry)
    }

    /** Navigate to the parent of [entryId], preserving the abandoned branch. */
    suspend fun rewindBefore(sessionId: String, entryId: String, laneName: String = MAIN_LANE) {
        val branch = activeEntries(sessionId, laneName)
        val target = branch.firstOrNull { it.id == entryId } ?: return
        repository.moveLane(sessionId, laneName, target.parentId)
    }

    suspend fun moveTo(sessionId: String, entryId: String?, laneName: String = MAIN_LANE) {
        repository.moveLane(sessionId, laneName, entryId)
    }

    suspend fun deleteSession(sessionId: String) {
        repository.deleteSessionData(sessionId)
    }

    suspend fun search(sessionId: String, query: String, limit: Int = 8): List<HarnessMessage> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return emptyList()
        return load(sessionId).asSequence()
            .filter { searchableText(it).lowercase().contains(needle) }
            .take(limit.coerceIn(1, 20))
            .toList()
    }

    suspend fun read(sessionId: String, messageId: String? = null, index: Int? = null): HarnessMessage? {
        val messages = load(sessionId)
        return when {
            !messageId.isNullOrBlank() -> messages.firstOrNull { it.id == messageId }
            index != null && messages.isNotEmpty() -> messages.getOrNull(index.coerceIn(0, messages.lastIndex))
            else -> null
        }
    }

    private suspend fun activeEntries(sessionId: String, laneName: String): List<HarnessEntryEntity> {
        val lane = repository.ensureLane(sessionId, laneName)
        return repository.branch(sessionId, lane.leafId)
    }

    private fun decode(entity: HarnessEntryEntity): HarnessMessage? {
        if (entity.entryType != "message") return null
        return runCatching { json.decodeFromString(HarnessMessage.serializer(), entity.payloadJson) }
            .onFailure { logger.w("Skipping invalid harness entry ${entity.id}: ${it.message}") }
            .getOrNull()
    }

    private fun messageType(message: HarnessMessage): String = when (message) {
        is UserMessage -> "user"
        is AssistantText -> "assistant"
        is ToolCall -> "tool_call"
        is ToolResult -> "tool_result"
        is CapabilityEvent -> "capability_event"
    }

    private fun searchableText(message: HarnessMessage): String = when (message) {
        is CapabilityEvent -> "${message.kind} ${message.name} ${message.details}"
        is UserMessage -> message.text
        is AssistantText -> "${message.text}\n${message.reasoning.orEmpty()}"
        is ToolCall -> "${message.rawToolName.orEmpty()} ${message.tool} ${message.args} ${message.reasoning.orEmpty()}"
        is ToolResult -> message.output
    }

    companion object {
        const val MAIN_LANE = "main"
    }
}
