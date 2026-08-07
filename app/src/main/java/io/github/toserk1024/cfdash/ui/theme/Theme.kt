package io.github.toserk1024.cfdash.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 极客风黑白灰 · 浅色
private val LightColorScheme = lightColorScheme(
    primary = GeeksBlack,
    onPrimary = GeeksWhite,
    primaryContainer = GeeksLightGray,
    onPrimaryContainer = GeeksBlack,
    secondary = GeeksDarkGray,
    onSecondary = GeeksWhite,
    secondaryContainer = GeeksLightGray,
    onSecondaryContainer = GeeksBlack,
    tertiary = GeeksGray,
    onTertiary = GeeksWhite,
    background = GeeksWhite,
    onBackground = GeeksBlack,
    surface = GeeksWhite,
    onSurface = GeeksBlack,
    surfaceVariant = GeeksLightGray,
    onSurfaceVariant = GeeksDarkGray,
    outline = GeeksGray,
    error = Color(0xFFB3261E),
    onError = GeeksWhite
)

// 极客风黑白灰 · 深色（背景 OLED 纯黑）
private val DarkColorScheme = darkColorScheme(
    primary = GeeksWhite,
    onPrimary = GeeksBlack,
    primaryContainer = GeeksDarkGray,
    onPrimaryContainer = GeeksWhite,
    secondary = GeeksLightGray,
    onSecondary = GeeksBlack,
    secondaryContainer = GeeksDarkGray,
    onSecondaryContainer = GeeksWhite,
    tertiary = GeeksGray,
    onTertiary = GeeksBlack,
    background = GeeksBlack,
    onBackground = GeeksWhite,
    surface = GeeksBlack,
    onSurface = GeeksWhite,
    surfaceVariant = GeeksDarkGray,
    onSurfaceVariant = GeeksLightGray,
    outline = GeeksGray,
    error = Color(0xFFCF6679),
    onError = GeeksBlack
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}