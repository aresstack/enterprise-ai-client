package com.aresstack.enterpriseai.proasteion.application.update;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A patch archive that turns one released fat jar into the next one.
 *
 * <pre>
 * patch.zip
 * ├─ patch.properties   format, baseVersion, targetVersion, baseJarSha256, targetJarSha256,
 * │                     targetContentSha256, targetGitCommit
 * ├─ changes.txt        release notes shown before installing (UTF-8)
 * ├─ changed.txt        new or changed jar entries, one per line
 * ├─ removed.txt        jar entries to drop, one per line
 * └─ files/&lt;entry&gt;      the bytes of every changed entry
 * </pre>
 *
 * <p>{@code targetJarSha256} is the released jar file; {@code targetContentSha256} is its {@link JarDigests#contentSha256
 * content digest}, which the patcher checks after writing. Reading validates every entry name: no absolute paths, no
 * {@code ..}, no backslashes, no drive letters.
 */
public final class JarPatch {

    public static final String FORMAT = "1";
    public static final String METADATA = "patch.properties";
    public static final String CHANGES = "changes.txt";
    public static final String CHANGED = "changed.txt";
    public static final String REMOVED = "removed.txt";
    public static final String FILES = "files/";

    static final String KEY_FORMAT = "format";
    static final String KEY_BASE_VERSION = "baseVersion";
    static final String KEY_TARGET_VERSION = "targetVersion";
    static final String KEY_BASE_SHA = "baseJarSha256";
    static final String KEY_TARGET_SHA = "targetJarSha256";
    static final String KEY_TARGET_CONTENT_SHA = "targetContentSha256";
    static final String KEY_TARGET_COMMIT = "targetGitCommit";

    private static final Pattern VERSION = Pattern.compile("\\d+(\\.\\d+){1,3}([-+][0-9A-Za-z.-]+)?");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final File file;
    private final String baseVersion;
    private final String targetVersion;
    private final String baseJarSha256;
    private final String targetJarSha256;
    private final String targetContentSha256;
    private final String targetGitCommit;
    private final String changes;
    private final List<String> changedEntries;
    private final List<String> removedEntries;

    private JarPatch(File file, Properties metadata, String changes, List<String> changedEntries,
                     List<String> removedEntries) {
        this.file = file;
        this.baseVersion = metadata.getProperty(KEY_BASE_VERSION, "").trim();
        this.targetVersion = metadata.getProperty(KEY_TARGET_VERSION, "").trim();
        this.baseJarSha256 = metadata.getProperty(KEY_BASE_SHA, "").trim().toLowerCase(java.util.Locale.ROOT);
        this.targetJarSha256 = metadata.getProperty(KEY_TARGET_SHA, "").trim().toLowerCase(java.util.Locale.ROOT);
        this.targetContentSha256 = metadata.getProperty(KEY_TARGET_CONTENT_SHA, "").trim()
                .toLowerCase(java.util.Locale.ROOT);
        this.targetGitCommit = metadata.getProperty(KEY_TARGET_COMMIT, "").trim();
        this.changes = changes;
        this.changedEntries = Collections.unmodifiableList(changedEntries);
        this.removedEntries = Collections.unmodifiableList(removedEntries);
    }

