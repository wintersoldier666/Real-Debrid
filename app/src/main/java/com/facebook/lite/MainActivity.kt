package com.facebook.lite

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.facebook.lite.databinding.ActivityMainBinding
import com.facebook.lite.ui.FacebookWebChromeClient
import com.facebook.lite.ui.FacebookWebViewClient
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Track current tab so we can restore on back-press
    private var currentTab = Tab.MARKETPLACE

    // Facebook URLs for each section
    private object Urls {
        const val MARKETPLACE = "https://www.facebook.com/marketplace/"
        const val MESSAGES    = "https://www.facebook.com/messages/"
    }

    private enum class Tab {
        MARKETPLACE,
        MESSAGES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupBottomNav()
        setupSwipeRefresh()
        setupErrorButtons()

        // Load initial tab
        if (savedInstanceState == null) {
            loadTab(Tab.MARKETPLACE)
        }
    }

    // ------------------------------------------------------------------ WebView

    private fun setupWebView() {
        val webView = binding.webView

        // Configure settings
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            // Set a real mobile user-agent so Facebook serves the mobile site
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        // Accept cookies (required for Facebook login)
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // Set our custom clients
        webView.webViewClient = FacebookWebViewClient(
            onPageStarted = { showLoading() },
            onPageFinished = { hideLoading() },
            onPageError = { showError() },
            onBlockedUrl = { showBlockedScreen() },
            onProgressUpdate = { binding.progressBar.progress = it }
        )

        webView.webChromeClient = FacebookWebChromeClient(
            onProgressChanged = { progress ->
                binding.progressBar.apply {
                    visibility = if (progress < 100) View.VISIBLE else View.GONE
                    this.progress = progress
                }
                binding.swipeRefresh.isRefreshing = false
            },
            onTitleReceived = { /* optional: update title */ }
        )
    }

    // ------------------------------------------------------------------ Bottom Navigation

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val tab = when (item.itemId) {
                R.id.nav_marketplace -> Tab.MARKETPLACE
                R.id.nav_messages    -> Tab.MESSAGES
                else -> return@setOnItemSelectedListener false
            }
            if (tab != currentTab) {
                loadTab(tab)
            }
            true
        }
    }

    private fun loadTab(tab: Tab) {
        currentTab = tab

        // Hide any error/blocked screens
        hideAllOverlays()

        // Check network before loading
        if (!isNetworkAvailable()) {
            showError()
            return
        }

        val url = when (tab) {
            Tab.MARKETPLACE -> Urls.MARKETPLACE
            Tab.MESSAGES    -> Urls.MESSAGES
        }

        // Update title in top bar
        binding.tvTitle.text = when (tab) {
            Tab.MARKETPLACE -> getString(R.string.nav_marketplace)
            Tab.MESSAGES    -> getString(R.string.nav_messages)
        }

        // Only reload if the current URL differs
        val currentUrl = binding.webView.url ?: ""
        if (!currentUrl.startsWith(url.removeSuffix("/"))) {
            binding.webView.loadUrl(url)
        }

        // Sync bottom nav selection
        val itemId = when (tab) {
            Tab.MARKETPLACE -> R.id.nav_marketplace
            Tab.MESSAGES    -> R.id.nav_messages
        }
        binding.bottomNav.selectedItemId = itemId
    }

    // ------------------------------------------------------------------ SwipeRefresh

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            getColor(R.color.facebook_blue)
        )
        binding.swipeRefresh.setOnRefreshListener {
            hideAllOverlays()
            if (isNetworkAvailable()) {
                binding.webView.reload()
            } else {
                binding.swipeRefresh.isRefreshing = false
                showError()
            }
        }
    }

    // ------------------------------------------------------------------ Error / Blocked screens

    private fun setupErrorButtons() {
        binding.btnRetry.setOnClickListener {
            hideAllOverlays()
            if (isNetworkAvailable()) {
                binding.webView.reload()
            } else {
                showError()
            }
        }

        binding.btnGoMarketplace.setOnClickListener {
            loadTab(Tab.MARKETPLACE)
        }
    }

    private fun showLoading() {
        hideAllOverlays()
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
    }

    private fun showError() {
        binding.swipeRefresh.visibility = View.GONE
        binding.errorView.visibility = View.VISIBLE
        binding.blockedView.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
    }

    private fun showBlockedScreen() {
        binding.swipeRefresh.visibility = View.GONE
        binding.blockedView.visibility = View.VISIBLE
        binding.errorView.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
    }

    private fun hideAllOverlays() {
        binding.swipeRefresh.visibility = View.VISIBLE
        binding.errorView.visibility = View.GONE
        binding.blockedView.visibility = View.GONE
    }

    // ------------------------------------------------------------------ Network

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ------------------------------------------------------------------ Back Press

    @Deprecated("Using legacy onKeyDown for WebView back navigation")
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val webView = binding.webView
            if (webView.canGoBack()) {
                val backForwardList = webView.copyBackForwardList()
                // Walk back only within allowed pages
                val targetIndex = findSafeBackIndex(backForwardList, webView.copyBackForwardList().currentIndex)
                if (targetIndex >= 0) {
                    val steps = webView.copyBackForwardList().currentIndex - targetIndex
                    webView.goBackOrForward(-steps)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun findSafeBackIndex(list: android.webkit.WebBackForwardList, currentIndex: Int): Int {
        for (i in currentIndex - 1 downTo 0) {
            val item = list.getItemAtIndex(i)
            val url = item?.url ?: continue
            if (isUrlAllowed(url)) return i
        }
        return -1
    }

    private fun isUrlAllowed(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("/marketplace") || lower.contains("/messages") || lower.contains("/login")
    }

    // ------------------------------------------------------------------ Save state

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
        outState.putString("current_tab", currentTab.name)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        binding.webView.restoreState(savedInstanceState)
        currentTab = Tab.valueOf(savedInstanceState.getString("current_tab", Tab.MARKETPLACE.name)!!)
        // Sync bottom nav
        binding.bottomNav.selectedItemId = when (currentTab) {
            Tab.MARKETPLACE -> R.id.nav_marketplace
            Tab.MESSAGES    -> R.id.nav_messages
        }
    }

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onDestroy() {
        binding.webView.apply {
            stopLoading()
            clearHistory()
            destroy()
        }
        super.onDestroy()
    }
}
