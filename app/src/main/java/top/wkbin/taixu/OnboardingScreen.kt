package top.wkbin.taixu

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.core.model.RuntimeState
import top.wkbin.taixu.ui.components.TaiXuBrandBadge

private data class SetupOption(val id: String, val label: String)

private val distributionOptions = listOf(
    SetupOption("ubuntu", "Ubuntu 24.04 LTS"),
    SetupOption("debian", "Debian 12 Bookworm"),
    SetupOption("kali", "Kali Linux"),
    SetupOption("arch", "Arch Linux"),
    SetupOption("alpine", "Alpine Linux 3.19"),
    SetupOption("fedora", "Fedora 40"),
    SetupOption("almalinux", "AlmaLinux 9"),
    SetupOption("opensuse", "openSUSE Tumbleweed"),
)

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
        if (page == 0) SystemSetupPage(viewModel, Modifier.fillMaxSize().padding(padding))
        else ModelSetupPage(viewModel, Modifier.fillMaxSize().padding(padding))
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

@Composable
private fun ModelSetupPage(viewModel: OnboardingViewModel, modifier: Modifier) {
    val provider by viewModel.modelProvider.collectAsStateWithLifecycle()
    val model by viewModel.modelId.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val discovered by viewModel.discoveredModels.collectAsStateWithLifecycle()
    val discoveryError by viewModel.modelDiscoveryError.collectAsStateWithLifecycle()
    val providers = viewModel.providerCatalog
    val selectedProvider = providers.firstOrNull { it.name == provider } ?: providers.first()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Intro(
                title = "连接 AI 大模型",
                description = "配置你所喜爱的模型服务商与 API Key，开启智能结对编程。",
            )
        }
        item {
            SetupDropdown("服务商预设", selectedProvider.id, providers.map { SetupOption(it.id, it.name) }, true, viewModel::selectProvider)
        }
        item {
            SetupDropdown(
                "模型预设",
                model,
                (discovered + selectedProvider.recommendedModels).distinct().map { SetupOption(it, it) }.ifEmpty { listOf(SetupOption(model, model.ifBlank { "请先刷新或手动填写" })) },
                true,
                viewModel::setModelId,
            )
        }
        item { OutlinedTextField(model, viewModel::setModelId, Modifier.fillMaxWidth(), label = { Text("模型 ID（可手动输入）") }, singleLine = true) }
        item { OutlinedTextField(baseUrl, viewModel::setBaseUrl, Modifier.fillMaxWidth(), label = { Text("Base URL（可选）") }, singleLine = true) }
        item { OutlinedTextField(apiKey, viewModel::setApiKey, Modifier.fillMaxWidth(), label = { Text("API Key（可选）") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) }
        item { TextButton(onClick = viewModel::discoverModels, enabled = baseUrl.isNotBlank()) { Text("刷新在线模型列表", color = MaterialTheme.colorScheme.primary) } }
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
