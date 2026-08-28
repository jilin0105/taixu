package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.QueuedPrompt
import top.wkbin.taixu.harness.events.HarnessEvent
import top.wkbin.taixu.harness.queue.PromptQueue
import top.wkbin.taixu.harness.session.ConversationBranch
import top.wkbin.taixu.harness.session.ConversationBranchKind
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName

@Composable
internal fun ChatWorkbenchStrip(
    currentBranch: ConversationBranch?,
    runtimeEvents: List<HarnessEvent>,
    running: Boolean,
    memoryCount: Int,
    scratchpadCount: Int,
    onOpenBranches: () -> Unit,
    onOpenRuntime: () -> Unit,
    onOpenMemory: () -> Unit,
) {
    val activeRound = runtimeEvents.filterIsInstance<HarnessEvent.ProviderRoundStarted>().lastOrNull()?.round
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WorkspacePill(
                icon = RuntimeIconName.Hub,
                title = stringResource(R.string.chat_branch_pill_title),
                subtitle = currentBranch?.name ?: stringResource(R.string.chat_main_line),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                onClick = onOpenBranches,
            )
            WorkspacePill(
                icon = RuntimeIconName.Logs,
                title = stringResource(R.string.chat_runtime_details),
                subtitle = activeRound?.let { stringResource(R.string.chat_round_number, it + 1) }
                    ?: stringResource(R.string.chat_event_count, runtimeEvents.size),
                tint = if (running) Color(0xFF7C4DFF) else MaterialTheme.colorScheme.tertiary,
                highlight = running,
                modifier = Modifier.weight(1f),
                onClick = onOpenRuntime,
            )
            WorkspacePill(
                icon = RuntimeIconName.Brain,
                title = stringResource(R.string.chat_memory_pill_title),
                subtitle = stringResource(R.string.chat_memory_pill_subtitle, memoryCount, scratchpadCount),
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
                onClick = onOpenMemory,
            )
        }
    }
}

