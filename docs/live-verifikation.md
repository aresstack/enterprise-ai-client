# Live-Verifikation gegen echte Dienste

Alle 26 Arbeitspakete sind gegen lokale Fakes getestet; gegen die echte Enterprise-API, ein echtes MediaWiki,
KeePass oder Confluence ist bis zur Live-Verifikation nichts gelaufen (UNVERIFIED, siehe
[Einschränkungen](einschraenkungen.md)). Diese Seite ist die Anleitung für den Lauf gegen die echten Dienste in
sieben Stufen, in der Reihenfolge des Auftraggebers, eine Stufe nach der anderen. Jede Stufe ist ein eigener
Aufruf von `./gradlew :integration-tests:liveTest` (lokal oder, für die Stufen 1 bis 5, über den manuell
gestarteten GitHub-Actions-Workflow „Live-Verifikation“, siehe unten); was zurückgemeldet wird, steht je
Stufe unter „Rückmeldung“. Nach jedem echten Ergebnis wird die UNVERIFIED-Markierung nur für genau die geprüfte Fähigkeit
entfernt und [Tests](tests.md) sowie [Einschränkungen](einschraenkungen.md) werden nachgeführt; das
[Ergebnisprotokoll](#ergebnisprotokoll) unten hält den Stand fest.

| Stufe | Was | Testklasse (`integration-tests`, Source-Set `liveTest`) | Pflichtparameter | Secret (Umgebungsvariable) |
|---|---|---|---|---|
| 1 | Enterprise `/chat/completions`: Streaming, ohne Streaming, Fehlerantwort | `LiveChatCompletionsIT` | `live.chat.baseUrl`, `live.chat.model` | `ENTERPRISE_AI_LIVE_API_KEY` |
| 2 | `/embeddings` mit einem Text (Adapter, `SINGLE_STRING`) | `LiveEmbeddingsIT.singleInput…` | `live.embedding.model` (+ Chat-Base-URL) | `ENTERPRISE_AI_LIVE_API_KEY` |
| 3 | `/embeddings` mit Array-Eingabe (Roh-Probe, dann Adapter `ARRAY_UNVERIFIED`) | `LiveEmbeddingsIT.arrayInput…` | wie 2 | `ENTERPRISE_AI_LIVE_API_KEY` |
| 4 | Tatsächliche Embedding-Dimension und Antwortform erfassen | `LiveEmbeddingDimensionIT` | wie 2 | `ENTERPRISE_AI_LIVE_API_KEY` |
| 5 | MediaWiki: Startseite finden, laden, indexieren, Wiki-Suche | `LiveMediaWikiIT` | `live.wiki.apiUrl`, `live.wiki.startPoint` | `ENTERPRISE_AI_LIVE_WIKI_PASSWORD` (nur mit `live.wiki.user`) |
| 6 | KeePassRPC: Pairing über den Dialog der Anwendung, Eintrag lesen | `LiveKeePassIT` | `live.keepass.entry` | – (Einmal-Passwort im Dialog) |
| 7 | Confluence über KeePass: Startpunkt laden, CQL-Suche | `LiveConfluenceKeePassIT` | `live.confluence.baseUrl`, `live.confluence.startPoint`, `live.confluence.credentialRef` | – (Pairing aus Stufe 6) |

Die Stufen 1 bis 4 brauchen nur die Enterprise-API und den API-Key; 5 ein erreichbares Wiki; 6 ein laufendes,
entsperrtes KeePass mit KeePassRPC-Plugin auf demselben Rechner; 7 zusätzlich ein Confluence Data Center.

## Vorbereitung (einmal)

1. Aktuellen Stand von `main` auschecken; ein JDK 8 oder neuer; `./gradlew :integration-tests:compileLiveTestJava`
   muss durchlaufen.
2. Secrets nur als Umgebungsvariablen der Shell setzen, nie auf der Gradle-Kommandozeile und nie in eine Datei
   des Repositories:

   ```bash
   # bash
   export ENTERPRISE_AI_LIVE_API_KEY='…'
   ```

   ```powershell
   # PowerShell
   $env:ENTERPRISE_AI_LIVE_API_KEY = '…'
   ```

3. Adressen, Modellnamen und Titel kommen als `-Dlive.*` auf der Kommandozeile (unter PowerShell jedes `-D…` in
   Anführungszeichen setzen). Die Stufe wählt `-Dlive.stage=N`; `-Dlive.stage=1-4` oder `-Dlive.stage=2,3` fassen
   Stufen zusammen, ohne Angabe laufen alle. Tests, denen ein Parameter fehlt, werden übersprungen (`SKIPPED`),
   nie rot.
4. Unternehmensproxy und eigene Zertifikate: `-Dhttps.proxyHost`, `-Dhttps.proxyPort`, `-Dhttp.nonProxyHosts`,
   `-Djavax.net.ssl.trustStore`, `-Djavax.net.ssl.trustStoreType` und `-Djavax.net.ssl.trustStorePassword` werden
   an die Test-JVM durchgereicht; alternativ wirkt `JAVA_TOOL_OPTIONS` auf alle JVMs.

### Was zurückgemeldet wird

- Alle Konsolenzeilen, die mit `[live] Stufe N:` beginnen. Sie enthalten nur Status, Codes, Anzahlen und
  Messwerte; weder URL, Token, Passwort noch Antworttexte.
- Die Gradle-Zusammenfassung je Test (`PASSED`, `SKIPPED`, `FAILED`).
- Bei `FAILED`: Typ und Meldung der Exception (die ersten Zeilen des Stacktrace). Transportfehler des JDK können
  Hostnamen oder Proxy-Adressen enthalten: vor dem Rückmelden schwärzen. Die Berichte unter
  `integration-tests/build/reports/tests/liveTest/` und `build/test-results/liveTest/` enthalten dieselbe Ausgabe
  und bleiben lokal.

Ein `SKIPPED` mit „Live-Test übersprungen: -Dlive.… fehlt“ heißt: Parameter vergessen, nicht: Dienst
fehlerhaft.

## Lauf über GitHub Actions (Stufen 1 bis 5)

Der Workflow **Live-Verifikation** (`.github/workflows/live-verification.yml`) läuft nur auf Knopfdruck:
GitHub → Actions → „Live-Verifikation“ → „Run workflow“, Stufe wählen (`1`, `2`, `3`, `4`, `1-4` oder `5`),
JDK (Standard 8) und Runner. Er ruft genau das Kommando dieser Anleitung mit `-Dlive.headless=true` auf; die
Parameter kommen aus Secrets und Variablen, die nur der Repository- oder Organisationsinhaber hinterlegt
(Settings → Secrets and variables → Actions). Der Workflow kennt nur die Namen:

| Name | Art | Stufe | Inhalt |
|---|---|---|---|
| `ENTERPRISE_AI_LIVE_API_KEY` | Secret | 1–4 | API-Key der Enterprise-API |
| `ENTERPRISE_AI_LIVE_CHAT_BASE_URL` | Secret | 1–4 | Base-URL der Enterprise-API (wird zu `-Dlive.chat.baseUrl`) |
| `ENTERPRISE_AI_LIVE_EMBEDDING_BASE_URL` | Secret | 2–4 | nur, wenn `/embeddings` eine andere Base-URL hat |
| `ENTERPRISE_AI_LIVE_CHAT_MODEL` | Variable | 1 | Chat-Modell (`-Dlive.chat.model`) |
| `ENTERPRISE_AI_LIVE_EMBEDDING_MODEL` | Variable | 2–4 | Embedding-Modell (`-Dlive.embedding.model`) |
| `ENTERPRISE_AI_LIVE_WIKI_API_URL` | Secret | 5 | MediaWiki-Basis (`-Dlive.wiki.apiUrl`) |
| `ENTERPRISE_AI_LIVE_WIKI_START_POINT` | Variable | 5 | Seitentitel als Startpunkt |
| `ENTERPRISE_AI_LIVE_WIKI_USER`, `ENTERPRISE_AI_LIVE_WIKI_PASSWORD` | Secret | 5 | Wiki-Login, optional (ohne Benutzer anonym) |

Die Base-URLs sind Secrets, damit GitHub ihre Werte im Log maskiert; ein Schritt des Workflows maskiert
zusätzlich die Hostnamen, sodass auch Transportfehler des JDK keinen Host zeigen. Die Eingabe
`embedding_dimension` prüft in Stufe 4 eine erwartete Dimension (leer: aus der ersten Antwort übernehmen).

- Rückmeldung ist das Job-Log des Schritts „Live-Verifikation, Stufe N“: die `[live] Stufe N:`-Zeilen und die
  Gradle-Zusammenfassung je Test. Testberichte werden bewusst nicht als Artefakt abgelegt.
- Die erste Zeile des Schritts („Hinterlegt: …“) sagt je Name nur `ja` oder `nein`; ein `SKIPPED` ohne
  `ja` an der passenden Stelle heißt: Secret oder Variable fehlt.
- Voraussetzung: Der Runner muss die Dienste erreichen. GitHub-gehostete Runner (`ubuntu-latest`) kommen nur
  ins Internet. Liegt die Enterprise-API oder das Wiki im Firmennetz, braucht es dort einen Self-hosted Runner
  (Eingabe `runner` = `self-hosted`, mit JDK oder Internetzugang für Temurin) oder die Stufen laufen auf einem
  Arbeitsplatzrechner nach dieser Anleitung.
- Stufen 6 und 7 (KeePassRPC, Confluence über KeePass) gibt es im Workflow nicht: Sie brauchen ein laufendes,
  entsperrtes KeePass und den Pairing-Dialog und laufen nur auf einem Arbeitsplatzrechner.

## Stufe 1 – Enterprise `/chat/completions`

Voraussetzung: Base-URL der Enterprise-API (ohne `/chat/completions`), Modellname, API-Key in
`ENTERPRISE_AI_LIVE_API_KEY`.

```bash
./gradlew :integration-tests:liveTest -Dlive.stage=1 \
  -Dlive.chat.baseUrl=https://ki.intern.example/v1 -Dlive.chat.model=openai/gpt-oss-120b
```

Drei Tests: `streamsAnAnswerFromTheEnterpriseApi` (Streaming über `ChatService`, `stream=true` im Body),
`completesWithoutStreaming` (`stream=false` direkt über den Port) und `reportsHowAnUnknownModelIsAnswered`
(Zusatzbefund zur Form einer Fehlerantwort; jede Antwort ist ein Befund, der Test wird davon nicht rot).

Rückmeldung: die drei `[live] Stufe 1:`-Zeilen (Deltas, Zeichen, `finish_reason`, ob `usage` gemeldet wird, ob
die Antwort das konfigurierte Modell nennt; HTTP-Status und Einordnung des unbekannten Modells).

## Stufen 2 bis 4 – `/embeddings`

Voraussetzung: wie Stufe 1, dazu der Name des Embedding-Modells. `live.embedding.baseUrl` ist nur nötig, wenn
der Embedding-Endpunkt eine andere Basis hat als der Chat. **`live.embedding.dimension` beim ersten Lauf
weglassen**: Die Tests übernehmen die Dimension aus der ersten echten Antwort und melden sie; erst danach
gehört der Wert in die Konfiguration (`embedding.dimension`). Mit gesetztem Wert prüfen die Stufen 2 und 3 ihn
über den Adapter, Stufe 4 vergleicht ihn ausdrücklich.

```bash
./gradlew :integration-tests:liveTest -Dlive.stage=2 \
  -Dlive.chat.baseUrl=https://ki.intern.example/v1 -Dlive.embedding.model=danielheinz/e5-base-sts-en-de
./gradlew :integration-tests:liveTest -Dlive.stage=3 …   # gleiche Parameter
./gradlew :integration-tests:liveTest -Dlive.stage=4 …   # gleiche Parameter
```

- **Stufe 2** (`singleInputReturnsOneVectorPerText`): der produktive Adapter im Standardmodus (ein Request je
  Text) mit einem und mit drei Texten. Rückmeldung: Dimension, Norm, die drei Cosinus-Werte (gleicher Text
  zweimal, Paraphrase, fremdes Thema).
- **Stufe 3** (`arrayInputProbe`): zuerst eine Roh-Probe mit `"input": [a, b, c]` über dieselben Header wie der
  Adapter, dann, nur wenn der Dienst Arrays annimmt und je Eingabe einen Eintrag liefert, der Adapter im Modus
  `ARRAY_UNVERIFIED` mit Teil-Batches von zwei Texten. Die Reihenfolge wird gegen Einzelanfragen geprüft
  (Cosinus ≥ 0,999). Rückmeldung: alle `[live] Stufe 3:`-Zeilen, vor allem die Zeile mit `BEFUND`. Der Befund
  („abgelehnt“, „angenommen, aber nicht verwendbar“, „Reihenfolge nicht bestätigt“ oder „angenommen, Reihenfolge
  bestätigt“) entscheidet, ob `ARRAY_UNVERIFIED` Standard werden kann; diese Entscheidung trifft der
  Auftraggeber.
- **Stufe 4** (`reportsTheActualDimensionAndResponseShape`): beschreibt die Antwort auf einen Einzeltext
  (HTTP-Status, `object`, Anzahl `data`, `index`, Form von `embedding`, Dimension, Endlichkeit, L2-Norm, `usage`)
  und meldet die tatsächliche Dimension als Empfehlung für `embedding.dimension`. Zusatzbefund: ob
  `encoding_format=float` angenommen wird. Rückmeldung: beide Zeilen.

## Stufe 5 – MediaWiki

Voraussetzung: die Action-API des Wikis ist vom Rechner erreichbar; ein Seitentitel als Startpunkt. Mit Login:
`live.wiki.user` und das Passwort in `ENTERPRISE_AI_LIVE_WIKI_PASSWORD`; ohne Benutzer wird anonym gelesen.

```bash
export ENTERPRISE_AI_LIVE_WIKI_PASSWORD='…'   # nur mit -Dlive.wiki.user
./gradlew :integration-tests:liveTest -Dlive.stage=5 \
  -Dlive.wiki.apiUrl=https://wiki.intern.example/w -Dlive.wiki.startPoint=Hauptseite \
  -Dlive.wiki.user=… [-Dlive.wiki.siteKey=intern] [-Dlive.wiki.maxDepth=0] [-Dlive.wiki.maxResources=20]
```

Zwei Tests: `startPageIsDiscoveredLoadedAndIndexed` (discover, load, Chunking und Indexierung in einen
In-Memory-Index mit deterministischen Test-Embeddings, Retrieval nach dem Seitentitel) und
`searchOfTheWikiFindsTheStartPage` (Volltextsuche des Wikis). `maxDepth=1` lässt den Crawl Links der
Startseite folgen (`maxResources` begrenzt ihn).

Rückmeldung: die `[live] Stufe 5:`-Zeilen (anonym oder mit Login, Seitenzahl, Zeichen, Revision bekannt,
Abschnitte, Treffer der Wiki-Suche).

## Stufe 6 – KeePassRPC

Voraussetzung: KeePass 2.x mit KeePassRPC-Plugin läuft auf demselben Rechner, die Datenbank ist entsperrt
und enthält einen Testeintrag mit Benutzername und Passwort; ein Display, weil das Pairing über den
Comic-Dialog der Anwendung läuft (KeePass zeigt das Einmal-Passwort erst an, wenn sich der Client meldet,
deshalb kann es nicht vorab übergeben werden).

```bash
./gradlew :integration-tests:liveTest -Dlive.stage=6 -Dlive.keepass.entry="Titel des Testeintrags" \
  [-Dlive.keepass.port=12546] [-Dlive.keepass.host=127.0.0.1] [-Dlive.keepass.origin=…] \
  [-Dlive.keepass.clientId=EnterpriseAiClient] [-Dlive.keepass.pairingKeyFile=…]
```

Ablauf: Der Test verbindet sich, KeePass zeigt das Einmal-Passwort, der Dialog „KeePass-Pairing“ öffnet sich,
das Passwort wird eingegeben. Der Pairing-Schlüssel wird wie in der Anwendung in einer Datei mit Rechten nur
für den Besitzer abgelegt (Standard `integration-tests/build/live/keepassrpc-pairing.key`), damit Stufe 7 ohne
neues Pairing läuft. Wer die Anwendung schon gepairt hat, kann mit `-Dlive.keepass.pairingKeyFile` auf deren
Schlüsseldatei zeigen (gleicher `clientId`). Nach der Verifikation die Datei löschen oder das Pairing in
KeePass widerrufen. Ohne Display (`-Dlive.headless=true`) und ohne Schlüsseldatei wird der Test übersprungen.

Rückmeldung: die `[live] Stufe 6:`-Zeilen (Pairing neu oder wiederverwendet, Benutzername vorhanden, Länge des
Passwortfelds, zweite Auflösung ohne neues Pairing). Werte werden nie ausgegeben.

## Stufe 7 – Confluence über KeePass

Voraussetzung: Stufe 6 ist gelaufen (Schlüsseldatei vorhanden) oder ein Display für ein neues Pairing; ein
KeePass-Eintrag mit den Confluence-Zugangsdaten (Benutzername und Passwort → `Basic`; nur Passwortfeld →
`Bearer`/Personal Access Token, bisher UNVERIFIED); ein Confluence Data Center, erreichbar über `https`
(`http` nur mit `-Dlive.confluence.allowInsecureHttp=true`).

```bash
./gradlew :integration-tests:liveTest -Dlive.stage=7 \
  -Dlive.confluence.baseUrl=https://confluence.intern.example/confluence \
  -Dlive.confluence.startPoint=space:DEV -Dlive.confluence.credentialRef="Confluence" \
  [-Dlive.confluence.searchSpaceKey=DEV] [-Dlive.confluence.query=Suchwort] \
  [-Dlive.confluence.maxDepth=0] [-Dlive.confluence.maxResources=20] [-Dlive.keepass.pairingKeyFile=…]
```

Zwei Tests: `startPointIsDiscoveredAndLoadedWithKeePassCredentials` (Zugangsdaten je Port-Aufruf aus KeePass,
discover und load am Startpunkt) und `searchOfConfluenceFindsPages` (CQL-Suche; ohne `live.confluence.query`
nach dem Titel der ersten gefundenen Seite).

Rückmeldung: die `[live] Stufe 7:`-Zeilen (Autorisierungsart, Pairing-Rückfragen, Ressourcen, Zeichen,
Revision, Treffer der Suche).

## Ergebnisprotokoll

| Stufe | Stand | Datum | Befund |
|---|---|---|---|
| 1 Chat | offen | – | – |
| 2 Embeddings, ein Text | offen | – | – |
| 3 Embeddings, Array | offen | – | – |
| 4 Embedding-Dimension | offen | – | – |
| 5 MediaWiki | offen | – | – |
| 6 KeePassRPC | offen | – | – |
| 7 Confluence über KeePass | offen | – | – |

Befunde werden hier nur als Verhalten festgehalten (Status, Form, Dimension), nie mit Hostnamen, Tokens oder
Inhalten. Entscheidungen, die aus einem Befund folgen (Array-Modus als Standard, Dimension festschreiben),
trifft der Auftraggeber; die Doku nennt bis dahin Befund und Empfehlung.
