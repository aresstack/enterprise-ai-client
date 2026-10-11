# Confluence-Konfiguration

Der Adapter `source-confluence` macht ein **Confluence Data Center** über dessen REST-API als Wissensquelle
verfügbar (`KnowledgeSourcePort` und `SearchableKnowledgeSource`). Endpunkte, Felder, Paging und mTLS sind
aus MainframeMate `ConfluenceRestClient` übernommen. Zugangsdaten holt der Adapter ausschließlich über den
Security-Port (`SecretProvider.withSecret`), einmal je Port-Aufruf, nie je HTTP-Request, und nie in einem Feld.

## Schlüssel (`source.<n>.*` mit `type=confluence`)

```properties
source.1.type=confluence
source.1.baseUrl=https://confluence.intern.example/confluence
source.1.credentialRef=keepass:Confluence
source.1.startPoints=space:DEV
source.1.maxDepth=3
source.1.maxResources=1000
source.1.pageSize=50
source.1.includeAttachments=false
source.1.searchSpaceKeys=DEV
source.1.connectTimeoutMillis=15000
source.1.readTimeoutMillis=60000
source.1.allowInsecureHttp=false
# Optional mTLS, Variante 1: Zertifikat aus dem Windows-Zertifikatsspeicher (Windows-MY); nur der Alias
#source.1.clientCertificate.alias=mein-client-zertifikat
# Optional mTLS, Variante 2: PKCS12-Datei; der Alias ist auch hier Pflicht und bezeichnet den Schlüsseleintrag
# in der Datei, das Passwort liegt in KeePass
#source.1.clientCertificate.alias=mein-client-zertifikat
#source.1.clientCertificate.keyStoreFile=/pfad/zu/client.p12
#source.1.clientCertificate.keyStorePasswordRef=keepass:Client-Zertifikat
```

| Schlüssel | Bedeutung |
|---|---|
| `baseUrl` | Basis-URL der Confluence-Instanz inklusive Kontextpfad; der Adapter hängt `/rest/api/…` an. |
| `credentialRef` | KeePass-Eintrag. Mit Benutzername → `Basic`; ohne Benutzername → `Bearer` mit dem Passwortfeld als Personal Access Token (UNVERIFIED). Leer = anonym. |
| `startPoints` | Space-Key (`DEV` oder `space:DEV`), Seiten-ID (`123456` oder `page:123456`), persönlicher Space (`~alice`) oder eine Ressourcen-ID. |
| `maxDepth`, `maxResources` | Tiefe der Kindseiten ab den Startpunkten und Obergrenze je Lauf; das Restbudget wird je Knoten weitergegeben. |
| `pageSize` | Seitengröße der REST-Aufrufe; Paging folgt `_links.next`. |
| `includeAttachments` | Anhänge als Ressourcen aufnehmen (nur Text: `text/*`, JSON, XML; PDF und Office → `UNSUPPORTED`). |
| `searchSpaceKeys` | Spaces für `search` (CQL `type=page AND text ~ "…"`, optional `space in (…)`). |
| `allowInsecureHttp` | `ConfluenceConfig.build()` lehnt Zugangsdaten über `http` ab; nur mit `true` (Entwicklung) erlaubt. |
| `clientCertificate.alias` | Pflicht, sobald ein Client-Zertifikat konfiguriert ist; `AppConfigLoader` lehnt `keyStoreFile` oder `keyStorePasswordRef` ohne Alias ab. Ohne `keyStoreFile`: Alias des Zertifikats im Windows-Zertifikatsspeicher (Windows-MY), nur unter Windows. Mit `keyStoreFile`: Alias des Schlüsseleintrags in der PKCS12-Datei. |
| `clientCertificate.keyStoreFile`, `keyStorePasswordRef` | mTLS mit einer PKCS12-Datei (zusätzlich zum Alias); das Passwort wird erst beim ersten Verbindungsaufbau über KeePass geholt (`ClientCertificateFactory.deferred`), nie beim Start. `keyStorePasswordRef` ohne `keyStoreFile` ist ein Konfigurationsfehler. |

## Verhalten

- Ressourcen-IDs: `confluence:<sourceId>/page/<id>` und `confluence:<sourceId>/attachment/att<id>` (stabile
  URIs mit Schema je Quelle, Prinzip aus corenth).
- **Discover**: Breitensuche über Kindseiten ab den Startpunkten; gelöschte Seiten werden übersprungen,
  unpassende Anhänge verbrauchen kein Budget.
