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

    // messenger.com is fully allowed — it IS the Messages section
    private static final String[] FULL_ACCESS_HOSTS = {
        "www.messenger.com", "messenger.com"
    };

    private static final String[] FACEBOOK_HOSTS = {
        "www.facebook.com", "facebook.com", "m.facebook.com",
        "web.facebook.com", "l.facebook.com"
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
        // Block app-store / intent links (the "install app" buttons)
        if (url.startsWith("intent://") || url.startsWith("fb://")
                || url.startsWith("market://") || url.startsWith("fbmessenger://")) {
            return true; // silently block
        }

        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        String path = uri.getPath();
        if (host == null) return true;
        host = host.toLowerCase();
        if (path == null) path = "/";

        // Always allow CDN hosts
        if (host.endsWith("fbcdn.net") || host.endsWith("facebook.net")
                || host.endsWith("fbsbx.com")) {
            return false;
        }

        // messenger.com — fully allow (this is the web Messages app)
        for (String h : FULL_ACCESS_HOSTS) {
            if (host.equals(h) || host.endsWith("." + h)) {
                return false;
            }
        }

        // Check if it's a Facebook host
        boolean isFacebook = false;
        for (String h : FACEBOOK_HOSTS) {
            if (host.equals(h) || host.endsWith("." + h)) {
                isFacebook = true;
                break;
            }
        }
        if (!isFacebook) {
            return true; // block unknown hosts silently
        }

        // Facebook host — check blocked path fragments
        String pathLower = path.toLowerCase();
        for (String blocked : BLOCKED_PATH_FRAGMENTS) {
            if (pathLower.contains(blocked)) {
                callbacks.onBlockedUrl();
                return true;
            }
        }
        if (pathLower.equals("/")) {
            callbacks.onBlockedUrl();
            return true;
        }

        // Check allowed prefixes
        for (String prefix : ALLOWED_PATH_PREFIXES) {
            if (pathLower.startsWith(prefix)) {
                return false;
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
        injectCleanup(view);
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

    /**
     * Injected once per page load. Uses a MutationObserver so it keeps
     * working as Facebook's SPA dynamically renders new content.
     */
    private void injectCleanup(WebView view) {
        String js =
            "(function() {" +
            "  function clean() {" +
            // ── Hide Facebook's own nav/chrome ───────────────────────────
            "    var hide = [" +
            "      'div[role=\"banner\"]'," +
            "      'div[data-pagelet=\"MWNavigation\"]'," +
            "      'div[data-pagelet=\"LeftRail\"]'," +
            "      'div[data-pagelet=\"Stories\"]'," +
            "      'div[data-pagelet=\"MobileBottomBar\"]'," +
            "      '[data-pagelet*=\"Reels\"]'," +
            "      '[data-pagelet*=\"NewsFeed\"]'," +
            "      'nav'" +
            "    ];" +
            "    hide.forEach(function(sel){" +
            "      try{document.querySelectorAll(sel).forEach(function(e){e.style.display='none';});}catch(x){}" +
            "    });" +
            // ── Block install-app overlays / smart banners ───────────────
            "    var popupSelectors = [" +
            // Smart app banner (meta-driven)
            "      'link[rel=\"alternate\"][media*=\"only screen\"]'," +
            // "Open in the Messenger / Facebook app" dialogs
            "      '[data-testid*=\"download_app\"]'," +
            "      '[data-testid*=\"install_app\"]'," +
            "      '[data-testid*=\"app_interstitial\"]'," +
            "      '[aria-label*=\"Get the Facebook app\"]'," +
            "      '[aria-label*=\"Open in Messenger\"]'," +
            "      '[aria-label*=\"Open in the Messenger\"]'," +
            "      '[aria-label*=\"Download the Messenger\"]'," +
            "      '[aria-label*=\"Continue in app\"]'," +
            "      '[aria-label*=\"Switch to app\"]'," +
            // Generic bottom-sheet modals that contain app store links
            "      '[role=\"dialog\"] a[href*=\"play.google.com\"]'," +
            "      '[role=\"dialog\"] a[href*=\"apps.apple.com\"]'," +
            "      'a[href*=\"play.google.com/store/apps/details?id=com.facebook\"]'," +
            "      'a[href*=\"play.google.com/store/apps/details?id=com.instagram\"]'," +
            // Messenger-specific "use the app" banners
            "      '[data-testid*=\"upsell\"]'," +
            "      '.ms-interstitial'," +
            "      '[class*=\"interstitial\"]'" +
            "    ];" +
            "    popupSelectors.forEach(function(sel) {" +
            "      try {" +
            "        document.querySelectorAll(sel).forEach(function(el) {" +
            "          var p = el.closest('[role=\"dialog\"]') || el.closest('div[style*=\"position: fixed\"]') || el;" +
            "          p.style.display = 'none';" +
            "        });" +
            "      } catch(x) {}" +
            "    });" +
            // ── Remove sponsored / ad posts ──────────────────────────────
            "    try {" +
            "      document.querySelectorAll('[aria-label=\"Sponsored\"]').forEach(function(el) {" +
            "        var article = el.closest('[role=\"article\"]') || el.parentElement;" +
            "        for(var i=0;i<5;i++){if(article&&article.parentElement)article=article.parentElement;else break;}" +
            "        if(article) article.style.display='none';" +
            "      });" +
            "    } catch(x) {}" +
            // Remove banner ads by data attribute
            "    try {" +
            "      document.querySelectorAll('[data-ad-comet-preview],[data-ad-preview]').forEach(function(e){" +
            "        var art = e.closest('[role=\"article\"]');if(art)art.style.display='none';" +
            "      });" +
            "    } catch(x) {}" +
            // Remove overlay that blocks scrolling (install-app interstitial)
            "    try {" +
            "      document.querySelectorAll('div[style*=\"overflow: hidden\"][style*=\"position: fixed\"]').forEach(function(e){" +
            "        if(e.querySelector('a[href*=\"play.google.com\"]')||e.querySelector('a[href*=\"apps.apple.com\"]')){" +
            "          e.style.display='none';" +
            "        }" +
            "      });" +
            "    } catch(x) {}" +
            "  }" +
            // Run now
            "  clean();" +
            // Run again after short delay (SPAs render late)
            "  setTimeout(clean, 800);" +
            "  setTimeout(clean, 2000);" +
            // Watch for any new DOM changes and re-clean
            "  try {" +
            "    var obs = new MutationObserver(function(){ clean(); });" +
            "    obs.observe(document.documentElement, {childList:true, subtree:true});" +
            "  } catch(x) {}" +
            "})();";

        view.evaluateJavascript(js, null);
    }
}
