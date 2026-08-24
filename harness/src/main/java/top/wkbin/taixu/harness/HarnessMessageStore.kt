package top.wkbin.taixu.harness

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.HarnessMessageEntity
import top.wkbin.taixu.core.database.HarnessMessageRepository

/** Owns transcript serialization, storage-size policy, and persistence failures. */
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
        val safeMessage = sanitize(message)
        runCatching {
            repository.insert(
                HarnessMessageEntity(
                    id = safeMessage.id,
                    sessionId = sessionId,
                    createdAt = safeMessage.createdAt,
                    type = safeMessage::class.simpleName.orEmpty(),
                    payloadJson = json.encodeToString(HarnessMessage.serializer(), safeMessage),
                ),
            )
        }.onFailure { throwable ->
            logger.e("Failed to persist message for session $sessionId: ${throwable.message}", throwable)
        }
    }

    suspend fun deleteFromTimestamp(sessionId: String, createdAt: Long) =
        repository.deleteFromTimestamp(sessionId, createdAt)

    suspend fun deleteByIds(ids: List<String>) = repository.deleteByIds(ids)

    private fun sanitize(message: HarnessMessage): HarnessMessage = when (message) {
        is CapabilityEvent -> message
        is ToolResult -> if (message.output.length > MAX_STORAGE_STRING_LENGTH) {
            val head = message.output.take(STORAGE_KEEP_LENGTH)
            val tail = message.output.takeLast(STORAGE_KEEP_LENGTH)
            message.copy(output = "$head\n\n... [工具输出过长（共 ${message.output.length} 字符），已截断保存] ...\n\n$tail")
        } else message
        is AssistantText -> message.copy(
            text = message.text.truncate("文本过长已截断"),
            reasoning = message.reasoning?.truncate("推理过程过长已截断"),
        )
        is UserMessage -> message.copy(text = message.text.truncate("用户消息过长已截断"))
        is ToolCall -> message
    }

    private fun String.truncate(note: String): String =
        if (length > MAX_STORAGE_STRING_LENGTH) take(MAX_STORAGE_STRING_LENGTH) + "\n... [$note]" else this

    private companion object {
        const val MAX_STORAGE_STRING_LENGTH = 128 * 1024
        const val STORAGE_KEEP_LENGTH = 60 * 1024
    }
}
