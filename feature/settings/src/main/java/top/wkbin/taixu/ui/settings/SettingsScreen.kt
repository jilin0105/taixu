package top.wkbin.taixu.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.model.ExecutionMode
import top.wkbin.taixu.core.tools.AgentProviderDefinition
import top.wkbin.taixu.core.tools.ProviderEndpointPolicy
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
    onOpenAgentEco: () -> Unit,
    onOpenLinuxEnv: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenSystemDev: () -> Unit,
    onOpenAboutCommunity: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val developer by viewModel.developerMode.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val skills by viewModel.allSkills.collectAsStateWithLifecycle()
    val executionMode by viewModel.executionMode.collectAsStateWithLifecycle()
    val installedDistros by viewModel.installedDistros.collectAsStateWithLifecycle()
    val activeDistroId by viewModel.activeDistroId.collectAsStateWithLifecycle()
    val terminalFontSize by viewModel.terminalFontSize.collectAsStateWithLifecycle()

    val themeLabel = when (themeMode) {
        "light" -> "浅色"
        "dark" -> "曜石"
        else -> "跟随系统"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = "太墟 · 乾坤",
                statusText = "系统设置与控制中枢",
            )
        },
        bottomBar = { RuntimeBottomBar(MainDestination.Settings, onNavigate) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = "系统与配置分类",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }

            // 1. 智能体与 AI 模型生态
            item {
                SettingsCategoryCard(
                    icon = RuntimeIconName.Brain,
                    iconTint = Color(0xFF6366F1),
                    iconBg = Color(0xFF6366F1).copy(alpha = 0.12f),
                    title = "智能体与 AI 模型",
                    subtitle = "模型档案 · 插件工具中心 · 技能与 MCP 生态",
                    badge = if (models.isEmpty()) "未配置模型" else "${models.size} 个模型 · ${skills.count { it.isEnabled }} 技能",
                    onClick = onOpenAgentEco,
                )
            }

            // 2. Linux 容器沙箱与存储
            item {
                SettingsCategoryCard(
                    icon = RuntimeIconName.Server,
                    iconTint = Color(0xFF10B981),
                    iconBg = Color(0xFF10B981).copy(alpha = 0.12f),
                    title = "Linux 容器与存储",
                    subtitle = "多发行版管理 · 宿主存储映射 · 运行特权模式",
                    badge = "${installedDistros.size} 套系统 · ${executionMode.name}",
                    onClick = onOpenLinuxEnv,
                )
            }

            // 3. 外观、字号与终端定制
            item {
                SettingsCategoryCard(
                    icon = RuntimeIconName.Palette,
                    iconTint = Color(0xFF8B5CF6),
                    iconBg = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                    title = "外观、字号与终端定制",
                    subtitle = "深浅色主题 · 应用字号缩放 · 终端配色与字体",
                    badge = "$themeLabel · ${terminalFontSize}sp",
                    onClick = onOpenAppearance,
                )
            }

            // 4. 系统保活与开发者诊断
            item {
                SettingsCategoryCard(
                    icon = RuntimeIconName.Admin,
                    iconTint = Color(0xFFF59E0B),
                    iconBg = Color(0xFFF59E0B).copy(alpha = 0.12f),
                    title = "系统保活与开发者诊断",
                    subtitle = "后台电池优化白名单 · 调试监控 · PRoot 控制台",
                    badge = if (developer) "诊断模式已开启" else "运行平稳",
                    onClick = onOpenSystemDev,
                )
            }

            // 5. 关于、更新与官方社区
            item {
                SettingsCategoryCard(
                    icon = RuntimeIconName.Community,
                    iconTint = Color(0xFF3B82F6),
                    iconBg = Color(0xFF3B82F6).copy(alpha = 0.12f),
                    title = "关于、更新与官方社区",
                    subtitle = "检查新版本 · GitHub 开源仓库 · 官方 QQ 交流群",
                    badge = "v0.1.0 稳定版",
                    onClick = onOpenAboutCommunity,
                )
            }
        }
    }
}

/**
 * 现代高质感大类导航卡片
 */
