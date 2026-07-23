package com.marketdata.app.volumefilter

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Volume filter mode. Both a raw threshold and a "top N most liquid" cut are
 * supported since they behave very differently depending on the universe
 * being filtered (e.g. NIFTY100 vs the full NSE equity list).
 */
sealed class VolumeFilterMode {
    object Off : VolumeFilterMode()
    data class MinThreshold(val minVolume: Long) : VolumeFilterMode()
    data class TopN(val count: Int) : VolumeFilterMode()

    fun serialize(): String = when (this) {
        is Off -> "OFF"
        is MinThreshold -> "MIN:$minVolume"
        is TopN -> "TOPN:$count"
    }

    companion object {
        fun deserialize(raw: String?): VolumeFilterMode {
            if (raw.isNullOrBlank()) return Off
            return when {
                raw == "OFF" -> Off
                raw.startsWith("MIN:") -> MinThreshold(raw.removePrefix("MIN:").toLongOrNull() ?: 0L)
                raw.startsWith("TOPN:") -> TopN(raw.removePrefix("TOPN:").toIntOrNull() ?: 100)
                else -> Off
            }
        }
    }
}

/**
 * One instance per screen (Download / Live Quotes / Scanner / Agent), each
 * with its own [screenKey] so the setting is independent per screen rather
 * than a single global toggle. Backed by plain SharedPreferences (not the
 * app's EncryptedSharedPreferences) since this is a UI preference, not
 * sensitive data.
 */
class VolumeFilterStore(context: Context, private val screenKey: String) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("volume_filter_prefs", Context.MODE_PRIVATE)

    private val prefKey = "volume_filter_mode_$screenKey"

    var mode: VolumeFilterMode by mutableStateOf(
        VolumeFilterMode.deserialize(prefs.getString(prefKey, null))
    )
        private set

    fun setMode(newMode: VolumeFilterMode) {
        mode = newMode
        prefs.edit().putString(prefKey, newMode.serialize()).apply()
    }
}

/** Pure + reusable across all four screens regardless of where the volume
 *  number came from (cached daily snapshot, live tick, or an OHLCV row). */
fun <T> applyVolumeFilter(
    items: List<T>,
    volumeOf: (T) -> Long,
    mode: VolumeFilterMode
): List<T> = when (mode) {
    is VolumeFilterMode.Off -> items
    is VolumeFilterMode.MinThreshold -> items.filter { volumeOf(it) >= mode.minVolume }
    is VolumeFilterMode.TopN -> items.sortedByDescending(volumeOf).take(mode.count)
}
