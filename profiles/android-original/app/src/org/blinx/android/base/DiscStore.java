// SPDX-License-Identifier: GPL-3.0-or-later
package org.blinx.android.base;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** A receipt becomes visible only with the disc it describes. The HDD is independent. */
final class DiscStore {
    interface Source { InputStream open() throws IOException; }

    static final class Identity {
        final String hash;
        final long size;

        Identity(String text) throws IOException {
            String[] fields = text.trim().split(" +");
            if (fields.length != 2 || !fields[0].matches("[a-f0-9]{64}") ||
                    !fields[1].matches("[0-9]{1,11}")) throw new IOException("Invalid disc identity");
            hash = fields[0];
            size = Long.parseLong(fields[1]);
            if (size <= 0 || size > (16L << 30)) throw new IOException("Unsupported disc size");
        }

        byte[] receipt() { return (hash + " " + size + "\n").getBytes(StandardCharsets.US_ASCII); }
    }

    static File image(File root, Identity id) { return new File(new File(root, id.hash), "disc.iso"); }

    private static void store(File source, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(source)) {
            output.write(bytes);
            output.getFD().sync();
        }
    }

    private static boolean copyVerified(InputStream source, File target, Identity id) throws IOException {
        MessageDigest sha;
        try { sha = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        try (InputStream input = source; FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[1 << 20];
            long size = 0;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                size += count;
                if (size > id.size) return false;
                sha.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
            if (size != id.size) return false;
            StringBuilder hex = new StringBuilder(64);
            for (byte b : sha.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            if (!id.hash.contentEquals(hex)) return false;
            output.getFD().sync();
            return true;
        }
    }

    static synchronized File prepare(File root, Identity id, Source packagedDisc) throws IOException {
        Files.createDirectories(root.toPath());
        try (RandomAccessFile lock = new RandomAccessFile(new File(root, "import.lock"), "rw");
             java.nio.channels.FileLock ignored = lock.getChannel().lock()) {
            File disc = image(root, id);
            File active = disc.getParentFile();
            if (active.exists()) {
                File receipt = new File(active, "disc.sha256");
                if (disc.isFile() && disc.length() == id.size && receipt.length() == id.receipt().length &&
                        Arrays.equals(Files.readAllBytes(receipt.toPath()), id.receipt())) return disc;
                throw new IOException("Incomplete installed media. Clear this app's private media directory " +
                        active.getName() + " and relaunch. Preserve the game/hdd folder containing saves.");
            }
            File staged = new File(root, "." + id.hash + ".pending");
            Files.createDirectories(staged.toPath());
            File temp = new File(staged, "disc.iso");
            File legacy = new File(root, "disc.iso");
            boolean recovered = legacy.isFile() && legacy.length() == id.size &&
                    copyVerified(new FileInputStream(legacy), temp, id);
            if (!recovered && !copyVerified(packagedDisc.open(), temp, id))
                throw new IOException("Bundled disc bytes differ from the identity recorded at build time");
            store(new File(staged, "disc.sha256"), id.receipt());
            Files.move(staged.toPath(), active.toPath(), StandardCopyOption.ATOMIC_MOVE);
            return disc;
        }
    }

    private DiscStore() { }
}
