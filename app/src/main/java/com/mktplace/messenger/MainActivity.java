package com.mktplace.messenger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.mktplace.messenger.ui.FacebookWebChromeClient;
import com.mktplace.messenger.ui.FacebookWebViewClient;

public class MainActivity extends Activity {

    private static final String URL_MARKETPLACE = "https://www.facebook.com/marketplace/";
    // Desktop UA means www.facebook.com/messages/ works as full web chat — no app prompts.
    private static final String URL_MESSAGES    = "https://www.facebook.com/messages/";

    // Desktop UA: Facebook serves full desktop site with no "Open in app" banners,
    // no mobile bottom-nav tabs, and messages open as web UI instead of app redirects.
    private static final String UA_DESKTOP =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36";

    private static final int TAB_MARKETPLACE = 0;
    private static final int TAB_MESSAGES    = 1;

    private WebView      webView;
    private ProgressBar  progressBar;
    private TextView     tvTitle;
    private LinearLayout errorView;
    private LinearLayout blockedView;
    private LinearLayout tabMarketplace;
    private LinearLayout tabMessages;
    private ImageView    iconMarketplace;
    private TextView     labelMarketplace;
    private ImageView    iconMessages;
    private TextView     labelMessages;
    private TextView     msgBadge;

    private int currentTab = TAB_MARKETPLACE;

    private FacebookWebViewClient fbClient;

    // JS → Java bridge (exposed as window.FBLite in the page)
    private class NavBridge {
        /** Called by the pushState/replaceState interceptor on every SPA navigation. */
        @JavascriptInterface
        public void onNav(final String url) {
            webView.post(new Runnable() {
                @Override public void run() {
                    if (url != null && fbClient != null && fbClient.isBlocked(url)) {
                        redirectToCurrentTab();
                    }
                }
            });
        }

        /**
         * Called by the injected JS with the unread message count parsed from the
         * page title, e.g. "(5) Messages | Facebook" → count = 5.
         * Only fired when the current page is the Messages page.
         */
        @JavascriptInterface
        public void onBadge(final int count) {
            webView.post(new Runnable() {
                @Override public void run() { updateBadge(count); }
            });
        }
    }

    private void updateBadge(int count) {
        if (msgBadge == null) return;
        if (count > 0) {
            msgBadge.setText(count > 99 ? "99+" : String.valueOf(count));
            msgBadge.setVisibility(View.VISIBLE);
        } else {
            msgBadge.setVisibility(View.GONE);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView          = (WebView)      findViewById(R.id.webView);
        progressBar      = (ProgressBar)  findViewById(R.id.progressBar);
        tvTitle          = (TextView)     findViewById(R.id.tvTitle);
        errorView        = (LinearLayout) findViewById(R.id.errorView);
        blockedView      = (LinearLayout) findViewById(R.id.blockedView);
        tabMarketplace   = (LinearLayout) findViewById(R.id.tabMarketplace);
        tabMessages      = (LinearLayout) findViewById(R.id.tabMessages);
        iconMarketplace  = (ImageView)    findViewById(R.id.iconMarketplace);
        labelMarketplace = (TextView)     tabMarketplace.getChildAt(1);
        iconMessages     = (ImageView)    findViewById(R.id.iconMessages);
        labelMessages    = (TextView)     findViewById(R.id.tvMessages);
        msgBadge         = (TextView)     findViewById(R.id.msgBadge);

        setupWebView();
        setupNavigation();
        setupButtons();

        if (savedInstanceState == null) {
            loadTab(TAB_MARKETPLACE);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setUserAgentString(UA_DESKTOP);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new NavBridge(), "FBLite");

        fbClient = new FacebookWebViewClient(new FacebookWebViewClient.Callbacks() {
            @Override public void onPageStarted(String url) {
                progressBar.setVisibility(View.VISIBLE);
                hideAllOverlays();
            }
            @Override public void onPageFinished() {
                progressBar.setVisibility(View.GONE);
                // Safety net: if we somehow landed on the Facebook homepage
                // (e.g. via a POST-login redirect that bypassed shouldOverrideUrlLoading),
                // redirect to the current tab immediately.
                String url = webView.getUrl();
                if (url != null && !url.isEmpty()) {
                    try {
                        android.net.Uri u = android.net.Uri.parse(url);
                        String path = u.getPath();
                        if (path == null || path.equals("/") || path.isEmpty()) {
                            redirectToCurrentTab();
                        }
                    } catch (Exception ignored) {}
                }
            }
            @Override public void onPageError()  { showError(); }
            @Override public void onBlockedUrl() { redirectToCurrentTab(); }
        });

        webView.setWebViewClient(fbClient);
        webView.setWebChromeClient(new FacebookWebChromeClient(new FacebookWebChromeClient.ProgressListener() {
            @Override public void onProgress(int p) {
                progressBar.setProgress(p);
                if (p >= 100) progressBar.setVisibility(View.GONE);
            }
        }));
    }

    private void redirectToCurrentTab() {
        hideAllOverlays();
        webView.stopLoading();
        webView.loadUrl(currentTab == TAB_MARKETPLACE ? URL_MARKETPLACE : URL_MESSAGES);
    }

    private void setupNavigation() {
        tabMarketplace.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { loadTab(TAB_MARKETPLACE); }
        });
        tabMessages.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { loadTab(TAB_MESSAGES); }
        });
    }

    private void setupButtons() {
        ((Button) findViewById(R.id.btnRetry)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (isNetworkAvailable()) { hideAllOverlays(); webView.reload(); }
                else showError();
            }
        });
        ((Button) findViewById(R.id.btnGoMarketplace)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { loadTab(TAB_MARKETPLACE); }
        });
    }

    private void loadTab(int tab) {
        currentTab = tab;
        hideAllOverlays();
        webView.stopLoading();
        if (!isNetworkAvailable()) { showError(); return; }
        String url = (tab == TAB_MARKETPLACE) ? URL_MARKETPLACE : URL_MESSAGES;
        tvTitle.setText(tab == TAB_MARKETPLACE ? "Marketplace" : "Messages");
        webView.loadUrl(url);
        updateNavColors(tab);
    }

    @SuppressWarnings("deprecation")
    private void updateNavColors(int active) {
        int blue = getResources().getColor(R.color.nav_selected);
        int gray = getResources().getColor(R.color.nav_unselected);
        iconMarketplace.setColorFilter(active == TAB_MARKETPLACE ? blue : gray);
        labelMarketplace.setTextColor(active == TAB_MARKETPLACE ? blue : gray);
        iconMessages.setColorFilter(active == TAB_MESSAGES ? blue : gray);
        labelMessages.setTextColor(active == TAB_MESSAGES ? blue : gray);
    }

    private void showError() {
        webView.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
        blockedView.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
    }

    private void hideAllOverlays() {
        webView.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);
        blockedView.setVisibility(View.GONE);
    }

    @SuppressWarnings("deprecation")
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        webView.saveState(out);
        out.putInt("tab", currentTab);
    }

    @Override
    protected void onRestoreInstanceState(Bundle in) {
        super.onRestoreInstanceState(in);
        webView.restoreState(in);
        currentTab = in.getInt("tab", TAB_MARKETPLACE);
        updateNavColors(currentTab);
    }

    @Override protected void onPause()   { super.onPause();   webView.onPause();  }
    @Override protected void onResume()  { super.onResume();  webView.onResume(); }
    @Override protected void onDestroy() { webView.destroy(); super.onDestroy();  }
}
