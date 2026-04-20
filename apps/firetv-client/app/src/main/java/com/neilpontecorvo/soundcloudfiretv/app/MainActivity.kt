package com.neilpontecorvo.soundcloudfiretv.app

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
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
import com.neilpontecorvo.soundcloudfiretv.auth.AuthSessionPhase
import com.neilpontecorvo.soundcloudfiretv.auth.AuthSessionState
import com.neilpontecorvo.soundcloudfiretv.auth.SessionPersistence
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
    private lateinit var sessionPersistence: SessionPersistence
    private lateinit var contentRepository: ContentRepository
    private var userInSettings: Boolean = false

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
        routeByAuthState(state)
    }

    private var currentScreen: AppScreen = AppScreen.HOME
    private var playerWebView: WebView? = null
    private var lastLoadedPlayableId: String? = null
    private var playerStateView: TextView? = null
    private var playerTrackView: TextView? = null
    private var playerArtistView: TextView? = null
    private var playerErrorView: TextView? = null
    private var playerUiState = PlayerUiState()
    private val playerTimeoutHandler = Handler(Looper.getMainLooper())
    private var playerTimeoutRunnable: Runnable? = null
    private var playerLoadToken: Int = 0
    private var hasLoggedFirstBridgeEvent: Boolean = false

    // Selected content context for player
    private var selectedCard: ContentCardSpec? = null
    private var playerQueueCards: List<ContentCardSpec> = emptyList()
    private var detailReturnScreen: AppScreen? = null
    private var currentSearchQuery: String = ""

    // WebView diagnostic state for display
    private var lastBlockedNavigation: String? = null
    private var providerSignInPollRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        titleView = findViewById(R.id.screenTitle)
        contentFrame = findViewById(R.id.contentFrame)
        focusCoordinator = FocusCoordinator(this)
        screenRenderer = ScreenRenderer(this, this)
        apiClient = DeviceSessionApiClient(BuildConfig.API_BASE_URL)
        sessionPersistence = SessionPersistence(this)
        authGateway = ApiBackedAuthGateway(
            apiClient = apiClient,
            deviceName = android.os.Build.MODEL.ifBlank { "Fire TV" },
            appVersion = BuildConfig.VERSION_NAME,
            sessionPersistence = sessionPersistence
        )
        contentRepository = ContentRepository(apiClient)
        authGateway.addListener(authStateListener)

        setupNavigation()
        // Choose initial screen from the already-persisted state so an
        // authenticated cold start renders HOME directly, while an unknown
        // state parks on LOGIN_REQUIRED until the backend probe completes.
        val startupState = authGateway.getCurrentState()
        navigateTo(if (startupState.isAuthenticated) AppScreen.HOME else AppScreen.LOGIN_REQUIRED)
        authGateway.restoreOrBootstrap()
    }

    private fun setupNavigation() {
        bindNavButton(R.id.btnHome, AppScreen.HOME)
        bindNavButton(R.id.btnSearch, AppScreen.SEARCH)
        bindNavButton(R.id.btnLibrary, AppScreen.LIBRARY)
        bindNavButton(R.id.btnPlayer, AppScreen.PLAYER)
        bindNavButton(R.id.btnSettings, AppScreen.SETTINGS)
    }

    override fun onDestroy() {
        releasePlayerHost(clearSelection = false)
        authGateway.removeListener(authStateListener)
        authGateway.shutdown()
        contentRepository.shutdown()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = RemoteInputHandler.mapKeyCode(event.keyCode)
        if (!action.isTransportAction()) {
            return super.dispatchKeyEvent(event)
        }

        val focused = describeFocusedView()
        Log.i(
            TAG,
            "Raw media key received: key=${KeyEvent.keyCodeToString(event.keyCode)} action=$action eventAction=${event.action} repeat=${event.repeatCount} focus=$focused"
        )

        if (event.action == KeyEvent.ACTION_UP) {
            val consumeUp = canDispatchTransportCommand()
            Log.i(TAG, "Global media key up ${if (consumeUp) "consumed" else "passed"}: action=$action focus=$focused")
            return if (consumeUp) true else super.dispatchKeyEvent(event)
        }

        if (event.action != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event)
        }

        val consumed = handleTransportAction(action, event.keyCode, event.repeatCount, focused)
        Log.i(TAG, "Global media key ${if (consumed) "consumed" else "passed"}: action=$action focus=$focused")
        return if (consumed) true else super.dispatchKeyEvent(event)
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

        if (action == RemoteAction.MENU && currentScreen != AppScreen.SETTINGS) {
            navigateTo(AppScreen.SETTINGS)
            return true
        }

        if (focusCoordinator.handle(action)) {
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun handleTransportAction(
        action: RemoteAction,
        keyCode: Int,
        repeatCount: Int,
        focusedView: String
    ): Boolean {
        val keyName = KeyEvent.keyCodeToString(keyCode)
        val selected = selectedCard
        val activeWebView = playerWebView
        val hasPlayableSelection = !selected?.webUrl.isNullOrBlank()
        val canDispatch = canDispatchTransportCommand()

        Log.i(
            TAG,
            "Transport key received: key=$keyName action=$action screen=$currentScreen activePlayer=${activeWebView != null} selectedId=${selected?.id ?: "none"} focus=$focusedView"
        )

        if (!canDispatch) {
            Log.i(
                TAG,
                "Transport key ignored: reason=${transportBlockReason()} key=$keyName action=$action screen=$currentScreen activePlayer=${activeWebView != null} hasPlayableSelection=$hasPlayableSelection focus=$focusedView"
            )
            return false
        }

        if (repeatCount > 0) {
            Log.d(TAG, "Transport key repeat ignored: key=$keyName action=$action repeat=$repeatCount")
            return true
        }

        if (activeWebView == null) {
            Log.i(TAG, "Transport key ignored: reason=no_active_webview key=$keyName action=$action")
            return false
        }

        when (action) {
            RemoteAction.PLAY -> {
                Log.i(TAG, "Transport dispatch: command=play selectedId=${selected?.id}")
                playerBridge.sendPlay(activeWebView)
            }
            RemoteAction.PAUSE -> {
                Log.i(TAG, "Transport dispatch: command=pause selectedId=${selected?.id}")
                playerBridge.sendPause(activeWebView)
            }
            RemoteAction.PLAY_PAUSE -> {
                Log.i(TAG, "Transport dispatch: command=toggle selectedId=${selected?.id}")
                playerBridge.sendTogglePlayPause(activeWebView)
            }
            RemoteAction.NEXT -> {
                Log.i(TAG, "Transport dispatch: command=next selectedId=${selected?.id}")
                playerBridge.sendNext(activeWebView)
            }
            RemoteAction.PREVIOUS -> {
                Log.i(TAG, "Transport dispatch: command=previous selectedId=${selected?.id}")
                playerBridge.sendPrevious(activeWebView)
            }
            RemoteAction.FAST_FORWARD,
            RemoteAction.REWIND -> {
                Log.i(
                    TAG,
                    "Transport command unsupported: key=$keyName action=$action reason=no reliable seek bridge currently defined"
                )
            }
            else -> return false
        }

        return true
    }

    private fun RemoteAction.isTransportAction(): Boolean = when (this) {
        RemoteAction.PLAY,
        RemoteAction.PAUSE,
        RemoteAction.PLAY_PAUSE,
        RemoteAction.NEXT,
        RemoteAction.PREVIOUS,
        RemoteAction.FAST_FORWARD,
        RemoteAction.REWIND -> true
        else -> false
    }

    private fun canDispatchTransportCommand(): Boolean {
        return currentScreen == AppScreen.PLAYER &&
            playerWebView != null &&
            !selectedCard?.webUrl.isNullOrBlank()
    }

    private fun transportBlockReason(): String = when {
        currentScreen != AppScreen.PLAYER -> "screen_not_player"
        playerWebView == null -> "no_active_player"
        selectedCard?.webUrl.isNullOrBlank() -> "no_playable_selection"
        else -> "unknown"
    }

    private fun describeFocusedView(): String {
        val focused = currentFocus ?: return "none"
        val idName = if (focused.id != View.NO_ID) {
            runCatching { resources.getResourceEntryName(focused.id) }.getOrDefault("id_${focused.id}")
        } else {
            "no_id"
        }
        return "${focused.javaClass.simpleName}#$idName"
    }

    // ContentCardSelectionListener implementation
    override fun onCardSelected(card: ContentCardSpec) {
        onCardSelectedFromSection(card, emptyList())
    }

    override fun onCardSelectedFromSection(card: ContentCardSpec, sectionCards: List<ContentCardSpec>) {
        selectedCard = card
        playerQueueCards = sectionCards.filter { !it.webUrl.isNullOrBlank() }
            .ifEmpty { listOfNotNull(card.takeIf { !it.webUrl.isNullOrBlank() }) }
        if (!card.webUrl.isNullOrBlank()) {
            navigateTo(AppScreen.PLAYER)
            return
        }

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
        if (!isPlayerVisibleWithPlayableSelection()) {
            Log.d(TAG, "Ignoring page start outside active player target: ${sanitizeUrlForLog(url)}")
            return
        }
        runOnUiThread {
            updatePlayerUi(playerUiState.copy(isLoading = true, errorMessage = null))
        }
    }

    override fun onPageFinished(url: String) {
        if (!isPlayerVisibleWithPlayableSelection()) {
            Log.d(TAG, "Ignoring page finish outside active player target: ${sanitizeUrlForLog(url)}")
            return
        }
        runOnUiThread {
            updatePlayerUi(playerUiState.copy(isLoading = false))
            snapshotPlayerDom("onPageFinished")
            if (currentScreen == AppScreen.SETTINGS) {
                refreshSettingsBody(authGateway.getCurrentState())
            }
        }
    }

    private fun snapshotPlayerDom(source: String) {
        val wv = playerWebView ?: return
        Log.i(TAG, "DocSnapshot[$source] webView.url=${sanitizeUrlForLog(wv.url)} title='${wv.title}'")
        wv.evaluateJavascript("document.readyState") { v -> Log.i(TAG, "DocSnapshot[$source] readyState=$v") }
        wv.evaluateJavascript("location.href") { v -> Log.i(TAG, "DocSnapshot[$source] location.href=$v") }
        wv.evaluateJavascript(
            "(function(){try{return JSON.stringify({len:document.documentElement.outerHTML.length, head:document.documentElement.outerHTML.slice(0,500)});}catch(e){return 'err:'+e.message;}})()"
        ) { v -> Log.i(TAG, "DocSnapshot[$source] outerHTML=$v") }
        wv.evaluateJavascript(
            "(function(){return JSON.stringify({hasSC:!!window.SC, hasWidget:!!(window.SC&&window.SC.Widget), hasNative:!!window.NativePlayer, hasHost:!!window.FireTvPlayerHost});})()"
        ) { v -> Log.i(TAG, "DocSnapshot[$source] globals=$v") }
    }

    private var loggedWebViewEnvOnce = false
    private fun logWebViewEnvironment() {
        if (loggedWebViewEnvOnce) return
        loggedWebViewEnvOnce = true
        val pkg = runCatching { WebView.getCurrentWebViewPackage() }.getOrNull()
        Log.i(
            TAG,
            "WebView env: manufacturer=${android.os.Build.MANUFACTURER} model=${android.os.Build.MODEL} sdk=${android.os.Build.VERSION.SDK_INT} wvPkg=${pkg?.packageName} wvVer=${pkg?.versionName}"
        )
    }

    override fun onLoadError(url: String?, errorCode: Int, description: String) {
        if (!isPlayerVisibleWithPlayableSelection()) {
            Log.d(TAG, "Ignoring load error outside active player target: ${sanitizeUrlForLog(url)}")
            return
        }
        runOnUiThread {
            cancelPlayerReadyTimeout()
            Log.w(TAG, "Player load failure: code=$errorCode, reason=$description")
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
        if (!isPlayerVisibleWithPlayableSelection()) {
            Log.d(TAG, "Ignoring SSL error outside active player target: ${sanitizeUrlForLog(url)}")
            return
        }
        runOnUiThread {
            cancelPlayerReadyTimeout()
            Log.w(TAG, "Player SSL failure for ${sanitizeUrlForLog(url)}")
            updatePlayerUi(playerUiState.copy(isLoading = false, errorMessage = "Connection error"))
            if (currentScreen == AppScreen.SETTINGS) {
                refreshSettingsBody(authGateway.getCurrentState())
            }
        }
    }

    override fun onLoadingStateChanged(isLoading: Boolean) {
        if (!isPlayerVisibleWithPlayableSelection()) {
            Log.d(TAG, "Ignoring bridge loading event outside active player target")
            return
        }
        logBridgeEvent("loading=$isLoading")
        runOnUiThread {
            updatePlayerUi(playerUiState.copy(isLoading = isLoading, errorMessage = null))
        }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        if (!isPlayerVisibleWithPlayableSelection()) {
            Log.d(TAG, "Ignoring bridge playback event outside active player target")
            return
        }
        logBridgeEvent("playing=$isPlaying")
        cancelPlayerReadyTimeout()
        runOnUiThread {
            updatePlayerUi(playerUiState.copy(isPlaying = isPlaying, isReady = true, errorMessage = null))
        }
    }

    override fun onTrackChanged(trackId: String?, title: String?, artist: String?) {
        if (!isPlayerVisibleWithPlayableSelection()) {
            Log.d(TAG, "Ignoring bridge track event outside active player target")
            return
        }
        logBridgeEvent("track")
        cancelPlayerReadyTimeout()
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
        if (!isPlayerVisibleWithPlayableSelection()) {
            Log.d(TAG, "Ignoring bridge error outside active player target: code=$errorCode")
            return
        }
        logBridgeEvent("error=$errorCode")
        cancelPlayerReadyTimeout()
        runOnUiThread {
            Log.w(TAG, "Player bridge failure: code=$errorCode, reason=$message")
            updatePlayerUi(playerUiState.copy(isLoading = false, errorMessage = message))
        }
    }

    override fun onReady() {
        if (!isPlayerVisibleWithPlayableSelection()) {
            Log.d(TAG, "Ignoring bridge ready event outside active player target")
            return
        }
        logBridgeEvent("ready")
        cancelPlayerReadyTimeout()
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
        val leavingPlayer = currentScreen == AppScreen.PLAYER && screen != AppScreen.PLAYER
        if (leavingPlayer) {
            releasePlayerHost(clearSelection = false)
        }
        if (screen != AppScreen.LOGIN_REQUIRED) {
            stopProviderSignInPolling()
        }

        detailReturnScreen = null
        currentScreen = screen
        updateNavSelection()
        updateTitle(screen)
        contentFrame.removeAllViews()

        userInSettings = screen == AppScreen.SETTINGS

        val view = when (screen) {
            AppScreen.HOME -> renderLoadingState("Home")
            AppScreen.SEARCH -> buildSearchScreen()
            AppScreen.LIBRARY -> renderLoadingState("Library")
            AppScreen.PLAYER -> buildPlayerView()
            AppScreen.SETTINGS -> buildSettingsView()
            AppScreen.LOGIN_REQUIRED -> buildLoginRequiredView(authGateway.getCurrentState())
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
            AppScreen.LOGIN_REQUIRED -> "Sign In Required"
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
        val selected = selectedCard
        val contentUrl = selected?.webUrl?.takeIf { it.isNotBlank() }

        if (selected == null) {
            releasePlayerHost(clearSelection = false)
            playerUiState = PlayerUiState()
            return buildPlayerIdleView(
                title = "No track selected",
                message = "Choose a track from Home, Search, or Library to start playback."
            )
        }

        if (contentUrl == null) {
            releasePlayerHost(clearSelection = false)
            playerUiState = PlayerUiState(
                trackTitle = selected.title,
                artist = selected.subtitle.takeIf { it != "Ready to play" }
            )
            return buildPlayerIdleView(
                title = selected.title,
                message = "Playback is unavailable for this item."
            )
        }

        val existingWebView = playerWebView
        val reuseExisting = existingWebView != null && lastLoadedPlayableId == selected.id
        val webView: WebView
        if (reuseExisting && existingWebView != null) {
            Log.i(TAG, "Player composed: WebView reused for id=${selected.id}")
            (existingWebView.parent as? ViewGroup)?.removeView(existingWebView)
            webView = existingWebView
        } else {
            if (existingWebView != null) {
                Log.i(TAG, "Player composed: selection changed ($lastLoadedPlayableId -> ${selected.id}); releasing prior WebView")
            } else {
                Log.i(TAG, "Player composed: no prior WebView; creating new for id=${selected.id}")
            }
            releasePlayerHost(clearSelection = false)
            val fresh = WebView(this)
            fresh.setBackgroundColor(Color.BLACK)
            webHost.configure(fresh)
            fresh.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    val level = consoleMessage?.messageLevel()?.name ?: "LOG"
                    val msg = consoleMessage?.message() ?: ""
                    val src = consoleMessage?.sourceId() ?: ""
                    val line = consoleMessage?.lineNumber() ?: -1
                    Log.w(TAG, "WebConsole[$level] $msg (src=$src:$line)")
                    return true
                }
            }
            playerBridge.attachToWebView(fresh)
            Log.i(TAG, "WebView created for id=${selected.id}")
            playerWebView = fresh
            hasLoggedFirstBridgeEvent = false
            webView = fresh
        }

        Log.i(
            TAG,
            "Selected playable content: id=${selected.id}, title=${selected.title}, webUrl=${sanitizeUrlForLog(contentUrl)}, reused=$reuseExisting"
        )

        val playerRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF050505.toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ── TOP REGION (40% of screen): compact header + WebView ────────────
        val topRegion = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF050505.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                2f   // weight 2 of 5 total = 40%
            )
        }

        // Compact now-playing header
        val headerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A0A0A.toInt())
            setPadding(dp(32), dp(14), dp(32), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        playerStateView = TextView(this).apply {
            text = "LOADING"
            setTextColor(0xFFFF6600.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            letterSpacing = 0.05f
        }
        headerContainer.addView(playerStateView)

        val initialTitle = selected.title
        val initialArtist = selected.subtitle.takeIf { it != "Ready to play" }

        playerTrackView = TextView(this).apply {
            text = initialTitle
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, dp(2))
        }
        headerContainer.addView(playerTrackView)

        playerArtistView = TextView(this).apply {
            text = initialArtist ?: ""
            setTextColor(0xFFAAAAAA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 1
            visibility = if (initialArtist.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        headerContainer.addView(playerArtistView)

        playerErrorView = TextView(this).apply {
            text = ""
            setTextColor(0xFFFF6666.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
            visibility = View.GONE
            setPadding(0, dp(4), 0, 0)
        }
        headerContainer.addView(playerErrorView)

        topRegion.addView(headerContainer)

        val webViewFrame = FrameLayout(this).apply {
            setBackgroundColor(0xFF111111.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(webView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        topRegion.addView(webViewFrame)
        playerRoot.addView(topRegion)

        // ── BOTTOM REGION (60% of screen): native scrollable track list ──────
        val bottomRegion = ScrollView(this).apply {
            setBackgroundColor(0xFF0A0A0A.toInt())
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                3f   // weight 3 of 5 total = 60%
            )
        }

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(8), dp(32), dp(32))
        }
        bottomRegion.addView(listContainer)

        if (playerQueueCards.isNotEmpty()) {
            val queueHeader = TextView(this).apply {
                text = "Up Next"
                setTextColor(0xFF888888.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                letterSpacing = 0.08f
                setPadding(0, dp(8), 0, dp(8))
            }
            listContainer.addView(queueHeader)

            val queueRows = mutableListOf<View>()
            playerQueueCards.forEach { queueCard ->
                val isActive = queueCard.id == selected.id
                val row = buildQueueRow(queueCard, isActive) {
                    if (queueCard.id != selectedCard?.id) {
                        selectedCard = queueCard
                        playerQueueCards = playerQueueCards  // preserve queue
                        navigateTo(AppScreen.PLAYER)
                    }
                }
                queueRows.add(row)
                listContainer.addView(row)
            }

            // Wire D-pad chaining within list rows
            for (i in queueRows.indices) {
                queueRows[i].nextFocusUpId =
                    if (i > 0) queueRows[i - 1].id else queueRows[i].id
                queueRows[i].nextFocusDownId =
                    if (i < queueRows.lastIndex) queueRows[i + 1].id else queueRows[i].id
            }

            // Give focus to the active row on first compose
            bottomRegion.post {
                queueRows.getOrNull(playerQueueCards.indexOfFirst { it.id == selected.id })
                    ?.requestFocus()
            }
        }

        playerRoot.addView(bottomRegion)

        // Initialize player UI state with selected content
        updatePlayerUi(PlayerUiState(
            isLoading = true,
            trackTitle = selected.title,
            artist = initialArtist
        ))

        if (reuseExisting) {
            Log.i(TAG, "loadPlayer skipped: already loaded ${selected.id}")
            return playerRoot
        }

        Log.i(TAG, "Starting Player load for ${selected.id}")
        logWebViewEnvironment()
        Log.i(TAG, "Player load method: webView.loadData(mime=text/html; charset=utf-8, no base URL); allowlist entry=${webHost.getEntryUrl()}")
        val didStartLoad = webHost.loadPlayer(webView, contentUrl)
        Log.i(TAG, "Post-load webView.url snapshot: ${sanitizeUrlForLog(webView.url)}")
        if (didStartLoad) {
            lastLoadedPlayableId = selected.id
            startPlayerReadyTimeout(selected, contentUrl)
            webHost.getDiagnosticState().lastError?.let { error ->
                updatePlayerUi(playerUiState.copy(isLoading = false, errorMessage = error))
            }
        } else {
            val failureReason = webHost.getDiagnosticState().lastError ?: "Player target was rejected."
            Log.w(TAG, "Player load did not start: $failureReason")
            updatePlayerUi(playerUiState.copy(isLoading = false, errorMessage = failureReason))
        }
        return playerRoot
    }

    private fun buildQueueRow(card: ContentCardSpec, isActive: Boolean, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(if (isActive) 0xFF1E1E1E.toInt() else Color.TRANSPARENT)
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, hasFocus ->
                v.setBackgroundColor(
                    when {
                        hasFocus -> 0xFF2E2E2E.toInt()
                        isActive -> 0xFF1E1E1E.toInt()
                        else -> Color.TRANSPARENT
                    }
                )
            }
        }

        val activeBar = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(3), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                rightMargin = dp(12)
            }
            setBackgroundColor(if (isActive) 0xFFFF6600.toInt() else Color.TRANSPARENT)
        }
        row.addView(activeBar)

        val textBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        textBlock.addView(TextView(this).apply {
            text = card.title
            setTextColor(if (isActive) Color.WHITE else 0xFFCCCCCC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            if (isActive) setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })

        val artist = card.subtitle.takeIf { it.isNotBlank() && it != "Ready to play" }
        if (artist != null) {
            textBlock.addView(TextView(this).apply {
                text = artist
                setTextColor(0xFF777777.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }
        row.addView(textBlock)

        if (card.metadata != null) {
            row.addView(TextView(this).apply {
                text = card.metadata
                setTextColor(0xFF555555.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(12), 0, 0, 0)
            })
        }

        return row
    }

    private fun buildPlayerIdleView(title: String, message: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF050505.toInt())
            setPadding(dp(48), dp(60), dp(48), dp(60))

            addView(TextView(this@MainActivity).apply {
                text = "IDLE"
                setTextColor(0xFF777777.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                letterSpacing = 0.05f
                gravity = Gravity.CENTER
            })

            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(10), 0, dp(6))
            })

            addView(TextView(this@MainActivity).apply {
                text = message
                setTextColor(0xFFAAAAAA.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                gravity = Gravity.CENTER
                maxLines = 2
            })
        }
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

        val fallbackTitle = selectedCard?.title
        val fallbackArtist = selectedCard?.subtitle?.takeIf { it != "Ready to play" }

        val trackTitle = nextState.trackTitle ?: fallbackTitle ?: when {
            nextState.isLoading -> "Preparing player..."
            nextState.isReady -> "Ready to play"
            else -> "Select a track to play"
        }

        playerStateView?.text = stateLabel
        playerTrackView?.text = trackTitle
        val artist = nextState.artist ?: fallbackArtist
        playerArtistView?.text = artist ?: ""
        playerArtistView?.visibility = if (artist.isNullOrBlank()) View.GONE else View.VISIBLE

        if (nextState.errorMessage != null) {
            playerErrorView?.text = nextState.errorMessage
            playerErrorView?.visibility = View.VISIBLE
        } else {
            playerErrorView?.visibility = View.GONE
        }
    }

    private fun isPlayerVisibleWithPlayableSelection(): Boolean {
        return currentScreen == AppScreen.PLAYER && !selectedCard?.webUrl.isNullOrBlank()
    }

    private fun releasePlayerHost(clearSelection: Boolean) {
        cancelPlayerReadyTimeout()
        playerWebView?.let { webView ->
            runCatching {
                webView.stopLoading()
                playerBridge.detachFromWebView(webView)
                webHost.clearSession(webView)
                webView.destroy()
            }.onFailure { error ->
                Log.w(TAG, "Failed to release player WebView cleanly: ${error.message}")
            }
        }
        Log.i(TAG, "Player disposed (lastLoadedId=$lastLoadedPlayableId)")
        playerWebView = null
        lastLoadedPlayableId = null
        playerQueueCards = emptyList()
        playerStateView = null
        playerTrackView = null
        playerArtistView = null
        playerErrorView = null
        playerUiState = PlayerUiState()
        hasLoggedFirstBridgeEvent = false
        playerLoadToken += 1
        if (clearSelection) {
            selectedCard = null
        }
    }

    private fun startPlayerReadyTimeout(card: ContentCardSpec, contentUrl: String) {
        cancelPlayerReadyTimeout()
        val token = ++playerLoadToken
        val timeout = Runnable {
            if (token != playerLoadToken || !isPlayerVisibleWithPlayableSelection()) return@Runnable
            if (playerUiState.isReady || playerUiState.errorMessage != null) return@Runnable

            val reason = "Player did not report ready within 15 seconds."
            Log.w(
                TAG,
                "Player timeout: id=${card.id}, title=${card.title}, webUrl=${sanitizeUrlForLog(contentUrl)}, reason=$reason"
            )
            updatePlayerUi(playerUiState.copy(isLoading = false, errorMessage = reason))
        }
        playerTimeoutRunnable = timeout
        playerTimeoutHandler.postDelayed(timeout, PLAYER_READY_TIMEOUT_MS)
    }

    private fun cancelPlayerReadyTimeout() {
        playerTimeoutRunnable?.let(playerTimeoutHandler::removeCallbacks)
        playerTimeoutRunnable = null
    }

    private fun logBridgeEvent(event: String) {
        if (hasLoggedFirstBridgeEvent) {
            Log.d(TAG, "Player bridge event: $event")
            return
        }

        hasLoggedFirstBridgeEvent = true
        Log.i(TAG, "First player bridge event: $event")
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
                stopProviderSignInPolling()
                authGateway.clearSession()
                releasePlayerHost(clearSelection = true)
                lastBlockedNavigation = null
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
                showContentMessage(screen, "Starting local session...")
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

    private fun sanitizeUrlForLog(url: String?): String {
        if (url == null) return "null"
        return try {
            val uri = Uri.parse(url)
            "${uri.scheme}://${uri.host}${uri.path ?: ""}"
        } catch (e: Exception) {
            "[malformed]"
        }
    }

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

    private fun routeByAuthState(state: AuthSessionState) {
        // Respect the user's explicit choice to be in Settings — don't yank
        // them out to LOGIN_REQUIRED while they're inspecting diagnostics or
        // running debug auth. Also don't fight the user's in-progress flow
        // inside LOGIN_REQUIRED itself (where a bootstrap is underway).
        if (userInSettings) return

        runOnUiThread {
            when (state.phase) {
                AuthSessionPhase.AUTHENTICATED -> {
                    stopProviderSignInPolling()
                    if (currentScreen == AppScreen.LOGIN_REQUIRED) {
                        navigateTo(AppScreen.HOME)
                    }
                }
                AuthSessionPhase.AWAITING_AUTH,
                AuthSessionPhase.EXPIRED,
                AuthSessionPhase.ERROR -> {
                    if (currentScreen != AppScreen.LOGIN_REQUIRED &&
                        currentScreen != AppScreen.SETTINGS
                    ) {
                        navigateTo(AppScreen.LOGIN_REQUIRED)
                    } else if (currentScreen == AppScreen.LOGIN_REQUIRED) {
                        refreshLoginRequiredBody(state)
                    }
                }
                AuthSessionPhase.BOOTSTRAPPING,
                AuthSessionPhase.REFRESHING,
                AuthSessionPhase.IDLE -> {
                    if (currentScreen == AppScreen.LOGIN_REQUIRED) {
                        refreshLoginRequiredBody(state)
                    }
                }
            }
        }
    }

    private fun buildLoginRequiredView(state: AuthSessionState): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF050505.toInt())
            setPadding(dp(48), dp(60), dp(48), dp(60))
        }

        root.addView(TextView(this).apply {
            text = "SIGN IN REQUIRED"
            setTextColor(0xFFFF6600.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "Private Cloud TV"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(6))
        })

        val bodyText = TextView(this).apply {
            id = R.id.panelBody
            text = loginRequiredMessage(state)
            setTextColor(0xFFAAAAAA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(24))
        }
        root.addView(bodyText)

        val signInButton = Button(this).apply {
            id = View.generateViewId()
            text = if (state.phase == AuthSessionPhase.AWAITING_AUTH) {
                "Check Sign-In Status"
            } else {
                "Start Provider Sign In"
            }
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.tv_focusable_background)
            setPadding(dp(24), 0, dp(24), 0)
            minWidth = dp(280)
            minHeight = dp(48)
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(48)
            )
            setOnClickListener { startProviderSignIn() }
        }
        TvFocusStyler.apply(signInButton, focusedScale = 1.06f)
        root.addView(signInButton)

        if (BuildConfig.DEBUG) {
            val debugButton = Button(this).apply {
                id = View.generateViewId()
                text = "Use Debug Fallback"
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.tv_focusable_background)
                setPadding(dp(24), 0, dp(24), 0)
                minWidth = dp(240)
                minHeight = dp(48)
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(48)
                ).apply {
                    topMargin = dp(12)
                }
                setOnClickListener {
                    // Debug-only convenience: ensure a fresh backend session
                    // exists, then stamp it authenticated via the debug route.
                    // Real users land in LOGIN_REQUIRED without this button.
                    val phase = authGateway.getCurrentState().phase
                    if (phase == AuthSessionPhase.AWAITING_AUTH) {
                        authGateway.debugAuthenticateSession()
                    } else {
                        authGateway.bootstrapSession()
                        handler.postDelayed({
                            if (authGateway.getCurrentState().phase == AuthSessionPhase.AWAITING_AUTH) {
                                authGateway.debugAuthenticateSession()
                            }
                        }, 400)
                    }
                }
            }
            TvFocusStyler.apply(debugButton, focusedScale = 1.06f)
            root.addView(debugButton)
        }

        root.post { signInButton.requestFocus() }
        return root
    }

    private fun refreshLoginRequiredBody(state: AuthSessionState) {
        if (currentScreen != AppScreen.LOGIN_REQUIRED) return
        contentFrame.findViewById<TextView>(R.id.panelBody)?.text = loginRequiredMessage(state)
    }

    private fun loginRequiredMessage(state: AuthSessionState): String = when (state.phase) {
        AuthSessionPhase.BOOTSTRAPPING -> "Checking your session..."
        AuthSessionPhase.REFRESHING -> "Refreshing your session..."
        AuthSessionPhase.ERROR -> "We couldn't reach the backend.\n${state.lastErrorMessage ?: "Please try again shortly."}"
        AuthSessionPhase.EXPIRED -> "Your session has expired. Please sign in again."
        AuthSessionPhase.AWAITING_AUTH -> providerSignInInstructions(state)
        else -> "Please sign in to continue."
    }

    private fun providerSignInInstructions(state: AuthSessionState): String {
        val signInUrl = state.verificationUriComplete ?: state.verificationUri
        val urlLine = if (signInUrl.isNullOrBlank()) {
            "Waiting for sign-in link..."
        } else {
            "Open this link on your phone or computer:\n$signInUrl"
        }
        val codeLine = state.userCode?.let { "\n\nCode: $it" } ?: ""
        return "$urlLine$codeLine\n\nAfter provider authorization, this TV will continue automatically."
    }

    private fun startProviderSignIn() {
        val state = authGateway.getCurrentState()
        when (state.phase) {
            AuthSessionPhase.AWAITING_AUTH -> authGateway.pollSession()
            AuthSessionPhase.BOOTSTRAPPING,
            AuthSessionPhase.REFRESHING -> Unit
            else -> authGateway.bootstrapSession()
        }
        startProviderSignInPolling()
    }

    private fun startProviderSignInPolling() {
        stopProviderSignInPolling()
        val runnable = object : Runnable {
            override fun run() {
                val state = authGateway.getCurrentState()
                if (currentScreen != AppScreen.LOGIN_REQUIRED || state.phase == AuthSessionPhase.AUTHENTICATED) {
                    stopProviderSignInPolling()
                    return
                }
                if (state.phase == AuthSessionPhase.AWAITING_AUTH) {
                    authGateway.pollSession()
                }
                handler.postDelayed(this, SIGN_IN_POLL_INTERVAL_MS)
            }
        }
        providerSignInPollRunnable = runnable
        handler.postDelayed(runnable, 1000L)
    }

    private fun stopProviderSignInPolling() {
        providerSignInPollRunnable?.let(handler::removeCallbacks)
        providerSignInPollRunnable = null
    }

    private val handler by lazy { Handler(Looper.getMainLooper()) }

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
        private const val PLAYER_READY_TIMEOUT_MS = 15_000L
        private const val SIGN_IN_POLL_INTERVAL_MS = 5_000L
    }
}
