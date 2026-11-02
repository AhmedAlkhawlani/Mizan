package com.maknoon.mizan.ui.saver

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maknoon.mizan.R
import com.maknoon.mizan.core.designsystem.theme.MizanTheme
import com.maknoon.mizan.core.designsystem.theme.statusColors
import com.maknoon.mizan.saver.SaverFailure
import com.maknoon.mizan.ui.apps.AppIcon
import java.util.LinkedHashSet

/**
 * The Guard section (docs/04).
 *
 * Consent is a system concept and stays in the composable; the blocklist and the tunnel state
 * live in [SaverViewModel] and [com.maknoon.mizan.saver.SaverStateHolder]. The screen never
 * claims protection is active unless the service reports an established tunnel.
 */
@Composable
fun SaverScreen(
    modifier: Modifier = Modifier,
    viewModel: SaverViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var consentGranted by remember { mutableStateOf(VpnService.prepare(context) == null) }
    var pendingEnable by rememberSaveable { mutableStateOf(false) }
    var showingPicker by rememberSaveable { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // The system is the authority, not the result code alone: re-check after the dialog.
        val granted = VpnService.prepare(context) == null
        consentGranted = granted
        if (granted && pendingEnable) {
            pendingEnable = false
            viewModel.setEnabled(true)
        } else if (!granted) {
            pendingEnable = false
        }
    }

    val launchConsent: () -> Unit = {
        val consentIntent = VpnService.prepare(context)
        if (consentIntent == null) {
            consentGranted = true
            if (pendingEnable) {
                pendingEnable = false
                viewModel.setEnabled(true)
            }
        } else {
            consentLauncher.launch(consentIntent)
        }
    }

    LifecycleResumeEffect(Unit) {
        val granted = VpnService.prepare(context) == null
        consentGranted = granted
        if (granted && pendingEnable) {
            pendingEnable = false
            viewModel.setEnabled(true)
        }
        onPauseOrDispose { }
    }

    SaverContent(
        uiState = uiState,
        consentGranted = consentGranted,
        onRequestConsent = launchConsent,
        onEnabledChange = { enabled ->
            if (enabled) {
                if (consentGranted) {
                    viewModel.setEnabled(true)
                } else {
                    // Ask for consent first, then turn the switch on if the user grants it.
                    pendingEnable = true
                    launchConsent()
                }
            } else {
                pendingEnable = false
                viewModel.setEnabled(false)
            }
        },
        onChooseApps = { showingPicker = true },
        onAppBlockedChange = viewModel::setAppBlocked,
        modifier = modifier,
    )

    if (showingPicker) {
        SaverAppPickerDialog(
            apps = uiState.apps,
            loading = uiState.appsLoading,
            blockedPackages = uiState.blockedPackages,
            onBlockedChange = viewModel::setAppBlocked,
            onDismiss = { showingPicker = false },
        )
    }
}

