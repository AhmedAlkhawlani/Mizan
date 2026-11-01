package com.maknoon.mizan.ui.budget

import com.maknoon.mizan.core.data.db.BudgetEntity
import com.maknoon.mizan.core.domain.budget.BudgetPeriod
import com.maknoon.mizan.core.domain.budget.BudgetScope
import com.maknoon.mizan.core.domain.cycle.BillingCycleCalculator
import java.util.Locale

/** One budget the operator edits on the screen: the daily or the monthly device cap. */
data class BudgetFieldUi(
    val text: String = "",
    val enabled: Boolean = false,
    val error: Boolean = false,
)

/** Everything the Budget screen draws: the two device caps and the billing-cycle start day. */
data class BudgetUiState(
    val daily: BudgetFieldUi = BudgetFieldUi(),
    val monthly: BudgetFieldUi = BudgetFieldUi(),
    val cycleStartDay: Int = BillingCycleCalculator.MIN_START_DAY,
)

/**
 * Builds the screen state from the stored device budgets.
 *
 * Kept pure and clock-free so the parsing and the row-to-field mapping are unit tested; the
 * view model supplies the operator's in-flight text, which wins over Room until the edit is
 * persisted. An emptied field is a pause in editing, not an error, so clearing a field to
 * retype does not flash the invalid message.
 */
internal fun budgetUiState(
    budgets: List<BudgetEntity>,
    cycleStartDay: Int,
    dailyText: String? = null,
    monthlyText: String? = null,
): BudgetUiState {
    val deviceRows = budgets.filter { it.scope == BudgetScope.DEVICE }
    return BudgetUiState(
        daily = fieldUi(deviceRows.firstOrNull { it.period == BudgetPeriod.DAILY }, dailyText),
        monthly = fieldUi(deviceRows.firstOrNull { it.period == BudgetPeriod.MONTHLY }, monthlyText),
        cycleStartDay = BillingCycleCalculator.normalizedStartDay(cycleStartDay),
    )
}

private fun fieldUi(row: BudgetEntity?, userText: String?): BudgetFieldUi = BudgetFieldUi(
    text = userText ?: formatLimitGb(row?.limitBytes ?: 0L),
    enabled = row?.enabled ?: false,
    error = userText != null && userText.isNotEmpty() && parseLimitBytes(userText) == null,
)

/** Bytes in one gigabyte, kept as a Double for the parse/format round trip. */
internal const val BYTES_PER_GIGABYTE = 1_000_000_000.0

/** The largest limit the editor accepts: a million gigabytes, beyond any real plan. */
internal const val MAX_LIMIT_BYTES = 1_000_000_000_000_000L

/**
 * Turns the operator's GB text into bytes, or null when it is not a positive number.
 *
 * Typing on an Arabic keyboard must work like a Latin one: Arabic-Indic and Persian digits are
 * normalised to Western digits, the Arabic decimal separator becomes a dot, and commas (and the
 * Arabic grouping separator) are accepted only as thousands separators — "1,5" is rejected
 * rather than silently read as 15 GB. A trailing decimal point ("2.") parses as the whole
 * number, so it does not flash an error mid-typing.
 */
internal fun parseLimitBytes(text: String): Long? {
    val normalized = text
        .map(::normalizeChar)
        .filterNot { it.isWhitespace() }
        .joinToString("")
    val parts = normalized.split('.')
    if (parts.size > 2) return null
    val integer = parseGroupedInteger(parts[0]) ?: return null
    val decimal = parts.getOrNull(1)
    if (decimal != null && decimal.any { !it.isDigit() }) return null
    val gb = if (decimal.isNullOrEmpty()) {
        integer.toDouble()
    } else {
        "$integer.$decimal".toDouble()
    }
    if (!gb.isFinite() || gb <= 0.0) return null
    val bytes = gb * BYTES_PER_GIGABYTE
    if (bytes >= MAX_LIMIT_BYTES) return null
    return bytes.toLong()
}

private fun normalizeChar(char: Char): Char = when {
    char == '٫' -> '.'
    char == '٬' -> ','
    char in '٠'..'٩' -> '0' + (char - '٠')
    char in '۰'..'۹' -> '0' + (char - '۰')
    else -> char
}

private fun parseGroupedInteger(text: String): Long? {
    if (text.isEmpty()) return null
    if (',' !in text) return text.toLongOrNull()
    val groups = text.split(',')
    if (groups.first().isEmpty() || groups.first().length > 3) return null
    if (groups.drop(1).any { it.length != 3 }) return null
    return groups.joinToString("").toLongOrNull()
}

/** Renders a stored limit in gigabytes; an unset limit is an empty field, not "0". */
internal fun formatLimitGb(bytes: Long): String {
    if (bytes <= 0L) return ""
    val gb = bytes.toDouble() / BYTES_PER_GIGABYTE
    return trimTrailingZeros(String.format(Locale.US, "%.3f", gb))
}

private fun trimTrailingZeros(text: String): String =
    if ('.' in text) text.trimEnd('0').trimEnd('.') else text