    /** Reads and validates a patch archive; the bytes of the changed entries stay in the file. */
    public static JarPatch read(File file) throws JarPatchException {
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry metadataEntry = zip.getEntry(METADATA);
            if (metadataEntry == null) {
                throw new JarPatchException("Die Datei ist kein Update-Paket (" + METADATA + " fehlt).");
            }
            Properties metadata = new Properties();
            try (InputStream in = zip.getInputStream(metadataEntry)) {
                metadata.load(in);
            }
            if (!FORMAT.equals(metadata.getProperty(KEY_FORMAT, "").trim())) {
                throw new JarPatchException("Unbekanntes Format des Update-Pakets: "
                        + metadata.getProperty(KEY_FORMAT, "(keins)"));
            }
            List<String> changed = lines(zip, CHANGED);
            List<String> removed = lines(zip, REMOVED);
            String changes = text(zip, CHANGES);
            JarPatch patch = new JarPatch(file, metadata, changes, changed, removed);
            patch.validate(zip);
            return patch;
        } catch (IOException ex) {
            throw new JarPatchException("Das Update-Paket ist nicht lesbar: " + ex.getMessage(), ex);
        }
    }

    private void validate(ZipFile zip) throws JarPatchException {
        if (!VERSION.matcher(baseVersion).matches()) {
            throw new JarPatchException("Basisversion fehlt oder ist ungültig: '" + baseVersion + "'");
        }
        if (!VERSION.matcher(targetVersion).matches()) {
            throw new JarPatchException("Zielversion fehlt oder ist ungültig: '" + targetVersion + "'");
        }
        if (targetVersion.equals(baseVersion)) {
            throw new JarPatchException("Ziel- und Basisversion sind gleich (" + baseVersion + ").");
        }
        if (!SHA256.matcher(baseJarSha256).matches() || !SHA256.matcher(targetContentSha256).matches()) {
            throw new JarPatchException("Prüfsummen im Update-Paket fehlen oder sind ungültig.");
        }
        if (!targetJarSha256.isEmpty() && !SHA256.matcher(targetJarSha256).matches()) {
            throw new JarPatchException("Prüfsumme der Zielversion ist ungültig.");
        }
        Set<String> seen = new HashSet<String>();
        for (String name : changedEntries) {
            checkEntryName(name);
            if (!seen.add(name)) {
                throw new JarPatchException("Eintrag doppelt im Update-Paket: " + name);
            }
            if (zip.getEntry(FILES + name) == null) {
                throw new JarPatchException("Inhalt fehlt im Update-Paket: " + name);
            }
        }
        for (String name : removedEntries) {
            checkEntryName(name);
            if (seen.contains(name)) {
                throw new JarPatchException("Eintrag zugleich geändert und entfernt: " + name);
            }
        }
        for (ZipEntry entry : Collections.list(zip.entries())) {
            String name = entry.getName();
            if (name.startsWith(FILES) && name.length() > FILES.length()
                    && !seen.contains(name.substring(FILES.length()))) {
                throw new JarPatchException("Nicht angekündigter Inhalt im Update-Paket: " + name);
            }
        }
        if (!changedEntries.contains(java.util.jar.JarFile.MANIFEST_NAME)) {
            throw new JarPatchException("Das Update-Paket enthält kein Manifest der Zielversion.");
        }
    }

    /** Rejects names that could leave the jar: absolute, parent segments, backslashes, drive letters. */
    static void checkEntryName(String name) throws JarPatchException {
        boolean bad = name.isEmpty() || name.startsWith("/") || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0
                || name.indexOf('\0') >= 0;
        if (!bad) {
            for (String segment : name.split("/", -1)) {
                if (segment.equals("..") || segment.equals(".")) {
                    bad = true;
                    break;
                }
            }
        }
        if (bad) {
            throw new JarPatchException("Unzulässiger Pfad im Update-Paket: " + name);
        }
    }

    private static List<String> lines(ZipFile zip, String name) throws IOException {
        List<String> lines = new ArrayList<String>();
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            return lines;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private static String text(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
        }
        return text.toString().trim();
    }

    public File file() {
        return file;
    }

    public String baseVersion() {
        return baseVersion;
    }

    public String targetVersion() {
        return targetVersion;
    }

    public String baseJarSha256() {
        return baseJarSha256;
    }

    public String targetJarSha256() {
        return targetJarSha256;
    }

    public String targetContentSha256() {
        return targetContentSha256;
    }

    public String targetGitCommit() {
        return targetGitCommit;
    }

    /** Release notes of the target version, empty when the patch has none. */
    public String changes() {
        return changes;
    }

    public List<String> changedEntries() {
        return changedEntries;
    }

    public List<String> removedEntries() {
        return removedEntries;
    }
}
