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
}
