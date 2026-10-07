# integration-tests – Vertical-Slice-Tests A–G (AP25)

Dieses Modul enthält nur Testcode. Es sieht alle Module (einschließlich `app-swing`) und die Testfixtures
der Stränge und beweist Ende-zu-Ende, dass die Slices des Arbeitsauftrags zusammenspielen. Alle Gegenstellen
sind lokale Fakes auf `127.0.0.1`; ein getrennter Lauf gegen echte Dienste ist standardmäßig aus.

```bash
./gradlew :integration-tests:test       # Slices A–G gegen Fakes (Teil von ./gradlew build, CI auf JDK 8 und 21)
./gradlew :integration-tests:liveTest   # gegen echte Dienste, nur mit -Dlive.* und Umgebungsvariablen (unten)
```

## Slices und Tests

| Slice | Kette laut Auftrag | Test (dieses Modul) | Echte Bausteine | Fakes / Fixtures |
|---|---|---|---|---|
| A – Chat | Swing → Application → Chat-Port → OpenAI-kompatibles HTTP → Streaming → Bubble | `SliceAChatTest` | `ShellAssembly`, `CompositionRoot`, `RagChatBinding`, `ChatService`, `OpenAiCompatibleChatAdapter` | `FakeChatCompletionsServer` (app-swing, SSE) |
| B – Knowledge | Fake-Quelle → Chunking → Embedding → Lucene + Vektoren → hybrides Retrieval | `SliceBKnowledgeTest` | `KnowledgeChunker`, `IndexKnowledgeUseCase`, `OpenAiCompatibleEmbeddingAdapter`, `LuceneKnowledgeIndex`, `RetrieveKnowledgeUseCase` | `InMemoryKnowledgeSource` (source-api), `FakeEmbeddingsServer` (app-swing) |
| C – MediaWiki | MediaWiki-Adapter → Knowledge-Pipeline → Suche | `SliceCMediaWikiTest` | `MediaWikiKnowledgeSource` mit HTTP-Transport und Login, Pipeline wie B | `FakeMediaWikiServer` + `FakeMediaWikiTransport` (source-mediawiki), `DeterministicEmbeddingPort` |
| D – Confluence + KeePass | SecretRef → KeePass-Adapter → Confluence-Adapter → Knowledge-Pipeline | `SliceDConfluenceKeePassTest` | `KeePassRpcSecretProvider` (WebSocket, SRP-Pairing), `ConfluenceKnowledgeSource` mit `UrlConnectionConfluenceTransport`, Pipeline wie B | `FakeKeePassRpcServer` (security-keepassrpc), `FakeConfluenceServer` + `FakeConfluence` (source-confluence) |
| E – ACP | Host → ACP → Demo-Agent | `SliceEAcpTest` | `AgentService`, `AcpAgentLauncher`, `SolonAcpAgentConnector`, Demo-Agent als Kindprozess (`acp-demo-agent-all.jar`) | – |
| F – MCP | MCP-Client → MCP-Runtime → Knowledge-Tool → Use Case | `SliceFMcpTest` | `SolonMcpToolClientFactory`, `SolonMcpServerRuntime`, `KnowledgeMcpTools`, Use Cases, `LuceneKnowledgeIndex` | `InMemoryKnowledgeSource`, `DeterministicEmbeddingPort` |
| G – Agent + MCP | Chat-UI Agent-Modus → ACP-Agent → MCP `search_knowledge` → Wissensindex → Antwort | `SliceGAgentMcpTest` | `ShellAssembly`, `CompositionRoot`, `AgentService`, `AcpAgentLauncher`, `SolonAcpAgentConnector`, `SolonMcpServerRuntime`, `LuceneKnowledgeIndex`; Testagent `KnowledgeDemoAgentMain` (Testcode dieses Moduls) | `FakeChatCompletionPort`, `InMemoryKnowledgeSource`, `DeterministicEmbeddingPort` |
| A–D aus Konfiguration | Properties → `AdapterAssembly` → Startindexierung → RAG-Frage über die Shell | `ConfiguredApplicationSliceTest` | alle echten Adapter aus `AdapterAssembly`, API-Key und Zugangsdaten nur aus KeePass | alle Fakes oben |

Vorhandene Tests der Stränge decken Teilketten derselben Slices ab und bleiben die Spezifikation der Details:
AP9 `KnowledgeVerticalSliceTest` (B ohne HTTP), AP17 `SolonAcpRoundTripTest` (E auf Adapterebene),
AP19 `SolonMcpRoundTripTest` (F mit Test-Tools), AP20 `KnowledgeMcpToolsRoundTripTest` (F in-process),
AP21 `AgentModeRoundTripTest` (G-Verdrahtung mit dem Demo-Agenten, der kein MCP nutzt),
AP22 `RagShellIntegrationTest` (A + RAG ohne Composition Root), AP23 `ApplicationCompositionTest` (Graph mit Fakes).

## Entscheidungen

