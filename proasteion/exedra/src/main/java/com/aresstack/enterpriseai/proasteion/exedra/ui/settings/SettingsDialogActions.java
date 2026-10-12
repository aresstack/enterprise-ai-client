package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.astu.acropolis.modelcatalog.CatalogStatus;
import com.aresstack.enterpriseai.astu.acropolis.modelcatalog.ModelCatalogSnapshot;
import com.aresstack.enterpriseai.astu.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelReference;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Was der Einstellungen-Dialog von außen braucht: Prüfen und Speichern des Formulars sowie die Probe des
 * KeePass-Eintrags und den Verbindungstest gegen den KI-Dienst. Die Oberfläche kennt weder Datei noch Adapter;
 * produktiv verdrahtet {@code app.settings.FileSettingsActions} die Konfigurationsdatei und
 * {@code app.composition} KeePass-Probe und Verbindungstest. Alle Methoden werden auf dem EDT gerufen;
 * {@link #checkSecret} und {@link #checkConnection} dürfen blockieren und liefern deshalb asynchron.
 */
public interface SettingsDialogActions {

    /**
     * Der Einstellungsordner der Anwendung (Konfiguration, Protokolle, Index) für den Knopf „Einstellungsordner
     * öffnen“; {@code null}: ohne Knopf.
     */
    default java.io.File settingsFolder() {
        return null;
    }

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
     * Die Titel der KeePass-Einträge zur Auswahl (Zugang „KeePass“); stößt bei Bedarf das Pairing an. Das Ergebnis
     * kommt später auf dem EDT, leer, wenn KeePass nicht erreichbar ist.
     */
    default void keePassEntries(SettingsForm form, Consumer<List<String>> onResult) {
        onResult.accept(Collections.<String>emptyList());
    }

    /**
     * Die Passwortverwaltung (Reiter „KeePass“, „➕ Neu“ am KeePass-Eintrag) mit den KeePass-Einstellungen des
     * Entwurfs; ohne Verdrahtung scheitert jeder Zugriff mit einer Meldung.
     */
    default PasswordStore passwordStore(SettingsForm form) {
        return PasswordStore.unavailable("In dieser Umgebung nicht verfügbar.");
    }

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
     * Fragt mit dem Entwurf alle Modellquellen ab (Enterprise-API {@code GET /models}, installierte lokale
     * Stimmen) und liefert den vereinten Katalog genau einmal auf dem EDT; Fehler stehen in seinen Quellenständen.
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

    /** Ob ein Modell lokal im Client läuft (Reiter „Lokale Modelle“) statt von der Enterprise-API. */
    default boolean isLocal(ModelReference reference) {
        return !"default".equals(reference.catalogId());
    }

    /**
     * „Installiert“ und „Vorschläge“ im Reiter „Lokale Modelle“: die angebotenen Stimmen mit Installationsstand im Modellverzeichnis des
     * Entwurfs, genau einmal auf dem EDT. Ließen sich die Angebote nicht ermitteln, kommt
     * {@link LocalVoiceOffersCallback#failed} statt einer leeren Liste.
     */
    default void localVoices(SettingsForm form, LocalVoiceOffersCallback callback) {
        callback.offers(Collections.<LocalVoiceOffer>emptyList());
    }

    /**
     * Installiert eine Stimme ins Modellverzeichnis des Entwurfs (Download über die Netzwerkeinstellungen des
     * Entwurfs); Fortschritt und Ende auf dem EDT.
     */
    default void installLocalVoice(SettingsForm form, String voiceId, LocalVoiceInstallProgress progress) {
        progress.finished(false, "In dieser Umgebung nicht verfügbar.");
    }

    /**
     * Sucht auf dem Hugging Face Hub nach Modellen der Kategorie ({@code null} = alle), die der Client ohne Umwandlung
     * lädt (Netzwerkeinstellungen des Entwurfs); Ergebnis genau einmal auf dem EDT. Installiert wird ein Treffer über
     * {@link #installLocalVoice}.
     */
    default void searchLocalVoices(SettingsForm form, String query, ModelCategory category,
                                   LocalVoiceSearchCallback callback) {
        callback.failed("In dieser Umgebung nicht verfügbar.");
    }

    /** Die letzten Suchbegriffe der Hub-Suche, neuester zuerst (Dropdown am Suchfeld). */
    default List<String> hubSearchHistory() {
        return Collections.<String>emptyList();
    }

    /** Merkt sich einen Suchbegriff der Hub-Suche; gibt die neue Historie zurück (neuester zuerst). */
    default List<String> rememberHubSearch(String query) {
        return hubSearchHistory();
    }

    /** Lädt eine installierte Stimme neu (wie {@link #installLocalVoice}); ändert keine Auswahl. */
    default void updateLocalVoice(SettingsForm form, String voiceId, LocalVoiceInstallProgress progress) {
        progress.finished(false, "In dieser Umgebung nicht verfügbar.");
    }

    /** Entfernt eine installierte Stimme aus dem Modellverzeichnis des Entwurfs; Ende auf dem EDT. */
    default void removeLocalVoice(SettingsForm form, String voiceId, LocalVoiceInstallProgress progress) {
        progress.finished(false, "In dieser Umgebung nicht verfügbar.");
    }

    /** Der Wert für die TTS-Auswahl, unter dem eine installierte Stimme gewählt wird. */
    default String localVoiceSelection(String voiceId) {
        return voiceId;
    }

    /**
     * Die OpenNLP-Modelle des Vorlesens (Satz- und Spracherkennung wie askai arch) mit Installationsstand, genau
     * einmal auf dem EDT.
     */
    default void nlpModels(Consumer<List<NlpModelOffer>> onResult) {
        onResult.accept(Collections.<NlpModelOffer>emptyList());
    }

    /**
     * Installiert ein OpenNLP-Modell (Download über die Netzwerkeinstellungen des Entwurfs, Größe und SHA-256
     * geprüft); Ergebnis genau einmal auf dem EDT.
     */
    default void installNlpModel(SettingsForm form, String id, BiConsumer<Boolean, String> onFinished) {
        onFinished.accept(Boolean.FALSE, "In dieser Umgebung nicht verfügbar.");
    }

    /** Das Standard-Ermittlungsskript der Bibliothek für einen Modus (PowerShell bzw. VBScript), sonst leer. */
    default String defaultDiscoveryScript(String proxyMode) {
        return "";
    }

    /**
     * Die bekannten Stimmen (Sprecher) des TTS-Modells dieser Auswahl, Standard zuerst; leer, wenn das Modell
     * unbekannt ist oder lokal läuft (eine lokale Stimme ist genau ein Sprecher).
     */
    default List<String> ttsVoices(String modelSelection) {
        return Collections.<String>emptyList();
    }

    /**
     * Die Stimme, mit der das Modell im Entwurf spräche: die für das Modell gespeicherte, sonst die passende frühere
     * globale ({@code speech.voice}), sonst die Standardstimme; leer, wenn keine gilt.
     */
    default String ttsVoice(SettingsForm form, String modelSelection) {
        return "";
    }

    /** „männlich“, „weiblich“ oder leer für eine Stimme des Modells (Anzeige neben der Auswahl). */
    default String ttsVoiceGender(String modelSelection, String voice) {
        return "";
    }

    /**
     * „Stimme testen“: spricht einen kurzen Satz mit Modell und Stimme des Entwurfs (Netz und Token wie die
     * Modellabfrage). Ergebnis genau einmal auf dem EDT: {@code null} = gesprochen, sonst der Grund ohne Secrets.
     */
    default void testVoice(SettingsForm form, String modelSelection, String voice, Consumer<String> onResult) {
        onResult.accept("In dieser Umgebung nicht verfügbar.");
    }
}
