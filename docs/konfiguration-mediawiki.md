# MediaWiki-Konfiguration

Der Adapter `source-mediawiki` macht ein MediaWiki über die Action-API (`api.php`) als Wissensquelle
verfügbar. Er implementiert `KnowledgeSourcePort` und `SearchableKnowledgeSource`; der Kern kennt weder
Wiki- noch HTTP-Typen. JWBF wird bewusst nicht verwendet, die Abfragen sind aus MainframeMate
`JwbfWikiContentService` übernommen und laufen über `HttpURLConnection`, JSON über Gson, HTML über jsoup.

## Schlüssel (`source.<n>.*` mit `type=mediawiki`)

```properties
source.1.type=mediawiki
source.1.apiUrl=https://wiki.intern.example/w/api.php
source.1.displayName=Intranet-Wiki
source.1.credentialRef=keepass:Intranet-Wiki
source.1.requiresLogin=true
source.1.startPoints=Hauptseite,Handbuch
source.1.maxDepth=2
source.1.maxResources=500
source.1.linkNamespaces=0
source.1.connectTimeoutMillis=15000
source.1.readTimeoutMillis=30000
```

| Schlüssel | Bedeutung |
|---|---|
| `source.<n>.*` | Ein Listeneintrag je Quelle (`n` nur Position). Die Identität ist die kanonische Adresse aus Typ und Endpoint (`source.<n>.address`, z. B. `wiki://wiki.intern.example/w` oder `confluence://confluence.intern.example/confluence`); die Anwendung setzt sie, eine zweite Quelle mit derselben Adresse wird abgelehnt. |
| `apiUrl` | Basis-URL des Wikis oder direkt die `api.php`; ohne Query, Fragment und Userinfo. Die `api.php` wird wie in MainframeMate abgeleitet. Aus ihr (Host klein, ohne Standardport, ohne `api.php`) entsteht jede Ressourcen-ID `wiki:<host/pfad>/<Titel>`, etwa `wiki:wiki.intern.example/w/Hauptseite`. Einen eigenen Site-Schlüssel gibt es nicht mehr. |
| `credentialRef` | Titel des KeePass-Eintrags mit Benutzername und Passwort; leer = anonym. Die Zugangsdaten werden erst beim Login über `MediaWikiCredentialsProvider` angefordert und danach gelöscht. |
| `requiresLogin` | `true`: ohne Login wird nicht gelesen. `false`: ohne Zugangsdaten wird anonym gelesen; verweigert das Wiki den Zugriff, ist das `ACCESS_DENIED`. |
| `startPoints` | Seitentitel, von denen der Crawl ausgeht. |
| `maxDepth` | Link-Tiefe ab den Startseiten; `0` = nur die Startseiten. |
| `maxResources` | Obergrenze je `discover`-Lauf (Standard 500). |
| `linkNamespaces` | Namensräume, deren Links verfolgt werden (`0` Artikel, `4` Projekt; mehrere kommagetrennt). |

## Verhalten

- **Discover**: Breitensuche je Ebene über Seitenlinks (Algorithmus aus MainframeMate `WikiSourceScanner`),
  besuchte Titel werden gemerkt, Weiterleitungen aufgelöst; Dokumente und Links tragen die ID der Zielseite.
- **Load**: gerendertes HTML (`action=parse`) wird in Klartext mit Markdown-Überschriften überführt
  (Bereinigung nach MainframeMate `HtmlPostProcessor`). Revision = `touched` plus `lastrevid`; damit kann die
  Indexierung unveränderte Seiten überspringen.
- **Search**: Wiki-Volltextsuche der Action-API für `SearchableKnowledgeSource.search`.
- **Fehler** werden auf `KnowledgeSourceException` abgebildet (`NOT_FOUND`, `UNAVAILABLE`, `ACCESS_DENIED`,
  `INVALID_RESPONSE`, `UNSUPPORTED`); Meldungen enthalten weder Zugangsdaten noch rohe Antworten. Passwörter
  werden nach dem Login gelöscht und in `toString()` maskiert.
- **Session**: eigener `CookieManager` je Site; Folgeanfragen nutzen dieselbe Session wie der Login.
- **Proxy und Timeouts** kommen aus der Composition Root (`network.proxy.*`, `connectTimeoutMillis`,
  `readTimeoutMillis`).

## Grenzen

- Nichts gegen ein echtes Firmen-Wiki getestet (UNVERIFIED): Login-Varianten, Single Sign-on, Proxy. Die Tests
  laufen gegen `FakeMediaWikiTransport` und einen lokalen HTTP-Server (`MediaWikiHttpIntegrationTest`).
- Anhänge und Bilder werden nicht indexiert.
- Zieht das Wiki um (andere `apiUrl`), ändern sich Schlüssel und Ressourcen-IDs; die alten Indexeinträge werden gelöscht.

## Weitere Quellen

Neue Quellen (SharePoint, lokale Dateien) implementieren `source-api`, erben den `KnowledgeSourceContractTest`
aus dessen Testfixtures und werden in `proasteion.application.composition.AdapterAssembly` aus einem eigenen
`source.<n>.type` gebaut. Kern und `acropolis` bleiben unverändert.
