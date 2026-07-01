package com.marketdata.app.util

import com.marketdata.app.data.models.KiteCandle
import kotlin.math.abs

enum class Direction { UP, DOWN }

data class PatternDef(
    val id: String,
    val label: String,
    val direction: Direction
)

data class PatternResult(
    val symbol: String,
    val patternId: String,
    val patternLabel: String,
    val direction: Direction,
    val occurrences: Int,
    val accuracy: Double,
    val confidence: Double,
    val activeNow: Boolean,
    val cmp: Double,
    val atr: Double
)

data class TradeLevels(
    val entry: Double,
    val stopLoss: Double,
    val target: Double,
    val riskReward: Double
)

enum class TradingStyle(val label: String, val blurb: String) {
    SCALP("Scalp", "Minutes-long trades, square off fast, tight stops"),
    INTRADAY("Intraday", "Same-day trades, square off by market close"),
    SHORT_TERM("Short-Term", "Held a few days to a couple weeks (swing)"),
    POSITIONAL("Positional", "Held weeks to months, wider stops")
}

/**
 * intervalIndex refers to NiftySymbols.INTERVALS (0=1Min ... 7=Day, 8=Week).
 * lookbackDays is capped implicitly by Kite's own history retention per interval
 * (see NiftySymbols.INTERVAL_DAY_LIMITS) - the chip options below stay within that.
 */
data class StylePreset(
    val style: TradingStyle,
    val intervalIndex: Int,
    val lookbackDays: Int,
    val lookbackChipOptions: List<Int>,
    val sessionOnly: Boolean,
    val thresholdFactor: Double,
    val slAtrMult: Double,
    val targetAtrMult: Double,
    val minOccurrences: Int,
    val minAccuracy: Double
)

object TradingStyles {
    val PRESETS: Map<TradingStyle, StylePreset> = mapOf(
        TradingStyle.SCALP to StylePreset(
            style = TradingStyle.SCALP,
            intervalIndex = 0, // 1 Min
            lookbackDays = 15,
            lookbackChipOptions = listOf(5, 10, 20, 40),
            sessionOnly = true,
            thresholdFactor = 0.08,
            slAtrMult = 0.5,
            targetAtrMult = 0.9,
            minOccurrences = 25,
            minAccuracy = 0.58
        ),
        TradingStyle.INTRADAY to StylePreset(
            style = TradingStyle.INTRADAY,
            intervalIndex = 2, // 5 Min
            lookbackDays = 30,
            lookbackChipOptions = listOf(10, 20, 30, 60),
            sessionOnly = true,
            thresholdFactor = 0.10,
            slAtrMult = 0.7,
            targetAtrMult = 1.3,
            minOccurrences = 15,
            minAccuracy = 0.56
        ),
        TradingStyle.SHORT_TERM to StylePreset(
            style = TradingStyle.SHORT_TERM,
            intervalIndex = 7, // Day
            lookbackDays = 150,
            lookbackChipOptions = listOf(60, 90, 150, 180),
            sessionOnly = false,
            thresholdFactor = 0.15,
            slAtrMult = 0.8,
            targetAtrMult = 1.5,
            minOccurrences = 12,
            minAccuracy = 0.55
        ),
        TradingStyle.POSITIONAL to StylePreset(
            style = TradingStyle.POSITIONAL,
            intervalIndex = 8, // Week
            lookbackDays = 730,
            lookbackChipOptions = listOf(180, 365, 730, 1095),
            sessionOnly = false,
            thresholdFactor = 0.15,
            slAtrMult = 1.0,
            targetAtrMult = 2.5,
            minOccurrences = 8,
            minAccuracy = 0.55
        )
    )
}

/**
 * Pattern-mining / backtest engine used by the Scanner screen.
 *
 * IMPORTANT METHODOLOGY NOTE:
 * Every pattern's predicted DIRECTION is fixed in advance below (e.g. "3 down days -> DOWN").
 * It is tempting to instead pick whichever direction happened to score higher per stock,
 * but that guarantees >=50% "accuracy" even on pure noise (you're optimizing the label
 * after seeing the outcome - classic look-ahead / multiple-comparisons bias). Keeping the
 * hypothesis fixed before scanning is what makes the accuracy numbers below meaningful,
 * even though it means many patterns will legitimately score close to (or below) 50%.
 */
object PatternEngine {

