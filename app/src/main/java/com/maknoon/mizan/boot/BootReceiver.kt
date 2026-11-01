package com.maknoon.mizan.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.maknoon.mizan.core.data.settings.MizanSettings
import com.maknoon.mizan.monitoring.MonitoringControl
import com.maknoon.mizan.sync.AppUsageSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Resumes monitoring after a reboot, but only if the user had it running (docs/02 §6).
 *
 * `goAsync` keeps the process alive while the preference is read from DataStore; without it
 * the receiver would return before the answer arrives and nothing would start.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        AppUsageSyncScheduler.schedule(appContext)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (MizanSettings(appContext).monitoringEnabled.first()) {
                    MonitoringControl.start(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
