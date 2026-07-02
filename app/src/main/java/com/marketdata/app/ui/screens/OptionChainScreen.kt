package com.marketdata.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.marketdata.app.data.models.OptionChainRow
import com.marketdata.app.ui.theme.*
import com.marketdata.app.viewmodel.OPTION_CHAIN_REFRESH_INTERVALS
import com.marketdata.app.viewmodel.OPTION_CHAIN_SPREAD_COUNTS
import com.marketdata.app.viewmodel.OPTION_CHAIN_UNDERLYINGS
import com.marketdata.app.viewmodel.OptionChainViewModel

@Composable
fun OptionChainScreen(viewModel: OptionChainViewModel) {
    val state by viewModel.state.collectAsState()

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
                    OptionChainRowView(row)
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
private fun OptionChainRowView(row: OptionChainRow) {
    val bg = if (row.isAtm) AccentBlue.copy(alpha = 0.12f) else Color.Transparent
    Column(Modifier.fillMaxWidth().background(bg).padding(vertical = 6.dp, horizontal = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
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
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
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
