package com.marketdata.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marketdata.app.data.models.AgentMessage
import com.marketdata.app.data.models.AiModelOption
import com.marketdata.app.data.models.AiModels
import com.marketdata.app.data.models.LiveQuoteDisplay
import com.marketdata.app.data.prefs.SecurePrefs
import com.marketdata.app.data.repository.AgentRepository
import com.marketdata.app.data.repository.KiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AgentState(
    val messages: List<AgentMessage> = emptyList(),
    val inputText: String = "",
    val selectedModel: AiModelOption = AiModels.DEFAULT,
    val isLoading: Boolean = false,
    val error: String? = null,
    val autoFetchQuotes: Boolean = true,
    val liveQuotes: List<LiveQuoteDisplay> = emptyList(),
    val fetchingQuotes: Boolean = false,
    val symbolsForContext: String = "NIFTY 50,NIFTY BANK,RELIANCE,TCS,HDFCBANK,INFY,ICICIBANK"
)

class AgentViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = SecurePrefs.getInstance(app)
    private val agentRepo = AgentRepository(prefs)
    private val kiteRepo = KiteRepository(app)

    private val _state = MutableStateFlow(AgentState())
    val state = _state.asStateFlow()

    fun setInputText(t: String) = update { copy(inputText = t) }
    fun setModel(m: AiModelOption) = update { copy(selectedModel = m) }
    fun setAutoFetch(v: Boolean) = update { copy(autoFetchQuotes = v) }
    fun setContextSymbols(s: String) = update { copy(symbolsForContext = s.uppercase()) }
    fun clearError() = update { copy(error = null) }
    fun clearChat() = update { copy(messages = emptyList(), liveQuotes = emptyList()) }

    fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty()) return

        val model = _state.value.selectedModel
        val hasKey = when (model.provider) {
            com.marketdata.app.data.models.AiProvider.CLAUDE -> prefs.claudeApiKey.isNotEmpty()
            com.marketdata.app.data.models.AiProvider.GEMINI -> prefs.geminiApiKey.isNotEmpty()
        }
        if (!hasKey) {
            update { copy(error = "No ${model.provider} API key set. Add it on the Login tab.") }
            return
        }

        val userMsg = AgentMessage(role = "user", content = text)
        update { copy(messages = messages + userMsg, inputText = "", isLoading = true, error = null) }

        viewModelScope.launch {
            var quotes = _state.value.liveQuotes

            // Auto-fetch live quotes if enabled
            if (_state.value.autoFetchQuotes && quotes.isEmpty()) {
                update { copy(fetchingQuotes = true) }
                val symbols = _state.value.symbolsForContext
                    .split(",").map { it.trim() }.filter { it.isNotBlank() }

                val result = kiteRepo.fetchQuotes(symbols)
                result.onSuccess { q ->
                    quotes = q
                    update { copy(liveQuotes = q, fetchingQuotes = false) }
                }
                result.onFailure {
                    update { copy(fetchingQuotes = false) }
                }
            }

            val result = agentRepo.call(
                model = _state.value.selectedModel,
                messages = _state.value.messages,
                liveQuotes = quotes.ifEmpty { null }
            )

            result.fold(
                onSuccess = { response ->
                    val assistantMsg = AgentMessage(role = "assistant", content = response)
                    update { copy(messages = messages + assistantMsg, isLoading = false) }
                },
                onFailure = { e ->
                    update { copy(isLoading = false, error = e.message ?: "AI error") }
                }
            )
        }
    }

    fun refreshQuotes() {
        viewModelScope.launch {
            update { copy(fetchingQuotes = true) }
            val symbols = _state.value.symbolsForContext
                .split(",").map { it.trim() }.filter { it.isNotBlank() }
            val result = kiteRepo.fetchQuotes(symbols)
            result.onSuccess { q -> update { copy(liveQuotes = q, fetchingQuotes = false) } }
            result.onFailure { update { copy(fetchingQuotes = false) } }
        }
    }

    private fun update(block: AgentState.() -> AgentState) {
        _state.value = _state.value.block()
    }
}
