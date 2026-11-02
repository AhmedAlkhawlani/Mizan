package com.maknoon.mizan.core.domain.saver

import com.maknoon.mizan.core.domain.usage.InstalledApp

/** The packages a blocking-only tunnel may admit, sorted and free of duplicates. */
data class VpnTunnelPlan(val packageNames: List<String>)

/**
 * Resolves the stored blocklist against the apps the platform can still route.
 *
 * A package that was uninstalled, lost its INTERNET permission, or belongs to Mizan itself is
 * dropped: `VpnService.Builder.addAllowedApplication` rejects unknown packages, and one stale
 * entry must not abort the whole tunnel. A null result means "do not establish", because an
 * empty allowlist would route every app into the tunnel instead of none (docs/02 §7).
 */
fun planVpnTunnel(
    blockedPackages: Collection<String>,
    installedApps: Collection<InstalledApp>,
    selfPackage: String,
): VpnTunnelPlan? {
    val installed = installedApps.mapTo(HashSet()) { it.packageName }
    val packages = blockedPackages
        .asSequence()
        .filter { it.isNotBlank() && it != selfPackage && it in installed }
        .distinct()
        .sorted()
        .toList()
    return if (packages.isEmpty()) null else VpnTunnelPlan(packages)
}
