package com.maknoon.mizan.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.maknoon.mizan.MainActivity
import com.maknoon.mizan.R
import com.maknoon.mizan.core.data.widget.BudgetWidgetRepository
import com.maknoon.mizan.core.data.widget.BudgetWidgetSnapshot
import com.maknoon.mizan.core.designsystem.theme.MizanPalette
import com.maknoon.mizan.core.domain.format.ByteFormatter
import com.maknoon.mizan.monitoring.MonitoringStateHolder
import com.maknoon.mizan.ui.dashboard.DashboardUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The 2×2 home-screen widget: today's budget as a ring, with the heaviest app below it.
 *
 * The ring is drawn into a bitmap because Glance has no canvas; its colour and dash pattern
 * carry the same meaning as the in-app budget bars, while the percentage and the figures always
 * stay on screen so colour is never the only signal (docs/04).
 *
 * A widget cannot stream, so the monitor pushes a redraw every [REFRESH_INTERVAL_SECONDS]
 * seconds while it runs, the app-usage sync pushes one after each sync, and the launcher's own
 * periodic update keeps it honest when monitoring is off. A tap opens the app.
 */
class BudgetWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = withContext(Dispatchers.IO) {
            try {
                BudgetWidgetRepository.create(context).load(LocalDate.now().toEpochDay())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "budget widget snapshot failed", e)
                BudgetWidgetSnapshot(null, null, null)
            }
        }
        val liveTotal = MonitoringStateHolder.state.value.usage?.totalBytes
        val state = budgetWidgetUiState(
            usedBytes = liveTotal ?: snapshot.storedTodayBytes,
            limitBytes = snapshot.dailyLimitBytes,
            topApp = snapshot.topApp,
        )
        val icon = snapshot.topApp?.let { loadAppIcon(context, it.packageName) }
        provideContent { BudgetWidgetContent(state, icon) }
    }

    companion object {
        /** Budget data moves far slower than speed; a minute is enough for the ring. */
        const val REFRESH_INTERVAL_SECONDS = 60L

        suspend fun refresh(context: Context) {
            BudgetWidget().updateAll(context)
        }

        private const val TAG = "MizanWidget"
    }
}

@Composable
private fun BudgetWidgetContent(state: BudgetWidgetUiState, topAppIcon: Bitmap?) {
    val context = LocalContext.current
    val compact = LocalSize.current.height < COMPACT_HEIGHT
    val ringSize = if (compact) COMPACT_RING_SIZE else RING_SIZE
    val statusColor = ringColor(state.status)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>())
            .background(Color(0xFF0D131A))
            .cornerRadius(22.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = context.getString(R.string.widget_budget_title),
                style = TextStyle(
                    color = ColorProvider(Color(0xFF8A94A6)),
                    fontSize = 9.sp,
                ),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
            )
            if (state.limitBytes != null) {
                Text(
                    text = state.percent?.let {
                        context.getString(R.string.widget_budget_percent, it)
                    } ?: DashboardUiState.UNKNOWN,
                    style = TextStyle(
                        color = ColorProvider(Color(statusColor)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
        }

        Box(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = GlanceModifier.size(ringSize),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(ringBitmap(context, state, ringSize, statusColor)),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
                Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                    Text(
                        text = state.usedBytes?.let { ByteFormatter.data(it) } ?: DashboardUiState.UNKNOWN,
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFE5E7EB)),
                            fontSize = if (compact) 11.sp else 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    Text(
                        text = state.limitBytes?.let {
                            context.getString(R.string.widget_budget_of_limit, ByteFormatter.data(it))
                        } ?: context.getString(R.string.widget_budget_no_limit),
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF8A94A6)),
                            fontSize = if (compact) 7.sp else 8.sp,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }

        Text(
            text = context.getString(R.string.widget_top_app),
            style = TextStyle(color = ColorProvider(Color(0xFF8A94A6)), fontSize = 7.sp),
            maxLines = 1,
        )
        if (state.topAppLabel != null) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                if (topAppIcon != null) {
                    Image(
                        provider = ImageProvider(topAppIcon),
                        contentDescription = null,
                        modifier = GlanceModifier.size(12.dp),
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                }
                Text(
                    text = state.topAppLabel,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFE5E7EB)),
                        fontSize = 10.sp,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                    maxLines = 1,
                )
                Text(
                    text = state.topAppBytes?.let { ByteFormatter.data(it) } ?: DashboardUiState.UNKNOWN,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF8A94A6)),
                        fontSize = 9.sp,
                    ),
                    maxLines = 1,
                )
            }
        } else {
            Text(
                text = context.getString(R.string.widget_top_app_none),
                style = TextStyle(color = ColorProvider(Color(0xFF8A94A6)), fontSize = 9.sp),
                maxLines = 1,
            )
        }
    }
}

/**
 * Renders the ring at the exact pixel size it is displayed at, so the stroke stays crisp.
 *
 * A budget that does not exist yet is a dashed track, not a zero-percent ring: "no cap" and
 * "0% of a cap" are different facts. A rim glow marks the live arc the way the board's ring
 * does, and an overrun keeps the danger colour at full circle.
 */
private fun ringBitmap(
    context: Context,
    state: BudgetWidgetUiState,
    size: Dp,
    statusColor: Int,
): Bitmap {
    val sizePx = max(1, (size.value * context.resources.displayMetrics.density).roundToInt())
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = sizePx * RING_STROKE_FRACTION
    val inset = stroke / 2f
    val bounds = RectF(inset, inset, sizePx - inset, sizePx - inset)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }

    paint.color = RING_TRACK
    if (state.limitBytes == null) {
        paint.pathEffect = DashPathEffect(floatArrayOf(stroke * 1.6f, stroke * 1.1f), 0f)
    }
    canvas.drawArc(bounds, 0f, 360f, false, paint)

    if (state.ringFraction > 0f) {
        paint.pathEffect = null
        paint.strokeWidth = stroke * 2.4f
        paint.alpha = GLOW_ALPHA
        paint.color = statusColor
        canvas.drawArc(bounds, START_ANGLE, 360f * state.ringFraction, false, paint)

        paint.strokeWidth = stroke
        paint.alpha = 255
        canvas.drawArc(bounds, START_ANGLE, 360f * state.ringFraction, false, paint)
    }
    return bitmap
}

private fun ringColor(status: BudgetRingStatus): Int = when (status) {
    BudgetRingStatus.NORMAL -> MizanPalette.DataDown.toArgb()
    BudgetRingStatus.WARNING -> MizanPalette.Warning.toArgb()
    BudgetRingStatus.EXCEEDED -> MizanPalette.Danger.toArgb()
    BudgetRingStatus.NONE -> RING_MUTED
}

private fun loadAppIcon(context: Context, packageName: String): Bitmap? = runCatching {
    val size = (24 * context.resources.displayMetrics.density).roundToInt()
    context.packageManager.getApplicationIcon(packageName).toBitmap(size, size)
}.getOrNull()

class BudgetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BudgetWidget()
}

private val RING_MUTED = 0xFF64748B.toInt()
private const val RING_TRACK = 0xFF1E293B.toInt()
private const val RING_STROKE_FRACTION = 0.085f
private const val START_ANGLE = -90f
private const val GLOW_ALPHA = 48

private val RING_SIZE = 76.dp
private val COMPACT_RING_SIZE = 48.dp
private val COMPACT_HEIGHT = 140.dp
