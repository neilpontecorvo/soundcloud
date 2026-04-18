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
        val result = config.validateUrl(url)

        return when (result) {
            is WebViewHostConfig.ValidationResult.Allowed -> {
                Log.d(TAG, "Allowing navigation to: ${sanitizeUrlForLog(url)}")
                false // Allow WebView to handle the navigation
            }
            is WebViewHostConfig.ValidationResult.Blocked -> {
                Log.w(TAG, "Blocked navigation to: ${sanitizeUrlForLog(url)} - ${result.reason}: ${result.message}")
                lastBlockedUrl = sanitizeUrlForStorage(url)
                lastBlockedReason = result.reason
                listener?.onNavigationBlocked(sanitizeUrlForStorage(url) ?: "unknown", result.reason, result.message)
                true // Block the navigation
            }
        }
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url?.toString()
        val result = config.validateUrl(url)

        if (result is WebViewHostConfig.ValidationResult.Blocked) {
            Log.d(TAG, "Blocking subresource from disallowed origin: ${sanitizeUrlForLog(url)}")
            // Return empty response to block disallowed subresources
            return WebResourceResponse("text/plain", "UTF-8", null)
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        isLoading = true
        currentUrl = sanitizeUrlForStorage(url)
        Log.d(TAG, "Page started: ${sanitizeUrlForLog(url)}")
        listener?.onPageStarted(sanitizeUrlForStorage(url) ?: "unknown")
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        isLoading = false
        currentUrl = sanitizeUrlForStorage(url)
        Log.d(TAG, "Page finished: ${sanitizeUrlForLog(url)}")
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
