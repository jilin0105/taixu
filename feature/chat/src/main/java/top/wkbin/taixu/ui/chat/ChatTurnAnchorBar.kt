package top.wkbin.taixu.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.UserMessage
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName

data class TurnAnchor(
    val turnIndex: Int,
    val label: String,
    val messageIndex: Int,
    val isError: Boolean = false,
)

/**
 * 🌟 消息轮次快速定位锚点栏 (Chat Message Anchor Bar)
 * 在长会话中提取每轮关键指令，支持点击一键平滑跳转至对应对话节点。
 */
@Composable
fun ChatTurnAnchorBar(
    messages: List<HarnessMessage>,
    onScrollToTurn: (messageIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val anchors = remember(messages) {
        val userIndices = messages.mapIndexedNotNull { index, msg ->
            if (msg is UserMessage) index else null
        }
        userIndices.mapIndexed { turnIdx, msgIdx ->
            val userMsg = messages[msgIdx] as UserMessage
            val promptPreview = userMsg.text.lines().firstOrNull { it.isNotBlank() }?.take(16) ?: "Turn ${turnIdx + 1}"
            TurnAnchor(
                turnIndex = turnIdx + 1,
                label = "${turnIdx + 1}. $promptPreview",
                messageIndex = msgIdx,
            )
        }
    }

    AnimatedVisibility(
        visible = anchors.size >= 3,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RuntimeIcon(
                name = RuntimeIconName.List,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            anchors.forEach { anchor ->
                Surface(
                    onClick = { onScrollToTurn(anchor.messageIndex) },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    Text(
                        text = anchor.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
