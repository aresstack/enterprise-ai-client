package com.aresstack.enterpriseai.proasteion.application.update;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Creates the one {@link JarPatch} from a release to its direct successor (#105; the build passes the previous
 * release's jar, never older ones, so there are no jump patches): every entry that is new or whose bytes differ goes into
 * {@code files/}, every entry missing from the target into {@code removed.txt}. The result is deterministic for the
 * same two jars. Before returning, the builder applies its patch to the base jar and checks the content digest
 * against the target jar, so a patch that would not reproduce the target is never written.
 */
public final class JarPatchBuilder {

    private static final int BUFFER = 64 * 1024;

    /** Writes the patch from {@code baseJar} to {@code targetJar} into {@code out}. */
    public JarPatch build(File baseJar, File targetJar, String changes, File out) throws IOException,
            JarPatchException {
        String baseVersion = version(baseJar);
        String targetVersion = version(targetJar);
        List<String> changed = new ArrayList<String>();
        List<String> removed = new ArrayList<String>();
        long time;
        try (ZipFile base = new ZipFile(baseJar); ZipFile target = new ZipFile(targetJar)) {
            Map<String, byte[]> baseDigests = new HashMap<String, byte[]>();
            for (ZipEntry entry : Collections.list(base.entries())) {
                baseDigests.put(entry.getName(), JarDigests.entrySha256(base, entry));
            }
            Set<String> targetNames = new HashSet<String>();
            for (ZipEntry entry : Collections.list(target.entries())) {
                String name = entry.getName();
                JarPatch.checkEntryName(name);
                targetNames.add(name);
                byte[] before = baseDigests.get(name);
                if (before == null || !Arrays.equals(before, JarDigests.entrySha256(target, entry))) {
                    changed.add(name);
                }
            }
            for (ZipEntry entry : Collections.list(base.entries())) {
                if (!targetNames.contains(entry.getName())) {
                    removed.add(entry.getName());
                }
            }
            ZipEntry manifest = target.getEntry(JarFile.MANIFEST_NAME);
            time = manifest == null ? 0L : manifest.getTime();
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
                text(zip, JarPatch.METADATA, metadata(baseJar, targetJar, baseVersion, targetVersion), time);
                text(zip, JarPatch.CHANGES, changes == null ? "" : changes.trim() + "\n", time);
                text(zip, JarPatch.CHANGED, lines(changed), time);
                text(zip, JarPatch.REMOVED, lines(removed), time);
                for (String name : changed) {
                    ZipEntry source = target.getEntry(name);
                    ZipEntry entry = new ZipEntry(JarPatch.FILES + name);
                    entry.setTime(source.getTime());
                    zip.putNextEntry(entry);
                    if (!source.isDirectory()) {
                        try (InputStream in = target.getInputStream(source)) {
                            byte[] buffer = new byte[BUFFER];
                            int read;
                            while ((read = in.read(buffer)) > 0) {
                                zip.write(buffer, 0, read);
                            }
                        }
                    }
                    zip.closeEntry();
                }
            }
        }
        JarPatch patch = JarPatch.read(out);
        selfCheck(baseJar, patch, out);
        return patch;
    }

    private static void selfCheck(File baseJar, JarPatch patch, File out) throws IOException, JarPatchException {
        File probe = File.createTempFile("jar-patch-probe-", ".jar", out.getAbsoluteFile().getParentFile());
        try {
            new JarPatcher().apply(baseJar, patch, probe);
        } catch (JarPatchException ex) {
            throw new JarPatchException("Der erzeugte Patch reproduziert die Zielversion nicht: " + ex.getMessage(),
                    ex);
        } finally {
            if (!probe.delete()) {
                probe.deleteOnExit();
            }
        }
    }

    private static String metadata(File baseJar, File targetJar, String baseVersion, String targetVersion)
            throws IOException {
        StringBuilder text = new StringBuilder();
        property(text, JarPatch.KEY_FORMAT, JarPatch.FORMAT);
        property(text, JarPatch.KEY_BASE_VERSION, baseVersion);
        property(text, JarPatch.KEY_TARGET_VERSION, targetVersion);
        property(text, JarPatch.KEY_BASE_CONTENT_SHA, JarDigests.contentSha256(baseJar));
        property(text, JarPatch.KEY_BASE_SHA, JarDigests.fileSha256(baseJar));
        property(text, JarPatch.KEY_TARGET_SHA, JarDigests.fileSha256(targetJar));
        property(text, JarPatch.KEY_TARGET_CONTENT_SHA, JarDigests.contentSha256(targetJar));
        property(text, JarPatch.KEY_TARGET_COMMIT, attribute(targetJar, "Git-Commit"));
        return text.toString();
    }

    private static void property(StringBuilder text, String key, String value) {
        text.append(key).append('=').append(value == null ? "" : value).append('\n');
    }

    private static String version(File jar) throws IOException, JarPatchException {
        String version = JarIdentity.manifestVersion(jar);
        if (version.isEmpty()) {
            throw new JarPatchException("Implementation-Version fehlt im Manifest von " + jar.getName());
        }
        return version;
    }

    private static String attribute(File jar, String name) throws IOException {
        try (JarFile file = new JarFile(jar)) {
            Manifest manifest = file.getManifest();
            return manifest == null ? null : manifest.getMainAttributes().getValue(name);
        }
    }

    private static String lines(List<String> names) {
        StringBuilder text = new StringBuilder();
        for (String name : names) {
            text.append(name).append('\n');
        }
        return text.toString();
    }

    private static void text(ZipOutputStream zip, String name, String content, long time) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(time);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
