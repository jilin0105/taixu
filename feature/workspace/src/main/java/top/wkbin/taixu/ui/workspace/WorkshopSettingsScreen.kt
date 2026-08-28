package top.wkbin.taixu.ui.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.SectionHeader
import top.wkbin.taixu.core.database.BuildScriptEntity
import top.wkbin.taixu.runtime.ProjectType

@Composable
fun WorkshopSettingsScreen(onBack: () -> Unit, onOpenEnvironment: () -> Unit, onOpenSigning: () -> Unit = {}, onEditScript: (WorkshopScriptType) -> Unit = {}, viewModel: WorkshopSettingsViewModel = hiltViewModel()) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val scripts by viewModel.managedScripts.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val bindings by viewModel.projectBindings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BuildScriptEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { RuntimeTopBar("工坊设置", onBack, "构建环境、签名与脚本") }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { SectionHeader("开发环境", "查看并调整 Android 与 Flutter 工具链") }
            item { SettingEntry(RuntimeIconName.Android, "Android / Flutter 环境", "SDK ${draft.androidSdkPath}\nNDK ${draft.ndkPath}\nGradle ${draft.gradlePath}\nCMake ${draft.cmakePath}", onOpenEnvironment) }
            item { SectionHeader("应用签名", "创建或导入签名文件，Release 构建时选用") }
            item { SettingEntry(RuntimeIconName.Key, "签名管理 (Keystore)", "创建 / 导入 Android 签名文件\n用于 Release 正式包构建", onOpenSigning) }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { SectionHeader("构建脚本", "Room 持久化；每个项目可选择不同脚本") }
                RuntimeButton(onClick = { creating = true }) { Text("新建脚本") }
            } }
            scripts.forEach { script -> item(key = script.id) {
                ManagedScriptCard(
                    script = script,
                    onEdit = { editing = script },
                    onClone = { viewModel.cloneScript(script) },
                    onDelete = { viewModel.deleteManagedScript(script.id) },
                )
            } }
            item { SectionHeader("项目挂载", "未挂载时继续使用标准构建流程") }
            projects.filter { it.projectType == ProjectType.ANDROID || it.projectType == ProjectType.FLUTTER }.forEach { project ->
                item(key = "binding-${project.name}") {
                    ProjectScriptBindingRow(
                        projectName = project.name,
                        projectType = project.projectType,
                        scripts = scripts.filter { it.projectType == project.projectType.name },
                        selectedId = bindings.firstOrNull { it.projectName == project.name }?.scriptId,
                        onSelect = { viewModel.bindProject(project.name, it) },
                    )
                }
            }
        }
    }
    if (creating) ManagedScriptEditorDialog(null, onDismiss = { creating = false }) { name, description, type, content ->
        viewModel.saveManagedScript(null, name, description, type, content)
        creating = false
    }
    editing?.let { script -> ManagedScriptEditorDialog(script, onDismiss = { editing = null }) { name, description, type, content ->
        viewModel.saveManagedScript(script.id, name, description, type, content)
        editing = null
    } }
}

