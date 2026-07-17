package com.neilpontecorvo.soundcloudfiretv.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.min
import kotlin.math.roundToInt

data class TvDesignMetrics(
    val windowWidth: Int,
    val windowHeight: Int
) {
    val scale: Float = min(windowWidth / REFERENCE_WIDTH, windowHeight / REFERENCE_HEIGHT)
    val offsetX: Int = ((windowWidth - REFERENCE_WIDTH * scale) / 2f).roundToInt()
    val offsetY: Int = ((windowHeight - REFERENCE_HEIGHT * scale) / 2f).roundToInt()

    fun px(units: Int): Int = (units * scale).roundToInt()
    fun textPx(units: Float): Float = units * scale

    fun frame(x: Int, y: Int, width: Int, height: Int): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(px(width), px(height)).apply {
            leftMargin = offsetX + px(x)
            topMargin = offsetY + px(y)
        }

    fun relativeFrame(x: Int, y: Int, width: Int, height: Int): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(px(width), px(height)).apply {
            leftMargin = px(x)
            topMargin = px(y)
        }

    fun applyFrame(view: View, x: Int, y: Int, width: Int, height: Int) {
        view.layoutParams = frame(x, y, width, height)
    }

    companion object {
        private const val REFERENCE_WIDTH = 1920f
        private const val REFERENCE_HEIGHT = 1080f
    }
}

object TvDesign {
    val BLACK: Int = Color.BLACK
    val SURFACE: Int = Color.rgb(17, 17, 17)
    val SURFACE_RAISED: Int = Color.rgb(24, 24, 24)
    val BORDER: Int = Color.rgb(54, 54, 54)
    val TEXT: Int = Color.rgb(244, 244, 244)
    val MUTED: Int = Color.rgb(155, 155, 155)
    val DIM: Int = Color.rgb(82, 82, 82)
    val ORANGE: Int = Color.rgb(255, 85, 0)
    val YELLOW: Int = Color.rgb(255, 216, 63)
    val ERROR: Int = Color.rgb(255, 105, 105)

    fun rounded(
        fill: Int,
        radiusPx: Int,
        strokeWidthPx: Int = 0,
        stroke: Int = Color.TRANSPARENT
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = radiusPx.toFloat()
        if (strokeWidthPx > 0) setStroke(strokeWidthPx, stroke)
    }

    fun oval(
        fill: Int,
        strokeWidthPx: Int = 0,
        stroke: Int = Color.TRANSPARENT
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        if (strokeWidthPx > 0) setStroke(strokeWidthPx, stroke)
    }
}

fun Context.designMetrics(): TvDesignMetrics {
    val metrics = resources.displayMetrics
    return TvDesignMetrics(metrics.widthPixels, metrics.heightPixels)
}

fun View.detachFromParent() {
    (parent as? ViewGroup)?.removeView(this)
}
