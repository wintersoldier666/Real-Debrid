package com.mktplace.messenger.ui;

import android.webkit.WebChromeClient;
import android.webkit.WebView;

public class FacebookWebChromeClient extends WebChromeClient {

    public interface ProgressListener {
        void onProgress(int progress);
    }

    private final ProgressListener listener;

    public FacebookWebChromeClient(ProgressListener listener) {
        this.listener = listener;
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);
        listener.onProgress(newProgress);
    }
}
