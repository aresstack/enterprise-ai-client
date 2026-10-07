# Testanleitung

Stand: `main` nach AP23 und AP24 (vor AP25). Alle Tests sind JUnit 4; es gibt keinen Testcode, der ein echtes
Backend, ein echtes Wiki, Confluence oder KeePass braucht, mit Ausnahme der unten genannten, standardmäßig
übersprungenen Läufe. Der Build ist auf JDK 8 und JDK 21 grün (CI); lokal reicht ein JDK 8 oder neuer.

## Befehle

```bash
./gradlew build                                       # alles: kompilieren, Tests, Architekturtests
./gradlew test                                        # nur Tests aller Module
./gradlew :application:test                           # ein Modul
./gradlew :chat-openai:test --tests '*OpenAiCompatibleChatAdapterTest'   # eine Klasse
./gradlew :architecture-tests:test                    # Architekturregeln (hängt an testClasses aller Module)
./gradlew build --no-daemon --warning-mode all        # wie die CI
```

Testberichte liegen je Modul unter `<modul>/build/reports/tests/test/index.html`; die XML-Ergebnisse unter
`<modul>/build/test-results/test/`. Mit `--console=plain` zeigt Gradle fehlgeschlagene Tests im Terminal.

## Testumfang je Modul

Zahlen aus einem vollständigen lokalen Lauf auf JDK 21 (2026-10-07): 1017 Tests, 0 Fehler, 2 übersprungen.

| Modul | Tests | Schwerpunkt |
|---|---|---|
| `domain` | 84 | Chat-Modelle, Embedding-Identität und Vektoren, Chunker, Ressourcen-IDs, Revision |
| `application` | 189 | `ChatService` (Historie, Abbruch, Fehler), Indexierung (Report, Skip, Prune), Retrieval (RRF, Degradation), Kontextaufbau, `RagChatUseCase`, `AgentService`, MCP-Werkzeuge (Roundtrip über In-Process-Registry), Vertragstests gegen die Fixtures |
| `chat-openai` | 42 | `OpenAiCompatibleChatAdapter` gegen einen lokalen Fake-HTTP-Server: Request-Form, Rollenregel, SSE-Normalisierung, Fehler- und Abbruchfälle |
| `embedding-api` | 16 | Port-Vertragstest gegen `DeterministicEmbeddingPort` |
| `embedding-openai` | 47 | Adapter gegen Fake-Server, Single-String- und Array-Modus, Dimension, `OpenAiCompatibleEmbeddingAdapterIT` (lokaler Fake, läuft im normalen Build) |
| `knowledge-api` | 22 | Port-Vertragstest gegen `InMemoryKnowledgeIndex`, Testdaten |
| `knowledge-lucene` | 29 | `LuceneKnowledgeIndex`: BM25, Cosinus, Namespaces, Rollback, `rebuild`, Sperre je Verzeichnis, derselbe Vertragstest wie die In-Memory-Referenz |
| `source-api` | 23 | Vertragstest gegen `InMemoryKnowledgeSource`, Ausnahmearten |
| `source-mediawiki` | 59 | Konfiguration, Fake-Transport, HTML-Bereinigung, Crawl, Login, HTTP-Integrationstest gegen lokalen Server, `SourceBoundaryTest` |
| `source-confluence` | 72 | `FakeConfluence`, Paging, Fehlerabbildung, mTLS-Fabrik, Transport gegen lokalen Server |
| `security-api` | 13 | `SecretRef`, `SecretMaterial`, `SecretProvider`-Vertrag |
| `security-keepassrpc` | 44 (1 übersprungen) | Pairing, verschlüsseltes JSON-RPC, Fehlerabbildung gegen `FakeKeePassRpcServer`; `KeePassRpcRealServerIT` nur mit Schalter |
| `acp-client-api` | 31 | Zustandsautomaten, `PromptDispatcher`, `Redaction`, `AgentLaunchSpec` |
| `acp-solon-client` | 12 | Connector gegen Demo-Agent (`SolonAcpRoundTripTest`), Abbruch, Prozessende |
| `mcp-runtime-api` | 29 | Vertragstest `McpServerRegistryContractTest` gegen `InProcessMcpServerRegistry`, Tool-Namensregel, Handle-Prüfung |
| `mcp-solon-runtime` | 29 | derselbe Vertragstest gegen den Solon-Server, Roundtrip `ping`/`echo`, falscher Token → 404, Shutdown |
| `comic-controls` | 10 | Zeichnen und Zustände der Comic-Komponenten (headless) |
| `app-swing` | 115 (1 übersprungen) | `ChatShellModel`, Bindings, Konfigurationslader, `ProxyPolicy`, `ApplicationCompositionTest` (headless Komposition mit Fakes), `RagShellIntegrationTest` (echte Adapter gegen Fake-HTTP-Server und Lucene-Temp-Index), `AgentModeRoundTripTest` (Demo-Agent), Pairing-Dialog |
| `architecture-tests` | 151 | Registry-Konsistenz, Schichtregeln, Bytecode 52, verbotene Importe, Konstantenpool, Secret-Grenze, RAG- und Agent-Grenzen; Gegenbeispiele unter `*.archfixture` |
| `acp-demo-agent` | 0 | Testfixture-Prozess, wird von anderen Modulen gestartet |

## Fixtures und Vertragstests

