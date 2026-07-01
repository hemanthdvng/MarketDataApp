package com.marketdata.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marketdata.app.data.models.SelectionType
import com.marketdata.app.ui.theme.*
import com.marketdata.app.util.Direction
import com.marketdata.app.util.NiftySymbols
import com.marketdata.app.viewmodel.ScanPick
import com.marketdata.app.viewmodel.ScanSource
import com.marketdata.app.viewmodel.ScannerViewModel
import com.marketdata.app.util.TradingStyle
import com.marketdata.app.util.TradingStyles
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ScannerScreen(viewModel: ScannerViewModel) {
    val state by viewModel.state.collectAsState()
    var showAdvanced by remember { mutableStateOf(false) }
    var showAllSignals by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("PATTERN SCANNER", style = MaterialTheme.typography.headlineMedium, color = AccentBlue)
        Text(
            "Backtests 10 candidate patterns per stock with a fixed, pre-specified direction (no after-the-fact cherry-picking), then ranks active setups by accuracy \u00d7 sample size.",
            color = TextSecondary, style = MaterialTheme.typography.bodySmall
        )

        // ---- Strategy / trading style ----
        SectionHeader("STRATEGY")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectionChip("Scalp", state.style == TradingStyle.SCALP) { viewModel.applyStyle(TradingStyle.SCALP) }
            SelectionChip("Intraday", state.style == TradingStyle.INTRADAY) { viewModel.applyStyle(TradingStyle.INTRADAY) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectionChip("Short-Term", state.style == TradingStyle.SHORT_TERM) { viewModel.applyStyle(TradingStyle.SHORT_TERM) }
            SelectionChip("Positional", state.style == TradingStyle.POSITIONAL) { viewModel.applyStyle(TradingStyle.POSITIONAL) }
        }
        Text(state.style.blurb, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        Text(
            "Sets timeframe, lookback, session handling and ATR stop/target for you \u2014 all still editable below.",
            color = TextMuted, style = MaterialTheme.typography.bodySmall
        )

        // ---- Data source ----
        SectionHeader("DATA SOURCE")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectionChip("Downloaded File", state.source == ScanSource.DOWNLOADED_FILE) {
                viewModel.setSource(ScanSource.DOWNLOADED_FILE)
            }
            SelectionChip("Live (Kite)", state.source == ScanSource.LIVE) {
                viewModel.setSource(ScanSource.LIVE)
            }
        }

        if (state.source == ScanSource.DOWNLOADED_FILE) {
            FileSourcePicker(state.downloadedFiles, state.selectedFileId, onSelect = { viewModel.selectFile(it) })
            Text(
                "The file's own timeframe applies (not the Strategy timeframe above) \u2014 session handling, thresholds and ATR stop/target still come from your Strategy.",
                color = TextMuted, style = MaterialTheme.typography.bodySmall
            )
        } else {
            LiveSourcePicker(viewModel)
        }

        // ---- Advanced settings ----
        Row(
            modifier = Modifier.fillMaxWidth().clickable { showAdvanced = !showAdvanced },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ADVANCED SETTINGS", style = MaterialTheme.typography.bodySmall, color = AccentBlue)
            Spacer(Modifier.weight(1f))
            Icon(
                if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null, tint = AccentBlue
            )
        }
        if (showAdvanced) {
            AdvancedSettings(state, viewModel)
        }

        // ---- Run button ----
        Button(
            onClick = { viewModel.runScan() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            enabled = !state.isScanning
        ) {
            if (state.isScanning) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Default.TrendingUp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("RUN SCAN", fontWeight = FontWeight.Bold)
            }
        }
        if (state.isScanning && state.progressText.isNotEmpty()) {
            Text(state.progressText, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0000)), modifier = Modifier.fillMaxWidth()) {
                Text(it, color = AccentRed, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        // ---- Results ----
        if (state.lastScanLabel.isNotEmpty() && !state.isScanning) {
            Text(state.lastScanLabel, color = TextMuted, style = MaterialTheme.typography.bodySmall)

            SectionHeader("TOP 3 \u2014 BUY SETUPS")
            if (state.topBuy.isEmpty()) {
                InfoCard("No BUY signals met your accuracy/sample thresholds right now.")
            } else {
                state.topBuy.forEach { PickCard(it, isBuy = true) }
            }

            SectionHeader("TOP 3 \u2014 SELL SETUPS")
            if (state.topSell.isEmpty()) {
                InfoCard("No SELL signals met your accuracy/sample thresholds right now.")
            } else {
                state.topSell.forEach { PickCard(it, isBuy = false) }
            }

            Row(
                modifier = Modifier.fillMaxWidth().clickable { showAllSignals = !showAllSignals },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ALL ACTIVE SIGNALS (${state.allActive.size})",
                    style = MaterialTheme.typography.bodySmall, color = AccentBlue
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    if (showAllSignals) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null, tint = AccentBlue
                )
            }
            if (showAllSignals) {
                state.allActive.forEach { r ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(r.symbol, color = TextPrimary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(r.patternLabel, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.6f))
                        Text(
                            "${(r.accuracy * 100).toInt()}% (n=${r.occurrences})",
                            color = if (r.direction == Direction.UP) AccentGreen else AccentRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Divider(color = DarkBorder, thickness = 0.5.dp)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = DarkCard), modifier = Modifier.fillMaxWidth()) {
            Text(
                "\u26a0 Backtested on a single historical window with modest sample sizes. This is statistically suggestive, not a guarantee. Confirm setups yourself before trading.",
                color = AccentAmber, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FileSourcePicker(
    files: List<com.marketdata.app.data.db.DownloadedFileEntity>,
    selectedId: Int?,
    onSelect: (Int) -> Unit
) {
    if (files.isEmpty()) {
        InfoCard("No downloaded files yet. Use the Download tab first, or switch to Live.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        files.take(15).forEach { f ->
            val selected = f.id == selectedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (selected) AccentBlue else DarkBorder, RoundedCornerShape(8.dp))
                    .background(if (selected) AccentBlue.copy(alpha = 0.12f) else DarkCard, RoundedCornerShape(8.dp))
                    .clickable { onSelect(f.id) }
                    .padding(10.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(f.fileName, color = TextPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${f.interval} \u2022 ${f.fromDate} to ${f.toDate} \u2022 ${f.rowCount} rows",
                        color = TextSecondary, style = MaterialTheme.typography.bodySmall
                    )
                }
                if (selected) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun LiveSourcePicker(viewModel: ScannerViewModel) {
    val state by viewModel.state.collectAsState()

    SectionHeader("SYMBOLS")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SelectionChip("Single", state.selectionType == SelectionType.SINGLE) { viewModel.setSelectionType(SelectionType.SINGLE) }
        SelectionChip("Multi", state.selectionType == SelectionType.MULTI) { viewModel.setSelectionType(SelectionType.MULTI) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SelectionChip("Nifty 50", state.selectionType == SelectionType.NIFTY50) { viewModel.setSelectionType(SelectionType.NIFTY50) }
        SelectionChip("Nifty 100", state.selectionType == SelectionType.NIFTY100) { viewModel.setSelectionType(SelectionType.NIFTY100) }
    }

    when (state.selectionType) {
        SelectionType.SINGLE -> {
            if (state.singleSymbol.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\u2713 ${state.singleSymbol}", color = AccentGreen, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearSingleSymbol() }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = AccentRed)
                    }
                }
            } else {
                AppTextField(
                    label = "Search stock symbol",
                    value = state.symbolQuery,
                    onValueChange = { viewModel.setSymbolQuery(it) },
                    placeholder = "e.g. RELIANCE"
                )
                viewModel.symbolSuggestions().forEach { sym ->
                    Text(
                        sym, color = TextPrimary, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.setSingleSymbol(sym) }.padding(vertical = 8.dp)
                    )
                }
            }
        }
        SelectionType.MULTI -> {
            AppTextField(
                label = "Search & add stocks",
                value = state.symbolQuery,
                onValueChange = { viewModel.setSymbolQuery(it) },
                placeholder = "e.g. TCS, INFY..."
            )
            viewModel.symbolSuggestions().forEach { sym ->
                Text(
                    sym, color = TextPrimary, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.addMultiSymbol(sym) }.padding(vertical = 8.dp)
                )
            }
            if (state.multiSymbols.isNotEmpty()) {
                Text("${state.multiSymbols.size} selected", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                SymbolChipFlow(symbols = state.multiSymbols, onRemove = { viewModel.removeMultiSymbol(it) })
            }
        }
        SelectionType.NIFTY50 -> InfoCard("\ud83d\udcca ${NiftySymbols.NIFTY_50.size} stocks will be scanned (Nifty 50)")
        SelectionType.NIFTY100 -> InfoCard("\ud83d\udcca ${NiftySymbols.NIFTY_100.size} stocks will be scanned (Nifty 100) \u2014 this fetches live history for every symbol, so it can take a while.")
        SelectionType.INDEX -> {}
    }

    SectionHeader("TIMEFRAME")
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(NiftySymbols.INTERVALS) { interval ->
            val idx = NiftySymbols.INTERVALS.indexOf(interval)
            SelectionChip(interval.first, state.selectedInterval == idx) { viewModel.setInterval(idx) }
        }
    }

    SectionHeader("LOOKBACK")
    val chipOptions = TradingStyles.PRESETS.getValue(state.style).lookbackChipOptions
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        chipOptions.forEach { d ->
            SelectionChip(formatLookback(d), state.lookbackDays == d) { viewModel.setLookbackDays(d) }
        }
    }
}

private fun formatLookback(days: Int): String = when {
    days % 365 == 0 && days >= 365 -> "${days / 365}y"
    days % 30 == 0 && days >= 60 -> "${days / 30}mo"
    else -> "${days}d"
}

@Composable
private fun AdvancedSettings(state: com.marketdata.app.viewmodel.ScannerState, viewModel: ScannerViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Session-only patterns", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Keep candlestick patterns within the same trading day (recommended for Scalp/Intraday)",
                        color = TextMuted, style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = state.sessionOnly, onCheckedChange = { viewModel.setSessionOnly(it) })
            }
            Text("Move threshold: ${state.thresholdFactor}\u00d7ATR-ratio", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.05, 0.08, 0.10, 0.15, 0.20).forEach { v ->
                    SelectionChip("$v", state.thresholdFactor == v) { viewModel.setThresholdFactor(v) }
                }
            }
            Text("Min occurrences: ${state.minOccurrences}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(8, 12, 15, 20).forEach { v ->
                    SelectionChip("$v", state.minOccurrences == v) { viewModel.setMinOccurrences(v) }
                }
            }
            Text("Min accuracy: ${(state.minAccuracy * 100).toInt()}%", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.50, 0.55, 0.60, 0.65).forEach { v ->
                    SelectionChip("${(v * 100).toInt()}%", state.minAccuracy == v) { viewModel.setMinAccuracy(v) }
                }
            }
            Text("SL = ${state.slAtrMult}\u00d7ATR \u2022 Target = ${state.targetAtrMult}\u00d7ATR \u2022 R:R \u2248 1:${"%.1f".format(state.targetAtrMult / state.slAtrMult)}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.5, 0.8, 1.0, 1.2).forEach { v ->
                    SelectionChip("SL ${v}x", state.slAtrMult == v) { viewModel.setSlMult(v) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1.0, 1.5, 2.0, 2.5).forEach { v ->
                    SelectionChip("Tgt ${v}x", state.targetAtrMult == v) { viewModel.setTargetMult(v) }
                }
            }
        }
    }
}

@Composable
private fun PickCard(pick: ScanPick, isBuy: Boolean) {
    val accent = if (isBuy) AccentGreen else AccentRed
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        modifier = Modifier.fillMaxWidth().border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pick.result.symbol, color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (isBuy) "BUY" else "SELL",
                    color = Color.Black, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(accent, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(pick.result.patternLabel, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text(
                "Accuracy ${(pick.result.accuracy * 100).toInt()}% (n=${pick.result.occurrences}) \u2022 Confidence ${(pick.result.confidence * 100).toInt()}%",
                color = TextSecondary, style = MaterialTheme.typography.bodySmall
            )
            Divider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                LevelStat("CMP", pick.result.cmp, TextPrimary)
                LevelStat("Entry", pick.levels.entry, AccentBlue)
                LevelStat("SL", pick.levels.stopLoss, AccentRed)
                LevelStat("Target", pick.levels.target, AccentGreen)
            }
            Text("R:R \u2248 1:${"%.1f".format(pick.levels.riskReward)}", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LevelStat(label: String, value: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        Text("\u20b9${"%.2f".format(value)}", color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
