package com.aresstack.enterpriseai.proasteion.application.update;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
 * Builds {@code enterprise-ai-client-<targetVersion>.jar} next to the running jar from that jar and a
 * {@link JarPatch}. The running jar is only read, never changed, moved or deleted.
 *
 * <p>{@link #check} proves the patch fits: base version and SHA-256 of the running jar. {@link #apply} writes a
 * temporary file in the target directory (unchanged entries from the base, changed ones from the patch, removed ones
 * left out, manifest first), checks its content digest and manifest version, and only then renames it to the target
 * name. On any failure the temporary file is deleted and nothing else is touched.
 */
public final class JarPatcher {

    /** File name prefix of the fat jar (gradle/fat-jar.gradle). */
    public static final String JAR_PREFIX = RunningJar.FAT_JAR_TITLE + "-";

    private static final int BUFFER = 64 * 1024;

    /** Reads the patch and checks it against the running jar. */
    public Check check(RunningJar base, File patchFile) throws JarPatchException {
        if (!base.isAvailable()) {
            throw new JarPatchException(base.reason());
        }
        JarPatch patch = JarPatch.read(patchFile);
        if (!patch.baseVersion().equals(base.version())) {
            throw new JarPatchException("Das Update gilt für Version " + patch.baseVersion() + ", gestartet ist "
                    + base.version() + ".");
        }
        verifyBase(base, patch);
        File target = new File(base.file().getParentFile(), JAR_PREFIX + patch.targetVersion() + ".jar");
        if (sameFile(target, base.file())) {
            throw new JarPatchException("Die Zieldatei wäre die laufende Jar-Datei: " + target.getName());
        }
        return new Check(base, patch, target);
    }

    /**
     * Writes the target jar. An existing target file is replaced only with {@code replaceExisting}; otherwise the
     * update is refused.
     */
    public File apply(Check check, boolean replaceExisting) throws JarPatchException {
        File target = check.target();
        if (target.exists() && !replaceExisting) {
            throw new JarPatchException("Die Datei " + target.getName() + " gibt es schon.");
        }
        verifyBase(check.base(), check.patch());
        File directory = target.getAbsoluteFile().getParentFile();
        File temporary = null;
        try {
            temporary = File.createTempFile("." + target.getName() + "-", ".part", directory);
            write(check.base().file(), check.patch(), temporary);
            verifyTarget(check.patch(), temporary);
            move(temporary, target, replaceExisting);
            return target;
        } catch (IOException ex) {
            throw new JarPatchException("Das Update konnte nicht geschrieben werden: " + ex.getMessage(), ex);
        } finally {
            if (temporary != null && temporary.exists() && !temporary.delete()) {
                temporary.deleteOnExit();
            }
        }
    }

    private static void verifyBase(RunningJar base, JarPatch patch) throws JarPatchException {
        String actual;
        try {
            actual = JarDigests.fileSha256(base.file());
        } catch (IOException ex) {
            throw new JarPatchException("Die gestartete Jar-Datei ist nicht lesbar: " + ex.getMessage(), ex);
        }
        if (!actual.equals(patch.baseJarSha256())) {
            throw new JarPatchException("Die gestartete Jar-Datei weicht von der Basis des Updates ab"
                    + " (SHA-256 passt nicht zu Version " + patch.baseVersion() + ").");
        }
    }

    static void write(File baseJar, JarPatch patch, File out) throws IOException {
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

    private static void verifyTarget(JarPatch patch, File written) throws IOException, JarPatchException {
        if (!JarDigests.contentSha256(written).equals(patch.targetContentSha256())) {
            throw new JarPatchException("Die erzeugte Jar-Datei entspricht nicht Version " + patch.targetVersion()
                    + " (Prüfsumme passt nicht); das Update wurde verworfen.");
        }
        try (JarFile jar = new JarFile(written)) {
            Manifest manifest = jar.getManifest();
            String version = manifest == null ? null
                    : manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            if (!patch.targetVersion().equals(version)) {
                throw new JarPatchException("Das Manifest der erzeugten Jar-Datei nennt Version " + version
                        + " statt " + patch.targetVersion() + ".");
            }
        }
    }

    private static void move(File temporary, File target, boolean replaceExisting) throws IOException {
        StandardCopyOption[] replace = replaceExisting
                ? new StandardCopyOption[]{StandardCopyOption.REPLACE_EXISTING} : new StandardCopyOption[0];
        try {
            StandardCopyOption[] atomic = replaceExisting
                    ? new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING}
                    : new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE};
            if (!replaceExisting && target.exists()) {
                throw new java.nio.file.FileAlreadyExistsException(target.getName());
            }
            Files.move(temporary.toPath(), target.toPath(), atomic);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary.toPath(), target.toPath(), replace);
        }
    }

    private static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException ex) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }

    /** A patch that fits the running jar, with the file it will create. */
    public static final class Check {
        private final RunningJar base;
        private final JarPatch patch;
        private final File target;

        Check(RunningJar base, JarPatch patch, File target) {
            this.base = base;
            this.patch = patch;
            this.target = target;
        }

        public RunningJar base() {
            return base;
        }

        public JarPatch patch() {
            return patch;
        }

        /** {@code enterprise-ai-client-<targetVersion>.jar} next to the running jar. */
        public File target() {
            return target;
        }

        public boolean targetExists() {
            return target.exists();
        }
    }
}
