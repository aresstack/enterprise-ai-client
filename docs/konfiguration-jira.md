# Jira-Konfiguration

Der Adapter `source-jira` macht ein **Jira Data Center** (geprüft gegen die REST-API 2, Ziel 11.3.x) als
Wissensquelle verfügbar (`KnowledgeSourcePort` und `SearchableKnowledgeSource`), **ausschließlich lesend**: der
Adapter sendet nur GET-Anfragen. Aufbau, Anmeldung, mTLS und Fehlerabbildung sind aus `source-confluence`
übernommen; Zugangsdaten holt der Adapter ausschließlich über den Security-Port (`SecretProvider.withSecret`),
einmal je Port-Aufruf, nie je HTTP-Request, und nie in einem Feld.

## Schlüssel (`source.<n>.*` mit `type=jira`)

```properties
source.1.type=jira
source.1.title=Jira
source.1.baseUrl=https://jira.intern.example/jira
source.1.credentialRef=keepass:Jira
# Zusätzliche JQL-Bedingung für Suche und Indexierung (ohne ORDER BY); leer = alle Vorgänge
source.1.jql=resolution = Unresolved
# Nur in der Datei:
source.1.startPoints=project:*
source.1.maxDepth=1
source.1.maxResources=1000000
source.1.pageSize=50
source.1.maxComments=200
source.1.maxResponseBytes=20971520
source.1.maxRetries=0
source.1.connectTimeoutMillis=15000
source.1.readTimeoutMillis=60000
source.1.allowInsecureHttp=false
# Optional mTLS, Variante 1: Zertifikat aus dem Windows-Zertifikatsspeicher (Windows-MY); nur der Alias
#source.1.clientCertificate.alias=mein-client-zertifikat
# Optional mTLS, Variante 2: PKCS12-Datei; der Alias bezeichnet den Schlüsseleintrag, das Passwort liegt in KeePass
#source.1.clientCertificate.alias=mein-client-zertifikat
#source.1.clientCertificate.keyStoreFile=/pfad/zu/client.p12
#source.1.clientCertificate.keyStorePasswordRef=keepass:Client-Zertifikat
# Zertifikat ausdrücklich nicht verwenden, auch wenn der KeePass-Eintrag MM_CertAlias trägt
#source.1.clientCertificate.use=false
```

| Schlüssel | Bedeutung |
|---|---|
| `baseUrl` | Basis-URL der Jira-Instanz inklusive Kontextpfad; der Adapter hängt `/rest/api/2/…` an. Mit KeePass-Eintrag auch aus dessen URL. |
| `credentialRef` | KeePass-Eintrag. Mit Benutzername → `Basic`; ohne Benutzername → `Bearer` mit dem Passwortfeld als Personal Access Token (Jira DC ab 8.14). Leer = anonym (nur öffentliche Projekte oder reines mTLS). `MM_RequiresLogin=false` im Eintrag: kein Header. |
| `jql` | Zusätzliche JQL-Bedingung, mit `AND` vor jede Suche und Auflistung gesetzt, z. B. `resolution = Unresolved` oder `updated >= -365d`. Keine Sortierung (`ORDER BY`). Im Dialog unter „Erweitert“. |
| `startPoints` | Standard `project:*` = alle Vorgänge aller Projekte, die unter Wissensquellen angehakt sind. Sonst `project:KEY`, ein Vorgang (`KEY-123`, `issue:KEY-123`), `jql:<Abfrage>` oder eine Ressourcen-ID. |
| `maxDepth`, `maxResources` | Ab einem Vorgang folgt `discover` Verknüpfungen, Teilvorgängen und dem übergeordneten Vorgang bis `maxDepth`; `maxResources` begrenzt jeden Lauf. |
| `pageSize` | Vorgänge je `/search`-Aufruf (`maxResults`, 1–1000; Jira kappt serverseitig). |
| `maxComments` | Höchstzahl Kommentare je Vorgang in Text und Lese-Ansicht (0–10000); fehlende Kommentare holt der Adapter über `/issue/<KEY>/comment` nach. |
| `allowInsecureHttp` | `JiraConfig.build()` lehnt Zugangsdaten über `http` ab; nur mit `true` (Entwicklung) erlaubt. |
| `clientCertificate.alias`, `.keyStoreFile`, `.keyStorePasswordRef`, `.use` | mTLS genau wie bei Confluence ([konfiguration-confluence.md](konfiguration-confluence.md)); ohne Alias gilt `MM_CertAlias` aus dem KeePass-Eintrag. |
| `maxRetries`, `retryBackoffMillis`, `maxRetryBackoffMillis` | Wiederholungen bei Netzfehler, HTTP 429 und 5xx wie bei Confluence (Standard 0). |

