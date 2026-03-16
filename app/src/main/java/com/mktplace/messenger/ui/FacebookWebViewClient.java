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
    public static final String[] BLOCKED_SECTIONS = {
        "/feed", "/video", "/watch", "/reels", "/reel",
        "/stories", "/story", "/events", "/groups", "/pages",
        "/gaming", "/jobs", "/news", "/ads", "/fundraisers",
        "/friends", "/notifications", "/hashtag", "/photos",
        "/live", "/memories", "/saved",
        "/search", "/profile.php", "/people"
    };

    // Single-segment paths that are known safe Facebook features.
    // Anything else at the top level (e.g. /{username}) is treated as a
    // profile page and blocked.
    private static final String[] SAFE_TOP_LEVEL = {
        "/marketplace", "/messages", "/login", "/checkout",
        "/settings", "/privacy", "/help", "/checkpoint",
        "/recover", "/two_step_verification", "/ajax", "/dialog"
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
     * Strategy: blocklist for known social sections + profile-page detection.
     *
     * - Known social sections (BLOCKED_SECTIONS) are always blocked.
     * - The root "/" is blocked (home/newsfeed).
     * - Single-segment paths not in SAFE_TOP_LEVEL are blocked — this catches
     *   profile pages like /{username} without needing to enumerate every username.
     * - Everything else is allowed (marketplace subcategories, messages threads, etc.)
     *
     * Handles both absolute URLs (shouldOverrideUrlLoading) and relative paths
     * (SPA pushState/replaceState bridge passing e.g. "/watch" or "/JohnDoe").
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

        // Strip query string, lowercase
        String pl = path.toLowerCase();
        int q = pl.indexOf('?');
        String seg = q >= 0 ? pl.substring(0, q) : pl;
        if (seg.isEmpty()) seg = "/";

        // Block root / home feed
        if (seg.equals("/")) return true;

        // Block known social sections
        for (String section : BLOCKED_SECTIONS) {
            if (seg.equals(section) || seg.startsWith(section + "/")) return true;
        }

        // Block single-segment paths not in the safe list.
        // Strip trailing slash then check: if no second '/' it's a single segment.
        // e.g. "/JohnDoe" → single-segment, not in SAFE_TOP_LEVEL → blocked (profile page)
        // e.g. "/marketplace" → single-segment, in SAFE_TOP_LEVEL → allowed
        // e.g. "/marketplace/item/123" → multi-segment → falls through to return false
        String segTrimmed = seg.endsWith("/") && seg.length() > 1
                ? seg.substring(0, seg.length() - 1) : seg;
        if (segTrimmed.indexOf('/', 1) < 0) {
            for (String safe : SAFE_TOP_LEVEL) {
                if (segTrimmed.equals(safe)) return false;
            }
            return true; // Unknown single-segment = profile page or unknown section
        }

        return false; // Multi-segment path not in blocked list → allow
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
            // Top navigation bar — multiple selector variants cover Facebook A/B tests
            "    '[data-pagelet=\"NavBar\"]',\n" +
            "    '[data-pagelet^=\"Nav\"]',\n" +           // catches NavBar, NavItems, etc.
            "    '[data-pagelet=\"MWNavigation\"]',\n" +
            "    '[data-pagelet=\"MWChatTabBar\"]',\n" +
            // Desktop sidebars
            "    '[data-pagelet=\"LeftRail\"]',\n" +
            "    '[data-pagelet=\"RightRail\"]',\n" +
            // Mobile nav (fallback pages)
            "    '[data-pagelet=\"MobileBottomBar\"]',\n" +
            "    '[data-pagelet=\"MobileTopBar\"]',\n" +
            // Feed / distraction sections
            "    '[data-pagelet*=\"Reels\"]',\n" +
            "    '[data-pagelet*=\"NewsFeed\"]',\n" +
            "    '[data-pagelet*=\"FeedUnit\"]',\n" +
            "    '[data-pagelet*=\"Stories\"]',\n" +
            "    '[role=\"banner\"]','[role=\"navigation\"]',\n" +
            "    'header','nav'\n" +
            "  ];\n" +
            "  if(!document.getElementById('fb-lite-hide')){\n" +
            "    var s=document.createElement('style');\n" +
            "    s.id='fb-lite-hide';\n" +
            // display:none + pointer-events:none so clicks can't reach hidden elements
            "    s.textContent=H.join(',') + '{display:none!important;pointer-events:none!important}';\n" +
            "    (document.head||document.documentElement).appendChild(s);\n" +
            "  }\n" +
            // ── 3. JS force-hide nav ─────────────────────────────────────────
            "  function hideNav(){\n" +
            "    H.forEach(function(sel){\n" +
            "      try{ document.querySelectorAll(sel).forEach(function(el){\n" +
            "        el.style.setProperty('display','none','important');\n" +
            "        el.style.setProperty('pointer-events','none','important');\n" +
            "      }); }catch(e){}\n" +
            "    });\n" +
            // Catch Facebook's top nav bar by its visual position: a fixed/sticky
            // element that spans the full width and is ≤ 80px tall.
            // This works even when Facebook changes data-pagelet attribute names.
            "    try{\n" +
            "      var topEls=document.body?Array.prototype.slice.call(document.body.children):[];\n" +
            "      topEls.forEach(function(el){\n" +
            "        try{\n" +
            "          var cs=window.getComputedStyle(el);\n" +
            "          if((cs.position==='fixed'||cs.position==='sticky')&&\n" +
            "              el.offsetWidth>300&&el.offsetHeight>0&&el.offsetHeight<=80){\n" +
            "            el.style.setProperty('display','none','important');\n" +
            "            el.style.setProperty('pointer-events','none','important');\n" +
            "          }\n" +
            "        }catch(e2){}\n" +
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
            // Helper: given an element that contains the sponsored label, walk up to
            // find the card container and hide it.
            //
            // Facebook Marketplace ad card structure:
            //   div.card_container   ← we want to hide THIS
            //     a.card_link        ← same dimensions — SKIP for dimension check
            //       div.image
            //       div.info
            //         span "Sponsored"  ← we start here
            //
            // Rule: semantic roles (li/article/listitem/gridcell) take priority.
            // Dimension fallback ONLY fires on block-level elements (DIV/SECTION),
            // never on inline elements (A/SPAN/IMG/etc.) so we don't stop at the
            // <a> wrapper and instead reach the outer card div.
            "  function hideCard(el){\n" +
            "    for(var i=0;i<25&&el&&el!==document.body;i++){\n" +
            "      var tag=el.tagName;\n" +
            "      var role=(el.getAttribute&&el.getAttribute('role'))||'';\n" +
            "      if(tag==='LI'||role==='listitem'||role==='article'||role==='gridcell'){\n" +
            "        el.style.setProperty('display','none','important'); return;\n" +
            "      }\n" +
            // Only use dimension check on block containers, not on inline/link elements
            "      if((tag==='DIV'||tag==='SECTION')&&el.offsetWidth>120&&el.offsetHeight>120){\n" +
            "        el.style.setProperty('display','none','important'); return;\n" +
            "      }\n" +
            "      el=el.parentElement;\n" +
            "    }\n" +
            "  }\n" +
            "  function hideAds(){\n" +
            // Method 1: standard ad-marker data attributes
            "    try{\n" +
            "      document.querySelectorAll('[data-ad-comet-preview],[data-ad-preview],[data-adunit-id]').forEach(function(el){\n" +
            "        hideCard(el.closest('[role=\"article\"]')||el.closest('li')||el.parentElement||el);\n" +
            "      });\n" +
            "    }catch(e){}\n" +
            // Method 2: aria-label containing "Sponsored" (covers attribute-level labelling)
            "    try{\n" +
            "      document.querySelectorAll('[aria-label*=\"Sponsored\"],[aria-label*=\"Promoted\"],[aria-label*=\"sponsored\"]').forEach(function(el){\n" +
            "        hideCard(el.closest('[role=\"article\"]')||el.closest('li')||el);\n" +
            "      });\n" +
            "    }catch(e){}\n" +
            // Method 3: desktop right-rail ad column
            "    try{\n" +
            "      document.querySelectorAll('[data-pagelet=\"RightRail\"],[data-pagelet*=\"AdUnit\"]').forEach(function(el){\n" +
            "        el.style.setProperty('display','none','important');\n" +
            "      });\n" +
            "    }catch(e){}\n" +
            // Method 4: TreeWalker — find text nodes whose trimmed value CONTAINS a
            // sponsored label (substring match handles zero-width spaces & hidden chars
            // that Facebook sometimes inserts to defeat exact-match blocking).
            "    try{\n" +
            "      var tw=document.createTreeWalker(document.body||document.documentElement,4,{\n" +
            "        acceptNode:function(n){\n" +
            "          var v=(n.nodeValue||'').replace(/[\\u200B-\\u200D\\uFEFF]/g,'').trim();\n" +
            "          return SPONSORED_LABELS.some(function(l){return v===l;})?1:3;\n" +
            "        }\n" +
            "      });\n" +
            "      var node;\n" +
            "      while((node=tw.nextNode())){\n" +
            "        hideCard(node.parentElement);\n" +
            "      }\n" +
            "    }catch(e){}\n" +
            // Method 5: scan leaf <span> and <a> elements for exact sponsored text.
            // Catches cases where the text is split across nested spans that the
            // TreeWalker handles individually as non-matching partial nodes.
            "    try{\n" +
            "      document.querySelectorAll('span,a').forEach(function(el){\n" +
            "        if(el.children.length>0) return;\n" +
            "        var v=(el.textContent||'').replace(/[\\u200B-\\u200D\\uFEFF]/g,'').trim();\n" +
            "        if(SPONSORED_LABELS.some(function(l){return v===l;})) hideCard(el);\n" +
            "      });\n" +
            "    }catch(e){}\n" +
            "  }\n" +
            "  hideAds();\n" +
            "  setTimeout(hideAds,400); setTimeout(hideAds,1200); setTimeout(hideAds,3000); setTimeout(hideAds,8000);\n" +
            // Scroll listener: when user scrolls, Facebook renders newly visible items.
            // Debounce hideAds() 300ms after scroll stops so layout is complete.
            "  if(!window.__fbLiteScroll){\n" +
            "    window.__fbLiteScroll=true;\n" +
            "    var _scrollT=null;\n" +
            "    window.addEventListener('scroll',function(){\n" +
            "      clearTimeout(_scrollT);\n" +
            "      _scrollT=setTimeout(function(){ hideAds(); setTimeout(hideAds,400); },300);\n" +
            "    },{passive:true,capture:true});\n" +
            "  }\n" +
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
            // Run hideAds at 150ms (first pass) then again at 550ms (second pass after
            // React finishes painting card images and dimensions become non-zero).
            "      _adsTimer=setTimeout(function(){ hideAds(); setTimeout(hideAds,400); },150);\n" +
            "    }).observe(document.documentElement,{childList:true,subtree:true}); }catch(e){}\n" +
            "  }\n" +
            "})();\n";

        view.evaluateJavascript(js, null);
    }
}