- **Load**: `content/{id}?expand=body.view,version,space,ancestors,metadata.labels`; HTML → Text mit
  Code-Makros als Codeblöcke, nummerierten Listen, Tabellen mit `|`, ohne Bilder und Makro-Bedienelemente.
  Revision aus `version` (Nummer und Zeitpunkt, ISO-8601 oder Data-Center-Kompaktoffset).
- **Transport** (`UrlConnectionConfluenceTransport`): folgt keinen Weiterleitungen (der Authorization-Header
  geht nie an ein anderes Ziel), nur `http`/`https`, Antwortgröße begrenzt (20 MB, Anhänge 5 MB). Proxy,
  Timeouts und Client-Zertifikat kommen aus der Composition Root.
- **Fehlerabbildung**: 401/403/3xx → `ACCESS_DENIED`, 404 → `NOT_FOUND`, 429/5xx/IO → `UNAVAILABLE`, HTML statt
  JSON, fehlendes `results`, Folgelink außerhalb `/rest/api/` oder Schleife → `INVALID_RESPONSE`. Meldungen
  nennen nie Antwortkörper oder Header. Fehlendes Secret: `NOT_AVAILABLE` → `UNAVAILABLE`, sonst `ACCESS_DENIED`.

## Lesebereich: Bilder und draw.io-Diagramme

- **Bilder** der Lese-Ansicht (`body.export_view`) laden angemeldet über denselben Transport wie REST, mit
  `Accept: image/*,*/*;q=0.8`. Adressen unter `_links.base` werden auf die eingestellte Adresse umgeschrieben,
  Leerzeichen und Umlaute prozentkodiert (`ConfluenceRestClient.encodeUnsafe`, nur im Bildweg). Fremde Hosts werden nie
  geladen. Einer Weiterleitung auf einen anderen Host folgt der Bildweg ohne Authorization, Cookie und
  Client-Zertifikat (`ConfluenceHttpTransport.getAnonymous`), nie von https auf http.
- **Sitzungs-Cookie-Neuversuch** (gefolgert für SSO-Instanzen, die Basic nur unter `/rest/` annehmen): Scheitert ein
  Bild mit 401/403, Weiterleitung oder HTML, holt der Client per angemeldetem `/rest/api/user/current` das
  Sitzungs-Cookie und lädt einmal neu. Das Cookie wird weder gespeichert noch geloggt; Löschcookies und doppelte
  Namen werden beim Lesen von `Set-Cookie` berücksichtigt. Bei 404 oder Netzfehlern gibt es keinen Neuversuch.
- **draw.io** (`drawio`, `drawio-sketch`, `inc-drawio`, erkannt an jedem `data-diagramdata`, bevor versteckte
  Elemente entfernt werden): `previewName`, `owningPageId` (Rückfall `ceoId`) und `revision` kommen aus den
  Makrodaten, nie aus der angezeigten Seite. An der Makrostelle steht ein Bild mit der Vorschau
  `/download/attachments/{owningPageId}/{previewName}?version={revision}&api=v2` (ohne lesbare Revision: ohne Query,
  also die jüngste Vorschau, mit Logzeile). Der Lader holt zuerst das Diagramm selbst
  (`/rest/drawio/1.0/diagram/crud/{Name}/{owningPageId}?revision={revision}`) und zeichnet es mit JGraphX als SVG
  (`DrawioDiagramRenderer`); enthält es Formen, die JGraphX nicht kennt, oder scheitert der Abruf, kommt die
  PNG-Vorschau. Angleichungen (kippbar): HTML-Beschriftungen als Text, draw.io-Farbwörter und -Standardfarben,
  `note` als Rechteck.

## Grenzen

- Nichts gegen eine echte Confluence-Instanz getestet (UNVERIFIED); die Tests laufen gegen `FakeConfluence`
  und einen lokalen HTTP-Server. Bearer/PAT ist UNVERIFIED, Windows-MY nur unter Windows prüfbar, der
  PKCS12-Pfad nur mit einem Testzertifikat ohne echte Gegenstelle.
- Nur Data Center: Folgelinks werden nur relativ zum Kontextpfad unter `/rest/api/` akzeptiert; Confluence
  Cloud ist nicht Ziel.
- Blogposts werden nicht erfasst; die Suche liefert keine Textausschnitte (`content/search` hat keine).
