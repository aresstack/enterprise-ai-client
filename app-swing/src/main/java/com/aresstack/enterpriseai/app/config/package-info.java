/**
 * Konfiguration der Anwendung (AP23): unveränderliche Snapshots ohne Swing, geladen aus einer Properties-Datei
 * im Benutzerverzeichnis ({@link com.aresstack.enterpriseai.app.config.AppPaths}).
 *
 * <p>In der Datei stehen keine Secrets. API-Key, Wiki- und Confluence-Zugangsdaten sind nur als
 * {@link com.aresstack.enterpriseai.domain.security.SecretRef} (Titel eines KeePass-Eintrags) hinterlegt; aufgelöst
 * werden sie erst in der Composition Root über den Security-Port. Fehlermeldungen beim Laden nennen den Schlüssel und
 * das Problem, nie den konfigurierten Wert ({@link com.aresstack.enterpriseai.app.config.AppConfigException}).
 *
 * <p>Herkunft: askai-java8 {@code AppConfigurationRepository} (Properties-Datei mit Test-Naht für einen expliziten
 * Pfad) und {@code AskAiPaths} (Benutzerverzeichnis über {@code APPDATA} bzw. {@code user.home}); MainframeMate
 * {@code Settings} (Feldzuschnitt für KeePassRPC, Proxy und Confluence-mTLS).
 */
package com.aresstack.enterpriseai.app.config;
