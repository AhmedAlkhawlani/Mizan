package com.maknoon.mizan.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maknoon.mizan.core.designsystem.theme.statusColors

/** One column of a stacked traffic chart; [fraction] is its height relative to the tallest. */
data class UsageBar(
    val rxBytes: Long,
    val txBytes: Long,
    val fraction: Float,
)

/**
 * Stacked bars: uploads on top, downloads below, both clipped to one rounded column so a
 * partially filled bar still reads as one day.
 */
@Composable
fun UsageBarChart(
    bars: List<UsageBar>,
    height: Dp,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val downColor = MaterialTheme.statusColors.dataDown
    val upColor = MaterialTheme.statusColors.dataUp
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription },
    ) {
        if (bars.isEmpty()) return@Canvas

        val gap = 4.dp.toPx()
        val slot = (size.width - gap * (bars.size - 1)) / bars.size
        if (slot <= 0f) return@Canvas
        val corner = CornerRadius(minOf(slot / 2f, 6.dp.toPx()))

        bars.forEachIndexed { index, bar ->
            val left = index * (slot + gap)
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(left, 0f),
                size = Size(slot, size.height),
                cornerRadius = corner,
            )

            val barHeight = size.height * bar.fraction.coerceIn(0f, 1f)
            if (barHeight <= 0f) return@forEachIndexed

            val top = size.height - barHeight
            val total = bar.rxBytes + bar.txBytes
            val downHeight = if (total > 0L) barHeight * (bar.rxBytes.toFloat() / total) else barHeight
            val upHeight = barHeight - downHeight

            val column = Path().apply {
                addRoundRect(RoundRect(left, top, left + slot, size.height, corner))
            }
            clipPath(column) {
                if (upHeight > 0f) {
                    drawRect(upColor, topLeft = Offset(left, top), size = Size(slot, upHeight))
                }
                if (downHeight > 0f) {
                    drawRect(
                        downColor,
                        topLeft = Offset(left, top + upHeight),
                        size = Size(slot, downHeight),
                    )
                }
            }
        }
    }
}
