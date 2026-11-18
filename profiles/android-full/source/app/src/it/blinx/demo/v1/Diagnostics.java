package it.blinx.demo.v1;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.view.InputDevice;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

final class Diagnostics {
    static File collect(Context context) throws Exception {
        File output = new File(context.getCacheDir(), "diagnostics.txt");
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(output), StandardCharsets.UTF_8))) {
            out.println("Blinx Demo V1 - diagnostics");
            out.println("Experimental offline build; device runtime compatibility is not certified.");
            out.println("Time: " + new Date());
            out.println("Package: " + context.getPackageName());
            out.println("Version: " + context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
            out.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
            out.println("Android: " + Build.VERSION.RELEASE + " API " + Build.VERSION.SDK_INT);
            out.println("ABIs: " + Arrays.toString(Build.SUPPORTED_ABIS));
            out.println("Launcher PID: " + Process.myPid());
            out.println("Private free bytes: " + context.getFilesDir().getUsableSpace());
            out.println("Input devices:");
            for (int id : InputDevice.getDeviceIds()) {
                InputDevice input = InputDevice.getDevice(id);
                if (input != null) out.println("  " + input.getName() + " sources=0x" + Integer.toHexString(input.getSources()) + " vendor=" + input.getVendorId() + " product=" + input.getProductId());
            }
            if (Build.VERSION.SDK_INT >= 30) {
                out.println("\nRecent process exits (Android, may include prior runs):");
                ActivityManager manager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
                List<ApplicationExitInfo> exits = manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, 6);
                for (ApplicationExitInfo info : exits) {
                    out.println(new Date(info.getTimestamp()) + " process=" + info.getProcessName() + " pid=" + info.getPid() + " reason=" + info.getReason() + " status=" + info.getStatus() + " description=" + info.getDescription());
                    // Android's own crash/ANR trace is included when provided by the OS.
                    try (InputStream trace = info.getTraceInputStream()) {
                        if (trace != null) {
                            byte[] bytes = new byte[65536]; int n = trace.read(bytes);
                            if (n > 0) out.println(new String(bytes, 0, n, StandardCharsets.UTF_8));
                        }
                    } catch (Exception error) { out.println("Trace unavailable: " + error); }
                }
            }
            for (String name : new String[] { "app.log", "runtime.log", "crash.txt", "crash.log" }) {
                File file = new File(context.getFilesDir(), name);
                out.println("\n=== " + name + " ===");
                if (!file.isFile()) { out.println("No file recorded."); continue; }
                try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
                    long total = in.length();
                    int length = (int)Math.min(total, 2L * 1024 * 1024);
                    if (total > length) out.println("Last " + length + " bytes of " + total + ":");
                    in.seek(total - length);
                    byte[] bytes = new byte[length]; in.readFully(bytes);
                    out.println(new String(bytes, StandardCharsets.UTF_8));
                }
            }
        }
        return output;
    }
}
