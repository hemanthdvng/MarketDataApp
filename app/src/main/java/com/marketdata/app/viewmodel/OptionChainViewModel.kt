package com.marketdata.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marketdata.app.data.models.OptionChainRow
import com.marketdata.app.data.repository.KiteRepository
import com.marketdata.app.util.CsvWriter
import com.marketdata.app.util.SaveFolder
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

/** Which bulk set (if any) a chain download should cover. SINGLE is the
 *  existing one-underlying flow; the other three drive downloadBulkChain(). */
enum class OcBulkMode { SINGLE, NIFTY50, NIFTY100, ALL_FNO }

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
    val lastUpdatedLabel: String = "",
    // Contract selection (tap a CE/PE cell in the chain to select it).
    val selectedContract: OptionChainRow? = null,
    val selectedSide: String = "", // "CE" or "PE"
    // CSV export (of whatever's currently on screen / the full chain).
    val isExporting: Boolean = false,
    val exportError: String? = null,
    val exportMessage: String? = null,
    // Search-as-you-type for the underlying picker (backed by the live F&O
    // instrument list, falls back to OPTION_CHAIN_UNDERLYINGS before that's loaded).
    val underlyingSearchResults: List<String> = emptyList(),
    // Bulk download (Nifty 50 / Nifty 100 / All F&O) - a separate flow from the
    // single-underlying on-screen chain above. bulkExpiryOptions is the union of
    // every expiry across the chosen set, so the user picks one calendar date and
    // each symbol resolves it to its own nearest available expiry on that date
    // or after.
    val bulkMode: OcBulkMode = OcBulkMode.SINGLE,
    val bulkExpiryOptions: List<String> = emptyList(),
    val selectedBulkExpiry: String = "",
    val isLoadingBulkExpiries: Boolean = false,
    val isBulkDownloading: Boolean = false,
    val bulkProgressText: String = "",
    val bulkDone: Int = 0,
    val bulkTotal: Int = 0
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
    private var allFnoUnderlyingsCache: List<String> = emptyList()

    /** Search-as-you-type version of the underlying picker. Uses the live F&O
     *  instrument list once it's loaded (covers every stock/index with
     *  options, not just the 8 hardcoded quick-select chips); falls back to
     *  those chips before instruments are refreshed. */
    fun setUnderlyingInput(s: String) {
        val q = s.uppercase()
        update { copy(underlyingInput = q) }
        if (q.isBlank()) {
            update { copy(underlyingSearchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            if (allFnoUnderlyingsCache.isEmpty()) {
                allFnoUnderlyingsCache = try { repo.getAllFnoUnderlyings() } catch (e: Exception) { emptyList() }
            }
            val pool = allFnoUnderlyingsCache.ifEmpty { OPTION_CHAIN_UNDERLYINGS }
            val results = pool.filter { it.contains(q) }.sortedBy { if (it.startsWith(q)) 0 else 1 }.take(20)
            update { copy(underlyingSearchResults = results) }
        }
    }

    fun selectUnderlying(name: String) {
        strikesCache = emptyList()
        symbolsCache = emptyMap()
        update {
            copy(
                underlying = name.uppercase(), underlyingInput = "", underlyingSearchResults = emptyList(),
                expiries = emptyList(), selectedExpiry = "", rows = emptyList(), spotPrice = 0.0,
                selectedContract = null, selectedSide = ""
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
        update { copy(selectedExpiry = expiry, rows = emptyList(), selectedContract = null, selectedSide = "") }
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

    /** Selects/deselects a contract from the on-screen chain (tap a CE or PE cell). */
    fun selectContract(row: OptionChainRow, side: String) {
        val s = _state.value
        if (s.selectedContract == row && s.selectedSide == side) {
            update { copy(selectedContract = null, selectedSide = "") }
        } else {
            update { copy(selectedContract = row, selectedSide = side) }
        }
    }

    fun clearSelection() = update { copy(selectedContract = null, selectedSide = "") }

    fun clearExportMessage() = update { copy(exportError = null, exportMessage = null) }

    /** Exports the rows currently visible on screen (the strike window around ATM). */
    fun exportVisibleChain(folderUri: Uri) {
        val s = _state.value
        if (s.rows.isEmpty()) {
            update { copy(exportError = "No option chain data loaded yet") }
            return
        }
        viewModelScope.launch {
            update { copy(isExporting = true, exportError = null, exportMessage = null) }
            try {
                val fileName = CsvWriter.generateOptionChainFileName(s.underlying, s.selectedExpiry)
                val docUri = SaveFolder.createCsv(getApplication(), folderUri, fileName)
                if (docUri == null) {
                    update { copy(isExporting = false, exportError = "Failed to create file in selected folder") }
                    return@launch
                }
                val rows = CsvWriter.writeOptionChainToUri(
                    getApplication(), docUri, s.underlying, s.selectedExpiry, s.spotPrice, s.rows
                )
                update { copy(isExporting = false, exportMessage = "Saved $fileName ($rows strikes)") }
            } catch (e: Exception) {
                update { copy(isExporting = false, exportError = "Export failed: ${e.message}") }
            }
        }
    }

    /** Fetches every strike's CE+PE quote for one underlying+expiry (not just
     *  the on-screen +/- window around ATM), batching quote requests to stay
     *  under Kite's per-request instrument cap and rate limit. Shared by both
     *  the single-underlying full-chain download and the bulk (Nifty 50/100/
     *  All F&O) download below. Returns null (with no state error set here -
     *  caller decides how to report it) if the underlying has no strikes for
     *  that expiry. */
    private suspend fun fetchFullChainForExpiry(underlying: String, expiry: String, fallbackSpot: Double): Pair<List<OptionChainRow>, Double>? {
        val instruments = repo.getOptionChainInstruments(underlying, expiry)
        val byStrike = instruments.groupBy { it.strike }
        if (byStrike.isEmpty()) return null
        val strikes = byStrike.keys.sorted()
        val symbolsByStrike = strikes.associateWith { strike ->
            val list = byStrike[strike] ?: emptyList()
            (list.find { it.instrumentType == "CE" }?.tradingSymbol ?: "") to
                (list.find { it.instrumentType == "PE" }?.tradingSymbol ?: "")
        }

        val spotSymbol = OPTION_CHAIN_SPOT_SYMBOL[underlying] ?: underlying
        val spotResult = repo.fetchQuotesRaw(listOf("NSE:$spotSymbol"))
        val spot = spotResult.getOrNull()?.values?.firstOrNull()?.last_price ?: fallbackSpot
        val atmStrike = strikes.minByOrNull { abs(it - spot) } ?: strikes.first()

        val allRows = mutableListOf<OptionChainRow>()
        for (batch in strikes.chunked(150)) {
            val ids = mutableListOf<String>()
            for (strike in batch) {
                val (ce, pe) = symbolsByStrike[strike] ?: ("" to "")
                if (ce.isNotBlank()) ids.add("NFO:$ce")
                if (pe.isNotBlank()) ids.add("NFO:$pe")
            }
            if (ids.isEmpty()) continue
            val quotes = repo.fetchQuotesRaw(ids).getOrElse { emptyMap() }
            for (strike in batch) {
                val (ce, pe) = symbolsByStrike[strike] ?: ("" to "")
                val ceQ = quotes["NFO:$ce"]
                val peQ = quotes["NFO:$pe"]
                allRows.add(
                    OptionChainRow(
                        strike = strike,
                        isAtm = strike == atmStrike,
                        ceSymbol = ce,
                        ceLtp = ceQ?.last_price ?: 0.0,
                        ceBid = ceQ?.depth?.buy?.firstOrNull()?.price ?: 0.0,
                        ceAsk = ceQ?.depth?.sell?.firstOrNull()?.price ?: 0.0,
                        ceOi = ceQ?.oi?.toLong() ?: 0L,
                        ceVolume = ceQ?.volume ?: 0L,
                        peSymbol = pe,
                        peLtp = peQ?.last_price ?: 0.0,
                        peBid = peQ?.depth?.buy?.firstOrNull()?.price ?: 0.0,
                        peAsk = peQ?.depth?.sell?.firstOrNull()?.price ?: 0.0,
                        peOi = peQ?.oi?.toLong() ?: 0L,
                        peVolume = peQ?.volume ?: 0L
                    )
                )
            }
            delay(350) // stay under Kite's rate limit across batches
        }
        return allRows to spot
    }

    /** Downloads the FULL option chain for the current underlying+expiry (every strike,
     *  not just the on-screen +/- window around ATM). Used by the Download tab. */
    fun downloadFullChain(folderUri: Uri) {
        val s = _state.value
        if (s.selectedExpiry.isBlank()) {
            update { copy(exportError = "Pick an underlying + expiry on the Options tab first") }
            return
        }
        viewModelScope.launch {
            update { copy(isExporting = true, exportError = null, exportMessage = null) }
            try {
                val result = fetchFullChainForExpiry(s.underlying, s.selectedExpiry, s.spotPrice)
                if (result == null) {
                    update { copy(isExporting = false, exportError = "No strikes found for this expiry") }
                    return@launch
                }
                val (allRows, spot) = result

                val fileName = CsvWriter.generateOptionChainFileName(s.underlying, s.selectedExpiry)
                val docUri = SaveFolder.createCsv(getApplication(), folderUri, fileName)
                if (docUri == null) {
                    update { copy(isExporting = false, exportError = "Failed to create file in selected folder") }
                    return@launch
                }
                val written = CsvWriter.writeOptionChainToUri(getApplication(), docUri, s.underlying, s.selectedExpiry, spot, allRows)
                update { copy(isExporting = false, exportMessage = "Saved $fileName ($written strikes, full chain)") }
            } catch (e: Exception) {
                update { copy(isExporting = false, exportError = "Download failed: ${e.message}") }
            }
        }
    }

    /** Switches between single-underlying and bulk (Nifty 50 / Nifty 100 / All
     *  F&O) download mode. Entering a bulk mode loads the union of expiries
     *  across that whole set, so the user picks ONE calendar date and it's
     *  matched per-symbol in downloadBulkChain(). */
    fun setBulkMode(mode: OcBulkMode) {
        update { copy(bulkMode = mode, bulkExpiryOptions = emptyList(), selectedBulkExpiry = "") }
        if (mode == OcBulkMode.SINGLE) return
        viewModelScope.launch {
            update { copy(isLoadingBulkExpiries = true) }
            try {
                if (repo.derivativeInstrumentsNeedRefresh()) repo.refreshDerivativeInstruments()
                val symbols = bulkModeSymbols(mode)
                // NIFTY's own expiry list is the most complete/frequent (weekly) and a
                // good universal picker - individual stocks/other indices resolve to
                // their own nearest available date from whatever the user picks here.
                val reference = repo.getExpiriesForUnderlying("NIFTY").ifEmpty {
                    symbols.firstNotNullOfOrNull { repo.getExpiriesForUnderlying(it).ifEmpty { null } } ?: emptyList()
                }
                update { copy(isLoadingBulkExpiries = false, bulkExpiryOptions = reference, selectedBulkExpiry = reference.firstOrNull() ?: "") }
            } catch (e: Exception) {
                update { copy(isLoadingBulkExpiries = false, exportError = "Failed to load expiries: ${e.message}") }
            }
        }
    }

    fun setSelectedBulkExpiry(expiry: String) = update { copy(selectedBulkExpiry = expiry) }

    private suspend fun bulkModeSymbols(mode: OcBulkMode): List<String> = when (mode) {
        OcBulkMode.NIFTY50 -> com.marketdata.app.util.NiftySymbols.NIFTY_50
        OcBulkMode.NIFTY100 -> com.marketdata.app.util.NiftySymbols.NIFTY_100
        OcBulkMode.ALL_FNO -> {
            if (allFnoUnderlyingsCache.isEmpty()) allFnoUnderlyingsCache = repo.getAllFnoUnderlyings()
            allFnoUnderlyingsCache
        }
        OcBulkMode.SINGLE -> emptyList()
    }

    /** Downloads the full option chain for every symbol in the chosen bulk
     *  set (Nifty 50 / Nifty 100 / All F&O), all at the user's one chosen
     *  expiry date - resolved per-symbol to an exact match if the underlying
     *  actually has that date, otherwise its nearest available expiry ON OR
     *  AFTER that date (so a stock's monthly-only expiries still resolve to
     *  something sensible when the picked date is one of NIFTY's weeklies).
     *  Writes everything into a single combined CSV, tagged per-row with the
     *  underlying and the expiry actually used for it. */
    fun downloadBulkChain(folderUri: Uri) {
        val s = _state.value
        if (s.bulkMode == OcBulkMode.SINGLE) return
        if (s.selectedBulkExpiry.isBlank()) {
            update { copy(exportError = "Pick an expiry date first") }
            return
        }
        viewModelScope.launch {
            update { copy(isBulkDownloading = true, exportError = null, exportMessage = null, bulkDone = 0, bulkProgressText = "Preparing...") }
            try {
                val symbols = bulkModeSymbols(s.bulkMode)
                if (symbols.isEmpty()) {
                    update { copy(isBulkDownloading = false, exportError = "No symbols in this set") }
                    return@launch
                }
                update { copy(bulkTotal = symbols.size) }

                val entries = mutableListOf<Pair<Triple<String, String, Double>, List<OptionChainRow>>>()
                var done = 0
                var skipped = 0
                for (sym in symbols) {
                    update { copy(bulkProgressText = "Fetching $sym ($done/${symbols.size})...") }
                    try {
                        val symExpiries = repo.getExpiriesForUnderlying(sym)
                        val resolvedExpiry = symExpiries.find { it == s.selectedBulkExpiry }
                            ?: symExpiries.filter { it >= s.selectedBulkExpiry }.minOrNull()
                            ?: symExpiries.maxOrNull()
                        if (resolvedExpiry != null) {
                            val result = fetchFullChainForExpiry(sym, resolvedExpiry, 0.0)
                            if (result != null && result.first.isNotEmpty()) {
                                entries.add(Triple(sym, resolvedExpiry, result.second) to result.first)
                            } else {
                                skipped++
                            }
                        } else {
                            skipped++
                        }
                    } catch (e: Exception) {
                        skipped++
                    }
                    done++
                    update { copy(bulkDone = done) }
                }

                if (entries.isEmpty()) {
                    update { copy(isBulkDownloading = false, exportError = "No option chain data found for any symbol in this set") }
                    return@launch
                }

                val label = when (s.bulkMode) {
                    OcBulkMode.NIFTY50 -> "nifty50"
                    OcBulkMode.NIFTY100 -> "nifty100"
                    OcBulkMode.ALL_FNO -> "all_fno"
                    OcBulkMode.SINGLE -> "chain"
                }
                val fileName = CsvWriter.generateBulkOptionChainFileName(label)
                val docUri = SaveFolder.createCsv(getApplication(), folderUri, fileName)
                if (docUri == null) {
                    update { copy(isBulkDownloading = false, exportError = "Failed to create file in selected folder") }
                    return@launch
                }
                val totalRows = CsvWriter.writeBulkOptionChainToUri(getApplication(), docUri, entries)
                val summary = "Saved $fileName \u2022 ${entries.size} symbol(s), $totalRows strikes" +
                    (if (skipped > 0) " \u2022 $skipped symbol(s) skipped (no matching expiry/data)" else "")
                update { copy(isBulkDownloading = false, bulkProgressText = "", exportMessage = summary) }
            } catch (e: Exception) {
                update { copy(isBulkDownloading = false, exportError = "Bulk download failed: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }

    private fun update(block: OptionChainState.() -> OptionChainState) {
        _state.value = _state.value.block()
    }
}
