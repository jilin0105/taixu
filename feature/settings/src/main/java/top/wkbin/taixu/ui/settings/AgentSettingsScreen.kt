package top.wkbin.taixu.ui.settings

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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.core.model.AgentPlugin
import top.wkbin.taixu.core.model.AgentSkill
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.SectionHeader

@Composable
fun AgentSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val thinkingExpanded by viewModel.thinkingExpanded.collectAsStateWithLifecycle()
    val thinkingLanguage by viewModel.thinkingLanguage.collectAsStateWithLifecycle()
    val customSystemPromptEnabled by viewModel.customSystemPromptEnabled.collectAsStateWithLifecycle()
    val customSystemPrompt by viewModel.customSystemPrompt.collectAsStateWithLifecycle()
    val compactionEnabled by viewModel.contextCompactionEnabled.collectAsStateWithLifecycle()
    val compactionThreshold by viewModel.contextCompactionThreshold.collectAsStateWithLifecycle()
    val maxToolRounds by viewModel.maxToolRounds.collectAsStateWithLifecycle()
    val autoWorkspaceCwd by viewModel.autoWorkspaceCwd.collectAsStateWithLifecycle()
    val destructiveGuardEnabled by viewModel.destructiveGuardEnabled.collectAsStateWithLifecycle()
    val maxToolsPerRound by viewModel.maxToolsPerRound.collectAsStateWithLifecycle()
    val maxConsecutiveFailures by viewModel.maxConsecutiveFailures.collectAsStateWithLifecycle()
    val contextBudgetTokens by viewModel.contextBudgetTokens.collectAsStateWithLifecycle()
    val skills by viewModel.allSkills.collectAsStateWithLifecycle()
    val plugins by viewModel.allPlugins.collectAsStateWithLifecycle()

    var showAddSkillDialog by remember { mutableStateOf(false) }
    var viewingSkillPrompt by remember { mutableStateOf<AgentSkill?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = "Agent 智能体管理与配置",
                statusText = "思考流、上下文压缩、Skill 与插件",
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
            // ---- 模块 1：模型思考与交互表现 ----
            item {
                SectionHeader(
                    title = "思考流与执行表现",
                    subtitle = "控制 DeepSeek 等推理模型的思考过程呈现与工具执行上限",
                )
            }
            item {
                AgentSettingsGroup {
                    AgentToggleRow(
                        icon = RuntimeIconName.Brain,
                        title = "默认展开模型思考过程",
                        subtitle = if (thinkingExpanded) "聊天界面中新生成的思考过程将默认展开呈现" else "思考过程（包括生成中内容）默认折叠，点击可展开查看",
                        checked = thinkingExpanded,
                        onCheckedChange = viewModel::setThinkingExpanded,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ThinkingLanguageSelectorRow(
                        currentLang = thinkingLanguage,
                        onLangChange = viewModel::setThinkingLanguage,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AgentToggleRow(
                        icon = RuntimeIconName.FolderOpen,
                        title = "自动注入关联工作区路径",
                        subtitle = "当会话关联了工作区时，执行 base 命令默认以该目录为工作路径 (cwd)",
                        checked = autoWorkspaceCwd,
                        onCheckedChange = viewModel::setAutoWorkspaceCwd,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    RoundsSliderRow(
                        currentValue = maxToolRounds,
                        onValueChange = viewModel::setMaxToolRounds,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AgentToggleRow(
                        icon = RuntimeIconName.Shield,
                        title = "危险命令闸门",
                        subtitle = if (destructiveGuardEnabled) "拦截 rm -rf /、mkfs、dd 写设备等不可逆高危命令" else "已关闭，base 工具将直接执行模型发出的任何 shell 命令",
                        checked = destructiveGuardEnabled,
                        onCheckedChange = viewModel::setDestructiveGuardEnabled,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ToolsPerRoundSliderRow(
                        currentValue = maxToolsPerRound,
                        onValueChange = viewModel::setMaxToolsPerRound,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ConsecutiveFailuresSliderRow(
                        currentValue = maxConsecutiveFailures,
                        onValueChange = viewModel::setMaxConsecutiveFailures,
                    )
                }
            }

            // ---- 模块 2：系统提示词与人设自定义 ----
            item {
                SectionHeader(
                    title = "系统提示词与人设自定义",
                    subtitle = "定制 Agent 初始人设、环境说明与动态宏变量注入",
                )
            }
            item {
                SystemPromptCustomCard(
                    enabled = customSystemPromptEnabled,
                    onEnabledChange = viewModel::setCustomSystemPromptEnabled,
                    prompt = customSystemPrompt,
                    onPromptChange = viewModel::setCustomSystemPrompt,
                )
            }

            // ---- 模块 2：上下文记忆与智能压缩 ----
            item {
                SectionHeader(
                    title = "上下文记忆与智能压缩",
                    subtitle = "优化长任务 Token 消耗，防止触及模型上下文窗口上限",
                )
            }
            item {
                AgentSettingsGroup {
                    AgentToggleRow(
                        icon = RuntimeIconName.Compress,
                        title = "开启上下文智能压缩 (Context Compaction)",
                        subtitle = "多轮工具调用超出阈值时，自动压缩历史中间工具输出日志，保留任务首尾与关键状态",
                        checked = compactionEnabled,
                        onCheckedChange = viewModel::setContextCompactionEnabled,
                    )
                    if (compactionEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ThresholdSliderRow(
                            currentThreshold = compactionThreshold,
                            onThresholdChange = viewModel::setContextCompactionThreshold,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ContextBudgetSliderRow(
                            currentValue = contextBudgetTokens,
                            onValueChange = viewModel::setContextBudgetTokens,
                        )
                    }
                }
            }

            // ---- 模块 3：Skill 专精技能库 ----
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader(
                        title = "Skill 专精技能库 (${skills.count { it.isEnabled }}/${skills.size} 已启用)",
                        subtitle = "向 Agent 系统提示词注入领域专业规范与操作指导",
                    )
                }
            }
            item {
                Button(
                    onClick = { showAddSkillDialog = true },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RuntimeIcon(RuntimeIconName.Sparkles, Modifier.size(16.dp), MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("新增自定义 Skill 技能", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                }
            }

            items(skills, key = { it.id }) { skill ->
                SkillCard(
                    skill = skill,
                    onToggle = { enabled -> viewModel.toggleSkill(skill.id, enabled) },
                    onViewPrompt = { viewingSkillPrompt = skill },
                    onDelete = if (!skill.isBuiltin) { { viewModel.deleteCustomSkill(skill.id) } } else null,
                )
            }

            // ---- 模块 4：Plugin 插件生态管理 ----
            item {
                SectionHeader(
                    title = "Plugin 插件生态管理 (${plugins.count { it.isEnabled }}/${plugins.size} 运行中)",
                    subtitle = "沙箱运行时探测、高危操作安全拦截与扩展能力",
                )
            }

            items(plugins, key = { it.id }) { plugin ->
                PluginCard(
                    plugin = plugin,
                    onToggle = { enabled -> viewModel.togglePlugin(plugin.id, enabled) },
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // 查看 Skill 完整提示词弹窗
    viewingSkillPrompt?.let { skill ->
        AlertDialog(
            onDismissRequest = { viewingSkillPrompt = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(skill.name, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(skill.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("注入系统的指导提示词 (System Prompt)：", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            skill.systemPrompt,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingSkillPrompt = null }) {
                    Text("关闭")
                }
            },
        )
    }

    // 新增自定义 Skill 弹窗
    if (showAddSkillDialog) {
        AddSkillDialog(
            onDismiss = { showAddSkillDialog = false },
            onConfirm = { name, desc, prompt, cmd ->
                viewModel.addCustomSkill(name, desc, prompt, cmd)
                showAddSkillDialog = false
            },
        )
    }
}

@Composable
private fun AgentSettingsGroup(content: @Composable () -> Unit) {
    RuntimeCard(
        Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun AgentToggleRow(
    icon: RuntimeIconName,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
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
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}



@Composable
private fun RoundsSliderRow(
    currentValue: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderVal by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("单回合最大工具轮次上限", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "${sliderVal.toInt()} 轮",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "防止复杂任务中模型陷入死循环；达到轮次后将输出总结并请用户分步进行",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderVal,
            onValueChange = { sliderVal = it },
            onValueChangeFinished = { onValueChange(sliderVal.toInt()) },
            valueRange = 10f..250f,
            steps = 23, // 10, 20, 30 ... 250
        )
    }
}

@Composable
private fun ThresholdSliderRow(
    currentThreshold: Int,
    onThresholdChange: (Int) -> Unit,
) {
    var sliderVal by remember(currentThreshold) { mutableFloatStateOf(currentThreshold.toFloat()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("压缩触发阈值（用户轮次）", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "${sliderVal.toInt()} 轮",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "当会话历史超过该轮数时，启动智能剪裁，最近 4 轮保持无损",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderVal,
            onValueChange = { sliderVal = it },
            onValueChangeFinished = { onThresholdChange(sliderVal.toInt()) },
            valueRange = 5f..40f,
            steps = 6,
        )
    }
}

@Composable
private fun ContextBudgetSliderRow(
    currentValue: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderVal by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("上下文 Token 预算上限", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "${sliderVal.toInt()} tok",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "模型未单独配置 contextTokens 时生效；长会话历史超出预算将自动折叠早期内容",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderVal,
            onValueChange = { sliderVal = it },
            onValueChangeFinished = { onValueChange(sliderVal.toInt()) },
            valueRange = 8000f..1000000f,
            steps = 48, // 步长约 2 万 tok
        )
    }
}

@Composable
private fun ToolsPerRoundSliderRow(
    currentValue: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderVal by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("单轮最大工具调用数", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "${sliderVal.toInt()} 个",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "防止模型一次性爆发大量工具调用耗尽上下文或失控循环",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderVal,
            onValueChange = { sliderVal = it },
            onValueChangeFinished = { onValueChange(sliderVal.toInt()) },
            valueRange = 1f..30f,
            steps = 29,
        )
    }
}

@Composable
private fun ConsecutiveFailuresSliderRow(
    currentValue: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderVal by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("连续失败熔断阈值", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "${sliderVal.toInt()} 轮",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "连续多轮工具调用全部失败时主动终止，避免陷入死循环空转",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderVal,
            onValueChange = { sliderVal = it },
            onValueChangeFinished = { onValueChange(sliderVal.toInt()) },
            valueRange = 2f..30f,
            steps = 28,
        )
    }
}

@Composable
private fun SkillCard(
    skill: AgentSkill,
    onToggle: (Boolean) -> Unit,
    onViewPrompt: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = if (skill.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(skill.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            skill.category,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val cmd = skill.triggerCommand
                    if (cmd != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                cmd,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Text(skill.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onViewPrompt, contentPadding = PaddingValues(0.dp)) {
                    Text("查看指导词 (Prompt)", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.weight(1f))
                if (skill.isImmutable) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            "系统核心常驻",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                } else {
                    if (onDelete != null) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            RuntimeIcon(RuntimeIconName.Trash, Modifier.size(16.dp), MaterialTheme.colorScheme.error)
                        }
                    }
                    Switch(
                        checked = skill.isEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginCard(
    plugin: AgentPlugin,
    onToggle: (Boolean) -> Unit,
) {
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = if (plugin.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(plugin.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("v${plugin.version}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("作者: ${plugin.author}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = plugin.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            Text(plugin.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (plugin.permissions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("权限:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    plugin.permissions.forEach { perm ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(perm, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSkillDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, systemPrompt: String, command: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var cmd by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增自定义 Skill 技能", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("技能名称（如: Rust 编译专家）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("简要描述") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = cmd,
                    onValueChange = { cmd = it },
                    label = { Text("触发指令（选填，如 /rust）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("系统提示词规则 (System Prompt)") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, desc, prompt, cmd) },
                enabled = name.isNotBlank() && prompt.isNotBlank(),
            ) {
                Text("添加并启用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ThinkingLanguageSelectorRow(
    currentLang: String,
    onLangChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RuntimeIcon(RuntimeIconName.Globe, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("思考与推理语言偏好 (Thinking Language)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = when (currentLang) {
                        "zh" -> "强约束模型思考过程全程使用中文（解决 DeepSeek/Claude 思考总跑英文的问题）"
                        "en" -> "强制模型思考过程全程使用英文 (English)"
                        else -> "由模型根据上下文或底层默认策略自主决定思考语言"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "zh" to "强制中文 (推荐)",
                "en" to "英文 (English)",
                "auto" to "自动 (Auto)",
            ).forEach { (lang, label) ->
                FilterChip(
                    selected = currentLang == lang,
                    onClick = { onLangChange(lang) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SystemPromptCustomCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    prompt: String,
    onPromptChange: (String) -> Unit,
) {
    var textBuffer by remember(prompt) { mutableStateOf(prompt) }

    val defaultPromptTemplate = """
You are a helpful and expert AI assistant called {{char}}, based on model {{model_name}}.

## System & Device Context
- Time: {{cur_datetime}}
- Locale: {{locale}}
- Timezone: {{timezone}}
- Device: {{device_info}}
- System: {{system_version}}
- Battery: {{battery_level}}

## Instructions
- Always think carefully before answering.
- Follow user instructions precisely and write clean code.
    """.trimIndent()

    val availableVariables = listOf(
        "{{cur_date}}" to "日期",
        "{{cur_time}}" to "时间",
        "{{cur_datetime}}" to "日期时间",
        "{{model_name}}" to "模型名称",
        "{{model_id}}" to "模型ID",
        "{{locale}}" to "语言环境",
        "{{timezone}}" to "时区",
        "{{device_info}}" to "设备信息",
        "{{system_version}}" to "系统版本",
        "{{battery_level}}" to "电池电量",
        "{{char}}" to "助手名称",
        "{{user}}" to "用户名称",
    )

    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    RuntimeIcon(RuntimeIconName.Prompt, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("自定义系统提示词 (System Prompt)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("覆盖全局默认 System Prompt，支持动态宏变量注入", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }

            if (enabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("提示词模板内容：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = {
                            textBuffer = defaultPromptTemplate
                            onPromptChange(defaultPromptTemplate)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(12.dp))
                            Text("重置模板", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                OutlinedTextField(
                    value = textBuffer,
                    onValueChange = {
                        textBuffer = it
                        onPromptChange(it)
                    },
                    placeholder = { Text("在此输入自定义系统提示词，支持 {{cur_datetime}} 等宏变量...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 10,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )

                Text("点击快捷插入动态宏变量：", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    availableVariables.forEach { (variable, label) ->
                        SuggestionChip(
                            onClick = {
                                val updated = if (textBuffer.isBlank()) variable else "$textBuffer $variable"
                                textBuffer = updated
                                onPromptChange(updated)
                            },
                            label = {
                                Text("$label: $variable", style = MaterialTheme.typography.labelSmall)
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            ),
                        )
                    }
                }
            }
        }
    }
}
