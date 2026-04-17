package com.neilpontecorvo.soundcloudfiretv.app

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
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
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ActionSpec
import com.neilpontecorvo.soundcloudfiretv.core.navigation.AppScreen
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ContentCardSpec
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ContentCardSelectionListener
import com.neilpontecorvo.soundcloudfiretv.core.navigation.FocusCoordinator
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ScreenRenderer
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ScreenViewModel
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
    PlayerBridge.BridgeEventListener,
    ContentCardSelectionListener {

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

    // Selected content context for player
    private var selectedCard: ContentCardSpec? = null
    private var detailReturnScreen: AppScreen? = null
    private var currentSearchQuery: String = ""

    // WebView diagnostic state for display
    private var lastBlockedNavigation: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        titleView = findViewById(R.id.screenTitle)
        contentFrame = findViewById(R.id.contentFrame)
        focusCoordinator = FocusCoordinator(this)
        screenRenderer = ScreenRenderer(this, this)
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

        detailReturnScreen?.let { returnScreen ->
            if (action == RemoteAction.BACK) {
                navigateTo(returnScreen)
                return true
            }
        }

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

    // ContentCardSelectionListener implementation
    override fun onCardSelected(card: ContentCardSpec) {
        selectedCard = card
        when (card.eyebrow.lowercase()) {
            "playlist", "station", "album" -> showCollectionDetail(card)
            else -> navigateTo(AppScreen.PLAYER)
        }
    }

    private fun showCollectionDetail(card: ContentCardSpec) {
        val returnScreen = currentScreen
        detailReturnScreen = returnScreen
        titleView.text = card.title
        updateNavSelection()

        val detailLines = listOfNotNull(
            card.subtitle.takeIf { it.isNotBlank() && it != "Ready to play" },
            card.metadata
        )

        val actions = mutableListOf<ActionSpec>()
        if (!card.webUrl.isNullOrBlank()) {
            actions.add(ActionSpec("Play") {
                selectedCard = card
                navigateTo(AppScreen.PLAYER)
            })
        }
        actions.add(ActionSpec("Back") { navigateTo(returnScreen) })

        val model = ScreenViewModel(
            title = card.title,
            body = detailLines.joinToString(separator = "\n").ifBlank {
                if (card.webUrl.isNullOrBlank()) {
                    "Playback is unavailable for this item."
                } else {
                    "Choose Play to open this item."
                }
            },
            actions = actions
        )

        contentFrame.removeAllViews()
        contentFrame.addView(screenRenderer.render(model))
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
        detailReturnScreen = null
        currentScreen = screen
        updateNavSelection()
        updateTitle(screen)
        contentFrame.removeAllViews()

        val view = when (screen) {
            AppScreen.HOME -> renderLoadingState("Home")
            AppScreen.SEARCH -> buildSearchScreen()
            AppScreen.LIBRARY -> renderLoadingState("Library")
            AppScreen.PLAYER -> buildPlayerView()
            AppScreen.SETTINGS -> buildSettingsView()
        }

        contentFrame.addView(view)
        view.post {
            val focused = contentFrame.findFocus()
            if (focused == null || focused == view || focused == contentFrame) {
                view.requestFocus()
            }
        }
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

    private fun buildSearchScreen(): View {
        val searchRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(16), dp(4), dp(16))
        }

        // Search input row
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(20))
        }

        val searchInput = EditText(this).apply {
            hint = "Search tracks, artists, playlists..."
            setHintTextColor(0xFF666666.toInt())
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setBackgroundResource(R.drawable.tv_focusable_background)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isFocusable = true
            isFocusableInTouchMode = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
            setText(currentSearchQuery)

            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    performSearch(text.toString())
                    true
                } else {
                    false
                }
            }
        }
        TvFocusStyler.apply(searchInput, focusedScale = 1.02f)
        searchRow.addView(searchInput)

        val searchButton = Button(this).apply {
            text = "Search"
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.tv_focusable_background)
            setPadding(dp(24), 0, dp(24), 0)
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(48)
            ).apply { marginStart = dp(12) }

            setOnClickListener {
                performSearch(searchInput.text.toString())
            }
        }
        TvFocusStyler.apply(searchButton, focusedScale = 1.05f)
        searchRow.addView(searchButton)

        searchRoot.addView(searchRow)

        // Results container
        val resultsContainer = FrameLayout(this).apply {
            id = R.id.contentFrame + 1000 // Unique ID for results
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        searchRoot.addView(resultsContainer)

        // Store reference for updating results
        searchRoot.tag = resultsContainer
        searchRoot.post { searchInput.requestFocus() }

        return searchRoot
    }

    private fun performSearch(query: String) {
        currentSearchQuery = query.trim()
        val sessionId = authGateway.getCurrentState().sessionId ?: return

        // Find results container
        val resultsContainer = (contentFrame.getChildAt(0) as? LinearLayout)?.tag as? FrameLayout
            ?: return

        // Show loading
        resultsContainer.removeAllViews()
        resultsContainer.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(48), dp(60), dp(48), dp(60))
            addView(TextView(this@MainActivity).apply {
                text = if (currentSearchQuery.isBlank()) "Enter a search term" else "Searching..."
                setTextColor(0xFF666666.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
            })
        })

        if (currentSearchQuery.isBlank()) return

        contentRepository.search(sessionId, currentSearchQuery) { state ->
            runOnUiThread {
                if (currentScreen != AppScreen.SEARCH) return@runOnUiThread
                updateSearchResults(resultsContainer, state)
            }
        }
    }

    private fun updateSearchResults(container: FrameLayout, state: ContentLoadState) {
        container.removeAllViews()

        when (state) {
            ContentLoadState.Loading -> {
                container.addView(buildCenteredMessage("Searching..."))
            }
            ContentLoadState.Empty -> {
                container.addView(buildCenteredMessage("No results found for \"$currentSearchQuery\""))
            }
            is ContentLoadState.Success -> {
                val model = SearchScreenFactory.create("", state.sections)
                container.addView(screenRenderer.render(model))
            }
            is ContentLoadState.Error -> {
                container.addView(buildCenteredMessage("Search failed: ${state.message}"))
            }
        }
    }

    private fun buildCenteredMessage(message: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(48), dp(60), dp(48), dp(60))
            addView(TextView(this@MainActivity).apply {
                text = message
                setTextColor(0xFF666666.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
            })
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

        // Now playing header - show selected content context
        val headerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A0A0A.toInt())
            setPadding(dp(32), dp(24), dp(32), dp(24))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        playerStateView = TextView(this).apply {
            text = "LOADING"
            setTextColor(0xFFFF6600.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            letterSpacing = 0.05f
        }
        headerContainer.addView(playerStateView)

        // Use selected card context for initial display
        val initialTitle = selectedCard?.title ?: "Preparing player..."
        val initialArtist = selectedCard?.subtitle

        playerTrackView = TextView(this).apply {
            text = initialTitle
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(8), 0, dp(4))
        }
        headerContainer.addView(playerTrackView)

        playerArtistView = TextView(this).apply {
            text = initialArtist ?: ""
            setTextColor(0xFFAAAAAA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 1
            visibility = if (initialArtist.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        headerContainer.addView(playerArtistView)

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

        // WebView container
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

        // Initialize player UI state with selected content
        updatePlayerUi(PlayerUiState(
            isLoading = true,
            trackTitle = selectedCard?.title,
            artist = selectedCard?.subtitle?.takeIf { it != "Ready to play" }
        ))

        val contentUrl = selectedCard?.webUrl
        if (selectedCard != null && contentUrl.isNullOrBlank()) {
            updatePlayerUi(
                playerUiState.copy(
                    isLoading = false,
                    errorMessage = "Playback is unavailable for this item."
                )
            )
        } else {
            webHost.loadPlayer(webView, contentUrl)
            webHost.getDiagnosticState().lastError?.let { error ->
                updatePlayerUi(playerUiState.copy(isLoading = false, errorMessage = error))
            }
        }
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
                selectedCard = null
            },
            appInfo = appInfo
        )

        val settingsModel = SettingsScreenFactory.create().copy(
            title = "Settings & Diagnostics",
            body = appInfo,
            actions = diagnosticsModel.actions
        )

        return renderSettingsScreen(settingsModel)
    }

    private fun renderSettingsScreen(model: ScreenViewModel): View {
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            isVerticalScrollBarEnabled = true
            setBackgroundColor(0xFF050505.toInt())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
            isFocusable = false
            isFocusableInTouchMode = false
        }
        scrollView.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(TextView(this).apply {
            text = model.title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        })

        val actionGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = false
            isFocusableInTouchMode = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }
        root.addView(actionGrid)

        val actionButtons = model.actions.map { action ->
            buildSettingsActionButton(action)
        }
        applySettingsFocusGraph(actionButtons)

        actionButtons.chunked(2).forEach { rowButtons ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START
                isFocusable = false
                isFocusableInTouchMode = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            }
            rowButtons.forEach { row.addView(it) }
            actionGrid.addView(row)
        }

        val body = TextView(this).apply {
            id = R.id.panelBody
            text = model.body
            setTextColor(0xFF999999.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = android.graphics.Typeface.MONOSPACE
            setLineSpacing(dp(4).toFloat(), 1f)
            setBackgroundColor(0xFF0D0D0D.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isFocusable = false
            isFocusableInTouchMode = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(body)

        scrollView.post {
            actionButtons.firstOrNull()?.requestFocus()
        }

        return scrollView
    }

    private fun buildSettingsActionButton(action: ActionSpec): Button {
        return Button(this).apply {
            id = View.generateViewId()
            text = action.label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setBackgroundResource(R.drawable.tv_focusable_background)
            setPadding(dp(16), 0, dp(16), 0)
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            minWidth = dp(240)
            minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginEnd = dp(10)
            }

            setOnClickListener {
                dispatchSettingsAction(action)
            }
            setOnKeyListener { _, keyCode, event ->
                val isSelect = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == KeyEvent.KEYCODE_ENTER ||
                    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                if (!isSelect) {
                    false
                } else {
                    if (event.action == KeyEvent.ACTION_UP) {
                        performClick()
                    }
                    true
                }
            }

            TvFocusStyler.apply(this, focusedScale = 1.06f, onFocusChanged = { hasFocus ->
                if (hasFocus) {
                    Log.d(TAG, "Settings focus: ${action.label}")
                }
            })
        }
    }

    private fun applySettingsFocusGraph(buttons: List<Button>) {
        val columns = 2
        buttons.forEachIndexed { index, button ->
            val row = index / columns
            val col = index % columns
            val leftIndex = index - 1
            val rightIndex = index + 1
            val upIndex = index - columns
            val downIndex = index + columns

            button.nextFocusLeftId = if (col > 0 && leftIndex >= 0) {
                buttons[leftIndex].id
            } else {
                button.id
            }
            button.nextFocusRightId = if (col < columns - 1 && rightIndex < buttons.size && rightIndex / columns == row) {
                buttons[rightIndex].id
            } else {
                button.id
            }
            button.nextFocusUpId = if (upIndex >= 0) {
                buttons[upIndex].id
            } else {
                button.id
            }
            button.nextFocusDownId = if (downIndex < buttons.size) {
                buttons[downIndex].id
            } else {
                button.id
            }
        }
    }

    private fun dispatchSettingsAction(action: ActionSpec) {
        Log.i(TAG, "Settings action selected: ${action.label}")
        action.onClick.invoke()
        refreshSettingsBody(authGateway.getCurrentState())
    }

    private fun refreshSettingsBody(state: AuthSessionState) {
        if (currentScreen != AppScreen.SETTINGS) return
        contentFrame.findViewById<TextView>(R.id.panelBody)?.text = buildDiagnosticsBody(state)
    }

    private fun refreshContentForCurrentScreen(state: AuthSessionState) {
        if (detailReturnScreen != null) return
        if (currentScreen == AppScreen.HOME || currentScreen == AppScreen.LIBRARY) {
            requestContentFor(currentScreen, state)
        }
    }

    private fun requestContentFor(screen: AppScreen, state: AuthSessionState) {
        val sessionId = state.sessionId
        if (sessionId == null) {
            if (screen == AppScreen.HOME || screen == AppScreen.LIBRARY) {
                showContentMessage(screen, "Connecting...")
            }
            return
        }

        when (screen) {
            AppScreen.HOME -> contentRepository.loadFeed(sessionId) { nextState ->
                handleContentState(AppScreen.HOME, nextState, "No content available")
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
            ContentLoadState.Loading -> { /* Already showing loading */ }
            ContentLoadState.Empty -> showContentMessage(screen, emptyMessage)
            is ContentLoadState.Success -> renderContentScreen(screen, state)
            is ContentLoadState.Error -> showContentMessage(screen, "Unable to load content\n${state.message}")
        }
    }

    private fun showContentMessage(screen: AppScreen, message: String) {
        if (currentScreen != screen) return
        contentFrame.removeAllViews()
        contentFrame.addView(buildCenteredMessage(message))
    }

    private fun renderContentScreen(screen: AppScreen, state: ContentLoadState.Success) {
        val model = when (screen) {
            AppScreen.HOME -> HomeScreenFactory.create("", state.sections)
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
            "Last Error: ${webViewState.lastError ?: "none"}",
            "",
            "Selected: ${selectedCard?.title ?: "none"}"
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

    companion object {
        private const val TAG = "MainActivity"
    }
}