@Composable
private fun WorkspacePill(
    icon: RuntimeIconName,
    title: String,
    subtitle: String,
    tint: Color,
    modifier: Modifier,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = tint.copy(alpha = if (highlight) 0.16f else 0.09f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RuntimeIcon(icon, Modifier.size(15.dp), tint)
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RuntimeIcon(RuntimeIconName.ChevronRight, Modifier.size(13.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BranchBrowserSheet(
    branches: List<ConversationBranch>,
    running: Boolean,
    onDismiss: () -> Unit,
    onSwitch: (ConversationBranch) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.chat_branches_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.chat_branches_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )
            if (branches.isEmpty()) {
                RuntimeCard {
                    Text(stringResource(R.string.chat_no_branches), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val conversation = branches.filter { it.kind != ConversationBranchKind.SUBAGENT }
                    val subagents = branches.filter { it.kind == ConversationBranchKind.SUBAGENT }
                    item { SectionLabel(stringResource(R.string.chat_conversation_paths), conversation.size) }
                    items(conversation, key = { it.leafId ?: it.id }) { branch ->
                        BranchCard(branch, enabled = !running && !branch.isBusy, onClick = { onSwitch(branch) })
                    }
                    if (subagents.isNotEmpty()) {
                        item { SectionLabel(stringResource(R.string.chat_subagent_lanes), subagents.size) }
                        items(subagents, key = { it.leafId ?: it.id }) { branch -> BranchCard(branch, enabled = false, onClick = {}) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BranchCard(branch: ConversationBranch, enabled: Boolean, onClick: () -> Unit) {
    val tint = when (branch.kind) {
        ConversationBranchKind.MAIN -> MaterialTheme.colorScheme.primary
        ConversationBranchKind.BRANCH -> Color(0xFF7C4DFF)
        ConversationBranchKind.SUBAGENT -> MaterialTheme.colorScheme.tertiary
        ConversationBranchKind.HISTORY -> MaterialTheme.colorScheme.secondary
    }
    RuntimeCard(
        containerColor = if (branch.isCurrent) tint.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = if (branch.isCurrent) tint.copy(alpha = 0.55f) else Color.Transparent,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        onClick = if (enabled && !branch.isCurrent) onClick else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).background(tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                RuntimeIcon(if (branch.kind == ConversationBranchKind.SUBAGENT) RuntimeIconName.Bot else RuntimeIconName.Hub, Modifier.size(18.dp), tint)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(branch.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (branch.isCurrent) MiniBadge(stringResource(R.string.chat_badge_current), tint)
                    if (branch.isBusy) MiniBadge(stringResource(R.string.chat_badge_busy), Color(0xFF7C4DFF))
                    if (branch.faulted) MiniBadge(stringResource(R.string.chat_badge_faulted), MaterialTheme.colorScheme.error)
                }
                Text(
                    branch.preview.ifBlank { stringResource(R.string.chat_branch_start) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.chat_branch_records, branch.depth) + " · " + stringResource(R.string.chat_branch_tool_calls, branch.toolCallCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (enabled && !branch.isCurrent) RuntimeIcon(RuntimeIconName.ChevronRight, Modifier.size(18.dp), tint)
        }
    }
}

@Composable
private fun MiniBadge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuntimeTimelineSheet(
    events: List<HarnessEvent>,
    messages: List<top.wkbin.taixu.harness.HarnessMessage>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.chat_runtime_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.chat_runtime_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (events.isEmpty()) {
                RuntimeCard { Text(stringResource(R.string.chat_no_events), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                // 汇总统计
                val roundCount = events.count { it is HarnessEvent.ProviderRoundStarted }
                val toolCount = events.count { it is HarnessEvent.ToolCallStarted }
                val approvalCount = events.count { it is HarnessEvent.ApprovalRequested }
                val recoveryCount = events.count { it is HarnessEvent.RecoveryApplied }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatPill(RuntimeIconName.Brain, roundCount.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    StatPill(RuntimeIconName.Wrench, toolCount.toString(), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    StatPill(RuntimeIconName.Shield, approvalCount.toString(), Color(0xFFB25E00), Modifier.weight(1f))
                    StatPill(RuntimeIconName.Refresh, recoveryCount.toString(), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(bottom = 8.dp))

                val (rounds, lifecycle) = remember(events, messages) { buildRoundGroups(events, messages) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 最新一轮在最上，组内按发生顺序链式展示；轮间以分隔线区隔。
                    items(rounds.asReversed(), key = { "round-${it.key}" }) { round ->
                        RoundSection(round)
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                    if (lifecycle.isNotEmpty()) {
                        item(key = "lifecycle-header") {
                            Text(
                                stringResource(R.string.chat_tl_lifecycle),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(lifecycle, key = { "life-${it.timestamp}-${eventKey(it)}" }) { event ->
                            RuntimeEventRow(event)
                        }
                    }
                }
            }
        }
    }
}

/** 某一轮的完整调用链：模型响应 + 工具执行 + 审批事件。 */
private class RoundGroup(
    val key: Long,
    val roundNumber: Int,
    val modelId: String?,
    val startedAt: Long,
) {
    val entries = mutableListOf<TimelineEntry>()
}

/** 轮内条目：展开详情时从 [messages] 回查 payload。 */
private sealed interface TimelineEntry {
    val timestamp: Long

    data class Assistant(
        override val timestamp: Long,
        val entryId: String,
        val inputTokens: Long,
        val outputTokens: Long,
        val message: top.wkbin.taixu.harness.AssistantText?,
    ) : TimelineEntry

    data class Tool(
        override val timestamp: Long,
        val callId: String,
        var name: String,
        var settled: HarnessEvent.ToolCallSettled?,
        var callMessage: top.wkbin.taixu.harness.ToolCall?,
        var result: top.wkbin.taixu.harness.ToolResult?,
    ) : TimelineEntry

    data class Approval(override val timestamp: Long, val toolName: String, val riskLevel: String) : TimelineEntry
}

/** 把扁平事件流切成（轮次组，会话级生命周期）两段；tool call 与 settled 结果就地配对。 */
private fun buildRoundGroups(
    events: List<HarnessEvent>,
    messages: List<top.wkbin.taixu.harness.HarnessMessage>,
): Pair<List<RoundGroup>, List<HarnessEvent>> {
    val toolResultsById = messages.filterIsInstance<top.wkbin.taixu.harness.ToolResult>()
        .associateBy { it.toolCallId }
    val callsById = messages.filterIsInstance<top.wkbin.taixu.harness.ToolCall>().associateBy { it.id }
    val assistantsById = messages.filterIsInstance<top.wkbin.taixu.harness.AssistantText>().associateBy { it.id }

    val rounds = mutableListOf<RoundGroup>()
    val lifecycle = mutableListOf<HarnessEvent>()
    var current: RoundGroup? = null
    val toolIndexByCallId = mutableMapOf<String, Int>()

    fun newGroup(event: HarnessEvent.ProviderRoundStarted) {
        current = RoundGroup(System.nanoTime(), event.round, event.modelId, event.timestamp).also { rounds += it }
        toolIndexByCallId.clear()
    }

    fun getOrCreateGroup(): RoundGroup {
        return current ?: RoundGroup(System.nanoTime(), 0, null, System.currentTimeMillis()).also {
            rounds += it
            current = it
        }
    }

    for (event in events) {
        when (event) {
            is HarnessEvent.ProviderRoundStarted -> newGroup(event)
            is HarnessEvent.ProviderRoundSettled -> getOrCreateGroup().entries.add(
                TimelineEntry.Assistant(
                    event.timestamp,
                    event.entryId.orEmpty(),
                    event.inputTokens,
                    event.outputTokens,
                    assistantsById[event.entryId],
                ),
            )
            is HarnessEvent.ToolCallStarted -> {
                val group = getOrCreateGroup()
                val entry = TimelineEntry.Tool(
                    timestamp = event.timestamp,
                    callId = event.toolCallId,
                    name = event.toolName,
                    settled = null,
                    callMessage = callsById[event.toolCallId],
                    result = toolResultsById[event.toolCallId],
                )
                group.entries.add(entry)
                toolIndexByCallId[event.toolCallId] = group.entries.lastIndex
            }
            is HarnessEvent.ToolCallSettled -> {
                val group = getOrCreateGroup()
                val index = toolIndexByCallId[event.toolCallId]
                val entry = (index?.let { group.entries.getOrNull(it) } as? TimelineEntry.Tool)
                    ?: TimelineEntry.Tool(event.timestamp, event.toolCallId, event.toolName, null, callsById[event.toolCallId], toolResultsById[event.toolCallId])
                        .also { group.entries.add(it); toolIndexByCallId[event.toolCallId] = group.entries.lastIndex }
                entry.settled = event
            }
            is HarnessEvent.ApprovalRequested -> getOrCreateGroup().entries.add(
                TimelineEntry.Approval(event.timestamp, event.toolName, event.riskLevel),
            )
            else -> lifecycle += event
        }
    }
    return rounds to lifecycle
}

@Composable
private fun RoundSection(group: RoundGroup) {
    val assistantEntries = group.entries.filterIsInstance<TimelineEntry.Assistant>()
    val totalIn = assistantEntries.sumOf { it.inputTokens }
    val totalOut = assistantEntries.sumOf { it.outputTokens }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        ) {
            Text(
                stringResource(R.string.chat_tl_round, group.roundNumber + 1),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            group.modelId?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            if (totalIn > 0 || totalOut > 0) {
                Text(
                    "↑$totalIn ↓$totalOut",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            group.entries.forEachIndexed { index, entry ->
                RoundEntryRow(entry, isLast = index == group.entries.lastIndex)
            }
        }
    }
}

@Composable
private fun RoundEntryRow(entry: TimelineEntry, isLast: Boolean) {
    var expanded by remember { mutableStateOf(false) }

    val visual = when (entry) {
        is TimelineEntry.Assistant -> Triple(RuntimeIconName.Sparkles, Color(0xFF7C4DFF), stringResource(R.string.chat_tl_model_response))
        is TimelineEntry.Approval -> Triple(RuntimeIconName.Shield, Color(0xFFB25E00), entry.toolName)
        is TimelineEntry.Tool -> {
            val settled = entry.settled
            val icon = when {
                settled == null -> RuntimeIconName.Wrench
                settled.success -> RuntimeIconName.Check
                else -> RuntimeIconName.Alert
            }
            val color = when {
                settled == null -> MaterialTheme.colorScheme.tertiary
                settled.success -> Color(0xFF2E7D32)
                else -> MaterialTheme.colorScheme.error
            }
            Triple(icon, color, entry.name)
        }
    }
    val (icon, color, title) = visual
    val detail: String? = when (entry) {
        is TimelineEntry.Assistant -> stringResource(R.string.chat_token_usage, entry.inputTokens, entry.outputTokens)
        is TimelineEntry.Approval -> entry.riskLevel
        is TimelineEntry.Tool -> buildString {
            append(entry.settled?.durationMs?.let(::formatPanelDuration) ?: stringResource(R.string.chat_tl_running))
            entry.callMessage?.args?.get("command")?.let { cmd ->
                append(" · ").append(cmd.toString().replace('\n', ' ').take(80))
            }
        }
    }
    val expandable: Boolean = when (entry) {
        is TimelineEntry.Tool -> true
        is TimelineEntry.Assistant ->
            entry.message != null && (entry.message.text.isNotBlank() || !entry.message.reasoning.isNullOrBlank())
        is TimelineEntry.Approval -> false
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = expandable) { expanded = !expanded },
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 链式节点：圆点图标 + 连接线，未轮条目首尾相接。
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(28.dp).background(color.copy(alpha = 0.13f), CircleShape), contentAlignment = Alignment.Center) {
                    RuntimeIcon(icon, Modifier.size(15.dp), color)
                }
                if (!isLast) {
                    Box(Modifier.width(1.dp).height(22.dp).background(MaterialTheme.colorScheme.outlineVariant))
                }
            }
            Column(Modifier.weight(1f).padding(top = 3.dp, bottom = if (isLast) 4.dp else 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatEventTime(entry.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                }
                detail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (expandable) {
                RuntimeIcon(
                    if (expanded) RuntimeIconName.ChevronDown else RuntimeIconName.ChevronRight,
                    Modifier.size(14.dp).padding(top = 6.dp),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        androidx.compose.animation.AnimatedVisibility(visible = expanded && expandable) {
            Box(Modifier.padding(start = 38.dp, bottom = 8.dp)) {
                when (entry) {
                    is TimelineEntry.Tool -> ToolExpandContent(entry)
                    is TimelineEntry.Assistant -> entry.message?.let { AssistantExpandContent(it) }
                    is TimelineEntry.Approval -> Unit
                }
            }
        }
    }
}

@Composable
private fun ToolExpandContent(entry: TimelineEntry.Tool) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entry.callMessage?.let { call ->
            PayloadBox(stringResource(R.string.chat_tl_args), prettyArgs(call.args))
        }
        val result = entry.result
        PayloadBox(
            stringResource(R.string.chat_tl_result),
            result?.output?.ifBlank { "（无输出）" } ?: "结果尚未写入投影",
        )
    }
}

@Composable
private fun AssistantExpandContent(message: top.wkbin.taixu.harness.AssistantText) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (message.text.isNotBlank()) {
            PayloadBox(stringResource(R.string.chat_tl_response), message.text)
        }
        message.reasoning?.takeIf { it.isNotBlank() }?.let {
            PayloadBox(stringResource(R.string.chat_tl_reasoning), it)
        }
    }
}

/** 展开详情中的长文本容器：等宽字体 + 可滚动 + 可选中复制。 */
@Composable
private fun PayloadBox(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(8.dp)) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun StatPill(icon: RuntimeIconName, count: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            RuntimeIcon(icon, Modifier.size(14.dp), color)
            Spacer(Modifier.width(5.dp))
            Text(
                count,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = color,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RuntimeEventRow(event: HarnessEvent) {
    val visual = eventVisual(event)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(30.dp).background(visual.color.copy(alpha = 0.13f), CircleShape), contentAlignment = Alignment.Center) {
                RuntimeIcon(visual.icon, Modifier.size(16.dp), visual.color)
            }
            Box(Modifier.width(1.dp).height(34.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Column(Modifier.weight(1f).padding(top = 2.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    visual.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatEventTime(event.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
            Text(
                visual.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class EventVisual(val icon: RuntimeIconName, val color: Color, val title: String, val detail: String)

@Composable
private fun eventVisual(event: HarnessEvent): EventVisual = when (event) {
    is HarnessEvent.OperationStarted -> EventVisual(
        RuntimeIconName.Play, MaterialTheme.colorScheme.primary,
        stringResource(R.string.chat_event_operation_started),
        stringResource(R.string.chat_operation_lane, event.laneName),
    )
    is HarnessEvent.OperationFinished -> EventVisual(
        if (event.outcome == "completed") RuntimeIconName.Check else RuntimeIconName.Stop,
        if (event.outcome == "completed") Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
        if (event.outcome == "completed") stringResource(R.string.chat_event_operation_completed) else stringResource(R.string.chat_event_operation_ended),
        event.detail ?: event.outcome,
    )
    is HarnessEvent.ProviderRoundStarted -> EventVisual(
        RuntimeIconName.Brain, Color(0xFF7C4DFF),
        stringResource(R.string.chat_event_provider_round, event.round + 1),
        buildString {
            append(stringResource(R.string.chat_attempt_count, event.attempt + 1))
            event.modelId?.let { append(" · $it") }
        },
    )
    is HarnessEvent.ProviderRoundSettled -> EventVisual(
        RuntimeIconName.Sparkles, Color(0xFF7C4DFF),
        stringResource(R.string.chat_event_provider_settled),
        stringResource(R.string.chat_token_usage, event.inputTokens, event.outputTokens),
    )
    is HarnessEvent.ToolCallStarted -> EventVisual(
        RuntimeIconName.Wrench, MaterialTheme.colorScheme.tertiary,
        stringResource(R.string.chat_event_tool_started, event.toolName),
        stringResource(R.string.chat_event_tool_executing),
    )
    is HarnessEvent.ToolCallSettled -> EventVisual(
        if (event.success) RuntimeIconName.Check else RuntimeIconName.Alert,
        if (event.success) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
        if (event.success) stringResource(R.string.chat_event_tool_completed, event.toolName)
        else stringResource(R.string.chat_event_tool_failed, event.toolName),
        event.durationMs?.let { stringResource(R.string.chat_event_duration, formatPanelDuration(it)) }
            ?: stringResource(R.string.chat_event_settled),
    )
    is HarnessEvent.ApprovalRequested -> EventVisual(
        RuntimeIconName.Shield, Color(0xFFB25E00),
        stringResource(R.string.chat_event_approval),
        "${event.toolName} · ${event.riskLevel}",
    )
    is HarnessEvent.RecoveryApplied -> EventVisual(
        RuntimeIconName.Refresh, MaterialTheme.colorScheme.secondary,
        stringResource(R.string.chat_event_recovery),
        event.detail ?: event.outcome,
    )
    is HarnessEvent.PermissionRequired -> EventVisual(
        RuntimeIconName.Shield, Color(0xFFB25E00),
        stringResource(R.string.chat_event_permission_required, event.permission),
        event.reason,
    )
}

@Composable
internal fun ComposerModeSelector(mode: ComposerSendMode, onModeChange: (ComposerSendMode) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposerModeChip(ComposerSendMode.STEER, mode, R.string.chat_label_steer, RuntimeIconName.Tune, Color(0xFF7C4DFF), onModeChange, Modifier.weight(1f))
            ComposerModeChip(ComposerSendMode.NEXT_RUN, mode, R.string.chat_label_queued, RuntimeIconName.List, MaterialTheme.colorScheme.secondary, onModeChange, Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerModeChip(
    target: ComposerSendMode,
    selected: ComposerSendMode,
    titleRes: Int,
    icon: RuntimeIconName,
    tint: Color,
    onModeChange: (ComposerSendMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = target == selected
    Surface(
        onClick = { onModeChange(target) },
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (active) tint.copy(alpha = 0.16f) else Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            RuntimeIcon(icon, Modifier.size(13.dp), if (active) tint else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = if (active) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueuedPromptStack(
    prompts: List<QueuedPrompt>,
    onEdit: (QueuedPrompt) -> Unit,
    onRemove: (QueuedPrompt) -> Unit,
) {
    if (prompts.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        prompts.forEach { prompt ->
            val (label, tint) = when (prompt.queue) {
                PromptQueue.STEER -> stringResource(R.string.chat_label_steer) to Color(0xFF7C4DFF)
                PromptQueue.FOLLOW_UP -> stringResource(R.string.chat_label_follow_up) to MaterialTheme.colorScheme.tertiary
                PromptQueue.NEXT_RUN -> stringResource(R.string.chat_label_queued) to MaterialTheme.colorScheme.secondary
            }
            Surface(color = tint.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MiniBadge(label, tint)
                    Text(
                        prompt.message.text.ifBlank { stringResource(R.string.chat_attachment_message) },
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(onClick = { onEdit(prompt) }, color = Color.Transparent, shape = CircleShape) {
                        RuntimeIcon(RuntimeIconName.Edit, Modifier.padding(3.dp).size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(onClick = { onRemove(prompt) }, color = Color.Transparent, shape = CircleShape) {
                        RuntimeIcon(RuntimeIconName.Close, Modifier.padding(3.dp).size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CreateBranchDialog(messageId: String, onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val defaultName = stringResource(R.string.chat_branch_default_name)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { RuntimeIcon(RuntimeIconName.Hub, Modifier.size(24.dp), MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.chat_create_branch)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.chat_create_branch_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(48) },
                    label = { Text(stringResource(R.string.chat_branch_name)) },
                    placeholder = { Text(stringResource(R.string.chat_branch_name_placeholder)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(messageId, name.ifBlank { defaultName }) }) { Text(stringResource(R.string.chat_create_and_switch)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) } },
    )
}

private fun eventOperationId(event: HarnessEvent): String? = when (event) {
    is HarnessEvent.OperationStarted -> event.operationId
    is HarnessEvent.OperationFinished -> event.operationId
    is HarnessEvent.ProviderRoundStarted -> event.operationId
    is HarnessEvent.ProviderRoundSettled -> event.operationId
    is HarnessEvent.ToolCallStarted -> event.operationId
    is HarnessEvent.ToolCallSettled -> event.operationId
    is HarnessEvent.ApprovalRequested -> event.operationId
    is HarnessEvent.RecoveryApplied -> event.operationId
    is HarnessEvent.PermissionRequired -> null
}

/** 用时间戳 + 操作 ID + 事件类型 + 工具调用 ID 组合保证 key 唯一，避免同类事件碰撞。 */
private fun eventKey(event: HarnessEvent): String = when (event) {
    is HarnessEvent.ToolCallStarted -> event.operationId + event::class.simpleName + event.toolCallId
    is HarnessEvent.ToolCallSettled -> event.operationId + event::class.simpleName + event.toolCallId
    is HarnessEvent.ProviderRoundStarted -> event.operationId + event::class.simpleName + event.round
    is HarnessEvent.ProviderRoundSettled -> event.operationId + event::class.simpleName + event.round
    else -> eventOperationId(event).orEmpty() + event::class.simpleName
}

private fun formatEventTime(timestamp: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
private fun formatPanelDuration(ms: Long): String = if (ms < 1_000) "${ms}ms" else String.format(Locale.getDefault(), "%.1fs", ms / 1_000.0)

private val PAYLOAD_JSON = Json { prettyPrint = true; ignoreUnknownKeys = true }

private fun prettyArgs(args: JsonObject): String =
    runCatching { PAYLOAD_JSON.encodeToString(JsonObject.serializer(), args) }.getOrDefault(args.toString())
