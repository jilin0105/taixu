package top.wkbin.taixu.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 「太墟 · TaiXu」Material 3 Expressive 设计系统色板
 * 融合 Google M3 Expressive 的生动高阶色调：
 * 曜石夜空（Obsidian Expressive Dark）与 温润素白（Alabaster Expressive Light）
 */

// M3 Expressive 主色系 (Indigo / Ultramarine)
val M3ExpLightPrimary = Color(0xFF4259C3)
val M3ExpLightOnPrimary = Color(0xFFFFFFFF)
val M3ExpLightPrimaryContainer = Color(0xFFDFE1FF)
val M3ExpLightOnPrimaryContainer = Color(0xFF001453)

val M3ExpDarkPrimary = Color(0xFFBAC3FF)
val M3ExpDarkOnPrimary = Color(0xFF0C2792)
val M3ExpDarkPrimaryContainer = Color(0xFF293FA9)
val M3ExpDarkOnPrimaryContainer = Color(0xFFDFE1FF)

// M3 Expressive 次色系 (Expressive Slate Iris)
val M3ExpLightSecondary = Color(0xFF5B5D72)
val M3ExpLightOnSecondary = Color(0xFFFFFFFF)
val M3ExpLightSecondaryContainer = Color(0xFFDFE1F9)
val M3ExpLightOnSecondaryContainer = Color(0xFF181A2C)

val M3ExpDarkSecondary = Color(0xFFC3C5DD)
val M3ExpDarkOnSecondary = Color(0xFF2C2F42)
val M3ExpDarkSecondaryContainer = Color(0xFF434559)
val M3ExpDarkOnSecondaryContainer = Color(0xFFDFE1F9)

// M3 Expressive 三级色 / 强调色 (Terracotta Coral / Rose)
val M3ExpLightTertiary = Color(0xFF944A32)
val M3ExpLightOnTertiary = Color(0xFFFFFFFF)
val M3ExpLightTertiaryContainer = Color(0xFFFFDBD1)
val M3ExpLightOnTertiaryContainer = Color(0xFF3B0900)

val M3ExpDarkTertiary = Color(0xFFFFB5A0)
val M3ExpDarkOnTertiary = Color(0xFF5A1C08)
val M3ExpDarkTertiaryContainer = Color(0xFF77321D)
val M3ExpDarkOnTertiaryContainer = Color(0xFFFFDBD1)

// M3 Expressive 状态色 (Semantic Feedback)
val M3ExpLightSuccess = Color(0xFF2E7D32)
val M3ExpLightSuccessContainer = Color(0xFFD7F5D9)
val M3ExpDarkSuccess = Color(0xFF81C784)
val M3ExpDarkSuccessContainer = Color(0xFF1B4D20)

val M3ExpLightWarning = Color(0xFFB25E00)
val M3ExpLightWarningContainer = Color(0xFFFFDCBE)
val M3ExpDarkWarning = Color(0xFFFFB77C)
val M3ExpDarkWarningContainer = Color(0xFF6B3600)

val M3ExpLightError = Color(0xFFBA1A1A)
val M3ExpLightErrorContainer = Color(0xFFFFDAD6)
val M3ExpDarkError = Color(0xFFFFB4AB)
val M3ExpDarkErrorContainer = Color(0xFF93000A)

// M3 Expressive 浅色表面层级 (Alabaster Warm Layers)
val M3ExpLightBackground = Color(0xFFFAF8FD)
val M3ExpLightOnBackground = Color(0xFF1A1B21)
val M3ExpLightSurface = Color(0xFFFAF8FD)
val M3ExpLightOnSurface = Color(0xFF1A1B21)
val M3ExpLightSurfaceVariant = Color(0xFFE2E2EC)
val M3ExpLightOnSurfaceVariant = Color(0xFF45464F)
val M3ExpLightSurfaceContainerLowest = Color(0xFFFFFFFF)
val M3ExpLightSurfaceContainerLow = Color(0xFFF4F2F8)
val M3ExpLightSurfaceContainer = Color(0xFFEEEBF2)
val M3ExpLightSurfaceContainerHigh = Color(0xFFE8E5EC)
val M3ExpLightSurfaceContainerHighest = Color(0xFFE2DFE7)
val M3ExpLightOutline = Color(0xFF757680)
val M3ExpLightOutlineVariant = Color(0xFFC6C6D0)

