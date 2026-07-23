package com.marketdata.app.volumefilter

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Drop into the top of each screen's filter row. Pass that screen's own
 * [VolumeFilterStore] so the setting stays independent per screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeFilterBar(
    store: VolumeFilterStore,
    modifier: Modifier = Modifier,
    minThresholdOptions: List<Long> = listOf(100_000L, 500_000L, 1_000_000L, 5_000_000L),
    topNOptions: List<Int> = listOf(50, 100, 150, 200)
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val label = when (val m = store.mode) {
        is VolumeFilterMode.Off -> "Volume: Off"
        is VolumeFilterMode.MinThreshold -> "Volume: Min ${formatVolumeShort(m.minVolume)}"
        is VolumeFilterMode.TopN -> "Volume: Top ${m.count}"
    }

    Box(modifier = modifier) {
        AssistChip(
            onClick = { menuExpanded = true },
            label = { Text(label) }
        )

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Off") },
                onClick = { store.updateMode(VolumeFilterMode.Off); menuExpanded = false }
            )

            Divider()
            Text(
                "Min volume",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall
            )
            minThresholdOptions.forEach { threshold ->
                DropdownMenuItem(
                    text = { Text(formatVolumeShort(threshold)) },
                    onClick = { store.updateMode(VolumeFilterMode.MinThreshold(threshold)); menuExpanded = false }
                )
            }

            Divider()
            Text(
                "Top N by volume",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall
            )
            topNOptions.forEach { n ->
                DropdownMenuItem(
                    text = { Text("Top $n") },
                    onClick = { store.updateMode(VolumeFilterMode.TopN(n)); menuExpanded = false }
                )
            }
        }
    }
}

private fun formatVolumeShort(v: Long): String = when {
    v >= 10_000_000L -> "${v / 10_000_000}Cr"
    v >= 100_000L -> "${v / 100_000}L"
    v >= 1_000L -> "${v / 1_000}K"
    else -> v.toString()
}
