package com.mktplace.messenger.ui;

import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class FacebookWebViewClient extends WebViewClient {

    public interface Callbacks {
        void onPageStarted();
        void onPageFinished();
        void onPageError();
        void onBlockedUrl();
    }

    private static final String[] ALLOWED_PATH_PREFIXES = {
        "/marketplace", "/messages", "/login",
        "/checkpoint", "/recover", "/two_step_verification", "/rsrc.php"
    };

    private static final String[] ALLOWED_HOSTS = {
        "www.facebook.com", "facebook.com", "m.facebook.com",
        "web.facebook.com", "l.facebook.com",
        "www.messenger.com", "messenger.com"
    };

    private static final String[] BLOCKED_PATH_FRAGMENTS = {
        "/feed", "/video", "/watch", "/reels", "/reel",
        "/stories", "/story", "/events", "/groups", "/pages",
        "/gaming", "/jobs", "/news", "/ads", "/fundraisers",
        "/friends", "/notifications", "/hashtag", "/photos",
        "/live", "/memories", "/saved"
    };

    private final Callbacks callbacks;

    public FacebookWebViewClient(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return handleUrl(url);
    }

    private boolean handleUrl(String url) {
        if (url == null) return true;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        String path = uri.getPath();
        if (host == null) return true;
        host = host.toLowerCase();
        if (path == null) path = "/";

        // Allow CDN hosts
        if (host.endsWith("fbcdn.net") || host.endsWith("facebook.net")) {
            return false;
        }

        // Check allowed hosts
        boolean hostAllowed = false;
        for (String h : ALLOWED_HOSTS) {
            if (host.equals(h) || host.endsWith("." + h)) {
                hostAllowed = true;
                break;
            }
        }
        if (!hostAllowed) {
            callbacks.onBlockedUrl();
            return true;
        }

        // Check blocked path fragments
        String pathLower = path.toLowerCase();
        for (String blocked : BLOCKED_PATH_FRAGMENTS) {
            if (pathLower.contains(blocked)) {
                callbacks.onBlockedUrl();
                return true;
            }
        }
        // Block bare homepage
        if (pathLower.equals("/")) {
            callbacks.onBlockedUrl();
            return true;
        }

        // Check allowed prefixes
        for (String prefix : ALLOWED_PATH_PREFIXES) {
            if (pathLower.startsWith(prefix)) {
                return false; // allow
            }
        }

        callbacks.onBlockedUrl();
        return true;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        callbacks.onPageStarted();
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        injectCss(view);
        callbacks.onPageFinished();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        super.onReceivedError(view, errorCode, description, failingUrl);
        if (errorCode == ERROR_HOST_LOOKUP || errorCode == ERROR_CONNECT || errorCode == ERROR_TIMEOUT) {
            callbacks.onPageError();
        }
    }

    private void injectCss(WebView view) {
        String css = "div[role='banner'],div[data-pagelet='MWNavigation'],"
                   + "div[data-pagelet='LeftRail'],div[data-pagelet='Stories'],"
                   + "[data-pagelet*='Reels'],[data-pagelet*='NewsFeed'],"
                   + "div[data-pagelet='MobileBottomBar'],nav{display:none!important}";
        String js = "(function(){var s=document.createElement('style');"
                  + "s.innerHTML='" + css + "';"
                  + "document.head.appendChild(s);})();";
        view.evaluateJavascript(js, null);
    }
}