## Verhalten

- **Ressourcen-IDs**: `jira:<locator>/issue/<KEY-123>` (Locator = Basis-URL ohne Schema, z. B. `jira.intern.example/jira`).
- **Projekte** sind die Partitionen der Quelle (`/rest/api/2/project`): unter Wissensquellen hakt man an, welche
  durchsucht und indexiert werden, genau wie Spaces bei Confluence.
- **Discover**: alle Vorgänge der angehakten Projekte über `/rest/api/2/search` mit `project = "KEY" ORDER BY key ASC`,
  stapelweise (`startAt`/`maxResults`); ab einem einzelnen Vorgang Breitensuche über dessen Verknüpfungen.
- **Load**: `/rest/api/2/issue/<KEY>?fields=…&expand=renderedFields`. Text: Titel `KEY: Zusammenfassung`, dann
  „Feld: Wert“-Zeilen (Projekt, Typ, Status, Priorität, Lösung, Bearbeiter, Melder, Erstellt, Aktualisiert, Labels,
  Komponenten, Lösungsversionen), Abschnitte Beschreibung, Umgebung, Verknüpfungen und Kommentare (je Autor und
  Zeitpunkt). Gerenderte Felder (HTML) werden wie bei Confluence in Text mit Markdown-Struktur überführt; fehlen sie,
  steht das Wiki-Markup. Revision = `updated`. Metadaten `jira.key`, `jira.project`, `jira.issueType`, `jira.status`,
  `jira.priority`, `jira.resolution`, `jira.assignee`, `jira.reporter`, `jira.createdAt`, `jira.labels`,
  `jira.components`, `jira.fixVersions`, `jira.parent`.
- **Lese-Ansicht** (`PageViewSource`): dieselben Angaben als HTML, die Beschreibung und die Kommentare so, wie Jira
  sie rendert (Bilder werden mit der Anmeldung der Quelle nachgeladen, nur von dieser Jira), Verknüpfungen als Links
  auf `…/browse/<KEY>`, die der Lesebereich intern öffnet, die Anhänge des Vorgangs zum Herunterladen, und zuletzt der
  Abschnitt **Rohdaten**: die unveränderte JSON-Antwort der REST-API, eingerückt.
- **Suche**: JQL `text ~ "…"` (nur Titel: `summary ~ "…"`), `project in (…)` für die gewählten und
  `project not in (…)` für die abgehakten Projekte, dazu die `jql`-Zusatzbedingung, `ORDER BY updated DESC`. Jeder
  Treffer nennt Titel, Typ, Status und den Anfang der Beschreibung, trägt sein Projekt als Partition und die
  `browse`-Adresse.
- **Adressen** (`AddressableKnowledgeSource`): `…/browse/KEY-1`, `…/projects/X/issues/KEY-1`, `…?selectedIssue=KEY-1`
  und `jira://<locator>/issue/KEY-1` werden ohne Netzzugriff zur Ressourcen-ID.
