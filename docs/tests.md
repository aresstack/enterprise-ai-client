# Testanleitung

Stand: `main` nach AP26, Unicode-Vertrag und Vorbereitung der Live-Verifikation (Modul `integration-tests`). Alle Tests sind JUnit 4; der normale Build braucht weder ein
echtes Backend noch ein echtes Wiki, Confluence oder KeePass. Läufe gegen echte Dienste sind getrennt und
standardmäßig aus (unten). Der Build ist auf JDK 8 und JDK 21 grün (CI); lokal reicht ein JDK 8 oder neuer.

## Befehle

```bash
./gradlew build                                       # alles: kompilieren, Tests, Architekturtests
./gradlew test                                        # nur Tests aller Module
./gradlew :application:test                           # ein Modul
./gradlew :chat-openai:test --tests '*OpenAiCompatibleChatAdapterTest'   # eine Klasse
./gradlew :architecture-tests:test                    # Architekturregeln (hängt an testClasses aller Module)
./gradlew :integration-tests:test                     # Vertical-Slice-Tests A–G gegen lokale Fakes (Teil von build)
./gradlew :integration-tests:liveTest -Dlive.stage=1 -Dlive.…   # gegen echte Dienste, eine Stufe, standardmäßig aus (siehe unten)
./gradlew build --no-daemon --warning-mode all        # wie die CI
./gradlew :app-swing:verifyFatJar :app-swing:smokeStartFatJar   # Fat-Jar-Prüfungen (Teil von check, siehe unten)
```

Testberichte liegen je Modul unter `<modul>/build/reports/tests/test/index.html`; die XML-Ergebnisse unter
`<modul>/build/test-results/test/`. Mit `--console=plain` zeigt Gradle fehlgeschlagene Tests im Terminal.

## Testumfang je Modul

Zahlen aus einem vollständigen lokalen Lauf auf JDK 21 (2026-10-09, Stand nach dem Einstellungen-Dialog und
der Netz-/TLS-Anpassung des Erststarts): 1164 Tests, 0 Fehler, 2 übersprungen.

| Modul | Tests | Schwerpunkt |
|---|---|---|
| `domain` | 100 | Chat-Modelle, Embedding-Identität und Vektoren, Chunker, Zeichenklassen-Vertrag `UnicodeClasses` (Tabelle per Hash eingefroren, gleiche Erwartungswerte auf JDK 8 und 21), Ressourcen-IDs, Revision |
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
| `comic-controls` | 31 | Zeichnen und Zustände der Comic-Komponenten (headless), Ports aus askai arch: `ComicSplitPaneTest`, `ComicHoverMenuTest`, `ComicSearchBarTest`, `ComicWindowCloseButtonTest`, `ComicWindowResizerTest`, `ComposerButtonTest`, `SpeechBubbleDetailsTest`, `ResearchUiTypographyTest` |
| `app-swing` | 239 (1 übersprungen) | `ChatShellModel`, Bindings, Konfigurationslader, `HttpRoutes`, `ApplicationCompositionTest` (headless Komposition mit Fakes, darunter `knowledgeToolsReadOnlyIndexedDocuments`), `RagShellIntegrationTest` (echte Adapter gegen Fake-HTTP-Server und Lucene-Temp-Index), `AgentModeRoundTripTest` (Demo-Agent), Pairing-Dialog; Netz/TLS/Protokoll: `TrustPolicyTest`, `PacProxyRoutesTest`, `ConnectionDiagnosisTest`, `AppLogFileTest`, `LoggingKnowledgeSourceTest`; Einstellungen-Dialog: `ConfigurationFileTest` (Zeilen ersetzen, auskommentierte aktivieren, anhängen, Escaping, CRLF), `SettingsMapperTest` (Formular ↔ Schlüssel, Vorlage lädt nach Rundreise, Quellen entfernen/Typ wechseln), `FileSettingsActionsTest` (Prüfen über den Loader, Speichern lässt fremde Schlüssel und Kommentare stehen), `ConfigurationStartupTest` (Erststart headless wie bisher, mit Oberfläche Dialog, Abbruch, fehlerhafte Datei, Prüfung über den Loader hinaus wie die TLS-Vertrauensregel), `SettingsPanelTest` (headless: Felder einschließlich Proxy AUTO/PAC/CA-Datei, Quellen-Editor, Probleme mit Reiterwahl, KeePass-Probe), `KeePassSecretCheckerTest` (Probe gegen `FakeKeePassRpcServer`: gefunden, leer, fehlt, nicht erreichbar, Pairing abgebrochen; Secret erscheint nie); Oberfläche nach askai arch: `ChatWorkspacePanelTest` (Modus-Pille, Drawer samt Schließen beim Verlassen des Fensters, Neuer Chat, Zahnrad, Chat-Zeilen, Wissensquellen), `ShellModeModelTest`, `SidebarTabRibbonTest`, `ChatSidebarPanelTest`, `ChatShellPanelTest` (Composer Senden/Stop, Fehlerblase mit Details, Leeren) |
| `architecture-tests` | 153 | Registry-Konsistenz, Schichtregeln, Bytecode 52, verbotene Importe, Konstantenpool, Secret-Grenze, RAG- und Agent-Grenzen, JDK-Zeichenklassen-Verbot in `domain.knowledge`; Gegenbeispiele unter `*.archfixture` |
| `integration-tests` | 39 | Vertical-Slice-Tests A–G und Konfigurations-Slice gegen lokale Fakes, Parser-Test des Testagenten, `EmbeddingEndpointProbeTest` (Roh-Probe der Live-Verifikation gegen `FakeEmbeddingsServer`), `FailureRedactionTest` (Schwärzung von Secrets und Hostnamen in Fehlerketten der Live-Tests); eigene JVM je Klasse (siehe unten) |
| `acp-demo-agent` | 0 | Testfixture-Prozess, wird von anderen Modulen gestartet |

