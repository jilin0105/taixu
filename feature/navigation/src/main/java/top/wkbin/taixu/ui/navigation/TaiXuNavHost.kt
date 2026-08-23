package top.wkbin.taixu.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import top.wkbin.taixu.ui.chat.ChatScreen
import top.wkbin.taixu.ui.chat.ChatViewModel
import top.wkbin.taixu.ui.components.MainDestination
import top.wkbin.taixu.ui.components.RuntimeBottomBar
import top.wkbin.taixu.ui.theme.LocalLiquidGlassBackdrop
import top.wkbin.taixu.ui.developer.DeveloperScreen
import top.wkbin.taixu.ui.home.HomeScreen
import top.wkbin.taixu.ui.settings.AgentSettingsScreen
import top.wkbin.taixu.ui.settings.ModelEditorScreen
import top.wkbin.taixu.ui.settings.ModelProfilesScreen
import top.wkbin.taixu.ui.settings.SettingsScreen
import top.wkbin.taixu.ui.settings.ToolDetailScreen
import top.wkbin.taixu.ui.terminal.TerminalScreen
import top.wkbin.taixu.ui.workspace.CodeEditorScreen
import top.wkbin.taixu.ui.workspace.WorkspaceExplorerScreen
import top.wkbin.taixu.ui.workspace.WorkspaceScreen
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppDestination : NavKey

@Serializable data object HomeDestination : AppDestination
@Serializable data object AgentDestination : AppDestination
@Serializable data object WorkspaceDestination : AppDestination
@Serializable data class WorkspaceExplorerDestination(val projectName: String, val initialPath: String = "") : AppDestination
@Serializable data class CodeEditorDestination(val projectName: String, val relativePath: String) : AppDestination
@Serializable data object SettingsDestination : AppDestination
@Serializable data object AgentEcoSettingsDestination : AppDestination
@Serializable data object LinuxEnvSettingsDestination : AppDestination
@Serializable data object AppearanceSettingsDestination : AppDestination
@Serializable data object SystemDevSettingsDestination : AppDestination
@Serializable data object AboutCommunityDestination : AppDestination
@Serializable data object AgentSettingsDestination : AppDestination
@Serializable data object McpSettingsDestination : AppDestination
@Serializable data object ToolCenterDestination : AppDestination
@Serializable data class ToolDetailDestination(val toolId: String) : AppDestination
@Serializable data object DistroManagementDestination : AppDestination
@Serializable data object StorageMountSettingsDestination : AppDestination
@Serializable data object EnvironmentVariableSettingsDestination : AppDestination
@Serializable data object ModelProfilesDestination : AppDestination
@Serializable data class ModelEditorDestination(val modelId: String? = null) : AppDestination
@Serializable data object DeveloperDestination : AppDestination
@Serializable data class TerminalDestination(val toolId: String = "", val project: String = "") : AppDestination

/**
 * 太墟核心导航分发系统
 * 采用 Navigation 3，为每个 Tab 独立维护持久回退栈与状态生命周期
 */
