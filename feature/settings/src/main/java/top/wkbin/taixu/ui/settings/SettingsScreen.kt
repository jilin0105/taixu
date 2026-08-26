package top.wkbin.taixu.ui.settings

import top.wkbin.taixu.ui.components.RuntimeAlertDialog

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.widget.Toast
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.FlowRow
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
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.HorizontalDivider
import top.wkbin.taixu.ui.components.RuntimeIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import top.wkbin.taixu.ui.components.RuntimeSwitch as Switch
import androidx.compose.material3.SwitchDefaults
import top.wkbin.taixu.ui.settings.LocalizedText as Text
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.model.ExecutionMode
import top.wkbin.taixu.core.tools.AgentProviderDefinition
import top.wkbin.taixu.core.tools.ProviderEndpointPolicy
import top.wkbin.taixu.runtime.privilege.PhantomProcessLimitState
import top.wkbin.taixu.runtime.privilege.PhantomProcessLimitStatus
import top.wkbin.taixu.ui.components.IconTile
import top.wkbin.taixu.ui.components.MainDestination
import top.wkbin.taixu.ui.components.RuntimeBottomBar
import top.wkbin.taixu.ui.components.liquidGlassContent
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeSwitch
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.SectionHeader
import top.wkbin.taixu.ui.theme.LocalLiquidGlassBackdrop

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
    val effectiveExecutionMode by viewModel.effectiveExecutionMode.collectAsStateWithLifecycle()
    val installedDistros by viewModel.installedDistros.collectAsStateWithLifecycle()
    val activeDistroId by viewModel.activeDistroId.collectAsStateWithLifecycle()
    val terminalFontSize by viewModel.terminalFontSize.collectAsStateWithLifecycle()

    val themeLabel = when (themeMode) {
        "light" -> "浅色"
        "dark" -> "曜石"
        else -> "跟随系统"
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val appVersionName = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        } catch (_: Exception) {
            null
        } ?: "unknown"
    }

    val glassBackdrop = LocalLiquidGlassBackdrop.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = "太墟 · 乾坤",
                statusText = "系统设置与控制中枢",
            )
        },
        bottomBar = {
            if (glassBackdrop == null) {
                RuntimeBottomBar(MainDestination.Settings, onNavigate)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassContent()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 104.dp),
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
                    badge = "${installedDistros.size} 套系统 · ${effectiveExecutionMode.shortLabel}",
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
                    badge = "v$appVersionName 稳定版",
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
    onOpenLocalLlm: () -> Unit,
    onOpenToolCenter: () -> Unit,
    onOpenAgentSettings: () -> Unit,
    onOpenMcpSettings: () -> Unit,
    onOpenQuickPhrases: () -> Unit,
    onOpenStats: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val skills by viewModel.allSkills.collectAsStateWithLifecycle()
    val phrases by viewModel.quickPhrases.collectAsStateWithLifecycle()

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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Cpu,
                        title = "本地 LLM",
                        subtitle = "导入或下载 GGUF，在 ARM64 设备端通过 llama.cpp 离线推理",
                        onClick = onOpenLocalLlm,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Chat,
                        title = "快捷短语与常用指令",
                        subtitle = "自定义智枢空白页快捷开始卡片与高频提示词模板",
                        value = "${phrases.count { it.isEnabled }} 条已启用",
                        onClick = onOpenQuickPhrases,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Speed,
                        title = "数据统计与用量分析",
                        subtitle = "Token 消耗、活跃度热力图、模型与话题排行",
                        onClick = onOpenStats,
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
    onOpenEnvironmentVariables: () -> Unit,
    onOpenSshSettings: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val executionMode by viewModel.executionMode.collectAsStateWithLifecycle()
    val effectiveExecutionMode by viewModel.effectiveExecutionMode.collectAsStateWithLifecycle()
    val privilegeState by viewModel.privilegeState.collectAsStateWithLifecycle()
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
        RuntimeAlertDialog(
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Key,
                        title = "环境变量",
                        subtitle = "为终端、Agent 和工具注入用户变量",
                        onClick = onOpenEnvironmentVariables,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Network,
                        title = "SSH 远程访问",
                        subtitle = "公钥认证 · 端口与局域网监听 · 随运行时启动",
                        onClick = onOpenSshSettings,
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
                        subtitle = if (executionMode != effectiveExecutionMode) {
                            "首选 ${executionMode.shortLabel} 暂不可用：${privilegeState.reason}"
                        } else {
                            "PRoot 用户态沙箱 · Shizuku · Root"
                        },
                        value = if (executionMode == effectiveExecutionMode) {
                            effectiveExecutionMode.shortLabel
                        } else {
                            "${effectiveExecutionMode.shortLabel}（已降级）"
                        },
                        onClick = { showExecutionModeDialog = true },
                    )
                }
            }
        }
    }
}