## Fixtures und Vertragstests

Fünf Port-Module liefern über `java-test-fixtures` Testhilfen: vier davon eine In-Memory-Referenz plus einen
abstrakten Vertragstest, den jeder Adapter erbt; `chat-api` nur einen Fake-Port ohne Vertragstest.
`security-api` und `acp-client-api` haben keine Fixtures. Seit AP25 stellen außerdem vier Adapter- bzw.
App-Module ihre Fake-Server als Testfixtures bereit, damit `integration-tests` die echten HTTP- und
WebSocket-Transporte prüfen kann. Produktionscode darf Testfixtures nicht sehen (`TestCodeIsolationTest`);
`testFixturesImplementation` zählt nicht als Produktionsabhängigkeit.

| Modul | Fixture | Vertragstest |
|---|---|---|
| `chat-api` | `chat.api.fake.FakeChatCompletionPort` | (Verhalten in `application`-Tests) |
| `embedding-api` | `embedding.api.testing.DeterministicEmbeddingPort` | `EmbeddingPortContractTest` |
| `knowledge-api` | `knowledge.api.testing.InMemoryKnowledgeIndex`, `KnowledgeIndexTestData` | `KnowledgeIndexPortContractTest` |
| `source-api` | `source.api.testing.InMemoryKnowledgeSource` | `KnowledgeSourceContractTest` |
| `mcp-runtime-api` | `mcp.api.testkit.InProcessMcpServerRegistry`, `InProcessMcpToolClientFactory`, `McpTestTools` | `McpServerRegistryContractTest` |
| `app-swing` | `FakeChatCompletionsServer` (SSE), `FakeEmbeddingsServer` (JDK-HttpServer auf 127.0.0.1) | – |
| `source-mediawiki` | `FakeMediaWikiTransport`, `FakeMediaWikiServer` (HTTP mit Sitzungs-Cookie) | – |
| `source-confluence` | `FakeConfluence`, `FakeConfluenceServer` | – |
| `security-keepassrpc` | `FakeKeePassRpcServer` (WebSocket, SRP-Pairing) | – |

Alle Fakes binden an `127.0.0.1`. Der Demo-Agent (`acp-demo-agent-all.jar`) und der Testagent
`KnowledgeDemoAgentMain` (Testcode von `integration-tests`) sind echte Kindprozesse.

## Besonderheiten

- **Eigene JVM je Testklasse** (`forkEvery = 1`) in `mcp-solon-runtime`, `app-swing` und `integration-tests`,
  weil Solon prozessglobal ist. Diese Module brauchen länger als die anderen.
- **Demo-Agent**: Die Gradle-Tests von `acp-solon-client`, `app-swing` und `integration-tests` hängen am Task
  `:acp-demo-agent:demoAgentJar` und setzen `acp.demo.agent.jar`; mit `acp.roundtrip.required=true` dürfen
  die Roundtrip-Tests nicht übersprungen werden. In einer IDE ohne diese Properties werden sie übersprungen;
  dann vorher `./gradlew :acp-demo-agent:demoAgentJar` bauen und die Properties setzen.
