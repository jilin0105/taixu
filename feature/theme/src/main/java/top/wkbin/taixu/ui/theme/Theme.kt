package top.wkbin.taixu.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Material 3 Expressive 浅色主题
 */
private val TaiXuLightColors = lightColorScheme(
    primary = M3ExpLightPrimary,
    onPrimary = M3ExpLightOnPrimary,
    primaryContainer = M3ExpLightPrimaryContainer,
    onPrimaryContainer = M3ExpLightOnPrimaryContainer,
    secondary = M3ExpLightSecondary,
    onSecondary = M3ExpLightOnSecondary,
    secondaryContainer = M3ExpLightSecondaryContainer,
    onSecondaryContainer = M3ExpLightOnSecondaryContainer,
    tertiary = M3ExpLightTertiary,
    onTertiary = M3ExpLightOnTertiary,
    tertiaryContainer = M3ExpLightTertiaryContainer,
    onTertiaryContainer = M3ExpLightOnTertiaryContainer,
    error = M3ExpLightError,
    onError = Color.White,
    errorContainer = M3ExpLightErrorContainer,
    onErrorContainer = Color(0xFF410002),
    background = M3ExpLightBackground,
    onBackground = M3ExpLightOnBackground,
    surface = M3ExpLightSurface,
    onSurface = M3ExpLightOnSurface,
    surfaceVariant = M3ExpLightSurfaceVariant,
    onSurfaceVariant = M3ExpLightOnSurfaceVariant,
    surfaceContainerLowest = M3ExpLightSurfaceContainerLowest,
    surfaceContainerLow = M3ExpLightSurfaceContainerLow,
    surfaceContainer = M3ExpLightSurfaceContainer,
    surfaceContainerHigh = M3ExpLightSurfaceContainerHigh,
    surfaceContainerHighest = M3ExpLightSurfaceContainerHighest,
    outline = M3ExpLightOutline,
    outlineVariant = M3ExpLightOutlineVariant,
)

/**
 * Material 3 Expressive 深色主题
 */
private val TaiXuDarkColors = darkColorScheme(
    primary = M3ExpDarkPrimary,
    onPrimary = M3ExpDarkOnPrimary,
    primaryContainer = M3ExpDarkPrimaryContainer,
    onPrimaryContainer = M3ExpDarkOnPrimaryContainer,
    secondary = M3ExpDarkSecondary,
    onSecondary = M3ExpDarkOnSecondary,
    secondaryContainer = M3ExpDarkSecondaryContainer,
    onSecondaryContainer = M3ExpDarkOnSecondaryContainer,
    tertiary = M3ExpDarkTertiary,
    onTertiary = M3ExpDarkOnTertiary,
    tertiaryContainer = M3ExpDarkTertiaryContainer,
    onTertiaryContainer = M3ExpDarkOnTertiaryContainer,
    error = M3ExpDarkError,
    onError = Color(0xFF690005),
    errorContainer = M3ExpDarkErrorContainer,
    onErrorContainer = Color(0xFFFFDAD6),
    background = M3ExpDarkBackground,
    onBackground = M3ExpDarkOnBackground,
    surface = M3ExpDarkSurface,
    onSurface = M3ExpDarkOnSurface,
    surfaceVariant = M3ExpDarkSurfaceVariant,
    onSurfaceVariant = M3ExpDarkOnSurfaceVariant,
    surfaceContainerLowest = M3ExpDarkSurfaceContainerLowest,
    surfaceContainerLow = M3ExpDarkSurfaceContainerLow,
    surfaceContainer = M3ExpDarkSurfaceContainer,
    surfaceContainerHigh = M3ExpDarkSurfaceContainerHigh,
    surfaceContainerHighest = M3ExpDarkSurfaceContainerHighest,
    outline = M3ExpDarkOutline,
    outlineVariant = M3ExpDarkOutlineVariant,
)

/**
 * Material 3 Expressive 形状体系 (Generous, Organic, Bolder)
 */
private val TaiXuShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun TaiXuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                // 浅色主题下状态栏与导航栏图标切换为深色/黑色；深色主题下切换为浅色/白色
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) TaiXuDarkColors else TaiXuLightColors,
        typography = AppTypography,
        shapes = TaiXuShapes,
        content = content,
    )
}
