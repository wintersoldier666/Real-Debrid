package com.mktplace.messenger

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.mktplace.messenger.ui.FacebookWebChromeClient
import com.mktplace.messenger.ui.FacebookWebViewClient

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvTitle: TextView
    private lateinit var errorView: LinearLayout
    private lateinit var blockedView: LinearLayout
    private lateinit var tabMarketplace: LinearLayout
    private lateinit var tabMessages: LinearLayout
    private lateinit var iconMarketplace: TextView
    private lateinit var iconMessages: TextView
    private lateinit var tvMessages: TextView

    private var currentTab = Tab.MARKETPLACE

    private enum class Tab { MARKETPLACE, MESSAGES }

    private object Urls {
        const val MARKETPLACE = "https://www.facebook.com/marketplace/"
        const val MESSAGES    = "https://www.facebook.com/messages/"
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView         = findViewById(R.id.webView) as WebView
        progressBar     = findViewById(R.id.progressBar) as ProgressBar
        tvTitle         = findViewById(R.id.tvTitle) as TextView
        errorView       = findViewById(R.id.errorView) as LinearLayout
        blockedView     = findViewById(R.id.blockedView) as LinearLayout
        tabMarketplace  = findViewById(R.id.tabMarketplace) as LinearLayout
        tabMessages     = findViewById(R.id.tabMessages) as LinearLayout
        iconMarketplace = findViewById(R.id.iconMarketplace) as TextView
        iconMessages    = findViewById(R.id.iconMessages) as TextView
        tvMessages      = findViewById(R.id.tvMessages) as TextView

        setupWebView()
        setupNavigation()
        setupButtons()

        if (savedInstanceState == null) {
            loadTab(Tab.MARKETPLACE)
        }
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = true
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Pixel 4) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.setWebViewClient(FacebookWebViewClient(
            onPageStarted = {
                progressBar.visibility = View.VISIBLE
                hideAllOverlays()
            },
            onPageFinished = {
                progressBar.visibility = View.GONE
            },
            onPageError = { showError() },
            onBlockedUrl = { showBlockedScreen() }
        ))

        webView.setWebChromeClient(FacebookWebChromeClient { progress ->
            progressBar.progress = progress
            if (progress >= 100) progressBar.visibility = View.GONE
        })
    }

    private fun setupNavigation() {
        tabMarketplace.setOnClickListener {
            if (currentTab != Tab.MARKETPLACE) loadTab(Tab.MARKETPLACE)
        }
        tabMessages.setOnClickListener {
            if (currentTab != Tab.MESSAGES) loadTab(Tab.MESSAGES)
        }
    }

    @Suppress("DEPRECATION")
    private fun setupButtons() {
        val btnRetry = findViewById(R.id.btnRetry) as Button
        btnRetry.setOnClickListener {
            if (isNetworkAvailable()) {
                hideAllOverlays()
                webView.reload()
            } else {
                showError()
            }
        }
        val btnGo = findViewById(R.id.btnGoMarketplace) as Button
        btnGo.setOnClickListener { loadTab(Tab.MARKETPLACE) }
    }

    @Suppress("DEPRECATION")
    private fun loadTab(tab: Tab) {
        currentTab = tab
        hideAllOverlays()
        if (!isNetworkAvailable()) { showError(); return }

        val url = if (tab == Tab.MARKETPLACE) Urls.MARKETPLACE else Urls.MESSAGES
        tvTitle.text = if (tab == Tab.MARKETPLACE) "Marketplace" else "Messages"

        val current = webView.url ?: ""
        if (!current.startsWith(url.dropLast(1))) {
            webView.loadUrl(url)
        }
        updateNavColors(tab)
    }

    @Suppress("DEPRECATION")
    private fun updateNavColors(active: Tab) {
        val blue = resources.getColor(R.color.nav_selected)
        val gray = resources.getColor(R.color.nav_unselected)

        iconMarketplace.setTextColor(if (active == Tab.MARKETPLACE) blue else gray)
        val mLabel = tabMarketplace.getChildAt(1) as TextView
        mLabel.setTextColor(if (active == Tab.MARKETPLACE) blue else gray)
        iconMessages.setTextColor(if (active == Tab.MESSAGES) blue else gray)
        tvMessages.setTextColor(if (active == Tab.MESSAGES) blue else gray)
    }

    private fun showError() {
        webView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        blockedView.visibility = View.GONE
        progressBar.visibility = View.GONE
    }

    private fun showBlockedScreen() {
        webView.visibility = View.GONE
        blockedView.visibility = View.VISIBLE
        errorView.visibility = View.GONE
        progressBar.visibility = View.GONE
    }

    private fun hideAllOverlays() {
        webView.visibility = View.VISIBLE
        errorView.visibility = View.GONE
        blockedView.visibility = View.GONE
    }

    @Suppress("DEPRECATION")
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val info = cm.activeNetworkInfo
        return info != null && info.isConnected
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
        outState.putString("tab", currentTab.name)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
        currentTab = Tab.valueOf(savedInstanceState.getString("tab") ?: Tab.MARKETPLACE.name)
        updateNavColors(currentTab)
    }

    override fun onPause()   { super.onPause();   webView.onPause() }
    override fun onResume()  { super.onResume();  webView.onResume() }
    override fun onDestroy() { webView.destroy(); super.onDestroy() }
}
