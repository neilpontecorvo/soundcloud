package com.neilpontecorvo.soundcloudfiretv.webview

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import java.net.URLEncoder

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
 * - JavaScript enabled (required for controlled player host functionality)
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
            // Required for controlled player host functionality
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
     * Loads the controlled TV player host page.
     *
     * Only the configured entry URL and widget URL are permitted. The loaded
     * document is native-owned HTML that embeds the approved playback widget.
     *
     * @param webView The WebView to load content into
     * @param url Optional selected content URL. It must be allowlisted and is
     * embedded through the controlled widget URL, never loaded as top-level web content.
     */
    fun loadPlayer(webView: WebView, url: String? = null): Boolean {
        val entryValidation = config.validateUrl(config.entryUrl)
        if (entryValidation is WebViewHostConfig.ValidationResult.Blocked) {
            lastLoadError = "Entry URL blocked: ${entryValidation.message}"
            Log.e(TAG, "Cannot load entry URL: ${entryValidation.message}")
            return false
        }

        val widgetUrl = buildWidgetUrl(url)
        if (widgetUrl == null) {
            Log.e(TAG, "Cannot load selected content URL: not allowlisted")
            return false
        }

        val widgetValidation = config.validateUrl(widgetUrl)
        if (widgetValidation is WebViewHostConfig.ValidationResult.Blocked) {
            lastLoadError = "Widget URL blocked: ${widgetValidation.message}"
            Log.e(TAG, "Cannot load widget URL: ${widgetValidation.message}")
            return false
        }

        Log.i(TAG, "Resolved controlled widget URL: ${sanitizeUrlForLog(widgetUrl)}")
        Log.i(TAG, "Loading controlled player entry via webView.loadData base64 (no base URL; config entry retained only for allowlist: ${sanitizeUrlForLog(config.entryUrl)})")
        lastLoadError = null
        val html = buildControlledPlayerHtml(widgetUrl)
        val htmlBase64 = Base64.encodeToString(
            html.toByteArray(Charsets.UTF_8),
            Base64.NO_PADDING or Base64.NO_WRAP
        )
        webView.loadData(htmlBase64, "text/html; charset=utf-8", "base64")
        return true
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

    private fun buildWidgetUrl(contentUrl: String?): String? {
        val selectedUrl = contentUrl?.takeIf { it.isNotBlank() }
            ?: return config.playerWidgetUrl

        val contentValidation = config.validateUrl(selectedUrl)
        if (contentValidation is WebViewHostConfig.ValidationResult.Blocked) {
            lastLoadError = "Content URL blocked: ${contentValidation.message}"
            return null
        }

        return "https://w.soundcloud.com/player/?url=${selectedUrl.urlEncode()}&auto_play=false&visual=false&show_comments=false&hide_related=true&show_user=true&show_reposts=false&show_teaser=false"
    }

    private fun buildControlledPlayerHtml(widgetUrl: String): String {
        val escapedWidgetUrl = widgetUrl.escapeHtml()
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta http-equiv="Content-Security-Policy" content="default-src 'none'; frame-src https://w.soundcloud.com; script-src 'unsafe-inline' https://w.soundcloud.com; style-src 'unsafe-inline'; img-src https: data:; connect-src https://api-widget.soundcloud.com https://api-v2.soundcloud.com https://w.soundcloud.com https://cf-media.sndcdn.com https://cf-hls-media.sndcdn.com https://wave.sndcdn.com https://playback.media-streaming.soundcloud.cloud;">
              <style>
                html, body {
                  margin: 0;
                  width: 100%;
                  height: 100%;
                  overflow: hidden;
                  background: #050505;
                  color: #f4f4f4;
                  font-family: sans-serif;
                }
                .shell {
                  box-sizing: border-box;
                  width: 100%;
                  height: 100%;
                  padding: 0;
                  background: #050505;
                }
                .frame {
                  width: 100%;
                  height: 100%;
                  border-radius: 8px;
                  overflow: hidden;
                  background: #050505;
                }
                iframe {
                  display: block;
                  width: 100%;
                  height: 100%;
                  border: 0;
                  background: #111;
                }
              </style>
            </head>
            <body>
              <div class="shell">
                <div class="frame">
                  <iframe id="providerPlayer" title="Player" allow="autoplay" src="$escapedWidgetUrl"></iframe>
                </div>
              </div>
              <script>
                try {
                  console.log('fire-tv: pre-api inline block executed');
                  if (window.NativePlayer && window.NativePlayer.reportBootstrap) {
                    window.NativePlayer.reportBootstrap('pre-api-inline');
                  }
                } catch (e) {}
              </script>
              <script src="https://w.soundcloud.com/player/api.js" onload="try{console.log('fire-tv: widget api onload fired');if(window.NativePlayer&&window.NativePlayer.reportBootstrap)window.NativePlayer.reportBootstrap('widget-api-onload');}catch(e){}" onerror="try{console.log('fire-tv: widget api onerror fired');if(window.NativePlayer&&window.NativePlayer.reportBootstrap)window.NativePlayer.reportBootstrap('widget-api-onerror');}catch(e){}"></script>
              <script>
                (function() {
                  try {
                    console.log('fire-tv: inline player script started');
                    if (window.NativePlayer && window.NativePlayer.reportBootstrap) {
                      window.NativePlayer.reportBootstrap('post-api-inline');
                    }
                  } catch (e) {}
                  var iframe = document.getElementById('providerPlayer');
                  var widget = null;
                  var isPlaying = false;
                  var didAttemptDebugPlay = false;
                  var lastProgressReportAt = 0;

                  function nativeCall(name) {
                    if (!window.NativePlayer || typeof window.NativePlayer[name] !== 'function') return;
                    var args = Array.prototype.slice.call(arguments, 1);
                    try { window.NativePlayer[name].apply(window.NativePlayer, args); } catch (error) {}
                  }

                  function debugPlayer(name, detail) {
                    var safeDetail = detail;
                    try {
                      if (safeDetail === undefined || safeDetail === null) safeDetail = '';
                      if (typeof safeDetail !== 'string') safeDetail = JSON.stringify(safeDetail);
                    } catch (error) {
                      safeDetail = String(detail);
                    }
                    try { console.log('fire-tv: player debug ' + name + '=' + safeDetail); } catch (error) {}
                    nativeCall('reportDebugEvent', String(name || 'unknown'), String(safeDetail || ''));
                  }

                  function finalIframeSrc() {
                    if (!iframe) return 'missing_iframe';
                    return iframe.getAttribute('src') || iframe.src || '';
                  }

                  function reportLoading(value) {
                    nativeCall('reportLoadingState', !!value);
                  }

                  function reportTrack() {
                    if (!widget || typeof widget.getCurrentSound !== 'function') return;
                    widget.getCurrentSound(function(sound) {
                      if (!sound) return;
                      var artist = sound.user && sound.user.username ? sound.user.username : '';
                      nativeCall('reportTrackChange', String(sound.id || ''), String(sound.title || ''), String(artist || ''));
                    });
                  }

                  function reportProgress(event, force) {
                    if (!widget || typeof widget.getDuration !== 'function') return;
                    var now = Date.now();
                    if (!force && now - lastProgressReportAt < 1000) return;
                    lastProgressReportAt = now;
                    var eventPosition = event && typeof event.currentPosition === 'number'
                      ? event.currentPosition
                      : null;
                    widget.getDuration(function(duration) {
                      function send(position) {
                        nativeCall('reportProgress', Number(position || 0), Number(duration || 0));
                      }
                      if (eventPosition !== null) {
                        send(eventPosition);
                      } else if (typeof widget.getPosition === 'function') {
                        widget.getPosition(send);
                      }
                    });
                  }

                  function reportPausedState(label) {
                    if (!widget || typeof widget.isPaused !== 'function') {
                      debugPlayer('widget_is_paused_' + label, 'unavailable');
                      return;
                    }
                    try {
                      widget.isPaused(function(paused) {
                        debugPlayer('widget_is_paused_' + label, String(!!paused));
                      });
                    } catch (error) {
                      debugPlayer('widget_is_paused_' + label + '_error', error && error.message ? error.message : String(error));
                    }
                  }

                  function reportCurrentSoundState(label) {
                    if (!widget || typeof widget.getCurrentSound !== 'function') {
                      debugPlayer('widget_current_sound_' + label, 'unavailable');
                      return;
                    }
                    try {
                      widget.getCurrentSound(function(sound) {
                        var payload = {
                          hasSound: !!sound,
                          id: sound && sound.id ? String(sound.id) : '',
                          title: sound && sound.title ? String(sound.title) : '',
                          user: sound && sound.user && sound.user.username ? String(sound.user.username) : ''
                        };
                        debugPlayer('widget_current_sound_' + label, payload);
                      });
                    } catch (error) {
                      debugPlayer('widget_current_sound_' + label + '_error', error && error.message ? error.message : String(error));
                    }
                  }

                  function bindEvent(eventName, handler) {
                    if (!eventName || !widget || typeof widget.bind !== 'function') {
                      debugPlayer('widget_event_bind_failed', String(eventName || 'missing_event'));
                      return;
                    }
                    widget.bind(eventName, function(event) {
                      if (eventName !== window.SC.Widget.Events.PLAY_PROGRESS) {
                        debugPlayer('widget_event_fired', String(eventName));
                      }
                      handler(event);
                    });
                    debugPlayer('widget_event_bound', String(eventName));
                  }

                  function bindWidget() {
                    reportLoading(true);
                    debugPlayer('final_iframe_src_before_widget', finalIframeSrc());
                    try { console.log('fire-tv: bindWidget entered; SC=' + (!!window.SC) + ' SC.Widget=' + (!!(window.SC && window.SC.Widget))); } catch (e) {}
                    if (!window.SC || !window.SC.Widget) {
                      nativeCall('reportPlaybackError', 'widget_api_missing', 'Player API did not load.');
                      reportLoading(false);
                      return;
                    }

                    widget = window.SC.Widget(iframe);
                    debugPlayer('final_iframe_src_after_widget', finalIframeSrc());
                    window.FireTvPlayerHost = {
                      command: function(command) {
                        if (!widget) return;
                        if (command === 'play') widget.play();
                        if (command === 'pause') widget.pause();
                        if (command === 'toggle') isPlaying ? widget.pause() : widget.play();
                        if (command === 'next' && widget.next) widget.next();
                        if (command === 'previous' && widget.prev) widget.prev();
                      },
                      seekTo: function(positionMs) {
                        if (!widget || typeof widget.seekTo !== 'function') return;
                        var safePosition = Math.max(0, Number(positionMs) || 0);
                        widget.seekTo(safePosition);
                        reportProgress({currentPosition: safePosition}, true);
                      }
                    };

                    bindEvent(window.SC.Widget.Events.READY, function() {
                      reportLoading(false);
                      nativeCall('reportReady');
                      debugPlayer('final_iframe_src_ready', finalIframeSrc());
                      reportPausedState('ready');
                      reportCurrentSoundState('ready');
                      reportTrack();
                      reportProgress(null, true);
                      if (!didAttemptDebugPlay) {
                        didAttemptDebugPlay = true;
                        try {
                          widget.play();
                          debugPlayer('debug_play_invoked', 'true');
                        } catch (error) {
                          debugPlayer('debug_play_error', error && error.message ? error.message : String(error));
                        }
                        window.setTimeout(function() {
                          reportPausedState('after_debug_play');
                          reportCurrentSoundState('after_debug_play');
                        }, 1000);
                      }
                    });
                    bindEvent(window.SC.Widget.Events.PLAY, function() {
                      isPlaying = true;
                      reportLoading(false);
                      nativeCall('reportPlaybackState', true);
                      reportTrack();
                    });
                    bindEvent(window.SC.Widget.Events.PAUSE, function() {
                      isPlaying = false;
                      nativeCall('reportPlaybackState', false);
                    });
                    bindEvent(window.SC.Widget.Events.PLAY_PROGRESS, function(event) {
                      reportProgress(event, false);
                    });
                    bindEvent(window.SC.Widget.Events.SEEK, function(event) {
                      reportProgress(event, true);
                    });
                    bindEvent(window.SC.Widget.Events.ERROR, function() {
                      reportLoading(false);
                      nativeCall('reportPlaybackError', 'widget_error', 'The player reported a playback error.');
                    });
                    bindEvent(window.SC.Widget.Events.FINISH, function() {
                      isPlaying = false;
                      nativeCall('reportPlaybackState', false);
                    });
                  }

                  if (document.readyState === 'complete') {
                    bindWidget();
                  } else {
                    window.addEventListener('load', bindWidget);
                  }
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun String.escapeHtml(): String = replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

    private fun sanitizeUrlForLog(url: String?): String {
        if (url == null) return "null"
        return try {
            val uri = Uri.parse(url)
            "${uri.scheme}://${uri.host}${uri.path ?: ""}"
        } catch (e: Exception) {
            "[malformed]"
        }
    }

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
        // Amazon WebView collapsed loadDataWithBaseURL into a chrome-error page with
        // both an https base and about:blank; injected HTML was discarded before any
        // inline script ran. Using loadData with no base URL keeps the injected HTML
        // as the live top-level document. All external script/widget URLs remain
        // absolute so no base-relative resolution is required.
    }
}
