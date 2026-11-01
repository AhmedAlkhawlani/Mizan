package com.maknoon.mizan.core.platform.usage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.maknoon.mizan.core.domain.usage.InstalledApp

/**
 * Lists the installed packages that can actually use the network.
 *
 * Package visibility on Android 11+ hides other apps unless `QUERY_ALL_PACKAGES` is declared
 * (docs/02 §8). Filtering on the INTERNET permission keeps the list to apps the platform can
 * attribute bytes to, so the sync does not carry entries that can never have a counter.
 */
class AndroidInstalledAppsSource(
    private val context: Context,
) {

    fun appsWithInternetAccess(): List<InstalledApp> {
        val packageManager = context.packageManager
        return packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .asSequence()
            .filter { packageInfo ->
                packageInfo.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
            }
            .mapNotNull { packageInfo ->
                val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                InstalledApp(
                    packageName = packageInfo.packageName,
                    uid = appInfo.uid,
                    label = appInfo.loadLabel(packageManager).toString()
                        .ifBlank { packageInfo.packageName },
                )
            }
            .sortedBy { it.packageName }
            .toList()
    }
}
