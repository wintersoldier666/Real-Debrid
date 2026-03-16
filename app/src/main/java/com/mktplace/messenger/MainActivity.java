package com.mktplace.messenger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.mktplace.messenger.ui.FacebookWebChromeClient;
import com.mktplace.messenger.ui.FacebookWebViewClient;

public class MainActivity extends Activity {

    private static final String URL_MARKETPLACE = "https://www.facebook.com/marketplace/";
    // mbasic.facebook.com is Facebook's plain-HTML interface for low-end phones.
    // It has NO JavaScript redirects and serves messages directly — the only
    // reliable way to read messages in a WebView without being pushed to the app.
    private static final String URL_MESSAGES    = "https://mbasic.facebook.com/messages/";

    private static final String UA_MOBILE =
        "Mozilla/5.0 (Linux; Android 14; SM-S918B Build/UP1A.231005.007) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.6099.210 Mobile Safari/537.36";

    private static final int TAB_MARKETPLACE = 0;
    private static final int TAB_MESSAGES    = 1;

    private WebView      webView;
    private ProgressBar  progressBar;
    private TextView     tvTitle;
    private LinearLayout errorView;
    private LinearLayout blockedView;
    private LinearLayout tabMarketplace;
    private LinearLayout tabMessages;
    private TextView     iconMarketplace;
    private TextView     labelMarketplace;
    private TextView     iconMessages;
    private TextView     labelMessages;

    private int currentTab = TAB_MARKETPLACE;

    private FacebookWebViewClient fbClient;

    // Polls the WebView URL every 600 ms to catch SPA navigation that
    // bypasses shouldOverrideUrlLoading (e.g. Facebook's history.pushState)
    private final Handler   pollHandler = new Handler();
    private final Runnable  pollRunnable = new Runnable() {
        @Override public void run() {
            enforceCurrentTab();
            pollHandler.postDelayed(this, 600);
        }
    };

    // JavaScript → Java bridge so pushState interception can ping us
    private class NavBridge {
        @JavascriptInterface
        public void onNav(final String url) {
            webView.post(new Runnable() {
                @Override public void run() {
                    if (fbClient != null && fbClient.isBlocked(url)) {
                        redirectToCurrentTab();
                    }
                }
            });
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
        iconMarketplace  = (TextView)     findViewById(R.id.iconMarketplace);
        labelMarketplace = (TextView)     tabMarketplace.getChildAt(1);
        iconMessages     = (TextView)     findViewById(R.id.iconMessages);
        labelMessages    = (TextView)     findViewById(R.id.tvMessages);

        setupWebView();
        setupNavigation();
        setupButtons();

        if (savedInstanceState == null) {
            loadTab(TAB_MARKETPLACE);
        }

        pollHandler.postDelayed(pollRunnable, 600);
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
        s.setUserAgentString(UA_MOBILE); // default; swapped per-tab in loadTab()

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // JS bridge: Facebook's pushState interceptor calls FBLite.onNav(url)
        webView.addJavascriptInterface(new NavBridge(), "FBLite");

        fbClient = new FacebookWebViewClient(new FacebookWebViewClient.Callbacks() {
            @Override public void onPageStarted(String url) {
                progressBar.setVisibility(View.VISIBLE);
                hideAllOverlays();
                // Also check URL on real page starts
                if (fbClient != null && fbClient.isBlocked(url)) {
                    redirectToCurrentTab();
                }
            }
            @Override public void onPageFinished() {
                progressBar.setVisibility(View.GONE);
            }
            @Override public void onPageError()   { showError(); }
            @Override public void onBlockedUrl()  { /* handled by isBlocked checks */ }
        });

        webView.setWebViewClient(fbClient);
        webView.setWebChromeClient(new FacebookWebChromeClient(new FacebookWebChromeClient.ProgressListener() {
            @Override public void onProgress(int p) {
                progressBar.setProgress(p);
                if (p >= 100) progressBar.setVisibility(View.GONE);
            }
        }));
    }

    /** Periodically called; redirects back if user navigated somewhere blocked */
    private void enforceCurrentTab() {
        String url = webView.getUrl();
        if (url == null || url.isEmpty()) return;
        if (fbClient != null && fbClient.isBlocked(url)) {
            redirectToCurrentTab();
        }
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
        if (!isNetworkAvailable()) { showError(); return; }

        webView.getSettings().setUserAgentString(UA_MOBILE);

        String url = (tab == TAB_MARKETPLACE) ? URL_MARKETPLACE : URL_MESSAGES;
        tvTitle.setText(tab == TAB_MARKETPLACE ? "Marketplace" : "Messages");
        webView.loadUrl(url);
        updateNavColors(tab);
    }

    @SuppressWarnings("deprecation")
    private void updateNavColors(int active) {
        int blue = getResources().getColor(R.color.nav_selected);
        int gray = getResources().getColor(R.color.nav_unselected);
        iconMarketplace.setTextColor(active == TAB_MARKETPLACE ? blue : gray);
        labelMarketplace.setTextColor(active == TAB_MARKETPLACE ? blue : gray);
        iconMessages.setTextColor(active == TAB_MESSAGES ? blue : gray);
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
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
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

    @Override protected void onPause()   { super.onPause();   webView.onPause();  pollHandler.removeCallbacks(pollRunnable); }
    @Override protected void onResume()  { super.onResume();  webView.onResume(); pollHandler.postDelayed(pollRunnable, 600); }
    @Override protected void onDestroy() { pollHandler.removeCallbacks(pollRunnable); webView.destroy(); super.onDestroy(); }
}
