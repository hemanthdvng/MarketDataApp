package com.marketdata.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.marketdata.app.data.api.KiteApiService
import com.marketdata.app.data.db.AppDatabase
import com.marketdata.app.data.db.InstrumentEntity
import com.marketdata.app.data.models.*
import com.marketdata.app.data.prefs.SecurePrefs
import com.marketdata.app.util.Extensions
import com.marketdata.app.util.NiftySymbols
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Date

class KiteRepository(private val context: Context) {

    private val api = KiteApiService.create()
    private val db = AppDatabase.getInstance(context)
    private val prefs = SecurePrefs.getInstance(context)

    private fun authHeader(): String =
        "token ${prefs.apiKey}:${prefs.accessToken}"

    // ---- Auth ----

    suspend fun createSession(requestToken: String): Result<SessionData> = try {
        val checksum = Extensions.kiteChecksum(prefs.apiKey, requestToken, prefs.apiSecret)
        val response = api.createSession(
            apiKey = prefs.apiKey,
            requestToken = requestToken,
            checksum = checksum
        )
        if (response.isSuccessful && response.body()?.status == "success") {
            val data = response.body()!!.data!!
            prefs.accessToken = data.access_token
            prefs.userId = data.user_id
            prefs.userName = data.user_name
            Result.success(data)
        } else {
            val msg = response.body()?.message ?: response.message()
            Result.failure(Exception(msg))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ---- Instruments ----

    suspend fun refreshInstruments(): Result<Int> = try {
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("https://api.kite.trade/instruments/NSE")
            .addHeader("X-Kite-Version", "3")
            .addHeader("Authorization", authHeader())
            .build()

        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) return Result.failure(Exception("Failed to download instruments: ${resp.code}"))

        val csv = resp.body?.string() ?: return Result.failure(Exception("Empty instruments response"))
        val lines = csv.lines()
        if (lines.size < 2) return Result.failure(Exception("Invalid instruments CSV"))

        val entities = mutableListOf<InstrumentEntity>()
        for (line in lines.drop(1)) { // skip header
            if (line.isBlank()) continue
            val cols = line.split(",")
            if (cols.size < 12) continue
            try {
                entities.add(
                    InstrumentEntity(
                        instrumentToken = cols[0].trim().toLong(),
                        exchangeToken = cols[1].trim().toLongOrNull() ?: 0L,
                        tradingSymbol = cols[2].trim(),
                        name = cols[3].trim(),
                        instrumentType = cols[9].trim(),
                        segment = cols[10].trim(),
                        exchange = cols[11].trim(),
                        lotSize = cols[8].trim().toIntOrNull() ?: 1,
                        tickSize = cols[7].trim().toDoubleOrNull() ?: 0.05
                    )
                )
            } catch (_: Exception) {}
        }

        db.instrumentDao().deleteByExchange("NSE")
        db.instrumentDao().insertAll(entities)
        prefs.instrumentsLastUpdated = System.currentTimeMillis()
        Result.success(entities.size)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getTokenForSymbol(symbol: String): Long? {
        val entity = db.instrumentDao().getBySymbol(symbol.uppercase())
        return entity?.instrumentToken
    }

    suspend fun getTokensForSymbols(symbols: List<String>): Map<String, Long> {
        val entities = db.instrumentDao().getBySymbols(symbols.map { it.uppercase() })
        return entities.associate { it.tradingSymbol to it.instrumentToken }
    }

    suspend fun searchSymbols(query: String): List<InstrumentEntity> =
        db.instrumentDao().search("%${query.uppercase()}%")

    // ---- Historical Data ----

    /**
     * Fetch historical candles with auto-chunking.
     * onProgress: (completed, total) chunks
     */
    suspend fun fetchHistorical(
        instrumentToken: Long,
        intervalKey: String,
        fromDate: Date,
        toDate: Date,
        includeOI: Boolean,
        onProgress: suspend (Int, Int) -> Unit = { _, _ -> }
    ): Result<List<KiteCandle>> = try {
        val isWeekly = intervalKey == "week"
        val actualInterval = if (isWeekly) "day" else intervalKey

        val chunks = Extensions.chunkDateRange(fromDate, toDate, actualInterval)
        val allCandles = mutableListOf<KiteCandle>()
        var completed = 0

        for ((from, to) in chunks) {
            delay(350) // Respect 3 req/sec rate limit
            val response = api.getHistoricalData(
                auth = authHeader(),
                instrumentToken = instrumentToken,
                interval = actualInterval,
                from = from,
                to = to,
                oi = if (includeOI) 1 else 0
            )

            if (response.isSuccessful) {
                val candles = response.body()?.data?.candles ?: emptyList()
                for (row in candles) {
                    if (row.size < 6) continue
                    allCandles.add(
                        KiteCandle(
                            timestamp = row[0].toString(),
                            open = (row[1] as? Number)?.toDouble() ?: 0.0,
                            high = (row[2] as? Number)?.toDouble() ?: 0.0,
                            low = (row[3] as? Number)?.toDouble() ?: 0.0,
                            close = (row[4] as? Number)?.toDouble() ?: 0.0,
                            volume = (row[5] as? Number)?.toLong() ?: 0L,
                            oi = if (row.size > 6 && includeOI) (row[6] as? Number)?.toLong() ?: 0L else 0L
                        )
                    )
                }
            }
            completed++
            onProgress(completed, chunks.size)
        }

        val finalCandles = if (isWeekly) Extensions.aggregateToWeekly(allCandles) else allCandles
        Result.success(finalCandles)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ---- Live Quotes ----

    suspend fun fetchQuotes(symbols: List<String>): Result<List<LiveQuoteDisplay>> = try {
        // Map symbols to NSE:SYMBOL format
        val instruments = symbols.map { "NSE:${it.uppercase()}" }
        val response = api.getQuote(auth = authHeader(), instruments = instruments)

        if (response.isSuccessful && response.body()?.status == "success") {
            val data = response.body()!!.data ?: emptyMap()
            val quotes = data.map { (key, q) ->
                val sym = key.substringAfter(":")
                val prevClose = q.ohlc?.close ?: q.last_price
                val change = q.last_price - prevClose
                val changePct = if (prevClose != 0.0) (change / prevClose) * 100 else 0.0
                LiveQuoteDisplay(
                    symbol = sym,
                    ltp = q.last_price,
                    open = q.ohlc?.open ?: 0.0,
                    high = q.ohlc?.high ?: 0.0,
                    low = q.ohlc?.low ?: 0.0,
                    close = prevClose,
                    change = change,
                    changePct = changePct,
                    volume = q.volume,
                    oi = q.oi
                )
            }
            Result.success(quotes)
        } else {
            Result.failure(Exception(response.body()?.toString() ?: "Failed to fetch quotes"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun fetchIndexQuotes(indexNames: List<String>): Result<List<LiveQuoteDisplay>> = try {
        val instruments = indexNames.map { "NSE:${it}" }
        val response = api.getQuote(auth = authHeader(), instruments = instruments)

        if (response.isSuccessful) {
            val data = response.body()?.data ?: emptyMap()
            val quotes = data.map { (key, q) ->
                val sym = key.substringAfter(":")
                val prevClose = q.ohlc?.close ?: q.last_price
                val change = q.last_price - prevClose
                val changePct = if (prevClose != 0.0) (change / prevClose) * 100 else 0.0
                LiveQuoteDisplay(
                    symbol = sym, ltp = q.last_price,
                    open = q.ohlc?.open ?: 0.0, high = q.ohlc?.high ?: 0.0,
                    low = q.ohlc?.low ?: 0.0, close = prevClose,
                    change = change, changePct = changePct,
                    volume = q.volume, oi = q.oi
                )
            }
            Result.success(quotes)
        } else {
            Result.failure(Exception("Failed to fetch index quotes"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun instrumentsNeedRefresh(): Boolean {
        val last = prefs.instrumentsLastUpdated
        val oneDayMs = 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() - last > oneDayMs
    }
}
