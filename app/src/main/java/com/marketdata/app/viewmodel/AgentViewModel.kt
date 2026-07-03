package com.marketdata.app.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marketdata.app.data.models.AgentAttachment
import com.marketdata.app.data.models.AgentMessage
import com.marketdata.app.data.models.AiModelOption
import com.marketdata.app.data.models.AiModels
import com.marketdata.app.data.models.LiveQuoteDisplay
import com.marketdata.app.data.models.OptionChainRow
import com.marketdata.app.data.models.ThinkingLevel
import com.marketdata.app.data.prefs.SecurePrefs
import com.marketdata.app.data.repository.AgentRepository
import com.marketdata.app.data.repository.KiteRepository
import com.marketdata.app.util.NiftySymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A file the user picked but hasn't sent yet. Images/PDFs go out as a real
 *  multimodal block (see AgentRepository); text/CSV is simpler to just fold
 *  into the message's own text at send time, so it's kept separately here. */
data class PendingTextAttachment(val displayName: String, val content: String)

data class AgentState(
    val messages: List<AgentMessage> = emptyList(),
    val inputText: String = "",
    val selectedModel: AiModelOption = AiModels.DEFAULT,
    val isLoading: Boolean = false,
    val error: String? = null,
    val autoFetchQuotes: Boolean = true,
    val liveQuotes: List<LiveQuoteDisplay> = emptyList(),
    val fetchingQuotes: Boolean = false,
    val symbolsForContext: String = "NIFTY 50,NIFTY BANK,RELIANCE,TCS,HDFCBANK,INFY,ICICIBANK",
    val thinkingLevel: ThinkingLevel = ThinkingLevel.MEDIUM,
    val customInstructions: String = "",
    val pendingBinaryAttachment: AgentAttachment? = null,
    val pendingTextAttachment: PendingTextAttachment? = null,
    val isReadingFile: Boolean = false,
    // Option chain context (off by default - the chain table can be large,
    // so this is opt-in rather than auto-included like live quotes).
    val includeOptionChain: Boolean = false,
    val optionChainRows: List<OptionChainRow> = emptyList(),
    val optionChainUnderlying: String = "",
    val optionChainExpiry: String = "",
    val optionChainSpot: Double = 0.0
)

class AgentViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = SecurePrefs.getInstance(app)
    private val agentRepo = AgentRepository(prefs)
    private val kiteRepo = KiteRepository(app)

    private val _state = MutableStateFlow(AgentState(customInstructions = prefs.agentCustomInstructions))
    val state = _state.asStateFlow()

    fun setInputText(t: String) = update { copy(inputText = t) }
    fun setModel(m: AiModelOption) = update { copy(selectedModel = m) }
    fun setAutoFetch(v: Boolean) = update { copy(autoFetchQuotes = v) }
    fun setContextSymbols(s: String) = update { copy(symbolsForContext = s.uppercase()) }
    fun useNifty50Context() = update { copy(symbolsForContext = NiftySymbols.NIFTY_50.joinToString(",")) }
    fun useNifty100Context() = update { copy(symbolsForContext = NiftySymbols.NIFTY_100.joinToString(",")) }
    fun setThinkingLevel(l: ThinkingLevel) = update { copy(thinkingLevel = l) }
    fun setIncludeOptionChain(v: Boolean) = update { copy(includeOptionChain = v) }

    /** Called from AgentScreen (via LaunchedEffect on the Options tab's state)
     *  to keep a snapshot of whatever chain is currently loaded there, so it
     *  can be folded into the system prompt when includeOptionChain is on.
     *  Kept as a plain snapshot rather than a live reference to the other
     *  ViewModel, since AgentViewModel and OptionChainViewModel are
     *  independently scoped and shouldn't hold references to each other. */
    fun setOptionChainContext(rows: List<OptionChainRow>, underlying: String, expiry: String, spot: Double) {
        update { copy(optionChainRows = rows, optionChainUnderlying = underlying, optionChainExpiry = expiry, optionChainSpot = spot) }
    }
    fun setCustomInstructions(s: String) {
        prefs.agentCustomInstructions = s
        update { copy(customInstructions = s) }
    }
    fun clearError() = update { copy(error = null) }
    fun clearChat() = update { copy(messages = emptyList(), liveQuotes = emptyList()) }
    fun clearPendingAttachment() = update { copy(pendingBinaryAttachment = null, pendingTextAttachment = null) }

    /** Reads a file the user picked from the chat's attach button. Images and
     *  PDFs are base64-encoded for a real multimodal request; anything else
     *  is read as plain text (CSV, txt) and merged into the message text on
     *  send. 15MB cap on binary files and ~30k chars on text - generous for
     *  a chart screenshot or a downloaded CSV, but keeps the request body
     *  (and Claude/Gemini's own per-request limits) from being blown past by
     *  an accidental huge file pick. */
    fun attachFile(uri: Uri) {
        viewModelScope.launch {
            update { copy(isReadingFile = true, error = null) }
            try {
                val resolver = getApplication<Application>().contentResolver
                val mimeType = resolver.getType(uri) ?: "application/octet-stream"
                val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "file"

                if (mimeType.startsWith("image/") || mimeType == "application/pdf") {
                    val bytes = withContext(Dispatchers.IO) {
                        resolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                    if (bytes == null) {
                        update { copy(isReadingFile = false, error = "Couldn't read that file") }
                        return@launch
                    }
                    if (bytes.size > 15 * 1024 * 1024) {
                        update { copy(isReadingFile = false, error = "File too large (max 15MB)") }
                        return@launch
                    }
                    val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    update {
                        copy(
                            isReadingFile = false,
                            pendingBinaryAttachment = AgentAttachment(displayName, mimeType, b64),
                            pendingTextAttachment = null
                        )
                    }
                } else {
                    val text = withContext(Dispatchers.IO) {
                        resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }
                    if (text == null) {
                        update { copy(isReadingFile = false, error = "Couldn't read that file") }
                        return@launch
                    }
                    val trimmed = if (text.length > 30_000) text.take(30_000) + "\n...[truncated]" else text
                    update {
                        copy(
                            isReadingFile = false,
                            pendingTextAttachment = PendingTextAttachment(displayName, trimmed),
                            pendingBinaryAttachment = null
                        )
                    }
                }
            } catch (e: Exception) {
                update { copy(isReadingFile = false, error = "Couldn't read file: ${e.message}") }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            }
        } catch (e: Exception) { null }
    }

    fun sendMessage() {
        val typed = _state.value.inputText.trim()
        val binaryAttachment = _state.value.pendingBinaryAttachment
        val textAttachment = _state.value.pendingTextAttachment
        if (typed.isEmpty() && binaryAttachment == null && textAttachment == null) return

        val model = _state.value.selectedModel
        val hasKey = when (model.provider) {
            com.marketdata.app.data.models.AiProvider.CLAUDE -> prefs.claudeApiKey.isNotEmpty()
            com.marketdata.app.data.models.AiProvider.GEMINI -> prefs.geminiApiKey.isNotEmpty()
        }
        if (!hasKey) {
            update { copy(error = "No ${model.provider} API key set. Add it on the Login tab.") }
            return
        }

        // Text/CSV attachments have no dedicated API block - fold them into
        // the message text itself, ahead of whatever the user typed.
        val finalContent = if (textAttachment != null) {
            val caption = if (typed.isNotEmpty()) "\n\n$typed" else ""
            "Attached file (${textAttachment.displayName}):\n\n${textAttachment.content}$caption"
        } else typed

        val userMsg = AgentMessage(role = "user", content = finalContent, attachment = binaryAttachment)
        update {
            copy(
                messages = messages + userMsg, inputText = "", isLoading = true, error = null,
                pendingBinaryAttachment = null, pendingTextAttachment = null
            )
        }

        viewModelScope.launch {
            var quotes = _state.value.liveQuotes

            // Auto-fetch live quotes if enabled
            if (_state.value.autoFetchQuotes && quotes.isEmpty()) {
                update { copy(fetchingQuotes = true) }
                val symbols = _state.value.symbolsForContext
                    .split(",").map { it.trim() }.filter { it.isNotBlank() }

                val result = kiteRepo.fetchQuotes(symbols)
                result.onSuccess { q ->
                    quotes = q
                    update { copy(liveQuotes = q, fetchingQuotes = false) }
                }
                result.onFailure {
                    update { copy(fetchingQuotes = false) }
                }
            }

            val optionChainSummary = if (_state.value.includeOptionChain) buildOptionChainSummary(_state.value) else null

            val result = agentRepo.call(
                model = _state.value.selectedModel,
                messages = _state.value.messages,
                liveQuotes = quotes.ifEmpty { null },
                customInstructions = _state.value.customInstructions.ifBlank { null },
                thinkingLevel = _state.value.thinkingLevel,
                optionChainSummary = optionChainSummary
            )

            result.fold(
                onSuccess = { response ->
                    val assistantMsg = AgentMessage(role = "assistant", content = response)
                    update { copy(messages = messages + assistantMsg, isLoading = false) }
                },
                onFailure = { e ->
                    val msg = e.message?.takeIf { it.isNotBlank() } ?: "AI error (${e::class.java.simpleName})"
                    update { copy(isLoading = false, error = msg) }
                }
            )
        }
    }

    fun refreshQuotes() {
        viewModelScope.launch {
            update { copy(fetchingQuotes = true) }
            val symbols = _state.value.symbolsForContext
                .split(",").map { it.trim() }.filter { it.isNotBlank() }
            val result = kiteRepo.fetchQuotes(symbols)
            result.onSuccess { q -> update { copy(liveQuotes = q, fetchingQuotes = false) } }
            result.onFailure { update { copy(fetchingQuotes = false) } }
        }
    }

    private fun update(block: AgentState.() -> AgentState) {
        _state.value = _state.value.block()
    }

    /** Formats the currently-loaded option chain (from the Options tab) as
     *  compact text for the AI's system prompt. Kept plain/tabular rather
     *  than JSON since that's both fewer tokens and easier for the model to
     *  read directly. Returns null if nothing's loaded yet, so the caller
     *  doesn't inject an empty "OPTION CHAIN DATA:" section for no reason. */
    private fun buildOptionChainSummary(s: AgentState): String? {
        if (s.optionChainRows.isEmpty()) return null
        val sb = StringBuilder()
        sb.append("Underlying: ${s.optionChainUnderlying}, Expiry: ${s.optionChainExpiry}, Spot: \u20b9${"%.2f".format(s.optionChainSpot)}\n")
        sb.append("Strike | CE_LTP | CE_OI | CE_Vol | PE_LTP | PE_OI | PE_Vol\n")
        for (r in s.optionChainRows) {
            val atmTag = if (r.isAtm) " (ATM)" else ""
            sb.append("${"%.0f".format(r.strike)}$atmTag | ${r.ceLtp} | ${r.ceOi} | ${r.ceVolume} | ${r.peLtp} | ${r.peOi} | ${r.peVolume}\n")
        }
        return sb.toString()
    }
}
