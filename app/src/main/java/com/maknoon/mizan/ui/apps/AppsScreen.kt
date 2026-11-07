package com.maknoon.mizan.ui.apps

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maknoon.mizan.R
import com.maknoon.mizan.core.designsystem.theme.MizanTheme
import com.maknoon.mizan.core.designsystem.theme.statusColors
import com.maknoon.mizan.core.domain.format.isolateFirstStrong
import com.maknoon.mizan.core.domain.format.ByteFormatter
import com.maknoon.mizan.core.platform.usage.UsageAccess
import com.maknoon.mizan.sync.AppUsageSyncScheduler
import com.maknoon.mizan.ui.component.AppTopBar
import com.maknoon.mizan.ui.component.AppTopBarRow
import com.maknoon.mizan.ui.budget.BudgetUnit
import com.maknoon.mizan.ui.component.BudgetLimitField
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Apps section (docs/04).
 *
 * Per-app byte counters are readable only after the user switches Usage access on in Settings.
 * The screen asks before it has anything to show, and re-reads the grant every time it comes
 * back to the foreground, because the user may have changed it while in Settings.
 *
 * The figures come from `NetworkStatsManager`, which is not a live counter: the header states
 * when they were last synchronised instead of implying "now".
 */
@Composable
fun AppsScreen(
    modifier: Modifier = Modifier,
    onAppClick: (String) -> Unit,
    viewModel: AppsViewModel = viewModel(),
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(UsageAccess.isGranted(context)) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()

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
        uiState = uiState,
        granted = granted,
        onPeriodChange = viewModel::setPeriod,
        onOpenSettings = { context.startActivity(UsageAccess.settingsIntent()) },
        onAppClick = onAppClick,
        onBudgetClick = { row -> viewModel.openBudgetEditor(row.packageName, row.label) },
        modifier = modifier,
    )

    editorState?.let { editor ->
        AppBudgetDialog(
            uiState = editor,
            onDailyLimitChange = { viewModel.onEditorDailyLimitChange(it, editor.daily.unit) },
            onDailyEnabledChange = viewModel::onEditorDailyEnabledChange,
            onDailyUnitChange = viewModel::onEditorDailyUnitChange,
            onMonthlyLimitChange = { viewModel.onEditorMonthlyLimitChange(it, editor.monthly.unit) },
            onMonthlyEnabledChange = viewModel::onEditorMonthlyEnabledChange,
            onMonthlyUnitChange = viewModel::onEditorMonthlyUnitChange,
            onDismiss = viewModel::closeBudgetEditor,
        )
    }
}

/**
 * The period selector and the recorded total live in the screen's own header (concept board), so
 * the list below starts with the apps themselves.
 */
@Composable
internal fun AppsContent(
    uiState: AppsUiState,
    granted: Boolean,
    onPeriodChange: (AppsPeriod) -> Unit,
    onOpenSettings: () -> Unit,
    onAppClick: (String) -> Unit,
    onBudgetClick: (AppUsageRowUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar {
            if (granted) {
                Text(
                    text = stringResource(R.string.apps_total_label),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = ByteFormatter.data(uiState.totalBytes),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        textDirection = TextDirection.Ltr,
                    ),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                PeriodChips(selected = uiState.period, onSelect = onPeriodChange)
            } else {
                Text(
                    text = stringResource(R.string.nav_apps),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!granted) {
                item(key = "access") { AccessRequestCard(onOpenSettings = onOpenSettings) }
                return@LazyColumn
            }

            item(key = "summary") { SyncNoteCard(uiState) }
            if (uiState.rows.isEmpty()) {
                item(key = "empty") { EmptyCard() }
            } else {
                items(items = uiState.rows, key = { it.packageName }) { row ->
                    AppUsageRow(
                        row = row,
                        onClick = { onAppClick(row.packageName) },
                        onBudgetClick = { onBudgetClick(row) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodChips(
    selected: AppsPeriod,
    onSelect: (AppsPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppsPeriod.entries.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelect(period) },
                label = { Text(text = stringResource(period.labelRes())) },
            )
        }
    }
}

private fun AppsPeriod.labelRes(): Int = when (this) {
    AppsPeriod.TODAY -> R.string.apps_period_today
    AppsPeriod.WEEK -> R.string.apps_period_week
    AppsPeriod.CYCLE -> R.string.apps_period_cycle
}

/** NetworkStatsManager is not a live counter, so the list still states how old its figures are. */
@Composable
private fun SyncNoteCard(uiState: AppsUiState, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            uiState.lastSyncedAtMillis?.let { syncedAt ->
                Text(
                    text = stringResource(R.string.apps_last_sync, formatTime(syncedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.apps_sync_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppUsageRow(
    row: AppUsageRowUi,
    onClick: () -> Unit,
    onBudgetClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(packageName = row.packageName)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = isolateFirstStrong(row.label),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = shareLabel(row.share),
                        style = MaterialTheme.typography.labelMedium.copy(
                            textDirection = TextDirection.Ltr,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = ByteFormatter.data(row.bytes),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDirection = TextDirection.Ltr,
                    ),
                    color = MaterialTheme.statusColors.dataDown,
                )
                if (row.dailyLimitBytes != null || row.monthlyLimitBytes != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.dailyLimitBytes?.let {
                            BudgetHint(label = stringResource(R.string.apps_budget_daily), bytes = it)
                        }
                        row.monthlyLimitBytes?.let {
                            BudgetHint(label = stringResource(R.string.apps_budget_monthly), bytes = it)
                        }
                    }
                }
                LinearProgressIndicator(
                    progress = { row.share },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.statusColors.dataDown,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            AppRowMenu(onBudgetClick = onBudgetClick)
        }
    }
}

@Composable
private fun BudgetHint(label: String, bytes: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = ByteFormatter.data(bytes),
            style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AppRowMenu(onBudgetClick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.apps_row_menu),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.app_budget_action)) },
                onClick = {
                    expanded = false
                    onBudgetClick()
                },
            )
        }
    }
}

/**
 * The per-app budget dialog (docs/04: ⋯ → set a budget).
 *
 * It edits both caps at once, like the device editor, and persists each valid keystroke through
 * the view model, so dismissing the dialog never discards an entry the operator already typed.
 */
@Composable
internal fun AppBudgetDialog(
    uiState: AppBudgetUiState,
    onDailyLimitChange: (String) -> Unit,
    onDailyEnabledChange: (Boolean) -> Unit,
    onDailyUnitChange: (BudgetUnit) -> Unit,
    onMonthlyLimitChange: (String) -> Unit,
    onMonthlyEnabledChange: (Boolean) -> Unit,
    onMonthlyUnitChange: (BudgetUnit) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = stringResource(R.string.app_budget_title, uiState.label))
                Text(
                    text = uiState.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.app_budget_daily_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                BudgetLimitField(
                    text = uiState.daily.text,
                    enabled = uiState.daily.enabled,
                    error = uiState.daily.error,
                    unit = uiState.daily.unit,
                    onTextChange = onDailyLimitChange,
                    onEnabledChange = onDailyEnabledChange,
                    onUnitChange = onDailyUnitChange,
                )
                Text(
                    text = stringResource(R.string.app_budget_monthly_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                BudgetLimitField(
                    text = uiState.monthly.text,
                    enabled = uiState.monthly.enabled,
                    error = uiState.monthly.error,
                    unit = uiState.monthly.unit,
                    onTextChange = onMonthlyLimitChange,
                    onEnabledChange = onMonthlyEnabledChange,
                    onUnitChange = onMonthlyUnitChange,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.app_budget_done))
            }
        },
    )
}

