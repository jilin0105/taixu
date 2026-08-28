package top.wkbin.taixu.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.HarnessTool
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.harness.UserMessage
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.StatusBadge

/**
 * 🌟 底部悬浮工具活动胶囊 (Floating Tool Activity Strip / Live Pill)
 * 实时捕获后台 Shell 命令/工具调用/构建进度，提供运行时长、命令摘要、一键中止与展开日志抽屉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolActivityPill(
    running: Boolean,
    status: String?,
    messages: List<HarnessMessage>,
    toolResults: Map<String, ToolResult>,
    workspace: String,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDetailSheet by remember { mutableStateOf(false) }

    val activeToolCall = remember(messages, running) {
        if (!running) null
        else messages.filterIsInstance<ToolCall>().lastOrNull { call ->
            !toolResults.containsKey(call.id)
        } ?: messages.filterIsInstance<ToolCall>().lastOrNull()
    }

    // 运行耗时秒数计算
    val roundStart = remember(messages.size, running) {
        if (!running) 0L
        else messages.lastOrNull { it is UserMessage }?.createdAt ?: System.currentTimeMillis()
    }
    var elapsedMillis by remember { mutableLongStateOf(0L) }

    LaunchedEffect(running, roundStart) {
        if (running) {
            while (true) {
                elapsedMillis = System.currentTimeMillis() - roundStart
                delay(200)
            }
        } else {
            elapsedMillis = 0L
        }
    }

    // 脉冲与呼吸光效
    val transition = rememberInfiniteTransition(label = "toolPillPulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pillPulseAlpha",
    )

    AnimatedVisibility(
        visible = running,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val toolKind = activeToolCall?.tool
        val toolIcon = when (toolKind) {
            HarnessTool.BASE, HarnessTool.PROCESS -> RuntimeIconName.Terminal
            HarnessTool.READ, HarnessTool.WRITE, HarnessTool.EDIT -> RuntimeIconName.Document
            HarnessTool.SUBAGENT -> RuntimeIconName.Brain
            else -> RuntimeIconName.Speed
        }

        val pillAccent = when (toolKind) {
            HarnessTool.BASE, HarnessTool.PROCESS -> Color(0xFF00E5FF)
            HarnessTool.WRITE, HarnessTool.EDIT -> Color(0xFF10B981)
            HarnessTool.SUBAGENT -> Color(0xFF8B5CF6)
            else -> MaterialTheme.colorScheme.primary
        }

        val actionSummary = activeToolCall?.let { formatToolSummary(it) } ?: status ?: stringResource(R.string.chat_thinking)

        Surface(
            onClick = { showDetailSheet = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
            border = androidx.compose.foundation.BorderStroke(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    // 运行状态微光指示点
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(pillAccent.copy(alpha = pulseAlpha)),
                    )

                    // 图标
                    RuntimeIcon(
                        name = toolIcon,
                        modifier = Modifier.size(13.dp),
                        tint = pillAccent,
                    )

                    // 工具名称与摘要
                    Text(
                        text = actionSummary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    // 耗时
                    if (elapsedMillis > 0) {
                        Text(
                            text = formatDuration(elapsedMillis),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }

                Spacer(Modifier.width(4.dp))

                // 一键终止微型按钮
                Surface(
                    onClick = onStop,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RuntimeIcon(
                            name = RuntimeIconName.Close,
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }

    if (showDetailSheet && activeToolCall != null) {
        ModalBottomSheet(
            onDismissRequest = { showDetailSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "活动工具执行状态",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    StatusBadge(
                        text = if (running) "执行中 · ${formatDuration(elapsedMillis)}" else "已就绪",
                        color = if (running) Color(0xFF00E5FF) else MaterialTheme.colorScheme.primary,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "工具类型: ${activeToolCall.tool.name}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "参数内容:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = activeToolCall.args.toString(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    return String.format(java.util.Locale.getDefault(), "%.1fs", ms / 1000f)
}

private fun formatToolSummary(call: ToolCall): String {
    return when (call.tool) {
        HarnessTool.BASE -> {
            val cmd = call.args["command"]?.jsonPrimitive?.content ?: call.args.toString()
            "bash: ${cmd.take(40)}"
        }
        HarnessTool.PROCESS -> {
            val action = call.args["action"]?.jsonPrimitive?.content ?: "process"
            val id = call.args["id"]?.jsonPrimitive?.content ?: ""
            "process[$action]: $id"
        }
        HarnessTool.READ -> "read: ${call.args["path"]?.jsonPrimitive?.content ?: "file"}"
        HarnessTool.WRITE -> "write: ${call.args["path"]?.jsonPrimitive?.content ?: "file"}"
        HarnessTool.EDIT -> "edit: ${call.args["path"]?.jsonPrimitive?.content ?: "file"}"
        HarnessTool.SUBAGENT -> "subagent: ${call.args["prompt"]?.jsonPrimitive?.content?.take(30) ?: "task"}"
        else -> "${call.tool.name.lowercase()}: active"
    }
}
