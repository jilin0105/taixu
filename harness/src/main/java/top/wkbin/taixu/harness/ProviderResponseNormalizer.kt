package top.wkbin.taixu.harness

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** Protocol normalization boundary shared by the main loop and independently testable. */
@Singleton
class ProviderResponseNormalizer @Inject constructor(
    private val json: Json,
) {
    fun normalize(result: ChatResult, rawText: String, toolsEnabled: Boolean): NormalizedProviderResponse {
        // Structured native calls are authoritative: skip the textual codec so the display text
        // stays byte-identical with rawText and stray markers in the content do not get stripped
        // as if they were executed. The textual branch only runs as a fallback.
        if (!toolsEnabled || result.toolCalls.isNotEmpty()) {
            return NormalizedProviderResponse(
                result = result,
                rawText = rawText,
                displayText = rawText,
                toolCalls = result.toolCalls,
                textToolCallCount = 0,
                invalidMarkerCount = 0,
                hasUnresolvedMarkers = false,
                scavengedToolCallCount = 0,
            )
        }
        val textNormalization = TextToolCallCodec.normalize(json, rawText)
        if (textNormalization.calls.isNotEmpty() || textNormalization.hasUnresolvedMarkers) {
            return NormalizedProviderResponse(
                result = result,
                rawText = rawText,
                displayText = textNormalization.displayText,
                toolCalls = textNormalization.calls,
                textToolCallCount = textNormalization.calls.size,
                invalidMarkerCount = textNormalization.invalidMarkerCount,
                hasUnresolvedMarkers = textNormalization.hasUnresolvedMarkers,
                scavengedToolCallCount = 0,
            )
        }

        // Scavenge: DeepSeek-R1 and similar reasoning models sometimes complete their tool-call
        // deliberation inside reasoning_content but forget to emit the call in content. If the
        // content produced zero calls and zero text markers, scan the tail of reasoning_content
        // for any tool-call markers as a last-resort rescue. This prevents silent no-ops where
        // the model "thought" a call but never "said" it.
        val scavenged = scavengeFromReasoning(result.reasoningContent)
        if (scavenged.isNotEmpty()) {
            return NormalizedProviderResponse(
                result = result,
                rawText = rawText,
                displayText = rawText,
                toolCalls = scavenged,
                textToolCallCount = 0,
                invalidMarkerCount = 0,
                hasUnresolvedMarkers = false,
                scavengedToolCallCount = scavenged.size,
            )
        }

        return NormalizedProviderResponse(
            result = result,
            rawText = rawText,
            displayText = rawText,
            toolCalls = emptyList(),
            textToolCallCount = 0,
            invalidMarkerCount = 0,
            hasUnresolvedMarkers = false,
            scavengedToolCallCount = 0,
        )
    }

    /**
     * Scan the tail of [reasoningContent] for tool-call markers that the model forgot to emit
     * in the content field. Only the last [SCAVENGE_WINDOW_CHARS] characters are examined to
     * keep the search bounded; tool calls written early in a long reasoning trace are stale by
     * the time the model reaches its conclusion.
     *
     * Returns an empty list when nothing is found, leaving the caller to treat the turn as a
     * normal no-tool assistant reply.
     */
    private fun scavengeFromReasoning(reasoningContent: String?): List<ApiToolCallSpec> {
        if (reasoningContent.isNullOrBlank()) return emptyList()
        val tail = if (reasoningContent.length > SCAVENGE_WINDOW_CHARS) {
            reasoningContent.takeLast(SCAVENGE_WINDOW_CHARS)
        } else {
            reasoningContent
        }
        return TextToolCallCodec.normalize(json, tail).calls
    }

    private companion object {
        /** Tail window size for scavenge: large enough to capture multi-param calls, bounded
         *  to avoid re-parsing a 128K reasoning trace on every turn. */
        const val SCAVENGE_WINDOW_CHARS = 4_096
    }
}

data class NormalizedProviderResponse(
    val result: ChatResult,
    val rawText: String,
    val displayText: String,
    val toolCalls: List<ApiToolCallSpec>,
    val textToolCallCount: Int,
    val invalidMarkerCount: Int,
    val hasUnresolvedMarkers: Boolean,
    /** Number of tool calls recovered from reasoning_content when content had none. */
    val scavengedToolCallCount: Int = 0,
)