@Composable
private fun ManagedScriptCard(script: BuildScriptEntity, onEdit: () -> Unit, onClone: () -> Unit, onDelete: () -> Unit) {
    RuntimeCard(onClick = onEdit) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeIcon(if (script.projectType == ProjectType.FLUTTER.name) RuntimeIconName.Flutter else RuntimeIconName.Android, Modifier.size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(script.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("${script.projectType} · ${if (script.isBuiltin) "内置模板" else "用户脚本"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (script.description.isNotBlank()) Text(script.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("编辑", Modifier.clickable(onClick = onEdit), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Text("复制", Modifier.clickable(onClick = onClone), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                if (!script.isBuiltin) Text("删除", Modifier.clickable(onClick = onDelete), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ProjectScriptBindingRow(projectName: String, projectType: ProjectType, scripts: List<BuildScriptEntity>, selectedId: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = scripts.firstOrNull { it.id == selectedId }
    RuntimeCard(onClick = { expanded = true }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(projectName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("${projectType.displayName} · ${selected?.name ?: "标准构建流程"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RuntimeIcon(RuntimeIconName.ChevronRight, Modifier.size(20.dp))
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("标准构建流程") }, onClick = { expanded = false; onSelect(null) })
                scripts.forEach { script -> DropdownMenuItem(text = { Text(script.name) }, onClick = { expanded = false; onSelect(script.id) }) }
            }
        }
    }
}

@Composable
private fun ManagedScriptEditorDialog(script: BuildScriptEntity?, onDismiss: () -> Unit, onSave: (String, String, ProjectType, String) -> Unit) {
    val defaultAndroidTemplate = "#!/bin/sh\nset -eu\nPROJECT_DIR=\"\${1:-.}\"\nTASK=\"\${2:-assembleDebug}\"\ncd \"\$PROJECT_DIR\"\n./gradlew \"\$TASK\" --no-daemon --max-workers=2\n"
    val defaultFlutterTemplate = "#!/bin/sh\nset -eu\nPROJECT_DIR=\"\${1:-.}\"\nTARGET=\"\${2:-apk --debug}\"\ncd \"\$PROJECT_DIR\"\nflutter pub get\nflutter build \$TARGET\n"
    var name by remember(script) { mutableStateOf(script?.name.orEmpty()) }
    var description by remember(script) { mutableStateOf(script?.description.orEmpty()) }
    var type by remember(script) { mutableStateOf(runCatching { ProjectType.valueOf(script?.projectType ?: ProjectType.ANDROID.name) }.getOrDefault(ProjectType.ANDROID)) }
    var content by remember(script) { mutableStateOf(script?.content ?: if (type == ProjectType.FLUTTER) defaultFlutterTemplate else defaultAndroidTemplate) }
    var typeExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (script == null) "新建构建脚本" else "编辑构建脚本") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("用途与适用版本") }, modifier = Modifier.fillMaxWidth())
            RuntimeOutlinedButton(onClick = { typeExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("类型：${type.displayName}") }
            DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                listOf(ProjectType.ANDROID, ProjectType.FLUTTER).forEach { candidate ->
                    DropdownMenuItem(
                        text = { Text(candidate.displayName) },
                        onClick = {
                            if (script == null && (content == defaultAndroidTemplate || content == defaultFlutterTemplate || content.isBlank())) {
                                content = if (candidate == ProjectType.FLUTTER) defaultFlutterTemplate else defaultAndroidTemplate
                            }
                            type = candidate
                            typeExpanded = false
                        },
                    )
                }
            }
            CodeEditorPanel(content, { content = it }, "build.sh", Modifier.fillMaxWidth().height(260.dp))
            Text("接口约定：${'$'}1 为项目目录；Android 的 ${'$'}2 为 Gradle task，Flutter 的 ${'$'}2 为完整 build 参数。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { RuntimeButton(onClick = { onSave(name, description, type, content) }, enabled = name.isNotBlank() && content.isNotBlank()) { Text("保存") } },
        dismissButton = { RuntimeOutlinedButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun WorkshopEnvironmentSettingsScreen(onBack: () -> Unit, viewModel: WorkshopSettingsViewModel = hiltViewModel()) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { RuntimeTopBar("开发环境", onBack, "沙箱内执行路径") }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SectionHeader("工具链路径", "路径均位于当前 Linux 沙箱内，保存后用于环境预检和构建") }
            item { RuntimeCard { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PathField("Android SDK", draft.androidSdkPath) { viewModel.update(draft.copy(androidSdkPath = it)) }
                PathField("Android NDK", draft.ndkPath) { viewModel.update(draft.copy(ndkPath = it)) }
                PathField("Flutter SDK", draft.flutterSdkPath) { viewModel.update(draft.copy(flutterSdkPath = it)) }
                PathField("Java / JDK", draft.javaPath) { viewModel.update(draft.copy(javaPath = it)) }
                PathField("Gradle", draft.gradlePath) { viewModel.update(draft.copy(gradlePath = it)) }
                PathField("CMake", draft.cmakePath) { viewModel.update(draft.copy(cmakePath = it)) }
                PathField("Ninja", draft.ninjaPath) { viewModel.update(draft.copy(ninjaPath = it)) }
                PathField("AAPT2", draft.aapt2Path) { viewModel.update(draft.copy(aapt2Path = it)) }
                PathField("Gradle 缓存", draft.gradleUserHome) { viewModel.update(draft.copy(gradleUserHome = it)) }
                PathField("Flutter Pub 缓存", draft.pubCache) { viewModel.update(draft.copy(pubCache = it)) }
                PathField("Android 工具目录 / ADB", draft.toolDir) { viewModel.update(draft.copy(toolDir = it)) }
            } } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeOutlinedButton(onClick = viewModel::resetEnvironment, modifier = Modifier.weight(1f)) { Text("重置") }
                RuntimeButton(onClick = viewModel::saveEnvironment, modifier = Modifier.weight(1f)) { Text("保存") }
            } }
        }
    }
}

