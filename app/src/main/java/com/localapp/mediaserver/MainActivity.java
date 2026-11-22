package com.localapp.mediaserver;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private PersistentWebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private View errorLayout;
    private TextView errorText;
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;

    private static final int PERMISSION_REQUEST_CODE = 2026;
    private static final String CONFIG_URL = "http://localhost:3000/api/app-config/proj_1795357705947_6gqnq";
    private static final String FALLBACK_URL = "http://192.168.0.125:8090/Media_Server/index.php";
    private String currentTargetUrl = FALLBACK_URL;

    // Baked-in App Feature & Permission Flags
    private static final boolean ALLOW_GEOLOCATION = true;
    private static final boolean ALLOW_CAMERA = true;
    private static final boolean ALLOW_MICROPHONE = true;
    private static final boolean ALLOW_STORAGE = true;
    private static final boolean BACKGROUND_PLAY = true;
    private static final boolean PULL_TO_REFRESH = true;
    private static final boolean CLEAR_CACHE_ON_EXIT = false;
    private static final boolean OFFLINE_FALLBACK = true;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        errorLayout = findViewById(R.id.errorLayout);
        errorText = findViewById(R.id.errorText);

        fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (filePathCallback != null) {
                    Uri[] results = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri dataUri = result.getData().getData();
                        if (dataUri != null) results = new Uri[]{ dataUri };
                    }
                    filePathCallback.onReceiveValue(results);
                    filePathCallback = null;
                }
            }
        );

        requestRequiredRuntimePermissions();
        configureWebView();

        if (swipeRefresh != null) {
            swipeRefresh.setEnabled(PULL_TO_REFRESH);
            swipeRefresh.setOnRefreshListener(() -> new FetchRemoteConfigTask().execute(CONFIG_URL));
        }

        // Fetch latest dynamic URL from Admin Server
        new FetchRemoteConfigTask().execute(CONFIG_URL);
    }

    private void requestRequiredRuntimePermissions() {
        List<String> needed = new ArrayList<>();

        if (ALLOW_GEOLOCATION) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
                needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
        }
        if (ALLOW_CAMERA) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.CAMERA);
            }
        }
        if (ALLOW_MICROPHONE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.RECORD_AUDIO);
            }
        }
        if (ALLOW_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.READ_MEDIA_IMAGES);
                }
            } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        if (BACKGROUND_PLAY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    private void configureWebView() {
        webView.setBackgroundPlayEnabled(BACKGROUND_PLAY);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setAllowFileAccess(ALLOW_STORAGE);
        settings.setAllowContentAccess(ALLOW_STORAGE);
        settings.setGeolocationEnabled(ALLOW_GEOLOCATION);
        settings.setMediaPlaybackRequiresUserGesture(!BACKGROUND_PLAY);
        settings.setCacheMode(OFFLINE_FALLBACK ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_NO_CACHE);

        // STRICT ANTI-ZOOM LOCK (No Pinch / Double-Tap / Text Zoom Allowed)
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);

        webView.setOnTouchListener((v, event) -> {
            // Block any multi-touch pinch gesture at native MotionEvent level
            return event.getPointerCount() > 1;
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, ALLOW_GEOLOCATION, false);
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    List<String> granted = new ArrayList<>();
                    for (String res : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res) && ALLOW_CAMERA) {
                            granted.add(res);
                        } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res) && ALLOW_MICROPHONE) {
                            granted.add(res);
                        } else if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(res)) {
                            granted.add(res);
                        }
                    }
                    if (!granted.isEmpty()) {
                        request.grant(granted.toArray(new String[0]));
                    } else {
                        request.deny();
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (!ALLOW_STORAGE) return false;
                MainActivity.this.filePathCallback = filePathCallback;
                try {
                    Intent intent = fileChooserParams.createIntent();
                    fileChooserLauncher.launch(intent);
                    return true;
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                errorLayout.setVisibility(View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Enforce No-Zoom Viewport + Touch Lock CSS + JS inside every loaded page
                String antiZoomJs = "(function(){" +
                    "var m=document.querySelector('meta[name=viewport]');" +
                    "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
                    "m.content='width=device-width,initial-scale=1.0,maximum-scale=1.0,minimum-scale=1.0,user-scalable=no';" +
                    "var s=document.getElementById('apk-anti-zoom-style');" +
                    "if(!s){s=document.createElement('style');s.id='apk-anti-zoom-style';" +
                    "s.innerHTML='html,body,*{touch-action:pan-x pan-y !important;-webkit-text-size-adjust:100% !important;}';" +
                    "document.head.appendChild(s);}" +
                    "document.addEventListener('touchstart',function(e){if(e.touches.length>1){e.preventDefault();}},{passive:false});" +
                    "document.addEventListener('gesturestart',function(e){e.preventDefault();},{passive:false});" +
                    "document.addEventListener('wheel',function(e){if(e.ctrlKey){e.preventDefault();}},{passive:false});" +
                    "})();";
                view.evaluateJavascript(antiZoomJs, null);

                // Keep media playing when backgrounded if BACKGROUND_PLAY is enabled
                if (BACKGROUND_PLAY) {
                    String bgPlayJs = "(function(){" +
                        "try{" +
                        "Object.defineProperty(document,'hidden',{get:function(){return false;},configurable:true});" +
                        "Object.defineProperty(document,'visibilityState',{get:function(){return 'visible';},configurable:true});" +
                        "document.addEventListener('visibilitychange',function(e){e.stopImmediatePropagation();},true);" +
                        "}catch(err){}" +
                        "})();";
                    view.evaluateJavascript(bgPlayJs, null);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    if (OFFLINE_FALLBACK) {
                        showError("সার্ভারের সাথে সংযোগ স্থাপন করা যায়নি। আইপি বা নেটওয়ার্ক চেক করুন।");
                    } else {
                        showError("ইন্টারনেট সংযোগ বিচ্ছিন্ন।");
                    }
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
        if (BACKGROUND_PLAY) {
            stopService(new Intent(this, BackgroundAudioService.class));
        }
    }

    @Override
    protected void onPause() {
        if (BACKGROUND_PLAY) {
            // Do NOT pause WebView or timers so audio/video keeps playing in background!
            Intent serviceIntent = new Intent(this, BackgroundAudioService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } else {
            if (webView != null) {
                webView.onPause();
                webView.pauseTimers();
            }
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (BACKGROUND_PLAY) {
            stopService(new Intent(this, BackgroundAudioService.class));
        }
        if (CLEAR_CACHE_ON_EXIT && webView != null) {
            webView.clearCache(true);
            webView.clearHistory();
            CookieManager.getInstance().removeAllCookies(null);
        }
        super.onDestroy();
    }

    private void showError(String msg) {
        webView.setVisibility(View.GONE);
        errorLayout.setVisibility(View.VISIBLE);
        errorText.setText(msg);
        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private class FetchRemoteConfigTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    return sb.toString();
                }
            } catch (Exception ignored) {}
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                try {
                    JSONObject json = new JSONObject(result);
                    String status = json.optString("status", "active");
                    if ("terminated".equalsIgnoreCase(status)) {
                        showError("এই প্রজেক্টটি অ্যাডমিন কর্তৃক ডিলিট অথবা বন্ধ করা হয়েছে।");
                        return;
                    }
                    String remoteUrl = json.optString("targetUrl", currentTargetUrl);
                    if (!remoteUrl.isEmpty()) currentTargetUrl = remoteUrl;
                } catch (Exception ignored) {}
            }
            webView.setVisibility(View.VISIBLE);
            webView.loadUrl(currentTargetUrl);
        }
    }
}
