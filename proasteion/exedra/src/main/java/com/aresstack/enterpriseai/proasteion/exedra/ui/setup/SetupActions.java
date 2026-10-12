package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;

import java.awt.Component;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Was der Einrichtungsassistent über die Einstellungen-Aktionen ({@code SettingsDialogActions}: KeePass,
 * Verbindungstest, Modellkatalog) hinaus braucht. Die Oberfläche kennt weder Datei noch Adapter; produktiv steht
 * {@code exedra.settings.FileSetupActions} dahinter. Aufrufe kommen vom EDT, Ergebnisse kommen auf dem EDT.
 */
public interface SetupActions {

    /**
     * Der Embedding-Test: ein echter kleiner Aufruf mit dem Embedding-Modell des Entwurfs; Ergebnis ist die Länge
     * des Vektors oder ({@code null}, Meldung), wenn sie sich nicht ermitteln ließ.
     */
    void embeddingDimension(SettingsForm form, BiConsumer<Integer, String> done);

    /**
     * Schreibt Konfiguration (ohne API-Key) und die aktivierte Confluence-Quelle und prüft danach, ob der Stand
     * betriebsbereit ist; liefert jeden Punkt der Abschlussprüfung.
     *
     * @param confluence die Confluence-Angaben oder {@code null}, wenn Confluence übersprungen wurde (optional):
     *                   dann wird weder eine Quelle angelegt noch Confluence geprüft
     */
    void finish(SettingsForm form, ConfluenceSetup confluence, Consumer<List<SetupCheck>> done);

    /**
     * Die erweiterten Netzwerkeinstellungen (Proxy, TLS) über den vorhandenen Einstellungen-Dialog; ändert nur den
     * Entwurf, schreibt nichts. Blockiert auf dem EDT.
     *
     * @return der geänderte Entwurf oder {@code null}, wenn nichts übernommen wurde
     */
    SettingsForm editNetwork(Component parent, SettingsForm form);

    /**
     * Auswahl eines Client-Zertifikats (Windows-Zertifikatspeicher wie im Quellen-Dialog); blockiert auf dem EDT.
     *
     * @return der Alias oder {@code null} bei Abbruch
     */
    String chooseCertificate(Component parent, String current);
}
