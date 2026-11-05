package com.maknoon.mizan.core.data.settings

/**
 * How the generated speed badge lays its two glyphs out inside the status-bar slot.
 *
 * The slot is roughly 24dp, so the templates trade digit height against unit prominence: a
 * raised unit leaves the number the full height, a unit underneath splits it, and a uniform
 * unit keeps both readable at the cost of a smaller number.
 */
enum class SpeedBadgeStyle {
    /** `345ᴋ` — the number fills the slot and the unit rides at its cap line. */
    RAISED,

    /** `345` over `K` — a two-line gauge. */
    UNDER,

    /** `345K` — one baseline, both glyphs the same height. */
    UNIFORM,
}
