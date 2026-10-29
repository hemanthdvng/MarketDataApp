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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.marketdata.app.util.SaveFolder
import com.marketdata.app.viewmodel.OcBulkMode
import com.marketdata.app.viewmodel.OPTION_CHAIN_REFRESH_INTERVALS
import com.marketdata.app.viewmodel.OPTION_CHAIN_SPREAD_COUNTS
import com.marketdata.app.viewmodel.OPTION_CHAIN_UNDERLYINGS
import com.marketdata.app.viewmodel.OptionChainViewModel

/**
 * The whole screen is a single LazyColumn (selectors + strike table together)
 * instead of a fixed-header Column with a weight(1f) LazyColumn beneath it.
 * With the old layout, once the selector/refresh/selected-contract cards above
 * the table got tall enough (e.g. auto-refresh card + a selected contract +
 * an export message all showing at once), the weighted LazyColumn could be
 * squeezed down to almost no visible height - the chain data was loaded and
 * present, just not visibly scrollable to reach it. Making everything one
 * LazyColumn means the whole screen scrolls together and the strike rows are
 * always reachable regardless of how much sits above them.
 */
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
    val bulkFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.downloadBulkChain(uri)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (state.expiries.isEmpty()) viewModel.loadExpiries()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("OPTION CHAIN", style = MaterialTheme.typography.headlineMedium, color = AccentBlue) }

        item {
            SectionHeader("UNDERLYING")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(OPTION_CHAIN_UNDERLYINGS) { name ->
                    SelectionChip(name, state.bulkMode == OcBulkMode.SINGLE && state.underlying == name) {
                        viewModel.setBulkMode(OcBulkMode.SINGLE)
                        viewModel.selectUnderlying(name)
                    }
                }
            }
        }

        item {
            AppTextField(
                label = "Search F&O symbol",
                value = state.underlyingInput,
                onValueChange = { viewModel.setUnderlyingInput(it) },
                placeholder = "Type to search e.g. AXISBANK"
            )
        }
        if (state.underlyingSearchResults.isNotEmpty()) {
            item {
                SymbolSuggestionList(
                    results = state.underlyingSearchResults,
                    onSelect = {
                        viewModel.setBulkMode(OcBulkMode.SINGLE)
                        viewModel.selectUnderlying(it)
                    }
                )
            }
        }

        // ---- Bulk download shortcuts: Nifty 50 / Nifty 100 / All F&O ----
        item {
            SectionHeader("BULK DOWNLOAD (ALL SYMBOLS, ONE EXPIRY)")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectionChip("Nifty 50", state.bulkMode == OcBulkMode.NIFTY50) { viewModel.setBulkMode(OcBulkMode.NIFTY50) }
                SelectionChip("Nifty 100", state.bulkMode == OcBulkMode.NIFTY100) { viewModel.setBulkMode(OcBulkMode.NIFTY100) }
                SelectionChip("All F&O", state.bulkMode == OcBulkMode.ALL_FNO) { viewModel.setBulkMode(OcBulkMode.ALL_FNO) }
            }
        }

        if (state.bulkMode != OcBulkMode.SINGLE) {
            item {
                if (state.isLoadingBulkExpiries) {
                    Text("Loading available expiries...", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                } else if (state.bulkExpiryOptions.isNotEmpty()) {
                    Column {
                        Text(
                            "Pick one expiry date - each symbol resolves it to its own nearest available expiry on or after that date.",
                            color = TextMuted, style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.bulkExpiryOptions) { exp ->
                                SelectionChip(exp, state.selectedBulkExpiry == exp) { viewModel.setSelectedBulkExpiry(exp) }
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        val folder = SaveFolder.current(context)
                        if (folder != null) viewModel.downloadBulkChain(folder)
                        else bulkFolderLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    enabled = state.selectedBulkExpiry.isNotBlank() && !state.isBulkDownloading
                ) {
                    if (state.isBulkDownloading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("DOWNLOAD FULL CHAIN CSV")
                    }
                }
            }
            if (state.isBulkDownloading) {
                item {
                    Column {
                        LinearProgressIndicator(
                            progress = { if (state.bulkTotal > 0) state.bulkDone.toFloat() / state.bulkTotal else 0f },
                            modifier = Modifier.fillMaxWidth(), color = AccentBlue, trackColor = DarkBorder
                        )
                        Text(state.bulkProgressText, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (state.bulkMode == OcBulkMode.SINGLE && state.expiries.isNotEmpty()) {
            item {
                SectionHeader("EXPIRY")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.expiries) { exp ->
                        SelectionChip(exp, state.selectedExpiry == exp) { viewModel.selectExpiry(exp) }
                    }
                }
            }
            item {
                SectionHeader("STRIKES EACH SIDE OF ATM")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OPTION_CHAIN_SPREAD_COUNTS.forEach { n ->
                        SelectionChip("\u00b1$n", state.spreadCount == n) { viewModel.setSpreadCount(n) }
                    }
                }
            }
            item {
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
            }

            if (state.lastUpdatedLabel.isNotEmpty()) {
                item { Text(state.lastUpdatedLabel, color = TextMuted, style = MaterialTheme.typography.bodySmall) }
            }

            item {
                OutlinedButton(
                    onClick = {
                        val folder = SaveFolder.current(context)
                        if (folder != null) viewModel.exportVisibleChain(folder)
                        else folderLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
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
        }

        state.exportMessage?.let {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF001A00)), modifier = Modifier.fillMaxWidth()) {
                    Text(it, color = AccentGreen, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        state.exportError?.let {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0000)), modifier = Modifier.fillMaxWidth()) {
                    Text(it, color = AccentRed, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (state.bulkMode == OcBulkMode.SINGLE) {
            state.selectedContract?.let { contract ->
                item {
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
            }

            if (state.isLoading && state.rows.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                }
            }

            state.error?.let {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0000)), modifier = Modifier.fillMaxWidth()) {
                        Text(it, color = AccentRed, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (state.rows.isNotEmpty()) {
                stickyHeaderRow()
                items(state.rows) { row ->
                    Column {
                        OptionChainRowView(row, viewModel, state.selectedContract, state.selectedSide)
                        Divider(color = DarkBorder, thickness = 0.5.dp)
                    }
                }
            } else if (!state.isLoading && state.error == null && state.expiries.isNotEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No data yet", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.stickyHeaderRow() {
    item {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("CALLS (CE)", color = AccentGreen, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("STRIKE", color = TextSecondary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("PUTS (PE)", color = AccentRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
            Divider(color = DarkBorder)
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
