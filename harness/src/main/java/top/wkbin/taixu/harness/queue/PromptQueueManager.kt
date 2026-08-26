package top.wkbin.taixu.harness.queue

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import top.wkbin.taixu.core.database.HarnessEntryEntity
import top.wkbin.taixu.core.database.HarnessQueueItemEntity
import top.wkbin.taixu.core.database.HarnessRuntimeRepository
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.PendingMessage
import top.wkbin.taixu.harness.UserMessage
import top.wkbin.taixu.harness.session.SessionTreeStore

enum class PromptQueue(val id: String) {
    STEER("steer"), FOLLOW_UP("follow_up"), NEXT_RUN("next_run")
}

/** Durable prompt queues with explicit consumption timing. */
@Singleton
class PromptQueueManager @Inject constructor(
    private val repository: HarnessRuntimeRepository,
    private val json: Json,
) {
    suspend fun enqueue(sessionId: String, queue: PromptQueue, prompt: PendingMessage): String {
        val operationId = repository.findLane(sessionId, SessionTreeStore.MAIN_LANE)?.currentOperationId
        val id = UUID.randomUUID().toString()
        repository.enqueue(
            HarnessQueueItemEntity(
                id = id,
                sessionId = sessionId,
                laneName = SessionTreeStore.MAIN_LANE,
                operationId = operationId,
                queueType = queue.id,
                createdAt = prompt.createdAt,
                payloadJson = json.encodeToString(PendingMessage.serializer(), prompt),
            ),
        )
        return id
    }

    suspend fun list(sessionId: String, queue: PromptQueue): List<Pair<String, PendingMessage>> =
        repository.listQueue(sessionId, SessionTreeStore.MAIN_LANE, queue.id).mapNotNull { item ->
            runCatching { item.id to json.decodeFromString(PendingMessage.serializer(), item.payloadJson) }.getOrNull()
        }

    suspend fun first(sessionId: String, queue: PromptQueue): Pair<String, PendingMessage>? =
        list(sessionId, queue).firstOrNull()

    suspend fun cancel(sessionId: String, queue: PromptQueue, index: Int) {
        list(sessionId, queue).getOrNull(index)?.first?.let { repository.cancelQueued(it) }
    }

    suspend fun clear(sessionId: String, queue: PromptQueue) {
        repository.clearQueue(sessionId, SessionTreeStore.MAIN_LANE, queue.id)
    }

    /** Atomically turns queued prompts into immutable entries on the active lane. */
    suspend fun consume(sessionId: String, queue: PromptQueue, limit: Int = Int.MAX_VALUE): List<UserMessage> {
        val items = repository.listQueue(sessionId, SessionTreeStore.MAIN_LANE, queue.id).take(limit)
        val consumed = ArrayList<UserMessage>(items.size)
        for (item in items) {
            val prompt = runCatching { json.decodeFromString(PendingMessage.serializer(), item.payloadJson) }.getOrNull()
                ?: continue
            val lane = repository.ensureLane(sessionId, SessionTreeStore.MAIN_LANE)
            val message = UserMessage(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                text = prompt.text,
                imageUrls = prompt.imageUrls,
            )
            val entry = HarnessEntryEntity(
                id = message.id,
                sessionId = sessionId,
                parentId = lane.leafId,
                createdAt = message.createdAt,
                entryType = "message",
                customType = "user",
                payloadJson = json.encodeToString(HarnessMessage.serializer(), message),
            )
            repository.consumeQueued(
                itemId = item.id,
                entry = entry,
                lane = lane.copy(leafId = entry.id, updatedAt = System.currentTimeMillis()),
            )
            consumed += message
        }
        return consumed
    }
}
