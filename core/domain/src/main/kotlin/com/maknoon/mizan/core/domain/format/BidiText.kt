package com.maknoon.mizan.core.domain.format

private const val LEFT_TO_RIGHT_ISOLATE = "\u2066"
private const val FIRST_STRONG_ISOLATE = "\u2068"
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

/**
 * Keeps a name or label in its own direction inside a sentence of the opposite one.
 *
 * Unlike [isolateLtr], this does not impose a direction: the first strong character decides. That
 * is what an app name needs, because it is data we did not write. "1DM+" begins with a digit, so
 * in an Arabic paragraph its trailing "+" used to jump to the front and the row read "+1DM";
 * wrapped in a first-strong isolate it reads correctly, and an Arabic name is unaffected.
 */
fun isolateFirstStrong(value: String): String =
    FIRST_STRONG_ISOLATE + value + POP_DIRECTIONAL_ISOLATE
