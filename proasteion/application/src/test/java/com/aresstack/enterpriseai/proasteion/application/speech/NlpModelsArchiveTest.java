package com.aresstack.enterpriseai.proasteion.application.speech;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Installation aus dem Ressourcen-Archiv neben dem Jar: nur was das Manifest mit genau Größe und SHA-256 des Katalogs
 * führt, wird entpackt; sonst bleibt es beim Download.
 */
public class NlpModelsArchiveTest {

    private static final String PREFIX = "enterprise-ai-client-resources";
    private static final byte[] MODEL = "pos model bytes".getBytes(StandardCharsets.UTF_8);

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private NlpModels.Entry entry(String sha256, long size) {
        return new NlpModels.Entry("pos-de", "Wortartenerkennung Deutsch", "pos", "de",
                URI.create("https://example.invalid/de-pos-maxent.bin"), "de-pos-maxent.bin", sha256, size);
    }

    private NlpModels models(Path jarDirectory, NlpModels.Entry entry) throws IOException {
        return new NlpModels(temp.newFolder("models").toPath(), Collections.singletonList(entry),
                new NlpResourceArchive(Collections.singletonList(jarDirectory), PREFIX, "manifest.properties"));
    }

    private static void archive(Path file, String manifest, byte[] content) throws IOException {
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("manifest.properties"));
            zip.write(manifest.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("opennlp/de-pos-maxent.bin"));
            zip.write(content);
            zip.closeEntry();
        }
    }

    private static String manifest(String sha256, long size) {
        return "models=pos-de\nmodel.pos-de.path=opennlp/de-pos-maxent.bin\nmodel.pos-de.sha256=" + sha256
                + "\nmodel.pos-de.size=" + size + "\n";
    }

    @Test
    public void installsFromTheArchiveWithoutNetwork() throws Exception {
        String sha = NlpModels.sha256(MODEL);
        Path jarDirectory = temp.newFolder("jar").toPath();
        archive(jarDirectory.resolve(PREFIX + "-0.53.0.zip"), manifest(sha, MODEL.length), MODEL);
        NlpModels.Entry entry = entry(sha, MODEL.length);
        NlpModels models = models(jarDirectory, entry);

        assertEquals(NlpModels.Source.ARCHIVE, models.source(entry));
        assertEquals(NlpModels.Source.ARCHIVE, models.install(entry, null));
        assertTrue(models.isInstalled(entry));
        assertEquals(NlpModels.Source.INSTALLED, models.source(entry));
    }

    @Test
    public void aManifestThatDisagreesWithTheCatalogMeansDownload() throws Exception {
        String sha = NlpModels.sha256(MODEL);
        Path jarDirectory = temp.newFolder("jar").toPath();
        archive(jarDirectory.resolve(PREFIX + ".zip"), manifest(sha, MODEL.length + 1), MODEL);
        NlpModels.Entry entry = entry(sha, MODEL.length);
        NlpModels models = models(jarDirectory, entry);

        assertEquals(NlpModels.Source.DOWNLOAD, models.source(entry));
        try {
            models.install(entry, null);
            fail("without archive copy and without network nothing is installed");
        } catch (IOException expected) {
            assertFalse(models.isInstalled(entry));
        }
    }

    @Test
    public void tamperedContentIsNotInstalled() throws Exception {
        String sha = NlpModels.sha256(MODEL);
        byte[] tampered = Arrays.copyOf(MODEL, MODEL.length);
        tampered[0] = 'X';
        Path jarDirectory = temp.newFolder("jar").toPath();
        archive(jarDirectory.resolve(PREFIX + ".zip"), manifest(sha, MODEL.length), tampered);
        NlpModels.Entry entry = entry(sha, MODEL.length);
        NlpModels models = models(jarDirectory, entry);

        try {
            models.install(entry, null);
            fail("content with a wrong SHA-256 must not be installed");
        } catch (IOException expected) {
            assertFalse(models.isInstalled(entry));
        }
    }

    @Test
    public void withoutArchiveTheSourceIsDownload() throws Exception {
        NlpModels.Entry entry = entry(NlpModels.sha256(MODEL), MODEL.length);
        assertEquals(NlpModels.Source.DOWNLOAD, models(temp.newFolder("empty").toPath(), entry).source(entry));
    }

    @Test
    public void theCatalogListsTheGermanPosModel() {
        NlpModels models = NlpModels.standard(temp.getRoot().toPath());
        NlpModels.Entry pos = models.entry("pos-de");
        assertTrue("pos-de im Katalog", pos != null);
        assertEquals("de-pos-maxent.bin", pos.file());
        assertEquals("pos", pos.capability());
        assertEquals("de", pos.language());
    }

    @Test
    public void installedFileHasTheArchiveContent() throws Exception {
        String sha = NlpModels.sha256(MODEL);
        Path jarDirectory = temp.newFolder("jar").toPath();
        archive(jarDirectory.resolve(PREFIX + ".zip"), manifest(sha, MODEL.length), MODEL);
        NlpModels.Entry entry = entry(sha, MODEL.length);
        Path root = temp.newFolder("root").toPath();
        NlpModels models = new NlpModels(root, Collections.singletonList(entry),
                new NlpResourceArchive(Collections.singletonList(jarDirectory), PREFIX, "manifest.properties"));
        models.install(entry, null);
        assertArrayEquals(MODEL, Files.readAllBytes(root.resolve("pos-de").resolve("de-pos-maxent.bin")));
    }
}
