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
- **Transport** (`UrlConnectionConfluenceTransport`): folgt bis zu fünf Weiterleitungen nur auf denselben Host (der
  Authorization-Header geht nie an ein anderes Ziel) und schickt dabei gesetzte Cookies mit; nach 301/302/303 als GET
  ohne Körper, nach 307/308 mit derselben Methode. Formulare sendet `postForm` (`application/x-www-form-urlencoded`)
  über dieselbe Verbindungskonfiguration. Nur `http`/`https`, Antwortgröße begrenzt (20 MB, Anhänge 5 MB). Proxy,
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
  Die Logzeile nennt Status, Cookie-Namen (nie Werte) sowie Typ und Benutzername aus der JSON-Antwort
  (`benutzerTyp=known|anonymous`); meldet sie `anonymous`, gibt es keinen Neuversuch.
- **HTML statt Datei** (Bild, Anhang, Vorschau): Der Client untersucht die Seite (`HtmlResponseDiagnosis`) und schreibt
  eine WARNING-Zeile `[Confluence] <Vorgang>: HTML statt Datei untersucht: …` mit Status, Content-Type, Länge,
  Adresse und Weiterleitungen ohne Query, gesendetem Authorization-Verfahren und Cookie-Namen, gesetzten Cookie-Namen,
  Art der Seite (`TWO_FACTOR`, `CONFLUENCE_LOGIN`, `SSO`, `ERROR_PAGE`, `OTHER_FORM`, `CONFLUENCE_PAGE`, `OTHER`),
  Titel, Überschrift, Textanfang, Formularen (Ziel, Methode, Feldnamen und -typen, Knöpfe), Meta-Refresh, Zielhosts
  und dem Benutzer laut `ajs-remote-user`. Bis zu fünf Antworten je Quelle und Programmlauf landen als
  `confluence-failed-download-<yyyyMMdd-HHmmss-SSS>.html` im Protokollverzeichnis (`<Anwendungsverzeichnis>/logs`),
  mit `***` statt der Werte von Eingabefeldern und Token-Metafeldern; Header und Cookie-Werte stehen nie darin.
- **Einmalcode (Zwei-Faktor-Plugin, OTP per E-Mail)**: Ist die HTML-Antwort eine Einmalcode-Seite (`TWO_FACTOR`),
  liest der Client das POST-Formular aus der unveränderten Seite (`OneTimeCodeForm`: Codefeld, versteckte Felder mit
  ihren echten Werten, Absende-Knopf; Ziel nur auf der eingestellten Confluence) und meldet den Abruf mit
  `OneTimeCodeRequiredException` samt `OneTimeCodeChallenge` (source-api). Kein Dialog: Der Anzeigebereich (Bild- oder
  Anhang-Ansicht) wird selbst zur Abfrage (`ReaderSignInPanel`: „Zusätzliche Anmeldung erforderlich“, Hinweis der
  Seite, OTP-Feld, „Code erneut senden“, „Bestätigen“). Das Formular geht über denselben Transport (Proxy, TLS,
  Client-Zertifikat, User-Agent) mit Authorization und den Cookies der Web-Sitzung zurück; gesetzte Cookies übernimmt
  die Sitzung (`ConfluenceWebSession`, je Quelle, nur im Speicher), Weiterleitungen werden verfolgt, danach lädt die
  Ansicht die Datei neu. Weitere Bilder, Anhänge und Vorschauen laden mit derselben Sitzung ohne neue Abfrage: zuerst
  nur mit Cookies wie der Browser, reicht das nicht, mit Authorization und Cookies. Läuft die Sitzung ab, kommt die
  Abfrage erst beim nächsten Dateiabruf wieder. „Code erneut senden“ setzt wie das Skript der Seite das versteckte Feld
  `resendOtpLinkClicked` auf `true` und sendet das Formular ohne Knopf. Ein falscher Code zeigt die Meldung der Seite
  (sonst „Der Code wurde nicht angenommen.“). `PageViewSource.downloadAccess()` meldet `OPEN`,
  `ONE_TIME_CODE_REQUIRED` oder `UNLOCKED` (für Ansichten mit eigenem Ersatz, etwa gerenderte draw.io-Diagramme).
  Code, Formularwerte und Cookie-Werte stehen in keiner Logzeile und werden nicht gespeichert; ein Programmneustart
  beginnt ohne Sitzung. Logzeilen: `[Confluence] Einmalcode-Abfrage: formular=… ziel=… felder=…`,
  `[Confluence] Einmalcode bestätigt: status=… sitzungsCookies=[Namen]`. Die Indexierung fragt nie nach einem Code.
