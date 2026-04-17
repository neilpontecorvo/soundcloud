package com.neilpontecorvo.soundcloudfiretv.feature.home

import com.neilpontecorvo.soundcloudfiretv.core.navigation.ScreenViewModel

object HomeScreenFactory {
    fun create(): ScreenViewModel = ScreenViewModel(
        title = "Home",
        body = "TV-first launch surface with large focusable rails and quick actions."
    )
}
