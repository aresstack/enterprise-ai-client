/**
 * Bearbeiten der Konfigurationsdatei aus der Oberfläche heraus, ohne Swing: {@link
 * com.aresstack.enterpriseai.app.settings.ConfigurationFile} schreibt Schlüssel unter Erhalt der Kommentare,
 * {@link com.aresstack.enterpriseai.app.settings.SettingsMapper} übersetzt zwischen Datei und
 * {@code app.ui.settings.SettingsForm}, {@link com.aresstack.enterpriseai.app.settings.FileSettingsActions}
 * prüft über den {@code AppConfigLoader} und speichert ({@link
 * com.aresstack.enterpriseai.app.settings.FileSourceActions} und {@link
 * com.aresstack.enterpriseai.app.settings.FileIndexActions} ebenso für die Dialoge der Drawer-Seite
 * „Wissensquellen“), {@link
 * com.aresstack.enterpriseai.app.settings.ConfigurationStartup} entscheidet beim Start zwischen Laden, Dialog
 * und Vorlage. Adapter werden hier nicht gebaut; die KeePass-Probe kommt als
 * {@link com.aresstack.enterpriseai.app.settings.SecretChecker} aus {@code app.composition}.
 */
package com.aresstack.enterpriseai.app.settings;
