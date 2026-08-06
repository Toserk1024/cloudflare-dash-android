package io.github.toserk1024.cfdash.data.model

import kotlinx.serialization.Serializable

/** Cloudflare 用户信息 */
@Serializable
data class User(
    val id: String = "",
    val email: String = "",
    val username: String = "",
    val first_name: String? = null,
    val last_name: String? = null,
    val country: String? = null,
    val two_factor_authentication_enabled: Boolean = false,
    val created_on: String? = null,
    val modified_on: String? = null,
    val api_support: Boolean = false
)
