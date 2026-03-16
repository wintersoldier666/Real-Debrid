package com.mktplace.messenger.ui;

import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class FacebookWebViewClient extends WebViewClient {

    public interface Callbacks {
        void onPageStarted(String url);
        void onPageFinished();
        void onPageError();
        void onBlockedUrl();
    }

    private static final String[] FACEBOOK_HOSTS = {
        "www.facebook.com", "facebook.com", "m.facebook.com",
        "web.facebook.com", "l.facebook.com",
        "mbasic.facebook.com"   // plain-HTML interface used for messages tab
    };

    // Paths on facebook.com that are explicitly allowed
    private static final String[] ALLOWED_FB_PREFIXES = {
        "/marketplace",              // browse listings
        "/messages",                 // mbasic messages
        "/login", "/checkpoint",
        "/recover", "/two_step_verification", "/rsrc.php"
    };

    public static final String[] BLOCKED_FRAGMENTS = {
        "/feed", "/video", "/watch", "/reels", "/reel",
        "/stories", "/story", "/events", "/groups", "/pages",
        "/gaming", "/jobs", "/news", "/ads", "/fundraisers",
        "/friends", "/notifications", "/hashtag", "/photos",
        "/live", "/memories", "/saved"
        // NOTE: /messages is intentionally NOT blocked — the Messages tab uses
        // a desktop UA so facebook.com/messages/ works without an app redirect
    };

    private final Callbacks callbacks;

    public FacebookWebViewClient(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    /** Called for regular top-level navigation */
    @Override
    @SuppressWarnings("deprecation")
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        if (url == null) return true;
        // Block app-scheme links silently
        if (url.startsWith("intent://") || url.startsWith("fb://")
                || url.startsWith("market://") || url.startsWith("fbmessenger://")
                || url.startsWith("fbrpc://")) {
            return true;
        }
        return isBlocked(url);
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        callbacks.onPageStarted(url);
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

    /** Returns true if this URL should be blocked */
    public boolean isBlocked(String url) {
        if (url == null || url.isEmpty()) return false;
        Uri uri;
        try { uri = Uri.parse(url); } catch (Exception e) { return true; }
        String host = uri.getHost();
        String path = uri.getPath();
        if (host == null) return true;
        host = host.toLowerCase();
        if (path == null) path = "/";

        // CDN — always allow
        if (host.endsWith("fbcdn.net") || host.endsWith("facebook.net")
                || host.endsWith("fbsbx.com")) return false;

        // Must be a Facebook host
        boolean isFB = false;
        for (String h : FACEBOOK_HOSTS) {
            if (host.equals(h) || host.endsWith("." + h)) { isFB = true; break; }
        }
        if (!isFB) return true;

        String pl = path.toLowerCase();

        // Block explicit fragments
        for (String frag : BLOCKED_FRAGMENTS) {
            if (pl.startsWith(frag)) return true;
        }
        // Block bare homepage
        if (pl.equals("/")) return true;

        // Allow whitelisted prefixes
        for (String prefix : ALLOWED_FB_PREFIXES) {
            if (pl.startsWith(prefix)) return false;
        }

        return true; // block anything else on facebook.com
    }

    private void injectCleanup(WebView view) {
        // language=JavaScript
        String js =
            "(function(){\n" +
            // ── 1. Hide Facebook's own nav chrome ──────────────────────
            "  var HIDE_CSS = [\n" +
            "    'div[role=\"banner\"]',\n" +
            "    'div[data-pagelet=\"MWNavigation\"]',\n" +
            "    'div[data-pagelet=\"LeftRail\"]',\n" +
            "    'div[data-pagelet=\"Stories\"]',\n" +
            "    'div[data-pagelet=\"MobileBottomBar\"]',\n" +
            "    'div[data-pagelet=\"MobileTopBar\"]',\n" +
            "    '[data-pagelet*=\"Reels\"]',\n" +
            "    '[data-pagelet*=\"NewsFeed\"]',\n" +
            "    '[data-pagelet*=\"FeedUnit\"]',\n" +
            "    'nav'\n" +
            "  ].join(',');\n" +
            "  var style = document.createElement('style');\n" +
            "  style.id = 'fb-lite-hide';\n" +
            "  style.textContent = HIDE_CSS + '{display:none!important}';\n" +
            "  if (!document.getElementById('fb-lite-hide'))\n" +
            "    (document.head||document.documentElement).appendChild(style);\n" +
            // ── 2. Block install-app popups / ads via DOM cleaning ──────
            "  function clean() {\n" +
            "    var popups = [\n" +
            "      '[aria-label*=\"Get the Facebook app\"]',\n" +
            "      '[aria-label*=\"Open in Messenger\"]',\n" +
            "      '[aria-label*=\"Continue in app\"]',\n" +
            "      '[aria-label*=\"Switch to app\"]',\n" +
            "      '[aria-label*=\"Download\"]',\n" +
            "      '[data-testid*=\"download_app\"]',\n" +
            "      '[data-testid*=\"install_app\"]',\n" +
            "      '[data-testid*=\"upsell\"]',\n" +
            "      'a[href*=\"play.google.com/store/apps/details?id=com.facebook\"]',\n" +
            "      'a[href*=\"play.google.com/store/apps/details?id=com.instagram\"]',\n" +
            "      '[class*=\"interstitial\"]',\n" +
            "      '.ms-interstitial'\n" +
            "    ];\n" +
            "    popups.forEach(function(sel){\n" +
            "      try{\n" +
            "        document.querySelectorAll(sel).forEach(function(el){\n" +
            "          var root = el.closest('[role=\"dialog\"]') ||\n" +
            "                     el.closest('div[style*=\"position: fixed\"]') || el;\n" +
            "          root.style.display='none';\n" +
            "        });\n" +
            "      }catch(e){}\n" +
            "    });\n" +
            // Remove sponsored posts
            "    try{\n" +
            "      document.querySelectorAll('[aria-label=\"Sponsored\"],[data-ad-comet-preview],[data-ad-preview]').forEach(function(el){\n" +
            "        var art = el.closest('[role=\"article\"]');\n" +
            "        if(art) art.style.display='none';\n" +
            "      });\n" +
            "    }catch(e){}\n" +
            // Remove fixed overlays containing app-store links
            "    try{\n" +
            "      document.querySelectorAll('div[style*=\"position: fixed\"],div[style*=\"position:fixed\"]').forEach(function(el){\n" +
            "        if(el.querySelector('a[href*=\"play.google.com\"]')||el.querySelector('a[href*=\"apps.apple.com\"]')){\n" +
            "          el.style.display='none';\n" +
            "        }\n" +
            "      });\n" +
            "    }catch(e){}\n" +
            "  }\n" +
            "  clean();\n" +
            "  setTimeout(clean,500); setTimeout(clean,1500); setTimeout(clean,3000);\n" +
            // ── 3. Intercept SPA history navigation ─────────────────────
            // Override pushState / replaceState so Android.onNav is called
            // whenever Facebook's JS tries to navigate to a new URL
            "  if(!window.__fbLitePatched){\n" +
            "    window.__fbLitePatched = true;\n" +
            "    function notifyAndroid(url){\n" +
            "      try{ if(window.FBLite) window.FBLite.onNav(url||location.href); }catch(e){}\n" +
            "    }\n" +
            "    var _push = history.pushState;\n" +
            "    var _replace = history.replaceState;\n" +
            "    history.pushState = function(s,t,url){\n" +
            "      _push.call(history,s,t,url);\n" +
            "      notifyAndroid(url||location.href);\n" +
            "    };\n" +
            "    history.replaceState = function(s,t,url){\n" +
            "      _replace.call(history,s,t,url);\n" +
            "      notifyAndroid(url||location.href);\n" +
            "    };\n" +
            "    window.addEventListener('popstate',function(){\n" +
            "      notifyAndroid(location.href);\n" +
            "    });\n" +
            "  }\n" +
            // ── 4. MutationObserver re-cleans on every DOM change ───────
            "  if(!window.__fbLiteObs){\n" +
            "    window.__fbLiteObs=true;\n" +
            "    try{\n" +
            "      new MutationObserver(function(){ clean(); })\n" +
            "        .observe(document.documentElement,{childList:true,subtree:true});\n" +
            "    }catch(e){}\n" +
            "  }\n" +
            "})();\n";

        view.evaluateJavascript(js, null);
    }
}
