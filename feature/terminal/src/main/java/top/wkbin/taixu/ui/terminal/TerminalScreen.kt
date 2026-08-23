package top.wkbin.taixu.ui.terminal

import top.wkbin.taixu.ui.components.RuntimeAlertDialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import top.wkbin.taixu.ui.components.RuntimeIconButton as IconButton
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.ui.components.NoticeBanner
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTopBar
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TermBg = Color(0xFF0F1117)
private val TermHeaderBg = Color(0xFF181A22)
private val TermTextDefault = Color(0xFFE2E2E9)
private val TermDimText = Color(0xFF8E9099)
private val TermBorder = Color(0xFF282A36)
private const val MIN_TERMINAL_FONT_SIZE_SP = 10f
private const val MAX_TERMINAL_FONT_SIZE_SP = 24f

/**
 * 太墟 · 矩阵控制台 (Matrix Terminal)
 * 基于原生 Linux PTY，集成开发者快捷辅助按键栏
 */
@Composable
fun TerminalScreen(
    onBack: () -> Unit,
    project: String = "",
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val cursor by viewModel.cursor.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val handles by viewModel.handles.collectAsStateWithLifecycle()
    val activeId by viewModel.activeId.collectAsStateWithLifecycle()
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val distributionName by viewModel.distributionName.collectAsStateWithLifecycle()
    val installedDistros by viewModel.installedDistros.collectAsStateWithLifecycle()
    val configuredFontSize by viewModel.terminalFontSize.collectAsStateWithLifecycle()
    val colorScheme by viewModel.terminalColorScheme.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.terminalHapticsEnabled.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current

    var fontSizeSp by remember { mutableFloatStateOf(configuredFontSize.toFloat()) }
    var terminalPxWidth by remember { mutableFloatStateOf(0f) }
    var terminalPxHeight by remember { mutableFloatStateOf(0f) }

    val (termBg, termHeaderBg, termTextDefault, termBorder) = remember(colorScheme) {
        when (colorScheme) {
            "matrix" -> listOf(Color(0xFF0A0F0D), Color(0xFF101B14), Color(0xFF10B981), Color(0xFF1A3324))
            "amber" -> listOf(Color(0xFF140F0A), Color(0xFF1F170F), Color(0xFFF59E0B), Color(0xFF3B2B1B))
            "aurora" -> listOf(Color(0xFF0D1424), Color(0xFF141F36), Color(0xFF38BDF8), Color(0xFF1E3A5F))
            else -> listOf(Color(0xFF0F1117), Color(0xFF181A22), Color(0xFFE2E2E9), Color(0xFF282A36))
        }
    }

    val (terminalCharWidth, terminalLineHeight) = remember(fontSizeSp) {
        val paint = android.graphics.Paint().apply {
            textSize = with(density) { fontSizeSp.sp.toPx() }
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
        }
        val charWidth = paint.measureText("M")
        val lineHeight = paint.fontMetrics.run { descent - ascent }
        charWidth to lineHeight
    }

    var fieldText by remember { mutableStateOf("") }
    var lastText by remember { mutableStateOf("") }

    val copyScreen = {
        val text = screen.joinToString("\n") { line ->
            line.cells.joinToString("") { it.character.toString() }.trimEnd()
        }
        if (text.isNotBlank()) {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("终端输出", text))
            Toast.makeText(context, "已复制终端内容", Toast.LENGTH_SHORT).show()
        }
    }

    val pasteToTerminal = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (text.isNotBlank()) viewModel.sendText(text)
    }

    val terminalFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var visibleRows by remember { mutableStateOf(0) }
    var inputFocused by remember { mutableStateOf(false) }
    var isLeaving by remember { mutableStateOf(false) }
    var followOutput by remember { mutableStateOf(true) }
    var showSessions by remember { mutableStateOf(false) }
    var showCreateSession by remember { mutableStateOf(false) }

    LaunchedEffect(project) {
        viewModel.initialize(project)
    }

    LaunchedEffect(configuredFontSize) {
        fontSizeSp = configuredFontSize.toFloat()
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (isScrolling, canScrollForward) ->
                if (isScrolling) followOutput = !canScrollForward
            }
    }

    LaunchedEffect(fontSizeSp, terminalPxWidth, terminalPxHeight, activeId) {
        if (terminalPxWidth > 0f && terminalPxHeight > 0f) {
            // Pinch gestures can emit many frames per second. Text updates immediately;
            // native PTY resize follows the latest dimensions after a short debounce.
            delay(80)
            val renderWidthPx = terminalPxWidth - with(density) { 24.dp.toPx() }
            val rows = (terminalPxHeight / terminalLineHeight).toInt().coerceIn(5, 200)
            visibleRows = rows
            viewModel.resize(
                columns = (renderWidthPx / terminalCharWidth).toInt().coerceIn(20, 400),
                rows = rows,
            )
        }
    }

    LaunchedEffect(screen.size, cursor.row, cursor.column, followOutput, activeId) {
        if (followOutput && screen.isNotEmpty()) {
            listState.scrollToItem(screen.size - 1)
        }
    }

    LaunchedEffect(visibleRows, inputFocused) {
        if (inputFocused && screen.isNotEmpty()) {
            followOutput = true
            withFrameNanos { }
            listState.scrollToItem(screen.size - 1)
        }
    }

    LaunchedEffect(inputFocused) {
        if (inputFocused) {
            doShowKeyboard(view, context)
        }
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val navigateBack = {
        if (!isLeaving) {
            isLeaving = true
            if (inputFocused) {
                keyboard?.hide()
                focusManager.clearFocus(force = true)
                inputFocused = false
                // Let the IME begin its close animation before removing the terminal page.
                coroutineScope.launch {
                    delay(180)
                    onBack()
                }
            } else {
                onBack()
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = inputFocused) {
        keyboard?.hide()
        focusManager.clearFocus()
        inputFocused = false
    }

    LaunchedEffect(Unit) {
        terminalFocusRequester.requestFocus()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = if (project.isNotBlank()) "矩阵 · $project" else "太墟 · 矩阵控制台",
                onBack = navigateBack,
                statusText = "$distributionName · PRoot",
            ) {
                IconButton(onClick = { showSessions = true }) {
                    RuntimeIcon(RuntimeIconName.List, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 终端主窗口
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                color = termBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, termBorder),
            ) {
                Column {
                    // 终端窗口装饰条
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(termHeaderBg)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TerminalDot(Color(0xFFFF3366))
                            TerminalDot(Color(0xFFFFB300))
                            TerminalDot(Color(0xFF00E676))
                        }
                        Text(
                            "PTY ${distributionName.substringBefore(" (").uppercase()} AARCH64",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "复制",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(onClick = copyScreen)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = termTextDefault.copy(alpha = 0.6f),
                            )
                            Text(
                                "粘贴",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(onClick = pasteToTerminal)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = termTextDefault.copy(alpha = 0.6f),
                            )
                        }
                    }

                    // 终端渲染区域
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var zoomChanged = false
                                    do {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val pressedPointers = event.changes.count { it.pressed }
                                        if (pressedPointers >= 2) {
                                            val zoom = event.calculateZoom()
                                            if (zoom != 1f) {
                                                fontSizeSp = (fontSizeSp * zoom).coerceIn(
                                                    MIN_TERMINAL_FONT_SIZE_SP,
                                                    MAX_TERMINAL_FONT_SIZE_SP,
                                                )
                                                zoomChanged = true
                                            }
                                            event.changes.forEach { it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })

                                    if (zoomChanged) {
                                        viewModel.setTerminalFontSize(fontSizeSp.roundToInt())
                                    }
                                }
                            }
                            .onSizeChanged { size ->
                                terminalPxWidth = size.width.toFloat()
                                terminalPxHeight = size.height.toFloat()
                            },
                    ) {
                        when {
                            error != null -> Text(
                                error.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(12.dp),
                                fontFamily = FontFamily.Monospace,
                            )
                            screen.isEmpty() || screen.all { it.cells.isEmpty() } -> Text(
                                "启动太墟 Linux 环境中…",
                                Modifier.padding(12.dp),
                                color = TermDimText,
                                fontFamily = FontFamily.Monospace,
                            )
                            else -> LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        followOutput = true
                                        terminalFocusRequester.requestFocus()
                                        keyboard?.show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                state = listState,
                            ) {
                                itemsIndexed(
                                    screen,
                                    key = { index, _ -> index },
                                ) { index, line ->
                                    TerminalLineRow(
                                        line = line,
                                        showCursor = cursor.visible && index == cursor.row,
                                        cursorColumn = cursor.column,
                                        fontSizeSp = fontSizeSp,
                                        termBg = termBg,
                                        termTextDefault = termTextDefault,
                                    )
                                }
                            }
                        }

                        // 透明输入锚点
                        BasicTextField(
                            value = fieldText,
                            onValueChange = { new ->
                                val delta = if (new.startsWith(lastText) && new.length > lastText.length) {
                                    new.removePrefix(lastText)
                                } else {
                                    new
                                }
                                if (delta.isNotEmpty()) viewModel.sendText(delta)
                                lastText = new
                                fieldText = new
                            },
                            modifier = Modifier
                                .size(1.dp)
                                .align(Alignment.BottomStart)
                                .alpha(0f)
                                .focusRequester(terminalFocusRequester)
                                .onFocusChanged { inputFocused = it.isFocused }
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        when {
                                            event.key == Key.Enter -> {
                                                viewModel.sendText("\r")
                                                fieldText = ""
                                                lastText = ""
                                                true
                                            }
                                            event.key == Key.Backspace && !event.isCtrlPressed -> {
                                                viewModel.sendText("\u007f")
                                                fieldText = fieldText.dropLast(1)
                                                lastText = fieldText
                                                true
                                            }
                                            else -> viewModel.onTerminalKey(event)
                                        }
                                    } else false
                                },
                            textStyle = TextStyle(color = Color.Transparent, fontSize = 14.sp),
                            cursorBrush = SolidColor(Color.Transparent),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Send,
                                keyboardType = KeyboardType.Password,
                                autoCorrectEnabled = false,
                            ),
                            keyboardActions = KeyboardActions(onSend = {
                                viewModel.sendText("\r")
                                fieldText = ""
                                lastText = ""
                            }),
                            decorationBox = { innerTextField -> innerTextField() },
                        )
                    }
                }
            }

            // 移动端开发者辅助键盘条（Horizontal Key Strip with Haptics）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ExtraKey("ESC", isAccent = true, hapticsEnabled = hapticsEnabled) { viewModel.sendText("\u001B") }
                ExtraKey("Tab", isAccent = true, hapticsEnabled = hapticsEnabled) { viewModel.sendText("\u0009") }
                ExtraKey("Ctrl+C", isDanger = true, hapticsEnabled = hapticsEnabled) { viewModel.sendText("\u0003") }
                ExtraKey("Ctrl+D", hapticsEnabled = hapticsEnabled) { viewModel.sendText("\u0004") }
                ExtraKey("Ctrl+Z", hapticsEnabled = hapticsEnabled) { viewModel.sendText("\u001A") }
                ExtraKey("↑", hapticsEnabled = hapticsEnabled) { viewModel.sendText("\u001B[A") }
                ExtraKey("↓", hapticsEnabled = hapticsEnabled) { viewModel.sendText("\u001B[B") }
                ExtraKey("←", hapticsEnabled = hapticsEnabled) { viewModel.sendText("\u001B[D") }
                ExtraKey("→", hapticsEnabled = hapticsEnabled) { viewModel.sendText("\u001B[C") }
                ExtraKey("|", hapticsEnabled = hapticsEnabled) { viewModel.sendText("|") }
                ExtraKey("/", hapticsEnabled = hapticsEnabled) { viewModel.sendText("/") }
                ExtraKey("-", hapticsEnabled = hapticsEnabled) { viewModel.sendText("-") }
                ExtraKey("~", hapticsEnabled = hapticsEnabled) { viewModel.sendText("~") }
                ExtraKey("$", hapticsEnabled = hapticsEnabled) { viewModel.sendText("$") }
                ExtraKey("&&", hapticsEnabled = hapticsEnabled) { viewModel.sendText(" && ") }
                ExtraKey("cd ..", hapticsEnabled = hapticsEnabled) { viewModel.sendText("cd ..\r") }
                ExtraKey("Clear", hapticsEnabled = hapticsEnabled) { viewModel.sendText("clear\r") }
            }

            error?.let { NoticeBanner(it, isError = true) }
        }
    }

    if (showSessions) {
        SessionListDialog(
            handles = handles,
            activeId = activeId,
            onDismiss = { showSessions = false },
            onSwitch = { id -> viewModel.switchSession(id); showSessions = false },
            onCreate = {
                showSessions = false
                showCreateSession = true
            },
            onClose = { id ->
                viewModel.closeSession(id)
                if (handles.size == 1) {
                    Toast.makeText(context, "已重置并重启终端", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    if (showCreateSession) {
        CreateTerminalDialog(
            workspaces = workspaces,
            installedDistros = installedDistros,
            nextSessionIndex = handles.size + 1,
            onDismiss = { showCreateSession = false },
            onCreate = { label, workDir, distroId ->
                showCreateSession = false
                viewModel.createSession(label, workDir, distroId)
            },
        )
    }
}

@Composable
private fun CreateTerminalDialog(
    workspaces: List<top.wkbin.taixu.runtime.WorkspaceProject>,
    installedDistros: List<top.wkbin.taixu.core.model.InstalledDistro> = emptyList(),
    nextSessionIndex: Int,
    onDismiss: () -> Unit,
    onCreate: (label: String, workingDirectory: String, distroId: String?) -> Unit,
) {
    var label by remember { mutableStateOf("终端 $nextSessionIndex") }
    var selectedDir by remember { mutableStateOf("/root") }
    var selectedDistroId by remember { mutableStateOf(installedDistros.firstOrNull { it.isActive }?.id ?: installedDistros.firstOrNull()?.id ?: "ubuntu") }
    val quickLabels = listOf("主终端", "后台构建", "服务调试", "Git运维", "Python环境")

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text("新建终端会话", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (installedDistros.size > 1) {
                    Text("目标 Linux 发行版：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        installedDistros.forEach { d ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (selectedDistroId == d.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    if (selectedDistroId == d.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                                ),
                                modifier = Modifier.clickable { selectedDistroId = d.id },
                            ) {
                                Text(
                                    d.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selectedDistroId == d.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }

                Text("终端标签名称：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                androidx.compose.material3.OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = { Text("例如：后台构建 / Git 运维") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                // 快捷预设标签
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    quickLabels.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (label == tag) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                1.dp,
                                if (label == tag) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                            ),
                            modifier = Modifier.clickable { label = tag },
                        ) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (label == tag) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text("初始工作目录 (CWD)：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        TerminalDirOption(
                            name = "系统根主目录 (/root)",
                            path = "/root",
                            selected = selectedDir == "/root",
                            onSelect = { selectedDir = "/root" },
                        )
                    }
                    items(workspaces.size) { index ->
                        val ws = workspaces[index]
                        TerminalDirOption(
                            name = ws.name,
                            path = ws.linuxPath,
                            selected = selectedDir == ws.linuxPath,
                            onSelect = { selectedDir = ws.linuxPath },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(label.ifBlank { "终端 $nextSessionIndex" }, selectedDir, selectedDistroId) },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("创建终端")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun TerminalDirOption(
    name: String,
    path: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
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
            Modifier.size(10.dp).clip(CircleShape).background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Column {
            Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal))
            Text(path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SessionListDialog(
    handles: List<TerminalSessionHandle>,
    activeId: String?,
    onDismiss: () -> Unit,
    onSwitch: (String) -> Unit,
    onCreate: () -> Unit,
    onClose: (String) -> Unit,
) {
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("矩阵终端会话", fontWeight = FontWeight.Bold)
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        "${handles.size} 个活动会话",
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
                if (handles.size == 1) {
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
                                "当前为唯一活动终端，点击右侧重置图标将重启该会话",
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
                    items(handles.size) { index ->
                        val handle = handles[index]
                        val active = handle.id == activeId
                        val distroName = runCatching {
                            top.wkbin.taixu.runtime.DistributionCatalog.require(handle.distributionId).displayName
                        }.getOrDefault(handle.distributionId)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) Color(0xFF13243B) else Color(0xFF0F1626),
                            border = BorderStroke(
                                1.dp,
                                if (active) Color(0xFF00F0FF) else Color(0xFF1E2D48),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSwitch(handle.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    Modifier.size(8.dp).clip(CircleShape).background(
                                        if (active) Color(0xFF00F0FF) else Color(0xFF53637D),
                                    ),
                                )
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            handle.label,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Text(
                                                distroName,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                        if (active) {
                                            Surface(
                                                color = Color(0xFF00F0FF).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp),
                                            ) {
                                                Text(
                                                    "当前",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = Color(0xFF00F0FF),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        handle.workingDirectory,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (handles.size == 1) {
                                    IconButton(
                                        onClick = { onClose(handle.id) },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                                    }
                                } else {
                                    IconButton(
                                        onClick = { onClose(handle.id) },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        RuntimeIcon(RuntimeIconName.Close, Modifier.size(14.dp), MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onCreate, shape = RoundedCornerShape(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RuntimeIcon(RuntimeIconName.Plus, Modifier.size(16.dp), MaterialTheme.colorScheme.onPrimary)
                    Text("新建终端")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun ExtraKey(
    label: String,
    isAccent: Boolean = false,
    isDanger: Boolean = false,
    hapticsEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val bg = when {
        isDanger -> MaterialTheme.colorScheme.errorContainer
        isAccent -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textCol = when {
        isDanger -> MaterialTheme.colorScheme.onErrorContainer
        isAccent -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderCol = when {
        isDanger -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
        isAccent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = {
                if (hapticsEnabled) {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                }
                onClick()
            }),
        color = bg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, borderCol),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            ),
            color = textCol,
        )
    }
}

@Composable
private fun TerminalDot(color: Color) {
    Box(Modifier.size(8.dp).background(color, CircleShape))
}

@Composable
private fun TerminalLineRow(
    line: TerminalLine,
    showCursor: Boolean,
    cursorColumn: Int,
    fontSizeSp: Float = 13.5f,
    termBg: Color = TermBg,
    termTextDefault: Color = TermTextDefault,
) {
    Text(
        text = buildAnnotatedString {
            line.cells.forEachIndexed { cellIndex, cell ->
                val isCursor = showCursor && cellIndex == cursorColumn
                withStyle(
                    SpanStyle(
                        color = if (isCursor) termBg else (cell.foreground?.let(::Color) ?: termTextDefault),
                        background = if (isCursor) Color(0xFF00F0FF) else Color.Unspecified,
                        fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
                    ),
                ) { append(cell.character) }
            }
            if (showCursor && cursorColumn >= line.cells.size) {
                repeat(cursorColumn - line.cells.size) { append(" ") }
                withStyle(SpanStyle(color = termBg, background = Color(0xFF00F0FF))) { append(" ") }
            }
        },
        fontFamily = FontFamily.Monospace,
        color = termTextDefault,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.35f).sp,
        ),
    )
}

private fun doShowKeyboard(view: View, context: Context) {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
    imm.showSoftInput(view, 0)
}
