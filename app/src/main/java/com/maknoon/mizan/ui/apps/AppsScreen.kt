package com.maknoon.mizan.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.maknoon.mizan.R
import com.maknoon.mizan.core.designsystem.theme.MizanTheme
import com.maknoon.mizan.core.designsystem.theme.statusColors
import com.maknoon.mizan.core.platform.usage.UsageAccess
import com.maknoon.mizan.sync.AppUsageSyncScheduler

/**
 * The Apps section.
 *
 * Per-app byte counters are readable only after the user switches Usage access on in Settings.
 * The screen asks before it has anything to show, and re-reads the grant every time it comes
 * back to the foreground, because the user may have changed it while in Settings.
 */
@Composable
fun AppsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(UsageAccess.isGranted(context)) }

    LifecycleResumeEffect(Unit) {
        granted = UsageAccess.isGranted(context)
        onPauseOrDispose { }
    }

    // Arriving here with the grant on — including straight back from Settings — should fill
    // the list now rather than at the next periodic run.
    LaunchedEffect(granted) {
        if (granted) AppUsageSyncScheduler.syncNow(context)
    }

    AppsContent(
        granted = granted,
        onOpenSettings = { context.startActivity(UsageAccess.settingsIntent()) },
        modifier = modifier,
    )
}

@Composable
internal fun AppsContent(
    granted: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (granted) {
            item(key = "granted") { GrantedCard() }
            item(key = "list") { PendingListCard() }
        } else {
            item(key = "access") { AccessRequestCard(onOpenSettings = onOpenSettings) }
        }
    }
}

@Composable
private fun AccessRequestCard(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_apps),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.apps_access_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.apps_access_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.apps_access_action))
            }
        }
    }
}

@Composable
private fun GrantedCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shield),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.statusColors.guard,
            )
            Text(
                text = stringResource(R.string.apps_access_granted),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun PendingListCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.nav_apps),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.apps_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.section_in_progress),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.statusColors.guard,
            )
        }
    }
}

@Preview(name = "Apps — access missing", locale = "ar", showBackground = true)
@Composable
private fun AppsAccessPreview() {
    MizanTheme(dynamicColor = false) {
        AppsContent(granted = false, onOpenSettings = {})
    }
}

@Preview(name = "Apps — access granted", locale = "ar", showBackground = true)
@Composable
private fun AppsGrantedPreview() {
    MizanTheme(dynamicColor = false) {
        AppsContent(granted = true, onOpenSettings = {})
    }
}
