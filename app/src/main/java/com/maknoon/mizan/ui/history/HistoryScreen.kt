package com.maknoon.mizan.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maknoon.mizan.R
import com.maknoon.mizan.core.designsystem.theme.MizanTheme
import com.maknoon.mizan.core.designsystem.theme.statusColors
import java.time.LocalDate

/**
 * The history section from docs/04: the last seven days and today's hours.
 *
 * Both charts use the brand's cyan for downloads and pink for uploads, stacked so the total
 * height is the day's traffic. Everything drawn comes from Room — the screen never guesses a
 * value the service did not observe.
 */
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryContent(uiState = uiState, modifier = modifier)
}

@Composable
internal fun HistoryContent(uiState: HistoryUiState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!uiState.hasData) {
            item(key = "empty") { EmptyHistoryCard() }
        } else {
            item(key = "days") { DailyChartCard(uiState) }
            item(key = "hours") { HourlyChartCard(uiState) }
            if (uiState.anyPartial) item(key = "partial") { PartialLegend() }
            item(key = "note") { ObservationNote() }
        }
    }
}

@Composable
private fun DailyChartCard(uiState: HistoryUiState, modifier: Modifier = Modifier) {
    val baseDescription = stringResource(
        R.string.history_daily_chart_description,
        uiState.weekTotalLabel,
    )
    val description = if (uiState.anyPartial) {
        baseDescription + " " + stringResource(R.string.history_partial_content_description)
    } else {
        baseDescription
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChartHeader(
                title = stringResource(R.string.history_last_7_days),
                total = stringResource(R.string.history_total, uiState.weekTotalLabel),
            )
            Legend()
            // The canvas always paints its list left-to-right. Wrapping the canvas and its axis
            // in LTR keeps bars and labels aligned in the otherwise RTL screen; time therefore
            // reads oldest-to-newest, like the figures, which stay Western digits per docs/04.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    UsageChart(
                        bars = uiState.days.map { ChartBar(it.rxBytes, it.txBytes, it.fraction) },
                        height = 140.dp,
                        contentDescription = description,
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        uiState.days.forEach { day ->
                            DayLabel(
                                day = day,
                                isToday = day.epochDay == uiState.todayEpochDay,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyChartCard(uiState: HistoryUiState, modifier: Modifier = Modifier) {
    val description = stringResource(
        R.string.history_hourly_chart_description,
        uiState.todayTotalLabel,
    )

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChartHeader(
                title = stringResource(R.string.history_today_hours),
                total = stringResource(R.string.history_today_total, uiState.todayTotalLabel),
            )
            Legend()
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    UsageChart(
                        bars = uiState.hours.map { ChartBar(it.rxBytes, it.txBytes, it.fraction) },
                        height = 120.dp,
                        contentDescription = description,
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        HOUR_AXIS_LABELS.forEach { hour ->
                            Text(
                                text = hour.toString(),
                                modifier = Modifier.weight(6f),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartHeader(title: String, total: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = total,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Legend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(
            color = MaterialTheme.statusColors.dataDown,
            label = stringResource(R.string.history_legend_down),
        )
        LegendItem(
            color = MaterialTheme.statusColors.dataUp,
            label = stringResource(R.string.history_legend_up),
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayLabel(day: DayBarUi, isToday: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    if (day.partial) MaterialTheme.statusColors.warning else Color.Transparent,
                    CircleShape,
                ),
        )
        Text(
            // Day-of-month keeps the axis legible in seven narrow columns; the year and month
            // are unambiguous inside a seven-day window, and digits stay Western per docs/04.
            text = LocalDate.ofEpochDay(day.epochDay).dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PartialLegend(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.history_partial_legend),
        modifier = modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.statusColors.warning,
    )
}

@Composable
private fun ObservationNote(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.history_observed_note),
        modifier = modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EmptyHistoryCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_history),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.history_empty_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.history_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private data class ChartBar(val rxBytes: Long, val txBytes: Long, val fraction: Float)

/**
 * Stacked bars: uploads on top, downloads below, both clipped to one rounded column so a
 * partially filled bar still reads as a single day.
 */
@Composable
private fun UsageChart(
    bars: List<ChartBar>,
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

private val HOUR_AXIS_LABELS = listOf(0, 6, 12, 18)

@Preview(name = "History — Arabic", locale = "ar", showBackground = true)
@Composable
private fun HistoryPreview() {
    val today = 20_758L
    val preview = HistoryUiState(
        days = List(HistoryUiState.HISTORY_DAYS) { index ->
            DayBarUi(
                epochDay = today - (HistoryUiState.HISTORY_DAYS - 1) + index,
                rxBytes = (index + 1) * 240_000_000L,
                txBytes = (index + 1) * 40_000_000L,
                fraction = (index + 1) / HistoryUiState.HISTORY_DAYS.toFloat(),
                partial = index == HistoryUiState.HISTORY_DAYS - 1,
            )
        },
        hours = List(24) { hour ->
            HourBarUi(
                hour = hour,
                rxBytes = if (hour in 9..22) (hour - 8) * 12_000_000L else 0L,
                txBytes = if (hour in 9..22) 2_000_000L else 0L,
                fraction = if (hour in 9..22) (hour - 8) / 14f else 0f,
            )
        },
        todayEpochDay = today,
        weekTotalLabel = "2.31 GB",
        todayTotalLabel = "280 MB",
        anyPartial = true,
        hasData = true,
    )
    MizanTheme(dynamicColor = false) {
        HistoryContent(uiState = preview)
    }
}
