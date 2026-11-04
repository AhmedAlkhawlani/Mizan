package com.maknoon.mizan.ui.dashboard

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maknoon.mizan.R
import com.maknoon.mizan.core.designsystem.theme.MizanTheme
import com.maknoon.mizan.core.designsystem.theme.statusColors
import com.maknoon.mizan.monitoring.MonitoringControl
import com.maknoon.mizan.ui.component.AppTopBar
import com.maknoon.mizan.ui.component.AppTopBarRow

private val NeonDown = Color(0xFF00E5FF)
private val NeonUp = Color(0xFFFF4081)

/**
 * The live dashboard from docs/04: download and upload rates, today's total, the status of the
 * monitor and a single start/stop control.
 *
 * The header belongs to the screen (concept board): the app name, a glowing metered/unmetered
 * chip and the budget-settings shortcut replace the old global top bar.
 */
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onOpenBudget: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.factory(
            LocalContext.current.applicationContext as Application,
        ),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Monitoring does not depend on the notification permission: without it the service
        // still measures, it simply has no visible banner. Blocking the core feature on a
        // notification prompt would be the wrong trade.
        MonitoringControl.start(context)
    }

    DashboardContent(
        uiState = uiState,
        onStart = {
            if (needsNotificationPermission(context)) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                MonitoringControl.start(context)
            }
        },
        onStop = { MonitoringControl.stop(context) },
        onOpenBudget = onOpenBudget,
        modifier = modifier,
    )
}

@Composable
internal fun DashboardContent(
    uiState: DashboardUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenBudget: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar {
            AppTopBarRow {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.size(8.dp))
                NetworkChip(metered = uiState.networkMetered)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenBudget) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = stringResource(R.string.dashboard_budget_settings),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SpeedCard(uiState)
            if (uiState.dailyBudgetLimit != null) {
                BudgetBarCard(uiState)
            }
            TodayCard(uiState)
            StatusText(uiState)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = if (uiState.running) onStop else onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    text = stringResource(
                        if (uiState.running) R.string.dashboard_stop else R.string.dashboard_start,
                    ),
                )
            }
        }
    }
}

/** docs/02 §4.6: the chip reports the resolved profile of the current default network. */
@Composable
private fun NetworkChip(metered: Boolean?, modifier: Modifier = Modifier) {
    val (labelRes, tint) = when (metered) {
        true -> R.string.network_chip_metered to MaterialTheme.statusColors.warning
        false -> R.string.network_chip_unmetered to MaterialTheme.statusColors.guard
        null -> R.string.saver_network_disconnected to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .border(1.dp, tint.copy(alpha = 0.45f), shape)
            .background(tint.copy(alpha = 0.10f), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
    }
}

@Composable
private fun SpeedCard(uiState: DashboardUiState, modifier: Modifier = Modifier) {
    // Merged so TalkBack reads each figure with its label as one unit (docs/04).
    Card(modifier = modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) {}) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SpeedFigure(
                label = stringResource(R.string.dashboard_speed_down),
                arrow = "↓",
                value = uiState.downSpeed,
                tint = NeonDown,
                modifier = Modifier.weight(1f),
            )
            SpeedFigure(
                label = stringResource(R.string.dashboard_speed_up),
                arrow = "↑",
                value = uiState.upSpeed,
                tint = NeonUp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SpeedFigure(
    label: String,
    arrow: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    // The unit is split off so a three-digit speed shrinks its unit instead of wrapping mid-token.
    val number = value.substringBeforeLast(' ', value)
    val unit = value.substringAfterLast(' ', "")
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$arrow $label",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row {
                Text(
                    text = number,
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        textDirection = TextDirection.Ltr,
                    ),
                    fontWeight = FontWeight.ExtraBold,
                    color = tint,
                    maxLines = 1,
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = " $unit",
                        modifier = Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            textDirection = TextDirection.Ltr,
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = tint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The concept board's glowing budget bar: today's total against the enabled daily limit. */
@Composable
private fun BudgetBarCard(uiState: DashboardUiState, modifier: Modifier = Modifier) {
    val limit = uiState.dailyBudgetLimit ?: return
    val fraction = uiState.dailyBudgetFraction.coerceIn(0f, 1f)
    val fill = Brush.horizontalGradient(
        listOf(MaterialTheme.colorScheme.primary, NeonDown),
    )
    val barShape = RoundedCornerShape(999.dp)

    Card(modifier = modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) {}) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_today),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.dashboard_budget_line,
                        uiState.todayTotal,
                        limit,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
            ) {
                // A blurred duplicate behind the bar produces the neon glow from the board.
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .blur(6.dp)
                        .background(fill),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(barShape)
                        .background(Color.White.copy(alpha = 0.07f)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(barShape)
                        .background(fill),
                )
            }
        }
    }
}

@Composable
private fun TodayCard(uiState: DashboardUiState, modifier: Modifier = Modifier) {
    Card(modifier = modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) {}) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.dashboard_today),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = uiState.todayTotal,
                    style = MaterialTheme.typography.titleLarge.copy(
                        textDirection = TextDirection.Ltr,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ChannelFigure(
                        label = stringResource(R.string.dashboard_mobile),
                        value = uiState.mobileTotal,
                    )
                    ChannelFigure(
                        label = stringResource(R.string.dashboard_wifi),
                        value = uiState.wifiTotal,
                    )
                }
            }
            if (uiState.partial) {
                Text(
                    text = stringResource(R.string.dashboard_partial),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.statusColors.warning,
                )
            }
        }
    }
}

@Composable
private fun ChannelFigure(label: String, value: String, modifier: Modifier = Modifier) {
    Text(
        text = "$label $value",
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatusText(uiState: DashboardUiState, modifier: Modifier = Modifier) {
    val (textRes, tint) = when {
        !uiState.running -> R.string.dashboard_status_stopped to MaterialTheme.colorScheme.onSurfaceVariant
        !uiState.speedAvailable -> R.string.dashboard_status_unavailable to MaterialTheme.statusColors.danger
        else -> R.string.dashboard_status_running to MaterialTheme.statusColors.guard
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(
                if (uiState.running) R.drawable.ic_notification_monitor else R.drawable.ic_shield,
            ),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
        )
    }
}

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED

@Preview(name = "Dashboard — running, Arabic", locale = "ar", showBackground = true)
@Composable
private fun DashboardRunningPreview() {
    MizanTheme(darkTheme = true, dynamicColor = false) {
        DashboardContent(
            uiState = DashboardUiState(
                running = true,
                speedAvailable = true,
                downSpeed = "12.4 MB/s",
                upSpeed = "1.2 MB/s",
                todayTotal = "1.24 GB",
                mobileTotal = "120 MB",
                wifiTotal = "1.12 GB",
                networkMetered = true,
                dailyBudgetLimit = "2.00 GB",
                dailyBudgetFraction = 0.62f,
            ),
            onStart = {},
            onStop = {},
        )
    }
}
