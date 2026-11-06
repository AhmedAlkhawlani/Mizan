package com.maknoon.mizan.ui.budget

import com.maknoon.mizan.core.data.db.BudgetEntity
import com.maknoon.mizan.core.data.settings.SpeedBadgeStyle
import com.maknoon.mizan.core.data.settings.StatusBarSpeedMode
import com.maknoon.mizan.core.domain.budget.BudgetPeriod
import com.maknoon.mizan.core.domain.budget.BudgetScope
import com.maknoon.mizan.core.domain.cycle.BillingCycleCalculator
import java.util.Locale

/** One budget the operator edits on the screen: the daily or the monthly device cap. */
data class BudgetFieldUi(
    val text: String = "",
    val enabled: Boolean = false,
    val error: Boolean = false,
    val unit: BudgetUnit = BudgetUnit.GIGABYTES,
)

/**
 * The unit one limit is edited in. Bytes stay the stored truth; the unit only decides how the
 * number is typed and shown, so a 90 MB plan is entered as "90" and not as "0.09".
 */
enum class BudgetUnit(val bytesPerUnit: Double) {
    MEGABYTES(1_000_000.0),
    GIGABYTES(1_000_000_000.0),
}

/** Everything the Budget screen draws: the two device caps, the cycle day and the icon style. */
data class BudgetUiState(
    val daily: BudgetFieldUi = BudgetFieldUi(),
    val monthly: BudgetFieldUi = BudgetFieldUi(),
    val cycleStartDay: Int = BillingCycleCalculator.MIN_START_DAY,
    val statusBarIcon: StatusBarSpeedMode = StatusBarSpeedMode.DOWNLOAD,
    val statusBarShowBytes: Boolean = false,
    val statusBarStyle: SpeedBadgeStyle = SpeedBadgeStyle.RAISED,
    val statusBarBoldGlyphs: Boolean = true,
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
    statusBarIcon: StatusBarSpeedMode = StatusBarSpeedMode.DOWNLOAD,
    dailyUnit: BudgetUnit? = null,
    monthlyUnit: BudgetUnit? = null,
): BudgetUiState {
    val deviceRows = budgets.filter { it.scope == BudgetScope.DEVICE }
    return BudgetUiState(
        daily = fieldUi(deviceRows.firstOrNull { it.period == BudgetPeriod.DAILY }, dailyText, dailyUnit),
        monthly = fieldUi(
            deviceRows.firstOrNull { it.period == BudgetPeriod.MONTHLY },
            monthlyText,
            monthlyUnit,
        ),
        cycleStartDay = BillingCycleCalculator.normalizedStartDay(cycleStartDay),
        statusBarIcon = statusBarIcon,
    )
}

private fun fieldUi(row: BudgetEntity?, userText: String?, userUnit: BudgetUnit?): BudgetFieldUi {
    val unit = userUnit ?: unitForBytes(row?.limitBytes ?: 0L)
    val bytes = row?.limitBytes ?: 0L
    return BudgetFieldUi(
        text = userText ?: formatLimit(bytes, unit),
        enabled = row?.enabled ?: false,
        error = userText != null && userText.isNotEmpty() && parseLimitBytes(userText, unit) == null,
        unit = unit,
    )
}

/** Bytes in one gigabyte, kept as a Double for the parse/format round trip. */
internal const val BYTES_PER_GIGABYTE = 1_000_000_000.0

/** The largest limit the editor accepts: a million gigabytes, beyond any real plan. */
internal const val MAX_LIMIT_BYTES = 1_000_000_000_000_000L

/** A stored cap below one gigabyte reads naturally in megabytes, so the editor opens in MB. */
internal fun unitForBytes(bytes: Long): BudgetUnit =
    if (bytes in 1L until BYTES_PER_GIGABYTE.toLong()) BudgetUnit.MEGABYTES else BudgetUnit.GIGABYTES

/**
 * Turns the operator's text into bytes in the chosen unit, or null when it is not a positive
 * number.
 *
 * Typing on an Arabic keyboard must work like a Latin one: Arabic-Indic and Persian digits are
 * normalised to Western digits, the Arabic decimal separator becomes a dot, and commas (and the
 * Arabic grouping separator) are accepted only as thousands separators — "1,5" is rejected
 * rather than silently read as 15 of the unit. A trailing decimal point ("2.") parses as the
 * whole number, so it does not flash an error mid-typing.
 */
internal fun parseLimitBytes(text: String, unit: BudgetUnit): Long? {
    val normalized = text
        .map(::normalizeChar)
        .filterNot { it.isWhitespace() }
        .joinToString("")
    val parts = normalized.split('.')
    if (parts.size > 2) return null
    val integer = parseGroupedInteger(parts[0]) ?: return null
    val decimal = parts.getOrNull(1)
    if (decimal != null && decimal.any { !it.isDigit() }) return null
    val value = if (decimal.isNullOrEmpty()) {
        integer.toDouble()
    } else {
        "$integer.$decimal".toDouble()
    }
    if (!value.isFinite() || value <= 0.0) return null
    val bytes = value * unit.bytesPerUnit
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

/** Renders a stored limit in the chosen unit; an unset limit is an empty field, not "0". */
internal fun formatLimit(bytes: Long, unit: BudgetUnit): String {
    if (bytes <= 0L) return ""
    val value = bytes.toDouble() / unit.bytesPerUnit
    return trimTrailingZeros(String.format(Locale.US, "%.3f", value))
}

/**
 * Rewrites an in-flight number into another unit while keeping the same bytes, so switching
 * 2 GB to MB shows 2000 instead of re-reading the digits as a different cap. Text that does not
 * parse yet is left alone: the operator is mid-keystroke, not wrong.
 */
internal fun convertLimitText(text: String, from: BudgetUnit, to: BudgetUnit): String {
    if (from == to) return text
    val bytes = parseLimitBytes(text, from) ?: return text
    return formatLimit(bytes, to)
}

private fun trimTrailingZeros(text: String): String =
    if ('.' in text) text.trimEnd('0').trimEnd('.') else text
