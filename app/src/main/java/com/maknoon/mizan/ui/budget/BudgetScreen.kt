package com.maknoon.mizan.ui.budget

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maknoon.mizan.R
import com.maknoon.mizan.core.data.settings.SpeedBadgeStyle
import com.maknoon.mizan.core.data.settings.StatusBarSpeedMode
import com.maknoon.mizan.core.designsystem.theme.MizanTheme
import com.maknoon.mizan.core.domain.format.SpeedBadge
import com.maknoon.mizan.monitoring.SpeedIconGenerator
import com.maknoon.mizan.ui.component.AppTopBar
import com.maknoon.mizan.ui.component.AppTopBarRow
import com.maknoon.mizan.ui.component.BudgetLimitField
import com.maknoon.mizan.ui.component.CycleStartDayStepper

/**
 * The device budget and cycle editor (docs/04, settings): a daily cap, a monthly cap that
 * follows the billing cycle, and the cycle's start day.
 *
 * It is reached from the dashboard header, so it carries its own back affordance and title.
 */
@Composable
fun BudgetScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.factory(application))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BudgetContent(
        uiState = uiState,
        onDailyLimitChange = viewModel::onDailyLimitChange,
        onDailyEnabledChange = viewModel::onDailyEnabledChange,
        onMonthlyLimitChange = viewModel::onMonthlyLimitChange,
        onMonthlyEnabledChange = viewModel::onMonthlyEnabledChange,
        onCycleStartDayChange = viewModel::setCycleStartDay,
        onStatusBarSpeedModeChange = viewModel::setStatusBarSpeedMode,
        onStatusBarShowBytesChange = viewModel::setStatusBarShowBytes,
        onStatusBarStyleChange = viewModel::setStatusBarBadgeStyle,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun BudgetContent(
    uiState: BudgetUiState,
    onDailyLimitChange: (String) -> Unit,
    onDailyEnabledChange: (Boolean) -> Unit,
    onMonthlyLimitChange: (String) -> Unit,
    onMonthlyEnabledChange: (Boolean) -> Unit,
    onCycleStartDayChange: (Int) -> Unit,
    onStatusBarSpeedModeChange: (StatusBarSpeedMode) -> Unit,
    onStatusBarShowBytesChange: (Boolean) -> Unit,
    onStatusBarStyleChange: (SpeedBadgeStyle) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar {
            AppTopBarRow {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.budget_back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.budget_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.budget_subtitle),
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
            item(key = "daily") {
                BudgetCard(
                    title = stringResource(R.string.budget_daily_title),
                    field = uiState.daily,
                    onLimitChange = onDailyLimitChange,
                    onEnabledChange = onDailyEnabledChange,
                )
            }
            item(key = "monthly") {
                BudgetCard(
                    title = stringResource(R.string.budget_monthly_title),
                    field = uiState.monthly,
                    onLimitChange = onMonthlyLimitChange,
                    onEnabledChange = onMonthlyEnabledChange,
                )
            }
            item(key = "cycle") {
                CycleCard(cycleStartDay = uiState.cycleStartDay, onCycleStartDayChange = onCycleStartDayChange)
            }
            item(key = "statusBarIcon") {
                StatusBarIconCard(
                    mode = uiState.statusBarIcon,
                    showBytes = uiState.statusBarShowBytes,
                    style = uiState.statusBarStyle,
                    onModeChange = onStatusBarSpeedModeChange,
                    onShowBytesChange = onStatusBarShowBytesChange,
                    onStyleChange = onStatusBarStyleChange,
                )
            }
        }
    }
}

@Composable
private fun BudgetCard(
    title: String,
    field: BudgetFieldUi,
    onLimitChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            BudgetLimitField(
                text = field.text,
                enabled = field.enabled,
                error = field.error,
                onTextChange = onLimitChange,
                onEnabledChange = onEnabledChange,
            )
        }
    }
}

