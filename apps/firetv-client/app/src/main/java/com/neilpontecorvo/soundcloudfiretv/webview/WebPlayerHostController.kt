package com.neilpontecorvo.soundcloudfiretv.webview

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Hardened WebView player host controller.
 *
 * This controller manages WebView configuration with explicit security settings:
 * - Controlled host entry via [WebViewHostConfig]
 * - Navigation blocking via [HardenedWebViewClient]
 * - Production-safe WebView settings
 * - Debug vs release behavior differentiation
 * - Diagnostic state tracking
 *
 * Security properties:
 * - JavaScript enabled (required for player functionality)
 * - DOM storage enabled (required for player state)
 * - File access disabled (no local file loading)
 * - Content access disabled (no content provider access)
 * - Mixed content blocked in production
 * - WebView debugging only in debug builds
 * - Safe browsing enabled where available
 */
class WebPlayerHostController(
    private val config: WebViewHostConfig = WebViewHostConfig.DEFAULT,
    private val isDebugBuild: Boolean = false
) {
    private var webViewClient: HardenedWebViewClient? = null
    private var lastLoadError: String? = null

    /**
     * Navigation listener for diagnostic state updates.
     */
    var navigationListener: HardenedWebViewClient.NavigationListener? = null

    /**
     * Configures a WebView with hardened production-safe settings.
     *
     * Settings applied:
     * - JavaScript: enabled (required for SoundCloud player)
     * - DOM storage: enabled (required for player state persistence)
     * - Media playback: no gesture required (TV remote UX)
     * - File access: disabled
     * - Content access: disabled
     * - Mixed content: blocked
     * - Geolocation: disabled
     * - Debugging: debug builds only
     * - Safe browsing: enabled where available
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView) {
        // Create and attach hardened client
        webViewClient = HardenedWebViewClient(config, navigationListener)
        webView.webViewClient = webViewClient!!

        with(webView.settings) {
            // Required for player functionality
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false

            // Cache behavior
            cacheMode = WebSettings.LOAD_DEFAULT

            // Security hardening: disable file and content access
            allowFileAccess = false
            allowContentAccess = false

            // Security hardening: block mixed content
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            // Security hardening: disable geolocation
            setGeolocationEnabled(false)

            // Security hardening: disable form data saving
            saveFormData = false

            // Security hardening: disable password saving
            @Suppress("DEPRECATION")
            savePassword = false

            // User agent: use default system user agent
            // Do not override to avoid fingerprinting detection

            // Database and app cache settings
            databaseEnabled = true

            // Text zoom: disable for TV (fixed layout)
            textZoom = 100

            // Viewport: use wide viewport for TV display
            useWideViewPort = true
            loadWithOverviewMode = true

            // Zoom: disable for TV (fixed display)
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        // Enable safe browsing where available (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.settings.safeBrowsingEnabled = true
        }

        // Enable WebView debugging in debug builds only
        WebView.setWebContentsDebuggingEnabled(isDebugBuild)

        // Cookie settings
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        Log.i(TAG, "WebView configured with hardened settings (debug=$isDebugBuild)")
        Log.i(TAG, "Controlled host: ${config.entryUrl}")
        Log.i(TAG, "Allowed hosts: ${config.allowedHosts}")
    }

    /**
     * Loads the controlled player entry URL.
     *
     * Only the configured entry URL is permitted. Any other URL passed to this
     * method is ignored and logged as a warning.
     *
     * @param webView The WebView to load content into
     * @param url Optional URL override (must match entry URL or be null)
     */
    fun loadPlayer(webView: WebView, url: String? = null) {
        val targetUrl = url ?: config.entryUrl

        // Validate that the target URL matches the configured entry URL
        if (url != null && url != config.entryUrl) {
            Log.w(TAG, "loadPlayer called with non-entry URL, using configured entry URL instead")
        }

        // Validate URL is allowed before loading
        val validation = config.validateUrl(config.entryUrl)
        if (validation is WebViewHostConfig.ValidationResult.Blocked) {
            lastLoadError = "Entry URL blocked: ${validation.message}"
            Log.e(TAG, "Cannot load entry URL: ${validation.message}")
            return
        }

        Log.i(TAG, "Loading controlled player entry: ${config.entryUrl}")
        webView.loadUrl(config.entryUrl)
    }

    /**
     * Reloads the current page.
     */
    fun reload(webView: WebView) {
        Log.d(TAG, "Reloading WebView")
        webView.reload()
    }

    /**
     * Clears all cookies.
     */
    fun clearCookies() {
        Log.d(TAG, "Clearing cookies")
        val manager = CookieManager.getInstance()
        manager.removeAllCookies(null)
        manager.flush()
    }

    /**
     * Clears WebView session state (history, cache, form data).
     */
    fun clearSession(webView: WebView) {
        Log.d(TAG, "Clearing WebView session")
        webView.clearHistory()
        webView.clearCache(true)
        webView.clearFormData()
        webViewClient?.clearDiagnosticState()
        lastLoadError = null
    }

    /**
     * Gets the current diagnostic state for display in settings/diagnostics.
     */
    fun getDiagnosticState(): WebViewDiagnosticState {
        val clientState = webViewClient?.getDiagnosticState()
        return WebViewDiagnosticState(
            entryUrl = config.entryUrl,
            allowedHosts = config.allowedHosts.joinToString(", "),
            currentUrl = clientState?.currentUrl,
            isLoading = clientState?.isLoading ?: false,
            lastBlockedUrl = clientState?.lastBlockedUrl,
            lastBlockedReason = clientState?.lastBlockedReason,
            lastError = clientState?.lastError ?: lastLoadError,
            isDebugBuild = isDebugBuild,
            hardeningEnabled = true
        )
    }

    /**
     * Gets the configured entry URL.
     */
    fun getEntryUrl(): String = config.entryUrl

    /**
     * Gets the set of allowed hosts.
     */
    fun getAllowedHosts(): Set<String> = config.allowedHosts

    /**
     * Diagnostic state for WebView display.
     */
    data class WebViewDiagnosticState(
        val entryUrl: String,
        val allowedHosts: String,
        val currentUrl: String?,
        val isLoading: Boolean,
        val lastBlockedUrl: String?,
        val lastBlockedReason: String?,
        val lastError: String?,
        val isDebugBuild: Boolean,
        val hardeningEnabled: Boolean
    )

    companion object {
        private const val TAG = "WebPlayerHostController"
    }
}
