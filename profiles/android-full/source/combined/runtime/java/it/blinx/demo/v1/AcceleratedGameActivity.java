// SPDX-License-Identifier: GPL-3.0-or-later
package it.blinx.demo.v1;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.view.Gravity;
import com.rfandango.haku_x.NativeCoreActivity;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Standalone host of the unchanged, private hakuX 1.3.0 ARM64 libraries. */
public final class AcceleratedGameActivity extends NativeCoreActivity {
    public static final String EDITION = "edition";
    public static final String DISC_PATH = "disc_path";
    public static final String XBOX_LANGUAGE = "xbox_language";
    public static final String SHOW_FPS = "show_fps";
    public static final String DEMO_LOCALE_MANIFEST = "demo_locale_manifest";
    static final String RELAUNCH = "core_relaunch";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TouchControls controls;
    private XboxTouchInput touch;
    private TextView fps;
    private TextView status;
    private TextView fpsButton;
    private TextView menuButton;
    private TextView utilitiesButton;
    private String[] arguments;
    private String edition;
    private boolean loaded, foreground, exiting, failed, firstFrame;
    private int railWidth;

    public static Intent intent(Context context, String edition, String discPath,
            int xboxLanguage, boolean showFps) {
        return new Intent(context, AcceleratedGameActivity.class)
                .putExtra(EDITION, edition).putExtra(DISC_PATH, discPath)
                .putExtra(XBOX_LANGUAGE, xboxLanguage).putExtra(SHOW_FPS, showFps);
    }

    @Override protected String[] getLibraries() { return new String[] {"SDL3", "hakuxcore"}; }
    @Override protected String[] getArguments() { return arguments.clone(); }
    @Override protected boolean keepFullscreen() { return true; }
    @Override public void loadLibraries() { super.loadLibraries(); loaded = true; }

