package com.neilpontecorvo.soundcloudfiretv.core.navigation

import android.view.View

object TvFocusStyler {
    fun apply(
        view: View,
        focusedScale: Float = 1.06f,
        onFocusChanged: ((Boolean) -> Unit)? = null
    ) {
        view.setOnFocusChangeListener { target, hasFocus ->
            target.animate()
                .scaleX(if (hasFocus) focusedScale else 1f)
                .scaleY(if (hasFocus) focusedScale else 1f)
                .setDuration(90L)
                .start()
            target.elevation = if (hasFocus) 18f else 0f
            onFocusChanged?.invoke(hasFocus)
        }
    }
}