@Composable
private fun CycleCard(
    cycleStartDay: Int,
    onCycleStartDayChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.budget_cycle_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.budget_cycle_start_day),
                    style = MaterialTheme.typography.bodyMedium,
                )
                CycleStartDayStepper(day = cycleStartDay, onDayChange = onCycleStartDayChange)
            }
            Text(
                text = stringResource(R.string.budget_cycle_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusBarIconCard(
    mode: StatusBarSpeedMode,
    showBytes: Boolean,
    style: SpeedBadgeStyle,
    onModeChange: (StatusBarSpeedMode) -> Unit,
    onShowBytesChange: (Boolean) -> Unit,
    onStyleChange: (SpeedBadgeStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_status_icon_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_status_icon_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            StatusBarOption(
                mode = StatusBarSpeedMode.DOWNLOAD,
                label = stringResource(R.string.settings_status_icon_download),
                selected = mode == StatusBarSpeedMode.DOWNLOAD,
                onSelect = onModeChange,
            )
            StatusBarOption(
                mode = StatusBarSpeedMode.UPLOAD,
                label = stringResource(R.string.settings_status_icon_upload),
                selected = mode == StatusBarSpeedMode.UPLOAD,
                onSelect = onModeChange,
            )
            StatusBarOption(
                mode = StatusBarSpeedMode.OFF,
                label = stringResource(R.string.settings_status_icon_off),
                selected = mode == StatusBarSpeedMode.OFF,
                onSelect = onModeChange,
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                text = stringResource(R.string.settings_status_icon_style_title),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            SpeedBadgeStyle.entries.forEach { option ->
                BadgeStyleOption(
                    style = option,
                    label = stringResource(option.labelRes),
                    selected = option == style,
                    onSelect = onStyleChange,
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            StatusBarBytesOption(showBytes = showBytes, onShowBytesChange = onShowBytesChange)
        }
    }
}

@Composable
private fun StatusBarBytesOption(
    showBytes: Boolean,
    onShowBytesChange: (Boolean) -> Unit,
) {
    // One selectable row like the radio options above, so TalkBack reads a single switch.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = showBytes,
                role = Role.Switch,
                onClick = { onShowBytesChange(!showBytes) },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_status_icon_bytes),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.settings_status_icon_bytes_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = showBytes, onCheckedChange = null)
    }
}

@Composable
private fun StatusBarOption(
    mode: StatusBarSpeedMode,
    label: String,
    selected: Boolean,
    onSelect: (StatusBarSpeedMode) -> Unit,
) {
    // The whole row is the radio target; the button itself has no listener so TalkBack
    // announces one selectable item instead of a row and a control.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onSelect(mode) },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The reading every style preview draws. One figure for all three keeps the comparison honest:
 * only the template changes, never the number.
 */
private val PREVIEW_BADGE = SpeedBadge("345", "K")

@Composable
private fun BadgeStyleOption(
    style: SpeedBadgeStyle,
    label: String,
    selected: Boolean,
    onSelect: (SpeedBadgeStyle) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onSelect(style) },
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeedBadgePreview(style)
        Spacer(modifier = Modifier.width(12.dp))
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The badge drawn by the notification's own renderer, so the operator picks the shape they
 * actually see in the bar rather than an approximation of it.
 */
@Composable
private fun SpeedBadgePreview(style: SpeedBadgeStyle, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val native = drawContext.canvas.nativeCanvas
            val saved = native.save()
            native.scale(
                size.width / SpeedIconGenerator.CANVAS_SIZE,
                size.height / SpeedIconGenerator.CANVAS_SIZE,
            )
            SpeedIconGenerator.draw(native, PREVIEW_BADGE, style)
            native.restoreToCount(saved)
        }
    }
}

@get:StringRes
private val SpeedBadgeStyle.labelRes: Int
    get() = when (this) {
        SpeedBadgeStyle.RAISED -> R.string.settings_status_icon_style_raised
        SpeedBadgeStyle.UNDER -> R.string.settings_status_icon_style_under
        SpeedBadgeStyle.UNIFORM -> R.string.settings_status_icon_style_uniform
    }

@Preview(name = "Budget — Arabic RTL", locale = "ar", showBackground = true)
@Composable
private fun BudgetPreview() {
    MizanTheme(darkTheme = true, dynamicColor = false) {
        BudgetContent(
            uiState = BudgetUiState(
                daily = BudgetFieldUi(text = "2.5", enabled = true),
                monthly = BudgetFieldUi(text = "50", enabled = true),
                cycleStartDay = 1,
            ),
            onDailyLimitChange = {},
            onDailyEnabledChange = {},
            onMonthlyLimitChange = {},
            onMonthlyEnabledChange = {},
            onCycleStartDayChange = {},
            onStatusBarSpeedModeChange = {},
            onStatusBarShowBytesChange = {},
            onStatusBarStyleChange = {},
        )
    }
}
