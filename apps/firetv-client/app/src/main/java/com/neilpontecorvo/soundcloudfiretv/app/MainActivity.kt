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
import com.neilpontecorvo.soundcloudfiretv.content.ContentLoadState
import com.neilpontecorvo.soundcloudfiretv.content.ContentRepository
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
import com.neilpontecorvo.soundcloudfiretv.webview.HardenedWebViewClient
import com.neilpontecorvo.soundcloudfiretv.webview.PlayerBridge
import com.neilpontecorvo.soundcloudfiretv.webview.WebPlayerHostController
import com.neilpontecorvo.soundcloudfiretv.webview.WebViewHostConfig

class MainActivity : AppCompatActivity(), HardenedWebViewClient.NavigationListener {

    private lateinit var titleView: TextView
    private lateinit var contentFrame: FrameLayout
    private lateinit var focusCoordinator: FocusCoordinator
    private lateinit var screenRenderer: ScreenRenderer
    private lateinit var apiClient: DeviceSessionApiClient
    private lateinit var authGateway: ApiBackedAuthGateway
    private lateinit var contentRepository: ContentRepository

    // WebView hardening components
    private val webHost = WebPlayerHostController(
        config = WebViewHostConfig.DEFAULT,
        isDebugBuild = BuildConfig.DEBUG
    ).apply {
        navigationListener = this@MainActivity
    }
    private val playerBridge = PlayerBridge()

    private val authStateListener: (AuthSessionState) -> Unit = { state ->
        refreshSettingsBody(state)
        refreshContentForCurrentScreen(state)
    }

    private var currentScreen: AppScreen = AppScreen.HOME
    private var playerWebView: WebView? = null

    // WebView diagnostic state for display
    private var lastBlockedNavigation: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        titleView = findViewById(R.id.screenTitle)
        contentFrame = findViewById(R.id.contentFrame)
        focusCoordinator = FocusCoordinator(this)
        screenRenderer = ScreenRenderer(this)
        apiClient = DeviceSessionApiClient(BuildConfig.API_BASE_URL)
        authGateway = ApiBackedAuthGateway(
            apiClient = apiClient,
            deviceName = android.os.Build.MODEL.ifBlank { "Fire TV" },
            appVersion = BuildConfig.VERSION_NAME
        )
        contentRepository = ContentRepository(apiClient)
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
        contentRepository.shutdown()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val action = RemoteInputHandler.mapKeyCode(keyCode)

        if (action == RemoteAction.BACK && currentScreen != AppScreen.HOME) {
            navigateTo(AppScreen.HOME)
            return true
        }

