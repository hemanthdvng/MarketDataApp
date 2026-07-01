package com.marketdata.app.util

import android.content.Context
import android.net.Uri
import com.marketdata.app.data.models.KiteCandle
import java.io.BufferedReader
import java.io.InputStreamReader

object CsvReader {
    /**
     * Parses a CSV previously written by CsvWriter
     * (header: Symbol,Timestamp,Open,High,Low,Close,Volume[,OI])
     * back into per-symbol candle lists, sorted oldest -> newest.
     */
    fun readMultiSymbolCsv(context: Context, uri: Uri): Map<String, List<KiteCandle>> {
        val out = LinkedHashMap<String, MutableList<KiteCandle>>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input)).use { reader ->
                val header = reader.readLine() ?: return out
                val hasOI = header.trim().endsWith("OI")
                reader.forEachLine { line ->
                    if (line.isBlank()) return@forEachLine
                    val cols = line.split(",")
                    if (cols.size < 7) return@forEachLine
                    try {
                        val symbol = cols[0]
                        val candle = KiteCandle(
                            timestamp = cols[1],
                            open = cols[2].toDouble(),
                            high = cols[3].toDouble(),
                            low = cols[4].toDouble(),
                            close = cols[5].toDouble(),
                            volume = cols[6].toLongOrNull() ?: 0L,
                            oi = if (hasOI && cols.size > 7) cols[7].toLongOrNull() ?: 0L else 0L
                        )
                        out.getOrPut(symbol) { mutableListOf() }.add(candle)
                    } catch (_: Exception) {
                        // skip malformed row
                    }
                }
            }
        }
        // Sort defensively by timestamp (ISO-8601 strings sort lexicographically in order).
        return out.mapValues { (_, list) -> list.sortedBy { it.timestamp } }
    }
}