- **Headless**: Alle Swing-Tests des normalen Builds laufen headless (`java.awt.headless=true`).
  `SwingPairingCallbackTest.promptRunsOnTheEventDispatchThreadWhenADisplayExists` wird ohne Display übersprungen
  (einer der zwei Skips). Einzige Ausnahme ist der Task `liveTest`: Stufe 6 pairt über den Swing-Dialog der
  Anwendung, dort entscheidet das JDK (ohne Display headless, der Test überspringt sich), `-Dlive.headless=true|false`
  überstimmt das.
- **Zeitabhängige Tests** (Streaming-Bündelung, Abbruch während `slow`) arbeiten mit Latches außerhalb des
  Event-Dispatch-Threads; unter Last gab es zwei Flaky-Fixes (PR #23 Handshake per Semaphore, PR #26 Latches
  und gedrosselter Demo-Agent). Ein hängender Lauf in `app-swing` über fünf Minuten ist abzubrechen und neu zu
  starten; flaky Tests werden robust gemacht, nie abgeschwächt oder übersprungen.

## Vertical-Slice-Tests A–G (`integration-tests`)

Das Modul `integration-tests` (AP25) enthält außer der Ankerklasse `IntegrationTestsModule` (für das Build-Modell
der Architekturtests) nur Testcode, sieht alle Module einschließlich `app-swing` und alle Testfixtures und beweist
Ende-zu-Ende, dass die Slices des Auftrags zusammenspielen. Es läuft im normalen
Build gegen lokale Fakes. Vollständige Zuordnung, Entscheidungen und Live-Parameter:
[integration-tests/README.md](../integration-tests/README.md).

| Slice | Test (`com.aresstack.enterpriseai.integration`) | Echte Bausteine | Fakes |
|---|---|---|---|
| A – Chat | `SliceAChatTest` | Shell und Composition Root, `RagChatBinding`, `ChatService`, `OpenAiCompatibleChatAdapter` | `FakeChatCompletionsServer` |
| B – Knowledge | `SliceBKnowledgeTest` | Chunker, Indexierung, `OpenAiCompatibleEmbeddingAdapter`, `LuceneKnowledgeIndex`, Retrieval | `InMemoryKnowledgeSource`, `FakeEmbeddingsServer` |
| C – MediaWiki | `SliceCMediaWikiTest` | `MediaWikiKnowledgeSource` mit HTTP-Transport und Login | `FakeMediaWikiServer`, `DeterministicEmbeddingPort` |
| D – Confluence + KeePass | `SliceDConfluenceKeePassTest` | `KeePassRpcSecretProvider` (Pairing), `ConfluenceKnowledgeSource` mit HTTP-Transport | `FakeKeePassRpcServer`, `FakeConfluenceServer` |
| E – ACP | `SliceEAcpTest` | `AgentService`, `AcpAgentLauncher`, `SolonAcpAgentConnector`, Demo-Agent als Kindprozess | – |
| F – MCP | `SliceFMcpTest` | `SolonMcpToolClientFactory`, `SolonMcpServerRuntime`, `KnowledgeMcpTools`, Lucene | `InMemoryKnowledgeSource`, `DeterministicEmbeddingPort` |
| G – Agent + MCP | `SliceGAgentMcpTest` | Shell im Agent-Modus, ACP, Solon-MCP-Server, Lucene, Testagent `KnowledgeDemoAgentMain` als Kindprozess | `FakeChatCompletionPort`, `InMemoryKnowledgeSource`, `DeterministicEmbeddingPort` |
| A–D aus Konfiguration | `ConfiguredApplicationSliceTest` | `AdapterAssembly` aus Properties, Startindexierung, RAG-Frage über die Shell | alle Fakes, Secrets nur aus Fake-KeePass |

Die Slices mit Secret prüfen außerdem, dass Token und Passwörter an den Stellen nicht auftauchen, die der jeweilige
Slice erzeugt (`SliceSupport.assertNoSecret`): Sprechblasen und Transkript (A, G, Konfiguration), Indexierungs-
berichte und Ergebnisse (B, C, D), `toString()` von Quelle, Ports und Konfiguration (D, Konfiguration),
Fehlermeldungen (A, B, C, D, F, G), der an das Modell gesendete Systemkontext (Konfiguration) und das
mitgelesene STDERR des Testagenten (G). Slice E hat kein Secret und prüft nur die Trennung von STDOUT und STDERR. Die älteren Tests der
Stränge bleiben die Spezifikation der Details (`KnowledgeVerticalSliceTest`, `SolonAcpRoundTripTest`,
`SolonMcpRoundTripTest`, `KnowledgeMcpToolsRoundTripTest`, `AgentModeRoundTripTest`, `RagShellIntegrationTest`,
`ApplicationCompositionTest`).