- **Fakes als Testfixtures.** `FakeChatCompletionsServer`/`FakeEmbeddingsServer` (app-swing),
  `FakeMediaWikiTransport` (source-mediawiki), `FakeConfluence` (source-confluence) und `FakeKeePassRpcServer`
  (security-keepassrpc) wurden unverändert in `src/testFixtures` der jeweiligen Module verschoben (Paket
  gleich, Sichtbarkeit `public`). Neu sind die HTTP-Hüllen `FakeMediaWikiServer` (JDK-HttpServer mit
  Sitzungs-Cookie) und `FakeConfluenceServer`, damit die echten HTTP-Transporte der Adapter geprüft werden.
  Testfixtures sind keine Produktionskonfiguration; die Architekturregeln (AP24) bleiben unberührt.
- **Slice G ohne Änderung an `acp-demo-agent`.** Der Demo-Agent darf kein Projektmodul sehen, kann also
  kein MCP sprechen. Der wissensnutzende Testagent `KnowledgeDemoAgentMain` ist Testcode dieses Moduls und
  läuft als eigener Kindprozess auf dem Test-Klassenpfad (über ein Pathing-Jar, `SliceSupport`). Er liest den
  Endpoint wie in der Produktion aus `ENTERPRISE_AI_MCP_*` (AP21) und spricht MCP nur über
  `McpToolClientFactory`/`SolonMcpToolClientFactory`. STDOUT trägt ausschließlich ACP (Transport zuerst, dann
  `System.setOut(System.err)`); URL und Token des Endpoints werden nie ausgegeben, auch nicht auf STDERR.
- **Solon ist prozessglobal**: jede Testklasse läuft in eigener JVM (`forkEvery 1`), Klassen mit MCP-Server
  stoppen ihn in `@AfterClass`.
- **Kein `sleep`-Polling auf dem EDT**: Warten über Latches an Model-Listenern (`SliceSupport.awaitIdle`),
  Fristen 60 s; Kindprozesse werden geprüft (lebt/tot), Tokens nach Shutdown gegen den Server geprüft.
- **Sicherheitsregeln des Nachtrags gelten auch hier**: jeder Slice prüft, dass Token und Passwörter weder
  in Sprechblasen, Transkripten, Berichten, Ausnahmen, `toString()` noch im mitgelesenen STDERR auftauchen.

## Live-Lauf gegen echte Dienste (standardmäßig aus, UNVERIFIED)

Der Task `liveTest` (Source-Set `src/liveTest/java`) ist nicht Teil von `check`; `check` kompiliert ihn nur.
Die Stufen 1 bis 5 lassen sich auch über den GitHub-Actions-Workflow „Live-Verifikation“ (`workflow_dispatch`,
Secrets und Variablen nur als Namen) starten, siehe [docs/live-verifikation.md](../docs/live-verifikation.md).
Er ist in sieben Stufen gegliedert (Reihenfolge des Auftraggebers), die `-Dlive.stage` auswählt: eine Stufe
(`-Dlive.stage=3`), eine Liste (`-Dlive.stage=2,3`) oder ein Bereich (`-Dlive.stage=1-4`); ohne Angabe laufen
alle. Jeder Test wird übersprungen (nicht rot), wenn seine Parameter fehlen. Adressen und Namen kommen als
`-Dlive.*`, Secrets ausschließlich aus Umgebungsvariablen; nichts davon wird ausgegeben. Jede Ausgabe beginnt
mit `[live] Stufe N:` und nennt nur Status, Codes, Anzahlen und Messwerte. Modellnamen sind Konfiguration
(bekannt: `openai/gpt-oss-120b` für Chat, `danielheinz/e5-base-sts-en-de` für Embeddings). Die Anleitung je
Stufe mit Voraussetzungen, Kommando und erwarteter Rückmeldung steht in
[docs/live-verifikation.md](../docs/live-verifikation.md).

