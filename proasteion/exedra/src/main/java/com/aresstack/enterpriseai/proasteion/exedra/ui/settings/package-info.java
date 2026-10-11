/**
 * Der Einstellungen-Dialog der Desktop-Anwendung (reine Oberfläche, Comic-Stil). Bearbeitet einen
 * {@link com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm}: die Werte der Konfigurationsdatei als Text
 * und Schalter, ohne Secrets. Prüfen, Speichern und der KeePass-Test laufen über
 * {@link com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsDialogActions}, die außerhalb von {@code app.ui}
 * ({@code app.settings}, {@code app.composition}) verdrahtet werden; der Dialog kennt weder Datei noch
 * Adapter noch Ports. Dazu der kleine Dialog der Drawer-Seite „Wissensquellen“:
 * {@link com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SourceDialog} für eine Quelle, über einen eigenen
 * Vertrag ({@code SourceActions}). Das Indexverzeichnis steht im Einstellungen-Dialog (Reiter „Index“).
 */
package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;
