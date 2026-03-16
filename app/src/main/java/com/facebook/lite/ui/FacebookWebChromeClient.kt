package com.facebook.lite.ui

import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Handles page load progress updates and title changes.
 */
class FacebookWebChromeClient(
    private val onProgressChanged: (Int) -> Unit,
    private val onTitleReceived: (String?) -> Unit
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        super.onReceivedTitle(view, title)
        onTitleReceived(title)
    }
}
