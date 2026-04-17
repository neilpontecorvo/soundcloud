package com.neilpontecorvo.soundcloudfiretv.feature.search

import com.neilpontecorvo.soundcloudfiretv.core.navigation.ScreenViewModel

object SearchScreenFactory {
    fun create(body: String = "Loading backend search preview..."): ScreenViewModel = ScreenViewModel(
        title = "Search",
        body = body
    )
}
