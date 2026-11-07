package com.maknoon.mizan.ui.saver

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.maknoon.mizan.core.designsystem.theme.MizanPalette
import com.maknoon.mizan.core.designsystem.theme.MizanTheme
import com.maknoon.mizan.core.designsystem.theme.statusColors
import com.maknoon.mizan.core.domain.format.ByteFormatter
import com.maknoon.mizan.core.domain.network.NetworkTransport
import com.maknoon.mizan.core.domain.network.ResolvedNetwork
import com.maknoon.mizan.core.domain.saver.BlockedTrafficSnapshot
import com.maknoon.mizan.saver.SaverFailure
import com.maknoon.mizan.ui.apps.AppIcon
import com.maknoon.mizan.ui.component.AppTopBar
import java.util.LinkedHashSet

/**
 * The Guard section (docs/04), styled after the concept board's third phone.
 *
 * Consent is a system concept and stays in the composable; the blocklist and the tunnel state
 * live in [SaverViewModel] and [com.maknoon.mizan.saver.SaverStateHolder]. The screen never
 * claims protection is active unless the service reports an established tunnel, and the traffic
 * card only shows what the tunnel actually dropped.
 *
 * The section draws its own header — shield, title and enable switch — because the app-wide
 * chrome is now the concept board's dark palette, so nothing else needs to be scoped here.
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
    var consentRefused by rememberSaveable { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // The system is the authority, not the result code alone: re-check after the dialog.
        val granted = VpnService.prepare(context) == null
        consentGranted = granted
        consentRefused = !granted
        if (granted && pendingEnable) {
            pendingEnable = false
            viewModel.setEnabled(true)
        } else if (!granted) {
            pendingEnable = false
        }
    }

    val launchConsent: () -> Unit = {
        consentRefused = false
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

    // A press on the switch that had to open the picker first still means "turn protection on":
    // once an app is selected and the picker closes, the request is honoured instead of dropped.
    LaunchedEffect(showingPicker, pendingEnable, uiState.blockedPackages.isEmpty()) {
        if (showingPicker || !pendingEnable || uiState.blockedPackages.isEmpty()) {
            return@LaunchedEffect
        }
        if (consentGranted) {
            pendingEnable = false
            viewModel.setEnabled(true)
        } else {
            launchConsent()
        }
    }

    LifecycleResumeEffect(Unit) {
        val granted = VpnService.prepare(context) == null
        consentGranted = granted
        if (granted && pendingEnable) {
            pendingEnable = false
            viewModel.setEnabled(true)
        }
        viewModel.refreshNetwork()
        onPauseOrDispose { }
    }

    SaverContent(
        uiState = uiState,
        consentGranted = consentGranted,
        consentRefused = consentRefused,
        onRequestConsent = launchConsent,
        onEnabledChange = { enabled ->
            // The switch is never disabled: every press has a visible consequence.
            when (guardToggleAction(enabled, uiState.blockedPackages.size, consentGranted)) {
                GuardToggleAction.STOP -> {
                    pendingEnable = false
                    viewModel.setEnabled(false)
                }

                GuardToggleAction.START -> {
                    pendingEnable = false
                    viewModel.setEnabled(true)
                }

                GuardToggleAction.CHOOSE_APPS -> {
                    pendingEnable = true
                    showingPicker = true
                }

                GuardToggleAction.REQUEST_CONSENT -> {
                    pendingEnable = true
                    launchConsent()
                }
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
            onDismiss = {
                showingPicker = false
                // Closing the picker empty-handed cancels the pending "turn on" request, so a
                // later tap on an app row cannot switch protection on behind the operator's back.
                if (uiState.blockedPackages.isEmpty()) pendingEnable = false
            },
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
    consentRefused: Boolean = false,
) {
    val blockedApps = uiState.apps.filter { it.packageName in uiState.blockedPackages }
    val allowedApps = uiState.apps.filter { it.packageName !in uiState.blockedPackages }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        GuardHeader(
            uiState = uiState,
            consentGranted = consentGranted,
            consentRefused = consentRefused,
            onEnabledChange = onEnabledChange,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ConsentCard(
                    consentGranted = consentGranted,
                    onRequestConsent = onRequestConsent,
                )
            }
            item { NetworkCard(uiState = uiState) }
            item { TrafficCard(uiState = uiState) }

            if (uiState.appsLoading) {
                item {
                    Text(
                        text = stringResource(R.string.saver_picker_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    AppsHeader(
                        blockedCount = blockedApps.size,
                        allowedCount = allowedApps.size,
                    )
                }
                items(blockedApps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        blocked = true,
                        onBlockedChange = onAppBlockedChange,
                    )
                }
                items(allowedApps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        blocked = false,
                        onBlockedChange = onAppBlockedChange,
                    )
                }
            }

            item {
                AppsFooter(
                    appsLoading = uiState.appsLoading,
                    onChooseApps = onChooseApps,
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
}

/** A dark, rounded panel with a hairline border, the concept board's `.card` treatment. */
@Composable
private fun GuardCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/**
 * The Guard header is this screen's top bar (concept board): the shield, the title and the
 * enable switch on one row, the live firewall status and the local-VPN note under them. Nothing
 * else sits above it, so the switch is the first thing the operator reaches.
 */
