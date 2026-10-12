package com.aresstack.enterpriseai.proasteion.application.settings;

import com.aresstack.enterpriseai.astu.localruntime.InstallCancellation;
import com.aresstack.enterpriseai.astu.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.proasteion.application.resources.OfflineResourceBundle;
import com.aresstack.enterpriseai.proasteion.application.resources.OfflineResourceBundleExporter;
import com.aresstack.enterpriseai.proasteion.application.speech.NlpModels;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

/**
 * „Offline-Paket erstellen …“: packt die installierten OpenNLP-Modelle und alle vollständig installierten lokalen
 * Modelle (je der ganze Ordner {@code <Modellverzeichnis>/<id>/} samt Herkunftsdatei) in ein ZIP, aus dem ein
 * anderer Rechner ohne Netz installiert, wenn es neben dem Jar liegt. Halbe Downloads ({@code .<id>.download})
 * kommen nie hinein.
 */
public final class OfflinePackageExport {

    private final NlpModels nlpModels;
    private final LocalVoiceInstaller voices;

    /** @param voices die lokalen Modelle oder {@code null} (dann nur OpenNLP) */
    public OfflinePackageExport(NlpModels nlpModels, LocalVoiceInstaller voices) {
        if (nlpModels == null) {
            throw new IllegalArgumentException("nlpModels must not be null");
        }
        this.nlpModels = nlpModels;
        this.voices = voices;
    }

    /** Der vorgeschlagene Dateiname, {@code enterprise-ai-client-resources-yyyyMMdd-HHmmss.zip}. */
    public String defaultFileName(Date now) {
        return bundle().exportFileName(now);
    }

    /** Der vorgeschlagene Ordner (der des Jars, sonst das Anwendungsverzeichnis) oder {@code null}. */
    public Path defaultDirectory() {
        return bundle().exportDirectory();
    }

    /**
     * Schreibt das Paket nach {@code target}. Blockiert.
     *
     * @throws OfflineResourceBundleExporter.Cancelled nach einem Abbruch (dann bleibt keine Datei zurück)
     * @throws IOException wenn nichts installiert ist oder das Schreiben scheitert
     */
    public OfflineResourceBundleExporter.Summary export(Path target, Path modelRoot,
                                                        OfflineResourceBundleExporter.Progress progress,
                                                        InstallCancellation cancellation) throws IOException {
        OfflineResourceBundleExporter exporter = bundle().exporter();
        nlpModels.addInstalledTo(exporter);
        if (voices != null && modelRoot != null) {
            for (LocalVoiceOffer offer : voices.offers(modelRoot)) {
                Path directory = modelRoot.resolve(offer.id());
                if (offer.isInstalled() && Files.isDirectory(directory)) {
                    exporter.addModel(offer.id(), offer.category().name(), directory);
                }
            }
        }
        if (exporter.isEmpty()) {
            throw new IOException("Es sind noch keine lokalen KI- oder Sprachmodelle installiert.");
        }
        return exporter.export(target, progress, cancellation);
    }

    private OfflineResourceBundle bundle() {
        return nlpModels.offlineBundle();
    }
}
