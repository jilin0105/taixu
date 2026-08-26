package top.wkbin.taixu.harness

import kotlinx.serialization.Serializable

/** User-facing projection of a durable queued prompt. */
@Serializable
data class PendingMessage(
    val text: String,
    val imageUrls: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

/** Durable prompt plus its delivery semantics, exposed for queue-aware UI. */
data class QueuedPrompt(
    val id: String,
    val queue: top.wkbin.taixu.harness.queue.PromptQueue,
    val message: PendingMessage,
)
