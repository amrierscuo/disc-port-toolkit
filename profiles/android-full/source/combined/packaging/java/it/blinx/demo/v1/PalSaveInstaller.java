package it.blinx.demo.v1;

import android.content.Context;
import android.system.Os;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/** Installs the bundled private PC snapshot once, retaining the previous title directory. */
public final class PalSaveInstaller {
    private static final String ASSETS = "pal_save_seed/";
    private static final String TITLE_PATH = "hdd/Partition1/UDATA/4d530013";
    private static final String STAMP = ".blinx-pc-seed";
    private PalSaveInstaller() { }

    private static byte[] read(InputStream input, int limit) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (out.size() + count > limit) throw new IOException("Save file exceeds its limit.");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    private static String hash(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder(64);
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            result.append(Character.forDigit((value >> 4) & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }

    private static void write(File file, byte[] bytes) throws IOException {
        if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs())
            throw new IOException("Cannot create save directory.");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
            output.getFD().sync();
        }
    }

    private static void atomicText(File file, String value) throws Exception {
        File temporary = new File(file.getPath() + ".tmp");
        write(temporary, value.getBytes(StandardCharsets.US_ASCII));
        Os.rename(temporary.getPath(), file.getPath());
    }

    private static String text(File file) throws IOException {
        return file.isFile() ? new String(read(new FileInputStream(file), 4096), StandardCharsets.US_ASCII).trim() : "";
    }

    private static Properties properties(byte[] bytes) throws IOException {
        Properties result = new Properties();
        result.load(new ByteArrayInputStream(bytes));
        return result;
    }

    public static synchronized String prepare(Context context) throws Exception {
        File root = new File(context.getFilesDir(), "editions/pal").getCanonicalFile();
        byte[] metadata = read(context.getAssets().open(ASSETS + "manifest.properties"), 16384);
        String identity = hash(metadata);
        File completed = new File(root, ".pal-save-imported");
        if (identity.equals(text(completed))) return "PC saves previously imported.";
        Properties manifest = properties(metadata);
        if (!"4d530013".equals(manifest.getProperty("title_id")) ||
                !manifest.getProperty("id", "").matches("[a-z0-9-]{1,64}"))
            throw new IOException("Invalid PAL save identity.");
        int count = Integer.parseInt(manifest.getProperty("count", "0"));
        if (count != 11) throw new IOException("The three PC save boxes are incomplete.");

        File target = new File(root, TITLE_PATH);
        File stage = new File(root, ".pal-save-stage-" + identity.substring(0, 12));
        File journal = new File(root, ".pal-save-pending");
        if (!target.getCanonicalFile().equals(target.getAbsoluteFile()) ||
                !stage.getCanonicalFile().equals(stage.getAbsoluteFile()))
            throw new IOException("Unexpected save directory link.");
        Properties transaction;
        if (journal.exists()) {
            transaction = properties(read(new FileInputStream(journal), 4096));
            if (!identity.equals(transaction.getProperty("identity")))
                throw new IOException("A different save import is pending.");
        } else {
            if (stage.exists()) {
                File incomplete = new File(root, "save_backups/incomplete-" + System.currentTimeMillis());
                if (incomplete.exists() || (!incomplete.getParentFile().isDirectory() && !incomplete.getParentFile().mkdirs()))
                    throw new IOException("Cannot preserve incomplete save staging.");
                Os.rename(stage.getPath(), incomplete.getPath());
            }
            Set<String> paths = new HashSet<>();
            for (int index = 0; index < count; index++) {
                String prefix = "file." + index + ".";
                String path = manifest.getProperty(prefix + "path", "");
                if (!path.matches("(?:00780F5619D[678]/(?:gamedata.dat|SaveMeta.xbx|SaveImage.xbx)|TitleMeta.xbx|TitleImage.xbx)") ||
                        !paths.add(path)) throw new IOException("Unexpected save file path.");
                int length = Integer.parseInt(manifest.getProperty(prefix + "bytes", "0"));
                if (length < 1 || length > 16384) throw new IOException("Invalid save file length.");
                byte[] bytes = read(context.getAssets().open(ASSETS + path), 16384);
                if (bytes.length != length || !hash(bytes).equals(manifest.getProperty(prefix + "sha256")))
                    throw new IOException("PC save verification failed.");
                write(new File(stage, path), bytes);
            }
            write(new File(stage, STAMP), identity.getBytes(StandardCharsets.US_ASCII));
            String backup = Long.toString(System.currentTimeMillis());
            atomicText(journal, "identity=" + identity + "\nbackup=" + backup +
                    "\nhad_target=" + target.exists() + "\n");
            transaction = properties(read(new FileInputStream(journal), 4096));
        }

        String backupName = transaction.getProperty("backup", "");
        if (!backupName.matches("[0-9]{10,20}")) throw new IOException("Invalid save backup path.");
        File backup = new File(root, "save_backups/" + backupName + "/4d530013");
        if (!identity.equals(text(new File(target, STAMP)))) {
            if (!identity.equals(text(new File(stage, STAMP))))
                throw new IOException("Pending save payload is unavailable.");
            // Validate again after a process interruption before moving any live save data.
            for (int index = 0; index < count; index++) {
                String prefix = "file." + index + ".";
                File file = new File(stage, manifest.getProperty(prefix + "path"));
                byte[] bytes = read(new FileInputStream(file), 16384);
                if (bytes.length != Integer.parseInt(manifest.getProperty(prefix + "bytes")) ||
                        !hash(bytes).equals(manifest.getProperty(prefix + "sha256")))
                    throw new IOException("Pending save payload changed.");
            }
            if (target.exists()) {
                if (backup.exists() || !Boolean.parseBoolean(transaction.getProperty("had_target")))
                    throw new IOException("Ambiguous save backup state.");
                if (!backup.getParentFile().isDirectory() && !backup.getParentFile().mkdirs())
                    throw new IOException("Cannot preserve the previous Android saves.");
                Os.rename(target.getPath(), backup.getPath());
            } else if (Boolean.parseBoolean(transaction.getProperty("had_target")) && !backup.isDirectory()) {
                throw new IOException("Previous save backup is unavailable.");
            }
            if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs())
                throw new IOException("Cannot create the PAL title directory.");
            Os.rename(stage.getPath(), target.getPath());
        }
        atomicText(completed, identity);
        if (journal.exists() && !journal.delete()) throw new IOException("Cannot finalize save import journal.");
        return "PC saves imported: Box A, Box B, Box C. Backup retained at " + backup.getPath();
    }
}
