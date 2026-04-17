package com.neilpontecorvo.soundcloudfiretv.app

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
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
import com.neilpontecorvo.soundcloudfiretv.core.navigation.TvFocusStyler
import com.neilpontecorvo.soundcloudfiretv.feature.diagnostics.DiagnosticsScreenFactory
import com.neilpontecorvo.soundcloudfiretv.feature.home.HomeScreenFactory
import com.neilpontecorvo.soundcloudfiretv.feature.library.LibraryScreenFactory
import com.neilpontecorvo.soundcloudfiretv.feature.search.SearchScreenFactory
import com.neilpontecorvo.soundcloudfiretv.feature.settings.SettingsScreenFactory
import com.neilpontecorvo.soundcloudfiretv.network.DeviceSessionApiClient
import com.neilpontecorvo.soundcloudfiretv.webview.HardenedWebViewClient
import com.neilpontecorvo.soundcloudfiretv.webview.PlayerBridge
import com.neilpontecorvo.soundcloudfiretv.webview.WebPlayerHostController
import com.neilpontecorvo.soundcloudfiretv.webview.WebViewHostConfig

class MainActivity : AppCompatActivity(),
    HardenedWebViewClient.NavigationListener,
    PlayerBridge.BridgeEventListener {

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
    private val playerBridge by lazy { PlayerBridge(this) }

    private val authStateListener: (AuthSessionState) -> Unit = { state ->
        refreshSettingsBody(state)
        refreshContentForCurrentScreen(state)
    }

    private var currentScreen: AppScreen = AppScreen.HOME
    private var playerWebView: WebView? = null
    private var playerStateView: TextView? = null
    private var playerTrackView: TextView? = null
    private var playerArtistView: TextView? = null
    private var playerErrorView: TextView? = null
    private var playerUiState = PlayerUiState()

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

        setupNavigation()
        navigateTo(AppScreen.HOME)
        authGateway.bootstrapSession()
    }

    private fun setupNavigation() {
        bindNavButton(R.id.btnHome, AppScreen.HOME)
        bindNavButton(R.id.btnSearch, AppScreen.SEARCH)
        bindNavButton(R.id.btnLibrary, AppScreen.LIBRARY)
        bindNavButton(R.id.btnPlayer, AppScreen.PLAYER)
        bindNavButton(R.id.btnSettings, AppScreen.SETTINGS)
    }

    override fun onDestroy() {
        playerWebView?.let(playerBridge::detachFromWebView)
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
        runOnUiThread {
            updatePlayerUi(playerUiState.copy(isLoading = true, errorMessage = null))
        }
    }

    override fun onPageFinished(url: String) {
        runOnUiThread {
            updatePlayerUi(playerUiState.copy(isLoading = false))
            if (currentScreen == AppScreen.SETTINGS) {
                refreshSettingsBody(authGateway.getCurrentState())
            }
        }
    }

    override fun onLoadError(url: String?, errorCode: Int, description: String) {
        runOnUiThread {
            updatePlayerUi(
                playerUiState.copy(
                    isLoading = false,
                    errorMessage = "Load error: $description"
                )
            )
            if (currentScreen == AppScreen.SETTINGS) {
                refreshSettingsBody(authGateway.getCurrentState())
            }
        }
    }

    override fun onSslError(url: String?) {
        runOnUiThread {
            updatePlayerUi(playerUiState.copy(isLoading = false, errorMessage = "Connection error"))
            if (currentScreen == AppScreen.SETTINGS) {
                refreshSettingsBody(authGateway.getCurrentState())
            }
        }
    }

    override fun onLoadingStateChanged(isLoading: Boolean) {
        runOnUiThread {
            updatePlayerUi(playerUiState.copy(isLoading = isLoading, errorMessage = null))
        }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        runOnUiThread {
            updatePlayerUi(playerUiState.copy(isPlaying = isPlaying, isReady = true, errorMessage = null))
        }
    }

    override fun onTrackChanged(trackId: String?, title: String?, artist: String?) {
        runOnUiThread {
            updatePlayerUi(
                playerUiState.copy(
                    trackTitle = title?.takeIf { it.isNotBlank() },
                    artist = artist?.takeIf { it.isNotBlank() },
                    isReady = true,
                    errorMessage = null
                )
            )
        }
    }

    override fun onPlaybackError(errorCode: String, message: String) {
        runOnUiThread {
            updatePlayerUi(playerUiState.copy(isLoading = false, errorMessage = message))
        }
    }

    override fun onReady() {
        runOnUiThread {
            updatePlayerUi(playerUiState.copy(isLoading = false, isReady = true, errorMessage = null))
        }
    }

    private fun bindNavButton(buttonId: Int, target: AppScreen) {
        findViewById<Button>(buttonId).apply {
            setOnClickListener { navigateTo(target) }
            TvFocusStyler.applyNavStyle(this, focusedScale = 1.06f)
        }
    }

    private fun navigateTo(screen: AppScreen) {
        currentScreen = screen
        updateNavSelection()
        updateTitle(screen)
        contentFrame.removeAllViews()

        val view = when (screen) {
            AppScreen.HOME -> renderLoadingState("Home")
            AppScreen.SEARCH -> renderLoadingState("Search")
            AppScreen.LIBRARY -> renderLoadingState("Library")
            AppScreen.PLAYER -> buildPlayerView()
            AppScreen.SETTINGS -> buildSettingsView()
        }

        contentFrame.addView(view)
        view.post { view.findFocus()?.requestFocus() ?: view.requestFocus() }
        requestContentFor(screen, authGateway.getCurrentState())
    }

    private fun updateTitle(screen: AppScreen) {
        titleView.text = when (screen) {
            AppScreen.HOME -> "Cloud Player"
            AppScreen.SEARCH -> "Search"
            AppScreen.LIBRARY -> "Your Library"
            AppScreen.PLAYER -> "Now Playing"
            AppScreen.SETTINGS -> "Settings"
        }
    }

    private fun updateNavSelection() {
        listOf(
            R.id.btnHome to AppScreen.HOME,
            R.id.btnSearch to AppScreen.SEARCH,
            R.id.btnLibrary to AppScreen.LIBRARY,
            R.id.btnPlayer to AppScreen.PLAYER,
            R.id.btnSettings to AppScreen.SETTINGS
        ).forEach { (buttonId, screen) ->
            findViewById<Button>(buttonId).isSelected = currentScreen == screen
        }
    }

    private fun renderLoadingState(screenName: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(48), dp(100), dp(48), dp(100))

            val loadingText = TextView(this@MainActivity).apply {
                text = "Loading $screenName..."
                setTextColor(0xFF666666.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
            }
            addView(loadingText)
        }
    }

    private fun buildPlayerView(): View {
        playerWebView?.let(playerBridge::detachFromWebView)
        val webView = WebView(this)
        webView.setBackgroundColor(Color.BLACK)
        webHost.configure(webView)
        playerBridge.attachToWebView(webView)
        playerWebView = webView

        val playerRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF050505.toInt())
        }

        // Now playing header
        val headerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A0A0A.toInt())
            setPadding(dp(32), dp(24), dp(32), dp(24))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // State indicator
        playerStateView = TextView(this).apply {
            text = "Loading"
            setTextColor(0xFFFF6600.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            letterSpacing = 0.05f
        }
        headerContainer.addView(playerStateView)

        // Track title
        playerTrackView = TextView(this).apply {
            text = "Preparing player..."
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(8), 0, dp(4))
        }
        headerContainer.addView(playerTrackView)

        // Artist name
        playerArtistView = TextView(this).apply {
            text = ""
            setTextColor(0xFFAAAAAA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 1
        }
        headerContainer.addView(playerArtistView)

        // Error message
        playerErrorView = TextView(this).apply {
            text = ""
            setTextColor(0xFFFF6666.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 2
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }
        headerContainer.addView(playerErrorView)

        playerRoot.addView(headerContainer)

        // WebView container with dark frame
        val webViewContainer = FrameLayout(this).apply {
            setBackgroundColor(0xFF0D0D0D.toInt())
            setPadding(dp(32), dp(16), dp(32), dp(24))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val webViewFrame = FrameLayout(this).apply {
            setBackgroundColor(0xFF111111.toInt())
            addView(webView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        webViewContainer.addView(webViewFrame)
        playerRoot.addView(webViewContainer)

        updatePlayerUi(PlayerUiState(isLoading = true))
        webHost.loadPlayer(webView)
        return playerRoot
    }

    private fun updatePlayerUi(nextState: PlayerUiState) {
        playerUiState = nextState

        val stateLabel = when {
            nextState.errorMessage != null -> "ERROR"
            nextState.isLoading -> "LOADING"
            nextState.isPlaying -> "PLAYING"
            nextState.isReady -> "PAUSED"
            else -> "IDLE"
        }

        val trackTitle = nextState.trackTitle ?: when {
            nextState.isLoading -> "Preparing player..."
            nextState.isReady -> "Ready to play"
            else -> "Select a track to play"
        }

        playerStateView?.text = stateLabel
        playerTrackView?.text = trackTitle
        playerArtistView?.text = nextState.artist ?: ""
        playerArtistView?.visibility = if (nextState.artist.isNullOrBlank()) View.GONE else View.VISIBLE

        if (nextState.errorMessage != null) {
            playerErrorView?.text = nextState.errorMessage
            playerErrorView?.visibility = View.VISIBLE
        } else {
            playerErrorView?.visibility = View.GONE
        }
    }

    private fun buildSettingsView(): View {
        val state = authGateway.getCurrentState()
        val appInfo = buildDiagnosticsBody(state)

        val diagnosticsModel = DiagnosticsScreenFactory.create(
            onBootstrapSession = { authGateway.bootstrapSession() },
            onPollSession = { authGateway.pollSession() },
            onRefreshSession = { authGateway.refreshSession() },
            onDebugAuthenticateSession = if (BuildConfig.DEBUG) {
                { authGateway.debugAuthenticateSession() }
            } else {
                null
            },
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
                showContentMessage(screen, "Connecting...")
            }
            return
        }

        when (screen) {
            AppScreen.HOME -> contentRepository.loadFeed(sessionId) { nextState ->
                handleContentState(AppScreen.HOME, nextState, "No content available")
            }
            AppScreen.SEARCH -> contentRepository.search(sessionId, "") { nextState ->
                handleContentState(AppScreen.SEARCH, nextState, "No results")
            }
            AppScreen.LIBRARY -> contentRepository.loadLibrary(sessionId) { nextState ->
                handleContentState(AppScreen.LIBRARY, nextState, "Library is empty")
            }
            else -> Unit
        }
    }

    private fun handleContentState(screen: AppScreen, state: ContentLoadState, emptyMessage: String) {
        if (currentScreen != screen) return

        when (state) {
            ContentLoadState.Loading -> {
                // Already showing loading state
            }
            ContentLoadState.Empty -> {
                showContentMessage(screen, emptyMessage)
            }
            is ContentLoadState.Success -> {
                renderContentScreen(screen, state)
            }
            is ContentLoadState.Error -> {
                showContentMessage(screen, "Unable to load content\n${state.message}")
            }
        }
    }

    private fun showContentMessage(screen: AppScreen, message: String) {
        if (currentScreen != screen) return
        contentFrame.removeAllViews()
        contentFrame.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(48), dp(100), dp(48), dp(100))

            val messageView = TextView(this@MainActivity).apply {
                text = message
                setTextColor(0xFF666666.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
            }
            addView(messageView)
        })
    }

    private fun renderContentScreen(screen: AppScreen, state: ContentLoadState.Success) {
        val model = when (screen) {
            AppScreen.HOME -> HomeScreenFactory.create("", state.sections)
            AppScreen.SEARCH -> SearchScreenFactory.create("", state.sections)
            AppScreen.LIBRARY -> LibraryScreenFactory.create("", state.sections)
            else -> return
        }
        contentFrame.removeAllViews()
        contentFrame.addView(screenRenderer.render(model))
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics
    ).toInt()

    private fun buildDiagnosticsBody(state: AuthSessionState): String {
        val webViewState = webHost.getDiagnosticState()

        val rows = listOf(
            "App Version: ${BuildConfig.VERSION_NAME}",
            "Build: ${if (BuildConfig.DEBUG) "Debug" else "Release"}",
            "Backend: ${BuildConfig.API_BASE_URL}",
            "",
            "Session: ${state.sessionId ?: "none"}",
            "Status: ${state.phase.label}",
            "Authenticated: ${state.isAuthenticated}",
            "",
            "WebView Hardened: ${webViewState.hardeningEnabled}",
            "Controlled Host: ${webViewState.entryUrl}",
            "Last Error: ${webViewState.lastError ?: "none"}"
        )
        return rows.joinToString(separator = "\n")
    }

    data class PlayerUiState(
        val isLoading: Boolean = false,
        val isReady: Boolean = false,
        val isPlaying: Boolean = false,
        val trackTitle: String? = null,
        val artist: String? = null,
        val errorMessage: String? = null
    )
}
