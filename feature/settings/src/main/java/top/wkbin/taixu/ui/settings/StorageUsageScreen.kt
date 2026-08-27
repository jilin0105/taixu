package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.runtime.StorageCategory
import top.wkbin.taixu.runtime.StorageUsage
import top.wkbin.taixu.ui.components.NoticeBanner
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.components.RuntimeTopBar
import java.util.Locale

@Composable
fun StorageUsageScreen(
    onBack: () -> Unit,
    viewModel: StorageUsageViewModel = hiltViewModel(),
) {
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var clearCacheConfirmation by remember { mutableStateOf(false) }

    if (clearCacheConfirmation) {
        RuntimeAlertDialog(
            onDismissRequest = { clearCacheConfirmation = false },
            title = { androidx.compose.material3.Text("清理下载缓存？") },
            text = { androidx.compose.material3.Text("只会清理运行时下载缓存，不会删除项目、插件或 Skills。") },
            confirmButton = {
                RuntimeTextButton(onClick = { clearCacheConfirmation = false; viewModel.clearCache() }) { androidx.compose.material3.Text("清理") }
            },
            dismissButton = { RuntimeTextButton(onClick = { clearCacheConfirmation = false }) { androidx.compose.material3.Text("取消") } },
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        RuntimeTopBar("存储管理", onBack, statusText = usage?.totalManagedBytes?.readableSize() ?: "统计中")
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                usage?.let { StorageSummary(it) } ?: RuntimeCard(Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Text("正在统计存储占用。首次统计会扫描本地文件，可能需要一点时间。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RuntimeOutlinedButton(onClick = viewModel::refresh, enabled = !refreshing, modifier = Modifier.weight(1f)) {
                        androidx.compose.material3.Text(if (refreshing) "统计中…" else "刷新统计")
                    }
                    RuntimeOutlinedButton(onClick = { clearCacheConfirmation = true }, enabled = !refreshing, modifier = Modifier.weight(1f)) {
                        androidx.compose.material3.Text("清理下载缓存")
                    }
                }
            }
            message?.let { item { NoticeBanner(it, isError = it.startsWith("读取") || it.startsWith("清理缓存失败")) } }
            item {
                androidx.compose.material3.Text("按大类查看", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            usage?.categories?.forEachIndexed { index, category ->
                item(key = category.id) { StorageCategoryCard(category, categoryColor(index)) }
            }
        }
    }
}

@Composable
private fun StorageSummary(usage: StorageUsage) = RuntimeCard(Modifier.fillMaxWidth()) {
    androidx.compose.material3.Text("太墟已管理", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    androidx.compose.material3.Text(usage.totalManagedBytes.readableSize(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth().height(12.dp).clip(MaterialTheme.shapes.small)) {
        val visible = usage.categories.filter { it.bytes > 0L }
        if (visible.isEmpty()) Spacer(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant))
        visible.forEachIndexed { index, category ->
            Spacer(Modifier.weight(category.bytes.toFloat()).fillMaxSize().background(categoryColor(index)))
        }
    }
    Spacer(Modifier.height(12.dp))
    androidx.compose.material3.Text("设备可用空间 ${usage.availableBytes.readableSize()} · 点击下方大类展开明细", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StorageCategoryCard(category: StorageCategory, color: Color) {
    var expanded by remember(category.id) { mutableStateOf(false) }
    RuntimeCard(Modifier.fillMaxWidth().clickable { if (category.entries.isNotEmpty()) expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.size(12.dp).clip(MaterialTheme.shapes.small).background(color))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                androidx.compose.material3.Text(category.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                androidx.compose.material3.Text(
                    if (category.entries.isEmpty()) "无可展开项目" else "${category.entries.size} 项 · ${if (expanded) "收起明细" else "查看明细"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.Text(category.bytes.readableSize(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            category.entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        androidx.compose.material3.Text(entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        if (entry.detail.isNotBlank()) androidx.compose.material3.Text(entry.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Text(entry.bytes.readableSize(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun categoryColor(index: Int): Color = listOf(
    Color(0xFF5D7DF6), Color(0xFF9C6ADE), Color(0xFF1D9E75), Color(0xFFE3863A), Color(0xFF6D7E91), Color(0xFFD16B86), Color(0xFF4788C7), Color(0xFF8D6E63),
)[index % 8]

private fun Long.readableSize(): String = when {
    this < 1024L -> "$this B"
    this < 1024L * 1024 -> String.format(Locale.US, "%.1f KB", this / 1024.0)
    this < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", this / (1024.0 * 1024))
    else -> String.format(Locale.US, "%.2f GB", this / (1024.0 * 1024 * 1024))
}