@Composable
private fun SettingsCategoryCard(
    icon: RuntimeIconName,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RuntimeCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBg)
                    .border(1.dp, iconTint.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                RuntimeIcon(icon, Modifier.size(22.dp), tint = iconTint)
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        color = iconTint,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }

            RuntimeIcon(
                name = RuntimeIconName.ChevronRight,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * 二级子页 1：智能体与 AI 模型生态
 */
@Composable
fun AgentEcoSettingsScreen(
    onBack: () -> Unit,
    onOpenModelProfiles: () -> Unit,
    onOpenToolCenter: () -> Unit,
    onOpenAgentSettings: () -> Unit,
    onOpenMcpSettings: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val skills by viewModel.allSkills.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar("智能体与模型", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "模型档案与提供商",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Model,
                        title = "模型档案管理",
                        subtitle = "配置 OpenAI / DeepSeek / Claude / 本地大模型密钥与端点",
                        value = if (models.isEmpty()) "未配置" else "${models.size} 个模型",
                        onClick = onOpenModelProfiles,
                    )
                }
            }

            item {
                Text(
                    text = "工具与插件生态",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Wrench,
                        title = "插件与工具生态中心",
                        subtitle = "一键安装 Claude Code、OpenClaw 等 AI CLI 与开发环境",
                        onClick = onOpenToolCenter,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Bot,
                        title = "Agent 智能体管理",
                        subtitle = "思考流呈现、上下文压缩阈值与技能插件",
                        value = "${skills.count { it.isEnabled }} 个技能",
                        onClick = onOpenAgentSettings,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Network,
                        title = "MCP 协议生态与服务",
                        subtitle = "管理 SQLite、Git、Fetch 等 Model Context Protocol 协议服务",
                        onClick = onOpenMcpSettings,
                    )
                }
            }
        }
    }
}

/**
 * 二级子页 2：Linux 容器沙箱与存储
 */
@Composable
fun LinuxEnvironmentSettingsScreen(
    onBack: () -> Unit,
    onOpenDistroManagement: () -> Unit,
    onOpenStorageMounts: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val executionMode by viewModel.executionMode.collectAsStateWithLifecycle()
    val switchingMode by viewModel.switchingMode.collectAsStateWithLifecycle()
    val installedDistros by viewModel.installedDistros.collectAsStateWithLifecycle()

    var showExecutionModeDialog by remember { mutableStateOf(false) }
    var privilegeResultMessage by remember { mutableStateOf<String?>(null) }

    if (showExecutionModeDialog) {
        ExecutionModeDialog(
            currentMode = executionMode,
            switching = switchingMode,
            onSelectMode = { mode ->
                showExecutionModeDialog = false
                viewModel.switchExecutionMode(mode) { success, msg ->
                    privilegeResultMessage = if (success) null else msg
                }
            },
            onDismiss = { showExecutionModeDialog = false },
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar("Linux 容器与存储", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "容器系统与沙箱管理",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Server,
                        title = "Linux 发行版管理",
                        subtitle = "多沙箱并存 · 镜像拉取 · 一键切换主系统",
                        value = "${installedDistros.size} 套系统",
                        onClick = onOpenDistroManagement,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.SdCard,
                        title = "存储挂载与共享",
                        subtitle = "PRoot 宿主存储映射 (-b /sdcard)",
                        onClick = onOpenStorageMounts,
                    )
                }
            }

            item {
                Text(
                    text = "系统底层特权",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Key,
                        title = "系统运行特权模式",
                        subtitle = "PRoot 用户态沙箱 · Shizuku · Root · ADB",
                        value = executionMode.name,
                        onClick = { showExecutionModeDialog = true },
                    )
                }
            }
        }
    }
}

/**
 * 二级子页 3：系统保活与开发者诊断
 */
