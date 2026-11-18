package it.blinx.demo.v1;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Private file overlays and one UI-locale data write for the exact OXM12 demo. */
public final class DemoLocale {
    static final long DISC_BYTES = 672202752L;
    static final String DISC_SHA256 = "d743eb9ecee49272f85f03be1ecd497a5c5ff4a266c30ad564b3923c5fa75a74";
    static final long XBE_BYTES = 44752896L;
    static final String XBE_SHA256 = "2a54363381adacbe8ea6362e8ae613e6049bb19928c6caa25c6b98ede521d26a";
    private static final String[] SUFFIXES = {"jp", "us", "ge", "fr", "sp", "it"};
    private static final String SCHEMA = "demo-locale-v1";
    private static final Set<String> ENGLISH_FALLBACKS = new HashSet<>(Arrays.asList(
            "h_title_logodemo", "h_menu01demo", "h_demofall2002"));
    private DemoLocale() { }

    /** Run on the launcher's existing preparation worker after DiscInstaller. */
    public static String prepare(File files, String edition, File disc, int xboxLanguage) throws IOException {
        if (!"demo".equals(edition)) return null;
        int locale = localeIndex(xboxLanguage);
        if (locale < 0) return null; // Original: no files, data writes or stale manifest.
        File root = privateRoot(files, disc);
        if (disc.length() != DISC_BYTES || !DISC_SHA256.equals(readSmall(new File(root, ".disc.sha256"), 128)))
            throw new IOException("The installed Demo does not match the verified OXM12 disc.");
        try (RandomAccessFile image = new RandomAccessFile(disc, "r")) {
            Map<String, Entry> top = rootDirectory(image);
            Entry xbe = top.get("default.xbe");
            if (xbe == null || xbe.directory || xbe.size != XBE_BYTES ||
                    !XBE_SHA256.equals(hash(image, xbe)))
                throw new IOException("The Demo executable does not match OXM12 version 2.");
            Entry media = top.get("media");
            if (media == null || !media.directory) throw new IOException("Demo media directory is missing.");
            Map<String, Entry> entries = directory(image, media);
            File destination = localeDirectory(root, locale);
            return build(image, entries, destination, locale);
        }
    }

    /** Small validation in :game, including guest relaunch, without extracting again. */
    public static String forLaunch(File files, String edition, File disc, int xboxLanguage,
            String supplied) throws IOException {
        if (!"demo".equals(edition)) {
            if (supplied != null) throw new IOException("Demo language data cannot be applied to PAL.");
            return null;
        }
        int locale = localeIndex(xboxLanguage);
        if (locale < 0) {
            if (supplied != null) throw new IOException("Original language cannot use a Demo override.");
            return null;
        }
        File root = privateRoot(files, disc);
        File directory = localeDirectory(root, locale);
        File expected = child(directory, "mods.tsv");
        if (supplied == null || !expected.equals(new File(supplied).getCanonicalFile()) ||
                disc.length() != DISC_BYTES || !DISC_SHA256.equals(readSmall(new File(root, ".disc.sha256"), 128)))
            throw new IOException("Prepare the selected Demo language before launch.");
        String receipt = readSmall(child(directory, "ready.txt"), 512);
        String wanted = SCHEMA + "\n" + XBE_SHA256 + "\n" + locale + "\n" + hash(expected);
        if (!wanted.equals(receipt)) throw new IOException("Demo language manifest failed verification.");
        return expected.getPath();
    }

    static int localeIndex(int xboxLanguage) throws IOException {
        if (xboxLanguage == 0) return -1;
        if (xboxLanguage == 1) return 1;
        if (xboxLanguage == 2) return 0;
        if (xboxLanguage >= 3 && xboxLanguage <= 6) return xboxLanguage - 1;
        throw new IOException("Demo language must be Original or one of its six languages.");
    }

    private static File privateRoot(File files, File disc) throws IOException {
        File expectedRoot = new File(files.getCanonicalFile(), "editions/demo");
        if (!expectedRoot.getCanonicalFile().equals(expectedRoot.getAbsoluteFile()))
            throw new IOException("Demo root must not be a symbolic link.");
        File expectedDisc = new File(expectedRoot, "disc.iso");
        if (!expectedDisc.equals(disc.getCanonicalFile()) || !disc.isFile())
            throw new IOException("Demo language requires its private disc.");
        return expectedRoot;
    }

