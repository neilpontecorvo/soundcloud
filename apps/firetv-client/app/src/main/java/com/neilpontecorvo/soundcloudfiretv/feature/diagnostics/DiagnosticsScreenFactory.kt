package com.neilpontecorvo.soundcloudfiretv.feature.diagnostics

import com.neilpontecorvo.soundcloudfiretv.core.navigation.ActionSpec
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ScreenViewModel

object DiagnosticsScreenFactory {
    fun create(
        onBootstrapSession: () -> Unit,
        onPollSession: () -> Unit,
        onRefreshSession: () -> Unit,
        onDebugAuthenticateSession: (() -> Unit)?,
        onReload: () -> Unit,
        onClearCookies: () -> Unit,
        onClearSession: () -> Unit,
        appInfo: String
    ): ScreenViewModel = ScreenViewModel(
        title = "Diagnostics",
        body = appInfo,
        actions = buildList {
            add(ActionSpec("Bootstrap Session", onBootstrapSession))
            add(ActionSpec("Poll Session", onPollSession))
            add(ActionSpec("Refresh Session", onRefreshSession))
            if (onDebugAuthenticateSession != null) {
                add(ActionSpec("Authenticate Debug Session", onDebugAuthenticateSession))
            }
            add(ActionSpec("Reload Player", onReload))
            add(ActionSpec("Clear Cookies", onClearCookies))
            add(ActionSpec("Clear Session", onClearSession))
        }
    )
}
