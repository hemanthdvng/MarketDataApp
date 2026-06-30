package com.marketdata.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marketdata.app.data.models.LiveQuoteDisplay
import com.marketdata.app.data.prefs.SecurePrefs
import com.marketdata.app.data.repository.KiteRepository
import com.marketdata.app.util.CsvWriter
import com.marketdata.app.util.NiftySymbols
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LiveQuoteState(
    val symbolInput: String = "",
    val quotes: List<LiveQuoteDisplay> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val showNifty50: Boolean = false
)

class LiveQuotesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = KiteRepository(app)
    private val _state = MutableStateFlow(LiveQuoteState())
    val state = _state.asStateFlow()

    fun setSymbolInput(s: String) = update { copy(symbolInput = s.uppercase()) }

    fun loadNifty50() {
        update { copy(symbolInput = NiftySymbols.NIFTY_50.joinToString(","), showNifty50 = true) }
        fetchQuotes()
    }

    fun fetchQuotes() {
        val symbols = _state.value.symbolInput
            .split(",", " ", "\n")
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }

        if (symbols.isEmpty()) {
            update { copy(error = "Enter at least one symbol") }
            return
        }

        viewModelScope.launch {
            update { copy(isLoading = true, error = null) }

            // Batch in groups of 50 (Kite limit per request)
            val allQuotes = mutableListOf<LiveQuoteDisplay>()
            val batches = symbols.chunked(50)

            for (batch in batches) {
                val result = repo.fetchQuotes(batch)
                result.onSuccess { allQuotes.addAll(it) }
                result.onFailure { e ->
                    update { copy(isLoading = false, error = e.message) }
                    return@launch
                }
            }

            update {
                copy(
                    isLoading = false,
                    quotes = allQuotes.sortedBy { it.symbol }
                )
            }
        }
    }

    fun saveToCSV(folderUri: Uri) {
        val quotes = _state.value.quotes
        if (quotes.isEmpty()) return

        viewModelScope.launch {
            try {
                val fileName = CsvWriter.generateQuoteFileName()
                val docFile = androidx.documentfile.provider.DocumentFile
                    .fromTreeUri(getApplication(), folderUri)
                    ?.createFile("text/csv", fileName)

                docFile?.uri?.let { uri ->
                    CsvWriter.writeQuotesToUri(getApplication(), uri, quotes)
                    update { copy(savedMessage = "Saved $fileName") }
                }
            } catch (e: Exception) {
                update { copy(error = "Save failed: ${e.message}") }
            }
        }
    }

    fun clearError() = update { copy(error = null, savedMessage = null) }

    private fun update(block: LiveQuoteState.() -> LiveQuoteState) {
        _state.value = _state.value.block()
    }
}
