package com.localapp.mediaserver;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.WebView;

public class PersistentWebView extends WebView {

    private boolean backgroundPlayEnabled = true;

    public PersistentWebView(Context context) {
        super(context);
    }

    public PersistentWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PersistentWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setBackgroundPlayEnabled(boolean enabled) {
        this.backgroundPlayEnabled = enabled;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        if (backgroundPlayEnabled && visibility != View.VISIBLE) {
            // Keep WebView media playback alive in background
            super.onWindowVisibilityChanged(View.VISIBLE);
        } else {
            super.onWindowVisibilityChanged(visibility);
        }
    }
}
