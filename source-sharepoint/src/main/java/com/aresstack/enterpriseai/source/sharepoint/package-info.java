/**
 * SharePoint als Wissensquelle: eine Dokumentbibliothek wird über WebDAV gelesen, unter Windows als UNC-Pfad
 * ({@code \\host@SSL\DavWWWRoot\...}, WebClient-Dienst). Pfadabbildung und Anmeldung (Probe, SSO über
 * {@code net use}, dann optional Benutzer und Passwort aus KeePass) aus MainframeMate {@code sharepoint};
 * Dateilauf und Extraktion wie die lokale Dateiquelle über den Port {@code document-api}.
 */
package com.aresstack.enterpriseai.source.sharepoint;
