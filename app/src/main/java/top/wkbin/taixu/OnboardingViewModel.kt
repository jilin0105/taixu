package top.wkbin.taixu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.wkbin.taixu.core.database.AiModelDao
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.datastore.SettingsDataStore
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
import kotlinx.coroutines.launch

import top.wkbin.taixu.core.tools.ProviderEndpointPolicy

data class OnboardingStatus(val loaded: Boolean = false, val completed: Boolean = false)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val linuxRuntime: LinuxRuntime,
    private val providerRepository: ProviderRepository,
    private val modelDao: AiModelDao,
    private val providerCatalogRepository: AgentProviderCatalog,
    private val modelDiscovery: AgentModelDiscovery,
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

    fun selectDistribution(value: String) { _distribution.value = value }
    fun selectMirror(value: String) { _mirror.value = value }
    fun setModelProvider(value: String) { _modelProvider.value = value }
    fun setModelId(value: String) { _modelId.value = value }
    fun setBaseUrl(value: String) {
        _baseUrl.value = value
        val clean = ProviderEndpointPolicy.normalizeUrl(value)
        if (ProviderEndpointPolicy.isSafeBaseUrl(clean)) {
            discoverModels()
        }
    }
    fun setApiKey(value: String) { _apiKey.value = value }
    fun selectProvider(id: String) {
        val preset = providerCatalogRepository.find(id)
        _modelProvider.value = preset.name
        _baseUrl.value = preset.baseUrl
        _modelId.value = preset.recommendedModels.firstOrNull().orEmpty()
        _discoveredModels.value = emptyList()
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

    /**
     * 已安装环境恢复失败后的轻量重试：只重跑健康检查与状态恢复，
     * 不触发重新下载。适合健康检查偶发失败（冷启动慢、子进程被系统限制等）的场景。
     */
    fun retryReady() {
        if (runtimeState.value is RuntimeState.Initializing) return
        restoreComplete.value = false
        restoreInstalledState()
    }

    fun skipModel() = finish()

    fun saveModelAndFinish() {
        val model = _modelId.value.trim()
        if (model.isBlank()) return
        viewModelScope.launch {
            val existing = modelDao.observeAll().first()
            val id = UUID.randomUUID().toString()
            modelDao.upsert(
                AiModelEntity(
                    id = id,
                    name = model,
                    provider = _modelProvider.value.trim(),
                    model = model,
                    baseUrl = _baseUrl.value.trim(),
                    apiKey = _apiKey.value.trim(),
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
