package com.maknoon.mizan.ui.saver

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.maknoon.mizan.R
import com.maknoon.mizan.core.designsystem.theme.MizanTheme
import com.maknoon.mizan.core.designsystem.theme.statusColors

/**
 * The Guard section (docs/04).
 *
 * Phase 6.1 only establishes the system VPN consent and explains exactly what the firewall
 * will and will not do. No tunnel exists yet, so the screen never implies that protection is
 * already running.
 */
@Composable
fun SaverScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var consentGranted by remember { mutableStateOf(VpnService.prepare(context) == null) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // The system is the authority, not the result code alone: re-check after the dialog.
        consentGranted = VpnService.prepare(context) == null
    }

    LifecycleResumeEffect(Unit) {
        consentGranted = VpnService.prepare(context) == null
        onPauseOrDispose { }
    }

    SaverContent(
        consentGranted = consentGranted,
        onRequestConsent = {
            val consentIntent = VpnService.prepare(context)
            if (consentIntent == null) {
                consentGranted = true
            } else {
                consentLauncher.launch(consentIntent)
            }
        },
        modifier = modifier,
    )
}

@Composable
internal fun SaverContent(
    consentGranted: Boolean,
    onRequestConsent: () -> Unit,
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

@Preview(name = "Guard — consent needed", locale = "ar", showBackground = true)
@Composable
private fun SaverConsentPreview() {
    MizanTheme(dynamicColor = false) {
        SaverContent(consentGranted = false, onRequestConsent = {})
    }
}

@Preview(name = "Guard — consent granted", locale = "ar", showBackground = true)
@Composable
private fun SaverGrantedPreview() {
    MizanTheme(dynamicColor = false) {
        SaverContent(consentGranted = true, onRequestConsent = {})
    }
}
