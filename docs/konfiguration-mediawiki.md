# MediaWiki-Konfiguration

Der Adapter `source-mediawiki` macht ein MediaWiki über die Action-API (`api.php`) als Wissensquelle
verfügbar. Er implementiert `KnowledgeSourcePort` und `SearchableKnowledgeSource`; der Kern kennt weder
Wiki- noch HTTP-Typen. JWBF wird bewusst nicht verwendet, die Abfragen sind aus MainframeMate
`JwbfWikiContentService` übernommen und laufen über `HttpURLConnection`, JSON über Gson, HTML über jsoup.

## Schlüssel (`source.<id>.*` mit `type=mediawiki`)

```properties
sources=wiki

source.wiki.type=mediawiki
source.wiki.apiUrl=https://wiki.intern.example/w/api.php
source.wiki.siteKey=intern
source.wiki.displayName=Intranet-Wiki
source.wiki.credentialRef=keepass:Intranet-Wiki
source.wiki.requiresLogin=true
source.wiki.startPoints=Hauptseite,Handbuch
source.wiki.maxDepth=2
source.wiki.maxResources=500
source.wiki.linkNamespaces=0
source.wiki.connectTimeoutMillis=15000
source.wiki.readTimeoutMillis=30000
```

| Schlüssel | Bedeutung |
|---|---|
| `sources` | Kommagetrennte Quell-IDs; je ID ein Block `source.<id>.*`. Die ID ist die `KnowledgeSourceId`, unter der Ressourcen im Index liegen, und der Wert für `source_ids` im MCP-Werkzeug `search_knowledge`. |
| `apiUrl` | Basis-URL des Wikis oder direkt die `api.php`; ohne Query, Fragment und Userinfo. Die `api.php` wird wie in MainframeMate abgeleitet. |
| `siteKey` | Stabiler, kleingeschriebener Schlüssel, Teil jeder Ressourcen-ID `wiki:<siteKey>/<Titel>`. Nach dem ersten Indexieren nicht mehr ändern, sonst gelten alle Seiten als neu. |
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
- Ändert sich `siteKey` oder zieht das Wiki um, ändern sich die Ressourcen-IDs.

## Weitere Quellen

Neue Quellen (SharePoint, lokale Dateien) implementieren `source-api`, erben den `KnowledgeSourceContractTest`
aus dessen Testfixtures und werden in `app.composition.AdapterAssembly` aus einem eigenen
`source.<id>.type` gebaut. Kern und `application` bleiben unverändert.
