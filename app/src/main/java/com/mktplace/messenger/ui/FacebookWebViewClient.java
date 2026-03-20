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
            // ── Overlay: a full-width white div covering the top 60 px.
            // Always re-appended to the END of <body> so it is always the
            // last element in DOM order.  When z-index is equal, last-in-DOM
            // wins the stacking contest — this ensures our blocker sits above
            // any nav bar element Facebook's React re-renders after us.
            "    try{\n" +
            "      var nb=document.getElementById('fb-lite-topblock');\n" +
            "      if(!nb){\n" +
            "        nb=document.createElement('div');\n" +
            "        nb.id='fb-lite-topblock';\n" +
            "        nb.style.position='fixed';\n" +
            "        nb.style.top='0';\n" +
            "        nb.style.left='0';\n" +
            "        nb.style.right='0';\n" +
            "        nb.style.height='60px';\n" +
            "        nb.style.zIndex='2147483647';\n" +
            "        nb.style.background='#fff';\n" +
            "        nb.style.pointerEvents='all';\n" +
            "        nb.style.margin='0';\n" +
            "        nb.style.padding='0';\n" +
            "        nb.style.border='none';\n" +
            "      }\n" +
            // appendChild on an element already in the DOM moves it to the end
            "      (document.body||document.documentElement).appendChild(nb);\n" +
            "    }catch(e){}\n" +
            // ── Deep fixed-element scan: walk up to 5 DOM levels below <body>
            // to find position:fixed elements that match the nav bar profile
            // (wide + short + sitting at the top of the viewport).
            // Catches the nav bar when Facebook nests it inside a non-semantic wrapper.
            "    try{\n" +
            "      function fbHide(el,d){\n" +
            "        if(!el||d>5) return;\n" +
            "        try{\n" +
            "          var cs=window.getComputedStyle(el);\n" +
            "          var rect=el.getBoundingClientRect();\n" +
            "          if((cs.position==='fixed'||cs.position==='sticky')&&\n" +
            "              rect.top<=2&&el.offsetWidth>300&&el.offsetHeight>0&&el.offsetHeight<=80){\n" +
            "            el.style.setProperty('display','none','important');\n" +
            "          }\n" +
            "        }catch(e2){}\n" +
            "        for(var i=0;i<el.children.length;i++) fbHide(el.children[i],d+1);\n" +
            "      }\n" +
            "      if(document.body) fbHide(document.body,0);\n" +
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
            // Recognised sponsored labels (English + common locales).
            // "Ad" intentionally omitted — too short, matches unrelated text.
            "  var SPONSORED_LABELS=['Sponsored','Promoted','Gesponsert','Sponsorisé','Patrocinado','Publicidad'];\n" +
            "  function hideCard(el){\n" +
            "    if(!el||el===document.body) return;\n" +
            "    for(var i=0;i<25&&el&&el!==document.body;i++){\n" +
            "      var tag=el.tagName;\n" +
            "      var role=(el.getAttribute&&el.getAttribute('role'))||'';\n" +
            "      if(tag==='LI'||role==='listitem'||role==='article'||role==='gridcell'){\n" +
            "        el.style.setProperty('display','none','important'); return;\n" +
            "      }\n" +
            // Dimension fallback: only stop at block-level containers, not <a>/<span>/<img>.
            // This ensures we walk past the inner <a> wrapping the card content and
            // reach the outer card <div> so no empty slot is left in the grid.
            "      if((tag==='DIV'||tag==='SECTION')&&el.offsetWidth>120&&el.offsetHeight>120){\n" +
            "        el.style.setProperty('display','none','important'); return;\n" +
            "      }\n" +
            "      el=el.parentElement;\n" +
            "    }\n" +
            "  }\n" +
            "  function hideAds(){\n" +
            // ── M1: data-attribute markers (legacy FB ad format)
            "    try{\n" +
            "      document.querySelectorAll('[data-ad-comet-preview],[data-ad-preview],[data-adunit-id]').forEach(function(el){ hideCard(el); });\n" +
            "    }catch(e){}\n" +
            // ── M2: aria-label containing "Sponsored" / "Promoted"
            "    try{\n" +
            "      document.querySelectorAll('[aria-label*=\"Sponsored\"],[aria-label*=\"sponsored\"],[aria-label*=\"Promoted\"]').forEach(function(el){ hideCard(el); });\n" +
            "    }catch(e){}\n" +
            // ── M3: right-rail ad column
            "    try{\n" +
            "      document.querySelectorAll('[data-pagelet=\"RightRail\"],[data-pagelet*=\"AdUnit\"]').forEach(function(el){\n" +
            "        el.style.setProperty('display','none','important');\n" +
            "      });\n" +
            "    }catch(e){}\n" +
            // ── M4: href-pattern — sponsored marketplace links embed 'sponsored' in
            //        the ref parameter or carry an explicit ad_id query key.
            "    try{\n" +
            "      document.querySelectorAll('a[href*=\"sponsored\"],a[href*=\"ad_id=\"]').forEach(function(el){ hideCard(el); });\n" +
            "    }catch(e){}\n" +
            // ── M5: comprehensive textContent scan — the robust replacement for the
            //        old TreeWalker + leaf-span scan.
            //
            //   Previous blind spots fixed:
            //     • TreeWalker only matched exact text NODES — missed text split
            //       across sibling nodes (e.g. <span>Spon</span><span>sored</span>).
            //     • Leaf-span scan bailed when el.children.length > 0 — missed elements
            //       like <span><svg/>Sponsored</span> where an icon shares the element.
            //
            //   This scan uses el.textContent which aggregates all descendant text,
            //   so it catches every rendering variant.  We skip elements with long
            //   text (> 30 chars) because those are card-containers, not label elements.
            "    try{\n" +
            "      var els=document.getElementsByTagName('*');\n" +
            "      for(var i=0;i<els.length;i++){\n" +
            "        var el=els[i],tag=el.tagName;\n" +
            "        if(tag==='SCRIPT'||tag==='STYLE'||tag==='HTML'||tag==='HEAD') continue;\n" +
            "        var t=(el.textContent||'').replace(/[\\u200B-\\u200D\\uFEFF\\u00AD]/g,'').trim();\n" +
            "        if(t.length===0||t.length>30) continue;\n" +
            "        if(SPONSORED_LABELS.some(function(l){return t===l;})) hideCard(el);\n" +
            "      }\n" +
            "    }catch(e){}\n" +
            "  }\n" +
            "  hideAds();\n" +
            "  setTimeout(hideAds,500); setTimeout(hideAds,1500); setTimeout(hideAds,4000); setTimeout(hideAds,10000);\n" +
            // Scroll listener: run after scroll settles so newly rendered cards are
            // fully laid out (dimensions non-zero) before we scan.
            "  if(!window.__fbLiteScroll){\n" +
            "    window.__fbLiteScroll=true;\n" +
            "    var _scrollT=null;\n" +
            "    window.addEventListener('scroll',function(){\n" +
            "      clearTimeout(_scrollT);\n" +
            "      _scrollT=setTimeout(function(){ hideAds(); setTimeout(hideAds,800); },400);\n" +
            "    },{passive:true,capture:true});\n" +
            "  }\n" +
            // Two intervals with different duties:
            //
            // ① Fast (500 ms) — URL patrol + overlay re-positioning.
            //   Facebook's SPA router often holds a pre-injection reference to the
            //   native history.pushState, so our pushState wrapper is bypassed for
            //   SPA navigation.  Polling location.pathname every 500 ms catches any
            //   drift to a blocked path within half a second — imperceptible to the
            //   user.  We also re-append the overlay to <body> last-child every tick
            //   so it always wins the DOM-order stacking race against React re-renders.
            //
            // ② Slow (2 s) — hideAds + hideNav sweep (heavier, less frequent).
            "  if(!window.__fbLiteInterval){\n" +
            "    window.__fbLiteInterval=true;\n" +
            "    var _lastPath=location.pathname;\n" +
            "    setInterval(function(){\n" +
            // URL patrol
            "      try{\n" +
            "        var p=location.pathname;\n" +
            "        if(p!==_lastPath){\n" +
            "          _lastPath=p;\n" +
            "          var ok=p.startsWith('/marketplace')||p.startsWith('/messages')||\n" +
            "                  p.startsWith('/login')||p.startsWith('/checkpoint')||\n" +
            "                  p.startsWith('/two_step_verification')||p.startsWith('/recover')||\n" +
            "                  p.startsWith('/ajax')||p.startsWith('/privacy')||p.startsWith('/settings');\n" +
            "          if(!ok&&window.FBLite) window.FBLite.onNav(location.href);\n" +
            "        }\n" +
            "      }catch(e){}\n" +
            // Keep overlay at end of body (last-in-DOM = always on top)
            "      try{\n" +
            "        var nb=document.getElementById('fb-lite-topblock');\n" +
            "        if(nb&&document.body&&nb!==document.body.lastChild) document.body.appendChild(nb);\n" +
            "      }catch(e){}\n" +
            "    },500);\n" +
            "    setInterval(function(){ hideAds(); hideNav(); },2000);\n" +
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
            // Run hideAds at 200ms (first pass) then 600ms (after React finishes
            // painting card images so dimensions are non-zero for the fallback check).
            "      _adsTimer=setTimeout(function(){ hideAds(); setTimeout(hideAds,600); },200);\n" +
            "    }).observe(document.documentElement,{childList:true,subtree:true}); }catch(e){}\n" +
            "  }\n" +
            "})();\n";

        view.evaluateJavascript(js, null);
    }
}
