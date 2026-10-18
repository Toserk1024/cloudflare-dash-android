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
 * 应用主题：**以 Material 3 标准为主**，只注入品牌主色。
 *
 * 约定：
 * - 除 `primary / onPrimary / primaryContainer / onPrimaryContainer` 外的所有颜色角色，
 *   全部使用 Material 3 默认派生（不自定义 secondary/tertiary/neutral），
 *   因此按钮、Chip 等组件保持 MD 默认观感（实心按钮 = primary 填充）。
 * - 深色模式背景/表面强制纯黑（OLED）；跟随系统动态取色时同样保留纯黑。
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
            if (darkTheme) {
                // 动态取色只接管主色系，深色底仍保持 OLED 纯黑
                dynamicDarkColorScheme(context).copy(
                    background = Color.Black,
                    surface = Color.Black
                )
            } else {
                dynamicLightColorScheme(context)
            }
        }

        else -> brandScheme(primary = customPrimary ?: CloudflareOrange, dark = darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/** 只覆盖品牌主色相关的 4 个角色，其余沿用 Material 3 默认值 */
private fun brandScheme(primary: Color, dark: Boolean): ColorScheme {
    val onPrimary = if (primary.luminance() > 0.55f) Color(0xFF141414) else Color.White
    val primaryContainer =
        if (dark) primary.copy(alpha = 0.26f).compositeOver(Color.Black)
        else primary.copy(alpha = 0.14f).compositeOver(Color.White)

    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimary,
            // OLED：深色底纯黑
            background = Color.Black,
            surface = Color.Black
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimary
        )
    }
}