@Composable
fun SystemDevSettingsScreen(
    onBack: () -> Unit,
    onOpenDeveloper: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val developer by viewModel.developerMode.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showBatteryDialog by remember { mutableStateOf(false) }
    var batteryExempted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    if (showBatteryDialog) {
        BatteryOptimizationDialog(
            exempted = batteryExempted,
            onRefresh = { batteryExempted = isIgnoringBatteryOptimizations(context) },
            onDismiss = { showBatteryDialog = false },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar("保活与诊断", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "进程保活与唤醒",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Battery,
                        title = "电池优化与后台保活",
                        subtitle = "豁免系统电池限制，防止 Agent 息屏被冻结",
                        value = if (batteryExempted) "已豁免" else "未豁免",
                        onClick = { showBatteryDialog = true },
                    )
                }
            }

            item {
                Text(
                    text = "开发者调试与控制台",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    ToggleRow(
                        icon = RuntimeIconName.Bug,
                        title = "开发者诊断模式",
                        subtitle = "开启底层健康监控与调试控制台",
                        checked = developer,
                        change = viewModel::setDeveloperMode,
                    )
                    if (developer) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        SettingsRow(
                            icon = RuntimeIconName.Terminal,
                            title = "开发者控制台",
                            subtitle = "实时查看 PRoot 进程与命令追踪",
                            onClick = onOpenDeveloper,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 模型档案管理全屏独立页面
 */
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

/**
 * 模型编辑与连接测试全屏独立页面
 */
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
            save = { name, provider, model, url, key, temperature, maxTokens, topP, reasoningMode, reasoningEffort, toolCallMode, contextTokens, customHeaders, pureChatMode, visionEnabled ->
                viewModel.saveModel(
                    id = modelId,
                    name = name,
                    provider = provider,
                    model = model,
                    baseUrl = url,
                    apiKey = key,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    topP = topP,
                    reasoningMode = reasoningMode,
                    reasoningEffort = reasoningEffort,
                    toolCallMode = toolCallMode,
                    contextTokens = contextTokens,
                    customHeaders = customHeaders,
                    pureChatMode = pureChatMode,
                    visionEnabled = visionEnabled,
                )
                onSaved()
            },
        )
    }
}

/**
 * 二级子页 4：关于、版本更新与官方社区
 */
@Composable
fun AboutCommunityScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val autoCheckUpdates by viewModel.autoCheckUpdates.collectAsStateWithLifecycle()
    val updateCheckState by viewModel.updateCheckState.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showAboutDialog by remember { mutableStateOf(false) }

    // 版本更新弹窗
    when (val state = updateCheckState) {
        is top.wkbin.taixu.core.model.UpdateCheckState.Success -> {
            if (state.info.hasUpdate) {
                UpdateInfoDialog(
                    info = state.info,
                    downloadProgress = downloadProgress,
                    isDownloading = isDownloading,
                    onDownload = { state.info.apkDownloadUrl?.let { viewModel.downloadAndInstall(it) } },
                    onOpenBrowser = { openBrowser(context, state.info.releaseUrl) },
                    onDismiss = { viewModel.clearUpdateState() },
                )
            } else {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { viewModel.clearUpdateState() },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RuntimeIcon(RuntimeIconName.Check, Modifier.size(22.dp), tint = Color(0xFF2E7D32))
                            Text("已是最新版本", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Text("当前太墟版本 v${state.info.currentVersion} 已是最新稳定版，无需更新。")
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearUpdateState() }) {
                            Text("确定")
                        }
                    },
                )
            }
        }
        is top.wkbin.taixu.core.model.UpdateCheckState.Error -> {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.clearUpdateState() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuntimeIcon(RuntimeIconName.Alert, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.error)
                        Text("检查更新失败")
                    }
                },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearUpdateState() }) {
                        Text("知道了")
                    }
                },
            )
        }
        else -> Unit
    }

    if (showAboutDialog) {
        AboutAppDialog(onDismiss = { showAboutDialog = false })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar("关于与社区", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "应用版本与更新",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Update,
                        title = "检查新版本",
                        subtitle = "基于 GitHub Releases 自动检测与在线升级",
                        value = if (updateCheckState is top.wkbin.taixu.core.model.UpdateCheckState.Checking) "检查中…" else "v0.1.0",
                        onClick = { viewModel.checkForUpdates("0.1.0") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ToggleRow(
                        icon = RuntimeIconName.Update,
                        title = "启动时自动检查更新",
                        subtitle = "应用启动时在后台静默检测新版本",
                        checked = autoCheckUpdates,
                        change = viewModel::setAutoCheckUpdates,
                    )
                }
            }

            item {
                Text(
                    text = "官方社区与开源",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Github,
                        title = "GitHub 开源项目",
                        subtitle = "https://github.com/wkbin/taixu · 欢迎 Star 支持",
                        onClick = { openBrowser(context, "https://github.com/wkbin/taixu") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Qq,
                        title = "官方 QQ 交流群",
                        subtitle = "群号: 964382207 · 点击一键加群 / 复制群号",
                        value = "964382207",
                        onClick = { joinQqGroup(context, "964382207") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Info,
                        title = "关于太墟 · TaiXu",
                        subtitle = "Android 原生 Linux PRoot 沙箱与 AI 结对中枢",
                        onClick = { showAboutDialog = true },
                    )
                }
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
    val context = androidx.compose.ui.platform.LocalContext.current
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
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { joinQqGroup(context, "964382207") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    RuntimeIcon(RuntimeIconName.Chat, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("加入 QQ 交流群 (964382207)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("确定") }
        },
    )
}

@Composable
private fun UpdateInfoDialog(
    info: top.wkbin.taixu.core.model.AppUpdateInfo,
    downloadProgress: Float?,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onOpenBrowser: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Text("发现新版本 v${info.latestVersion}", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "当前版本: v${info.currentVersion}  ➔  最新版本: v${info.latestVersion}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }

                if (info.releaseNotes.isNotBlank()) {
                    Text(
                        text = "更新日志：",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = info.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                if (isDownloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("正在下载更新安装包...", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        if (downloadProgress != null) {
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            )
                        } else {
                            androidx.compose.material3.LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (info.apkDownloadUrl != null) {
                Button(
                    onClick = onDownload,
                    enabled = !isDownloading,
                ) {
                    Text(if (isDownloading) "正在下载…" else "应用内立即更新")
                }
            } else {
                Button(onClick = onOpenBrowser) {
                    Text("前往 GitHub 下载")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDownloading) {
                Text("稍后再说")
            }
        },
    )
}

private fun joinQqGroup(context: Context, groupId: String = "964382207") {
    val uri = Uri.parse("mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$groupId&card_type=group&source=qrcode")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        // 剪贴板兜底
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("太墟官方交流群", groupId)
        clipboard?.setPrimaryClip(clip)
        android.widget.Toast.makeText(context, "已复制 QQ 群号：$groupId，可打开 QQ 搜索加入", android.widget.Toast.LENGTH_LONG).show()
    }
}

private fun openBrowser(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("URL", url)
        clipboard?.setPrimaryClip(clip)
        android.widget.Toast.makeText(context, "已复制链接：$url", android.widget.Toast.LENGTH_SHORT).show()
    }
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
internal fun SettingsGroup(content: @Composable () -> Unit) {
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
internal fun SettingsRow(
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onClick != null) {
            RuntimeIcon(RuntimeIconName.ChevronRight, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ToggleRow(
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
    modifier: Modifier = Modifier,
    existing: top.wkbin.taixu.core.database.AiModelEntity?,
    providers: List<AgentProviderDefinition>,
    discovered: List<String>,
    discovering: Boolean,
    error: String?,
    testing: Boolean,
    result: String?,
    discover: (String, String, String) -> Unit,
    test: (String, String, String) -> Unit,
    save: (String, String, String, String, String, Float?, Int?, Float?, String?, String?, String?, Int?, String, Boolean, Boolean) -> Unit,
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

    // 推理与上下文参数
    var temperatureText by remember(existing?.id) { mutableStateOf(existing?.temperature?.toString().orEmpty()) }
    var maxTokensText by remember(existing?.id) { mutableStateOf(existing?.maxTokens?.toString().orEmpty()) }
    var contextTokensText by remember(existing?.id) { mutableStateOf(existing?.contextTokens?.toString().orEmpty()) }
    var topPText by remember(existing?.id) { mutableStateOf(existing?.topP?.toString().orEmpty()) }

    // 推理开关/强度（"auto" = 跟随模型默认）
    var reasoningModeText by remember(existing?.id) { mutableStateOf(existing?.reasoningMode ?: "auto") }
    var reasoningEffortText by remember(existing?.id) { mutableStateOf(existing?.reasoningEffort.orEmpty()) }
    var reasoningModeMenu by remember { mutableStateOf(false) }
    var reasoningEffortMenu by remember { mutableStateOf(false) }

    // 核心功能开关
    var toolCallEnabled by remember(existing?.id) {
        mutableStateOf(existing?.toolCallMode != "disabled")
    }
    var pureChatMode by remember(existing?.id) {
        mutableStateOf(existing?.pureChatMode ?: false)
    }
    var visionEnabled by remember(existing?.id) {
        mutableStateOf(existing?.visionEnabled ?: true)
    }

    // 自定义请求头
    var customHeaders by remember(existing?.id) { mutableStateOf(existing?.customHeaders.orEmpty()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                if (existing == null) "新增模型档案" else "编辑模型档案",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        }

        // 服务商预设选择
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
                    leadingIcon = {
                        top.wkbin.taixu.ui.components.ProviderBadge(
                            providerIdOrName = provider.id,
                            size = 24.dp,
                        )
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerMenu) },
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
                            onClick = {
                                providerId = option.id
                                url = option.baseUrl
                                model = option.recommendedModels.firstOrNull().orEmpty()
                                providerMenu = false
                                if (option.baseUrl.isNotBlank() && ProviderEndpointPolicy.isSafeBaseUrl(option.baseUrl)) {
                                    discover(option.id, option.baseUrl, key)
                                }
                            },
                        )
                    }
                }
            }
        }

        // 档案名称
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("档案名称") },
                placeholder = { Text(model.ifBlank { "My Model" }) },
                singleLine = true,
            )
        }

        // 接口 Base URL
        item {
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    if (ProviderEndpointPolicy.isSafeBaseUrl(it)) {
                        discover(providerId, it, key)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL（接口地址）") },
                placeholder = { Text("https://api.openai.com/v1") },
                trailingIcon = {
                    IconButton(
                        onClick = { discover(providerId, url, key) },
                        enabled = !discovering && url.isNotBlank(),
                    ) {
                        if (discovering) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                singleLine = true,
            )
        }

        // API Key
        item {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key（可选）") },
                placeholder = { Text("sk-...") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
        }

        // 模型 ID
        item {
            ExposedDropdownMenuBox(
                expanded = modelMenu,
                onExpandedChange = { modelMenu = !modelMenu },
            ) {
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth(),
                    label = { Text("模型 ID（可选择或输入）") },
                    placeholder = { Text("gpt-4o / deepseek-chat") },
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

        // 双列紧凑参数：Temperature 与 Max Tokens
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = temperatureText,
                    onValueChange = { temperatureText = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Temperature") },
                    placeholder = { Text("0.7") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = maxTokensText,
                    onValueChange = { maxTokensText = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Max Tokens") },
                    placeholder = { Text("8000") },
                    singleLine = true,
                )
            }
        }

        // 上下文 Token 上限
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = contextTokensText,
                    onValueChange = { contextTokensText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("上下文 Token 上限") },
                    placeholder = { Text("128000") },
                    singleLine = true,
                )
                Text(
                    text = "超出时自动压缩旧消息（滑动窗口+摘要记忆）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 功能开关组
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 1. 支持函数调用 (Tool Call)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "支持函数调用 (Tool Call)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                "使用 OpenAI 标准函数调用执行沙箱与扩展命令",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = toolCallEnabled,
                            onCheckedChange = { toolCallEnabled = it },
                        )
                    }
                }

                // 2. 不注入工具和提示词 (纯净排查模式)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "不注入工具和提示词",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                "关闭系统提示词和工具定义注入，仅发送用户消息（用于排查问题）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = pureChatMode,
                            onCheckedChange = { pureChatMode = it },
                        )
                    }
                }

                // 3. 支持识图 (Vision)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "支持识图",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                "开启后图片直接发送给 AI 识别；关闭后自动调用工具读取图片",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = visionEnabled,
                            onCheckedChange = { visionEnabled = it },
                        )
                    }
                }
            }
        }

        // 自定义请求头
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = customHeaders,
                    onValueChange = { customHeaders = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("自定义请求头（可选）") },
                    placeholder = { Text("HTTP-Referer: https://taixu.ai\nX-Title: TaiXu") },
                    minLines = 2,
                    maxLines = 4,
                )
                Text(
                    text = "每行一个请求头，格式 \"Key: Value\"，会追加到 API 请求中",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 推理深度设置
        item {
            ExposedDropdownMenuBox(
                expanded = reasoningModeMenu,
                onExpandedChange = { reasoningModeMenu = !reasoningModeMenu },
            ) {
                OutlinedTextField(
                    value = when (reasoningModeText) {
                        "enabled" -> "开启深度推理（更深入思考）"
                        else -> "跟随模型默认"
                    },
                    onValueChange = {},
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    readOnly = true,
                    label = { Text("推理思考模式") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(reasoningModeMenu) },
                )
                ExposedDropdownMenu(
                    expanded = reasoningModeMenu,
                    onDismissRequest = { reasoningModeMenu = false },
                ) {
                    DropdownMenuItem(text = { Text("跟随模型默认") }, onClick = {
                        reasoningModeText = "auto"
                        reasoningModeMenu = false
                    })
                    DropdownMenuItem(text = { Text("开启深度推理（更深入思考）") }, onClick = {
                        reasoningModeText = "enabled"
                        reasoningModeMenu = false
                    })
                }
            }
        }

        // 测试与刷新按钮
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
            val parsedContextTokens = contextTokensText.trim().toIntOrNull()
            val parsedTopP = topPText.trim().toFloatOrNull()
            val invalid = buildList {
                if (temperatureText.isNotBlank() && (parsedTemperature == null || parsedTemperature !in 0f..2f)) add("Temperature 需为 0.0 ~ 2.0 的数字")
                if (maxTokensText.isNotBlank() && (parsedMaxTokens == null || parsedMaxTokens <= 0)) add("Max Tokens 需为正整数")
                if (contextTokensText.isNotBlank() && (parsedContextTokens == null || parsedContextTokens <= 0)) add("上下文 Token 需为正整数")
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
                        reasoningModeText.takeIf { it != "auto" },
                        reasoningEffortText.ifBlank { null },
                        if (toolCallEnabled) "native" else "disabled",
                        parsedContextTokens,
                        customHeaders,
                        pureChatMode,
                        visionEnabled,
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
