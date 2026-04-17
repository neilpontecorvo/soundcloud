package com.neilpontecorvo.soundcloudfiretv.webview

import android.net.Uri

/**
 * Controlled host configuration for WebView player boundary.
 *
 * This configuration defines the explicit allowlist of origins/hosts
 * that the WebView is permitted to load and navigate to. Any navigation
 * outside this boundary is blocked and logged.
 *
 * Security properties:
 * - Entry URL is the only externally loadable starting point
 * - Allowed hosts restrict all subsequent navigation
 * - Subresource origins can differ from primary host where required
 */
data class WebViewHostConfig(
    /**
     * The controlled entry URL for the player WebView.
     * This is the only URL that loadPlayer() will directly load.
     */
    val entryUrl: String,

    /**
     * Provider widget URL embedded by the controlled TV host page.
     * This keeps the WebView on a playback-only surface instead of a broad web page.
     */
    val playerWidgetUrl: String,

    /**
     * Allowed hosts for navigation. Any navigation to a host not in this list
     * will be blocked. Include the primary host and any required subresource hosts.
     */
    val allowedHosts: Set<String>,

    /**
     * Allowed schemes. Typically https only for production.
     */
    val allowedSchemes: Set<String> = setOf("https"),

    /**
     * Whether to allow navigation to subpaths of allowed hosts.
     * When true, any path under an allowed host is permitted.
     * When false, only exact entry URL is permitted.
     */
    val allowSubpaths: Boolean = true
) {
    /**
     * Validates whether a URL is permitted by this configuration.
     *
     * @param url The URL to validate
     * @return ValidationResult indicating whether the URL is allowed and why
     */
    fun validateUrl(url: String?): ValidationResult {
        if (url.isNullOrBlank()) {
            return ValidationResult.Blocked(BlockReason.EMPTY_URL, "URL is null or blank")
        }

        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            return ValidationResult.Blocked(BlockReason.MALFORMED_URL, "Failed to parse URL")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme == null || scheme !in allowedSchemes) {
            return ValidationResult.Blocked(
                BlockReason.DISALLOWED_SCHEME,
                "Scheme '$scheme' not in allowed schemes: $allowedSchemes"
            )
        }

        val host = uri.host?.lowercase()
        if (host == null || host !in allowedHosts) {
            return ValidationResult.Blocked(
                BlockReason.DISALLOWED_HOST,
                "Host '$host' not in allowed hosts"
            )
        }

        return ValidationResult.Allowed
    }

    /**
     * Checks if a URL is allowed without returning detailed reason.
     */
    fun isUrlAllowed(url: String?): Boolean = validateUrl(url) is ValidationResult.Allowed

    sealed class ValidationResult {
        object Allowed : ValidationResult()
        data class Blocked(val reason: BlockReason, val message: String) : ValidationResult()
    }

    enum class BlockReason {
        EMPTY_URL,
        MALFORMED_URL,
        DISALLOWED_SCHEME,
        DISALLOWED_HOST
    }

    companion object {
        /**
         * Default configuration for SoundCloud player boundary.
         *
         * Allows:
         * - soundcloud.com and www.soundcloud.com for primary player content
         * - sndcdn.com for CDN-hosted static assets and media
         * - widget.sndcdn.com for embedded widget resources
         *
         * This list should be reviewed and minimized based on actual runtime requirements.
         */
        val DEFAULT = WebViewHostConfig(
            entryUrl = "https://soundcloud.com/tv-player-host",
            playerWidgetUrl = "https://w.soundcloud.com/player/?url=https%3A%2F%2Fsoundcloud.com%2Fdiscover&auto_play=false&visual=false&show_comments=false&hide_related=true&show_user=true&show_reposts=false&show_teaser=false",
            allowedHosts = setOf(
                "soundcloud.com",
                "www.soundcloud.com",
                "m.soundcloud.com",
                "w.soundcloud.com",
                "api-widget.soundcloud.com",
                "api-v2.soundcloud.com",
                "sndcdn.com",
                "a-v2.sndcdn.com",
                "cf-media.sndcdn.com",
                "cf-hls-media.sndcdn.com",
                "i1.sndcdn.com",
                "widget.sndcdn.com"
            ),
            allowedSchemes = setOf("https"),
            allowSubpaths = true
        )

        /**
         * Strict configuration for testing that only allows the primary host.
         */
        val STRICT = WebViewHostConfig(
            entryUrl = "https://soundcloud.com/tv-player-host",
            playerWidgetUrl = "https://w.soundcloud.com/player/?url=https%3A%2F%2Fsoundcloud.com%2Fdiscover&auto_play=false&visual=false&show_comments=false",
            allowedHosts = setOf("soundcloud.com", "www.soundcloud.com", "w.soundcloud.com"),
            allowedSchemes = setOf("https"),
            allowSubpaths = true
        )
    }
}
