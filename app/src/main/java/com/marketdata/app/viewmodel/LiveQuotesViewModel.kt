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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Selectable auto-refresh intervals for the Live Quotes screen, in seconds. */
val LIVE_REFRESH_INTERVALS = listOf(5, 10, 15, 30, 60)

data class LiveQuoteState(
    val symbolInput: String = "",
    val quotes: List<LiveQuoteDisplay> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val showNifty50: Boolean = false,
    val autoRefreshEnabled: Boolean = false,
    val refreshIntervalSeconds: Int = 10,
    val lastUpdatedAt: Long = 0L,
    val isLoadingAllInstruments: Boolean = false,
    val allInstrumentsProgress: String = ""
)

class LiveQuotesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = KiteRepository(app)
    private val _state = MutableStateFlow(LiveQuoteState())
    val state = _state.asStateFlow()

    private var pollingJob: Job? = null

    fun setSymbolInput(s: String) = update { copy(symbolInput = s.uppercase()) }

    fun loadNifty50() {
        update { copy(symbolInput = NiftySymbols.NIFTY_50.joinToString(","), showNifty50 = true) }
        fetchQuotes()
    }

    fun loadNifty100() {
        update { copy(symbolInput = NiftySymbols.NIFTY_100.joinToString(","), showNifty50 = true) }
        fetchQuotes()
    }

    /** Loads every NSE-listed equity's live quote, not just Nifty 50/100.
     *  Uses fetchQuotesBatched() since 1500-2000+ symbols can't go in one
     *  request the way Nifty 100 can - runs as its own flow (rather than
     *  reusing fetchQuotesOnce/symbolInput) so progress can be shown while
     *  it works through the batches. */
    fun loadAllInstruments() {
        viewModelScope.launch {
            update { copy(isLoadingAllInstruments = true, error = null, isLoading = true) }
            try {
                if (repo.instrumentsNeedRefresh()) repo.refreshInstruments()
                val symbols = repo.getAllNseEquitySymbols()
                if (symbols.isEmpty()) {
                    update { copy(isLoadingAllInstruments = false, isLoading = false, error = "No instruments found - try refreshing instruments") }
                    return@launch
                }
                update { copy(symbolInput = symbols.joinToString(","), showNifty50 = true) }
                val result = repo.fetchQuotesBatched(symbols) { done, total ->
                    update { copy(allInstrumentsProgress = "Fetching batch $done/$total...") }
                }
                result.onSuccess { q ->
                    update { copy(quotes = q, isLoading = false, isLoadingAllInstruments = false, allInstrumentsProgress = "", lastUpdatedAt = System.currentTimeMillis()) }
                }
                result.onFailure { e ->
                    update { copy(isLoading = false, isLoadingAllInstruments = false, allInstrumentsProgress = "", error = e.message ?: "Failed to fetch quotes") }
                }
            } catch (e: Exception) {
                update { copy(isLoading = false, isLoadingAllInstruments = false, allInstrumentsProgress = "", error = e.message ?: "Failed to load instrument list") }
            }
        }
    }

    fun fetchQuotes() {
        viewModelScope.launch { fetchQuotesOnce() }
    }

    /** Turns the periodic auto-refresh loop on/off. While on, quotes are
     *  re-fetched every [LiveQuoteState.refreshIntervalSeconds] using whatever
     *  symbols are currently in the input field. */
    fun setAutoRefresh(enabled: Boolean) {
        update { copy(autoRefreshEnabled = enabled) }
        if (enabled) startPolling() else stopPolling()
    }

    fun setRefreshInterval(seconds: Int) {
        update { copy(refreshIntervalSeconds = seconds) }
        if (_state.value.autoRefreshEnabled) startPolling() // restart with the new interval
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                fetchQuotesOnce()
                delay(_state.value.refreshIntervalSeconds * 1000L)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun fetchQuotesOnce() {
        val symbols = _state.value.symbolInput
            .split(",", " ", "\n")
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }

        if (symbols.isEmpty()) {
            update { copy(error = "Enter at least one symbol") }
            return
        }

        update { copy(isLoading = true, error = null) }

        // Batch in groups of 50 (Kite limit per request)
        val allQuotes = mutableListOf<LiveQuoteDisplay>()
        val batches = symbols.chunked(50)

        for (batch in batches) {
            val result = repo.fetchQuotes(batch)
            result.onSuccess { allQuotes.addAll(it) }
            result.onFailure { e ->
                update { copy(isLoading = false, error = e.message) }
                return
            }
        }

        update {
            copy(
                isLoading = false,
                quotes = allQuotes.sortedBy { it.symbol },
                lastUpdatedAt = System.currentTimeMillis()
            )
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

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }

    private fun update(block: LiveQuoteState.() -> LiveQuoteState) {
        _state.value = _state.value.block()
    }
}
