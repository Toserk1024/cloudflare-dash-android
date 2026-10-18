package io.github.toserk1024.cfdash.data.storage

import io.github.toserk1024.cfdash.data.api.AuthCredential

/**
 * 账号唯一 id 生成（纯函数，便于单元测试）。
 *
 * ⚠️ 算法**必须与历史版本保持一致**，否则已保存用户的加密存储 key 会变化，等同于"账号全部丢失"：
 * - Token：`tok_<内容 hashCode>`
 * - Global API Key：`eml_<邮箱小写>`
 */
object AccountId {

    fun of(credential: AuthCredential): String = when (credential) {
        is AuthCredential.Token -> "tok_${credential.value.hashCode()}"
        is AuthCredential.GlobalKey -> "eml_${credential.email.lowercase()}"
    }

    /** 账号展示名（Token 无法得知邮箱，统一占位；Global Key 用邮箱） */
    fun labelOf(credential: AuthCredential): String = when (credential) {
        is AuthCredential.Token -> "Token 用户"
        is AuthCredential.GlobalKey -> credential.email
    }
}
