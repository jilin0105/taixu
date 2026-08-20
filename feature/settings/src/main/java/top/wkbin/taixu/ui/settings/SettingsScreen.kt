package top.wkbin.taixu.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.model.ExecutionMode
import top.wkbin.taixu.core.tools.AgentProviderDefinition
import top.wkbin.taixu.ui.components.IconTile
import top.wkbin.taixu.ui.components.MainDestination
import top.wkbin.taixu.ui.components.RuntimeBottomBar
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.SectionHeader

/**
 * 太墟 · 乾坤配置 (TaiXu Settings & Models)
 */
@Composable
fun SettingsScreen(
    onNavigate: (MainDestination) -> Unit,
    onOpenModelProfiles: () -> Unit,
    onOpenAgentSettings: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenStorageMounts: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val developer by viewModel.developerMode.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val skills by viewModel.allSkills.collectAsStateWithLifecycle()
    val executionMode by viewModel.executionMode.collectAsStateWithLifecycle()
    val switchingMode by viewModel.switchingMode.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = "太墟 · 乾坤",
                statusText = "系统设置与模型管理",
            )
        },
        bottomBar = { RuntimeBottomBar(MainDestination.Settings, onNavigate) },
    ) { padding ->
        HomePage(
            modifier = Modifier.padding(padding),
            count = models.size,
            activeSkillsCount = skills.count { it.isEnabled },
            developer = developer,
            themeMode = themeMode,
            executionMode = executionMode,
            switchingMode = switchingMode,
            onSwitchExecutionMode = viewModel::switchExecutionMode,
            onThemeModeChanged = viewModel::setThemeMode,
            openModels = onOpenModelProfiles,
            openAgentSettings = onOpenAgentSettings,
            openStorageMounts = onOpenStorageMounts,
            setDeveloper = viewModel::setDeveloperMode,
            openDeveloper = onOpenDeveloper,
        )
    }
}

@Composable
fun ModelProfilesScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar("模型档案", onBack) },
    ) { padding ->
        ModelsPage(
            modifier = Modifier.padding(padding),
            models = models,
            add = onCreate,
            edit = { model -> onEdit(model.id) },
            activate = viewModel::setActiveModel,
            delete = viewModel::deleteModel,
        )
    }
}

@Composable
fun ModelEditorScreen(
    modelId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val discovered by viewModel.discoveredModels.collectAsStateWithLifecycle()
    val discovering by viewModel.discoveringModels.collectAsStateWithLifecycle()
    val discoveryError by viewModel.modelDiscoveryError.collectAsStateWithLifecycle()
    val testing by viewModel.testingConnection.collectAsStateWithLifecycle()
    val testResult by viewModel.connectionResult.collectAsStateWithLifecycle()
    val existing = models.firstOrNull { it.id == modelId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar(if (existing == null) "新增模型" else "编辑模型", onBack) },
    ) { padding ->
        ModelEditor(
            modifier = Modifier.padding(padding),
            existing = existing,
            providers = viewModel.providerCatalog,
            discovered = discovered,
            discovering = discovering,
            error = discoveryError,
            testing = testing,
            result = testResult,
            discover = { provider, url, key -> viewModel.discoverModels(provider, url, key) },
            test = viewModel::testConnection,
            save = { name, provider, model, url, key, temperature, maxTokens, topP ->
                viewModel.saveModel(modelId, name, provider, model, url, key, temperature, maxTokens, topP)
                onSaved()
            },
        )
    }
}

