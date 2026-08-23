package top.wkbin.taixu.ui.settings

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import top.wkbin.taixu.ui.components.RuntimeRadioButton as RadioButton
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import top.wkbin.taixu.ui.components.RuntimeSlider as Slider
import top.wkbin.taixu.ui.components.RuntimeSwitch as Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTopBar

/**
 * 太墟 · 外观、字号与终端深度定制页面 (Appearance & Terminal Settings)
 */
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val themeStyle by viewModel.themeStyle.collectAsStateWithLifecycle()
    val terminalFontSize by viewModel.terminalFontSize.collectAsStateWithLifecycle()
    val terminalColorScheme by viewModel.terminalColorScheme.collectAsStateWithLifecycle()
    val terminalHapticsEnabled by viewModel.terminalHapticsEnabled.collectAsStateWithLifecycle()
    val appFontScale by viewModel.appFontScale.collectAsStateWithLifecycle()
    val backgroundUri by viewModel.chengmingBackgroundUri.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var terminalFontSizeSlider by remember { mutableFloatStateOf(terminalFontSize.toFloat()) }
    var pageScaleSlider by remember { mutableFloatStateOf(appFontScale) }
    LaunchedEffect(terminalFontSize) {
        terminalFontSizeSlider = terminalFontSize.toFloat()
    }
    LaunchedEffect(appFontScale) {
        pageScaleSlider = appFontScale
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        viewModel.setChengmingBackgroundUri(uri.toString())
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar("外观与终端", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. 终端实时效果预览卡片
            item {
                Text(
                    text = "终端实时效果预览",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                TerminalPreviewCard(
                    colorScheme = terminalColorScheme,
                    fontSizeSp = terminalFontSizeSlider.roundToInt(),
                )
            }

            // 2. 终端外观与控制台偏好
            item {
                Text(
                    text = "终端外观与控制台",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    // 终端配色方案
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "终端配色方案",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TerminalThemeChip(
                                title = "曜石黑",
                                bg = Color(0xFF0F1117),
                                text = Color(0xFFE2E2E9),
                                selected = terminalColorScheme == "obsidian",
                                onClick = { viewModel.setTerminalColorScheme("obsidian") },
                                modifier = Modifier.weight(1f),
                            )
                            TerminalThemeChip(
                                title = "黑客绿",
                                bg = Color(0xFF0A0F0D),
                                text = Color(0xFF10B981),
                                selected = terminalColorScheme == "matrix",
                                onClick = { viewModel.setTerminalColorScheme("matrix") },
                                modifier = Modifier.weight(1f),
                            )
                            TerminalThemeChip(
                                title = "复古琥珀",
                                bg = Color(0xFF140F0A),
                                text = Color(0xFFF59E0B),
                                selected = terminalColorScheme == "amber",
                                onClick = { viewModel.setTerminalColorScheme("amber") },
                                modifier = Modifier.weight(1f),
                            )
                            TerminalThemeChip(
                                title = "深海极光",
                                bg = Color(0xFF0D1424),
                                text = Color(0xFF38BDF8),
                                selected = terminalColorScheme == "aurora",
                                onClick = { viewModel.setTerminalColorScheme("aurora") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 终端字体大小
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "终端字体大小",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${terminalFontSizeSlider.roundToInt()} sp",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Slider(
                            value = terminalFontSizeSlider,
                            onValueChange = { terminalFontSizeSlider = it.roundToInt().toFloat() },
                            onValueChangeFinished = {
                                viewModel.setTerminalFontSize(terminalFontSizeSlider.roundToInt())
                            },
                            valueRange = 10f..24f,
                            steps = 13,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "10 sp",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "24 sp",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 终端触觉震动反馈
                    ToggleRow(
                        icon = RuntimeIconName.Vibrate,
                        title = "终端触觉按键反馈",
                        subtitle = "点击辅助按键条与回车时产生微弱触觉震动",
                        checked = terminalHapticsEnabled,
                        change = viewModel::setTerminalHapticsEnabled,
                    )
                }
            }

            // 3. 应用视觉与主题
            item {
                Text(
                    text = "主题风格",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    ThemeOptionRow(
                        title = "玄同 · Xuantong",
                        subtitle = "默认主题：源自《老子》「万物同归于玄」，曜石夜空与温润素白",
                        accentColor = androidx.compose.ui.graphics.Color(0xFF4259C3),
                        selected = themeStyle == "xuantong",
                        onClick = { viewModel.setThemeStyle("xuantong") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ThemeOptionRow(
                        title = "澄明 · Chengming",
                        subtitle = "液态玻璃：毛玻璃折射 + 流光 Aurora，通透清澈",
                        accentColor = androidx.compose.ui.graphics.Color(0xFF6E7CE0),
                        selected = themeStyle == "chengming",
                        onClick = { viewModel.setThemeStyle("chengming") },
                    )
                }
            }

            item {
                Text(
                    text = "澄明背景",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = if (backgroundUri == null) "无背景（默认）" else "使用用户图片作为玻璃折射源",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                        backgroundUri?.let { uri -> ChengmingBackgroundPreview(uri) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { backgroundPicker.launch(arrayOf("image/*")) },
                            ) { Text(if (backgroundUri == null) "上传图片" else "更换图片") }
                            if (backgroundUri != null) {
                                TextButton(
                                    onClick = { viewModel.setChengmingBackgroundUri(null) },
                                ) { Text("移除背景") }
                            }
                        }
                    }
                }
            }

            // 3.5 深浅色模式
            item {
                Text(
                    text = "深浅色模式",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    ThemeOptionRow(
                        title = "跟随系统 (Auto)",
                        subtitle = "随 Android 设备深浅色自动无缝切换",
                        selected = themeMode == "system",
                        onClick = { viewModel.setThemeMode("system") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ThemeOptionRow(
                        title = "素白浅色 (Light)",
                        subtitle = "明澈素雅，适合日间光线明亮环境",
                        selected = themeMode == "light",
                        onClick = { viewModel.setThemeMode("light") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ThemeOptionRow(
                        title = "深邃曜石 (Dark)",
                        subtitle = "Material 3 曜石天鹅绒暗色，沉浸专注",
                        selected = themeMode == "dark",
                        onClick = { viewModel.setThemeMode("dark") },
                    )
                }
            }

            // 4. 页面缩放
            item {
                Text(
                    text = "页面缩放",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "缩放比例",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${(pageScaleSlider * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Slider(
                            value = pageScaleSlider,
                            onValueChange = {
                                pageScaleSlider = ((it * 20f).roundToInt() / 20f).coerceIn(0.8f, 1.3f)
                            },
                            onValueChangeFinished = { viewModel.setAppFontScale(pageScaleSlider) },
                            valueRange = 0.8f..1.3f,
                            steps = 9,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "80%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "130%",
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

@Composable
private fun TerminalPreviewCard(
    colorScheme: String,
    fontSizeSp: Int,
) {
    val (bg, textColor, accentColor) = when (colorScheme) {
        "matrix" -> Triple(Color(0xFF0A0F0D), Color(0xFF10B981), Color(0xFF34D399))
        "amber" -> Triple(Color(0xFF140F0A), Color(0xFFF59E0B), Color(0xFFFBBF24))
        "aurora" -> Triple(Color(0xFF0D1424), Color(0xFF38BDF8), Color(0xFF818CF8))
        else -> Triple(Color(0xFF0F1117), Color(0xFFE2E2E9), Color(0xFF60A5FA))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, Color(0xFF282A36)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF5F56)))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF27C93F)))
                }
                Text(
                    text = "Linux PRoot (pty0)",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                    color = textColor.copy(alpha = 0.5f),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "taixu@debian:~$ uname -a",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = accentColor,
            )
            Text(
                text = "Linux taixu 6.6.0-aarch64 #1 SMP PREEMPT GNU/Linux",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp,
                ),
                color = textColor.copy(alpha = 0.9f),
            )
            Text(
                text = "taixu@debian:~$ echo \"太墟沙箱运行正常 🚀\"",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = accentColor,
            )
            Text(
                text = "太墟沙箱运行正常 🚀",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp,
                ),
                color = textColor,
            )
        }
    }
}

@Composable
private fun ChengmingBackgroundPreview(uri: String) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        runCatching {
            context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use(BitmapFactory::decodeStream)
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "澄明背景预览",
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun TerminalThemeChip(
    title: String,
    bg: Color,
    text: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(
            1.5.dp,
            if (selected) MaterialTheme.colorScheme.primary else Color(0xFF282A36),
        ),
    ) {
        Column(
            Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(text),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    RuntimeIcon(RuntimeIconName.Check, Modifier.size(10.dp), bg)
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                color = text,
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: androidx.compose.ui.graphics.Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (accentColor != null) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(accentColor),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
        )
    }
}
