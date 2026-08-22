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

    // ==================== 开发环境套件聚合管理 ====================
    val devSuites: List<top.wkbin.taixu.core.model.DevEnvironmentSuite> = top.wkbin.taixu.core.model.BuiltinDevSuites.presets
    private val _showSuiteDialog = MutableStateFlow(false)
    val showSuiteDialog: StateFlow<Boolean> = _showSuiteDialog.asStateFlow()

    private val _selectedSuites = MutableStateFlow<Set<String>>(
        top.wkbin.taixu.core.model.BuiltinDevSuites.presets.filter { it.isDefaultSelected }.map { it.id }.toSet(),
    )
    val selectedSuites: StateFlow<Set<String>> = _selectedSuites.asStateFlow()

    private val _isInstallingSuites = MutableStateFlow(false)
    val isInstallingSuites: StateFlow<Boolean> = _isInstallingSuites.asStateFlow()

    private val _suiteInstallProgress = MutableStateFlow<String?>(null)
    val suiteInstallProgress: StateFlow<String?> = _suiteInstallProgress.asStateFlow()

    fun openSuiteDialog() {
        _showSuiteDialog.value = true
    }

    fun closeSuiteDialog() {
        if (!_isInstallingSuites.value) {
            _showSuiteDialog.value = false
        }
    }

    fun toggleSuite(id: String) {
        val current = _selectedSuites.value
        _selectedSuites.value = if (id in current) current - id else current + id
    }

    fun installSelectedSuites() {
        val selected = _selectedSuites.value.toSet()
        if (selected.isEmpty()) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isInstallingSuites.value = true
            try {
                toolManager.batchInstallSuites(selected).collect { event ->
                    if (event is top.wkbin.taixu.runtime.tools.InstallEvent.Progress) {
                        _suiteInstallProgress.value = event.message
                    }
                }
                syncRegistry()
                _showSuiteDialog.value = false
            } catch (e: Exception) {
                logger.w("Batch install suites failed: ${e.message}", e)
            } finally {
                _isInstallingSuites.value = false
                _suiteInstallProgress.value = null
            }
        }
    }
}
