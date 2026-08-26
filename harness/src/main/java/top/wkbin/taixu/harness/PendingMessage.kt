package top.wkbin.taixu.harness

import kotlinx.serialization.Serializable

/** User-facing projection of a durable queued prompt. */
@Serializable
data class PendingMessage(
    val text: String,
    val imageUrls: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)
