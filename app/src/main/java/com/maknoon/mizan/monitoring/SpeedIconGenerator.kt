package com.maknoon.mizan.monitoring

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.LruCache
import androidx.core.graphics.drawable.IconCompat
import com.maknoon.mizan.core.data.settings.SpeedBadgeStyle
import com.maknoon.mizan.core.domain.format.SpeedBadge
import com.maknoon.mizan.core.domain.format.SpeedBadgeFormatter

/**
 * Draws the live rate into the notification's status-bar icon: the value and its single-letter
 * unit, white on transparent so the system can tint it like any other status icon. Returns null
 * when the reading is too small to show, which leaves the caller on its static icon.
 *
 * Everything is drawn in a [CANVAS_SIZE] square and shrinks only to fit, because the slot is
 * barely 24dp: a plain bold outline at that size reads as a smudge beside the system glyphs, so
 * each glyph is filled and stroked to thicken it. [draw] is public so the settings screen can
 * preview the very same code path instead of a hand-drawn approximation.
 *
 * A bitmap per distinct reading is cached, because the notification refreshes every second and
 * allocating a new bitmap for an unchanged value (idle traffic) would be pure garbage.
 */
object SpeedIconGenerator {

    /** The square every template draws into; callers scale it to whatever slot they fill. */
    const val CANVAS_SIZE = 96

    private const val MAX_TEXT_WIDTH = 94f
    private const val UNIT_GAP = 2f
    private const val STROKE_RATIO = 0.07f
    private const val MIN_NUMBER_SIZE = 24f

    // Raised and uniform keep the number on one line, so it may fill the whole height.
    private const val INLINE_MAX_NUMBER_SIZE = 68f
    private const val RAISED_UNIT_RATIO = 0.56f

    // Two lines share the square: the number stops at 60 so it keeps its cap inside once stroked,
    // and the spelled-out unit takes 60% of it, which is what makes `KB/s` readable at 24dp.
    private const val UNDER_MAX_NUMBER_SIZE = 60f
    private const val UNDER_UNIT_RATIO = 0.60f
    private const val UNDER_NUMBER_CENTRE_Y = 30f
    private const val UNDER_UNIT_CENTRE_Y = 78f

    private val cache = LruCache<String, IconCompat>(32)

    private val boldFont: Typeface =
        Typeface.create("sans-serif-condensed", Typeface.BOLD)

    private val regularFont: Typeface =
        Typeface.create("sans-serif-condensed", Typeface.NORMAL)

