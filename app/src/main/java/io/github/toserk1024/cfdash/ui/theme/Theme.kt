package io.github.toserk1024.cfdash.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.rememberDynamicColorScheme

/**
 * 应用主题：**完全按 Material 3 标准**，由种子色（seed）派生整套配色。
 *
 * - 不再手写 ColorScheme 角色：`primary / secondary / tertiary / neutral / neutralVariant`
 *   全部由 material-kolor（Material Color Utilities）从 seed 生成，等同 Material Theme Builder 产物；
 * - 文本与图标遵循 MD3 语义（`onSurface` / `onSurfaceVariant`），**不会**被品牌主色刷满；
 * - `isAmoled = true`：深色模式使用 AMOLED 纯黑方案（OLED 省电），浅色保持标准 MD3 浅色；
 * - 动态配色（Android 12+）与自定义种子色走**同一条生成管线**，只是 seed 来源不同。
 *
 * @param dynamicColor true = 跟随系统壁纸取色（以系统 primary 作为 seed）
 * @param seedColor 自定义种子色（默认 Cloudflare 橙）
 */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    seedColor: Color = CloudflareOrange,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // 动态配色：取系统方案的主色作为 seed（保持 MD3 生成管线一致，同时可获得 AMOLED 纯黑）
    val systemScheme: ColorScheme? = if (dynamicColor && supportsDynamic) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }
    val effectiveSeed = systemScheme?.primary ?: seedColor

    val colorScheme = rememberDynamicColorScheme(
        seedColor = effectiveSeed,
        isDark = darkTheme,
        isAmoled = darkTheme
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}