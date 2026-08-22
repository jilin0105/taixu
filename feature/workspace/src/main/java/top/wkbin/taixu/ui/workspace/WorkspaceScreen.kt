package top.wkbin.taixu.ui.workspace

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import top.wkbin.taixu.runtime.ApkImportSource
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.runtime.WorkspaceStorage
import top.wkbin.taixu.ui.components.EmptyPanel
import top.wkbin.taixu.ui.components.IconTile
import top.wkbin.taixu.ui.components.MainDestination
import top.wkbin.taixu.ui.components.NoticeBanner
import top.wkbin.taixu.ui.components.RuntimeBottomBar
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.SectionHeader
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.flow.Flow
import top.wkbin.taixu.runtime.build.StepDuration

/**
 * 太墟 · 工坊空间 (Workspace Space)
 * 管理 Linux 隔离工作区、代码工程与文件项目
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun WorkspaceScreen(
    onNavigate: (MainDestination) -> Unit,
    onOpenExplorer: (String) -> Unit,
    onOpenTerminal: (String) -> Unit,
    onOpenToolCenter: () -> Unit = {},
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val buildProgress by viewModel.buildProgress.collectAsStateWithLifecycle()
    val activeBuildingProjectName by viewModel.activeBuildingProjectName.collectAsStateWithLifecycle()
    val isBuildDialogVisible by viewModel.isBuildDialogVisible.collectAsStateWithLifecycle()
    val installedComponentIds by viewModel.installedComponentIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCreate by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf(top.wkbin.taixu.runtime.ProjectTemplate.ANDROID_COMPOSE) }
    var projectName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var gitUrl by remember { mutableStateOf("") }
    var projectStorage by remember { mutableStateOf(WorkspaceStorage.INTERNAL) }
    var directoryPath by remember { mutableStateOf("") }
    var internalDirectoryMenuExpanded by remember { mutableStateOf(false) }
    var permissionRefresh by remember { mutableStateOf(0) }
    var deleteTarget by remember { mutableStateOf<WorkspaceProject?>(null) }
    var apkSource by remember { mutableStateOf<ApkImportSource?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    var exportApkToDownload by remember { mutableStateOf(false) }

    val legacyStoragePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRefresh++
    }
    val allFilesPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionRefresh++
    }
    // APK 逆向模板：系统文件管理器选择 .apk
    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val displayName = queryDisplayName(context, uri) ?: "target.apk"
            apkSource = ApkImportSource.FromFileUri(uri.toString(), displayName)
        }
    }
    val directoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                val documentId = DocumentsContract.getTreeDocumentId(uri)
                if (documentId.startsWith("primary:")) {
                    directoryPath = documentId.substringAfter(':')
                }
            }
        }
    }
    val sharedAccessGranted = permissionRefresh.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = "工坊 · 空间",
                statusText = "${projects.size} 个活动工程",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenToolCenter, enabled = !busy) {
                        RuntimeIcon(RuntimeIconName.Package, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showCreate = true }, enabled = !busy) {
                        RuntimeIcon(RuntimeIconName.Plus, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        bottomBar = { RuntimeBottomBar(MainDestination.Workspace, onNavigate) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            message?.let { notice ->
                NoticeBanner(
                    text = notice,
                    isError = notice.contains("失败") || notice.contains("无效") || notice.contains("存在"),
                )
            }

            // 🔨 后台构建常驻状态栏 (Banner)
            if (activeBuildingProjectName != null && !isBuildDialogVisible) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.showBuildDialog() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "正在后台编译: $activeBuildingProjectName",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = buildProgress?.step ?: "正在执行构建任务...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { viewModel.showBuildDialog() }) {
                            Text("查看日志", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else if (activeBuildingProjectName == null && buildProgress != null && !isBuildDialogVisible) {
                val progress = buildProgress!!
                Surface(
                    color = if (progress.isSuccess == true) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RuntimeIcon(
                            if (progress.isSuccess == true) RuntimeIconName.Check else RuntimeIconName.Close,
                            Modifier.size(18.dp),
                            tint = if (progress.isSuccess == true) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (progress.isSuccess == true) "构建完成：已就绪" else "构建失败",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (progress.isSuccess == true) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            )
                            progress.message?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (progress.isSuccess == true) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (progress.isSuccess == true && progress.apkPath != null) {
                                TextButton(onClick = { viewModel.launchInstaller(progress.apkPath!!) }) {
                                    Text("安装", fontWeight = FontWeight.Bold)
                                }
                            }
                            TextButton(onClick = { viewModel.showBuildDialog() }) {
                                Text("详情")
                            }
                            IconButton(onClick = { viewModel.dismissBuildProgress() }, modifier = Modifier.size(28.dp)) {
                                RuntimeIcon(RuntimeIconName.Close, Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // 宿主外部存储快速访问入口
            RuntimeCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                onClick = { onOpenExplorer("sdcard") },
                contentPadding = PaddingValues(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        RuntimeIcon(
                            RuntimeIconName.Folder,
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "宿主共享存储 (/sdcard)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "已挂载 Android Download 与共享目录，零拷贝直达",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    RuntimeIcon(
                        RuntimeIconName.ChevronRight,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionHeader(
                title = "工坊隔离工程",
                subtitle = "位于 Linux 沙箱 /workspace 目录下，与 Agent 实时互通",
            )

            if (projects.isEmpty()) {
                EmptyPanel(
                    icon = RuntimeIconName.Workspace,
                    title = "还没有工坊工程",
                    description = "点击右上角 ➕ 选择 Android / Flutter / APK 逆向模板或自定义创建工程",
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                projects.forEach { project ->
                    ProjectCard(
                        project = project,
                        busy = busy,
                        isBuilding = (activeBuildingProjectName == project.name),
                        onOpenExplorer = { onOpenExplorer(project.name) },
                        onOpenTerminal = { onOpenTerminal(project.name) },
                        onOpenAgent = { onNavigate(MainDestination.Agent) },
                        onRunProject = { viewModel.runProject(project) },
                        onShowBuildLog = { viewModel.showBuildDialog() },
                        onDelete = { deleteTarget = project },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // 运行/构建进度与实时日志弹窗 (支持后台运行与随时最小化)
    if (isBuildDialogVisible && buildProgress != null) {
        val progress = buildProgress!!
        var showBuildLog by remember { mutableStateOf(false) }
        var showStepAnalysis by remember { mutableStateOf(false) }
        // Category collapse states: dependencies & others collapsed by default; compile & package expanded
        var collapsedDeps by remember { mutableStateOf(true) }
        var collapsedCompile by remember { mutableStateOf(false) }
        var collapsedPackage by remember { mutableStateOf(false) }
        var collapsedOther by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { viewModel.hideBuildDialog() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (progress.isRunning) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    Text(if (progress.isRunning) "正在构建到手机..." else (if (progress.isSuccess == true) "运行就绪" else "运行失败"), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(progress.step, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                    if (progress.isRunning) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        )
                    }
                    progress.message?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (progress.isSuccess == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // ====== 构建阶段耗时分析 ======
                    if (progress.stepDurations.isNotEmpty()) {
                        TextButton(
                            onClick = { showStepAnalysis = !showStepAnalysis },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                RuntimeIcon(if (showStepAnalysis) RuntimeIconName.ArrowUp else RuntimeIconName.ChevronDown, Modifier.size(14.dp))
                                Text(if (showStepAnalysis) "收起构建耗时分析" else "📊 构建耗时分析", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        if (showStepAnalysis) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val maxDuration = (progress.stepDurations.maxOfOrNull { it.durationMs } ?: 1L).coerceAtLeast(1L)
                                    progress.stepDurations.forEach { step ->
                                        val barRatio = (step.durationMs.toFloat() / maxDuration.toFloat()).coerceIn(0.02f, 1f)
                                        val barColor = when {
                                            step.step.contains("拉取") || step.step.contains("依赖") -> androidx.compose.ui.graphics.Color(0xFF2196F3) // 蓝色
                                            step.step.contains("编译") || step.step.contains("Kotlin") || step.step.contains("Java") || step.step.contains("Dex") -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // 绿色
                                            step.step.contains("打包") || step.step.contains("APK") -> androidx.compose.ui.graphics.Color(0xFFFF9800) // 橙色
                                            else -> androidx.compose.ui.graphics.Color(0xFF9E9E9E) // 灰色
                                        }
                                        val durationText = if (step.durationMs >= 1000) "${"%.1f".format(step.durationMs / 1000.0)}s" else "${step.durationMs}ms"
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Text(
                                                    text = step.step,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                                Text(
                                                    text = durationText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(barRatio)
                                                        .height(6.dp)
                                                        .clip(RoundedCornerShape(3.dp))
                                                        .background(barColor),
                                                )
                                            }
                                        }
                                    }
                                    // 总时长
                                    progress.totalDurationMs?.let { total ->
                                        val totalText = if (total >= 1000) "${"%.1f".format(total / 1000.0)}s" else "${total}ms"
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                text = "总计",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = totalText,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ====== 构建日志折叠面板 ======
                    // The build task survives destination recreation in the
                    // coordinator. Its first restored snapshot may not have
                    // received log text yet, so keep the entry visible while
                    // running (and for completed tasks) instead of tying it
                    // to logOutput being non-empty.
                    run {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(
                                onClick = { showBuildLog = !showBuildLog },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    RuntimeIcon(if (showBuildLog) RuntimeIconName.ArrowUp else RuntimeIconName.ChevronDown, Modifier.size(14.dp))
                                    Text(if (showBuildLog) "收起构建日志" else "查看构建日志", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            if (showBuildLog && progress.logOutput.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("TaiXu Build Log", progress.logOutput))
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        RuntimeIcon(RuntimeIconName.Copy, Modifier.size(12.dp))
                                        Text("复制日志", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        if (showBuildLog) {
                            if (progress.logOutput.isBlank()) {
                                Text(
                                    text = if (progress.isRunning) "构建日志正在接收..." else "暂无可用构建日志",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // 分类日志
                            val logLines = progress.logOutput.lines()
                            val depKeywords = listOf("downloading", "fetching", "kilobytes", "megabytes", "get")
                            val compileKeywords = listOf("compile", "kotlin", "javac", "dex")
                            val packageKeywords = listOf("package", "install", "apk")
                            fun classifyLine(line: String): Int {
                                val lower = line.lowercase()
                                return when {
                                    depKeywords.any { lower.contains(it) } -> 0
                                    compileKeywords.any { lower.contains(it) } -> 1
                                    packageKeywords.any { lower.contains(it) } -> 2
                                    else -> 3
                                }
                            }
                            val categorized = logLines.map { line -> classifyLine(line) to line }
                            val depsLogs = categorized.filter { it.first == 0 }.map { it.second }
                            val compileLogs = categorized.filter { it.first == 1 }.map { it.second }
                            val packageLogs = categorized.filter { it.first == 2 }.map { it.second }
                            val otherLogs = categorized.filter { it.first == 3 }.map { it.second }

                            data class Category(val emoji: String, val name: String, val logs: List<String>, val collapsed: Boolean)
                            val categories = listOf(
                                Category("📥", "拉取依赖", depsLogs, collapsedDeps),
                                Category("🔨", "编译源码", compileLogs, collapsedCompile),
                                Category("📦", "打包安装", packageLogs, collapsedPackage),
                                Category("📋", "其他", otherLogs, collapsedOther),
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                categories.forEach { cat ->
                                    if (cat.logs.isNotEmpty()) {
                                        val isCollapsed = when (cat.name) {
                                            "拉取依赖" -> collapsedDeps
                                            "编译源码" -> collapsedCompile
                                            "打包安装" -> collapsedPackage
                                            else -> collapsedOther
                                        }
                                        TextButton(
                                            onClick = {
                                                when (cat.name) {
                                                    "拉取依赖" -> collapsedDeps = !collapsedDeps
                                                    "编译源码" -> collapsedCompile = !collapsedCompile
                                                    "打包安装" -> collapsedPackage = !collapsedPackage
                                                    else -> collapsedOther = !collapsedOther
                                                }
                                            },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    RuntimeIcon(if (isCollapsed) RuntimeIconName.ChevronRight else RuntimeIconName.ArrowUp, Modifier.size(12.dp))
                                                    Text("${cat.emoji} ${cat.name}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                                }
                                                Text("${cat.logs.size} 条", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        if (!isCollapsed) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Column(modifier = Modifier.padding(6.dp)) {
                                                    val displayLogs = if (cat.logs.size > 200) cat.logs.takeLast(200) else cat.logs
                                                    displayLogs.forEach { line ->
                                                        Text(
                                                            text = line,
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontFamily = FontFamily.Monospace,
                                                                fontSize = 10.sp,
                                                                lineHeight = 14.sp,
                                                            ),
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                        )
                                                    }
                                                    if (cat.logs.size > 200) {
                                                        Text(
                                                            text = "... 仅显示最近 200 条，共 ${cat.logs.size} 条",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                }
            },
            confirmButton = {
                if (!progress.isRunning) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 缺少环境时展示前往插件中心准备环境按钮
                        if (progress.suggestedSuiteId != null) {
                            Button(
                                onClick = {
                                    viewModel.dismissBuildProgress()
                                    onOpenToolCenter()
                                },
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    RuntimeIcon(RuntimeIconName.Package, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                    Text("前往插件中心准备环境")
                                }
                            }
                        }

                        val path = progress.apkPath
                        if (progress.isSuccess == true && path != null) {
                            Button(onClick = { viewModel.launchInstaller(path) }) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    RuntimeIcon(RuntimeIconName.Download, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                    Text("调起安装")
                                }
                            }
                        }
                        TextButton(onClick = { viewModel.dismissBuildProgress() }) {
                            Text("完成")
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.cancelBuild() }) {
                            Text("停止编译", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { viewModel.hideBuildDialog() }) {
                            Text("后台运行", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            dismissButton = {
                if (progress.isRunning) {
                    TextButton(onClick = { viewModel.hideBuildDialog() }) {
                        Text("收起")
                    }
                }
            },
        )
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = {
                showCreate = false
                projectName = ""
                packageName = ""
                gitUrl = ""
                directoryPath = ""
                projectStorage = WorkspaceStorage.INTERNAL
                apkSource = null
                exportApkToDownload = false
            },
            title = { Text("新建工坊工程", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("选择工程模板", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // 模板选择 Chips（FlowRow 自动换行，避免挤压）
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            top.wkbin.taixu.runtime.ProjectTemplate.ANDROID_COMPOSE to ("Android" to RuntimeIconName.Android),
                            top.wkbin.taixu.runtime.ProjectTemplate.FLUTTER to ("Flutter" to RuntimeIconName.Flutter),
                            top.wkbin.taixu.runtime.ProjectTemplate.APK_REVERSE to ("APK 逆向" to RuntimeIconName.Reverse),
                            top.wkbin.taixu.runtime.ProjectTemplate.GIT_IMPORT to ("Git 导入" to RuntimeIconName.Github),
                            top.wkbin.taixu.runtime.ProjectTemplate.EMPTY to ("空工程" to RuntimeIconName.Code),
                        ).forEach { (tmpl, pair) ->
                            val (label, icon) = pair
                            FilterChip(
                                selected = selectedTemplate == tmpl,
                                onClick = {
                                    selectedTemplate = tmpl
                                    if (projectName.isNotBlank() && packageName.isBlank()) {
                                        packageName = "com.example.${projectName.lowercase().filter { it.isLetterOrDigit() }}"
                                    }
                                },
                                leadingIcon = { RuntimeIcon(icon, Modifier.size(16.dp)) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = projectName,
                        onValueChange = {
                            projectName = it
                            if (packageName.isBlank() || packageName.startsWith("com.example.")) {
                                packageName = "com.example.${it.lowercase().filter { c -> c.isLetterOrDigit() }}"
                            }
                        },
                        label = { Text("工程名称 (Name)") },
                        placeholder = { Text("MyApplication / demo-app") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (selectedTemplate == top.wkbin.taixu.runtime.ProjectTemplate.ANDROID_COMPOSE ||
                        selectedTemplate == top.wkbin.taixu.runtime.ProjectTemplate.FLUTTER
                    ) {
                        OutlinedTextField(
                            value = packageName,
                            onValueChange = { packageName = it },
                            label = { Text("应用包名 (Package Name)") },
                            placeholder = { Text("com.example.myapp") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // ============ APK 逆向模板：选择安装包来源 ============
                    if (selectedTemplate == top.wkbin.taixu.runtime.ProjectTemplate.APK_REVERSE) {
                        Text(
                            "选择安装包来源",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { showAppPicker = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                RuntimeIcon(RuntimeIconName.Package, Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("从本地应用提取", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = {
                                    apkPicker.launch(
                                        arrayOf(
                                            "application/vnd.android.package-archive",
                                            "application/octet-stream",
                                        ),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                RuntimeIcon(RuntimeIconName.Folder, Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("文件管理器选 APK", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Text(
                            "从本地应用提取：无需 Root，直接拷贝应用安装包到工程并解包；文件管理器方式支持任意位置 .apk。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // 逆向工具链就绪状态：避免"建好工程却发现 jadx/apktool 缺失"
                        val runtimeReady by viewModel.runtimeReady.collectAsStateWithLifecycle()
                        val reverseToolReady = "android-re" in installedComponentIds && runtimeReady
                        val statusSurfaceColor = when {
                            reverseToolReady -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                            runtimeReady -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = statusSurfaceColor,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                RuntimeIcon(
                                    when {
                                        reverseToolReady -> RuntimeIconName.Check
                                        runtimeReady -> RuntimeIconName.Alert
                                        else -> RuntimeIconName.Info
                                    },
                                    Modifier.size(17.dp),
                                    tint = when {
                                        reverseToolReady -> MaterialTheme.colorScheme.onSecondaryContainer
                                        runtimeReady -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = when {
                                            reverseToolReady -> "逆向工具链已就绪：apktool / jadx"
                                            runtimeReady -> "逆向工具链未装配：jadx / apktool 暂不可用"
                                            else -> "Linux 沙箱未初始化，暂无法检测工具链"
                                        },
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = when {
                                            reverseToolReady -> MaterialTheme.colorScheme.onSecondaryContainer
                                            runtimeReady -> MaterialTheme.colorScheme.onErrorContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    Text(
                                        text = when {
                                            reverseToolReady -> "解包产物已就绪，可直接在终端/Agent 深度反编译"
                                            runtimeReady -> "解包产物已就绪；深度反编译需装配「Android 逆向分析与代码审计」子组件"
                                            else -> "APK 提取与解包不依赖沙箱，可正常创建；初始化后工具链将可用"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            reverseToolReady -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                                            runtimeReady -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        },
                                    )
                                }
                                if (!reverseToolReady && runtimeReady) {
                                    TextButton(onClick = {
                                        showCreate = false
                                        onOpenToolCenter()
                                    }) {
                                        Text("去装配", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        apkSource?.let { source ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    RuntimeIcon(RuntimeIconName.Reverse, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = source.displayName,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = when (source) {
                                                is ApkImportSource.FromInstalledApp -> "已安装应用 · 导入后自动解包"
                                                is ApkImportSource.FromFileUri -> "APK 文件 · 导入后自动解包"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { apkSource = null }, modifier = Modifier.size(30.dp)) {
                                        RuntimeIcon(RuntimeIconName.Close, Modifier.size(15.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        // 同时导出 APK 到宿主公共下载目录（供 MT 管理器等宿主侧工具直接打开）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { exportApkToDownload = !exportApkToDownload }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Checkbox(
                                checked = exportApkToDownload,
                                onCheckedChange = { exportApkToDownload = it },
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "同时导出 APK 到手机 Download 目录",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "便于用宿主侧工具（MT 管理器等）直接打开；需已授予全部文件访问权限",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (selectedTemplate == top.wkbin.taixu.runtime.ProjectTemplate.GIT_IMPORT) {
                        OutlinedTextField(
                            value = gitUrl,
                            onValueChange = { gitUrl = it },
                            label = { Text("Git 仓库地址") },
                            placeholder = { Text("https://github.com/user/project.git") },
                            supportingText = { Text("导入到目标目录后以仓库内容为准，不会生成模板文件") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = projectStorage == WorkspaceStorage.INTERNAL,
                            onClick = { projectStorage = WorkspaceStorage.INTERNAL; directoryPath = "" },
                            label = { Text("内部空间") },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = projectStorage == WorkspaceStorage.SHARED,
                            onClick = { projectStorage = WorkspaceStorage.SHARED; directoryPath = "" },
                            label = { Text("共享空间") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (projectStorage == WorkspaceStorage.INTERNAL) {
                        val nameForPath = projectName.trim().ifBlank { "my-app" }
                        val commonDirectories = listOf(
                            "" to "工程专属目录  /workspace/$nameForPath",
                            "projects/$nameForPath" to "项目集合  /workspace/projects/$nameForPath",
                            "repos/$nameForPath" to "代码仓库  /workspace/repos/$nameForPath",
                            "work/$nameForPath" to "工作目录  /workspace/work/$nameForPath",
                        )
                        ExposedDropdownMenuBox(
                            expanded = internalDirectoryMenuExpanded,
                            onExpandedChange = { internalDirectoryMenuExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = directoryPath,
                                onValueChange = {
                                    directoryPath = it
                                    internalDirectoryMenuExpanded = true
                                },
                                label = { Text("关联目录（可输入或选择）") },
                                placeholder = { Text("留空则使用工程名") },
                                supportingText = { Text("仅填写 /workspace 后的相对路径") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(internalDirectoryMenuExpanded)
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor(
                                    ExposedDropdownMenuAnchorType.PrimaryEditable,
                                    true,
                                ),
                            )
                            ExposedDropdownMenu(
                                expanded = internalDirectoryMenuExpanded,
                                onDismissRequest = { internalDirectoryMenuExpanded = false },
                            ) {
                                commonDirectories.forEach { (path, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            directoryPath = path
                                            internalDirectoryMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = directoryPath,
                            onValueChange = { directoryPath = it },
                            label = { Text("关联目录") },
                            placeholder = { Text("Download/my-app") },
                            supportingText = { Text("可关联现有目录；目录不存在时自动创建") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (projectStorage == WorkspaceStorage.SHARED) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(onClick = { directoryPicker.launch(null) }) { Text("选择共享目录") }
                            if (!sharedAccessGranted) {
                                TextButton(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            allFilesPermission.launch(
                                                Intent(
                                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                                    android.net.Uri.parse("package:${context.packageName}"),
                                                ),
                                            )
                                        } else {
                                            legacyStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                        }
                                    },
                                ) { Text("授权共享空间") }
                            }
                        }
                    }
                    val path = directoryPath.trim().ifBlank { projectName.trim() }
                    if (path.isNotBlank()) Text(
                        "沙箱路径：${if (projectStorage == WorkspaceStorage.INTERNAL) "/workspace" else "/sdcard"}/${path.trimStart('/')}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.create(projectName, projectStorage, directoryPath, selectedTemplate, packageName, apkSource, exportApkToDownload, gitUrl)
                        projectName = ""
                        packageName = ""
                        gitUrl = ""
                        directoryPath = ""
                        projectStorage = WorkspaceStorage.INTERNAL
                        apkSource = null
                        exportApkToDownload = false
                        showCreate = false
                    },
                    enabled = projectName.isNotBlank() && !busy &&
                        (projectStorage != WorkspaceStorage.SHARED || sharedAccessGranted) &&
                        (selectedTemplate != top.wkbin.taixu.runtime.ProjectTemplate.APK_REVERSE || apkSource != null) &&
                        (selectedTemplate != top.wkbin.taixu.runtime.ProjectTemplate.GIT_IMPORT || gitUrl.isNotBlank()),
                ) { Text("确认创建", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreate = false
                    projectName = ""
                    packageName = ""
                    gitUrl = ""
                    directoryPath = ""
                    projectStorage = WorkspaceStorage.INTERNAL
                    apkSource = null
                    exportApkToDownload = false
                }) { Text("取消") }
            },
        )
    }

    // APK 逆向模板：已安装应用选择弹窗
    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onSelect = { app ->
                apkSource = ApkImportSource.FromInstalledApp(app.packageName, app.appLabel(context))
                showAppPicker = false
            },
        )
    }

    deleteTarget?.let { project ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除工程 ${project.name}？", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (project.ownsDirectory) "将永久移除 ${project.linuxPath} 目录及其全部代码文件，此操作不可撤销。"
                    else "只移除工程关联，不会删除关联目录中的原文件。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { deleteTarget = null; viewModel.delete(project.name) },
                ) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ProjectCard(
    project: WorkspaceProject,
    busy: Boolean,
    isBuilding: Boolean,
    onOpenExplorer: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenAgent: () -> Unit,
    onRunProject: () -> Unit,
    onShowBuildLog: () -> Unit,
    onDelete: () -> Unit,
) {
    var moreExpanded by remember { mutableStateOf(false) }

    val typeBadgeColor = when (project.projectType) {
        top.wkbin.taixu.runtime.ProjectType.ANDROID -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
        top.wkbin.taixu.runtime.ProjectType.FLUTTER -> androidx.compose.ui.graphics.Color(0xFF0288D1)
        top.wkbin.taixu.runtime.ProjectType.REVERSE -> androidx.compose.ui.graphics.Color(0xFF6A1B9A)
        top.wkbin.taixu.runtime.ProjectType.GENERAL -> MaterialTheme.colorScheme.primary
    }

    val typeIcon = when (project.projectType) {
        top.wkbin.taixu.runtime.ProjectType.ANDROID -> RuntimeIconName.Android
        top.wkbin.taixu.runtime.ProjectType.FLUTTER -> RuntimeIconName.Flutter
        top.wkbin.taixu.runtime.ProjectType.REVERSE -> RuntimeIconName.Reverse
        top.wkbin.taixu.runtime.ProjectType.GENERAL -> RuntimeIconName.Code
    }

    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (isBuilding) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        onClick = onOpenExplorer,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconTile(typeIcon, size = 40.dp, color = typeBadgeColor)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Surface(
                            color = if (isBuilding) MaterialTheme.colorScheme.primaryContainer else typeBadgeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = if (isBuilding) "🔨 正在后台编译" else project.projectType.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = if (isBuilding) MaterialTheme.colorScheme.primary else typeBadgeColor,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Text(
                        text = "${project.linuxPath} · ${project.sizeBytes.toReadableSize()}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Box {
                    IconButton(
                        onClick = { moreExpanded = true },
                        enabled = !busy,
                        modifier = Modifier.size(32.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.More, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("打开专属终端") },
                            leadingIcon = {
                                RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(17.dp))
                            },
                            onClick = {
                                moreExpanded = false
                                onOpenTerminal()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除工程", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                RuntimeIcon(RuntimeIconName.Trash, Modifier.size(17.dp), MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                moreExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }

            if (isBuilding) {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(1.5.dp)),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // 快捷操作栏：精简去噪，只保留最核心的直达动作
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                // 只有可编译运行工程（Android / Flutter）显示"运行到手机"按钮；逆向/通用工程走终端
                if (project.projectType == top.wkbin.taixu.runtime.ProjectType.ANDROID ||
                    project.projectType == top.wkbin.taixu.runtime.ProjectType.FLUTTER
                ) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = if (isBuilding) onShowBuildLog else onRunProject,
                        enabled = !busy || isBuilding,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            if (isBuilding) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text("编译中...", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                            } else {
                                RuntimeIcon(RuntimeIconName.Play, Modifier.size(14.dp), tint = typeBadgeColor)
                                Text("运行到手机", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = typeBadgeColor)
                            }
                        }
                    }
                } else {
                    TextButton(
                        onClick = onOpenTerminal,
                        enabled = !busy,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(15.dp), MaterialTheme.colorScheme.primary)
                            Text("终端", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

            }
        }
    }
}

private fun Long.toReadableSize(): String = when {
    this < 1024 -> "$this B"
    this < 1024 * 1024 -> "%.1f KB".format(this / 1024.0)
    this < 1024 * 1024 * 1024 -> "%.1f MB".format(this / (1024.0 * 1024))
    else -> "%.1f GB".format(this / (1024.0 * 1024 * 1024))
}

// ==================== APK 逆向模板：已安装应用选择器 ====================

/** 已安装应用的应用名（label），失败时回退包名。 */
private fun ApplicationInfo.appLabel(context: Context): String =
    runCatching { loadLabel(context.packageManager).toString() }.getOrDefault(packageName)

/** 通过 SAF 查询 URI 的显示文件名。 */
private fun queryDisplayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

@Composable
private fun AppPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (ApplicationInfo) -> Unit,
) {
    val context = LocalContext.current
    val apps = remember {
        runCatching {
            context.packageManager.getInstalledApplications(0)
                .filter { it.sourceDir != null && java.io.File(it.sourceDir).isFile }
                .sortedBy { it.appLabel(context).lowercase() }
        }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择已安装应用", fontWeight = FontWeight.Bold) },
        text = {
            if (apps.isEmpty()) {
                Text(
                    "无法获取应用列表（可能未授予包可见性权限）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "选择后将从系统安装目录提取该应用的 APK 到工程并自动解包。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        val label = app.appLabel(context)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(app) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                RuntimeIcon(
                                    RuntimeIconName.Package,
                                    Modifier.size(22.dp),
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            RuntimeIcon(RuntimeIconName.ChevronRight, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(start = 52.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
