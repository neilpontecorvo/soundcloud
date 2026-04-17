package com.neilpontecorvo.soundcloudfiretv.feature.library

import com.neilpontecorvo.soundcloudfiretv.core.navigation.ScreenViewModel

object LibraryScreenFactory {
    fun create(): ScreenViewModel = ScreenViewModel(
        title = "Library",
        body = "User library shell for likes, playlists, and history once backend session mode is enabled."
    )
}
