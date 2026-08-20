package top.wkbin.taixu.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.AiModelDao
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.database.ToolEntity
import top.wkbin.taixu.core.datastore.SettingsDataStore
import top.wkbin.taixu.core.model.ToolManifest
import top.wkbin.taixu.core.tools.ProviderRepository
import top.wkbin.taixu.core.tools.ToolManager
import java.util.UUID
import javax.inject.Inject

data class ToolDetailUiState(
    val tool: ToolEntity? = null,
    val manifest: ToolManifest? = null,
    val gatewayRunning: Boolean = false,
    val autoStartEnabled: Boolean = false,
    val accessToken: String? = null,
    val gatewayOperating: Boolean = false,
    val error: String? = null,
    val models: List<AiModelEntity> = emptyList(),
    val appliedModelId: String? = null,
    val applyingModel: Boolean = false,
) {
    val isWebService: Boolean get() = manifest?.launchType == "web"
    val servicePort: Int? get() = manifest?.servicePort
    val activeModel: AiModelEntity? get() = models.firstOrNull { it.isActive }

    val accessUrl: String?
        get() {
            val port = servicePort ?: return null
            val path = manifest?.servicePath ?: "/"
            return if (accessToken != null) {
                "http://localhost:$port$path?token=$accessToken"
            } else {
                "http://localhost:$port$path"
            }
        }
}

@HiltViewModel
class ToolDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val toolManager: ToolManager,
    private val settingsDataStore: SettingsDataStore,
    private val providerRepository: ProviderRepository,
    private val aiModelDao: AiModelDao,
    private val logger: AppLogger,
) : ViewModel() {

    val toolId: String = checkNotNull(savedStateHandle["toolId"]) { "toolId is required" }

    private val _gatewayRunning = MutableStateFlow(false)
    private val _gatewayOperating = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _appliedModelId = MutableStateFlow<String?>(null)
    private val _applyingModel = MutableStateFlow(false)

    val uiState: StateFlow<ToolDetailUiState> = combine(
        toolManager.observeTools(),
        settingsDataStore.toolAutoStart(toolId),
        settingsDataStore.toolAccessToken(toolId),
        _gatewayRunning,
        _gatewayOperating,
        _error,
        aiModelDao.observeAll(),
        _appliedModelId,
        _applyingModel,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val tools = values[0] as List<ToolEntity>
        val autoStart = values[1] as Boolean
        val token = values[2] as String?
        val running = values[3] as Boolean
        val operating = values[4] as Boolean
        val error = values[5] as String?
        val models = values[6] as List<AiModelEntity>
        val appliedId = values[7] as String?
        val applying = values[8] as Boolean

        val tool = tools.firstOrNull { it.id == toolId }
        val manifest = toolManager.manifest(toolId)

        ToolDetailUiState(
            tool = tool,
            manifest = manifest,
            gatewayRunning = running,
            autoStartEnabled = autoStart,
            accessToken = token,
            gatewayOperating = operating,
            error = error,
            models = models,
            appliedModelId = appliedId,
            applyingModel = applying,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ToolDetailUiState())

    init {
        pollGatewayStatus()
    }

    private fun pollGatewayStatus() {
        viewModelScope.launch {
            while (true) {
                _gatewayRunning.value = toolManager.isGatewayRunning(toolId)
                delay(3000)
            }
        }
    }

    fun startGateway() {
        _gatewayOperating.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                toolManager.startGateway(toolId)
                delay(1500) // allow port to come up
                _gatewayRunning.value = toolManager.isGatewayRunning(toolId)
            } catch (e: Exception) {
                logger.w("Failed to start gateway for $toolId: ${e.message}", e)
                _error.value = "网关启动失败：${e.message}"
            } finally {
                _gatewayOperating.value = false
            }
        }
    }

    fun stopGateway() {
        _gatewayOperating.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                toolManager.stopGateway(toolId)
                delay(500)
                _gatewayRunning.value = toolManager.isGatewayRunning(toolId)
            } catch (e: Exception) {
                logger.w("Failed to stop gateway for $toolId: ${e.message}", e)
                _error.value = "网关停止失败：${e.message}"
            } finally {
                _gatewayOperating.value = false
            }
        }
    }

    fun toggleAutoStart(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setToolAutoStart(toolId, enabled)
            } catch (e: Exception) {
                logger.w("Failed to set auto-start for $toolId: ${e.message}", e)
            }
        }
    }

    fun generateToken() {
        viewModelScope.launch {
            try {
                val token = UUID.randomUUID().toString().replace("-", "").take(32)
                settingsDataStore.setToolAccessToken(toolId, token)
            } catch (e: Exception) {
                logger.w("Failed to generate token for $toolId: ${e.message}", e)
            }
        }
    }

    fun clearToken() {
        viewModelScope.launch {
            try {
                settingsDataStore.setToolAccessToken(toolId, null)
            } catch (e: Exception) {
                logger.w("Failed to clear token for $toolId: ${e.message}", e)
            }
        }
    }

    /**
     * 一键将已配置模型应用到工具环境：
     * 将所选模型的 provider、baseUrl、model、apiKey 写入全局 ProviderRepository，
     * 所有工具适配器通过 ProviderManager.environment() 自动继承。
     */
    fun applyModel(model: AiModelEntity) {
        _applyingModel.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                providerRepository.setProvider(model.provider)
                if (model.baseUrl.isNotBlank()) {
                    providerRepository.setBaseUrl(model.baseUrl)
                }
                if (model.model.isNotBlank()) {
                    providerRepository.setModel(model.model)
                }
                // Read the actual API key from secure storage using the model's secretRef
                val actualKey = if (model.secretRef.isNotBlank()) {
                    providerRepository.readModelApiKey(model.secretRef)
                } else null
                if (!actualKey.isNullOrBlank()) {
                    providerRepository.setApiKey(actualKey)
                } else if (model.apiKey.isNotBlank()) {
                    providerRepository.setApiKey(model.apiKey)
                }
                _appliedModelId.value = model.id
            } catch (e: Exception) {
                logger.w("Failed to apply model ${model.name} for $toolId: ${e.message}", e)
                _error.value = "应用模型失败：${e.message}"
            } finally {
                _applyingModel.value = false
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    fun verifyTool() {
        viewModelScope.launch {
            try {
                toolManager.verify(toolId)
            } catch (e: Exception) {
                logger.w("Tool verification failed: $toolId, ${e.message}", e)
                _error.value = "自检失败：${e.message}"
            }
        }
    }

    fun uninstallTool() {
        viewModelScope.launch {
            try {
                toolManager.uninstall(toolId)
            } catch (e: Exception) {
                logger.w("Tool uninstall failed: $toolId, ${e.message}", e)
                _error.value = "卸载失败：${e.message}"
            }
        }
    }
}
