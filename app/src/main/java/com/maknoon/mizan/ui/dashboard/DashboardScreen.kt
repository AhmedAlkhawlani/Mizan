package com.maknoon.mizan.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maknoon.mizan.R
import com.maknoon.mizan.core.designsystem.theme.MizanTheme
import com.maknoon.mizan.core.designsystem.theme.statusColors
import com.maknoon.mizan.monitoring.MonitoringControl

/**
 * The live dashboard from docs/04: download and upload rates, today's total, the status of the
 * monitor and a single start/stop control.
 */
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(),
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
        modifier = modifier,
    )
}

@Composable
internal fun DashboardContent(
    uiState: DashboardUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SpeedCard(uiState)
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
                tint = MaterialTheme.statusColors.dataDown,
                modifier = Modifier.weight(1f),
            )
            SpeedFigure(
                label = stringResource(R.string.dashboard_speed_up),
                arrow = "↑",
                value = uiState.upSpeed,
                tint = MaterialTheme.statusColors.dataUp,
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
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$arrow $label",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = tint,
            textAlign = TextAlign.Center,
        )
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
            Column {
                Text(
                    text = stringResource(R.string.dashboard_today),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = uiState.todayTotal,
                    style = MaterialTheme.typography.titleLarge,
                )
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
    MizanTheme(dynamicColor = false) {
        DashboardContent(
            uiState = DashboardUiState(
                running = true,
                speedAvailable = true,
                downSpeed = "12.4 MB/s",
                upSpeed = "1.2 MB/s",
                todayTotal = "1.24 GB",
            ),
            onStart = {},
            onStop = {},
        )
    }
}
