package com.aresstack.enterpriseai.model.api;

import com.aresstack.enterpriseai.domain.localruntime.LocalVoiceOffer;

import java.nio.file.Path;
import java.util.List;

/**
 * Versorgt den lokalen Sidecar mit Stimmen für die Sprachausgabe: welche Stimmen der Client anbietet, ob sie im
 * Modellverzeichnis liegen, und das Installieren (Herunterladen, Prüfen, Ablegen genau im Format, das der Sidecar
 * liest). Der Sidecar selbst lädt nie etwas herunter. Woher die Dateien kommen, ist Sache des Adapters.
 */
public interface LocalVoiceProvisioning {

    /** Die angebotenen Stimmen mit Installationsstand unter {@code modelRoot}; blockiert nur fürs Dateisystem. */
    List<LocalVoiceOffer> offers(Path modelRoot);

    /**
     * Installiert die Stimme nach {@code modelRoot/<id>}; ist sie vollständig da, passiert nichts. Unterbrochene
     * Downloads werden fortgesetzt, eine halbe Installation ist für den Sidecar nie sichtbar. Blockiert (Netz).
     */
    void install(String voiceId, Path modelRoot, LocalVoiceInstallListener listener)
            throws LocalVoiceInstallException;

    /**
     * Lädt alle Dateien der Stimme neu und ersetzt sie unter {@code modelRoot/<id>}; die Auswahl bleibt unberührt.
     * Blockiert (Netz).
     */
    void update(String voiceId, Path modelRoot, LocalVoiceInstallListener listener)
            throws LocalVoiceInstallException;

    /** Entfernt die Stimme aus {@code modelRoot/<id>}; ist sie nicht da, passiert nichts. Blockiert. */
    void remove(String voiceId, Path modelRoot) throws LocalVoiceInstallException;
}
