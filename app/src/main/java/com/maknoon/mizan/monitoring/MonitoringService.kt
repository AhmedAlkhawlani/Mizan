package com.maknoon.mizan.monitoring

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.maknoon.mizan.MainActivity
import com.maknoon.mizan.core.data.usage.DeviceUsageRepository
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

    /** Room-backed: the day's total and the accountant's state survive both a process death
     * and a reboot, which restarts the device counters at zero. */
    private val repository by lazy { DeviceUsageRepository.create(this) }

    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    private val openIntent by lazy {
        PendingIntent.getActivity(
            this,
            OPEN_REQUEST_CODE,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private val stopIntent by lazy {
        PendingIntent.getBroadcast(
            this,
            STOP_REQUEST_CODE,
            Intent(this, MonitoringActionReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private var samplingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        MonitoringNotifications.createChannel(this, notificationManager)
        MonitoringStateHolder.publish(MonitoringState(running = true))
        startAsForeground()
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
            buildNotification(MonitoringState(running = true)),
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
            val state = MonitoringState(
                running = true,
                usage = snapshot?.let { repository.record(it) },
            )
            MonitoringStateHolder.publish(state)
            refreshNotification(state)
            delay(SAMPLE_INTERVAL_MILLIS)
        }
    }

    private fun refreshNotification(state: MonitoringState) {
        // From Android 13 a denied notification permission hides the foreground notification;
        // posting anyway would only throw, so the service keeps measuring without a banner.
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        if (allowed) {
            notificationManager.notify(
                MonitoringNotifications.MONITORING_NOTIFICATION_ID,
                buildNotification(state),
            )
        }
    }

    private fun buildNotification(state: MonitoringState) = MonitoringNotifications
        .monitoringNotification(this, state, openIntent, stopIntent)

    companion object {
        const val SAMPLE_INTERVAL_MILLIS = 1_000L
        const val ACTION_STOP = "com.maknoon.mizan.action.STOP_MONITORING"

        private const val OPEN_REQUEST_CODE = 10
        private const val STOP_REQUEST_CODE = 11
    }
}