@Composable
fun WorkshopScriptEditorScreen(type: WorkshopScriptType, onBack: () -> Unit, viewModel: WorkshopSettingsViewModel = hiltViewModel()) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val customScripts by viewModel.customScripts.collectAsStateWithLifecycle()
    var content by remember(type, draft) { mutableStateOf(viewModel.scriptContent(type)) }
    val isCustom = type in customScripts
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { RuntimeTopBar(type.title, onBack, if (isCustom) "自定义脚本" else "系统默认脚本") }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScriptPathPanel(if (isCustom) type.customPath else type.defaultPath)
            CodeEditorPanel(value = content, onValueChange = { content = it }, fileName = type.defaultPath.substringAfterLast('/'), modifier = Modifier.fillMaxWidth().weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeOutlinedButton(onClick = { viewModel.resetScript(type, onBack) }, modifier = Modifier.weight(1f)) { Text("恢复默认") }
                RuntimeButton(onClick = { viewModel.saveScript(type, content, onBack) }, modifier = Modifier.weight(1f)) { Text("保存脚本") }
            }
        }
    }
}

@Composable private fun SettingEntry(icon: RuntimeIconName, title: String, subtitle: String, onClick: () -> Unit) {
    RuntimeCard(onClick = onClick) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) { RuntimeIcon(icon, Modifier.padding(9.dp), MaterialTheme.colorScheme.onSecondaryContainer) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        RuntimeIcon(RuntimeIconName.ChevronRight, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}

@Composable private fun ScriptRow(type: WorkshopScriptType, isCustom: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RuntimeIcon(if (type == WorkshopScriptType.ANDROID) RuntimeIconName.Android else RuntimeIconName.Flutter, Modifier.size(28.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(type.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); Text(if (isCustom) type.customPath else type.defaultPath, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(if (isCustom) "已使用自定义脚本" else "使用系统默认脚本", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
        RuntimeIcon(RuntimeIconName.ChevronRight, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun ScriptPathPanel(path: String) { RuntimeCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("当前执行路径", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(path, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)) } } }

@Composable
private fun PathField(label: String, value: String, onValueChange: (String) -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f), shape).padding(horizontal = 12.dp, vertical = 10.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun CodeEditorPanel(value: String, onValueChange: (String) -> Unit, fileName: String, modifier: Modifier = Modifier) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest, shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), shape)) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, androidx.compose.foundation.shape.RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RuntimeIcon(RuntimeIconName.Code, Modifier.size(15.dp), MaterialTheme.colorScheme.primary)
            Text(fileName, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
    }
}
