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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.marketdata.app.ui.theme.*
import com.marketdata.app.viewmodel.AuthViewModel

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
        Text("⚡ NIFTYBOT", style = MaterialTheme.typography.headlineLarge,
            color = AccentBlue)
        Text("Market Data Terminal", style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary)

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

        // Kite API Section
        SectionHeader("KITE CONNECT")

        AppTextField(
            label = "API Key",
            value = state.apiKey,
            onValueChange = { viewModel.updateApiKey(it) },
            placeholder = "6ju6ony28o03f742"
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
        }

        Spacer(Modifier.height(8.dp))

        // AI APIs Section
        SectionHeader("AI AGENT KEYS")

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
