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
) : ViewModel() {

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
            _discoveringModels.value = true
            _modelDiscoveryError.value = null
            val provider = providerCatalogRepository.find(providerId)
            runCatching { modelDiscovery.discover(provider, baseUrl, apiKey.ifBlank { providerRepository.readApiKey() }) }
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
