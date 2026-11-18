package it.blinx.demo.v1;

import android.app.Application;
import android.os.Process;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public final class DemoApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try (PrintWriter writer = new PrintWriter(new FileOutputStream(new File(getFilesDir(), "crash.txt"), true))) {
                writer.println(new Date() + " Java crash pid=" + Process.myPid() + " thread=" + thread.getName());
                error.printStackTrace(writer);
            } catch (Exception ignored) { }
            if (previous != null) previous.uncaughtException(thread, error);
            else { Process.killProcess(Process.myPid()); System.exit(10); }
        });
        appendLog("Process started: " + Application.getProcessName() + " pid=" + Process.myPid());
    }

    public synchronized void appendLog(String message) {
        try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "app.log"), true)) {
            out.write((new Date() + " " + message + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }
}
