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
import com.marketdata.app.ui.theme.*
import com.marketdata.app.viewmodel.LiveQuotesViewModel

@Composable
fun LiveQuotesScreen(viewModel: LiveQuotesViewModel) {
    val state by viewModel.state.collectAsState()
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
                viewModel.saveToCSV(uri)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground).padding(16.dp)) {
        Text("LIVE QUOTES", style = MaterialTheme.typography.headlineMedium, color = AccentBlue)
        Spacer(Modifier.height(12.dp))

        AppTextField(
            label = "Symbols (comma separated)",
            value = state.symbolInput,
            onValueChange = { viewModel.setSymbolInput(it) },
            placeholder = "RELIANCE,TCS,INFY"
        )

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { viewModel.fetchQuotes() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                else { Icon(Icons.Default.Refresh, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("FETCH") }
            }
            OutlinedButton(
                onClick = { viewModel.loadNifty50() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber)
            ) {
                Text("NIFTY 50")
            }
        }

        if (state.quotes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    folderLauncher.launch(intent)
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

        if (state.quotes.isNotEmpty()) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("SYMBOL", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1.3f))
                Text("LTP", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                Text("CHG%", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                Text("VOL", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
            }
            Divider(color = DarkBorder)

            LazyColumn {
                items(state.quotes) { quote ->
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
