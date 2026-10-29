package com.nairocy.claude.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LightScheme = lightColorScheme(
    primary = BrandAccentLight,
    onPrimary = CardLight,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = SecondaryTextLight,
    error = ErrorLight,
    errorContainer = ErrorContainerLight,
)

private val DarkScheme = darkColorScheme(
    primary = BrandAccentDark,
    onPrimary = SurfaceDark,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = SecondaryTextDark,
    error = ErrorDark,
    errorContainer = ErrorContainerDark,
)

object ClaudeTheme {
    val colors: ClaudeColors
        @Composable @ReadOnlyComposable
        get() = LocalClaudeColors.current

    val typography: Typography
        @Composable @ReadOnlyComposable
        get() = ClaudeTypography
}

@Composable
fun ClaudeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** true = 字体样式「系统」：全部切成 FontFamily.Default */
    systemFont: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(
        LocalClaudeColors provides colors,
        LocalSystemFont provides systemFont,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = ClaudeTypography,
            content = content,
        )
    }
}
