package top.wkbin.taixu.ui.workspace

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.ui.components.NoticeBanner
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName

private val EditorBackground = Color(0xFF0F1117)
private val EditorGutterBackground = Color(0xFF181A22)
private val EditorText = Color(0xFFE2E2E9)
private val EditorGutterText = Color(0xFF8E9099)
private val EditorAccent = Color(0xFFBAC3FF)
private val EditorWarning = Color(0xFFFFB5A0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    projectName: String,
    relativePath: String,
    onBack: () -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val fileContent by viewModel.fileContent.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val loading by viewModel.loadingFiles.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showUnsavedDialog by remember { mutableStateOf(false) }
    var wordWrap by remember { mutableStateOf(false) }

    val fileName = relativePath.substringAfterLast('/')
    val extension = relativePath.substringAfterLast('.', "")

    LaunchedEffect(projectName, relativePath) {
        viewModel.openFile(projectName, relativePath)
    }

    val attemptBack = {
        if (isDirty) {
            showUnsavedDialog = true
        } else {
            viewModel.closeFile()
            onBack()
        }
    }

    BackHandler(onBack = attemptBack)

    val copyAll = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(fileName, fileContent))
        Toast.makeText(context, "已复制全部代码", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = EditorBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.titleMedium,
                                color = EditorText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isDirty) {
                                Text(
                                    text = "●",
                                    color = Color(0xFFFFA657),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        Text(
                            text = "/workspace/$projectName/$relativePath",
                            style = MaterialTheme.typography.labelSmall,
                            color = EditorGutterText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = attemptBack) {
                        RuntimeIcon(RuntimeIconName.Back, Modifier.size(22.dp), tint = EditorText)
                    }
                },
                actions = {
                    // 复制
                    IconButton(onClick = copyAll) {
                        RuntimeIcon(RuntimeIconName.Copy, Modifier.size(19.dp), tint = EditorGutterText)
                    }

                    // 重置
                    if (isDirty) {
                        IconButton(onClick = { viewModel.resetContent() }) {
                            RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(19.dp), tint = Color(0xFFFFA657))
                        }
                    }

                    // 保存
                    IconButton(
                        onClick = { viewModel.saveFile() },
                        enabled = isDirty && !isSaving,
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = EditorAccent, strokeWidth = 2.dp)
                        } else {
                            RuntimeIcon(
                                RuntimeIconName.Save,
                                Modifier.size(20.dp),
                                tint = if (isDirty) EditorAccent else EditorGutterText.copy(alpha = 0.5f),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EditorGutterBackground),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            // 提示信息
            message?.let { notice ->
                Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    NoticeBanner(text = notice, isError = notice.contains("失败") || notice.contains("错误"))
                }
            }

            if (loading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(36.dp), color = EditorAccent)
                }
            } else {
                // 代码编辑核心区域
                val lines = remember(fileContent) { fileContent.split('\n') }
                val lineCount = lines.size
                val scrollState = rememberScrollState()
                val horizontalScrollState = rememberScrollState()

                // 高亮语法生成
                val syntaxTransformation = remember(extension) {
                    VisualTransformation { text ->
                        androidx.compose.ui.text.input.TransformedText(
                            text = SyntaxHighlighter.highlight(text.text, extension),
                            offsetMapping = androidx.compose.ui.text.input.OffsetMapping.Identity,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(EditorBackground),
                ) {
                    // 行号列
                    val gutterWidth = (lineCount.toString().length * 10 + 24).dp.coerceAtLeast(42.dp)
                    Column(
                        modifier = Modifier
                            .width(gutterWidth)
                            .fillMaxHeight()
                            .background(EditorGutterBackground)
                            .verticalScroll(scrollState)
                            .padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        for (i in 1..lineCount) {
                            Text(
                                text = "$i",
                                style = TextStyle(
                                    color = EditorGutterText,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 20.sp,
                                ),
                                textAlign = TextAlign.End,
                            )
                        }
                    }

                    // 垂直分割线
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF1E2638)),
                    )

                    // 编辑区
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(scrollState)
                            .then(
                                if (!wordWrap) Modifier.horizontalScroll(horizontalScrollState)
                                else Modifier,
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        BasicTextField(
                            value = fileContent,
                            onValueChange = viewModel::onContentChanged,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                color = EditorText,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 20.sp,
                            ),
                            cursorBrush = SolidColor(EditorAccent),
                            visualTransformation = syntaxTransformation,
                        )
                    }
                }
            }

            // 底部状态栏
            EditorStatusBar(
                extension = extension,
                lineCount = remember(fileContent) { fileContent.count { it == '\n' } + 1 },
                charCount = fileContent.length,
                isDirty = isDirty,
                wordWrap = wordWrap,
                onToggleWrap = { wordWrap = !wordWrap },
            )
        }
    }

    // 未保存退出提示
    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("未保存修改") },
            text = { Text("当前文件有未保存的更改，是否在退出前保存？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveFile(onSuccess = {
                            showUnsavedDialog = false
                            viewModel.closeFile()
                            onBack()
                        })
                    },
                ) { Text("保存并退出") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUnsavedDialog = false
                        viewModel.closeFile()
                        onBack()
                    },
                ) { Text("放弃更改", color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

@Composable
private fun EditorStatusBar(
    extension: String,
    lineCount: Int,
    charCount: Int,
    isDirty: Boolean,
    wordWrap: Boolean,
    onToggleWrap: () -> Unit,
) {
    Surface(
        color = EditorGutterBackground,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 语言类型
            Text(
                text = if (extension.isNotBlank()) extension.uppercase() else "PLAIN TEXT",
                style = MaterialTheme.typography.labelSmall,
                color = EditorAccent,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = "•",
                color = EditorGutterText,
                style = MaterialTheme.typography.labelSmall,
            )

            // 行数与字数
            Text(
                text = "$lineCount 行  $charCount 字符",
                style = MaterialTheme.typography.labelSmall,
                color = EditorGutterText,
                fontFamily = FontFamily.Monospace,
            )

            Spacer(Modifier.weight(1f))

            // 自动换行切换
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (wordWrap) EditorAccent.copy(alpha = 0.2f) else Color.Transparent,
                modifier = Modifier.clickable(onClick = onToggleWrap),
            ) {
                Text(
                    text = if (wordWrap) "自动换行: 开" else "自动换行: 关",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (wordWrap) EditorAccent else EditorGutterText,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            // 保存状态
            Text(
                text = if (isDirty) "未保存" else "已保存",
                style = MaterialTheme.typography.labelSmall,
                color = if (isDirty) EditorWarning else EditorAccent,
            )
        }
    }
}
