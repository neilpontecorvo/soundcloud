package com.neilpontecorvo.soundcloudfiretv.feature.library

import com.neilpontecorvo.soundcloudfiretv.core.navigation.ContentSectionSpec
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ScreenViewModel

object LibraryScreenFactory {
    fun create(
        body: String = "Loading library from backend...",
        sections: List<ContentSectionSpec> = emptyList()
    ): ScreenViewModel = ScreenViewModel(
        title = "Library",
        body = body,
        contentSections = sections
    )
}
