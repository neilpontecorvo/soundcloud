package com.neilpontecorvo.soundcloudfiretv.app

import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.neilpontecorvo.soundcloudfiretv.BuildConfig
import com.neilpontecorvo.soundcloudfiretv.R
import com.neilpontecorvo.soundcloudfiretv.auth.ApiBackedAuthGateway
import com.neilpontecorvo.soundcloudfiretv.auth.AuthSessionPhase
import com.neilpontecorvo.soundcloudfiretv.auth.AuthSessionState
import com.neilpontecorvo.soundcloudfiretv.auth.SessionPersistence
import com.neilpontecorvo.soundcloudfiretv.content.ContentLoadState
import com.neilpontecorvo.soundcloudfiretv.content.ContentRepository
import com.neilpontecorvo.soundcloudfiretv.content.PlaylistDetail
import com.neilpontecorvo.soundcloudfiretv.content.PlaylistLoadState
import com.neilpontecorvo.soundcloudfiretv.core.input.RemoteAction
import com.neilpontecorvo.soundcloudfiretv.core.input.RemoteInputHandler
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ActionSpec
import com.neilpontecorvo.soundcloudfiretv.core.navigation.AppScreen
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ContentCardSelectionListener
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ContentCardSpec
import com.neilpontecorvo.soundcloudfiretv.core.navigation.FocusCoordinator
import com.neilpontecorvo.soundcloudfiretv.core.navigation.GridRenderOptions
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ScreenRenderer
import com.neilpontecorvo.soundcloudfiretv.core.navigation.ScreenViewModel
import com.neilpontecorvo.soundcloudfiretv.core.navigation.TvFocusStyler
import com.neilpontecorvo.soundcloudfiretv.feature.diagnostics.DiagnosticsScreenFactory
import com.neilpontecorvo.soundcloudfiretv.feature.home.HomeScreenFactory
import com.neilpontecorvo.soundcloudfiretv.feature.library.LibraryScreenFactory
import com.neilpontecorvo.soundcloudfiretv.feature.search.SearchScreenFactory
import com.neilpontecorvo.soundcloudfiretv.feature.settings.SettingsScreenFactory
import com.neilpontecorvo.soundcloudfiretv.network.DeviceSessionApiClient
import com.neilpontecorvo.soundcloudfiretv.ui.TvArtworkLoader
import com.neilpontecorvo.soundcloudfiretv.ui.TvDesign
import com.neilpontecorvo.soundcloudfiretv.ui.TvDesignMetrics
import com.neilpontecorvo.soundcloudfiretv.ui.TvWaveformView
import com.neilpontecorvo.soundcloudfiretv.ui.TvInteractionRules
import com.neilpontecorvo.soundcloudfiretv.webview.HardenedWebViewClient
import com.neilpontecorvo.soundcloudfiretv.webview.PlayerBridge
import com.neilpontecorvo.soundcloudfiretv.webview.WebPlayerHostController
import com.neilpontecorvo.soundcloudfiretv.webview.WebViewHostConfig
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(),
    HardenedWebViewClient.NavigationListener,
    PlayerBridge.BridgeEventListener,
    ContentCardSelectionListener {

    private lateinit var rootContainer: FrameLayout
    private lateinit var headerFrame: FrameLayout
    private lateinit var contentFrame: FrameLayout
    private lateinit var miniPlayerFrame: FrameLayout
    private lateinit var playerHostFrame: FrameLayout
    private lateinit var titleView: TextView
    private lateinit var metrics: TvDesignMetrics
    private lateinit var screenRenderer: ScreenRenderer
    private lateinit var focusCoordinator: FocusCoordinator

    private lateinit var apiClient: DeviceSessionApiClient
    private lateinit var authGateway: ApiBackedAuthGateway
    private lateinit var sessionPersistence: SessionPersistence
    private lateinit var contentRepository: ContentRepository

    private val webHost = WebPlayerHostController(
        config = WebViewHostConfig.DEFAULT,
        isDebugBuild = BuildConfig.DEBUG
    ).apply { navigationListener = this@MainActivity }
    private val playerBridge by lazy { PlayerBridge(this) }

    private val navButtons = linkedMapOf<AppScreen, TextView>()
    private var currentScreen = AppScreen.HOME
    private var userInSettings = false
    private var detailReturnScreen: AppScreen? = null
    private var playerReturnScreen: AppScreen? = null
    private var currentDetailCard: ContentCardSpec? = null
    private var currentPlaylistDetail: PlaylistDetail? = null
    private val lastFocusedCard = mutableMapOf<AppScreen, String>()

    private var currentSearchQuery = ""
    private var lastSearchState: ContentLoadState? = null
    private var isSearchInFlight = false
    private var searchInputView: EditText? = null
    private var searchButtonView: TextView? = null
    private var searchResultsContainer: FrameLayout? = null

    private var selectedCard: ContentCardSpec? = null
    private var playerQueueCards: List<ContentCardSpec> = emptyList()
    private var activeQueueIndex = -1
    private var playerWebView: WebView? = null
    private var nativePlayer: MediaPlayer? = null
    private var nativePlayerPrepared = false
    private var nativeProgressRunnable: Runnable? = null
    private var lastLoadedPlayableId: String? = null
    private var playerUiState = PlayerUiState()
    private var playerLoadToken = 0
    private var hasLoggedFirstBridgeEvent = false
    private var playWhenPlayerReady = false
    private var lastBlockedNavigation: String? = null

    private var playerStateView: TextView? = null
    private var playerTrackView: TextView? = null
    private var playerArtistView: TextView? = null
    private var playerErrorView: TextView? = null
    private var playerPositionView: TextView? = null
    private var playerDurationView: TextView? = null
    private var playerWaveformView: TvWaveformView? = null
    private var playerArtworkView: ImageView? = null
    private var playerPlayControl: TextView? = null
    private var playlistWaveformFocusView: View? = null
    private var playlistRowsScrollView: ScrollView? = null
    private var playlistTrackViews: List<View> = emptyList()
    private var playlistTrackCards: List<ContentCardSpec> = emptyList()
    private var playlistTransportControls: List<View> = emptyList()
    private var collectionPreviewCard: ContentCardSpec? = null
    private var collectionFocusZone = CollectionFocusZone.OTHER
    private var collectionFocusedTrackIndex = -1
    private var collectionHeaderIndex = 2
    private val queueRowsById = mutableMapOf<String, View>()

    private lateinit var miniArtwork: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniContext: TextView
    private lateinit var miniProgressPlayed: View
    private lateinit var miniPosition: TextView
    private lateinit var miniRemaining: TextView
    private lateinit var miniPlayGlyph: TextView
    private lateinit var miniStatus: TextView

    private val playerTimeoutHandler = Handler(Looper.getMainLooper())
    private var playerTimeoutRunnable: Runnable? = null
    private var providerSignInPollRunnable: Runnable? = null
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private val authStateListener: (AuthSessionState) -> Unit = { state ->
        refreshSettingsBody(state)
        refreshContentForCurrentScreen(state)
        routeByAuthState(state)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fire TV should not dim, start its screensaver, or sleep while this app
        // is the foreground activity. Android applies this flag only while the
        // window is visible, so background system sleep behavior is unaffected.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        setContentView(R.layout.activity_main)

        rootContainer = findViewById(R.id.rootContainer)
        headerFrame = findViewById(R.id.headerFrame)
        contentFrame = findViewById(R.id.contentFrame)
        miniPlayerFrame = findViewById(R.id.miniPlayerFrame)
        playerHostFrame = findViewById(R.id.playerHostFrame)
        metrics = TvDesignMetrics(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        focusCoordinator = FocusCoordinator(this)
        screenRenderer = ScreenRenderer(this, metrics, this)

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

        configureReferenceShell()
        buildGlobalHeader()
        buildPersistentMiniPlayer()

        val startupState = authGateway.getCurrentState()
        navigateTo(if (startupState.isAuthenticated) AppScreen.HOME else AppScreen.LOGIN_REQUIRED)
        authGateway.restoreOrBootstrap()
        Log.i(TAG, "UI reference frame: window=${metrics.windowWidth}x${metrics.windowHeight} scale=${metrics.scale}")
    }

    override fun onDestroy() {
        releasePlayerHost(clearSelection = false)
        authGateway.removeListener(authStateListener)
        authGateway.shutdown()
        contentRepository.shutdown()
        super.onDestroy()
    }

    private fun configureReferenceShell() {
        metrics.applyFrame(headerFrame, 0, 0, 1920, 120)
        metrics.applyFrame(contentFrame, 0, 120, 1920, 888)
        contentFrame.clipChildren = true
        contentFrame.clipToPadding = true
        metrics.applyFrame(miniPlayerFrame, 0, 1008, 1920, 72)
        metrics.applyFrame(playerHostFrame, 0, 0, 1, 1)
        playerHostFrame.isFocusable = false
        playerHostFrame.isFocusableInTouchMode = false
        playerHostFrame.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    private fun buildGlobalHeader() {
        headerFrame.removeAllViews()
        titleView = label("Home", 32f, TvDesign.TEXT, bold = true).apply {
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = metrics.relativeFrame(64, 42, 800, 62)
        }
        headerFrame.addView(titleView)

        val destinations = listOf(
            Triple(AppScreen.HOME, "Home", R.id.btnHome),
            Triple(AppScreen.SEARCH, "Search", R.id.btnSearch),
            Triple(AppScreen.LIBRARY, "Library", R.id.btnLibrary),
            Triple(AppScreen.PLAYER, "Player", R.id.btnPlayer),
            Triple(AppScreen.SETTINGS, "Settings", R.id.btnSettings)
        )
        destinations.forEachIndexed { index, (screen, text, idValue) ->
            val button = label(text, 16f, TvDesign.DIM, bold = true).apply {
                id = idValue
                gravity = Gravity.CENTER
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                layoutParams = metrics.relativeFrame(1285 + index * 116, 48, 107, 57)
                setOnClickListener { navigateTo(screen) }
                setOnFocusChangeListener { _, _ -> refreshNavStyles() }
            }
            navButtons[screen] = button
            headerFrame.addView(button)
        }
        val buttons = navButtons.values.toList()
        buttons.forEachIndexed { index, button ->
            button.nextFocusLeftId = buttons.getOrNull(index - 1)?.id ?: button.id
            button.nextFocusRightId = buttons.getOrNull(index + 1)?.id ?: button.id
            button.nextFocusUpId = button.id
        }
        refreshNavStyles()
    }

    private fun buildPersistentMiniPlayer() {
        miniPlayerFrame.removeAllViews()
        miniPlayerFrame.isFocusable = true
        miniPlayerFrame.isFocusableInTouchMode = true
        miniPlayerFrame.isClickable = true
        miniPlayerFrame.contentDescription = "Now playing mini-player. Select to open Player."
        miniPlayerFrame.background = TvDesign.rounded(TvDesign.BLACK, 0)
        miniPlayerFrame.setOnClickListener {
            if (selectedCard != null) {
                if (currentScreen != AppScreen.PLAYER) playerReturnScreen = currentScreen
                navigateTo(AppScreen.PLAYER)
            }
        }
        miniPlayerFrame.setOnFocusChangeListener { _, hasFocus ->
            miniPlayerFrame.background = TvDesign.rounded(
                TvDesign.BLACK,
                0,
                metrics.px(if (hasFocus) 3 else 0),
                TvDesign.YELLOW
            )
        }

        miniPlayerFrame.addView(View(this).apply {
            setBackgroundColor(TvDesign.BORDER)
            layoutParams = metrics.relativeFrame(0, 0, 1920, 1)
        })
        miniArtwork = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = metrics.relativeFrame(64, 11, 48, 48)
        }
        miniPlayerFrame.addView(miniArtwork)
        miniTitle = label("Nothing playing", 16f, TvDesign.TEXT, bold = true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = metrics.relativeFrame(128, 10, 510, 24)
        }
        miniPlayerFrame.addView(miniTitle)
        miniContext = label("Select a track from Home, Search, or Library", 13f, TvDesign.DIM).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = metrics.relativeFrame(128, 38, 510, 19)
        }
        miniPlayerFrame.addView(miniContext)

        val progressTrack = FrameLayout(this).apply {
            background = TvDesign.rounded(TvDesign.BORDER, metrics.px(3))
            layoutParams = metrics.relativeFrame(690, 33, 610, 6)
        }
        miniProgressPlayed = View(this).apply {
            background = TvDesign.rounded(TvDesign.ORANGE, metrics.px(3))
        }
        progressTrack.addView(miniProgressPlayed, FrameLayout.LayoutParams(0, metrics.px(6)))
        miniPlayerFrame.addView(progressTrack)
        miniPosition = label("0:00", 11f, TvDesign.DIM).apply {
            layoutParams = metrics.relativeFrame(690, 43, 80, 18)
        }
        miniPlayerFrame.addView(miniPosition)
        miniRemaining = label("", 11f, TvDesign.DIM).apply {
            gravity = Gravity.END
            layoutParams = metrics.relativeFrame(1210, 43, 90, 18)
        }
        miniPlayerFrame.addView(miniRemaining)
        miniPlayGlyph = label("▶", 22f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            background = TvDesign.oval(TvDesign.ORANGE)
            layoutParams = metrics.relativeFrame(1360, 9, 54, 54)
        }
        miniPlayerFrame.addView(miniPlayGlyph)
        miniStatus = label("IDLE", 12f, TvDesign.ORANGE, bold = true).apply {
            layoutParams = metrics.relativeFrame(1432, 13, 160, 20)
        }
        miniPlayerFrame.addView(miniStatus)
        miniPlayerFrame.addView(label("Select: open player  •  Play/Pause: global control", 12f, TvDesign.DIM).apply {
            layoutParams = metrics.relativeFrame(1432, 39, 420, 18)
        })
        updateMiniPlayer()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = RemoteInputHandler.mapKeyCode(event.keyCode)
        val collectionKey = action in setOf(
            RemoteAction.UP,
            RemoteAction.DOWN,
            RemoteAction.LEFT,
            RemoteAction.RIGHT,
            RemoteAction.SELECT,
            RemoteAction.BACK
        )
        if (currentScreen == AppScreen.PLAYLIST_DETAIL && collectionKey) {
            val regionBefore = collectionFocusZone
            val indexBefore = collectionFocusedTrackIndex
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (action) {
                    RemoteAction.SELECT -> when (collectionFocusZone) {
                        CollectionFocusZone.TRACKS -> activateCollectionTrack(collectionFocusedTrackIndex)
                        CollectionFocusZone.HEADER -> COLLECTION_HEADER_SCREENS
                            .getOrNull(collectionHeaderIndex)
                            ?.let(::navigateTo)
                        else -> Unit
                    }
                    RemoteAction.BACK -> navigateTo(detailReturnScreen ?: AppScreen.LIBRARY)
                    RemoteAction.LEFT, RemoteAction.RIGHT -> {
                        if (collectionFocusZone == CollectionFocusZone.WAVEFORM) {
                            scanCollectionWaveform(
                                if (action == RemoteAction.LEFT) -1 else 1,
                                event.repeatCount
                            )
                        } else {
                            handleCollectionDetailFocus(action)
                        }
                    }
                    RemoteAction.UP, RemoteAction.DOWN -> handleCollectionDetailFocus(action)
                    else -> Unit
                }
            }
            Log.i(
                TAG,
                "CollectionKey: key=${event.keyCode}, eventAction=${event.action}, " +
                    "regionBefore=$regionBefore, indexBefore=$indexBefore, " +
                    "regionAfter=$collectionFocusZone, indexAfter=$collectionFocusedTrackIndex, consumed=true"
            )
            return true
        }
        if (!action.isTransportAction()) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_UP) return if (canDispatchTransportCommand()) true else super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        return if (handleTransportAction(action, event.repeatCount)) true else super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val action = RemoteInputHandler.mapKeyCode(keyCode)
        if (action == RemoteAction.BACK && currentScreen == AppScreen.SEARCH && currentFocus === searchInputView) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(searchInputView?.windowToken, 0)
            searchButtonView?.requestFocus()
            return true
        }
        if (action == RemoteAction.BACK) {
            when (currentScreen) {
                AppScreen.PLAYLIST_DETAIL -> navigateTo(detailReturnScreen ?: AppScreen.LIBRARY)
                AppScreen.PLAYER -> navigateTo(playerReturnScreen ?: AppScreen.HOME)
                AppScreen.HOME -> return super.onKeyDown(keyCode, event)
                else -> navigateTo(AppScreen.HOME)
            }
            return true
        }
        if (action == RemoteAction.MENU && currentScreen != AppScreen.SETTINGS) {
            navigateTo(AppScreen.SETTINGS)
            return true
        }
        if (focusCoordinator.handle(action)) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun handleCollectionDetailFocus(action: RemoteAction): Boolean {
        if (playlistTrackViews.isEmpty()) return true
        when (collectionFocusZone) {
            CollectionFocusZone.HEADER -> when (action) {
                RemoteAction.DOWN -> {
                    collectionFocusZone = CollectionFocusZone.TRACKS
                    collectionFocusedTrackIndex = collectionFocusedTrackIndex
                        .coerceIn(0, playlistTrackViews.lastIndex)
                }
                RemoteAction.LEFT -> collectionHeaderIndex = (collectionHeaderIndex - 1).coerceAtLeast(0)
                RemoteAction.RIGHT -> collectionHeaderIndex = (collectionHeaderIndex + 1)
                    .coerceAtMost(COLLECTION_HEADER_SCREENS.lastIndex)
                else -> Unit
            }
            CollectionFocusZone.TRACKS -> when (action) {
                RemoteAction.DOWN -> collectionFocusedTrackIndex =
                    (collectionFocusedTrackIndex + 1).coerceAtMost(playlistTrackViews.lastIndex)
                RemoteAction.UP -> {
                    if (collectionFocusedTrackIndex > 0) {
                        collectionFocusedTrackIndex -= 1
                    } else {
                        collectionFocusZone = CollectionFocusZone.HEADER
                        collectionHeaderIndex = COLLECTION_HEADER_SCREENS.indexOf(AppScreen.LIBRARY)
                    }
                }
                RemoteAction.LEFT -> collectionFocusZone = CollectionFocusZone.WAVEFORM
                RemoteAction.RIGHT -> Unit
                else -> Unit
            }
            CollectionFocusZone.WAVEFORM -> when (action) {
                RemoteAction.DOWN -> collectionFocusZone = CollectionFocusZone.TRACKS
                RemoteAction.UP -> {
                    collectionFocusZone = CollectionFocusZone.HEADER
                    collectionHeaderIndex = COLLECTION_HEADER_SCREENS.indexOf(AppScreen.LIBRARY)
                }
                else -> Unit
            }
            CollectionFocusZone.OTHER -> if (action == RemoteAction.DOWN) {
                collectionFocusZone = CollectionFocusZone.TRACKS
                collectionFocusedTrackIndex = 0
            }
        }
        updateCollectionFocusVisuals()
        ensureSelectedTrackVisible()
        return true
    }

    private fun updateCollectionFocusVisuals() {
        updateHeaderFocusVisuals()
        updateTrackRowFocusVisuals()
        updateWaveformFocusVisuals()
        updateTransportFocusVisuals()
        Log.i(
            TAG,
            "CollectionFocusPaint: region=$collectionFocusZone, " +
                "selectedTrackIndex=$collectionFocusedTrackIndex, rowCount=${playlistTrackViews.size}, " +
                "selectedRowTitle=${playlistTrackCards.getOrNull(collectionFocusedTrackIndex)?.title}"
        )
    }

    private fun updateHeaderFocusVisuals() = refreshNavStyles()

    private fun updateTrackRowFocusVisuals() {
        if (collectionFocusZone == CollectionFocusZone.TRACKS) {
            playlistTrackCards.getOrNull(collectionFocusedTrackIndex)?.let { focusedCard ->
                val waveform = playlistWaveformFocusView as? TvWaveformView
                if (collectionPreviewCard?.id != focusedCard.id) {
                    collectionPreviewCard = focusedCard
                    waveform?.setTrack(focusedCard.waveformUrl, focusedCard.id)
                }
                val focusedIsActive = focusedCard.id == selectedCard?.id
                waveform?.setProgress(
                    if (focusedIsActive) playerUiState.positionMs else 0L,
                    if (focusedIsActive) playerUiState.durationMs else focusedCard.durationMs ?: 0L
                )
            }
        }
        playlistTrackViews.forEachIndexed { index, row ->
            val playing = playlistTrackCards.getOrNull(index)?.id == selectedCard?.id
            row.background = trackRowBackground(
                collectionFocusZone == CollectionFocusZone.TRACKS && index == collectionFocusedTrackIndex,
                playing
            )
            row.invalidate()
        }
    }

    private fun updateWaveformFocusVisuals() {
        (playlistWaveformFocusView as? TvWaveformView)
            ?.setFocusHighlighted(collectionFocusZone == CollectionFocusZone.WAVEFORM)
    }

    private fun updateTransportFocusVisuals() {
        playlistTransportControls.forEachIndexed { index, control ->
            control.background = transportBackground(primary = index == 2, focused = false)
        }
    }

    private fun ensureSelectedTrackVisible() {
        val row = playlistTrackViews.getOrNull(collectionFocusedTrackIndex) ?: return
        playlistRowsScrollView?.post {
            playlistRowsScrollView?.smoothScrollTo(0, row.top.coerceAtLeast(0))
        }
    }

    private fun activateCollectionTrack(index: Int) {
        val tracks = playlistTrackCards
        if (index !in tracks.indices) {
            Log.w(TAG, "CollectionActivate: invalid requestedIndex=$index, trackCount=${tracks.size}")
            return
        }
        val selectedTrack = tracks[index]
        val hasWebUrl = !selectedTrack.webUrl.isNullOrBlank()
        val playableQueue = tracks.filter { !it.webUrl.isNullOrBlank() }
        val queueIndex = playableQueue.indexOfFirst { it.id == selectedTrack.id }
        Log.i(
            TAG,
            "CollectionActivate: requestedIndex=$index, trackId=${selectedTrack.id}, " +
                "title=${selectedTrack.title}, hasWebUrl=$hasWebUrl, " +
                "queueSize=${playableQueue.size}, queueIndex=$queueIndex"
        )
        if (!hasWebUrl) {
            Toast.makeText(this, "This track is unavailable for playback.", Toast.LENGTH_SHORT).show()
            return
        }
        selectedCard = selectedTrack
        playerQueueCards = playableQueue
        activeQueueIndex = queueIndex
        detailReturnScreen = AppScreen.LIBRARY
        playerReturnScreen = AppScreen.PLAYLIST_DETAIL
        playWhenPlayerReady = true
        Log.i(
            TAG,
            "PlayerSelectionBeforeNavigation: selectedCardId=${selectedTrack.id}, " +
                "selectedCardTitle=${selectedTrack.title}, selectedCardWebUrlPresent=$hasWebUrl, " +
                "queueSize=${playerQueueCards.size}, activeQueueIndex=$activeQueueIndex"
        )
        navigateTo(AppScreen.PLAYER)
        startPendingPlaybackIfReady(selectedTrack.id)
    }

    private fun scanCollectionWaveform(direction: Int, repeatCount: Int) {
        val preview = collectionPreviewCard ?: return
        val detail = currentPlaylistDetail ?: return
        if (selectedCard?.id != preview.id || lastLoadedPlayableId != preview.id) {
            selectedCard = preview
            playerQueueCards = detail.tracks.filter { !it.webUrl.isNullOrBlank() }
            activeQueueIndex = playerQueueCards.indexOfFirst { it.id == preview.id }
            playerReturnScreen = AppScreen.PLAYLIST_DETAIL
            ensurePlayerHost(preview)
            updateMiniPlayer()
        }
        val waveform = playlistWaveformFocusView as? TvWaveformView
        playerWaveformView = waveform
        seekBy(direction * TvInteractionRules.scanStepMs(repeatCount))
        waveform?.setProgress(playerUiState.positionMs, playerUiState.durationMs)
    }

    private fun handleTransportAction(action: RemoteAction, repeatCount: Int): Boolean {
        if (!canDispatchTransportCommand()) return false
        if (repeatCount > 0) return true
        when (action) {
            RemoteAction.PLAY -> sendPlayCommand()
            RemoteAction.PAUSE -> sendPauseCommand()
            RemoteAction.PLAY_PAUSE -> sendTogglePlayPauseCommand()
            RemoteAction.NEXT -> selectQueueOffset(1)
            RemoteAction.PREVIOUS -> selectQueueOffset(-1)
            RemoteAction.FAST_FORWARD -> seekBy(10_000L)
            RemoteAction.REWIND -> seekBy(-10_000L)
            else -> return false
        }
        Log.i(TAG, "Transport dispatch: action=$action selectedId=${selectedCard?.id}")
        return true
    }

    private fun RemoteAction.isTransportAction(): Boolean = when (this) {
        RemoteAction.PLAY, RemoteAction.PAUSE, RemoteAction.PLAY_PAUSE,
        RemoteAction.NEXT, RemoteAction.PREVIOUS,
        RemoteAction.FAST_FORWARD, RemoteAction.REWIND -> true
        else -> false
    }

    private fun canDispatchTransportCommand(): Boolean =
        (playerWebView != null || nativePlayer != null) && !selectedCard?.webUrl.isNullOrBlank()

    private fun seekBy(deltaMs: Long) {
        val duration = playerUiState.durationMs
        if (duration <= 0L) return
        val target = TvInteractionRules.clampedSeek(playerUiState.positionMs, duration, deltaMs) ?: return
        updatePlayerUi(playerUiState.copy(positionMs = target))
        nativePlayer?.takeIf { nativePlayerPrepared }?.let { player ->
            runCatching { player.seekTo(target.toInt()) }
                .onFailure { showNativePlaybackError("Unable to seek this private track.") }
        } ?: playerWebView?.let { playerBridge.sendSeekTo(it, target) }
    }

    private fun selectQueueOffset(delta: Int) {
        val current = selectedCard ?: return
        val index = activeQueueIndex.takeIf { it in playerQueueCards.indices }
            ?: playerQueueCards.indexOfFirst { it.id == current.id }
        val targetIndex = TvInteractionRules.queueTargetIndex(index, delta, playerQueueCards.size) ?: return
        val next = playerQueueCards[targetIndex]
        selectedCard = next
        activeQueueIndex = targetIndex
        ensurePlayerHost(next)
        if (currentScreen == AppScreen.PLAYER) renderCurrentScreen()
        updateMiniPlayer()
    }

    override fun onCardSelected(card: ContentCardSpec) = onCardSelectedFromSection(card, emptyList())

    override fun onCardSelectedFromSection(card: ContentCardSpec, sectionCards: List<ContentCardSpec>) {
        when (card.eyebrow.lowercase()) {
            "playlist", "station", "album" -> showCollectionDetail(card)
            "artist" -> showStateMessage("Artist playback is unavailable for this item.", null, null)
            else -> {
                playerReturnScreen = currentScreen
                selectedCard = card
                playerQueueCards = sectionCards
                    .filter { it.eyebrow.equals("track", true) && !it.webUrl.isNullOrBlank() }
                    .ifEmpty { listOf(card) }
                activeQueueIndex = playerQueueCards.indexOfFirst { it.id == card.id }
                navigateTo(AppScreen.PLAYER)
            }
        }
    }

    private fun showCollectionDetail(card: ContentCardSpec) {
        detailReturnScreen = if (currentScreen == AppScreen.PLAYLIST_DETAIL) AppScreen.LIBRARY else currentScreen
        currentDetailCard = card
        currentPlaylistDetail = null
        navigateTo(AppScreen.PLAYLIST_DETAIL)
    }

    private fun navigateTo(screen: AppScreen) {
        if (screen != AppScreen.LOGIN_REQUIRED) stopProviderSignInPolling()
        currentScreen = screen
        userInSettings = screen == AppScreen.SETTINGS
        clearScreenViewReferences()
        updateTitleAndNavigation()
        contentFrame.removeAllViews()
        val view = when (screen) {
            AppScreen.HOME -> buildLoadingState("Loading Home…")
            AppScreen.SEARCH -> buildSearchScreen()
            AppScreen.LIBRARY -> buildLoadingState("Loading Library…")
            AppScreen.PLAYLIST_DETAIL -> buildPlaylistDetailLoading()
            AppScreen.PLAYER -> buildPlayerView()
            AppScreen.SETTINGS -> buildSettingsView()
            AppScreen.LOGIN_REQUIRED -> buildLoginRequiredView(authGateway.getCurrentState())
        }
        contentFrame.addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        requestContentFor(screen, authGateway.getCurrentState())
    }

    private fun renderCurrentScreen() = navigateTo(currentScreen)

    private fun clearScreenViewReferences() {
        playerStateView = null
        playerTrackView = null
        playerArtistView = null
        playerErrorView = null
        playerPositionView = null
        playerDurationView = null
        playerWaveformView = null
        playerArtworkView = null
        playerPlayControl = null
        playlistWaveformFocusView = null
        playlistRowsScrollView = null
        playlistTrackViews = emptyList()
        playlistTrackCards = emptyList()
        playlistTransportControls = emptyList()
        collectionPreviewCard = null
        collectionFocusZone = CollectionFocusZone.OTHER
        collectionFocusedTrackIndex = -1
        collectionHeaderIndex = COLLECTION_HEADER_SCREENS.indexOf(AppScreen.LIBRARY)
        queueRowsById.clear()
        searchInputView = null
        searchButtonView = null
        searchResultsContainer = null
    }

    private fun updateTitleAndNavigation() {
        titleView.text = when (currentScreen) {
            AppScreen.HOME -> "Home"
            AppScreen.SEARCH -> "Search"
            AppScreen.LIBRARY -> "Your Library"
            AppScreen.PLAYLIST_DETAIL -> currentCollectionTypeLabel().lowercase()
                .replaceFirstChar { it.titlecase() }
            AppScreen.PLAYER -> "Player"
            AppScreen.SETTINGS -> "Settings"
            AppScreen.LOGIN_REQUIRED -> "Sign In"
        }
        refreshNavStyles()
    }

    private fun refreshNavStyles() {
        navButtons.forEach { (screen, button) ->
            val active = when (currentScreen) {
                AppScreen.PLAYLIST_DETAIL -> screen == AppScreen.LIBRARY
                else -> currentScreen == screen
            }
            val focused = if (currentScreen == AppScreen.PLAYLIST_DETAIL) {
                collectionFocusZone == CollectionFocusZone.HEADER &&
                    COLLECTION_HEADER_SCREENS.getOrNull(collectionHeaderIndex) == screen
            } else {
                button.hasFocus()
            }
            button.setTextColor(if (active || focused) Color.WHITE else TvDesign.DIM)
            button.background = TvDesign.rounded(
                fill = if (active) TvDesign.ORANGE else TvDesign.SURFACE,
                radiusPx = metrics.px(11),
                strokeWidthPx = metrics.px(if (focused) 3 else 0),
                stroke = TvDesign.YELLOW
            )
        }
    }

    private fun buildLoadingState(message: String): View = buildStateMessage(message, null, null)

    private fun buildStateMessage(message: String, actionLabel: String?, action: (() -> Unit)?): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        root.addView(label(message, 18f, TvDesign.MUTED).apply { gravity = Gravity.CENTER })
        if (actionLabel != null && action != null) {
            root.addView(focusButton(actionLabel, 210, 58, action).apply {
                layoutParams = LinearLayout.LayoutParams(metrics.px(210), metrics.px(58)).apply {
                    topMargin = metrics.px(22)
                }
            })
        }
        return root
    }

    private fun showStateMessage(message: String, actionLabel: String?, action: (() -> Unit)?) {
        contentFrame.removeAllViews()
        contentFrame.addView(buildStateMessage(message, actionLabel, action), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun requestContentFor(screen: AppScreen, state: AuthSessionState) {
        val sessionId = state.sessionId ?: return
        when (screen) {
            AppScreen.HOME -> contentRepository.loadFeed(sessionId) { handleContentState(screen, it) }
            AppScreen.LIBRARY -> contentRepository.loadLibrary(sessionId) { handleContentState(screen, it) }
            AppScreen.PLAYLIST_DETAIL -> {
                val card = currentDetailCard ?: return
                contentRepository.loadPlaylist(sessionId, card.id) { handlePlaylistState(card.id, it) }
            }
            else -> Unit
        }
    }

    private fun handleContentState(screen: AppScreen, state: ContentLoadState) {
        if (currentScreen != screen) return
        when (state) {
            ContentLoadState.Loading -> Unit
            ContentLoadState.Empty -> showStateMessage(
                if (screen == AppScreen.HOME) "No content available" else "Your library is empty",
                "Retry"
            ) { requestContentFor(screen, authGateway.getCurrentState()) }
            is ContentLoadState.Error -> showStateMessage(
                "Unable to load content\n${state.message}",
                "Retry"
            ) { requestContentFor(screen, authGateway.getCurrentState()) }
            is ContentLoadState.Success -> renderContentGrid(screen, state)
        }
    }

    private fun renderContentGrid(screen: AppScreen, state: ContentLoadState.Success) {
        if (currentScreen != screen) return
        val model = when (screen) {
            AppScreen.HOME -> HomeScreenFactory.create("", state.sections)
            AppScreen.LIBRARY -> LibraryScreenFactory.create("", state.sections)
            else -> return
        }
        val activeNav = navButtons[screen]?.id ?: View.NO_ID
        val options = GridRenderOptions(
                topInset = 111,
                verticalGap = if (screen == AppScreen.HOME) 28 else 14,
                upFocusId = activeNav,
                downFocusId = R.id.miniPlayerFrame,
                preferredCardId = lastFocusedCard[screen],
                onCardFocused = { id -> lastFocusedCard[screen] = id }
            )
        val rendered = when (screen) {
            AppScreen.HOME,
            AppScreen.LIBRARY -> screenRenderer.renderSectionRails(model, options)
            else -> return
        }
        contentFrame.removeAllViews()
        contentFrame.addView(rendered.view)
        rendered.firstFocusableId?.let { first -> navButtons[screen]?.nextFocusDownId = first }
        rendered.lastFocusableId?.let { miniPlayerFrame.nextFocusUpId = it }
    }

    private fun buildSearchScreen(): View {
        val root = FrameLayout(this)
        val input = EditText(this).apply {
            id = View.generateViewId()
            hint = "Search tracks, artists, playlists…"
            setHintTextColor(TvDesign.DIM)
            setTextColor(TvDesign.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.textPx(20f))
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(metrics.px(52), 0, metrics.px(20), 0)
            background = searchFieldBackground(true)
            setText(currentSearchQuery)
            layoutParams = metrics.relativeFrame(63, 31, 1567, 69)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    performSearch(text.toString())
                    true
                } else false
            }
            setOnFocusChangeListener { _, focused -> background = searchFieldBackground(focused) }
        }
        root.addView(input)
        val searchButton = focusButton("Search", 211, 69) { performSearch(input.text.toString()) }.apply {
            layoutParams = metrics.relativeFrame(1645, 31, 211, 69)
            background = TvDesign.rounded(TvDesign.ORANGE, metrics.px(11))
        }
        root.addView(searchButton)
        val results = FrameLayout(this).apply {
            layoutParams = metrics.relativeFrame(0, 128, 1920, 760)
        }
        root.addView(results)
        input.nextFocusRightId = searchButton.id
        input.nextFocusUpId = R.id.btnSearch
        searchButton.nextFocusLeftId = input.id
        searchButton.nextFocusUpId = R.id.btnSearch
        navButtons[AppScreen.SEARCH]?.nextFocusDownId = input.id
        searchInputView = input
        searchButtonView = searchButton
        searchResultsContainer = results
        lastSearchState?.let { updateSearchResults(it) }
        root.post { input.requestFocus() }
        return root
    }

    private fun performSearch(rawQuery: String) {
        val query = TvInteractionRules.normalizeSearchQuery(rawQuery)
        if (query.isBlank() || isSearchInFlight) return
        val sessionId = authGateway.getCurrentState().sessionId ?: return
        currentSearchQuery = query
        isSearchInFlight = true
        lastSearchState = ContentLoadState.Loading
        updateSearchResults(ContentLoadState.Loading)
        contentRepository.search(sessionId, query) { state ->
            isSearchInFlight = state is ContentLoadState.Loading
            lastSearchState = state
            if (currentScreen == AppScreen.SEARCH) updateSearchResults(state)
        }
    }

    private fun updateSearchResults(state: ContentLoadState) {
        val container = searchResultsContainer ?: return
        container.removeAllViews()
        when (state) {
            ContentLoadState.Loading -> container.addView(buildStateMessage("Searching…", null, null))
            ContentLoadState.Empty -> container.addView(buildStateMessage("No results for “$currentSearchQuery”", null, null))
            is ContentLoadState.Error -> container.addView(buildStateMessage(
                "Search failed\n${state.message}",
                "Retry"
            ) { performSearch(currentSearchQuery) })
            is ContentLoadState.Success -> {
                if (state.sections.isEmpty()) {
                    container.addView(buildStateMessage("No results for “$currentSearchQuery”", null, null))
                } else {
                    val rendered = screenRenderer.renderGrid(
                        SearchScreenFactory.create("", state.sections),
                        GridRenderOptions(
                            topInset = 0,
                            verticalGap = 28,
                            upFocusId = searchInputView?.id ?: R.id.btnSearch,
                            downFocusId = R.id.miniPlayerFrame,
                            preferredCardId = lastFocusedCard[AppScreen.SEARCH],
                            onCardFocused = { id -> lastFocusedCard[AppScreen.SEARCH] = id }
                        )
                    )
                    container.addView(rendered.view)
                    rendered.firstFocusableId?.let { first ->
                        searchInputView?.nextFocusDownId = first
                        searchButtonView?.nextFocusDownId = first
                    }
                    rendered.lastFocusableId?.let { miniPlayerFrame.nextFocusUpId = it }
                }
            }
        }
    }

    private fun searchFieldBackground(focused: Boolean) = TvDesign.rounded(
        TvDesign.SURFACE_RAISED,
        metrics.px(11),
        metrics.px(if (focused) 3 else 1),
        if (focused) TvDesign.YELLOW else TvDesign.BORDER
    )

    private fun buildPlaylistDetailLoading(): View {
        val card = currentDetailCard ?: return buildStateMessage("No collection selected", null, null)
        return buildStateMessage("Loading ${card.title}…", null, null)
    }

    private fun handlePlaylistState(playlistId: String, state: PlaylistLoadState) {
        if (currentScreen != AppScreen.PLAYLIST_DETAIL || currentDetailCard?.id != playlistId) return
        when (state) {
            PlaylistLoadState.Loading -> Unit
            is PlaylistLoadState.Error -> showStateMessage(
                "Unable to load ${currentCollectionTypeLabel().lowercase()}\n${state.message}",
                "Retry"
            ) { requestContentFor(AppScreen.PLAYLIST_DETAIL, authGateway.getCurrentState()) }
            is PlaylistLoadState.Success -> {
                currentPlaylistDetail = state.detail
                contentFrame.removeAllViews()
                val detailView = buildPlaylistDetailView(state.detail)
                contentFrame.addView(detailView)
            }
        }
    }

    private fun buildPlaylistDetailView(detail: PlaylistDetail): View {
        val root = FrameLayout(this)
        val artwork = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = metrics.relativeFrame(63, 31, 362, 362)
        }
        root.addView(artwork)
        TvArtworkLoader.load(this, detail.artworkUrl, artwork, metrics.px(362), metrics.px(362))
        root.addView(label(currentCollectionTypeLabel(), 14f, TvDesign.ORANGE, bold = true).apply {
            layoutParams = metrics.relativeFrame(64, 409, 360, 22)
        })
        root.addView(label(detail.title, 34f, TvDesign.TEXT, bold = true).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = metrics.relativeFrame(64, 442, 362, 82)
        })
        root.addView(label(
            listOfNotNull(
                detail.creatorName,
                "${detail.trackCount} tracks",
                detail.durationText
            ).joinToString("  •  "),
            17f,
            TvDesign.MUTED
        ).apply {
            maxLines = 2
            layoutParams = metrics.relativeFrame(64, 533, 362, 48)
        })
        collectionPreviewCard = selectedCard
            ?.takeIf { selected -> detail.tracks.any { track -> track.id == selected.id } }
            ?: detail.tracks.firstOrNull()
        val playlistWaveform = TvWaveformView(this).apply {
            id = View.generateViewId()
            isFocusable = true
            isFocusableInTouchMode = true
            contentDescription = "Selected playlist track waveform. Left and Right scan by one minute; hold to accelerate."
            background = TvDesign.rounded(TvDesign.SURFACE, metrics.px(8))
            layoutParams = metrics.relativeFrame(57, 575, 360, 79)
            setTrack(collectionPreviewCard?.waveformUrl, collectionPreviewCard?.id ?: detail.id)
            val previewIsActive = collectionPreviewCard?.id == selectedCard?.id
            setProgress(
                if (previewIsActive) playerUiState.positionMs else 0L,
                if (previewIsActive) playerUiState.durationMs else collectionPreviewCard?.durationMs ?: 0L
            )
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                val direction = when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> -1
                    KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                    else -> return@setOnKeyListener false
                }
                scanCollectionWaveform(direction, event.repeatCount)
                true
            }
        }
        if (collectionPreviewCard?.id == selectedCard?.id) playerWaveformView = playlistWaveform
        root.addView(playlistWaveform)
        val controls = buildTransportControls(onInitialPlay = {
            activateCollectionTrack(collectionFocusedTrackIndex.coerceAtLeast(0))
        })
        controls.layoutParams = metrics.relativeFrame(89, 703, 310, 66)
        root.addView(controls)

        val header = FrameLayout(this).apply {
            layoutParams = metrics.relativeFrame(507, 31, 1350, 54)
        }
        header.addView(label("#", 14f, TvDesign.MUTED, bold = true).apply {
            layoutParams = metrics.relativeFrame(18, 16, 40, 22)
        })
        header.addView(label("TITLE", 14f, TvDesign.MUTED, bold = true).apply {
            layoutParams = metrics.relativeFrame(76, 16, 650, 22)
        })
        header.addView(label("ARTIST", 14f, TvDesign.MUTED, bold = true).apply {
            layoutParams = metrics.relativeFrame(856, 16, 260, 22)
        })
        header.addView(label("TIME", 14f, TvDesign.MUTED, bold = true).apply {
            gravity = Gravity.END
            layoutParams = metrics.relativeFrame(1226, 16, 95, 22)
        })
        root.addView(header)

        val rowsScroll = ScrollView(this).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = metrics.relativeFrame(507, 85, 1350, 733)
        }
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rowsScroll.addView(rows)
        val rowViews = detail.tracks.mapIndexed { index, card ->
            buildTrackRow(card, index).also { row ->
                rows.addView(row, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    metrics.px(60)
                ).apply { bottomMargin = metrics.px(2) })
            }
        }
        rowViews.forEachIndexed { index, row ->
            val upTarget = rowViews.getOrNull(index - 1)
            val downTarget = rowViews.getOrNull(index + 1)
            row.nextFocusUpId = upTarget?.id ?: R.id.btnLibrary
            row.nextFocusDownId = downTarget?.id ?: R.id.miniPlayerFrame
            row.nextFocusLeftId = playlistWaveform.id
            row.nextFocusRightId = row.id
        }
        val firstTrackRow = rowViews.firstOrNull()
        playlistWaveformFocusView = playlistWaveform
        playlistRowsScrollView = rowsScroll
        playlistTrackViews = rowViews
        playlistTrackCards = detail.tracks
        playlistTransportControls = (0 until controls.childCount).map(controls::getChildAt)
        playlistWaveform.nextFocusUpId = R.id.btnLibrary
        playlistWaveform.nextFocusDownId = firstTrackRow?.id ?: R.id.miniPlayerFrame
        navButtons[AppScreen.LIBRARY]?.nextFocusDownId = firstTrackRow?.id ?: R.id.btnLibrary
        miniPlayerFrame.nextFocusUpId = rowViews.lastOrNull()?.id ?: R.id.btnLibrary
        for (index in 0 until controls.childCount) {
            controls.getChildAt(index).nextFocusUpId = playlistWaveform.id
            controls.getChildAt(index).nextFocusDownId = firstTrackRow?.id ?: controls.getChildAt(index).id
        }
        playerPlayControl?.nextFocusRightId = firstTrackRow?.id ?: playerPlayControl?.id ?: View.NO_ID
        controls.getChildAt(controls.childCount - 1).nextFocusRightId =
            firstTrackRow?.id ?: controls.getChildAt(controls.childCount - 1).id
        root.addView(rowsScroll)
        val restoredIndex = detail.tracks.indexOfFirst { it.id == selectedCard?.id }
        collectionFocusZone = CollectionFocusZone.TRACKS
        collectionFocusedTrackIndex = if (restoredIndex >= 0) restoredIndex else 0
        updateCollectionFocusVisuals()
        ensureSelectedTrackVisible()
        Log.i(
            TAG,
            "CollectionInit: playlistId=${detail.id}, trackCount=${detail.trackCount}, " +
                "trackRows.size=${playlistTrackViews.size}, region=$collectionFocusZone, " +
                "selectedTrackIndex=$collectionFocusedTrackIndex, " +
                "selectedRowTitle=${detail.tracks.getOrNull(collectionFocusedTrackIndex)?.title}"
        )
        return root
    }

    private fun currentCollectionTypeLabel(): String =
        if (currentDetailCard?.eyebrow.equals("ALBUM", ignoreCase = true)) "ALBUM" else "PLAYLIST"

    private fun buildTrackRow(
        card: ContentCardSpec,
        index: Int
    ): View {
        val playing = card.id == selectedCard?.id
        val row = FrameLayout(this).apply {
            id = View.generateViewId()
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            background = trackRowBackground(false, playing)
            contentDescription = "Track ${index + 1}, ${card.title}, ${card.creatorName ?: card.subtitle}, ${card.metadata.orEmpty()}"
            setOnClickListener { activateCollectionTrack(index) }
        }
        row.addView(label(if (playing) "▶" else "${index + 1}", 16f, TvDesign.TEXT).apply {
            layoutParams = metrics.relativeFrame(18, 19, 42, 24)
        })
        row.addView(label(card.title, 18f, TvDesign.TEXT, bold = true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = metrics.relativeFrame(76, 17, 650, 28)
        })
        row.addView(label(card.creatorName ?: card.subtitle, 16f, TvDesign.MUTED).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = metrics.relativeFrame(856, 18, 300, 26)
        })
        row.addView(label(card.metadata.orEmpty(), 16f, TvDesign.MUTED).apply {
            gravity = Gravity.END
            layoutParams = metrics.relativeFrame(1226, 18, 95, 26)
        })
        queueRowsById[card.id] = row
        return row
    }

    private fun trackRowBackground(focused: Boolean, playing: Boolean) = TvDesign.rounded(
        if (focused) Color.rgb(72, 59, 8) else if (playing) Color.rgb(37, 37, 37) else TvDesign.SURFACE,
        metrics.px(10),
        metrics.px(if (focused) 5 else 1),
        if (focused) TvDesign.YELLOW else TvDesign.BORDER
    )

    private fun buildPlayerView(): View {
        val selected = selectedCard ?: return buildStateMessage(
            "No track selected\nChoose a track from Home, Search, or Library.",
            null,
            null
        )
        Log.i(
            TAG,
            "PlayerBuild: selectedCardId=${selected.id}, selectedCardTitle=${selected.title}, " +
                "queueSize=${playerQueueCards.size}, activeQueueIndex=$activeQueueIndex, " +
                "lastLoadedPlayableId=$lastLoadedPlayableId"
        )
        if (selected.webUrl.isNullOrBlank()) return buildStateMessage("Playback is unavailable for this item.", null, null)
        ensurePlayerHost(selected)
        val root = FrameLayout(this)

        val artwork = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = metrics.relativeFrame(63, 31, 362, 362)
        }
        root.addView(artwork)
        playerArtworkView = artwork
        TvArtworkLoader.load(this, selected.artworkUrl ?: selected.creatorAvatarUrl, artwork, metrics.px(362), metrics.px(362))

        root.addView(label(selected.title, 34f, TvDesign.TEXT, bold = true).apply {
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = metrics.relativeFrame(64, 431, 362, 125)
        })
        root.addView(label(selected.creatorName ?: selected.subtitle, 23f, TvDesign.TEXT).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = metrics.relativeFrame(64, 558, 362, 42)
        })
        root.addView(label(
            if (selected.isPrivate) "SOUNDCLOUD  •  PRIVATE ACCOUNT TRACK" else "SOUNDCLOUD",
            13f,
            TvDesign.ORANGE,
            bold = true
        ).apply {
            contentDescription = "SoundCloud source. ${selected.creatorName.orEmpty()}"
            layoutParams = metrics.relativeFrame(64, 607, 362, 22)
        })
        val controls = buildTransportControls(onInitialPlay = null)
        controls.layoutParams = metrics.relativeFrame(89, 647, 310, 66)
        root.addView(controls)

        val nowPlayingPanel = FrameLayout(this).apply {
            background = TvDesign.rounded(Color.rgb(24, 18, 17), metrics.px(11), metrics.px(1), Color.rgb(93, 48, 30))
            layoutParams = metrics.relativeFrame(507, 31, 1350, 362)
        }
        playerStateView = label("NOW PLAYING", 14f, TvDesign.ORANGE, bold = true).apply {
            layoutParams = metrics.relativeFrame(38, 31, 260, 22)
        }
        nowPlayingPanel.addView(playerStateView)
        playerTrackView = label(selected.title, 23f, TvDesign.TEXT, bold = true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = metrics.relativeFrame(38, 61, 1230, 34)
        }
        nowPlayingPanel.addView(playerTrackView)
        playerArtistView = label(selected.creatorName ?: selected.subtitle, 18f, TvDesign.MUTED).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = metrics.relativeFrame(38, 99, 1230, 28)
        }
        nowPlayingPanel.addView(playerArtistView)
        val waveform = TvWaveformView(this).apply {
            id = View.generateViewId()
            isFocusable = true
            isFocusableInTouchMode = true
            contentDescription = "Playback timeline. Left and Right scan by one minute; hold to accelerate."
            layoutParams = metrics.relativeFrame(38, 156, 1248, 142)
            setTrack(selected.waveformUrl, selected.id)
            setProgress(playerUiState.positionMs, playerUiState.durationMs)
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                val direction = when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> -1
                    KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                    else -> return@setOnKeyListener false
                }
                seekBy(direction * TvInteractionRules.scanStepMs(event.repeatCount))
                true
            }
        }
        playerWaveformView = waveform
        nowPlayingPanel.addView(waveform)
        playerPositionView = label(formatTime(playerUiState.positionMs), 14f, TvDesign.TEXT).apply {
            layoutParams = metrics.relativeFrame(40, 311, 100, 24)
        }
        nowPlayingPanel.addView(playerPositionView)
        playerDurationView = label(formatTime(playerUiState.durationMs), 14f, TvDesign.TEXT).apply {
            gravity = Gravity.END
            layoutParams = metrics.relativeFrame(1178, 311, 108, 24)
        }
        nowPlayingPanel.addView(playerDurationView)
        playerErrorView = label("", 14f, TvDesign.ERROR).apply {
            maxLines = 1
            visibility = View.GONE
            layoutParams = metrics.relativeFrame(330, 31, 940, 22)
        }
        nowPlayingPanel.addView(playerErrorView)
        root.addView(nowPlayingPanel)

        val descriptionPanel = FrameLayout(this).apply {
            background = TvDesign.rounded(TvDesign.SURFACE, metrics.px(11), metrics.px(1), TvDesign.BORDER)
            layoutParams = metrics.relativeFrame(507, 415, 1350, 402)
        }
        descriptionPanel.addView(label("DESCRIPTION", 14f, TvDesign.ORANGE, bold = true).apply {
            layoutParams = metrics.relativeFrame(32, 30, 240, 22)
        })
        val descriptionScroll = ScrollView(this).apply {
            id = View.generateViewId()
            isFocusable = true
            isFocusableInTouchMode = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            background = TvDesign.rounded(Color.TRANSPARENT, metrics.px(8))
            layoutParams = metrics.relativeFrame(30, 68, 1290, 305)
            setOnFocusChangeListener { view, focused ->
                view.background = TvDesign.rounded(
                    Color.TRANSPARENT,
                    metrics.px(8),
                    metrics.px(if (focused) 3 else 0),
                    TvDesign.YELLOW
                )
            }
        }
        val primaryControl = playerPlayControl
        if (primaryControl != null) {
            navButtons[AppScreen.PLAYER]?.nextFocusDownId = primaryControl.id
            primaryControl.nextFocusUpId = R.id.btnPlayer
            waveform.nextFocusUpId = primaryControl.id
        }
        for (index in 0 until controls.childCount) {
            controls.getChildAt(index).nextFocusDownId = waveform.id
        }
        waveform.nextFocusDownId = descriptionScroll.id
        descriptionScroll.nextFocusUpId = waveform.id
        descriptionScroll.addView(label(
            selected.description?.takeIf { it.isNotBlank() } ?: "No description provided.",
            18f,
            TvDesign.TEXT
        ).apply {
            setLineSpacing(metrics.px(5).toFloat(), 1f)
            setPadding(metrics.px(2), 0, metrics.px(12), metrics.px(18))
        })
        descriptionPanel.addView(descriptionScroll)
        root.addView(descriptionPanel)
        updatePlayerUi(playerUiState)
        root.post { playerPlayControl?.requestFocus() }
        return root
    }

    private fun buildTransportControls(onInitialPlay: (() -> Unit)?): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val specs = listOf(
            "↶ 10" to { seekBy(-10_000L) },
            "◀◀" to { selectQueueOffset(-1) },
            (if (playerUiState.isPlaying) "Ⅱ" else "▶") to {
                if (!hasPlayerHost() && onInitialPlay != null) onInitialPlay() else sendTogglePlayPauseCommand()
            },
            "▶▶" to { selectQueueOffset(1) },
            "10 ↷" to { seekBy(10_000L) }
        )
        specs.forEachIndexed { index, (glyph, action) ->
            val size = if (index == 2) 64 else 48
            val button = label(glyph, if (index == 2) 25f else 15f, Color.WHITE, bold = true).apply {
                id = View.generateViewId()
                gravity = Gravity.CENTER
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                background = transportBackground(index == 2, false)
                layoutParams = LinearLayout.LayoutParams(metrics.px(size), metrics.px(size)).apply {
                    marginEnd = metrics.px(if (index < specs.lastIndex) 10 else 0)
                }
                setOnClickListener { action() }
                setOnFocusChangeListener { view, focused ->
                    view.background = transportBackground(index == 2, focused)
                }
            }
            if (index == 2) playerPlayControl = button
            row.addView(button)
        }
        val buttons = (0 until row.childCount).map { row.getChildAt(it) }
        buttons.forEachIndexed { index, button ->
            button.nextFocusLeftId = buttons.getOrNull(index - 1)?.id ?: button.id
            button.nextFocusRightId = buttons.getOrNull(index + 1)?.id ?: button.id
        }
        return row
    }

    private fun transportBackground(primary: Boolean, focused: Boolean) = TvDesign.oval(
        if (primary) TvDesign.ORANGE else TvDesign.SURFACE_RAISED,
        metrics.px(if (focused) 3 else 0),
        if (focused) TvDesign.YELLOW else Color.TRANSPARENT
    )

    private fun ensurePlayerHost(card: ContentCardSpec) {
        val expectedHostPresent = if (card.isPrivate) nativePlayer != null else playerWebView != null
        if (expectedHostPresent && lastLoadedPlayableId == card.id) return
        disposeCurrentPlayerHost()
        if (card.isPrivate) {
            ensureNativePlayerHost(card)
            return
        }
        val contentUrl = card.webUrl ?: return
        val webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = false
            isFocusableInTouchMode = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    Log.d(TAG, "WebConsole[${consoleMessage?.messageLevel()}] ${consoleMessage?.message().orEmpty()}")
                    return true
                }
            }
        }
        webHost.configure(webView)
        playerBridge.attachToWebView(webView)
        playerHostFrame.removeAllViews()
        playerHostFrame.addView(webView, FrameLayout.LayoutParams(metrics.px(1), metrics.px(1)))
        playerWebView = webView
        hasLoggedFirstBridgeEvent = false
        playerUiState = PlayerUiState(
            isLoading = true,
            trackTitle = card.title,
            artist = card.creatorName ?: card.subtitle,
            durationMs = card.durationMs ?: 0L
        )
        updatePlayerUi(playerUiState)
        val started = webHost.loadPlayer(webView, contentUrl)
        if (started) {
            lastLoadedPlayableId = card.id
            startPlayerReadyTimeout(card)
        } else {
            updatePlayerUi(playerUiState.copy(
                isLoading = false,
                errorMessage = webHost.getDiagnosticState().lastError ?: "Player target was rejected."
            ))
        }
    }

    private fun ensureNativePlayerHost(card: ContentCardSpec) {
        val sessionId = authGateway.getCurrentState().sessionId
        if (sessionId.isNullOrBlank()) {
            showNativePlaybackError("Your app session is unavailable. Sign in again to play this private track.")
            return
        }
        val streamUrl = "${BuildConfig.API_BASE_URL.trimEnd('/')}/v1/tracks/${Uri.encode(card.id)}/stream"
        val player = MediaPlayer()
        nativePlayer = player
        nativePlayerPrepared = false
        hasLoggedFirstBridgeEvent = false
        playerUiState = PlayerUiState(
            isLoading = true,
            trackTitle = card.title,
            artist = card.creatorName ?: card.subtitle,
            durationMs = card.durationMs ?: 0L
        )
        updatePlayerUi(playerUiState)
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.setOnPreparedListener { preparedPlayer ->
                if (nativePlayer !== preparedPlayer) return@setOnPreparedListener
                nativePlayerPrepared = true
                cancelPlayerReadyTimeout()
                val resolvedDuration = preparedPlayer.duration.toLong().takeIf { it > 0L }
                    ?: card.durationMs
                    ?: 0L
                updatePlayerUi(playerUiState.copy(
                    isLoading = false,
                    isReady = true,
                    durationMs = resolvedDuration,
                    errorMessage = null
                ))
                startNativeProgressUpdates(preparedPlayer)
                startPendingPlaybackIfReady(card.id)
                Log.i(TAG, "PrivateTrackPrepared: trackId=${card.id}, durationMs=$resolvedDuration")
            }
            player.setOnCompletionListener { completedPlayer ->
                if (nativePlayer !== completedPlayer) return@setOnCompletionListener
                updatePlayerUi(playerUiState.copy(
                    isPlaying = false,
                    isReady = true,
                    positionMs = playerUiState.durationMs
                ))
            }
            player.setOnErrorListener { errorPlayer, what, extra ->
                if (nativePlayer === errorPlayer) {
                    Log.e(TAG, "PrivateTrackError: trackId=${card.id}, what=$what, extra=$extra")
                    showNativePlaybackError("This private track could not be opened from your authenticated account.")
                }
                true
            }
            player.setOnInfoListener { infoPlayer, what, _ ->
                if (nativePlayer !== infoPlayer) return@setOnInfoListener false
                when (what) {
                    MediaPlayer.MEDIA_INFO_BUFFERING_START -> updatePlayerUi(playerUiState.copy(isLoading = true))
                    MediaPlayer.MEDIA_INFO_BUFFERING_END -> updatePlayerUi(playerUiState.copy(isLoading = false))
                }
                false
            }
            player.setDataSource(
                this,
                Uri.parse(streamUrl),
                mapOf("X-Session-Id" to sessionId)
            )
            lastLoadedPlayableId = card.id
            startPlayerReadyTimeout(card)
            player.prepareAsync()
            Log.i(TAG, "PrivateTrackLoad: trackId=${card.id}, backendProxy=true")
        } catch (error: Exception) {
            Log.e(TAG, "PrivateTrackSetupFailed: trackId=${card.id}", error)
            runCatching { player.release() }
            if (nativePlayer === player) nativePlayer = null
            nativePlayerPrepared = false
            lastLoadedPlayableId = null
            showNativePlaybackError("This private track could not be prepared for playback.")
        }
    }

    private fun hasPlayerHost(): Boolean = playerWebView != null || nativePlayer != null

    private fun sendPlayCommand(): Boolean {
        nativePlayer?.let { player ->
            if (!nativePlayerPrepared) {
                playWhenPlayerReady = true
                return true
            }
            return runCatching {
                player.start()
                startNativeProgressUpdates(player)
                updatePlayerUi(playerUiState.copy(isPlaying = true, isReady = true, isLoading = false, errorMessage = null))
                true
            }.getOrElse {
                showNativePlaybackError("This private track could not start playback.")
                false
            }
        }
        return playerWebView?.let {
            playerBridge.sendPlay(it)
            true
        } ?: false
    }

    private fun sendPauseCommand(): Boolean {
        nativePlayer?.let { player ->
            if (!nativePlayerPrepared) {
                playWhenPlayerReady = false
                return true
            }
            return runCatching {
                player.pause()
                updatePlayerUi(playerUiState.copy(isPlaying = false, isReady = true, isLoading = false))
                true
            }.getOrElse {
                showNativePlaybackError("This private track could not be paused.")
                false
            }
        }
        return playerWebView?.let {
            playerBridge.sendPause(it)
            true
        } ?: false
    }

    private fun sendTogglePlayPauseCommand(): Boolean {
        nativePlayer?.let { player ->
            if (!nativePlayerPrepared) {
                playWhenPlayerReady = !playWhenPlayerReady
                return true
            }
            return if (runCatching { player.isPlaying }.getOrDefault(false)) {
                sendPauseCommand()
            } else {
                sendPlayCommand()
            }
        }
        return playerWebView?.let {
            playerBridge.sendTogglePlayPause(it)
            true
        } ?: false
    }

    private fun startNativeProgressUpdates(player: MediaPlayer) {
        stopNativeProgressUpdates()
        val runnable = object : Runnable {
            override fun run() {
                if (nativePlayer !== player || !nativePlayerPrepared) return
                val position = runCatching { player.currentPosition.toLong() }.getOrNull() ?: return
                val duration = runCatching { player.duration.toLong() }.getOrNull()
                    ?.takeIf { it > 0L }
                    ?: playerUiState.durationMs
                val playing = runCatching { player.isPlaying }.getOrDefault(false)
                updatePlayerUi(playerUiState.copy(
                    positionMs = position.coerceAtLeast(0L),
                    durationMs = duration,
                    isPlaying = playing,
                    isReady = true,
                    isLoading = false
                ))
                handler.postDelayed(this, 1_000L)
            }
        }
        nativeProgressRunnable = runnable
        handler.post(runnable)
    }

    private fun stopNativeProgressUpdates() {
        nativeProgressRunnable?.let(handler::removeCallbacks)
        nativeProgressRunnable = null
    }

    private fun showNativePlaybackError(message: String) {
        playWhenPlayerReady = false
        cancelPlayerReadyTimeout()
        updatePlayerUi(playerUiState.copy(isLoading = false, isPlaying = false, errorMessage = message))
    }

    private fun disposeCurrentPlayerHost() {
        cancelPlayerReadyTimeout()
        stopNativeProgressUpdates()
        nativePlayer?.let { player ->
            runCatching { player.reset() }
            runCatching { player.release() }
        }
        nativePlayer = null
        nativePlayerPrepared = false
        playerWebView?.let { webView ->
            runCatching {
                webView.stopLoading()
                playerBridge.detachFromWebView(webView)
                webHost.clearSession(webView)
                webView.destroy()
            }
        }
        playerHostFrame.removeAllViews()
        playerWebView = null
        lastLoadedPlayableId = null
        playerLoadToken += 1
    }

    private fun releasePlayerHost(clearSelection: Boolean) {
        playWhenPlayerReady = false
        disposeCurrentPlayerHost()
        playerUiState = PlayerUiState()
        if (clearSelection) {
            selectedCard = null
            playerQueueCards = emptyList()
            activeQueueIndex = -1
            currentPlaylistDetail = null
        }
        updateMiniPlayer()
    }

    private fun startPlayerReadyTimeout(card: ContentCardSpec) {
        cancelPlayerReadyTimeout()
        val token = ++playerLoadToken
        val timeout = Runnable {
            if (token != playerLoadToken || lastLoadedPlayableId != card.id || playerUiState.isReady) return@Runnable
            updatePlayerUi(playerUiState.copy(isLoading = false, errorMessage = "Player did not report ready within 15 seconds."))
        }
        playerTimeoutRunnable = timeout
        playerTimeoutHandler.postDelayed(timeout, PLAYER_READY_TIMEOUT_MS)
    }

    private fun cancelPlayerReadyTimeout() {
        playerTimeoutRunnable?.let(playerTimeoutHandler::removeCallbacks)
        playerTimeoutRunnable = null
    }

    private fun updatePlayerUi(nextState: PlayerUiState) {
        playerUiState = nextState
        val selected = selectedCard
        val stateLabel = when {
            nextState.errorMessage != null -> "ERROR"
            nextState.isLoading -> "LOADING"
            nextState.isPlaying -> "NOW PLAYING"
            nextState.isReady -> "PAUSED"
            else -> "IDLE"
        }
        playerStateView?.text = stateLabel
        playerTrackView?.text = nextState.trackTitle ?: selected?.title.orEmpty()
        playerArtistView?.text = nextState.artist ?: selected?.creatorName ?: selected?.subtitle.orEmpty()
        playerErrorView?.apply {
            text = nextState.errorMessage.orEmpty()
            visibility = if (nextState.errorMessage == null) View.GONE else View.VISIBLE
        }
        playerPositionView?.text = formatTime(nextState.positionMs)
        playerDurationView?.text = formatTime(nextState.durationMs)
        playerWaveformView?.setProgress(nextState.positionMs, nextState.durationMs)
        playerPlayControl?.text = if (nextState.isPlaying) "Ⅱ" else "▶"
        queueRowsById.forEach { (id, row) -> row.background = trackRowBackground(row.hasFocus(), id == selected?.id) }
        updateMiniPlayer()
    }

    private fun updateMiniPlayer() {
        if (!::miniTitle.isInitialized) return
        val selected = selectedCard
        if (selected == null) {
            miniArtwork.setImageDrawable(null)
            miniArtwork.alpha = 0f
            miniTitle.text = "Nothing playing"
            miniContext.text = "Select a track from Home, Search, or Library"
            miniPosition.text = "0:00"
            miniRemaining.text = ""
            miniPlayGlyph.text = "▶"
            miniStatus.text = "IDLE"
            miniProgressPlayed.layoutParams = FrameLayout.LayoutParams(0, metrics.px(6))
            return
        }
        miniArtwork.alpha = 1f
        TvArtworkLoader.load(this, selected.artworkUrl ?: selected.creatorAvatarUrl, miniArtwork, metrics.px(48), metrics.px(48))
        miniTitle.text = playerUiState.trackTitle ?: selected.title
        miniContext.text = listOfNotNull(
            playerUiState.artist ?: selected.creatorName,
            currentPlaylistDetail?.title
        ).joinToString("  •  ").ifBlank { selected.subtitle }
        miniPosition.text = formatTime(playerUiState.positionMs)
        val remaining = (playerUiState.durationMs - playerUiState.positionMs).coerceAtLeast(0L)
        miniRemaining.text = if (playerUiState.durationMs > 0L) "−${formatTime(remaining)}" else ""
        miniPlayGlyph.text = if (playerUiState.isPlaying) "Ⅱ" else "▶"
        miniStatus.text = when {
            playerUiState.errorMessage != null -> "ERROR"
            playerUiState.isLoading -> "LOADING"
            playerUiState.isPlaying -> "PLAYING"
            playerUiState.isReady -> "PAUSED"
            else -> "IDLE"
        }
        val ratio = if (playerUiState.durationMs > 0L) {
            playerUiState.positionMs.toFloat() / playerUiState.durationMs
        } else 0f
        miniProgressPlayed.layoutParams = FrameLayout.LayoutParams(
            (metrics.px(610) * ratio.coerceIn(0f, 1f)).roundToInt(),
            metrics.px(6)
        )
    }

    override fun onNavigationBlocked(url: String, reason: WebViewHostConfig.BlockReason, message: String) {
        lastBlockedNavigation = "$url (${reason.name})"
        if (currentScreen == AppScreen.SETTINGS) refreshSettingsBody(authGateway.getCurrentState())
    }

    override fun onPageStarted(url: String) {
        if (!canDispatchTransportCommand()) return
        runOnUiThread { updatePlayerUi(playerUiState.copy(isLoading = true, errorMessage = null)) }
    }

    override fun onPageFinished(url: String) {
        if (!canDispatchTransportCommand()) return
        runOnUiThread { updatePlayerUi(playerUiState.copy(isLoading = false)) }
    }

    override fun onLoadError(url: String?, errorCode: Int, description: String) {
        if (!canDispatchTransportCommand()) return
        runOnUiThread { updatePlayerUi(playerUiState.copy(isLoading = false, errorMessage = "Load error: $description")) }
    }

    override fun onSslError(url: String?) {
        if (!canDispatchTransportCommand()) return
        runOnUiThread { updatePlayerUi(playerUiState.copy(isLoading = false, errorMessage = "Connection error")) }
    }

    override fun onLoadingStateChanged(isLoading: Boolean) {
        logBridgeEvent("loading=$isLoading")
        runOnUiThread { updatePlayerUi(playerUiState.copy(isLoading = isLoading, errorMessage = null)) }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        logBridgeEvent("playing=$isPlaying")
        if (isPlaying) playWhenPlayerReady = false
        cancelPlayerReadyTimeout()
        runOnUiThread { updatePlayerUi(playerUiState.copy(isPlaying = isPlaying, isReady = true, isLoading = false, errorMessage = null)) }
    }

    override fun onTrackChanged(trackId: String?, title: String?, artist: String?) {
        logBridgeEvent("track")
        cancelPlayerReadyTimeout()
        runOnUiThread {
            updatePlayerUi(playerUiState.copy(
                trackTitle = title?.takeIf { it.isNotBlank() },
                artist = artist?.takeIf { it.isNotBlank() },
                isReady = true,
                errorMessage = null
            ))
        }
    }

    override fun onProgressChanged(positionMs: Long, durationMs: Long) {
        runOnUiThread {
            updatePlayerUi(playerUiState.copy(
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.takeIf { it > 0L } ?: playerUiState.durationMs
            ))
        }
    }

    override fun onPlaybackError(errorCode: String, message: String) {
        logBridgeEvent("error=$errorCode")
        playWhenPlayerReady = false
        cancelPlayerReadyTimeout()
        runOnUiThread { updatePlayerUi(playerUiState.copy(isLoading = false, errorMessage = message)) }
    }

    override fun onReady() {
        logBridgeEvent("ready")
        cancelPlayerReadyTimeout()
        runOnUiThread {
            updatePlayerUi(playerUiState.copy(isLoading = false, isReady = true, errorMessage = null))
            selectedCard?.id?.let(::startPendingPlaybackIfReady)
        }
    }

    private fun startPendingPlaybackIfReady(trackId: String) {
        if (!playWhenPlayerReady || lastLoadedPlayableId != trackId || !playerUiState.isReady) return
        playWhenPlayerReady = false
        sendPlayCommand()
    }

    private fun logBridgeEvent(event: String) {
        if (!hasLoggedFirstBridgeEvent) {
            hasLoggedFirstBridgeEvent = true
            Log.i(TAG, "First player bridge event: $event")
        }
    }

    private fun buildSettingsView(): View {
        val state = authGateway.getCurrentState()
        val appInfo = buildDiagnosticsBody(state)
        val diagnosticsModel = DiagnosticsScreenFactory.create(
            onBootstrapSession = { authGateway.bootstrapSession() },
            onPollSession = { authGateway.pollSession() },
            onRefreshSession = { authGateway.refreshSession() },
            onDebugAuthenticateSession = if (BuildConfig.DEBUG) ({ authGateway.debugAuthenticateSession() }) else null,
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
        return renderSettingsScreen(SettingsScreenFactory.create().copy(
            title = "Settings & Diagnostics",
            body = appInfo,
            actions = diagnosticsModel.actions
        ))
    }

    private fun renderSettingsScreen(model: ScreenViewModel): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            setPadding(metrics.px(64), metrics.px(28), metrics.px(64), metrics.px(28))
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(root)
        root.addView(label(model.title, 24f, TvDesign.TEXT, bold = true))
        val buttons = model.actions.map { action ->
            focusButton(action.label, 360, 58) {
                action.onClick()
                refreshSettingsBody(authGateway.getCurrentState())
            }
        }
        buttons.chunked(3).forEach { group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEach { button ->
                row.addView(button, LinearLayout.LayoutParams(metrics.px(360), metrics.px(58)).apply {
                    marginEnd = metrics.px(14)
                    topMargin = metrics.px(16)
                })
            }
            root.addView(row)
        }
        root.addView(label(model.body, 14f, TvDesign.MUTED).apply {
            id = R.id.panelBody
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(metrics.px(20), metrics.px(20), metrics.px(20), metrics.px(20))
            background = TvDesign.rounded(TvDesign.SURFACE, metrics.px(8))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = metrics.px(24) })
        scroll.post { buttons.firstOrNull()?.requestFocus() }
        return scroll
    }

    private fun refreshSettingsBody(state: AuthSessionState) {
        if (currentScreen == AppScreen.SETTINGS) {
            contentFrame.findViewById<TextView>(R.id.panelBody)?.text = buildDiagnosticsBody(state)
        }
    }

    private fun refreshContentForCurrentScreen(state: AuthSessionState) {
        if (!state.isAuthenticated) return
        if (currentScreen == AppScreen.HOME || currentScreen == AppScreen.LIBRARY) {
            requestContentFor(currentScreen, state)
        }
    }

    private fun buildDiagnosticsBody(state: AuthSessionState): String {
        val webViewState = webHost.getDiagnosticState()
        return listOf(
            "App Version: ${BuildConfig.VERSION_NAME}",
            "Build: ${if (BuildConfig.DEBUG) "Debug" else "Release"}",
            "Backend: ${BuildConfig.API_BASE_URL}",
            "Window: ${metrics.windowWidth}x${metrics.windowHeight} @ ${metrics.scale}",
            "",
            "Session: ${state.sessionId ?: "none"}",
            "Status: ${state.phase.label}",
            "Authenticated: ${state.isAuthenticated}",
            "",
            "WebView Hardened: ${webViewState.hardeningEnabled}",
            "Controlled Host: ${webViewState.entryUrl}",
            "Last Blocked: ${lastBlockedNavigation ?: "none"}",
            "Last Error: ${webViewState.lastError ?: "none"}",
            "",
            "Selected: ${selectedCard?.title ?: "none"}",
            "Queue size: ${playerQueueCards.size}",
            "Position: ${formatTime(playerUiState.positionMs)} / ${formatTime(playerUiState.durationMs)}"
        ).joinToString("\n")
    }

    private fun routeByAuthState(state: AuthSessionState) {
        if (userInSettings) return
        runOnUiThread {
            when (state.phase) {
                AuthSessionPhase.AUTHENTICATED -> if (currentScreen == AppScreen.LOGIN_REQUIRED) navigateTo(AppScreen.HOME)
                AuthSessionPhase.AWAITING_AUTH, AuthSessionPhase.EXPIRED, AuthSessionPhase.ERROR -> {
                    if (currentScreen != AppScreen.LOGIN_REQUIRED && currentScreen != AppScreen.SETTINGS) {
                        navigateTo(AppScreen.LOGIN_REQUIRED)
                    } else refreshLoginRequiredBody(state)
                }
                else -> if (currentScreen == AppScreen.LOGIN_REQUIRED) refreshLoginRequiredBody(state)
            }
        }
    }

    private fun buildLoginRequiredView(state: AuthSessionState): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        root.addView(label("SIGN IN REQUIRED", 14f, TvDesign.ORANGE, bold = true))
        root.addView(label("Private Cloud TV", 34f, TvDesign.TEXT, bold = true).apply {
            setPadding(0, metrics.px(14), 0, metrics.px(10))
        })
        root.addView(label(loginRequiredMessage(state), 18f, TvDesign.MUTED).apply {
            id = R.id.panelBody
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, metrics.px(24))
        })
        val primary = focusButton(loginPrimaryActionLabel(state), 320, 58) { startProviderSignIn() }.apply {
            id = R.id.login_primary_action
        }
        root.addView(primary, LinearLayout.LayoutParams(metrics.px(320), metrics.px(58)))
        if (BuildConfig.DEBUG) {
            root.addView(focusButton("Use Debug Fallback", 280, 58) {
                if (authGateway.getCurrentState().phase == AuthSessionPhase.AWAITING_AUTH) {
                    authGateway.debugAuthenticateSession()
                } else {
                    authGateway.bootstrapSession()
                    handler.postDelayed({
                        if (authGateway.getCurrentState().phase == AuthSessionPhase.AWAITING_AUTH) {
                            authGateway.debugAuthenticateSession()
                        }
                    }, 400L)
                }
            }, LinearLayout.LayoutParams(metrics.px(280), metrics.px(58)).apply { topMargin = metrics.px(14) })
        }
        root.post { primary.requestFocus() }
        return root
    }

    private fun refreshLoginRequiredBody(state: AuthSessionState) {
        if (currentScreen != AppScreen.LOGIN_REQUIRED) return
        contentFrame.findViewById<TextView>(R.id.panelBody)?.text = loginRequiredMessage(state)
        contentFrame.findViewById<TextView>(R.id.login_primary_action)?.text = loginPrimaryActionLabel(state)
    }

    private fun loginPrimaryActionLabel(state: AuthSessionState): String = when (state.phase) {
        AuthSessionPhase.AWAITING_AUTH -> "Check Sign-In Status"
        AuthSessionPhase.BOOTSTRAPPING, AuthSessionPhase.REFRESHING -> "Checking…"
        else -> "Start Provider Sign In"
    }

    private fun loginRequiredMessage(state: AuthSessionState): String = when (state.phase) {
        AuthSessionPhase.BOOTSTRAPPING -> "Checking your session…"
        AuthSessionPhase.REFRESHING -> "Refreshing your session…"
        AuthSessionPhase.ERROR -> "We couldn't reach the backend.\n${state.lastErrorMessage ?: "Please try again shortly."}"
        AuthSessionPhase.EXPIRED -> "Your session has expired. Please sign in again."
        AuthSessionPhase.AWAITING_AUTH -> providerSignInInstructions(state)
        else -> "Please sign in to continue."
    }

    private fun providerSignInInstructions(state: AuthSessionState): String {
        val signInUrl = state.verificationUriComplete ?: state.verificationUri
        val urlLine = if (signInUrl.isNullOrBlank()) "Waiting for sign-in link…" else "Open this link on your phone or computer:\n$signInUrl"
        val codeLine = state.userCode?.let { "\n\nCode: $it" }.orEmpty()
        return "$urlLine$codeLine\n\nAfter provider authorization, this TV will continue automatically."
    }

    private fun startProviderSignIn() {
        when (authGateway.getCurrentState().phase) {
            AuthSessionPhase.AWAITING_AUTH -> authGateway.pollSession()
            AuthSessionPhase.BOOTSTRAPPING, AuthSessionPhase.REFRESHING -> Unit
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
                if (state.phase == AuthSessionPhase.AWAITING_AUTH) authGateway.pollSession()
                handler.postDelayed(this, SIGN_IN_POLL_INTERVAL_MS)
            }
        }
        providerSignInPollRunnable = runnable
        handler.postDelayed(runnable, 1_000L)
    }

    private fun stopProviderSignInPolling() {
        providerSignInPollRunnable?.let(handler::removeCallbacks)
        providerSignInPollRunnable = null
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.textPx(size))
        includeFontPadding = false
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun focusButton(
        value: String,
        width: Int,
        height: Int,
        action: () -> Unit
    ): TextView = label(value, 16f, Color.WHITE, bold = true).apply {
        id = View.generateViewId()
        gravity = Gravity.CENTER
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        background = TvDesign.rounded(TvDesign.SURFACE_RAISED, metrics.px(10), metrics.px(1), TvDesign.BORDER)
        layoutParams = FrameLayout.LayoutParams(metrics.px(width), metrics.px(height))
        setOnClickListener { action() }
        setOnFocusChangeListener { view, focused ->
            view.background = TvDesign.rounded(
                if (value == "Search") TvDesign.ORANGE else TvDesign.SURFACE_RAISED,
                metrics.px(10),
                metrics.px(if (focused) 3 else 1),
                if (focused) TvDesign.YELLOW else TvDesign.BORDER
            )
        }
    }

    private fun formatTime(milliseconds: Long): String {
        if (milliseconds <= 0L) return "0:00"
        val seconds = milliseconds / 1_000L
        val hours = seconds / 3_600L
        val minutes = (seconds % 3_600L) / 60L
        val remainder = seconds % 60L
        return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, remainder)
        else "%d:%02d".format(minutes, remainder)
    }

    data class PlayerUiState(
        val isLoading: Boolean = false,
        val isReady: Boolean = false,
        val isPlaying: Boolean = false,
        val trackTitle: String? = null,
        val artist: String? = null,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val errorMessage: String? = null
    )

    companion object {
        private const val TAG = "MainActivity"
        private const val PLAYER_READY_TIMEOUT_MS = 15_000L
        private const val SIGN_IN_POLL_INTERVAL_MS = 5_000L
        private val COLLECTION_HEADER_SCREENS = listOf(
            AppScreen.HOME,
            AppScreen.SEARCH,
            AppScreen.LIBRARY,
            AppScreen.PLAYER,
            AppScreen.SETTINGS
        )
    }

    private enum class CollectionFocusZone {
        HEADER,
        TRACKS,
        WAVEFORM,
        OTHER
    }
}
