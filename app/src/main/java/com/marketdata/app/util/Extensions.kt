package com.marketdata.app.util

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

object Extensions {

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun kiteChecksum(apiKey: String, requestToken: String, apiSecret: String): String =
        sha256(apiKey + requestToken + apiSecret)

    fun formatDate(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

    fun formatDateTime(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(date)

    fun parseDate(dateStr: String): Date? = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)
    } catch (e: Exception) { null }

    /**
     * Splits a date range into chunks based on interval limits.
     * Returns list of (fromDate, toDate) pairs as formatted strings.
     */
    fun chunkDateRange(
        fromDate: Date,
        toDate: Date,
        intervalKey: String
    ): List<Pair<String, String>> {
        val limitDays = NiftySymbols.INTERVAL_DAY_LIMITS[intervalKey] ?: 60
        val chunks = mutableListOf<Pair<String, String>>()
        val cal = Calendar.getInstance()
        var current = fromDate

        while (current.before(toDate) || current == toDate) {
            val chunkStart = current
            cal.time = current
            cal.add(Calendar.DAY_OF_YEAR, limitDays - 1)
            val chunkEnd = if (cal.time.after(toDate)) toDate else cal.time

            chunks.add(Pair(formatDateTime(chunkStart), formatDateTime(chunkEnd)))

            cal.time = chunkEnd
            cal.add(Calendar.DAY_OF_YEAR, 1)
            current = cal.time

            if (current.after(toDate)) break
        }
        return chunks
    }

    fun aggregateToWeekly(dailyCandles: List<com.marketdata.app.data.models.KiteCandle>)
        : List<com.marketdata.app.data.models.KiteCandle> {
        if (dailyCandles.isEmpty()) return emptyList()

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        val result = mutableListOf<com.marketdata.app.data.models.KiteCandle>()
        val cal = Calendar.getInstance()

        val grouped = dailyCandles.groupBy { candle ->
            try {
                val d = sdf.parse(candle.timestamp.replace("+05:30", "+0530")) ?: Date()
                cal.time = d
                val year = cal.get(Calendar.YEAR)
                val week = cal.get(Calendar.WEEK_OF_YEAR)
                "$year-W$week"
            } catch (e: Exception) { "unknown" }
        }

        for ((_, weekCandles) in grouped.entries.sortedBy { it.key }) {
            if (weekCandles.isEmpty()) continue
            val open = weekCandles.first().open
            val high = weekCandles.maxOf { it.high }
            val low = weekCandles.minOf { it.low }
            val close = weekCandles.last().close
            val volume = weekCandles.sumOf { it.volume }
            val oi = weekCandles.last().oi
            result.add(
                com.marketdata.app.data.models.KiteCandle(
                    timestamp = weekCandles.first().timestamp,
                    open = open, high = high, low = low, close = close,
                    volume = volume, oi = oi
                )
            )
        }
        return result
    }
}
