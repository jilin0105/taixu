package top.wkbin.taixu.ui.workspace

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
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

/**
 * 太墟 · 工坊空间 (Workspace Space)
 * 管理 Linux 隔离工作区、代码工程与文件项目
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun WorkspaceScreen(
    onNavigate: (MainDestination) -> Unit,
    onOpenExplorer: (String) -> Unit,
    onOpenTerminal: (String) -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val buildProgress by viewModel.buildProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCreate by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf(top.wkbin.taixu.runtime.ProjectTemplate.ANDROID_COMPOSE) }
    var projectName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var projectStorage by remember { mutableStateOf(WorkspaceStorage.INTERNAL) }
    var directoryPath by remember { mutableStateOf("") }
    var internalDirectoryMenuExpanded by remember { mutableStateOf(false) }
    var permissionRefresh by remember { mutableStateOf(0) }
    var deleteTarget by remember { mutableStateOf<WorkspaceProject?>(null) }

    val legacyStoragePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRefresh++
    }
    val allFilesPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionRefresh++
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
                IconButton(onClick = { showCreate = true }, enabled = !busy) {
                    RuntimeIcon(RuntimeIconName.Plus, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
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
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        RuntimeIcon(
                            RuntimeIconName.Folder,
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
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
                    description = "点击右上角 ➕ 选择 Android / Flutter 模板或自定义创建工程",
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                projects.forEach { project ->
                    ProjectCard(
                        project = project,
                        busy = busy,
                        onOpenExplorer = { onOpenExplorer(project.name) },
                        onOpenTerminal = { onOpenTerminal(project.name) },
                        onOpenAgent = { onNavigate(MainDestination.Agent) },
                        onRunProject = { viewModel.runProject(project) },
                        onDelete = { deleteTarget = project },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // 运行/构建进度与实时日志弹窗
    buildProgress?.let { progress ->
        var showBuildLog by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!progress.isRunning) viewModel.dismissBuildProgress() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (progress.isRunning) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    Text(if (progress.isRunning) "正在运行到手机..." else (if (progress.isSuccess == true) "运行就绪" else "运行失败"), fontWeight = FontWeight.Bold)
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

                    // 构建日志折叠面板
                    if (progress.logOutput.isNotBlank()) {
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

                            if (showBuildLog) {
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
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState()),
                                ) {
                                    Text(
                                        text = progress.logOutput,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!progress.isRunning) {
                    TextButton(onClick = { viewModel.dismissBuildProgress() }) {
                        Text("完成")
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
                directoryPath = ""
                projectStorage = WorkspaceStorage.INTERNAL
            },
            title = { Text("新建工坊工程", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("选择工程模板", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // 模板选择 Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        top.wkbin.taixu.runtime.ProjectTemplate.entries.forEach { t ->
                            val isSelected = selectedTemplate == t
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedTemplate = t
                                    if (projectName.isNotBlank() && packageName.isBlank()) {
                                        packageName = "com.example.${projectName.lowercase().filter { it.isLetterOrDigit() }}"
                                    }
                                },
                                label = { Text(when(t) {
                                    top.wkbin.taixu.runtime.ProjectTemplate.ANDROID_COMPOSE -> "Android"
                                    top.wkbin.taixu.runtime.ProjectTemplate.FLUTTER -> "Flutter"
                                    top.wkbin.taixu.runtime.ProjectTemplate.EMPTY -> "空工程"
                                }, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f),
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

                    if (selectedTemplate != top.wkbin.taixu.runtime.ProjectTemplate.EMPTY) {
                        OutlinedTextField(
                            value = packageName,
                            onValueChange = { packageName = it },
                            label = { Text("应用包名 (Package Name)") },
                            placeholder = { Text("com.example.myapp") },
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
                        viewModel.create(projectName, projectStorage, directoryPath, selectedTemplate, packageName)
                        projectName = ""
                        packageName = ""
                        directoryPath = ""
                        projectStorage = WorkspaceStorage.INTERNAL
                        showCreate = false
                    },
                    enabled = projectName.isNotBlank() && !busy &&
                        (projectStorage != WorkspaceStorage.SHARED || sharedAccessGranted),
                ) { Text("确认创建", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreate = false
                    projectName = ""
                    packageName = ""
                    directoryPath = ""
                    projectStorage = WorkspaceStorage.INTERNAL
                }) { Text("取消") }
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
    onOpenExplorer: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenAgent: () -> Unit,
    onRunProject: () -> Unit,
    onDelete: () -> Unit,
) {
    var moreExpanded by remember { mutableStateOf(false) }

    val typeBadgeColor = when (project.projectType) {
        top.wkbin.taixu.runtime.ProjectType.ANDROID -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
        top.wkbin.taixu.runtime.ProjectType.FLUTTER -> androidx.compose.ui.graphics.Color(0xFF0288D1)
        top.wkbin.taixu.runtime.ProjectType.GENERAL -> MaterialTheme.colorScheme.primary
    }

    val typeIcon = when (project.projectType) {
        top.wkbin.taixu.runtime.ProjectType.ANDROID -> RuntimeIconName.Play
        top.wkbin.taixu.runtime.ProjectType.FLUTTER -> RuntimeIconName.Sparkles
        top.wkbin.taixu.runtime.ProjectType.GENERAL -> RuntimeIconName.Workspace
    }

    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenExplorer,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconTile(typeIcon, size = 44.dp, color = typeBadgeColor)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            project.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Surface(
                            color = typeBadgeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = project.projectType.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = typeBadgeColor,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Text(
                        project.linuxPath,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 大小胶囊
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = project.sizeBytes.toReadableSize(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }

                Box {
                    IconButton(
                        onClick = { moreExpanded = true },
                        enabled = !busy,
                        modifier = Modifier.size(36.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.More, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false },
                    ) {
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

            // 快捷操作栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                // 如果是可运行工程（Android / Flutter），显示运行到手机按钮
                if (project.projectType != top.wkbin.taixu.runtime.ProjectType.GENERAL) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = onRunProject,
                        enabled = !busy,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RuntimeIcon(RuntimeIconName.Play, Modifier.size(14.dp), tint = typeBadgeColor)
                            Text("运行到手机", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = typeBadgeColor)
                        }
                    }
                }

                // Agent 协同
                TextButton(
                    onClick = onOpenAgent,
                    enabled = !busy,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Sparkles, Modifier.size(15.dp), MaterialTheme.colorScheme.primary)
                        Text("Agent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }

                // 打开终端
                TextButton(
                    onClick = onOpenTerminal,
                    enabled = !busy,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(15.dp), MaterialTheme.colorScheme.primary)
                        Text("终端", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }

                // 浏览代码
                Button(
                    onClick = onOpenExplorer,
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Folder, Modifier.size(15.dp), MaterialTheme.colorScheme.onPrimary)
                        Text("浏览代码", style = MaterialTheme.typography.labelMedium)
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
