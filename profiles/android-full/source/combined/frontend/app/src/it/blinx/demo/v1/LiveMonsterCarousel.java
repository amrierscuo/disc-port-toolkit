package it.blinx.demo.v1;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Local, noninteractive WebGL preview slot. All public methods may be called repeatedly. */
public final class LiveMonsterCarousel extends FrameLayout {
    private static final String TAG = "LiveMonsterCarousel";
    private static final String HOST = "blinx-carousel.invalid";
    private static final String PAGE = "https://" + HOST + "/index.html";
    private static final String ROOT = "monsters_live/";
    private static final String CSP = "default-src 'none'; script-src 'self' 'unsafe-inline'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self'; connect-src 'self'; "
            + "font-src 'none'; media-src 'none'; object-src 'none'; frame-src 'none'; "
            + "worker-src 'none'; base-uri 'none'; form-action 'none'";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final AssetManager assets;
    private WebView browser;
    private MonsterCarousel fallback;
    private boolean attached;
    private boolean enabled = true;
    private boolean released;
    private boolean usingFallback;
    private boolean ready;
    private boolean active;
    private boolean showLabels = true;
    private long pollGeneration;
    private long pollStartedAt;
    private boolean pollInFlight;
    private final Runnable diagnosticPoll = this::pollDiagnostics;
    private final Runnable diagnosticTimeout = () -> {
        if (pollInFlight && shouldRun() && browser != null && !usingFallback)
            activateFallback("WebGL diagnostic request stalled");
    };

    public LiveMonsterCarousel(Context context) { this(context, null); }

    public LiveMonsterCarousel(Context context, AttributeSet attributes) {
        super(context, attributes);
        assets = context.getApplicationContext().getAssets();
        setBackgroundColor(Color.TRANSPARENT);
        setClipChildren(true);
        setClipToPadding(true);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("3D character previews");
    }

    public void setShowLabels(final boolean show) {
        if (Looper.myLooper() != Looper.getMainLooper()) { ui.post(() -> setShowLabels(show)); return; }
        if (released) return;
        showLabels = show;
        if (fallback != null) fallback.setShowLabels(show);
        applyJavascriptState();
    }

    /** Owner onResume hook. State is remembered until the local page is ready. */
    public void start() {
        if (Looper.myLooper() != Looper.getMainLooper()) { ui.post(this::start); return; }
        if (released) return;
        enabled = true;
        reconcile();
    }