| Stufe | Test | Parameter | Secret |
|---|---|---|---|
| 1 | `LiveChatCompletionsIT` (Streaming über `ChatService`, `complete()` ohne Streaming, Fehlerantwort auf ein unbekanntes Modell) | `-Dlive.chat.baseUrl=https://…/v1 -Dlive.chat.model=…` | `ENTERPRISE_AI_LIVE_API_KEY` |
| 2 | `LiveEmbeddingsIT.singleInputReturnsOneVectorPerText` (Adapter `SINGLE_STRING`, ein und drei Texte) | `-Dlive.embedding.model=…` (optional `-Dlive.embedding.baseUrl=…`, sonst Chat-URL; `-Dlive.embedding.dimension=…`, sonst aus der ersten Antwort übernommen) | `ENTERPRISE_AI_LIVE_API_KEY` |
| 3 | `LiveEmbeddingsIT.arrayInputProbe` (Roh-Probe mit `"input": [...]`, dann Adapter `ARRAY_UNVERIFIED` mit Teil-Batches, Reihenfolge gegen Einzelanfragen) | wie 2 | `ENTERPRISE_AI_LIVE_API_KEY` |
| 4 | `LiveEmbeddingDimensionIT` (Antwortform und tatsächliche Dimension, Zusatzbefund `encoding_format=float`) | wie 2 | `ENTERPRISE_AI_LIVE_API_KEY` |
| 5 | `LiveMediaWikiIT` (discover, load, Indexierung, Wiki-Suche) | `-Dlive.wiki.apiUrl=https://…/w -Dlive.wiki.startPoint=Titel` (optional `-Dlive.wiki.siteKey=… -Dlive.wiki.user=… -Dlive.wiki.maxDepth=0 -Dlive.wiki.maxResources=20`) | `ENTERPRISE_AI_LIVE_WIKI_PASSWORD` (nur mit Benutzer) |
| 6 | `LiveKeePassIT` (Pairing über den Dialog der Anwendung, Eintrag lesen, zweite Auflösung ohne Pairing) | `-Dlive.keepass.entry=Titel` (optional `-Dlive.keepass.port=12546 -Dlive.keepass.host=127.0.0.1 -Dlive.keepass.origin=… -Dlive.keepass.clientId=… -Dlive.keepass.pairingKeyFile=…`) | Einmal-Passwort im Dialog; `ENTERPRISE_AI_LIVE_KEEPASS_PAIRING` nur, wenn es vorab bekannt ist |
| 7 | `LiveConfluenceKeePassIT` (discover und load am Startpunkt mit Zugangsdaten aus KeePass, CQL-Suche) | `-Dlive.confluence.baseUrl=https://…/wiki -Dlive.confluence.startPoint=space:KEY -Dlive.confluence.credentialRef=KeePass-Titel` (optional `-Dlive.confluence.allowInsecureHttp=true -Dlive.confluence.searchSpaceKey=KEY -Dlive.confluence.query=… -Dlive.confluence.maxDepth=0 -Dlive.confluence.maxResources=20`) | wie 6 |

Beispiel (Stufen 1 bis 4):

```bash
export ENTERPRISE_AI_LIVE_API_KEY='…'
./gradlew :integration-tests:liveTest -Dlive.stage=1-4 \
  -Dlive.chat.baseUrl=https://ki.intern.example/v1 -Dlive.chat.model=openai/gpt-oss-120b \
  -Dlive.embedding.model=danielheinz/e5-base-sts-en-de
```

Entscheidungen des Live-Laufs:

- **Roh-Probe statt Adapter für Stufen 3 und 4**: `EmbeddingEndpointProbe` (Testcode in `src/test`, gegen
  `FakeEmbeddingsServer` unit-getestet) sendet mit den Headern des Adapters und *beschreibt* die Antwort, statt
  sie zu validieren. So lassen sich Dimension, `index`, Form von `embedding` und `usage` erfassen, bevor eine
  Dimension konfiguriert ist, und ein abgelehntes Array ist ein Befund in der Ausgabe, kein roter Test. Der
  Adapter bleibt die einzige produktive Implementierung und wird in den Stufen 2 und 3 ebenfalls geprüft.
- **Dimension nicht raten**: Fehlt `-Dlive.embedding.dimension`, übernehmen die Stufen 2 und 3 die Dimension aus
  der ersten echten Antwort und melden sie; Stufe 4 meldet sie als Empfehlung für `embedding.dimension` bzw.
  prüft einen gesetzten Wert.
- **KeePass-Pairing über den Dialog**: KeePassRPC zeigt das Einmal-Passwort erst, wenn sich der Client meldet,
  deshalb fragt Stufe 6 es mit `SwingPairingCallback`/`KeePassPairingDialog` der Anwendung ab (der Task
  `liveTest` läuft als einziger nicht headless; `-Dlive.headless=true` erzwingt headless) und legt den Schlüssel
  mit `FilePairingKeyStore` ab (Standard `build/live/keepassrpc-pairing.key`, Rechte nur für den Besitzer),
  damit Stufe 7 ihn wiederverwendet. Nach der Verifikation die Datei löschen oder das Pairing in KeePass
  widerrufen.
- **Kein Secret in Meldungen**: `LiveSettings.withoutSecretLeak` prüft jede Exception eines Live-Tests samt
  Ursachen auf die Secrets aus der Umgebung und ersetzt eine betroffene Meldung durch eine geschwärzte.

Proxys und Zertifikate: die Adapter nutzen `java.net`-Verbindungen; ein Unternehmensproxy wird über die üblichen
JVM-Properties wirksam. `liveTest` reicht neben `-Dlive.*` auch `-Dhttp.proxyHost`, `-Dhttp.proxyPort`,
`-Dhttps.proxyHost`, `-Dhttps.proxyPort`, `-Dhttp.nonProxyHosts`, `-Djavax.net.ssl.trustStore`,
`-Djavax.net.ssl.trustStoreType` und `-Djavax.net.ssl.trustStorePassword` an die Test-JVM durch; alternativ
wirkt `JAVA_TOOL_OPTIONS` auf alle JVMs. Der Verifikationsstand je Stufe steht im Ergebnisprotokoll von
[docs/live-verifikation.md](../docs/live-verifikation.md); bis dahin gilt alles als UNVERIFIED.
