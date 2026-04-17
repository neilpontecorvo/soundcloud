package com.neilpontecorvo.soundcloudfiretv.feature.player

import com.neilpontecorvo.soundcloudfiretv.core.navigation.ScreenViewModel

object PlayerScreenFactory {
    fun create(): ScreenViewModel = ScreenViewModel(
        title = "Player",
        body = "WebView host layer for MVP SoundCloud playback with native remote transport integration."
    )
}