@Composable
private fun GuardHeader(
    uiState: SaverUiState,
    consentGranted: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    consentRefused: Boolean = false,
) {
    val blockedCount = uiState.blockedPackages.size
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
        uiState.unmetered -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val hintRes = when {
        consentRefused -> R.string.saver_consent_refused
        !consentGranted -> R.string.saver_need_consent
        blockedCount == 0 -> R.string.saver_need_apps
        else -> null
    }

    AppTopBar(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = uiState.enabled,
                    enabled = true,
                    role = Role.Switch,
                    onValueChange = onEnabledChange,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MizanPalette.Guard.copy(alpha = 0.14f))
                    .border(
                        width = 1.dp,
                        color = MizanPalette.Guard.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shield),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MizanPalette.Guard,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.saver_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.saver_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = uiState.enabled,
                onCheckedChange = null,
            )
        }
        StatusLine(text = statusText, color = statusColor)
        if (hintRes != null) {
            // The reason a press cannot start the tunnel has to be as visible as the switch:
            // a faint grey line under a live-looking switch reads as a broken button.
            val hintColor =
                if (consentRefused) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(hintColor.copy(alpha = 0.10f))
                    .border(1.dp, hintColor.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = hintColor,
                )
                Text(
                    text = stringResource(hintRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = hintColor,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.saver_local_vpn_pill),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.saver_vpn_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ConsentCard(
    consentGranted: Boolean,
    onRequestConsent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GuardCard(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(
                    if (consentGranted) R.drawable.ic_check_circle else R.drawable.ic_shield,
                ),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MizanPalette.Guard,
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

@Composable
private fun StatusLine(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

@Composable
private fun NetworkCard(
    uiState: SaverUiState,
    modifier: Modifier = Modifier,
) {
    val network = uiState.network
    GuardCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.saver_network_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (network != null && network.connected) {
                TransportPill(transport = network.transport)
            }
        }

        when {
            network == null -> Text(
                text = stringResource(R.string.saver_network_reading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            !network.connected -> {
                NetworkChip(
                    text = stringResource(R.string.saver_network_disconnected),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BehaviorLine(
                    text = stringResource(R.string.saver_behavior_disconnected),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                NetworkChip(
                    text = stringResource(
                        if (network.metered) {
                            R.string.saver_network_metered
                        } else {
                            R.string.saver_network_unmetered
                        },
                    ),
                    color = if (network.metered) {
                        MaterialTheme.statusColors.warning
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                val behavior = when {
                    !uiState.enabled -> R.string.saver_behavior_off
                    uiState.unmetered || !network.metered -> R.string.saver_behavior_unmetered
                    else -> R.string.saver_behavior_metered
                }
                BehaviorLine(
                    text = stringResource(behavior),
                    color = when {
                        !uiState.enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        uiState.unmetered || !network.metered -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.statusColors.warning
                    },
                )
                network.profile?.let { profile ->
                    Text(
                        text = stringResource(R.string.saver_profile_label, profile.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (network.roaming) {
                    Text(
                        text = stringResource(R.string.saver_network_roaming),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.statusColors.warning,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportPill(transport: NetworkTransport, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(transport.labelRes()),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun NetworkChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.10f))
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.28f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun BehaviorLine(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = modifier,
    )
}

@Composable
private fun TrafficCard(
    uiState: SaverUiState,
    modifier: Modifier = Modifier,
) {
    val snapshot = uiState.blockedTraffic
    val rows = remember(snapshot, uiState.apps) { trafficRows(snapshot, uiState.apps) }

    GuardCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.saver_traffic_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (snapshot.totalPackets == 0L) {
            Text(
                text = stringResource(R.string.saver_traffic_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox(
                    label = stringResource(R.string.saver_traffic_packets_label),
                    value = snapshot.totalPackets.toString(),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                StatBox(
                    label = stringResource(R.string.saver_traffic_bytes_label),
                    value = ByteFormatter.data(snapshot.totalBytes),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
            rows.forEach { row -> TrafficRowItem(row = row) }
            if (snapshot.unknown.packets > 0) {
                TrafficRowItem(
                    row = TrafficRow(
                        packageName = null,
                        label = stringResource(R.string.saver_traffic_unknown),
                        packets = snapshot.unknown.packets,
                        bytes = snapshot.unknown.bytes,
                    ),
                )
            }
        }
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.09f))
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.20f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall
                .copy(textDirection = TextDirection.Ltr),
            color = color,
        )
    }
}

private data class TrafficRow(
    val packageName: String?,
    val label: String,
    val packets: Long,
    val bytes: Long,
)

private fun trafficRows(
    snapshot: BlockedTrafficSnapshot,
    apps: List<SaverApp>,
): List<TrafficRow> {
    val labels = apps.associate { it.packageName to it.label }
    return snapshot.perPackage.entries
        .sortedByDescending { it.value.bytes }
        .map { (packageName, count) ->
            TrafficRow(
                packageName = packageName,
                label = labels[packageName] ?: packageName,
                packets = count.packets,
                bytes = count.bytes,
            )
        }
}

@Composable
private fun TrafficRowItem(row: TrafficRow, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val packageName = row.packageName
        if (packageName != null) {
            AppIcon(packageName = packageName)
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_apps),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.saver_traffic_row,
                    row.packets,
                    ByteFormatter.data(row.bytes),
                ),
                style = MaterialTheme.typography.bodySmall
                    .copy(textDirection = TextDirection.Ltr),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppsHeader(
    blockedCount: Int,
    allowedCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.saver_apps_section_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            Text(
                text = stringResource(
                    R.string.saver_apps_counts,
                    blockedCount,
                    allowedCount,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(
                if (blockedCount == 0) {
                    R.string.saver_apps_empty
                } else {
                    R.string.saver_apps_allowed_note
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppRow(
    app: SaverApp,
    blocked: Boolean,
    onBlockedChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp),
            )
            .toggleable(
                value = blocked,
                role = Role.Switch,
                onValueChange = { onBlockedChange(app.packageName, it) },
            )
            .heightIn(min = 58.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
        StatusTag(blocked = blocked)
        Switch(checked = blocked, onCheckedChange = null)
    }
}

@Composable
private fun StatusTag(blocked: Boolean, modifier: Modifier = Modifier) {
    val color = if (blocked) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = stringResource(
            if (blocked) R.string.saver_apps_tag_blocked else R.string.saver_apps_tag_allowed,
        ),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun AppsFooter(
    appsLoading: Boolean,
    onChooseApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onChooseApps,
            enabled = !appsLoading,
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
    GuardCard(modifier = modifier) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = if (warn) {
                MaterialTheme.statusColors.warning
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@StringRes
private fun NetworkTransport.labelRes(): Int = when (this) {
    NetworkTransport.WIFI -> R.string.saver_transport_wifi
    NetworkTransport.CELLULAR -> R.string.saver_transport_cellular
    NetworkTransport.ETHERNET -> R.string.saver_transport_ethernet
    NetworkTransport.VPN -> R.string.saver_transport_vpn
    NetworkTransport.OTHER -> R.string.saver_transport_other
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
    MizanTheme(darkTheme = true, dynamicColor = false) {
        SaverContent(
            uiState = SaverUiState(
                network = ResolvedNetwork(
                    connected = true,
                    transport = NetworkTransport.WIFI,
                    metered = true,
                    roaming = false,
                    profile = null,
                ),
            ),
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
    val blocked = apps.mapTo(LinkedHashSet()) { it.packageName }
    MizanTheme(darkTheme = true, dynamicColor = false) {
        SaverContent(
            uiState = SaverUiState(
                enabled = true,
                blockedPackages = blocked,
                apps = apps,
                appsLoading = false,
                serviceRunning = true,
                active = true,
                activeBlockedCount = blocked.size,
                network = ResolvedNetwork(
                    connected = true,
                    transport = NetworkTransport.WIFI,
                    metered = true,
                    roaming = false,
                    profile = null,
                ),
                blockedTraffic = BlockedTrafficSnapshot(
                    perPackage = mapOf(
                        "com.example.video" to com.maknoon.mizan.core.domain.saver.TrafficCount(
                            packets = 82,
                            bytes = 5_518,
                        ),
                    ),
                    unknown = com.maknoon.mizan.core.domain.saver.TrafficCount(
                        packets = 3,
                        bytes = 210,
                    ),
                ),
            ),
            consentGranted = true,
            onRequestConsent = {},
            onEnabledChange = {},
            onChooseApps = {},
            onAppBlockedChange = { _, _ -> },
        )
    }
}
