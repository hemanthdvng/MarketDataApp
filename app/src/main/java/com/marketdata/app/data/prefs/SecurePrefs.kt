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

    var derivativeInstrumentsLastUpdated: Long
        get() = prefs.getLong(KEY_DERIV_INSTRUMENTS_UPDATED, 0L)
        set(value) = prefs.edit().putLong(KEY_DERIV_INSTRUMENTS_UPDATED, value).apply()

    /** User-editable text appended to the agent's built-in system prompt.
     *  Lets the user steer tone/focus (e.g. "prefer options strategies",
     *  "keep answers under 100 words") without losing the base market-analyst framing. */
    var agentCustomInstructions: String
        get() = prefs.getString(KEY_AGENT_INSTRUCTIONS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AGENT_INSTRUCTIONS, value).apply()

    // --- Background pattern scanner config. Snapshotted from the Scanner
    // screen's own settings when "Run in background" is switched on, so
    // PatternScanService (which has no UI / ViewModel of its own) can read
    // what to scan without needing a live screen reference. ---
    var scannerBgRunning: Boolean
        get() = prefs.getBoolean(KEY_SCANNER_BG_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_SCANNER_BG_RUNNING, value).apply()

    var scannerBgIntervalSeconds: Int
        get() = prefs.getInt(KEY_SCANNER_BG_POLL_SECS, 60)
        set(value) = prefs.edit().putInt(KEY_SCANNER_BG_POLL_SECS, value).apply()

    var scannerBgSelectionType: String
        get() = prefs.getString(KEY_SCANNER_BG_SEL_TYPE, "NIFTY50") ?: "NIFTY50"
        set(value) = prefs.edit().putString(KEY_SCANNER_BG_SEL_TYPE, value).apply()

    var scannerBgSymbols: String
        get() = prefs.getString(KEY_SCANNER_BG_SYMBOLS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SCANNER_BG_SYMBOLS, value).apply()

    var scannerBgIntervalIndex: Int
        get() = prefs.getInt(KEY_SCANNER_BG_INTERVAL_IDX, 7)
        set(value) = prefs.edit().putInt(KEY_SCANNER_BG_INTERVAL_IDX, value).apply()

    var scannerBgLookbackDays: Int
        get() = prefs.getInt(KEY_SCANNER_BG_LOOKBACK, 150)
        set(value) = prefs.edit().putInt(KEY_SCANNER_BG_LOOKBACK, value).apply()

    var scannerBgSessionOnly: Boolean
        get() = prefs.getBoolean(KEY_SCANNER_BG_SESSION_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_SCANNER_BG_SESSION_ONLY, value).apply()

    var scannerBgThresholdFactor: Float
        get() = prefs.getFloat(KEY_SCANNER_BG_THRESHOLD, 0.15f)
        set(value) = prefs.edit().putFloat(KEY_SCANNER_BG_THRESHOLD, value).apply()

    var scannerBgMinOccurrences: Int
        get() = prefs.getInt(KEY_SCANNER_BG_MIN_OCC, 12)
        set(value) = prefs.edit().putInt(KEY_SCANNER_BG_MIN_OCC, value).apply()

    var scannerBgMinAccuracy: Float
        get() = prefs.getFloat(KEY_SCANNER_BG_MIN_ACC, 0.55f)
        set(value) = prefs.edit().putFloat(KEY_SCANNER_BG_MIN_ACC, value).apply()

    /** Comma-joined "symbol|patternId|direction" keys the service already
     *  notified about, so it only notifies again for genuinely NEW hits
     *  instead of re-notifying the same still-active signal every cycle. */
    var scannerBgSeenSignals: String
        get() = prefs.getString(KEY_SCANNER_BG_SEEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SCANNER_BG_SEEN, value).apply()

    var scannerBgLastRunLabel: String
        get() = prefs.getString(KEY_SCANNER_BG_LAST_RUN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SCANNER_BG_LAST_RUN, value).apply()

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
        private const val KEY_DERIV_INSTRUMENTS_UPDATED = "derivative_instruments_last_updated"
        private const val KEY_AGENT_INSTRUCTIONS = "agent_custom_instructions"
        private const val KEY_SCANNER_BG_RUNNING = "scanner_bg_running"
        private const val KEY_SCANNER_BG_POLL_SECS = "scanner_bg_poll_secs"
        private const val KEY_SCANNER_BG_SEL_TYPE = "scanner_bg_sel_type"
        private const val KEY_SCANNER_BG_SYMBOLS = "scanner_bg_symbols"
        private const val KEY_SCANNER_BG_INTERVAL_IDX = "scanner_bg_interval_idx"
        private const val KEY_SCANNER_BG_LOOKBACK = "scanner_bg_lookback"
        private const val KEY_SCANNER_BG_SESSION_ONLY = "scanner_bg_session_only"
        private const val KEY_SCANNER_BG_THRESHOLD = "scanner_bg_threshold"
        private const val KEY_SCANNER_BG_MIN_OCC = "scanner_bg_min_occ"
        private const val KEY_SCANNER_BG_MIN_ACC = "scanner_bg_min_acc"
        private const val KEY_SCANNER_BG_SEEN = "scanner_bg_seen_signals"
        private const val KEY_SCANNER_BG_LAST_RUN = "scanner_bg_last_run"

        @Volatile private var INSTANCE: SecurePrefs? = null

        fun getInstance(context: Context): SecurePrefs =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurePrefs(context.applicationContext).also { INSTANCE = it }
            }
    }
}
