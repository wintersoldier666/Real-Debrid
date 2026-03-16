package com.mktplace.messenger.ui

import android.webkit.WebChromeClient
import android.webkit.WebView

class FacebookWebChromeClient(
    private val onProgressChanged: (Int) -> Unit
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(newProgress)
    }
}
