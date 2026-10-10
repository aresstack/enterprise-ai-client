package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.application.localruntime.JavaRuntimeOverview;
import com.aresstack.enterpriseai.application.modelcatalog.CatalogStatus;
import com.aresstack.enterpriseai.application.modelcatalog.ModelCatalogSnapshot;
import com.aresstack.enterpriseai.domain.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelReference;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Was der Einstellungen-Dialog von außen braucht: Prüfen und Speichern des Formulars sowie die Probe des
 * KeePass-Eintrags und den Verbindungstest gegen den KI-Dienst. Die Oberfläche kennt weder Datei noch Adapter;
 * produktiv verdrahtet {@code app.settings.FileSettingsActions} die Konfigurationsdatei und
 * {@code app.composition} KeePass-Probe und Verbindungstest. Alle Methoden werden auf dem EDT gerufen;
 * {@link #checkSecret} und {@link #checkConnection} dürfen blockieren und liefern deshalb asynchron.
 */
public interface SettingsDialogActions {

    /** Technische Details für die gleichnamige Kategorie (Protokolldatei und ihre letzten Zeilen); leer: keine. */
    default String technicalDetails() {
        return "";
    }

    /** Probleme des Entwurfs (Schlüssel und Erwartung, nie Werte); leer, wenn er sich speichern lässt. */
    List<String> validate(SettingsForm form);

    /** Schreibt den Entwurf dauerhaft. Vorher ist {@link #validate} leer. */
    void save(SettingsForm form) throws IOException;

    /**
     * Prüft, ob der KeePass-Eintrag mit dem Titel {@code secretRef} unter den KeePass-Einstellungen des Entwurfs
     * erreichbar ist; stößt bei Bedarf das Pairing an. Das Ergebnis kommt später auf dem EDT.
     */
    void checkSecret(SettingsForm form, String secretRef, Consumer<SecretCheckResult> onResult);

    /**
     * Prüft mit dem Entwurf Schritt für Schritt den Weg zum KI-Dienst (API-Key aus KeePass, Proxy-Route,
     * HTTPS-Verbindung, {@code GET /models}, Chat- und Embedding-Modell) und meldet jeden Schritt, sobald er feststeht, danach genau einmal
     * {@link ConnectionCheckListener#onFinished}; alles auf dem EDT. Ohne Verdrahtung meldet die Vorgabe, dass der
     * Test hier nicht verfügbar ist.
     */
    default void checkConnection(SettingsForm form, ConnectionCheckListener listener) {
        listener.onStep(ConnectionCheckStep.failed("Verbindungstest",
                "In dieser Umgebung nicht verfügbar."));
        listener.onFinished(false);
    }

    /**
     * „Proxy auflösen“: nur die Proxy-Auflösung der Test-URL mit den Netzwerkeinstellungen des Entwurfs (keine
     * Namensauflösung des Ziels, kein TLS, keine Anmeldung); Zeilen und Ende auf dem EDT.
     */
    default void resolveProxy(SettingsForm form, NetworkLogListener listener) {
        listener.line("ERROR: in dieser Umgebung nicht verfügbar");
        listener.finished(false);
    }

    /** „HTTPS-Verbindung testen“: dieselbe Route plus echte HTTPS-Verbindung mit den TLS-Quellen des Entwurfs. */
    default void checkHttps(SettingsForm form, NetworkLogListener listener) {
        listener.line("ERROR: in dieser Umgebung nicht verfügbar");
        listener.finished(false);
    }

    /** Der zuletzt bekannte Modellkatalog (Zwischenspeicher der letzten Abfrage); blockiert nicht nennenswert. */
    default ModelCatalogSnapshot cachedModels() {
        return ModelCatalogSnapshot.empty();
    }

    /**
     * Fragt mit dem Entwurf alle Modellquellen ab (Enterprise-API {@code GET /models}, optional der lokale
     * Sidecar) und liefert den vereinten Katalog genau einmal auf dem EDT; Fehler stehen in seinen Quellenständen.
     */
    default void refreshModels(SettingsForm form, Consumer<ModelCatalogSnapshot> onResult) {
        onResult.accept(new ModelCatalogSnapshot(null, Collections.singletonList(
                new CatalogStatus("modelle", "Modellquellen", false, "in dieser Umgebung nicht verfügbar"))));
    }

    /** Liest eine gespeicherte Auswahl {@code [<katalog>:]<modell>}; {@code null} bei leerem Text. */
    default ModelReference parseModel(String text) {
        return ModelReference.parse(text, Collections.<String>emptyList(), "default");
    }

    /** Der Wert, unter dem ein Katalogmodell gespeichert wird. */
    default String storedModel(ModelReference reference) {
        return reference.key();
    }

    /** Ob ein Modell vom lokalen Sidecar kommt (Reiter „Lokale Modelle“) statt von der Enterprise-API. */
    default boolean isLocal(ModelReference reference) {
        return !"default".equals(reference.catalogId());
    }

    /**
     * „Neu suchen“ im Abschnitt „Lokale Modelle“: sucht installierte Java-Laufzeiten und liefert sie genau einmal
     * auf dem EDT, kompatible (Java 21+) zuerst, mit der automatischen Wahl.
     */
    default void discoverJavaRuntimes(Consumer<JavaRuntimeOverview> onResult) {
        onResult.accept(JavaRuntimeOverview.empty());
    }

    /** Das ohne Eintrag automatisch gefundene Sidecar-Jar; leer, wenn keins gefunden wurde. Blockiert kurz. */
    default String detectedSidecarJar(String modelRoot) {
        return "";
    }

    /**
     * „Lokale Stimmen“ im Reiter „Lokale Modelle“: die angebotenen Stimmen mit Installationsstand im Modellverzeichnis des
     * Entwurfs, genau einmal auf dem EDT.
     */
    default void localVoices(SettingsForm form, Consumer<List<LocalVoiceOffer>> onResult) {
        onResult.accept(Collections.<LocalVoiceOffer>emptyList());
    }

    /**
     * Installiert eine Stimme ins Modellverzeichnis des Entwurfs (Download über die Netzwerkeinstellungen des
     * Entwurfs); Fortschritt und Ende auf dem EDT.
     */
    default void installLocalVoice(SettingsForm form, String voiceId, LocalVoiceInstallProgress progress) {
        progress.finished(false, "In dieser Umgebung nicht verfügbar.");
    }

    /** Lädt eine installierte Stimme neu (wie {@link #installLocalVoice}); ändert keine Auswahl. */
    default void updateLocalVoice(SettingsForm form, String voiceId, LocalVoiceInstallProgress progress) {
        progress.finished(false, "In dieser Umgebung nicht verfügbar.");
    }

    /** Entfernt eine installierte Stimme aus dem Modellverzeichnis des Entwurfs; Ende auf dem EDT. */
    default void removeLocalVoice(SettingsForm form, String voiceId, LocalVoiceInstallProgress progress) {
        progress.finished(false, "In dieser Umgebung nicht verfügbar.");
    }

    /** Der Wert für die TTS-Auswahl, unter dem der Sidecar eine installierte Stimme meldet. */
    default String localVoiceSelection(String voiceId) {
        return voiceId;
    }

    /** Das Standard-Ermittlungsskript der Bibliothek für einen Modus (PowerShell bzw. VBScript), sonst leer. */
    default String defaultDiscoveryScript(String proxyMode) {
        return "";
    }
}