    /** Owner onPause hook. This does not pause timers in unrelated WebViews. */
    public void stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) { ui.post(this::stop); return; }
        if (released) return;
        enabled = false;
        reconcile();
    }

    /** Terminal owner onDestroy hook. A detached view can otherwise be reattached. */
    public void release() {
        if (Looper.myLooper() != Looper.getMainLooper()) { ui.post(this::release); return; }
        if (released) return;
        released = true;
        enabled = false;
        ui.removeCallbacksAndMessages(null);
        destroyBrowser();
        if (fallback != null) {
            fallback.release();
            removeView(fallback);
            fallback = null;
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        reconcile();
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        destroyBrowser();
        if (fallback != null) fallback.stop();
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (assets != null) reconcile();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (assets != null) reconcile();
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) { return true; }
    @Override public boolean onTouchEvent(MotionEvent event) { return false; }

    private boolean shouldRun() {
        return !released && enabled && attached && isShown() && getWindowVisibility() == VISIBLE;
    }

    private void reconcile() {
        boolean run = shouldRun();
        if (usingFallback) {
            if (fallback != null) {
                if (run) fallback.start(); else fallback.stop();
            }
            return;
        }
        if (run && browser == null) {
            try { createBrowser(); }
            catch (RuntimeException error) { activateFallback("WebView unavailable: " + error.getClass().getSimpleName()); return; }
        }
        if (browser == null) return;
        if (run) {
            if (!active) browser.onResume();
            active = true;
            applyJavascriptState();
            if (ready && pollStartedAt == 0) {
                pollStartedAt = SystemClock.uptimeMillis();
                ui.post(diagnosticPoll);
            }
        } else {
            active = false;
            cancelDiagnostics();
            applyJavascriptState();
            browser.onPause();
        }
    }

    @SuppressWarnings("deprecation")
    private void createBrowser() {
        final WebView view = new WebView(getContext());
        browser = view;
        ready = false;
        active = false;
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        view.setFocusable(false);
        view.setFocusableInTouchMode(false);
        view.setClickable(false);
        view.setLongClickable(false);
        view.setHapticFeedbackEnabled(false);
        view.setHorizontalScrollBarEnabled(false);
        view.setVerticalScrollBarEnabled(false);
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setBlockNetworkLoads(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setGeolocationEnabled(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSaveFormData(false);
        settings.setSafeBrowsingEnabled(false);
        view.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
        });
        view.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView target, WebResourceRequest request) {
                if (!"GET".equals(request.getMethod())) return blocked();
                return localResponse(request.getUrl());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView target, String url) {
                return localResponse(Uri.parse(url));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView target, WebResourceRequest request) { return true; }
            @Override public boolean shouldOverrideUrlLoading(WebView target, String url) { return true; }
            @Override public void onPageFinished(WebView target, String url) {
                if (target != browser || released || !PAGE.equals(url)) return;
                ready = true;
                reconcile();
            }
            @Override public void onReceivedError(WebView target, WebResourceRequest request, WebResourceError error) {
                if (target != browser || !request.isForMainFrame()) return;
                ready = false;
                activateFallback("Local preview page unavailable: " + error.getErrorCode());
            }
            @Override public void onReceivedHttpError(WebView target, WebResourceRequest request, WebResourceResponse error) {
                if (target != browser || !request.isForMainFrame()) return;
                ready = false;
                activateFallback("Local preview page status: " + error.getStatusCode());
            }
            @Override public boolean onRenderProcessGone(WebView target, RenderProcessGoneDetail detail) {
                if (target == browser) {
                    ready = false;
                    activateFallback("WebView renderer stopped");
                }
                return true;
            }
        });
        addView(view, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        view.loadUrl(PAGE);
    }

    private void applyJavascriptState() {
        if (browser == null || !ready) return;
        String command = "(function(){var c=window.BlinxCarousel;if(!c)return;"
                + "c.setShowLabels(" + (showLabels ? "true" : "false") + ");c."
                + (shouldRun() ? "start" : "stop") + "();})();";
        browser.evaluateJavascript(command, null);
    }

    private void destroyBrowser() {
        cancelDiagnostics();
        WebView old = browser;
        browser = null;
        boolean pageWasReady = ready;
        ready = false;
        active = false;
        if (old == null) return;
        // JavaScript cleanup is best effort; destroy() also tears down a stalled page.
        if (pageWasReady) old.evaluateJavascript("(function(){var c=window.BlinxCarousel;if(c)c.release();})();", null);
        old.stopLoading();
        old.onPause();
        removeView(old);
        old.removeAllViews();
        old.destroy();
    }

    private void cancelDiagnostics() {
        pollGeneration++;
        pollStartedAt = 0;
        pollInFlight = false;
        ui.removeCallbacks(diagnosticPoll);
        ui.removeCallbacks(diagnosticTimeout);
    }

    private void pollDiagnostics() {
        if (!shouldRun() || usingFallback || browser == null || !ready || pollInFlight) return;
        final WebView target = browser;
        final long request = pollGeneration;
        pollInFlight = true;
        ui.postDelayed(diagnosticTimeout, 10000);
        String script = "(function(){try{var c=window.BlinxCarousel;"
                + "return c&&typeof c.diagnostics==='function'?c.diagnostics():null;}"
                + "catch(e){return {failed:true,errors:[String(e)]};}})();";
        target.evaluateJavascript(script, value -> {
            if (target != browser || request != pollGeneration || released || usingFallback) return;
            pollInFlight = false;
            ui.removeCallbacks(diagnosticTimeout);
            if (!shouldRun()) return;
            long elapsed = SystemClock.uptimeMillis() - pollStartedAt;
            JSONObject status = null;
            try { if (value != null && !"null".equals(value)) status = new JSONObject(value); }
            catch (Exception invalidDiagnostic) { /* An API still loading gets the same startup grace. */ }
            if (status != null && (status.optBoolean("failed") || status.optBoolean("released"))) {
                String errors = String.valueOf(status.optJSONArray("errors"));
                activateFallback("WebGL unavailable: " + errors.substring(0, Math.min(errors.length(), 400)));
                return;
            }
            if (status == null && elapsed >= 10000) {
                activateFallback("WebGL diagnostic API unavailable");
                return;
            }
            // An initialized:false reply without failed:true can keep loading.
            // Continue a slower health check afterward to catch later context loss.
            ui.postDelayed(diagnosticPoll, elapsed < 10000 ? 250 : 1000);
        });
    }

    private void activateFallback(String reason) {
        if (released || usingFallback) return;
        Log.w(TAG, reason + "; showing static model previews");
        usingFallback = true;
        destroyBrowser();
        fallback = new MonsterCarousel(getContext());
        fallback.stop();
        fallback.setShowLabels(showLabels);
        addView(fallback, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        reconcile();
    }

    private WebResourceResponse localResponse(Uri uri) {
        if (!"https".equals(uri.getScheme()) || !HOST.equals(uri.getEncodedAuthority())
                || uri.getEncodedQuery() != null || uri.getEncodedFragment() != null) return blocked();
        String path = uri.getEncodedPath();
        if (path == null || !path.matches("/[A-Za-z0-9_./-]+")) return blocked();
        String relative = path.substring(1);
        for (String part : relative.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) return blocked();
        }
        String mime = mime(relative);
        if (mime == null) return blocked();
        try {
            InputStream input = assets.open(ROOT + relative, AssetManager.ACCESS_STREAMING);
            return new WebResourceResponse(mime, textMime(mime) ? "UTF-8" : null,
                    200, "OK", headers(), input);
        } catch (IOException unavailable) {
            return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                    headers(), new ByteArrayInputStream(new byte[0]));
        }
    }

    private static WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden",
                headers(), new ByteArrayInputStream(new byte[0]));
    }

    private static Map<String, String> headers() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Security-Policy", CSP);
        headers.put("Cache-Control", "no-store");
        headers.put("X-Content-Type-Options", "nosniff");
        headers.put("Referrer-Policy", "no-referrer");
        return headers;
    }

    private static boolean textMime(String mime) {
        return mime.startsWith("text/") || "application/javascript".equals(mime)
                || "application/json".equals(mime) || "model/gltf+json".equals(mime);
    }

    private static String mime(String path) {
        String suffix = path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        switch (suffix) {
            case "html": return "text/html";
            case "js": case "mjs": return "application/javascript";
            case "json": return "application/json";
            case "css": return "text/css";
            case "vert": case "frag": return "text/plain";
            case "png": return "image/png";
            case "jpg": case "jpeg": return "image/jpeg";
            case "webp": return "image/webp";
            case "bin": return "application/octet-stream";
            case "glb": return "model/gltf-binary";
            case "gltf": return "model/gltf+json";
            default: return null;
        }
    }
}
