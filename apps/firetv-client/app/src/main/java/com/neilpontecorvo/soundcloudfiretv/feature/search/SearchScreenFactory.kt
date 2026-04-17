package com.neilpontecorvo.soundcloudfiretv.feature.search

import com.neilpontecorvo.soundcloudfiretv.core.navigation.ScreenViewModel

object SearchScreenFactory {
    fun create(): ScreenViewModel = ScreenViewModel(
        title = "Search",
        body = "Search UI shell for remote-driven text entry and API-backed results."
    )
}
