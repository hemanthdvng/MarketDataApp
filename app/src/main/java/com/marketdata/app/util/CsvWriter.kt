package com.marketdata.app.util

import android.content.Context
import android.net.Uri
import com.marketdata.app.data.models.KiteCandle
import com.marketdata.app.data.models.LiveQuoteDisplay
import com.marketdata.app.data.models.OptionChainRow
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
        context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
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
        context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
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
        context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
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

    /** Writes an option chain (one row per strike, CE+PE side by side) to CSV. */
    fun writeOptionChainToUri(
        context: Context,
        uri: Uri,
        underlying: String,
        expiry: String,
        spotPrice: Double,
        rows: List<OptionChainRow>
    ): Int {
        context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
            BufferedWriter(OutputStreamWriter(os)).use { writer ->
                writer.write("Underlying,Expiry,SpotPrice,Strike,IsATM,CE_Symbol,CE_LTP,CE_Bid,CE_Ask,CE_OI,CE_Volume,PE_Symbol,PE_LTP,PE_Bid,PE_Ask,PE_OI,PE_Volume")
                writer.newLine()
                for (r in rows) {
                    writer.write(
                        "$underlying,$expiry,$spotPrice,${r.strike},${r.isAtm}," +
                        "${r.ceSymbol},${r.ceLtp},${r.ceBid},${r.ceAsk},${r.ceOi},${r.ceVolume}," +
                        "${r.peSymbol},${r.peLtp},${r.peBid},${r.peAsk},${r.peOi},${r.peVolume}"
                    )
                    writer.newLine()
                }
                writer.flush()
            }
        }
        return rows.size
    }

    /** One row per (underlying, strike): a bulk download's combined chain
     *  across many underlyings (Nifty 50 / Nifty 100 / All F&O), each tagged
     *  with its own expiry and spot price since those can differ per symbol
     *  when the requested expiry date doesn't exist for that underlying and
     *  the nearest available one was substituted (see OptionChainViewModel's
     *  bulk download - it always uses the expiry actually fetched, not just
     *  the one the user picked). */
    fun writeBulkOptionChainToUri(
        context: Context,
        uri: Uri,
        entries: List<Pair<Triple<String, String, Double>, List<OptionChainRow>>> // (underlying, expiry, spot) -> rows
    ): Int {
        var totalRows = 0
        context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
            BufferedWriter(OutputStreamWriter(os)).use { writer ->
                writer.write("Underlying,Expiry,SpotPrice,Strike,IsATM,CE_Symbol,CE_LTP,CE_Bid,CE_Ask,CE_OI,CE_Volume,PE_Symbol,PE_LTP,PE_Bid,PE_Ask,PE_OI,PE_Volume")
                writer.newLine()
                for ((meta, rows) in entries) {
                    val (underlying, expiry, spotPrice) = meta
                    for (r in rows) {
                        writer.write(
                            "$underlying,$expiry,$spotPrice,${r.strike},${r.isAtm}," +
                            "${r.ceSymbol},${r.ceLtp},${r.ceBid},${r.ceAsk},${r.ceOi},${r.ceVolume}," +
                            "${r.peSymbol},${r.peLtp},${r.peBid},${r.peAsk},${r.peOi},${r.peVolume}"
                        )
                        writer.newLine()
                        totalRows++
                    }
                }
                writer.flush()
            }
        }
        return totalRows
    }

    fun generateBulkOptionChainFileName(label: String): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${label}_optionchain_bulk_$ts.csv"
    }

    fun generateOptionChainFileName(underlying: String, expiry: String): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeExpiry = expiry.replace("-", "")
        return "${underlying}_${safeExpiry}_optionchain_$ts.csv"
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
