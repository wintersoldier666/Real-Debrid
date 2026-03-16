package com.mktplace.messenger.ui

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class FacebookWebViewClient(
    private val onPageStarted: () -> Unit,
    private val onPageFinished: () -> Unit,
    private val onPageError: () -> Unit,
    private val onBlockedUrl: () -> Unit
) : WebViewClient() {

    companion object {
        private val ALLOWED_PATH_PREFIXES = listOf(
            "/marketplace",
            "/messages",
            "/login",
            "/checkpoint",
            "/recover",
            "/two_step_verification",
            "/rsrc.php"
        )

        private val ALLOWED_HOSTS = setOf(
            "www.facebook.com",
            "facebook.com",
            "m.facebook.com",
            "web.facebook.com",
            "l.facebook.com",
            "www.messenger.com",
            "messenger.com"
        )

        private val BLOCKED_PATH_PATTERNS = listOf(
            "^/$",
            "/feed", "/video", "/watch",
            "/reels", "/reel",
            "/stories", "/story",
            "/events", "/groups", "/pages",
            "/gaming", "/jobs", "/news",
            "/ads", "/fundraisers",
            "/friends", "/notifications",
            "/hashtag", "/photos", "/live",
            "/memories", "/saved"
        ).map { it.toRegex(RegexOption.IGNORE_CASE) }
    }

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        return handleUri(Uri.parse(url))
    }

    private fun handleUri(uri: Uri): Boolean {
        val host = uri.host?.toLowerCase() ?: return true
        val path = uri.path ?: "/"

        // Always allow CDN hosts
        if (host.endsWith("fbcdn.net") || host.endsWith("facebook.net")) {
            return false
        }

        val isAllowedHost = ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }
        if (!isAllowedHost) {
            onBlockedUrl()
            return true
        }

        val isBlocked = BLOCKED_PATH_PATTERNS.any { it.containsMatchIn(path) }
        if (isBlocked) {
            onBlockedUrl()
            return true
        }

        val isAllowed = ALLOWED_PATH_PREFIXES.any { path.toLowerCase().startsWith(it) }
        return if (isAllowed) {
            false
        } else {
            onBlockedUrl()
            true
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted()
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        injectCss(view)
        onPageFinished()
    }

    override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        if (errorCode == ERROR_HOST_LOOKUP || errorCode == ERROR_CONNECT || errorCode == ERROR_TIMEOUT) {
            onPageError()
        }
    }

    private fun injectCss(view: WebView) {
        val css = "div[role='banner'],div[data-pagelet='MWNavigation']," +
                  "div[data-pagelet='LeftRail'],div[data-pagelet='Stories']," +
                  "[data-pagelet*='Reels'],[data-pagelet*='NewsFeed']," +
                  "div[data-pagelet='MobileBottomBar'],nav{display:none!important}"
        val js = "(function(){var s=document.createElement('style');s.innerHTML='$css';document.head.appendChild(s);})();"
        view.evaluateJavascript(js, null)
    }
}
