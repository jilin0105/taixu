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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
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

@Composable
fun WorkshopSettingsScreen(onBack: () -> Unit, onOpenEnvironment: () -> Unit, onOpenSigning: () -> Unit = {}, onEditScript: (WorkshopScriptType) -> Unit, viewModel: WorkshopSettingsViewModel = hiltViewModel()) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val customScripts by viewModel.customScripts.collectAsStateWithLifecycle()
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { RuntimeTopBar("工坊设置", onBack, "构建环境、签名与脚本") }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { SectionHeader("开发环境", "查看并调整 Android 与 Flutter 工具链") }
            item { SettingEntry(RuntimeIconName.Android, "Android / Flutter 环境", "SDK ${draft.androidSdkPath}\nNDK ${draft.ndkPath}\nGradle ${draft.gradlePath}\nCMake ${draft.cmakePath}", onOpenEnvironment) }
            item { SectionHeader("应用签名", "创建或导入签名文件，Release 构建时选用") }
            item { SettingEntry(RuntimeIconName.Key, "签名管理 (Keystore)", "创建 / 导入 Android 签名文件\n用于 Release 正式包构建", onOpenSigning) }
            item { SectionHeader("构建脚本", "点击脚本进入独立编辑页面") }
            item { RuntimeCard(contentPadding = PaddingValues(0.dp)) {
                ScriptRow(WorkshopScriptType.ANDROID, WorkshopScriptType.ANDROID in customScripts) { onEditScript(WorkshopScriptType.ANDROID) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ScriptRow(WorkshopScriptType.FLUTTER, WorkshopScriptType.FLUTTER in customScripts) { onEditScript(WorkshopScriptType.FLUTTER) }
            } }
        }
    }
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
