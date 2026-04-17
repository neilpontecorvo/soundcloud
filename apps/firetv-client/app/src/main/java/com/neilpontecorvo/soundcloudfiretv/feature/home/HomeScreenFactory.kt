package com.neilpontecorvo.soundcloudfiretv.feature.home

import com.neilpontecorvo.soundcloudfiretv.core.navigation.ScreenViewModel

object HomeScreenFactory {
    fun create(body: String = "Loading feed from backend..."): ScreenViewModel = ScreenViewModel(
        title = "Home",
        body = body
    )
}
