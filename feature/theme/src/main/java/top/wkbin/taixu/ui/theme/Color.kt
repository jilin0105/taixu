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

