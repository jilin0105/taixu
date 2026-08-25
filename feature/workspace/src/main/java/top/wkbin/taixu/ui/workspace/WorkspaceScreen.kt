package top.wkbin.taixu.ui.workspace

import top.wkbin.taixu.ui.components.RuntimeAlertDialog

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeFilledTonalButton as FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import top.wkbin.taixu.ui.components.RuntimeCheckbox as Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import top.wkbin.taixu.ui.components.RuntimeIconButton as IconButton
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
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
import androidx.compose.ui.res.stringResource
import top.wkbin.taixu.feature.workspace.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import top.wkbin.taixu.runtime.ApkImportSource
import top.wkbin.taixu.runtime.GitTransport
import top.wkbin.taixu.runtime.ProjectArchiveSource
import top.wkbin.taixu.runtime.ProjectType
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.runtime.WorkspaceStorage
import top.wkbin.taixu.ui.components.EmptyPanel
import top.wkbin.taixu.ui.components.IconTile
import top.wkbin.taixu.ui.components.MainDestination
import top.wkbin.taixu.ui.components.NoticeBanner
import top.wkbin.taixu.ui.components.RuntimeBottomBar
import top.wkbin.taixu.ui.components.liquidGlassContent
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.SectionHeader
import top.wkbin.taixu.ui.theme.LocalLiquidGlassBackdrop
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.flow.Flow
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import top.wkbin.taixu.runtime.build.StepDuration

private enum class ProjectImportMode { LOCAL, GITHUB }

/**
 * 太墟 · 工坊空间 (Workspace Space)
 * 管理 Linux 隔离工作区、代码工程与文件项目
 */
