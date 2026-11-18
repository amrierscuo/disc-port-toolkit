// SPDX-License-Identifier: GPL-3.0-or-later
package org.blinx.android.base;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** JVM-only regression fixture. Main receives a new, empty test directory. */
public final class DiscStoreTest {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        Files.createDirectory(root.toPath());
        byte[] bytes = {97, 98, 99};
        String hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        DiscStore.Identity id = new DiscStore.Identity(hash + " 3");
        try {
            DiscStore.prepare(root, id, () -> new ByteArrayInputStream(new byte[] {0, 1, 2}));
            throw new AssertionError("Accepted bad digest");
        } catch (IOException expected) {
            check(!DiscStore.image(root, id).exists(), "Exposed invalid image");
        }
        // An interrupted legacy import can leave a good image with no receipt.
        Files.write(new File(root, "disc.iso").toPath(), bytes);
        DiscStore.Source mustNotRead = () -> { throw new IOException("Unexpected packaged-disc read"); };
        File installed = DiscStore.prepare(root, id, mustNotRead);
        check(installed.length() == 3, "Wrong size");
        check(new File(installed.getParentFile(), "disc.sha256").isFile(), "Missing receipt");
        check(DiscStore.prepare(root, id, mustNotRead).equals(installed), "Retry did not reuse completed pair");
        // A legacy image from another title must fall back to the packaged input.
        File other = new File(root, "other");
        Files.createDirectory(other.toPath());
        Files.write(new File(other, "disc.iso").toPath(), new byte[] {1, 2, 3});
        check(DiscStore.prepare(other, id, () -> new ByteArrayInputStream(bytes)).length() == 3,
                "Wrong legacy content prevented reimport");
        System.out.println("DiscStore passed: rejection, retry, legacy recovery, receipt and idempotence.");
    }
}
