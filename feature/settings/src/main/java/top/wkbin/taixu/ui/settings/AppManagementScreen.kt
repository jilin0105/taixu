package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.core.database.AndroidAppEntity
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.settings.LocalizedText as Text

@Composable
fun AppManagementScreen(
    onBack: () -> Unit,
    viewModel: AppManagementViewModel = hiltViewModel(),
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var showSystemApps by remember { mutableStateOf(false) }

    // Every entry reconciles the live privileged package list with Room: missing packages are
    // removed and newly installed ones are inserted, without rebuilding the UI's data source.
    LaunchedEffect(Unit) { viewModel.synchronize() }
    val userApps = apps.filterNot { it.isSystemApp }
    val systemApps = apps.filter { it.isSystemApp }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar("应用管理", onBack, statusText = if (syncing) "正在同步" else "普通读取 · 特权增强") },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                RuntimeCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("受控应用索引", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "任何模式均可读取应用并写入 Room；以后进入只增量对比包名。Shizuku/Root 会额外读取冻结和后台联网限制状态，冻结或授权等执行操作在 PRoot 模式会提示权限不足。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RuntimeButton(onClick = viewModel::synchronize, enabled = !syncing) {
                                if (syncing) RuntimeCircularProgressIndicator(Modifier.size(18.dp)) else RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(18.dp))
                                Text(if (syncing) "同步中" else "立即同步", modifier = Modifier.padding(start = 8.dp))
                            }
                            Text("共 ${apps.size} 个", style = MaterialTheme.typography.labelLarge)
                        }
                        message?.let {
                            Text(it, color = if (it.startsWith("权限不足")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = true, onClick = {}, label = { Text("用户应用 (${userApps.size})") })
                    FilterChip(
                        selected = showSystemApps,
                        onClick = { showSystemApps = !showSystemApps },
                        label = { Text("显示系统应用 (${systemApps.size})") },
                    )
                }
            }
            if (userApps.isEmpty() && !syncing) {
                item { Text("暂无应用数据，请执行一次同步。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(userApps, key = { it.packageName }) { app -> AndroidAppRow(app) }
            if (showSystemApps && systemApps.isNotEmpty()) {
                item {
                    Text(
                        "系统应用",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(systemApps, key = { it.packageName }) { app -> AndroidAppRow(app) }
            }
        }
    }
}

@Composable
private fun AndroidAppRow(app: AndroidAppEntity) {
    RuntimeCard(contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(app.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppStatusChip(if (app.isEnabled) "已启用" else "已禁用/冻结", !app.isEnabled)
                if (app.isSuspended) AppStatusChip("已冻结", true)
                if (app.isNetworkRestricted) AppStatusChip("后台联网受限", true)
            }
        }
    }
}

@Composable
private fun AppStatusChip(label: String, warning: Boolean) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        ),
    )
}
