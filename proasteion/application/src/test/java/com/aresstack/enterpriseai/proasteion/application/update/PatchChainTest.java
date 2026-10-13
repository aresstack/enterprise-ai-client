package com.aresstack.enterpriseai.proasteion.application.update;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Issue #105: incremental patches N → N+1, chained by version and content digest. */
public class PatchChainTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final PatchChainPlanner planner = new PatchChainPlanner();
    private final PatchChainExecutor executor = new PatchChainExecutor(new JarPatcher());

    @Test
    public void fourUnsortedPatchesTurn060Into064InOneRunAndLeaveOnlyTheFinalJar() throws Exception {
        File install = folder.newFolder("install");
        File running = release(new File(install, "enterprise-ai-client-0.60.0.jar"), 0);
        String runningSha = JarDigests.fileSha256(running);
        List<File> patches = new ArrayList<File>(Arrays.asList(patch(0, 1), patch(1, 2), patch(2, 3), patch(3, 4)));
        Collections.reverse(patches);
        patches.add(1, patchBetween(release(folder.newFile("r58.jar"), "0.58.0", content(-2)),
                release(folder.newFile("r59.jar"), "0.59.0", content(-1)), "alt"));

        PatchPlan plan = planner.plan(JarIdentity.of(running), patches);
        assertEquals("0.60.0", plan.start().version());
        assertEquals("0.64.0", plan.target().version());
        assertEquals(4, plan.steps().size());
        assertEquals("0.60.0 → 0.61.0", plan.steps().get(0).toString());
        assertEquals("0.63.0 → 0.64.0", plan.steps().get(3).toString());
        assertEquals("irrelevantes älteres Paket bleibt liegen", 1, plan.unused().size());
        assertTrue(plan.notices().isEmpty());
        assertTrue(plan.releaseNotes(), plan.releaseNotes().startsWith("0.61.0:\n- Schritt 1"));
        assertTrue(plan.releaseNotes(), plan.releaseNotes().contains("0.64.0:\n- Schritt 4"));

        List<String> progress = new ArrayList<String>();
        File created = executor.execute(plan, running, install, false, progress(progress, -1));
        assertEquals(Arrays.asList("1/4 0.60.0 → 0.61.0", "2/4 0.61.0 → 0.62.0", "3/4 0.62.0 → 0.63.0",
                "4/4 0.63.0 → 0.64.0"), progress);
        assertEquals(new File(install, "enterprise-ai-client-0.64.0.jar"), created);
        assertEquals(JarDigests.contentSha256(release(folder.newFile("r64.jar"), 4)), JarDigests.contentSha256(created));
        try (JarFile jar = new JarFile(created)) {
            assertEquals("0.64.0", jar.getManifest().getMainAttributes()
                    .getValue(Attributes.Name.IMPLEMENTATION_VERSION));
            assertNull("in Schritt 1 entfernt, kommt nicht wieder", jar.getEntry("app/Old.class"));
            assertNull("in Schritt 3 entfernt", jar.getEntry("app/Temp.class"));
            assertEquals("v4", read(jar, "app/Changed.class"));
            assertEquals("same", read(jar, "app/Same.class"));
        }
        assertEquals("laufende Datei unverändert", runningSha, JarDigests.fileSha256(running));
        assertEquals("nur die finale Jar, keine Zwischenstände",
                Arrays.asList("enterprise-ai-client-0.60.0.jar", "enterprise-ai-client-0.64.0.jar"),
                sorted(install.list()));
        assertEquals("0.64.0", RunningJar.at(created).version());
    }

    @Test
    public void oneAndTwoStepsUseTheSamePlannerAndExecutor() throws Exception {
        for (int steps = 1; steps <= 2; steps++) {
            File install = folder.newFolder("install" + steps);
            File running = release(new File(install, "enterprise-ai-client-0.60.0.jar"), 0);
            List<File> patches = new ArrayList<File>();
            for (int i = 0; i < steps; i++) {
                patches.add(patch(i, i + 1));
            }
            PatchPlan plan = planner.plan(JarIdentity.of(running), patches);
            assertEquals(steps, plan.steps().size());
            File created = executor.execute(plan, running, install, false, progress(new ArrayList<String>(), -1));
            assertEquals("enterprise-ai-client-0.6" + steps + ".0.jar", created.getName());
            assertEquals(2, install.list().length);
        }
    }

    @Test
    public void aRunningJarWithOtherZipLayoutButTheReleasedContentStillChains() throws Exception {
        File install = folder.newFolder("install");
        File released = release(folder.newFile("released-0.60.0.jar"), 0);
        File running = relayout(released, new File(install, "enterprise-ai-client-0.60.0.jar"));
        assertNotEquals(JarDigests.fileSha256(released), JarDigests.fileSha256(running));
        assertEquals(JarDigests.contentSha256(released), JarDigests.contentSha256(running));

        PatchPlan plan = planner.plan(JarIdentity.of(running), Arrays.asList(patch(0, 1), patch(1, 2)));
        File created = executor.execute(plan, running, install, false, progress(new ArrayList<String>(), -1));
        assertEquals(JarDigests.contentSha256(release(folder.newFile("r62.jar"), 2)), JarDigests.contentSha256(created));
    }

    @Test
    public void aGapAboveTheChainIsNamedAndAMissingStartIsRefused() throws Exception {
        File running = release(folder.newFile("enterprise-ai-client-0.60.0.jar"), 0);
        PatchPlan plan = planner.plan(JarIdentity.of(running), Arrays.asList(patch(0, 1), patch(2, 3)));
        assertEquals("0.61.0", plan.target().version());
        assertEquals(1, plan.notices().size());
        assertTrue(plan.notices().get(0), plan.notices().get(0).contains("es fehlt das Update 0.61.0 → 0.62.0"));

        try {
            planner.plan(JarIdentity.of(running), Arrays.asList(patch(1, 2), patch(2, 3)));
            fail("Kettenbeginn fehlt");
        } catch (JarPatchException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("es fehlt das Update 0.60.0 → 0.61.0"));
        }
    }

    @Test
    public void baseAndIntermediateContentMismatchesAreRefusedBeforeWriting() throws Exception {
        Map<String, String> tampered = content(0);
        tampered.put("app/Extra.class", "x");
        File other = release(folder.newFile("enterprise-ai-client-0.60.0.jar"), "0.60.0", tampered);
        try {
            planner.plan(JarIdentity.of(other), Collections.singletonList(patch(0, 1)));
            fail("Basis-Inhalt");
        } catch (JarPatchException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("ihr Inhalt aber nicht"));
        }

        Map<String, String> foreign61 = content(1);
        foreign61.put("app/Fork.class", "fork");
        File fork = patchBetween(release(folder.newFile("f61.jar"), "0.61.0", foreign61),
                release(folder.newFile("f62.jar"), 2), "fork");
        File running = release(folder.newFile("run.jar"), 0);
        try {
            planner.plan(JarIdentity.of(running), Arrays.asList(patch(0, 1), fork));
            fail("Zwischen-Inhalt");
        } catch (JarPatchException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Die Kette bricht bei Version 0.61.0 ab"));
        }
    }

    @Test
    public void twoDifferentStepsFromOneJarAreAmbiguousWhileDuplicatesAreHarmless() throws Exception {
        File running = release(folder.newFile("run.jar"), 0);
        Map<String, String> other61 = content(1);
        other61.put("app/Other.class", "other");
        File rival = patchBetween(release(folder.newFile("b60.jar"), 0),
                release(folder.newFile("b61.jar"), "0.61.0", other61), "rival");
        try {
            planner.plan(JarIdentity.of(running), Arrays.asList(patch(0, 1), rival));
            fail("mehrdeutig");
        } catch (JarPatchException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("nicht eindeutig"));
        }

        File first = patch(0, 1);
        File copy = folder.newFile("copy.zip");
        java.nio.file.Files.copy(first.toPath(), copy.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        PatchPlan plan = planner.plan(JarIdentity.of(running), Arrays.asList(first, copy));
        assertEquals(1, plan.steps().size());
        assertEquals(Collections.singletonList(copy), plan.unused());
    }

    @Test
    public void aBrokenPayloadInStepTwoLeavesNoJarAndNoStaging() throws Exception {
        File install = folder.newFolder("install");
        File running = release(new File(install, "enterprise-ai-client-0.60.0.jar"), 0);
        File broken = rewrite(patch(1, 2), JarPatch.FILES + "app/Changed.class", "manipuliert");
        PatchPlan plan = planner.plan(JarIdentity.of(running), Arrays.asList(patch(0, 1), broken, patch(2, 3)));
        assertEquals(3, plan.steps().size());
        try {
            executor.execute(plan, running, install, false, progress(new ArrayList<String>(), -1));
            fail("Inhaltsprüfsumme in Schritt 2");
        } catch (JarPatchException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("0.62.0"));
        }
        assertEquals(Collections.singletonList("enterprise-ai-client-0.60.0.jar"), Arrays.asList(install.list()));
    }

    @Test
    public void cancellingAfterStepTwoLeavesNothingBehind() throws Exception {
        File install = folder.newFolder("install");
        File running = release(new File(install, "enterprise-ai-client-0.60.0.jar"), 0);
        PatchPlan plan = planner.plan(JarIdentity.of(running),
                Arrays.asList(patch(0, 1), patch(1, 2), patch(2, 3), patch(3, 4)));
        List<String> progress = new ArrayList<String>();
        try {
            executor.execute(plan, running, install, false, progress(progress, 2));
            fail("abgebrochen");
        } catch (JarPatchException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Abgebrochen vor Schritt 3 von 4"));
        }
        assertEquals(2, progress.size());
        assertEquals(Collections.singletonList("enterprise-ai-client-0.60.0.jar"), Arrays.asList(install.list()));
    }

    @Test
    public void anExistingTargetIsOnlyReplacedWhenAskedFor() throws Exception {
        File install = folder.newFolder("install");
        File running = release(new File(install, "enterprise-ai-client-0.60.0.jar"), 0);
        File existing = new File(install, "enterprise-ai-client-0.61.0.jar");
        java.nio.file.Files.write(existing.toPath(), "alt".getBytes(StandardCharsets.UTF_8));
        PatchPlan plan = planner.plan(JarIdentity.of(running), Collections.singletonList(patch(0, 1)));
        try {
            executor.execute(plan, running, install, false, progress(new ArrayList<String>(), -1));
            fail("nicht still überschreiben");
        } catch (JarPatchException expected) {
            assertEquals(3, existing.length());
        }
        executor.execute(plan, running, install, true, progress(new ArrayList<String>(), -1));
        assertEquals("0.61.0", RunningJar.at(existing).version());
        assertEquals(2, install.list().length);
    }

    @Test
    public void oldFormatInvalidEntriesAndBrokenZipsAreRefusedUpFront() throws Exception {
        File running = release(folder.newFile("run.jar"), 0);
        File oldFormat = rewrite(patch(0, 1), JarPatch.METADATA, "format=1\nbaseVersion=0.60.0\n");
        File broken = folder.newFile("kaputt.zip");
        java.nio.file.Files.write(broken.toPath(), "kein zip".getBytes(StandardCharsets.UTF_8));
        try {
            planner.plan(JarIdentity.of(running), Arrays.asList(oldFormat, broken));
            fail("altes Format, kaputtes ZIP");
        } catch (JarPatchException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Format 1"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("kaputt.zip"));
        }
        for (String evil : new String[]{"../evil.class", "/abs.class", "a/../../b", "c:\\x", "a\\b"}) {
            File bad = folder.newFile();
            Map<String, String> files = new LinkedHashMap<String, String>();
            files.put(JarPatch.METADATA, "format=2\nbaseVersion=0.60.0\ntargetVersion=0.61.0\nbaseContentSha256="
                    + repeat('a') + "\ntargetContentSha256=" + repeat('b') + "\n");
            files.put(JarPatch.CHANGED, "META-INF/MANIFEST.MF\n" + evil + "\n");
            files.put(JarPatch.FILES + "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n");
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
    public void backwardPatchesAreRejectedAndVersionsCompareNumerically() throws Exception {
        assertTrue(ReleaseVersions.compare("0.10.0", "0.9.0") > 0);
        assertTrue(ReleaseVersions.compare("1.0", "1.0.0") == 0);
        assertTrue(ReleaseVersions.compare("0.66.0", "0.66.1") < 0);
        File backwards = patch(0, 1);
        File rewritten = rewrite(backwards, JarPatch.METADATA, metadataOf(backwards)
                .replace("targetVersion=0.61.0", "targetVersion=0.59.0"));
        try {
            JarPatch.read(rewritten);
            fail("rückwärts");
        } catch (JarPatchException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("nicht zu einer höheren Version"));
        }
    }

    @Test
    public void startFromClassDirectoriesOrAForeignJarIsNoFatJarBase() throws Exception {
        RunningJar classes = RunningJar.at(folder.newFolder("classes"));
        assertFalse(classes.isAvailable());
        assertTrue(classes.reason(), classes.reason().contains("Fat Jar"));
        File foreign = folder.newFile("application-0.56.0.jar");
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(foreign), new Manifest())) {
            out.putNextEntry(new ZipEntry("app/Same.class"));
            out.write("same".getBytes(StandardCharsets.UTF_8));
        }
        assertFalse(RunningJar.at(foreign).isAvailable());
        assertFalse(RunningJar.of(PatchChainTest.class).isAvailable());
    }

    // ------------------------------------------------------------------ fixtures

    /** Content of release 0.6x.0: Changed evolves, Old goes in step 1, Temp lives from 1 to 2. */
    private static Map<String, String> content(int minor) {
        Map<String, String> entries = new LinkedHashMap<String, String>();
        entries.put("app/", null);
        entries.put("app/Same.class", "same");
        entries.put("app/Changed.class", "v" + minor);
        if (minor <= 0) {
            entries.put("app/Old.class", "old");
        }
        if (minor == 1 || minor == 2) {
            entries.put("app/Temp.class", "temp");
        }
        return entries;
    }

    private static String version(int minor) {
        return "0.6" + minor + ".0";
    }

    private File release(File file, int minor) throws IOException {
        return release(file, version(minor), content(minor));
    }

    private File release(File file, String version, Map<String, String> entries) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.IMPLEMENTATION_TITLE, RunningJar.FAT_JAR_TITLE);
        attributes.put(Attributes.Name.IMPLEMENTATION_VERSION, version);
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

    private File patch(int from, int to) throws Exception {
        return patchBetween(release(folder.newFile(), from), release(folder.newFile(), to), "- Schritt " + to);
    }

    private File patchBetween(File base, File target, String changes) throws Exception {
        File out = folder.newFile();
        new JarPatchBuilder().build(base, target, changes, out);
        return out;
    }

    /** The same entries, stored uncompressed, in reverse order, with other timestamps. */
    private static File relayout(File source, File out) throws IOException {
        try (ZipFile zip = new ZipFile(source);
             ZipOutputStream target = new ZipOutputStream(new FileOutputStream(out))) {
            List<ZipEntry> entries = new ArrayList<ZipEntry>(Collections.list(zip.entries()));
            List<ZipEntry> reordered = new ArrayList<ZipEntry>(entries.subList(2, entries.size()));
            Collections.reverse(reordered);
            reordered.addAll(0, entries.subList(0, 2));
            for (ZipEntry entry : reordered) {
                byte[] bytes = bytes(zip, entry);
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setMethod(ZipEntry.STORED);
                copy.setSize(bytes.length);
                CRC32 crc = new CRC32();
                crc.update(bytes);
                copy.setCrc(crc.getValue());
                copy.setTime(0L);
                target.putNextEntry(copy);
                target.write(bytes);
                target.closeEntry();
            }
        }
        return out;
    }

    /** A copy of {@code patch} with {@code name} replaced by {@code text}. */
    private File rewrite(File patch, String name, String text) throws IOException {
        File out = folder.newFile();
        Map<String, String> files = new LinkedHashMap<String, String>();
        try (ZipFile zip = new ZipFile(patch)) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                files.put(entry.getName(), new String(bytes(zip, entry), StandardCharsets.ISO_8859_1));
            }
        }
        files.put(name, text);
        try (ZipOutputStream target = new ZipOutputStream(new FileOutputStream(out))) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                target.putNextEntry(new ZipEntry(entry.getKey()));
                target.write(entry.getValue().getBytes(entry.getKey().equals(name) ? StandardCharsets.UTF_8
                        : StandardCharsets.ISO_8859_1));
                target.closeEntry();
            }
        }
        return out;
    }

    private static String metadataOf(File patch) throws IOException {
        try (ZipFile zip = new ZipFile(patch)) {
            return new String(bytes(zip, zip.getEntry(JarPatch.METADATA)), StandardCharsets.UTF_8);
        }
    }

    private static PatchChainExecutor.Progress progress(List<String> seen, int cancelAfter) {
        return new PatchChainExecutor.Progress() {
            @Override
            public void step(int number, int total, JarPatch patch) {
                seen.add(number + "/" + total + " " + patch);
            }

            @Override
            public boolean cancelRequested() {
                return cancelAfter >= 0 && seen.size() >= cancelAfter;
            }
        };
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

    private static byte[] bytes(ZipFile zip, ZipEntry entry) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!entry.isDirectory()) {
            try (InputStream in = zip.getInputStream(entry)) {
                byte[] buffer = new byte[256];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                }
            }
        }
        return out.toByteArray();
    }

    private static String read(ZipFile zip, String name) throws IOException {
        return new String(bytes(zip, zip.getEntry(name)), StandardCharsets.UTF_8);
    }

    private static String repeat(char c) {
        char[] chars = new char[64];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    private static List<String> sorted(String[] names) {
        List<String> list = new ArrayList<String>(Arrays.asList(names));
        Collections.sort(list);
        return list;
    }
}
