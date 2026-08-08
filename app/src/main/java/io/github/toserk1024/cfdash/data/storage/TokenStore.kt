package io.github.toserk1024.cfdash.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.toserk1024.cfdash.data.api.AuthCredential

/**
 * 认证凭据安全存储（EncryptedSharedPreferences，Android Keystore 主密钥加密）
 * 支持两种方式：API Token / Global API Key（email + key）。
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

    /** 保存 API Token（authMode=token） */
    fun saveToken(token: String) {
        prefs.edit()
            .putString(KEY_AUTH_MODE, MODE_TOKEN)
            .putString(KEY_TOKEN, token.trim())
            .apply()
    }

    /** 保存 Global API Key（authMode=global） */
    fun saveGlobalKey(email: String, apiKey: String) {
        prefs.edit()
            .putString(KEY_AUTH_MODE, MODE_GLOBAL)
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_GLOBAL_KEY, apiKey.trim())
            .apply()
    }

    /** 读取当前认证凭据（未配置返回 null） */
    fun getCredential(): AuthCredential? = when (prefs.getString(KEY_AUTH_MODE, null)) {
        MODE_TOKEN -> getToken()?.takeIf { it.isNotBlank() }?.let { AuthCredential.Token(it) }
        MODE_GLOBAL -> {
            val email = prefs.getString(KEY_EMAIL, null)
            val key = prefs.getString(KEY_GLOBAL_KEY, null)
            if (!email.isNullOrBlank() && !key.isNullOrBlank()) {
                AuthCredential.GlobalKey(email, key)
            } else null
        }
        else -> null
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun hasCredential(): Boolean = getCredential() != null

    fun hasToken(): Boolean = !getToken().isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE_NAME = "cf_secure_prefs"
        private const val KEY_AUTH_MODE = "cf_auth_mode"
        private const val MODE_TOKEN = "token"
        private const val MODE_GLOBAL = "global"
        private const val KEY_TOKEN = "cf_api_token"
        private const val KEY_EMAIL = "cf_email"
        private const val KEY_GLOBAL_KEY = "cf_global_key"
    }
}