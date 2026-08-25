package top.wkbin.taixu.harness

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.HarnessMessageEntity
import top.wkbin.taixu.core.database.HarnessMessageRepository

/** Owns lossless transcript serialization and persistence failures. */
@Singleton
class HarnessMessageStore @Inject constructor(
    private val repository: HarnessMessageRepository,
    private val json: Json,
    private val logger: AppLogger,
) {
    suspend fun load(sessionId: String): List<HarnessMessage> = runCatching {
        repository.listForSession(sessionId).mapNotNull { entity ->
            runCatching { json.decodeFromString(HarnessMessage.serializer(), entity.payloadJson) }.getOrNull()
        }
    }.onFailure { throwable ->
        logger.e("Failed to load history for session $sessionId: ${throwable.message}", throwable)
    }.getOrDefault(emptyList())

    suspend fun insert(sessionId: String, message: HarnessMessage) {
        runCatching {
            repository.insert(
                HarnessMessageEntity(
                    id = message.id,
                    sessionId = sessionId,
                    createdAt = message.createdAt,
                    type = message::class.simpleName.orEmpty(),
                    payloadJson = json.encodeToString(HarnessMessage.serializer(), message),
                ),
            )
        }.onFailure { throwable ->
            logger.e("Failed to persist message for session $sessionId: ${throwable.message}", throwable)
        }
    }

    suspend fun deleteFromTimestamp(sessionId: String, createdAt: Long) =
        repository.deleteFromTimestamp(sessionId, createdAt)

    suspend fun deleteByIds(ids: List<String>) = repository.deleteByIds(ids)

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
            index != null -> messages.getOrNull(index.coerceIn(0, messages.lastIndex.coerceAtLeast(0)))
            else -> null
        }
    }

    private fun searchableText(message: HarnessMessage): String = when (message) {
        is CapabilityEvent -> "${message.kind} ${message.name} ${message.details}"
        is UserMessage -> message.text
        is AssistantText -> "${message.text}\n${message.reasoning.orEmpty()}"
        is ToolCall -> "${message.rawToolName.orEmpty()} ${message.tool} ${message.args} ${message.reasoning.orEmpty()}"
        is ToolResult -> message.output
    }

}
