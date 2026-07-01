package com.marketdata.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marketdata.app.data.db.AppDatabase
import com.marketdata.app.data.db.DownloadedFileEntity
import com.marketdata.app.data.models.KiteCandle
import com.marketdata.app.data.models.SelectionType
import com.marketdata.app.data.repository.KiteRepository
import com.marketdata.app.util.CsvReader
import com.marketdata.app.util.Direction
import com.marketdata.app.util.NiftySymbols
import com.marketdata.app.util.PatternEngine
import com.marketdata.app.util.PatternResult
import com.marketdata.app.util.TradeLevels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

enum class ScanSource { LIVE, DOWNLOADED_FILE }

data class ScanPick(
    val result: PatternResult,
    val levels: TradeLevels
)

data class ScannerState(
    val source: ScanSource = ScanSource.DOWNLOADED_FILE,
    val selectionType: SelectionType = SelectionType.NIFTY100,
    val singleSymbol: String = "",
    val multiSymbols: List<String> = emptyList(),
    val symbolQuery: String = "",
    val selectedInterval: Int = 7, // index into NiftySymbols.INTERVALS ("Day")
    val lookbackMonths: Int = 5,
    val downloadedFiles: List<DownloadedFileEntity> = emptyList(),
    val selectedFileId: Int? = null,
    val minOccurrences: Int = 12,
    val minAccuracy: Double = 0.55,
    val slAtrMult: Double = 0.8,
    val targetAtrMult: Double = 1.5,
    val isScanning: Boolean = false,
    val progressText: String = "",
    val error: String? = null,
    val topBuy: List<ScanPick> = emptyList(),
    val topSell: List<ScanPick> = emptyList(),
    val allActive: List<PatternResult> = emptyList(),
    val scannedSymbolCount: Int = 0,
    val lastScanLabel: String = ""
)

class ScannerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = KiteRepository(app)
    private val db = AppDatabase.getInstance(app)

    private val _state = MutableStateFlow(ScannerState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            db.downloadedFileDao().getAllFiles().collect { files ->
                val stillValid = _state.value.selectedFileId == null ||
                    files.any { it.id == _state.value.selectedFileId }
                update {
                    copy(
                        downloadedFiles = files,
                        selectedFileId = if (stillValid) selectedFileId else files.firstOrNull()?.id
                    )
                }
            }
        }
    }

    fun setSource(s: ScanSource) = update { copy(source = s) }
    fun setSelectionType(t: SelectionType) = update { copy(selectionType = t, symbolQuery = "") }
    fun setSingleSymbol(s: String) = update { copy(singleSymbol = s.uppercase(), symbolQuery = "") }
    fun clearSingleSymbol() = update { copy(singleSymbol = "") }
    fun setSymbolQuery(q: String) = update { copy(symbolQuery = q.uppercase()) }
    fun addMultiSymbol(s: String) = update {
        copy(multiSymbols = if (s in multiSymbols) multiSymbols else multiSymbols + s, symbolQuery = "")
    }
    fun removeMultiSymbol(s: String) = update { copy(multiSymbols = multiSymbols - s) }
    fun setInterval(i: Int) = update { copy(selectedInterval = i) }
    fun setLookbackMonths(m: Int) = update { copy(lookbackMonths = m) }
    fun selectFile(id: Int) = update { copy(selectedFileId = id) }
    fun setMinOccurrences(v: Int) = update { copy(minOccurrences = v) }
    fun setMinAccuracy(v: Double) = update { copy(minAccuracy = v) }
    fun setSlMult(v: Double) = update { copy(slAtrMult = v) }
    fun setTargetMult(v: Double) = update { copy(targetAtrMult = v) }
    fun clearError() = update { copy(error = null) }

    fun symbolSuggestions(): List<String> {
        val q = _state.value.symbolQuery
        if (q.isBlank()) return emptyList()
        return NiftySymbols.NIFTY_100.filter { it.contains(q) }.take(15)
    }

    private fun getLiveSymbols(): List<String> {
        val s = _state.value
        return when (s.selectionType) {
            SelectionType.SINGLE -> if (s.singleSymbol.isNotBlank()) listOf(s.singleSymbol) else emptyList()
            SelectionType.MULTI -> s.multiSymbols
            SelectionType.NIFTY50 -> NiftySymbols.NIFTY_50
            SelectionType.NIFTY100 -> NiftySymbols.NIFTY_100
            SelectionType.INDEX -> emptyList()
        }
    }

    fun runScan() {
        viewModelScope.launch {
            update {
                copy(
                    isScanning = true, error = null, progressText = "Preparing...",
                    topBuy = emptyList(), topSell = emptyList(), allActive = emptyList()
                )
            }
            try {
                val s = _state.value
                val dataMap: Map<String, List<KiteCandle>> = when (s.source) {
                    ScanSource.DOWNLOADED_FILE -> loadFromFile(s)
                    ScanSource.LIVE -> loadLive(s)
                }
                if (dataMap.isEmpty()) {
                    update {
                        copy(
                            isScanning = false,
                            error = "No data to scan. Pick a downloaded file, or check your symbol selection."
                        )
                    }
                    return@launch
                }

                update { copy(progressText = "Scanning ${dataMap.size} symbols...") }
                val allResults = mutableListOf<PatternResult>()
                for ((sym, candles) in dataMap) {
                    allResults.addAll(PatternEngine.scan(sym, candles))
                }

                val cur = _state.value
                val active = allResults.filter {
                    it.activeNow && it.occurrences >= cur.minOccurrences && it.accuracy >= cur.minAccuracy
                }.sortedByDescending { it.confidence }

                val buys = active.filter { it.direction == Direction.UP }.take(3)
                val sells = active.filter { it.direction == Direction.DOWN }.take(3)

                val buyPicks = buys.map {
                    ScanPick(it, PatternEngine.tradeLevels(it.direction, it.cmp, it.atr, cur.slAtrMult, cur.targetAtrMult))
                }
                val sellPicks = sells.map {
                    ScanPick(it, PatternEngine.tradeLevels(it.direction, it.cmp, it.atr, cur.slAtrMult, cur.targetAtrMult))
                }

                update {
                    copy(
                        isScanning = false,
                        progressText = "",
                        topBuy = buyPicks,
                        topSell = sellPicks,
                        allActive = active,
                        scannedSymbolCount = dataMap.size,
                        lastScanLabel = "${dataMap.size} symbols \u2022 ${allResults.size} pattern checks \u2022 ${active.size} active signals"
                    )
                }
            } catch (e: Exception) {
                update { copy(isScanning = false, error = "Scan failed: ${e.message}") }
            }
        }
    }

    private fun loadFromFile(s: ScannerState): Map<String, List<KiteCandle>> {
        val file = s.downloadedFiles.find { it.id == s.selectedFileId } ?: return emptyMap()
        val uri = Uri.parse(file.filePath)
        return CsvReader.readMultiSymbolCsv(getApplication(), uri)
    }

    private suspend fun loadLive(s: ScannerState): Map<String, List<KiteCandle>> {
        val symbols = getLiveSymbols()
        if (symbols.isEmpty()) return emptyMap()
        val intervalEntry = NiftySymbols.INTERVALS[s.selectedInterval]
        val intervalKey = intervalEntry.second

        val toDate = Date()
        val fromDate = Calendar.getInstance().apply { add(Calendar.MONTH, -s.lookbackMonths) }.time

        var tokenMap = repo.getTokensForSymbols(symbols)
        if (tokenMap.isEmpty()) {
            repo.refreshInstruments()
            tokenMap = repo.getTokensForSymbols(symbols)
        }

        val result = LinkedHashMap<String, List<KiteCandle>>()
        var done = 0
        for ((sym, token) in tokenMap) {
            done++
            update { copy(progressText = "Downloading $sym ($done/${tokenMap.size})...") }
            repo.fetchHistorical(token, intervalKey, fromDate, toDate, includeOI = false)
                .onSuccess { candles -> if (candles.isNotEmpty()) result[sym] = candles }
        }
        return result
    }

    private fun update(block: ScannerState.() -> ScannerState) {
        _state.value = _state.value.block()
    }
}
