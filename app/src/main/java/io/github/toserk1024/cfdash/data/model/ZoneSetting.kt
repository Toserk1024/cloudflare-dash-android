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
