package com.neilpontecorvo.soundcloudfiretv.feature.home

import com.neilpontecorvo.soundcloudfiretv.core.navigation.ContentSectionSpec
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ScreenViewModel

object HomeScreenFactory {
    fun create(
        body: String = "",
        sections: List<ContentSectionSpec> = emptyList()
    ): ScreenViewModel = ScreenViewModel(
        title = "Home",
        body = body,
        contentSections = sections
    )
}