/**
 * Loads the launcher icon off the main thread and falls back to the section icon, so a package
 * that vanished between the sync and the render cannot crash the list.
 */
@Composable
internal fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
            }.getOrNull()
        }
    }
    val loaded = icon
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = null,
            modifier = modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
    } else {
        Icon(
            painter = painterResource(R.drawable.ic_apps),
            contentDescription = null,
            modifier = modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun EmptyCard(modifier: Modifier = Modifier) {
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
                text = stringResource(R.string.apps_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
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

private fun shareLabel(share: Float): String =
    String.format(Locale.US, "%d%%", (share * 100f).roundToInt())

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

private fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)

@Preview(name = "Apps — access missing", locale = "ar", showBackground = true)
@Composable
private fun AppsAccessPreview() {
    MizanTheme(darkTheme = true, dynamicColor = false) {
        AppsContent(
            uiState = AppsUiState(),
            granted = false,
            onPeriodChange = {},
            onOpenSettings = {},
            onAppClick = {},
            onBudgetClick = {},
        )
    }
}

@Preview(name = "Apps — list", locale = "ar", showBackground = true)
@Composable
private fun AppsListPreview() {
    MizanTheme(darkTheme = true, dynamicColor = false) {
        AppsContent(
            uiState = AppsUiState(
                period = AppsPeriod.WEEK,
                totalBytes = 30_000_000,
                lastSyncedAtMillis = 1_762_000_000_000,
                rows = listOf(
                    AppUsageRowUi(
                        packageName = "com.google.android.youtube",
                        label = "YouTube",
                        rxBytes = 18_000_000,
                        txBytes = 2_000_000,
                        bytes = 20_000_000,
                        share = 0.66f,
                        monthlyLimitBytes = 5_000_000_000,
                    ),
                    AppUsageRowUi(
                        packageName = "com.whatsapp.w4b",
                        label = "واتساب للأعمال",
                        rxBytes = 7_000_000,
                        txBytes = 3_000_000,
                        bytes = 10_000_000,
                        share = 0.34f,
                        dailyLimitBytes = 2_000_000_000,
                    ),
                ),
            ),
            granted = true,
            onPeriodChange = {},
            onOpenSettings = {},
            onAppClick = {},
            onBudgetClick = {},
        )
    }
}

@Preview(name = "App budget — Arabic RTL", locale = "ar", showBackground = true)
@Composable
private fun AppBudgetPreview() {
    MizanTheme(darkTheme = true, dynamicColor = false) {
        AppBudgetDialog(
            uiState = AppBudgetUiState(
                packageName = "com.google.android.youtube",
                label = "YouTube",
                daily = AppBudgetFieldUi(text = "2", enabled = true),
                monthly = AppBudgetFieldUi(text = "30", enabled = true),
            ),
            onDailyLimitChange = {},
            onDailyEnabledChange = {},
            onDailyUnitChange = {},
            onMonthlyLimitChange = {},
            onMonthlyEnabledChange = {},
            onMonthlyUnitChange = {},
            onDismiss = {},
        )
    }
}