    private static File localeDirectory(File root, int locale) throws IOException {
        File directory = child(root, SCHEMA + "/" + SUFFIXES[locale]);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot prepare Demo language.");
        return directory;
    }

    private static File child(File parent, String name) throws IOException {
        File plain = new File(parent.getAbsoluteFile(), name);
        File result = plain.getCanonicalFile();
        if (!plain.equals(result) || !result.getPath().startsWith(parent.getCanonicalPath() + File.separator))
            throw new IOException("Invalid Demo language path.");
        return result;
    }

    static final class Entry {
        final String name;
        final long offset, size;
        final boolean directory;
        Entry(String name, long offset, long size, boolean directory) {
            this.name = name; this.offset = offset; this.size = size; this.directory = directory;
        }
    }

    static Map<String, Entry> rootDirectory(RandomAccessFile image) throws IOException {
        byte[] header = new byte[32]; image.seek(32L * 2048); image.readFully(header);
        if (!"MICROSOFT*XBOX*MEDIA".equals(new String(header, 0, 20, StandardCharsets.US_ASCII)))
            throw new IOException("Expected a standalone Xbox XISO.");
        return directory(image, new Entry("/", u32(header, 20) * 2048, u32(header, 24), true));
    }

    static Map<String, Entry> directory(RandomAccessFile image, Entry entry) throws IOException {
        if (!entry.directory || entry.size <= 0 || entry.size > 16 * 1024 * 1024)
            throw new IOException("Invalid XISO directory size.");
        range(image, entry.offset, entry.size);
        byte[] table = new byte[(int)entry.size]; image.seek(entry.offset); image.readFully(table);
        TreeMap<String, Entry> result = new TreeMap<>();
        List<Integer> pending = new ArrayList<>(); pending.add(0);
        Set<Integer> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            int off = pending.remove(pending.size() - 1);
            if (!visited.add(off) || off < 0 || off > table.length - 14)
                throw new IOException("Invalid XISO directory tree.");
            int length = table[off + 13] & 255;
            if (length == 0 || off + 14 + length > table.length) throw new IOException("Invalid XISO filename.");
            String name = new String(table, off + 14, length, StandardCharsets.US_ASCII);
            if (!name.matches("[A-Za-z0-9_ .()!+&'-]+")) throw new IOException("Unsafe XISO filename.");
            long offset = u32(table, off + 4) * 2048, size = u32(table, off + 8);
            range(image, offset, size);
            Entry item = new Entry(name, offset, size, (table[off + 12] & 16) != 0);
            if (result.put(name.toLowerCase(Locale.ROOT), item) != null) throw new IOException("Duplicate XISO name.");
            int left = u16(table, off), right = u16(table, off + 2);
            if (left != 0) pending.add(left * 4);
            if (right != 0) pending.add(right * 4);
        }
        return result;
    }

    /** Package-private to test deterministic overlay generation against small fixtures. */
    static String build(RandomAccessFile image, Map<String, Entry> entries, File destination, int locale)
            throws IOException {
        if (locale < 0 || locale >= SUFFIXES.length) throw new IOException("Invalid internal locale.");
        String suffix = SUFFIXES[locale];
        Map<String, Entry> sources = new TreeMap<>();
        for (Map.Entry<String, Entry> item : entries.entrySet()) {
            String name = item.getKey();
            if (!item.getValue().directory && (name.endsWith("_" + suffix + ".dds") ||
                    name.endsWith("_" + suffix + ".mit"))) sources.put(name, item.getValue());
        }
        for (String base : ENGLISH_FALLBACKS) {
            String selected = base + "_" + suffix + ".dds";
            if (!sources.containsKey(selected)) {
                Entry english = entries.get(base + "_us.dds");
                if (english != null) sources.put(selected, english);
            }
        }
        if (sources.isEmpty()) throw new IOException("Demo has no assets for this language.");
        StringBuilder manifest = new StringBuilder();
        StringBuilder assets = new StringBuilder("selected\tsource\tsha256\n");
        for (Map.Entry<String, Entry> source : sources.entrySet()) {
            String selected = source.getKey(); Entry entry = source.getValue();
            File copy = child(destination, selected);
            String sha = extract(image, entry, copy);
            assets.append(selected).append('\t').append(entry.name).append('\t').append(sha).append('\n');
            int split = selected.lastIndexOf('_');
            String stem = selected.substring(0, split + 1), extension = selected.substring(selected.lastIndexOf('.'));
            // JP/US cover the original startup and its own two-language menu.
            // The selected suffix covers accesses after the data override.
            Set<String> aliases = new java.util.TreeSet<>(Arrays.asList("jp", "us", suffix));
            for (String alias : aliases) {
                String discName = stem + alias + extension;
                if (entries.containsKey(discName) || alias.equals(suffix)) {
                    manifest.append("file\tmedia\\").append(discName).append('\t').append(copy.getPath()).append('\n');
                }
            }
        }
        // Exact DATA location from the verified OXM12 XBE. Never patch instructions.
        manifest.append("cheat\t1\t0\tDemo UI locale\ncode\tw32 0x0050AB8C ").append(locale).append('\n');
        File path = child(destination, "mods.tsv");
        write(path, manifest.toString().getBytes(StandardCharsets.UTF_8));
        write(child(destination, "assets.tsv"), assets.toString().getBytes(StandardCharsets.UTF_8));
        write(child(destination, "ready.txt"), (SCHEMA + "\n" + XBE_SHA256 + "\n" + locale + "\n" + hash(path) + "\n")
                .getBytes(StandardCharsets.US_ASCII));
        return path.getPath();
    }

    private static String extract(RandomAccessFile image, Entry entry, File output) throws IOException {
        if (entry.size <= 0 || entry.size > 32L * 1024 * 1024) throw new IOException("Invalid localized asset size.");
        byte[] bytes = new byte[(int)entry.size]; image.seek(entry.offset); image.readFully(bytes);
        String digest = hex(digest().digest(bytes));
        if (!output.isFile() || output.length() != entry.size || !digest.equals(hash(output))) write(output, bytes);
        return digest;
    }

    private static void write(File output, byte[] bytes) throws IOException {
        if (output.isFile() && Arrays.equals(Files.readAllBytes(output.toPath()), bytes)) return;
        File temporary = child(output.getParentFile(), output.getName() + ".part");
        try (FileOutputStream out = new FileOutputStream(temporary)) { out.write(bytes); out.getFD().sync(); }
        try { Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException error) {
            Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String readSmall(File file, int limit) throws IOException {
        if (!file.isFile() || file.length() > limit) return "";
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.US_ASCII).trim();
    }

    private static String hash(File file) throws IOException {
        MessageDigest digest = digest();
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[256 * 1024]; int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        return hex(digest.digest());
    }

    private static String hash(RandomAccessFile image, Entry entry) throws IOException {
        MessageDigest digest = digest(); byte[] buffer = new byte[256 * 1024];
        image.seek(entry.offset); long remaining = entry.size;
        while (remaining > 0) { int n = (int)Math.min(buffer.length, remaining); image.readFully(buffer, 0, n); digest.update(buffer, 0, n); remaining -= n; }
        return hex(digest.digest());
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }
    private static String hex(byte[] data) { StringBuilder text = new StringBuilder(64); for (byte value : data) text.append(String.format(Locale.ROOT, "%02x", value & 255)); return text.toString(); }
    private static int u16(byte[] data, int offset) { return (data[offset] & 255) | (data[offset + 1] & 255) << 8; }
    private static long u32(byte[] data, int offset) { return (u16(data, offset) | (long)u16(data, offset + 2) << 16) & 0xffffffffL; }
    private static void range(RandomAccessFile image, long offset, long size) throws IOException {
        if (offset < 0 || size < 0 || offset > image.length() || size > image.length() - offset) throw new IOException("XISO entry is outside the image.");
    }
}
