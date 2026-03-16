package com.facebook.lite.ui

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Custom WebViewClient that intercepts navigation and blocks any URL
 * that is not part of Facebook Marketplace or Messages.
 */
class FacebookWebViewClient(
    private val onPageStarted: (String) -> Unit,
    private val onPageFinished: (String) -> Unit,
    private val onPageError: () -> Unit,
    private val onBlockedUrl: () -> Unit,
    private val onProgressUpdate: (Int) -> Unit
) : WebViewClient() {

    companion object {
        // Only these URL path prefixes are allowed
        private val ALLOWED_PATH_PREFIXES = listOf(
            "/marketplace",
            "/messages",
            // Login / account required to use app
            "/login",
            "/checkpoint",
            "/recover",
            "/two_step_verification",
            "/ajax/login",
            // Facebook CDN resources (images, scripts, etc.)
            "/rsrc.php",
            "/common",
            "/connect",
        )

        // Allowed hosts
        private val ALLOWED_HOSTS = setOf(
            "www.facebook.com",
            "facebook.com",
            "m.facebook.com",
            "web.facebook.com",
            "static.xx.fbcdn.net",
            "scontent.xx.fbcdn.net",
            "fbcdn.net",
            "l.facebook.com",   // redirect/link wrapper
            "www.messenger.com",
            "messenger.com",
        )

        // These path patterns are explicitly BLOCKED even if host is allowed
        private val BLOCKED_PATH_PATTERNS = listOf(
            "^/$",                      // Home feed
            "/feed",
            "/video",
            "/watch",
            "/reels",
            "/reel",
            "/stories",
            "/story",
            "/events",
            "/groups",
            "/pages",
            "/gaming",
            "/jobs",
            "/news",
            "/ads",
            "/fundraisers",
            "/friends",
            "/notifications",
            "/search",
            "/hashtag",
            "/profile",
            "/photo",
            "/photos",
            "/live",
            "/memories",
            "/saved",
            "/help",
            "/settings/general",
            "/me",
        ).map { it.toRegex(RegexOption.IGNORE_CASE) }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url ?: return false
        return handleUri(uri)
    }

    private fun handleUri(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return true // block unknown hosts
        val path = uri.path ?: "/"

        // Always allow CDN / static resource hosts
        if (host.endsWith("fbcdn.net") || host.endsWith("facebook.net")) {
            return false // allow
        }

        // Block non-Facebook hosts entirely
        val isAllowedHost = ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }
        if (!isAllowedHost) {
            onBlockedUrl()
            return true // block
        }

        // Check if path matches any explicitly blocked pattern
        val isBlocked = BLOCKED_PATH_PATTERNS.any { regex ->
            regex.containsMatchIn(path)
        }
        if (isBlocked) {
            onBlockedUrl()
            return true // block and show blocked screen
        }

        // Check if path matches an allowed prefix
        val isAllowed = ALLOWED_PATH_PREFIXES.any { prefix ->
            path.startsWith(prefix, ignoreCase = true)
        }

        return if (isAllowed) {
            false // allow navigation
        } else {
            // Default-block anything not explicitly allowed
            onBlockedUrl()
            true
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        // Inject CSS to hide Facebook's own nav/tab bars so only our content shows
        injectHideNavCss(view)
        onPageFinished(url)
    }

    override fun onReceivedError(
        view: WebView,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        if (errorCode != ERROR_CONNECT && errorCode != -1) return
        onPageError()
    }

    /**
     * Injects CSS that hides Facebook's global navigation bar, left sidebar,
     * and any floating elements like the Stories bar — keeping only the page content.
     */
    private fun injectHideNavCss(view: WebView) {
        val css = """
            /* Hide Facebook's top nav bar */
            div[role='banner'],
            div[data-pagelet='MWNavigation'],
            div[data-pagelet='LeftRail'],
            div[data-pagelet='RightRail'],
            div[data-pagelet='Stories'],
            div[data-pagelet='FeedSidebar'],
            div[data-testid='left_nav'],
            [data-pagelet*='Reels'],
            [data-pagelet*='Stories'],
            [data-pagelet*='FeedUnit'],
            [data-pagelet*='NewsFeed'],
            [aria-label='Facebook'],
            nav,
            /* Messenger mobile header */
            ._1enh,
            /* Mobile bottom nav */
            div[data-pagelet='MobileBottomBar'] {
                display: none !important;
            }
        """.trimIndent().replace("\n", " ")

        val js = """
            (function() {
                var style = document.createElement('style');
                style.innerHTML = '$css';
                document.head.appendChild(style);
            })();
        """.trimIndent()

        view.evaluateJavascript(js, null)
    }
}
