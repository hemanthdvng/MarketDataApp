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
    val ohlc: OhlcData? = null
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

data class AgentMessage(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
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
