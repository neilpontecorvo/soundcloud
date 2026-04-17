package com.neilpontecorvo.soundcloudfiretv.feature.search

import com.neilpontecorvo.soundcloudfiretv.core.navigation.ContentSectionSpec
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ScreenViewModel

object SearchScreenFactory {
    fun create(
        body: String = "Loading backend search preview...",
        sections: List<ContentSectionSpec> = emptyList()
    ): ScreenViewModel = ScreenViewModel(
        title = "Search",
        body = body,
        contentSections = sections
    )
}
