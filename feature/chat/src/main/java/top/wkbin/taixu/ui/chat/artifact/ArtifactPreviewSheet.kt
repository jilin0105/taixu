package top.wkbin.taixu.ui.chat.artifact

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.ui.chat.MarkdownText
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton
import top.wkbin.taixu.ui.components.SyntaxHighlighter
import top.wkbin.taixu.ui.components.TaiXuGlassPanel

enum class ArtifactViewMode {
    PREVIEW,
    CODE,
    EDIT,
}

/**
 * 太墟产物交付与全功能预览浮层 (Artifact Preview Sheet)
 *
 * 1. 支持 Markdown 富文本解析与渲染；
 * 2. 支持全语种代码语法高亮与行号显示；
 * 3. 支持无缝切换即时就地编辑（Edit Mode）；
 * 4. 提供一键复制、Android 原生系统分享与导出通道。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactPreviewSheet(
    title: String,
    content: String,
    relativePath: String = "",
    workspace: String = "",
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismiss: () -> Unit,
    onSaveContent: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val extension = remember(relativePath, title) {
        val target = if (relativePath.isNotEmpty()) relativePath else title
        target.substringAfterLast('.', "").lowercase()
    }

    val isMarkdown = extension == "md" || extension == "markdown"
    var viewMode by remember {
        mutableStateOf(if (isMarkdown) ArtifactViewMode.PREVIEW else ArtifactViewMode.CODE)
    }

    var editableContent by remember(content) { mutableStateOf(content) }
    val isDirty = editableContent != content

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 680.dp),
        ) {
            // 1. 顶栏标题与模式切换
            TaiXuGlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // 左侧图标与文件名
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                RuntimeIcon(
                                    name = when {
                                        isMarkdown -> RuntimeIconName.Document
                                        extension in setOf("kt", "java", "py", "c", "cpp", "rs", "go", "js", "ts") -> RuntimeIconName.Code
                                        else -> RuntimeIconName.File
                                    },
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = title.ifEmpty { relativePath.substringAfterLast('/') },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (isDirty) {
                                    Text(
                                        text = " *",
                                        color = Color(0xFFFF9800),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                    )
                                }
                            }
                            if (relativePath.isNotEmpty()) {
                                Text(
                                    text = if (workspace.isNotEmpty()) "$workspace/$relativePath" else relativePath,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // 右侧模式切换胶囊
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isMarkdown) {
                            ModeChip(
                                label = "预览",
                                selected = viewMode == ArtifactViewMode.PREVIEW,
                                onClick = { viewMode = ArtifactViewMode.PREVIEW },
                            )
                        }
                        ModeChip(
                            label = "源码",
                            selected = viewMode == ArtifactViewMode.CODE,
                            onClick = { viewMode = ArtifactViewMode.CODE },
                        )
                        if (onSaveContent != null) {
                            ModeChip(
                                label = "编辑",
                                selected = viewMode == ArtifactViewMode.EDIT,
                                onClick = { viewMode = ArtifactViewMode.EDIT },
                            )
                        }
                        RuntimeIconButton(
                            onClick = onDismiss,
                            contentDescription = "关闭",
                        ) {
                            RuntimeIcon(RuntimeIconName.Close, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 2. 主体内容区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF090D14)),
            ) {
                when (viewMode) {
                    ArtifactViewMode.PREVIEW -> {
                        // Markdown 渲染模式
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        ) {
                            MarkdownText(
                                markdown = editableContent,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    ArtifactViewMode.CODE -> {
                        // 语法高亮代码模式
                        val highlighted = remember(editableContent, extension) {
                            SyntaxHighlighter.highlight(editableContent, extension)
                        }
                        val lines = remember(editableContent) { editableContent.lines() }

                        SelectionContainer {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp),
                            ) {
                                // 行号栏
                                Column(
                                    modifier = Modifier.padding(end = 12.dp),
                                    horizontalAlignment = Alignment.End,
                                ) {
                                    lines.indices.forEach { idx ->
                                        Text(
                                            text = "${idx + 1}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            color = Color.White.copy(alpha = 0.35f),
                                        )
                                    }
                                }

                                // 语法高亮正文
                                Text(
                                    text = highlighted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = Color(0xFFDCE6F5),
                                )
                            }
                        }
                    }

                    ArtifactViewMode.EDIT -> {
                        // 就地编辑模式
                        BasicTextField(
                            value = editableContent,
                            onValueChange = { editableContent = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState()),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = Color(0xFFDCE6F5),
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 3. 底栏操作通道
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 复制全文
                    RuntimeOutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText(title, editableContent))
                            Toast.makeText(context, "已复制产物全文到剪贴板", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        RuntimeIcon(RuntimeIconName.Copy, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("复制", fontSize = 12.sp)
                    }

                    // 系统分享
                    RuntimeOutlinedButton(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, editableContent)
                                putExtra(Intent.EXTRA_TITLE, title)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "分享产物: $title"))
                        },
                    ) {
                        RuntimeIcon(RuntimeIconName.OpenInNew, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("分享", fontSize = 12.sp)
                    }
                }

                // 保存/保存修改
                if (viewMode == ArtifactViewMode.EDIT && onSaveContent != null) {
                    RuntimeButton(
                        onClick = {
                            onSaveContent(editableContent)
                            Toast.makeText(context, "已保存修改", Toast.LENGTH_SHORT).show()
                            viewMode = if (isMarkdown) ArtifactViewMode.PREVIEW else ArtifactViewMode.CODE
                        },
                        enabled = isDirty,
                    ) {
                        RuntimeIcon(RuntimeIconName.Check, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("保存修改", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
