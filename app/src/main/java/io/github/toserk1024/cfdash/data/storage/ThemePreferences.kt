package io.github.toserk1024.cfdash.data.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 主题偏好（非敏感，明文 SharedPreferences 即可）。
 * - [ThemeConfig.dynamicColor]：true = 跟随系统壁纸取色（Android 12+）；false = 使用自定义主色
 * - [ThemeConfig.colorHex]：自定义主色的 hex 字符串（#RRGGBB）
 * 以 StateFlow 暴露，供 Compose 直接订阅并即时生效。
 */
object ThemePreferences {

    const val DEFAULT_COLOR_HEX = "#F6821F"

    /** 备选主题色（hex 到 名称） */
    val PRESET_COLORS: List<Pair<String, String>> = listOf(
        "#F6821F" to "Cloudflare 橙",
        "#0051C3" to "蓝",
        "#27AE60" to "绿",
        "#9B51E0" to "紫",
        "#EB5757" to "红",
        "#607D8B" to "蓝灰"
    )

    data class ThemeConfig(
        val dynamicColor: Boolean = false,
        val colorHex: String = DEFAULT_COLOR_HEX
    )

    private const val FILE_NAME = "cf_theme_prefs"
    private const val KEY_DYNAMIC = "dynamic_color"
    private const val KEY_COLOR = "custom_color_hex"

    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(ThemeConfig())
    val state: StateFlow<ThemeConfig> = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        prefs = p
        _state.value = ThemeConfig(
            dynamicColor = p.getBoolean(KEY_DYNAMIC, false),
            colorHex = normalize(p.getString(KEY_COLOR, DEFAULT_COLOR_HEX)) ?: DEFAULT_COLOR_HEX
        )
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_DYNAMIC, enabled)?.apply()
        _state.update { it.copy(dynamicColor = enabled) }
    }

    /** 设置自定义主色（非法 hex 会被忽略） */
    fun setColorHex(hex: String) {
        val normalized = normalize(hex) ?: return
        prefs?.edit()?.putString(KEY_COLOR, normalized)?.apply()
        _state.update { it.copy(colorHex = normalized) }
    }

    /** 校验并规范化 hex：接受 `#RRGGBB` 或 `RRGGBB`，返回大写 `#RRGGBB`；非法返回 null */
    fun normalize(input: String): String? {
        val text = input.trim().removePrefix("#")
        if (!text.matches(Regex("^[0-9a-fA-F]{6}$"))) return null
        return "#" + text.uppercase()
    }

    /** 转 ARGB（0xFFRRGGBB），非法返回 null */
    fun toArgb(hex: String): Long? {
        val normalized = normalize(hex) ?: return null
        return normalized.removePrefix("#").toLong(16) or 0xFF000000L
    }
}
