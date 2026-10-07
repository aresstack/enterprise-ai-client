# Modulübersicht

Alle Module liegen unter dem Basispaket `com.aresstack.enterpriseai` und sind in `settings.gradle` und in der
`ModuleRegistry` der Architekturtests eingetragen (beides wird gegeneinander geprüft). Jedes Modul hat genau
ein Basispaket; Klassen eines Moduls liegen nur dort. Die Spalte "darf sehen" nennt die erlaubten
Produktionsabhängigkeiten auf andere Module; alles andere ist verboten und wird von
`ClassBoundaryTest` und `BuildModelTest` rot. Siehe auch [Architektur](architektur.md).

## Kern

| Modul | Rolle | Paket | darf sehen | Inhalt |
|---|---|---|---|---|
| `domain` | DOMAIN | `domain` | nichts | Reine Fachobjekte, nur JDK. Unterpakete je Fähigkeit: `domain.chat` (`ChatRole`, `ChatMessage`, `ChatConversation`, `ChatRequest`, `ChatResponse`, `ChatOptions`, `ChatUsage`, `ChatFinishReason`), `domain.embedding` (`EmbeddingVector`, `EmbeddingModelIdentity` mit SHA-256-Fingerprint, `EmbeddingWorldMismatchException`), `domain.knowledge` (`KnowledgeResource`, `KnowledgeResourceId`, `KnowledgeDocument`, `KnowledgeChunk`, `KnowledgeChunker`, `KnowledgeChunkingPolicy`, `KnowledgeRevision`, `KnowledgeSourceId`, `KnowledgeTokenCounter`, `SentenceSplitter`), `domain.security` (`SecretRef`). `domain.source` ist als Paket angelegt, enthält aber derzeit nur die Paketbeschreibung. |
| `application` | APPLICATION | `application` | domain, alle `*-api` | Use Cases und Orchestrierung: `application.chat` (`ChatService`, `ChatTurn`, `ChatTurnListener`), `application.rag` (`RetrieveKnowledgeUseCase`, `PromptContextAssembler`, `RagChatUseCase`, `ReciprocalRankFusion`, `RetrievalSettings`, `ContextSettings`), `application.knowledge` (`IndexKnowledgeUseCase`, `IndexingReport`, `KnowledgeSourceCatalog`, `LoadKnowledgeDocumentUseCase`, `RefreshKnowledgeSourceUseCase`), `application.mcp` (`KnowledgeMcpTools` mit `search_knowledge`, `get_knowledge_document`, `refresh_knowledge_source`), `application.agent` (`AgentService`, `AgentLauncher`). Kennt nur Ports, keinen Adapter. |

## Ports (`*-api`) und Adapter