        if (action == RemoteAction.PLAY_PAUSE && currentScreen == AppScreen.PLAYER) {
            playerWebView?.let { playerBridge.sendTogglePlayPause(it) }
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

    // HardenedWebViewClient.NavigationListener implementation

    override fun onNavigationBlocked(url: String, reason: WebViewHostConfig.BlockReason, message: String) {
        lastBlockedNavigation = "$url (${reason.name})"
        runOnUiThread {
            if (currentScreen == AppScreen.SETTINGS) {
                refreshSettingsBody(authGateway.getCurrentState())
            }
        }
    }

    override fun onPageStarted(url: String) {
        // Could be used for loading indicator in future
    }

    override fun onPageFinished(url: String) {
        runOnUiThread {
            if (currentScreen == AppScreen.SETTINGS) {
                refreshSettingsBody(authGateway.getCurrentState())
            }
        }
    }

    override fun onLoadError(url: String?, errorCode: Int, description: String) {
        runOnUiThread {
            if (currentScreen == AppScreen.SETTINGS) {
                refreshSettingsBody(authGateway.getCurrentState())
            }
        }
    }

    override fun onSslError(url: String?) {
        runOnUiThread {
            if (currentScreen == AppScreen.SETTINGS) {
                refreshSettingsBody(authGateway.getCurrentState())
            }
        }
    }

    private fun bindNavButton(buttonId: Int, target: AppScreen) {
        findViewById<Button>(buttonId).setOnClickListener { navigateTo(target) }
    }

    private fun navigateTo(screen: AppScreen) {
        currentScreen = screen
        titleView.text = "Private Cloud TV \u2022 ${screen.title}"
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
        requestContentFor(screen, authGateway.getCurrentState())
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
                lastBlockedNavigation = null
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

    private fun refreshContentForCurrentScreen(state: AuthSessionState) {
        if (currentScreen == AppScreen.HOME || currentScreen == AppScreen.SEARCH || currentScreen == AppScreen.LIBRARY) {
            requestContentFor(currentScreen, state)
        }
    }

    private fun requestContentFor(screen: AppScreen, state: AuthSessionState) {
        val sessionId = state.sessionId
        if (sessionId == null) {
            if (screen == AppScreen.HOME || screen == AppScreen.SEARCH || screen == AppScreen.LIBRARY) {
                updatePanelBody("Waiting for backend session bootstrap...")
            }
            return
        }

        when (screen) {
            AppScreen.HOME -> contentRepository.loadFeed(sessionId) { nextState ->
                updateContentBody(AppScreen.HOME, nextState, "Feed is empty.")
            }
            AppScreen.SEARCH -> contentRepository.search(sessionId, "") { nextState ->
                updateContentBody(AppScreen.SEARCH, nextState, "Search returned no results.")
            }
            AppScreen.LIBRARY -> contentRepository.loadLibrary(sessionId) { nextState ->
                updateContentBody(AppScreen.LIBRARY, nextState, "Library is empty.")
            }
            else -> Unit
        }
    }

    private fun updateContentBody(screen: AppScreen, state: ContentLoadState, emptyMessage: String) {
        if (currentScreen != screen) return
        val body = when (state) {
            ContentLoadState.Loading -> "Loading ${screen.title.lowercase()} from backend..."
            ContentLoadState.Empty -> emptyMessage
            is ContentLoadState.Success -> state.body
            is ContentLoadState.Error -> "Unable to load ${screen.title.lowercase()}.\n${state.message}"
        }
        updatePanelBody(body)
    }

    private fun updatePanelBody(body: String) {
        contentFrame.findViewById<TextView>(R.id.panelBody)?.text = body
    }

    private fun buildDiagnosticsBody(state: AuthSessionState): String {
        val webViewState = webHost.getDiagnosticState()

        val rows = listOf(
            // App info section
            "=== App Info ===",
            "Version: ${BuildConfig.VERSION_NAME}",
            "Build type: ${if (BuildConfig.DEBUG) "Debug" else "Release"}",
            "Backend API: ${BuildConfig.API_BASE_URL}",
            "",
            // Auth/session section
            "=== Session ===",
            "Auth state: ${state.phase.label}",
            "Authenticated: ${state.isAuthenticated}",
            "Session ID: ${state.sessionId ?: "none"}",
            "Verification URI: ${state.verificationUri ?: "not issued"}",
            "User code: ${state.userCode ?: "not issued"}",
            "Session expires: ${state.expiresAtIso ?: "unknown"}",
            "Authenticated at: ${state.authenticatedAtIso ?: "not authenticated"}",
            "Access token expires: ${state.accessTokenExpiresAtIso ?: "not available"}",
            "Last auth error: ${state.lastErrorMessage ?: "none"}",
            "",
            // WebView hardening section
            "=== WebView Hardening ===",
            "Hardening enabled: ${webViewState.hardeningEnabled}",
            "Debug mode: ${webViewState.isDebugBuild}",
            "Controlled host: ${webViewState.entryUrl}",
            "Allowed hosts: ${webViewState.allowedHosts}",
            "Current URL: ${webViewState.currentUrl ?: "not loaded"}",
            "Loading: ${webViewState.isLoading}",
            "Last blocked URL: ${webViewState.lastBlockedUrl ?: "none"}",
            "Block reason: ${webViewState.lastBlockedReason ?: "n/a"}",
            "Last WebView error: ${webViewState.lastError ?: "none"}"
        )
        return rows.joinToString(separator = "\n")
    }
}
