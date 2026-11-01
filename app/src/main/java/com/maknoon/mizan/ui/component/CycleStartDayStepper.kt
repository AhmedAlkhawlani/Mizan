package com.maknoon.mizan.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maknoon.mizan.R
import com.maknoon.mizan.core.domain.cycle.BillingCycleCalculator

/**
 * −/+ stepper for the billing-cycle start day (docs/04). The History cycle card and the Budget
 * screen edit the same setting, so they share the control.
 */
@Composable
fun CycleStartDayStepper(
    day: Int,
    onDayChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val decreaseLabel = stringResource(R.string.cycle_start_decrease)
    val increaseLabel = stringResource(R.string.cycle_start_increase)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onDayChange(day - 1) },
            enabled = day > BillingCycleCalculator.MIN_START_DAY,
        ) {
            Text(
                text = "−",
                modifier = Modifier.semantics { contentDescription = decreaseLabel },
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Text(
            text = day.toString(),
            modifier = Modifier.widthIn(min = 32.dp),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        IconButton(
            onClick = { onDayChange(day + 1) },
            enabled = day < BillingCycleCalculator.MAX_START_DAY,
        ) {
            Text(
                text = "+",
                modifier = Modifier.semantics { contentDescription = increaseLabel },
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
