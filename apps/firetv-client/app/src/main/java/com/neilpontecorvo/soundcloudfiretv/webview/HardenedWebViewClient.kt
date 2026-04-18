package com.neilpontecorvo.soundcloudfiretv.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Hardened WebViewClient that enforces controlled host navigation policy.
 *
 * Security features:
 * - Blocks navigation to hosts not in the allowlist
 * - Prevents arbitrary redirects and off-domain browsing
 * - Logs blocked navigation attempts for diagnostics
 * - Enforces SSL certificate validation
 * - Tracks load state for diagnostics display
 *
 * This client should be used with [WebViewHostConfig] to define the allowed boundary.
 */
class HardenedWebViewClient(
    private val config: WebViewHostConfig,
    private val listener: NavigationListener? = null
) : WebViewClient() {

    private var currentUrl: String? = null
    private var lastBlockedUrl: String? = null
    private var lastBlockedReason: WebViewHostConfig.BlockReason? = null
    private var lastError: String? = null
    private var isLoading: Boolean = false

    /**
     * Interface for receiving navigation events for diagnostics and state tracking.
     */
    interface NavigationListener {
        fun onNavigationBlocked(url: String, reason: WebViewHostConfig.BlockReason, message: String)
        fun onPageStarted(url: String)
        fun onPageFinished(url: String)
        fun onLoadError(url: String?, errorCode: Int, description: String)
        fun onSslError(url: String?)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString()
        if (isControlledInjectedMainFrame(request, url)) {
            Log.i(
                TAG,
                "Allowing controlled injected main-frame navigation: ${sanitizeUrlForLog(url)}"
            )
            return false
        }

        // Any further top-level navigation away from the controlled injected document is
        // blocked unconditionally — even to allowlisted hosts. The player host is a
        // data: document; replacing it with any real page destroys the JS bridge and
        // the SC.Widget instance. Subframe / iframe navigation (e.g. the widget iframe)
        // does not go through shouldOverrideUrlLoading.
        if (request?.isForMainFrame == true) {
            Log.w(TAG, "Blocked top-level navigation away from controlled host: ${sanitizeUrlForLog(url)}")
            lastBlockedUrl = sanitizeUrlForStorage(url)
            lastBlockedReason = WebViewHostConfig.BlockReason.DISALLOWED_HOST
            listener?.onNavigationBlocked(
                sanitizeUrlForStorage(url) ?: "unknown",
                WebViewHostConfig.BlockReason.DISALLOWED_HOST,
                "Top-level navigation away from controlled host blocked"
            )
            return true
        }

        // Subframe navigation: apply host allowlist as usual.
        val result = config.validateUrl(url)

        return when (result) {
            is WebViewHostConfig.ValidationResult.Allowed -> {
                Log.d(TAG, "Allowing subframe navigation to: ${sanitizeUrlForLog(url)}")
                false
            }
            is WebViewHostConfig.ValidationResult.Blocked -> {
                Log.w(TAG, "Blocked subframe navigation to: ${sanitizeUrlForLog(url)} - ${result.reason}: ${result.message}")
                lastBlockedUrl = sanitizeUrlForStorage(url)
                lastBlockedReason = result.reason
                listener?.onNavigationBlocked(sanitizeUrlForStorage(url) ?: "unknown", result.reason, result.message)
                true
            }
        }
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url?.toString()
        if (isControlledInjectedMainFrame(request, url)) {
            // The top-level document is the HTML we just injected via loadData(...).
            // Do not classify it as a blocked subresource — that interferes with the
            // main-frame load lifecycle on Amazon WebView and prevents onPageFinished
            // from firing. Subresource data: URLs (e.g. inline fonts within the iframe)
            // still fall through to the normal validation path below.
            Log.i(
                TAG,
                "Allowing controlled injected main-frame document: ${sanitizeUrlForLog(url)}"
            )
            return super.shouldInterceptRequest(view, request)
        }

        val result = config.validateUrl(url)

        if (result is WebViewHostConfig.ValidationResult.Blocked) {
            val host = try { android.net.Uri.parse(url).host } catch (_: Exception) { null }
            Log.w(
                TAG,
                "Blocked subresource host='$host' reason=${result.reason} url=${sanitizeUrlForLog(url)}"
            )
            // Return empty response to block disallowed subresources
            return WebResourceResponse("text/plain", "UTF-8", null)
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        isLoading = true
        currentUrl = sanitizeUrlForStorage(url)
        Log.i(TAG, "Page started: ${sanitizeUrlForLog(url)}")
        listener?.onPageStarted(sanitizeUrlForStorage(url) ?: "unknown")
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        isLoading = false
        currentUrl = sanitizeUrlForStorage(url)
        Log.i(TAG, "Page finished: ${sanitizeUrlForLog(url)}")
        listener?.onPageFinished(sanitizeUrlForStorage(url) ?: "unknown")
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        val url = request?.url?.toString()
        val errorCode = error?.errorCode ?: -1
        val description = error?.description?.toString() ?: "Unknown error"

        // Only track main frame errors
        if (request?.isForMainFrame == true) {
            if (isSyntheticControlledLoadError(url)) {
                Log.w(
                    TAG,
                    "Ignoring non-fatal synthetic controlled host load error for ${sanitizeUrlForLog(url)}: $errorCode - $description"
                )
                return
            }
            lastError = "Error $errorCode: $description"
            Log.e(TAG, "Load error for ${sanitizeUrlForLog(url)}: $errorCode - $description")
            listener?.onLoadError(sanitizeUrlForStorage(url), errorCode, description)
        }
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        // Always reject SSL errors - do not call handler.proceed()
        Log.e(TAG, "SSL error for ${sanitizeUrlForLog(error?.url)}: ${error?.primaryError}")
        lastError = "SSL error: ${sslErrorToString(error?.primaryError ?: -1)}"
        listener?.onSslError(sanitizeUrlForStorage(error?.url))
        handler?.cancel()
    }

    /**
     * Returns the current diagnostic state for display in settings/diagnostics screen.
     */
    fun getDiagnosticState(): DiagnosticState = DiagnosticState(
        currentUrl = currentUrl,
        isLoading = isLoading,
        lastBlockedUrl = lastBlockedUrl,
        lastBlockedReason = lastBlockedReason?.name,
        lastError = lastError,
        allowedHosts = config.allowedHosts.joinToString(", "),
        entryUrl = config.entryUrl
    )

    /**
     * Clears diagnostic state (e.g., after session clear).
     */
    fun clearDiagnosticState() {
        currentUrl = null
        lastBlockedUrl = null
        lastBlockedReason = null
        lastError = null
        isLoading = false
    }

    /**
     * Diagnostic state snapshot for UI display.
     */
    data class DiagnosticState(
        val currentUrl: String?,
        val isLoading: Boolean,
        val lastBlockedUrl: String?,
        val lastBlockedReason: String?,
        val lastError: String?,
        val allowedHosts: String,
        val entryUrl: String
    )

    private fun sanitizeUrlForLog(url: String?): String {
        if (url == null) return "null"
        return try {
            val uri = android.net.Uri.parse(url)
            // Log scheme, host, and path - strip query params which may contain tokens
            "${uri.scheme}://${uri.host}${uri.path ?: ""}"
        } catch (e: Exception) {
            "[malformed]"
        }
    }

    private fun sanitizeUrlForStorage(url: String?): String? {
        if (url == null) return null
        return try {
            val uri = android.net.Uri.parse(url)
            // Store scheme, host, and path only - never store query params
            "${uri.scheme}://${uri.host}${uri.path ?: ""}"
        } catch (e: Exception) {
            null
        }
    }

    private fun isControlledEntryUrl(url: String?): Boolean {
        return sanitizeUrlForStorage(url) == sanitizeUrlForStorage(config.entryUrl)
    }

    /**
     * True when the request is the top-level document created by our intentional
     * `webView.loadData(...)` call. Amazon WebView surfaces this as `data:` (often
     * literally `data://null`) in both `shouldOverrideUrlLoading` and
     * `shouldInterceptRequest`. Classifying it as a disallowed host/scheme stalls
     * the load and blocks `onPageFinished`.
     *
     * Subresources with `data:` URLs (e.g. inline fonts inside the widget iframe)
     * are still subject to normal validation because they are not main-frame.
     */
    private fun isControlledInjectedMainFrame(
        request: WebResourceRequest?,
        url: String?
    ): Boolean {
        if (request?.isForMainFrame != true) return false
        val normalized = url?.trim()?.lowercase() ?: return false
        return normalized.startsWith("data:") || normalized == "about:blank"
    }

    private fun isSyntheticControlledLoadError(url: String?): Boolean {
        if (isControlledEntryUrl(url)) return true
        if (url == null) return true

        val normalized = url.trim().lowercase()
        return normalized == "data://null" || normalized.startsWith("data:")
    }

    private fun sslErrorToString(primaryError: Int): String = when (primaryError) {
        SslError.SSL_NOTYETVALID -> "Certificate not yet valid"
        SslError.SSL_EXPIRED -> "Certificate expired"
        SslError.SSL_IDMISMATCH -> "Certificate hostname mismatch"
        SslError.SSL_UNTRUSTED -> "Certificate not trusted"
        SslError.SSL_DATE_INVALID -> "Certificate date invalid"
        SslError.SSL_INVALID -> "Certificate invalid"
        else -> "Unknown SSL error ($primaryError)"
    }

    companion object {
        private const val TAG = "HardenedWebViewClient"
    }
}
