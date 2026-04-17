package com.neilpontecorvo.soundcloudfiretv.webview

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

class WebPlayerHostController {

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView) {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().setAcceptCookie(true)
    }

    fun loadPlayer(webView: WebView, url: String = DEFAULT_PLAYER_URL) {
        webView.loadUrl(url)
    }

    fun reload(webView: WebView) {
        webView.reload()
    }

    fun clearCookies() {
        val manager = CookieManager.getInstance()
        manager.removeAllCookies(null)
        manager.flush()
    }

    fun clearSession(webView: WebView) {
        webView.clearHistory()
        webView.clearCache(true)
        webView.clearFormData()
    }

    companion object {
        // TODO: Replace with a controlled web host URL after legal/auth review.
        const val DEFAULT_PLAYER_URL: String = "https://soundcloud.com"
    }
}
