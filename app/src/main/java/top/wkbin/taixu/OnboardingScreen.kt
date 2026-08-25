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
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import top.wkbin.taixu.ui.components.RuntimeIconButton as IconButton
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

/**
 * 太墟 · 启程配置向导 (Onboarding Wizard)
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val page by viewModel.page.collectAsStateWithLifecycle()
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        when (page) {
            0 -> SystemSetupPage(viewModel, Modifier.fillMaxSize().padding(padding))
            else -> ModelSetupPage(viewModel, Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun SystemSetupPage(viewModel: OnboardingViewModel, modifier: Modifier) {
    val distribution by viewModel.distribution.collectAsStateWithLifecycle()
    val mirror by viewModel.mirror.collectAsStateWithLifecycle()
    val state by viewModel.runtimeState.collectAsStateWithLifecycle()
    val importing by viewModel.isImporting.collectAsStateWithLifecycle()
    val importError by viewModel.importError.collectAsStateWithLifecycle()
    val installing = state is RuntimeState.Initializing || importing
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importArchive) }
    val mirrorOptions = listOf(
        SetupOption("auto", stringResource(R.string.taixu_onboarding_mirror_auto)),
        SetupOption("official", stringResource(R.string.taixu_onboarding_mirror_official)),
        SetupOption("china", stringResource(R.string.taixu_onboarding_mirror_china)),
    )

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Intro(
                title = stringResource(R.string.taixu_onboarding_title),
                description = stringResource(R.string.taixu_onboarding_description),
            )
        }
        item {
            SetupDropdown(
                label = stringResource(R.string.taixu_onboarding_distribution),
                selectedId = distribution,
                options = distributionOptions,
                enabled = !installing,
                onSelected = viewModel::selectDistribution,
            )
        }
        item {
            SetupDropdown(
                label = stringResource(R.string.taixu_onboarding_mirror),
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
                                overflow = TextOverflow.Ellipsis,
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
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        (state as? RuntimeState.Error)?.let { error ->
            item {
                Text(
                    error.throwable.message ?: stringResource(R.string.taixu_onboarding_initialization_failed),
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
                    stringResource(
                        if (state is RuntimeState.Error) R.string.taixu_onboarding_retry
                        else if (installing) R.string.taixu_onboarding_preparing
                        else R.string.taixu_onboarding_initialize,
                    ),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        importError?.let { error ->
            item {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            TextButton(
                onClick = { importLauncher.launch(arrayOf("application/gzip", "application/x-gzip", "application/x-tar", "application/x-xz", "application/octet-stream")) },
                enabled = !installing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.taixu_onboarding_import))
            }
        }
        item {
            Text(
                stringResource(R.string.taixu_onboarding_import_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state is RuntimeState.Error) {
            item {
                TextButton(
                    onClick = viewModel::retryReady,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.taixu_onboarding_recheck), color = MaterialTheme.colorScheme.primary)
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
                Text(stringResource(R.string.taixu_onboarding_connect_model), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Text(
                    stringResource(R.string.taixu_onboarding_connect_model_description),
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
                label = { Text(stringResource(R.string.taixu_onboarding_base_url)) },
                placeholder = { Text("https://api.openai.com/v1") },
                shape = RoundedCornerShape(10.dp),
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
                label = { Text(stringResource(R.string.taixu_onboarding_api_key)) },
                placeholder = { Text("sk-...") },
                shape = RoundedCornerShape(10.dp),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
        }
        item {
            SetupDropdown(
                stringResource(R.string.taixu_onboarding_model_selection),
                model,
                (discovered + selectedProvider.recommendedModels).distinct().map { SetupOption(it, it) }.ifEmpty { listOf(SetupOption(model, model.ifBlank { stringResource(R.string.taixu_onboarding_model_empty) })) },
                true,
                viewModel::setModelId,
            )
        }
        item {
            OutlinedTextField(
                value = model,
                onValueChange = viewModel::setModelId,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.taixu_onboarding_model_id)) },
                placeholder = { Text("gpt-4o / deepseek-chat") },
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
            )
        }
        discoveryError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = viewModel::skipModel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.taixu_onboarding_later)) }
                Button(
                    onClick = viewModel::saveModelAndFinish,
                    enabled = model.isNotBlank(),
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                ) {
                    Text(stringResource(R.string.taixu_onboarding_enter))
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
            label = { Text(stringResource(R.string.taixu_onboarding_provider_preset)) },
            shape = RoundedCornerShape(10.dp),
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
            shape = RoundedCornerShape(10.dp),
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
