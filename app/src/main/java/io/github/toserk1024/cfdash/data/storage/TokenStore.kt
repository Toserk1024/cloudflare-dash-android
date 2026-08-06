package io.github.toserk1024.cfdash.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * API Token 安全存储（EncryptedSharedPreferences）
 * 使用 Android Keystore 主密钥加密，Token 不会明文落盘。
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

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun hasToken(): Boolean = !getToken().isNullOrBlank()

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    companion object {
        private const val FILE_NAME = "cf_secure_prefs"
        private const val KEY_TOKEN = "cf_api_token"
    }
}