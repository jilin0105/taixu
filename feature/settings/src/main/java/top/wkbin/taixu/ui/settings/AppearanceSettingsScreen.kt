package top.wkbin.taixu.ui.settings

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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val terminalFontSize by viewModel.terminalFontSize.collectAsStateWithLifecycle()
    val terminalColorScheme by viewModel.terminalColorScheme.collectAsStateWithLifecycle()
    val terminalHapticsEnabled by viewModel.terminalHapticsEnabled.collectAsStateWithLifecycle()
    val appFontScale by viewModel.appFontScale.collectAsStateWithLifecycle()

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
                    fontSizeSp = terminalFontSize,
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
                                text = "${terminalFontSize} sp",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(11 to "极小 11", 13 to "标准 13", 15 to "舒适 15", 17 to "大号 17").forEach { (size, label) ->
                                val isSelected = terminalFontSize == size
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.setTerminalFontSize(size) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    ),
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
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
                    text = "应用视觉主题",
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

            // 4. 应用全局字号缩放
            item {
                Text(
                    text = "应用全局字号缩放",
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
                                text = "字号比例",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${(appFontScale * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(0.9f to "紧凑 90%", 1.0f to "标准 100%", 1.1f to "舒适 110%", 1.2f to "大字 120%").forEach { (scale, label) ->
                                val isSelected = kotlin.math.abs(appFontScale - scale) < 0.05f
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.setAppFontScale(scale) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    ),
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
        )
    }
}
