package com.neilpontecorvo.soundcloudfiretv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import kotlin.math.abs
import kotlin.math.max

class TvWaveformView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var waveform: Bitmap? = null
    private var seed: Int = 1
    private var progress: Float = 0f
    private var requestToken: String? = null
    private var forceFocusOutline = false

    fun setTrack(waveformUrl: String?, stableId: String) {
        seed = stableId.hashCode()
        waveform = null
        requestToken = waveformUrl
        invalidate()
        TvArtworkLoader.loadBitmap(context, waveformUrl, 1350, 180) { bitmap ->
            if (requestToken == waveformUrl) {
                waveform = bitmap
                invalidate()
            }
        }
    }

    fun setProgress(positionMs: Long, durationMs: Long) {
        progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        invalidate()
    }

    fun setFocusHighlighted(highlighted: Boolean) {
        forceFocusOutline = highlighted
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = waveform
        if (bitmap != null) {
            drawBitmapWaveform(canvas, bitmap)
        } else {
            drawFallbackBars(canvas)
        }
        if (isFocused || forceFocusOutline) drawFocusOutline(canvas)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    private fun drawFocusOutline(canvas: Canvas) {
        val stroke = max(3f, width / 420f)
        paint.colorFilter = null
        paint.color = TvDesign.YELLOW
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        canvas.drawRoundRect(
            RectF(stroke / 2f, stroke / 2f, width - stroke / 2f, height - stroke / 2f),
            stroke * 2f,
            stroke * 2f,
            paint
        )
        paint.style = Paint.Style.FILL
    }

    private fun drawBitmapWaveform(canvas: Canvas, bitmap: Bitmap) {
        val source = Rect(0, 0, bitmap.width, bitmap.height)
        val destination = Rect(0, 0, width, height)
        paint.colorFilter = PorterDuffColorFilter(TvDesign.MUTED, PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, source, destination, paint)
        canvas.save()
        canvas.clipRect(0f, 0f, width * progress, height.toFloat())
        paint.colorFilter = PorterDuffColorFilter(TvDesign.ORANGE, PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, source, destination, paint)
        canvas.restore()
        paint.colorFilter = null
    }

    private fun drawFallbackBars(canvas: Canvas) {
        val bars = 92
        val gap = max(2f, width / 480f)
        val barWidth = max(3f, (width - gap * (bars - 1)) / bars)
        val center = height / 2f
        for (index in 0 until bars) {
            val signal = abs(((seed * 31L + index * 97L) % 100).toInt() - 50) / 50f
            val barHeight = height * (0.18f + signal * 0.72f)
            val left = index * (barWidth + gap)
            paint.color = if (left / width.coerceAtLeast(1).toFloat() <= progress) TvDesign.ORANGE else TvDesign.MUTED
            canvas.drawRoundRect(
                RectF(left, center - barHeight / 2f, left + barWidth, center + barHeight / 2f),
                barWidth / 2f,
                barWidth / 2f,
                paint
            )
        }
    }
}
