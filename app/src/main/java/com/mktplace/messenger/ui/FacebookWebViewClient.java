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

    // Sections to block on facebook.com.
    // Matching: path == section OR path starts with section + "/"
    // (avoids false matches like "/feed" hitting "/feedback")
    public static final String[] BLOCKED_SECTIONS = {
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

        // Drop all app-scheme deep-links silently — desktop UA means Facebook
        // won't generate these anyway, but block them as a safety net
        if (url.startsWith("fb://") || url.startsWith("fbmessenger://")
                || url.startsWith("fbrpc://")
                || url.startsWith("intent://") || url.startsWith("market://")) {
            return true;
        }

        if (isBlocked(url)) {
            callbacks.onBlockedUrl();
            return true;
        }
        return false;
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
     * Returns true if this URL should be blocked.
     *
     * Handles both absolute URLs (from shouldOverrideUrlLoading / real navigation)
     * and relative paths (from the SPA pushState / replaceState bridge, which passes
     * only the path component like "/watch" or "/feed").
     */
    public boolean isBlocked(String url) {
        if (url == null || url.isEmpty()) return false;

        String path;

        if (url.startsWith("http://") || url.startsWith("https://")) {
            Uri uri;
            try { uri = Uri.parse(url); } catch (Exception e) { return false; }
            String host = uri.getHost();
            path = uri.getPath();
            if (host == null) return false;
            host = host.toLowerCase();

            // CDN assets — always allow
            if (host.endsWith("fbcdn.net") || host.endsWith("facebook.net")
                    || host.endsWith("fbsbx.com")) return false;

            // Non-Facebook domain — block
            boolean isFB = false;
            for (String h : FACEBOOK_HOSTS) {
                if (host.equals(h) || host.endsWith("." + h)) { isFB = true; break; }
            }
            if (!isFB) return true;
        } else {
            // Relative URL from SPA pushState — no host, just a path like "/watch"
            path = url.startsWith("/") ? url : "/" + url;
        }

        if (path == null || path.isEmpty()) path = "/";

        // Strip query string for section matching
        String pl = path.toLowerCase();
        int q = pl.indexOf('?');
        String seg = q >= 0 ? pl.substring(0, q) : pl;

        // Block the home/feed root ("/") — SPA pushState to "/" = navigating to
        // the newsfeed, not a login redirect (login redirects go through POST which
        // doesn't trigger shouldOverrideUrlLoading, and we handle onPageFinished).
        if (seg.equals("/") || seg.isEmpty()) return true;

        // Block specific social-feed sections
        for (String section : BLOCKED_SECTIONS) {
            if (seg.equals(section) || seg.startsWith(section + "/")) return true;
        }

        return false;
    }

    private void injectCleanup(WebView view) {
        // language=JavaScript
        String js =
            "(function(){\n" +
            // ── 1. Remove meta tags that trigger native "Open in app" browser banners
            "  try{\n" +
            "    document.querySelectorAll(\n" +
            "      'meta[name=\"al:android:url\"],meta[name=\"al:ios:url\"],\n" +
            "       meta[name=\"google-play-app\"],meta[name=\"apple-itunes-app\"]'\n" +
            "    ).forEach(function(m){ m.parentNode && m.parentNode.removeChild(m); });\n" +
            "  }catch(e){}\n" +
            // ── 2. CSS hide (fast initial paint) ────────────────────────────
            "  var H = [\n" +
            // Top navigation bar (blue bar with Facebook logo + Home/Watch/Groups links)
            "    '[data-pagelet=\"NavBar\"]',\n" +
            "    '[data-pagelet=\"MWNavigation\"]',\n" +
            "    '[data-pagelet=\"MWChatTabBar\"]',\n" +
            // Desktop sidebars
            "    '[data-pagelet=\"LeftRail\"]',\n" +           // left sidebar
            "    '[data-pagelet=\"RightRail\"]',\n" +          // right sidebar (ads column)
            // Mobile nav (fallback pages)
            "    '[data-pagelet=\"MobileBottomBar\"]',\n" +
            "    '[data-pagelet=\"MobileTopBar\"]',\n" +
            // Feed / distraction sections
            "    '[data-pagelet*=\"Reels\"]',\n" +
            "    '[data-pagelet*=\"NewsFeed\"]',\n" +
            "    '[data-pagelet*=\"FeedUnit\"]',\n" +
            "    '[data-pagelet*=\"Stories\"]',\n" +
            "    'header','nav'\n" +
            "  ];\n" +
            "  if(!document.getElementById('fb-lite-hide')){\n" +
            "    var s=document.createElement('style');\n" +
            "    s.id='fb-lite-hide';\n" +
            "    s.textContent=H.join(',') + '{display:none!important}';\n" +
            "    (document.head||document.documentElement).appendChild(s);\n" +
            "  }\n" +
            // ── 3. JS force-hide nav (setProperty beats Facebook's inline styles) ─
            "  function hideNav(){\n" +
            "    H.forEach(function(sel){\n" +
            "      try{ document.querySelectorAll(sel).forEach(function(el){\n" +
            "        el.style.setProperty('display','none','important');\n" +
            "      }); }catch(e){}\n" +
            "    });\n" +
            // Hide role=banner (top app bar) and role=navigation unconditionally —
            // we inject only on facebook.com so there's no risk of hiding unrelated UI
            "    try{\n" +
            "      document.querySelectorAll('[role=\"banner\"],[role=\"navigation\"]').forEach(function(el){\n" +
            "        el.style.setProperty('display','none','important');\n" +
            "      });\n" +
            "    }catch(e){}\n" +
            "  }\n" +
            "  hideNav();\n" +
            "  setTimeout(hideNav,200); setTimeout(hideNav,800); setTimeout(hideNav,2500);\n" +
            // ── 4. Remove "Open app" banners / install prompts ───────────────
            // Keywords that appear in Facebook's sticky "Open in Messenger / Get the app" bars
            "  var APP_TEXTS=['Open in Messenger','Get the Facebook app','Continue in app',\n" +
            "    'Switch to app','Open app','Get the app','Use the app','Open Facebook'];\n" +
            "  function clean(){\n" +
            // Selector-based removal
            "    var sels=[\n" +
            "      '[aria-label*=\"Get the Facebook app\"],[aria-label*=\"Open in Messenger\"]',\n" +
            "      '[aria-label*=\"Continue in app\"],[aria-label*=\"Switch to app\"]',\n" +
            "      '[data-testid*=\"download_app\"],[data-testid*=\"install_app\"],[data-testid*=\"upsell\"]',\n" +
            "      'a[href*=\"play.google.com/store/apps/details?id=com.facebook\"]',\n" +
            "      'a[href*=\"apps.apple.com\"][href*=\"facebook\"]',\n" +
            "      '[class*=\"interstitial\"]','.ms-interstitial'\n" +
            "    ];\n" +
            "    sels.forEach(function(sel){\n" +
            "      try{ document.querySelectorAll(sel).forEach(function(el){\n" +
            "        var root=el.closest('[role=\"dialog\"]')||el.closest('[style*=\"position: fixed\"]')||el;\n" +
            "        root.style.setProperty('display','none','important');\n" +
            "      }); }catch(e){}\n" +
            "    });\n" +
            // Text-content based: hide any fixed-position div whose text matches app CTA keywords
            "    try{\n" +
            "      document.querySelectorAll('div[style*=\"position: fixed\"],div[style*=\"position:fixed\"]').forEach(function(el){\n" +
            "        var t=el.textContent||'';\n" +
            "        var hasAppLink=el.querySelector('a[href*=\"play.google.com\"]')||el.querySelector('a[href*=\"apps.apple.com\"]')||el.querySelector('a[href^=\"fb://\"]')||el.querySelector('a[href^=\"intent://\"]');\n" +
            "        var hasAppText=APP_TEXTS.some(function(k){ return t.indexOf(k)>=0; });\n" +
            "        if(hasAppLink||hasAppText) el.style.setProperty('display','none','important');\n" +
            "      });\n" +
            "    }catch(e){}\n" +
            "  }\n" +
            "  clean();\n" +
            "  setTimeout(clean,500); setTimeout(clean,1500); setTimeout(clean,4000);\n" +
            // ── 5. Ad blocking ──────────────────────────────────────────────
            "  var SPONSORED_LABELS=['Sponsored','Promoted','Ad','Gesponsert','Sponsorisé','Patrocinado','Publicidad'];\n" +
            "  function hideAds(){\n" +
            // Method 1: standard ad-marker data attributes (most reliable when present)
            "    try{\n" +
            "      document.querySelectorAll('[data-ad-comet-preview],[data-ad-preview],[data-adunit-id],[aria-label=\"Sponsored\"]').forEach(function(el){\n" +
            "        (el.closest('[role=\"article\"]')||el.closest('li')||el.parentElement||el)\n" +
            "          .style.setProperty('display','none','important');\n" +
            "      });\n" +
            "    }catch(e){}\n" +
            // Method 2: desktop right-rail ad column
            "    try{\n" +
            "      document.querySelectorAll('[data-pagelet=\"RightRail\"],[data-pagelet*=\"AdUnit\"]').forEach(function(el){\n" +
            "        el.style.setProperty('display','none','important');\n" +
            "      });\n" +
            "    }catch(e){}\n" +
            // Method 3: TreeWalker — find exact "Sponsored" / "Promoted" text nodes,
            // then walk UP the DOM looking for the listing card. Stops at the first
            // ancestor that is a <li>, has role=listitem/article, or is large enough
            // to be a card (>120px wide). Works regardless of Facebook's class names.
            "    try{\n" +
            "      var tw=document.createTreeWalker(document.body||document.documentElement,4,{\n" +
            "        acceptNode:function(n){\n" +
            "          var v=n.nodeValue?n.nodeValue.trim():'';\n" +
            "          return SPONSORED_LABELS.indexOf(v)>=0?1:3;\n" +
            "        }\n" +
            "      });\n" +
            "      var node;\n" +
            "      while((node=tw.nextNode())){\n" +
            "        var el=node.parentElement;\n" +
            "        for(var i=0;i<20&&el&&el!==document.body;i++){\n" +
            "          var tag=el.tagName;\n" +
            "          var role=el.getAttribute('role')||'';\n" +
            "          if(tag==='LI'||role==='listitem'||role==='article'||role==='gridcell'){\n" +
            "            el.style.setProperty('display','none','important');\n" +
            "            break;\n" +
            "          }\n" +
            // Fallback: card-sized container (marketplace listing cards ~170×260px)
            "          if(el.offsetWidth>120&&el.offsetHeight>120){\n" +
            "            el.style.setProperty('display','none','important');\n" +
            "            break;\n" +
            "          }\n" +
            "          el=el.parentElement;\n" +
            "        }\n" +
            "      }\n" +
            "    }catch(e){}\n" +
            "  }\n" +
            "  hideAds();\n" +
            "  setTimeout(hideAds,600); setTimeout(hideAds,2000); setTimeout(hideAds,5000); setTimeout(hideAds,10000);\n" +
            // ── 7. Unread message badge ──────────────────────────────────────
            // Only runs on the /messages page. Reads the count from document.title
            // e.g. "(5) Messages | Facebook" → sends 5 to the app badge via FBLite.onBadge().
            // A MutationObserver on <title> keeps the count live as messages arrive.
            "  if(!window.__fbLiteBadge&&location.pathname.indexOf('/messages')>=0){\n" +
            "    window.__fbLiteBadge=true;\n" +
            "    function readBadge(){\n" +
            "      var m=document.title.match(/\\((\\d+)\\)/);\n" +
            "      try{ window.FBLite.onBadge(m?parseInt(m[1]):0); }catch(e){}\n" +
            "    }\n" +
            "    readBadge();\n" +
            "    var titleEl=document.getElementsByTagName('title')[0];\n" +
            "    if(titleEl) new MutationObserver(readBadge)\n" +
            "      .observe(titleEl,{childList:true,characterData:true,subtree:true});\n" +
            "  }\n" +

            // ── 8. Intercept SPA pushState/replaceState ──────────────────────
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
            // ── 9. MutationObserver ───────────────────────────────────────────
            // hideNav + clean run immediately on each mutation (they use CSS/setProperty,
            // no layout needed). hideAds is DEBOUNCED: infinite-scroll inserts new
            // listing cards in batches; if we run hideAds() synchronously the cards
            // haven't been laid out yet (offsetWidth=0) so the dimension fallback misses
            // them. Waiting 150 ms lets the browser finish rendering before we scan.
            "  if(!window.__fbLiteObs){\n" +
            "    window.__fbLiteObs=true;\n" +
            "    var _adsTimer=null;\n" +
            "    try{ new MutationObserver(function(){\n" +
            "      hideNav(); clean();\n" +
            "      clearTimeout(_adsTimer);\n" +
            "      _adsTimer=setTimeout(hideAds,150);\n" +
            "    }).observe(document.documentElement,{childList:true,subtree:true}); }catch(e){}\n" +
            "  }\n" +
            "})();\n";

        view.evaluateJavascript(js, null);
    }
}
