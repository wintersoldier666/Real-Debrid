package com.mktplace.messenger;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
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
    private static final String URL_MESSAGES    = "https://www.facebook.com/messages/";

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
    }

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
        s.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 14; SM-S918B) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new FacebookWebViewClient(new FacebookWebViewClient.Callbacks() {
            @Override public void onPageStarted() {
                progressBar.setVisibility(View.VISIBLE);
                hideAllOverlays();
            }
            @Override public void onPageFinished() {
                progressBar.setVisibility(View.GONE);
            }
            @Override public void onPageError() {
                showError();
            }
            @Override public void onBlockedUrl() {
                showBlockedScreen();
            }
        }));

        webView.setWebChromeClient(new FacebookWebChromeClient(new FacebookWebChromeClient.ProgressListener() {
            @Override public void onProgress(int progress) {
                progressBar.setProgress(progress);
                if (progress >= 100) progressBar.setVisibility(View.GONE);
            }
        }));
    }

    private void setupNavigation() {
        tabMarketplace.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (currentTab != TAB_MARKETPLACE) loadTab(TAB_MARKETPLACE);
            }
        });
        tabMessages.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (currentTab != TAB_MESSAGES) loadTab(TAB_MESSAGES);
            }
        });
    }

    private void setupButtons() {
        Button btnRetry = (Button) findViewById(R.id.btnRetry);
        btnRetry.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (isNetworkAvailable()) { hideAllOverlays(); webView.reload(); }
                else showError();
            }
        });

        Button btnGo = (Button) findViewById(R.id.btnGoMarketplace);
        btnGo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { loadTab(TAB_MARKETPLACE); }
        });
    }

    private void loadTab(int tab) {
        currentTab = tab;
        hideAllOverlays();
        if (!isNetworkAvailable()) { showError(); return; }

        String url = (tab == TAB_MARKETPLACE) ? URL_MARKETPLACE : URL_MESSAGES;
        tvTitle.setText(tab == TAB_MARKETPLACE ? "Marketplace" : "Messages");

        String current = webView.getUrl();
        if (current == null || !current.startsWith(url.substring(0, url.length() - 1))) {
            webView.loadUrl(url);
        }
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

    private void showBlockedScreen() {
        webView.setVisibility(View.GONE);
        blockedView.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);
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
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
        outState.putInt("tab", currentTab);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        webView.restoreState(savedInstanceState);
        currentTab = savedInstanceState.getInt("tab", TAB_MARKETPLACE);
        updateNavColors(currentTab);
    }

    @Override protected void onPause()   { super.onPause();   webView.onPause();  }
    @Override protected void onResume()  { super.onResume();  webView.onResume(); }
    @Override protected void onDestroy() { webView.destroy(); super.onDestroy();  }
}
