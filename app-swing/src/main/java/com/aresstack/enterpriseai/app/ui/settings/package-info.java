/**
 * Der Einstellungen-Dialog der Desktop-Anwendung (reine Oberfläche, Comic-Stil). Bearbeitet einen
 * {@link com.aresstack.enterpriseai.app.ui.settings.SettingsForm}: die Werte der Konfigurationsdatei als Text
 * und Schalter, ohne Secrets. Prüfen, Speichern und der KeePass-Test laufen über
 * {@link com.aresstack.enterpriseai.app.ui.settings.SettingsDialogActions}, die außerhalb von {@code app.ui}
 * ({@code app.settings}, {@code app.composition}) verdrahtet werden; der Dialog kennt weder Datei noch
 * Adapter noch Ports. Dazu die kleinen Dialoge der Drawer-Seite „Wissensquellen“:
 * {@link com.aresstack.enterpriseai.app.ui.settings.SourceDialog} für eine Quelle und
 * {@link com.aresstack.enterpriseai.app.ui.settings.IndexDialog} für Indexverzeichnis und Indexierung beim
 * Start, beide über eigene Verträge ({@code SourceActions}, {@code IndexActions}).
 */
package com.aresstack.enterpriseai.app.ui.settings;
