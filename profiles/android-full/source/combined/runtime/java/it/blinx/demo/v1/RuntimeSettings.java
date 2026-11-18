// SPDX-License-Identifier: GPL-3.0-or-later
package it.blinx.demo.v1;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Read on demand in either process; no cross-process SharedPreferences cache. */
public final class RuntimeSettings {
    private RuntimeSettings() {}
    private static AtomicFile fpsFile(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), "show_fps.setting"));
    }
    public static boolean isFpsEnabled(Context context) {
        try {
            return new String(fpsFile(context).readFully(), StandardCharsets.US_ASCII).trim().equals("1");
        } catch (IOException missing) { return false; }
    }
    public static void setFpsEnabled(Context context, boolean enabled) {
        AtomicFile file = fpsFile(context);
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(enabled ? '1' : '0');
            output.write('\n');
            file.finishWrite(output);
        } catch (IOException error) {
            if (output != null) file.failWrite(output);
            Log.e("BlinxRuntime", "Could not persist FPS setting", error);
        }
    }
}
