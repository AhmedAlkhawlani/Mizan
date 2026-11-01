package com.maknoon.mizan.ui.budget

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maknoon.mizan.R
import com.maknoon.mizan.core.designsystem.theme.MizanTheme
import com.maknoon.mizan.ui.component.BudgetLimitField
import com.maknoon.mizan.ui.component.CycleStartDayStepper

/**
 * The device budget and cycle editor (docs/04, settings): a daily cap, a monthly cap that
 * follows the billing cycle, and the cycle's start day.
 */
@Composable
fun BudgetScreen(modifier: Modifier = Modifier) {
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
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.budget_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.budget_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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

@Preview(name = "Budget — Arabic RTL", locale = "ar", showBackground = true)
@Composable
private fun BudgetPreview() {
    MizanTheme(dynamicColor = false) {
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
        )
    }
}
