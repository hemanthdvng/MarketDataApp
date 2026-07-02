package com.marketdata.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marketdata.app.data.db.AppDatabase
import com.marketdata.app.data.db.DownloadedFileEntity
import com.marketdata.app.data.db.SymbolSyncEntity
import com.marketdata.app.data.db.SyncFileEntity
import com.marketdata.app.data.models.KiteCandle
import com.marketdata.app.data.models.SelectionType
import com.marketdata.app.data.prefs.SecurePrefs
import com.marketdata.app.data.repository.KiteRepository
import com.marketdata.app.util.CsvReader
import com.marketdata.app.util.CsvWriter
import com.marketdata.app.util.Extensions
import com.marketdata.app.util.NiftySymbols
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

data class DownloadState(
    val selectionType: SelectionType = SelectionType.SINGLE,
    val singleSymbol: String = "",
    val selectedMultiSymbols: List<String> = emptyList(),
    val selectedIndex: String = "NIFTY 50",
    val selectedInterval: Int = 7, // index into NiftySymbols.INTERVALS (day)
    val fromDate: Date = Calendar.getInstance().apply { add(Calendar.MONTH, -3) }.time,
    val toDate: Date = Date(),
    val includeOI: Boolean = false,
    val folderUri: Uri? = null,
    val folderName: String = "Not selected",
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val progressText: String = "",
    val error: String? = null,
    val successMessage: String? = null,
    val symbolSearchQuery: String = "",
    val symbolSearchResults: List<String> = emptyList()
)

class DownloadViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = SecurePrefs.getInstance(app)
    private val repo = KiteRepository(app)
    private val db = AppDatabase.getInstance(app)

    private val _state = MutableStateFlow(DownloadState())
    val state = _state.asStateFlow()

    fun setSelectionType(type: SelectionType) = updateState {
        copy(selectionType = type, symbolSearchQuery = "", symbolSearchResults = emptyList())
    }
    fun setSelectedIndex(idx: String) = updateState { copy(selectedIndex = idx) }
    fun setInterval(i: Int) = updateState { copy(selectedInterval = i) }
    fun setFromDate(d: Date) = updateState { copy(fromDate = d) }
    fun setToDate(d: Date) = updateState { copy(toDate = d) }
    fun setIncludeOI(v: Boolean) = updateState { copy(includeOI = v) }
    fun clearError() = updateState { copy(error = null, successMessage = null) }

    fun setFolder(uri: Uri, displayName: String) {
        updateState { copy(folderUri = uri, folderName = displayName) }
    }

    /** Search-as-you-type. Falls back to the static Nifty 100 list if the
     *  instrument DB hasn't been loaded yet (e.g. before first login). */
    fun searchSymbols(query: String) {
        updateState { copy(symbolSearchQuery = query.uppercase()) }
        viewModelScope.launch {
            if (query.isNotBlank()) {
                val q = query.uppercase()
                val dbResults = try { repo.searchSymbols(q) } catch (e: Exception) { emptyList() }
                val results = if (dbResults.isNotEmpty()) {
                    dbResults.map { it.tradingSymbol }
                } else {
                    NiftySymbols.NIFTY_100.filter { it.contains(q) }
                }
                updateState { copy(symbolSearchResults = results.take(25)) }
            } else {
                updateState { copy(symbolSearchResults = emptyList()) }
            }
        }
    }

    /** SINGLE mode: pick a symbol from the suggestion list */
    fun selectSingleSymbol(symbol: String) {
        updateState { copy(singleSymbol = symbol, symbolSearchQuery = "", symbolSearchResults = emptyList()) }
    }

    fun clearSingleSymbol() {
        updateState { copy(singleSymbol = "", symbolSearchQuery = "") }
    }

    /** MULTI mode: add/remove from the selected chip list */
    fun addMultiSymbol(symbol: String) {
        updateState {
            val updated = if (symbol in selectedMultiSymbols) selectedMultiSymbols
                else selectedMultiSymbols + symbol
            copy(selectedMultiSymbols = updated, symbolSearchQuery = "", symbolSearchResults = emptyList())
        }
    }

    fun removeMultiSymbol(symbol: String) {
        updateState { copy(selectedMultiSymbols = selectedMultiSymbols - symbol) }
    }

    fun clearMultiSymbols() {
        updateState { copy(selectedMultiSymbols = emptyList()) }
    }

    fun startDownload() {
        val s = _state.value
        if (s.folderUri == null) {
            updateState { copy(error = "Please select a folder first") }
            return
        }

        val symbols = getSymbols(s)
        if (symbols.isEmpty()) {
            updateState { copy(error = "Please select at least one symbol") }
            return
        }

        val intervalEntry = NiftySymbols.INTERVALS[s.selectedInterval]
        val intervalKey = intervalEntry.second
        val intervalLabel = intervalEntry.third

        viewModelScope.launch {
            updateState { copy(isDownloading = true, progress = 0f, error = null, successMessage = null) }

            // --- Incremental sync: load what we already have for this interval
            // so we only ask Kite for what's actually missing. ---
            updateState { copy(progressText = "Checking what's already downloaded...") }
            val coverage = try {
                db.syncDao().getCoverage(intervalLabel).associateBy { it.symbol }
            } catch (e: Exception) { emptyMap() }
            val existingSyncFile = try { db.syncDao().getSyncFile(intervalLabel) } catch (e: Exception) { null }
            val mergedData: MutableMap<String, MutableList<KiteCandle>> = existingSyncFile?.let { sf ->
                try {
                    CsvReader.readMultiSymbolCsv(getApplication(), Uri.parse(sf.filePath))
                        .mapValues { it.value.toMutableList() }.toMutableMap()
                } catch (e: Exception) {
                    mutableMapOf() // file moved/deleted - fall back to full refetch below
                }
            } ?: mutableMapOf()

            // Resolve tokens
            updateState { copy(progressText = "Resolving instrument tokens...") }

            val tokenMap = mutableMapOf<String, Long>()
            if (s.selectionType == SelectionType.INDEX) {
                val token = NiftySymbols.INDEX_TOKENS[s.selectedIndex]
                if (token != null) tokenMap[s.selectedIndex] = token
            } else {
                val symbolTokens = repo.getTokensForSymbols(symbols)
                if (symbolTokens.isEmpty()) {
                    // Try refreshing instruments
                    val refreshResult = repo.refreshInstruments()
                    refreshResult.onFailure { e ->
                        updateState { copy(isDownloading = false, error = "Could not load instrument list: ${e.message}") }
                    }
                    if (refreshResult.isFailure) return@launch
                    tokenMap.putAll(repo.getTokensForSymbols(symbols))
                } else {
                    tokenMap.putAll(symbolTokens)
                }
            }

            if (tokenMap.isEmpty()) {
                updateState { copy(isDownloading = false, error = "No instrument tokens found. Try refreshing instruments.") }
                return@launch
            }

            val totalSymbols = tokenMap.size
            var symbolsDone = 0
            var newRowsFetched = 0
            var upToDateCount = 0
            val updatedCoverage = mutableListOf<SymbolSyncEntity>()

            for ((sym, token) in tokenMap) {
                val cov = coverage[sym]
                val priorEarliest = cov?.earliestDate?.let { Extensions.parseCandleTimestamp(it) }
                val priorLatest = cov?.latestDate?.let { Extensions.parseCandleTimestamp(it) }
                // Only trust the "just fetch what's new" shortcut if our existing
                // coverage already reaches back to (or before) the requested start.
                // Otherwise a request for an earlier fromDate than before would
                // silently skip that earlier gap - so fall back to a full fetch.
                val coversRequestedStart = priorEarliest != null && !priorEarliest.after(s.fromDate)
                val effectiveFromDate = if (coversRequestedStart && priorLatest != null && priorLatest.after(s.fromDate))
                    priorLatest else s.fromDate

                if (coversRequestedStart && priorLatest != null && !priorLatest.before(s.toDate)) {
                    upToDateCount++
                    symbolsDone++
                    updateState { copy(progressText = "$sym already up to date (${symbolsDone}/$totalSymbols)") }
                    continue
                }

                updateState {
                    copy(progressText = "Downloading $sym${if (coversRequestedStart) " (new data only)" else ""}... (${symbolsDone + 1}/$totalSymbols)")
                }

                val result = repo.fetchHistorical(
                    instrumentToken = token,
                    intervalKey = intervalKey,
                    fromDate = effectiveFromDate,
                    toDate = s.toDate,
                    includeOI = s.includeOI,
                    onProgress = { done, total ->
                        val symbolProgress = symbolsDone.toFloat() / totalSymbols
                        val chunkProgress = done.toFloat() / total / totalSymbols
                        updateState { copy(progress = symbolProgress + chunkProgress) }
                    }
                )
                result.onSuccess { newCandles ->
                    val bucket = mergedData.getOrPut(sym) { mutableListOf() }
                    val seen = bucket.mapTo(HashSet()) { it.timestamp }
                    var added = 0
                    for (c in newCandles) {
                        if (seen.add(c.timestamp)) {
                            bucket.add(c)
                            added++
                        }
                    }
                    bucket.sortBy { it.timestamp }
                    newRowsFetched += added
                    if (bucket.isNotEmpty()) {
                        updatedCoverage.add(SymbolSyncEntity(sym, intervalLabel, bucket.first().timestamp, bucket.last().timestamp))
                    }
                }
                result.onFailure { e ->
                    updateState { copy(progressText = "Warning: failed $sym — ${e.message}") }
                }
                symbolsDone++
            }

            if (mergedData.isEmpty()) {
                updateState { copy(isDownloading = false, error = "No data downloaded") }
                return@launch
            }

            // Write the full merged master file back out (previously-synced
            // symbols untouched this run + this run's updated/new symbols).
            updateState { copy(progressText = "Writing CSV...") }
            try {
                val docUri: Uri = if (existingSyncFile != null) {
                    Uri.parse(existingSyncFile.filePath)
                } else {
                    androidx.documentfile.provider.DocumentFile
                        .fromTreeUri(getApplication(), s.folderUri!!)
                        ?.createFile("text/csv", CsvWriter.generateSyncFileName(intervalLabel))
                        ?.uri
                        ?: run {
                            updateState { copy(isDownloading = false, error = "Failed to create file in selected folder") }
                            return@launch
                        }
                }

                val totalRows = CsvWriter.writeMultiSymbolCandlesToUri(
                    getApplication(), docUri, mergedData, s.includeOI
                )

                db.syncDao().setSyncFile(SyncFileEntity(intervalLabel, docUri.toString()))
                if (updatedCoverage.isNotEmpty()) db.syncDao().upsertCoverage(updatedCoverage)

                if (db.downloadedFileDao().countByPath(docUri.toString()) == 0) {
                    db.downloadedFileDao().insert(
                        DownloadedFileEntity(
                            fileName = CsvWriter.generateSyncFileName(intervalLabel),
                            filePath = docUri.toString(),
                            symbols = symbols.joinToString(","),
                            interval = intervalLabel,
                            fromDate = Extensions.formatDate(s.fromDate),
                            toDate = Extensions.formatDate(s.toDate),
                            rowCount = totalRows
                        )
                    )
                } else {
                    db.downloadedFileDao().updateStatsByPath(
                        docUri.toString(), totalRows, Extensions.formatDate(s.toDate)
                    )
                }

                val summary = buildString {
                    append("✅ $newRowsFetched new row(s) fetched")
                    if (upToDateCount > 0) append(", $upToDateCount symbol(s) already up to date")
                    append(" • $totalRows total rows in ${CsvWriter.generateSyncFileName(intervalLabel)}")
                }
                updateState { copy(isDownloading = false, progress = 1f, successMessage = summary) }
            } catch (e: Exception) {
                updateState { copy(isDownloading = false, error = "Write failed: ${e.message}") }
            }
        }
    }

    private fun getSymbols(s: DownloadState): List<String> = when (s.selectionType) {
        SelectionType.SINGLE -> if (s.singleSymbol.isNotBlank()) listOf(s.singleSymbol.trim()) else emptyList()
        SelectionType.MULTI -> s.selectedMultiSymbols
        SelectionType.INDEX -> listOf(s.selectedIndex)
        SelectionType.NIFTY50 -> NiftySymbols.NIFTY_50
        SelectionType.NIFTY100 -> NiftySymbols.NIFTY_100
    }

    private fun updateState(block: DownloadState.() -> DownloadState) {
        _state.value = _state.value.block()
    }
}
