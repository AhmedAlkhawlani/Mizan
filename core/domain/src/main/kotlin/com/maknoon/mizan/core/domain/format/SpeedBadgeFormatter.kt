package com.maknoon.mizan.core.domain.format

/**
 * The two glyphs of the status-bar speed icon: a whole number and a single-letter unit,
 * for example `45` + `K` or `345` + `B`.
 *
 * [fullUnit] spells the same unit out (`KB/s`, `B/s`) for the two-line template, where the unit
 * gets a line of its own and the single letter reads as noise.
 */
data class SpeedBadge(val value: String, val unit: String) {
    val fullUnit: String get() = if (unit == "B") "B/s" else "${unit}B/s"
}

/**
 * Compresses a transfer rate into the fewest glyphs that still read as a speed.
 *
 * The badge is a few pixels tall in the status bar, so it carries a whole number and one unit
 * letter: no decimal point and no grouping separator, because at that size a dot reads as a
 * smudge rather than a figure. A unit carry at 999.5 keeps `1000 K` off the bar.
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
        return SpeedBadge(value.toLong().toString(), UNITS[unit])
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
}