/** 骨架屏：shimmer 流动高光 + 卡片占位 */
@Composable
private fun shimmerBrush(): androidx.compose.ui.graphics.Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.3f),
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
    )
    return androidx.compose.ui.graphics.Brush.linearGradient(
        colors = shimmerColors,
        start = androidx.compose.ui.geometry.Offset(translateAnim - 200f, 0f),
        end = androidx.compose.ui.geometry.Offset(translateAnim, 0f),
    )
}
@Composable
private fun ProjectCardSkeleton(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    RuntimeCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brush),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(brush),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brush),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brush),
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun WorkspaceScreen(
    onNavigate: (MainDestination) -> Unit,
    onOpenExplorer: (String) -> Unit,
    onOpenTerminal: (String) -> Unit,
    onOpenToolCenter: () -> Unit = {},
    onOpenWorkshopSettings: () -> Unit = {},
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val buildProgress by viewModel.buildProgress.collectAsStateWithLifecycle()
    val activeBuildingProjectName by viewModel.activeBuildingProjectName.collectAsStateWithLifecycle()
    val isBuildDialogVisible by viewModel.isBuildDialogVisible.collectAsStateWithLifecycle()
    val installedComponentIds by viewModel.installedComponentIds.collectAsStateWithLifecycle()
    val loadingProjects by viewModel.loadingProjects.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCreate by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf(top.wkbin.taixu.runtime.ProjectTemplate.ANDROID_COMPOSE) }
    var projectName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var projectStorage by remember { mutableStateOf(WorkspaceStorage.INTERNAL) }
    var directoryPath by remember { mutableStateOf("") }
    var internalDirectoryMenuExpanded by remember { mutableStateOf(false) }
    var permissionRefresh by remember { mutableStateOf(0) }
    var deleteTarget by remember { mutableStateOf<WorkspaceProject?>(null) }
    var apkSource by remember { mutableStateOf<ApkImportSource?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    var exportApkToDownload by remember { mutableStateOf(false) }
    var importMode by remember { mutableStateOf(ProjectImportMode.LOCAL) }
    var importProjectName by remember { mutableStateOf("") }
    var importDirectoryPath by remember { mutableStateOf("") }
    var importDirectoryMenuExpanded by remember { mutableStateOf(false) }
    var importProjectType by remember { mutableStateOf(ProjectType.ANDROID) }
    var archiveSource by remember { mutableStateOf<ProjectArchiveSource?>(null) }
    var importGitUrl by remember { mutableStateOf("") }
    var gitTransport by remember { mutableStateOf(GitTransport.HTTP) }
    var pendingExportProject by remember { mutableStateOf<WorkspaceProject?>(null) }

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
    val archivePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val displayName = queryDisplayName(context, uri) ?: "project.zip"
            archiveSource = ProjectArchiveSource(uri.toString(), displayName)
            if (importProjectName.isBlank()) {
                importProjectName = displayName.substringBeforeLast('.').filter {
                    it.isLetterOrDigit() || it == '.' || it == '_' || it == '-'
                }
            }
        }
    }
    val exportDirectoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val project = pendingExportProject
        pendingExportProject = null
        if (uri != null && project != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.exportProject(project, uri.toString())
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

    val glassBackdrop = LocalLiquidGlassBackdrop.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = stringResource(R.string.workspace_title),
                statusText = stringResource(R.string.workspace_active_projects, projects.size),
            ) {
                Box {
                    IconButton(onClick = { actionsExpanded = true }, enabled = !busy) {
                        RuntimeIcon(RuntimeIconName.More, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                        DropdownMenuItem(text = { Text("创建") }, leadingIcon = { RuntimeIcon(RuntimeIconName.Plus, Modifier.size(18.dp)) }, onClick = { actionsExpanded = false; showCreate = true })
                        DropdownMenuItem(text = { Text("导入") }, leadingIcon = { RuntimeIcon(RuntimeIconName.FolderDownload, Modifier.size(18.dp)) }, onClick = { actionsExpanded = false; showImport = true })
                        DropdownMenuItem(text = { Text("插件") }, leadingIcon = { RuntimeIcon(RuntimeIconName.Package, Modifier.size(18.dp)) }, onClick = { actionsExpanded = false; onOpenToolCenter() })
                        DropdownMenuItem(text = { Text("工坊设置") }, leadingIcon = { RuntimeIcon(RuntimeIconName.Settings, Modifier.size(18.dp)) }, onClick = { actionsExpanded = false; onOpenWorkshopSettings() })
                    }
                }
            }
        },
        bottomBar = {
            if (glassBackdrop == null) {
                RuntimeBottomBar(MainDestination.Workspace, onNavigate)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassContent()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                message?.let { notice ->
                    NoticeBanner(
                        text = notice,
                        isError = notice.contains("失败") || notice.contains("无效") || notice.contains("存在"),
                    )
                }

                // 后台构建常驻状态栏 (Banner)
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
                        RuntimeCircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.workspace_building_project, activeBuildingProjectName.orEmpty()),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = buildProgress?.step ?: stringResource(R.string.workspace_building),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { viewModel.showBuildDialog() }) {
                            Text(stringResource(R.string.workspace_view_logs), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                                text = stringResource(if (progress.isSuccess == true) R.string.workspace_build_ready else R.string.workspace_build_failed),
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
                                    Text(stringResource(R.string.workspace_install), fontWeight = FontWeight.Bold)
                                }
                            }
                            TextButton(onClick = { viewModel.showBuildDialog() }) {
                                Text(stringResource(R.string.workspace_details))
                            }
                            IconButton(onClick = { viewModel.dismissBuildProgress() }, modifier = Modifier.size(28.dp)) {
                                RuntimeIcon(RuntimeIconName.Close, Modifier.size(14.dp))
                            }
                        }
                    }
                    }
                }
            }

            item {
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
                            text = stringResource(R.string.workspace_shared_storage),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.workspace_shared_storage_description),
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
            }

            item {
                SectionHeader(
                    title = stringResource(R.string.workspace_projects_section),
                    subtitle = stringResource(R.string.workspace_projects_description),
                )
            }

            if (loadingProjects) {
                item {
                    repeat(3) { index ->
                        ProjectCardSkeleton(
                            modifier = Modifier.padding(top = if (index == 0) 16.dp else 0.dp),
                        )
                    }
                }
            } else if (projects.isEmpty()) {
                item {
                    EmptyPanel(
                        icon = RuntimeIconName.Workspace,
                        title = stringResource(R.string.workspace_no_projects),
                        description = stringResource(R.string.workspace_no_projects_description),
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            } else {
                items(projects, key = { it.name }) { project ->
                    ProjectCard(
                        project = project,
                        busy = busy,
                        isBuilding = (activeBuildingProjectName == project.name),
                        onOpenExplorer = { onOpenExplorer(project.name) },
                        onOpenTerminal = { onOpenTerminal(project.name) },
                        onOpenAgent = { onNavigate(MainDestination.Agent) },
                        onRunProject = { viewModel.runProject(project) },
                        onShowBuildLog = { viewModel.showBuildDialog() },
                        onExport = {
                            pendingExportProject = project
                            exportDirectoryPicker.launch(null)
                        },
                        onDelete = { deleteTarget = project },
                    )
                }
            }


            item { Spacer(Modifier.height(16.dp)) }
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

        RuntimeAlertDialog(
            onDismissRequest = { viewModel.hideBuildDialog() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (progress.isRunning) {
                        RuntimeCircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    Text(stringResource(if (progress.isRunning) R.string.workspace_building_device else if (progress.isSuccess == true) R.string.workspace_run_ready else R.string.workspace_run_failed), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(progress.step, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                    if (progress.isRunning) {
                        LinearProgressIndicator(
                            progress = { progress.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        )
                    }
                    if (progress.currentDependency != null || progress.dependencyItemsObserved > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.workspace_dependency_activity),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    val countText = progress.dependenciesTotal?.let { total ->
                                        stringResource(R.string.workspace_dependency_count_total, progress.dependencyItemsObserved, total)
                                    } ?: stringResource(R.string.workspace_dependency_count, progress.dependencyItemsObserved)
                                    Text(countText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                progress.currentDependency?.let { dependency ->
                                    Text(
                                        text = dependency,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                progress.dependencyProgressPercent?.let { percent ->
                                    LinearProgressIndicator(
                                        progress = { percent / 100f },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    )
                                    Text(
                                        text = stringResource(R.string.workspace_dependency_percent, percent),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
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
                                Text(stringResource(if (showStepAnalysis) R.string.workspace_hide_timing else R.string.workspace_timing), style = MaterialTheme.typography.labelMedium)
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
                                                text = stringResource(R.string.workspace_total),
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
                                    Text(stringResource(if (showBuildLog) R.string.workspace_hide_build_log else R.string.workspace_view_build_log), style = MaterialTheme.typography.labelMedium)
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
                                        Text(stringResource(R.string.workspace_copy_log), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        if (showBuildLog) {
                            if (progress.logOutput.isBlank()) {
                                Text(
                                    text = stringResource(if (progress.isRunning) R.string.workspace_build_log_receiving else R.string.workspace_no_build_log),
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

                            data class Category(val type: Int, val emoji: String, val name: String, val logs: List<String>)
                            val categories = listOf(
                                Category(0, "📥", stringResource(R.string.workspace_log_dependencies), depsLogs),
                                Category(1, "🔨", stringResource(R.string.workspace_log_compile), compileLogs),
                                Category(2, "📦", stringResource(R.string.workspace_log_package), packageLogs),
                                Category(3, "📋", stringResource(R.string.workspace_log_other), otherLogs),
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                categories.forEach { cat ->
                                    if (cat.logs.isNotEmpty()) {
                                        val isCollapsed = when (cat.type) {
                                            0 -> collapsedDeps
                                            1 -> collapsedCompile
                                            2 -> collapsedPackage
                                            else -> collapsedOther
                                        }
                                        TextButton(
                                            onClick = {
                                                when (cat.type) {
                                                    0 -> collapsedDeps = !collapsedDeps
                                                    1 -> collapsedCompile = !collapsedCompile
                                                    2 -> collapsedPackage = !collapsedPackage
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
                                                Text(stringResource(R.string.workspace_log_count, cat.logs.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                            text = stringResource(R.string.workspace_log_truncated, cat.logs.size),
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
                                    Text(stringResource(R.string.workspace_prepare_environment))
                                }
                            }
                        }

                        val path = progress.apkPath
                        if (progress.isSuccess == true && path != null) {
                            Button(onClick = { viewModel.launchInstaller(path) }) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    RuntimeIcon(RuntimeIconName.Download, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                    Text(stringResource(R.string.workspace_launch_install))
                                }
                            }
                        }
                        TextButton(onClick = { viewModel.dismissBuildProgress() }) {
                            Text(stringResource(R.string.workspace_done))
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.cancelBuild() }) {
                            Text(stringResource(R.string.workspace_stop_build), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { viewModel.hideBuildDialog() }) {
                            Text(stringResource(R.string.workspace_background), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            dismissButton = {
                if (progress.isRunning) {
                    TextButton(onClick = { viewModel.hideBuildDialog() }) {
                        Text(stringResource(R.string.workspace_collapse))
                    }
                }
            },
        )
    }

    if (showCreate) {
        RuntimeAlertDialog(
            onDismissRequest = {
                showCreate = false
                projectName = ""
                packageName = ""
                directoryPath = ""
                projectStorage = WorkspaceStorage.INTERNAL
                apkSource = null
                exportApkToDownload = false
            },
            title = { Text(stringResource(R.string.workspace_new_project), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.workspace_choose_template), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // 模板选择 Chips（FlowRow 自动换行，避免挤压）
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            top.wkbin.taixu.runtime.ProjectTemplate.ANDROID_COMPOSE to ("Android" to RuntimeIconName.Android),
                            top.wkbin.taixu.runtime.ProjectTemplate.FLUTTER to ("Flutter" to RuntimeIconName.Flutter),
                            top.wkbin.taixu.runtime.ProjectTemplate.APK_REVERSE to (stringResource(R.string.workspace_template_reverse) to RuntimeIconName.Reverse),
                            top.wkbin.taixu.runtime.ProjectTemplate.EMPTY to (stringResource(R.string.workspace_template_empty) to RuntimeIconName.Code),
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
                        label = { Text(stringResource(R.string.workspace_project_name)) },
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
                            label = { Text(stringResource(R.string.workspace_package_name)) },
                            placeholder = { Text("com.example.myapp") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // ============ APK 逆向模板：选择安装包来源 ============
                    if (selectedTemplate == top.wkbin.taixu.runtime.ProjectTemplate.APK_REVERSE) {
                        Text(
                            stringResource(R.string.workspace_choose_apk_source),
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
                                Text(stringResource(R.string.workspace_extract_installed), style = MaterialTheme.typography.labelMedium)
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
                                Text(stringResource(R.string.workspace_choose_apk_file), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Text(
                            stringResource(R.string.workspace_apk_source_description),
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
                                            reverseToolReady -> stringResource(R.string.workspace_reverse_ready)
                                            runtimeReady -> stringResource(R.string.workspace_reverse_missing)
                                            else -> stringResource(R.string.workspace_runtime_uninitialized)
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
                                            reverseToolReady -> stringResource(R.string.workspace_reverse_output_ready)
                                            runtimeReady -> stringResource(R.string.workspace_reverse_component_needed)
                                            else -> stringResource(R.string.workspace_apk_creation_available)
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
                                        Text(stringResource(R.string.workspace_install_components), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
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
                                                is ApkImportSource.FromInstalledApp -> stringResource(R.string.workspace_installed_app_source)
                                                is ApkImportSource.FromFileUri -> stringResource(R.string.workspace_apk_file_source)
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
                                    stringResource(R.string.workspace_export_apk),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    stringResource(R.string.workspace_export_apk_description),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = projectStorage == WorkspaceStorage.INTERNAL,
                            onClick = { projectStorage = WorkspaceStorage.INTERNAL; directoryPath = "" },
                            label = { Text(stringResource(R.string.workspace_internal_storage)) },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = projectStorage == WorkspaceStorage.SHARED,
                            onClick = { projectStorage = WorkspaceStorage.SHARED; directoryPath = "" },
                            label = { Text(stringResource(R.string.workspace_shared_space)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (projectStorage == WorkspaceStorage.INTERNAL) {
                        val nameForPath = projectName.trim().ifBlank { "my-app" }
                        val commonDirectories = listOf(
                            "" to stringResource(R.string.workspace_location_project, nameForPath),
                            "projects/$nameForPath" to stringResource(R.string.workspace_location_projects, nameForPath),
                            "repos/$nameForPath" to stringResource(R.string.workspace_location_repos, nameForPath),
                            "work/$nameForPath" to stringResource(R.string.workspace_location_work, nameForPath),
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
                                label = { Text(stringResource(R.string.workspace_linked_directory)) },
                                placeholder = { Text(stringResource(R.string.workspace_directory_default)) },
                                supportingText = { Text(stringResource(R.string.workspace_directory_relative_hint)) },
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
                            label = { Text(stringResource(R.string.workspace_link_directory)) },
                            placeholder = { Text("Download/my-app") },
                            supportingText = { Text(stringResource(R.string.workspace_link_directory_description)) },
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
                            TextButton(onClick = { directoryPicker.launch(null) }) { Text(stringResource(R.string.workspace_choose_shared_directory)) }
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
                                ) { Text(stringResource(R.string.workspace_authorize_shared_space)) }
                            }
                        }
                    }
                    val path = directoryPath.trim().ifBlank { projectName.trim() }
                    if (path.isNotBlank()) Text(
                        stringResource(R.string.workspace_sandbox_path, if (projectStorage == WorkspaceStorage.INTERNAL) "/workspace" else "/sdcard", path.trimStart('/')),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.create(
                            name = projectName,
                            storage = projectStorage,
                            directoryPath = directoryPath,
                            template = selectedTemplate,
                            packageName = packageName,
                            apkSource = apkSource,
                            exportApkToDownload = exportApkToDownload,
                        )
                        projectName = ""
                        packageName = ""
                        directoryPath = ""
                        projectStorage = WorkspaceStorage.INTERNAL
                        apkSource = null
                        exportApkToDownload = false
                        showCreate = false
                    },
                    enabled = projectName.isNotBlank() && !busy &&
                        (projectStorage != WorkspaceStorage.SHARED || sharedAccessGranted) &&
                        (selectedTemplate != top.wkbin.taixu.runtime.ProjectTemplate.APK_REVERSE || apkSource != null),
                ) { Text(stringResource(R.string.workspace_create), color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreate = false
                    projectName = ""
                    packageName = ""
                    directoryPath = ""
                    projectStorage = WorkspaceStorage.INTERNAL
                    apkSource = null
                    exportApkToDownload = false
                }) { Text(stringResource(R.string.workspace_cancel)) }
            },
        )
    }

    if (showImport) {
        fun resetImportDialog() {
            showImport = false
            importMode = ProjectImportMode.LOCAL
            importProjectName = ""
            importDirectoryPath = ""
            importProjectType = ProjectType.ANDROID
            archiveSource = null
            importGitUrl = ""
            gitTransport = GitTransport.HTTP
        }
        RuntimeAlertDialog(
            onDismissRequest = { resetImportDialog() },
            title = { Text(stringResource(R.string.workspace_import_project), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.workspace_import_source),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = importMode == ProjectImportMode.LOCAL,
                            onClick = { importMode = ProjectImportMode.LOCAL },
                            leadingIcon = { RuntimeIcon(RuntimeIconName.FolderDownload, Modifier.size(16.dp)) },
                            label = { Text(stringResource(R.string.workspace_local_archive)) },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = importMode == ProjectImportMode.GITHUB,
                            onClick = { importMode = ProjectImportMode.GITHUB },
                            leadingIcon = { RuntimeIcon(RuntimeIconName.Github, Modifier.size(16.dp)) },
                            label = { Text("GitHub") },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (importMode == ProjectImportMode.LOCAL) {
                        OutlinedButton(
                            onClick = {
                                archivePicker.launch(
                                    arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RuntimeIcon(RuntimeIconName.FolderOpen, Modifier.size(17.dp))
                            Spacer(Modifier.size(7.dp))
                            Text(stringResource(R.string.workspace_choose_project_archive))
                        }
                        archiveSource?.let { source ->
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
                                    RuntimeIcon(RuntimeIconName.Compress, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                                    Text(
                                        source.fileName,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    IconButton(onClick = { archiveSource = null }, modifier = Modifier.size(30.dp)) {
                                        RuntimeIcon(RuntimeIconName.Close, Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            stringResource(R.string.workspace_git_transport),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = gitTransport == GitTransport.HTTP,
                                onClick = { gitTransport = GitTransport.HTTP },
                                label = { Text("HTTP(S)") },
                                modifier = Modifier.weight(1f),
                            )
                            FilterChip(
                                selected = gitTransport == GitTransport.SSH,
                                onClick = { gitTransport = GitTransport.SSH },
                                label = { Text("SSH") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OutlinedTextField(
                            value = importGitUrl,
                            onValueChange = { importGitUrl = it },
                            label = { Text(stringResource(R.string.workspace_git_url)) },
                            placeholder = {
                                Text(
                                    if (gitTransport == GitTransport.HTTP) "https://github.com/user/project.git"
                                    else "git@github.com:user/project.git",
                                )
                            },
                            supportingText = { Text(stringResource(R.string.workspace_git_import_description)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Text(
                        stringResource(R.string.workspace_project_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            ProjectType.ANDROID to ("Android" to RuntimeIconName.Android),
                            ProjectType.FLUTTER to ("Flutter" to RuntimeIconName.Flutter),
                            ProjectType.REVERSE to (stringResource(R.string.workspace_template_reverse) to RuntimeIconName.Reverse),
                            ProjectType.GENERAL to (stringResource(R.string.workspace_template_empty) to RuntimeIconName.Code),
                        ).forEach { (type, pair) ->
                            FilterChip(
                                selected = importProjectType == type,
                                onClick = { importProjectType = type },
                                leadingIcon = { RuntimeIcon(pair.second, Modifier.size(16.dp)) },
                                label = { Text(pair.first, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = importProjectName,
                        onValueChange = { importProjectName = it },
                        label = { Text(stringResource(R.string.workspace_project_name)) },
                        placeholder = { Text("my-imported-project") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    val importPathName = importProjectName.trim().ifBlank { "my-project" }
                    val commonDirectories = listOf(
                        "" to stringResource(R.string.workspace_location_project, importPathName),
                        "projects/$importPathName" to stringResource(R.string.workspace_location_projects, importPathName),
                        "repos/$importPathName" to stringResource(R.string.workspace_location_repos, importPathName),
                        "work/$importPathName" to stringResource(R.string.workspace_location_work, importPathName),
                    )
                    ExposedDropdownMenuBox(
                        expanded = importDirectoryMenuExpanded,
                        onExpandedChange = { importDirectoryMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = importDirectoryPath,
                            onValueChange = {
                                importDirectoryPath = it
                                importDirectoryMenuExpanded = true
                            },
                            label = { Text(stringResource(R.string.workspace_linked_directory)) },
                            placeholder = { Text(stringResource(R.string.workspace_directory_default)) },
                            supportingText = { Text(stringResource(R.string.workspace_import_directory_hint)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(importDirectoryMenuExpanded) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(
                                ExposedDropdownMenuAnchorType.PrimaryEditable,
                                true,
                            ),
                        )
                        ExposedDropdownMenu(
                            expanded = importDirectoryMenuExpanded,
                            onDismissRequest = { importDirectoryMenuExpanded = false },
                        ) {
                            commonDirectories.forEach { (path, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        importDirectoryPath = path
                                        importDirectoryMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    val targetPath = importDirectoryPath.trim().ifBlank { importProjectName.trim() }
                    if (targetPath.isNotBlank()) {
                        Text(
                            stringResource(R.string.workspace_sandbox_path, "/workspace", targetPath.trimStart('/')),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (importMode == ProjectImportMode.LOCAL) {
                            archiveSource?.let {
                                viewModel.importLocalProject(importProjectName, importDirectoryPath, importProjectType, it)
                            }
                        } else {
                            viewModel.importGithubProject(
                                importProjectName,
                                importDirectoryPath,
                                importProjectType,
                                importGitUrl,
                                gitTransport,
                            )
                        }
                        resetImportDialog()
                    },
                    enabled = importProjectName.isNotBlank() && !busy &&
                        ((importMode == ProjectImportMode.LOCAL && archiveSource != null) ||
                            (importMode == ProjectImportMode.GITHUB && importGitUrl.isNotBlank())),
                ) { Text(stringResource(R.string.workspace_import), color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { resetImportDialog() }) { Text(stringResource(R.string.workspace_cancel)) }
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
        RuntimeAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.workspace_delete_project_title, project.name), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (project.ownsDirectory) stringResource(R.string.workspace_delete_owned_project, project.linuxPath)
                    else stringResource(R.string.workspace_unlink_project),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { deleteTarget = null; viewModel.delete(project.name) },
                ) { Text(stringResource(R.string.workspace_confirm_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.workspace_cancel)) } },
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
    onExport: () -> Unit,
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
                                text = if (isBuilding) stringResource(R.string.workspace_building_badge) else project.projectType.displayName,
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
                            text = { Text(stringResource(R.string.workspace_open_terminal)) },
                            leadingIcon = {
                                RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(17.dp))
                            },
                            onClick = {
                                moreExpanded = false
                                onOpenTerminal()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.workspace_export_project)) },
                            leadingIcon = {
                                RuntimeIcon(RuntimeIconName.Compress, Modifier.size(17.dp))
                            },
                            onClick = {
                                moreExpanded = false
                                onExport()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.workspace_delete_project), color = MaterialTheme.colorScheme.error) },
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
                LinearProgressIndicator(
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
                    FilledTonalButton(
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
                                RuntimeCircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(stringResource(R.string.workspace_compiling), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                            } else {
                                RuntimeIcon(RuntimeIconName.Play, Modifier.size(14.dp), tint = typeBadgeColor)
                                Text(stringResource(R.string.workspace_run_on_device), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = typeBadgeColor)
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
                            Text(stringResource(R.string.workspace_terminal), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_choose_installed_app), fontWeight = FontWeight.Bold) },
        text = {
            if (apps.isEmpty()) {
                Text(
                    stringResource(R.string.workspace_apps_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.workspace_choose_app_description),
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.workspace_cancel)) } },
    )
}
