package com.neilpontecorvo.soundcloudfiretv.core.navigation

import android.view.View
import android.view.animation.OvershootInterpolator

/**
 * Applies TV-friendly focus styling to views with scale, elevation, and alpha effects.
 * Designed to be visible from across the room on Fire TV displays.
 */
object TvFocusStyler {

    private val overshootInterpolator = OvershootInterpolator(1.5f)

    fun apply(
        view: View,
        focusedScale: Float = 1.08f,
        focusedElevation: Float = 24f,
        unfocusedAlpha: Float = 0.85f,
        animationDuration: Long = 150L,
        onFocusChanged: ((Boolean) -> Unit)? = null
    ) {
        // Set initial state
        view.alpha = unfocusedAlpha
        view.elevation = 0f

        view.setOnFocusChangeListener { target, hasFocus ->
            if (hasFocus) {
                // Scale up with overshoot for satisfying TV feel
                target.animate()
                    .scaleX(focusedScale)
                    .scaleY(focusedScale)
                    .alpha(1f)
                    .setDuration(animationDuration)
                    .setInterpolator(overshootInterpolator)
                    .start()
                target.elevation = focusedElevation

                // Bring to front for proper layering
                target.bringToFront()
                target.parent?.requestLayout()
            } else {
                // Scale back down smoothly
                target.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(unfocusedAlpha)
                    .setDuration(animationDuration - 30)
                    .setInterpolator(null)
                    .start()
                target.elevation = 0f
            }

            onFocusChanged?.invoke(hasFocus)
        }
    }

    /**
     * Applies minimal focus styling for nav buttons that rely more on background state.
     */
    fun applyNavStyle(
        view: View,
        focusedScale: Float = 1.05f,
        animationDuration: Long = 120L
    ) {
        view.setOnFocusChangeListener { target, hasFocus ->
            target.animate()
                .scaleX(if (hasFocus) focusedScale else 1f)
                .scaleY(if (hasFocus) focusedScale else 1f)
                .setDuration(animationDuration)
                .start()
            target.elevation = if (hasFocus) 8f else 0f
        }
    }
}
