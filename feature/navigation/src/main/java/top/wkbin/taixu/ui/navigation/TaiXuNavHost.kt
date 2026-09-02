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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
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
import top.wkbin.taixu.ui.settings.LocalLlmScreen
import top.wkbin.taixu.ui.settings.SettingsScreen
import top.wkbin.taixu.ui.settings.SettingsViewModel
import top.wkbin.taixu.ui.settings.ToolDetailScreen
import top.wkbin.taixu.ui.iteration.CustomIterationScreen
import top.wkbin.taixu.ui.terminal.TerminalScreen
import top.wkbin.taixu.ui.browser.BrowserScreen
import top.wkbin.taixu.ui.navigation.BrowserDestination
import top.wkbin.taixu.ui.workspace.CodeEditorScreen
import top.wkbin.taixu.ui.workspace.WorkspaceExplorerScreen
import top.wkbin.taixu.ui.workspace.WorkspaceScreen
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppDestination : NavKey

@Serializable data object HomeDestination : AppDestination
@Serializable data object AgentDestination : AppDestination
@Serializable data object WorkspaceDestination : AppDestination
@Serializable data object WorkshopSettingsDestination : AppDestination
@Serializable data object WorkshopEnvironmentSettingsDestination : AppDestination
@Serializable data object WorkshopSigningSettingsDestination : AppDestination
@Serializable data class WorkshopScriptEditorDestination(val type: String) : AppDestination
@Serializable data class WorkspaceExplorerDestination(val projectName: String, val initialPath: String = "") : AppDestination
@Serializable data class CodeEditorDestination(val projectName: String, val relativePath: String) : AppDestination
@Serializable data object SettingsDestination : AppDestination
@Serializable data object AgentEcoSettingsDestination : AppDestination
@Serializable data object LinuxEnvSettingsDestination : AppDestination
@Serializable data object AppearanceSettingsDestination : AppDestination
@Serializable data object SystemDevSettingsDestination : AppDestination
@Serializable data object AboutCommunityDestination : AppDestination
@Serializable data object SponsorDestination : AppDestination
@Serializable data object AgentSettingsDestination : AppDestination
@Serializable data object AgentSubagentSettingsDestination : AppDestination
@Serializable data object AgentSkillSettingsDestination : AppDestination
@Serializable data object McpSettingsDestination : AppDestination
@Serializable data object ToolCenterDestination : AppDestination
@Serializable data class ToolDetailDestination(val toolId: String) : AppDestination
@Serializable data object DistroManagementDestination : AppDestination
@Serializable data object StorageMountSettingsDestination : AppDestination
@Serializable data object StorageUsageDestination : AppDestination
@Serializable data object AppManagementDestination : AppDestination
@Serializable data object EnvironmentVariableSettingsDestination : AppDestination
@Serializable data object SshSettingsDestination : AppDestination
@Serializable data object ModelProfilesDestination : AppDestination
@Serializable data object LocalLlmDestination : AppDestination
@Serializable data class ModelEditorDestination(val modelId: String? = null) : AppDestination
@Serializable data object QuickPhrasesDestination : AppDestination
@Serializable data object StatsDestination : AppDestination
@Serializable data object PermissionGuideDestination : AppDestination
@Serializable data object DeveloperDestination : AppDestination
@Serializable data object CustomIterationDestination : AppDestination
@Serializable data class TerminalDestination(val toolId: String = "", val project: String = "") : AppDestination
@Serializable data object BrowserDestination : AppDestination

/**
 * 太墟核心导航分发系统
 * 采用 Navigation 3，为每个 Tab 独立维护持久回退栈与状态生命周期
 */
