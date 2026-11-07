package com.maknoon.mizan.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maknoon.mizan.core.designsystem.theme.statusColors

/**
 * A thin bar filled to [fraction] of its width.
 *
 * Drawn here rather than with `LinearProgressIndicator` for two reasons: the Material component
 * paints its track in `secondaryContainer` — which is the upload pink in this theme, so the empty
 * half of a bar reads as a second data series — and it adds a stop-indicator dot at the end of
 * the track, which at 4dp looks like a stray dot beside a short bar. Both are wrong for a figure
 * that is only meant to compare one app against another.
 *
 * The fill follows the layout direction, so an RTL screen fills from the right like its text.
 */
@Composable
fun FractionBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.statusColors.dataDown,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    height: Dp = 4.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color),
        )
    }
}
