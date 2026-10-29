package com.marketdata.app.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marketdata.app.data.models.LiveQuoteDisplay
import com.marketdata.app.ui.components.InfoBanner
import com.marketdata.app.ui.components.ScreenHeader
import com.marketdata.app.ui.theme.*
import com.marketdata.app.util.SaveFolder
import com.marketdata.app.viewmodel.LIVE_REFRESH_INTERVALS
import com.marketdata.app.viewmodel.LiveQuotesViewModel
import com.marketdata.app.volumefilter.VolumeFilterBar
import com.marketdata.app.volumefilter.VolumeFilterStore
import com.marketdata.app.volumefilter.applyVolumeFilter

@Composable
fun LiveQuotesScreen(viewModel: LiveQuotesViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val volumeFilterStore = remember { VolumeFilterStore(context, "live_quotes") }
    // Filtered for DISPLAY only (recomputes on every recomposition, not on
    // every tick, since this list already only refreshes on the poll
    // interval / manual fetch - no extra debouncing needed on top of that).
    val displayedQuotes = remember(state.quotes, volumeFilterStore.mode) {
        applyVolumeFilter(state.quotes, { it.volume }, volumeFilterStore.mode)
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.saveToCSV(uri)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground).padding(16.dp)) {
        ScreenHeader("LIVE QUOTES", subtitle = "Latest prices for the symbols you choose")
        Spacer(Modifier.height(10.dp))
        InfoBanner(
            title = "BEST DURING MARKET HOURS",
            text = "Prices update while NSE is open (Mon-Fri, 9:15 AM to 3:30 PM IST). Outside " +
                "those hours you will only see the last session's values. Live quotes also need " +
                "the paid Kite Connect plan; the free Personal plan has no market data."
        )
        Spacer(Modifier.height(10.dp))

        AppTextField(
            label = "Symbols (comma separated)",
            value = state.symbolInput,
            onValueChange = { viewModel.setSymbolInput(it) },
            placeholder = "RELIANCE,TCS,INFY"
        )

        Spacer(Modifier.height(8.dp))
        VolumeFilterBar(store = volumeFilterStore)

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { viewModel.fetchQuotes() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            enabled = !state.isLoading
        ) {
            if (state.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
            else { Icon(Icons.Default.Refresh, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("FETCH") }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { viewModel.loadNifty50() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber)
            ) {
                Text("NIFTY 50")
            }
            OutlinedButton(
                onClick = { viewModel.loadNifty100() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber)
            ) {
                Text("NIFTY 100")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.loadAllInstruments() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoadingAllInstruments,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber)
        ) {
            if (state.isLoadingAllInstruments) {
                CircularProgressIndicator(color = AccentAmber, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(state.allInstrumentsProgress.ifBlank { "Loading..." })
            } else {
                Text("ALL INSTRUMENTS (FULL NSE)")
            }
        }

        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = DarkCard)) {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = state.autoRefreshEnabled,
                        onCheckedChange = { viewModel.setAutoRefresh(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.autoRefreshEnabled) "Auto-refreshing every ${state.refreshIntervalSeconds}s" else "Auto-refresh off",
                        color = TextPrimary, style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LIVE_REFRESH_INTERVALS.forEach { secs ->
                        val selected = state.refreshIntervalSeconds == secs
                        OutlinedButton(
                            onClick = { viewModel.setRefreshInterval(secs) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (selected) Color.White else AccentBlue,
                                containerColor = if (selected) AccentBlue else Color.Transparent
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("${secs}s", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }

        if (displayedQuotes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val folder = SaveFolder.current(context)
                    if (folder != null) viewModel.saveToCSV(folder)
                    else folderLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("SAVE TO CSV")
            }
        }

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0000))) {
                Text(it, color = AccentRed, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        state.savedMessage?.let {
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF001A00))) {
                Text(it, color = AccentGreen, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (displayedQuotes.isNotEmpty()) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("SYMBOL (${displayedQuotes.size}/${state.quotes.size})", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1.3f))
                Text("LTP", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                Text("CHG%", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                Text("VOL", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
            }
            Divider(color = DarkBorder)

            LazyColumn {
                items(displayedQuotes) { quote ->
                    QuoteRow(quote)
                    Divider(color = DarkBorder, thickness = 0.5.dp)
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No quotes loaded", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun QuoteRow(q: LiveQuoteDisplay) {
    val changeColor = if (q.changePct >= 0) AccentGreen else AccentRed
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(q.symbol, color = TextPrimary, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f))
        Text("₹${String.format("%.2f", q.ltp)}", color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            "${if (q.changePct >= 0) "+" else ""}${String.format("%.2f", q.changePct)}%",
            color = changeColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)
        )
        Text(formatVolume(q.volume), color = TextSecondary,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

fun formatVolume(vol: Long): String = when {
    vol >= 10_000_000 -> "${vol / 10_000_000}Cr"
    vol >= 100_000 -> "${vol / 100_000}L"
    vol >= 1000 -> "${vol / 1000}K"
    else -> vol.toString()
}
