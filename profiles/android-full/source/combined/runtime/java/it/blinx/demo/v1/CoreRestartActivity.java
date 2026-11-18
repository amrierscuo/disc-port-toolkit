// SPDX-License-Identifier: GPL-3.0-or-later
package it.blinx.demo.v1;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.TextView;
import java.util.List;

/** Short relay in the frontend process for guest XLaunchNewImage. */
public final class CoreRestartActivity extends Activity {
    static final String NEXT = "next_core_intent", OLD_PID = "previous_core_pid";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long started;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView label = new TextView(this);
        label.setText("Restarting game...");
        setContentView(label);
        started = SystemClock.uptimeMillis();
        handler.post(waitForExit);
    }
    private final Runnable waitForExit = new Runnable() {
        @Override public void run() {
            int oldPid = getIntent().getIntExtra(OLD_PID, -1);
            ActivityManager manager = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
            boolean alive = false;
            if (processes != null) for (ActivityManager.RunningAppProcessInfo p : processes) {
                if (p.pid == oldPid && p.processName.equals(getPackageName() + ":game")) alive = true;
            }
            if (alive) {
                if (SystemClock.uptimeMillis() - started < 10000) handler.postDelayed(this, 100);
                else finish();
                return;
            }
            Intent next = getIntent().getParcelableExtra(NEXT);
            if (next != null) {
                next.setClass(CoreRestartActivity.this, AcceleratedGameActivity.class);
                startActivity(next);
            }
            finish();
        }
    };
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
