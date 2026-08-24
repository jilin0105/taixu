package top.wkbin.taixu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.wkbin.taixu.core.database.AiModelRepository
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.datastore.OnboardingPreferences
import top.wkbin.taixu.core.model.RuntimeState
import top.wkbin.taixu.core.tools.ProviderRepository
import top.wkbin.taixu.core.tools.AgentProviderCatalog
import top.wkbin.taixu.core.tools.AgentModelDiscovery
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.RegistryRoute
import top.wkbin.taixu.runtime.RuntimeInstallRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import top.wkbin.taixu.core.tools.ProviderEndpointPolicy

data class OnboardingStatus(val loaded: Boolean = false, val completed: Boolean = false)

data class StarterPlugin(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val isRecommended: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: OnboardingPreferences,
    private val linuxRuntime: LinuxRuntime,
    private val providerRepository: ProviderRepository,
    private val modelDao: AiModelRepository,
    private val providerCatalogRepository: AgentProviderCatalog,
    private val modelDiscovery: AgentModelDiscovery,
    private val toolManager: top.wkbin.taixu.core.tools.ToolManager,
) : ViewModel() {
    val providerCatalog = providerCatalogRepository.providers
    private val restoreComplete = MutableStateFlow(false)
    val status: StateFlow<OnboardingStatus> = combine(
        settings.onboardingCompleted,
        linuxRuntime.state,
        restoreComplete,
    ) { completed, state, restored ->
        OnboardingStatus(loaded = restored, completed = completed && state is RuntimeState.Ready)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingStatus())
    val runtimeState: StateFlow<RuntimeState> = linuxRuntime.state

    val devSuites: List<top.wkbin.taixu.core.model.PluginBundle> = top.wkbin.taixu.core.model.BuiltinPluginBundles.bundles

    private val _selectedSuites = MutableStateFlow<Set<String>>(
        top.wkbin.taixu.core.model.BuiltinPluginBundles.bundles.map { it.id }.toSet(),
    )
    val selectedSuites = _selectedSuites.asStateFlow()

    private val _isInstallingPlugins = MutableStateFlow(false)
    val isInstallingPlugins = _isInstallingPlugins.asStateFlow()

    private val _pluginInstallProgress = MutableStateFlow<String?>(null)
    val pluginInstallProgress = _pluginInstallProgress.asStateFlow()

    private val _distribution = MutableStateFlow("ubuntu")
    val distribution = _distribution.asStateFlow()
    private val _mirror = MutableStateFlow("auto")
    val mirror = _mirror.asStateFlow()
    private val _page = MutableStateFlow(0)
    val page = _page.asStateFlow()
    private val _modelProvider = MutableStateFlow("自定义 OpenAI 兼容接口")
    val modelProvider = _modelProvider.asStateFlow()
    private val _modelId = MutableStateFlow("")
    val modelId = _modelId.asStateFlow()
    private val _baseUrl = MutableStateFlow("")
    val baseUrl = _baseUrl.asStateFlow()
    private val _apiKey = MutableStateFlow("")
    val apiKey = _apiKey.asStateFlow()
    private val _discoveredModels = MutableStateFlow<List<String>>(emptyList())
    val discoveredModels = _discoveredModels.asStateFlow()
    private val _discoveringModels = MutableStateFlow(false)
    val discoveringModels = _discoveringModels.asStateFlow()
    private val _modelDiscoveryError = MutableStateFlow<String?>(null)
    val modelDiscoveryError = _modelDiscoveryError.asStateFlow()

    init {
        viewModelScope.launch {
            _distribution.value = settings.selectedDistribution.first()
            _mirror.value = settings.mirrorPolicy.first()
        }
    }

    fun selectDistribution(distribution: String) {
        _distribution.value = distribution
        viewModelScope.launch { settings.setSelectedDistribution(distribution) }
    }

    fun selectMirror(mirror: String) {
        _mirror.value = mirror
        viewModelScope.launch { settings.setMirrorPolicy(mirror) }
    }

    fun setPage(page: Int) {
        _page.value = page
    }

    fun setModelProvider(provider: String) {
        _modelProvider.value = provider
    }

    fun setModelId(id: String) {
        _modelId.value = id
    }

    fun setBaseUrl(url: String) {
        _baseUrl.value = url
    }

    fun setApiKey(key: String) {
        _apiKey.value = key
    }

    fun selectProvider(id: String) {
        val preset = providerCatalogRepository.find(id)
        _modelProvider.value = preset.name
        _baseUrl.value = preset.baseUrl
        _modelId.value = preset.recommendedModels.firstOrNull().orEmpty()
        _discoveredModels.value = emptyList()
        _modelDiscoveryError.value = null
        if (preset.baseUrl.isNotBlank() && ProviderEndpointPolicy.isSafeBaseUrl(preset.baseUrl)) {
            discoverModels()
        }
    }

    fun discoverModels() {
        viewModelScope.launch {
            val cleanUrl = ProviderEndpointPolicy.normalizeUrl(_baseUrl.value)
            if (!ProviderEndpointPolicy.isSafeBaseUrl(cleanUrl)) return@launch
            val preset = providerCatalog.firstOrNull { it.name == _modelProvider.value } ?: providerCatalogRepository.find("custom")
            _discoveringModels.value = true
            _modelDiscoveryError.value = null
            runCatching { modelDiscovery.discover(preset, cleanUrl, _apiKey.value.ifBlank { null }) }
                .onSuccess { models ->
                    _discoveredModels.value = models
                    if ((_modelId.value.isBlank() || _modelId.value == preset.recommendedModels.firstOrNull()) && models.isNotEmpty()) {
                        _modelId.value = models.first()
                    }
                }
                .onFailure { _modelDiscoveryError.value = it.message ?: "刷新模型失败" }
            _discoveringModels.value = false
        }
    }

    fun install() {
        if (runtimeState.value is RuntimeState.Initializing) return
        viewModelScope.launch {
            settings.setSelectedDistribution(_distribution.value)
            settings.setMirrorPolicy(_mirror.value)
            linuxRuntime.initialize(
                RuntimeInstallRequest(
                    distributionId = _distribution.value,
                    registryRoute = when (_mirror.value) {
                        "official" -> RegistryRoute.OFFICIAL
                        "china" -> RegistryRoute.CHINA_ACCELERATED
                        else -> RegistryRoute.AUTO
                    },
                ),
            )
            if (linuxRuntime.state.value is RuntimeState.Ready) _page.value = 1
        }
    }

    fun restoreInstalledState() {
        viewModelScope.launch {
            val restored = linuxRuntime.restoreInstalledState()
            if (restored && !settings.onboardingCompleted.first()) _page.value = 1
            restoreComplete.value = true
        }
    }

    fun retryReady() {
        if (runtimeState.value is RuntimeState.Initializing) return
        restoreComplete.value = false
        restoreInstalledState()
    }

    fun toggleSuite(id: String) {
        val current = _selectedSuites.value
        _selectedSuites.value = if (id in current) current - id else current + id
    }

    fun skipSuites() {
        _page.value = 2
    }

    fun installSelectedSuitesAndProceed() {
        val selected = _selectedSuites.value.toSet()
        _page.value = 2
        if (selected.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _isInstallingPlugins.value = true
            try {
                toolManager.batchInstallSuites(selected).collect { event ->
                    if (event is top.wkbin.taixu.runtime.tools.InstallEvent.Progress) {
                        _pluginInstallProgress.value = event.message
                    }
                }
            } catch (e: Exception) {
                // 忽略异常保证流程顺畅
            } finally {
                _isInstallingPlugins.value = false
                _pluginInstallProgress.value = null
            }
        }
    }

    fun skipModel() = finish()

    fun saveModelAndFinish() {
        val model = _modelId.value.trim()
        if (model.isBlank()) return
        viewModelScope.launch {
            val existing = modelDao.observeAll().first()
            val id = UUID.randomUUID().toString()
            val secretRef = "model_${id.replace("-", "")}" 
            if (_apiKey.value.isNotBlank()) settings.setModelApiKey(secretRef, _apiKey.value)
            modelDao.upsert(
                AiModelEntity(
                    id = id,
                    name = model,
                    provider = _modelProvider.value.trim(),
                    model = model,
                    baseUrl = _baseUrl.value.trim(),
                    secretRef = secretRef,
                    isActive = existing.none { it.isActive },
                    createdAt = System.currentTimeMillis(),
                ),
            )
            if (existing.none { it.isActive }) modelDao.setActive(id)
            finish()
        }
    }

    private fun finish() {
        viewModelScope.launch { settings.setOnboardingCompleted(true) }
    }
}