// M3 Expressive 深色表面层级 (Obsidian Velvet Layers)
val M3ExpDarkBackground = Color(0xFF121318)
val M3ExpDarkOnBackground = Color(0xFFE3E2E9)
val M3ExpDarkSurface = Color(0xFF121318)
val M3ExpDarkOnSurface = Color(0xFFE3E2E9)
val M3ExpDarkSurfaceVariant = Color(0xFF45464F)
val M3ExpDarkOnSurfaceVariant = Color(0xFFC6C6D0)
val M3ExpDarkSurfaceContainerLowest = Color(0xFF0D0E13)
val M3ExpDarkSurfaceContainerLow = Color(0xFF1A1B21)
val M3ExpDarkSurfaceContainer = Color(0xFF1E1F25)
val M3ExpDarkSurfaceContainerHigh = Color(0xFF282A30)
val M3ExpDarkSurfaceContainerHighest = Color(0xFF33343B)
val M3ExpDarkOutline = Color(0xFF90909A)
val M3ExpDarkOutlineVariant = Color(0xFF45464F)

// 兼容旧语义常量别名（自动桥接至 M3 Expressive）
val CyberCyan = M3ExpDarkPrimary
val CyberCyanMuted = M3ExpDarkSecondary
val CyberCyanContainer = M3ExpDarkPrimaryContainer
val AuroraViolet = M3ExpLightSecondary
val AuroraVioletLight = M3ExpDarkSecondary
val AuroraVioletContainer = M3ExpDarkSecondaryContainer
val PulseEmerald = M3ExpDarkSuccess
val PulseEmeraldContainer = M3ExpDarkSuccessContainer
val SolarAmber = M3ExpDarkWarning
val SolarAmberContainer = M3ExpDarkWarningContainer
val LaserCrimson = M3ExpDarkError
val LaserCrimsonContainer = M3ExpDarkErrorContainer

val TaiXuVoid = M3ExpDarkSurfaceContainerLowest
val TaiXuBackground = M3ExpDarkBackground
val TaiXuSurface = M3ExpDarkSurface
val TaiXuSurfaceLow = M3ExpDarkSurfaceContainerLow
val TaiXuSurfaceContainerLow = M3ExpDarkSurfaceContainer
val TaiXuSurfaceHigh = M3ExpDarkSurfaceContainerHigh
val TaiXuSurfaceHighest = M3ExpDarkSurfaceContainerHighest
val TaiXuOnPrimary = M3ExpDarkOnPrimary
val TaiXuOnSurface = M3ExpDarkOnSurface
val TaiXuOnSurfaceMuted = M3ExpDarkOnSurfaceVariant
val TaiXuOnSurfaceDim = M3ExpDarkOutline
val TaiXuOutline = M3ExpDarkOutline
val TaiXuOutlineVariant = M3ExpDarkOutlineVariant
val TaiXuGlassBg = Color(0xF21E1F25)

val LightBg = M3ExpLightBackground
val LightSurface = M3ExpLightSurface
val LightSurfaceLow = M3ExpLightSurfaceContainerLow
val LightSurfaceHigh = M3ExpLightSurfaceContainerHigh
val LightSurfaceHighest = M3ExpLightSurfaceContainerHighest
val LightPrimary = M3ExpLightPrimary
val LightPrimaryContainer = M3ExpLightPrimaryContainer
val LightOnPrimary = M3ExpLightOnPrimary
val LightOnPrimaryContainer = M3ExpLightOnPrimaryContainer
val LightSecondary = M3ExpLightSecondary
val LightSecondaryContainer = M3ExpLightSecondaryContainer
val LightOnSecondaryContainer = M3ExpLightOnSecondaryContainer
val LightOnSurface = M3ExpLightOnSurface
val LightOnSurfaceMuted = M3ExpLightOnSurfaceVariant
val LightOnSurfaceDim = M3ExpLightOutline
val LightOutline = M3ExpLightOutline
val LightOutlineVariant = M3ExpLightOutlineVariant
val LightGlassBg = Color(0xF2FFFFFF)
val LightGlowBorder = Color(0x1F4259C3)
val TaiXuGlowBorder = Color(0x2FBAC3FF)

// ======================= 「澄明 · Chengming」液态玻璃主题色板 =======================
// 半透明冰霜质地，让根层流光（Aurora）透出并经由 backdrop 折射，呈现液态玻璃质感。
// 半透明度取折中：保留可读性，同时玻璃感足够明显。