## Umgebungsabhängige Läufe (nicht im normalen Build)

| Lauf | Aktivierung | Was er prüft |
|---|---|---|
| `./gradlew :integration-tests:liveTest -Dlive.stage=N` (Source-Set `liveTest`, nicht Teil von `check`; `check` kompiliert ihn nur) | Stufe 1–7 über `-Dlive.stage` (auch `1-4` oder `2,3`; ohne Angabe alle), Adressen und Namen als `-Dlive.*`, Secrets nur aus Umgebungsvariablen (`ENTERPRISE_AI_LIVE_API_KEY`, `ENTERPRISE_AI_LIVE_WIKI_PASSWORD`); fehlende Parameter → Test übersprungen; Ausgabe nur als `[live] Stufe N:`-Zeilen ohne Secrets | Stufe 1 `LiveChatCompletionsIT` (echte `/chat/completions`, mit und ohne Streaming), 2 `LiveEmbeddingsIT.singleInput…` (Adapter), 3 `LiveEmbeddingsIT.arrayInput…` (Array-Probe und Adapter `ARRAY_UNVERIFIED`), 4 `LiveEmbeddingDimensionIT` (tatsächliche Dimension), 5 `LiveMediaWikiIT`, 6 `LiveKeePassIT` (Pairing über den Dialog der Anwendung, braucht ein Display), 7 `LiveConfluenceKeePassIT`; Anleitung je Stufe in [Live-Verifikation](live-verifikation.md), Parameter in [integration-tests/README.md](../integration-tests/README.md); Stufen 1–5 auch als manuell gestarteter GitHub-Actions-Workflow „Live-Verifikation“ (`workflow_dispatch`, Secrets und Variablen nur als Namen im Workflow) |
| `KeePassRpcRealServerIT` (`security-keepassrpc`) | `-Dkeepassrpc.it=true -Dkeepassrpc.it.entry=<Titel>`, optional `keepassrpc.it.keyFile`, `host`, `port`, `origin` | Pairing und Lesen eines Eintrags aus einem echten, entsperrten KeePass mit KeePassRPC; gibt nur aus, ob Felder nicht leer sind |

`liveTest` reicht neben `-Dlive.*` die üblichen Proxy-Properties (`http(s).proxyHost`, `http(s).proxyPort`,
`http.nonProxyHosts`) und den Truststore (`javax.net.ssl.trustStore`, `javax.net.ssl.trustStoreType`; ein Passwort nur
aus der Umgebungsvariablen `ENTERPRISE_AI_LIVE_TRUSTSTORE_PASSWORD`) an die Test-JVM durch. Welche Stufen
bereits gegen die echte Enterprise-API, ein echtes Wiki, Confluence oder KeePass gelaufen sind, steht im
Ergebnisprotokoll der [Live-Verifikation](live-verifikation.md); alles andere bleibt UNVERIFIED
([Einschränkungen](einschraenkungen.md)).

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

`build` baut seit dem Fat Jar auch `enterprise-ai-client-<version>.jar` (`:app-swing:fatJar`, an `assemble`) und
prüft es über `check` mit zwei Tasks aus `gradle/fat-jar.gradle`, die keine JUnit-Tests sind und deshalb nicht in
den Testzahlen oben erscheinen:

- `verifyFatJar`: Manifest-Attribute (`Main-Class`, `Implementation-Version`, `Git-Commit`, `Multi-Release`),
  keine Signaturdateien oder `module-info.class`, jeder Eintrag jedes Laufzeit-Jars ist enthalten, jede
  `META-INF/services`-Datei enthält die Einträge aller Einzeljars, und die Hauptklasse lässt sich aus dem Jar
  allein (ohne Klassenpfad des Builds, ohne Initialisierung) laden.
- `smokeStartFatJar`: startet das Jar in einer eigenen headless JVM mit leerem Anwendungsverzeichnis und erwartet
  das Verhalten des ersten Starts: Vorlage `enterprise-ai-client.properties` angelegt, Exit-Code 2. Es wird kein
  Fenster und kein Server gestartet.

`.github/workflows/release.yml` baut bei jedem Push auf jeden Branch mit JDK 8 (`./gradlew build`, also mit allen
Tests) und veröffentlicht das Jar als Release (`main`, Tag `v<version>`) oder als rollierenden Snapshot des
Branches (Pre-Release `snapshot-<branch>`); Einzelheiten in [Einrichtung](einrichtung.md#fat-jar-version-und-releases).
