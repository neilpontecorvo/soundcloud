package com.neilpontecorvo.soundcloudfiretv.feature.diagnostics

import com.neilpontecorvo.soundcloudfiretv.core.navigation.ActionSpec
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ScreenViewModel

object DiagnosticsScreenFactory {
    fun create(
        onBootstrapSession: () -> Unit,
        onPollSession: () -> Unit,
        onRefreshSession: () -> Unit,
        onReload: () -> Unit,
        onClearCookies: () -> Unit,
        onClearSession: () -> Unit,
        appInfo: String
    ): ScreenViewModel = ScreenViewModel(
        title = "Diagnostics",
        body = appInfo,
        actions = listOf(
            ActionSpec("Bootstrap Session", onBootstrapSession),
            ActionSpec("Poll Session", onPollSession),
            ActionSpec("Refresh Session", onRefreshSession),
            ActionSpec("Reload Player", onReload),
            ActionSpec("Clear Cookies", onClearCookies),
            ActionSpec("Clear Session", onClearSession)
        )
    )
}
