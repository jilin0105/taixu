package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.harness.HarnessTool
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val DiffAddedBg = Color(0xFF0E2E1E)
private val DiffAddedText = Color(0xFF7EE787)
private val DiffRemovedBg = Color(0xFF38141B)
private val DiffRemovedText = Color(0xFFFFA198)
private val DiffHeaderBg = Color(0xFF0F1523)
private val TerminalBoxBg = Color(0xFF070B12)

@Composable
fun ToolDiffView(
    call: ToolCall,
    result: ToolResult?,
    workspace: String = "",
    onOpenFile: ((projectName: String, relativePath: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF090D14), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (call.tool) {
            HarnessTool.EDIT -> EditToolDiff(call, result, workspace, onOpenFile)
            HarnessTool.WRITE -> WriteToolDiff(call, result, workspace, onOpenFile)
            HarnessTool.READ -> ReadToolDiff(call, result, workspace, onOpenFile)
            HarnessTool.BASE -> BaseToolDiff(call, result)
            HarnessTool.MEMORY, HarnessTool.PLAN, HarnessTool.SCRATCHPAD, HarnessTool.SUBAGENT, HarnessTool.MCP -> BaseToolDiff(call, result)
        }
    }
}

@Composable
private fun EditToolDiff(
    call: ToolCall,
    result: ToolResult?,
    workspace: String,
    onOpenFile: ((String, String) -> Unit)?,
) {
    val path = call.args["path"]?.jsonPrimitive?.content.orEmpty()
    val oldText = call.args["oldText"]?.jsonPrimitive?.content.orEmpty()
    val newText = call.args["newText"]?.jsonPrimitive?.content.orEmpty()

    // 顶部路径与在编辑器中打开按钮
    FilePathHeader(path = path, workspace = workspace, onOpenFile = onOpenFile)

    // Diff 区域
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF06090F), RoundedCornerShape(6.dp))
            .padding(vertical = 4.dp),
    ) {
        val oldLines = remember(oldText) { if (oldText.isEmpty()) emptyList() else oldText.split('\n') }
        val newLines = remember(newText) { if (newText.isEmpty()) emptyList() else newText.split('\n') }

        // 删除行
        oldLines.forEach { line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DiffRemovedBg)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "- $line",
                    color = DiffRemovedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        // 新增行
        newLines.forEach { line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DiffAddedBg)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "+ $line",
                    color = DiffAddedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }

    // 执行状态提示
    result?.let {
        if (!it.success) {
            Text(
                text = it.output,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun WriteToolDiff(
    call: ToolCall,
    result: ToolResult?,
    workspace: String,
    onOpenFile: ((String, String) -> Unit)?,
) {
    val path = call.args["path"]?.jsonPrimitive?.content.orEmpty()
    val content = call.args["content"]?.jsonPrimitive?.content.orEmpty()

    FilePathHeader(path = path, workspace = workspace, onOpenFile = onOpenFile)

    val lineCount = remember(content) { content.count { it == '\n' } + 1 }
    Text(
        text = "写入全量文件（$lineCount 行，${content.length} 字符）",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // 内容预览（前 10 行）
    val preview = remember(content) {
        content.lines().take(10).joinToString("\n") +
            if (lineCount > 10) "\n… (共 $lineCount 行)" else ""
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF06090F), RoundedCornerShape(6.dp))
            .horizontalScroll(rememberScrollState())
            .padding(8.dp),
    ) {
        Text(
            text = preview,
            color = Color(0xFFDCE6F5),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }

    result?.let {
        if (!it.success) {
            Text(it.output, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ReadToolDiff(
    call: ToolCall,
    result: ToolResult?,
    workspace: String,
    onOpenFile: ((String, String) -> Unit)?,
) {
    val path = call.args["path"]?.jsonPrimitive?.content.orEmpty()
    FilePathHeader(path = path, workspace = workspace, onOpenFile = onOpenFile)

    result?.let { res ->
        if (res.success) {
            val preview = res.output.lines().take(8).joinToString("\n") +
                if (res.output.lines().size > 8) "\n… (已读取 ${res.output.length} 字符)" else ""
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF06090F), RoundedCornerShape(6.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
            ) {
                Text(
                    text = preview,
                    color = Color(0xFF8FA1BA),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        } else {
            Text(res.output, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun BaseToolDiff(
    call: ToolCall,
    result: ToolResult?,
) {
    val command = call.args["command"]?.jsonPrimitive?.content.orEmpty()
    val cwd = call.args["cwd"]?.jsonPrimitive?.content.orEmpty()

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(14.dp), tint = Color(0xFF8FA1BA))
        Text(
            text = if (cwd.isNotBlank()) cwd else "/root",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF8FA1BA),
            fontFamily = FontFamily.Monospace,
        )
    }

    // 命令执行行
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF06090F), RoundedCornerShape(6.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = "$ $command",
            color = Color(0xFFFFA657),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }

    // 输出结果
    result?.let { res ->
        val output = res.output.trim()
        if (output.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalBoxBg, RoundedCornerShape(6.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                Text(
                    text = output.take(2000) + if (output.length > 2000) "\n… (输出已截断)" else "",
                    color = if (res.success) Color(0xFFDCE6F5) else Color(0xFFFF8896),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun FilePathHeader(
    path: String,
    workspace: String,
    onOpenFile: ((String, String) -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            RuntimeIcon(RuntimeIconName.File, Modifier.size(15.dp), tint = Color(0xFF8FA1BA))
            Text(
                text = path,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFDCE6F5),
                fontWeight = FontWeight.Medium,
            )
        }

        // 解析工作区项目名与相对路径，提供跳转
        val projectName = remember(workspace, path) {
            when {
                workspace.startsWith("/workspace/") -> workspace.removePrefix("/workspace/").substringBefore('/')
                path.startsWith("/workspace/") -> path.removePrefix("/workspace/").substringBefore('/')
                else -> ""
            }
        }
        val relativePath = remember(workspace, path, projectName) {
            when {
                path.startsWith("/workspace/$projectName/") -> path.removePrefix("/workspace/$projectName/")
                path.startsWith("/workspace/") -> path.substringAfter("/workspace/")
                path.startsWith("/") -> path.removePrefix("/")
                else -> path
            }
        }

        if (projectName.isNotBlank() && onOpenFile != null) {
            Surface(
                color = Color(0xFF1E283D),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.clickable { onOpenFile(projectName, relativePath) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RuntimeIcon(RuntimeIconName.Code, Modifier.size(12.dp), tint = Color(0xFF7EE787))
                    Text(
                        text = "编辑器打开",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7EE787),
                    )
                }
            }
        }
    }
}
