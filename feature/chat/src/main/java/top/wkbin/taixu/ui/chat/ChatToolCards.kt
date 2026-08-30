package top.wkbin.taixu.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import androidx.compose.material3.MaterialTheme
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton as OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import top.wkbin.taixu.feature.chat.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.wkbin.taixu.harness.HarnessTool
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.core.database.AgentApprovalRequestEntity

/** 工具调用卡与审批请求卡。 */
private val DotRunning = Color(0xFFB25E00)
private val DotSuccess = Color(0xFF2E7D32)
private val DotFailed = Color(0xFFBA1A1A)

@Composable
internal fun ToolCard(
    call: ToolCall,
    result: ToolResult?,
    workspace: String,
    onOpenFile: ((String, String) -> Unit)?,
    running: Boolean,
    liveStatus: String?,
    showReasoning: Boolean = false,
    defaultExpanded: Boolean = false,
    onRetry: () -> Unit = {},
) {
    var expanded by remember(call.id) { mutableStateOf(false) }
    val dotColor = when {
        result == null && running -> DotRunning
        result?.success == true -> DotSuccess
        result?.success == false -> DotFailed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // 状态点补充文本语义，避免 TalkBack 只感知到一块颜色
    val dotDesc = when {
        result == null && running -> stringResource(R.string.chat_tool_state_running)
        result?.success == true -> stringResource(R.string.chat_tool_state_success)
        result?.success == false -> stringResource(R.string.chat_tool_state_failed)
        else -> null
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (showReasoning) {
                call.reasoning?.takeIf { it.isNotBlank() }
                    ?.let {
                        ThinkingBlock(
                            id = call.id,
                            reasoning = it,
                            defaultExpanded = defaultExpanded,
                        )
                    }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 1.dp),
            ) {
                Box(
                    modifier = Modifier.size(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val dotModifier = if (dotDesc != null) {
                        Modifier.semantics { contentDescription = dotDesc }
                    } else Modifier
                    Box(dotModifier.size(6.dp).clip(CircleShape).background(dotColor))
                }
                Text(
                    if (call.tool == HarnessTool.MCP) stringResource(R.string.chat_call_tool) else toolName(call.tool, call.rawToolName),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (call.tool == HarnessTool.MCP) {
                        "${toolName(call.tool, call.rawToolName)} · ${toolArgsSummary(call)}"
                    } else {
                        toolArgsSummary(call)
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val durationText = if (result != null) {
                    val duration = result.durationMs ?: (result.createdAt - call.createdAt).coerceAtLeast(0L)
                    formatChatDuration(duration)
                } else if (running) {
                    var elapsed by remember(call.id) { mutableStateOf(0L) }
                    LaunchedEffect(call.id) {
                        while (true) {
                            elapsed = (System.currentTimeMillis() - call.createdAt).coerceAtLeast(0L)
                            // 500ms 刷新足够展示秒级耗时；过密轮询会让长会话持续重组抖动
                            delay(500)
                        }
                    }
                    formatChatDuration(elapsed)
                } else {
                    null
                }
                durationText?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace,
                    )
                }
                RuntimeIcon(
                    if (expanded) RuntimeIconName.ChevronDown else RuntimeIconName.ChevronRight,
                    Modifier.size(11.dp),
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }

            val downloadingPrefix = stringResource(R.string.chat_downloading_prefix)
            val verifyingDownload = stringResource(R.string.chat_verifying_download)
            val downloadStatus = liveStatus?.takeIf {
                call.tool == HarnessTool.DOWNLOAD && result == null && running &&
                    (it.startsWith(downloadingPrefix) || it.startsWith(verifyingDownload))
            }
            if (downloadStatus != null) {
                Text(
                    downloadStatus,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
                val percent = Regex("\\((\\d+)%\\)").find(downloadStatus)
                    ?.groupValues?.getOrNull(1)?.toFloatOrNull()?.div(100f)
                if (percent == null) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { percent },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }

            // 展开 Diff 与输出详情视图
            if (expanded) {
                ToolDiffView(
                    call = call,
                    result = result,
                    workspace = workspace,
                    onOpenFile = onOpenFile,
                )
                if (result != null && !running) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onRetry) {
                            RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(13.dp), MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.chat_retry_from_tool), fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ApprovalRequestCard(
    request: top.wkbin.taixu.core.database.AgentApprovalRequestEntity,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val riskColor = when (request.riskLevel) {
        "critical" -> MaterialTheme.colorScheme.error
        "high" -> Color(0xFFB45309)
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, riskColor.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeIcon(RuntimeIconName.Shield, Modifier.size(18.dp), riskColor)
                Text(stringResource(R.string.chat_approval_required), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Surface(color = riskColor.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        request.riskLevel.uppercase(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = riskColor,
                    )
                }
            }
            Text(request.summary, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
            Text(request.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // 过期倒计时：审批 TTL 默认 10 分钟，超时未决会被 Harness 判定失效。
            var remainingMinutes by remember(request.expiresAt) { mutableStateOf(Int.MAX_VALUE) }
            LaunchedEffect(request.expiresAt) {
                while (true) {
                    val remaining = request.expiresAt - System.currentTimeMillis()
                    remainingMinutes = if (remaining <= 0) 0 else ((remaining + 59_999) / 60_000).toInt()
                    if (remainingMinutes == 0) break
                    delay(15_000)
                }
            }
            if (remainingMinutes > 0 && remainingMinutes != Int.MAX_VALUE) {
                val urgent = remainingMinutes <= 2
                Text(
                    if (remainingMinutes == 1) {
                        stringResource(R.string.chat_approval_expires_soon)
                    } else {
                        stringResource(R.string.chat_approval_expires_minutes, remainingMinutes)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 审批处理期间禁用按钮，防止重复提交
            var resolving by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        resolving = true
                        onReject()
                    },
                    enabled = !resolving,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.chat_reject)) }
                Button(
                    onClick = {
                        resolving = true
                        onApprove()
                    },
                    enabled = !resolving,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.chat_approve_continue)) }
            }
        }
    }
}

internal fun toolName(tool: HarnessTool, rawToolName: String? = null): String {
    if (!rawToolName.isNullOrBlank()) return rawToolName
    return when (tool) {
        HarnessTool.READ -> "read"
        HarnessTool.WRITE -> "write"
        HarnessTool.EDIT -> "edit"
        HarnessTool.BASE -> "base"
        HarnessTool.PROCESS -> "process"
        HarnessTool.HOST -> "host"
        HarnessTool.DOWNLOAD -> "download"
        HarnessTool.MEMORY -> "memory"
        HarnessTool.PLAN -> "plan"
        HarnessTool.SCRATCHPAD -> "scratchpad"
        HarnessTool.HISTORY_SEARCH -> "history.search"
        HarnessTool.HISTORY_READ -> "history.read"
        HarnessTool.BUILD_SCRIPT -> "build_script"
        HarnessTool.SUBAGENT -> "invoke_subagent"
        HarnessTool.MCP -> "mcp"
        HarnessTool.LOAD_RULE -> "load_rule"
    }
}

internal fun toolArgsSummary(call: ToolCall): String {
    val entries = call.args.toMap().entries.take(3)
        .joinToString(", ") { (key, value) -> "$key=${value.toString().take(40)}" }
    return entries
}

