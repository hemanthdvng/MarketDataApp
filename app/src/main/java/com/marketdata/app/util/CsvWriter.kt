package com.marketdata.app.util

import android.content.Context
import android.net.Uri
import com.marketdata.app.data.models.KiteCandle
import com.marketdata.app.data.models.LiveQuoteDisplay
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

object CsvWriter {

    fun writeCandlesToUri(
        context: Context,
        uri: Uri,
        symbol: String,
        candles: List<KiteCandle>,
        includeOI: Boolean
    ): Int {
        context.contentResolver.openOutputStream(uri)?.use { os ->
            BufferedWriter(OutputStreamWriter(os)).use { writer ->
                // Header
                val header = if (includeOI)
                    "Symbol,Timestamp,Open,High,Low,Close,Volume,OI"
                else
                    "Symbol,Timestamp,Open,High,Low,Close,Volume"
                writer.write(header)
                writer.newLine()

                // Rows
                for (c in candles) {
                    val row = if (includeOI)
                        "$symbol,${c.timestamp},${c.open},${c.high},${c.low},${c.close},${c.volume},${c.oi}"
                    else
                        "$symbol,${c.timestamp},${c.open},${c.high},${c.low},${c.close},${c.volume}"
                    writer.write(row)
                    writer.newLine()
                }
                writer.flush()
            }
        }
        return candles.size
    }

    fun writeMultiSymbolCandlesToUri(
        context: Context,
        uri: Uri,
        data: Map<String, List<KiteCandle>>,
        includeOI: Boolean
    ): Int {
        var totalRows = 0
        context.contentResolver.openOutputStream(uri)?.use { os ->
            BufferedWriter(OutputStreamWriter(os)).use { writer ->
                val header = if (includeOI)
                    "Symbol,Timestamp,Open,High,Low,Close,Volume,OI"
                else
                    "Symbol,Timestamp,Open,High,Low,Close,Volume"
                writer.write(header)
                writer.newLine()

                for ((symbol, candles) in data) {
                    for (c in candles) {
                        val row = if (includeOI)
                            "$symbol,${c.timestamp},${c.open},${c.high},${c.low},${c.close},${c.volume},${c.oi}"
                        else
                            "$symbol,${c.timestamp},${c.open},${c.high},${c.low},${c.close},${c.volume}"
                        writer.write(row)
                        writer.newLine()
                        totalRows++
                    }
                }
                writer.flush()
            }
        }
        return totalRows
    }

    fun writeQuotesToUri(
        context: Context,
        uri: Uri,
        quotes: List<LiveQuoteDisplay>
    ) {
        context.contentResolver.openOutputStream(uri)?.use { os ->
            BufferedWriter(OutputStreamWriter(os)).use { writer ->
                writer.write("Symbol,LTP,Open,High,Low,PrevClose,Change,Change%,Volume,OI")
                writer.newLine()
                for (q in quotes) {
                    writer.write(
                        "${q.symbol},${q.ltp},${q.open},${q.high},${q.low},${q.close}," +
                        "${q.change},${String.format("%.2f", q.changePct)},${q.volume},${q.oi}"
                    )
                    writer.newLine()
                }
                writer.flush()
            }
        }
    }

    fun generateFileName(symbols: List<String>, interval: String, fromDate: String, toDate: String): String {
        val symbolPart = if (symbols.size == 1) symbols[0]
        else if (symbols.size <= 3) symbols.joinToString("_")
        else "${symbols.size}stocks"
        val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "${symbolPart}_${interval}_${fromDate}_${toDate}_${ts}.csv"
    }

    /** Stable (non-timestamped) filename for the growing "everything synced at
     *  this interval" master file used by incremental downloads. Deliberately
     *  has no date/timestamp in the name, since it's rewritten in place rather
     *  than replaced each time. */
    fun generateSyncFileName(interval: String): String = "synced_${interval}.csv"

    fun generateQuoteFileName(): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "quotes_$ts.csv"
    }
}