    val PATTERNS = listOf(
        PatternDef("down3_continuation", "3 Down Days \u2192 Down", Direction.DOWN),
        PatternDef("down3_reversal", "3 Down Days \u2192 Bounce", Direction.UP),
        PatternDef("up3_continuation", "3 Up Days \u2192 Up", Direction.UP),
        PatternDef("up3_reversal", "3 Up Days \u2192 Pullback", Direction.DOWN),
        PatternDef("rsi_oversold_bounce", "RSI<30 \u2192 Bounce", Direction.UP),
        PatternDef("rsi_overbought_fade", "RSI>70 \u2192 Fade", Direction.DOWN),
        PatternDef("bull_engulf_cont", "Bullish Engulfing \u2192 Up", Direction.UP),
        PatternDef("bear_engulf_cont", "Bearish Engulfing \u2192 Down", Direction.DOWN),
        PatternDef("vol_spike_up_cont", "Volume Spike (Up Day) \u2192 Up", Direction.UP),
        PatternDef("vol_spike_down_cont", "Volume Spike (Down Day) \u2192 Down", Direction.DOWN)
    )

    fun rsi(closes: List<Double>, period: Int = 14): List<Double?> {
        val n = closes.size
        val out = MutableList<Double?>(n) { null }
        if (n < period + 1) return out
        var avgGain = 0.0
        var avgLoss = 0.0
        for (i in 1..period) {
            val d = closes[i] - closes[i - 1]
            if (d > 0) avgGain += d else avgLoss += -d
        }
        avgGain /= period
        avgLoss /= period
        out[period] = rsiFromAvg(avgGain, avgLoss)
        for (i in period + 1 until n) {
            val d = closes[i] - closes[i - 1]
            val gain = if (d > 0) d else 0.0
            val loss = if (d < 0) -d else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
            out[i] = rsiFromAvg(avgGain, avgLoss)
        }
        return out
    }

    private fun rsiFromAvg(avgGain: Double, avgLoss: Double): Double {
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100 - (100 / (1 + rs))
    }

    fun atr(candles: List<KiteCandle>, period: Int = 14): List<Double?> {
        val n = candles.size
        val out = MutableList<Double?>(n) { null }
        if (n == 0) return out
        val tr = DoubleArray(n)
        for (i in 0 until n) {
            tr[i] = if (i == 0) {
                candles[i].high - candles[i].low
            } else {
                maxOf(
                    candles[i].high - candles[i].low,
                    abs(candles[i].high - candles[i - 1].close),
                    abs(candles[i].low - candles[i - 1].close)
                )
            }
        }
        if (n < period + 1) return out
        var avg = tr.toList().subList(1, period + 1).average()
        out[period] = avg
        for (i in period + 1 until n) {
            avg = (avg * (period - 1) + tr[i]) / period
            out[i] = avg
        }
        return out
    }