// 澄明 · 浅色
val ChengmingLightPrimary = Color(0xFF3E63DD)
val ChengmingLightOnPrimary = Color(0xFFFFFFFF)
val ChengmingLightPrimaryContainer = Color(0xCCD6E4FF)
val ChengmingLightOnPrimaryContainer = Color(0xFF0A2540)
val ChengmingLightSecondary = Color(0xFF2E6E9E)
val ChengmingLightOnSecondary = Color(0xFFFFFFFF)
val ChengmingLightSecondaryContainer = Color(0xCCCFE4FF)
val ChengmingLightOnSecondaryContainer = Color(0xFF0A2740)
val ChengmingLightTertiary = Color(0xFF7A6BD0)
val ChengmingLightOnTertiary = Color(0xFFFFFFFF)
val ChengmingLightTertiaryContainer = Color(0xCCE4DCFF)
val ChengmingLightOnTertiaryContainer = Color(0xFF241A4A)
val ChengmingLightError = Color(0xFFB3261E)
val ChengmingLightOnError = Color(0xFFFFFFFF)
val ChengmingLightErrorContainer = Color(0xFFF9DEDC)
val ChengmingLightOnErrorContainer = Color(0xFF410E0B)
// 近不透明白蓝，保持内容可读；流光 Aurora 由底部玻璃折射呈现
val ChengmingLightBackground = Color(0xB8F2F7FF)
val ChengmingLightOnBackground = Color(0xFF0F2438)
val ChengmingLightSurface = Color(0xC8F7FAFF)
val ChengmingLightOnSurface = Color(0xFF0F2438)
val ChengmingLightSurfaceVariant = Color(0x99DCE6F1)
val ChengmingLightOnSurfaceVariant = Color(0xFF3D5268)
val ChengmingLightSurfaceContainerLowest = Color(0xA8FFFFFF)
val ChengmingLightSurfaceContainerLow = Color(0xC8EAF1FC)
val ChengmingLightSurfaceContainer = Color(0xCCE0EAF7)
val ChengmingLightSurfaceContainerHigh = Color(0xCCD5E2F1)
val ChengmingLightSurfaceContainerHighest = Color(0xCCC9D8EA)
val ChengmingLightOutline = Color(0xFF7A8BA0)
val ChengmingLightOutlineVariant = Color(0x88C2D0E0)

// 澄明 · 深色
val ChengmingDarkPrimary = Color(0xFF8FABFF)
val ChengmingDarkOnPrimary = Color(0xFF0A2540)
val ChengmingDarkPrimaryContainer = Color(0x661B3A6E)
val ChengmingDarkOnPrimaryContainer = Color(0xFFD6E4FF)
val ChengmingDarkSecondary = Color(0xFF8FC8FF)
val ChengmingDarkOnSecondary = Color(0xFF0A2740)
val ChengmingDarkSecondaryContainer = Color(0x662C4C70)
val ChengmingDarkOnSecondaryContainer = Color(0xFFCFE4FF)
val ChengmingDarkTertiary = Color(0xFFC4B5FF)
val ChengmingDarkOnTertiary = Color(0xFF241A4A)
val ChengmingDarkTertiaryContainer = Color(0x66332A6B)
val ChengmingDarkOnTertiaryContainer = Color(0xFFE4DCFF)
val ChengmingDarkError = Color(0xFFFFB4AB)
val ChengmingDarkOnError = Color(0xFF690005)
val ChengmingDarkErrorContainer = Color(0xFF93000A)
val ChengmingDarkOnErrorContainer = Color(0xFFFFDAD6)
// 深色近不透明，内容可读；流光同样由底部玻璃折射呈现
val ChengmingDarkBackground = Color(0xB80B1322)
val ChengmingDarkOnBackground = Color(0xFFD7E4F5)
val ChengmingDarkSurface = Color(0xC8101B2E)
val ChengmingDarkOnSurface = Color(0xFFD7E4F5)
val ChengmingDarkSurfaceVariant = Color(0x6646535F)
val ChengmingDarkOnSurfaceVariant = Color(0xFFB6C4D4)
val ChengmingDarkSurfaceContainerLowest = Color(0xA8080F1C)
val ChengmingDarkSurfaceContainerLow = Color(0xCC141F33)
val ChengmingDarkSurfaceContainer = Color(0xCC1A2639)
val ChengmingDarkSurfaceContainerHigh = Color(0xCC222F43)
val ChengmingDarkSurfaceContainerHighest = Color(0xCC2A3850)
val ChengmingDarkOutline = Color(0xFF8A97A8)
val ChengmingDarkOutlineVariant = Color(0x5546535F)

// 澄明 · 流光底色（Aurora）——液态玻璃折射的"源"
val ChengmingAuroraTop = Color(0xFF3E63DD)
val ChengmingAuroraMid = Color(0xFF6E7CE0)
val ChengmingAuroraBottom = Color(0xFFB47AE8)
val ChengmingAuroraGlow = Color(0x6629B6F6)

