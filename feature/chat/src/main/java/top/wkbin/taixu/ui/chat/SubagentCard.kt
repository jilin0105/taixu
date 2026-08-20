package top.wkbin.taixu.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Subagent 子智能体任务派发卡片：
 * 可视化展示主智能体派发给子智能体的任务列表、各角色执行状态及输出结论。
 */
@Composable
fun SubagentCard(
    call: ToolCall,
    result: ToolResult?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(result != null) }
    val rotationState by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "arrow_anim")

    val tasks = remember(call.args) {
        val list = mutableListOf<Triple<String, String, String>>() // taskName, role, prompt
        val array = call.args["subagents"]?.jsonArray
        if (array != null) {
            for (elem in array) {
                val obj = elem.jsonObject
                val taskName = obj["taskName"]?.jsonPrimitive?.content ?: "子任务"
                val role = obj["role"]?.jsonPrimitive?.content ?: "assistant"
                val prompt = obj["prompt"]?.jsonPrimitive?.content ?: ""
                list.add(Triple(taskName, role, prompt))
            }
        } else {
            val taskName = call.args["taskName"]?.jsonPrimitive?.content ?: "子任务"
            val role = call.args["role"]?.jsonPrimitive?.content ?: "assistant"
            val prompt = call.args["prompt"]?.jsonPrimitive?.content ?: ""
            if (prompt.isNotBlank()) list.add(Triple(taskName, role, prompt))
        }
        list
    }

    val isFinished = result != null
    val isSuccess = result?.success ?: true

    val cardBorderColor = if (isFinished) {
        if (isSuccess) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
    } else {
        Color(0xFFF59E0B).copy(alpha = 0.5f)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Terminal,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "子智能体协同派发",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Surface(
                                color = if (isFinished) {
                                    if (isSuccess) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
                                } else Color(0xFFF59E0B).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    text = if (isFinished) (if (isSuccess) "已完成" else "执行中断") else "并行执行中",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                    color = if (isFinished) (if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444)) else Color(0xFFF59E0B),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }

                        Text(
                            "${tasks.size} 个子任务并发调度",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                RuntimeIcon(
                    name = RuntimeIconName.ChevronDown,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotationState),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Task Badges List
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tasks.forEach { (taskName, role, _) ->
                    val (roleColor, roleIcon) = when (role.lowercase()) {
                        "researcher" -> Color(0xFF3B82F6) to RuntimeIconName.File
                        "coder" -> Color(0xFF10B981) to RuntimeIconName.Terminal
                        "tester" -> Color(0xFFF59E0B) to RuntimeIconName.Code
                        else -> MaterialTheme.colorScheme.primary to RuntimeIconName.Logs
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = roleColor.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, roleColor.copy(alpha = 0.3f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RuntimeIcon(name = roleIcon, modifier = Modifier.size(12.dp), tint = roleColor)
                            Text(
                                text = "$role: $taskName",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                color = roleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // Expandable Content
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (result != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "执行汇总与产出",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = result.output,
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        Text(
                            "子智能体正在沙箱中执行工具并分析结果，完成后将在此汇总展示…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
