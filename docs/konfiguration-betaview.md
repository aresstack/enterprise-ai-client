# BetaView-Konfiguration

Der Adapter `source-betaview` macht das BetaView-Dokumentarchiv (Listen und Reports) als Wissensquelle verfügbar.
Den Ablauf der Weboberfläche (Anmeldung, Auswahl, Dokumentreiter, Download) übernimmt er aus MainframeMate
`betaview-integration` (`BetaViewHttpClient`, `LoadResultsHtmlUseCase`, `ResultFilter`, `DownloadDocumentUseCase`,
`DocumentTabParser`); HTTP über `HttpURLConnection`, HTML über jsoup, den Text der Dokumente über den Port `deigma`.

## Schlüssel (`source.<n>.*` mit `type=betaview`)

```properties
source.9.type=betaview
source.9.baseUrl=https://betaview.intern.example/betaview/
source.9.credentialRef=keepass:BetaView
source.9.favoriteId=1234
source.9.daysBack=60
source.9.extension=*
source.9.form=*
source.9.report=*
source.9.jobName=*
source.9.maxResources=200
```

| Schlüssel | Bedeutung |
|---|---|
| `baseUrl` | Adresse der Weboberfläche (dort liegen `login.action` usw.). Fehlt sie, gilt die URL des KeePass-Eintrags. Ressourcen-IDs: `betaview:<host/pfad>/<Favoriten-ID>/<Dokument-ID>`. |
| `credentialRef` | Pflicht: KeePass-Eintrag mit Benutzername und Passwort. Die Brücke `SecretBackedBetaViewCredentialsProvider` holt sie erst beim Login über den Security-Port; danach werden sie gelöscht. |
| `favoriteId` | Der Favorit, dessen Ergebnisliste gelesen wird (`favoriteID` in der Adresse der Auswahlseite). |
| `daysBack` | Zeitraum „letzte n Tage“ (Standard 60). |
| `extension`, `form`, `report`, `jobName` | Muster der Auswahl wie im Formular (Standard `*`). |
| `maxResources` | Höchstzahl Dokumente je Lauf (Standard 200). |
| `maxFileMegabytes` | Größere Downloads werden abgebrochen (Standard 50). |
| `locale`, `connectTimeoutMillis`, `readTimeoutMillis`, `userAgent` | Nur in der Datei; Standard `de`, 15 s, 120 s, User-Agent der Anwendung. |

## Verhalten

- **Sitzung**: eigener Cookie-Speicher je Quelle (keine Browser-Cookies), Weiterleitungen mit Cookies von Hand,
  CSRF-Token aus `getAppContext.action` an jeder Aktion. Die Sitzung bleibt bestehen, bis der Server sie ablehnt;
  dann einmal neu anmelden. Zugriffe einer Quelle laufen nacheinander, weil BetaView das geöffnete Dokument je
  Sitzung führt.
- **Discover**: `select.action` (Struts-Token) → `result.action` → `showResult.action`; jede Zeile wird einmal
  geöffnet, damit die stabile Dokument-ID aus dem Dokumentreiter feststeht. Titel = die ersten Zellen der Zeile.
  Danach `closeAllDocuments.action`.
- **Load**: `opendocumentlink.action?docid=…&favid=…`, dann Download im Originalformat über `downloadDialog.action`
  und `downloadWithOptions.action` (alle Seiten), Text über den Extraktor. Revision = Dokument-ID.
- **Fehler**: abgelehnte Anmeldung `ACCESS_DENIED`, 404 `NOT_FOUND`, 5xx und Netz `UNAVAILABLE`.

## Grenzen

- Nicht gegen ein echtes BetaView getestet (UNVERIFIED): Formularfelder und Zeitraum (`lastsel=individual`) sind aus
  MainframeMate übernommen, ebenso der Download; Windows ungeprüft. Die Tests laufen gegen einen Fake-Server
  (`BetaViewKnowledgeSourceTest`).
- Gespeicherte Auswahlen und Lesezeichen aus MainframeMate werden nicht gelesen; je Favorit eine Quelle.