@Composable
fun EnvironmentVariableSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val entries by viewModel.environmentVariables.collectAsStateWithLifecycle()
    val values by viewModel.environmentValues.collectAsStateWithLifecycle()
    val effectiveEntries by viewModel.effectiveEnvironment.collectAsStateWithLifecycle()
    val privacyMode by viewModel.environmentPrivacyMode.collectAsStateWithLifecycle()
    val loading by viewModel.environmentLoading.collectAsStateWithLifecycle()
    val error by viewModel.environmentError.collectAsStateWithLifecycle()
    val activeDistroId by viewModel.activeDistroId.collectAsStateWithLifecycle()
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<top.wkbin.taixu.core.model.EnvironmentVariable?>(null) }
    var editorInitialKey by remember { mutableStateOf("") }
    var editorInitialValue by remember { mutableStateOf("") }
    var showDelete by remember { mutableStateOf<top.wkbin.taixu.core.model.EnvironmentVariable?>(null) }

    fun openEnvironmentEditor(
        entry: top.wkbin.taixu.core.model.EnvironmentVariable?,
        key: String,
        value: String,
    ) {
        viewModel.clearEnvironmentError()
        editing = entry
        editorInitialKey = key
        editorInitialValue = value
        showEditor = true
    }

    if (showEditor) {
        EnvironmentVariableEditor(
            entry = editing,
            initialKey = editorInitialKey,
            currentValue = editorInitialValue,
            error = error,
            onDismiss = {
                viewModel.clearEnvironmentError()
                showEditor = false
                editing = null
            },
            onSave = { key, value, note ->
                if (editing == null) viewModel.addEnvironmentVariable(key, value, note) { if (it) { showEditor = false } }
                else viewModel.updateEnvironmentVariable(editing!!.id, key, value, note) { if (it) { showEditor = false; editing = null } }
            },
        )
    }
    showDelete?.let { entry ->
        RuntimeAlertDialog(
            onDismissRequest = { showDelete = null },
            title = { Text("删除环境变量") },
            text = { Text("确定删除 ${entry.key}？") },
            confirmButton = { TextButton(onClick = { viewModel.deleteEnvironmentVariable(entry.id); showDelete = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { showDelete = null }) { Text("取消") } },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                "环境变量",
                onBack,
                actions = {
                    IconButton(onClick = { viewModel.refreshEnvironmentVariables() }, enabled = !loading && runtimeState is top.wkbin.taixu.core.model.RuntimeState.Ready) {
                        RuntimeIcon(RuntimeIconName.Refresh)
                    }
                    IconButton(onClick = { openEnvironmentEditor(null, "", "") }, enabled = !loading && runtimeState is top.wkbin.taixu.core.model.RuntimeState.Ready) {
                        RuntimeIcon(RuntimeIconName.Plus)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Shield,
                        title = "Agent 隐私遮盖",
                        subtitle = "仅遮盖发送给 Agent 和写入对话的变量值；本页仍显示明文",
                        trailing = { Switch(checked = privacyMode, onCheckedChange = viewModel::setEnvironmentPrivacyMode) },
                    )
                }
            }
            item {
                RuntimeCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                    borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f),
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        RuntimeIcon(RuntimeIconName.Alert, tint = MaterialTheme.colorScheme.tertiary)
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                "Warning · 修改环境变量可能导致运行异常",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                "错误覆盖 JAVA_HOME、GRADLE_HOME、LANG 等变量，可能使终端、构建工具或插件无法启动。请只修改你明确了解用途的变量；TaiXu 运行时关键变量会被强制保护。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "用户变量保存在 $activeDistroId 的 Linux /etc/profile.d 中，并在下一次命令或终端会话启动时生效。值以受限文件权限保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let { message ->
                item {
                    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            message,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            item {
                SectionHeader(
                    title = "用户变量",
                    subtitle = "可编辑的 TaiXu 用户配置",
                    trailing = { Text(entries.size.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
            if (loading && entries.isEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            } else if (entries.isEmpty()) {
                item { RuntimeCard(modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("暂无用户变量", style = MaterialTheme.typography.titleMedium); Text("点击右上角 + 添加", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            } else {
                items(entries, key = { it.id }) { entry ->
                    RuntimeCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { openEnvironmentEditor(entry, entry.key, values[entry.key].orEmpty()) },
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.key, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                                if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    values[entry.key].orEmpty().ifEmpty { "（空值）" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            IconButton(onClick = { openEnvironmentEditor(entry, entry.key, values[entry.key].orEmpty()) }) { RuntimeIcon(RuntimeIconName.Edit) }
                            IconButton(onClick = { showDelete = entry }) { RuntimeIcon(RuntimeIconName.Trash, tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            item {
                SectionHeader(
                    title = "当前有效环境",
                    subtitle = "新命令实际可见的变量与值",
                    trailing = { Text(effectiveEntries.size.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
            if (loading && effectiveEntries.isEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            } else if (effectiveEntries.isEmpty()) {
                item {
                    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
                        Text("暂无可读取的运行时环境", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                item {
                    SettingsGroup {
                        effectiveEntries.forEachIndexed { index, entry ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            val managedEntry = entries.firstOrNull { it.key == entry.key }
                            SettingsRow(
                                icon = RuntimeIconName.Key,
                                title = entry.key,
                                subtitle = entry.value.ifEmpty { "（空值）" },
                                onClick = {
                                    if (managedEntry == null) {
                                        openEnvironmentEditor(null, entry.key, entry.value)
                                    } else {
                                        openEnvironmentEditor(managedEntry, managedEntry.key, values[managedEntry.key].orEmpty())
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvironmentVariableEditor(
    entry: top.wkbin.taixu.core.model.EnvironmentVariable?,
    initialKey: String,
    currentValue: String,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var key by remember(entry?.id, initialKey) { mutableStateOf(initialKey) }
    var value by remember(entry?.id, initialKey) { mutableStateOf(currentValue) }
    var note by remember(entry) { mutableStateOf(entry?.note.orEmpty()) }
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry != null) "编辑环境变量" else if (initialKey.isNotBlank()) "配置环境变量" else "添加环境变量") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    RuntimeIcon(RuntimeIconName.Alert, Modifier.size(18.dp), MaterialTheme.colorScheme.tertiary)
                    Text(
                        if (entry == null && initialKey.isNotBlank()) {
                            "这会在用户配置中覆盖 Linux 当前值。错误配置可能导致相关命令无法运行。"
                        } else {
                            "修改后会影响新启动的命令与终端，请确认变量名称和值正确。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                OutlinedTextField(value = key, onValueChange = { key = it.uppercase() }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("值") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注（可选）") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key, value, note) }, enabled = key.isNotBlank() && (entry != null || value.isNotEmpty())) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 二级子页 3：系统保活与开发者诊断
 */
@Composable
fun SystemDevSettingsScreen(
    onBack: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenCustomIteration: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val developer by viewModel.developerMode.collectAsStateWithLifecycle()
    val qemuCompatibilityEnabled by viewModel.qemuCompatibilityEnabled.collectAsStateWithLifecycle()
    val qemuCompatibilityReady by viewModel.qemuCompatibilityReady.collectAsStateWithLifecycle()
    val qemuCompatibilityMessage by viewModel.qemuCompatibilityMessage.collectAsStateWithLifecycle()
    val phantomStatus by viewModel.phantomProcessStatus.collectAsStateWithLifecycle()
    val phantomBusy by viewModel.phantomProcessBusy.collectAsStateWithLifecycle()
    val phantomMessage by viewModel.phantomProcessMessage.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showBatteryDialog by remember { mutableStateOf(false) }
    var showPhantomProcessDialog by remember { mutableStateOf(false) }
    var batteryExempted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    LaunchedEffect(Unit) { viewModel.refreshPhantomProcessLimit() }

    if (showBatteryDialog) {
        BatteryOptimizationDialog(
            exempted = batteryExempted,
            onRefresh = { batteryExempted = isIgnoringBatteryOptimizations(context) },
            onDismiss = { showBatteryDialog = false },
        )
    }
    if (showPhantomProcessDialog) {
        PhantomProcessLimitDialog(
            status = phantomStatus,
            busy = phantomBusy,
            message = phantomMessage,
            adbCommand = viewModel.phantomProcessAdbCommand,
            onRefresh = viewModel::refreshPhantomProcessLimit,
            onRemove = viewModel::removePhantomProcessLimit,
            onDismiss = {
                viewModel.clearPhantomProcessMessage()
                showPhantomProcessDialog = false
            },
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Speed,
                        title = "Android 12 子进程限制",
                        subtitle = "解除 Phantom Process 最多 32 个的后台限制",
                        value = when {
                            phantomBusy && phantomStatus == null -> "检测中"
                            phantomStatus?.state == PhantomProcessLimitState.REMOVED -> "已解除"
                            phantomStatus?.state == PhantomProcessLimitState.ACTIVE -> "未解除"
                            phantomStatus?.state == PhantomProcessLimitState.UNSUPPORTED -> "无需处理"
                            else -> "待检测"
                        },
                        onClick = {
                            showPhantomProcessDialog = true
                            viewModel.refreshPhantomProcessLimit()
                        },
                    )
                }
            }

            item {
                Text(
                    text = "太墟自定义迭代与共建",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Code,
                        title = "自定义迭代（TaiXuDev）",
                        subtitle = "在手机沙盒中调用 AI 开发太墟自身并云端构建 APK",
                        onClick = onOpenCustomIteration,
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ToggleRow(
                        icon = RuntimeIconName.Cpu,
                        title = "QEMU x86_64 兼容模式",
                        subtitle = if (qemuCompatibilityReady) {
                            "允许明确请求的会话使用 QEMU x86_64 user-mode；ARM64 会话不受影响"
                        } else {
                            "未检测到 QEMU x86_64 兼容环境，请先在插件中心安装 qemu-x86-64-compat 插件"
                        },
                        checked = qemuCompatibilityEnabled && qemuCompatibilityReady,
                        enabled = qemuCompatibilityReady,
                        change = viewModel::setQemuCompatibilityEnabled,
                    )
                    Text(
                        text = qemuCompatibilityMessage ?: if (qemuCompatibilityEnabled) {
                            "已开启。兼容插件只提供 ARM64 QEMU user-mode 与最小 x86_64 RootFS。"
                        } else {
                            "默认关闭，不会下载或使用 x86_64 工具。开启后仅对明确选择兼容环境的第三方项目生效，不会改变 APK 的 arm64-v8a 默认 ABI。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (qemuCompatibilityReady) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
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

    var initialApiKey by remember(modelId) { mutableStateOf("") }
    LaunchedEffect(modelId, existing?.secretRef) {
        val secretRef = existing?.secretRef
        if (!secretRef.isNullOrBlank()) {
            initialApiKey = viewModel.readModelApiKey(secretRef)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar(if (existing == null) "新增模型" else "编辑模型", onBack) },
    ) { padding ->
        ModelEditor(
            modifier = Modifier.padding(padding),
            modelId = modelId,
            existing = existing,
            initialApiKey = initialApiKey,
            providers = viewModel.providerCatalog,
            discovered = discovered,
            discovering = discovering,
            error = discoveryError,
            testing = testing,
            result = testResult,
            discover = { provider, url, key -> viewModel.discoverModels(provider, url, key) },
            test = viewModel::testConnection,
            save = { name, provider, modelsList, url, key, rpmLimit, temperature, maxTokens, topP, reasoningMode, reasoningEffort, toolCallMode, contextTokens, customHeaders, pureChatMode, visionEnabled ->
                viewModel.saveModels(
                    id = modelId,
                    models = modelsList,
                    name = name,
                    provider = provider,
                    baseUrl = url,
                    apiKey = key,
                    requestsPerMinutePerKey = rpmLimit,
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
    val currentVersion = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        } catch (_: Exception) {
            null
        } ?: "unknown"
    }

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
                RuntimeAlertDialog(
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
            RuntimeAlertDialog(
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
                        value = if (updateCheckState is top.wkbin.taixu.core.model.UpdateCheckState.Checking) "检查中…" else "v$currentVersion",
                        onClick = { viewModel.checkForUpdates(currentVersion) },
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
    RuntimeAlertDialog(
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

    RuntimeAlertDialog(
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

@Composable
private fun PhantomProcessLimitDialog(
    status: PhantomProcessLimitStatus?,
    busy: Boolean,
    message: String?,
    adbCommand: String,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onRefresh() }

    val state = status?.state
    val statusText = when (state) {
        PhantomProcessLimitState.REMOVED -> "已解除限制"
        PhantomProcessLimitState.ACTIVE -> "限制仍生效"
        PhantomProcessLimitState.UNSUPPORTED -> "当前系统无需处理"
        PhantomProcessLimitState.UNAVAILABLE -> "暂时无法检测"
        null -> if (busy) "正在检测" else "尚未检测"
    }
    val healthy = state == PhantomProcessLimitState.REMOVED || state == PhantomProcessLimitState.UNSUPPORTED
    val statusContainer = if (healthy) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val statusContent = if (healthy) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeIcon(RuntimeIconName.Speed, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Android 12 子进程限制", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(shape = RoundedCornerShape(6.dp), color = statusContainer) {
                        Text(
                            statusText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = statusContent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !busy) {
                        RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Text(
                    status?.details ?: "读取系统实际配置，确认幽灵进程限制是否仍在生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state == PhantomProcessLimitState.REMOVED || state == PhantomProcessLimitState.ACTIVE) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                "最大幽灵进程数：${status.maxPhantomProcesses ?: "系统默认（通常为 32）"}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                "幽灵进程监控：${when (status.monitoringEnabled) { true -> "开启"; false -> "关闭"; null -> "系统默认（开启）" }}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                Text(
                    "Android 12+ 会监控应用派生的子进程，超过系统上限后可能终止 PRoot、编译器或 Agent 任务。这里解除的是子进程限制，不是 Java/Kotlin 线程数。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = onRemove,
                    enabled = !busy && state != PhantomProcessLimitState.REMOVED && state != PhantomProcessLimitState.UNSUPPORTED,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在处理")
                    } else {
                        Text("使用 Shizuku / Root 一键解除")
                    }
                }

                Text(
                    "也可以在已连接手机的电脑终端执行：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            adbCommand,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Android 12 子进程限制命令", adbCommand))
                                Toast.makeText(context, "命令已复制", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            RuntimeIcon(RuntimeIconName.Copy, Modifier.size(15.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("复制命令")
                        }
                    }
                }

                if (!message.isNullOrBlank()) {
                    Text(
                        message,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state == PhantomProcessLimitState.REMOVED) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
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
    RuntimeAlertDialog(
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
    val appVersion = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        } catch (_: Exception) {
            null
        } ?: "unknown"
    }
    RuntimeAlertDialog(
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
                Text("版本: v$appVersion (Material 3 Expressive)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
    RuntimeAlertDialog(
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
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            )
                        } else {
                            LinearProgressIndicator(
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
            // 紧凑小圆角框（对齐首页 DoctorItemRow 的 10dp 风格）：
            // 三行结构——标题行内联操作、摘要行、BaseURL 行，整体高度比
            // RuntimeCard 版本矮近一半。
            val modelCardShape = RoundedCornerShape(10.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(modelCardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(
                        width = 1.dp,
                        color = if (model.isActive) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        },
                        shape = modelCardShape,
                    )
                    .clickable { edit(model) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    top.wkbin.taixu.ui.components.ProviderBadge(
                        providerIdOrName = model.provider,
                        size = 22.dp,
                    )
                    Text(
                        model.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (model.isActive) {
                        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                            Text("当前激活", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    } else {
                        TextButton(
                            onClick = { activate(model.id) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("设为激活", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    IconButton(onClick = { delete(model.id) }, modifier = Modifier.size(30.dp)) {
                        RuntimeIcon(RuntimeIconName.Trash, Modifier.size(16.dp), MaterialTheme.colorScheme.error)
                    }
                }
                val modelList = remember(model.model) {
                    model.model.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                }
                val modelDisplay = if (modelList.size <= 3) {
                    modelList.joinToString(", ")
                } else {
                    "${modelList.take(3).joinToString(", ")} 等 ${modelList.size} 个模型"
                }
                Text(
                    text = "模型: $modelDisplay",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val metadataSummary = buildList {
                    if (model.baseUrl.isNotBlank()) add(model.baseUrl)
                    if (model.apiKeyCount > 0) add("${model.apiKeyCount} Key")
                    if (model.requestsPerMinutePerKey > 0) add("${model.requestsPerMinutePerKey} RPM/Key")
                }.joinToString(" • ")
                Text(
                    text = metadataSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    trailing: @Composable RowScope.() -> Unit = {},
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
        trailing()
    }
}

@Composable
internal fun ToggleRow(
    icon: RuntimeIconName,
    title: String,
    subtitle: String,
    checked: Boolean,
    change: (Boolean) -> Unit,
    enabled: Boolean = true,
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
        RuntimeSwitch(
            checked = checked,
            onCheckedChange = change,
            enabled = enabled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelEditor(
    modifier: Modifier = Modifier,
    modelId: String?,
    existing: top.wkbin.taixu.core.database.AiModelEntity?,
    initialApiKey: String = "",
    providers: List<AgentProviderDefinition>,
    discovered: List<String>,
    discovering: Boolean,
    error: String?,
    testing: Boolean,
    result: String?,
    discover: (String, String, String) -> Unit,
    test: (String, String, String) -> Unit,
    save: (String, String, List<String>, String, String, Int, Float?, Int?, Float?, String?, String?, String?, Int?, String, Boolean, Boolean) -> Unit,
) {
    var providerId by remember(modelId) {
        mutableStateOf(providers.firstOrNull { it.name == existing?.provider }?.id ?: providers.first().id)
    }
    val provider = providers.first { it.id == providerId }
    var providerMenu by remember { mutableStateOf(false) }
    var name by remember(modelId) { mutableStateOf(existing?.name.orEmpty()) }
    val existingModelList = remember(existing?.model) {
        existing?.model?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()
    }
    var selectedModels by remember(modelId, providerId) {
        mutableStateOf(
            if (existing != null) {
                existingModelList
            } else {
                provider.recommendedModels.take(1).toSet()
            }
        )
    }
    var customModelInput by remember(modelId, providerId) { mutableStateOf("") }
    var url by remember(modelId) { mutableStateOf(existing?.baseUrl ?: provider.baseUrl) }
    var keyList by remember(modelId, initialApiKey) {
        mutableStateOf(
            initialApiKey.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
                .ifEmpty { listOf("") }
        )
    }
    var revealedKeyIndices by remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(initialApiKey) {
        if (keyList.all { it.isBlank() } && initialApiKey.isNotBlank()) {
            keyList = initialApiKey.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
                .ifEmpty { listOf("") }
        }
    }

    val combinedKey = remember(keyList) {
        keyList.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }

    val candidateModels = remember(discovered, provider) {
        (discovered + provider.recommendedModels).distinct().filter { it.isNotBlank() }
    }

    var advancedExpanded by remember(modelId) { mutableStateOf(false) }
    var rpmLimitText by remember(modelId) {
        mutableStateOf(existing?.requestsPerMinutePerKey?.takeIf { it > 0 }?.toString().orEmpty())
    }

    // 推理与上下文参数
    var temperatureText by remember(modelId) { mutableStateOf(existing?.temperature?.toString().orEmpty()) }
    var maxTokensText by remember(modelId) { mutableStateOf(existing?.maxTokens?.toString().orEmpty()) }
    var contextTokensText by remember(modelId) { mutableStateOf(existing?.contextTokens?.toString().orEmpty()) }
    var topPText by remember(modelId) { mutableStateOf(existing?.topP?.toString().orEmpty()) }

    // 推理开关/强度（"auto" = 跟随模型默认）
    var reasoningModeText by remember(modelId) { mutableStateOf(existing?.reasoningMode ?: "auto") }
    var reasoningEffortText by remember(modelId) { mutableStateOf(existing?.reasoningEffort.orEmpty()) }
    var reasoningModeMenu by remember { mutableStateOf(false) }

    // 核心功能开关
    var toolCallEnabled by remember(modelId) {
        mutableStateOf(existing?.toolCallMode != "disabled")
    }
    var pureChatMode by remember(modelId) {
        mutableStateOf(existing?.pureChatMode ?: false)
    }
    var visionEnabled by remember(modelId) {
        mutableStateOf(existing?.visionEnabled ?: true)
    }

    // 自定义请求头
    var customHeaders by remember(modelId) { mutableStateOf(existing?.customHeaders.orEmpty()) }

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

        // 服务商预设选择（切换预设不发起自动请求）
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
                                if (existing == null) {
                                    selectedModels = option.recommendedModels.take(1).toSet()
                                    customModelInput = ""
                                } else {
                                    customModelInput = option.recommendedModels.firstOrNull().orEmpty()
                                }
                                providerMenu = false
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
                label = { Text("档案名称（可选，留空按模型名命名）") },
                placeholder = { Text(if (selectedModels.isNotEmpty()) selectedModels.first() else "My Model") },
                singleLine = true,
            )
        }

        // 接口 Base URL
        item {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL（接口地址）") },
                placeholder = { Text("https://api.openai.com/v1") },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            discover(providerId, url, combinedKey)
                        },
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

        // API Key 池
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "API Key 池",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${keyList.count { it.isNotBlank() }} 个已配置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                keyList.forEachIndexed { index, currentKey ->
                    var isFocused by remember { mutableStateOf(false) }
                    val isRevealed = revealedKeyIndices.contains(index)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = currentKey,
                            onValueChange = { newVal ->
                                keyList = keyList.toMutableList().also { it[index] = newVal }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isFocused = it.isFocused },
                            label = { Text("API Key ${if (keyList.size > 1) "#${index + 1}" else ""}") },
                            placeholder = { Text("sk-...") },
                            singleLine = true,
                            visualTransformation = if (isRevealed) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                if (currentKey.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            revealedKeyIndices = if (isRevealed) {
                                                revealedKeyIndices - index
                                            } else {
                                                revealedKeyIndices + index
                                            }
                                        },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        RuntimeIcon(
                                            name = if (isRevealed) RuntimeIconName.Brain else RuntimeIconName.Key,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        )
                                    }
                                }
                            },
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (index == keyList.size - 1) {
                                FilledTonalIconButton(
                                    onClick = { keyList = keyList + "" },
                                    modifier = Modifier.size(40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    RuntimeIcon(
                                        name = RuntimeIconName.Plus,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            if (keyList.size > 1) {
                                IconButton(
                                    onClick = {
                                        keyList = keyList.toMutableList().also { it.removeAt(index) }
                                        revealedKeyIndices = revealedKeyIndices.mapNotNull {
                                            when {
                                                it < index -> it
                                                it > index -> it - 1
                                                else -> null
                                            }
                                        }.toSet()
                                    },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    RuntimeIcon(
                                        name = RuntimeIconName.Close,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "同一接口地址的多个 Key 将按请求自动轮询并在 429 时自动切换",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 模型选择区域（展开点击即勾选多选）
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = customModelInput,
                    onValueChange = { customModelInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("自定义模型 ID（可选，输入额外模型）") },
                    placeholder = { Text("例如：gpt-4.5-preview / claude-3-7-sonnet") },
                    singleLine = true,
                )

                if (candidateModels.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "候选模型（展开点击即可勾选多个）",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val totalSelectedCount = (selectedModels + customModelInput.split(",").map { it.trim() }).filter { it.isNotBlank() }.distinct().size
                        if (totalSelectedCount > 0) {
                            Text(
                                text = "已选择 $totalSelectedCount 个模型",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        candidateModels.forEach { option ->
                            val isSelected = selectedModels.contains(option)
                            Surface(
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                ),
                                modifier = Modifier.clickable {
                                    selectedModels = if (isSelected) {
                                        selectedModels - option
                                    } else {
                                        selectedModels + option
                                    }
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (isSelected) {
                                        RuntimeIcon(
                                            name = RuntimeIconName.Check,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            RuntimeCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                onClick = { advancedExpanded = !advancedExpanded },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "高级设置",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            "采样、上下文、推理、工具与请求头",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RuntimeIcon(
                        name = RuntimeIconName.ChevronDown,
                        modifier = Modifier.size(20.dp).rotate(if (advancedExpanded) 180f else 0f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (advancedExpanded) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = rpmLimitText,
                        onValueChange = { rpmLimitText = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("单 Key 每分钟请求上限") },
                        placeholder = { Text("8") },
                        singleLine = true,
                    )
                    Text(
                        text = "0 或留空表示不限制；达到上限时优先轮换其他 Key",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                                    "直接向模型发送图片；关闭后由工具读取图片内容",
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
                        label = { Text("自定义请求头（多行 Key: Value）") },
                        placeholder = { Text("HTTP-Referer: https://taixu.app\nX-Title: TaiXu") },
                        minLines = 2,
                        maxLines = 4,
                    )
                    Text(
                        text = "每行一个 Header，格式为 Header-Name: Header-Value",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 推理思考模式
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
        }

        // 测试与刷新按钮
        item {
            val testModelTarget = (if (existing != null) customModelInput.trim() else (selectedModels.firstOrNull() ?: customModelInput.trim())).ifBlank { provider.recommendedModels.firstOrNull().orEmpty() }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        discover(providerId, url, combinedKey)
                    },
                    enabled = !discovering && url.isNotBlank(),
                ) {
                    Text(if (discovering) "刷新中…" else "刷新在线模型")
                }
                OutlinedButton(
                    onClick = { test(url, testModelTarget, combinedKey) },
                    enabled = !testing && testModelTarget.isNotBlank() && url.isNotBlank(),
                ) {
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
            val effectiveModels = remember(selectedModels, customModelInput) {
                val customList = customModelInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                (selectedModels + customList).filter { it.isNotBlank() }.distinct()
            }
            val parsedTemperature = temperatureText.trim().toFloatOrNull()
            val parsedMaxTokens = maxTokensText.trim().toIntOrNull()
            val parsedContextTokens = contextTokensText.trim().toIntOrNull()
            val parsedTopP = topPText.trim().toFloatOrNull()
            val parsedRpmLimit = rpmLimitText.trim().toIntOrNull() ?: 0
            val invalid = buildList {
                if (temperatureText.isNotBlank() && (parsedTemperature == null || parsedTemperature !in 0f..2f)) add("Temperature 需为 0.0 ~ 2.0 的数字")
                if (maxTokensText.isNotBlank() && (parsedMaxTokens == null || parsedMaxTokens <= 0)) add("Max Tokens 需为正整数")
                if (contextTokensText.isNotBlank() && (parsedContextTokens == null || parsedContextTokens <= 0)) add("上下文 Token 需为正整数")
                if (topPText.isNotBlank() && (parsedTopP == null || parsedTopP !in 0f..1f)) add("Top P 需为 0.0 ~ 1.0 的数字")
                if (rpmLimitText.isNotBlank() && rpmLimitText.toIntOrNull() == null) add("单 Key RPM 需为非负整数")
            }.joinToString("；").ifBlank { null }
            invalid?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            val buttonText = if (effectiveModels.size > 1) {
                "保存已勾选的 ${effectiveModels.size} 个模型"
            } else {
                "保存模型配置"
            }

            Button(
                onClick = {
                    save(
                        name.trim(),
                        provider.name,
                        effectiveModels,
                        url.trim(),
                        combinedKey,
                        parsedRpmLimit,
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
                enabled = effectiveModels.isNotEmpty() && url.isNotBlank() && invalid == null,
            ) {
                Text(buttonText, fontWeight = FontWeight.Bold)
            }
        }
    }
}