- **Weitere Ports**: `PartitionListingSource` (alle Vorgänge eines Projekts flach, Cursor `p1.<KEY>.<startAt>`),
  `ResourceMetadataSource`, `StoredContentSource` (JSON-Antwort eines Vorgangs unverändert, Format
  `jira-rest-issue+json;expand=renderedFields`), `RestQuerySource` (GET unter `/rest/`, etwa Übergänge, Verlauf oder
  Arbeitsprotokolle eines Vorgangs für die KI-Werkzeuge).
- **Transport** (`UrlConnectionJiraTransport`): GET für die Wissensquelle; POST nur für die schreibenden
  Jira-Werkzeuge (siehe unten), genau einmal, ohne Wiederholung, mit `X-Atlassian-Token: no-check`; keine Weiterleitungen (der Authorization-Header verlässt den
  Server nie; eine Weiterleitung auf die Anmeldeseite ist `ACCESS_DENIED`), Antwortgröße begrenzt, Proxy-Route, TLS
  und Client-Zertifikat aus der Composition Root.
- **Fehlerabbildung**: 401/403/3xx → `ACCESS_DENIED`, 404 → `NOT_FOUND`, 429/5xx/IO → `UNAVAILABLE` (429 als
  `RateLimitedException`), HTML statt JSON oder zu große Antwort → `INVALID_RESPONSE`. Meldungen nennen Vorgang und
  Status, nie Antwortkörper, Header oder Zugangsdaten.

## Jira-Werkzeuge im Chat (`JiraMcpTools`)

Über die angehakten Jira-Quellen, keine eigene Anbindung (`JiraIssueOperations`, umgesetzt von
`JiraKnowledgeSource`). Ohne angehakte Jira-Quelle gibt es die Werkzeuge nicht.

| Werkzeug | Kanal | Wirkung | Freigabe |
|---|---|---|---|
| `jira_search` | Chat und Agent | JQL-Suche mit der Einschränkung `jql` der Quelle; Schlüssel, Titel, Typ, Status, Bearbeiter, Link | keine (nur lesend) |
| `jira_comment` | Chat | schreibt einen Kommentar (`POST /issue/KEY/comment`) | `write` = **ASK** |
| `jira_transition` | Chat | ohne `transition`: nennt die Übergänge; mit `transition` (ID oder Name): führt ihn aus (`POST /issue/KEY/transitions`) | `list` = ALLOW, `write` = **ASK** |

Schreiben nur mit ausdrücklicher Freigabe: Vor jedem Kommentar und jedem Übergang fragt das gemeinsame Freigabe-Gate
(`ToolApprovalGate`) den Benutzer und zeigt Vorgang, Titel und Inhalt bzw. Status vorher → Übergang → Status
nachher. Das Ziel bestimmt der Resolver aus Jira selbst (Schlüssel, Titel, letzte Änderung als Version, der echte
Übergang), nie aus den Angaben des Modells; ändert sich der Vorgang zwischen Frage und Ausführung, gilt die Freigabe
nicht. Ohne jemanden zum Fragen, bei Ablehnung oder Zeitablauf wird nichts geschrieben. Wer will, kann unter
Einstellungen › Tools `jira_comment:write` bzw. `jira_transition:write` umstellen; die Vorgabe bleibt ASK.

Lesen, Volltext und Seitenbaum laufen weiter über die Wissenswerkzeuge (`knowledge_read` mit dem Link oder der
Adresse eines Vorgangs).

## Grenzen

- Die Wissensquelle bleibt nur lesend; geschrieben wird nur über `jira_comment` und `jira_transition` nach Freigabe.
  Vorgänge anlegen oder Felder bearbeiten gibt es nicht.
- Jira-Makros und Vorgangs-Links in Confluence-Seiten werden hier noch nicht erkannt (eigener Schritt).
- Die Live-Prüfung gegen eine echte Jira-Instanz (Proxy mit Anmeldung, mTLS, Einmalcode) steht noch aus; der
  Adapter wurde gegen eine Fake-Jira mit den Antwortformen der REST-API 2 getestet.
