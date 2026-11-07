package com.maknoon.mizan.core.domain.saver

import com.maknoon.mizan.core.domain.network.ResolvedNetwork

/**
 * Whether the blocking tunnel should be up for the current connection (6.4).
 *
 * [protectEveryNetwork] is the operator's decision, and it defaults to on. A blocklist is an
 * instruction, not a hint: this used to stand down on every unmetered connection — which is
 * every home and office Wi-Fi — so a blocked app opened normally and the firewall looked broken
 * while it was only obeying a rule nobody had asked for. Turning the setting off restores the
 * older data-saving rule, where only metered connections are blocked.
 *
 * A disconnected device has nothing to block either way.
 */
fun shouldBlockTraffic(resolved: ResolvedNetwork, protectEveryNetwork: Boolean): Boolean =
    resolved.connected && (protectEveryNetwork || resolved.metered)
