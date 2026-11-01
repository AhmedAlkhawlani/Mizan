package com.maknoon.mizan.core.platform.usage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.maknoon.mizan.core.domain.usage.InstalledApp

/**
 * Lists the installed packages that can actually use the network.
 *
 * Package visibility on Android 11+ hides other apps unless `QUERY_ALL_PACKAGES` is declared
 * (docs/02 §8). Filtering on the INTERNET permission keeps the list to apps the platform can
 * attribute bytes to, so the sync does not carry entries that can never have a counter.
 *
 * Framework UIDs below `FIRST_APPLICATION_UID` are dropped as well: `com.android.shell` (the
 * adb connection) and the system uid do consume bytes, but they are not apps an operator can
 * manage, and showing them would top the list with traffic the screen cannot explain.
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
                if (appInfo.uid < Process.FIRST_APPLICATION_UID) return@mapNotNull null
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