| Modul | Rolle | Paket | darf sehen | Inhalt | Bibliotheken |
|---|---|---|---|---|---|
| `chat-api` | PORT | `chat.api` | domain | `ChatCompletionPort` (blockierend und streamend), `ChatStreamListener`, `ChatTask` (Cancel), `ChatCompletionException`, `ChatErrorKind`. Testfixture `chat.api.fake.FakeChatCompletionPort`. | keine |
| `chat-openai` | ADAPTER | `chat.openai` | domain, chat-api | `OpenAiCompatibleChatAdapter` für `POST <baseUrl>/chat/completions`, `OpenAiCompatibleChatConfig` (Builder, `TokenSource` je Anfrage), `DeveloperRolePolicy`. JSON, SSE-Parser und HTTP (`HttpURLConnection`) paketintern. | Gson |
| `embedding-api` | PORT | `embedding.api` | domain | `EmbeddingPort`, `EmbeddingBatch`, `EmbeddingInputs`, `EmbeddingException`, `EmbeddingFailureKind`. Testfixtures `embedding.api.testing.DeterministicEmbeddingPort` und `EmbeddingPortContractTest`. | keine |
| `embedding-openai` | ADAPTER | `embedding.openai` | domain, embedding-api | `OpenAiCompatibleEmbeddingAdapter` für `POST <baseUrl>/embeddings`, `OpenAiCompatibleEmbeddingConfiguration`, `EmbeddingInputMode` (`SINGLE_STRING`, `ARRAY_UNVERIFIED`), `BearerTokenSource`; Transport-Naht `EmbeddingHttpTransport`. | Gson |
| `knowledge-api` | PORT | `knowledge.api` | domain | `KnowledgeIndexPort` (index, replace, searchKeyword, searchSemantic, resourceIds, revisionOf, remove, removeSource, rebuild), `KnowledgeIndexEntry`, `KnowledgeSearchHit`, `KnowledgeKeywordQuery`, `KnowledgeSemanticQuery`, `QueryLimits`. Testfixtures `knowledge.api.testing.InMemoryKnowledgeIndex` (TF-IDF plus Cosine) und `KnowledgeIndexPortContractTest`. | keine |
| `knowledge-lucene` | ADAPTER | `knowledge.lucene` | domain, knowledge-api | `LuceneKnowledgeIndex(Path)` als einziger öffentlicher Typ: Lucene-BM25 unter `text/<fingerprint>/` und exakter Cosinus über `vectors/<fingerprint>.vec` je Embedding-Namespace. | Lucene 8.11.3 |
| `source-api` | PORT | `source.api` | domain | `KnowledgeSourcePort` (`discover(SourceScope)`, `load(id)`, `discoverLinks(id)`), optional `SearchableKnowledgeSource.search(SourceQuery)`, `SourceScope`, `SourceLink`, `SourceSearchHit`, `KnowledgeSourceException` (NOT_FOUND, UNAVAILABLE, ACCESS_DENIED, INVALID_RESPONSE, UNSUPPORTED). Testfixtures `source.api.testing.InMemoryKnowledgeSource` und `KnowledgeSourceContractTest`. | keine |
| `source-mediawiki` | ADAPTER | `source.mediawiki` | domain, source-api | `MediaWikiKnowledgeSource`, `MediaWikiSiteConfig`, `MediaWikiCredentials`, `MediaWikiCredentialsProvider` (Callback). Action-API-Client, Crawler und HTML-Bereinigung paketintern. Ressourcen-IDs `wiki:<siteKey>/<Titel>`. | Gson, jsoup |
| `source-confluence` | ADAPTER | `source.confluence` | domain, source-api, security-api | `ConfluenceKnowledgeSource`, `ConfluenceConfig`, Transport-Naht `ConfluenceHttpTransport` mit `UrlConnectionConfluenceTransport`, `ClientCertificates` (mTLS). Zugangsdaten nur über `SecretProvider`. IDs `confluence:<sourceId>/page/<id>`. | Gson, jsoup |
| `security-api` | PORT | `security.api` | domain | `SecretProvider` (`resolve`, `withSecret`), `SecretMaterial` (`char[]`, `close()` löscht), `SecretFunction`, `SecretUnavailableException` mit `Reason`. | keine |
| `security-keepassrpc` | ADAPTER | `security.keepassrpc` | domain, security-api | `KeePassRpcSecretProvider`, `KeePassRpcConfig`, Nähte `KeePassPairingCallback` und `KeePassPairingKeyStore` (`InMemoryPairingKeyStore`). SRP-Pairing, verschlüsseltes JSON-RPC und WebSocket paketintern. | Java-WebSocket, Gson |
| `acp-client-api` | PORT | `acp.api` | domain (nicht deklariert) | Neutrale ACP-Verträge: `AcpAgentConnector`, `AgentLaunchSpec`, `AgentProcessHandle`, `AcpConnection`, `AcpSession`, `PromptHandle`, `AcpUpdate`, `AcpUpdateListener`, `AcpEndpointDescriptor`, `AcpStates`, `PromptDispatcher`, `Redaction`. | keine |
| `acp-solon-client` | ADAPTER | `acp.solon` | domain, acp-client-api | `SolonAcpAgentConnector`: startet den Agentenprozess und spricht ACP über STDIO mit `org.noear:acp-sdk`. | acp-sdk 3.10.1 |
| `acp-demo-agent` | TEST_FIXTURE | `acp.demo` | nichts (nur Bibliotheken) | `DemoAcpAgentMain`: externer Demo-Agent als Fat-Jar (`acp-demo-agent-all.jar`) für Roundtrip-Tests und die Agent-Demo. Kein Modul darf davon abhängen. | acp-sdk, slf4j-nop |
| `mcp-runtime-api` | PORT | `mcp.api` | domain (nicht deklariert) | `McpServerRegistry`, `McpEndpointDefinition`, `McpEndpointHandle`, `McpToolContribution`, `McpToolHandler`, `McpToolParameter`, `McpToolType`, `McpToolCall`, `McpToolResult`, `McpToolClient`, `McpToolClientFactory`, `McpToolCallException`. Testfixtures `mcp.api.testkit.InProcessMcpServerRegistry`, `InProcessMcpToolClientFactory`, `McpTestTools`, `McpServerRegistryContractTest`. | keine |
| `mcp-solon-runtime` | ADAPTER | `mcp.solon` | domain, mcp-runtime-api | `SolonMcpServerRuntime` (MCP Streamable HTTP nur auf `127.0.0.1`, Pfad `/mcp/<id>/<token>`), `SolonMcpToolClientFactory`. | solon, solon-boot-jdkhttp, solon-ai-mcp 3.10.1 |

