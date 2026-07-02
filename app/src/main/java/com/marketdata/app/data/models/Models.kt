package com.marketdata.app.data.models

data class KiteCandle(
    val timestamp: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val oi: Long = 0L
)

data class HistoricalResponse(
    val status: String,
    val data: HistoricalData?
)

data class HistoricalData(
    val candles: List<List<Any>>
)

data class QuoteResponse(
    val status: String,
    val data: Map<String, QuoteData>?
)

data class QuoteData(
    val instrument_token: Long = 0L,
    val timestamp: String = "",
    val last_price: Double = 0.0,
    val last_quantity: Long = 0L,
    val buy_quantity: Long = 0L,
    val sell_quantity: Long = 0L,
    val volume: Long = 0L,
    val average_price: Double = 0.0,
    val oi: Double = 0.0,
    val oi_day_high: Double = 0.0,
    val oi_day_low: Double = 0.0,
    val net_change: Double = 0.0,
    val ohlc: OhlcData? = null,
    val depth: DepthData? = null
)

data class DepthLevel(
    val price: Double = 0.0,
    val quantity: Long = 0L,
    val orders: Int = 0
)

data class DepthData(
    val buy: List<DepthLevel> = emptyList(),
    val sell: List<DepthLevel> = emptyList()
)

data class OhlcData(
    val open: Double = 0.0,
    val high: Double = 0.0,
    val low: Double = 0.0,
    val close: Double = 0.0
)

data class SessionResponse(
    val status: String,
    val data: SessionData?,
    val message: String? = null
)

data class SessionData(
    val access_token: String = "",
    val user_id: String = "",
    val user_name: String = "",
    val email: String = ""
)

/** A binary attachment (image or PDF) on a chat message, sent as an inline
 *  base64 block to whichever provider is selected. Text/CSV attachments
 *  don't use this - they're simpler to just fold into the message's own
 *  text content (see AgentViewModel.attachFile). */
data class AgentAttachment(
    val displayName: String,
    val mimeType: String,
    val base64Data: String
)

data class AgentMessage(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachment: AgentAttachment? = null
)

data class LiveQuoteDisplay(
    val symbol: String,
    val ltp: Double,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val change: Double,
    val changePct: Double,
    val volume: Long,
    val oi: Double
)

enum class SelectionType { SINGLE, MULTI, INDEX, NIFTY50, NIFTY100 }
enum class AiProvider { CLAUDE, GEMINI }

/**
 * Gemini's "thinking" control. As of the Gemini 3.x model generation, the API
 * replaced the old integer thinkingConfig.thinkingBudget with a string enum
 * thinkingConfig.thinkingLevel (docs: ai.google.dev/gemini-api/docs/thinking).
 * Sending the old integer field to a 3.x model is deprecated and unreliable.
 *
 * MINIMAL is intentionally omitted here: it's only valid on some Gemini 3.x
 * models (Flash / Flash-Lite) and returns a 400 on gemini-3.1-pro-preview,
 * so LOW is used as the universal "cheapest/fastest" option across every
 * model configured in AiModels.GEMINI_OPTIONS.
 */
enum class ThinkingLevel(val apiValue: String, val label: String) {
    LOW("LOW", "Low"),
    MEDIUM("MEDIUM", "Medium"),
    HIGH("HIGH", "High")
}

data class AiModelOption(
    val id: String,
    val label: String,
    val provider: AiProvider,
    val note: String? = null
)

object AiModels {
    val CLAUDE_OPTIONS = listOf(
        AiModelOption("claude-sonnet-5", "Claude Sonnet 5", AiProvider.CLAUDE),
        AiModelOption("claude-opus-4-8", "Claude Opus 4.8", AiProvider.CLAUDE, "Most capable, slower"),
        AiModelOption("claude-haiku-4-5-20251001", "Claude Haiku 4.5", AiProvider.CLAUDE, "Fastest, cheapest")
    )
    val GEMINI_OPTIONS = listOf(
        AiModelOption("gemini-3.5-flash", "Gemini 3.5 Flash", AiProvider.GEMINI),
        AiModelOption("gemini-3.1-pro-preview", "Gemini 3.1 Pro", AiProvider.GEMINI, "Preview — needs billing enabled"),
        AiModelOption("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite", AiProvider.GEMINI, "Cheapest, high-volume")
    )
    val ALL = CLAUDE_OPTIONS + GEMINI_OPTIONS
    val DEFAULT = GEMINI_OPTIONS[0]
}

/** One strike's worth of CE + PE data for the option chain screen. Bid/ask
 *  come from Kite's quote depth (best of 5 levels), refreshed on a poll
 *  interval like the Live Quotes screen rather than a raw tick WebSocket -
 *  reuses the same batch /quote endpoint the rest of the app already relies
 *  on instead of adding a new streaming dependency. */
data class OptionChainRow(
    val strike: Double,
    val isAtm: Boolean,
    val ceSymbol: String,
    val ceLtp: Double = 0.0,
    val ceBid: Double = 0.0,
    val ceAsk: Double = 0.0,
    val ceOi: Long = 0L,
    val ceVolume: Long = 0L,
    val peSymbol: String,
    val peLtp: Double = 0.0,
    val peBid: Double = 0.0,
    val peAsk: Double = 0.0,
    val peOi: Long = 0L,
    val peVolume: Long = 0L
)
