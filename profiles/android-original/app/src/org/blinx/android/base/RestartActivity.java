// SPDX-License-Identifier: GPL-3.0-or-later
package org.blinx.android.base;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** XLaunchNewImage relay; observes only this application's previous core PID. */
public final class RestartActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long started;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        started = SystemClock.uptimeMillis();
        handler.post(check);
    }
    private final Runnable check = new Runnable() {
        @Override public void run() {
            int old = getIntent().getIntExtra("old_pid", -1);
            ActivityManager manager = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
            java.util.List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
            boolean alive = false;
            if (processes != null) for (ActivityManager.RunningAppProcessInfo process : processes) {
                if (process.pid == old && process.processName.equals(getPackageName() + ":game")) alive = true;
            }
            if (alive && SystemClock.uptimeMillis() - started < 10000) {
                handler.postDelayed(this, 100); return;
            }
            if (!alive) startActivity(new Intent(RestartActivity.this, MinimalGameActivity.class).putExtra("relaunch", true));
            finish();
        }
    };
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