    @Override protected void onCreate(Bundle state) {
        if (!android.app.Application.getProcessName().equals(getPackageName() + ":game")) {
            throw new IllegalStateException("The native activity must run in its private :game process");
        }
        prepareArguments();
        if (!getIntent().getBooleanExtra(RELAUNCH, false)) {
            RuntimeSettings.setFpsEnabled(this, getIntent().getBooleanExtra(SHOW_FPS, false));
        }
        super.onCreate(state);
        if (!loaded || mLayout == null) return; // SDL displays its own loader error.
        touch = new XboxTouchInput(NativeCoreActivity::nativeSetOverlayPad);
        controls = new TouchControls(this, touch::key);
        mLayout.addView(controls, new ViewGroup.LayoutParams(-1, -1));
        utilitiesButton = utility("⋯", true);
        utilitiesButton.setTextSize(16);
        utilitiesButton.setContentDescription("Show game controls");
        fpsButton = utility("FPS", true);
        fpsButton.setOnClickListener(view -> {
            RuntimeSettings.setFpsEnabled(this, !RuntimeSettings.isFpsEnabled(this));
            updateFpsVisibility();
        });
        menuButton = utility("Menu", false);
        menuButton.setOnClickListener(view -> leaveGame());
        setUtilitiesVisible(false);
        utilitiesButton.setOnClickListener(view -> setUtilitiesVisible(fpsButton.getVisibility() != View.VISIBLE));
        fps = label();
        RelativeLayout.LayoutParams fpsParams = new RelativeLayout.LayoutParams(-2, -2);
        fpsParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        fpsParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        mLayout.addView(fps, fpsParams);
        status = label();
        status.setText("Starting " + (edition.equals("demo") ? "demo" : "PAL") + "...");
        RelativeLayout.LayoutParams statusParams = new RelativeLayout.LayoutParams(-2, -2);
        statusParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        mLayout.addView(status, statusParams);
        mLayout.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> placeGame(r-l, b-t));
        mLayout.post(() -> placeGame(mLayout.getWidth(), mLayout.getHeight()));
        updateFpsVisibility();
        Log.i("BlinxRuntime", "core=hakuX-1.3.0 edition=" + edition + " args=" + Arrays.toString(arguments));
    }

    private void prepareArguments() {
        edition = getIntent().getStringExtra(EDITION);
        if (!"demo".equals(edition) && !"pal".equals(edition)) throw new IllegalArgumentException("Unknown edition");
        int language = getIntent().getIntExtra(XBOX_LANGUAGE, 1);
        if (language < 0 || language > 9) throw new IllegalArgumentException("Xbox language must be 0..9");
        try {
            File root = new File(getFilesDir(), "editions/" + edition).getCanonicalFile();
            File disc = new File(getIntent().getStringExtra(DISC_PATH)).getCanonicalFile();
            if (!disc.equals(new File(root, "disc.iso")) || !disc.isFile() || !disc.canRead()) {
                throw new IllegalArgumentException("Prepare the edition's private disc before launch");
            }
            File hdd = new File(root, "hdd");
            if (!hdd.isDirectory() && !hdd.mkdirs()) throw new IOException("Cannot create edition HDD");
            List<String> args = new ArrayList<>(Arrays.asList(disc.getPath(), hdd.getPath(),
                    "version=hakuX-1.3.0", "scale=1", "aspect=auto", "fxaa=0", "upscale=bilinear",
                    "asyncpipe=1", "prebuild=1", "devtime=1", "pads=1", "padinput=sdl", "net=off",
                    "tz=" + TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000));
            if (language != 0) args.add("lang=" + language);
            String localeManifest = DemoLocale.forLaunch(getFilesDir(), edition, disc, language,
                    getIntent().getStringExtra(DEMO_LOCALE_MANIFEST));
            if (localeManifest != null) args.add("mods=" + localeManifest);
            if (getIntent().getBooleanExtra(RELAUNCH, false)) args.add("relaunch");
            arguments = args.toArray(new String[0]);
        } catch (IOException error) { throw new IllegalStateException("Cannot prepare runtime storage", error); }
    }

    private TextView label() {
        TextView view = new TextView(this);
        view.setTextColor(Color.WHITE);
        view.setTextSize(13);
        view.setPadding(12, 5, 12, 5);
        view.setBackgroundColor(0xb0101823);
        return view;
    }

    private TextView utility(String label, boolean left) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(Color.WHITE);
        view.setTextSize(9);
        view.setGravity(Gravity.CENTER);
        view.setBackground(BlinxArt.frame(this, false));
        view.setPadding(0, 0, 0, 0);
        view.setFocusable(true);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(0, dp(32));
        params.addRule(left ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        params.setMargins(dp(3), 0, dp(3), dp(7));
        mLayout.addView(view, params);
        return view;
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    private void setUtilitiesVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.INVISIBLE;
        fpsButton.setVisibility(visibility);
        menuButton.setVisibility(visibility);
        utilitiesButton.setContentDescription(visible ? "Hide game controls" : "Show game controls");
        utilitiesButton.setBackground(BlinxArt.frame(this, visible));
        utilitiesButton.setPadding(0, 0, 0, 0);
    }
    private void updateFpsVisibility() {
        if (fps == null) return;
        boolean enabled = RuntimeSettings.isFpsEnabled(this);
        fps.setVisibility(enabled ? View.VISIBLE : View.GONE);
        fpsButton.setContentDescription(enabled ? "FPS: on" : "FPS: off");
        fpsButton.setBackground(BlinxArt.frame(this, enabled));
        fpsButton.setPadding(0, 0, 0, 0);
    }
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!loaded || !foreground || exiting) return;
            String fatal = nativeGetFatal();
            if (fatal != null && !failed) {
                failed = true;
                Log.e("BlinxRuntime", "core failed kind=" + nativeGetFatalKind() + " " + fatal);
                nativeSetPaused(true);
                new AlertDialog.Builder(AcceleratedGameActivity.this).setTitle("Game stopped")
                        .setMessage(fatal).setCancelable(false)
                        .setPositiveButton("Menu", (dialog, which) -> leaveGame()).show();
            }
            if (!failed && nativeGetRelaunch()) { restartCore(); return; }
            if (!failed && nativeGetQuit()) { leaveGame(); return; }
            if (nativeGetFrameShown()) {
                status.setVisibility(View.GONE);
                if (!firstFrame) {
                    firstFrame = true;
                    Log.i("BlinxRuntime", "first native game frame presented; edition=" + edition);
                }
            } else if (!failed) {
                int[] shaders = nativeGetShaderBuild();
                if (shaders != null && shaders.length >= 2 && shaders[1] > 0) {
                    status.setText("Preparing shaders " + shaders[0] + "/" + shaders[1]);
                }
            }
            if (RuntimeSettings.isFpsEnabled(AcceleratedGameActivity.this)) {
                float[] stats = nativeGetFrameStats();
                if (stats != null && stats.length >= 3) {
                    fps.setText(String.format(Locale.ROOT, "%.1f FPS  |  %.1f ms", stats[0], stats[1]));
                }
            }
            handler.postDelayed(this, 500);
        }
    };
    private void placeGame(int width, int height) {
        if (controls == null || mSurface == null || width <= 0 || height <= 0) return;
        float density = getResources().getDisplayMetrics().density;
        int gutter = (width - height * 4 / 3) / 2;
        int rails = Math.max(Math.round(104 * density), Math.min(Math.round(152 * density), gutter));
        rails = Math.min(rails, width / 4);
        if (rails == railWidth) return;
        railWidth = rails;
        RelativeLayout.LayoutParams game = new RelativeLayout.LayoutParams(-1, -1);
        game.setMargins(rails, 0, rails, 0);
        mSurface.setLayoutParams(game);
        controls.setRailWidth(rails);
        int utilityWidth = Math.max(0, Math.min(dp(42), rails / 2 - dp(27)));
        for (TextView button : new TextView[] {utilitiesButton, fpsButton, menuButton}) {
            if (button == null) continue;
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)button.getLayoutParams();
            params.width = utilityWidth;
            if (button == fpsButton) params.leftMargin = rails - utilityWidth - dp(3);
            button.setLayoutParams(params);
        }
    }
    @Override public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        super.setOrientationBis(w, h, resizable, "LandscapeLeft LandscapeRight");
    }
    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        if (loaded) nativeSetPaused(failed);
        updateFpsVisibility();
        handler.removeCallbacks(poll);
        if (loaded && controls != null) handler.postDelayed(poll, 500);
    }
    @Override protected void onPause() {
        foreground = false;
        handler.removeCallbacks(poll);
        releaseTouch();
        if (loaded) nativeSetPaused(true);
        super.onPause();
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        if (!focused) releaseTouch();
        super.onWindowFocusChanged(focused);
    }
    private void releaseTouch() {
        if (controls != null) controls.releaseAll();
        if (touch != null) touch.clear();
    }
    @Override public void onBackPressed() { leaveGame(); }
    private void leaveGame() {
        if (exiting) return;
        exiting = true;
        releaseTouch();
        handler.removeCallbacks(poll);
        // SDL onDestroy sends QUIT; the native core then exits this :game process.
        finish();
    }
    private void restartCore() {
        exiting = true;
        releaseTouch();
        Intent next = new Intent(getIntent()).putExtra(RELAUNCH, true);
        startActivity(new Intent(this, CoreRestartActivity.class)
                .putExtra(CoreRestartActivity.NEXT, next)
                .putExtra(CoreRestartActivity.OLD_PID, android.os.Process.myPid()));
        finish();
        // The core has stopped after XLaunchNewImage. Recreate its global state.
        android.os.Process.killProcess(android.os.Process.myPid());
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releaseTouch();
        super.onDestroy();
        // QUIT may arrive before SDL_Init during an early cancel. Never leave a
        // partly booted core alive for the next edition; this is only :game.
        if (isFinishing() && android.app.Application.getProcessName().equals(getPackageName() + ":game")) {
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
