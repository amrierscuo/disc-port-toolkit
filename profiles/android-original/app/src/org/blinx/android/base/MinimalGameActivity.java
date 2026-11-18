// SPDX-License-Identifier: GPL-3.0-or-later
package org.blinx.android.base;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import com.rfandango.haku_x.NativeCoreActivity;
import java.io.File;
import java.util.TimeZone;

/** Single PAL game with generated touch controls and SDL controller input. */
public final class MinimalGameActivity extends NativeCoreActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String[] arguments;
    private boolean loaded, leaving, failed;
    private TouchControls controls;
    private XboxTouchInput touch;
    private int railWidth = -1;
    @Override protected String[] getLibraries() { return new String[] {"SDL3", "hakuxcore"}; }
    @Override protected String[] getArguments() { return arguments.clone(); }
    @Override protected boolean keepFullscreen() { return true; }
    @Override public void loadLibraries() { super.loadLibraries(); loaded = true; }
    @Override protected void onCreate(Bundle state) {
        if (!android.app.Application.getProcessName().equals(getPackageName() + ":game")) {
            throw new IllegalStateException("Core must run in its own :game process");
        }
        File root = new File(getFilesDir(), "game");
        File disc;
        try {
            disc = DiscStore.image(root, ImportActivity.identity(this));
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Private PAL disc identity missing", error);
        }
        File hdd = new File(root, "hdd");
        if (!disc.isFile() || (!hdd.isDirectory() && !hdd.mkdirs())) {
            throw new IllegalStateException("Import the game before starting the core");
        }
        String[] ordinary = new String[] {disc.getPath(), hdd.getPath(), "version=hakuX-1.3.0",
            "scale=1", "aspect=auto", "fxaa=0", "upscale=bilinear", "asyncpipe=1", "prebuild=1",
            "devtime=1", "pads=1", "padinput=sdl", "net=off", "lang=1",
            "tz=" + TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000};
        if (getIntent().getBooleanExtra("relaunch", false)) {
            arguments = java.util.Arrays.copyOf(ordinary, ordinary.length + 1);
            arguments[ordinary.length] = "relaunch";
        } else arguments = ordinary;
        super.onCreate(state);
        if (loaded && mLayout != null) {
            touch = new XboxTouchInput(NativeCoreActivity::nativeSetOverlayPad);
            controls = new TouchControls(this, touch::key);
            mLayout.addView(controls, new android.widget.RelativeLayout.LayoutParams(-1, -1));
            mLayout.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> placeGame(r - l, b - t));
            mLayout.post(() -> placeGame(mLayout.getWidth(), mLayout.getHeight()));
            handler.postDelayed(poll, 250);
        }
    }
    private void placeGame(int width, int height) {
        if (width <= 0 || height <= 0 || mSurface == null) return;
        float density = getResources().getDisplayMetrics().density;
        int gutter = (width - height * 4 / 3) / 2;
        int rails = Math.min(width / 4, Math.max(Math.round(104 * density),
                Math.min(Math.round(152 * density), gutter)));
        if (rails == railWidth) return;
        railWidth = rails;
        android.widget.RelativeLayout.LayoutParams game = new android.widget.RelativeLayout.LayoutParams(-1, -1);
        game.setMargins(rails, 0, rails, 0);
        mSurface.setLayoutParams(game);
        controls.setRailWidth(rails);
    }
    private void releaseTouch() {
        if (controls != null) controls.releaseAll();
        if (touch != null) touch.clear();
    }
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!loaded || leaving || failed) return;
            String error = nativeGetFatal();
            if (error != null && !error.isEmpty()) {
                failed = true;
                nativeSetPaused(true);
                new AlertDialog.Builder(MinimalGameActivity.this).setTitle("Core stopped")
                    .setMessage(error).setPositiveButton("Close", (d,w) -> finish()).show();
                return;
            }
            if (nativeGetRelaunch()) {
                leaving = true;
                Intent relay = new Intent(MinimalGameActivity.this, RestartActivity.class)
                    .putExtra("old_pid", android.os.Process.myPid());
                startActivity(relay);
                finish();
                return;
            }
            if (nativeGetQuit()) { leaving = true; finish(); return; }
            handler.postDelayed(this, 250);
        }
    };
    @Override protected void onResume() { super.onResume(); if (loaded) nativeSetPaused(failed); }
    @Override protected void onPause() { releaseTouch(); if (loaded) nativeSetPaused(true); super.onPause(); }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (!focus) releaseTouch();
        if (loaded) nativeSetPaused(!focus || failed);
    }
    @Override public void onBackPressed() { leaving = true; finish(); }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releaseTouch();
        super.onDestroy();
        // SDL requests QUIT. The fallback can terminate only this app's core process.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (android.app.Application.getProcessName().equals(getPackageName() + ":game"))
                android.os.Process.killProcess(android.os.Process.myPid());
        }, 1000);
    }
}
