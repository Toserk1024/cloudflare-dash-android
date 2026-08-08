package io.github.toserk1024.cfdash.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.toserk1024.cfdash.data.api.AuthCredential

/** 用户账号（多用户） */
data class UserAccount(
    val id: String,
    val label: String,
    val credential: AuthCredential
)

/**
 * 认证凭据安全存储（EncryptedSharedPreferences，Android Keystore 主密钥加密）
 * 支持多用户：每个用户一组凭据（Token / Global Key），维护当前激活用户。
 */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** 保存（或更新）用户凭据并设为激活，返回用户 id */
    fun saveUser(credential: AuthCredential): String {
        val id = idOf(credential)
        prefs.edit()
            .putString("${KEY_USER_PREFIX}$id", labelOf(credential))
            .apply {
                when (credential) {
                    is AuthCredential.Token -> {
                        putString("${KEY_USER_PREFIX}$id${KEY_SUFFIX_MODE}", MODE_TOKEN)
                        putString("${KEY_USER_PREFIX}$id${KEY_SUFFIX_TOKEN}", credential.value)
                        remove("${KEY_USER_PREFIX}$id${KEY_SUFFIX_EMAIL}")
                        remove("${KEY_USER_PREFIX}$id${KEY_SUFFIX_KEY}")
                    }
                    is AuthCredential.GlobalKey -> {
                        putString("${KEY_USER_PREFIX}$id${KEY_SUFFIX_MODE}", MODE_GLOBAL)
                        putString("${KEY_USER_PREFIX}$id${KEY_SUFFIX_EMAIL}", credential.email)
                        putString("${KEY_USER_PREFIX}$id${KEY_SUFFIX_KEY}", credential.apiKey)
                        remove("${KEY_USER_PREFIX}$id${KEY_SUFFIX_TOKEN}")
                    }
                }
                putString(KEY_ACTIVE_USER, id)
            }
            .apply()
        return id
    }

    /** 全部已保存用户 */
    fun getUsers(): List<UserAccount> =
        prefs.all.keys
            .filter { it.startsWith(KEY_USER_PREFIX) && it.endsWith(KEY_SUFFIX_MODE) }
            .mapNotNull { key ->
                val id = key.removePrefix(KEY_USER_PREFIX).removeSuffix(KEY_SUFFIX_MODE)
                readCredential(id)?.let { cred ->
                    UserAccount(id, prefs.getString("${KEY_USER_PREFIX}$id", null) ?: id, cred)
                }
            }

    /** 当前激活用户（无则 null） */
    fun getActiveUser(): UserAccount? {
        val id = prefs.getString(KEY_ACTIVE_USER, null) ?: return null
        return readCredential(id)?.let { cred ->
            UserAccount(id, prefs.getString("${KEY_USER_PREFIX}$id", null) ?: id, cred)
        }
    }

    /** 当前激活用户凭据（CloudflareClient 认证用） */
    fun getCredential(): AuthCredential? = getActiveUser()?.credential

    fun setActiveUser(id: String) {
        prefs.edit().putString(KEY_ACTIVE_USER, id).apply()
    }

    fun hasCredential(): Boolean = getUsers().isNotEmpty()

    /** 删除指定用户；若删除的是激活用户，自动切换到剩余第一个用户 */
    fun deleteUser(id: String) {
        prefs.edit()
            .remove("${KEY_USER_PREFIX}$id")
            .remove("${KEY_USER_PREFIX}$id${KEY_SUFFIX_MODE}")
            .remove("${KEY_USER_PREFIX}$id${KEY_SUFFIX_TOKEN}")
            .remove("${KEY_USER_PREFIX}$id${KEY_SUFFIX_EMAIL}")
            .remove("${KEY_USER_PREFIX}$id${KEY_SUFFIX_KEY}")
            .apply()
        if (prefs.getString(KEY_ACTIVE_USER, null) == id) {
            val next = getUsers().firstOrNull()?.id
            prefs.edit().putString(KEY_ACTIVE_USER, next).apply()
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun readCredential(id: String): AuthCredential? =
        when (prefs.getString("${KEY_USER_PREFIX}$id${KEY_SUFFIX_MODE}", null)) {
            MODE_TOKEN -> prefs.getString("${KEY_USER_PREFIX}$id${KEY_SUFFIX_TOKEN}", null)
                ?.takeIf { it.isNotBlank() }?.let { AuthCredential.Token(it) }
            MODE_GLOBAL -> {
                val email = prefs.getString("${KEY_USER_PREFIX}$id${KEY_SUFFIX_EMAIL}", null)
                val key = prefs.getString("${KEY_USER_PREFIX}$id${KEY_SUFFIX_KEY}", null)
                if (!email.isNullOrBlank() && !key.isNullOrBlank()) {
                    AuthCredential.GlobalKey(email, key)
                } else null
            }
            else -> null
        }

    /** 用户唯一 id：GlobalKey 用邮箱（小写）；Token 用内容 hash */
    private fun idOf(credential: AuthCredential): String = when (credential) {
        is AuthCredential.Token -> "tok_${credential.value.hashCode()}"
        is AuthCredential.GlobalKey -> "eml_${credential.email.lowercase()}"
    }

    private fun labelOf(credential: AuthCredential): String = when (credential) {
        is AuthCredential.Token -> "Token 用户"
        is AuthCredential.GlobalKey -> credential.email
    }

    companion object {
        private const val FILE_NAME = "cf_secure_prefs"
        private const val KEY_ACTIVE_USER = "cf_active_user"
        private const val KEY_USER_PREFIX = "cf_user_"
        private const val KEY_SUFFIX_MODE = "_mode"
        private const val KEY_SUFFIX_TOKEN = "_token"
        private const val KEY_SUFFIX_EMAIL = "_email"
        private const val KEY_SUFFIX_KEY = "_key"
        private const val MODE_TOKEN = "token"
        private const val MODE_GLOBAL = "global"
    }
}