Jedes Port-Modul liefert über `java-test-fixtures` eine In-Memory-Referenz und einen abstrakten Vertragstest,
den jeder Adapter erbt. Produktionscode darf diese Pakete (`testing`, `testkit`, `fake`, `archfixture`) nicht
kennen (`TestCodeIsolationTest`).

| Modul | Fixture | Vertragstest |
|---|---|---|
| `chat-api` | `chat.api.fake.FakeChatCompletionPort` | (Verhalten in `application`-Tests) |
| `embedding-api` | `embedding.api.testing.DeterministicEmbeddingPort` | `EmbeddingPortContractTest` |
| `knowledge-api` | `knowledge.api.testing.InMemoryKnowledgeIndex`, `KnowledgeIndexTestData` | `KnowledgeIndexPortContractTest` |
| `source-api` | `source.api.testing.InMemoryKnowledgeSource` | `KnowledgeSourceContractTest` |
| `mcp-runtime-api` | `mcp.api.testkit.InProcessMcpServerRegistry`, `InProcessMcpToolClientFactory`, `McpTestTools` | `McpServerRegistryContractTest` |

Externe Dienste werden in Tests durch lokale Server ersetzt: `FakeKeePassRpcServer` (WebSocket),
Fake-HTTP-Server für Chat und Embeddings (`com.sun.net.httpserver`), `FakeMediaWikiTransport` und ein lokaler
MediaWiki-HTTP-Server, `FakeConfluence`. Der Demo-Agent (`acp-demo-agent-all.jar`) ist ein echter
Kindprozess.

## Besonderheiten

- **Eigene JVM je Testklasse** (`forkEvery = 1`) in `mcp-solon-runtime` und `app-swing`, weil Solon
  prozessglobal ist. Diese Module brauchen länger als die anderen.
- **Demo-Agent**: Die Gradle-Tests von `acp-solon-client` und `app-swing` hängen am Task
  `:acp-demo-agent:demoAgentJar` und setzen `acp.demo.agent.jar`; mit `acp.roundtrip.required=true` dürfen
  die Roundtrip-Tests nicht übersprungen werden. In einer IDE ohne diese Properties werden sie übersprungen;
  dann vorher `./gradlew :acp-demo-agent:demoAgentJar` bauen und die Properties setzen.
- **Headless**: Alle Swing-Tests laufen headless. `SwingPairingCallbackTest.promptRunsOnTheEventDispatchThreadWhenADisplayExists`
  wird ohne Display übersprungen (einer der zwei Skips).
- **Zeitabhängige Tests** (Streaming-Bündelung, Abbruch während `slow`) arbeiten mit Latches außerhalb des
  Event-Dispatch-Threads; unter Last gab es zwei Flaky-Fixes (PR #23 Handshake per Semaphore, PR #26 Latches
  und gedrosselter Demo-Agent). Ein hängender Lauf in `app-swing` über fünf Minuten ist abzubrechen und neu zu
  starten; flaky Tests werden robust gemacht, nie abgeschwächt oder übersprungen.

## Umgebungsabhängige Läufe (nicht im normalen Build)

| Lauf | Aktivierung | Was er prüft |
|---|---|---|
| `KeePassRpcRealServerIT` (`security-keepassrpc`) | `-Dkeepassrpc.it=true -Dkeepassrpc.it.entry=<Titel>`, optional `keepassrpc.it.keyFile`, `host`, `port`, `origin` | Pairing und Lesen eines Eintrags aus einem echten, entsperrten KeePass mit KeePassRPC; gibt nur aus, ob Felder nicht leer sind |

Für die echte Enterprise-API, ein echtes Wiki und ein echtes Confluence gibt es keine Integrationstests; die
Adapter sind nur gegen lokale Fakes geprüft ([Einschränkungen](einschraenkungen.md)). AP25 (Vertical-Slice-
Tests A–G) ergänzt ein Modul `integration-tests` mit einem separaten, nicht standardmäßigen Lauf für
umgebungsabhängige Tests; dieser Abschnitt wird nach dem Merge von AP25 erweitert.

## Architekturtests

`architecture-tests` prüft die Bytecode-Ausgabe aller Module (ArchUnit 1.4.1) und das Build-Modell gegen die
`ModuleRegistry`. Ein neues Modul muss in `settings.gradle` **und** in der Registry stehen; ein Kompilierfehler
in irgendeinem Testcode macht den Lauf rot. Die Regeln und ihre Testklassen stehen in
[Architektur](architektur.md#regeln); die vollständige Liste (56 Regeln, 8 davon nicht
automatisierbar) in [ARCHITECTURE.md](../ARCHITECTURE.md#architekturtests-architecture-tests).

## Demos statt Tests

Für manuelle Prüfung ohne Backend: `./gradlew :app-swing:runChatDemo`, `runRagDemo` und `runAgentDemo`
starten die Shell mit Fake-Ports (siehe [Einrichtung](einrichtung.md#demos-ohne-backend)).

## Continuous Integration

`.github/workflows/build.yml` führt `./gradlew build --no-daemon --warning-mode all` auf JDK 8 und JDK 21 aus
(Temurin). Pull Requests werden erst gemergt, wenn beide Läufe grün sind; vor dem Merge wird `main`
eingemergt, besonders nach Vertragsänderungen oder neuen Architekturregeln.
