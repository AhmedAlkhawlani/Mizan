package com.maknoon.mizan.monitoring

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.maknoon.mizan.core.data.usage.InMemoryUsageStateStore
import com.maknoon.mizan.core.domain.accounting.TrafficAccountant
import com.maknoon.mizan.core.platform.traffic.AndroidTrafficSource
import com.maknoon.mizan.core.platform.traffic.TrafficSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Samples the device counters once a second and publishes the result.
 *
 * A foreground service is unavoidable here: without it the system kills the process and the
 * live figure stops. The type is `specialUse` because Android 14 has no type that describes
 * "continuous local measurement"; the manifest carries the required subtype justification.
 */
class MonitoringService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val trafficSource: TrafficSource = AndroidTrafficSource()

    /**
     * In memory for now: the counters themselves are cumulative since boot, so a process death
     * costs at most the partial-day nuance, not the total. Room takes over in phase 2.
     */
    private val accountant = TrafficAccountant(InMemoryUsageStateStore())

    private var samplingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        MonitoringNotifications.createChannel(this)
        startAsForeground()
        MonitoringStateHolder.publish(MonitoringState(running = true))
        samplingJob = scope.launch { sampleContinuously() }
    }

    /**
     * Sticky so a low-memory kill brings monitoring back; the counters are cumulative, so
     * resuming never double counts.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        samplingJob?.cancel()
        scope.cancel()
        MonitoringStateHolder.publish(MonitoringState(running = false))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startAsForeground() {
        ServiceCompat.startForeground(
            this,
            MonitoringNotifications.MONITORING_NOTIFICATION_ID,
            MonitoringNotifications.monitoringNotification(this),
            // Before Android 14 the platform has no specialUse type, and passing it would not
            // match the manifest, so the type is only declared where it exists.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
    }

    private suspend fun sampleContinuously() {
        while (currentCoroutineContext().isActive) {
            val snapshot = trafficSource.snapshot()
            MonitoringStateHolder.publish(
                MonitoringState(
                    running = true,
                    usage = snapshot?.let(accountant::record),
                ),
            )
            delay(SAMPLE_INTERVAL_MILLIS)
        }
    }

    companion object {
        const val SAMPLE_INTERVAL_MILLIS = 1_000L
    }
}
