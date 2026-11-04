package com.maknoon.mizan.core.domain.format

private const val LEFT_TO_RIGHT_ISOLATE = "\u2066"
private const val POP_DIRECTIONAL_ISOLATE = "\u2069"

/**
 * Keeps a left-to-right value (a number with its unit) intact inside an Arabic sentence.
 *
 * The bidi algorithm resolves the neutral space between "3.96" and "GB" to the paragraph's
 * RTL direction, splitting the value into two runs that an RTL view then paints as
 * "GB 3.96". An LTR isolate (UAX#9) makes the whole value a single left-to-right run.
 */
fun isolateLtr(value: String): String =
    LEFT_TO_RIGHT_ISOLATE + value + POP_DIRECTIONAL_ISOLATE
