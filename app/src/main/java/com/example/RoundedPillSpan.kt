package com.example

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan

/**
 * Custom ReplacementSpan that renders text inside a modern, rounded pill badge
 * with custom background color, optional border/glow stroke, text color, and padding.
 */
class RoundedPillSpan(
    private val bgColor: Int,
    private val textColor: Int,
    private val borderColor: Int = 0,
    private val cornerRadius: Float = 14f,
    private val paddingH: Float = 16f,
    private val paddingV: Float = 6f,
    private val isBold: Boolean = true,
    private val strokeWidth: Float = 2.5f
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val originalBold = paint.isFakeBoldText
        paint.isFakeBoldText = isBold
        val textWidth = paint.measureText(text, start, end)
        paint.isFakeBoldText = originalBold

        if (fm != null) {
            val currentFm = paint.fontMetricsInt
            val extraV = (paddingV / 2).toInt()
            fm.ascent = currentFm.ascent - extraV
            fm.descent = currentFm.descent + extraV
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }
        return (textWidth + paddingH * 2).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val originalColor = paint.color
        val originalStyle = paint.style
        val originalStroke = paint.strokeWidth
        val originalBold = paint.isFakeBoldText

        paint.isFakeBoldText = isBold
        val textWidth = paint.measureText(text, start, end)

        val rectTop = top.toFloat() + 2f
        val rectBottom = bottom.toFloat() - 2f
        val rect = RectF(x + 2f, rectTop, x + textWidth + paddingH * 2 - 2f, rectBottom)

        // 1. Draw Pill Background
        paint.color = bgColor
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        // 2. Draw Pill Border / Glow
        if (borderColor != 0) {
            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        }

        // 3. Draw Text
        paint.style = Paint.Style.FILL
        paint.color = textColor
        canvas.drawText(text, start, end, x + paddingH, y.toFloat(), paint)

        // Restore paint attributes
        paint.color = originalColor
        paint.style = originalStyle
        paint.strokeWidth = originalStroke
        paint.isFakeBoldText = originalBold
    }
}
