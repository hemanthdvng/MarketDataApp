package com.marketdata.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marketdata.app.data.models.AgentMessage
import com.marketdata.app.data.models.AiModelOption
import com.marketdata.app.data.models.AiModels
import com.marketdata.app.data.models.AiProvider
import com.marketdata.app.data.models.ThinkingLevel
import com.marketdata.app.ui.theme.*
import com.marketdata.app.viewmodel.AgentViewModel
import kotlinx.coroutines.launch

@Composable
fun AgentScreen(viewModel: AgentViewModel) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.attachFile(it) } }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(state.messages.size - 1) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AI AGENT", style = MaterialTheme.typography.headlineMedium, color = AccentBlue)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showSettings = !showSettings }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
            }
            IconButton(onClick = { viewModel.clearChat() }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Clear", tint = TextSecondary)
            }
        }

        // Model selector
        var modelMenuExpanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                OutlinedButton(
                    onClick = { modelMenuExpanded = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(state.selectedModel.label, style = MaterialTheme.typography.bodySmall)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false },
                    modifier = Modifier.background(DarkCard)
                ) {
                    Text("CLAUDE", color = TextMuted, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    AiModels.CLAUDE_OPTIONS.forEach { option ->
                        ModelMenuItem(option, state.selectedModel.id == option.id) {
                            viewModel.setModel(option); modelMenuExpanded = false
                        }
                    }
                    Divider(color = DarkBorder)
                    Text("GEMINI", color = TextMuted, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    AiModels.GEMINI_OPTIONS.forEach { option ->
                        ModelMenuItem(option, state.selectedModel.id == option.id) {
                            viewModel.setModel(option); modelMenuExpanded = false
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (state.fetchingQuotes) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = AccentAmber)
                    Spacer(Modifier.width(4.dp))
                    Text("Live data...", color = AccentAmber, style = MaterialTheme.typography.bodySmall)
                }
            } else if (state.liveQuotes.isNotEmpty()) {
                Text("📡 ${state.liveQuotes.size} symbols", color = AccentGreen, style = MaterialTheme.typography.bodySmall)
            }
        }
        state.selectedModel.note?.let {
            Text("⚠ $it", color = AccentAmber, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp))
        }

        if (showSettings) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Context symbols (for agent's live data):", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    AppTextField(
                        label = "Symbols",
                        value = state.symbolsForContext,
                        onValueChange = { viewModel.setContextSymbols(it) },
                        placeholder = "NIFTY 50,RELIANCE,TCS"
                    )
                    Spacer(Modifier.height(6.dp))
                    Row {
                        OutlinedButton(
                            onClick = { viewModel.useNifty50Context() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("NIFTY 50", style = MaterialTheme.typography.labelSmall) }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.useNifty100Context() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("NIFTY 100", style = MaterialTheme.typography.labelSmall) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = state.autoFetchQuotes,
                            onCheckedChange = { viewModel.setAutoFetch(it) },
                            colors = CheckboxDefaults.colors(checkedColor = AccentBlue)
                        )
                        Text("Auto-fetch live quotes for context", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { viewModel.refreshQuotes() }) {
                        Text("🔄 Refresh quotes now", color = AccentBlue, style = MaterialTheme.typography.bodySmall)
                    }

                    if (state.selectedModel.provider == AiProvider.GEMINI) {
                        Divider(color = DarkBorder, modifier = Modifier.padding(vertical = 8.dp))
                        Text("Thinking mode (Gemini):", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Row {
                            ThinkingLevel.entries.forEach { level ->
                                val selected = state.thinkingLevel == level
                                OutlinedButton(
                                    onClick = { viewModel.setThinkingLevel(level) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (selected) Color.White else AccentBlue,
                                        containerColor = if (selected) AccentBlue else Color.Transparent
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) { Text(level.label, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        Text(
                            "Higher = deeper reasoning but slower and more tokens.",
                            color = TextMuted, style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Divider(color = DarkBorder, modifier = Modifier.padding(vertical = 8.dp))
                    Text("Agent instructions (optional, saved on this device):", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = state.customInstructions,
                        onValueChange = { viewModel.setCustomInstructions(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. Focus on options strategies. Keep answers under 100 words.", color = TextMuted, style = MaterialTheme.typography.bodySmall) },
                        minLines = 2,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            }
        }

        Divider(color = DarkBorder, modifier = Modifier.padding(top = 8.dp))

        // Messages
        if (state.messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🤖", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Ask me about stocks, trends, or recommendations", color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium)
                    Text("e.g. \"Which Nifty 50 stocks look bullish today?\"", color = TextMuted,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(state.messages) { msg -> MessageBubble(msg) }
                if (state.isLoading) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AccentBlue, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Analyzing...", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        state.error?.let {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp, 0.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0000))
            ) {
                Text(it, color = AccentRed, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        // Pending attachment preview
        val pendingBinary = state.pendingBinaryAttachment
        val pendingText = state.pendingTextAttachment
        if (pendingBinary != null || pendingText != null || state.isReadingFile) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.isReadingFile) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AccentBlue)
                    Spacer(Modifier.width(6.dp))
                    Text("Reading file...", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                } else {
                    Icon(Icons.Default.AttachFile, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        pendingBinary?.displayName ?: pendingText?.displayName ?: "",
                        color = TextPrimary, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.clearPendingAttachment() }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = AccentRed, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Input bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = {
                filePicker.launch(arrayOf("image/*", "text/csv", "text/plain", "application/pdf"))
            }) {
                Icon(Icons.Default.AttachFile, contentDescription = "Attach file", tint = TextSecondary)
            }
            OutlinedTextField(
                value = state.inputText,
                onValueChange = { viewModel.setInputText(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about stocks...", color = TextMuted) },
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { viewModel.sendMessage() },
                modifier = Modifier
                    .size(48.dp)
                    .background(AccentBlue, RoundedCornerShape(12.dp)),
                enabled = !state.isLoading &&
                    (state.inputText.isNotBlank() || pendingBinary != null || pendingText != null)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}

@Composable
fun ModelMenuItem(option: AiModelOption, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    option.label,
                    color = if (selected) AccentBlue else TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
                option.note?.let {
                    Text(it, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        onClick = onClick,
        trailingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp)) }
        } else null
    )
}

@Composable
fun MessageBubble(msg: AgentMessage) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) AccentBlue.copy(alpha = 0.15f) else DarkCard
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                if (!isUser) {
                    Text("🤖 AGENT", color = AccentBlue, style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                }
                msg.attachment?.let { att ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(att.displayName, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(msg.content, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
