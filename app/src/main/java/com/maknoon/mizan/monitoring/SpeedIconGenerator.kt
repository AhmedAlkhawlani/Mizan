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
 * Draws the live rate into the notification's status-bar icon: the value and its single-letter
 * unit on one line, white on transparent so the system can tint it like any other status icon.
 * Returns null when the reading is too small to show, which leaves the caller on its static icon.
 *
 * The slot is barely 24dp, so the digits take all the width they can and are then thickened with
 * a stroke: a plain bold outline at that size reads as a smudge beside the system glyphs. The
 * unit rides beside the number rather than under it because a second line would halve the height
 * of the digits, which is exactly what made them unreadable.
 *
 * A bitmap per distinct reading is cached, because the notification refreshes every second and
 * allocating a new bitmap for an unchanged value (idle traffic) would be pure garbage.
 */
object SpeedIconGenerator {

    private const val SIZE = 96
    private const val MAX_TEXT_WIDTH = 94f
    private const val UNIT_GAP = 2f
    private const val UNIT_RATIO = 0.56f
    private const val STROKE_RATIO = 0.07f
    private const val MAX_NUMBER_SIZE = 68f
    private const val MIN_NUMBER_SIZE = 24f

    private val cache = LruCache<String, IconCompat>(32)

    private val font: Typeface =
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
        val number = glyphPaint()
        val unit = glyphPaint()

        var numberSize = MAX_NUMBER_SIZE
        while (numberSize > MIN_NUMBER_SIZE &&
            totalWidth(number, unit, badge, numberSize) > MAX_TEXT_WIDTH
        ) {
            numberSize -= 2f
        }
        applySize(number, numberSize)
        applySize(unit, numberSize * UNIT_RATIO)

        val numberWidth = number.measureText(badge.value) + number.strokeWidth
        val unitWidth = unit.measureText(badge.unit) + unit.strokeWidth
        val startX = (SIZE - (numberWidth + UNIT_GAP + unitWidth)) / 2f
        val baseline = centreBaseline(number, SIZE / 2f)
        // The unit shares the number's cap line rather than its baseline, so it reads as `345K`.
        val unitBaseline = baseline + number.ascent() - unit.ascent()

        canvas.drawText(badge.value, startX, baseline, number)
        canvas.drawText(badge.unit, startX + numberWidth + UNIT_GAP, unitBaseline, unit)
        return bitmap
    }

    private fun glyphPaint(): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.WHITE
            typeface = font
            textAlign = Paint.Align.LEFT
            style = Paint.Style.FILL_AND_STROKE
        }

    /** The stroke is what thickens the glyph, so it always travels with the text size. */
    private fun applySize(paint: Paint, textSize: Float) {
        paint.textSize = textSize
        paint.strokeWidth = textSize * STROKE_RATIO
    }

    private fun totalWidth(
        number: Paint,
        unit: Paint,
        badge: SpeedBadge,
        numberSize: Float,
    ): Float {
        applySize(number, numberSize)
        applySize(unit, numberSize * UNIT_RATIO)
        return number.measureText(badge.value) + number.strokeWidth + UNIT_GAP +
            unit.measureText(badge.unit) + unit.strokeWidth
    }

    /** The baseline that places the text's visual centre on [centreY]. */
    private fun centreBaseline(paint: Paint, centreY: Float): Float =
        centreY - (paint.ascent() + paint.descent()) / 2f
}
