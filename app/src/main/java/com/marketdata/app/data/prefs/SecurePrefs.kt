package com.marketdata.app.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePrefs(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "market_data_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var apiSecret: String
        get() = prefs.getString(KEY_API_SECRET, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_SECRET, value).apply()

    var accessToken: String
        get() = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var userId: String
        get() = prefs.getString(KEY_USER_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var claudeApiKey: String
        get() = prefs.getString(KEY_CLAUDE_API, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLAUDE_API, value).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_API, value).apply()

    var instrumentsLastUpdated: Long
        get() = prefs.getLong(KEY_INSTRUMENTS_UPDATED, 0L)
        set(value) = prefs.edit().putLong(KEY_INSTRUMENTS_UPDATED, value).apply()

    val isLoggedIn: Boolean
        get() = apiKey.isNotEmpty() && accessToken.isNotEmpty()

    fun clearSession() {
        prefs.edit().remove(KEY_ACCESS_TOKEN).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_SECRET = "api_secret"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_CLAUDE_API = "claude_api_key"
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_INSTRUMENTS_UPDATED = "instruments_last_updated"

        @Volatile private var INSTANCE: SecurePrefs? = null

        fun getInstance(context: Context): SecurePrefs =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurePrefs(context.applicationContext).also { INSTANCE = it }
            }
    }
}
