package top.wkbin.taixu.harness.compaction

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import top.wkbin.taixu.core.database.HarnessEntryEntity
import top.wkbin.taixu.core.database.HarnessRuntimeRepository
import top.wkbin.taixu.harness.ContextWindowPolicy
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.session.SessionTreeStore

/** Persists compaction as an immutable tree entry and projects provider context from it. */
@Singleton
class CompactionManager @Inject constructor(
    private val repository: HarnessRuntimeRepository,
    private val json: Json,
) {
    suspend fun project(sessionId: String, laneName: String = SessionTreeStore.MAIN_LANE): CompactedContext {
        val lane = repository.ensureLane(sessionId, laneName)
        val entries = repository.branch(sessionId, lane.leafId)
        val compactionIndex = entries.indexOfLast { it.entryType == ENTRY_TYPE }
        if (compactionIndex < 0) return CompactedContext(messages = entries.mapNotNull(::decodeMessage))

        val payload = json.decodeFromString(CompactionPayload.serializer(), entries[compactionIndex].payloadJson)
        val retained = json.decodeFromString(ListSerializer(HarnessMessage.serializer()), payload.retainedMessagesJson)
        val after = entries.drop(compactionIndex + 1).mapNotNull(::decodeMessage)
        return CompactedContext(summary = payload.summary, messages = retained + after)
    }

    suspend fun compact(
        sessionId: String,
        context: CompactedContext,
        keepFromIndex: Int,
        laneName: String = SessionTreeStore.MAIN_LANE,
    ): CompactedContext {
        require(keepFromIndex in 1..context.messages.size) { "Compaction must remove at least one message" }
        val lane = repository.ensureLane(sessionId, laneName)
        val collapsed = context.messages.take(keepFromIndex)
        val retained = context.messages.drop(keepFromIndex)
        val incrementalSummary = ContextWindowPolicy.buildHistorySummary(collapsed)
        val summary = listOfNotNull(context.summary, incrementalSummary.takeIf { it.isNotBlank() })
            .joinToString("\n\n")
            .take(MAX_SUMMARY_CHARS)
        val now = System.currentTimeMillis()
        val payload = CompactionPayload(
            sourceLeafId = lane.leafId,
            summary = summary,
            retainedMessagesJson = json.encodeToString(ListSerializer(HarnessMessage.serializer()), retained),
            compactedMessageCount = collapsed.size,
            retainedMessageCount = retained.size,
            estimatedTokensBefore = context.messages.sumOf(::messageTokens),
            createdAt = now,
        )
        val entry = HarnessEntryEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            parentId = lane.leafId,
            createdAt = now,
            entryType = ENTRY_TYPE,
            customType = null,
            payloadJson = json.encodeToString(CompactionPayload.serializer(), payload),
        )
        repository.appendToLane(sessionId, laneName, entry)
        return CompactedContext(summary, retained)
    }

    private fun decodeMessage(entry: HarnessEntryEntity): HarnessMessage? =
        entry.takeIf { it.entryType == "message" }?.let {
            runCatching { json.decodeFromString(HarnessMessage.serializer(), it.payloadJson) }.getOrNull()
        }

    private fun messageTokens(message: HarnessMessage): Int = ContextWindowPolicy.estimateTokens(message.toString())

    companion object {
        const val ENTRY_TYPE = "compaction"
        private const val MAX_SUMMARY_CHARS = 4_800
    }
}
