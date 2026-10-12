# SharePoint-Konfiguration

Der Adapter `source-sharepoint` macht die Dokumentbibliotheken einer SharePoint-Site als Wissensquelle verfügbar.
Er spricht die REST-API der Site (`_api/web`, OData-JSON) über `HttpURLConnection` an, den einen HTTP-Client der
Anwendung, und liest PDF, Office, Text und HTML über den Port `deigma` (Apache Tika). Der Kern kennt weder
SharePoint- noch HTTP-Typen. Herkunft: MainframeMate `de.bund.zrb.sharepoint` (Site, Pfadregeln); statt WebDAV über
den Windows-WebClient und PowerShell läuft alles im Prozess, ohne fremdes Programm.

## Schlüssel (`source.<n>.*` mit `type=sharepoint`)

```properties
source.8.type=sharepoint
source.8.siteUrl=https://sharepoint.intern.example/sites/team
source.8.credentialRef=keepass:SharePoint
source.8.startPoints=.
source.8.maxDepth=10
source.8.maxResources=2000
source.8.maxFileMegabytes=50
```

| Schlüssel | Bedeutung |
|---|---|
| `siteUrl` | Adresse der Site ohne Query und Fragment. Fehlt sie und ist ein KeePass-Eintrag gewählt, gilt dessen URL. Aus ihr entsteht jede Ressourcen-ID `sharepoint:<host/pfad>/<Pfad relativ zur Site>`, etwa `sharepoint:sharepoint.intern.example/sites/team/Dokumente/Handbuch.pdf`. |
| `credentialRef` | KeePass-Eintrag mit Benutzername (`DOMÄNE\name` oder `name@domäne`) und Passwort. Die Anmeldedaten holt die Brücke `SecretBackedSharePointCredentialsProvider` je Lauf über den Security-Port; der Adapter sendet sie als Basic-Anmeldung und löscht sie danach. Leer = keine eigene Anmeldung, das JDK entscheidet (unter Windows NTLM/Kerberos mit der Windows-Anmeldung). |
| `startPoints` | `.` = alle sichtbaren Dokumentbibliotheken der Site; sonst kommagetrennte Ordner relativ zur Site (`Dokumente/Handbuch`) oder serverrelativ (`/sites/team/Dokumente`). |
| `maxDepth` | Ordnerebenen unter den Startpunkten (Standard 10, `0` = nur die Dateien im Startordner). |
| `maxResources` | Obergrenze je Lauf (Standard 2000). |
| `maxFileMegabytes` | Größere Dateien werden übersprungen (Standard 50). |
| `connectTimeoutMillis`, `readTimeoutMillis`, `userAgent` | Nur in der Datei; Standard 15 s, 60 s und der User-Agent der Anwendung. |

## Verhalten

- **Discover**: Bibliotheken (`lists`, `BaseTemplate 101`, ohne versteckte), dann Breitensuche über `Folders` und
  `Files` je Ordner. Der Ordner `Forms` einer Bibliothek und Office-Sperrdateien (`~$…`) werden übersprungen, ebenso
  Dateien, die `deigma` nicht als Text, PDF, Office, HTML, Quelltext oder strukturierte Daten erkennt.
- **Load**: Dateiangaben über `GetFileByServerRelativeUrl`, Inhalt über `$value`, Text über den Extraktor.
  Revision = `TimeLastModified` plus `UIVersionLabel` (sonst Größe); unveränderte Dateien überspringt die Indexierung.
- **Alles indexieren** ist angeboten (wie Dateien, FTP, NDV); eine SharePoint-Suche nutzt der Adapter nicht.
- **Fehler**: 401/403 `ACCESS_DENIED`, 404 `NOT_FOUND`, 5xx und Netz `UNAVAILABLE`, kein OData-JSON
  `INVALID_RESPONSE`; Meldungen enthalten weder Anmeldedaten noch rohe Antworten.
- **Proxy und TLS** kommen aus der Composition Root (`network.*`), je Verbindung.

## Grenzen

- Nicht gegen ein echtes SharePoint getestet (UNVERIFIED): Anmeldung (Basic, NTLM, Kerberos, SharePoint Online mit
  moderner Anmeldung geht so nicht), Pfade mit `%` oder `#`, Listen-Drosselung bei sehr großen Bibliotheken, Windows.
  Die Tests laufen gegen einen Fake-Transport (`SharePointKnowledgeSourceTest`).
- Die Auswahl der Sites über eine Linkseite wie in MainframeMate gibt es nicht; je Site eine Quelle.
