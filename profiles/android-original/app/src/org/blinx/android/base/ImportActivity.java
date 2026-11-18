// SPDX-License-Identifier: GPL-3.0-or-later
package org.blinx.android.base;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Imports only the single, locally packaged PAL image and retains private saves. */
public final class ImportActivity extends Activity {
    static DiscStore.Identity identity(Context context) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("disc/identity.txt"), StandardCharsets.US_ASCII))) {
            String line = reader.readLine();
            if (line == null || reader.readLine() != null) throw new IOException("Invalid packaged identity");
            return new DiscStore.Identity(line);
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView status = new TextView(this);
        status.setGravity(android.view.Gravity.CENTER);
        status.setText("Preparing your PAL disc...");
        setContentView(status);
        new Thread(() -> {
            try {
                DiscStore.prepare(new File(getFilesDir(), "game"), identity(this),
                        () -> getAssets().open("disc/disc.iso"));
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    startActivity(new Intent(this, MinimalGameActivity.class));
                    finish();
                });
            } catch (IOException error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    new AlertDialog.Builder(this).setTitle("Disc preparation failed")
                            .setMessage(error.getMessage()).setPositiveButton("Close", (d, w) -> finish()).show();
                });
            }
        }, "private-disc-import").start();
    }
}