@Composable
internal fun SaverContent(
    uiState: SaverUiState,
    consentGranted: Boolean,
    onRequestConsent: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onChooseApps: () -> Unit,
    onAppBlockedChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SaverHero() }
        item {
            ConsentCard(
                consentGranted = consentGranted,
                onRequestConsent = onRequestConsent,
            )
        }
        item {
            FirewallCard(
                uiState = uiState,
                consentGranted = consentGranted,
                onEnabledChange = onEnabledChange,
            )
        }
        item {
            BlockedAppsCard(
                uiState = uiState,
                onChooseApps = onChooseApps,
                onAppBlockedChange = onAppBlockedChange,
            )
        }
        item {
            ExplanationCard(
                titleRes = R.string.saver_how_title,
                bodyRes = R.string.saver_how_body,
            )
        }
        item {
            ExplanationCard(
                titleRes = R.string.saver_privacy_title,
                bodyRes = R.string.saver_privacy_body,
            )
        }
        item {
            ExplanationCard(
                titleRes = R.string.saver_limits_title,
                bodyRes = R.string.saver_limits_body,
                warn = true,
            )
        }
        item {
            Text(
                text = stringResource(R.string.saver_revoke_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SaverHero(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shield),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.statusColors.guard,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.saver_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.saver_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConsentCard(
    consentGranted: Boolean,
    onRequestConsent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(
                        if (consentGranted) R.drawable.ic_check_circle else R.drawable.ic_shield,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.statusColors.guard,
                )
                Text(
                    text = stringResource(
                        if (consentGranted) {
                            R.string.saver_consent_granted_title
                        } else {
                            R.string.saver_consent_title
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Text(
                text = stringResource(
                    if (consentGranted) {
                        R.string.saver_consent_granted_body
                    } else {
                        R.string.saver_consent_body
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!consentGranted) {
                Button(
                    onClick = onRequestConsent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(text = stringResource(R.string.saver_consent_action))
                }
            }
        }
    }
}

@Composable
private fun FirewallCard(
    uiState: SaverUiState,
    consentGranted: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blockedCount = uiState.blockedPackages.size
    val canToggle = consentGranted && (blockedCount > 0 || uiState.enabled)
    val failure = uiState.failure
    val statusText = when {
        failure != null -> stringResource(failure.messageRes())
        uiState.active -> pluralStringResource(
            R.plurals.saver_active_status,
            uiState.activeBlockedCount,
            uiState.activeBlockedCount,
        )

        uiState.unmetered -> stringResource(R.string.saver_status_unmetered)
        uiState.enabled -> stringResource(R.string.saver_status_starting)
        else -> stringResource(R.string.saver_status_stopped)
    }
    val statusColor = when {
        failure != null -> MaterialTheme.colorScheme.error
        uiState.active -> MaterialTheme.statusColors.guard
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val hintRes = when {
        !consentGranted -> R.string.saver_need_consent
        blockedCount == 0 -> R.string.saver_need_apps
        else -> null
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = uiState.enabled,
                        enabled = canToggle,
                        role = Role.Switch,
                        onValueChange = onEnabledChange,
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.saver_firewall_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.saver_firewall_switch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.enabled,
                    onCheckedChange = null,
                    enabled = canToggle,
                )
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor,
            )
            if (hintRes != null) {
                Text(
                    text = stringResource(hintRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BlockedAppsCard(
    uiState: SaverUiState,
    onChooseApps: () -> Unit,
    onAppBlockedChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blockedApps = uiState.apps.filter { it.packageName in uiState.blockedPackages }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.saver_apps_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            when {
                uiState.appsLoading -> Text(
                    text = stringResource(R.string.saver_picker_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                blockedApps.isEmpty() -> Text(
                    text = stringResource(R.string.saver_apps_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> blockedApps.forEach { app ->
                    BlockedAppRow(
                        app = app,
                        onUnblock = { onAppBlockedChange(app.packageName, false) },
                    )
                }
            }
            OutlinedButton(
                onClick = onChooseApps,
                enabled = !uiState.appsLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(text = stringResource(R.string.saver_apps_choose))
            }
            Text(
                text = stringResource(R.string.saver_apps_shared_uid_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BlockedAppRow(
    app: SaverApp,
    onUnblock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = true,
                role = Role.Switch,
                onValueChange = { checked -> if (!checked) onUnblock() },
            )
            .heightIn(min = 56.dp)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = app.packageName)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall
                    .copy(textDirection = TextDirection.Ltr),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = true, onCheckedChange = null)
    }
}

@Composable
internal fun SaverAppPickerDialog(
    apps: List<SaverApp>,
    loading: Boolean,
    blockedPackages: Set<String>,
    onBlockedChange: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            apps
        } else {
            apps.filter { app ->
                app.label.contains(needle, ignoreCase = true) ||
                    app.packageName.contains(needle, ignoreCase = true)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.saver_picker_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.saver_picker_done))
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.saver_picker_search)) },
                )
                when {
                    loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    filtered.isEmpty() -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.saver_picker_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            AppPickerRow(
                                app = app,
                                checked = app.packageName in blockedPackages,
                                onCheckedChange = { checked ->
                                    onBlockedChange(app.packageName, checked)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerRow(
    app: SaverApp,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = app.packageName)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall
                    .copy(textDirection = TextDirection.Ltr),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ExplanationCard(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    modifier: Modifier = Modifier,
    warn: Boolean = false,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = if (warn) MaterialTheme.statusColors.warning else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@StringRes
private fun SaverFailure.messageRes(): Int = when (this) {
    SaverFailure.CONSENT_MISSING -> R.string.saver_failure_consent
    SaverFailure.NO_APPS -> R.string.saver_failure_no_apps
    SaverFailure.ESTABLISH_FAILED -> R.string.saver_failure_establish
    SaverFailure.REVOKED -> R.string.saver_failure_revoked
}

@Preview(name = "Guard — consent needed", locale = "ar", showBackground = true)
@Composable
private fun SaverConsentPreview() {
    MizanTheme(dynamicColor = false) {
        SaverContent(
            uiState = SaverUiState(),
            consentGranted = false,
            onRequestConsent = {},
            onEnabledChange = {},
            onChooseApps = {},
            onAppBlockedChange = { _, _ -> },
        )
    }
}

@Preview(name = "Guard — active", locale = "ar", showBackground = true)
@Composable
private fun SaverActivePreview() {
    val apps = listOf(
        SaverApp(packageName = "com.example.video", label = "تطبيق فيديو"),
        SaverApp(packageName = "com.example.social", label = "شبكة اجتماعية"),
    )
    MizanTheme(dynamicColor = false) {
        SaverContent(
            uiState = SaverUiState(
                enabled = true,
                blockedPackages = apps.mapTo(LinkedHashSet()) { it.packageName },
                apps = apps,
                appsLoading = false,
                serviceRunning = true,
                active = true,
                activeBlockedCount = apps.size,
            ),
            consentGranted = true,
            onRequestConsent = {},
            onEnabledChange = {},
            onChooseApps = {},
            onAppBlockedChange = { _, _ -> },
        )
    }
}
