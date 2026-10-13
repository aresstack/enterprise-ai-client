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
 * One incremental patch archive: it turns release N into its direct successor N+1 (#105). Several of them form a
 * chain; {@link PatchChainPlanner} finds it, {@link PatchChainExecutor} applies it.
 *
 * <pre>
 * patch.zip
 * ├─ patch.properties   format=2, baseVersion, targetVersion, baseContentSha256, targetContentSha256 (required);
 * │                     baseJarSha256, targetJarSha256, targetGitCommit (informational)
 * ├─ changes.txt        release notes shown before installing (UTF-8)
 * ├─ changed.txt        new or changed jar entries, one per line
 * ├─ removed.txt        jar entries to drop, one per line
 * └─ files/&lt;entry&gt;      the bytes of every changed entry
 * </pre>
 *
 * <p>Base and target are identified by version and {@link JarDigests#contentSha256 content digest}; the file digests
 * of the released jars are only informational, because a jar the updater wrote has the released content but not
 * necessarily the released bytes. Format 1 (file digest as base check) is not read any more. Reading validates every
 * entry name: no absolute paths, no {@code ..}, no backslashes, no drive letters.
 */
public final class JarPatch {

    public static final String FORMAT = "2";
    public static final String METADATA = "patch.properties";
    public static final String CHANGES = "changes.txt";
    public static final String CHANGED = "changed.txt";
    public static final String REMOVED = "removed.txt";
    public static final String FILES = "files/";

    static final String KEY_FORMAT = "format";
    static final String KEY_BASE_VERSION = "baseVersion";
    static final String KEY_TARGET_VERSION = "targetVersion";
    static final String KEY_BASE_CONTENT_SHA = "baseContentSha256";
    static final String KEY_BASE_SHA = "baseJarSha256";
    static final String KEY_TARGET_SHA = "targetJarSha256";
    static final String KEY_TARGET_CONTENT_SHA = "targetContentSha256";
    static final String KEY_TARGET_COMMIT = "targetGitCommit";

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final File file;
    private final String baseVersion;
    private final String targetVersion;
    private final String baseContentSha256;
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
        this.baseContentSha256 = sha(metadata, KEY_BASE_CONTENT_SHA);
        this.baseJarSha256 = sha(metadata, KEY_BASE_SHA);
        this.targetJarSha256 = sha(metadata, KEY_TARGET_SHA);
        this.targetContentSha256 = sha(metadata, KEY_TARGET_CONTENT_SHA);
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
                throw new JarPatchException(file.getName() + ": Die Datei ist kein Update-Paket (" + METADATA
                        + " fehlt).");
            }
            Properties metadata = new Properties();
            try (InputStream in = zip.getInputStream(metadataEntry)) {
                metadata.load(in);
            }
            if (!FORMAT.equals(metadata.getProperty(KEY_FORMAT, "").trim())) {
                throw new JarPatchException(file.getName() + ": Format " + metadata.getProperty(KEY_FORMAT, "(keins)")
                        + " des Update-Pakets wird nicht unterstützt (erwartet " + FORMAT
                        + "); ältere Test-Pakete gelten nicht mehr, Ausgangspunkt ist die aktuelle Vollversion.");
            }
            List<String> changed = lines(zip, CHANGED);
            List<String> removed = lines(zip, REMOVED);
            String changes = text(zip, CHANGES);
            JarPatch patch = new JarPatch(file, metadata, changes, changed, removed);
            patch.validate(zip);
            return patch;
        } catch (IOException ex) {
            throw new JarPatchException(file.getName() + ": Das Update-Paket ist nicht lesbar (" + ex.getMessage()
                    + ").", ex);
        }
    }

    private void validate(ZipFile zip) throws JarPatchException {
        String prefix = file.getName() + ": ";
        if (!ReleaseVersions.isValid(baseVersion)) {
            throw new JarPatchException(prefix + "Basisversion fehlt oder ist ungültig: '" + baseVersion + "'");
        }
        if (!ReleaseVersions.isValid(targetVersion)) {
            throw new JarPatchException(prefix + "Zielversion fehlt oder ist ungültig: '" + targetVersion + "'");
        }
        if (ReleaseVersions.compare(targetVersion, baseVersion) <= 0) {
            throw new JarPatchException(prefix + "Das Update führt nicht zu einer höheren Version (" + baseVersion
                    + " → " + targetVersion + ").");
        }
        if (!SHA256.matcher(baseContentSha256).matches() || !SHA256.matcher(targetContentSha256).matches()) {
            throw new JarPatchException(prefix + "Inhaltsprüfsummen im Update-Paket fehlen oder sind ungültig.");
        }
        if (baseContentSha256.equals(targetContentSha256)) {
            throw new JarPatchException(prefix + "Basis und Ziel haben denselben Inhalt.");
        }
        if (!baseJarSha256.isEmpty() && !SHA256.matcher(baseJarSha256).matches()
                || !targetJarSha256.isEmpty() && !SHA256.matcher(targetJarSha256).matches()) {
            throw new JarPatchException(prefix + "Dateiprüfsumme im Update-Paket ist ungültig.");
        }
        Set<String> seen = new HashSet<String>();
        for (String name : changedEntries) {
            checkEntryName(name);
            if (!seen.add(name)) {
                throw new JarPatchException(prefix + "Eintrag doppelt im Update-Paket: " + name);
            }
            if (zip.getEntry(FILES + name) == null) {
                throw new JarPatchException(prefix + "Inhalt fehlt im Update-Paket: " + name);
            }
        }
        for (String name : removedEntries) {
            checkEntryName(name);
            if (seen.contains(name)) {
                throw new JarPatchException(prefix + "Eintrag zugleich geändert und entfernt: " + name);
            }
        }
        for (ZipEntry entry : Collections.list(zip.entries())) {
            String name = entry.getName();
            if (name.startsWith(FILES) && name.length() > FILES.length()
                    && !seen.contains(name.substring(FILES.length()))) {
                throw new JarPatchException(prefix + "Nicht angekündigter Inhalt im Update-Paket: " + name);
            }
        }
        if (!changedEntries.contains(java.util.jar.JarFile.MANIFEST_NAME)) {
            throw new JarPatchException(prefix + "Das Update-Paket enthält kein Manifest der Zielversion.");
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

    /** {@link JarDigests#contentSha256 Content digest} of the base release. */
    public String baseContentSha256() {
        return baseContentSha256;
    }

    /** File digest of the released base jar; informational, may be empty. */
    public String baseJarSha256() {
        return baseJarSha256;
    }

    public String targetJarSha256() {
        return targetJarSha256;
    }

    /** {@link JarDigests#contentSha256 Content digest} of the target release. */
    public String targetContentSha256() {
        return targetContentSha256;
    }

    public String targetGitCommit() {
        return targetGitCommit;
    }

    public JarIdentity base() {
        return new JarIdentity(baseVersion, baseContentSha256);
    }

    public JarIdentity target() {
        return new JarIdentity(targetVersion, targetContentSha256);
    }

    /** Same base and same target: two files that offer the same step. */
    boolean sameStep(JarPatch other) {
        return baseVersion.equals(other.baseVersion) && baseContentSha256.equals(other.baseContentSha256)
                && targetVersion.equals(other.targetVersion) && targetContentSha256.equals(other.targetContentSha256);
    }

    @Override
    public String toString() {
        return baseVersion + " → " + targetVersion;
    }

    private static String sha(Properties metadata, String key) {
        return metadata.getProperty(key, "").trim().toLowerCase(java.util.Locale.ROOT);
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