    fun icon(
        bytesPerSecond: Double,
        showBytes: Boolean,
        style: SpeedBadgeStyle,
        bold: Boolean = true,
    ): IconCompat? {
        val badge = SpeedBadgeFormatter.iconBadge(bytesPerSecond, showBytes) ?: return null
        val key = badge.value + badge.unit + style.name + bold
        cache.get(key)?.let { return it }
        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), badge, style, bold)
        val created = IconCompat.createWithBitmap(bitmap)
        cache.put(key, created)
        return created
    }

    /** Draws [badge] into a [CANVAS_SIZE] square already scaled onto [canvas]. */
    fun draw(
        canvas: Canvas,
        badge: SpeedBadge,
        style: SpeedBadgeStyle,
        bold: Boolean = true,
    ) {
        when (style) {
            SpeedBadgeStyle.UNDER -> drawStacked(canvas, badge, bold)
            SpeedBadgeStyle.UNIFORM -> drawInline(canvas, badge, unitRatio = 1f, bold = bold)
            SpeedBadgeStyle.RAISED -> drawInline(canvas, badge, unitRatio = RAISED_UNIT_RATIO, bold = bold)
        }
    }

    /** One baseline: the number, a gap, then the unit at [unitRatio] of its size. */
    private fun drawInline(canvas: Canvas, badge: SpeedBadge, unitRatio: Float, bold: Boolean) {
        val number = glyphPaint(bold)
        val unit = glyphPaint(bold)
        var numberSize = INLINE_MAX_NUMBER_SIZE
        while (numberSize > MIN_NUMBER_SIZE &&
            inlineWidth(number, unit, badge, numberSize, unitRatio) > MAX_TEXT_WIDTH
        ) {
            numberSize -= 2f
        }
        applySize(number, numberSize)
        applySize(unit, numberSize * unitRatio)

        val numberWidth = number.measureText(badge.value) + number.strokeWidth
        val unitWidth = unit.measureText(badge.unit) + unit.strokeWidth
        val startX = (CANVAS_SIZE - (numberWidth + UNIT_GAP + unitWidth)) / 2f
        val baseline = centreBaseline(number, CANVAS_SIZE / 2f)
        // A smaller unit shares the number's cap line rather than its baseline: `345K` with a
        // raised K. At full size both sit on the same baseline.
        val unitBaseline =
            if (unitRatio == 1f) baseline else baseline + number.ascent() - unit.ascent()

        canvas.drawText(badge.value, startX, baseline, number)
        canvas.drawText(badge.unit, startX + numberWidth + UNIT_GAP, unitBaseline, unit)
    }

    /** Two lines: the number above, the spelled-out unit centred underneath it. */
    private fun drawStacked(canvas: Canvas, badge: SpeedBadge, bold: Boolean) {
        val number = glyphPaint(bold)
        val unit = glyphPaint(bold)
        var numberSize = UNDER_MAX_NUMBER_SIZE
        while (numberSize > MIN_NUMBER_SIZE &&
            stackedWidth(number, unit, badge, numberSize) > MAX_TEXT_WIDTH
        ) {
            numberSize -= 2f
        }
        applySize(number, numberSize)
        applySize(unit, numberSize * UNDER_UNIT_RATIO)

        canvas.drawText(
            badge.value,
            centredX(number, badge.value),
            centreBaseline(number, UNDER_NUMBER_CENTRE_Y),
            number,
        )
        canvas.drawText(
            badge.fullUnit,
            centredX(unit, badge.fullUnit),
            centreBaseline(unit, UNDER_UNIT_CENTRE_Y),
            unit,
        )
    }

    private fun glyphPaint(bold: Boolean): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.WHITE
            typeface = if (bold) boldFont else regularFont
            textAlign = Paint.Align.LEFT
            // Only the heavy face is stroked: a stroke on a regular weight closes the counters.
            style = if (bold) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
        }

    /** The stroke is what thickens the glyph, so it always travels with the text size. */
    private fun applySize(paint: Paint, textSize: Float) {
        paint.textSize = textSize
        paint.strokeWidth =
            if (paint.style == Paint.Style.FILL_AND_STROKE) textSize * STROKE_RATIO else 0f
    }

    private fun inlineWidth(
        number: Paint,
        unit: Paint,
        badge: SpeedBadge,
        numberSize: Float,
        unitRatio: Float,
    ): Float {
        applySize(number, numberSize)
        applySize(unit, numberSize * unitRatio)
        return number.measureText(badge.value) + number.strokeWidth + UNIT_GAP +
            unit.measureText(badge.unit) + unit.strokeWidth
    }

    /** Stacked lines are only as wide as their longest one. */
    private fun stackedWidth(
        number: Paint,
        unit: Paint,
        badge: SpeedBadge,
        numberSize: Float,
    ): Float {
        applySize(number, numberSize)
        applySize(unit, numberSize * UNDER_UNIT_RATIO)
        return maxOf(
            number.measureText(badge.value) + number.strokeWidth,
            unit.measureText(badge.fullUnit) + unit.strokeWidth,
        )
    }

    private fun centredX(paint: Paint, text: String): Float =
        (CANVAS_SIZE - paint.measureText(text) - paint.strokeWidth) / 2f

    /** The baseline that places the text's visual centre on [centreY]. */
    private fun centreBaseline(paint: Paint, centreY: Float): Float =
        centreY - (paint.ascent() + paint.descent()) / 2f
}
