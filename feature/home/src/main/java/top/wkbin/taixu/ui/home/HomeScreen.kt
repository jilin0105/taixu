package top.wkbin.taixu.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeFilledTonalButton as FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import top.wkbin.taixu.ui.components.RuntimeIconButton as IconButton
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton as OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.core.model.DoctorItem
import top.wkbin.taixu.core.model.DoctorReport
import top.wkbin.taixu.core.model.DoctorStatus
import top.wkbin.taixu.core.model.RepairProgress
import top.wkbin.taixu.core.model.RuntimeState
import top.wkbin.taixu.ui.components.MainDestination
import top.wkbin.taixu.ui.components.NoticeBanner
import top.wkbin.taixu.ui.components.RuntimeBottomBar
import top.wkbin.taixu.ui.components.liquidGlassContent
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.distroIconFor
import top.wkbin.taixu.ui.components.StatusBadge
import top.wkbin.taixu.ui.theme.LocalLiquidGlassBackdrop

/**
 * 太墟 · 运行仪表盘 (TaiXu Linux Runtime Dashboard)
 * 集成沙箱引擎状态、运行环境体检自愈中心、实时内存/存储监控与规格信息
 */
@Composable
fun HomeScreen(
    onNavigate: (MainDestination) -> Unit,
    onOpenTerminal: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.runtimeState.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val doctorReport by viewModel.doctorReport.collectAsStateWithLifecycle()
    val isCheckingDoctor by viewModel.isCheckingDoctor.collectAsStateWithLifecycle()
    val repairProgress by viewModel.repairProgress.collectAsStateWithLifecycle()
    val isRepairing by viewModel.isRepairing.collectAsStateWithLifecycle()
    val installedDistros by viewModel.installedDistros.collectAsStateWithLifecycle()
    val activeDistroId by viewModel.activeDistroId.collectAsStateWithLifecycle()
    val switchingDistro by viewModel.switchingDistro.collectAsStateWithLifecycle()

    val glassBackdrop = LocalLiquidGlassBackdrop.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = "太墟 · 运行仪表盘",
                statusText = "${metrics.linuxDistro} · ${metrics.cpuArch}",
                actions = {
                    IconButton(onClick = {
                        viewModel.refreshMetrics()
                        viewModel.runDoctorCheck()
                    }) {
                        RuntimeIcon(
                            name = RuntimeIconName.Refresh,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (glassBackdrop == null) {
                RuntimeBottomBar(MainDestination.Home, onNavigate)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassContent()
                .padding(top = innerPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. 运行时引擎主状态卡片 (Status Banner)
            RuntimeEngineStatusCard(
                state = state,
                metrics = metrics,
                installedDistros = installedDistros,
                activeDistroId = activeDistroId,
                switchingDistro = switchingDistro,
                onSwitchDistro = viewModel::switchDistro,
                onInitialize = viewModel::initializeRuntime,
                onCancel = viewModel::cancelInitialization,
                onOpenTerminal = onOpenTerminal,
            )

            // 2. 运行与开发环境体检自愈中心 (TaiXu Doctor & Auto-Fix)
            EnvironmentDoctorCard(
                report = doctorReport,
                isChecking = isCheckingDoctor,
                isRepairing = isRepairing,
                repairProgress = repairProgress,
                runtimeReady = state is RuntimeState.Ready,
                onRunCheck = viewModel::runDoctorCheck,
                onStartAutoRepair = viewModel::startAutoRepair,
                onCancelRepair = viewModel::cancelAutoRepair,
            )

            // 3. 核心指标看板 (Live Resource Metrics Grid)
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
                    icon = RuntimeIconName.Cpu,
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
                    icon = RuntimeIconName.Storage,
                )
            }

            // 4. 活跃任务与服务监控卡片
            ActiveTasksStatusCard(
                metrics = metrics,
                onOpenTerminal = onOpenTerminal,
            )

            // 5. 运行环境与规格详情
            SystemSpecsCard(metrics = metrics)

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * 运行与开发环境体检自愈卡片 (TaiXu Doctor & Auto-Fix)
 */
@Composable
private fun EnvironmentDoctorCard(
    report: DoctorReport?,
    isChecking: Boolean,
    isRepairing: Boolean,
    repairProgress: RepairProgress?,
    runtimeReady: Boolean,
    onRunCheck: () -> Unit,
    onStartAutoRepair: () -> Unit,
    onCancelRepair: () -> Unit,
) {
    var isCardExpanded by remember { mutableStateOf(false) }
    var expandedDetails by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }

    // 修复中时自动展开
    LaunchedEffect(isRepairing) {
        if (isRepairing) isCardExpanded = true
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { isCardExpanded = !isCardExpanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (report?.isAllHealthy == true) Color(0xFF2E7D32).copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.primaryContainer,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        RuntimeIcon(
                            name = if (report?.isAllHealthy == true) RuntimeIconName.Check else RuntimeIconName.Shield,
                            modifier = Modifier.size(20.dp),
                            tint = if (report?.isAllHealthy == true) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "环境体检与自愈中心",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = when {
                                isRepairing -> "正在执行环境自愈与加速..."
                                isChecking -> "正在全面体检沙箱环境..."
                                report == null -> if (runtimeReady) "沙箱就绪 · 点击体检" else "等待沙箱就绪"
                                report.isAllHealthy -> "全功能开发就绪 · 体验最佳"
                                report.needsFix -> "${report.warningCount + report.errorCount} 项待就绪 · 点击展开"
                                else -> "体检完成"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (report?.needsFix == true && !isCardExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isChecking) {
                        RuntimeCircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (runtimeReady) {
                        IconButton(
                            onClick = { onRunCheck() },
                            enabled = !isRepairing,
                            modifier = Modifier.size(32.dp),
                        ) {
                            RuntimeIcon(
                                name = RuntimeIconName.Refresh,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    RuntimeIcon(
                        name = RuntimeIconName.ChevronDown,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(if (isCardExpanded) 180f else 0f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            // 展开区域：修复进度与体检指标详情
            AnimatedVisibility(
                visible = isCardExpanded || isRepairing,
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 修复中状态展示
                    if (isRepairing && repairProgress != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
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
                                        text = "[步骤 ${repairProgress.stepIndex}/${repairProgress.totalSteps}] ${repairProgress.stepTitle}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${(repairProgress.progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp),
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }

                                RuntimeLinearProgressIndicator(
                                    progress = { repairProgress.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(
                                        onClick = { showLogs = !showLogs },
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                    ) {
                                        Text(
                                            text = if (showLogs) "收起执行日志" else "查看实时日志 (${repairProgress.logs.size})",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }

                                    TextButton(
                                        onClick = onCancelRepair,
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                    ) {
                                        Text("取消修复", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }

                                AnimatedVisibility(visible = showLogs) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 140.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp)
                                                .verticalScroll(rememberScrollState()),
                                        ) {
                                            repairProgress.logs.takeLast(20).forEach { logLine ->
                                                Text(
                                                    text = logLine,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 11.sp,
                                                    ),
                                                    color = if (logLine.startsWith("ERR:")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 体检报告列表展示
                    if (report != null && !isRepairing) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val displayedItems = if (expandedDetails || report.needsFix) report.items else report.items.take(3)
                            displayedItems.forEach { item ->
                                DoctorItemRow(item = item)
                            }

                            if (report.items.size > 3) {
                                TextButton(
                                    onClick = { expandedDetails = !expandedDetails },
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                ) {
                                    Text(
                                        text = if (expandedDetails) "收起体检详情" else "查看全部 ${report.items.size} 项体检指标",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }

                        // 一键修复按钮或就绪横幅
                        if (report.needsFix) {
                            RuntimeButton(
                                onClick = onStartAutoRepair,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 12.dp),
                            ) {
                                RuntimeIcon(
                                    name = RuntimeIconName.Play,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "一键自愈修复",
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        } else if (report.isAllHealthy) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF2E7D32).copy(alpha = 0.1f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                RuntimeIcon(
                                    name = RuntimeIconName.Check,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFF2E7D32),
                                )
                                Text(
                                    text = "环境配置完善，已满足 Claude Code / OpenClaw 等 AI 工具运行要求",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF2E7D32),
                                )
                            }
                        }
                    } else if (!runtimeReady) {
                        Text(
                            text = "请先初始化并启动沙箱，太墟将自动体检 DNS、国内镜像加速源与核心开发工具链。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 体检条目单行展示
 */
@Composable
private fun DoctorItemRow(item: DoctorItem) {
    val statusColor = when (item.status) {
        DoctorStatus.HEALTHY -> Color(0xFF2E7D32)
        DoctorStatus.WARNING -> Color(0xFFE65100)
        DoctorStatus.ERROR -> MaterialTheme.colorScheme.error
        DoctorStatus.CHECKING -> MaterialTheme.colorScheme.tertiary
    }

    val statusIcon = when (item.status) {
        DoctorStatus.HEALTHY -> RuntimeIconName.Check
        DoctorStatus.WARNING -> RuntimeIconName.Alert
        DoctorStatus.ERROR -> RuntimeIconName.Alert
        DoctorStatus.CHECKING -> RuntimeIconName.Refresh
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                RuntimeIcon(
                    name = statusIcon,
                    modifier = Modifier.size(14.dp),
                    tint = statusColor,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "· ${item.category.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status != DoctorStatus.HEALTHY) statusColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val detail = item.detail
                if (!detail.isNullOrBlank() && item.status != DoctorStatus.HEALTHY) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 运行时引擎主状态卡片
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RuntimeEngineStatusCard(
    state: RuntimeState,
    metrics: SystemResourceMetrics,
    installedDistros: List<top.wkbin.taixu.core.model.InstalledDistro> = emptyList(),
    activeDistroId: String = "ubuntu",
    switchingDistro: Boolean = false,
    onSwitchDistro: (String) -> Unit = {},
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
                    modifier = Modifier.weight(1f),
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

                // 右侧展示当前发行版官方精确 Logo 徽章
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    RuntimeIcon(
                        name = distroIconFor(activeDistroId),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // 多系统快速切换 Chips（安装了 2 套及以上时展示，自动换行，避免横向滚动）
            if (ready && installedDistros.size > 1) {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "切换系统:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .align(androidx.compose.ui.Alignment.CenterVertically),
                    )
                    if (switchingDistro) {
                        RuntimeCircularProgressIndicator(
                            modifier = Modifier
                                .size(12.dp)
                                .align(androidx.compose.ui.Alignment.CenterVertically),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    installedDistros.forEach { d ->
                        val isSelected = d.id.equals(activeDistroId, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                            ),
                            modifier = Modifier.clickable(enabled = !switchingDistro) {
                                if (!isSelected) onSwitchDistro(d.id)
                            },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                RuntimeIcon(distroIconFor(d.id), Modifier.size(13.dp))
                                Text(
                                    text = if (isSelected && switchingDistro) "切换中…" else d.displayName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (initializing != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuntimeLinearProgressIndicator(
                        progress = { initializing.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
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
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text("取消", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (state is RuntimeState.Error) {
                NoticeBanner(
                    text = state.throwable.message ?: "沙箱引擎发生错误",
                    isError = true,
                )
            }

            // 快捷控制操作区
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (ready) {
                    RuntimeButton(
                        onClick = onOpenTerminal,
                        modifier = Modifier.weight(1f),
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
                    RuntimeButton(
                        onClick = onInitialize,
                        modifier = Modifier.weight(1f),
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

            RuntimeLinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = effectiveAccent,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
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

            RuntimeButton(
                onClick = onOpenTerminal,
                tonal = true,
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
            modifier = Modifier.weight(0.42f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(0.58f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
        remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
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
