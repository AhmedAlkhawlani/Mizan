package com.maknoon.mizan.app

/**
 * Where a launcher shortcut points, parsed from the URI host in `res/xml/shortcuts.xml`.
 *
 * Static shortcuts cannot carry extras, so each one uses a `mizan://` data URI that the explicit
 * MainActivity intent still delivers. Parsing is pure and unit tested; the activity only applies
 * the result.
 */
internal sealed interface ShortcutTarget {
    data class Destination(val route: String) : ShortcutTarget

    data object StopMonitoring : ShortcutTarget
}

internal fun shortcutTarget(host: String?): ShortcutTarget? = when (host) {
    "apps" -> ShortcutTarget.Destination(MizanDestination.Apps.route)
    "history" -> ShortcutTarget.Destination(MizanDestination.History.route)
    "budget" -> ShortcutTarget.Destination(BUDGET_ROUTE)
    "stop-monitoring" -> ShortcutTarget.StopMonitoring
    else -> null
}
