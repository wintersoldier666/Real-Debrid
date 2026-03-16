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
        "web.facebook.com", "l.facebook.com", "mbasic.facebook.com"
    };

    // Sections to block. Everything else on facebook.com is allowed, including
    // all login / auth redirect paths (/r.php, /home.php, /, etc.).
    // Matching rule: path equals segment OR path starts with segment + "/"
    // to avoid false matches (e.g. "/feed" must not block "/feedback").
    private static final String[] BLOCKED_SECTIONS = {
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
        if (url == null) return true;
        // Silently drop app-scheme deep-links
        if (url.startsWith("intent://") || url.startsWith("fb://")
                || url.startsWith("market://") || url.startsWith("fbmessenger://")
                || url.startsWith("fbrpc://")) {
            return true;
        }
        if (isBlocked(url)) {
            callbacks.onBlockedUrl();
            return true;
        }
        return false; // let WebView load it
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

    /**
     * Returns true only for URLs we explicitly want to block.
     * Login flows, redirects through /, /r.php, /home.php, etc. are all allowed.
     */
    public boolean isBlocked(String url) {
        if (url == null || url.isEmpty()) return false;
        Uri uri;
        try { uri = Uri.parse(url); } catch (Exception e) { return false; }
        String host = uri.getHost();
        String path = uri.getPath();
        if (host == null) return false;
        host = host.toLowerCase();
        if (path == null) path = "/";

        // CDN assets — always allow
        if (host.endsWith("fbcdn.net") || host.endsWith("facebook.net")
                || host.endsWith("fbsbx.com")) return false;

        // Non-Facebook domain — block
        boolean isFB = false;
        for (String h : FACEBOOK_HOSTS) {
            if (host.equals(h) || host.endsWith("." + h)) { isFB = true; break; }
        }
        if (!isFB) return true;

        // Block only specific social-feed sections
        String pl = path.toLowerCase();
        for (String section : BLOCKED_SECTIONS) {
            if (pl.equals(section) || pl.startsWith(section + "/")) return true;
        }

        // Everything else on facebook.com is allowed (login, marketplace,
        // messages, profile pages needed for marketplace listings, etc.)
        return false;
    }

    private void injectCleanup(WebView view) {
        // language=JavaScript
        String js =
            "(function(){\n" +
            // 1. Hide Facebook's own nav chrome so our bottom bar is the only nav
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
            // 2. Remove install-app banners / popups
            "  function clean() {\n" +
            "    var sels = [\n" +
            "      '[aria-label*=\"Get the Facebook app\"]',\n" +
            "      '[aria-label*=\"Open in Messenger\"]',\n" +
            "      '[aria-label*=\"Continue in app\"]',\n" +
            "      '[aria-label*=\"Switch to app\"]',\n" +
            "      '[aria-label*=\"Download\"]',\n" +
            "      '[data-testid*=\"download_app\"],[data-testid*=\"install_app\"],[data-testid*=\"upsell\"]',\n" +
            "      'a[href*=\"play.google.com/store/apps/details?id=com.facebook\"]',\n" +
            "      '[class*=\"interstitial\"]','.ms-interstitial'\n" +
            "    ];\n" +
            "    sels.forEach(function(sel){\n" +
            "      try{\n" +
            "        document.querySelectorAll(sel).forEach(function(el){\n" +
            "          var root = el.closest('[role=\"dialog\"]')||\n" +
            "                     el.closest('div[style*=\"position: fixed\"]')||el;\n" +
            "          root.style.display='none';\n" +
            "        });\n" +
            "      }catch(e){}\n" +
            "    });\n" +
            "    try{\n" +
            "      document.querySelectorAll('div[style*=\"position: fixed\"],div[style*=\"position:fixed\"]').forEach(function(el){\n" +
            "        if(el.querySelector('a[href*=\"play.google.com\"]')||el.querySelector('a[href*=\"apps.apple.com\"]'))\n" +
            "          el.style.display='none';\n" +
            "      });\n" +
            "    }catch(e){}\n" +
            "  }\n" +
            "  clean();\n" +
            "  setTimeout(clean,500); setTimeout(clean,1500); setTimeout(clean,3000);\n" +
            // 3. Intercept SPA pushState/replaceState so blocked sections are caught
            "  if(!window.__fbLitePatched){\n" +
            "    window.__fbLitePatched=true;\n" +
            "    function notifyAndroid(url){\n" +
            "      try{ if(window.FBLite) window.FBLite.onNav(url||location.href); }catch(e){}\n" +
            "    }\n" +
            "    var _push=history.pushState, _replace=history.replaceState;\n" +
            "    history.pushState=function(s,t,u){ _push.call(history,s,t,u); notifyAndroid(u||location.href); };\n" +
            "    history.replaceState=function(s,t,u){ _replace.call(history,s,t,u); notifyAndroid(u||location.href); };\n" +
            "    window.addEventListener('popstate',function(){ notifyAndroid(location.href); });\n" +
            "  }\n" +
            // 4. Re-clean on DOM mutations (Facebook inserts banners late)
            "  if(!window.__fbLiteObs){\n" +
            "    window.__fbLiteObs=true;\n" +
            "    try{ new MutationObserver(function(){ clean(); })\n" +
            "      .observe(document.documentElement,{childList:true,subtree:true}); }catch(e){}\n" +
            "  }\n" +
            "})();\n";

        view.evaluateJavascript(js, null);
    }
}
