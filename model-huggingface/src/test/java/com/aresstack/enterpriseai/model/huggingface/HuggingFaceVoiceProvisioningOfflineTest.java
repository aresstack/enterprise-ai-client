package com.aresstack.enterpriseai.model.huggingface;

import com.aresstack.enterpriseai.astu.localruntime.InstallCancellation;
import com.aresstack.enterpriseai.astu.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallException;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallListener;
import com.aresstack.enterpriseai.model.api.OfflineModelPackage;
import com.aresstack.enterpriseai.proasteion.platform.network.HttpRoutePort;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Installation from an offline package without network: the package lists its own models (also ones neither curated
 * nor searched), installs them without a hub address and leaves a folder that behaves like a downloaded one.
 */
public class HuggingFaceVoiceProvisioningOfflineTest {

    private static final String ID = "speech-model";
    private static final byte[] MODEL = "onnx-weights".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CONFIG = "{\"model_type\":\"whisper\"}".getBytes(StandardCharsets.UTF_8);
    private static final String SOURCE = "{\"name\":\"Speech Model\",\"language\":\"de\",\"repository\":\"test/speech\","
            + "\"revision\":\"main\",\"category\":\"STT\",\"files\":[\"model.onnx\",\"config.json\"]}";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void listsAPackagedModelThatWasNeverSearched() throws IOException {
        Path root = tmp.newFolder("models").toPath();
        List<LocalVoiceOffer> offers = provisioning(new FakePackage(false)).offers(root);

        LocalVoiceOffer offer = find(offers, ID);
        assertNotNull("the package lists its own model", offer);
        assertEquals(ModelCategory.STT, offer.category());
        assertEquals("Speech Model", offer.displayName());
        assertFalse(offer.isInstalled());
        assertTrue(offer.isInOfflinePackage());
    }

    @Test
    public void installsFromThePackageWithoutHubAndCanBeRemovedAgain() throws Exception {
        Path root = tmp.newFolder("models").toPath();
        HuggingFaceVoiceProvisioning provisioning = provisioning(new FakePackage(false));

        provisioning.install(ID, root, quiet(), new InstallCancellation());

        assertArrayEquals(MODEL, Files.readAllBytes(root.resolve(ID).resolve("model.onnx")));
        assertTrue(Files.isRegularFile(root.resolve(ID).resolve(VoiceSource.FILE_NAME)));
        assertFalse("no staging folder stays", Files.exists(root.resolve("." + ID + ".offline")));
        LocalVoiceOffer offer = find(provisioning.offers(root), ID);
        assertTrue(offer.isInstalled());
        assertFalse("installed offers carry no package note", offer.isInOfflinePackage());

        // Without the package the folder is still a model with its source file, so it can be removed.
        HuggingFaceVoiceProvisioning without = provisioning(OfflineModelPackage.none());
        assertTrue(find(without.offers(root), ID).isInstalled());
        without.remove(ID, root);
        assertFalse(Files.exists(root.resolve(ID)));
    }

    @Test
    public void aBrokenPackageWithoutHubInstallsNothing() throws IOException {
        Path root = tmp.newFolder("models").toPath();
        try {
            provisioning(new FakePackage(true)).install(ID, root, quiet(), new InstallCancellation());
            fail("a file that does not match must fail");
        } catch (LocalVoiceInstallException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Offline-Paket"));
        }
        assertFalse(Files.exists(root.resolve(ID)));
        assertFalse(Files.exists(root.resolve("." + ID + ".offline")));
    }

    private static HuggingFaceVoiceProvisioning provisioning(OfflineModelPackage offline) {
        return new HuggingFaceVoiceProvisioning(Collections.<HuggingFaceVoice>emptyList(),
                Collections.<HubRepositorySuggestion>emptyList(), new ConcurrentHashMap<String, HuggingFaceVoice>(),
                "", HttpRoutePort.direct(), offline);
    }

    private static LocalVoiceInstallListener quiet() {
        return (file, number, count, done, total) -> { };
    }

    private static LocalVoiceOffer find(List<LocalVoiceOffer> offers, String id) {
        for (LocalVoiceOffer offer : offers) {
            if (offer.id().equals(id)) {
                return offer;
            }
        }
        return null;
    }

    /** A package with one STT model; {@code broken} makes the extraction fail like a SHA-256 mismatch. */
    private static final class FakePackage implements OfflineModelPackage {
        private final boolean broken;
        private final Map<String, byte[]> files = new LinkedHashMap<String, byte[]>();

        FakePackage(boolean broken) {
            this.broken = broken;
            files.put("config.json", CONFIG);
            files.put("model.onnx", MODEL);
            files.put(VoiceSource.FILE_NAME, SOURCE.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public List<String> modelIds() {
            return Collections.singletonList(ID);
        }

        @Override
        public ModelCategory category(String id) {
            return ID.equals(id) ? ModelCategory.STT : null;
        }

        @Override
        public byte[] readFile(String id, String relativePath) {
            return ID.equals(id) ? files.get(relativePath) : null;
        }

        @Override
        public void extract(String id, Path directory, LocalVoiceInstallListener listener,
                            InstallCancellation cancellation) throws IOException {
            int number = 0;
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                number++;
                if (broken && "model.onnx".equals(file.getKey())) {
                    throw new IOException("model.onnx stimmt nicht mit dem Manifest überein");
                }
                Files.write(directory.resolve(file.getKey()), file.getValue());
                listener.progress(file.getKey(), number, files.size(), file.getValue().length,
                        file.getValue().length);
            }
        }
    }
}
