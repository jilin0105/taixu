package top.wkbin.taixu.harness.compaction

import kotlinx.serialization.Serializable
import top.wkbin.taixu.harness.HarnessMessage

@Serializable
data class CompactionPayload(
    val sourceLeafId: String?,
    val summary: String,
    val retainedMessagesJson: String,
    val compactedMessageCount: Int,
    val retainedMessageCount: Int,
    val estimatedTokensBefore: Int,
    val createdAt: Long,
)

data class CompactedContext(
    val summary: String? = null,
    val messages: List<HarnessMessage> = emptyList(),
)
