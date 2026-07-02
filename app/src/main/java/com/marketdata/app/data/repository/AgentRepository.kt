package com.marketdata.app.data.repository

import com.marketdata.app.data.models.AgentMessage
import com.marketdata.app.data.models.AiModelOption
import com.marketdata.app.data.models.AiProvider
import com.marketdata.app.data.models.LiveQuoteDisplay
import com.marketdata.app.data.models.ThinkingLevel
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

    private fun buildSystemPrompt(
        quotes: List<LiveQuoteDisplay>?,
        csvSummary: String?,
        customInstructions: String? = null
    ): String {
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

        if (!customInstructions.isNullOrBlank()) {
            sb.append("\n\nADDITIONAL INSTRUCTIONS FROM THE USER (follow these too, alongside the above):\n")
            sb.append(customInstructions.trim())
            sb.append("\n")
        }

        return sb.toString()
    }

    suspend fun callClaude(
        modelId: String,
        messages: List<AgentMessage>,
        liveQuotes: List<LiveQuoteDisplay>? = null,
        csvSummary: String? = null,
        customInstructions: String? = null
    ): Result<String> {
        return try {
            val apiKey = prefs.claudeApiKey
            if (apiKey.isEmpty()) return Result.failure(Exception("Claude API key not set"))

            val systemPrompt = buildSystemPrompt(liveQuotes, csvSummary, customInstructions)

            val messagesJson = JSONArray()
            for (msg in messages) {
                val obj = JSONObject()
                obj.put("role", msg.role)
                val att = msg.attachment
                if (att != null) {
                    // Claude wants content as an array of blocks (not a plain
                    // string) whenever there's an image/document alongside text.
                    val blocks = JSONArray()
                    if (att.mimeType.startsWith("image/")) {
                        blocks.put(JSONObject().apply {
                            put("type", "image")
                            put("source", JSONObject().apply {
                                put("type", "base64")
                                put("media_type", att.mimeType)
                                put("data", att.base64Data)
                            })
                        })
                    } else if (att.mimeType == "application/pdf") {
                        blocks.put(JSONObject().apply {
                            put("type", "document")
                            put("source", JSONObject().apply {
                                put("type", "base64")
                                put("media_type", att.mimeType)
                                put("data", att.base64Data)
                            })
                        })
                    }
                    if (msg.content.isNotBlank()) {
                        blocks.put(JSONObject().apply { put("type", "text"); put("text", msg.content) })
                    }
                    obj.put("content", blocks)
                } else {
                    obj.put("content", msg.content)
                }
                messagesJson.put(obj)
            }

            val body = JSONObject().apply {
                put("model", modelId)
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
    }

    suspend fun callGemini(
        modelId: String,
        messages: List<AgentMessage>,
        liveQuotes: List<LiveQuoteDisplay>? = null,
        csvSummary: String? = null,
        customInstructions: String? = null,
        thinkingLevel: ThinkingLevel = ThinkingLevel.MEDIUM
    ): Result<String> {
        return try {
            val apiKey = prefs.geminiApiKey
            if (apiKey.isEmpty()) return Result.failure(Exception("Gemini API key not set"))

            val systemPrompt = buildSystemPrompt(liveQuotes, csvSummary, customInstructions)

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
                val parts = JSONArray()
                val att = msg.attachment
                if (att != null && (att.mimeType.startsWith("image/") || att.mimeType == "application/pdf")) {
                    parts.put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", att.mimeType)
                            put("data", att.base64Data)
                        })
                    })
                }
                if (msg.content.isNotBlank()) {
                    parts.put(JSONObject().put("text", msg.content))
                }
                val obj = JSONObject().apply {
                    put("role", role)
                    put("parts", parts)
                }
                contents.put(obj)
            }

            val body = JSONObject().apply {
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    // Thinking now consumes tokens from this same budget on Gemini 3.x,
                    // so 2048 was tight enough to sometimes truncate the actual answer
                    // to nothing (surfaced below as "Gemini returned an empty response").
                    put("maxOutputTokens", 4096)
                    put("temperature", 0.7)
                    // Gemini 3.x (all three models in AiModels.GEMINI_OPTIONS are 3.x)
                    // replaced the old integer thinkingConfig.thinkingBudget with a
                    // string-enum thinkingConfig.thinkingLevel: LOW / MEDIUM / HIGH
                    // (MINIMAL exists too but 400s on gemini-3.1-pro-preview, so it's
                    // deliberately not offered here - see ThinkingLevel in Models.kt).
                    // Sending the old thinkingBudget field to a 3.x model is what was
                    // breaking these calls. Docs: ai.google.dev/gemini-api/docs/thinking
                    put("thinkingConfig", JSONObject().apply {
                        put("thinkingLevel", thinkingLevel.apiValue)
                    })
                })
            }

            // Current Gemini API auth: x-goog-api-key header (see ai.google.dev/api).
            // The older "?key=" query param can still work but the header is what
            // Google's own docs/examples use now, so we match that exactly.
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"

            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("content-type", "application/json")
                .post(body.toString().toRequestBody(JSON))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return Result.failure(Exception("Gemini API error ${response.code}: $responseBody"))
            }

            val json = JSONObject(responseBody)

            // The prompt itself can be blocked (e.g. safety filters) before any
            // candidate is produced at all -- surface that clearly instead of a
            // generic "no value for candidates" crash.
            val promptFeedback = json.optJSONObject("promptFeedback")
            val blockReason = promptFeedback?.optString("blockReason", "")
            if (!blockReason.isNullOrEmpty()) {
                return Result.failure(Exception("Gemini blocked the request ($blockReason). Try rephrasing."))
            }

            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return Result.failure(Exception("Gemini returned no response. Raw: ${responseBody.take(300)}"))
            }

            val candidate = candidates.getJSONObject(0)
            val finishReason = candidate.optString("finishReason", "")
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts")

            // Concatenate every non-"thought" text part, in case the model returns
            // more than one part (e.g. partial thinking traces mixed with the answer).
            val text = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    val isThought = part.optBoolean("thought", false)
                    if (isThought) continue
                    if (part.has("text")) text.append(part.getString("text"))
                }
            }

            if (text.isEmpty()) {
                val reasonMsg = if (finishReason.isNotEmpty()) " (finishReason: $finishReason)" else ""
                return Result.failure(Exception("Gemini returned an empty response$reasonMsg. Try again."))
            }

            Result.success(text.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun call(
        model: AiModelOption,
        messages: List<AgentMessage>,
        liveQuotes: List<LiveQuoteDisplay>? = null,
        csvSummary: String? = null,
        customInstructions: String? = null,
        thinkingLevel: ThinkingLevel = ThinkingLevel.MEDIUM
    ): Result<String> = when (model.provider) {
        AiProvider.CLAUDE -> callClaude(model.id, messages, liveQuotes, csvSummary, customInstructions)
        AiProvider.GEMINI -> callGemini(model.id, messages, liveQuotes, csvSummary, customInstructions, thinkingLevel)
    }
}
