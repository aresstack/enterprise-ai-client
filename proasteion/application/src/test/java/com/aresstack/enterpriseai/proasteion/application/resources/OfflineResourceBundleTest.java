package com.aresstack.enterpriseai.proasteion.application.resources;

import com.aresstack.enterpriseai.astu.localruntime.InstallCancellation;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallCancelledException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Offline package: written uncompressed via {@code .part} with the manifest as the last entry, read back with size and
 * SHA-256 checks; an aborted export leaves no file that looks like a valid ZIP.
 */
public class OfflineResourceBundleTest {

    private static final String PREFIX = "enterprise-ai-client-resources";
    private static final String MANIFEST = "manifest.properties";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void exportsResourcesAndCompleteModelFoldersAndReadsThemBack() throws Exception {
        Path jarDirectory = tmp.newFolder("jar").toPath();
        byte[] sentence = "sentence-model".getBytes(StandardCharsets.UTF_8);
        Path sentenceFile = write(tmp.newFolder("nlp").toPath().resolve("de-sent.bin"), sentence);
        ResourceDescriptor descriptor = new ResourceDescriptor("sentence-de", null, "de-sent.bin",
                ResourceInstaller.sha256(sentence), sentence.length);
        Path modelRoot = tmp.newFolder("models").toPath();
        Path model = modelRoot.resolve("speech-model");
        write(model.resolve("model.onnx"), bytes(200_000));
        write(model.resolve("tokenizer").resolve("vocab.json"), "{}".getBytes(StandardCharsets.UTF_8));
        write(model.resolve("huggingface-source.json"), "{\"category\":\"STT\"}".getBytes(StandardCharsets.UTF_8));
        write(modelRoot.resolve(".speech-model.download").resolve("model.onnx.part"), bytes(10));

        OfflineResourceBundle bundle = bundle(jarDirectory);
        Path target = jarDirectory.resolve(bundle.exportFileName(new Date(0)));
        OfflineResourceBundleExporter exporter = bundle.exporter();
        exporter.addResource(descriptor, sentenceFile);
        exporter.addModel("speech-model", "STT", model);
        List<String> progress = new ArrayList<String>();
        OfflineResourceBundleExporter.Summary summary = exporter.export(target,
                (name, number, count, done, total) -> progress.add(number + "/" + count + " " + name),
                new InstallCancellation());

        assertTrue(target.getFileName().toString().matches(PREFIX + "-\\d{8}-\\d{6}\\.zip"));
        assertFalse(Files.exists(jarDirectory.resolve(target.getFileName() + ".part")));
        assertEquals(1, summary.resources());
        assertEquals(1, summary.models());
        assertTrue(progress.contains("4/4 vocab.json"));
        List<String> names = entryNames(target);
        assertEquals("manifest is the last entry", MANIFEST, names.get(names.size() - 1));
        assertTrue(names.contains("opennlp/de-sent.bin"));
        assertTrue(names.contains("local-models/speech-model/tokenizer/vocab.json"));
        for (String name : names) {
            assertFalse("no partial download in the package: " + name, name.contains(".download"));
        }
        try (ZipFile zip = new ZipFile(target.toFile())) {
            ZipEntry weights = zip.getEntry("local-models/speech-model/model.onnx");
            assertTrue("stored without compression", weights.getCompressedSize() >= weights.getSize());
            Properties manifest = new Properties();
            try (InputStream in = zip.getInputStream(zip.getEntry(MANIFEST))) {
                manifest.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            assertEquals(OfflineResourceBundle.FORMAT, manifest.getProperty("bundle.format"));
            assertEquals("1", manifest.getProperty("bundle.version"));
            assertEquals("speech-model", manifest.getProperty("local-models"));
            assertEquals("local-models/speech-model/", manifest.getProperty("local-model.speech-model.path"));
            assertEquals("STT", manifest.getProperty("local-model.speech-model.category"));
            assertEquals("opennlp/de-sent.bin", manifest.getProperty("model.sentence-de.path"));
            assertEquals(descriptor.sha256(), manifest.getProperty("model.sentence-de.sha256"));
        }

        assertTrue("OpenNLP stays readable", bundle.offers(descriptor));
        assertArrayEquals(sentence, bundle.read(descriptor));
        assertEquals(Collections.singletonList("speech-model"), bundle.modelIds());
        assertEquals(ModelCategory.STT, bundle.category("speech-model"));
        assertArrayEquals(Files.readAllBytes(model.resolve("huggingface-source.json")),
                bundle.readFile("speech-model", "huggingface-source.json"));
        Path installed = tmp.newFolder("installed").toPath();
        bundle.extract("speech-model", installed, (file, number, count, done, total) -> { }, new InstallCancellation());
        assertArrayEquals(Files.readAllBytes(model.resolve("model.onnx")),
                Files.readAllBytes(installed.resolve("model.onnx")));
        assertTrue(Files.isRegularFile(installed.resolve("tokenizer").resolve("vocab.json")));
        assertEquals("the package version is the hash of its manifest", ResourceInstaller.sha256(manifestBytes(target)),
                bundle.bundleSha256());
    }

    @Test
    public void anAbortedExportLeavesNoFile() throws Exception {
        Path jarDirectory = tmp.newFolder("jar").toPath();
        Path model = write(tmp.newFolder("models").toPath().resolve("big").resolve("model.onnx"), bytes(1_000_000))
                .getParent();
        OfflineResourceBundle bundle = bundle(jarDirectory);
        Path target = jarDirectory.resolve(bundle.exportFileName(new Date()));
        OfflineResourceBundleExporter exporter = bundle.exporter();
        exporter.addModel("big", "STT", model);
        final InstallCancellation cancellation = new InstallCancellation();
        try {
            exporter.export(target, (name, number, count, done, total) -> {
                if (done > 100_000) {
                    cancellation.cancel();
                }
            }, cancellation);
            fail("the export must stop");
        } catch (OfflineResourceBundleExporter.Cancelled expected) {
            // stopped
        }
        assertFalse(Files.exists(target));
        assertFalse(Files.exists(jarDirectory.resolve(target.getFileName() + ".part")));
        assertNull("nothing is offered", bundle.locate());
    }

    @Test
    public void aFileThatDoesNotMatchTheManifestIsRejected() throws Exception {
        Path jarDirectory = tmp.newFolder("jar").toPath();
        Path model = write(tmp.newFolder("models").toPath().resolve("m").resolve("model.onnx"), bytes(5000))
                .getParent();
        OfflineResourceBundle bundle = bundle(jarDirectory);
        Path exported = tmp.getRoot().toPath().resolve("exported.zip");
        OfflineResourceBundleExporter exporter = bundle.exporter();
        exporter.addModel("m", "STT", model);
        exporter.export(exported, null, null);
        // Same manifest, one byte of the model changed.
        Path tampered = jarDirectory.resolve(PREFIX + "-tampered.zip");
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(exported));
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tampered))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                byte[] content = readAll(in);
                if (entry.getName().endsWith("model.onnx")) {
                    content[0] ^= 1;
                }
                out.putNextEntry(new ZipEntry(entry.getName()));
                out.write(content);
                out.closeEntry();
            }
        }
        Path installed = tmp.newFolder("installed").toPath();
        try {
            bundle.extract("m", installed, null, null);
            fail("a changed file must be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Manifest"));
        }
        assertFalse(Files.exists(installed.resolve("model.onnx")));
    }

    @Test
    public void cancellingTheExtractionStops() throws Exception {
        Path jarDirectory = tmp.newFolder("jar").toPath();
        Path model = write(tmp.newFolder("models").toPath().resolve("m").resolve("model.onnx"), bytes(500_000))
                .getParent();
        OfflineResourceBundle bundle = bundle(jarDirectory);
        OfflineResourceBundleExporter exporter = bundle.exporter();
        exporter.addModel("m", "STT", model);
        exporter.export(jarDirectory.resolve(bundle.exportFileName(new Date())), null, null);
        InstallCancellation cancellation = new InstallCancellation();
        cancellation.cancel();
        try {
            bundle.extract("m", tmp.newFolder("installed").toPath(), null, cancellation);
            fail("the extraction must stop");
        } catch (LocalVoiceInstallCancelledException expected) {
            // stopped
        }
    }

    private static OfflineResourceBundle bundle(Path directory) {
        return new OfflineResourceBundle(Collections.singletonList(directory), PREFIX, MANIFEST, "opennlp",
                "local-models");
    }

    private static Path write(Path file, byte[] content) throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(content);
        }
        return file;
    }

    private static byte[] bytes(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (i * 31);
        }
        return bytes;
    }

    private static List<String> entryNames(Path zipFile) throws IOException {
        List<String> names = new ArrayList<String>();
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
        }
        return names;
    }

    private static byte[] manifestBytes(Path zipFile) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile.toFile()); InputStream in = zip.getInputStream(zip.getEntry(MANIFEST))) {
            return readAll(in);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
