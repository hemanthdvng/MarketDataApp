package com.marketdata.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.marketdata.app.MainActivity
import com.marketdata.app.data.prefs.SecurePrefs
import com.marketdata.app.data.repository.KiteRepository
import com.marketdata.app.util.NiftySymbols
import com.marketdata.app.util.PatternEngine
import com.marketdata.app.util.PatternResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Runs the same live pattern scan the Scanner screen can run manually, on a
 * timer, and posts a notification for each genuinely NEW active signal.
 * Config (which symbols/interval/thresholds) is snapshotted into SecurePrefs
 * by ScannerScreen when the "Run in background" switch is turned on - this
 * service has no ViewModel/UI of its own to read live state from.
 *
 * Uses foregroundServiceType="specialUse" rather than "dataSync": Android 15+
 * caps dataSync-type foreground services at 6 hours per rolling 24h, and
 * NSE's trading session (9:15-15:30, ~6h15m) would blow past that on almost
 * any day the user doesn't happen to reopen the app in between. specialUse
 * has no such OS-level time cap. (If this app is ever published to the Play
 * Store, specialUse requires a declared justification and gets more review
 * scrutiny than dataSync - a non-issue for a sideloaded personal APK, but
 * worth revisiting before any store listing.)
 */
class PatternScanService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var loopJob: Job? = null
    private lateinit var prefs: SecurePrefs
    private lateinit var repo: KiteRepository
    private lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = SecurePrefs.getInstance(this)
        repo = KiteRepository(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID_STATUS, buildStatusNotification("Starting scanner..."))
        prefs.scannerBgRunning = true

        loopJob?.cancel()
        loopJob = scope.launch {
            while (isActive) {
                runOneCycle()
                delay(prefs.scannerBgIntervalSeconds.coerceAtLeast(15) * 1000L)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        prefs.scannerBgRunning = false
        loopJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    // Added in API 35. Safe to override unconditionally on minSdk 23 - the
    // framework simply never invokes a callback that didn't exist yet on
    // older OS versions. Kept defensively in case this is ever switched to
    // a foregroundServiceType that IS subject to the 6h timeout.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    private suspend fun runOneCycle() {
        try {
            val symbols = when (prefs.scannerBgSelectionType) {
                "NIFTY50" -> NiftySymbols.NIFTY_50
                "NIFTY100" -> NiftySymbols.NIFTY_100
                else -> prefs.scannerBgSymbols.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() }
            }
            if (symbols.isEmpty()) {
                updateStatus("No symbols configured")
                return
            }

            val intervalEntry = NiftySymbols.INTERVALS.getOrElse(prefs.scannerBgIntervalIndex) { NiftySymbols.INTERVALS[7] }
            val intervalKey = intervalEntry.second

            val toDate = Date()
            val fromDate = Calendar.getInstance()
                .apply { add(Calendar.DAY_OF_YEAR, -prefs.scannerBgLookbackDays) }.time

            var tokenMap = repo.getTokensForSymbols(symbols)
            if (tokenMap.isEmpty()) {
                repo.refreshInstruments()
                tokenMap = repo.getTokensForSymbols(symbols)
            }
            if (tokenMap.isEmpty()) {
                updateStatus("No instrument tokens - open the app once to log in / refresh instruments")
                return
            }

            val allResults = mutableListOf<PatternResult>()
            for ((sym, token) in tokenMap) {
                repo.fetchHistorical(token, intervalKey, fromDate, toDate, includeOI = false)
                    .onSuccess { candles ->
                        if (candles.isNotEmpty()) {
                            allResults.addAll(
                                PatternEngine.scan(
                                    sym, candles,
                                    sessionOnly = prefs.scannerBgSessionOnly,
                                    thresholdFactor = prefs.scannerBgThresholdFactor.toDouble()
                                )
                            )
                        }
                    }
            }

            val active = allResults.filter {
                it.activeNow &&
                    it.occurrences >= prefs.scannerBgMinOccurrences &&
                    it.accuracy >= prefs.scannerBgMinAccuracy.toDouble()
            }.sortedByDescending { it.confidence }

            val seen = prefs.scannerBgSeenSignals.split(",").filter { it.isNotBlank() }.toSet()
            fun key(r: PatternResult) = "${r.symbol}|${r.patternId}|${r.direction}"
            val currentKeys = active.map { key(it) }.toSet()
            val newHits = active.filter { key(it) !in seen }

            if (newHits.isNotEmpty()) notifyNewSignals(newHits)
            prefs.scannerBgSeenSignals = currentKeys.joinToString(",")

            val timeLabel = SimpleDateFormat("HH:mm", Locale.US).format(Date())
            val label = "Last scan $timeLabel \u2022 ${active.size} active signal(s) across ${symbols.size} symbols"
            prefs.scannerBgLastRunLabel = label
            updateStatus(label)
        } catch (e: Exception) {
            updateStatus("Scan error: ${e.message}")
        }
    }

    private fun updateStatus(text: String) {
        notificationManager.notify(NOTIF_ID_STATUS, buildStatusNotification(text))
    }

    private fun notifyNewSignals(hits: List<PatternResult>) {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // One grouped notification per cycle instead of one-per-signal, so a
        // NIFTY100 scan that turns up 8 new setups doesn't fire 8 separate alerts.
        val lines = hits.take(8).map {
            "${it.symbol}: ${it.patternLabel} (${(it.accuracy * 100).toInt()}% hist. accuracy)"
        }
        val style = NotificationCompat.InboxStyle()
        lines.forEach { style.addLine(it) }
        val title = if (hits.size == 1) "New signal: ${hits[0].symbol}" else "${hits.size} new signals"

        val notif = NotificationCompat.Builder(this, CHANNEL_SIGNALS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull() ?: "")
            .setStyle(style)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(NOTIF_ID_SIGNAL_BASE + (System.currentTimeMillis() % 10000).toInt(), notif)
    }

    private fun buildStatusNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, PatternScanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Live pattern scanner running")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val statusChannel = NotificationChannel(
                CHANNEL_STATUS, "Scanner status", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Ongoing 'scanner is running' notification" }
            val signalChannel = NotificationChannel(
                CHANNEL_SIGNALS, "New pattern signals", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alerts when the background scanner finds a new active pattern" }
            notificationManager.createNotificationChannel(statusChannel)
            notificationManager.createNotificationChannel(signalChannel)
        }
    }

    companion object {
        const val ACTION_STOP = "com.marketdata.app.action.STOP_SCAN"
        private const val CHANNEL_STATUS = "scanner_status"
        private const val CHANNEL_SIGNALS = "scanner_signals"
        private const val NOTIF_ID_STATUS = 9001
        private const val NOTIF_ID_SIGNAL_BASE = 9100
    }
}
