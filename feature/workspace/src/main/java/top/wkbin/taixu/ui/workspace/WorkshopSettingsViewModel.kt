package top.wkbin.taixu.ui.workspace

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.datastore.WorkshopPreferences
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.ProjectType
import top.wkbin.taixu.runtime.WorkspaceManager
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.runtime.scripts.RuntimeAssetSynchronizer
import top.wkbin.taixu.runtime.shell.ShellCommand

data class WorkshopEnvironmentDraft(
    val androidSdkPath: String = "",
    val ndkPath: String = "",
    val flutterSdkPath: String = "",
    val javaPath: String = "",
    val androidScript: String = "",
    val flutterScript: String = "",
)

enum class WorkshopScriptType(val title: String, val defaultPath: String, val customPath: String) {
    ANDROID("Android 打包脚本", "/opt/taixu/scripts/build_android.sh", "/opt/taixu/scripts/workshop-build-android.sh"),
    FLUTTER("Flutter 打包脚本", "/opt/taixu/scripts/build_flutter.sh", "/opt/taixu/scripts/workshop-build-flutter.sh"),
}

@HiltViewModel
class WorkshopSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: WorkshopPreferences,
    private val linuxRuntime: LinuxRuntime,
    private val assetSynchronizer: RuntimeAssetSynchronizer,
    workspaceManager: WorkspaceManager,
) : ViewModel() {
    private val defaults = WorkshopEnvironmentDraft(
        androidSdkPath = "/opt/android-sdk",
        ndkPath = "/opt/taixu/toolchains/android/ndk",
        flutterSdkPath = "/opt/flutter",
        javaPath = "/opt/taixu/toolchains/android/jdk",
        androidScript = readDefaultScript("build_android.sh"),
        flutterScript = readDefaultScript("build_flutter.sh"),
    )
    private val _draft = MutableStateFlow(WorkshopEnvironmentDraft())
    val draft: StateFlow<WorkshopEnvironmentDraft> = _draft.asStateFlow()
    val projects: StateFlow<List<WorkspaceProject>> = workspaceManager.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _selectedProject = MutableStateFlow<WorkspaceProject?>(null)
    val selectedProject: StateFlow<WorkspaceProject?> = _selectedProject.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()
    private val _customScripts = MutableStateFlow<Set<WorkshopScriptType>>(emptySet())
    val customScripts: StateFlow<Set<WorkshopScriptType>> = _customScripts.asStateFlow()

    init { reload() }

    fun reload() = viewModelScope.launch {
        val storedAndroidScript = preferences.androidScript.first()
        val storedFlutterScript = preferences.flutterScript.first()
        _customScripts.value = buildSet {
            if (storedAndroidScript.isNotBlank()) add(WorkshopScriptType.ANDROID)
            if (storedFlutterScript.isNotBlank()) add(WorkshopScriptType.FLUTTER)
        }
        _draft.value = WorkshopEnvironmentDraft(
            preferences.androidSdkPath.first().ifBlank { defaults.androidSdkPath },
            preferences.ndkPath.first().ifBlank { defaults.ndkPath },
            preferences.flutterSdkPath.first().ifBlank { defaults.flutterSdkPath },
            preferences.javaPath.first().ifBlank { defaults.javaPath },
            storedAndroidScript.ifBlank { defaults.androidScript },
            storedFlutterScript.ifBlank { defaults.flutterScript },
        )
    }

    fun selectProject(project: WorkspaceProject) { _selectedProject.value = project }
    fun update(value: WorkshopEnvironmentDraft) { _draft.value = value }

    fun saveEnvironment() = viewModelScope.launch {
        val d = _draft.value
        preferences.setAndroidSdkPath(d.androidSdkPath)
        preferences.setNdkPath(d.ndkPath)
        preferences.setFlutterSdkPath(d.flutterSdkPath)
        preferences.setJavaPath(d.javaPath)
    }

    fun saveScripts() = viewModelScope.launch {
        val d = _draft.value
        preferences.setAndroidScript(d.androidScript)
        preferences.setFlutterScript(d.flutterScript)
        reload()
    }

    fun saveScript(type: WorkshopScriptType, content: String, onSaved: () -> Unit = {}) = viewModelScope.launch {
        when (type) {
            WorkshopScriptType.ANDROID -> preferences.setAndroidScript(content)
            WorkshopScriptType.FLUTTER -> preferences.setFlutterScript(content)
        }
        reload()
        onSaved()
    }

    fun resetScript(type: WorkshopScriptType, onReset: () -> Unit = {}) = viewModelScope.launch {
        when (type) {
            WorkshopScriptType.ANDROID -> preferences.setAndroidScript("")
            WorkshopScriptType.FLUTTER -> preferences.setFlutterScript("")
        }
        reload()
        onReset()
    }

    fun scriptContent(type: WorkshopScriptType): String = when (type) {
        WorkshopScriptType.ANDROID -> _draft.value.androidScript
        WorkshopScriptType.FLUTTER -> _draft.value.flutterScript
    }

    fun effectiveScriptPath(type: WorkshopScriptType): String =
        if (type in _customScripts.value) type.customPath else type.defaultPath

    fun resetEnvironment() = viewModelScope.launch {
        preferences.resetEnvironment()
        reload()
    }

    fun resetScripts() = viewModelScope.launch {
        preferences.resetScripts()
        reload()
    }

    fun runSelected() {
        val project = _selectedProject.value ?: return
        if (_running.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _running.value = true
            _output.value = ""
            val d = _draft.value
            val custom = if (project.projectType == ProjectType.FLUTTER) d.flutterScript else d.androidScript
            val fileName = if (project.projectType == ProjectType.FLUTTER) "workshop-build-flutter.sh" else "workshop-build-android.sh"
            val script = if (custom.isBlank()) "/opt/taixu/scripts/taixu-build.sh" else assetSynchronizer.syncWorkshopScript(linuxRuntime.activeDistroId.value, fileName, custom)
            val command = if (script.endsWith("taixu-build.sh")) {
                "/bin/sh $script ${if (project.projectType == ProjectType.FLUTTER) "flutter" else "android"} \"${project.linuxPath}\" ${if (project.projectType == ProjectType.FLUTTER) "apk --debug --target-platform android-arm64" else "assembleDebug"}"
            } else {
                "/bin/sh $script \"${project.linuxPath}\" ${if (project.projectType == ProjectType.FLUTTER) "\"apk --debug --target-platform android-arm64\"" else "assembleDebug"}"
            }
            val env = buildMap {
                d.androidSdkPath.takeIf(String::isNotBlank)?.let { put("ANDROID_HOME", it); put("ANDROID_SDK_ROOT", it) }
                d.ndkPath.takeIf(String::isNotBlank)?.let { put("ANDROID_NDK_HOME", it); put("TAIXU_NDK_PATH", it) }
                d.flutterSdkPath.takeIf(String::isNotBlank)?.let { put("FLUTTER_HOME", it) }
                d.javaPath.takeIf(String::isNotBlank)?.let { put("JAVA_HOME", it) }
            }
            val result = linuxRuntime.execute(ShellCommand(command, environment = env, forcePty = true, timeoutMs = 1_800_000L, onOutput = ::appendOutput))
            appendOutput("\n\n退出码: ${result.exitCode}\n${result.stderr}")
            _running.value = false
        }
    }

    private fun appendOutput(chunk: String) {
        val combined = _output.value + chunk
        _output.value = if (combined.length <= 60_000) combined else "…前面日志已截断…\n" + combined.takeLast(60_000)
    }

    private fun readDefaultScript(name: String): String = runCatching {
        context.assets.open("scripts/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrDefault("")
}
