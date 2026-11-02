package com.maknoon.mizan.saver

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.maknoon.mizan.MainActivity
import com.maknoon.mizan.R
import com.maknoon.mizan.core.data.settings.MizanSettings
import com.maknoon.mizan.core.domain.saver.planVpnTunnel
import com.maknoon.mizan.core.platform.usage.AndroidInstalledAppsSource
import java.io.FileInputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A blocking-only VPN (docs/02 §7).
 *
 * Only the selected apps are admitted to this tunnel; every packet they send is read here and
 * dropped. No upstream socket is opened and the app holds no INTERNET permission, so the tunnel
 * cannot forward traffic even by accident. Allowing no app at all would have the opposite
 * meaning — it would route the whole device — hence the two guards before
 * [VpnService.Builder.establish].
 */
class MizanVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val settings by lazy { MizanSettings(this) }

    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    private val openIntent by lazy {
        PendingIntent.getActivity(
            this,
            OPEN_REQUEST_CODE,
            Intent(this, MainActivity::class.java).setData("mizan://saver".toUri()),
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private val stopIntent by lazy {
        PendingIntent.getBroadcast(
            this,
            STOP_REQUEST_CODE,
            Intent(this, SaverActionReceiver::class.java).setAction(SaverControl.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @Volatile
    private var tunnel: ParcelFileDescriptor? = null

    private var readJob: Job? = null
    private var establishJob: Job? = null

    /** Bumped by every reload and every teardown so a slow establishment cannot win a race. */
    @Volatile
    private var generation = 0

    override fun onCreate() {
        super.onCreate()
        SaverNotifications.createChannel(this, notificationManager)
        SaverStateHolder.publish(SaverState(serviceRunning = true))
        startForegroundNotification(
            SaverNotifications.startingNotification(this, openIntent, stopIntent),
        )
    }

    /**
     * Sticky so a low-memory kill re-establishes the firewall. An explicit stop arrives as
     * [SaverControl.ACTION_STOP] so the tunnel is torn down here rather than being left to
     * [onDestroy], which the system defers while it still binds an active VpnService.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == SaverControl.ACTION_STOP) {
            stopFromCommand()
            return START_NOT_STICKY
        }
        reload()
        return START_STICKY
    }

    override fun onRevoke() {
        // The user or another VPN took the tunnel away. Say so on the Guard screen and make sure
        // the launch check does not immediately try to take it back.
        generation++
        closeTunnel()
        SaverStateHolder.publish(SaverState(failure = SaverFailure.REVOKED))
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { settings.setDataSaverEnabled(false) }
        }
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        generation++
        establishJob?.cancel()
        closeTunnel()
        scope.cancel()
        SaverStateHolder.markServiceDestroyed()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /**
     * Closing the descriptor ends the VPN; only then does the framework release its bind and
     * let the service be destroyed. Stopping the service alone would leave the tunnel running.
     */
    private fun stopFromCommand() {
        generation++
        establishJob?.cancel()
        closeTunnel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun reload() {
        val currentGeneration = ++generation
        establishJob?.cancel()
        establishJob = scope.launch {
            try {
                closeTunnel()
                establishTunnel(currentGeneration)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed reload must not leave a half-built tunnel behind.
                Log.w(TAG, "vpn reload failed", e)
                fail(currentGeneration, SaverFailure.ESTABLISH_FAILED)
            }
        }
    }

    private suspend fun establishTunnel(currentGeneration: Int) {
        val blockedPackages = settings.blockedPackages.first()
        if (currentGeneration != generation) return
        if (blockedPackages.isEmpty()) {
            fail(currentGeneration, SaverFailure.NO_APPS)
            return
        }
        if (VpnService.prepare(this) != null) {
            fail(currentGeneration, SaverFailure.CONSENT_MISSING)
            return
        }

        val installed = AndroidInstalledAppsSource(this).appsWithInternetAccess()
        val plan = planVpnTunnel(blockedPackages, installed, packageName)
        if (currentGeneration != generation) return
        if (plan == null) {
            fail(currentGeneration, SaverFailure.NO_APPS)
            return
        }

        val builder = Builder()
            .setSession(getString(R.string.saver_vpn_session))
            .setMtu(VPN_MTU)
            .addAddress(VPN_IPV4_ADDRESS, 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(VPN_IPV4_DNS)
            .addAddress(VPN_IPV6_ADDRESS, 128)
            .addRoute("::", 0)
            .addDnsServer(VPN_IPV6_DNS)
            .setBlocking(true)

        var admitted = 0
        for (app in plan.packageNames) {
            try {
                builder.addAllowedApplication(app)
                admitted++
            } catch (e: PackageManager.NameNotFoundException) {
                // Uninstalled between the package scan and this call; the rest still apply.
                Log.w(TAG, "skipping uninstalled $app", e)
            }
        }
        // Establishing with an empty allowlist would admit every app instead of none.
        if (admitted == 0) {
            fail(currentGeneration, SaverFailure.NO_APPS)
            return
        }

        val established = try {
            builder.establish()
        } catch (e: Exception) {
            Log.w(TAG, "establish failed", e)
            null
        }
        if (established == null) {
            fail(currentGeneration, SaverFailure.ESTABLISH_FAILED)
            return
        }
        if (currentGeneration != generation) {
            runCatching { established.close() }
            return
        }

        tunnel = established
        // An always-on start can reach this service without a UI write; keep the switch honest.
        settings.setDataSaverEnabled(true)
        SaverStateHolder.publish(
            SaverState(serviceRunning = true, active = true, blockedCount = admitted),
        )
        refreshNotification(admitted)
        startReading(established)
    }

    private suspend fun fail(currentGeneration: Int, failure: SaverFailure) {
        if (currentGeneration != generation) return
        closeTunnel()
        SaverStateHolder.publish(SaverState(failure = failure))
        runCatching { settings.setDataSaverEnabled(false) }
        stopSelf()
    }

    /**
     * Drops every packet the admitted apps send. Reading (rather than merely leaving the TUN
     * unread) keeps the interface flowing and gives phase 6.3 the byte counts it needs.
     */
    private fun startReading(established: ParcelFileDescriptor) {
        readJob?.cancel()
        readJob = scope.launch {
            val packets = ByteArray(MAX_PACKET_SIZE)
            val input = FileInputStream(established.fileDescriptor)
            try {
                while (isActive) {
                    if (input.read(packets) < 0) break
                }
            } catch (e: IOException) {
                // Closing the descriptor is how a stop or reload unblocks this read.
            }
        }
    }

    private fun closeTunnel() {
        readJob?.cancel()
        readJob = null
        val current = tunnel
        tunnel = null
        runCatching { current?.close() }
    }

    private fun startForegroundNotification(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            SaverNotifications.NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
    }

    private fun refreshNotification(blockedCount: Int) {
        if (!canPostNotifications()) return
        notificationManager.notify(
            SaverNotifications.NOTIFICATION_ID,
            SaverNotifications.activeNotification(this, blockedCount, openIntent, stopIntent),
        )
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val VPN_MTU = 1_500
        const val VPN_IPV4_ADDRESS = "10.111.222.1"
        const val VPN_IPV4_DNS = "10.111.222.2"
        const val VPN_IPV6_ADDRESS = "fd00:6d69:7a61:6e::1"
        const val VPN_IPV6_DNS = "fd00:6d69:7a61:6e::2"
        const val MAX_PACKET_SIZE = 65_535
        const val OPEN_REQUEST_CODE = 20
        const val STOP_REQUEST_CODE = 21
        const val TAG = "MizanVpn"
    }
}
