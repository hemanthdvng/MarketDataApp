package com.marketdata.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marketdata.app.data.prefs.SecurePrefs
import com.marketdata.app.data.repository.KiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthState(
    val apiKey: String = "",
    val apiSecret: String = "",
    val isLoggedIn: Boolean = false,
    val userName: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = SecurePrefs.getInstance(app)
    private val repo = KiteRepository(app)

    private val _state = MutableStateFlow(
        AuthState(
            apiKey = prefs.apiKey,
            apiSecret = prefs.apiSecret,
            isLoggedIn = prefs.isLoggedIn,
            userName = prefs.userName
        )
    )
    val state = _state.asStateFlow()

    fun updateApiKey(key: String) {
        _state.value = _state.value.copy(apiKey = key)
        prefs.apiKey = key
    }

    fun updateApiSecret(secret: String) {
        _state.value = _state.value.copy(apiSecret = secret)
        prefs.apiSecret = secret
    }

    fun handleRequestToken(requestToken: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = repo.createSession(requestToken)
            result.fold(
                onSuccess = { session ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        userName = session.user_name,
                        successMessage = "Logged in as ${session.user_name}"
                    )
                    // Trigger instrument refresh
                    repo.refreshInstruments()
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Login failed"
                    )
                }
            )
        }
    }

    fun logout() {
        prefs.clearSession()
        _state.value = AuthState(
            apiKey = prefs.apiKey,
            apiSecret = prefs.apiSecret
        )
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null, successMessage = null)
    }

    fun updateClaudeKey(key: String) { prefs.claudeApiKey = key }
    fun updateGeminiKey(key: String) { prefs.geminiApiKey = key }
    fun getClaudeKey() = prefs.claudeApiKey
    fun getGeminiKey() = prefs.geminiApiKey
}
