package com.aresstack.enterpriseai.proasteion.application.update;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JarPatcherTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void patchTurnsTheRunningJarIntoANewVersionedJarAndLeavesTheRunningOneAlone() throws Exception {
        File install = folder.newFolder("install");
        File base = baseJar(new File(install, "enterprise-ai-client-0.56.0.jar"));
        File target = targetJar(folder.newFile("released-0.57.0.jar"));
        File patchFile = folder.newFile("enterprise-ai-client-patch-0.56.0-to-0.57.0.zip");
        JarPatch built = new JarPatchBuilder().build(base, target, "- Lesebereich responsiv\n- Ressourcen", patchFile);
        assertEquals(Arrays.asList("META-INF/MANIFEST.MF", "app/Changed.class", "app/New.txt"),
                built.changedEntries());
        assertEquals(Arrays.asList("app/Removed.class"), built.removedEntries());
        String baseSha = JarDigests.fileSha256(base);

        JarPatcher patcher = new JarPatcher();
        JarPatcher.Check check = patcher.check(RunningJar.at(base), patchFile);
        assertEquals("0.57.0", check.patch().targetVersion());
        assertEquals("- Lesebereich responsiv\n- Ressourcen", check.patch().changes());
        assertEquals(new File(install, "enterprise-ai-client-0.57.0.jar"), check.target());
        assertFalse(check.targetExists());

        File created = patcher.apply(check, false);
        assertEquals(check.target(), created);
        assertEquals(JarDigests.contentSha256(target), JarDigests.contentSha256(created));
        try (JarFile jar = new JarFile(created)) {
            assertEquals("0.57.0", jar.getManifest().getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION));
            assertEquals("bbbb", jar.getManifest().getMainAttributes().getValue("Git-Commit"));
            assertNull(jar.getEntry("app/Removed.class"));
            assertEquals("new", read(jar, "app/Changed.class"));
            assertEquals("same", read(jar, "app/Same.class"));
        }
        assertEquals("Die laufende Datei bleibt unverändert", baseSha, JarDigests.fileSha256(base));
        assertEquals(Arrays.asList("enterprise-ai-client-0.56.0.jar", "enterprise-ai-client-0.57.0.jar"),
                sorted(install.list()));
        assertTrue(RunningJar.at(created).isAvailable());
        assertEquals("0.57.0", RunningJar.at(created).version());
    }

    @Test
    public void aPatchForAnotherBaseOrAChangedJarIsRefused() throws Exception {
        File install = folder.newFolder("install");
        File base = baseJar(new File(install, "enterprise-ai-client-0.56.0.jar"));
        File patchFile = folder.newFile("patch.zip");
        new JarPatchBuilder().build(base, targetJar(folder.newFile("t.jar")), "", patchFile);

        File other = jar(new File(install, "enterprise-ai-client-0.55.0.jar"), "0.55.0", "cccc", entries("same"));
        try {
            new JarPatcher().check(RunningJar.at(other), patchFile);
            fail("andere Basisversion");
        } catch (JarPatchException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("0.56.0"));
        }

        Map<String, String> modified = entries("same");
        modified.put("app/Extra.class", "x");
        File tampered = jar(new File(folder.newFolder("other"), "enterprise-ai-client-0.56.0.jar"), "0.56.0", "aaaa",
                modified);
        try {
            new JarPatcher().check(RunningJar.at(tampered), patchFile);
            fail("veränderte Basis");
        } catch (JarPatchException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("SHA-256"));
        }
    }

    @Test
    public void anExistingTargetIsNotSilentlyReplaced() throws Exception {
        File install = folder.newFolder("install");
        File base = baseJar(new File(install, "enterprise-ai-client-0.56.0.jar"));
        File patchFile = folder.newFile("patch.zip");
        new JarPatchBuilder().build(base, targetJar(folder.newFile("t.jar")), "", patchFile);
        File existing = new File(install, "enterprise-ai-client-0.57.0.jar");
        java.nio.file.Files.write(existing.toPath(), "alt".getBytes(StandardCharsets.UTF_8));

        JarPatcher patcher = new JarPatcher();
        JarPatcher.Check check = patcher.check(RunningJar.at(base), patchFile);
        assertTrue(check.targetExists());
        try {
            patcher.apply(check, false);
            fail("ohne Bestätigung nicht überschreiben");
        } catch (JarPatchException expected) {
            assertEquals(3, existing.length());
        }
        patcher.apply(check, true);
        assertTrue(RunningJar.at(existing).isAvailable());
        assertEquals(2, install.list().length);
    }

    @Test
    public void aPatchThatDoesNotReproduceTheTargetLeavesNothingBehind() throws Exception {
        File install = folder.newFolder("install");
        File base = baseJar(new File(install, "enterprise-ai-client-0.56.0.jar"));
        File good = folder.newFile("good.zip");
        JarPatch patch = new JarPatchBuilder().build(base, targetJar(folder.newFile("t.jar")), "", good);
        File bad = folder.newFile("bad.zip");
        Map<String, String> files = new LinkedHashMap<String, String>();
        files.put(JarPatch.METADATA, metadata(patch, JarDigests.fileSha256(base)));
        files.put(JarPatch.CHANGED, "META-INF/MANIFEST.MF\napp/Changed.class\n");
        files.put(JarPatch.FILES + "META-INF/MANIFEST.MF", manifestText("0.57.0", "bbbb"));
        files.put(JarPatch.FILES + "app/Changed.class", "manipuliert");
        zip(bad, files);

        JarPatcher patcher = new JarPatcher();
        JarPatcher.Check check = patcher.check(RunningJar.at(base), bad);
        try {
            patcher.apply(check, false);
            fail("Prüfsumme der Zielversion");
        } catch (JarPatchException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Prüfsumme"));
        }
        assertEquals("keine Zieldatei, keine Reste", Arrays.asList("enterprise-ai-client-0.56.0.jar"),
                Arrays.asList(install.list()));
    }

    @Test
    public void entriesOutsideTheJarAreRejected() throws Exception {
        for (String evil : new String[]{"../evil.class", "/abs.class", "a/../../b", "c:\\x", "a\\b"}) {
            File bad = folder.newFile();
            Map<String, String> files = new LinkedHashMap<String, String>();
            files.put(JarPatch.METADATA, "format=1\nbaseVersion=0.56.0\ntargetVersion=0.57.0\nbaseJarSha256="
                    + repeat('a') + "\ntargetContentSha256=" + repeat('b') + "\n");
            files.put(JarPatch.CHANGED, "META-INF/MANIFEST.MF\n" + evil + "\n");
            files.put(JarPatch.FILES + "META-INF/MANIFEST.MF", manifestText("0.57.0", "bbbb"));
            files.put(JarPatch.FILES + evil, "x");
            zip(bad, files);
            try {
                JarPatch.read(bad);
                fail(evil);
            } catch (JarPatchException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("Unzulässiger Pfad"));
            }
        }
    }

    @Test
    public void startFromClassDirectoriesOrAForeignJarIsNoFatJarBase() throws Exception {
        RunningJar classes = RunningJar.at(folder.newFolder("classes"));
        assertFalse(classes.isAvailable());
        assertTrue(classes.reason(), classes.reason().contains("Fat Jar"));
        Map<String, String> plain = entries("same");
        File foreign = folder.newFile("application-0.56.0.jar");
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(foreign), new Manifest())) {
            out.putNextEntry(new ZipEntry("app/Same.class"));
            out.write(plain.get("app/Same.class").getBytes(StandardCharsets.UTF_8));
        }
        assertFalse(RunningJar.at(foreign).isAvailable());
        try {
            new JarPatcher().check(classes, folder.newFile("x.zip"));
            fail("ohne Fat Jar");
        } catch (JarPatchException expected) {
            assertEquals(classes.reason(), expected.getMessage());
        }
        assertNotNull(RunningJar.of(JarPatcherTest.class).reason());
    }

    private File baseJar(File file) throws IOException {
        Map<String, String> entries = entries("same");
        entries.put("app/Changed.class", "old");
        entries.put("app/Removed.class", "gone");
        return jar(file, "0.56.0", "aaaa", entries);
    }

    private File targetJar(File file) throws IOException {
        Map<String, String> entries = entries("same");
        entries.put("app/Changed.class", "new");
        entries.put("app/New.txt", "neu");
        return jar(file, "0.57.0", "bbbb", entries);
    }

    private static Map<String, String> entries(String same) {
        Map<String, String> entries = new LinkedHashMap<String, String>();
        entries.put("app/", null);
        entries.put("app/Same.class", same);
        return entries;
    }

    private static File jar(File file, String version, String commit, Map<String, String> entries)
            throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.IMPLEMENTATION_TITLE, RunningJar.FAT_JAR_TITLE);
        attributes.put(Attributes.Name.IMPLEMENTATION_VERSION, version);
        attributes.putValue("Git-Commit", commit);
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(file), manifest)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                if (entry.getValue() != null) {
                    out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                }
                out.closeEntry();
            }
        }
        return file;
    }

    private static String manifestText(String version, String commit) {
        return "Manifest-Version: 1.0\r\nImplementation-Title: " + RunningJar.FAT_JAR_TITLE
                + "\r\nImplementation-Version: " + version + "\r\nGit-Commit: " + commit + "\r\n\r\n";
    }

    private static String metadata(JarPatch patch, String baseSha) {
        return "format=1\nbaseVersion=" + patch.baseVersion() + "\ntargetVersion=" + patch.targetVersion()
                + "\nbaseJarSha256=" + baseSha + "\ntargetContentSha256=" + patch.targetContentSha256() + "\n";
    }

    private static void zip(File file, Map<String, String> files) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }

    private static String read(ZipFile zip, String name) throws IOException {
        java.io.InputStream in = zip.getInputStream(zip.getEntry(name));
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String repeat(char c) {
        char[] chars = new char[64];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    private static java.util.List<String> sorted(String[] names) {
        java.util.List<String> list = Arrays.asList(names);
        java.util.Collections.sort(list);
        return list;
    }
}
