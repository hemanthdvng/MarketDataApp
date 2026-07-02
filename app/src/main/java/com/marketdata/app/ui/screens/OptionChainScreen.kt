package com.marketdata.app.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marketdata.app.data.models.OptionChainRow
import com.marketdata.app.ui.theme.*
import com.marketdata.app.viewmodel.OPTION_CHAIN_REFRESH_INTERVALS
import com.marketdata.app.viewmodel.OPTION_CHAIN_SPREAD_COUNTS
import com.marketdata.app.viewmodel.OPTION_CHAIN_UNDERLYINGS
import com.marketdata.app.viewmodel.OptionChainViewModel

@Composable
fun OptionChainScreen(viewModel: OptionChainViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.exportVisibleChain(uri)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (state.expiries.isEmpty()) viewModel.loadExpiries()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("OPTION CHAIN", style = MaterialTheme.typography.headlineMedium, color = AccentBlue)

        SectionHeader("UNDERLYING")
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(OPTION_CHAIN_UNDERLYINGS) { name ->
                SelectionChip(name, state.underlying == name) { viewModel.selectUnderlying(name) }
            }
        }
        AppTextField(
            label = "Or type any F&O symbol",
            value = state.underlyingInput,
            onValueChange = { viewModel.setUnderlyingInput(it) },
            placeholder = "e.g. AXISBANK"
        )
        if (state.underlyingInput.isNotBlank()) {
            TextButton(onClick = { viewModel.selectUnderlying(state.underlyingInput) }) {
                Icon(Icons.Default.Search, contentDescription = null, tint = AccentBlue)
                Spacer(Modifier.width(6.dp))
                Text("Load ${state.underlyingInput}", color = AccentBlue)
            }
        }

        if (state.expiries.isNotEmpty()) {
            SectionHeader("EXPIRY")
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.expiries) { exp ->
                    SelectionChip(exp, state.selectedExpiry == exp) { viewModel.selectExpiry(exp) }
                }
            }

            SectionHeader("STRIKES EACH SIDE OF ATM")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OPTION_CHAIN_SPREAD_COUNTS.forEach { n ->
                    SelectionChip("\u00b1$n", state.spreadCount == n) { viewModel.setSpreadCount(n) }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = DarkCard), modifier = Modifier.fillMaxWidth()) {
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
                            color = TextPrimary, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.refreshNow() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh now", tint = AccentBlue)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OPTION_CHAIN_REFRESH_INTERVALS.forEach { secs ->
                            SelectionChip("${secs}s", state.refreshIntervalSeconds == secs) { viewModel.setRefreshInterval(secs) }
                        }
                    }
                }
            }

            if (state.lastUpdatedLabel.isNotEmpty()) {
                Text(state.lastUpdatedLabel, color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    folderLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.rows.isNotEmpty() && !state.isExporting,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen)
            ) {
                if (state.isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AccentGreen)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("SAVE VISIBLE CHAIN TO CSV")
                }
            }
        }

        state.exportMessage?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF001A00)), modifier = Modifier.fillMaxWidth()) {
                Text(it, color = AccentGreen, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        state.exportError?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0000)), modifier = Modifier.fillMaxWidth()) {
                Text(it, color = AccentRed, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        state.selectedContract?.let { contract ->
            val symbol = if (state.selectedSide == "CE") contract.ceSymbol else contract.peSymbol
            val ltp = if (state.selectedSide == "CE") contract.ceLtp else contract.peLtp
            val bid = if (state.selectedSide == "CE") contract.ceBid else contract.peBid
            val ask = if (state.selectedSide == "CE") contract.ceAsk else contract.peAsk
            val oi = if (state.selectedSide == "CE") contract.ceOi else contract.peOi
            val vol = if (state.selectedSide == "CE") contract.ceVolume else contract.peVolume
            val accent = if (state.selectedSide == "CE") AccentGreen else AccentRed

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth().border(1.dp, accent, RoundedCornerShape(10.dp))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(symbol, color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            state.selectedSide, color = Color.Black, fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(accent, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Deselect", tint = TextSecondary)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        LevelStat("LTP", ltp, accent)
                        LevelStat("Bid", bid, TextPrimary)
                        LevelStat("Ask", ask, TextPrimary)
                    }
                    Text("Strike \u20b9${"%.0f".format(contract.strike)} \u2022 OI ${formatVolume(oi)} \u2022 Vol ${formatVolume(vol)}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { clipboard.setText(AnnotatedString(symbol)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy trading symbol", color = AccentBlue, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (state.isLoading && state.rows.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentBlue)
            }
        }

        state.error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0000)), modifier = Modifier.fillMaxWidth()) {
                Text(it, color = AccentRed, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        if (state.rows.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("CALLS (CE)", color = AccentGreen, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("STRIKE", color = TextSecondary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("PUTS (PE)", color = AccentRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
            Divider(color = DarkBorder)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.rows) { row ->
                    OptionChainRowView(row, viewModel, state.selectedContract, state.selectedSide)
                    Divider(color = DarkBorder, thickness = 0.5.dp)
                }
            }
        } else if (!state.isLoading && state.error == null && state.expiries.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No data yet", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
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

@Composable
private fun OptionChainRowView(row: OptionChainRow, viewModel: OptionChainViewModel, selectedRow: OptionChainRow?, selectedSide: String) {
    val bg = if (row.isAtm) AccentBlue.copy(alpha = 0.12f) else Color.Transparent
    val ceSelected = selectedRow == row && selectedSide == "CE"
    val peSelected = selectedRow == row && selectedSide == "PE"
    Column(Modifier.fillMaxWidth().background(bg).padding(vertical = 6.dp, horizontal = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(if (ceSelected) AccentGreen.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { viewModel.selectContract(row, "CE") }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    "\u20b9${"%.2f".format(row.ceLtp)}", color = AccentGreen,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
                )
                Text(
                    "${"%.2f".format(row.ceBid)}/${"%.2f".format(row.ceAsk)} \u2022 OI ${formatVolume(row.ceOi)}",
                    color = TextMuted, style = MaterialTheme.typography.bodySmall
                )
            }
            Column(modifier = Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "%.0f".format(row.strike),
                    color = if (row.isAtm) AccentBlue else TextPrimary,
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium
                )
                if (row.isAtm) Text("ATM", color = AccentBlue, style = MaterialTheme.typography.bodySmall)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(if (peSelected) AccentRed.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { viewModel.selectContract(row, "PE") }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    "\u20b9${"%.2f".format(row.peLtp)}", color = AccentRed,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
                )
                Text(
                    "${"%.2f".format(row.peBid)}/${"%.2f".format(row.peAsk)} \u2022 OI ${formatVolume(row.peOi)}",
                    color = TextMuted, style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
