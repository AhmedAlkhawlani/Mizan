package com.maknoon.mizan.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maknoon.mizan.R
import com.maknoon.mizan.core.designsystem.theme.MizanTheme
import com.maknoon.mizan.core.designsystem.theme.statusColors
import com.maknoon.mizan.core.platform.usage.UsageAccess
import com.maknoon.mizan.monitoring.MonitoringControl
import com.maknoon.mizan.monitoring.MonitoringStateHolder
import com.maknoon.mizan.ui.component.AppTopBar
import com.maknoon.mizan.ui.component.AppTopBarRow

/**
 * The first-run walkthrough (docs/05, 7.3): what the app does, the two permissions it can ask
 * for, and the measurement switch. Every step is optional except finishing, because monitoring
 * works without usage access and without notifications, so the flow never traps the operator.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val monitoringState by MonitoringStateHolder.state.collectAsStateWithLifecycle()
    var usageGranted by remember { mutableStateOf(UsageAccess.isGranted(context)) }
    var notificationsAllowed by remember { mutableStateOf(notificationsAllowed(context)) }
    var pendingMonitoring by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        notificationsAllowed = notificationsAllowed(context)
        // Measurement never waits on the notification permission; if the operator asked to
        // start it, it starts whether or not the banner will be visible.
        if (pendingMonitoring) {
            pendingMonitoring = false
            MonitoringControl.start(context)
        }
    }

    // Returning from the Usage access list or the notification settings refreshes both rows.
    LifecycleResumeEffect(Unit) {
        usageGranted = UsageAccess.isGranted(context)
        notificationsAllowed = notificationsAllowed(context)
        onPauseOrDispose { }
    }

    OnboardingContent(
        usageGranted = usageGranted,
        notificationsAllowed = notificationsAllowed,
        monitoringRunning = monitoringState.running,
        onOpenUsageSettings = { context.startActivity(UsageAccess.settingsIntent()) },
        onManageNotifications = {
            if (needsNotificationPermission(context)) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(notificationSettingsIntent(context))
            }
        },
        onStartMonitoring = {
            if (needsNotificationPermission(context)) {
                pendingMonitoring = true
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                MonitoringControl.start(context)
            }
        },
        onFinished = onFinished,
        modifier = modifier,
    )
}

@Composable
internal fun OnboardingContent(
    usageGranted: Boolean,
    notificationsAllowed: Boolean,
    monitoringRunning: Boolean,
    onOpenUsageSettings: () -> Unit,
    onManageNotifications: () -> Unit,
    onStartMonitoring: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar {
            AppTopBarRow {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.onboarding_tagline),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shield),
                        contentDescription = null,
                        tint = MaterialTheme.statusColors.guard,
                        modifier = Modifier.size(28.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.onboarding_privacy_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = stringResource(R.string.onboarding_privacy_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            StepCard(
                title = stringResource(R.string.apps_access_title),
                body = stringResource(R.string.apps_access_body),
                statusText = stringResource(R.string.onboarding_status_not_granted),
                statusGranted = usageGranted,
                grantedText = stringResource(R.string.onboarding_status_granted),
            ) {
                if (!usageGranted) {
                    OutlinedButton(onClick = onOpenUsageSettings) {
                        Text(text = stringResource(R.string.apps_access_action))
                    }
                }
            }

            StepCard(
                title = stringResource(R.string.onboarding_notifications_title),
                body = stringResource(R.string.onboarding_notifications_body),
                statusText = stringResource(R.string.onboarding_status_not_allowed),
                statusGranted = notificationsAllowed,
                grantedText = stringResource(R.string.onboarding_status_allowed),
            ) {
                OutlinedButton(onClick = onManageNotifications) {
                    Text(
                        text = stringResource(
                            if (needsNotificationPermission(LocalContext.current)) {
                                R.string.onboarding_notifications_allow
                            } else {
                                R.string.onboarding_notifications_manage
                            },
                        ),
                    )
                }
            }

            StepCard(
                title = stringResource(R.string.onboarding_monitoring_title),
                body = stringResource(R.string.onboarding_monitoring_body),
                statusText = stringResource(R.string.onboarding_monitoring_running),
                statusGranted = monitoringRunning,
                grantedText = stringResource(R.string.onboarding_monitoring_running),
                showMissingStatus = false,
            ) {
                if (!monitoringRunning) {
                    OutlinedButton(onClick = onStartMonitoring) {
                        Text(text = stringResource(R.string.dashboard_start))
                    }
                }
            }

            StepCard(
                title = stringResource(R.string.onboarding_saver_title),
                body = stringResource(R.string.onboarding_saver_body),
            )

            Text(
                text = stringResource(R.string.onboarding_footer_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Button(
                onClick = onFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(text = stringResource(R.string.onboarding_finish))
            }
            TextButton(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.onboarding_skip))
            }
        }
    }
}

/**
 * One optional setup step. The status line always states the state in words, so it never
 * depends on colour alone, and a granted step drops its action because there is nothing to do.
 */
@Composable
private fun StepCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    statusText: String = "",
    statusGranted: Boolean = false,
    grantedText: String = "",
    showMissingStatus: Boolean = true,
    action: (@Composable () -> Unit)? = null,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (statusGranted || showMissingStatus) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (statusGranted) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check_circle),
                            contentDescription = null,
                            tint = MaterialTheme.statusColors.guard,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = if (statusGranted) grantedText else statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (statusGranted) {
                            MaterialTheme.statusColors.guard
                        } else {
                            MaterialTheme.statusColors.warning
                        },
                    )
                }
            }
            action?.invoke()
        }
    }
}

private fun notificationsAllowed(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        !needsNotificationPermission(context)

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED

private fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@Preview(name = "Onboarding — Arabic", locale = "ar", showBackground = true)
@Composable
private fun OnboardingPreview() {
    MizanTheme(darkTheme = true, dynamicColor = false) {
        OnboardingContent(
            usageGranted = true,
            notificationsAllowed = false,
            monitoringRunning = true,
            onOpenUsageSettings = {},
            onManageNotifications = {},
            onStartMonitoring = {},
            onFinished = {},
        )
    }
}
