package com.neilpontecorvo.soundcloudfiretv.core.navigation

import android.view.View

object TvFocusStyler {
    fun apply(
        view: View,
        focusedScale: Float = 1.08f,
        onFocusChanged: ((Boolean) -> Unit)? = null
    ) {
        view.setOnFocusChangeListener { target, hasFocus ->
            target.animate()
                .scaleX(if (hasFocus) focusedScale else 1f)
                .scaleY(if (hasFocus) focusedScale else 1f)
                .alpha(if (hasFocus) 1f else 0.88f)
                .setDuration(110L)
                .start()
            target.elevation = if (hasFocus) 28f else 0f
            onFocusChanged?.invoke(hasFocus)
        }
        view.alpha = 0.88f
    }
}
