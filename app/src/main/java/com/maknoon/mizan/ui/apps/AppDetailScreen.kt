package com.maknoon.mizan.ui.apps

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
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
import com.maknoon.mizan.core.domain.format.ByteFormatter
import java.time.LocalDate

/**
 * One app's last seven days (docs/04): a stacked chart and the Wi-Fi/mobile split.
 *
 * The chart is fixed at seven columns ending today, so a day the sync did not cover leaves an
 * empty column instead of shifting the axis, and the note says as much.
 */
@Composable
fun AppDetailScreen(
    packageName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application, packageName) {
        AppDetailViewModel.factory(application, packageName)
    }
    val viewModel: AppDetailViewModel = viewModel(key = packageName, factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AppDetailContent(uiState = uiState, onBack = onBack, modifier = modifier)
}

@Composable
internal fun AppDetailContent(
    uiState: AppDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") { DetailHeader(uiState = uiState, onBack = onBack) }
        if (!uiState.hasData) {
            item(key = "empty") { EmptyDetailCard() }
        } else {
            item(key = "chart") { DetailChartCard(uiState) }
            item(key = "split") { SplitCard(uiState.split) }
            item(key = "note") {
                Text(
                    text = stringResource(R.string.app_detail_note),
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DetailHeader(
    uiState: AppDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.app_detail_back),
            )
        }
        AppIcon(packageName = uiState.packageName)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = uiState.label,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = uiState.packageName,
                style = MaterialTheme.typography.bodySmall.copy(
                    textDirection = TextDirection.Ltr,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailChartCard(uiState: AppDetailUiState, modifier: Modifier = Modifier) {
    val totalLabel = ByteFormatter.data(uiState.totalBytes)
    val description = stringResource(R.string.app_detail_chart_description, totalLabel)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.history_last_7_days),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.app_detail_total, totalLabel),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ChartLegend()
            // As on the History screen, the canvas and its axis stay LTR so bars and labels
            // align inside the otherwise RTL layout.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    UsageBarChart(
                        bars = uiState.days.map { UsageBar(it.rxBytes, it.txBytes, it.fraction) },
                        height = 140.dp,
                        contentDescription = description,
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val today = uiState.days.lastOrNull()?.epochDay
                        uiState.days.forEach { day ->
                            DayAxisLabel(
                                epochDay = day.epochDay,
                                isToday = day.epochDay == today,
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
private fun ChartLegend(modifier: Modifier = Modifier) {
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
private fun DayAxisLabel(
    epochDay: Long,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = LocalDate.ofEpochDay(epochDay).dayOfMonth.toString(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = if (isToday) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SplitCard(split: AppNetworkSplitUi, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.app_detail_split_title),
                style = MaterialTheme.typography.titleMedium,
            )
            SplitRow(
                label = stringResource(R.string.dashboard_mobile),
                rxBytes = split.mobileRxBytes,
                txBytes = split.mobileTxBytes,
            )
            SplitRow(
                label = stringResource(R.string.dashboard_wifi),
                rxBytes = split.wifiRxBytes,
                txBytes = split.wifiTxBytes,
            )
        }
    }
}

@Composable
private fun SplitRow(
    label: String,
    rxBytes: Long,
    txBytes: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(
                R.string.app_detail_split_line,
                ByteFormatter.data(rxBytes),
                ByteFormatter.data(txBytes),
            ),
            style = MaterialTheme.typography.bodyMedium.copy(
                textDirection = TextDirection.Ltr,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyDetailCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.apps_empty_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.app_detail_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(name = "App detail — Arabic", locale = "ar", showBackground = true)
@Composable
private fun AppDetailPreview() {
    val today = 20_758L
    val preview = AppDetailUiState(
        packageName = "com.google.android.youtube",
        label = "YouTube",
        days = List(7) { index ->
            AppDayBarUi(
                epochDay = today - 6 + index,
                rxBytes = (index + 1) * 3_000_000L,
                txBytes = (index + 1) * 500_000L,
                fraction = (index + 1) / 7f,
            )
        },
        split = AppNetworkSplitUi(
            mobileRxBytes = 8_000_000,
            mobileTxBytes = 1_000_000,
            wifiRxBytes = 20_000_000,
            wifiTxBytes = 3_500_000,
        ),
        totalBytes = 32_500_000,
        lastSyncedAtMillis = 1_762_000_000_000,
    )
    MizanTheme(dynamicColor = false) {
        AppDetailContent(uiState = preview, onBack = {})
    }
}
