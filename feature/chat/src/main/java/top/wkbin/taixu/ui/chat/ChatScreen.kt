package top.wkbin.taixu.ui.chat

import top.wkbin.taixu.ui.components.RuntimeAlertDialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import androidx.compose.material3.ButtonDefaults
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import top.wkbin.taixu.ui.components.RuntimeIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton as OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeSwitch as Switch
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import top.wkbin.taixu.feature.chat.R
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.model.ApprovalMode
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.PendingMessage
import top.wkbin.taixu.harness.HarnessTool
import top.wkbin.taixu.harness.CapabilityEvent
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.harness.UserMessage
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.ui.components.MainDestination
import top.wkbin.taixu.ui.components.NoticeBanner
import top.wkbin.taixu.ui.components.RuntimeBottomBar
import top.wkbin.taixu.ui.components.liquidGlassContent
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.scrollFadingEdge
import top.wkbin.taixu.ui.theme.LocalLiquidGlassBackdrop
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.composed
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch

private val DotRunning = Color(0xFFB25E00)
private val DotSuccess = Color(0xFF2E7D32)
private val DotFailed = Color(0xFFBA1A1A)
private val AgentBottomBarHeight = 78.dp

@Composable
private fun chatBottomInsets(bottomBarHeight: Dp): WindowInsets {
    val bottomBarInsets = WindowInsets.navigationBars.add(WindowInsets(bottom = bottomBarHeight))
    return bottomBarInsets.union(WindowInsets.ime)
}

private val ApprovalMode.labelRes: Int
    get() = when (this) {
        ApprovalMode.REQUEST -> R.string.chat_approval_request
        ApprovalMode.ASSISTED -> R.string.chat_approval_assisted
        ApprovalMode.FULL_ACCESS -> R.string.chat_approval_full_access
    }

/**
 * 太墟 · 智枢对话界面 (TaiXu Agent)
 * 智能结对编程、工具自动化调用与代码生成
 */
@OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)
@Composable
fun ChatScreen(
    onNavigate: (MainDestination) -> Unit,
    onOpenFile: ((projectName: String, relativePath: String) -> Unit)? = null,
    terminalPane: (@Composable (project: String) -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val thinkingLive by viewModel.thinkingLive.collectAsStateWithLifecycle()
    val thinkingExpanded by viewModel.thinkingExpanded.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val providerModelIds by viewModel.providerModelIds.collectAsStateWithLifecycle()
    val discoveringProviderModels by viewModel.discoveringProviderModels.collectAsStateWithLifecycle()
    val providerModelDiscoveryError by viewModel.providerModelDiscoveryError.collectAsStateWithLifecycle()
    val modelPickerProfileId by viewModel.modelPickerProfileId.collectAsStateWithLifecycle()
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val sessionProjectType by viewModel.projectType.collectAsStateWithLifecycle()
    val matchingCommands by viewModel.matchingCommands.collectAsStateWithLifecycle()
    val matchingMentions by viewModel.matchingMentions.collectAsStateWithLifecycle()
    val attachedMentions by viewModel.attachedMentions.collectAsStateWithLifecycle()
    val queuedPrompts by viewModel.queuedPrompts.collectAsStateWithLifecycle()
    val sendMode by viewModel.sendMode.collectAsStateWithLifecycle()
    val branches by viewModel.branches.collectAsStateWithLifecycle()
    val runtimeEvents by viewModel.runtimeEvents.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val sessionRunStates by viewModel.sessionRunStates.collectAsStateWithLifecycle()
    val activeDistroId by viewModel.activeDistroId.collectAsStateWithLifecycle()
    val installedDistros by viewModel.installedDistros.collectAsStateWithLifecycle()
    val allSkills by viewModel.allSkills.collectAsStateWithLifecycle()
    val pinnedCapabilities by viewModel.pinnedCapabilities.collectAsStateWithLifecycle()
    val pinnedMentionIds by viewModel.pinnedMentionIds.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val scratchpads by viewModel.scratchpads.collectAsStateWithLifecycle()
    val mcpServers by viewModel.mcpServers.collectAsStateWithLifecycle()
    val mcpConnectionStates by viewModel.mcpConnectionStates.collectAsStateWithLifecycle()
    val initializing by viewModel.initializing.collectAsStateWithLifecycle()
    val pendingApprovals by viewModel.pendingApprovals.collectAsStateWithLifecycle()
    val activePlan by viewModel.activePlan.collectAsStateWithLifecycle()
    val activeCompaction by viewModel.activeCompaction.collectAsStateWithLifecycle()
    val contextUsage by viewModel.contextUsage.collectAsStateWithLifecycle()
    val quickPhrases by viewModel.quickPhrases.collectAsStateWithLifecycle()
    val onboardingPrivilege by viewModel.privilegeOnboarding.collectAsStateWithLifecycle()

    var showSessions by remember { mutableStateOf(false) }
    var showNewSession by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var showApprovalModes by remember { mutableStateOf(false) }
    var showSkillsMcpSheet by remember { mutableStateOf(false) }
    var showBranches by remember { mutableStateOf(false) }
    var showRuntimeTimeline by remember { mutableStateOf(false) }
    var showMemorySheet by remember { mutableStateOf(false) }
    var branchFromMessageId by remember { mutableStateOf<String?>(null) }
    var editTargetMessage by remember { mutableStateOf<UserMessage?>(null) }

    val activeSkillsCount = remember(allSkills) { allSkills.count { it.isEnabled } }
    val activeMcpCount = remember(mcpServers) { mcpServers.count { it.isEnabled } }

    val listState = rememberLazyListState()
    val context = LocalContext.current

    val activeModel = remember(models) { models.firstOrNull { it.isActive } }
    val currentSession = remember(sessions, currentSessionId) { sessions.firstOrNull { it.id == currentSessionId } }
    val currentApprovalMode = remember(currentSession?.approvalMode) { ApprovalMode.fromId(currentSession?.approvalMode) }
    val activeWorkspaceProject = remember(workspace, workspaces) {
        workspaces.firstOrNull { it.linuxPath == workspace }
    }
    val effectiveWorkspaceProject = remember(activeWorkspaceProject, sessionProjectType) {
        val override = when (sessionProjectType.uppercase()) {
            "ANDROID" -> top.wkbin.taixu.runtime.ProjectType.ANDROID
            "FLUTTER" -> top.wkbin.taixu.runtime.ProjectType.FLUTTER
            "REVERSE" -> top.wkbin.taixu.runtime.ProjectType.REVERSE
            "GENERAL" -> top.wkbin.taixu.runtime.ProjectType.GENERAL
            else -> null
        }
        activeWorkspaceProject?.let { project -> override?.let { project.copy(projectType = it) } ?: project }
    }
    val distroDisplayName = remember(activeDistroId) {
        runCatching { top.wkbin.taixu.runtime.DistributionCatalog.require(activeDistroId).displayName }
            .getOrDefault(activeDistroId)
    }

    val toolResults = remember(messages) {
        messages.filterIsInstance<ToolResult>().associateBy { it.toolCallId }
    }
    val liveThinkingMessageId = remember(messages) {
        messages.filterIsInstance<AssistantText>().lastOrNull()?.id
    }
    val lastAssistantMessageId = remember(messages) {
        messages.filterIsInstance<AssistantText>().lastOrNull()?.id
    }
    val currentBranch = remember(branches) { branches.firstOrNull { it.isCurrent } }

    val isImeVisible = WindowInsets.isImeVisible

    // Navigation3 removes inactive tab content from composition. Persist this marker with the
    // entry so returning to 智枢 does not perform a second, redundant scrollToItem during the
    // tab transition.
    var initialPositionedSessionKey by rememberSaveable { mutableStateOf<String?>(null) }
    val currentSessionKey = remember(messages) { messages.firstOrNull()?.id ?: "" }

    val lastMessageSignature = remember(messages) {
        val last = messages.lastOrNull()
        when (last) {
            is AssistantText -> "${last.id}:${last.reasoning?.length ?: 0}:${last.text.length}"
            is ToolCall -> "${last.id}:${last.args.hashCode()}"
            is ToolResult -> "${last.id}:${last.output.length}"
            is UserMessage -> "${last.id}:${last.text.length}"
            else -> "${messages.size}"
        }
    }

    // 🌟 1. 消息发送与流式输出跟随滚动
    // 仅当用户本来就停在底部附近时才跟随输出滚动；上滑阅读历史时不打扰，
    // 重新滑回底部（或新发消息）后恢复自动跟随。
    LaunchedEffect(messages.size, lastMessageSignature) {
        if (messages.isNotEmpty()) {
            delay(30)
            val totalCount = listState.layoutInfo.totalItemsCount
            if (totalCount > 0) {
                if (initialPositionedSessionKey != currentSessionKey) {
                    initialPositionedSessionKey = currentSessionKey
                    listState.scrollToItem(totalCount - 1)
                } else {
                    val layoutInfo = listState.layoutInfo
                    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
                    val isNearBottom = lastVisible == null || lastVisible.index >= totalCount - 3
                    if (isNearBottom) {
                        listState.scrollToItem(totalCount - 1)
                    }
                }
            }
        }
    }

    // 🌟 2. 软键盘弹起时自动平滑滚动定位到最后一条消息
    LaunchedEffect(isImeVisible) {
        if (isImeVisible && listState.layoutInfo.totalItemsCount > 0) {
            delay(80)
            val totalCount = listState.layoutInfo.totalItemsCount
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        viewModel.permissionRequests.collect { req ->
            val actionLabel = when (req.permission) {
                "WRITE_SETTINGS" -> "去授权"
                else -> "知道了"
            }
            val result = snackbarHostState.showSnackbar(
                message = req.reason,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed && req.permission == "WRITE_SETTINGS") {
                runCatching {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:${appContext.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(intent)
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            RuntimeTopBar(
                title = stringResource(R.string.chat_title),
                statusText = stringResource(R.string.chat_status, if (workspace.isNotBlank()) workspace else stringResource(R.string.chat_default_workspace), distroDisplayName),
            ) {
                // 🌟 1. 一体化模型切换胶囊
                val activeSub = activeModel?.let { entity ->
                    entity.model.split(",").firstOrNull()?.trim().takeUnless { it.isNullOrBlank() } ?: entity.name
                } ?: stringResource(R.string.chat_no_model_selected)

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.clickable { showModels = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = activeSub,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        RuntimeIcon(RuntimeIconName.ChevronDown, Modifier.size(11.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // 🌟 2. 智枢工作台与功能聚合菜单 (Workbench & Tools Menu)
                var showWorkbenchMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showWorkbenchMenu = true }) {
                        RuntimeIcon(
                            name = RuntimeIconName.Hub,
                            modifier = Modifier.size(19.dp),
                            tint = if (running) Color(0xFF7C4DFF) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = showWorkbenchMenu,
                        onDismissRequest = { showWorkbenchMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("分支调度", fontWeight = FontWeight.SemiBold)
                                    Text(currentBranch?.name ?: "主线分支", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            leadingIcon = { RuntimeIcon(RuntimeIconName.Hub, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showWorkbenchMenu = false
                                showBranches = true
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("运行事件与时间线", fontWeight = FontWeight.SemiBold)
                                    Text("${runtimeEvents.size} 个事件", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            leadingIcon = { RuntimeIcon(RuntimeIconName.Logs, Modifier.size(18.dp), tint = Color(0xFF7C4DFF)) },
                            onClick = {
                                showWorkbenchMenu = false
                                showRuntimeTimeline = true
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("工作区长期记忆", fontWeight = FontWeight.SemiBold)
                                    Text("${memories.size} 条记忆事实", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            leadingIcon = { RuntimeIcon(RuntimeIconName.Brain, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary) },
                            onClick = {
                                showWorkbenchMenu = false
                                showMemorySheet = true
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("专精技能与 MCP", fontWeight = FontWeight.SemiBold)
                                    Text("${activeSkillsCount + activeMcpCount} 项已激活", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            leadingIcon = { RuntimeIcon(RuntimeIconName.Extension, Modifier.size(18.dp), tint = Color(0xFF10B981)) },
                            onClick = {
                                showWorkbenchMenu = false
                                showSkillsMcpSheet = true
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("审批权限模式", fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(currentApprovalMode.labelRes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            leadingIcon = { RuntimeIcon(RuntimeIconName.Shield, Modifier.size(18.dp), tint = Color(0xFFFF9800)) },
                            onClick = {
                                showWorkbenchMenu = false
                                showApprovalModes = true
                            },
                        )
                    }
                }

                // 🌟 3. 会话抽屉/列表
                IconButton(onClick = { showSessions = true }) {
                    RuntimeIcon(RuntimeIconName.List, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // 🌟 4. 新建会话
                IconButton(onClick = { showNewSession = true }) {
                    RuntimeIcon(RuntimeIconName.Plus, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val isDualPane = maxWidth >= 720.dp

            val knownMentionNames = remember(allSkills, mcpServers) {
                (allSkills.filter { it.isEnabled }.flatMap { listOf(it.name, it.id) } +
                    mcpServers.filter { it.isEnabled }.flatMap { listOf(it.name, it.id) })
                    .filter { it.isNotBlank() }
                    .distinct()
            }

            val bottomInsets = chatBottomInsets(AgentBottomBarHeight)

            if (isDualPane && terminalPane != null) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .liquidGlassContent()
                        .padding(horizontal = 12.dp)
                        .windowInsetsPadding(bottomInsets),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 左栏：Agent 对话与指令区
                    ChatPaneContent(
                        onboardingPrivilege = onboardingPrivilege,
                        modifier = Modifier
                            .weight(0.48f)
                            .fillMaxHeight(),
                        messages = messages,
                        listState = listState,
                        running = running,
                        status = status,
                        thinkingExpanded = thinkingExpanded,
                        thinkingLive = thinkingLive,
                        liveThinkingMessageId = liveThinkingMessageId,
                        toolResults = toolResults,
                        workspace = workspace,
                        workspaceProject = effectiveWorkspaceProject,
                        onOpenFile = onOpenFile,
                        onEditMessage = { editTargetMessage = it },
                        onDeleteMessage = viewModel::deleteMessage,
                        error = error,
                        onClearError = viewModel::clearError,
                        matchingCommands = matchingCommands,
                        matchingMentions = matchingMentions,
                        attachedMentions = attachedMentions,
                        knownMentionNames = knownMentionNames,
                        queuedPrompts = queuedPrompts,
                        onEditQueuedPrompt = viewModel::editQueuedPrompt,
                        onRemoveQueuedPrompt = viewModel::removeQueuedPrompt,
                        sendMode = sendMode,
                        onSendModeChange = viewModel::setSendMode,
                        input = input,
                        onInputChanged = viewModel::onInputChanged,
                        onApplyCommand = viewModel::applySlashCommand,
                        onApplyMention = viewModel::applyMention,
                        onRemoveMention = viewModel::removeMention,
                        onTriggerMention = viewModel::triggerMentionInput,
                        attachments = pendingAttachments,
                        onAttachmentsPicked = viewModel::onAttachmentsPicked,
                        onRemoveAttachment = viewModel::removeAttachment,
                        onSend = viewModel::sendFromComposer,
                        onStop = viewModel::stop,
                        currentBranch = currentBranch,
                        runtimeEvents = runtimeEvents,
                        onOpenBranches = { showBranches = true },
                        onOpenRuntime = { showRuntimeTimeline = true },
                        lastAssistantMessageId = lastAssistantMessageId,
                        onRegenerate = viewModel::regenerateLast,
                        onCreateBranch = { branchFromMessageId = it },
                        onRetryTool = viewModel::retryToolCall,
                        initializing = initializing,
                        activeSkillsCount = activeSkillsCount,
                        activeMcpCount = activeMcpCount,
                        pinnedCapabilities = pinnedCapabilities,
                        onOpenSkillsMcp = { showSkillsMcpSheet = true },
                        onToggleSkill = viewModel::setSkillEnabled,
                        onToggleMcpServer = viewModel::setMcpServerEnabled,
                        onUnpinMention = viewModel::unpinMention,
                        onTogglePinMention = viewModel::togglePinMention,
                        activePlan = activePlan,
                        pendingApprovals = pendingApprovals,
                        onResolveApproval = viewModel::resolveApproval,
                        contextUsage = contextUsage,
                        activeModel = activeModel,
                        onUpdateReasoning = viewModel::updateActiveModelReasoning,
                        quickPhrases = quickPhrases,
                        onSelectPhrase = viewModel::applyQuickPhrase,
                        activeCompaction = activeCompaction,
                        memoryCount = memories.size,
                        scratchpadCount = scratchpads.size,
                        onOpenMemory = { showMemorySheet = true },
                    )

                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )

                    // 右栏：实时演武终端 (Terminal) 联动视窗
                    Box(
                        modifier = Modifier
                            .weight(0.52f)
                            .fillMaxHeight()
                            .padding(top = 4.dp, bottom = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                RoundedCornerShape(14.dp),
                            ),
                    ) {
                        terminalPane(workspace)
                    }
                }
            } else {
                ChatPaneContent(
                    onboardingPrivilege = onboardingPrivilege,
                    modifier = Modifier
                        .fillMaxSize()
                        .liquidGlassContent()
                        .padding(horizontal = 12.dp)
                        .windowInsetsPadding(bottomInsets),
                    messages = messages,
                    listState = listState,
                    running = running,
                    status = status,
                    thinkingExpanded = thinkingExpanded,
                    thinkingLive = thinkingLive,
                    liveThinkingMessageId = liveThinkingMessageId,
                    toolResults = toolResults,
                    workspace = workspace,
                    workspaceProject = effectiveWorkspaceProject,
                    onOpenFile = onOpenFile,
                    onEditMessage = { editTargetMessage = it },
                    onDeleteMessage = viewModel::deleteMessage,
                    error = error,
                    onClearError = viewModel::clearError,
                    matchingCommands = matchingCommands,
                    matchingMentions = matchingMentions,
                    attachedMentions = attachedMentions,
                    knownMentionNames = knownMentionNames,
                    queuedPrompts = queuedPrompts,
                    onEditQueuedPrompt = viewModel::editQueuedPrompt,
                    onRemoveQueuedPrompt = viewModel::removeQueuedPrompt,
                    sendMode = sendMode,
                    onSendModeChange = viewModel::setSendMode,
                    input = input,
                    onInputChanged = viewModel::onInputChanged,
                    onApplyCommand = viewModel::applySlashCommand,
                    onApplyMention = viewModel::applyMention,
                    onRemoveMention = viewModel::removeMention,
                    onTriggerMention = viewModel::triggerMentionInput,
                    attachments = pendingAttachments,
                    onAttachmentsPicked = viewModel::onAttachmentsPicked,
                    onRemoveAttachment = viewModel::removeAttachment,
                    onSend = viewModel::sendFromComposer,
                    onStop = viewModel::stop,
                    currentBranch = currentBranch,
                    runtimeEvents = runtimeEvents,
                    onOpenBranches = { showBranches = true },
                    onOpenRuntime = { showRuntimeTimeline = true },
                    lastAssistantMessageId = lastAssistantMessageId,
                    onRegenerate = viewModel::regenerateLast,
                    onCreateBranch = { branchFromMessageId = it },
                    onRetryTool = viewModel::retryToolCall,
                    initializing = initializing,
                    activeSkillsCount = activeSkillsCount,
                    activeMcpCount = activeMcpCount,
                    pinnedCapabilities = pinnedCapabilities,
                    onOpenSkillsMcp = { showSkillsMcpSheet = true },
                    onToggleSkill = viewModel::setSkillEnabled,
                    onToggleMcpServer = viewModel::setMcpServerEnabled,
                    onUnpinMention = viewModel::unpinMention,
                    onTogglePinMention = viewModel::togglePinMention,
                    activePlan = activePlan,
                    pendingApprovals = pendingApprovals,
                    onResolveApproval = viewModel::resolveApproval,
                    contextUsage = contextUsage,
                    activeModel = activeModel,
                    onUpdateReasoning = viewModel::updateActiveModelReasoning,
                    quickPhrases = quickPhrases,
                    onSelectPhrase = viewModel::applyQuickPhrase,
                    activeCompaction = activeCompaction,
                    memoryCount = memories.size,
                    scratchpadCount = scratchpads.size,
                    onOpenMemory = { showMemorySheet = true },
                )
            }

            if (LocalLiquidGlassBackdrop.current == null) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    RuntimeBottomBar(MainDestination.Agent, onNavigate)
                }
            }
        }
    }

    // 编辑并重新发送对话框
    editTargetMessage?.let { target ->
        EditAndResendDialog(
            originalText = target.text,
            onDismiss = { editTargetMessage = null },
            onConfirm = { newText ->
                viewModel.editAndResend(target.id, newText)
                editTargetMessage = null
            },
        )
    }

    if (showSessions) {
        SessionsDialog(
            sessions = sessions,
            currentSessionId = currentSessionId,
            sessionRunStates = sessionRunStates,
            onDismiss = { showSessions = false },
            onSwitch = { id -> viewModel.switchSession(id); showSessions = false },
            onNew = { showSessions = false; showNewSession = true },
            onDelete = viewModel::deleteSession,
            onRename = viewModel::renameSession,
        )
    }

    if (showNewSession) {
        NewSessionDialog(
            workspaces = workspaces,
            onDismiss = { showNewSession = false },
            onCreate = { title, selected, selectedType ->
                showNewSession = false
                viewModel.createSession(title = title, workspace = selected, projectType = selectedType.name)
            },
        )
    }

    if (showModels) {
        ModelDialog(
            models = models,
            providerModelIds = providerModelIds,
            discoveringProviderModels = discoveringProviderModels,
            providerModelDiscoveryError = providerModelDiscoveryError,
            modelPickerProfileId = modelPickerProfileId,
            onDismiss = {
                viewModel.closeProviderModelPicker()
                showModels = false
            },
            onSelectProfile = { id -> viewModel.setActiveModel(id) },
            onSelectSubModel = { id, subModel -> viewModel.selectModel(id, subModel) },
            onOpenModelPicker = viewModel::openProviderModelPicker,
            onCloseModelPicker = viewModel::closeProviderModelPicker,
            onRefreshModels = viewModel::discoverProviderModels,
            onSwitchModel = viewModel::switchModelInProfile,
            onAdd = { name, provider, model, baseUrl -> viewModel.addModel(name, provider, model, baseUrl) },
            onDelete = viewModel::deleteModel,
        )
    }

    if (showSkillsMcpSheet) {
        // 打开挂载面板时自动探测一次 MCP 连通性
        LaunchedEffect(Unit) { viewModel.refreshMcpConnections() }
        SkillsAndMcpSheet(
            allSkills = allSkills,
            mcpServers = mcpServers,
            mcpConnectionStates = mcpConnectionStates,
            pinnedMentionIds = pinnedMentionIds,
            onDismiss = { showSkillsMcpSheet = false },
            onToggleSkill = { id, enabled ->
                viewModel.setSkillEnabled(id, enabled)
            },
            onToggleMcpServer = { id, enabled ->
                viewModel.setMcpServerEnabled(id, enabled)
            },
            onTogglePin = viewModel::togglePinMention,
            onNavigateToSettings = {
                showSkillsMcpSheet = false
                onNavigate(MainDestination.Settings)
            },
        )
    }

    if (showBranches) {
        BranchBrowserSheet(
            branches = branches,
            running = running,
            onDismiss = { showBranches = false },
            onSwitch = { branch ->
                viewModel.switchBranch(branch)
                showBranches = false
            },
        )
    }

    if (showRuntimeTimeline) {
        RuntimeTimelineSheet(
            events = runtimeEvents,
            messages = messages,
            onDismiss = { showRuntimeTimeline = false },
        )
    }

    if (showApprovalModes) {
        ModalBottomSheet(
            onDismissRequest = { showApprovalModes = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "审批权限模式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = { showApprovalModes = false }) {
                        RuntimeIcon(RuntimeIconName.Close, Modifier.size(20.dp))
                    }
                }

                Text(
                    text = "控制 Agent 执行 Bash 命令、文件读写及系统操作时的拦截与确认策略：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                listOf(
                    Triple(ApprovalMode.REQUEST, "询问模式 (安全严谨)", stringResource(R.string.chat_approval_request_description)),
                    Triple(ApprovalMode.ASSISTED, "辅助模式 (半自动)", stringResource(R.string.chat_approval_assisted_description)),
                    Triple(ApprovalMode.FULL_ACCESS, "全自动模式 (极速通行)", stringResource(R.string.chat_approval_full_access_description)),
                ).forEach { (mode, title, desc) ->
                    val isSelected = mode == currentApprovalMode
                    Surface(
                        onClick = {
                            viewModel.setCurrentSessionApprovalMode(mode)
                            showApprovalModes = false
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RuntimeIcon(
                                name = if (isSelected) RuntimeIconName.Check else RuntimeIconName.Shield,
                                modifier = Modifier.size(22.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMemorySheet) {
        SessionMemorySheet(
            memories = memories,
            scratchpads = scratchpads,
            onDeleteMemory = viewModel::deleteMemory,
            onDeleteScratchpad = viewModel::deleteScratchpad,
            onClearScratchpads = viewModel::clearScratchpads,
            onDismiss = { showMemorySheet = false },
        )
    }

    branchFromMessageId?.let { messageId ->
        CreateBranchDialog(
            messageId = messageId,
            onDismiss = { branchFromMessageId = null },
            onCreate = { id, name ->
                viewModel.createBranch(id, name)
                branchFromMessageId = null
            },
        )
    }
}

@Composable
private fun ChatPaneContent(
    modifier: Modifier = Modifier,
    messages: List<HarnessMessage>,
    onboardingPrivilege: OnboardingPrivilege? = null,
    listState: androidx.compose.foundation.lazy.LazyListState,
    running: Boolean,
    status: String?,
    thinkingExpanded: Boolean,
    thinkingLive: Boolean,
    liveThinkingMessageId: String?,
    toolResults: Map<String, ToolResult>,
    workspace: String,
    workspaceProject: WorkspaceProject? = null,
    onOpenFile: ((projectName: String, relativePath: String) -> Unit)?,
    onEditMessage: (UserMessage) -> Unit,
    onDeleteMessage: (String) -> Unit,
    error: String?,
    onClearError: () -> Unit,
    matchingCommands: List<SlashCommandItem>,
    matchingMentions: List<MentionItem> = emptyList(),
    attachedMentions: List<MentionItem> = emptyList(),
    knownMentionNames: List<String> = emptyList(),
    queuedPrompts: List<top.wkbin.taixu.harness.QueuedPrompt>,
    onEditQueuedPrompt: (top.wkbin.taixu.harness.QueuedPrompt) -> Unit,
    onRemoveQueuedPrompt: (top.wkbin.taixu.harness.QueuedPrompt) -> Unit,
    sendMode: ComposerSendMode,
    onSendModeChange: (ComposerSendMode) -> Unit,
    input: String,
    onInputChanged: (String) -> Unit,
    onApplyCommand: (SlashCommandItem) -> Unit,
    onApplyMention: (MentionItem) -> Unit = {},
    onRemoveMention: (MentionItem) -> Unit = {},
    onTriggerMention: () -> Unit = {},
    attachments: List<ChatAttachment> = emptyList(),
    onAttachmentsPicked: (List<android.net.Uri>, Boolean) -> Unit = { _, _ -> },
    onRemoveAttachment: (ChatAttachment) -> Unit = {},
    onSend: () -> Unit,
    onStop: () -> Unit,
    currentBranch: top.wkbin.taixu.harness.session.ConversationBranch?,
    runtimeEvents: List<top.wkbin.taixu.harness.events.HarnessEvent>,
    onOpenBranches: () -> Unit,
    onOpenRuntime: () -> Unit,
    lastAssistantMessageId: String?,
    onRegenerate: () -> Unit,
    onCreateBranch: (String) -> Unit,
    onRetryTool: (String) -> Unit,
    initializing: Boolean = false,
    activeSkillsCount: Int = 0,
    activeMcpCount: Int = 0,
    pinnedCapabilities: List<MentionItem> = emptyList(),
    onOpenSkillsMcp: () -> Unit = {},
    onToggleSkill: (String, Boolean) -> Unit = { _, _ -> },
    onToggleMcpServer: (String, Boolean) -> Unit = { _, _ -> },
    onUnpinMention: (String) -> Unit = {},
    onTogglePinMention: (String) -> Unit = {},
    activeModel: top.wkbin.taixu.core.database.AiModelEntity? = null,
    onUpdateReasoning: (mode: String?, effort: String?) -> Unit = { _, _ -> },
    pendingApprovals: List<top.wkbin.taixu.core.database.AgentApprovalRequestEntity> = emptyList(),
    activePlan: top.wkbin.taixu.core.database.AgentPlanEntity? = null,
    activeCompaction: top.wkbin.taixu.harness.compaction.CompactionSnapshot? = null,
    memoryCount: Int = 0,
    scratchpadCount: Int = 0,
    onOpenMemory: () -> Unit = {},
    onResolveApproval: (String, Boolean) -> Unit = { _, _ -> },
    contextUsage: ContextUsage = ContextUsage(),
    quickPhrases: List<top.wkbin.taixu.core.model.QuickPhrase> = emptyList(),
    onSelectPhrase: (top.wkbin.taixu.core.model.QuickPhrase) -> Unit = {},
) {
    var showReasoningSlider by remember { mutableStateOf(false) }
    var expandedOverrides by rememberSaveable { mutableStateOf(mapOf<String, Boolean>()) }

    // 🌟 任务执行中的极光流光边框动效 (Aurora Glow Border Animation)
    val infiniteTransition = rememberInfiniteTransition(label = "capsuleGlowTransition")
    val glowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glowOffset",
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowPulse",
    )

    val focusRequester = remember { FocusRequester() }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(input, TextRange(input.length)))
    }

    LaunchedEffect(input) {
        if (textFieldValue.text != input) {
            textFieldValue = TextFieldValue(
                text = input,
                selection = TextRange(input.length),
            )
            if (input.isNotEmpty()) {
                runCatching { focusRequester.requestFocus() }
            }
        }
    }

    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        onAttachmentsPicked(uris.toList(), true)
    }

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        onAttachmentsPicked(uris.toList(), false)
    }

    val coroutineScope = rememberCoroutineScope()

    val doSend = {
        if (input.isNotBlank() || attachments.isNotEmpty()) {
            onSend()
            coroutineScope.launch {
                delay(60)
                val count = listState.layoutInfo.totalItemsCount
                if (count > 0) {
                    listState.animateScrollToItem(count - 1)
                }
            }
        }
    }

    Column(modifier = modifier) {
        val renderItems = remember(messages, toolResults, expandedOverrides) {
            projectChatMessages(
                messages = messages,
                toolResults = toolResults,
                expandedOverrides = expandedOverrides,
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .scrollFadingEdge(top = 8.dp, bottom = 8.dp),
        ) {
            if (initializing) {
                item {
                    ChatMessageSkeleton(
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                if (messages.isEmpty()) {
                    item {
                        EmptyChatGuidance(
                            workspaceProject = workspaceProject,
                            onboardingPrivilege = onboardingPrivilege,
                            quickPhrases = quickPhrases,
                            onSelectPhrase = onSelectPhrase,
                            onSelectCommand = onApplyCommand,
                        )
                    }
                }
                // 上下文压缩透明度横幅：真实折叠条数 + 摘要原地展开预览。
                activeCompaction?.let { snapshot ->
                    item(key = "session_compaction_banner") {
                        CompactionBanner(
                            snapshot = snapshot,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
                // 结构化任务规划看板：plan 工具的真实数据源，与文本正则卡片互补。
                activePlan?.let { plan ->
                    item(key = "session_plan_board") {
                        SessionPlanBoardCard(
                            goal = plan.goal,
                            steps = PlanStepParser.parse(plan.stepsJson),
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
                itemsIndexed(renderItems, key = { _, item -> item.stableKey }) { index, item ->
                    val top = when {
                        item is ChatRenderItem.MessageItem && item.message is ToolCall && prevRenderedIsToolCall(renderItems, index) -> 4.dp
                        item is ChatRenderItem.MessageItem && item.message is UserMessage -> 14.dp
                        else -> 10.dp
                    }
                    if (index > 0) Spacer(Modifier.height(top))
                    when (item) {
                        is ChatRenderItem.CollapseButtonItem -> {
                            RoundCollapseButton(
                                item = item,
                                onToggle = {
                                    val currentExpanded = item.isExpanded
                                    expandedOverrides = expandedOverrides + (item.roundKey to !currentExpanded)
                                },
                            )
                        }
                        is ChatRenderItem.MessageItem -> {
                            when (val message = item.message) {
                                is CapabilityEvent -> CapabilityEventCard(message)
                                is UserMessage -> UserBubble(
                                    message = message,
                                    knownMentionNames = knownMentionNames,
                                    onEdit = { onEditMessage(message) },
                                    onDelete = { onDeleteMessage(message.id) },
                                    onCreateBranch = { onCreateBranch(message.id) },
                                )
                                is AssistantText -> AssistantBubble(
                                    message = message,
                                    defaultExpanded = thinkingExpanded,
                                    live = thinkingLive && message.id == liveThinkingMessageId,
                                    showRegenerate = message.id == lastAssistantMessageId,
                                    onRegenerate = onRegenerate,
                                    onCreateBranch = { onCreateBranch(message.id) },
                                )
                                is ToolCall -> {
                                    val rawIndex = messages.indexOfFirst { it.id == message.id }
                                    if (message.tool == HarnessTool.SUBAGENT) {
                                        SubagentCard(
                                            call = message,
                                            result = toolResults[message.id],
                                        )
                                    } else {
                                        ToolCard(
                                            call = message,
                                            result = toolResults[message.id],
                                            workspace = workspace,
                                            onOpenFile = onOpenFile,
                                            running = running,
                                            liveStatus = status,
                                            showReasoning = message.reasoning != null &&
                                                !reasoningAlreadyShown(messages, rawIndex, message.reasoning),
                                            defaultExpanded = thinkingExpanded,
                                            onRetry = { onRetryTool(message.id) },
                                        )
                                    }
                                }
                                is ToolResult -> Unit
                            }
                        }
                    }
                }
            }
            item {
                pendingApprovals.firstOrNull()?.let { request ->
                    ApprovalRequestCard(
                        request = request,
                        onApprove = { onResolveApproval(request.id, true) },
                        onReject = { onResolveApproval(request.id, false) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        error?.let {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearError) {
                        Text(stringResource(R.string.chat_close), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        // 斜杠指令快捷提示弹窗
        if (matchingCommands.isNotEmpty()) {
            SlashCommandPopup(
                commands = matchingCommands,
                onSelect = onApplyCommand,
            )
        }

        // @ 艾特精准挂载快捷弹窗 (Skills & MCP)
        if (matchingMentions.isNotEmpty()) {
            MentionPopup(
                mentions = matchingMentions,
                onSelect = onApplyMention,
            )
        }

        QueuedPromptStack(prompts = queuedPrompts, onEdit = onEditQueuedPrompt, onRemove = onRemoveQueuedPrompt)

        // 待发送附件预览栏
        AttachmentPreviewRow(
            attachments = attachments,
            onRemove = onRemoveAttachment,
        )

        // 🌟 思考/推理强度浮动调节胶囊 (ChatGPT 同款滑块面板)
        androidx.compose.animation.AnimatedVisibility(
            visible = showReasoningSlider,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
        ) {
            ReasoningEffortSlider(
                currentMode = activeModel?.reasoningMode,
                currentEffort = activeModel?.reasoningEffort,
                onSelect = { mode, effort ->
                    onUpdateReasoning(mode, effort)
                },
                onClose = { showReasoningSlider = false },
            )
        }

        // 🌟 底部悬浮工具活动胶囊 (Floating Tool Activity Strip / Live Pill)
        ToolActivityPill(
            running = running,
            status = status,
            messages = messages,
            toolResults = toolResults,
            workspace = workspace,
            onStop = onStop,
        )

        // 现代化一体化输入胶囊 (Unified Chat Input Capsule with Aurora Glow)
        val auroraBrush = if (running) {
            androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(
                    Color(0xFF00E5FF),
                    Color(0xFF7C4DFF),
                    Color(0xFFFF4081),
                    Color(0xFF00E5FF),
                ),
                start = androidx.compose.ui.geometry.Offset(glowOffset, 0f),
                end = androidx.compose.ui.geometry.Offset(glowOffset + 600f, 600f),
                tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
            )
        } else null

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 4.dp)
                .then(
                    if (running && auroraBrush != null) {
                        Modifier.border(
                            androidx.compose.foundation.BorderStroke(
                                (1.2f + 0.3f * glowPulse).dp,
                                auroraBrush,
                            ),
                            RoundedCornerShape(20.dp),
                        )
                    } else Modifier
                ),
            shape = RoundedCornerShape(20.dp),
            color = if (running) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = if (!running) androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            ) else null,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 仅在运行中且用户开始输入时展示，避免发送后立刻占位/换行
                if (running && input.isNotBlank()) {
                    ComposerModeSelector(mode = sendMode, onModeChange = onSendModeChange)
                }

                // 🌟 常驻/已钉选能力与临时挂载胶囊栏 (Pinned & Attached Capabilities Strip)
                val allDisplayItems = remember(pinnedCapabilities, attachedMentions) {
                    (pinnedCapabilities + attachedMentions).distinctBy { it.id }
                }
                if (allDisplayItems.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(allDisplayItems.size) { index ->
                            val item = allDisplayItems[index]
                            val isSkill = item.type == MentionType.SKILL
                            val isAttached = attachedMentions.any { it.id == item.id }
                            val isPinned = pinnedCapabilities.any { it.id == item.id }
                            val tagBg = if (isSkill) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                            val tagBorder = if (isSkill) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                            else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                            val tagColor = if (isSkill) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.tertiary

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = tagBg,
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, tagBorder),
                                modifier = Modifier.clickable {
                                    if (isAttached) {
                                        onRemoveMention(item)
                                    } else if (isPinned) {
                                        onUnpinMention(item.id)
                                    }
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    RuntimeIcon(
                                        item.icon,
                                        Modifier.size(12.dp),
                                        tint = tagColor,
                                    )
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                                        color = tagColor,
                                    )
                                    if (isPinned) {
                                        Surface(
                                            color = tagColor.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Text(
                                                "常驻",
                                                modifier = Modifier.padding(horizontal = 3.5.dp, vertical = 0.5.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold),
                                                color = tagColor,
                                            )
                                        }
                                    }
                                    RuntimeIcon(
                                        RuntimeIconName.Close,
                                        Modifier.size(10.dp),
                                        tint = tagColor.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }

                        item {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                modifier = Modifier.clickable { onOpenSkillsMcp() },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    RuntimeIcon(
                                        RuntimeIconName.Plus,
                                        Modifier.size(11.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "挂载",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // 🌟 上排主体：弹性文本输入框
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (input.isEmpty()) {
                        Text(
                            text = stringResource(if (running) R.string.chat_input_running else R.string.chat_input_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            val updatedValue = if (newValue.text.length < textFieldValue.text.length) {
                                val oldText = textFieldValue.text
                                val deletedPos = newValue.selection.start
                                val baseRegex = buildMentionRegex(knownMentionNames)
                                val regex = Regex("""(${baseRegex.pattern})\s*""")
                                var handled: TextFieldValue? = null
                                for (match in regex.findAll(oldText)) {
                                    val range = match.range
                                    if (deletedPos in range.first until (range.last + 1)) {
                                        val before = oldText.substring(0, range.first)
                                        val after = oldText.substring((range.last + 1).coerceAtMost(oldText.length))
                                        val resultText = before + after
                                        val newCursor = range.first.coerceAtMost(resultText.length)
                                        handled = TextFieldValue(resultText, TextRange(newCursor))
                                        break
                                    }
                                }
                                handled ?: newValue
                            } else {
                                newValue
                            }
                            textFieldValue = updatedValue
                            if (updatedValue.text != input) {
                                onInputChanged(updatedValue.text)
                            }
                        },
                        visualTransformation = run {
                            val mentionColor = MaterialTheme.colorScheme.primary
                            val mentionBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            remember(knownMentionNames, mentionColor, mentionBg) {
                                MentionVisualTransformation(knownMentionNames, mentionColor, mentionBg)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        minLines = 1,
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { doSend() }),
                    )
                }

                // 🌟 下排操作栏：精简工具集 + 状态与发送控制
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // 左侧工具图标栏
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        // 📎 附件弹出菜单（图片 / 文件）
                        var showAttachmentMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showAttachmentMenu = true },
                                modifier = Modifier.size(28.dp),
                            ) {
                                RuntimeIcon(
                                    name = RuntimeIconName.Attach,
                                    modifier = Modifier.size(17.dp),
                                    tint = if (attachments.isNotEmpty()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(
                                expanded = showAttachmentMenu,
                                onDismissRequest = { showAttachmentMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("发送图片", fontSize = 13.sp) },
                                    leadingIcon = { RuntimeIcon(RuntimeIconName.Image, Modifier.size(16.dp)) },
                                    onClick = {
                                        showAttachmentMenu = false
                                        imagePickerLauncher.launch("image/*")
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("发送文件 / 产物", fontSize = 13.sp) },
                                    leadingIcon = { RuntimeIcon(RuntimeIconName.Document, Modifier.size(16.dp)) },
                                    onClick = {
                                        showAttachmentMenu = false
                                        filePickerLauncher.launch("*/*")
                                    },
                                )
                            }
                        }

                        // 🧠 大脑能力快捷入口（@ 快捷选单）
                        IconButton(
                            onClick = {
                                onTriggerMention()
                                runCatching { focusRequester.requestFocus() }
                            },
                            modifier = Modifier.size(28.dp),
                        ) {
                            RuntimeIcon(
                                name = RuntimeIconName.Brain,
                                modifier = Modifier.size(17.dp),
                                tint = if (attachedMentions.isNotEmpty()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // ⏱️ 思考/推理强度滑块开关
                        IconButton(
                            onClick = { showReasoningSlider = !showReasoningSlider },
                            modifier = Modifier.size(28.dp),
                        ) {
                            val isReasoningDisabled = activeModel?.reasoningMode == "disabled"
                            val isHigh = activeModel?.reasoningEffort == "high"
                            val isLow = activeModel?.reasoningEffort == "low"
                            val speedIconTint = when {
                                showReasoningSlider -> MaterialTheme.colorScheme.primary
                                isReasoningDisabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                isHigh -> Color(0xFF8B5CF6)
                                isLow -> Color(0xFF10B981)
                                else -> Color(0xFF3B82F6)
                            }
                            RuntimeIcon(
                                name = RuntimeIconName.Speed,
                                modifier = Modifier.size(17.dp),
                                tint = speedIconTint,
                            )
                        }
                    }

                    // 右侧发送 / 停止控制区（圆形灵动按钮）
                    val canSend = input.isNotBlank() || attachments.isNotEmpty()
                    if (running) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ContextUsageRing(contextUsage)
                            if (canSend) {
                                val sendTint = when (sendMode) {
                                    ComposerSendMode.STEER -> Color(0xFF7C4DFF)
                                    ComposerSendMode.NEXT_RUN -> MaterialTheme.colorScheme.secondary
                                }
                                Surface(
                                    onClick = doSend,
                                    shape = CircleShape,
                                    color = sendTint,
                                    modifier = Modifier.size(30.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        RuntimeIcon(
                                            name = RuntimeIconName.ArrowUp,
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.White,
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    onClick = onStop,
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(30.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        RuntimeIcon(
                                            name = RuntimeIconName.Stop,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onError,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ContextUsageRing(contextUsage)
                            Surface(
                                onClick = { if (canSend) doSend() },
                                enabled = canSend,
                                shape = CircleShape,
                                color = if (canSend) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(30.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    RuntimeIcon(
                                        name = RuntimeIconName.ArrowUp,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextUsageButton(usage: ContextUsage) {
    var expanded by remember { mutableStateOf(false) }
    val ratio = usage.usedTokens.toFloat() / usage.limitTokens.coerceAtLeast(1)
    val displayPercent = (ratio * 100).roundToInt().coerceIn(0, 100)
    val tint = when {
        ratio >= 0.9f -> MaterialTheme.colorScheme.error
        ratio >= 0.7f -> Color(0xFFB25E00)
        else -> MaterialTheme.colorScheme.primary
    }
    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(9.dp),
            color = tint.copy(alpha = 0.12f),
            modifier = Modifier.height(30.dp).widthIn(min = 42.dp),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$displayPercent%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = tint,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 248.dp, max = 292.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.chat_context_usage), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatContextTokens(usage.usedTokens)} / ${formatContextTokens(usage.limitTokens)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = tint,
                    )
                }
                LinearProgressIndicator(
                    progress = { ratio.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = tint,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                ContextUsageRow(stringResource(R.string.chat_system_prompt), usage.systemTokens)
                ContextUsageRow(stringResource(R.string.chat_tools), usage.toolTokens)
                ContextUsageRow(stringResource(R.string.chat_conversation_messages), usage.conversationTokens)
                Text(
                    stringResource(if (ratio >= 1f) R.string.chat_context_over_budget else R.string.chat_context_estimate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ContextUsageRow(label: String, tokens: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatContextTokens(tokens), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun formatContextTokens(tokens: Int): String = when {
    tokens >= 1_000_000 -> "~${"%.1f".format(java.util.Locale.US, tokens / 1_000_000f)}M"
    tokens >= 1_000 -> "~${"%.1f".format(java.util.Locale.US, tokens / 1_000f)}K"
    else -> "~${tokens}"
}

@Composable
private fun EmptyChatGuidance(
    workspaceProject: WorkspaceProject? = null,
    onboardingPrivilege: OnboardingPrivilege? = null,
    quickPhrases: List<top.wkbin.taixu.core.model.QuickPhrase> = emptyList(),
    onSelectPhrase: (top.wkbin.taixu.core.model.QuickPhrase) -> Unit = {},
    onSelectCommand: (SlashCommandItem) -> Unit,
) {
    val context = LocalContext.current
    val enabledPhrases = remember(quickPhrases, workspaceProject) {
        val active = quickPhrases.filter { it.isEnabled }
        if (active.isEmpty()) emptyList()
        else {
            val projectType = workspaceProject?.projectType
            val matched = when (projectType) {
                top.wkbin.taixu.runtime.ProjectType.ANDROID -> active.filter { it.targetProjectType == "ANDROID" || it.targetProjectType == null }
                top.wkbin.taixu.runtime.ProjectType.FLUTTER -> active.filter { it.targetProjectType == "FLUTTER" || it.targetProjectType == null }
                top.wkbin.taixu.runtime.ProjectType.REVERSE -> active.filter { it.targetProjectType == "REVERSE" || it.targetProjectType == null }
                else -> active.filter { it.targetProjectType == null }
            }
            if (matched.isNotEmpty()) matched else active.take(4)
        }
    }

    val quickCommands = remember(workspaceProject) {
        when (workspaceProject?.projectType) {
            top.wkbin.taixu.runtime.ProjectType.ANDROID -> listOf(
                SlashCommandItem("/android-check", context.getString(R.string.chat_android_check), context.getString(R.string.chat_android_check_description), context.getString(R.string.chat_android_check_prompt), RuntimeIconName.Check),
                SlashCommandItem("/android-build-install", context.getString(R.string.chat_android_build), context.getString(R.string.chat_android_build_description), context.getString(R.string.chat_android_build_prompt), RuntimeIconName.Play),
                SlashCommandItem("/android-debug", context.getString(R.string.chat_android_debug), context.getString(R.string.chat_android_debug_description), context.getString(R.string.chat_android_debug_prompt), RuntimeIconName.Alert),
            )
            top.wkbin.taixu.runtime.ProjectType.FLUTTER -> listOf(
                SlashCommandItem("/flutter-check", context.getString(R.string.chat_flutter_check), context.getString(R.string.chat_flutter_check_description), context.getString(R.string.chat_flutter_check_prompt), RuntimeIconName.Check),
                SlashCommandItem("/flutter-build-install", context.getString(R.string.chat_flutter_build), context.getString(R.string.chat_flutter_build_description), context.getString(R.string.chat_flutter_build_prompt), RuntimeIconName.Play),
                SlashCommandItem("/flutter-debug", context.getString(R.string.chat_flutter_debug), context.getString(R.string.chat_flutter_debug_description), context.getString(R.string.chat_flutter_debug_prompt), RuntimeIconName.Alert),
            )
            top.wkbin.taixu.runtime.ProjectType.REVERSE -> listOf(
                SlashCommandItem("/reverse-analyze", context.getString(R.string.chat_reverse_analyze), context.getString(R.string.chat_reverse_analyze_description), context.getString(R.string.chat_reverse_analyze_prompt), RuntimeIconName.Search),
                SlashCommandItem("/reverse-decode", context.getString(R.string.chat_reverse_decode), context.getString(R.string.chat_reverse_decode_description), context.getString(R.string.chat_reverse_decode_prompt), RuntimeIconName.Code),
            )
            else -> SlashCommands.presetCommands(context).take(4)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        onboardingPrivilege?.let { privilege ->
            val ready = privilege == OnboardingPrivilege.SHIZUKU_READY || privilege == OnboardingPrivilege.ROOT_READY
            Surface(
                color = if (ready) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        when (privilege) {
                            OnboardingPrivilege.SANDBOX -> R.string.chat_onboarding_sandbox
                            OnboardingPrivilege.SANDBOX_UNLOCKABLE -> R.string.chat_onboarding_unlockable
                            OnboardingPrivilege.SHIZUKU_READY -> R.string.chat_onboarding_shizuku
                            OnboardingPrivilege.ROOT_READY -> R.string.chat_onboarding_root
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ready) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
        Text(
            stringResource(R.string.chat_agent_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.chat_quick_start),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (enabledPhrases.isNotEmpty()) {
                enabledPhrases.forEach { phrase ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPhrase(phrase) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            RuntimeIcon(parseIconName(phrase.iconName), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    phrase.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    phrase.description.ifBlank { phrase.content },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            } else {
                quickCommands.forEach { cmd ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCommand(cmd) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            RuntimeIcon(cmd.icon, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    cmd.command,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(cmd.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseIconName(name: String): RuntimeIconName {
    return when (name.lowercase()) {
        "play" -> RuntimeIconName.Play
        "check" -> RuntimeIconName.Check
        "alert" -> RuntimeIconName.Alert
        "code" -> RuntimeIconName.Code
        "plus" -> RuntimeIconName.Plus
        "package" -> RuntimeIconName.Package
        "search" -> RuntimeIconName.Search
        "brain" -> RuntimeIconName.Brain
        "bot" -> RuntimeIconName.Bot
        "chat" -> RuntimeIconName.Chat
        "refresh" -> RuntimeIconName.Refresh
        "terminal" -> RuntimeIconName.Terminal
        "tool", "wrench" -> RuntimeIconName.Wrench
        else -> RuntimeIconName.Play
    }
}

@Composable
private fun SlashCommandPopup(
    commands: List<SlashCommandItem>,
    onSelect: (SlashCommandItem) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            commands.forEach { cmd ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(cmd) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RuntimeIcon(cmd.icon, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        cmd.command,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        cmd.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MentionPopup(
    mentions: List<MentionItem>,
    onSelect: (MentionItem) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.chat_capability_mount),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.chat_enabled_for_request),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            LazyColumn(
                modifier = Modifier.heightIn(max = 240.dp),
            ) {
                items(mentions.size) { index ->
                    val item = mentions[index]
                    val isSkill = item.type == MentionType.SKILL
                    val tagBg = if (isSkill) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                    val tagBorder = if (isSkill) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                    val tagColor = if (isSkill) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.tertiary

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // 左侧图标芯片
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = tagBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, tagBorder),
                            modifier = Modifier.size(34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                RuntimeIcon(
                                    item.icon,
                                    Modifier.size(17.dp),
                                    tint = tagColor,
                                )
                            }
                        }

                        // 中间文案与描述
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "@${item.name}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Surface(
                                    color = tagBg,
                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, tagBorder),
                                    shape = RoundedCornerShape(4.dp),
                                ) {
                                    Text(
                                        if (isSkill) stringResource(R.string.chat_skill_badge) else "🔌 MCP",
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium),
                                        color = tagColor,
                                    )
                                }
                            }
                            Text(
                                item.description,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserBubble(
    message: UserMessage,
    knownMentionNames: List<String> = emptyList(),
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCreateBranch: () -> Unit,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val copyText = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.chat_user_request_clipboard), message.text))
        Toast.makeText(context, context.getString(R.string.chat_request_copied), Toast.LENGTH_SHORT).show()
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 🌟 1. 多模态图片预览卡片
            if (message.imageUrls.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 2.dp),
                ) {
                    itemsIndexed(
                        items = message.imageUrls,
                        key = { index, _ -> "${message.id}:$index" },
                    ) { index, imageUrl ->
                        ImageThumbnail(
                            imageUrl = imageUrl,
                            cacheKey = "chat-image-${message.id}-$index",
                        )
                    }
                }
            }

            // 🌟 2. 用户文字气泡
            if (message.text.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clickable { showMenu = true },
                ) {
                    val mentionColor = MaterialTheme.colorScheme.primary
                    val mentionBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    val annotatedText = remember(message.text, knownMentionNames, mentionColor, mentionBg) {
                        formatMentionText(message.text, knownMentionNames, mentionColor, mentionBg)
                    }
                    Text(
                        text = annotatedText,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            } else if (message.imageUrls.isNotEmpty()) {
                // 纯图片消息：显示小菜单按钮
                Surface(
                    onClick = { showMenu = true },
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape,
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RuntimeIcon(
                            RuntimeIconName.More,
                            Modifier.size(15.dp),
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_copy)) },
                leadingIcon = { RuntimeIcon(RuntimeIconName.Copy, Modifier.size(16.dp)) },
                onClick = {
                    showMenu = false
                    copyText()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_edit_resend)) },
                leadingIcon = { RuntimeIcon(RuntimeIconName.Edit, Modifier.size(16.dp)) },
                onClick = {
                    showMenu = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_create_branch)) },
                leadingIcon = { RuntimeIcon(RuntimeIconName.Hub, Modifier.size(16.dp)) },
                onClick = {
                    showMenu = false
                    onCreateBranch()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    RuntimeIcon(
                        RuntimeIconName.Trash,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    showMenu = false
                    onDelete()
                },
            )
        }
    }
}

/**
 * 🌟 多模态图片优雅缩略图卡片（支持 Base64 Data URL 与本地文件路径）。
 *
 * Base64 解码不能放在 remember 里：remember 的计算发生在 Compose 主线程，历史大图会
 * 直接阻塞切换智枢的首帧。交给 Coil 后，DataUriFetcher/图片解码在后台执行，并按缩略图
 * 的实际尺寸采样；稳定的 cacheKey 也让 Navigation3 重组页面时直接命中内存缓存。
 */
@Composable
private fun ImageThumbnail(
    imageUrl: String,
    cacheKey: String,
) {
    val context = LocalContext.current
    val thumbnailPx = with(LocalDensity.current) { 130.dp.roundToPx() }
    val request = remember(imageUrl, cacheKey, thumbnailPx) {
        val data: Any = when {
            imageUrl.startsWith("file://") -> java.io.File(imageUrl.removePrefix("file://"))
            imageUrl.startsWith("/") -> java.io.File(imageUrl)
            else -> imageUrl
        }
        ImageRequest.Builder(context)
            .data(data)
            .size(thumbnailPx, thumbnailPx)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = stringResource(R.string.chat_user_image),
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(width = 130.dp, height = 130.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                RoundedCornerShape(12.dp),
            ),
    )
}

@Composable
private fun AssistantBubble(
    message: AssistantText,
    defaultExpanded: Boolean,
    live: Boolean = false,
    showRegenerate: Boolean = false,
    onRegenerate: () -> Unit = {},
    onCreateBranch: () -> Unit = {},
) {
    val reasoning = message.reasoning
    val context = LocalContext.current

    val copyAll = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.chat_ai_response_clipboard), message.text))
        Toast.makeText(context, context.getString(R.string.chat_response_copied), Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!reasoning.isNullOrBlank()) {
            ThinkingBlock(
                id = message.id,
                reasoning = reasoning,
                defaultExpanded = defaultExpanded,
                live = live,
            )
        }

        if (!live) {
            val planSteps = remember(message.text) { extractTaskPlanSteps(message.text) }
            if (planSteps.size >= 2) {
                TaskPlanCard(steps = planSteps)
            }
        }

        if (message.text.isNotBlank()) {
            if (live) {
                SelectionContainer {
                    Text(
                        text = message.text,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                MarkdownText(message.text, modifier = Modifier.fillMaxWidth())
            }
        }

        // 底部动作栏：耗时信息 + 复制按钮
        if (!live && message.text.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                message.totalMs?.let {
                    Text(
                        stringResource(R.string.chat_elapsed, formatDuration(it)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                // Provider 报告的本轮 token 用量明细（输入/输出/缓存命中）。
                val tokenParts = buildList {
                    message.promptTokens?.let { add("↑${formatTokenCount(it)}") }
                    message.completionTokens?.let { add("↓${formatTokenCount(it)}") }
                    message.cachedTokens?.takeIf { cached -> cached > 0 }?.let { add("⚡${formatTokenCount(it)}") }
                }
                if (tokenParts.isNotEmpty()) {
                    Text(
                        tokenParts.joinToString(" "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Spacer(Modifier.weight(1f))

                if (showRegenerate) {
                    IconButton(onClick = onRegenerate, modifier = Modifier.size(26.dp), contentDescription = stringResource(R.string.chat_regenerate)) {
                        RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }

                IconButton(onClick = onCreateBranch, modifier = Modifier.size(26.dp), contentDescription = stringResource(R.string.chat_branch_from_here)) {
                    RuntimeIcon(RuntimeIconName.Hub, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // 复制按钮
                IconButton(onClick = copyAll, modifier = Modifier.size(24.dp), contentDescription = stringResource(R.string.chat_copy)) {
                    RuntimeIcon(
                        RuntimeIconName.Copy,
                        Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private data class TaskStepItem(
    val index: Int,
    val title: String,
    val isCompleted: Boolean,
)

private fun extractTaskPlanSteps(text: String): List<TaskStepItem> {
    val lines = text.lines()
    val steps = mutableListOf<TaskStepItem>()
    val checkboxRegex = Regex("""^(\s*[-*]|\s*\d+[\.\)])?\s*\[([ xX])\]\s*(.+)""")
    var idx = 1
    for (line in lines) {
        val match = checkboxRegex.find(line.trim())
        if (match != null) {
            val isChecked = match.groupValues[2].equals("x", ignoreCase = true)
            val title = match.groupValues[3].trim()
            if (title.isNotBlank()) {
                steps.add(TaskStepItem(index = idx++, title = title, isCompleted = isChecked))
            }
        }
    }
    return steps
}

@Composable
private fun TaskPlanCard(
    steps: List<TaskStepItem>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }
    val completedCount = steps.count { it.isCompleted }
    val progress = if (steps.isNotEmpty()) completedCount.toFloat() / steps.size else 0f
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                expanded = !expanded
            },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    RuntimeIcon(
                        RuntimeIconName.Code,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Text(
                    text = stringResource(R.string.chat_plan),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.weight(1f))

                Surface(
                    color = if (completedCount == steps.size) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.chat_plan_completed, completedCount, steps.size),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        ),
                        color = if (completedCount == steps.size) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                RuntimeIcon(
                    if (expanded) RuntimeIconName.ChevronDown else RuntimeIconName.ChevronRight,
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 进度条
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    steps.forEach { step ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (step.isCompleted) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (step.isCompleted) {
                                    Text(
                                        "✓",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }

                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (step.isCompleted) FontWeight.Normal else FontWeight.Medium,
                                ),
                                color = if (step.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ThinkingBlock(
    id: String,
    reasoning: String,
    defaultExpanded: Boolean,
    live: Boolean = false,
) {
    var expanded by rememberSaveable(id) { mutableStateOf(defaultExpanded) }
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val copyToClipboard = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.chat_reasoning_clipboard), reasoning))
        Toast.makeText(context, context.getString(R.string.chat_reasoning_copied), Toast.LENGTH_SHORT).show()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(
                if (live) Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    RoundedCornerShape(12.dp),
                ) else Modifier,
            )
            .clickable {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                expanded = !expanded
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (live) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                RuntimeIcon(
                    RuntimeIconName.Chat,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                stringResource(if (live) R.string.chat_deep_reasoning else R.string.chat_reasoning_process),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.weight(1f))

            IconButton(
                onClick = copyToClipboard,
                modifier = Modifier.size(24.dp),
            ) {
                RuntimeIcon(
                    RuntimeIconName.Copy,
                    Modifier.size(13.dp),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape,
                modifier = Modifier.size(22.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RuntimeIcon(
                        if (expanded) RuntimeIconName.ChevronDown else RuntimeIconName.ChevronRight,
                        Modifier.size(12.dp),
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                if (live) {
                    SelectionContainer {
                        Text(
                            text = reasoning,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    MarkdownText(reasoning, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CapabilityEventCard(event: CapabilityEvent) {
    val isSkill = event.kind == CapabilityEvent.Kind.SKILL
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RuntimeIcon(
            if (isSkill) RuntimeIconName.Brain else RuntimeIconName.Cpu,
            Modifier.size(18.dp),
            MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (isSkill) "Skill: ${event.name}" else "MCP: ${event.name}",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
            if (event.details.isNotBlank()) {
                Text(
                    event.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ToolCard(
    call: ToolCall,
    result: ToolResult?,
    workspace: String,
    onOpenFile: ((String, String) -> Unit)?,
    running: Boolean,
    liveStatus: String?,
    showReasoning: Boolean = false,
    defaultExpanded: Boolean = false,
    onRetry: () -> Unit = {},
) {
    var expanded by remember(call.id) { mutableStateOf(false) }
    val dotColor = when {
        result == null && running -> DotRunning
        result?.success == true -> DotSuccess
        result?.success == false -> DotFailed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLow,
                    RoundedCornerShape(12.dp),
                )
                .padding(start = 12.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showReasoning) {
                call.reasoning?.takeIf { it.isNotBlank() }
                    ?.let {
                        ThinkingBlock(
                            id = call.id,
                            reasoning = it,
                            defaultExpanded = defaultExpanded,
                        )
                    }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                Text(
                    if (call.tool == HarnessTool.MCP) stringResource(R.string.chat_call_tool) else toolName(call.tool, call.rawToolName),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (call.tool == HarnessTool.MCP) {
                        "${toolName(call.tool, call.rawToolName)} · ${toolArgsSummary(call)}"
                    } else {
                        toolArgsSummary(call)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                val durationText = result?.durationMs?.let { formatDuration(it) }
                    ?: if (running) {
                        var elapsed by remember(call.id) { mutableStateOf(0L) }
                        LaunchedEffect(call.id) {
                            while (true) {
                                elapsed = System.currentTimeMillis() - call.createdAt
                                delay(100)
                            }
                        }
                        formatDuration(elapsed)
                    } else {
                        null
                    }
                durationText?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Surface(
                    modifier = Modifier.clickable { expanded = !expanded },
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape,
                ) {
                    RuntimeIcon(
                        if (expanded) RuntimeIconName.ChevronDown else RuntimeIconName.ChevronRight,
                        Modifier.padding(5.dp).size(14.dp),
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val downloadingPrefix = stringResource(R.string.chat_downloading_prefix)
            val verifyingDownload = stringResource(R.string.chat_verifying_download)
            val downloadStatus = liveStatus?.takeIf {
                call.tool == HarnessTool.DOWNLOAD && result == null && running &&
                    (it.startsWith(downloadingPrefix) || it.startsWith(verifyingDownload))
            }
            if (downloadStatus != null) {
                Text(
                    downloadStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
                val percent = Regex("\\((\\d+)%\\)").find(downloadStatus)
                    ?.groupValues?.getOrNull(1)?.toFloatOrNull()?.div(100f)
                if (percent == null) {
                LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { percent },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }

            // 展开 Diff 与输出详情视图
            if (expanded) {
                ToolDiffView(
                    call = call,
                    result = result,
                    workspace = workspace,
                    onOpenFile = onOpenFile,
                )
                if (result != null && !running) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onRetry) {
                            RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(15.dp), MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.chat_retry_from_tool))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalRequestCard(
    request: top.wkbin.taixu.core.database.AgentApprovalRequestEntity,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val riskColor = when (request.riskLevel) {
        "critical" -> MaterialTheme.colorScheme.error
        "high" -> Color(0xFFB45309)
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, riskColor.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeIcon(RuntimeIconName.Shield, Modifier.size(18.dp), riskColor)
                Text(stringResource(R.string.chat_approval_required), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Surface(color = riskColor.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        request.riskLevel.uppercase(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = riskColor,
                    )
                }
            }
            Text(request.summary, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
            Text(request.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // 过期倒计时：审批 TTL 默认 10 分钟，超时未决会被 Harness 判定失效。
            var remainingMinutes by remember(request.expiresAt) { mutableStateOf(Int.MAX_VALUE) }
            LaunchedEffect(request.expiresAt) {
                while (true) {
                    val remaining = request.expiresAt - System.currentTimeMillis()
                    remainingMinutes = if (remaining <= 0) 0 else ((remaining + 59_999) / 60_000).toInt()
                    if (remainingMinutes == 0) break
                    delay(15_000)
                }
            }
            if (remainingMinutes > 0 && remainingMinutes != Int.MAX_VALUE) {
                val urgent = remainingMinutes <= 2
                Text(
                    if (remainingMinutes == 1) {
                        stringResource(R.string.chat_approval_expires_soon)
                    } else {
                        stringResource(R.string.chat_approval_expires_minutes, remainingMinutes)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.chat_reject)) }
                Button(onClick = onApprove, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.chat_approve_continue)) }
            }
        }
    }
}

@Composable
private fun EditAndResendDialog(
    originalText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(originalText) { mutableStateOf(originalText) }
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_edit_resend), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.chat_user_request_clipboard)) },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.chat_resend_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.chat_send), color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) }
        },
    )
}

@Composable
private fun SessionsDialog(
    sessions: List<HarnessSessionEntity>,
    currentSessionId: String,
    sessionRunStates: Map<String, top.wkbin.taixu.core.model.SessionRunState>,
    onDismiss: () -> Unit,
    onSwitch: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
) {
    var renameTarget by remember { mutableStateOf<HarnessSessionEntity?>(null) }
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuntimeIcon(RuntimeIconName.List, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.chat_session_manager), fontWeight = FontWeight.Bold)
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        stringResource(R.string.chat_session_count, sessions.size),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (sessions.size == 1) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RuntimeIcon(RuntimeIconName.Alert, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(R.string.chat_last_session_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sessions.size) { index ->
                        val session = sessions[index]
                        val isCurrent = session.id == currentSessionId
                        val runState = sessionRunStates[session.id] ?: top.wkbin.taixu.core.model.SessionRunState.IDLE

                        val (dotColor, stateLabel) = when (runState) {
                            top.wkbin.taixu.core.model.SessionRunState.RUNNING -> Color(0xFFF59E0B) to stringResource(R.string.chat_state_running)
                            top.wkbin.taixu.core.model.SessionRunState.WAITING_APPROVAL -> Color(0xFF8B5CF6) to stringResource(R.string.chat_state_approval)
                            top.wkbin.taixu.core.model.SessionRunState.FAILED -> Color(0xFFEF4444) to stringResource(R.string.chat_state_failed)
                            top.wkbin.taixu.core.model.SessionRunState.COMPLETED -> Color(0xFF10B981) to stringResource(R.string.chat_state_completed)
                            top.wkbin.taixu.core.model.SessionRunState.IDLE -> Color(0xFF10B981) to stringResource(R.string.chat_state_ready)
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.surfaceContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = androidx.compose.foundation.BorderStroke(
                                if (isCurrent) 1.5.dp else 1.dp,
                                if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSwitch(session.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // 状态指示圆点（绿色完成/橙色进行中/红色失败）
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(dotColor),
                                )

                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            session.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (isCurrent) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp),
                                            ) {
                                                Text(
                                                    stringResource(R.string.chat_current),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                )
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = dotColor.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Text(
                                                stateLabel,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                ),
                                                color = dotColor,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }

                                        if (session.workspace.isNotBlank()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                shape = RoundedCornerShape(4.dp),
                                            ) {
                                                Text(
                                                    session.workspace,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        } else {
                                            Text(
                                                stringResource(R.string.chat_isolated_sandbox),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                IconButton(onClick = { renameTarget = session }, modifier = Modifier.size(28.dp)) {
                                    RuntimeIcon(RuntimeIconName.Settings, Modifier.size(15.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onDelete(session.id) }, modifier = Modifier.size(28.dp)) {
                                    RuntimeIcon(RuntimeIconName.Trash, Modifier.size(15.dp), MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onNew, shape = RoundedCornerShape(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RuntimeIcon(RuntimeIconName.Plus, Modifier.size(16.dp), MaterialTheme.colorScheme.onPrimary)
                    Text(stringResource(R.string.chat_new_session))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_close)) }
        },
    )

    renameTarget?.let { target ->
        RenameSessionDialog(
            currentTitle = target.title,
            onDismiss = { renameTarget = null },
            onRename = { title ->
                if (title.isNotBlank()) onRename(target.id, title)
                renameTarget = null
            },
        )
    }
}

@Composable
private fun RenameSessionDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var title by remember(currentTitle) { mutableStateOf(currentTitle) }
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_rename_session), fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.chat_title_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onRename(title) }) { Text(stringResource(R.string.chat_save), color = MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) } },
    )
}

@Composable
private fun ModelDialog(
    models: List<AiModelEntity>,
    providerModelIds: List<String>,
    discoveringProviderModels: Boolean,
    providerModelDiscoveryError: String?,
    modelPickerProfileId: String?,
    onDismiss: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onSelectSubModel: (profileId: String, subModel: String) -> Unit,
    onOpenModelPicker: (String) -> Unit,
    onCloseModelPicker: () -> Unit,
    onRefreshModels: (String) -> Unit,
    onSwitchModel: (profileId: String, modelId: String) -> Unit,
    onAdd: (String, String, String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    if (showAdd) {
        AddModelDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, provider, model, baseUrl ->
                onAdd(name, provider, model, baseUrl)
                showAdd = false
            },
        )
    }
    val pickingProfile = modelPickerProfileId?.let { id -> models.firstOrNull { it.id == id } }
    if (pickingProfile != null) {
        ProviderModelPickerDialog(
            profile = pickingProfile,
            modelIds = providerModelIds,
            discovering = discoveringProviderModels,
            error = providerModelDiscoveryError,
            onDismiss = onCloseModelPicker,
            onRefresh = { onRefreshModels(pickingProfile.id) },
            onSelect = { modelId -> onSwitchModel(pickingProfile.id, modelId) },
        )
        return
    }
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_select_model), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.chat_select_provider_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                models.forEach { model ->
                    val subModels = model.model.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(model.model) }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (model.isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                                MaterialTheme.shapes.small,
                            )
                            .border(
                                1.dp,
                                if (model.isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                                MaterialTheme.shapes.small,
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProfile(model.id) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            top.wkbin.taixu.ui.components.ProviderBadge(
                                providerIdOrName = model.provider,
                                size = 26.dp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    model.name.ifBlank { model.provider },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    model.provider,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (model.isActive) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(6.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.chat_current),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            IconButton(onClick = { onDelete(model.id) }, modifier = Modifier.size(28.dp)) {
                                RuntimeIcon(RuntimeIconName.Trash, Modifier.size(15.dp), MaterialTheme.colorScheme.error)
                            }
                        }

                        if (subModels.size > 1) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                subModels.forEachIndexed { subIndex, subModel ->
                                    val isSubActive = model.isActive && (subIndex == 0)
                                    Surface(
                                        color = if (isSubActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        shape = RoundedCornerShape(8.dp),
                                        border = if (isSubActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier.clickable {
                                            onSelectSubModel(model.id, subModel)
                                            onDismiss()
                                        },
                                    ) {
                                        Text(
                                            subModel,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = if (isSubActive) FontWeight.Bold else FontWeight.Normal,
                                            ),
                                            color = if (isSubActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (subModels.size <= 1) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        model.model.ifBlank { stringResource(R.string.chat_model_not_set) },
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    )
                                }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            Surface(
                                onClick = { onOpenModelPicker(model.id) },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    RuntimeIcon(RuntimeIconName.Tune, Modifier.size(14.dp), MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text(
                                        stringResource(R.string.chat_switch_model),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
                if (models.isEmpty()) {
                    Text(stringResource(R.string.chat_no_models), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showAdd = true }) { Text(stringResource(R.string.chat_add_model), color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_close)) }
        },
    )
}

@Composable
private fun ProviderModelPickerDialog(
    profile: AiModelEntity,
    modelIds: List<String>,
    discovering: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var manualId by remember(profile.id, profile.model) { mutableStateOf(profile.model) }
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.chat_switch_model_title), fontWeight = FontWeight.Bold)
                Text(
                    profile.name.ifBlank { profile.provider },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.chat_switch_model_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    discovering -> {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.chat_model_discovering),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    error != null && modelIds.isEmpty() -> {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    modelIds.isNotEmpty() -> {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            modelIds.forEach { modelId ->
                                val selected = modelId == profile.model
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else MaterialTheme.colorScheme.surfaceContainerLow,
                                        )
                                        .clickable { onSelect(modelId) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        modelId,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (selected) {
                                        RuntimeIcon(RuntimeIconName.Check, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
                if (!error.isNullOrBlank() && modelIds.isNotEmpty()) {
                    Text(error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = manualId,
                    onValueChange = { manualId = it },
                    label = { Text(stringResource(R.string.chat_model_id)) },
                    placeholder = { Text("gpt-4o / deepseek-chat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRefresh, enabled = !discovering) {
                    Text(stringResource(R.string.chat_refresh_models), color = MaterialTheme.colorScheme.primary)
                }
                TextButton(
                    onClick = { onSelect(manualId) },
                    enabled = manualId.isNotBlank(),
                ) {
                    Text(stringResource(R.string.chat_apply_model), color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) }
        },
    )
}

@Composable
private fun AddModelDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val defaultProvider = stringResource(R.string.chat_custom_provider)
    var provider by remember { mutableStateOf(defaultProvider) }
    var model by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_add_model), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.chat_optional_name)) }, singleLine = true)
                OutlinedTextField(value = provider, onValueChange = { provider = it }, label = { Text("Provider") }, singleLine = true)
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text(stringResource(R.string.chat_optional_base_url)) }, placeholder = { Text("https://api.openai.com/v1") }, singleLine = true)
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text(stringResource(R.string.chat_model_id)) }, placeholder = { Text("deepseek-chat / gpt-4o") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, provider, model, baseUrl) }, enabled = model.isNotBlank()) { Text(stringResource(R.string.chat_confirm_add), color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) }
        },
    )
}

@Composable
private fun NewSessionDialog(
    workspaces: List<WorkspaceProject>,
    onDismiss: () -> Unit,
    onCreate: (title: String, workspace: String, projectType: top.wkbin.taixu.runtime.ProjectType) -> Unit,
) {
    val defaultTitle = stringResource(R.string.chat_new_session)
    var title by remember { mutableStateOf(defaultTitle) }
    var selected by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(top.wkbin.taixu.runtime.ProjectType.GENERAL) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    val quickTags = listOf(
        defaultTitle,
        stringResource(R.string.chat_quick_bug),
        stringResource(R.string.chat_quick_feature),
        stringResource(R.string.chat_quick_environment),
        stringResource(R.string.chat_quick_refactor),
    )

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeIcon(RuntimeIconName.Plus, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.chat_new_agent_session), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.chat_session_title), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text(stringResource(R.string.chat_session_name_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                // 快捷预设标题标签
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    quickTags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (title == tag) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (title == tag) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                            ),
                            modifier = Modifier.clickable { title = tag },
                        ) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (title == tag) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    stringResource(R.string.chat_link_workspace),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        WorkspaceOption(stringResource(R.string.chat_no_workspace), "/root", selected == "", onSelect = {
                            selected = ""
                            selectedType = top.wkbin.taixu.runtime.ProjectType.GENERAL
                        })
                    }
                    items(workspaces.size) { index ->
                        val ws = workspaces[index]
                        WorkspaceOption(ws.name, ws.linuxPath, selected == ws.linuxPath) {
                            selected = ws.linuxPath
                            selectedType = ws.projectType
                        }
                    }
                }

                val selectedProject = workspaces.firstOrNull { it.linuxPath == selected }
                // Keep the detected type as the default, but allow an imported
                // repository to be assigned to a different specialist Agent.
                Text(
                    if (selectedProject == null || selectedProject.projectType == top.wkbin.taixu.runtime.ProjectType.GENERAL) {
                        stringResource(R.string.chat_project_type_manual)
                    } else {
                        stringResource(R.string.chat_project_type_detected)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Box {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().clickable { typeMenuExpanded = true },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                selectedType.displayName,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(stringResource(R.string.chat_select), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                        top.wkbin.taixu.runtime.ProjectType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    selectedType = type
                                    typeMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title.ifBlank { defaultTitle }, selected, selectedType) },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(stringResource(R.string.chat_create_session))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) }
        },
    )
}

@Composable
private fun WorkspaceOption(name: String, path: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(8.dp),
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(8.dp),
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(12.dp).clip(CircleShape).background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Column {
            Text(name.ifBlank { path }, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal))
            if (path.isNotBlank()) {
                Text(path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RoundCollapseButton(
    item: ChatRenderItem.CollapseButtonItem,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = if (item.isExpanded) {
        "收起"
    } else {
        val durationSuffix = if (item.hiddenDurationMs > 0) {
            " · " + formatDuration(item.hiddenDurationMs)
        } else {
            ""
        }
        "展开更多 ${item.hiddenSteps} 步（共 ${item.totalSteps} 步）$durationSuffix"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        )
    }
}

private fun prevRenderedIsToolCall(items: List<ChatRenderItem>, index: Int): Boolean {
    val prev = items.getOrNull(index - 1)
    return prev is ChatRenderItem.MessageItem && prev.message is ToolCall
}

private fun reasoningAlreadyShown(messages: List<HarnessMessage>, index: Int, reasoning: String?): Boolean {
    if (reasoning == null) return false
    var i = index - 1
    while (i >= 0) {
        val m = messages[i]
        if (m is UserMessage) break
        if (m is AssistantText && m.reasoning == reasoning) return true
        if (m is ToolCall && m.reasoning == reasoning) return true
        i--
    }
    return false
}

private fun toolName(tool: HarnessTool, rawToolName: String? = null): String {
    if (!rawToolName.isNullOrBlank()) return rawToolName
    return when (tool) {
        HarnessTool.READ -> "read"
        HarnessTool.WRITE -> "write"
        HarnessTool.EDIT -> "edit"
        HarnessTool.BASE -> "base"
        HarnessTool.PROCESS -> "process"
        HarnessTool.HOST -> "host"
        HarnessTool.DOWNLOAD -> "download"
        HarnessTool.MEMORY -> "memory"
        HarnessTool.PLAN -> "plan"
        HarnessTool.SCRATCHPAD -> "scratchpad"
        HarnessTool.HISTORY_SEARCH -> "history.search"
        HarnessTool.HISTORY_READ -> "history.read"
        HarnessTool.BUILD_SCRIPT -> "build_script"
        HarnessTool.SUBAGENT -> "invoke_subagent"
        HarnessTool.MCP -> "mcp"
        HarnessTool.LOAD_RULE -> "load_rule"
    }
}

private fun toolArgsSummary(call: ToolCall): String {
    val entries = call.args.toMap().entries.take(3)
        .joinToString(", ") { (key, value) -> "$key=${value.toString().take(40)}" }
    return entries
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return when {
        totalSeconds < 10 -> String.format(java.util.Locale.US, "%.1fs", ms / 1000.0)
        totalSeconds < 60 -> "${totalSeconds}s"
        else -> "${totalSeconds / 60}m${totalSeconds % 60}s"
    }
}

/** Token 计数紧凑格式：1234 → 1.2k，用于气泡底部的用量明细。 */
internal fun formatTokenCount(tokens: Int): String = when {
    tokens >= 10_000 -> String.format(java.util.Locale.US, "%.1fk", tokens / 1000f)
    tokens >= 1_000 -> String.format(java.util.Locale.US, "%.2fk", tokens / 1000f)
    else -> tokens.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillsAndMcpSheet(
    allSkills: List<top.wkbin.taixu.core.model.AgentSkill>,
    mcpServers: List<top.wkbin.taixu.core.model.McpServerConfig>,
    mcpConnectionStates: Map<String, top.wkbin.taixu.core.model.McpConnectionState>,
    pinnedMentionIds: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onToggleSkill: (String, Boolean) -> Unit,
    onToggleMcpServer: (String, Boolean) -> Unit,
    onTogglePin: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    val activeSkillsCount = remember(allSkills) { allSkills.count { it.isEnabled } }
    val activeMcpCount = remember(mcpServers) { mcpServers.count { it.isEnabled } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 头部标题与统计
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.chat_capabilities),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.chat_capabilities_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.chat_capabilities_enabled, activeSkillsCount + activeMcpCount),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            // 分段标签页
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)),
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            stringResource(R.string.chat_skills_count, activeSkillsCount, allSkills.size),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            stringResource(R.string.chat_mcp_count, activeMcpCount, mcpServers.size),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                    },
                )
            }

            // 列表内容展示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
            ) {
                if (selectedTab == 0) {
                    // Skills 列表
                    if (allSkills.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.chat_no_skills),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(allSkills.size) { index ->
                                val skill = allSkills[index]
                                val isPinned = skill.id in pinnedMentionIds || skill.name.lowercase() in pinnedMentionIds
                                Surface(
                                    color = if (skill.isEnabled) MaterialTheme.colorScheme.surfaceContainerHigh
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (skill.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    text = skill.name,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = RoundedCornerShape(6.dp),
                                                ) {
                                                    Text(
                                                        text = skill.category,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                            if (skill.description.isNotBlank()) {
                                                Text(
                                                    text = skill.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(top = 2.dp),
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isPinned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                border = androidx.compose.foundation.BorderStroke(
                                                    0.8.dp,
                                                    if (isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                ),
                                                modifier = Modifier.clickable { onTogglePin(skill.id) },
                                            ) {
                                                Text(
                                                    if (isPinned) "📌 已常驻" else "📌 设为常驻",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = if (isPinned) FontWeight.Bold else FontWeight.Normal,
                                                    ),
                                                    color = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                )
                                            }

                                            Switch(
                                                checked = skill.isEnabled,
                                                onCheckedChange = { onToggleSkill(skill.id, it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // MCP 插件列表
                    if (mcpServers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.chat_no_mcp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(mcpServers.size) { index ->
                                val server = mcpServers[index]
                                val isPinned = server.id in pinnedMentionIds || server.name.lowercase() in pinnedMentionIds
                                val connState = mcpConnectionStates[server.id] ?: top.wkbin.taixu.core.model.McpConnectionState.UNKNOWN
                                Surface(
                                    color = if (server.isEnabled) MaterialTheme.colorScheme.surfaceContainerHigh
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (server.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    text = server.name,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(9.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            when {
                                                                !server.isEnabled -> MaterialTheme.colorScheme.outlineVariant
                                                                connState == top.wkbin.taixu.core.model.McpConnectionState.CHECKING -> MaterialTheme.colorScheme.tertiary
                                                                connState == top.wkbin.taixu.core.model.McpConnectionState.ONLINE -> Color(0xFF2E7D32)
                                                                connState == top.wkbin.taixu.core.model.McpConnectionState.OFFLINE -> MaterialTheme.colorScheme.error
                                                                else -> MaterialTheme.colorScheme.outline
                                                            }
                                                        ),
                                                )
                                                Surface(
                                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                                    shape = RoundedCornerShape(6.dp),
                                                ) {
                                                    Text(
                                                        text = server.transportType.name,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                        ),
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                            val endpointDesc = if (server.command.isNotBlank()) server.command else server.serverUrl
                                            if (endpointDesc.isNotBlank()) {
                                                Text(
                                                    text = endpointDesc,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 11.sp,
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(top = 2.dp),
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isPinned) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                border = androidx.compose.foundation.BorderStroke(
                                                    0.8.dp,
                                                    if (isPinned) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                ),
                                                modifier = Modifier.clickable { onTogglePin(server.id) },
                                            ) {
                                                Text(
                                                    if (isPinned) "📌 已常驻" else "📌 设为常驻",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = if (isPinned) FontWeight.Bold else FontWeight.Normal,
                                                    ),
                                                    color = if (isPinned) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                )
                                            }

                                            Switch(
                                                checked = server.isEnabled,
                                                onCheckedChange = { onToggleMcpServer(server.id, it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 底部操作区：直达管理
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToSettings),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Settings,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.chat_manage_capabilities),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    RuntimeIcon(
                        name = RuntimeIconName.ChevronRight,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * ⚡ 思考/推理强度悬浮调节面板（ChatGPT 同款平滑胶囊滑块）
 */
@Composable
private fun ReasoningEffortSlider(
    currentMode: String?,
    currentEffort: String?,
    onSelect: (mode: String?, effort: String?) -> Unit,
    onClose: () -> Unit,
) {
    val levels = listOf(
        Triple(stringResource(R.string.chat_reasoning_disabled), stringResource(R.string.chat_reasoning_disabled_description), "disabled" to null),
        Triple(stringResource(R.string.chat_reasoning_low), stringResource(R.string.chat_reasoning_low_description), "enabled" to "low"),
        Triple(stringResource(R.string.chat_reasoning_medium), stringResource(R.string.chat_reasoning_medium_description), "enabled" to "medium"),
        Triple(stringResource(R.string.chat_reasoning_high), stringResource(R.string.chat_reasoning_high_description), "enabled" to "high"),
    )

    val currentIndex = when {
        currentMode == "disabled" -> 0
        currentMode == "enabled" && currentEffort == "low" -> 1
        currentMode == "enabled" && currentEffort == "medium" -> 2
        currentMode == "enabled" && currentEffort == "high" -> 3
        else -> 2 // 默认中推理
    }

    var selectedIndex by remember(currentIndex) { mutableIntStateOf(currentIndex) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val activeLevel = levels.getOrElse(selectedIndex) { levels[2] }

            // 顶部居中大字标题与说明（还原 ChatGPT 样式）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier.size(20.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = activeLevel.first,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        ),
                        color = when (selectedIndex) {
                            0 -> MaterialTheme.colorScheme.onSurfaceVariant
                            1 -> Color(0xFF10B981)
                            2 -> Color(0xFF3B82F6)
                            3 -> Color(0xFF8B5CF6)
                            else -> MaterialTheme.colorScheme.primary
                        },
                    )
                    Text(
                        text = activeLevel.second,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    RuntimeIcon(RuntimeIconName.Close, Modifier.size(13.dp), MaterialTheme.colorScheme.outline)
                }
            }

            // 现代化连续胶囊滑动条 (Smooth Segmented Slider)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    levels.indices.forEach { index ->
                        val isSelected = index == selectedIndex
                        val itemColor = when (index) {
                            0 -> MaterialTheme.colorScheme.onSurface
                            1 -> Color(0xFF10B981)
                            2 -> Color(0xFF3B82F6)
                            3 -> Color(0xFF8B5CF6)
                            else -> MaterialTheme.colorScheme.primary
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isSelected) {
                                        when (index) {
                                            0 -> MaterialTheme.colorScheme.surfaceContainer
                                            1 -> Color(0xFF10B981).copy(alpha = 0.25f)
                                            2 -> Color(0xFF3B82F6).copy(alpha = 0.25f)
                                            3 -> Color(0xFF8B5CF6).copy(alpha = 0.25f)
                                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                        }
                                    } else Color.Transparent,
                                )
                                .clickable {
                                    selectedIndex = index
                                    val (mode, effort) = levels[index].third
                                    onSelect(mode, effort)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = when (index) {
                                    0 -> stringResource(R.string.chat_depth_off)
                                    1 -> stringResource(R.string.chat_depth_light)
                                    2 -> stringResource(R.string.chat_depth_medium)
                                    3 -> stringResource(R.string.chat_depth_deep)
                                    else -> ""
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                ),
                                color = if (isSelected) itemColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 构建精准匹配技能与插件实体的正则表达式（优先长词带空格全称匹配） */
@Composable
private fun ChatMessageSkeleton(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        // 用户消息占位（右侧）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                    .background(brush),
            )
        }
        Spacer(Modifier.height(16.dp))
        // 助手消息占位（左侧）
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(brush),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(brush),
            )
        }
        Spacer(Modifier.height(16.dp))
        // 用户消息占位 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                    .background(brush),
            )
        }
    }
}

@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "chatShimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "chatShimmerTranslate",
    )
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.25f),
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 200f, 0f),
        end = Offset(translateAnim, 0f),
    )
}

private fun buildMentionRegex(knownNames: List<String>): Regex {
    val sorted = knownNames.filter { it.isNotBlank() }.sortedByDescending { it.length }
    val escaped = sorted.map { Regex.escape(it) }
    val pattern = if (escaped.isNotEmpty()) {
        """@(${escaped.joinToString("|")}|[^\s@,，:：\n]+)"""
    } else {
        """@([^\s@,，:：\n]+)"""
    }
    return Regex(pattern)
}

/** 为文本中的 @能力 实体添加自适应半透明高亮样式（支持带空格全称） */
private fun formatMentionText(
    text: String,
    knownNames: List<String>,
    mentionColor: Color,
    mentionBg: Color,
): AnnotatedString {
    if (!text.contains("@")) return AnnotatedString(text)
    val builder = AnnotatedString.Builder(text)
    val regex = buildMentionRegex(knownNames)
    for (match in regex.findAll(text)) {
        val range = match.range
        builder.addStyle(
            SpanStyle(
                color = mentionColor,
                fontWeight = FontWeight.SemiBold,
                background = mentionBg,
            ),
            range.first,
            range.last + 1,
        )
    }
    return builder.toAnnotatedString()
}

/**
 * 🌟 输入框内 @能力 实体富文本语法高亮变换器
 * 将 `@xxx` 自动渲染为优雅的主题色半透明胶囊样式（对齐 Telegram / 微信 / Discord 设计，支持带空格全称）
 */
private class MentionVisualTransformation(
    private val knownNames: List<String>,
    private val mentionColor: Color,
    private val mentionBg: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val transformed = formatMentionText(text.text, knownNames, mentionColor, mentionBg)
        return TransformedText(transformed, OffsetMapping.Identity)
    }
}
