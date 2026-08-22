package top.wkbin.taixu.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.InstallLogEntity
import top.wkbin.taixu.core.database.ToolEntity
import top.wkbin.taixu.core.tools.ToolInstallProgress
import top.wkbin.taixu.core.tools.ToolManager
import top.wkbin.taixu.core.tools.ToolVerification
import top.wkbin.taixu.runtime.LinuxRuntime
import javax.inject.Inject

@HiltViewModel
class ToolCenterViewModel @Inject constructor(
    private val toolManager: ToolManager,
    private val linuxRuntime: LinuxRuntime,
    private val logger: AppLogger,
) : ViewModel() {

    val tools: StateFlow<List<ToolEntity>> = toolManager.observeTools()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installProgress: StateFlow<Map<String, ToolInstallProgress>> = toolManager.installProgress
    val verifications: StateFlow<Map<String, ToolVerification>> = toolManager.verifications

    private val _selectedCategory = MutableStateFlow("ALL")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _viewingLogsToolId = MutableStateFlow<String?>(null)
    val viewingLogsToolId: StateFlow<String?> = _viewingLogsToolId.asStateFlow()

    private val _toolLogs = MutableStateFlow<List<InstallLogEntity>>(emptyList())
    val toolLogs: StateFlow<List<InstallLogEntity>> = _toolLogs.asStateFlow()

    init {
        syncRegistry()
    }

    fun syncRegistry() {
        viewModelScope.launch {
            try {
                toolManager.syncRegistry()
            } catch (e: Exception) {
                logger.w("Failed to sync tool registry: ${e.message}", e)
            }
        }
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun installTool(toolId: String) {
        toolManager.startInstall(toolId)
    }

    fun updateTool(toolId: String) {
        toolManager.startUpdate(toolId)
    }

    fun uninstallTool(toolId: String) {
        viewModelScope.launch {
            try {
                toolManager.uninstall(toolId)
            } catch (e: Exception) {
                logger.w("Tool uninstall failed: $toolId, ${e.message}", e)
            }
        }
    }

    fun verifyTool(toolId: String) {
        viewModelScope.launch {
            try {
                toolManager.verify(toolId)
            } catch (e: Exception) {
                logger.w("Tool verification failed: $toolId, ${e.message}", e)
            }
        }
    }

    fun clearLogs(toolId: String) {
        viewModelScope.launch {
            try {
                toolManager.clearLogs(toolId)
                _toolLogs.value = emptyList()
            } catch (e: Exception) {
                logger.w("Failed to clear logs for $toolId: ${e.message}", e)
            }
        }
    }

    fun viewLogs(toolId: String?) {
        _viewingLogsToolId.value = toolId
        if (toolId != null) {
            viewModelScope.launch {
                toolManager.observeInstallLogs(toolId).collect { logs ->
                    _toolLogs.value = logs
                }
            }
        }
    }

    init {
        syncRegistry()
        refreshInstalledStatus()
    }

    // ==================== 聚合大插件套件与子组件状态管理 ====================
    val pluginBundles: List<top.wkbin.taixu.core.model.PluginBundle> = top.wkbin.taixu.core.model.BuiltinPluginBundles.bundles

    private val _installedComponentIds = MutableStateFlow<Set<String>>(emptySet())
    val installedComponentIds: StateFlow<Set<String>> = _installedComponentIds.asStateFlow()

    private val _activeBundle = MutableStateFlow<top.wkbin.taixu.core.model.PluginBundle?>(null)
    val activeBundle: StateFlow<top.wkbin.taixu.core.model.PluginBundle?> = _activeBundle.asStateFlow()

    private val _selectedComponents = MutableStateFlow<Set<String>>(emptySet())
    val selectedComponents: StateFlow<Set<String>> = _selectedComponents.asStateFlow()

    val isInstallingComponents: StateFlow<Boolean> = toolManager.isBatchInstalling
    val componentInstallProgress: StateFlow<String?> = toolManager.bundleInstallState

    fun refreshInstalledStatus() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val installed = toolManager.probeInstalledComponents()
                _installedComponentIds.value = installed
            } catch (e: Exception) {
                logger.w("Failed to probe installed components: ${e.message}", e)
            }
        }
    }

    fun openBundleSetup(bundle: top.wkbin.taixu.core.model.PluginBundle) {
        val installed = _installedComponentIds.value
        // 必选组件强制预选 + 已安装的组件预选 + 默认勾选
        val initialSelected = bundle.components.filter { it.isRequired || it.id in installed }.map { it.id }.toSet()
        _selectedComponents.value = if (initialSelected.isEmpty()) bundle.components.map { it.id }.toSet() else initialSelected
        _activeBundle.value = bundle
    }

    fun closeBundleSetup() {
        _activeBundle.value = null
    }

    fun toggleComponent(component: top.wkbin.taixu.core.model.PluginComponent) {
        if (component.isRequired) return // 必选基础环境锁定，不可取消
        val current = _selectedComponents.value
        _selectedComponents.value = if (component.id in current) current - component.id else current + component.id
    }

    fun installActiveBundleComponents() {
        val selected = _selectedComponents.value
        if (selected.isEmpty()) return

        // 立即关闭装配弹窗，后台静默装配并发送系统通知栏进度
        _activeBundle.value = null

        toolManager.startBackgroundBatchInstall(selected) {
            refreshInstalledStatus()
            syncRegistry()
        }
    }

    // 兼容原 devSuites 接口
    val devSuites: List<top.wkbin.taixu.core.model.PluginBundle> get() = pluginBundles
    val showSuiteDialog: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    val selectedSuites: StateFlow<Set<String>> = MutableStateFlow(emptySet<String>()).asStateFlow()
    val isInstallingSuites: StateFlow<Boolean> get() = isInstallingComponents
    val suiteInstallProgress: StateFlow<String?> get() = componentInstallProgress
    fun openSuiteDialog() {
        pluginBundles.firstOrNull()?.let { openBundleSetup(it) }
    }
    fun closeSuiteDialog() = closeBundleSetup()
    fun toggleSuite(id: String) {}
    fun installSelectedSuites() {}
}