@Composable
fun TaiXuNavHost() {
    // Root tab entries are removed from composition when another tab becomes active. Keep the
    // conversation owner at the Activity scope so switching back to 智枢 does not rebuild Hilt's
    // graph, restore the latest session, and restart its initialization skeleton on every visit.
    val chatViewModel: ChatViewModel = hiltViewModel()
    // 浏览器引擎是全局单例：BrowserViewModel 同样挂到 Activity 作用域，
    // 使智枢内嵌浏览器面板与独立浏览器页共享同一份 tab/URL/共浏览状态。
    val browserViewModel: top.wkbin.taixu.ui.browser.BrowserViewModel = hiltViewModel()
    val browserUiState by browserViewModel.uiState.collectAsStateWithLifecycle()
    // SettingsViewModel owns dozens of eagerly shared DataStore/database streams and performs
    // repository initialization. Let the settings navigation graph share the Activity-scoped
    // instance instead of constructing that whole graph once for every Navigation3 entry.
    val settingsViewModel: SettingsViewModel = hiltViewModel()
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

    val appEntryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
            entry<HomeDestination> {
                HomeScreen(
                    onNavigate = ::navigateMain,
                    onOpenTerminal = { homeStack.push(TerminalDestination()) },
                    onOpenToolCenter = { homeStack.push(ToolCenterDestination) },
                )
            }
            entry<AgentDestination> {
                LaunchedEffect(pendingHealingTask) {
                    pendingHealingTask?.let { task ->
                        chatViewModel.startHealingTask(task.title, task.prompt)
                        pendingHealingTask = null
                    }
                }
                ChatScreen(
                    viewModel = chatViewModel,
                    onNavigate = ::navigateMain,
                    // 内嵌终端面板非独立导航节点，无返回目标：隐藏顶栏返回箭头，避免点击无反馈
                    terminalPane = { project -> TerminalScreen(onBack = {}, project = project, showBackButton = false) },
                    // 内嵌浏览器面板：与独立浏览器页共享 Activity 级 BrowserViewModel，
                    // 手机端左右滑动切换对话/浏览器，宽屏双栏可切"终端/浏览器"
                    browserPane = { onExit ->
                        top.wkbin.taixu.ui.browser.BrowserPane(
                            viewModel = browserViewModel,
                            onExit = onExit,
                        )
                    },
                    browserActivityTick = browserUiState.activityTick,
                    browserBackPressed = { browserViewModel.handleBackImmediate() },
                    onOpenFile = { projectName, relativePath ->
                        agentStack.push(CodeEditorDestination(projectName, relativePath))
                    },
                )
            }
            entry<WorkspaceDestination> {
                WorkspaceScreen(
                    onNavigate = ::navigateMain,
                    onOpenExplorer = { projectName -> workspaceStack.push(WorkspaceExplorerDestination(projectName)) },
                    onOpenTerminal = { project -> workspaceStack.push(TerminalDestination(project = project)) },
                    onOpenToolCenter = { workspaceStack.push(ToolCenterDestination) },
                    onOpenWorkshopSettings = { workspaceStack.push(WorkshopSettingsDestination) },
                )
            }
            entry<WorkshopSettingsDestination> {
                top.wkbin.taixu.ui.workspace.WorkshopSettingsScreen(
                    onBack = ::popBack,
                    onOpenEnvironment = { workspaceStack.push(WorkshopEnvironmentSettingsDestination) },
                    onOpenSigning = { workspaceStack.push(WorkshopSigningSettingsDestination) },
                    onEditScript = { type -> workspaceStack.push(WorkshopScriptEditorDestination(type.name)) },
                )
            }
            entry<WorkshopEnvironmentSettingsDestination> {
                top.wkbin.taixu.ui.workspace.WorkshopEnvironmentSettingsScreen(onBack = ::popBack)
            }
            entry<WorkshopSigningSettingsDestination> {
                top.wkbin.taixu.ui.workspace.WorkshopSigningScreen(onBack = ::popBack)
            }
            entry<WorkshopScriptEditorDestination> { destination ->
                top.wkbin.taixu.ui.workspace.WorkshopScriptEditorScreen(
                    type = top.wkbin.taixu.ui.workspace.WorkshopScriptType.valueOf(destination.type),
                    onBack = ::popBack,
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
                    viewModel = settingsViewModel,
                )
            }
            entry<AppearanceSettingsDestination> {
                top.wkbin.taixu.ui.settings.AppearanceSettingsScreen(
                    onBack = ::popBack,
                    viewModel = settingsViewModel,
                )
            }
            entry<AgentEcoSettingsDestination> {
                top.wkbin.taixu.ui.settings.AgentEcoSettingsScreen(
                    onBack = ::popBack,
                    onOpenModelProfiles = { settingsStack.push(ModelProfilesDestination) },
                    onOpenLocalLlm = { settingsStack.push(LocalLlmDestination) },
                    onOpenToolCenter = { settingsStack.push(ToolCenterDestination) },
                    onOpenAgentSettings = { settingsStack.push(AgentSettingsDestination) },
                    onOpenSubagentSettings = { settingsStack.push(AgentSubagentSettingsDestination) },
                    onOpenSkillSettings = { settingsStack.push(AgentSkillSettingsDestination) },
                    onOpenMcpSettings = { settingsStack.push(McpSettingsDestination) },
                    onOpenQuickPhrases = { settingsStack.push(QuickPhrasesDestination) },
                    onOpenStats = { settingsStack.push(StatsDestination) },
                    viewModel = settingsViewModel,
                )
            }
            entry<LinuxEnvSettingsDestination> {
                top.wkbin.taixu.ui.settings.LinuxEnvironmentSettingsScreen(
                    onBack = ::popBack,
                    onOpenDistroManagement = { settingsStack.push(DistroManagementDestination) },
                    onOpenStorageMounts = { settingsStack.push(StorageMountSettingsDestination) },
                    onOpenStorageUsage = { settingsStack.push(StorageUsageDestination) },
                    onOpenAppManagement = { settingsStack.push(AppManagementDestination) },
                    onOpenEnvironmentVariables = { settingsStack.push(EnvironmentVariableSettingsDestination) },
                    onOpenSshSettings = { settingsStack.push(SshSettingsDestination) },
                    viewModel = settingsViewModel,
                )
            }
            entry<SystemDevSettingsDestination> {
                top.wkbin.taixu.ui.settings.SystemDevSettingsScreen(
                    onBack = ::popBack,
                    onOpenDeveloper = { settingsStack.push(DeveloperDestination) },
                    onOpenCustomIteration = { settingsStack.push(CustomIterationDestination) },
                    onOpenPermissionGuide = { settingsStack.push(PermissionGuideDestination) },
                    viewModel = settingsViewModel,
                )
            }
            entry<PermissionGuideDestination> {
                top.wkbin.taixu.ui.settings.permission.PermissionGuideScreen(
                    onBack = ::popBack,
                )
            }
            entry<AboutCommunityDestination> {
                top.wkbin.taixu.ui.settings.AboutCommunityScreen(
                    onBack = ::popBack,
                    onOpenSponsor = { settingsStack.push(SponsorDestination) },
                    viewModel = settingsViewModel,
                )
            }
            entry<SponsorDestination> {
                top.wkbin.taixu.ui.settings.SponsorScreen(onBack = ::popBack)
            }
            entry<DistroManagementDestination> {
                top.wkbin.taixu.ui.settings.DistroManagementScreen(
                    onBack = ::popBack,
                    viewModel = settingsViewModel,
                )
            }
            entry<AgentSettingsDestination> {
                AgentSettingsScreen(
                    onBack = ::popBack,
                    category = top.wkbin.taixu.ui.settings.AgentSettingsCategory.EXECUTION,
                    viewModel = settingsViewModel,
                )
            }
            entry<AgentSubagentSettingsDestination> {
                AgentSettingsScreen(onBack = ::popBack, category = top.wkbin.taixu.ui.settings.AgentSettingsCategory.SUBAGENTS, viewModel = settingsViewModel)
            }
            entry<AgentSkillSettingsDestination> {
                AgentSettingsScreen(onBack = ::popBack, category = top.wkbin.taixu.ui.settings.AgentSettingsCategory.SKILLS, viewModel = settingsViewModel)
            }
            entry<McpSettingsDestination> {
                top.wkbin.taixu.ui.settings.McpSettingsScreen(
                    onBack = ::popBack,
                    viewModel = settingsViewModel,
                )
            }
            entry<ToolCenterDestination> {
                top.wkbin.taixu.ui.settings.ToolCenterScreen(
                    onBack = ::popBack,
                    onLaunchPty = { toolId -> activeStack.push(TerminalDestination(toolId = toolId)) },
                    onOpenToolDetail = { toolId -> activeStack.push(ToolDetailDestination(toolId = toolId)) },
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
                    onLaunchTerminal = { toolId -> activeStack.push(TerminalDestination(toolId = toolId)) },
                    onStartAiHealing = { toolId, toolName, logs ->
                        val prompt = top.wkbin.taixu.ui.settings.ToolSelfHealingHelper.buildHealingPrompt(toolId, toolName, logs)
                        pendingHealingTask = HealingTask("🔧 自愈: $toolName", prompt)
                        selectedMain = MainDestination.Agent
                    },
                )
            }
            entry<StorageMountSettingsDestination> {
                top.wkbin.taixu.ui.settings.StorageMountSettingsScreen(
                    onBack = ::popBack,
                    viewModel = settingsViewModel,
                )
            }
            entry<StorageUsageDestination> {
                top.wkbin.taixu.ui.settings.StorageUsageScreen(onBack = ::popBack)
            }
            entry<AppManagementDestination> {
                top.wkbin.taixu.ui.settings.AppManagementScreen(onBack = ::popBack)
            }
            entry<EnvironmentVariableSettingsDestination> {
                top.wkbin.taixu.ui.settings.EnvironmentVariableSettingsScreen(
                    onBack = ::popBack,
                    viewModel = settingsViewModel,
                )
            }
            entry<SshSettingsDestination> {
                top.wkbin.taixu.ui.settings.SshSettingsScreen(onBack = ::popBack)
            }
            entry<ModelProfilesDestination> {
                ModelProfilesScreen(
                    onBack = ::popBack,
                    onCreate = { settingsStack.push(ModelEditorDestination()) },
                    onEdit = { modelId -> settingsStack.push(ModelEditorDestination(modelId)) },
                    viewModel = settingsViewModel,
                )
            }
            entry<LocalLlmDestination> {
                LocalLlmScreen(
                    onBack = ::popBack,
                    onOpenEngine = { settingsStack.push(ToolDetailDestination("llama-cpp")) },
                )
            }
            entry<ModelEditorDestination> { destination ->
                ModelEditorScreen(
                    modelId = destination.modelId,
                    onBack = ::popBack,
                    onSaved = ::popBack,
                    viewModel = settingsViewModel,
                )
            }
            entry<QuickPhrasesDestination> {
                top.wkbin.taixu.ui.settings.QuickPhrasesScreen(
                    onBack = ::popBack,
                    viewModel = settingsViewModel,
                )
            }
            entry<StatsDestination> {
                top.wkbin.taixu.ui.settings.stats.StatsScreen(onBack = ::popBack)
            }
            entry<DeveloperDestination> {
                DeveloperScreen(onBack = ::popBack)
            }
            entry<CustomIterationDestination> {
                CustomIterationScreen(
                    onBack = ::popBack,
                    onNavigateToChat = { prompt ->
                        pendingHealingTask = HealingTask("🚀 自定义迭代", prompt)
                        selectedMain = MainDestination.Agent
                    },
                )
            }
            entry<TerminalDestination> { destination ->
                TerminalScreen(onBack = ::popBack, project = destination.project)
            }
            entry<BrowserDestination> { BrowserScreen(onBack = ::popBack, viewModel = browserViewModel) }
    }

    val density = LocalDensity.current
    val liquidGlassBackdrop = LocalLiquidGlassBackdrop.current
    val showLiquidBottomBar = liquidGlassBackdrop != null &&
        activeStack.size == 1 &&
        WindowInsets.ime.getBottom(density) == 0
    Box(Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = activeStack,
            modifier = Modifier.fillMaxSize(),
            onBack = ::popBack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = appEntryProvider,
        )
        if (liquidGlassBackdrop != null) {
            // Keep the expensive glass layers composed while a secondary destination is open.
            // Recreating both backdrop render layers in the same frame as the root screen was
            // the main source of pop-navigation stalls. Moving the retained bar off-screen also
            // prevents its invisible click targets from intercepting the secondary page.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .zIndex(if (showLiquidBottomBar) 1f else -1f)
                    .graphicsLayer {
                        alpha = if (showLiquidBottomBar) 1f else 0f
                        translationY = if (showLiquidBottomBar) 0f else size.height
                    },
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
