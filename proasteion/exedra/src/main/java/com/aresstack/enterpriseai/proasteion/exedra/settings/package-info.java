/**
 * Bearbeiten der Konfigurationsdatei aus der Oberfläche heraus, ohne Swing: {@link
 * com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationFile} schreibt Schlüssel unter Erhalt der Kommentare,
 * {@link com.aresstack.enterpriseai.proasteion.exedra.settings.SettingsMapper} übersetzt zwischen Datei und
 * {@code app.ui.settings.SettingsForm}, {@link com.aresstack.enterpriseai.proasteion.exedra.settings.FileSettingsActions}
 * prüft über den {@code AppConfigLoader} und speichert, das Indexverzeichnis eingeschlossen ({@link
 * com.aresstack.enterpriseai.proasteion.application.settings.FileSourceActions} ebenso für den Quellen-Dialog der
 * Drawer-Seite „Wissensquellen“), {@link
 * com.aresstack.enterpriseai.proasteion.exedra.settings.ConfigurationStartup} entscheidet beim Start zwischen Laden, Dialog
 * und Vorlage. Adapter werden hier nicht gebaut; die KeePass-Probe kommt als
 * {@link com.aresstack.enterpriseai.proasteion.exedra.settings.SecretChecker} aus {@code app.composition}.
 */
package com.aresstack.enterpriseai.proasteion.exedra.settings;
