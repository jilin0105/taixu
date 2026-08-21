package top.wkbin.taixu.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.wkbin.taixu.core.datastore.SettingsDataStore
import top.wkbin.taixu.core.database.AiModelDao
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.tools.ProviderRepository
import top.wkbin.taixu.core.tools.AgentModelDiscovery
import top.wkbin.taixu.core.tools.AgentProviderCatalog
import top.wkbin.taixu.core.tools.AgentModelConnectionTester
import top.wkbin.taixu.core.tools.ProviderEndpointPolicy
import top.wkbin.taixu.core.model.ExecutionMode
import top.wkbin.taixu.runtime.privilege.PrivilegeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val providerRepository: ProviderRepository,
    private val aiModelDao: AiModelDao,
    private val modelDiscovery: AgentModelDiscovery,
    private val providerCatalogRepository: AgentProviderCatalog,
    private val connectionTester: AgentModelConnectionTester,
    private val privilegeManager: PrivilegeManager,
    private val mcpManager: top.wkbin.taixu.harness.mcp.McpManager,
    private val linuxRuntime: top.wkbin.taixu.runtime.LinuxRuntime,
    private val appUpdateManager: top.wkbin.taixu.core.network.AppUpdateManager,
) : ViewModel() {

    val installedDistros = linuxRuntime.installedDistros
    val activeDistroId = linuxRuntime.activeDistroId
    val runtimeState = linuxRuntime.state

    // ---- 终端外观与显示定制 ----
    val terminalFontSize: StateFlow<Int> = settingsDataStore.terminalFontSize
        .stateIn(viewModelScope, SharingStarted.Eagerly, 13)

    val terminalColorScheme: StateFlow<String> = settingsDataStore.terminalColorScheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, "obsidian")

    val terminalHapticsEnabled: StateFlow<Boolean> = settingsDataStore.terminalHapticsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val appFontScale: StateFlow<Float> = settingsDataStore.appFontScale
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)

    fun setTerminalFontSize(sizeSp: Int) {
        viewModelScope.launch { settingsDataStore.setTerminalFontSize(sizeSp) }
    }

    fun setTerminalColorScheme(scheme: String) {
        viewModelScope.launch { settingsDataStore.setTerminalColorScheme(scheme) }
    }

    fun setTerminalHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setTerminalHapticsEnabled(enabled) }
    }

    fun setAppFontScale(scale: Float) {
        viewModelScope.launch { settingsDataStore.setAppFontScale(scale) }
    }

    // ---- 应用版本更新机制 ----
    val autoCheckUpdates: StateFlow<Boolean> = settingsDataStore.autoCheckUpdates
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _updateCheckState = MutableStateFlow<top.wkbin.taixu.core.model.UpdateCheckState>(top.wkbin.taixu.core.model.UpdateCheckState.Idle)
    val updateCheckState: StateFlow<top.wkbin.taixu.core.model.UpdateCheckState> = _updateCheckState.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    fun setAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAutoCheckUpdates(enabled) }
    }

    fun checkForUpdates(currentVersion: String = "0.1.0") {
        viewModelScope.launch {
            _updateCheckState.value = top.wkbin.taixu.core.model.UpdateCheckState.Checking
            val res = appUpdateManager.checkUpdate(currentVersion)
            res.onSuccess { info ->
                _updateCheckState.value = top.wkbin.taixu.core.model.UpdateCheckState.Success(info)
            }.onFailure { err ->
                _updateCheckState.value = top.wkbin.taixu.core.model.UpdateCheckState.Error(err.message ?: "检查更新失败，请检查网络")
            }
        }
    }

    fun downloadAndInstall(apkUrl: String) {
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadProgress.value = 0f
            val res = appUpdateManager.downloadApk(apkUrl) { downloaded, total ->
                if (total != null && total > 0) {
                    _downloadProgress.value = downloaded.toFloat() / total.toFloat()
                } else {
                    _downloadProgress.value = null
                }
            }
            _isDownloading.value = false
            res.onSuccess { apkFile ->
                _downloadProgress.value = 1f
                appUpdateManager.installApk(apkFile)
            }.onFailure { err ->
                _downloadProgress.value = null
                _updateCheckState.value = top.wkbin.taixu.core.model.UpdateCheckState.Error("下载更新包失败：${err.message}")
            }
        }
    }

    fun clearUpdateState() {
        _updateCheckState.value = top.wkbin.taixu.core.model.UpdateCheckState.Idle
        _downloadProgress.value = null
        _isDownloading.value = false
    }

    fun switchActiveDistro(distroId: String) {
        viewModelScope.launch {
            linuxRuntime.switchActiveDistro(distroId)
        }
    }

    fun installDistro(
        request: top.wkbin.taixu.runtime.RuntimeInstallRequest,
        onProgress: suspend (top.wkbin.taixu.runtime.DownloadProgress) -> Unit,
        onResult: (Boolean, String) -> Unit,
    ) {
        viewModelScope.launch {
            val res = linuxRuntime.installDistro(request, onProgress)
            if (res is top.wkbin.taixu.core.common.result.AppResult.Success) {
                onResult(true, "安装成功")
            } else {
                onResult(false, res.errorOrNull()?.message ?: "安装失败")
            }
        }
    }

    fun uninstallDistro(distroId: String) {
        viewModelScope.launch {
            linuxRuntime.uninstallDistro(distroId)
        }
    }

    val mcpServers: StateFlow<List<top.wkbin.taixu.core.model.McpServerConfig>> = settingsDataStore.mcpServers
        .stateIn(viewModelScope, SharingStarted.Eagerly, top.wkbin.taixu.core.model.BuiltinMcpPresets.presets)

    fun toggleMcpServer(serverId: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.toggleMcpServer(serverId, enabled)
        }
    }

    fun saveMcpServer(server: top.wkbin.taixu.core.model.McpServerConfig) {
        viewModelScope.launch {
            settingsDataStore.saveMcpServer(server)
        }
    }

    fun deleteMcpServer(serverId: String) {
        viewModelScope.launch {
            settingsDataStore.deleteMcpServer(serverId)
        }
    }

    suspend fun testMcpServer(server: top.wkbin.taixu.core.model.McpServerConfig): Result<List<top.wkbin.taixu.core.model.McpToolInfo>> {
        return mcpManager.testServer(server)
    }

    val executionMode: StateFlow<ExecutionMode> = settingsDataStore.executionMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ExecutionMode.PROOT)

    private val _switchingMode = MutableStateFlow(false)
    val switchingMode: StateFlow<Boolean> = _switchingMode.asStateFlow()

    fun switchExecutionMode(mode: ExecutionMode, onResult: (Boolean, String) -> Unit) {
        if (_switchingMode.value) return
        viewModelScope.launch {
            _switchingMode.value = true
            val result = privilegeManager.switchMode(mode)
            _switchingMode.value = false
            if (result.isSuccess) {
                val authorized = result.getOrNull()
                onResult(true, authorized?.details ?: "已成功切换至 ${mode.title}")
            } else {
                onResult(false, result.errorOrNull()?.message ?: "授权失败")
            }
        }
    }

    val providerCatalog = providerCatalogRepository.providers

    val models: StateFlow<List<AiModelEntity>> = aiModelDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val developerMode: StateFlow<Boolean> = settingsDataStore.developerMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val themeMode: StateFlow<String> = settingsDataStore.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    val provider: StateFlow<String> = providerRepository.provider
        .stateIn(viewModelScope, SharingStarted.Eagerly, "OpenAI")
    val apiKeyConfigured: StateFlow<Boolean> = providerRepository.apiKeyConfigured
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val baseUrl: StateFlow<String> = providerRepository.baseUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val model: StateFlow<String> = providerRepository.model
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // ---- Agent 配置与管理 ----
    val thinkingExpanded: StateFlow<Boolean> = settingsDataStore.thinkingExpanded
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val defaultReasoningDepth: StateFlow<String> = settingsDataStore.defaultReasoningDepth
        .stateIn(viewModelScope, SharingStarted.Eagerly, "auto")

    val contextCompactionEnabled: StateFlow<Boolean> = settingsDataStore.contextCompactionEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val contextCompactionThreshold: StateFlow<Int> = settingsDataStore.contextCompactionThreshold
        .stateIn(viewModelScope, SharingStarted.Eagerly, 15)

    val maxToolRounds: StateFlow<Int> = settingsDataStore.maxToolRounds
        .stateIn(viewModelScope, SharingStarted.Eagerly, 100)

    val autoWorkspaceCwd: StateFlow<Boolean> = settingsDataStore.autoWorkspaceCwd
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val allSkills: StateFlow<List<top.wkbin.taixu.core.model.AgentSkill>> = settingsDataStore.allSkills
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allPlugins: StateFlow<List<top.wkbin.taixu.core.model.AgentPlugin>> = settingsDataStore.allPlugins
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setThinkingExpanded(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setThinkingExpanded(value) }
    }

    fun setDefaultReasoningDepth(value: String) {
        viewModelScope.launch { settingsDataStore.setDefaultReasoningDepth(value) }
    }

    fun setContextCompactionEnabled(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setContextCompactionEnabled(value) }
    }

    fun setContextCompactionThreshold(value: Int) {
        viewModelScope.launch { settingsDataStore.setContextCompactionThreshold(value) }
    }

    fun setMaxToolRounds(value: Int) {
        viewModelScope.launch { settingsDataStore.setMaxToolRounds(value) }
    }

    fun setAutoWorkspaceCwd(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setAutoWorkspaceCwd(value) }
    }

    fun toggleSkill(skillId: String, enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setSkillEnabled(skillId, enabled) }
    }

    fun addCustomSkill(name: String, description: String, systemPrompt: String, command: String?) {
        val trimmedName = name.trim()
        val trimmedPrompt = systemPrompt.trim()
        if (trimmedName.isBlank() || trimmedPrompt.isBlank()) return
        val id = "custom_" + java.util.UUID.randomUUID().toString().take(8)
        val skill = top.wkbin.taixu.core.model.AgentSkill(
            id = id,
            name = trimmedName,
            description = description.trim().ifBlank { "自定义技能" },
            systemPrompt = trimmedPrompt,
            triggerCommand = command?.trim()?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("/")) it else "/$it" },
            iconName = "Code",
            isEnabled = true,
            isBuiltin = false,
            category = "自定义",
        )
        viewModelScope.launch { settingsDataStore.addCustomSkill(skill) }
    }

    fun deleteCustomSkill(skillId: String) {
        viewModelScope.launch { settingsDataStore.deleteCustomSkill(skillId) }
    }

    fun togglePlugin(pluginId: String, enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setPluginEnabled(pluginId, enabled) }
    }

    private val _apiKeyDraft = MutableStateFlow("")
    val apiKeyDraft: StateFlow<String> = _apiKeyDraft.asStateFlow()
    private val _discoveredModels = MutableStateFlow<List<String>>(emptyList())
    val discoveredModels: StateFlow<List<String>> = _discoveredModels.asStateFlow()
    private val _discoveringModels = MutableStateFlow(false)
    val discoveringModels: StateFlow<Boolean> = _discoveringModels.asStateFlow()
    private val _modelDiscoveryError = MutableStateFlow<String?>(null)
    val modelDiscoveryError: StateFlow<String?> = _modelDiscoveryError.asStateFlow()
    private val _testingConnection = MutableStateFlow(false)
    val testingConnection: StateFlow<Boolean> = _testingConnection.asStateFlow()
    private val _connectionResult = MutableStateFlow<String?>(null)
    val connectionResult: StateFlow<String?> = _connectionResult.asStateFlow()

    fun setDeveloperMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setDeveloperMode(enabled)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            settingsDataStore.setThemeMode(mode)
        }
    }

    fun setProvider(value: String) {
        viewModelScope.launch { providerRepository.setProvider(value) }
    }

    fun setBaseUrl(value: String) {
        viewModelScope.launch { providerRepository.setBaseUrl(value) }
    }

    fun setModel(value: String) {
        viewModelScope.launch { providerRepository.setModel(value) }
    }

    fun onApiKeyChanged(value: String) {
        _apiKeyDraft.value = value
    }

    fun discoverModels(providerId: String, baseUrl: String, apiKey: String = "") {
        viewModelScope.launch {
            val cleanUrl = ProviderEndpointPolicy.normalizeUrl(baseUrl)
            if (!ProviderEndpointPolicy.isSafeBaseUrl(cleanUrl)) return@launch
            _discoveringModels.value = true
            _modelDiscoveryError.value = null
            val provider = providerCatalogRepository.find(providerId)
            runCatching { modelDiscovery.discover(provider, cleanUrl, apiKey.ifBlank { providerRepository.readApiKey() }) }
                .onSuccess { models ->
                    _discoveredModels.value = models
                    if (models.isEmpty()) _modelDiscoveryError.value = "端点未返回可用的 Agent 模型"
                }
                .onFailure { _modelDiscoveryError.value = it.message ?: "模型发现失败" }
            _discoveringModels.value = false
        }
    }

    fun clearDiscoveredModels() {
        _discoveredModels.value = emptyList()
        _modelDiscoveryError.value = null
    }

    fun testConnection(baseUrl: String, model: String, apiKey: String) {
        viewModelScope.launch {
            _testingConnection.value = true
            _connectionResult.value = null
            runCatching { connectionTester.test(baseUrl, model, apiKey.ifBlank { null }) }
                .onSuccess { _connectionResult.value = "连接成功" }
                .onFailure { _connectionResult.value = it.message ?: "连接失败" }
            _testingConnection.value = false
        }
    }

    fun saveApiKey() {
        viewModelScope.launch {
            providerRepository.setApiKey(_apiKeyDraft.value)
            _apiKeyDraft.value = ""
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            providerRepository.setApiKey("")
            _apiKeyDraft.value = ""
        }
    }

    fun saveModel(
        id: String?,
        name: String,
        provider: String,
        model: String,
        baseUrl: String,
        apiKey: String,
        temperature: Float? = null,
        maxTokens: Int? = null,
        topP: Float? = null,
        reasoningMode: String? = null,
        reasoningEffort: String? = null,
        toolCallMode: String? = null,
        contextTokens: Int? = null,
        customHeaders: String = "",
        pureChatMode: Boolean = false,
        visionEnabled: Boolean = true,
    ) {
        viewModelScope.launch {
            val existing = aiModelDao.observeAll().first()
            val old: AiModelEntity? = if (id == null) null else aiModelDao.findById(id)
            val modelId = id ?: java.util.UUID.randomUUID().toString()
            val secretRef = old?.secretRef?.takeIf { it.isNotBlank() } ?: "model_${modelId.replace("-", "")}"
            if (existing.none { it.isActive } || old?.isActive == true) aiModelDao.clearActive()
            aiModelDao.upsert(
                AiModelEntity(
                    id = modelId,
                    name = name.trim(),
                    provider = provider.trim(),
                    model = model.trim(),
                    baseUrl = baseUrl.trim(),
                    apiKey = if (apiKey.isNotBlank()) apiKey.trim() else old?.apiKey.orEmpty(),
                    secretRef = secretRef,
                    isActive = old?.isActive ?: existing.none { it.isActive },
                    createdAt = old?.createdAt ?: System.currentTimeMillis(),
                    temperature = temperature,
                    maxTokens = maxTokens,
                    topP = topP,
                    reasoningMode = reasoningMode?.ifBlank { null },
                    reasoningEffort = reasoningEffort?.ifBlank { null },
                    toolCallMode = toolCallMode?.ifBlank { null },
                    contextTokens = contextTokens,
                    customHeaders = customHeaders.trim(),
                    pureChatMode = pureChatMode,
                    visionEnabled = visionEnabled,
                ),
            )
        }
    }

    fun setActiveModel(id: String) {
        viewModelScope.launch {
            aiModelDao.clearActive()
            aiModelDao.setActive(id)
        }
    }

    fun deleteModel(id: String) {
        viewModelScope.launch {
            aiModelDao.delete(id)
        }
    }

    // ---- 宿主与沙箱存储挂载配置 (PRoot -b) ----
    val mountDownloadEnabled: StateFlow<Boolean> = settingsDataStore.mountDownloadEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val mountDocumentsEnabled: StateFlow<Boolean> = settingsDataStore.mountDocumentsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val mountSharedStorageEnabled: StateFlow<Boolean> = settingsDataStore.mountSharedStorageEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val customMountBindings: StateFlow<List<top.wkbin.taixu.core.model.StorageMountBinding>> = settingsDataStore.customMountBindings
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setMountDownloadEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setMountDownloadEnabled(enabled) }
    }

    fun setMountDocumentsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setMountDocumentsEnabled(enabled) }
    }

    fun setMountSharedStorageEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setMountSharedStorageEnabled(enabled) }
    }

    fun addCustomMountBinding(name: String, hostPath: String, guestPath: String) {
        val binding = top.wkbin.taixu.core.model.StorageMountBinding(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "自定义挂载" },
            hostPath = hostPath.trim(),
            guestPath = if (guestPath.trim().startsWith("/")) guestPath.trim() else "/${guestPath.trim()}",
            enabled = true,
            isSystemDefault = false,
        )
        viewModelScope.launch { settingsDataStore.addCustomMountBinding(binding) }
    }

    fun removeCustomMountBinding(bindingId: String) {
        viewModelScope.launch { settingsDataStore.removeCustomMountBinding(bindingId) }
    }

    fun toggleCustomMountBinding(bindingId: String, enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setCustomMountEnabled(bindingId, enabled) }
    }
}
