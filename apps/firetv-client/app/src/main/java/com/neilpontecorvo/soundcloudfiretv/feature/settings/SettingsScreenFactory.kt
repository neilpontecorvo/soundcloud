package com.neilpontecorvo.soundcloudfiretv.feature.settings

import com.neilpontecorvo.soundcloudfiretv.core.navigation.ScreenViewModel

object SettingsScreenFactory {
    fun create(): ScreenViewModel = ScreenViewModel(
        title = "Settings",
        body = "App-level preferences, auth state, and diagnostics entrypoint."
    )
}