@Composable
private fun HomePage(
    modifier: Modifier,
    count: Int,
    activeSkillsCount: Int,
    developer: Boolean,
    themeMode: String,
    executionMode: ExecutionMode,
    switchingMode: Boolean,
    onSwitchExecutionMode: (ExecutionMode, (Boolean, String) -> Unit) -> Unit,
    onThemeModeChanged: (String) -> Unit,
    openModels: () -> Unit,
    openAgentSettings: () -> Unit,
    openStorageMounts: () -> Unit,
    setDeveloper: (Boolean) -> Unit,
    openDeveloper: () -> Unit,
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showExecutionModeDialog by remember { mutableStateOf(false) }
    var showBatteryDialog by remember { mutableStateOf(false) }
    var privilegeResultMessage by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var batteryExempted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    val themeLabel = when (themeMode) {
        "light" -> "素白浅色"
        "dark" -> "深邃曜石"
        else -> "跟随系统"
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = themeMode,
            onSelect = onThemeModeChanged,
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showAboutDialog) {
        AboutAppDialog(onDismiss = { showAboutDialog = false })
    }

    if (showExecutionModeDialog) {
        ExecutionModeDialog(
            currentMode = executionMode,
            switching = switchingMode,
            onSelectMode = { mode ->
                onSwitchExecutionMode(mode) { success, message ->
                    if (success) {
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                        showExecutionModeDialog = false
                    } else {
                        privilegeResultMessage = message
                    }
                }
            },
            onDismiss = { showExecutionModeDialog = false },
        )
    }

    if (showBatteryDialog) {
        BatteryOptimizationDialog(
            exempted = batteryExempted,
            onRefresh = { batteryExempted = isIgnoringBatteryOptimizations(context) },
            onDismiss = { showBatteryDialog = false },
        )
    }

    privilegeResultMessage?.let { errorMsg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { privilegeResultMessage = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuntimeIcon(RuntimeIconName.Alert, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.error)
                    Text("运行模式授权未通过")
                }
            },
            text = { Text(errorMsg, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { privilegeResultMessage = null }) {
                    Text("知道了")
                }
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. 视觉与偏好
        item {
            Text(
                text = "视觉与偏好",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            SettingsGroup {
                SettingsRow(
                    icon = RuntimeIconName.Globe,
                    title = "外观与主题",
                    subtitle = "深浅色模式与 Material 3 Expressive 风格",
                    value = themeLabel,
                    onClick = { showThemeDialog = true },
                )
            }
        }

        // 2. 运行模式与特权
        item {
            Text(
                text = "运行模式与特权",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            SettingsGroup {
                SettingsRow(
                    icon = RuntimeIconName.Shield,
                    title = "系统运行模式",
                    subtitle = "沙箱 · Shizuku · Root · ADB",
                    value = executionMode.name,
                    onClick = { showExecutionModeDialog = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsRow(
                    icon = RuntimeIconName.Shield,
                    title = "电池优化与后台运行",
                    subtitle = "豁免电池优化，防止 Agent 后台执行被冻结",
                    value = if (batteryExempted) "已豁免" else "未豁免",
                    onClick = { showBatteryDialog = true },
                )
            }
        }

        // 3. 智能体与模型
        item {
            Text(
                text = "智能体与模型",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            SettingsGroup {
                SettingsRow(
                    icon = RuntimeIconName.Package,
                    title = "Agent 智能体管理",
                    subtitle = "思考流呈现、上下文压缩阈值与技能插件",
                    value = "$activeSkillsCount 个技能",
                    onClick = openAgentSettings,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsRow(
                    icon = RuntimeIconName.Globe,
                    title = "模型档案管理",
                    subtitle = "配置 OpenAI / DeepSeek / Claude API 密钥",
                    value = if (count == 0) "未配置" else "$count 个模型",
                    onClick = openModels,
                )
            }
        }

        // 4. 存储与系统
        item {
            Text(
                text = "存储与系统",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            SettingsGroup {
                SettingsRow(
                    icon = RuntimeIconName.Folder,
                    title = "存储挂载与共享",
                    subtitle = "PRoot 宿主存储映射 (-b /sdcard)",
                    onClick = openStorageMounts,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ToggleRow(
                    icon = RuntimeIconName.Terminal,
                    title = "开发者诊断模式",
                    subtitle = "开启底层健康监控与调试控制台",
                    checked = developer,
                    change = setDeveloper,
                )
                if (developer) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Logs,
                        title = "开发者控制台",
                        subtitle = "实时查看 PRoot 进程与命令追踪",
                        onClick = openDeveloper,
                    )
                }
            }
        }

        // 5. 关于与版本
        item {
            Text(
                text = "关于",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            SettingsGroup {
                SettingsRow(
                    icon = RuntimeIconName.Package,
                    title = "太墟 · TaiXu",
                    subtitle = "Android 原生 Linux 沙箱与 AI 结对中枢",
                    value = "v0.1.0",
                    onClick = { showAboutDialog = true },
                )
            }
        }
    }
}

@Composable
private fun ExecutionModeDialog(
    currentMode: ExecutionMode,
    switching: Boolean,
    onSelectMode: (ExecutionMode) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!switching) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeIcon(RuntimeIconName.Shield, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Text("选择系统运行模式", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "切换特权模式将自动发起授权检测；授权成功后即刻释放对应的高级系统与硬件能力。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (switching) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                        Text("正在进行特权探测与授权申请…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                ExecutionMode.entries.forEach { mode ->
                    ExecutionModeOptionItem(
                        mode = mode,
                        selected = currentMode == mode,
                        enabled = !switching,
                        onClick = { onSelectMode(mode) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !switching) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun ExecutionModeOptionItem(
    mode: ExecutionMode,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            "当前激活",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                text = mode.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "要求: ${mode.requiredPrivilege}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun BatteryOptimizationDialog(
    exempted: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 从系统授权页返回时刷新豁免状态
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onRefresh() }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeIcon(RuntimeIconName.Shield, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Text("电池优化与后台运行", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (exempted) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                    ) {
                        Text(
                            if (exempted) "已豁免电池优化" else "未豁免 · 后台可能被冻结",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (exempted) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                Text(
                    "太墟在 Agent 执行期间会启动前台服务并持有 CPU 进程锁，但系统电池优化仍可能在息屏后" +
                        "冻结进程，表现为 Agent 推理或命令执行中途停住。建议开启以下两项：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("申请豁免电池优化")
                }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("打开应用详情（自启动/后台运行）")
                }
                Text(
                    "提示：小米/华为/OPPO 等厂商系统还需在应用详情中手动允许「自启动」与「后台运行」，" +
                        "否则厂商省电策略仍会终止进程。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

@Composable
private fun ThemeSelectionDialog(
    currentTheme: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("选择外观主题", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeOptionItem(
                    title = "跟随系统",
                    subtitle = "随 Android 设备系统深浅色自动切换",
                    icon = RuntimeIconName.Refresh,
                    selected = currentTheme == "system",
                    onClick = {
                        onSelect("system")
                        onDismiss()
                    },
                )
                ThemeOptionItem(
                    title = "素白浅色 (Light)",
                    subtitle = "明澈素雅，适合日间光线明亮环境",
                    icon = RuntimeIconName.Globe,
                    selected = currentTheme == "light",
                    onClick = {
                        onSelect("light")
                        onDismiss()
                    },
                )
                ThemeOptionItem(
                    title = "深邃曜石 (Dark)",
                    subtitle = "M3 曜石天鹅绒暗色，沉浸专注",
                    icon = RuntimeIconName.Terminal,
                    selected = currentTheme == "dark",
                    onClick = {
                        onSelect("dark")
                        onDismiss()
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        },
    )
}

@Composable
private fun ThemeOptionItem(
    title: String,
    subtitle: String,
    icon: RuntimeIconName,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RuntimeIcon(
                name = icon,
                modifier = Modifier.size(20.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                RuntimeIcon(
                    name = RuntimeIconName.Check,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun AboutAppDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeIcon(name = RuntimeIconName.Package, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Text("太墟 · TaiXu", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Android 原生 Linux PRoot 沙箱与 AI 结对编程中枢", style = MaterialTheme.typography.bodyMedium)
                Text("版本: v0.1.0 (Material 3 Expressive)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("架构: aarch64 · chroot-less user-space virtualization", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("协议: Apache-2.0 License", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("确定") }
        },
    )
}

@Composable
private fun ModelsPage(
    modifier: Modifier,
    models: List<AiModelEntity>,
    add: () -> Unit,
    edit: (AiModelEntity) -> Unit,
    activate: (String) -> Unit,
    delete: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Button(
                onClick = add,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuntimeIcon(RuntimeIconName.Plus, Modifier.size(18.dp), MaterialTheme.colorScheme.onPrimary)
                    Text("新增模型档案", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (models.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconTile(RuntimeIconName.Globe, color = MaterialTheme.colorScheme.primary, size = 48.dp)
                    Text("暂无模型档案", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text("点击上方按钮添加 OpenAI / DeepSeek / Claude 等模型配置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(models, key = { it.id }) { model ->
            RuntimeCard(
                modifier = Modifier.fillMaxWidth().clickable { edit(model) },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                borderColor = if (model.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        top.wkbin.taixu.ui.components.ProviderBadge(
                            providerIdOrName = model.provider,
                            size = 26.dp,
                        )
                        Text(model.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                        if (model.isActive) {
                            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                Text("当前激活", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(model.provider, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(model.model, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        model.baseUrl,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        if (!model.isActive) {
                            OutlinedButton(onClick = { activate(model.id) }, shape = RoundedCornerShape(8.dp)) { Text("设为激活") }
                        }
                        IconButton(onClick = { delete(model.id) }) {
                            RuntimeIcon(RuntimeIconName.Trash, Modifier.size(18.dp), MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    RuntimeCard(
        Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        contentPadding = PaddingValues(0.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: RuntimeIconName,
    title: String,
    subtitle: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Row(
        rowModifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            RuntimeIcon(icon, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        value?.let {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            ) {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        if (onClick != null) {
            RuntimeIcon(RuntimeIconName.ChevronRight, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(
    icon: RuntimeIconName,
    title: String,
    subtitle: String,
    checked: Boolean,
    change: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            RuntimeIcon(icon, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = change,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelEditor(
    modifier: Modifier,
    existing: AiModelEntity?,
    providers: List<AgentProviderDefinition>,
    discovered: List<String>,
    discovering: Boolean,
    error: String?,
    testing: Boolean,
    result: String?,
    discover: (String, String, String) -> Unit,
    test: (String, String, String) -> Unit,
    save: (String, String, String, String, String, Float?, Int?, Float?) -> Unit,
) {
    var providerId by remember(existing?.id) {
        mutableStateOf(providers.firstOrNull { it.name == existing?.provider }?.id ?: providers.first().id)
    }
    val provider = providers.first { it.id == providerId }
    var providerMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var model by remember(existing?.id) { mutableStateOf(existing?.model ?: provider.recommendedModels.firstOrNull().orEmpty()) }
    var url by remember(existing?.id) { mutableStateOf(existing?.baseUrl ?: provider.baseUrl) }
    var key by remember(existing?.id) { mutableStateOf(existing?.apiKey.orEmpty()) }
    // 推理参数（空 = 使用服务端默认）
    var temperatureText by remember(existing?.id) { mutableStateOf(existing?.temperature?.toString().orEmpty()) }
    var maxTokensText by remember(existing?.id) { mutableStateOf(existing?.maxTokens?.toString().orEmpty()) }
    var topPText by remember(existing?.id) { mutableStateOf(existing?.topP?.toString().orEmpty()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(if (existing == null) "新增模型档案" else "编辑模型档案", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) }
        item {
            ExposedDropdownMenuBox(
                expanded = providerMenu,
                onExpandedChange = { providerMenu = !providerMenu },
            ) {
                OutlinedTextField(
                    value = provider.name,
                    onValueChange = {},
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    readOnly = true,
                    label = { Text("服务商预设") },
                )
                ExposedDropdownMenu(
                    expanded = providerMenu,
                    onDismissRequest = { providerMenu = false },
                ) {
                    providers.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    top.wkbin.taixu.ui.components.ProviderBadge(
                                        providerIdOrName = option.id,
                                        size = 22.dp,
                                    )
                                    Text(option.name)
                                }
                            },
                            leadingIcon = null,
                            onClick = {
                                providerId = option.id
                                url = option.baseUrl
                                model = option.recommendedModels.firstOrNull().orEmpty()
                                providerMenu = false
                            },
                        )
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("档案名称") },
                singleLine = true,
            )
        }
        item {
            ExposedDropdownMenuBox(
                expanded = modelMenu,
                onExpandedChange = { modelMenu = !modelMenu },
            ) {
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth(),
                    label = { Text("模型 ID") },
                    singleLine = true,
                )
                ExposedDropdownMenu(
                    expanded = modelMenu,
                    onDismissRequest = { modelMenu = false },
                ) {
                    (discovered + provider.recommendedModels).distinct().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                model = option
                                modelMenu = false
                            },
                        )
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
        }
        item {
            Text("推理参数（可选，留空使用服务端默认）", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
        }
        item {
            OutlinedTextField(
                value = temperatureText,
                onValueChange = { temperatureText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Temperature（0.0 ~ 2.0）") },
                placeholder = { Text("默认") },
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = maxTokensText,
                onValueChange = { maxTokensText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Max Tokens（单次回复上限）") },
                placeholder = { Text("默认") },
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = topPText,
                onValueChange = { topPText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Top P（0.0 ~ 1.0）") },
                placeholder = { Text("默认") },
                singleLine = true,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { discover(providerId, url, key) }, enabled = !discovering) {
                    Text(if (discovering) "刷新中…" else "刷新在线模型")
                }
                OutlinedButton(onClick = { test(url, model, key) }, enabled = !testing) {
                    Text(if (testing) "测试中…" else "测试连接")
                }
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        result?.let {
            item {
                Text(
                    it,
                    color = if (it == "连接成功") Color(0xFF00E676) else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        item {
            val parsedTemperature = temperatureText.trim().toFloatOrNull()
            val parsedMaxTokens = maxTokensText.trim().toIntOrNull()
            val parsedTopP = topPText.trim().toFloatOrNull()
            val invalid = buildList {
                if (temperatureText.isNotBlank() && (parsedTemperature == null || parsedTemperature !in 0f..2f)) add("Temperature 需为 0.0 ~ 2.0 的数字")
                if (maxTokensText.isNotBlank() && (parsedMaxTokens == null || parsedMaxTokens <= 0)) add("Max Tokens 需为正整数")
                if (topPText.isNotBlank() && (parsedTopP == null || parsedTopP !in 0f..1f)) add("Top P 需为 0.0 ~ 1.0 的数字")
            }.joinToString("；").ifBlank { null }
            invalid?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = {
                    save(
                        name.ifBlank { model },
                        provider.name,
                        model,
                        url,
                        key,
                        parsedTemperature,
                        parsedMaxTokens,
                        parsedTopP,
                    )
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                enabled = model.isNotBlank() && url.isNotBlank() && invalid == null,
            ) {
                Text("保存模型配置", fontWeight = FontWeight.Bold)
            }
        }
    }
}
