package io.github.toserk1024.cfdash.data.model

import kotlinx.serialization.Serializable

/** Cloudflare Zone（域名） */
@Serializable
data class Zone(
    val id: String,
    val name: String,
    val status: String = "",
    val paused: Boolean = false,
    val type: String = "full",
    val name_servers: List<String> = emptyList(),
    val original_name_servers: List<String> = emptyList(),
    val original_registrar: String? = null,
    val created_on: String? = null,
    val modified_on: String? = null,
    val activated_on: String? = null,
    val development_mode: Long = 0,
    val plan: ZonePlan? = null,
    val permissions: List<String> = emptyList()
) {
    val isActive: Boolean get() = status.equals("active", ignoreCase = true)
}

@Serializable
data class ZonePlan(
    val id: String? = null,
    val name: String = "",
    val price: Long = 0,
    val currency: String = "USD",
    val frequency: String = "",
    val is_subscribed: Boolean = false,
    val can_subscribe: Boolean = false,
    val legacy_id: String? = null,
    val is_deprecated: Boolean = false
)
