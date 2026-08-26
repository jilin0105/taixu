package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    branchCount: Int,
    runtimeEvents: List<HarnessEvent>,
    running: Boolean,
    onOpenBranches: () -> Unit,
    onOpenRuntime: () -> Unit,
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
                title = currentBranch?.name ?: stringResource(R.string.chat_main_line),
                subtitle = stringResource(R.string.chat_branch_count, branchCount),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                onClick = onOpenBranches,
            )
            WorkspacePill(
                icon = RuntimeIconName.Logs,
                title = if (running) stringResource(R.string.chat_runtime_running) else stringResource(R.string.chat_runtime_details),
                subtitle = activeRound?.let { stringResource(R.string.chat_round_number, it + 1) }
                    ?: stringResource(R.string.chat_event_count, runtimeEvents.size),
                tint = if (running) Color(0xFF7C4DFF) else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
                onClick = onOpenRuntime,
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
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = tint.copy(alpha = 0.09f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RuntimeIcon(icon, Modifier.size(17.dp), tint)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            RuntimeIcon(RuntimeIconName.ChevronRight, Modifier.size(15.dp), MaterialTheme.colorScheme.onSurfaceVariant)
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
internal fun RuntimeTimelineSheet(events: List<HarnessEvent>, onDismiss: () -> Unit) {
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
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                    items(events.asReversed(), key = { "${it.timestamp}:${eventKey(it)}" }) { event ->
                        RuntimeEventRow(event)
                    }
                }
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
            ComposerModeChip(ComposerSendMode.FOLLOW_UP, mode, R.string.chat_label_follow_up, RuntimeIconName.Link, MaterialTheme.colorScheme.tertiary, onModeChange, Modifier.weight(1f))
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
internal fun QueuedPromptStack(prompts: List<QueuedPrompt>, onRemove: (QueuedPrompt) -> Unit) {
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
