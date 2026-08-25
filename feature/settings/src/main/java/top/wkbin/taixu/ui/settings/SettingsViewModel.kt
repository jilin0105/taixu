package top.wkbin.taixu.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.wkbin.taixu.core.datastore.SettingsDataStore
import top.wkbin.taixu.core.database.AiModelRepository
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.database.AgentSkillRepository
import top.wkbin.taixu.core.database.McpServerRepository
import top.wkbin.taixu.core.database.StorageMountBindingRepository
import top.wkbin.taixu.core.tools.ProviderRepository
import top.wkbin.taixu.core.tools.ToolManager
import top.wkbin.taixu.core.tools.AgentModelDiscovery
import top.wkbin.taixu.core.tools.AgentProviderCatalog
import top.wkbin.taixu.core.tools.AgentModelConnectionTester
import top.wkbin.taixu.core.tools.ProviderEndpointPolicy
import top.wkbin.taixu.core.model.ExecutionMode
import top.wkbin.taixu.core.model.McpConnectionState
import top.wkbin.taixu.core.model.RuntimeState
import top.wkbin.taixu.runtime.LinuxEnvironmentManager
import top.wkbin.taixu.runtime.RuntimePathManager
import top.wkbin.taixu.runtime.proot.QemuCompatibilityLayout
import top.wkbin.taixu.runtime.privilege.PhantomProcessLimitStatus
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val providerRepository: ProviderRepository,
    private val aiModelDao: AiModelRepository,
    private val modelDiscovery: AgentModelDiscovery,
    private val providerCatalogRepository: AgentProviderCatalog,
    private val connectionTester: AgentModelConnectionTester,
    private val privilegeManager: PrivilegeManager,
    private val mcpManager: top.wkbin.taixu.harness.mcp.McpManager,
    private val linuxRuntime: top.wkbin.taixu.runtime.LinuxRuntime,
    private val pathManager: RuntimePathManager,
    private val linuxEnvironmentManager: LinuxEnvironmentManager,
    private val appUpdateManager: top.wkbin.taixu.core.network.AppUpdateManager,
    private val subagentRepository: top.wkbin.taixu.core.database.AgentSubagentRepository,
    private val agentSkillRepository: AgentSkillRepository,
    private val mcpServerRepository: McpServerRepository,
    private val storageMountBindingRepository: StorageMountBindingRepository,
    private val approvalRepository: top.wkbin.taixu.core.database.AgentApprovalRepository,
    private val toolManager: ToolManager,
) : ViewModel() {
    val installedDistros = linuxRuntime.installedDistros
    val activeDistroId = linuxRuntime.activeDistroId
    val runtimeState = linuxRuntime.state

    val environmentVariables = linuxEnvironmentManager.variables
    val environmentValues = linuxEnvironmentManager.values
    val effectiveEnvironment = linuxEnvironmentManager.effectiveEnvironment

    private val _environmentLoading = MutableStateFlow(false)
    val environmentLoading: StateFlow<Boolean> = _environmentLoading.asStateFlow()

    private val _environmentError = MutableStateFlow<String?>(null)
    val environmentError: StateFlow<String?> = _environmentError.asStateFlow()

    init {
        viewModelScope.launch {
            subagentRepository.ensureInitialized()
            agentSkillRepository.ensureInitialized()
            mcpServerRepository.ensureInitialized()
        }
        viewModelScope.launch {
            combine(linuxRuntime.state, linuxRuntime.activeDistroId) { state, distroId ->
                (state is RuntimeState.Ready) to distroId
            }
                .distinctUntilChanged()
                .collectLatest { (ready, distroId) ->
                    if (ready) refreshEnvironmentVariables(distroId)
                }
        }
        viewModelScope.launch {
            combine(
                linuxRuntime.activeDistroId,
                toolManager.installProgress,
                toolManager.localPluginImportState,
            ) { distroId, _, _ ->
                QemuCompatibilityLayout.isReady(pathManager.taixuRootDir(distroId))
            }
                .flowOn(Dispatchers.IO)
                .distinctUntilChanged()
                .collectLatest { ready ->
                    _qemuCompatibilityReady.value = ready
                    if (ready) _qemuCompatibilityMessage.value = null
                }
        }
    }

    val environmentPrivacyMode: StateFlow<Boolean> = settingsDataStore.environmentPrivacyMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setEnvironmentPrivacyMode(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setEnvironmentPrivacyMode(enabled) }
    }

    fun addEnvironmentVariable(key: String, value: String, note: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _environmentLoading.value = true
            val result = linuxEnvironmentManager.add(key, value, note)
            finishEnvironmentOperation(result, onResult)
        }
    }

    fun updateEnvironmentVariable(id: String, key: String, value: String?, note: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _environmentLoading.value = true
            val result = linuxEnvironmentManager.update(id, key, value, note)
            finishEnvironmentOperation(result, onResult)
        }
    }

    fun deleteEnvironmentVariable(id: String) {
        viewModelScope.launch {
            _environmentLoading.value = true
            finishEnvironmentOperation(linuxEnvironmentManager.delete(id))
        }
    }

    fun refreshEnvironmentVariables(distroId: String = linuxRuntime.activeDistroId.value) {
        viewModelScope.launch {
            _environmentLoading.value = true
            finishEnvironmentOperation(linuxEnvironmentManager.refresh(distroId))
        }
    }

    fun clearEnvironmentError() {
        _environmentError.value = null
    }

    private fun finishEnvironmentOperation(result: Result<Unit>, onResult: (Boolean) -> Unit = {}) {
        _environmentLoading.value = false
        _environmentError.value = result.exceptionOrNull()?.message
        onResult(result.isSuccess)
    }

    // ---- 终端外观与显示定制 ----
    val terminalFontSize: StateFlow<Int> = settingsDataStore.terminalFontSize
        .stateIn(viewModelScope, SharingStarted.Eagerly, 13)

    val terminalColorScheme: StateFlow<String> = settingsDataStore.terminalColorScheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, "obsidian")

    val terminalHapticsEnabled: StateFlow<Boolean> = settingsDataStore.terminalHapticsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val appFontScale: StateFlow<Float> = settingsDataStore.appFontScale
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)

    val chengmingBackgroundUri: StateFlow<String?> = settingsDataStore.chengmingBackgroundUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    fun setChengmingBackgroundUri(uri: String?) {
        viewModelScope.launch { settingsDataStore.setChengmingBackgroundUri(uri) }
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

    fun checkForUpdates(currentVersion: String) {
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

    private val _restoringDistroId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val restoringDistroId: StateFlow<String?> = _restoringDistroId.asStateFlow()

    fun resetDistro(distroId: String, onResult: ((Boolean, String) -> Unit)? = null) {
        if (_restoringDistroId.value != null) return
        _restoringDistroId.value = distroId
        viewModelScope.launch {
            val res = linuxRuntime.resetSandbox(distroId)
            if (res is top.wkbin.taixu.core.common.result.AppResult.Success) {
                toolManager.resetDistroState(distroId)
            }
            _restoringDistroId.value = null
            if (res is top.wkbin.taixu.core.common.result.AppResult.Success) {
                onResult?.invoke(true, "已恢复初始状态")
            } else {
                onResult?.invoke(false, res.errorOrNull()?.message ?: "重置失败")
            }
        }
    }

    private val _deletingDistroId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val deletingDistroId: StateFlow<String?> = _deletingDistroId.asStateFlow()

    fun uninstallDistro(distroId: String, onResult: ((Boolean, String) -> Unit)? = null) {
        if (_deletingDistroId.value != null) return
        _deletingDistroId.value = distroId
        viewModelScope.launch {
            val res = linuxRuntime.uninstallDistro(distroId)
            _deletingDistroId.value = null
            if (res is top.wkbin.taixu.core.common.result.AppResult.Success) {
                onResult?.invoke(true, "系统已成功删除")
            } else {
                onResult?.invoke(false, res.errorOrNull()?.message ?: "删除失败")
            }
        }
    }

    val mcpServers: StateFlow<List<top.wkbin.taixu.core.model.McpServerConfig>> = mcpServerRepository.servers
        .stateIn(viewModelScope, SharingStarted.Eagerly, top.wkbin.taixu.core.model.BuiltinMcpPresets.presets)

    /** 各 MCP 服务的实时连通性状态（与 McpManager 共享，设置页与聊天页联动）。 */
    val mcpConnectionStates: StateFlow<Map<String, McpConnectionState>> = mcpManager.connectionStates

    /** 手动/自动触发一次全量 MCP 连通性探测。 */
    fun refreshMcpConnections() {
        viewModelScope.launch { mcpManager.refreshConnections() }
    }

    fun toggleMcpServer(serverId: String, enabled: Boolean) {
        viewModelScope.launch {
            mcpServerRepository.setEnabled(serverId, enabled)
            mcpManager.refreshConnections()
        }
    }

    fun saveMcpServer(server: top.wkbin.taixu.core.model.McpServerConfig) {
        viewModelScope.launch {
            mcpServerRepository.save(server)
            mcpManager.refreshConnections()
        }
    }

    fun deleteMcpServer(serverId: String) {
        viewModelScope.launch {
            mcpServerRepository.delete(serverId)
            mcpManager.refreshConnections()
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

    private val _phantomProcessStatus = MutableStateFlow<PhantomProcessLimitStatus?>(null)
    val phantomProcessStatus: StateFlow<PhantomProcessLimitStatus?> = _phantomProcessStatus.asStateFlow()

    private val _phantomProcessBusy = MutableStateFlow(false)
    val phantomProcessBusy: StateFlow<Boolean> = _phantomProcessBusy.asStateFlow()

    private val _phantomProcessMessage = MutableStateFlow<String?>(null)
    val phantomProcessMessage: StateFlow<String?> = _phantomProcessMessage.asStateFlow()

    val phantomProcessAdbCommand: String = PrivilegeManager.PHANTOM_PROCESS_ADB_COMMAND

    fun refreshPhantomProcessLimit() {
        if (_phantomProcessBusy.value) return
        viewModelScope.launch {
            _phantomProcessBusy.value = true
            try {
                _phantomProcessStatus.value = privilegeManager.checkPhantomProcessLimit()
            } finally {
                _phantomProcessBusy.value = false
            }
        }
    }

    fun removePhantomProcessLimit() {
        if (_phantomProcessBusy.value) return
        viewModelScope.launch {
            _phantomProcessBusy.value = true
            try {
                val result = privilegeManager.removePhantomProcessLimit()
                _phantomProcessMessage.value = if (result.success) {
                    "解除命令执行成功，已重新读取系统状态。"
                } else {
                    result.stderr.ifBlank { "解除失败（退出码 ${result.exitCode}）" }
                }
                _phantomProcessStatus.value = privilegeManager.checkPhantomProcessLimit()
            } finally {
                _phantomProcessBusy.value = false
            }
        }
    }

    fun clearPhantomProcessMessage() {
        _phantomProcessMessage.value = null
    }

    val providerCatalog = providerCatalogRepository.providers

    val models: StateFlow<List<AiModelEntity>> = aiModelDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val developerMode: StateFlow<Boolean> = settingsDataStore.developerMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val qemuCompatibilityEnabled: StateFlow<Boolean> = settingsDataStore.qemuCompatibilityEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _qemuCompatibilityMessage = MutableStateFlow<String?>(null)
    val qemuCompatibilityMessage: StateFlow<String?> = _qemuCompatibilityMessage.asStateFlow()

    private val _qemuCompatibilityReady = MutableStateFlow(false)
    val qemuCompatibilityReady: StateFlow<Boolean> = _qemuCompatibilityReady.asStateFlow()

    val themeMode: StateFlow<String> = settingsDataStore.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    val themeStyle: StateFlow<String> = settingsDataStore.themeStyle
        .stateIn(viewModelScope, SharingStarted.Eagerly, "xuantong")

    fun setThemeStyle(style: String) {
        viewModelScope.launch {
            settingsDataStore.setThemeStyle(style)
        }
    }

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

    val thinkingLanguage: StateFlow<String> = settingsDataStore.thinkingLanguage
        .stateIn(viewModelScope, SharingStarted.Eagerly, "zh")

    fun setThinkingLanguage(lang: String) {
        viewModelScope.launch { settingsDataStore.setThinkingLanguage(lang) }
    }

    val customSystemPromptEnabled: StateFlow<Boolean> = settingsDataStore.customSystemPromptEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setCustomSystemPromptEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setCustomSystemPromptEnabled(enabled) }
    }

    val customSystemPrompt: StateFlow<String> = settingsDataStore.customSystemPrompt
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setCustomSystemPrompt(prompt: String) {
        viewModelScope.launch { settingsDataStore.setCustomSystemPrompt(prompt) }
    }

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

    val baseCommandTimeoutSeconds: StateFlow<Int> = settingsDataStore.baseCommandTimeoutSeconds
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsDataStore.DEFAULT_BASE_COMMAND_TIMEOUT_SECONDS)

    val approvalMode: StateFlow<top.wkbin.taixu.core.model.ApprovalMode> = approvalRepository.mode
        .stateIn(viewModelScope, SharingStarted.Eagerly, top.wkbin.taixu.core.model.ApprovalMode.ASSISTED)

    val contextBudgetTokens: StateFlow<Int> = settingsDataStore.contextBudgetTokens
        .stateIn(viewModelScope, SharingStarted.Eagerly, 128_000)

    val maxToolsPerRound: StateFlow<Int> = settingsDataStore.maxToolsPerRound
        .stateIn(viewModelScope, SharingStarted.Eagerly, 12)

    val maxConsecutiveFailures: StateFlow<Int> = settingsDataStore.maxConsecutiveFailures
        .stateIn(viewModelScope, SharingStarted.Eagerly, 8)

    val allSkills: StateFlow<List<top.wkbin.taixu.core.model.AgentSkill>> = agentSkillRepository.allSkills
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val autoSubagentDelegationEnabled: StateFlow<Boolean> = subagentRepository.autoDelegationEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val allSubagents: StateFlow<List<top.wkbin.taixu.core.model.AgentSubagent>> = subagentRepository.profiles
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

    fun setBaseCommandTimeoutSeconds(value: Int) {
        viewModelScope.launch { settingsDataStore.setBaseCommandTimeoutSeconds(value) }
    }

    fun setApprovalMode(mode: top.wkbin.taixu.core.model.ApprovalMode) {
        viewModelScope.launch { approvalRepository.setMode(mode) }
    }

    fun setContextBudgetTokens(value: Int) {
        viewModelScope.launch { settingsDataStore.setContextBudgetTokens(value) }
    }

    fun setMaxToolsPerRound(value: Int) {
        viewModelScope.launch { settingsDataStore.setMaxToolsPerRound(value) }
    }

    fun setMaxConsecutiveFailures(value: Int) {
        viewModelScope.launch { settingsDataStore.setMaxConsecutiveFailures(value) }
    }

    fun toggleSkill(skillId: String, enabled: Boolean) {
        viewModelScope.launch { agentSkillRepository.setEnabled(skillId, enabled) }
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
        viewModelScope.launch { agentSkillRepository.addCustom(skill) }
    }

    fun deleteCustomSkill(skillId: String) {
        viewModelScope.launch { agentSkillRepository.deleteCustom(skillId) }
    }

    fun setAutoSubagentDelegationEnabled(enabled: Boolean) {
        viewModelScope.launch { subagentRepository.setAutoDelegationEnabled(enabled) }
    }

    fun toggleSubagent(profileId: String, enabled: Boolean) {
        viewModelScope.launch { subagentRepository.setEnabled(profileId, enabled) }
    }

    fun saveSubagent(
        previous: top.wkbin.taixu.core.model.AgentSubagent?,
        roleId: String,
        name: String,
        description: String,
        systemPrompt: String,
    ) {
        val normalizedId = roleId.trim().lowercase()
            .replace(Regex("[^a-z0-9_-]+"), "_")
            .trim('_')
        val trimmedName = name.trim()
        val trimmedPrompt = systemPrompt.trim()
        if (normalizedId.isBlank() || trimmedName.isBlank() || trimmedPrompt.isBlank()) return
        viewModelScope.launch {
            val profile = top.wkbin.taixu.core.model.AgentSubagent(
                id = normalizedId,
                name = trimmedName,
                description = description.trim().ifBlank { "自定义子智能体角色" },
                systemPrompt = trimmedPrompt,
                isEnabled = previous?.isEnabled ?: true,
                isBuiltin = previous?.isBuiltin ?: false,
                sortOrder = previous?.sortOrder ?: subagentRepository.nextSortOrder(),
            )
            subagentRepository.replace(previous?.id, profile)
        }
    }

    fun deleteSubagent(profileId: String) {
        viewModelScope.launch { subagentRepository.delete(profileId) }
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

    fun setQemuCompatibilityEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val distroId = linuxRuntime.activeDistroId.value
                if (!QemuCompatibilityLayout.isReady(pathManager.taixuRootDir(distroId))) {
                    _qemuCompatibilityMessage.value = "未检测到 QEMU x86_64 兼容环境，无法开启。请先在插件中心安装 qemu-x86-64-compat 插件。"
                    return@launch
                }
            }
            _qemuCompatibilityMessage.value = null
            settingsDataStore.setQemuCompatibilityEnabled(enabled)
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
        val cleanUrl = ProviderEndpointPolicy.normalizeUrl(baseUrl)
        if (!ProviderEndpointPolicy.isSafeBaseUrl(cleanUrl)) return
        _discoveringModels.value = true
        _modelDiscoveryError.value = null
        _discoveredModels.value = emptyList()
        viewModelScope.launch {
            val provider = providerCatalogRepository.find(providerId)
            val discoveryKey = parseApiKeys(apiKey).firstOrNull() ?: providerRepository.readApiKey()
            runCatching { modelDiscovery.discover(provider, cleanUrl, discoveryKey) }
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
            runCatching { connectionTester.test(baseUrl, model, parseApiKeys(apiKey).firstOrNull()) }
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
        requestsPerMinutePerKey: Int = 0,
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
            val submittedKeys = parseApiKeys(apiKey)
            val existingKeys = old?.let { providerRepository.readModelApiKeys(secretRef) }.orEmpty()
            if (existing.none { it.isActive } || old?.isActive == true) aiModelDao.clearActive()
            aiModelDao.upsert(
                AiModelEntity(
                    id = modelId,
                    name = name.trim(),
                    provider = provider.trim(),
                    model = model.trim(),
                    baseUrl = baseUrl.trim(),
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
                    apiKeyCount = submittedKeys.ifEmpty { existingKeys }.size,
                    requestsPerMinutePerKey = requestsPerMinutePerKey.coerceAtLeast(0),
                ),
            )
            if (submittedKeys.isNotEmpty()) providerRepository.setModelApiKeys(secretRef, submittedKeys)
        }
    }

    private fun parseApiKeys(raw: String): List<String> = raw
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .toList()

    fun setActiveModel(id: String) {
        viewModelScope.launch {
            aiModelDao.clearActive()
            aiModelDao.setActive(id)
        }
    }

    fun deleteModel(id: String) {
        viewModelScope.launch {
            aiModelDao.findById(id)?.secretRef?.takeIf { it.isNotBlank() }?.let { providerRepository.removeModelApiKey(it) }
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

    val customMountBindings: StateFlow<List<top.wkbin.taixu.core.model.StorageMountBinding>> = storageMountBindingRepository.bindings
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
        viewModelScope.launch { storageMountBindingRepository.add(binding) }
    }

    fun removeCustomMountBinding(bindingId: String) {
        viewModelScope.launch { storageMountBindingRepository.remove(bindingId) }
    }

    fun toggleCustomMountBinding(bindingId: String, enabled: Boolean) {
        viewModelScope.launch { storageMountBindingRepository.setEnabled(bindingId, enabled) }
    }
}
