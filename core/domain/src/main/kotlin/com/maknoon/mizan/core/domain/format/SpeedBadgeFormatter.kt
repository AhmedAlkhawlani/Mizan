package com.maknoon.mizan.core.domain.format

import java.util.Locale

/**
 * The two lines of the status-bar speed icon: a value short enough for a 24dp slot and a
 * single-letter unit, for example `45` + `K` or `2.4` + `M`.
 */
data class SpeedBadge(val value: String, val unit: String)

/**
 * Compresses a transfer rate into the fewest glyphs that still read as a speed.
 *
 * The notification icon is drawn as a bitmap, so the value must fit roughly three characters:
 * two decimals only below 10, a whole number from 10 up, and a unit carry at 999.5 so the icon
 * never shows `1000 K`. Units are letters instead of `KB/s` because the icon is too narrow.
 */
object SpeedBadgeFormatter {

    private val UNITS = listOf("B", "K", "M", "G")

    fun of(bytesPerSecond: Double): SpeedBadge {
        var value = bytesPerSecond.coerceAtLeast(0.0)
        var unit = 0
        while (value >= 1000.0 && unit < UNITS.lastIndex) {
            value /= 1000.0
            unit++
        }
        if (unit < UNITS.lastIndex && value >= 999.5) {
            value = 1.0
            unit++
        }
        val text = when {
            unit == 0 -> value.toLong().toString()
            value >= 10.0 -> value.toLong().toString()
            else -> trimTrailingZero(String.format(Locale.US, "%.1f", value))
        }
        return SpeedBadge(text, UNITS[unit])
    }

    /**
     * The badge for the status bar, or null when the reading is too small to be worth a digit.
     *
     * Quiet mode (showBytes = false) leaves the bar still while traffic only moves bytes: a number
     * that changes every second over a few hundred bytes reads like an alarm for nothing. The gate
     * sits on the formatted unit rather than the raw rate so 999.6 B/s, which already displays as
     * `1 K`, counts as worth showing.
     */
    fun iconBadge(bytesPerSecond: Double, showBytes: Boolean): SpeedBadge? {
        val badge = of(bytesPerSecond)
        return badge.takeIf { showBytes || it.unit != UNITS.first() }
    }

    private fun trimTrailingZero(text: String): String =
        if (text.endsWith(".0")) text.dropLast(2) else text
}
