package com.aresstack.enterpriseai.proasteion.application.speech;

import com.aresstack.enterpriseai.proasteion.application.resources.LocalResourceSource;
import com.aresstack.enterpriseai.proasteion.application.resources.ResourceInstaller;
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
                new LocalResourceSource(Collections.singletonList(jarDirectory), PREFIX, "manifest.properties"));
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
        String sha = ResourceInstaller.sha256(MODEL);
        Path jarDirectory = temp.newFolder("jar").toPath();
        archive(jarDirectory.resolve(PREFIX + "-0.53.0.zip"), manifest(sha, MODEL.length), MODEL);
        NlpModels.Entry entry = entry(sha, MODEL.length);
        NlpModels models = models(jarDirectory, entry);

        assertEquals(ResourceInstaller.Source.ARCHIVE, models.source(entry));
        assertEquals(ResourceInstaller.Source.ARCHIVE, models.install(entry, null));
        assertTrue(models.isInstalled(entry));
        assertEquals(ResourceInstaller.Source.INSTALLED, models.source(entry));
        assertEquals("bundle hash recorded", ResourceInstaller.BundleState.INSTALLED, models.bundleState());
    }

    @Test
    public void aReplacedBundleIsRecognisedByItsHash() throws Exception {
        String sha = ResourceInstaller.sha256(MODEL);
        Path jarDirectory = temp.newFolder("jar").toPath();
        Path bundle = jarDirectory.resolve(PREFIX + ".zip");
        archive(bundle, manifest(sha, MODEL.length), MODEL);
        NlpModels.Entry entry = entry(sha, MODEL.length);
        NlpModels models = models(jarDirectory, entry);
        assertEquals(ResourceInstaller.BundleState.NEW, models.bundleState());
        models.install(entry, null);
        assertEquals(ResourceInstaller.BundleState.INSTALLED, models.bundleState());
        archive(bundle, manifest(sha, MODEL.length) + "# another build\n", MODEL);
        Files.setLastModifiedTime(bundle, java.nio.file.attribute.FileTime.fromMillis(
                Files.getLastModifiedTime(bundle).toMillis() + 2000));
        assertEquals(ResourceInstaller.BundleState.CHANGED, models.bundleState());
    }

    @Test
    public void aNewerCatalogModelIsNotTakenForInstalled() throws Exception {
        String sha = ResourceInstaller.sha256(MODEL);
        Path jarDirectory = temp.newFolder("jar").toPath();
        archive(jarDirectory.resolve(PREFIX + ".zip"), manifest(sha, MODEL.length), MODEL);
        Path root = temp.newFolder("root").toPath();
        NlpModels.Entry entry = entry(sha, MODEL.length);
        new NlpModels(root, Collections.singletonList(entry),
                new LocalResourceSource(Collections.singletonList(jarDirectory), PREFIX, "manifest.properties"))
                .install(entry, null);
        NlpModels.Entry newer = entry(ResourceInstaller.sha256("newer".getBytes(StandardCharsets.UTF_8)), 5);
        assertFalse(new NlpModels(root, Collections.singletonList(newer)).isInstalled(newer));
    }

    @Test
    public void aManifestThatDisagreesWithTheCatalogMeansDownload() throws Exception {
        String sha = ResourceInstaller.sha256(MODEL);
        Path jarDirectory = temp.newFolder("jar").toPath();
        archive(jarDirectory.resolve(PREFIX + ".zip"), manifest(sha, MODEL.length + 1), MODEL);
        NlpModels.Entry entry = entry(sha, MODEL.length);
        NlpModels models = models(jarDirectory, entry);

        assertEquals(ResourceInstaller.Source.DOWNLOAD, models.source(entry));
        try {
            models.install(entry, null);
            fail("without archive copy and without network nothing is installed");
        } catch (IOException expected) {
            assertFalse(models.isInstalled(entry));
        }
    }

    @Test
    public void tamperedContentIsNotInstalled() throws Exception {
        String sha = ResourceInstaller.sha256(MODEL);
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
        NlpModels.Entry entry = entry(ResourceInstaller.sha256(MODEL), MODEL.length);
        assertEquals(ResourceInstaller.Source.DOWNLOAD, models(temp.newFolder("empty").toPath(), entry).source(entry));
    }

    @Test
    public void theCatalogListsTheGermanTokenizerAndPosModel() {
        NlpModels models = NlpModels.standard(temp.getRoot().toPath());
        NlpModels.Entry tokens = models.entry("tokenize-de");
        assertTrue("tokenize-de im Katalog", tokens != null);
        assertEquals("de-token.bin", tokens.file());
        assertEquals("tokenize", tokens.capability());
        NlpModels.Entry pos = models.entry("pos-de");
        assertTrue("pos-de im Katalog", pos != null);
        assertEquals("de-pos-maxent.bin", pos.file());
        assertEquals("pos", pos.capability());
        assertEquals("de", pos.language());
    }

    @Test
    public void installedFileHasTheArchiveContent() throws Exception {
        String sha = ResourceInstaller.sha256(MODEL);
        Path jarDirectory = temp.newFolder("jar").toPath();
        archive(jarDirectory.resolve(PREFIX + ".zip"), manifest(sha, MODEL.length), MODEL);
        NlpModels.Entry entry = entry(sha, MODEL.length);
        Path root = temp.newFolder("root").toPath();
        NlpModels models = new NlpModels(root, Collections.singletonList(entry),
                new LocalResourceSource(Collections.singletonList(jarDirectory), PREFIX, "manifest.properties"));
        models.install(entry, null);
        assertArrayEquals(MODEL, Files.readAllBytes(root.resolve("pos-de").resolve("de-pos-maxent.bin")));
    }
}
