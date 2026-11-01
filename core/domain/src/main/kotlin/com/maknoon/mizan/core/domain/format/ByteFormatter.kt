package com.maknoon.mizan.core.domain.format

import java.util.Locale
import kotlin.math.pow
import kotlin.math.round

/**
 * Formats byte counts and transfer rates using decimal (SI) units, so 1 KB is 1000 B.
 *
 * Decimal units are deliberate: carriers, data plans and Android's own traffic counters
 * all report decimal units, so showing binary units would understate usage by 2.4% at GB
 * scale and break the accuracy promise in docs/00.
 *
 * Digits are always Western ASCII and the decimal separator is always a dot, whatever the
 * device locale is, because docs/04 keeps numbers LTR inside the Arabic, RTL interface.
 */
object ByteFormatter {

    private val UNITS = listOf("B", "KB", "MB", "GB", "TB", "PB")

    private const val STEP = 1000.0
    private const val SMALLEST_VISIBLE = 1.0

    /** Formats an accumulating byte count, for example `2_450_000_000` as `2.45 GB`. */
    fun data(bytes: Long): String = format(bytes.coerceAtLeast(0L).toDouble())

    /** Formats a transfer rate, for example `12_400_000.0` as `12.4 MB/s`. */
    fun rate(bytesPerSecond: Double): String {
        val value = bytesPerSecond.coerceAtLeast(0.0)
        return if (value < SMALLEST_VISIBLE) "0 B/s" else "${format(value)}/s"
    }

    private fun format(bytes: Double): String {
        if (bytes < SMALLEST_VISIBLE) return "0 B"
        val (value, unit) = scale(bytes)
        val text = if (unit == 0) {
            value.toLong().toString()
        } else {
            trimTrailingZeros(String.format(Locale.US, "%.${decimals(value)}f", value))
        }
        return "$text ${UNITS[unit]}"
    }

    /**
     * Divides down to the largest unit that keeps the value below 1000.
     *
     * Rounding can carry a value up to the next unit — 999_999 B would otherwise render as
     * `1000 KB` — so the carry is resolved here, before rendering.
     */
    private fun scale(bytes: Double): Pair<Double, Int> {
        var value = bytes
        var unit = 0
        while (value >= STEP && unit < UNITS.lastIndex) {
            value /= STEP
            unit++
        }
        if (unit < UNITS.lastIndex && round(value, decimals(value)) >= STEP) {
            value /= STEP
            unit++
        }
        return value to unit
    }

    /** Two decimals below 100 keeps three significant digits; one decimal above it is enough. */
    private fun decimals(scaled: Double): Int = if (scaled >= 100) 1 else 2

    private fun round(value: Double, decimals: Int): Double {
        val factor = 10.0.pow(decimals)
        return round(value * factor) / factor
    }

    private fun trimTrailingZeros(text: String): String =
        if ('.' in text) text.trimEnd('0').trimEnd('.') else text
}
