package com.aresstack.enterpriseai.model.api;

import com.aresstack.enterpriseai.astu.localruntime.InstallCancellation;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Ein Offline-Paket mit vollständig installierten lokalen Modellen (je Modell der ganze Modellordner): welche Modelle
 * es führt und das geprüfte Entpacken eines Modellordners. Jede Datei wird gegen die Größe und den SHA-256 des
 * Paket-Manifests geprüft; wo das Paket liegt und wie es aufgebaut ist, ist Sache des Adapters.
 */
public interface OfflineModelPackage {

    /** Die Kennungen (= Ordnernamen) der Modelle im Paket, in Paketreihenfolge; leer ohne Paket. Blockiert kurz. */
    List<String> modelIds();

    /** Die Kategorie des Modells laut Paket oder {@code null}, wenn das Paket es nicht führt. */
    ModelCategory category(String id);

    /**
     * Der geprüfte Inhalt einer kleinen Datei des Modellordners (etwa der Herkunftsdatei) oder {@code null}, wenn das
     * Paket sie nicht führt oder sie nicht stimmt.
     */
    byte[] readFile(String id, String relativePath) throws IOException;

    /**
     * Entpackt den Modellordner nach {@code directory} (vorhanden und leer); jede Datei wird beim Schreiben gegen
     * Größe und SHA-256 geprüft. Blockiert.
     *
     * @throws LocalVoiceInstallCancelledException wenn {@code cancellation} den Abbruch verlangt
     * @throws IOException wenn das Modell fehlt oder eine Datei nicht stimmt; {@code directory} ist dann unvollständig
     */
    void extract(String id, Path directory, LocalVoiceInstallListener listener, InstallCancellation cancellation)
            throws IOException, LocalVoiceInstallCancelledException;

    /** Kein Paket. */
    static OfflineModelPackage none() {
        return new OfflineModelPackage() {
            @Override
            public List<String> modelIds() {
                return Collections.emptyList();
            }

            @Override
            public ModelCategory category(String id) {
                return null;
            }

            @Override
            public byte[] readFile(String id, String relativePath) {
                return null;
            }

            @Override
            public void extract(String id, Path directory, LocalVoiceInstallListener listener,
                                InstallCancellation cancellation) throws IOException {
                throw new IOException(id + ": kein Offline-Paket vorhanden");
            }
        };
    }
}
