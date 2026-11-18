package it.blinx.demo.v1;

import android.content.Context;
import android.os.StatFs;
import android.system.Os;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Properties;

/** Extracts one signed, bundled disc. It never cleans or modifies HDD/HLE data. */
public final class DiscInstaller {
    private static final long RESERVE_BYTES = 256L * 1024 * 1024;
    private DiscInstaller() { }
    public interface Progress { void update(String message, int percent); }
    public static final class Paths {
        public final String edition;
        public final String dataRoot;
        public final String discPath;
        public final String hddPath;
        public final String hlePath;
        private Paths(String edition, File root) {
            this.edition = edition;
            dataRoot = root.getAbsolutePath();
            discPath = new File(root, "disc.iso").getAbsolutePath();
            hddPath = new File(root, "hdd").getAbsolutePath();
            hlePath = new File(root, "hle").getAbsolutePath();
        }
    }

    private static void checkEdition(String edition) throws IOException {
        if (!"demo".equals(edition) && !"pal".equals(edition)) throw new IOException("Unknown game edition.");
    }
    private static Properties metadata(Context context, String edition) throws Exception {
        checkEdition(edition);
        Properties info = new Properties();
        try (InputStream in = context.getAssets().open("discs/" + edition + ".properties")) { info.load(in); }
        if (!edition.equals(info.getProperty("id"))) throw new IOException("Disc edition does not match.");
        String digest = info.getProperty("sha256", "");
        long bytes = Long.parseLong(info.getProperty("bytes", "0"));
        if (!digest.matches("[0-9a-f]{64}") || bytes < 65536 || bytes > 8L * 1024 * 1024 * 1024)
            throw new IOException("Invalid disc metadata.");
        return info;
    }
    private static File privateRoot(Context context, String edition) throws Exception {
        checkEdition(edition);
        File files = context.getFilesDir().getCanonicalFile();
        File root = new File(files, "editions/" + edition).getCanonicalFile();
        if (!root.getPath().startsWith(files.getPath() + File.separator)) throw new IOException("Invalid private disc path.");
        return root;
    }
    private static String smallText(File file) throws IOException {
        if (!file.isFile() || file.length() > 128) return "";
        byte[] bytes = new byte[(int)file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0, count;
            while (offset < bytes.length && (count = in.read(bytes, offset, bytes.length-offset)) > 0) offset += count;
            return new String(bytes, 0, offset, StandardCharsets.US_ASCII).trim();
        }
    }
    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(64);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }

    private static String fileDigest(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[256 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return hex(digest.digest());
    }

    private static void writeMarker(File pending, String expected) throws IOException {
        try (FileOutputStream out = new FileOutputStream(pending)) {
            out.write((expected + "\n").getBytes(StandardCharsets.US_ASCII));
            out.getFD().sync();
        }
    }

    public static boolean isReady(Context context, String edition) {
        try {
            Properties info = metadata(context, edition);
            File root = privateRoot(context, edition);
            File disc = new File(root, "disc.iso");
            return disc.isFile() && disc.length() == Long.parseLong(info.getProperty("bytes")) &&
                    info.getProperty("sha256").equals(smallText(new File(root, ".disc.sha256")));
        } catch (Exception error) { return false; }
    }

    public static synchronized Paths prepare(Context context, String edition, Progress progress) throws Exception {
        Properties info = metadata(context, edition);
        File root = privateRoot(context, edition);
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create private game folder.");
        File disc = new File(root, "disc.iso");
        File marker = new File(root, ".disc.sha256");
        long expectedBytes = Long.parseLong(info.getProperty("bytes"));
        String expectedHash = info.getProperty("sha256");
        if (disc.isFile() && disc.length() == expectedBytes && expectedHash.equals(smallText(marker))) {
            progress.update("Disc ready.", 100);
            return new Paths(edition, root);
        }
        File partial = new File(root, "disc.iso.part");
        File markerPart = new File(root, ".disc.sha256.part");
        if (partial.exists() && !partial.delete()) throw new IOException("Cannot remove incomplete disc copy.");
        if (markerPart.exists() && !markerPart.delete()) throw new IOException("Cannot remove incomplete disc marker.");
        // A previous run may have installed the disc and stopped before committing its marker.
        if (disc.isFile() && disc.length() == expectedBytes && expectedHash.equals(fileDigest(disc))) {
            writeMarker(markerPart, expectedHash);
            Os.rename(markerPart.getPath(), marker.getPath());
            progress.update("Disc ready.", 100);
            return new Paths(edition, root);
        }
        if (new StatFs(root.getPath()).getAvailableBytes() < expectedBytes + RESERVE_BYTES)
            throw new IOException("Not enough free space. Preparing this disc needs at least " +
                    ((expectedBytes + RESERVE_BYTES + 1048575) / 1048576) + " MiB free.");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long copied = 0;
        int previousPercent = -1;
        progress.update("Preparing " + ("pal".equals(edition) ? "PAL" : "Demo") + " disc...", 0);
        try {
            // Hash while copying exactly once. The old complete disc remains until
            // the new copy has passed both size and hash checks and is synced.
            try (InputStream in = new BufferedInputStream(context.getAssets().open("discs/" + edition + ".iso"));
                 FileOutputStream out = new FileOutputStream(partial)) {
                byte[] buffer = new byte[256 * 1024];
                int count;
                while ((count = in.read(buffer)) >= 0) {
                    if (count == 0) continue;
                    copied += count;
                    if (copied > expectedBytes) throw new IOException("Disc size exceeds metadata.");
                    digest.update(buffer, 0, count);
                    out.write(buffer, 0, count);
                    int percent = (int)Math.min(99, copied * 100 / expectedBytes);
                    if (percent != previousPercent) { progress.update("Preparing disc...", percent); previousPercent = percent; }
                }
                out.getFD().sync();
            }
            if (copied != expectedBytes || !expectedHash.equals(hex(digest.digest())))
                throw new IOException("Disc verification failed. The previous disc is preserved.");
            writeMarker(markerPart, expectedHash);
            Os.rename(partial.getPath(), disc.getPath());
            Os.rename(markerPart.getPath(), marker.getPath());
        } catch (Exception error) {
            partial.delete(); markerPart.delete();
            throw error;
        }
        progress.update("Disc ready.", 100);
        return new Paths(edition, root);
    }
}
