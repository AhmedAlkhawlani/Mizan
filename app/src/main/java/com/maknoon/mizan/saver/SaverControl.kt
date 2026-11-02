package com.maknoon.mizan.saver

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.maknoon.mizan.core.data.settings.MizanSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Starts and stops the blocking-only tunnel, and keeps the persisted switch consistent. */
object SaverControl {

    const val ACTION_STOP = "com.maknoon.mizan.action.STOP_SAVER"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context) {
        val appContext = context.applicationContext
        // The service promotes itself in onCreate, well inside the platform's five seconds.
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, MizanVpnService::class.java),
        )
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        // A VpnService stays bound by the system while its tunnel is up, so stopService() alone
        // would not reach onDestroy; the running service has to close the tunnel itself.
        if (SaverStateHolder.state.value.serviceRunning) {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, MizanVpnService::class.java).setAction(ACTION_STOP),
            )
        }
        SaverStateHolder.markStopped()
        // Written here rather than in the service lifecycle: a service killed by the system must
        // not look like a user stop, or the next launch would not resume the firewall.
        scope.launch { MizanSettings(appContext).setDataSaverEnabled(false) }
    }
}
