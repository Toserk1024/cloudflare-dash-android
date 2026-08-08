package io.github.toserk1024.cfdash.data.api

/** Cloudflare 认证凭据（支持两种方式） */
sealed interface AuthCredential {
    /** API Token（Authorization: Bearer） */
    data class Token(val value: String) : AuthCredential

    /** Global API Key（X-Auth-Email + X-Auth-Key） */
    data class GlobalKey(val email: String, val apiKey: String) : AuthCredential
}