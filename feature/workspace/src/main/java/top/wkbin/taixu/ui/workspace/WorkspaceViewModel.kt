package top.wkbin.taixu.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.wkbin.taixu.runtime.WorkspaceFileItem
import top.wkbin.taixu.runtime.WorkspaceManager
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.runtime.WorkspaceStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val workspaceManager: WorkspaceManager,
    private val workspaceBuildRunner: top.wkbin.taixu.runtime.build.WorkspaceBuildRunner,
    private val toolManager: top.wkbin.taixu.core.tools.ToolManager,
) : ViewModel() {

    // ==================== 开发环境套件弹窗状态 ====================
    val devSuites: List<top.wkbin.taixu.core.model.DevEnvironmentSuite> = top.wkbin.taixu.core.model.BuiltinDevSuites.presets
    private val _showDevSuiteDialog = MutableStateFlow(false)
    val showDevSuiteDialog: StateFlow<Boolean> = _showDevSuiteDialog.asStateFlow()

    private val _selectedDevSuites = MutableStateFlow<Set<String>>(emptySet())
    val selectedDevSuites: StateFlow<Set<String>> = _selectedDevSuites.asStateFlow()

    private val _isInstallingSuites = MutableStateFlow(false)
    val isInstallingSuites: StateFlow<Boolean> = _isInstallingSuites.asStateFlow()

    private val _suiteInstallProgress = MutableStateFlow<String?>(null)
    val suiteInstallProgress: StateFlow<String?> = _suiteInstallProgress.asStateFlow()

    fun openDevEnvironmentDialog(suggestedSuiteId: String? = null) {
        val initial = if (suggestedSuiteId != null) {
            when (suggestedSuiteId) {
                "android_sdk" -> setOf("jdk17", "android_sdk")
                "flutter" -> setOf("jdk17", "android_sdk", "flutter")
                else -> setOf(suggestedSuiteId)
            }
        } else {
            devSuites.filter { it.isDefaultSelected }.map { it.id }.toSet()
        }
        _selectedDevSuites.value = initial
        _showDevSuiteDialog.value = true
    }

    fun closeDevEnvironmentDialog() {
        if (!_isInstallingSuites.value) {
            _showDevSuiteDialog.value = false
        }
    }

    fun toggleDevSuite(suiteId: String) {
        val current = _selectedDevSuites.value
        _selectedDevSuites.value = if (suiteId in current) current - suiteId else current + suiteId
    }

    fun installSelectedSuites() {
        val selected = _selectedDevSuites.value.toSet()
        if (selected.isEmpty()) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isInstallingSuites.value = true
            try {
                toolManager.batchInstallSuites(selected).collect { event ->
                    if (event is top.wkbin.taixu.runtime.tools.InstallEvent.Progress) {
                        _suiteInstallProgress.value = event.message
                    }
                }
                _message.value = "开发环境已成功部署！"
                _showDevSuiteDialog.value = false
            } catch (e: Exception) {
                _message.value = "部署失败：${e.message}"
            } finally {
                _isInstallingSuites.value = false
                _suiteInstallProgress.value = null
            }
        }
    }

    // ==================== 项目列表状态 ====================
    val projects: StateFlow<List<WorkspaceProject>> = workspaceManager.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    // 运行/构建状态
    private val _buildProgress = MutableStateFlow<top.wkbin.taixu.runtime.build.BuildRunProgress?>(null)
    val buildProgress: StateFlow<top.wkbin.taixu.runtime.build.BuildRunProgress?> = _buildProgress.asStateFlow()

    // ==================== 文件浏览器状态 ====================
    private val _selectedProject = MutableStateFlow<String?>(null)
    val selectedProject: StateFlow<String?> = _selectedProject.asStateFlow()

    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _fileItems = MutableStateFlow<List<WorkspaceFileItem>>(emptyList())
    val fileItems: StateFlow<List<WorkspaceFileItem>> = _fileItems.asStateFlow()

    private val _loadingFiles = MutableStateFlow(false)
    val loadingFiles: StateFlow<Boolean> = _loadingFiles.asStateFlow()

    // ==================== 代码编辑器状态 ====================
    private val _openedFilePath = MutableStateFlow<String?>(null)
    val openedFilePath: StateFlow<String?> = _openedFilePath.asStateFlow()

    private val _openedFileExtension = MutableStateFlow("")
    val openedFileExtension: StateFlow<String> = _openedFileExtension.asStateFlow()

    private val _fileContent = MutableStateFlow("")
    val fileContent: StateFlow<String> = _fileContent.asStateFlow()

    private var originalContent: String = ""

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    init {
        refresh()
    }

    fun clearMessage() {
        _message.value = null
    }

    fun dismissBuildProgress() {
        _buildProgress.value = null
    }

    fun refresh() {
        viewModelScope.launch {
            workspaceManager.listProjects()
        }
    }

    // ==================== 项目级操作 ====================

    fun create(
        name: String,
        storage: WorkspaceStorage,
        directoryPath: String,
        template: top.wkbin.taixu.runtime.ProjectTemplate = top.wkbin.taixu.runtime.ProjectTemplate.EMPTY,
        packageName: String = "",
    ) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = workspaceManager.createProject(name, storage, directoryPath, template, packageName)
            _message.value = result.errorOrNull()?.message ?: "项目已创建，目录已关联"
            if (result.isSuccess) workspaceManager.listProjects()
            _busy.value = false
        }
    }

    fun runProject(project: WorkspaceProject) {
        if (_buildProgress.value?.isRunning == true) return
        viewModelScope.launch {
            workspaceBuildRunner.runProject(project).collect { progress ->
                _buildProgress.value = progress
            }
        }
    }

    fun launchInstaller(apkPath: String) {
        val file = java.io.File(apkPath)
        if (file.exists()) {
            workspaceBuildRunner.launchPackageInstaller(file)
        }
    }

    fun delete(name: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = workspaceManager.deleteProject(name)
            _message.value = result.errorOrNull()?.message ?: "项目已删除"
            if (result.isSuccess) workspaceManager.listProjects()
            _busy.value = false
        }
    }

    // ==================== 文件浏览器操作 ====================

    fun loadExplorer(projectName: String, relativePath: String = "") {
        _selectedProject.value = projectName
        _currentPath.value = relativePath.trim().removePrefix("/")
        refreshDirectory()
    }

    fun navigateToDirectory(relativePath: String) {
        _currentPath.value = relativePath.trim().removePrefix("/")
        refreshDirectory()
    }

    fun navigateUp() {
        val current = _currentPath.value
        if (current.isBlank()) return
        val parent = current.substringBeforeLast('/', "")
        _currentPath.value = parent
        refreshDirectory()
    }

    fun refreshDirectory() {
        val proj = _selectedProject.value ?: return
        val path = _currentPath.value
        viewModelScope.launch {
            _loadingFiles.value = true
            val result = workspaceManager.listFiles(proj, path)
            if (result.isSuccess) {
                _fileItems.value = result.getOrNull().orEmpty()
            } else {
                _message.value = result.errorOrNull()?.message ?: "读取目录失败"
            }
            _loadingFiles.value = false
        }
    }

    fun createFile(name: String) {
        val proj = _selectedProject.value ?: return
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val fullRelative = if (_currentPath.value.isBlank()) trimmed else "${_currentPath.value}/$trimmed"
        viewModelScope.launch {
            _busy.value = true
            val result = workspaceManager.createFile(proj, fullRelative)
            if (result.isSuccess) {
                _message.value = "文件已创建：$trimmed"
                refreshDirectory()
            } else {
                _message.value = result.errorOrNull()?.message ?: "创建文件失败"
            }
            _busy.value = false
        }
    }

    fun createDirectory(name: String) {
        val proj = _selectedProject.value ?: return
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val fullRelative = if (_currentPath.value.isBlank()) trimmed else "${_currentPath.value}/$trimmed"
        viewModelScope.launch {
            _busy.value = true
            val result = workspaceManager.createDirectory(proj, fullRelative)
            if (result.isSuccess) {
                _message.value = "目录已创建：$trimmed"
                refreshDirectory()
            } else {
                _message.value = result.errorOrNull()?.message ?: "创建目录失败"
            }
            _busy.value = false
        }
    }

    fun renameItem(oldRelativePath: String, newName: String) {
        val proj = _selectedProject.value ?: return
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            _busy.value = true
            val result = workspaceManager.renameItem(proj, oldRelativePath, trimmed)
            if (result.isSuccess) {
                _message.value = "已重命名为：$trimmed"
                refreshDirectory()
            } else {
                _message.value = result.errorOrNull()?.message ?: "重命名失败"
            }
            _busy.value = false
        }
    }

    fun deleteItem(relativePath: String) {
        val proj = _selectedProject.value ?: return
        viewModelScope.launch {
            _busy.value = true
            val result = workspaceManager.deleteItem(proj, relativePath)
            if (result.isSuccess) {
                _message.value = "已删除"
                refreshDirectory()
            } else {
                _message.value = result.errorOrNull()?.message ?: "删除失败"
            }
            _busy.value = false
        }
    }

    // ==================== 编辑器操作 ====================

    fun openFile(projectName: String, relativePath: String) {
        _selectedProject.value = projectName
        _openedFilePath.value = relativePath
        val ext = relativePath.substringAfterLast('.', "")
        _openedFileExtension.value = ext
        viewModelScope.launch {
            _loadingFiles.value = true
            val result = workspaceManager.readFile(projectName, relativePath)
            if (result.isSuccess) {
                val content = result.getOrNull().orEmpty()
                originalContent = content
                _fileContent.value = content
                _isDirty.value = false
            } else {
                _message.value = result.errorOrNull()?.message ?: "打开文件失败"
            }
            _loadingFiles.value = false
        }
    }

    fun onContentChanged(newText: String) {
        _fileContent.value = newText
        _isDirty.value = newText != originalContent
    }

    fun resetContent() {
        _fileContent.value = originalContent
        _isDirty.value = false
    }

    fun saveFile(onSuccess: (() -> Unit)? = null) {
        val proj = _selectedProject.value ?: return
        val path = _openedFilePath.value ?: return
        val text = _fileContent.value
        viewModelScope.launch {
            _isSaving.value = true
            val result = workspaceManager.writeFile(proj, path, text)
            if (result.isSuccess) {
                originalContent = text
                _isDirty.value = false
                _message.value = "已保存文件"
                onSuccess?.invoke()
            } else {
                _message.value = result.errorOrNull()?.message ?: "保存失败"
            }
            _isSaving.value = false
        }
    }

    fun closeFile() {
        _openedFilePath.value = null
        _fileContent.value = ""
        originalContent = ""
        _isDirty.value = false
    }
}
