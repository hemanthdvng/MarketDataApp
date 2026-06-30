package com.marketdata.app.data.repository

import com.marketdata.app.data.models.AgentMessage
import com.marketdata.app.data.models.AiModel
import com.marketdata.app.data.models.LiveQuoteDisplay
import com.marketdata.app.data.prefs.SecurePrefs
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AgentRepository(private val prefs: SecurePrefs) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json".toMediaType()

    private fun buildSystemPrompt(quotes: List<LiveQuoteDisplay>?, csvSummary: String?): String {
        val sb = StringBuilder()
        sb.append("""
You are a professional Indian stock market analyst with expertise in NSE/BSE markets, 
technical analysis, options, and futures. You help analyze stocks, indices, and 
provide trading recommendations.

You have access to the following data:
""".trimIndent())

        if (!quotes.isNullOrEmpty()) {
            sb.append("\n\nCURRENT LIVE MARKET DATA:\n")
            sb.append("Symbol | LTP | Open | High | Low | PrevClose | Change% | Volume | OI\n")
            for (q in quotes) {
                sb.append("${q.symbol} | ${q.ltp} | ${q.open} | ${q.high} | ${q.low} | " +
                    "${q.close} | ${String.format("%.2f", q.changePct)}% | ${q.volume} | ${q.oi}\n")
            }
        }

        if (!csvSummary.isNullOrEmpty()) {
            sb.append("\n\nHISTORICAL DATA SUMMARY:\n$csvSummary\n")
        }

        sb.append("""

When analyzing, provide:
1. Clear BUY/SELL/HOLD recommendation with confidence level
2. Key support and resistance levels
3. Technical signals (if historical data available)
4. Risk assessment
5. Entry/exit zones
6. Stop loss recommendation

Always mention if you need more specific data to give better analysis.
Format numbers clearly. Use INR for prices.
""".trimIndent())

        return sb.toString()
    }

    suspend fun callClaude(
        messages: List<AgentMessage>,
        liveQuotes: List<LiveQuoteDisplay>? = null,
        csvSummary: String? = null
    ): Result<String> = try {
        val apiKey = prefs.claudeApiKey
        if (apiKey.isEmpty()) return Result.failure(Exception("Claude API key not set"))

        val systemPrompt = buildSystemPrompt(liveQuotes, csvSummary)

        val messagesJson = JSONArray()
        for (msg in messages) {
            val obj = JSONObject()
            obj.put("role", msg.role)
            obj.put("content", msg.content)
            messagesJson.put(obj)
        }

        val body = JSONObject().apply {
            put("model", "claude-sonnet-4-6")
            put("max_tokens", 2048)
            put("system", systemPrompt)
            put("messages", messagesJson)
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            return Result.failure(Exception("Claude API error ${response.code}: $responseBody"))
        }

        val json = JSONObject(responseBody)
        val content = json.getJSONArray("content").getJSONObject(0).getString("text")
        Result.success(content)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun callGemini(
        messages: List<AgentMessage>,
        liveQuotes: List<LiveQuoteDisplay>? = null,
        csvSummary: String? = null
    ): Result<String> = try {
        val apiKey = prefs.geminiApiKey
        if (apiKey.isEmpty()) return Result.failure(Exception("Gemini API key not set"))

        val systemPrompt = buildSystemPrompt(liveQuotes, csvSummary)

        val contents = JSONArray()

        // Add system prompt as first user message
        val sysMsg = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
        }
        contents.put(sysMsg)
        val sysAck = JSONObject().apply {
            put("role", "model")
            put("parts", JSONArray().put(JSONObject().put("text", "Understood. I'm ready to analyze Indian market data.")))
        }
        contents.put(sysAck)

        for (msg in messages) {
            val role = if (msg.role == "assistant") "model" else "user"
            val obj = JSONObject().apply {
                put("role", role)
                put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
            }
            contents.put(obj)
        }

        val body = JSONObject().apply {
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 2048)
                put("temperature", 0.7)
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            return Result.failure(Exception("Gemini API error ${response.code}: $responseBody"))
        }

        val json = JSONObject(responseBody)
        val text = json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
        Result.success(text)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun call(
        model: AiModel,
        messages: List<AgentMessage>,
        liveQuotes: List<LiveQuoteDisplay>? = null,
        csvSummary: String? = null
    ): Result<String> = when (model) {
        AiModel.CLAUDE -> callClaude(messages, liveQuotes, csvSummary)
        AiModel.GEMINI -> callGemini(messages, liveQuotes, csvSummary)
    }
}
