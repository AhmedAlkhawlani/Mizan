package com.maknoon.mizan.ui.history

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maknoon.mizan.R
import com.maknoon.mizan.core.designsystem.component.UsageBar
import com.maknoon.mizan.core.designsystem.component.UsageBarChart
import com.maknoon.mizan.core.designsystem.theme.MizanTheme
import com.maknoon.mizan.core.designsystem.theme.statusColors
import com.maknoon.mizan.core.domain.cycle.BillingCycleCalculator
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    val context = LocalContext.current
    val savedMessage = stringResource(R.string.history_export_saved)
    val failedMessage = stringResource(R.string.history_export_failed)

    // ACTION_CREATE_DOCUMENT beneath the contract's hood: the system picker creates the file
    // and grants this app write access to it alone, so the manifest needs no storage permission.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> if (uri != null) viewModel.exportCsv(uri) }

    LaunchedEffect(viewModel, savedMessage, failedMessage) {
        viewModel.exportResult.collect { result ->
            val message = if (result == ExportResult.SAVED) savedMessage else failedMessage
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    HistoryContent(
        uiState = uiState,
        onCycleStartDayChange = viewModel::setCycleStartDay,
        onExportRequest = {
            exportLauncher.launch("mizan-usage-${LocalDate.now()}.csv")
        },
        modifier = modifier,
    )
}

@Composable
internal fun HistoryContent(
    uiState: HistoryUiState,
    onCycleStartDayChange: (Int) -> Unit,
    onExportRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "cycle") {
            CycleCard(uiState = uiState, onCycleStartDayChange = onCycleStartDayChange)
        }
        if (!uiState.hasData) {
            item(key = "empty") { EmptyHistoryCard() }
        } else {
            item(key = "days") { DailyChartCard(uiState) }
            item(key = "hours") { HourlyChartCard(uiState) }
            if (uiState.anyPartial) item(key = "partial") { PartialLegend() }
            item(key = "note") { ObservationNote() }
            item(key = "export") { ExportCard(onExportRequest = onExportRequest) }
        }
    }
}

/**
 * Hands the stored days to the system file picker. Nothing is uploaded and nothing is shared:
 * the operator chooses a folder, and the file belongs to them.
 */
@Composable
private fun ExportCard(onExportRequest: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.history_export_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.history_export_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onExportRequest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.history_export_action))
            }
        }
    }
}

/**
 * The operator's billing period: its date range, the traffic recorded inside it, and the
 * day-of-month it starts on. Changing the start day re-reads the cycle from Room.
 */
@Composable
private fun CycleCard(
    uiState: HistoryUiState,
    onCycleStartDayChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.history_cycle_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (uiState.cycleStartEpochDay > 0L && uiState.cycleEndEpochDay >= uiState.cycleStartEpochDay) {
                Text(
                    text = cycleRangeLabel(uiState.cycleStartEpochDay, uiState.cycleEndEpochDay),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.history_cycle_total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = uiState.cycleTotalLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.history_cycle_start_day),
                    style = MaterialTheme.typography.bodyMedium,
                )
                CycleStartDayStepper(
                    day = uiState.cycleStartDay,
                    onDayChange = onCycleStartDayChange,
                )
            }
            if (uiState.cyclePartial) {
                Text(
                    text = stringResource(R.string.history_cycle_partial),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.statusColors.warning,
                )
            }
        }
    }
}

@Composable
private fun CycleStartDayStepper(
    day: Int,
    onDayChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val decreaseLabel = stringResource(R.string.history_cycle_start_decrease)
    val increaseLabel = stringResource(R.string.history_cycle_start_increase)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onDayChange(day - 1) },
            enabled = day > BillingCycleCalculator.MIN_START_DAY,
        ) {
            Text(
                text = "−",
                modifier = Modifier.semantics { contentDescription = decreaseLabel },
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Text(
            text = day.toString(),
            modifier = Modifier.widthIn(min = 32.dp),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        IconButton(
            onClick = { onDayChange(day + 1) },
            enabled = day < BillingCycleCalculator.MAX_START_DAY,
        ) {
            Text(
                text = "+",
                modifier = Modifier.semantics { contentDescription = increaseLabel },
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

/**
 * Renders the two ends as day-of-month plus a localised month name. The day stays a Western
 * number per docs/04, which also keeps the label stable in RTL where digits do not mirror.
 */
@Composable
private fun cycleRangeLabel(startEpochDay: Long, endEpochDay: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("MMM", locale) }
    val start = LocalDate.ofEpochDay(startEpochDay)
    val end = LocalDate.ofEpochDay(endEpochDay)
    return stringResource(
        R.string.history_cycle_range,
        "${start.dayOfMonth} ${start.format(monthFormat)}",
        "${end.dayOfMonth} ${end.format(monthFormat)}",
    )
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
                    UsageBarChart(
                        bars = uiState.days.map { UsageBar(it.rxBytes, it.txBytes, it.fraction) },
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
                    UsageBarChart(
                        bars = uiState.hours.map { UsageBar(it.rxBytes, it.txBytes, it.fraction) },
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
        cycleStartDay = 15,
        cycleStartEpochDay = today - 5,
        cycleEndEpochDay = today + 24,
        cycleTotalLabel = "1.85 GB",
        cyclePartial = true,
        anyPartial = true,
        hasData = true,
    )
    MizanTheme(dynamicColor = false) {
        HistoryContent(uiState = preview, onCycleStartDayChange = {}, onExportRequest = {})
    }
}
