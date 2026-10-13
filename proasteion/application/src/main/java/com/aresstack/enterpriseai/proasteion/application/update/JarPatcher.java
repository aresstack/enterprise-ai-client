package com.aresstack.enterpriseai.proasteion.application.update;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Applies one {@link JarPatch} to one jar: unchanged entries from the base, changed ones from the patch, removed
 * ones left out, manifest first. Afterwards it checks what it wrote: content digest, manifest version and that no
 * removed entry is left. It only reads the base and writes {@code out}; staging, chains and publishing belong to
 * {@link PatchChainExecutor}.
 */
public final class JarPatcher {

    /** File name prefix of the fat jar (gradle/fat-jar.gradle). */
    public static final String JAR_PREFIX = RunningJar.FAT_JAR_TITLE + "-";

    private static final int BUFFER = 64 * 1024;

    /** Writes {@code out} from {@code baseJar} and {@code patch} and verifies it; on failure {@code out} is invalid. */
    public void apply(File baseJar, JarPatch patch, File out) throws JarPatchException {
        try {
            write(baseJar, patch, out);
            verify(patch, out);
        } catch (IOException ex) {
            throw new JarPatchException("Schritt " + patch + " konnte nicht geschrieben werden: " + ex.getMessage(),
                    ex);
        }
    }

    private static void write(File baseJar, JarPatch patch, File out) throws IOException {
        Set<String> changed = new LinkedHashSet<String>(patch.changedEntries());
        Set<String> removed = new HashSet<String>(patch.removedEntries());
        Set<String> written = new HashSet<String>();
        try (ZipFile base = new ZipFile(baseJar);
             ZipFile files = new ZipFile(patch.file());
             ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            // Keep the manifest first, as JarInputStream expects.
            for (String name : new String[]{"META-INF/", JarFile.MANIFEST_NAME}) {
                if (changed.contains(name)) {
                    copy(files, files.getEntry(JarPatch.FILES + name), name, zip);
                    written.add(name);
                } else if (base.getEntry(name) != null && !removed.contains(name)) {
                    copy(base, base.getEntry(name), name, zip);
                    written.add(name);
                }
            }
            for (ZipEntry entry : Collections.list(base.entries())) {
                String name = entry.getName();
                if (removed.contains(name) || !written.add(name)) {
                    continue;
                }
                if (changed.contains(name)) {
                    copy(files, files.getEntry(JarPatch.FILES + name), name, zip);
                } else {
                    copy(base, entry, name, zip);
                }
            }
            for (String name : changed) {
                if (written.add(name)) {
                    copy(files, files.getEntry(JarPatch.FILES + name), name, zip);
                }
            }
        }
    }

    private static void copy(ZipFile from, ZipEntry source, String name, ZipOutputStream zip) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(source.getTime());
        zip.putNextEntry(entry);
        if (!source.isDirectory()) {
            try (InputStream in = from.getInputStream(source)) {
                copy(in, zip);
            }
        }
        zip.closeEntry();
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
    }

    private static void verify(JarPatch patch, File written) throws IOException, JarPatchException {
        if (!JarDigests.contentSha256(written).equals(patch.targetContentSha256())) {
            throw new JarPatchException("Die erzeugte Jar-Datei entspricht nicht Version " + patch.targetVersion()
                    + " (Inhaltsprüfsumme passt nicht).");
        }
        try (JarFile jar = new JarFile(written)) {
            Manifest manifest = jar.getManifest();
            String version = manifest == null ? null
                    : manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            if (!patch.targetVersion().equals(version == null ? null : version.trim())) {
                throw new JarPatchException("Das Manifest der erzeugten Jar-Datei nennt Version " + version
                        + " statt " + patch.targetVersion() + ".");
            }
            for (String name : patch.removedEntries()) {
                if (jar.getEntry(name) != null) {
                    throw new JarPatchException("Entfernter Eintrag ist nach " + patch + " noch vorhanden: " + name);
                }
            }
        }
    }
}