@Composable
fun TaiXuNavHost() {
    val homeStack = rememberNavBackStack(HomeDestination)
    val agentStack = rememberNavBackStack(AgentDestination)
    val workspaceStack = rememberNavBackStack(WorkspaceDestination)
    val settingsStack = rememberNavBackStack(SettingsDestination)
    var pendingHealingTask by remember { mutableStateOf<HealingTask?>(null) }
    var selectedMain by rememberSaveable { mutableStateOf(MainDestination.Home) } // 默认进入太墟开辟主界

    val activeStack = when (selectedMain) {
        MainDestination.Home -> homeStack
        MainDestination.Agent -> agentStack
        MainDestination.Workspace -> workspaceStack
        MainDestination.Settings -> settingsStack
    }

    fun navigateMain(destination: MainDestination) {
        selectedMain = destination
    }

    fun NavBackStack<NavKey>.push(destination: NavKey) {
        if (lastOrNull() != destination) add(destination)
    }

    fun popBack() {
        if (activeStack.size > 1) activeStack.removeLastOrNull()
    }

    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = activeStack,
            modifier = Modifier.fillMaxSize(),
            onBack = ::popBack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
            entry<HomeDestination> {
                HomeScreen(
                    onNavigate = ::navigateMain,
                    onOpenTerminal = { homeStack.push(TerminalDestination()) },
                )
            }
            entry<AgentDestination> {
                val chatViewModel: ChatViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
                LaunchedEffect(pendingHealingTask) {
                    pendingHealingTask?.let { task ->
                        chatViewModel.startHealingTask(task.title, task.prompt)
                        pendingHealingTask = null
                    }
                }
                ChatScreen(
                    viewModel = chatViewModel,
                    onNavigate = ::navigateMain,
                    onOpenFile = { projectName, relativePath ->
                        selectedMain = MainDestination.Workspace
                        workspaceStack.push(CodeEditorDestination(projectName, relativePath))
                    },
                )
            }
            entry<WorkspaceDestination> {
                WorkspaceScreen(
                    onNavigate = ::navigateMain,
                    onOpenExplorer = { projectName -> workspaceStack.push(WorkspaceExplorerDestination(projectName)) },
                    onOpenTerminal = { project -> workspaceStack.push(TerminalDestination(project = project)) },
                    onOpenToolCenter = { workspaceStack.push(ToolCenterDestination) },
                )
            }
            entry<WorkspaceExplorerDestination> { destination ->
                WorkspaceExplorerScreen(
                    projectName = destination.projectName,
                    initialPath = destination.initialPath,
                    onBack = ::popBack,
                    onOpenFile = { relativePath ->
                        workspaceStack.push(CodeEditorDestination(destination.projectName, relativePath))
                    },
                    onOpenTerminal = { project ->
                        workspaceStack.push(TerminalDestination(project = project))
                    },
                )
            }
            entry<CodeEditorDestination> { destination ->
                CodeEditorScreen(
                    projectName = destination.projectName,
                    relativePath = destination.relativePath,
                    onBack = ::popBack,
                )
            }
            entry<SettingsDestination> {
                SettingsScreen(
                    onNavigate = ::navigateMain,
                    onOpenAgentEco = { settingsStack.push(AgentEcoSettingsDestination) },
                    onOpenLinuxEnv = { settingsStack.push(LinuxEnvSettingsDestination) },
                    onOpenAppearance = { settingsStack.push(AppearanceSettingsDestination) },
                    onOpenSystemDev = { settingsStack.push(SystemDevSettingsDestination) },
                    onOpenAboutCommunity = { settingsStack.push(AboutCommunityDestination) },
                )
            }
            entry<AppearanceSettingsDestination> {
                top.wkbin.taixu.ui.settings.AppearanceSettingsScreen(onBack = ::popBack)
            }
            entry<AgentEcoSettingsDestination> {
                top.wkbin.taixu.ui.settings.AgentEcoSettingsScreen(
                    onBack = ::popBack,
                    onOpenModelProfiles = { settingsStack.push(ModelProfilesDestination) },
                    onOpenToolCenter = { settingsStack.push(ToolCenterDestination) },
                    onOpenAgentSettings = { settingsStack.push(AgentSettingsDestination) },
                    onOpenMcpSettings = { settingsStack.push(McpSettingsDestination) },
                )
            }
            entry<LinuxEnvSettingsDestination> {
                top.wkbin.taixu.ui.settings.LinuxEnvironmentSettingsScreen(
                    onBack = ::popBack,
                    onOpenDistroManagement = { settingsStack.push(DistroManagementDestination) },
                    onOpenStorageMounts = { settingsStack.push(StorageMountSettingsDestination) },
                    onOpenEnvironmentVariables = { settingsStack.push(EnvironmentVariableSettingsDestination) },
                )
            }
            entry<SystemDevSettingsDestination> {
                top.wkbin.taixu.ui.settings.SystemDevSettingsScreen(
                    onBack = ::popBack,
                    onOpenDeveloper = { settingsStack.push(DeveloperDestination) },
                )
            }
            entry<AboutCommunityDestination> {
                top.wkbin.taixu.ui.settings.AboutCommunityScreen(onBack = ::popBack)
            }
            entry<DistroManagementDestination> {
                top.wkbin.taixu.ui.settings.DistroManagementScreen(onBack = ::popBack)
            }
            entry<AgentSettingsDestination> {
                AgentSettingsScreen(onBack = ::popBack)
            }
            entry<McpSettingsDestination> {
                top.wkbin.taixu.ui.settings.McpSettingsScreen(onBack = ::popBack)
            }
            entry<ToolCenterDestination> {
                top.wkbin.taixu.ui.settings.ToolCenterScreen(
                    onBack = ::popBack,
                    onLaunchPty = { toolId -> homeStack.push(TerminalDestination(toolId = toolId)) },
                    onOpenToolDetail = { toolId -> settingsStack.push(ToolDetailDestination(toolId = toolId)) },
                    onStartAiHealing = { toolId, toolName, logs ->
                        val prompt = top.wkbin.taixu.ui.settings.ToolSelfHealingHelper.buildHealingPrompt(toolId, toolName, logs)
                        pendingHealingTask = HealingTask("🔧 自愈: $toolName", prompt)
                        selectedMain = MainDestination.Agent
                    },
                )
            }
            entry<ToolDetailDestination> { destination ->
                top.wkbin.taixu.ui.settings.ToolDetailScreen(
                    toolId = destination.toolId,
                    onBack = ::popBack,
                    onLaunchTerminal = { toolId -> homeStack.push(TerminalDestination(toolId = toolId)) },
                    onStartAiHealing = { toolId, toolName, logs ->
                        val prompt = top.wkbin.taixu.ui.settings.ToolSelfHealingHelper.buildHealingPrompt(toolId, toolName, logs)
                        pendingHealingTask = HealingTask("🔧 自愈: $toolName", prompt)
                        selectedMain = MainDestination.Agent
                    },
                )
            }
            entry<StorageMountSettingsDestination> {
                top.wkbin.taixu.ui.settings.StorageMountSettingsScreen(onBack = ::popBack)
            }
            entry<EnvironmentVariableSettingsDestination> {
                top.wkbin.taixu.ui.settings.EnvironmentVariableSettingsScreen(onBack = ::popBack)
            }
            entry<ModelProfilesDestination> {
                ModelProfilesScreen(
                    onBack = ::popBack,
                    onCreate = { settingsStack.push(ModelEditorDestination()) },
                    onEdit = { modelId -> settingsStack.push(ModelEditorDestination(modelId)) },
                )
            }
            entry<ModelEditorDestination> { destination ->
                ModelEditorScreen(
                    modelId = destination.modelId,
                    onBack = ::popBack,
                    onSaved = ::popBack,
                )
            }
            entry<DeveloperDestination> {
                DeveloperScreen(onBack = ::popBack)
            }
            entry<TerminalDestination> { destination ->
                TerminalScreen(onBack = ::popBack, project = destination.project)
            }
            },
        )
        if (LocalLiquidGlassBackdrop.current != null && activeStack.size == 1 && WindowInsets.ime.getBottom(density) == 0) {
            // Keep placement in the NavHost's Box. Passing BoxScope alignment through the
            // glass component's modifier chain can leave the bar at the top on some devices.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                RuntimeBottomBar(
                    selected = selectedMain,
                    onNavigate = ::navigateMain,
                )
            }
        }
    }
}

private data class HealingTask(
    val title: String,
    val prompt: String,
)
