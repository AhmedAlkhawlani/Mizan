package com.maknoon.mizan.core.domain.saver

import com.maknoon.mizan.core.domain.network.ResolvedNetwork

/**
 * Whether the blocking tunnel should be up for the current connection (6.4): blocked apps are
 * kept off the network only while the connection is metered. On an unmetered network (home
 * Wi-Fi) they are allowed through, and a disconnected device has nothing to block.
 */
fun shouldBlockTraffic(resolved: ResolvedNetwork): Boolean =
    resolved.connected && resolved.metered
