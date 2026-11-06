package com.maknoon.mizan.tile

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.maknoon.mizan.MainActivity
import com.maknoon.mizan.R
import com.maknoon.mizan.app.AppLanguage
import com.maknoon.mizan.core.domain.format.ByteFormatter
import com.maknoon.mizan.monitoring.MonitoringState
import com.maknoon.mizan.monitoring.MonitoringStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The Quick Settings tile from docs/04: the live down rate while the panel is open, or the
 * stopped label when monitoring is off.
 *
 * The tile only listens while the panel is visible, so collecting here costs nothing when the
 * user is not looking at Quick Settings.
 */
class MizanTileService : TileService() {

    private var scope: CoroutineScope? = null
    private var observation: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope
        observation = newScope.launch {
            MonitoringStateHolder.state.collect(::render)
        }
    }

    override fun onStopListening() {
        observation?.cancel()
        observation = null
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    /**
     * A tap opens the app, as specified in docs/04.
     *
     * Launching the activity directly collapses the panel by itself, so this avoids the
     * deprecated `startActivityAndCollapse(Intent)` without needing a second code path for
     * Android 14's PendingIntent overload.
     */
    override fun onClick() {
        super.onClick()
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun render(state: MonitoringState) {
        val tile = qsTile ?: return
        val usage = state.usage
        val context = AppLanguage.localized(this)

        tile.state = if (state.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification_monitor)

        val detail = when {
            !state.running -> context.getString(R.string.tile_stopped)
            usage == null -> context.getString(R.string.tile_unavailable)
            else -> context.getString(R.string.tile_speed, ByteFormatter.rate(usage.downBytesPerSecond))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = context.getString(R.string.app_name)
            tile.subtitle = detail
        } else {
            tile.label = detail
        }
        tile.updateTile()
    }
}
