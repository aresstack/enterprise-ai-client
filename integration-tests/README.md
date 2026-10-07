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
Jeder Test wird übersprungen (nicht rot), wenn seine Parameter fehlen. Adressen und Namen kommen als
`-Dlive.*`, Secrets ausschließlich aus Umgebungsvariablen; nichts davon wird ausgegeben. Modellnamen sind
Konfiguration (bekannt: `openai/gpt-oss-120b` für Chat, `danielheinz/e5-base-sts-en-de` für Embeddings).

| Test | Parameter | Secret |
|---|---|---|
| `LiveChatCompletionsIT` | `-Dlive.chat.baseUrl=https://…/v1 -Dlive.chat.model=…` | `ENTERPRISE_AI_LIVE_API_KEY` |
| `LiveEmbeddingsIT` | `-Dlive.embedding.model=… -Dlive.embedding.dimension=…` (optional `-Dlive.embedding.baseUrl=…`, sonst Chat-URL; `-Dlive.embedding.probeArray=true` für die Array-Probe) | `ENTERPRISE_AI_LIVE_API_KEY` |
| `LiveMediaWikiIT` | `-Dlive.wiki.apiUrl=https://…/w -Dlive.wiki.startPoint=Titel` (optional `-Dlive.wiki.siteKey=… -Dlive.wiki.user=…`) | `ENTERPRISE_AI_LIVE_WIKI_PASSWORD` (nur mit Benutzer) |
| `LiveKeePassIT` | `-Dlive.keepass.entry=Titel` (optional `-Dlive.keepass.port=12546`) | `ENTERPRISE_AI_LIVE_KEEPASS_PAIRING` (Einmal-Passwort aus KeePass; Schlüssel nur im Speicher) |
| `LiveConfluenceKeePassIT` | `-Dlive.confluence.baseUrl=https://…/wiki -Dlive.confluence.startPoint=space:KEY -Dlive.confluence.credentialRef=KeePass-Titel` (optional `-Dlive.confluence.allowInsecureHttp=true`) | wie `LiveKeePassIT` |

Beispiel:

```bash
export ENTERPRISE_AI_LIVE_API_KEY='…'
./gradlew :integration-tests:liveTest \
  -Dlive.chat.baseUrl=https://ki.example.internal/v1 -Dlive.chat.model=openai/gpt-oss-120b \
  -Dlive.embedding.model=danielheinz/e5-base-sts-en-de -Dlive.embedding.dimension=768
```

Proxys: die Adapter nutzen `java.net`-Verbindungen; ein Unternehmensproxy wird über die üblichen
JVM-Properties wirksam. `liveTest` reicht neben `-Dlive.*` auch `-Dhttp.proxyHost`, `-Dhttp.proxyPort`,
`-Dhttps.proxyHost`, `-Dhttps.proxyPort` und `-Dhttp.nonProxyHosts` an die Test-JVM durch; alternativ
wirkt `JAVA_TOOL_OPTIONS` auf alle JVMs. Nichts davon ist gegen die echte Enterprise-API, ein echtes Wiki,
Confluence oder KeePass gelaufen (UNVERIFIED, siehe Bericht AP25).
