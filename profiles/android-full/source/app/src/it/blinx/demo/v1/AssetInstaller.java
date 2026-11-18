package it.blinx.demo.v1;

import android.content.Context;
import android.os.StatFs;
import java.io.BufferedInputStream;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class AssetInstaller {
    interface Progress { void update(String text, int percent); }
    private static final long RESERVE_BYTES = 256L * 1024 * 1024;

    static File prepare(Context context, Progress progress) throws Exception {
        Properties info = new Properties();
        try (InputStream in = context.getAssets().open("game_files.properties")) { info.load(in); }
        String expected = info.getProperty("sha256", "");
        if (!expected.matches("[0-9a-f]{64}")) throw new IOException("Hash dei dati assente o non valido.");
        long zipBytes = Long.parseLong(info.getProperty("bytes"));
        long expandedBytes = Long.parseLong(info.getProperty("uncompressedBytes"));
        int expectedFiles = Integer.parseInt(info.getProperty("fileCount"));
        if (zipBytes <= 0 || expandedBytes <= 0 || expandedBytes > 8L * 1024 * 1024 * 1024 || expectedFiles <= 0)
            throw new IOException("Dimensioni dei dati non valide.");
        File files = context.getFilesDir().getCanonicalFile();
        File destination = new File(files, "game_files");
        File marker = new File(destination, ".bundle_sha256");
        File xbe = new File(destination, "default.xbe");
        if (marker.isFile() && xbe.isFile() && expected.equals(readSmall(marker).trim())) {
            progress.update("Dati gia pronti.", 100);
            return files;
        }
        File stage = new File(files, ".game_files_unpack");
        File previous = new File(files, ".game_files_previous");
        removeGenerated(stage, files);
        // The previous directory contains only an interrupted or replaced bundled copy.
        removeGenerated(previous, files);
        if (new StatFs(files.getAbsolutePath()).getAvailableBytes() < expandedBytes + RESERVE_BYTES)
            throw new IOException("Not enough free space. At least " + ((expandedBytes + RESERVE_BYTES) / 1048576) + " MB are needed to prepare the demo.");
        progress.update("Verifica dei dati inclusi...", 0);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[256 * 1024];
        long readBytes = 0;
        int lastPercent = -1;
        try (InputStream in = new BufferedInputStream(context.getAssets().open("game_files.zip"))) {
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n == 0) continue;
                digest.update(buffer, 0, n); readBytes += n;
                int percent = (int)Math.min(30, 30L * readBytes / zipBytes);
                if (percent != lastPercent) { progress.update("Verifica dei dati inclusi...", percent); lastPercent = percent; }
            }
        }
        if (readBytes != zipBytes || !expected.equals(hex(digest.digest()))) throw new IOException("Verifica SHA-256 dei dati non riuscita.");
        if (!stage.mkdirs()) throw new IOException("Impossibile creare l'area privata dei dati.");
        Set<String> names = new HashSet<>();
        long total = 0;
        int fileCount = 0;
        try {
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(context.getAssets().open("game_files.zip")))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name.isEmpty() || name.startsWith("/") || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0 || name.indexOf('\0') >= 0 || !names.add(name))
                        throw new IOException("Percorso non sicuro o duplicato nell'archivio.");
                    for (String part : name.split("/")) if (part.equals("..") || part.equals(".")) throw new IOException("Percorso non sicuro nell'archivio.");
                    File output = new File(stage, name).getCanonicalFile();
                    if (!output.getPath().startsWith(stage.getCanonicalPath() + File.separator)) throw new IOException("Percorso esterno all'area privata.");
                    if (entry.isDirectory()) {
                        if (!output.isDirectory() && !output.mkdirs()) throw new IOException("Creazione cartella non riuscita.");
                    } else {
                        File parent = output.getParentFile();
                        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Creazione cartella non riuscita.");
                        try (FileOutputStream out = new FileOutputStream(output)) {
                            int n;
                            while ((n = zip.read(buffer)) >= 0) {
                                if (n == 0) continue;
                                total += n;
                                if (total > expandedBytes) throw new IOException("Archivio piu grande del previsto.");
                                out.write(buffer, 0, n);
                                int percent = 30 + (int)Math.min(69, 69L * total / expandedBytes);
                                if (percent != lastPercent) { progress.update("Preparing game files...", percent); lastPercent = percent; }
                            }
                        }
                        if (++fileCount > expectedFiles) throw new IOException("Troppi file nell'archivio.");
                    }
                    zip.closeEntry();
                }
            }
            if (total != expandedBytes || fileCount != expectedFiles || !new File(stage, "default.xbe").isFile())
                throw new IOException("Dati estratti incompleti.");
            try (FileOutputStream out = new FileOutputStream(new File(stage, ".bundle_sha256"))) {
                out.write((expected + "\n").getBytes(StandardCharsets.US_ASCII)); out.getFD().sync();
            }
            if (destination.exists() && !destination.renameTo(previous)) throw new IOException("Impossibile aggiornare i dati privati.");
            if (!stage.renameTo(destination)) {
                if (previous.exists()) previous.renameTo(destination);
                throw new IOException("Impossibile completare la preparazione.");
            }
            removeGenerated(previous, files);
            progress.update("Game files ready. Starting the demo...", 100);
            return files;
        } catch (Exception error) { removeGenerated(stage, files); throw error; }
    }

    private static String readSmall(File file) throws IOException {
        if (file.length() > 256) return "";
        byte[] bytes = new byte[(int)file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0, n;
            while (offset < bytes.length && (n = in.read(bytes, offset, bytes.length - offset)) > 0) offset += n;
            return new String(bytes, 0, offset, StandardCharsets.US_ASCII);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }

    private static void removeGenerated(File file, File files) throws IOException {
        if (!file.exists()) return;
        String canonical = file.getCanonicalPath();
        if (!canonical.startsWith(files.getCanonicalPath() + File.separator)) throw new IOException("Pulizia fuori dall'area privata bloccata.");
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cartella privata non leggibile.");
            for (File child : children) removeGenerated(child, files);
        }
        if (!file.delete()) throw new IOException("Could not remove a temporary demo file.");
    }
}
