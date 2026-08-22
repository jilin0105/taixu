package top.wkbin.taixu

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.core.model.RuntimeState
import top.wkbin.taixu.core.tools.AgentProviderDefinition
import top.wkbin.taixu.runtime.DistributionCatalog
import top.wkbin.taixu.ui.components.ProviderBadge
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.TaiXuBrandBadge

private data class SetupOption(val id: String, val label: String)

private val distributionOptions = DistributionCatalog.supported.map { SetupOption(it.id, it.displayWithVersion) }

private val mirrorOptions = listOf(
    SetupOption("auto", "自动择优线路 (Recommended)"),
    SetupOption("official", "官方源 (Global CDN)"),
    SetupOption("china", "国内镜像加速线路"),
)

/**
 * 太墟 · 启程配置向导 (Onboarding Wizard)
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val page by viewModel.page.collectAsStateWithLifecycle()
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        when (page) {
            0 -> SystemSetupPage(viewModel, Modifier.fillMaxSize().padding(padding))
            1 -> PluginSetupPage(viewModel, Modifier.fillMaxSize().padding(padding))
            else -> ModelSetupPage(viewModel, Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun SystemSetupPage(viewModel: OnboardingViewModel, modifier: Modifier) {
    val distribution by viewModel.distribution.collectAsStateWithLifecycle()
    val mirror by viewModel.mirror.collectAsStateWithLifecycle()
    val state by viewModel.runtimeState.collectAsStateWithLifecycle()
    val installing = state is RuntimeState.Initializing

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Intro(
                title = "太墟 · 启程准备",
                description = "正在为你准备纯用户空间 Linux PRoot 沙箱引擎与代码工作区。",
            )
        }
        item {
            SetupDropdown(
                label = "Linux 发行版",
                selectedId = distribution,
                options = distributionOptions,
                enabled = !installing,
                onSelected = viewModel::selectDistribution,
            )
        }
        item {
            SetupDropdown(
                label = "镜像加速线路",
                selectedId = mirror,
                options = mirrorOptions,
                enabled = !installing,
                onSelected = viewModel::selectMirror,
            )
        }
        if (state is RuntimeState.Initializing) {
            val progress = state as RuntimeState.Initializing
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = progress.step,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${(progress.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        progress.detail?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        (state as? RuntimeState.Error)?.let { error ->
            item {
                Text(
                    error.throwable.message ?: "初始化失败，请检查网络后重试",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            Button(
                onClick = viewModel::install,
                enabled = !installing,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    if (state is RuntimeState.Error) "重试下载与就绪" else if (installing) "正在准备环境…" else "一键初始化沙箱",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (state is RuntimeState.Error) {
            item {
                TextButton(
                    onClick = viewModel::retryReady,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("已装过环境？点此重试就绪（不重新下载）", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/**
 * 首次开机向导 · 准备开发环境页（套件化聚合装配）
 */
@Composable
private fun PluginSetupPage(viewModel: OnboardingViewModel, modifier: Modifier) {
    val suites = viewModel.devSuites
    val selected by viewModel.selectedSuites.collectAsStateWithLifecycle()
    val isInstalling by viewModel.isInstallingPlugins.collectAsStateWithLifecycle()
    val progressMessage by viewModel.pluginInstallProgress.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        // 套件列表区域（独立滚动）
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Package,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text("准备开发环境", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "请选择需要准备的开发环境。下载时会自动选择连接更快的线路，只执行一次批量安全装配。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            // 安装进度卡片
            if (isInstalling) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = progressMessage ?: "正在批量装配所选开发环境...",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // 开发环境套件选择列表
            items(suites.size) { index ->
                val suite = suites[index]
                val isChecked = suite.id in selected

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = !isInstalling) { viewModel.toggleSuite(suite.id) },
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = isChecked,
                            onCheckedChange = { viewModel.toggleSuite(suite.id) },
                            enabled = !isInstalling,
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = suite.name,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = suite.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // 底部常驻操作区（固定在屏幕底端）
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = viewModel::installSelectedSuitesAndProceed,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = if (isInstalling) "已转入后台装配 · 直接进入太墟" else (if (selected.isNotEmpty()) "开始安装 (${selected.size})" else "下一步"),
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (!isInstalling) {
                    TextButton(
                        onClick = viewModel::skipSuites,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("暂时跳过", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelSetupPage(viewModel: OnboardingViewModel, modifier: Modifier) {
    val provider by viewModel.modelProvider.collectAsStateWithLifecycle()
    val model by viewModel.modelId.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val discovered by viewModel.discoveredModels.collectAsStateWithLifecycle()
    val discovering by viewModel.discoveringModels.collectAsStateWithLifecycle()
    val discoveryError by viewModel.modelDiscoveryError.collectAsStateWithLifecycle()
    val providers = viewModel.providerCatalog
    val selectedProvider = providers.firstOrNull { it.name == provider } ?: providers.first()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProviderBadge(providerIdOrName = selectedProvider.id, size = 52.dp)
                Text("连接 AI 大模型", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Text(
                    "配置你所喜爱的模型服务商与 API Key，开启智能结对编程。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        item {
            SetupProviderDropdown(
                selectedProvider = selectedProvider,
                providers = providers,
                enabled = true,
                onSelected = viewModel::selectProvider,
            )
        }
        item {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = viewModel::setBaseUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL（接口地址）") },
                placeholder = { Text("https://api.openai.com/v1") },
                trailingIcon = {
                    IconButton(
                        onClick = viewModel::discoverModels,
                        enabled = !discovering && baseUrl.isNotBlank(),
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
        item {
            OutlinedTextField(
                value = apiKey,
                onValueChange = viewModel::setApiKey,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key（可选）") },
                placeholder = { Text("sk-...") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
        }
        item {
            SetupDropdown(
                "模型选择 / 推荐预设",
                model,
                (discovered + selectedProvider.recommendedModels).distinct().map { SetupOption(it, it) }.ifEmpty { listOf(SetupOption(model, model.ifBlank { "请先刷新或手动填写" })) },
                true,
                viewModel::setModelId,
            )
        }
        item {
            OutlinedTextField(
                value = model,
                onValueChange = viewModel::setModelId,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("模型 ID（可手动输入）") },
                placeholder = { Text("gpt-4o / deepseek-chat") },
                singleLine = true,
            )
        }
        discoveryError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = viewModel::skipModel, modifier = Modifier.weight(1f)) { Text("稍后配置") }
                Button(
                    onClick = viewModel::saveModelAndFinish,
                    enabled = model.isNotBlank(),
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                ) {
                    Text("进入太墟", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Intro(title: String, description: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TaiXuBrandBadge(52.dp)
        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupProviderDropdown(
    selectedProvider: AgentProviderDefinition,
    providers: List<AgentProviderDefinition>,
    enabled: Boolean,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selectedProvider.name,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("服务商预设") },
            leadingIcon = {
                ProviderBadge(
                    providerIdOrName = selectedProvider.id,
                    size = 24.dp,
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(
                ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                enabled,
            ),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            providers.forEach { option ->
                DropdownMenuItem(
                    leadingIcon = {
                        ProviderBadge(
                            providerIdOrName = option.id,
                            size = 22.dp,
                        )
                    },
                    text = { Text(option.name) },
                    onClick = {
                        onSelected(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupDropdown(
    label: String,
    selectedId: String,
    options: List<SetupOption>,
    enabled: Boolean,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId } ?: options.first()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(
                ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                enabled,
            ),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