- **draw.io** (`drawio`, `drawio-sketch`, `inc-drawio`, erkannt an jedem `data-diagramdata`, bevor versteckte
  Elemente entfernt werden): `previewName`, `owningPageId` (Rückfall `ceoId`) und `revision` kommen aus den
  Makrodaten, nie aus der angezeigten Seite. An der Makrostelle steht ein Bild mit der Vorschau
  `/download/attachments/{owningPageId}/{previewName}?version={revision}&api=v2` (ohne lesbare Revision: ohne Query,
  also die jüngste Vorschau, mit Logzeile) und dem Kennzeichen `#drawio={Name}&page={owningPageId}&revision={revision}`.
  Liefert Confluence im Makro (oder anderswo auf der Seite) schon ein `<img>` auf genau diese Vorschau, bekommt dieses
  Bild dieselbe Adresse samt Kennzeichen; es wird nie als gewöhnliches Bild geladen. Fehlen in `export_view` die Diagrammdaten
  (nur das Vorschaubild), kommen Name, Revision und bei `inc-drawio` die Eigentümerseite aus den Makroparametern in
  `body.storage` (`diagramName`, `revision`, `pageId`); gekennzeichnet wird dann jedes Bild auf
  `/download/attachments/{Seite}/{diagramName}.png`. Je Seite mit draw.io-Makros eine Logzeile
  `[Confluence] draw.io: Seite … Bilder gekennzeichnet aus Diagrammdaten=…, aus Makroparametern=…, Makros im
  Seitenquelltext=…`. Der Lader holt zuerst das Diagramm selbst
  (`/rest/drawio/1.0/diagram/crud/{Name}/{owningPageId}?revision={revision}`) und zeichnet es mit JGraphX als SVG
  (`DrawioDiagramRenderer`); enthält es Formen, die JGraphX nicht kennt, oder scheitert der Abruf, kommt die
  PNG-Vorschau. Angleichungen (kippbar): HTML-Beschriftungen als Text, draw.io-Farbwörter und -Standardfarben,
  `note` als Rechteck. Selbst gezeichnet, weil JGraphX sie nicht kennt: Blockpfeil `mxgraph.arrows2.arrow` (`dx`,
  `dy`, `notch`, Richtung, Spiegelung) und UML-Schnittstelle `providedRequiredInterface` (Kugel und Pfanne, Drehung).
  Im Bild-Viewer schaltet „Gerendert“/„Original“ je draw.io-Bild um: „Gerendert“ (Standard) ist die Zeichnung
  aus den Diagrammdaten über REST, ohne `/download/`; „Original“ hängt `&view=original` an das Fragment und lädt nur
  die PNG-Vorschau. Scheitert das Original (etwa an der Einmalcode-Abfrage), zeigt der Viewer sofort wieder die
  gerenderte Zeichnung und der Knopf heißt „Original (Anmeldung nötig)“. Hat die Quelle Dateiabrufe schon per
  Einmalcode freigeschaltet, zeigt der Viewer ohne eigene Wahl das Original zuerst; ist die Sitzung abgelaufen, fragt
  er dann nicht, sondern zeigt die gerenderte Zeichnung. Die Code-Eingabe erscheint nur, wenn man „Original“
  ausdrücklich wählt, im Bildbereich mit „Zurück zu Gerendert“. Gespeichert wird, was zu sehen ist:
  gerendert als `.svg`, Original als `.png`.
  Logzeilen (INFO): `[Confluence] draw.io: rendere Diagramm name=… page=… revision=…`, danach
  `[Confluence] draw.io: SVG erzeugt (… Bytes)` oder
  `[Confluence] draw.io: JGraphX nicht möglich (Grund), verwende PNG-Vorschau: …`.

## Grenzen

- Nichts gegen eine echte Confluence-Instanz getestet (UNVERIFIED); die Tests laufen gegen `FakeConfluence`
  und einen lokalen HTTP-Server. Bearer/PAT ist UNVERIFIED, Windows-MY nur unter Windows prüfbar, der
  PKCS12-Pfad nur mit einem Testzertifikat ohne echte Gegenstelle.
- Nur Data Center: Folgelinks werden nur relativ zum Kontextpfad unter `/rest/api/` akzeptiert; Confluence
  Cloud ist nicht Ziel.
- Blogposts werden nicht erfasst; die Suche liefert keine Textausschnitte (`content/search` hat keine).
