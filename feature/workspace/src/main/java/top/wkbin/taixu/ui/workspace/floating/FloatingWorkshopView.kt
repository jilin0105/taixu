package top.wkbin.taixu.ui.workspace.floating

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.feature.workspace.R
import top.wkbin.taixu.runtime.build.BuildRunProgress
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeFilledTonalButton
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator

/**
 * 工坊桌面悬浮小窗视图（支持胶囊态与面板态双模交互）。
 */
@Composable
fun FloatingWorkshopView(
    activeProjectName: String?,
    buildProgress: BuildRunProgress?,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDragBy: (dx: Float, dy: Float) -> Unit,
    onRestoreApp: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenAgent: () -> Unit,
    onClose: () -> Unit,
) {
    if (isExpanded) {
        FloatingWorkshopPanel(
            activeProjectName = activeProjectName,
            buildProgress = buildProgress,
            onCollapse = onToggleExpanded,
            onDragBy = onDragBy,
            onRestoreApp = onRestoreApp,
            onOpenTerminal = onOpenTerminal,
            onOpenAgent = onOpenAgent,
            onClose = onClose,
        )
    } else {
        FloatingWorkshopCapsule(
            activeProjectName = activeProjectName,
            isBuilding = buildProgress?.isRunning == true,
            onExpand = onToggleExpanded,
            onDragBy = onDragBy,
        )
    }
}

/**
 * 胶囊态 (Mini Capsule Mode)
 */
@Composable
private fun FloatingWorkshopCapsule(
    activeProjectName: String?,
    isBuilding: Boolean,
    onExpand: () -> Unit,
    onDragBy: (dx: Float, dy: Float) -> Unit,
) {
    Surface(
        modifier = Modifier
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragBy(dragAmount.x, dragAmount.y)
                }
            }
            .clickable { onExpand() },
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isBuilding) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isBuilding) {
                    RuntimeCircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.8.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    RuntimeIcon(
                        RuntimeIconName.Code,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                text = if (isBuilding) stringResource(R.string.workspace_floating_building_capsule)
                else (activeProjectName ?: stringResource(R.string.workspace_floating_window)),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 110.dp),
            )
        }
    }
}

/**
 * 展开面板态 (Expanded Floating Panel)
 */
@Composable
private fun FloatingWorkshopPanel(
    activeProjectName: String?,
    buildProgress: BuildRunProgress?,
    onCollapse: () -> Unit,
    onDragBy: (dx: Float, dy: Float) -> Unit,
    onRestoreApp: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenAgent: () -> Unit,
    onClose: () -> Unit,
) {
    val isBuilding = buildProgress?.isRunning == true

    Surface(
        modifier = Modifier
            .width(290.dp)
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 顶栏（支持按住拖拽移动）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDragBy(dragAmount.x, dragAmount.y)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    RuntimeIcon(
                        RuntimeIconName.OpenInNew,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.workspace_floating_title),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    RuntimeIconButton(
                        onClick = onCollapse,
                        modifier = Modifier.size(24.dp),
                        contentDescription = "收起为胶囊",
                    ) {
                        RuntimeIcon(
                            RuntimeIconName.ChevronDown,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RuntimeIconButton(
                        onClick = onClose,
                        modifier = Modifier.size(24.dp),
                        contentDescription = stringResource(R.string.workspace_floating_close),
                    ) {
                        RuntimeIcon(
                            RuntimeIconName.Close,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 当前活动工程与状态卡片
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RuntimeIcon(
                            RuntimeIconName.Folder,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = activeProjectName ?: stringResource(R.string.workspace_floating_no_active_project),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (buildProgress != null) {
                        if (isBuilding) {
                            RuntimeLinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(1.5.dp)),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = buildProgress.step,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                color = if (isBuilding) MaterialTheme.colorScheme.primary
                                else if (buildProgress.isSuccess == true) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.error,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 快捷动作栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RuntimeButton(
                    onClick = onRestoreApp,
                    modifier = Modifier.weight(1.3f).height(32.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.OpenInNew, Modifier.size(12.dp))
                        Text(stringResource(R.string.workspace_floating_restore_app), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                RuntimeFilledTonalButton(
                    onClick = onOpenTerminal,
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(12.dp))
                        Text(stringResource(R.string.workspace_floating_open_terminal), fontSize = 11.sp)
                    }
                }

                RuntimeFilledTonalButton(
                    onClick = onOpenAgent,
                    modifier = Modifier.weight(1.1f).height(32.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Brain, Modifier.size(12.dp))
                        Text(stringResource(R.string.workspace_floating_open_agent), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
