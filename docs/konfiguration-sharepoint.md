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
source.8.startPoints=.
source.8.maxFileMegabytes=50
```

| Schlüssel | Bedeutung |
|---|---|
| `siteUrl` | Adresse der Site ohne Query und Fragment. Fehlt sie und ist ein KeePass-Eintrag gewählt, gilt dessen URL. Aus ihr entsteht jede Ressourcen-ID `sharepoint:<host/pfad>/<Pfad relativ zur Site>`, etwa `sharepoint:sharepoint.intern.example/sites/team/Dokumente/Handbuch.pdf`. |
| `credentialRef` | KeePass-Eintrag mit Benutzername (`DOMÄNE\name` oder `name@domäne`) und Passwort. Die Anmeldedaten holt die Brücke `SecretBackedSharePointCredentialsProvider` je Lauf über den Security-Port; der Adapter sendet sie als Basic-Anmeldung und löscht sie danach. Leer = keine eigene Anmeldung, das JDK entscheidet (unter Windows NTLM/Kerberos mit der Windows-Anmeldung). |
| `startPoints` | `.` = alle sichtbaren Dokumentbibliotheken der Site; sonst kommagetrennte Ordner relativ zur Site (`Dokumente/Handbuch`) oder serverrelativ (`/sites/team/Dokumente`). |
| `maxDepth` | Optionale Ordnerebenen unter den Startpunkten (leer = unbegrenzt, `0` = nur die Dateien im Startordner). |
| `maxResources` | Optionale Obergrenze je Lauf (leer = unbegrenzt). |
| `maxFileMegabytes` | Inhaltsabruf begrenzen (Standard 50 MiB); Metadaten größerer Dateien bleiben sichtbar. |
| `connectTimeoutMillis`, `readTimeoutMillis`, `userAgent` | Nur in der Datei; Standard 15 s, 60 s und der User-Agent der Anwendung. |

## Verhalten

- **Discover**: Bibliotheken (`lists`, `BaseTemplate 101`, ohne versteckte), dann Breitensuche über `Folders` und
  `Files` je Ordner. Der Ordner `Forms` einer Bibliothek und Office-Sperrdateien (`~$…`) werden übersprungen, ebenso
  Dateien, die `deigma` nicht als Text, PDF, Office, HTML, Quelltext oder strukturierte Daten erkennt.
- **Load**: Dateiangaben über `GetFileByServerRelativeUrl`, Inhalt über `$value`, Text über den Extraktor.
  Revision = `TimeLastModified` plus `UIVersionLabel` (sonst Größe); unveränderte Dateien überspringt die Indexierung.
- **Alles indexieren** ist angeboten (wie Dateien, FTP, NDV); native SharePoint-Suche und Ordnernavigation über REST sind zusätzlich verfügbar.
- **Fehler**: 401/403 `ACCESS_DENIED`, 404 `NOT_FOUND`, 5xx und Netz `UNAVAILABLE`, kein OData-JSON
  `INVALID_RESPONSE`; Meldungen enthalten weder Anmeldedaten noch rohe Antworten.
- **Proxy und TLS** kommen aus der Composition Root (`network.*`), je Verbindung.

## Grenzen

- Der Java-Praxistest für die Windows-Anmeldung und sechs REST-Endpunkte war erfolgreich. Die Adaptertests laufen gegen einen Fake-Transport. Suchtreffer, Originaldownload und Gesamtabnahme an der echten Instanz stehen aus. ChangeTokens, SharePoint Online mit moderner Anmeldung und besondere Drosselungsfälle bleiben offen.
- Die Auswahl der Sites über eine Linkseite wie in MainframeMate gibt es nicht; je Site eine Quelle.

## Integrierte Windows-Anmeldung

Den KeePass-Verweis leer lassen. Die im Praxistest verwendeten JVM-Eigenschaften müssen beim Start des Java-Prozesses gesetzt werden:

```text
-Dsun.security.jgss.native=true
-Djavax.security.auth.useSubjectCredsOnly=false
-Djdk.http.ntlm.transparentAuth=trustedHosts
-Djava.net.useSystemProxies=true
```

Die Eigenschaften gelten JVM-weit; nur in einer freigegebenen Windows-Umgebung verwenden. Bestehende explizite Proxy-Routen und TLS-Prüfungen bleiben wirksam. Die Auswahl des konkreten Authentifizierungsprotokolls bleibt beim JDK; keine zusätzlichen Auswahlfelder oder JNA. Der Adapter folgt keinen Redirects. OData-Folgelinks werden auf denselben REST-Endpunkt und Origin beschränkt. Die Inventur erfolgt ohne implizite Anzahl-/Tiefengrenze, sofern keine Grenzen konfiguriert sind. Der Originalabruf prüft Dateigröße und Revision vor/nach dem Download.
