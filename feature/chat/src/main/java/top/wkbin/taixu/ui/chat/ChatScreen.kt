package top.wkbin.taixu.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.HarnessTool
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.harness.UserMessage
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.ui.components.MainDestination
import top.wkbin.taixu.ui.components.NoticeBanner
import top.wkbin.taixu.ui.components.RuntimeBottomBar
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTopBar

private val DotRunning = Color(0xFFB25E00)
private val DotSuccess = Color(0xFF2E7D32)
private val DotFailed = Color(0xFFBA1A1A)
private val AgentBottomBarHeight = 78.dp

/**
 * 太墟 · 智枢对话界面 (TaiXu Agent)
 * 智能结对编程、工具自动化调用与代码生成
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    onNavigate: (MainDestination) -> Unit,
    onOpenFile: ((projectName: String, relativePath: String) -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val thinkingLive by viewModel.thinkingLive.collectAsStateWithLifecycle()
    val thinkingExpanded by viewModel.thinkingExpanded.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val matchingCommands by viewModel.matchingCommands.collectAsStateWithLifecycle()
    val pendingMessages by viewModel.pendingMessages.collectAsStateWithLifecycle()

    var showSessions by remember { mutableStateOf(false) }
    var showNewSession by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var editTargetMessage by remember { mutableStateOf<UserMessage?>(null) }

    val listState = rememberLazyListState()
    val context = LocalContext.current

    val activeModel = remember(models) { models.firstOrNull { it.isActive } }

    val toolResults = remember(messages) {
        messages.filterIsInstance<ToolResult>().associateBy { it.toolCallId }
    }
    val liveThinkingMessageId = remember(messages) {
        messages.filterIsInstance<AssistantText>().lastOrNull()?.id
    }

    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navigationBottom = WindowInsets.navigationBars.getBottom(density)
    val imeVisible = imeBottom > navigationBottom
    val composerBottomPadding = with(density) {
        maxOf(imeBottom, navigationBottom + AgentBottomBarHeight.roundToPx()).toDp()
    }

    var initialPositionedSessionKey by remember { mutableStateOf<String?>(null) }
    val currentSessionKey = remember(messages) { messages.firstOrNull()?.id ?: "" }

    val streamedChars = remember(messages) {
        messages.filterIsInstance<AssistantText>().sumOf { (it.reasoning?.length ?: 0) + it.text.length }
    }
    LaunchedEffect(messages.size, streamedChars, running) {
        if (messages.isNotEmpty()) {
            if (initialPositionedSessionKey != currentSessionKey) {
                initialPositionedSessionKey = currentSessionKey
                listState.scrollToItem(messages.size - 1)
            } else if (listState.layoutInfo.totalItemsCount > 0) {
                val layoutInfo = listState.layoutInfo
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
                val nearBottom = lastVisible == null ||
                    lastVisible.index >= layoutInfo.totalItemsCount - 2 &&
                    lastVisible.offset + lastVisible.size <= layoutInfo.viewportEndOffset + 400
                if (nearBottom) {
                    if (running) {
                        listState.scrollToItem(layoutInfo.totalItemsCount - 1)
                    } else {
                        listState.animateScrollToItem(layoutInfo.totalItemsCount - 1)
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = "智枢",
                statusText = if (workspace.isNotBlank()) workspace else "独立沙箱会话",
            ) {
                // 模型快速切换胶囊
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable { showModels = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        Text(
                            text = activeModel?.name ?: "未选模型",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                        )
                    }
                }

                IconButton(onClick = { showSessions = true }) {
                    RuntimeIcon(RuntimeIconName.List, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showNewSession = true }) {
                    RuntimeIcon(RuntimeIconName.Plus, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val isDualPane = maxWidth >= 720.dp

            if (isDualPane) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = composerBottomPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 左栏：Agent 对话与指令区
                    ChatPaneContent(
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
                        onOpenFile = onOpenFile,
                        onEditMessage = { editTargetMessage = it },
                        onDeleteMessage = viewModel::deleteMessage,
                        onToggleExpanded = viewModel::setThinkingExpanded,
                        error = error,
                        onClearError = viewModel::clearError,
                        matchingCommands = matchingCommands,
                        pendingMessages = pendingMessages,
                        onRemovePending = viewModel::removePendingMessage,
                        input = input,
                        onInputChanged = viewModel::onInputChanged,
                        onApplyCommand = viewModel::applySlashCommand,
                        onSend = viewModel::send,
                        onStop = viewModel::stop,
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
                        top.wkbin.taixu.ui.terminal.TerminalScreen(
                            onBack = {},
                            project = workspace,
                        )
                    }
                }
            } else {
                // 单栏 Phone 视图
                ChatPaneContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = composerBottomPadding),
                    messages = messages,
                    listState = listState,
                    running = running,
                    status = status,
                    thinkingExpanded = thinkingExpanded,
                    thinkingLive = thinkingLive,
                    liveThinkingMessageId = liveThinkingMessageId,
                    toolResults = toolResults,
                    workspace = workspace,
                    onOpenFile = onOpenFile,
                    onEditMessage = { editTargetMessage = it },
                    onDeleteMessage = viewModel::deleteMessage,
                    onToggleExpanded = viewModel::setThinkingExpanded,
                    error = error,
                    onClearError = viewModel::clearError,
                    matchingCommands = matchingCommands,
                    pendingMessages = pendingMessages,
                    onRemovePending = viewModel::removePendingMessage,
                    input = input,
                    onInputChanged = viewModel::onInputChanged,
                    onApplyCommand = viewModel::applySlashCommand,
                    onSend = viewModel::send,
                    onStop = viewModel::stop,
                )
            }

            if (!imeVisible) {
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
            onCreate = { title, selected ->
                showNewSession = false
                viewModel.createSession(title = title, workspace = selected)
            },
        )
    }

    if (showModels) {
        ModelDialog(
            models = models,
            onDismiss = { showModels = false },
            onSelect = { id -> viewModel.setActiveModel(id) },
            onAdd = { name, provider, model, baseUrl -> viewModel.addModel(name, provider, model, baseUrl) },
            onDelete = viewModel::deleteModel,
        )
    }
}

@Composable
private fun ChatPaneContent(
    modifier: Modifier = Modifier,
    messages: List<HarnessMessage>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    running: Boolean,
    status: String?,
    thinkingExpanded: Boolean,
    thinkingLive: Boolean,
    liveThinkingMessageId: String?,
    toolResults: Map<String, ToolResult>,
    workspace: String,
    onOpenFile: ((projectName: String, relativePath: String) -> Unit)?,
    onEditMessage: (UserMessage) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onToggleExpanded: (Boolean) -> Unit,
    error: String?,
    onClearError: () -> Unit,
    matchingCommands: List<SlashCommandItem>,
    pendingMessages: List<String>,
    onRemovePending: (Int) -> Unit,
    input: String,
    onInputChanged: (String) -> Unit,
    onApplyCommand: (SlashCommandItem) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Column(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            if (messages.isEmpty()) {
                item {
                    EmptyChatGuidance(
                        onSelectCommand = onApplyCommand,
                    )
                }
            }
            itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                if (message is ToolResult) return@itemsIndexed
                val top = when {
                    message is ToolCall && prevRenderedIsToolCall(messages, index) -> 2.dp
                    else -> 8.dp
                }
                if (index > 0) Spacer(Modifier.height(top))
                when (message) {
                    is UserMessage -> UserBubble(
                        message = message,
                        onEdit = { onEditMessage(message) },
                        onDelete = { onDeleteMessage(message.id) },
                    )
                    is AssistantText -> AssistantBubble(
                        message = message,
                        defaultExpanded = thinkingExpanded,
                        onToggleExpanded = onToggleExpanded,
                        live = thinkingLive && message.id == liveThinkingMessageId,
                    )
                    is ToolCall -> ToolCard(
                        call = message,
                        result = toolResults[message.id],
                        workspace = workspace,
                        onOpenFile = onOpenFile,
                        running = running,
                        showReasoning = message.reasoning != null &&
                            !reasoningAlreadyShown(messages, index, message.reasoning),
                        defaultExpanded = thinkingExpanded,
                        onToggleExpanded = onToggleExpanded,
                    )
                    is ToolResult -> Unit
                }
            }
            item {
                if (running) {
                    val roundStart = remember(messages.size) {
                        messages.lastOrNull { it is UserMessage }?.createdAt ?: 0L
                    }
                    var roundElapsed by remember { mutableStateOf(0L) }
                    LaunchedEffect(roundStart) {
                        while (true) {
                            roundElapsed = System.currentTimeMillis() - roundStart
                            delay(200)
                        }
                    }
                    ThinkingIndicator("${status ?: "思考中"} · ${formatDuration(roundElapsed)}")
                } else {
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        error?.let {
            NoticeBanner(it, isError = true, modifier = Modifier.padding(vertical = 6.dp))
            TextButton(onClick = onClearError) { Text("忽略", color = MaterialTheme.colorScheme.primary) }
        }

        // 斜杠指令快捷提示弹窗
        if (matchingCommands.isNotEmpty()) {
            SlashCommandPopup(
                commands = matchingCommands,
                onSelect = onApplyCommand,
            )
        }

        // 排队消息提示条：运行中排队的消息，当前任务结束后自动接续
        if (pendingMessages.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                pendingMessages.forEachIndexed { index, queued ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "排队 ${index + 1}：",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                queued,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { onRemovePending(index) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                RuntimeIcon(
                                    RuntimeIconName.Close,
                                    Modifier.size(16.dp),
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChanged,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        if (running) "正在执行… 输入内容可排队，任务结束后自动接续"
                        else "输入指令… 输入 / 触发快捷命令",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                minLines = 1,
                maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            )
            if (running) {
                // 运行中：输入非空时提供"排队发送"，同时保留"停止"
                if (input.isNotBlank()) {
                    Button(
                        onClick = onSend,
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(13.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        RuntimeIcon(RuntimeIconName.Plus, Modifier.size(20.dp), MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(13.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    RuntimeIcon(RuntimeIconName.Stop, Modifier.size(20.dp), Color.White)
                }
            } else {
                Button(
                    onClick = onSend,
                    enabled = input.isNotBlank(),
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(13.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    RuntimeIcon(RuntimeIconName.ArrowUp, Modifier.size(20.dp), MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun EmptyChatGuidance(
    onSelectCommand: (SlashCommandItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "太墟智枢可以为你编写代码、读写 Linux 工作区文件、执行 Shell 命令或排查系统故障。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "快捷开始：",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SlashCommands.presetCommands.take(4).forEach { cmd ->
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
private fun UserBubble(
    message: UserMessage,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val copyText = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("用户指令", message.text))
        Toast.makeText(context, "已复制指令", Toast.LENGTH_SHORT).show()
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(horizontalAlignment = Alignment.End) {
            Box {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clickable { showMenu = true },
                ) {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("复制") },
                        leadingIcon = { RuntimeIcon(RuntimeIconName.Copy, Modifier.size(16.dp)) },
                        onClick = {
                            showMenu = false
                            copyText()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("编辑并重发") },
                        leadingIcon = { RuntimeIcon(RuntimeIconName.Edit, Modifier.size(16.dp)) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
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
    }
}

@Composable
private fun AssistantBubble(
    message: AssistantText,
    defaultExpanded: Boolean,
    onToggleExpanded: (Boolean) -> Unit,
    live: Boolean = false,
) {
    val reasoning = message.reasoning
    val context = LocalContext.current

    val copyAll = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AI 回复", message.text))
        Toast.makeText(context, "已复制回复内容", Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!reasoning.isNullOrBlank()) {
            ThinkingBlock(
                id = message.id,
                reasoning = reasoning,
                defaultExpanded = defaultExpanded,
                onToggleExpanded = onToggleExpanded,
                live = live,
            )
        }

        val planSteps = remember(message.text) { extractTaskPlanSteps(message.text) }
        if (planSteps.size >= 2) {
            TaskPlanCard(steps = planSteps)
        }

        if (message.text.isNotBlank()) {
            MarkdownText(message.text, modifier = Modifier.fillMaxWidth())
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
                        "用时 ${formatDuration(it)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Spacer(Modifier.weight(1f))

                // 复制按钮
                IconButton(onClick = copyAll, modifier = Modifier.size(24.dp)) {
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
                    text = "执行计划拆解",
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
                        text = "$completedCount / ${steps.size} 完成",
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
            androidx.compose.material3.LinearProgressIndicator(
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
private fun ThinkingIndicator(status: String) {
    Surface(
        modifier = Modifier
            .padding(top = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                status,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ThinkingBlock(
    id: String,
    reasoning: String,
    defaultExpanded: Boolean,
    onToggleExpanded: (Boolean) -> Unit,
    live: Boolean = false,
) {
    var expanded by remember(id) { mutableStateOf(defaultExpanded) }
    LaunchedEffect(defaultExpanded) {
        expanded = defaultExpanded
    }
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val copyToClipboard = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("思考过程", reasoning))
        Toast.makeText(context, "思考内容已复制", Toast.LENGTH_SHORT).show()
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
                val next = !expanded
                expanded = next
                onToggleExpanded(next)
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
                if (live) "深度推理中…" else "推理思考过程",
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
                MarkdownText(reasoning, modifier = Modifier.fillMaxWidth())
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
    showReasoning: Boolean = false,
    defaultExpanded: Boolean = false,
    onToggleExpanded: (Boolean) -> Unit = {},
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
                            onToggleExpanded = onToggleExpanded,
                        )
                    }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                Text(
                    toolName(call.tool),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    toolArgsSummary(call),
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
                                delay(200)
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

            // 展开 Diff 与输出详情视图
            if (expanded) {
                ToolDiffView(
                    call = call,
                    result = result,
                    workspace = workspace,
                    onOpenFile = onOpenFile,
                )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑并重发", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("用户指令") },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "重发将移除该消息之后的所有会话记录，并以此指令重新生成回答。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text("确认发送", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun SessionsDialog(
    sessions: List<HarnessSessionEntity>,
    onDismiss: () -> Unit,
    onSwitch: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
) {
    var renameTarget by remember { mutableStateOf<HarnessSessionEntity?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuntimeIcon(RuntimeIconName.List, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("智枢会话管理", fontWeight = FontWeight.Bold)
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        "${sessions.size} 个会话",
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
                    .heightIn(max = 380.dp),
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
                                "当前为最后一条会话，删除后将自动重置并开启全新会话",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(sessions.size) { index ->
                        val session = sessions[index]
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSwitch(session.id) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        session.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (session.workspace.isNotBlank()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                shape = RoundedCornerShape(4.dp),
                                            ) {
                                                Text(
                                                    session.workspace,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        } else {
                                            Text(
                                                "独立沙箱",
                                                style = MaterialTheme.typography.labelSmall,
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
                    Text("新建会话")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名会话", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onRename(title) }) { Text("保存", color = MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ModelDialog(
    models: List<AiModelEntity>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择模型", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                models.forEach { model ->
                    Row(
                        Modifier.fillMaxWidth()
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
                            .clickable { onSelect(model.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier.size(10.dp).clip(CircleShape).background(
                                if (model.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                model.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text("${model.provider} · ${model.model}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDelete(model.id) }, modifier = Modifier.size(28.dp)) {
                            RuntimeIcon(RuntimeIconName.Trash, Modifier.size(15.dp), MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (models.isEmpty()) {
                    Text("还没有模型。添加一个后将用于 Agent 对话。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showAdd = true }) { Text("添加模型", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun AddModelDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("OpenAI") }
    var model by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加模型", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称（可选）") }, singleLine = true)
                OutlinedTextField(value = provider, onValueChange = { provider = it }, label = { Text("Provider") }, singleLine = true)
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("模型 ID") }, placeholder = { Text("deepseek-chat / gpt-4o") }, singleLine = true)
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL（可选）") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, provider, model, baseUrl) }, enabled = model.isNotBlank()) { Text("确认添加", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun NewSessionDialog(
    workspaces: List<WorkspaceProject>,
    onDismiss: () -> Unit,
    onCreate: (title: String, workspace: String) -> Unit,
) {
    var title by remember { mutableStateOf("新会话") }
    var selected by remember { mutableStateOf("") }
    val quickTags = listOf("新会话", "Bug排查", "特性开发", "环境配置", "代码重构")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeIcon(RuntimeIconName.Plus, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text("新建智枢会话", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("会话标题：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("输入会话名称…") },
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
                    "关联工作区工程（Agent 工具将默认在该目录执行）：",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        WorkspaceOption("不关联工作区（纯对话与全局 Linux）", "/root", selected == "", onSelect = { selected = "" })
                    }
                    items(workspaces.size) { index ->
                        val ws = workspaces[index]
                        WorkspaceOption(ws.name, ws.linuxPath, selected == ws.linuxPath) {
                            selected = ws.linuxPath
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title.ifBlank { "新会话" }, selected) },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("创建并开启会话")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
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

private fun prevRenderedIsToolCall(messages: List<HarnessMessage>, index: Int): Boolean {
    var i = index - 1
    while (i >= 0 && messages[i] is ToolResult) i--
    return i >= 0 && messages[i] is ToolCall
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

private fun toolName(tool: HarnessTool): String = when (tool) {
    HarnessTool.READ -> "read"
    HarnessTool.WRITE -> "write"
    HarnessTool.EDIT -> "edit"
    HarnessTool.BASE -> "base"
}

private fun toolArgsSummary(call: ToolCall): String {
    val entries = call.args.toMap().entries.take(3)
        .joinToString(", ") { (key, value) -> "$key=${value.toString().take(40)}" }
    return entries.ifBlank { "(无参数)" }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return when {
        totalSeconds < 10 -> String.format(java.util.Locale.US, "%.1fs", ms / 1000.0)
        totalSeconds < 60 -> "${totalSeconds}s"
        else -> "${totalSeconds / 60}m${totalSeconds % 60}s"
    }
}
