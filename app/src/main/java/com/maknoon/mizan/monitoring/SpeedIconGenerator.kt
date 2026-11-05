package com.maknoon.mizan.monitoring

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.LruCache
import androidx.core.graphics.drawable.IconCompat
import com.maknoon.mizan.core.domain.format.SpeedBadge
import com.maknoon.mizan.core.domain.format.SpeedBadgeFormatter

/**
 * Draws the live rate into the notification's status-bar icon: the value on one line and the
 * unit underneath, white on transparent so the system can tint it like any other status icon.
 * Returns null when the reading is too small to show, which leaves the caller on its static icon.
 *
 * A bitmap per distinct reading is cached, because the notification refreshes every second and
 * allocating a new bitmap for an unchanged value (idle traffic) would be pure garbage.
 */
object SpeedIconGenerator {

    private const val SIZE = 96
    private const val MAX_TEXT_WIDTH = 88f

    private val cache = LruCache<String, IconCompat>(32)

    private val typeface: Typeface =
        Typeface.create("sans-serif-condensed", Typeface.BOLD)

    fun icon(bytesPerSecond: Double, showBytes: Boolean): IconCompat? {
        val badge = SpeedBadgeFormatter.iconBadge(bytesPerSecond, showBytes) ?: return null
        val key = badge.value + badge.unit
        cache.get(key)?.let { return it }
        val created = IconCompat.createWithBitmap(render(badge))
        cache.put(key, created)
        return created
    }

    private fun render(badge: SpeedBadge): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.WHITE
            typeface = typeface
            textAlign = Paint.Align.CENTER
        }

        // Three digits or fewer read best large; anything longer shrinks just enough to fit.
        paint.textSize = when (badge.value.length) {
            1 -> 52f
            2 -> 46f
            else -> 38f
        }
        while (paint.measureText(badge.value) > MAX_TEXT_WIDTH && paint.textSize > 18f) {
            paint.textSize -= 2f
        }
        canvas.drawText(badge.value, SIZE / 2f, lineBaseline(paint, 38f), paint)

        paint.textSize = 22f
        canvas.drawText(badge.unit, SIZE / 2f, lineBaseline(paint, 72f), paint)
        return bitmap
    }

    /** The baseline that places the text's visual centre on [centreY]. */
    private fun lineBaseline(paint: Paint, centreY: Float): Float =
        centreY - (paint.ascent() + paint.descent()) / 2f
}
