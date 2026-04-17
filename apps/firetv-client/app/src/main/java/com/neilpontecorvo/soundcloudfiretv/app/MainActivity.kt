package com.neilpontecorvo.soundcloudfiretv.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.neilpontecorvo.soundcloudfiretv.BuildConfig
import com.neilpontecorvo.soundcloudfiretv.R
import com.neilpontecorvo.soundcloudfiretv.auth.ApiBackedAuthGateway
import com.neilpontecorvo.soundcloudfiretv.auth.AuthSessionState
import com.neilpontecorvo.soundcloudfiretv.core.input.RemoteAction
import com.neilpontecorvo.soundcloudfiretv.core.input.RemoteInputHandler
import com.neilpontecorvo.soundcloudfiretv.core.navigation.AppScreen
import com.neilpontecorvo.soundcloudfiretv.core.navigation.FocusCoordinator
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ScreenRenderer
import com.neilpontecorvo.soundcloudfiretv.feature.diagnostics.DiagnosticsScreenFactory
import com.neilpontecorvo.soundcloudfiretv.feature.home.HomeScreenFactory
import com.neilpontecorvo.soundcloudfiretv.feature.library.LibraryScreenFactory
import com.neilpontecorvo.soundcloudfiretv.feature.player.PlayerScreenFactory
import com.neilpontecorvo.soundcloudfiretv.feature.search.SearchScreenFactory
import com.neilpontecorvo.soundcloudfiretv.feature.settings.SettingsScreenFactory
import com.neilpontecorvo.soundcloudfiretv.network.DeviceSessionApiClient
import com.neilpontecorvo.soundcloudfiretv.webview.WebPlayerHostController

class MainActivity : AppCompatActivity() {

    private lateinit var titleView: TextView
    private lateinit var contentFrame: FrameLayout
    private lateinit var focusCoordinator: FocusCoordinator
    private lateinit var screenRenderer: ScreenRenderer
    private lateinit var authGateway: ApiBackedAuthGateway
    private val webHost = WebPlayerHostController()
    private val authStateListener: (AuthSessionState) -> Unit = { state ->
        refreshSettingsBody(state)
    }

    private var currentScreen: AppScreen = AppScreen.HOME
    private var playerWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        titleView = findViewById(R.id.screenTitle)
        contentFrame = findViewById(R.id.contentFrame)
        focusCoordinator = FocusCoordinator(this)
        screenRenderer = ScreenRenderer(this)
        authGateway = ApiBackedAuthGateway(
            apiClient = DeviceSessionApiClient(BuildConfig.API_BASE_URL),
            deviceName = android.os.Build.MODEL.ifBlank { "Fire TV" },
            appVersion = BuildConfig.VERSION_NAME
        )
        authGateway.addListener(authStateListener)

        bindNavButton(R.id.btnHome, AppScreen.HOME)
        bindNavButton(R.id.btnSearch, AppScreen.SEARCH)
        bindNavButton(R.id.btnLibrary, AppScreen.LIBRARY)
        bindNavButton(R.id.btnPlayer, AppScreen.PLAYER)
        bindNavButton(R.id.btnSettings, AppScreen.SETTINGS)

        navigateTo(AppScreen.HOME)
        authGateway.bootstrapSession()
    }

    override fun onDestroy() {
        authGateway.removeListener(authStateListener)
        authGateway.shutdown()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val action = RemoteInputHandler.mapKeyCode(keyCode)

        if (action == RemoteAction.BACK && currentScreen != AppScreen.HOME) {
            navigateTo(AppScreen.HOME)
            return true
        }

        if (action == RemoteAction.PLAY_PAUSE && currentScreen == AppScreen.PLAYER) {
            playerWebView?.evaluateJavascript("document.querySelector('button[aria-label*=\\\"Play\\\"],button[aria-label*=\\\"Pause\\\"]')?.click();", null)
            return true
        }

        if (action == RemoteAction.MENU && currentScreen != AppScreen.SETTINGS) {
            navigateTo(AppScreen.SETTINGS)
            return true
        }

        if (focusCoordinator.handle(action)) {
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun bindNavButton(buttonId: Int, target: AppScreen) {
        findViewById<Button>(buttonId).setOnClickListener { navigateTo(target) }
    }

    private fun navigateTo(screen: AppScreen) {
        currentScreen = screen
        titleView.text = "Private Cloud TV • ${screen.title}"
        contentFrame.removeAllViews()
        playerWebView?.let { contentFrame.removeView(it) }

        val view = when (screen) {
            AppScreen.HOME -> screenRenderer.render(HomeScreenFactory.create())
            AppScreen.SEARCH -> screenRenderer.render(SearchScreenFactory.create())
            AppScreen.LIBRARY -> screenRenderer.render(LibraryScreenFactory.create())
            AppScreen.PLAYER -> buildPlayerView()
            AppScreen.SETTINGS -> buildSettingsView()
        }

        contentFrame.addView(view)
        view.post { view.findFocus()?.requestFocus() ?: view.requestFocus() }
    }

    private fun buildPlayerView(): View {
        val webView = WebView(this)
        webHost.configure(webView)
        webHost.loadPlayer(webView)
        playerWebView = webView
        return webView
    }

    private fun buildSettingsView(): View {
        val state = authGateway.getCurrentState()
        val appInfo = buildDiagnosticsBody(state)

        val diagnosticsModel = DiagnosticsScreenFactory.create(
            onBootstrapSession = { authGateway.bootstrapSession() },
            onPollSession = { authGateway.pollSession() },
            onRefreshSession = { authGateway.refreshSession() },
            onReload = { playerWebView?.let(webHost::reload) },
            onClearCookies = { webHost.clearCookies() },
            onClearSession = {
                authGateway.clearSession()
                playerWebView?.let(webHost::clearSession)
            },
            appInfo = appInfo
        )

        val settingsModel = SettingsScreenFactory.create().copy(
            title = "Settings & Diagnostics",
            body = appInfo,
            actions = diagnosticsModel.actions
        )

        return screenRenderer.render(settingsModel)
    }

    private fun refreshSettingsBody(state: AuthSessionState) {
        if (currentScreen != AppScreen.SETTINGS) return
        contentFrame.findViewById<TextView>(R.id.panelBody)?.text = buildDiagnosticsBody(state)
    }

    private fun buildDiagnosticsBody(state: AuthSessionState): String {
        val rows = listOf(
            "Version: ${BuildConfig.VERSION_NAME}",
            "Backend API: ${BuildConfig.API_BASE_URL}",
            "Auth state: ${state.phase.label}",
            "Authenticated: ${state.isAuthenticated}",
            "Session ID: ${state.sessionId ?: "none"}",
            "Verification URI: ${state.verificationUri ?: "not issued"}",
            "User code: ${state.userCode ?: "not issued"}",
            "Session expires: ${state.expiresAtIso ?: "unknown"}",
            "Authenticated at: ${state.authenticatedAtIso ?: "not authenticated"}",
            "Access token expires: ${state.accessTokenExpiresAtIso ?: "not available"}",
            "Last error: ${state.lastErrorMessage ?: "none"}",
            "Build: Debug scaffold"
        )
        return rows.joinToString(separator = "\n")
    }
}
