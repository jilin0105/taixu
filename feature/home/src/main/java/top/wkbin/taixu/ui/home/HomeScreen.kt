package top.wkbin.taixu.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.core.model.RuntimeState
import top.wkbin.taixu.ui.components.MainDestination
import top.wkbin.taixu.ui.components.NoticeBanner
import top.wkbin.taixu.ui.components.RuntimeBottomBar
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTopBar

/**
 * 太墟 · 运行仪表盘 (TaiXu Linux Runtime Dashboard)
 * 化繁为简，聚焦展示沙箱核心运行状态、实时内存/存储指标、活跃进程与系统环境
 */
@Composable
fun HomeScreen(
    onNavigate: (MainDestination) -> Unit,
    onOpenTerminal: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.runtimeState.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = "太墟 · 运行仪表盘",
                statusText = "${metrics.linuxDistro} · ${metrics.cpuArch}",
                actions = {
                    IconButton(onClick = viewModel::refreshMetrics) {
                        RuntimeIcon(
                            name = RuntimeIconName.Refresh,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = { RuntimeBottomBar(MainDestination.Home, onNavigate) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. 运行时主状态卡片 (Status Banner)
            RuntimeEngineStatusCard(
                state = state,
                metrics = metrics,
                onInitialize = viewModel::initializeRuntime,
                onCancel = viewModel::cancelInitialization,
                onOpenTerminal = onOpenTerminal,
            )

            // 2. 核心指标看板 (Live Resource Metrics Grid)
            Text(
                text = "系统资源监控",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 内存指标
                ResourceMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "运行内存 (RAM)",
                    primaryValue = "${metrics.memoryUsedMb} MB",
                    secondaryValue = "总量 ${metrics.memoryTotalMb} MB",
                    progress = (metrics.memoryUsagePercent / 100f).coerceIn(0f, 1f),
                    progressText = "已占用 ${metrics.memoryUsagePercent}%",
                    extraInfo = "App 堆: ${metrics.appHeapUsedMb} MB",
                    accentColor = MaterialTheme.colorScheme.primary,
                    icon = RuntimeIconName.Terminal,
                )

                // 存储指标
                ResourceMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "沙箱存储 (Disk)",
                    primaryValue = "${metrics.storageUsedGb} GB",
                    secondaryValue = "总空间 ${metrics.storageTotalGb} GB",
                    progress = (metrics.storageUsagePercent / 100f).coerceIn(0f, 1f),
                    progressText = "已使用 ${metrics.storageUsagePercent}%",
                    extraInfo = "Rootfs 状态正常",
                    accentColor = MaterialTheme.colorScheme.secondary,
                    icon = RuntimeIconName.Folder,
                )
            }

            // 3. 活跃任务与服务监控卡片
            ActiveTasksStatusCard(
                metrics = metrics,
                onOpenTerminal = onOpenTerminal,
            )

            // 4. 运行环境与规格详情
            SystemSpecsCard(metrics = metrics)

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * 运行时引擎主状态卡片
 */
@Composable
private fun RuntimeEngineStatusCard(
    state: RuntimeState,
    metrics: SystemResourceMetrics,
    onInitialize: () -> Unit,
    onCancel: () -> Unit,
    onOpenTerminal: () -> Unit,
) {
    val ready = state is RuntimeState.Ready
    val error = state is RuntimeState.Error
    val initializing = state as? RuntimeState.Initializing

    val statusColor = when {
        ready -> Color(0xFF2E7D32)
        error -> MaterialTheme.colorScheme.error
        initializing != null -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PulsingStatusDot(color = statusColor, isPulsing = ready || initializing != null)
                    Column {
                        Text(
                            text = when {
                                ready -> "Linux 沙箱引擎 · 已就绪"
                                error -> "沙箱引擎异常"
                                initializing != null -> "正在初始化沙箱环境..."
                                else -> "沙箱环境未初始化"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${metrics.linuxDistro} · ${metrics.engineVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (initializing != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { initializing.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        strokeCap = StrokeCap.Round,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = initializing.step,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onCancel) {
                            Text("取消", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (error) {
                NoticeBanner(
                    text = (state as RuntimeState.Error).throwable.message ?: "沙箱引擎发生错误",
                    isError = true,
                )
            }

            // 快捷控制操作区
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (ready) {
                    Button(
                        onClick = onOpenTerminal,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 10.dp),
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Terminal,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("进入控制台", fontWeight = FontWeight.SemiBold)
                    }
                } else if (initializing == null) {
                    Button(
                        onClick = onInitialize,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 10.dp),
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Play,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (error) "重试初始化" else "立即初始化沙箱", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * 资源监控指标卡片
 */
@Composable
private fun ResourceMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    primaryValue: String,
    secondaryValue: String,
    progress: Float,
    progressText: String,
    extraInfo: String,
    accentColor: Color,
    icon: RuntimeIconName,
) {
    val effectiveAccent = when {
        progress >= 0.9f -> MaterialTheme.colorScheme.error
        progress >= 0.8f -> MaterialTheme.colorScheme.tertiary
        else -> accentColor
    }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RuntimeIcon(
                    name = icon,
                    modifier = Modifier.size(16.dp),
                    tint = effectiveAccent,
                )
            }

            Text(
                text = primaryValue,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = effectiveAccent,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
            )

            Text(
                text = "$progressText · $secondaryValue",
                style = MaterialTheme.typography.labelSmall,
                color = if (progress >= 0.8f) effectiveAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )

            Text(
                text = if (progress >= 0.9f) "$extraInfo · 建议清理" else extraInfo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 活跃任务与后台服务卡片
 */
@Composable
private fun ActiveTasksStatusCard(
    metrics: SystemResourceMetrics,
    onOpenTerminal: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    RuntimeIcon(
                        name = RuntimeIconName.List,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${metrics.activeProcessCount} 个活跃后台进程",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "持续运行: ${metrics.uptimeFormatted} · 包含终端与伴侣服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            FilledTonalButton(
                onClick = onOpenTerminal,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("查看", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * 宿主与运行环境规格卡片
 */
@Composable
private fun SystemSpecsCard(metrics: SystemResourceMetrics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "环境与规格详情",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            SpecRow(label = "CPU 架构体系", value = metrics.cpuArch)
            SpecRow(label = "宿主系统版本", value = metrics.hostAndroidVersion)
            SpecRow(label = "沙箱运行引擎", value = metrics.engineVersion)
            SpecRow(label = "Guest OS 发行版", value = metrics.linuxDistro)
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 状态呼吸灯圆点
 */
@Composable
private fun PulsingStatusDot(color: Color, isPulsing: Boolean) {
    val transition = rememberInfiniteTransition(label = "status_dot_pulse")
    val alpha by if (isPulsing) {
        transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse_alpha",
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }

    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}
