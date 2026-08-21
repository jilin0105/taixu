package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.model.InstalledDistro
import top.wkbin.taixu.core.model.RuntimeState
import top.wkbin.taixu.runtime.DistributionCatalog
import top.wkbin.taixu.runtime.DistributionSpec
import top.wkbin.taixu.runtime.RegistryRoute
import top.wkbin.taixu.runtime.RuntimeInstallRequest
import top.wkbin.taixu.ui.components.NoticeBanner
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.StatusBadge

/**
 * 太墟 · Linux 发行版与沙箱多实例管理 (Distro Management Hub)
 */
@Composable
fun DistroManagementScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val installedDistros by viewModel.installedDistros.collectAsStateWithLifecycle()
    val activeDistroId by viewModel.activeDistroId.collectAsStateWithLifecycle()
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()

    var showInstallDialog by remember { mutableStateOf(false) }
    var distroToUninstall by remember { mutableStateOf<InstalledDistro?>(null) }
    var installProgress by remember { mutableStateOf<String?>(null) }
    var installProgressFraction by remember { mutableStateOf(0f) }
    var isInstalling by remember { mutableStateOf(false) }
    var installError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = "Linux 发行版管理",
                statusText = "多沙箱并存与动态切换",
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                NoticeBanner(
                    text = "太墟支持多套 Linux 系统并存。所有系统均自动挂载 /workspace 代码工程与 /sdcard 外部存储，各发行版软件生态与包管理器完全独立隔离。",
                )
            }

            // 1. 已安装系统列表
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "已安装沙箱 (${installedDistros.size})",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Button(
                        onClick = { showInstallDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Plus,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "安装新系统", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            items(installedDistros, key = { it.id }) { distro ->
                DistroItemCard(
                    distro = distro,
                    isActive = distro.id.equals(activeDistroId, ignoreCase = true),
                    canUninstall = installedDistros.size > 1,
                    onSwitchActive = {
                        viewModel.switchActiveDistro(distro.id)
                    },
                    onUninstall = {
                        distroToUninstall = distro
                    },
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // 安装新系统对话框
    if (showInstallDialog) {
        InstallDistroDialog(
            installedIds = installedDistros.map { it.id }.toSet(),
            isInstalling = isInstalling,
            progressText = installProgress,
            progressFraction = installProgressFraction,
            errorMessage = installError,
            onDismiss = {
                if (!isInstalling) {
                    showInstallDialog = false
                    installError = null
                }
            },
            onConfirmInstall = { spec, route ->
                isInstalling = true
                installError = null
                installProgress = "准备拉取镜像..."
                installProgressFraction = 0.05f
                scope.launch {
                    viewModel.installDistro(
                        request = RuntimeInstallRequest(
                            distributionId = spec.id,
                            registryRoute = route,
                        ),
                        onProgress = { p ->
                            installProgress = if (p.totalMegabytes != null) {
                                "下载中：${p.downloadedMegabytes} / ${p.totalMegabytes} MB"
                            } else {
                                "已下载：${p.downloadedMegabytes} MB"
                            }
                            installProgressFraction = (p.fraction ?: 0f) * 0.8f + 0.1f
                        },
                        onResult = { success, msg ->
                            isInstalling = false
                            if (success) {
                                showInstallDialog = false
                                installProgress = null
                                installError = null
                            } else {
                                installError = msg
                            }
                        },
                    )
                }
            },
        )
    }

    // 卸载确认对话框
    distroToUninstall?.let { target ->
        AlertDialog(
            onDismissRequest = { distroToUninstall = null },
            title = { Text(text = "确认卸载 ${target.displayName}？") },
            text = {
                Text(
                    text = "卸载将清除该发行版沙箱下的根目录文件与独立软件（释放约 ${String.format("%.1f", target.sizeBytes.toDouble() / (1024 * 1024))} MB 空间）。/workspace 工作区中的代码文件不会受到任何影响。",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = target.id
                        distroToUninstall = null
                        viewModel.uninstallDistro(toDelete)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(text = "确认卸载", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { distroToUninstall = null }) {
                    Text(text = "取消")
                }
            },
        )
    }
}

@Composable
private fun DistroItemCard(
    distro: InstalledDistro,
    isActive: Boolean,
    canUninstall: Boolean,
    onSwitchActive: () -> Unit,
    onUninstall: () -> Unit,
) {
    RuntimeCard(
        borderColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Storage,
                            modifier = Modifier.size(20.dp),
                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = distro.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "标识: ${distro.id} · 包管理: ${distro.packageManager}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (isActive) {
                    StatusBadge(text = "主系统", color = androidx.compose.ui.graphics.Color(0xFF2E7D32))
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "占用空间: ${formatSize(distro.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isActive) {
                        OutlinedButton(
                            onClick = onSwitchActive,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(text = "设为主系统", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (canUninstall) {
                        TextButton(
                            onClick = onUninstall,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "卸载",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallDistroDialog(
    installedIds: Set<String>,
    isInstalling: Boolean,
    progressText: String?,
    progressFraction: Float,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirmInstall: (DistributionSpec, RegistryRoute) -> Unit,
) {
    val availableDistros = remember {
        DistributionCatalog.supported.filter { it.id !in installedIds }
    }
    var selectedSpec by remember {
        mutableStateOf(availableDistros.firstOrNull() ?: DistributionCatalog.supported.first())
    }
    var selectedRoute by remember { mutableStateOf(RegistryRoute.AUTO) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isInstalling) "正在安装 Linux 沙箱..." else "安装新 Linux 发行版",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isInstalling) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        )
                        Text(
                            text = progressText ?: "正在下载并配置 RootFS...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    if (availableDistros.isEmpty()) {
                        Text(text = "所有预置 Linux 发行版均已安装完毕！")
                    } else {
                        Text(
                            text = "选择要下载并安装的 Linux 系统：",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        availableDistros.forEach { spec ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedSpec = spec }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedSpec.id == spec.id,
                                    onClick = { selectedSpec = spec },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = spec.displayWithVersion,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    )
                                    Text(
                                        text = spec.imageReference,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "下载线路与镜像加速：",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { selectedRoute = RegistryRoute.AUTO },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = if (selectedRoute == RegistryRoute.AUTO) {
                                    ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                } else ButtonDefaults.outlinedButtonColors(),
                            ) {
                                Text("自动加速", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { selectedRoute = RegistryRoute.CHINA_ACCELERATED },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = if (selectedRoute == RegistryRoute.CHINA_ACCELERATED) {
                                    ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                } else ButtonDefaults.outlinedButtonColors(),
                            ) {
                                Text("国内镜像", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    errorMessage?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isInstalling && availableDistros.isNotEmpty()) {
                Button(
                    onClick = { onConfirmInstall(selectedSpec, selectedRoute) },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(text = "开始安装")
                }
            }
        },
        dismissButton = {
            if (!isInstalling) {
                TextButton(onClick = onDismiss) {
                    Text(text = "取消")
                }
            }
        },
    )
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1024) {
        String.format("%.2f GB", mb / 1024)
    } else {
        String.format("%.1f MB", mb)
    }
}
