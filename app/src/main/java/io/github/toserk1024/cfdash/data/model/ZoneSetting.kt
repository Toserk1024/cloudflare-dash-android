package io.github.toserk1024.cfdash.data.model

import kotlinx.serialization.Serializable

/** Zone 设置项（/zones/{id}/settings/{name} 通用响应） */
@Serializable
data class ZoneSetting(
    val id: String = "",
    val value: String = "",
    val editable: Boolean = true,
    val time_remaining: Long = 0
)

/** Zone 设置更新请求体（PATCH {"value": ...}） */
@Serializable
data class ZoneSettingRequest(
    val value: String
)

// ===== NEL（网络错误记录）特殊设置 =====
// 与普通 zone setting 不同，nel 的 value 是对象 {"enabled": bool}，而非 on/off 字符串，需单独模型。

/** NEL 设置值对象（value.enabled = true/false） */
@Serializable
data class NelValue(val enabled: Boolean = false)

/** NEL 设置响应（GET /zones/{id}/settings/nel） */
@Serializable
data class NelSetting(
    val id: String = "",
    val value: NelValue = NelValue()
)

/** NEL 设置更新请求体（PATCH {"value": {"enabled": ...}}） */
@Serializable
data class NelSettingRequest(
    val value: NelValue
)