    /**
     * Scans one symbol's candle history (oldest -> newest) and returns backtest stats
     * for every pre-specified pattern that occurred at least once, plus whether each
     * pattern is ACTIVE on the most recent candle (i.e. a live, tradeable setup as of
     * the last close).
     *
     * @param sessionOnly When true (use for scalp/intraday), candlestick-shape patterns
     *   (3-in-a-row, engulfing) must fall entirely within the same trading session, and
     *   the next-candle outcome used to score a signal must also stay within that same
     *   session. Without this, a "pattern" could be built from yesterday's last candles
     *   plus today's first one, silently mixing overnight gap risk into what's supposed
     *   to be pure intraday momentum.
     * @param thresholdFactor The up/down classification threshold scales with each
     *   candle's own ATR-to-price ratio (thresholdFactor * ATR/price) instead of a fixed
     *   percentage. A flat 0.05% cutoff tuned for daily bars is far too loose for 1-min
     *   candles (most won't clear it) and would silently deflate every pattern's accuracy.
     */
    fun scan(
        symbol: String,
        candles: List<KiteCandle>,
        sessionOnly: Boolean = false,
        thresholdFactor: Double = 0.15
    ): List<PatternResult> {
        if (candles.size < 30) return emptyList()
        val n = candles.size
        val close = candles.map { it.close }
        val open = candles.map { it.open }
        val volume = candles.map { it.volume.toDouble() }
        val sessionDate = candles.map { it.timestamp.take(10) } // "yyyy-MM-dd"
        val rsiVals = rsi(close)
        val atrVals = atr(candles)
        val volAvg20 = MutableList<Double?>(n) { null }
        for (i in 19 until n) volAvg20[i] = volume.subList(i - 19, i + 1).average()

        fun sameSession(a: Int, b: Int) = !sessionOnly || sessionDate[a] == sessionDate[b]

        fun down3(i: Int) = i >= 3 && close[i - 2] > close[i - 1] && close[i - 1] > close[i] && sameSession(i - 2, i)
        fun up3(i: Int) = i >= 3 && close[i - 2] < close[i - 1] && close[i - 1] < close[i] && sameSession(i - 2, i)
        fun rsiOS(i: Int) = (rsiVals[i] ?: 50.0) < 30
        fun rsiOB(i: Int) = (rsiVals[i] ?: 50.0) > 70
        fun bullEngulf(i: Int) = i >= 1 && close[i - 1] < open[i - 1] && close[i] > open[i] &&
            close[i] >= open[i - 1] && open[i] <= close[i - 1] && sameSession(i - 1, i)
        fun bearEngulf(i: Int) = i >= 1 && close[i - 1] > open[i - 1] && close[i] < open[i] &&
            open[i] >= close[i - 1] && close[i] <= open[i - 1] && sameSession(i - 1, i)
        fun volSpike(i: Int): Boolean {
            val avg = volAvg20[i] ?: return false
            if (avg <= 0) return false
            return volume[i] > 1.5 * avg
        }
        fun isUpDay(i: Int) = close[i] > open[i]
        fun isDownDay(i: Int) = close[i] < open[i]

        fun condFor(id: String, i: Int): Boolean = when (id) {
            "down3_continuation", "down3_reversal" -> down3(i)
            "up3_continuation", "up3_reversal" -> up3(i)
            "rsi_oversold_bounce" -> rsiOS(i)
            "rsi_overbought_fade" -> rsiOB(i)
            "bull_engulf_cont" -> bullEngulf(i)
            "bear_engulf_cont" -> bearEngulf(i)
            "vol_spike_up_cont" -> volSpike(i) && isUpDay(i)
            "vol_spike_down_cont" -> volSpike(i) && isDownDay(i)
            else -> false
        }

        fun moveThreshold(i: Int): Double {
            val atrRatio = (atrVals[i] ?: (candles[i].high - candles[i].low)) / close[i]
            val t = thresholdFactor * atrRatio
            return if (t.isFinite() && t > 0) t else 0.0005
        }

        val results = mutableListOf<PatternResult>()
        val cmp = close[n - 1]
        val lastAtr = atrVals[n - 1] ?: (candles[n - 1].high - candles[n - 1].low)

        for (pat in PATTERNS) {
            var occ = 0
            var hits = 0
            for (i in 0 until n - 1) { // need i+1 to exist for the outcome
                if (!condFor(pat.id, i)) continue
                if (sessionOnly && sessionDate[i] != sessionDate[i + 1]) continue // don't score across a session boundary
                occ++
                val thr = moveThreshold(i)
                val ret = close[i + 1] / close[i] - 1
                val wentUp = ret > thr
                val wentDown = ret < -thr
                val hit = if (pat.direction == Direction.UP) wentUp else wentDown
                if (hit) hits++
            }
            if (occ == 0) continue
            val acc = hits.toDouble() / occ
            // confidence = accuracy * min(1, n/30) -- penalizes thin sample sizes
            val confidence = acc * (minOf(occ, 30).toDouble() / 30.0)
            val active = condFor(pat.id, n - 1)
            results.add(
                PatternResult(symbol, pat.id, pat.label, pat.direction, occ, acc, confidence, active, cmp, lastAtr)
            )
        }
        return results
    }

    /**
     * ATR-based entry/SL/target. slMult/targetMult are configurable risk parameters
     * (defaults give roughly a 1:1.9 reward-to-risk).
     */
    fun tradeLevels(
        direction: Direction,
        cmp: Double,
        atrVal: Double,
        slMult: Double = 0.8,
        targetMult: Double = 1.5
    ): TradeLevels {
        val entry = cmp
        val sl = if (direction == Direction.UP) entry - slMult * atrVal else entry + slMult * atrVal
        val target = if (direction == Direction.UP) entry + targetMult * atrVal else entry - targetMult * atrVal
        val rr = if (slMult > 0) targetMult / slMult else 0.0
        return TradeLevels(entry, sl, target, rr)
    }
}
