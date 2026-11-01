package com.maknoon.mizan.core.platform.usage

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

/**
 * The system's Usage access switch, which per-app accounting depends on.
 *
 * The permission is declared in the manifest but the user grants it from Settings, so the app
 * must never assume it: the Apps screen re-reads this on every resume, and everything that
 * needs the counters is gated on it.
 */
object UsageAccess {

    fun isGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * The list of apps that may access usage data, not a per-app dialog. Settings has no
     * reliable deep link to this app's row across OEMs, so the list opens and the app is named
     * in the rationale beside the button.
     */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
