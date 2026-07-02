package com.marketdata.app.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marketdata.app.data.models.SelectionType
import com.marketdata.app.ui.components.AppDatePickerDialog
import com.marketdata.app.ui.theme.*
import com.marketdata.app.util.Extensions
import com.marketdata.app.util.NiftySymbols
import com.marketdata.app.viewmodel.DownloadViewModel
import com.marketdata.app.viewmodel.OPTION_CHAIN_UNDERLYINGS
import com.marketdata.app.viewmodel.OptionChainViewModel

@Composable
fun DownloadScreen(viewModel: DownloadViewModel, optionChainViewModel: OptionChainViewModel) {
    val state by viewModel.state.collectAsState()
    val ocState by optionChainViewModel.state.collectAsState()
    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.setFolder(uri, uri.lastPathSegment ?: "Selected")
            }
        }
    }
    val ocFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                optionChainViewModel.downloadFullChain(uri)
            }
        }
    }
    LaunchedEffect(Unit) {
        if (ocState.expiries.isEmpty()) optionChainViewModel.loadExpiries()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("DOWNLOAD HISTORICAL DATA", style = MaterialTheme.typography.headlineMedium, color = AccentBlue)

        // Selection Type
        SectionHeader("SELECTION TYPE")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            SelectionChip("Single", state.selectionType == SelectionType.SINGLE) { viewModel.setSelectionType(SelectionType.SINGLE) }
            SelectionChip("Multi", state.selectionType == SelectionType.MULTI) { viewModel.setSelectionType(SelectionType.MULTI) }
            SelectionChip("Index", state.selectionType == SelectionType.INDEX) { viewModel.setSelectionType(SelectionType.INDEX) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            SelectionChip("Nifty 50", state.selectionType == SelectionType.NIFTY50) { viewModel.setSelectionType(SelectionType.NIFTY50) }
            SelectionChip("Nifty 100", state.selectionType == SelectionType.NIFTY100) { viewModel.setSelectionType(SelectionType.NIFTY100) }
        }

        // Dynamic input based on selection
        when (state.selectionType) {
            SelectionType.SINGLE -> {
                if (state.singleSymbol.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "✓ ${state.singleSymbol}",
                                color = AccentGreen,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { viewModel.clearSingleSymbol() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = AccentRed)
                        }
                    }
                } else {
                    AppTextField(
                        label = "Search stock symbol",
                        value = state.symbolSearchQuery,
                        onValueChange = { viewModel.searchSymbols(it) },
                        placeholder = "Type to search e.g. RELIANCE"
                    )
                    SymbolSuggestionList(
                        results = state.symbolSearchResults,
                        onSelect = { viewModel.selectSingleSymbol(it) }
                    )
                }
            }
            SelectionType.MULTI -> {
                AppTextField(
                    label = "Search & add stocks",
                    value = state.symbolSearchQuery,
                    onValueChange = { viewModel.searchSymbols(it) },
                    placeholder = "Type to search e.g. TCS, INFY..."
                )
                SymbolSuggestionList(
                    results = state.symbolSearchResults,
                    onSelect = { viewModel.addMultiSymbol(it) }
                )
                if (state.selectedMultiSymbols.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${state.selectedMultiSymbols.size} selected", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { viewModel.clearMultiSymbols() }) {
                            Text("Clear all", color = AccentRed, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    SymbolChipFlow(
                        symbols = state.selectedMultiSymbols,
                        onRemove = { viewModel.removeMultiSymbol(it) }
                    )
                }
            }
            SelectionType.INDEX -> {
                Text("Select Index:", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(NiftySymbols.INDEX_TOKENS.keys.toList()) { idx ->
                        SelectionChip(idx, state.selectedIndex == idx) { viewModel.setSelectedIndex(idx) }
                    }
                }
            }
            SelectionType.NIFTY50 -> {
                InfoCard("📊 ${NiftySymbols.NIFTY_50.size} stocks will be downloaded (Nifty 50 constituents)")
            }
            SelectionType.NIFTY100 -> {
                InfoCard("📊 ${NiftySymbols.NIFTY_100.size} stocks will be downloaded (Nifty 100 constituents)")
            }
        }

        // Interval
        SectionHeader("INTERVAL")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(NiftySymbols.INTERVALS) { idx, interval ->
                SelectionChip(interval.first, state.selectedInterval == idx) { viewModel.setInterval(idx) }
            }
        }

        // Date Range
        SectionHeader("DATE RANGE")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DateBox("From", Extensions.formatDate(state.fromDate), Modifier.weight(1f)) { showFromDatePicker = true }
            DateBox("To", Extensions.formatDate(state.toDate), Modifier.weight(1f)) { showToDatePicker = true }
        }

        // OI Toggle
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(
                checked = state.includeOI,
                onCheckedChange = { viewModel.setIncludeOI(it) },
                colors = CheckboxDefaults.colors(checkedColor = AccentBlue)
            )
            Text("Include Open Interest (OI) data", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
        }
        Text("Note: OI only available for F&O instruments", color = TextMuted, style = MaterialTheme.typography.bodySmall)

        // Folder selection
        SectionHeader("SAVE LOCATION")
        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                folderLauncher.launch(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (state.folderUri == null) "SELECT FOLDER" else "📁 ${state.folderName}")
        }

        // Download Button
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { viewModel.startDownload() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            enabled = !state.isDownloading
        ) {
            if (state.isDownloading) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("DOWNLOAD CSV", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        // Progress
        if (state.isDownloading) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
                color = AccentBlue,
                trackColor = DarkBorder
            )
            Text(state.progressText, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }

        state.error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0000)), modifier = Modifier.fillMaxWidth()) {
                Text(it, color = AccentRed, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        state.successMessage?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF001A00)), modifier = Modifier.fillMaxWidth()) {
                Text(it, color = AccentGreen, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        // ---- Option Chain download (separate from the candle/history download above) ----
        SectionHeader("OPTION CHAIN")
        Text(
            "Downloads every strike of the full chain for one underlying + expiry (not just the on-screen window on the Options tab).",
            color = TextMuted, style = MaterialTheme.typography.bodySmall
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(OPTION_CHAIN_UNDERLYINGS) { name ->
                SelectionChip(name, ocState.underlying == name) { optionChainViewModel.selectUnderlying(name) }
            }
        }
        if (ocState.expiries.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ocState.expiries) { exp ->
                    SelectionChip(exp, ocState.selectedExpiry == exp) { optionChainViewModel.selectExpiry(exp) }
                }
            }
        } else if (ocState.isLoading) {
            Text("Loading expiries...", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                ocFolderLauncher.launch(intent)
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            enabled = ocState.selectedExpiry.isNotBlank() && !ocState.isExporting
        ) {
            if (ocState.isExporting) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("DOWNLOAD FULL OPTION CHAIN CSV")
            }
        }
        ocState.exportMessage?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF001A00)), modifier = Modifier.fillMaxWidth()) {
                Text(it, color = AccentGreen, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        ocState.exportError?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0000)), modifier = Modifier.fillMaxWidth()) {
                Text(it, color = AccentRed, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    if (showFromDatePicker) {
        AppDatePickerDialog(
            initialDate = state.fromDate,
            onDismiss = { showFromDatePicker = false },
            onDateSelected = { viewModel.setFromDate(it) }
        )
    }
    if (showToDatePicker) {
        AppDatePickerDialog(
            initialDate = state.toDate,
            onDismiss = { showToDatePicker = false },
            onDateSelected = { viewModel.setToDate(it) }
        )
    }
}

@Composable
fun SelectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentBlue,
            selectedLabelColor = Color.White,
            containerColor = DarkCard,
            labelColor = TextSecondary
        )
    )
}

@Composable
fun DateBox(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier = modifier) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                .background(DarkCard, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(12.dp)
        ) {
            Text(value, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun InfoCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = TextSecondary, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SymbolSuggestionList(results: List<String>, onSelect: (String) -> Unit) {
    if (results.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
    ) {
        LazyColumn {
            items(results) { symbol ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(symbol) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(symbol, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                }
                Divider(color = DarkBorder, thickness = 0.5.dp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SymbolChipFlow(symbols: List<String>, onRemove: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        symbols.forEach { symbol ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(AccentBlue.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .border(1.dp, AccentBlue, RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(symbol, color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = AccentRed,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onRemove(symbol) }
                )
            }
        }
    }
}
