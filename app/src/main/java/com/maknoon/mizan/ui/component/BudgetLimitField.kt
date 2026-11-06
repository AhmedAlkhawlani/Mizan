package com.maknoon.mizan.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maknoon.mizan.R
import com.maknoon.mizan.ui.budget.BudgetUnit

/**
 * The enable switch, the limit field and its unit chips for one budget, shared by the device
 * editor and the per-app dialog so both accept the same input and show the same invalid message.
 */
@Composable
fun BudgetLimitField(
    text: String,
    enabled: Boolean,
    error: Boolean,
    unit: BudgetUnit,
    onTextChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onUnitChange: (BudgetUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = enabled,
                    role = Role.Switch,
                    onValueChange = onEnabledChange,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.budget_enabled),
                style = MaterialTheme.typography.bodyMedium,
            )
            // The row carries the toggle semantics, so the switch must not announce twice.
            Switch(
                checked = enabled,
                onCheckedChange = null,
            )
        }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            enabled = enabled,
            label = { Text(text = stringResource(R.string.budget_limit_label)) },
            placeholder = { Text(text = stringResource(R.string.budget_limit_hint)) },
            supportingText = if (error) {
                { Text(text = stringResource(R.string.budget_limit_invalid)) }
            } else {
                null
            },
            isError = error,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // The unit is chosen before the cap is switched on, so the chips stay live while the
        // number field itself waits for the enable switch.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BudgetUnit.entries.forEach { option ->
                FilterChip(
                    selected = option == unit,
                    onClick = { onUnitChange(option) },
                    label = { Text(text = stringResource(option.labelRes())) },
                )
            }
        }
    }
}

private fun BudgetUnit.labelRes(): Int = when (this) {
    BudgetUnit.MEGABYTES -> R.string.budget_unit_mb
    BudgetUnit.GIGABYTES -> R.string.budget_unit_gb
}
