package io.github.toserk1024.cfdash.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

/**
 * 应用主题。
 *
 * 设计约定（避免"主题色叠满按钮"）：
 * - primary（品牌强调色）只用于**强调**：图标 tint、选中态、Switch/Checkbox、FAB、文字按钮；
 * - 普通实心按钮统一走 secondaryContainer（中性灰），不再用 primary 大面积填充；
 * - 深色模式背景保持纯黑（OLED 省电）。
 *
 * @param dynamicColor true = 跟随系统壁纸取色（Android 12+），此时忽略 [customPrimary]
 * @param customPrimary 自定义品牌主色；null = 默认 Cloudflare 橙
 */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    customPrimary: Color? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> brandScheme(primary = customPrimary ?: CloudflareOrange, dark = darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/** 基于品牌主色派生完整配色（自动计算 onPrimary 的黑白对比 / 容器色） */
private fun brandScheme(primary: Color, dark: Boolean): ColorScheme {
    val onPrimary = if (primary.luminance() > 0.55f) Color(0xFF141414) else Color.White
    val primaryContainer =
        if (dark) primary.copy(alpha = 0.22f).compositeOver(Color.Black)
        else primary.copy(alpha = 0.14f).compositeOver(Color.White)
    // 中性容器：给 FilledTonalButton / Chip / 选中背景使用
    val neutralContainer = if (dark) Color(0xFF2A2A2A) else Color(0xFFECECEC)
    val onNeutralContainer = if (dark) Color(0xFFE6E6E6) else Color(0xFF1C1C1C)
    val neutral = if (dark) Color(0xFFBDBDBD) else Color(0xFF5F6368)

    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = primary,
            secondary = neutral,
            onSecondary = Color(0xFF141414),
            secondaryContainer = neutralContainer,
            onSecondaryContainer = onNeutralContainer,
            tertiary = primary,
            onTertiary = onPrimary,
            tertiaryContainer = primaryContainer,
            onTertiaryContainer = primary,
            background = Color.Black,
            onBackground = Color(0xFFE6E6E6),
            surface = Color.Black,
            onSurface = Color(0xFFE6E6E6),
            surfaceVariant = Color(0xFF1F1F1F),
            onSurfaceVariant = Color(0xFFB0B0B0),
            outline = Color(0xFF444444),
            outlineVariant = Color(0xFF2C2C2C)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = primary,
            secondary = neutral,
            onSecondary = Color.White,
            secondaryContainer = neutralContainer,
            onSecondaryContainer = onNeutralContainer,
            tertiary = primary,
            onTertiary = onPrimary,
            tertiaryContainer = primaryContainer,
            onTertiaryContainer = primary,
            background = Color(0xFFFAFAFA),
            onBackground = Color(0xFF1A1A1A),
            surface = Color.White,
            onSurface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFFF1F1F1),
            onSurfaceVariant = Color(0xFF5F6368),
            outline = Color(0xFFBDBDBD),
            outlineVariant = Color(0xFFE0E0E0)
        )
    }
}