# Confluence-Konfiguration

Der Adapter `source-confluence` macht ein **Confluence Data Center** über dessen REST-API als Wissensquelle
verfügbar (`KnowledgeSourcePort` und `SearchableKnowledgeSource`). Endpunkte, Felder, Paging und mTLS sind
aus MainframeMate `ConfluenceRestClient` übernommen. Zugangsdaten holt der Adapter ausschließlich über den
Security-Port (`SecretProvider.withSecret`), einmal je Port-Aufruf, nie je HTTP-Request, und nie in einem Feld.

## Schlüssel (`source.<id>.*` mit `type=confluence`)

```properties
sources=confluence

source.confluence.type=confluence
source.confluence.baseUrl=https://confluence.intern.example/confluence
source.confluence.credentialRef=keepass:Confluence
source.confluence.startPoints=space:DEV
source.confluence.maxDepth=3
source.confluence.maxResources=1000
source.confluence.pageSize=50
source.confluence.includeAttachments=false
source.confluence.searchSpaceKeys=DEV
source.confluence.connectTimeoutMillis=15000
source.confluence.readTimeoutMillis=60000
source.confluence.allowInsecureHttp=false
# Optional mTLS: Alias im Windows-Zertifikatsspeicher (Windows-MY) ...
#source.confluence.clientCertificate.alias=mein-client-zertifikat
# ... oder PKCS12-Datei; das Passwort liegt in KeePass
#source.confluence.clientCertificate.keyStoreFile=/pfad/zu/client.p12
#source.confluence.clientCertificate.keyStorePasswordRef=keepass:Client-Zertifikat
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
| `clientCertificate.alias` | mTLS mit dem Zertifikat aus dem Windows-Zertifikatsspeicher (Windows-MY), nur unter Windows. |
| `clientCertificate.keyStoreFile`, `keyStorePasswordRef` | mTLS mit einer PKCS12-Datei; das Passwort wird erst beim ersten Verbindungsaufbau über KeePass geholt (`ClientCertificateFactory.deferred`), nie beim Start. |

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

## Grenzen

- Nichts gegen eine echte Confluence-Instanz getestet (UNVERIFIED); die Tests laufen gegen `FakeConfluence`
  und einen lokalen HTTP-Server. Bearer/PAT ist UNVERIFIED, Windows-MY nur unter Windows prüfbar, der
  PKCS12-Pfad nur mit einem Testzertifikat ohne echte Gegenstelle.
- Nur Data Center: Folgelinks werden nur relativ zum Kontextpfad unter `/rest/api/` akzeptiert; Confluence
  Cloud ist nicht Ziel.
- Blogposts werden nicht erfasst; die Suche liefert keine Textausschnitte (`content/search` hat keine).
