package com.neilpontecorvo.soundcloudfiretv.webview

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * Minimal JavaScript bridge for native-to-web player communication.
 *
 * SECURITY BOUNDARY DOCUMENTATION:
 *
 * This bridge exposes a minimal surface area to JavaScript running in the WebView.
 * The design follows these principles:
 *
 * 1. EXPLICIT COMMANDS ONLY: No generic eval-style or arbitrary-command surfaces.
 *    Each exposed method performs one specific, well-defined action.
 *
 * 2. INPUT VALIDATION: All inputs from JavaScript are validated before use.
 *    String inputs are sanitized and bounded in length.
 *
 * 3. NO SENSITIVE DATA EXPOSURE: The bridge never exposes tokens, secrets,
 *    or session credentials to JavaScript.
 *
 * 4. LOGGING WITHOUT LEAKING: Diagnostic logs never include sensitive data.
 *
 * 5. UNIDIRECTIONAL BY DEFAULT: Prefer native-calls-web over web-calls-native
 *    where possible to reduce attack surface.
 *
 * EXPOSED INTERFACE NAME: "NativePlayer"
 * This name is injected into the WebView JavaScript context.
 *
 * CURRENT STATUS: Attached only to the controlled player host WebView.
 * It receives playback state from the native-owned host page and sends fixed
 * playback commands back to that page.
 */
