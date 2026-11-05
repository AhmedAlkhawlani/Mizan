package com.maknoon.mizan.ui.about

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.maknoon.mizan.R
import com.maknoon.mizan.core.designsystem.theme.MizanTheme
import com.maknoon.mizan.ui.component.AppTopBar
import com.maknoon.mizan.ui.component.AppTopBarRow

private const val TELEGRAM_URL = "https://t.me/maknoonsoft"
private const val WEBSITE_URL = "https://ahmedalkhwlany.github.io/maknoonApp/"

/**
 * The About screen from the operator's brief: how the measurement and the local firewall work,
 * the three pillars the app is built on, and the Maknoon team card with direct contact routes.
 *
 * It is reached from the dashboard header and draws its own top bar, like the budget editor.
 */
@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    var showTeam by rememberSaveable { mutableStateOf(false) }

    AboutContent(
        onBack = onBack,
        onOpenTeam = { showTeam = true },
        modifier = modifier,
    )
    if (showTeam) {
        TeamDialog(onDismiss = { showTeam = false })
    }
}

@Composable
internal fun AboutContent(
    onBack: () -> Unit,
    onOpenTeam: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar {
            AppTopBarRow {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.about_back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.about_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.about_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "how") {
                LazyHeader(title = stringResource(R.string.about_how_title))
            }
            item(key = "howLead") {
                Text(
                    text = stringResource(R.string.about_how_lead),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "measure") {
                ExplanationCard(
                    title = stringResource(R.string.about_measure_title),
                    body = stringResource(R.string.about_measure_body),
                )
            }
            item(key = "firewall") {
                ExplanationCard(
                    title = stringResource(R.string.about_firewall_title),
                    body = stringResource(R.string.about_firewall_body),
                )
            }
            item(key = "pillarsHeader") {
                LazyHeader(title = stringResource(R.string.about_pillars_title))
            }
            item(key = "privacy") {
                ExplanationCard(
                    title = stringResource(R.string.about_pillar_privacy_title),
                    body = stringResource(R.string.about_pillar_privacy_body),
                )
            }
            item(key = "free") {
                ExplanationCard(
                    title = stringResource(R.string.about_pillar_free_title),
                    body = stringResource(R.string.about_pillar_free_body),
                )
            }
            item(key = "saving") {
                ExplanationCard(
                    title = stringResource(R.string.about_pillar_saving_title),
                    body = stringResource(R.string.about_pillar_saving_body),
                )
            }
            item(key = "team") {
                Button(
                    onClick = onOpenTeam,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Icon(painter = painterResource(R.drawable.ic_info), contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.about_team_open))
                }
            }
        }
    }
}

@Composable
private fun LazyHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun ExplanationCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The team's identity and the three direct routes. The rows are plain buttons so TalkBack reads
 * each contact as one item, and scroll inside the dialog so a large font scale cannot clip them.
 */
@Composable
private fun TeamDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val phone = stringResource(R.string.about_team_phone_number)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.about_team_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.about_team_identity),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.about_team_mission_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.about_team_mission),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = stringResource(R.string.about_team_contact_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                ContactButton(
                    label = stringResource(R.string.about_team_phone_label),
                    value = phone,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()))
                    },
                )
                ContactButton(
                    label = stringResource(R.string.about_team_telegram_label),
                    value = stringResource(R.string.about_team_telegram_handle),
                    onClick = { uriHandler.openUri(TELEGRAM_URL) },
                )
                ContactButton(
                    label = stringResource(R.string.about_team_website_label),
                    value = stringResource(R.string.about_team_website_handle),
                    onClick = { uriHandler.openUri(WEBSITE_URL) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.about_team_close))
            }
        },
    )
}

@Composable
private fun ContactButton(label: String, value: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(
                    textDirection = TextDirection.Ltr,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(name = "About — Arabic RTL", locale = "ar", showBackground = true)
@Composable
private fun AboutPreview() {
    MizanTheme(darkTheme = true, dynamicColor = false) {
        AboutContent(onBack = {}, onOpenTeam = {})
    }
}