## Oberfläche und Composition Root

| Modul | Rolle | Paket | darf sehen | Inhalt |
|---|---|---|---|---|
| `comic-controls` | UI_LIBRARY | `ui.comic` | nichts | Abhängigkeitsfreie Swing/Java2D-Bibliothek im Comic-Stil aus askai-java8: `theme` (`ComicPalette`, `ComicTheme`), `paint` (`ComicImpactPainter`), `border` (`ComicBorder`), `control` (`ComicButton`, `ComicToggleButton`, `ComicSectionPanel`, `ComicScrollPane`, `ComicScrollBarUI`, `PlaceholderTextArea`), `bubble` (`SpeechBubblePanel`, `BubbleMessageRow`, `BubblePalette`, `StreamingTextMeasure`). |
| `app-swing` | COMPOSITION_ROOT | `app` | alles außer acp-demo-agent und architecture-tests | Einziger Ort, an dem Konfiguration gelesen und Adapter gebaut werden. `EnterpriseAiClientMain`; `app.config` (Snapshots und `AppConfigLoader`, `AppPaths`); `app.composition` (`AdapterAssembly`, `ApplicationPorts`, `CompositionRoot`, `ShellAssembly`, `ShutdownSequence`, `StartupNotices`); `app.security` (Brücken zum Security-Port: `SecretBackedTokenSource`, `SecretBackedBearerTokenSource`, `SecretBackedMediaWikiCredentialsProvider`, `FilePairingKeyStore`, `SwingPairingCallback`, `ClientCertificateFactory`, `UnavailableSecretProvider`); `app.net` (`ProxyPolicy`); `app.chat` (`RagChatBinding`, `KnowledgeIndexingBinding`, `ChatServiceBinding`); `app.agent` (`AcpAgentLauncher`, `AgentMcpEnvironment`, `AgentModeAssembly`, `AgentServiceBinding`); `app.knowledge` (`StartupIndexing`); `app.ui.chat` (Shell: `ChatShellPanel`, `ChatShellModel`, `ChatTranscriptPanel`, `ChatComposerPanel`, `SourceListPanel`, `KnowledgeStatusBar`, `ChatWindow`); `app.ui.agent` (`ModalShellPanel`, `ModeSwitchBar`); `app.ui.security` (`KeePassPairingDialog`). |
| `architecture-tests` | ARCHITECTURE_TESTS | `architecture` (nur Tests) | liest kompilierte Klassen aller Module | `ModuleRegistry`, Regelklassen (`*Rules`), Tests je Regelgruppe, Gegenbeispiele unter `<Modulpaket>.archfixture..`, Bibliotheks-Stubs unter `<lib>.archstub`. Regel-Liste: [Architektur](architektur.md#regeln). |

## Abweichungen von der Modulliste des Auftrags

Zwei zusätzliche Module, beide ohne Aufweichung der fachlichen Grenzen (Begründung in ARCHITECTURE.md):

- `acp-demo-agent`: Der Demo-Agent läuft als eigener Kindprozess und ist Testfixture. Die Roundtrip-Tests in
  `acp-solon-client` und `app-swing` starten nur sein gebautes Jar (`:acp-demo-agent:demoAgentJar`), ohne
  Projektabhängigkeit.
- `comic-controls`: Die generischen Comic-Komponenten bleiben eine eigenständige Bibliothek; die Chat-Shell
  selbst lebt in `app-swing`.

`acp-client-api` und `mcp-runtime-api` dürfen `domain` sehen, deklarieren die Abhängigkeit aber nicht: Die aus
askai-java8 übernommenen Verträge sind bewusst eigenständig.

## Testfixtures

Fixtures liegen im Source-Set `testFixtures` eines Port-Moduls und werden mit
`testImplementation testFixtures(project(':<modul>'))` eingebunden. Produktionscode darf sie nicht sehen
(`TestCodeIsolationTest`).

| Fixture | Modul | Zweck |
|---|---|---|
| `FakeChatCompletionPort` | chat-api | skriptbare Antworten, Streaming und Fehler für UI- und Use-Case-Tests |
| `DeterministicEmbeddingPort`, `EmbeddingPortContractTest` | embedding-api | reproduzierbare Vektoren ohne Netz; Vertragstest für Adapter |
| `InMemoryKnowledgeIndex`, `KnowledgeIndexPortContractTest`, `KnowledgeIndexTestData` | knowledge-api | Index im Speicher (TF-IDF plus Cosine); Vertragstest, den `LuceneKnowledgeIndex` erbt |
| `InMemoryKnowledgeSource`, `KnowledgeSourceContractTest` | source-api | Fake-Quelle mit `update`/`remove`; Vertragstest, den MediaWiki- und Confluence-Adapter erben |
| `InProcessMcpServerRegistry`, `InProcessMcpToolClientFactory`, `McpTestTools`, `McpServerRegistryContractTest` | mcp-runtime-api | MCP ohne Netzwerk; Vertragstest, den `SolonMcpServerRuntime` erbt |

## Externe Bibliotheken

Versionen stehen zentral in `gradle/libs.versions.toml`; aktiviert wird eine Bibliothek nur im `build.gradle`
des Moduls, das sie braucht, und immer mit `implementation` (nie `api`), damit kein Bibliothekstyp über den
Klassenpfad nach außen sickert.

| Bibliothek | Version | Module |
|---|---|---|
| JUnit | 4.13.2 | alle Tests |
| ArchUnit | 1.4.1 | architecture-tests |
| Lucene (core, analyzers-common, queryparser) | 8.11.3 | knowledge-lucene |
| org.noear acp-sdk | 3.10.1 | acp-solon-client, acp-demo-agent |
| org.noear solon, solon-boot-jdkhttp, solon-ai-mcp | 3.10.1 | mcp-solon-runtime |
| Gson | 2.10.1 | chat-openai, embedding-openai, source-mediawiki, source-confluence, security-keepassrpc; app-swing nur im Test |
| jsoup | 1.17.2 | source-mediawiki, source-confluence |
| Java-WebSocket | 1.5.2 | security-keepassrpc |
| slf4j-nop | 2.0.17 | acp-demo-agent (runtime), security-keepassrpc (Test-Runtime) |

Deklariert, aber in keinem Modul aktiviert: JWBF 3.1.1 und OkHttp 4.9.3 (der MediaWiki-Adapter spricht die
Action-API ohne JWBF, HTTP läuft überall über `HttpURLConnection` aus dem JDK).
