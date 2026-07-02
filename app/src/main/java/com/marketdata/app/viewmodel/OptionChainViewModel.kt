package com.marketdata.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marketdata.app.data.models.OptionChainRow
import com.marketdata.app.data.repository.KiteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

val OPTION_CHAIN_UNDERLYINGS = listOf("NIFTY", "BANKNIFTY", "FINNIFTY", "RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK")
val OPTION_CHAIN_SPREAD_COUNTS = listOf(5, 10, 15, 20)
val OPTION_CHAIN_REFRESH_INTERVALS = listOf(10, 15, 30, 60)

/** Maps an F&O "name" (Kite's underlying identifier) to the NSE spot symbol
 *  to pull LTP from, for the indices where the two differ. Single-stock
 *  option underlyings equal their own equity tradingsymbol, so anything not
 *  listed here is used as-is. Double-check FINNIFTY/MIDCPNIFTY's exact
 *  current spot symbol if you use them - not independently re-verified. */
val OPTION_CHAIN_SPOT_SYMBOL = mapOf(
    "NIFTY" to "NIFTY 50",
    "BANKNIFTY" to "NIFTY BANK",
    "FINNIFTY" to "NIFTY FIN SERVICE",
    "MIDCPNIFTY" to "NIFTY MID SELECT"
)

data class OptionChainState(
    val underlying: String = "NIFTY",
    val underlyingInput: String = "",
    val expiries: List<String> = emptyList(),
    val selectedExpiry: String = "",
    val spreadCount: Int = 10,
    val spotPrice: Double = 0.0,
    val rows: List<OptionChainRow> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val autoRefreshEnabled: Boolean = false,
    val refreshIntervalSeconds: Int = 15,
    val lastUpdatedLabel: String = ""
)

/**
 * Polls Kite's regular batched /quote endpoint (same one Live Quotes and the
 * Scanner already use) rather than opening a WebSocket ticker - reuses
 * proven, already-working plumbing instead of adding a new streaming
 * dependency + binary tick parsing. Trade-off: updates every N seconds
 * instead of on every tick. If you want true tick-by-tick depth later, the
 * official com.zerodhatech.kiteconnect SDK's KiteTicker (mode FULL) is the
 * natural upgrade path - deliberately not pulled in here to keep this first
 * cut buildable without adding a new third-party dependency sight-unseen.
 */
class OptionChainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = KiteRepository(app)
    private val _state = MutableStateFlow(OptionChainState())
    val state = _state.asStateFlow()

    private var pollingJob: Job? = null
    private var strikesCache: List<Double> = emptyList()
    private var symbolsCache: Map<Double, Pair<String, String>> = emptyMap() // strike -> (ceSymbol, peSymbol)

    fun setUnderlyingInput(s: String) = update { copy(underlyingInput = s.uppercase()) }

    fun selectUnderlying(name: String) {
        strikesCache = emptyList()
        symbolsCache = emptyMap()
        update {
            copy(
                underlying = name.uppercase(), underlyingInput = "",
                expiries = emptyList(), selectedExpiry = "", rows = emptyList(), spotPrice = 0.0
            )
        }
        loadExpiries()
    }

    fun setSpreadCount(n: Int) {
        update { copy(spreadCount = n) }
        viewModelScope.launch { loadChainOnce() }
    }

    fun selectExpiry(expiry: String) {
        strikesCache = emptyList()
        symbolsCache = emptyMap()
        update { copy(selectedExpiry = expiry, rows = emptyList()) }
        viewModelScope.launch { loadChainOnce() }
    }

    fun loadExpiries() {
        viewModelScope.launch {
            update { copy(isLoading = true, error = null) }
            try {
                if (repo.derivativeInstrumentsNeedRefresh()) {
                    val refresh = repo.refreshDerivativeInstruments()
                    refresh.onFailure { e ->
                        update { copy(isLoading = false, error = "Couldn't load option instrument list: ${e.message}") }
                        return@launch
                    }
                }
                val expiries = repo.getExpiriesForUnderlying(_state.value.underlying)
                if (expiries.isEmpty()) {
                    update {
                        copy(
                            isLoading = false,
                            error = "No option contracts found for ${_state.value.underlying}. Check the symbol, or that it has listed F&O contracts."
                        )
                    }
                    return@launch
                }
                update { copy(isLoading = false, expiries = expiries, selectedExpiry = expiries.first()) }
                loadChainOnce()
            } catch (e: Exception) {
                update { copy(isLoading = false, error = "Failed to load expiries: ${e.message}") }
            }
        }
    }

    fun setAutoRefresh(enabled: Boolean) {
        update { copy(autoRefreshEnabled = enabled) }
        if (enabled) startPolling() else stopPolling()
    }

    fun setRefreshInterval(seconds: Int) {
        update { copy(refreshIntervalSeconds = seconds) }
        if (_state.value.autoRefreshEnabled) startPolling()
    }

    fun refreshNow() { viewModelScope.launch { loadChainOnce() } }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(_state.value.refreshIntervalSeconds * 1000L)
                loadChainOnce()
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun loadChainOnce() {
        val s = _state.value
        if (s.selectedExpiry.isBlank()) return
        update { copy(isLoading = true, error = null) }
        try {
            if (strikesCache.isEmpty()) {
                val instruments = repo.getOptionChainInstruments(s.underlying, s.selectedExpiry)
                val byStrike = instruments.groupBy { it.strike }
                val ces = HashMap<Double, String>()
                val pes = HashMap<Double, String>()
                for ((strike, list) in byStrike) {
                    list.find { it.instrumentType == "CE" }?.let { ces[strike] = it.tradingSymbol }
                    list.find { it.instrumentType == "PE" }?.let { pes[strike] = it.tradingSymbol }
                }
                strikesCache = byStrike.keys.sorted()
                symbolsCache = strikesCache.associateWith { (ces[it] ?: "") to (pes[it] ?: "") }
            }
            if (strikesCache.isEmpty()) {
                update { copy(isLoading = false, error = "No strikes found for this expiry") }
                return
            }

            val spotSymbol = OPTION_CHAIN_SPOT_SYMBOL[s.underlying] ?: s.underlying
            val spotResult = repo.fetchQuotesRaw(listOf("NSE:$spotSymbol"))
            val fetchedSpot = spotResult.getOrNull()?.values?.firstOrNull()?.last_price ?: 0.0
            val effectiveSpot = if (fetchedSpot > 0.0) fetchedSpot else s.spotPrice
            if (effectiveSpot <= 0.0) {
                update { copy(isLoading = false, error = "Couldn't get spot price for ${s.underlying} (tried NSE:$spotSymbol)") }
                return
            }

            val atmStrike = strikesCache.minByOrNull { abs(it - effectiveSpot) } ?: strikesCache.first()
            val atmIdx = strikesCache.indexOf(atmStrike)
            val lo = (atmIdx - s.spreadCount).coerceAtLeast(0)
            val hi = (atmIdx + s.spreadCount).coerceAtMost(strikesCache.size - 1)
            val windowStrikes = strikesCache.subList(lo, hi + 1)

            val instrumentIds = mutableListOf<String>()
            for (strike in windowStrikes) {
                val pair = symbolsCache[strike] ?: ("" to "")
                if (pair.first.isNotBlank()) instrumentIds.add("NFO:${pair.first}")
                if (pair.second.isNotBlank()) instrumentIds.add("NFO:${pair.second}")
            }
            if (instrumentIds.isEmpty()) {
                update { copy(isLoading = false, error = "No tradable strikes in range") }
                return
            }

            val quotes = repo.fetchQuotesRaw(instrumentIds).getOrElse { e ->
                update { copy(isLoading = false, error = "Quote fetch failed: ${e.message}") }
                return
            }

            val rows = windowStrikes.map { strike ->
                val pair = symbolsCache[strike] ?: ("" to "")
                val ceQ = quotes["NFO:${pair.first}"]
                val peQ = quotes["NFO:${pair.second}"]
                OptionChainRow(
                    strike = strike,
                    isAtm = strike == atmStrike,
                    ceSymbol = pair.first,
                    ceLtp = ceQ?.last_price ?: 0.0,
                    ceBid = ceQ?.depth?.buy?.firstOrNull()?.price ?: 0.0,
                    ceAsk = ceQ?.depth?.sell?.firstOrNull()?.price ?: 0.0,
                    ceOi = ceQ?.oi?.toLong() ?: 0L,
                    ceVolume = ceQ?.volume ?: 0L,
                    peSymbol = pair.second,
                    peLtp = peQ?.last_price ?: 0.0,
                    peBid = peQ?.depth?.buy?.firstOrNull()?.price ?: 0.0,
                    peAsk = peQ?.depth?.sell?.firstOrNull()?.price ?: 0.0,
                    peOi = peQ?.oi?.toLong() ?: 0L,
                    peVolume = peQ?.volume ?: 0L
                )
            }

            val timeLabel = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            update {
                copy(
                    isLoading = false,
                    spotPrice = effectiveSpot,
                    rows = rows,
                    lastUpdatedLabel = "Updated $timeLabel \u2022 spot \u20b9${"%.2f".format(effectiveSpot)}"
                )
            }
        } catch (e: Exception) {
            update { copy(isLoading = false, error = "Load failed: ${e.message}") }
        }
    }

    fun clearError() = update { copy(error = null) }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }

    private fun update(block: OptionChainState.() -> OptionChainState) {
        _state.value = _state.value.block()
    }
}
