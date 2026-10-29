package com.marketdata.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.marketdata.app.ui.theme.AccentAmber
import com.marketdata.app.ui.theme.AccentBlue
import com.marketdata.app.ui.theme.DarkCard
import com.marketdata.app.ui.theme.TextMuted
import com.marketdata.app.ui.theme.TextSecondary

/**
 * One consistent look for notices across the app: a dark card with a thin
 * coloured outline, an optional bold title and a short message.
 * Use amber for "heads up" warnings and blue for neutral information.
 */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    accent: Color = AccentAmber
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (title != null) {
                Text(title, color = accent, style = MaterialTheme.typography.labelLarge)
            }
            Text(text, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Screen title with an optional one-line subtitle, used at the top of every tab. */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = AccentBlue)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}