class PlayerBridge(
    private val listener: BridgeEventListener? = null
) {
    /**
     * Listener for events received from JavaScript.
     */
    interface BridgeEventListener {
        fun onLoadingStateChanged(isLoading: Boolean)
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onTrackChanged(trackId: String?, title: String?, artist: String?)
        fun onPlaybackError(errorCode: String, message: String)
        fun onReady()
    }

    /**
     * Attaches this bridge to a WebView.
     *
     * The bridge will be accessible in JavaScript as:
     * ```javascript
     * window.NativePlayer.reportLoadingState(false);
     * window.NativePlayer.reportPlaybackState(true);
     * window.NativePlayer.reportTrackChange("123", "Song Title", "Artist");
     * ```
     */
    fun attachToWebView(webView: WebView) {
        webView.addJavascriptInterface(this, BRIDGE_NAME)
        Log.i(TAG, "PlayerBridge attached to WebView as '$BRIDGE_NAME'")
    }

    /**
     * Detaches the bridge from a WebView.
     */
    fun detachFromWebView(webView: WebView) {
        webView.removeJavascriptInterface(BRIDGE_NAME)
        Log.i(TAG, "PlayerBridge detached from WebView")
    }

    // =========================================================================
    // JavaScript-callable methods (web-to-native)
    // =========================================================================

    /**
     * Called by JavaScript to report player loading state.
     *
     * @param isLoading True while the controlled player host is preparing playback UI.
     */
    @JavascriptInterface
    fun reportLoadingState(isLoading: Boolean) {
        Log.d(TAG, "JS -> Native: loading state changed: isLoading=$isLoading")
        listener?.onLoadingStateChanged(isLoading)
    }

    /**
     * Called by JavaScript to report playback state changes.
     *
     * @param isPlaying True if playback is active, false if paused/stopped
     */
    @JavascriptInterface
    fun reportPlaybackState(isPlaying: Boolean) {
        Log.d(TAG, "JS -> Native: playback state changed: isPlaying=$isPlaying")
        listener?.onPlaybackStateChanged(isPlaying)
    }

    /**
     * Called by JavaScript to report the current track.
     *
     * @param trackId Track identifier (sanitized, max 64 chars)
     * @param title Track title (sanitized, max 256 chars)
     * @param artist Artist name (sanitized, max 256 chars)
     */
    @JavascriptInterface
    fun reportTrackChange(trackId: String?, title: String?, artist: String?) {
        val safeTrackId = sanitizeString(trackId, 64)
        val safeTitle = sanitizeString(title, 256)
        val safeArtist = sanitizeString(artist, 256)

        Log.d(TAG, "JS -> Native: track changed: id=$safeTrackId, title=$safeTitle")
        listener?.onTrackChanged(safeTrackId, safeTitle, safeArtist)
    }

    /**
     * Called by JavaScript to report playback errors.
     *
     * @param errorCode Error code identifier (sanitized, max 32 chars)
     * @param message Error message (sanitized, max 256 chars)
     */
    @JavascriptInterface
    fun reportPlaybackError(errorCode: String?, message: String?) {
        val safeCode = sanitizeString(errorCode, 32) ?: "unknown"
        val safeMessage = sanitizeString(message, 256) ?: "Unknown error"

        Log.e(TAG, "JS -> Native: playback error: code=$safeCode, message=$safeMessage")
        listener?.onPlaybackError(safeCode, safeMessage)
    }

    /**
     * Called by JavaScript to signal the player is ready.
     */
    @JavascriptInterface
    fun reportReady() {
        Log.i(TAG, "JS -> Native: player ready")
        listener?.onReady()
    }

    // =========================================================================
    // Native-to-JavaScript commands
    // =========================================================================

    /**
     * Sends a play command to the web player.
     */
    fun sendPlay(webView: WebView) {
        executePlayerCommand(webView, PlayerCommand.PLAY)
    }

    /**
     * Sends a pause command to the web player.
     */
    fun sendPause(webView: WebView) {
        executePlayerCommand(webView, PlayerCommand.PAUSE)
    }

    /**
     * Sends a toggle play/pause command to the web player.
     */
    fun sendTogglePlayPause(webView: WebView) {
        executePlayerCommand(webView, PlayerCommand.TOGGLE_PLAY_PAUSE)
    }

    /**
     * Sends a next track command to the web player.
     */
    fun sendNext(webView: WebView) {
        executePlayerCommand(webView, PlayerCommand.NEXT)
    }

    /**
     * Sends a previous track command to the web player.
     */
    fun sendPrevious(webView: WebView) {
        executePlayerCommand(webView, PlayerCommand.PREVIOUS)
    }

    private fun executePlayerCommand(webView: WebView, command: PlayerCommand) {
        Log.d(TAG, "Native -> JS: sending command ${command.name}")
        webView.evaluateJavascript(command.javascript, null)
    }

    /**
     * Enumeration of supported player commands with their JavaScript implementations.
     *
     * These commands use DOM selectors targeting SoundCloud's player controls.
     * The selectors should be validated against the actual SoundCloud web player
     * and updated if the DOM structure changes.
     */
    enum class PlayerCommand(val javascript: String) {
        PLAY(
            """
            (function() {
                if (window.FireTvPlayerHost) window.FireTvPlayerHost.command('play');
            })();
            """.trimIndent()
        ),
        PAUSE(
            """
            (function() {
                if (window.FireTvPlayerHost) window.FireTvPlayerHost.command('pause');
            })();
            """.trimIndent()
        ),
        TOGGLE_PLAY_PAUSE(
            """
            (function() {
                if (window.FireTvPlayerHost) window.FireTvPlayerHost.command('toggle');
            })();
            """.trimIndent()
        ),
        NEXT(
            """
            (function() {
                if (window.FireTvPlayerHost) window.FireTvPlayerHost.command('next');
            })();
            """.trimIndent()
        ),
        PREVIOUS(
            """
            (function() {
                if (window.FireTvPlayerHost) window.FireTvPlayerHost.command('previous');
            })();
            """.trimIndent()
        )
    }

    private fun sanitizeString(input: String?, maxLength: Int): String? {
        if (input == null) return null
        // Trim whitespace, limit length, remove control characters
        return input
            .trim()
            .take(maxLength)
            .replace(Regex("[\\x00-\\x1F\\x7F]"), "")
    }

    companion object {
        private const val TAG = "PlayerBridge"

        /**
         * The JavaScript interface name exposed to the WebView.
         * Accessible in JS as: window.NativePlayer
         */
        const val BRIDGE_NAME = "NativePlayer"
    }
}
