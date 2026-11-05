package com.marketdata.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import com.marketdata.app.data.repository.KiteRepository
import com.marketdata.app.ui.components.InfoBanner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.marketdata.app.ui.theme.*
import com.marketdata.app.viewmodel.AuthViewModel

/** Step-by-step guide for getting the Kite, Claude and Gemini keys (edit docs/GETTING_STARTED.md to change it, no app update needed). */
private const val KEY_GUIDE_URL = "https://github.com/hemanthdvng/MarketDataApp/blob/main/docs/GETTING_STARTED.md"

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onLoginClick: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showApiSecret by remember { mutableStateOf(false) }
    var claudeKey by remember { mutableStateOf(viewModel.getClaudeKey()) }
    var geminiKey by remember { mutableStateOf(viewModel.getGeminiKey()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        // Header
        Text("MarketData", style = MaterialTheme.typography.headlineLarge,
            color = AccentBlue)
        Text("Indian market data, scanner and AI assistant", style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary)

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder, MaterialTheme.shapes.medium)
        ) {
            Text(
                "Independent tool, not affiliated with NSE or Zerodha. You use your own Kite Connect " +
                    "and AI API keys. Market data, pattern signals and AI replies are for information " +
                    "and education only. They are not investment advice and may be wrong. Trading " +
                    "involves risk of loss.",
                color = TextSecondary,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }

        val uriHandler = LocalUriHandler.current
        OutlinedButton(
            onClick = { runCatching { uriHandler.openUri(KEY_GUIDE_URL) } },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
        ) {
            Icon(Icons.Default.HelpOutline, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("HOW TO GET YOUR KEYS (STEP BY STEP)")
        }

        Spacer(Modifier.height(8.dp))

        // Logged in status
        if (state.isLoggedIn) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1F0A)),
                modifier = Modifier.fillMaxWidth().border(1.dp, AccentGreen, MaterialTheme.shapes.medium)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Connected", color = AccentGreen, style = MaterialTheme.typography.titleLarge)
                        Text(state.userName, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { viewModel.logout() }) {
                        Text("Logout", color = AccentRed)
                    }
                }
            }
        }

        if (state.isLoggedIn) {
            when {
                state.isCheckingAccess ->
                    InfoBanner(text = "Checking what your Kite plan allows...", accent = AccentBlue)
                state.dataAccess == KiteRepository.DataAccess.OK ->
                    InfoBanner(
                        title = "MARKET DATA AVAILABLE",
                        text = "Your Kite plan can read market data, so Live, Data and Options should work.",
                        accent = AccentGreen
                    )
                state.dataAccess == KiteRepository.DataAccess.NO_PLAN ->
                    InfoBanner(
                        title = "FREE KITE PLAN: NO MARKET DATA",
                        text = "You are logged in, but Zerodha's free Personal API does not include market " +
                            "data. Live, Data and Options will show an error until you move to the paid " +
                            "Kite Connect plan (about Rs 500 per month). Login, Files and the AI tab " +
                            "still work, and Scan works on files you already have."
                    )
                state.dataAccess == KiteRepository.DataAccess.NO_HISTORICAL ->
                    InfoBanner(
                        title = "NO HISTORICAL DATA ON YOUR PLAN",
                        text = "Live prices work, but your Kite plan does not include historical data, " +
                            "so the Data tab and live scans will show an error until you move to the " +
                            "paid Kite Connect plan."
                    )
                state.dataAccess == KiteRepository.DataAccess.SESSION_EXPIRED ->
                    InfoBanner(
                        title = "LOGIN EXPIRED",
                        text = "Your Kite login has expired (it lasts one day). Tap REFRESH TOKEN below.",
                        accent = AccentRed
                    )
                state.dataAccess == KiteRepository.DataAccess.UNKNOWN ->
                    InfoBanner(
                        text = "Could not check your Kite plan right now. Check your internet and tap " +
                            "CHECK MY PLAN to try again."
                    )
            }
            TextButton(onClick = { viewModel.checkDataAccess() }, enabled = !state.isCheckingAccess) {
                Text("CHECK MY PLAN AGAIN", color = AccentBlue)
            }
        }

        // Kite API Section
        SectionHeader("KITE CONNECT (needed for Live, Download, Options and Scan)")

        AppTextField(
            label = "API Key",
            value = state.apiKey,
            onValueChange = { viewModel.updateApiKey(it) },
            placeholder = "Your Kite Connect API key"
        )

        AppTextField(
            label = "API Secret",
            value = state.apiSecret,
            onValueChange = { viewModel.updateApiSecret(it) },
            placeholder = "••••••••••••••••",
            isPassword = true,
            showPassword = showApiSecret,
            onTogglePassword = { showApiSecret = !showApiSecret }
        )

        if (!state.isLoggedIn) {
            Button(
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                enabled = state.apiKey.isNotEmpty() && state.apiSecret.isNotEmpty() && !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("LOGIN WITH KITE")
                }
            }
        } else {
            OutlinedButton(
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("REFRESH TOKEN")
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.refreshInstruments() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                enabled = !state.isRefreshingInstruments
            ) {
                if (state.isRefreshingInstruments) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("REFRESH INSTRUMENT LIST", color = Color.Black)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // AI APIs Section
        SectionHeader("AI AGENT KEYS (optional, only for the AI tab)")

        AppTextField(
            label = "Claude API Key (Anthropic)",
            value = claudeKey,
            onValueChange = { claudeKey = it; viewModel.updateClaudeKey(it) },
            placeholder = "sk-ant-...",
            isPassword = true
        )

        AppTextField(
            label = "Gemini API Key (Google)",
            value = geminiKey,
            onValueChange = { geminiKey = it; viewModel.updateGeminiKey(it) },
            placeholder = "AIza...",
            isPassword = true
        )

        // Error / Success
        state.error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0000)),
                modifier = Modifier.fillMaxWidth()) {
                Text(it, color = AccentRed, modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        state.successMessage?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF001A00)),
                modifier = Modifier.fillMaxWidth()) {
                Text(it, color = AccentGreen, modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.bodySmall, color = AccentBlue,
        modifier = Modifier.padding(top = 8.dp))
    Divider(color = DarkBorder)
}

@Composable
fun AppTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        placeholder = { Text(placeholder, color = TextMuted, style = MaterialTheme.typography.bodySmall) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (isPassword && !showPassword)
            PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = if (isPassword && onTogglePassword != null) {
            {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null, tint = TextSecondary
                    )
                }
            }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentBlue,
            unfocusedBorderColor = DarkBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = AccentBlue,
            focusedLabelColor = AccentBlue,
            unfocusedLabelColor = TextSecondary
        )
    )
}
