# Herkunft der Bausteine

Der Client ist aus drei Referenz-Repositories zusammengeführt worden: **Miguel0888/MainframeMate** (erprobte
Implementierungen gegen die Enterprise-API, KeePassRPC, Wiki, Confluence), **aresstack/corenth**
(Architekturvorlage: Ports und Adapter, Composition Root, Architekturtests; ungetestet, `pinakes`
unvollständig) und **Miguel0888/askai-java8** (Comic-UI, Semantic Index, ACP, MCP). Diese Tabelle nennt je
übernommenem oder adaptiertem Teil die Quelle und das, was geändert wurde. Grundlage sind die Javadoc-Hinweise
im Code, die Berichte der Arbeitspakete und die Koordinator-Notizen; Unklares ist als **offen** markiert.
Die kurze Tabelle in [ARCHITECTURE.md](../ARCHITECTURE.md#herkunft) bleibt gültig; diese Seite ist die
ausführliche Fassung.

## Build und Architekturgerüst (AP1, AP24)

| Teil | Quelle | Geändert |
|---|---|---|
| Java-8-Multiprojekt, `--release 8` auf neueren JDKs, `FAIL_ON_PROJECT_REPOS`, `java-library`, Versionskatalog | corenth (`build.gradle`, `settings.gradle`) | Gradle 8.14.3, Katalog `gradle/libs.versions.toml`, CI auf JDK 8 und 21 |
| Architekturtest-Modul mit expliziter Modulliste, ArchUnit 1.4.1, Klassenverzeichnisse per Systemeigenschaft | corenth (`architecture-tests`) | in eine Java-`ModuleRegistry` plus Gradle-Build-Modell überführt; Abhängigkeitsprüfung, Selbsttests, Bytecode-52-Prüfung |
| Prinzip "Regel plus Gegenbeispiel im selben Modul" | corenth | Gegenbeispiele unter `<Modulpaket>.archfixture..`, Bibliotheks-Stubs unter `<lib>.archstub` |
| Modulschnitt ACP/MCP/comic-controls, JUnit 4.13.2, Versionen acp-sdk/solon 3.10.1, Lucene 8.11.3, slf4j-nop | askai-java8 | unverändert übernommen |
| Versionen Gson 2.10.1, OkHttp 4.9.3, jsoup 1.17.2, JWBF 3.1.1, Java-WebSocket 1.5.2 | MainframeMate (`app`, `wiki-integration`) | OkHttp und JWBF sind deklariert, aber von keinem Modul verwendet |

## Chat (AP2, AP3)

| Teil | Quelle | Geändert |
|---|---|---|
| `ChatService` mit Historie im Speicher, System-Prompt getrennt | neu (Architekturentscheidung, s. u.) | Nutzerfrage bleibt bei Abbruch oder Fehler in der Historie |
| `OpenAiCompatibleChatAdapter`: SSE-Zeilen normalisieren, `data:`-Parsing bis `[DONE]`, UTF-8 | MainframeMate `CloudChatManager` | HttpURLConnection statt OkHttp, Gson nur intern, keine Provider-Umschaltung; Rollenregel `DeveloperRolePolicy`, `stop` als Array, `usage` ignoriert nach realen Tests |

## Embeddings (AP5, AP6)

| Teil | Quelle | Geändert |
|---|---|---|
| `EmbeddingPort` als Fassade, `EmbeddingVector`, `EmbeddingModelIdentity` mit SHA-256-Fingerprint | askai-java8 (`research-knowledge-pipeline/EmbeddingPort`, `EmbeddingEndpointDescriptor#embeddingFingerprint`, `VectorMath`) | Fingerprint ohne URL, Timeouts und Secrets; Dimension konfiguriert und geprüft |
| Transport-Naht und Antwortvalidierung | askai-java8 (`EmbeddingHttpTransport`, `UrlConnectionEmbeddingHttpTransport`) | um Bearer-Token und Status erweitert |
| Batch-Erfahrung (Teil-Batches, Reihenfolge über `index`) | MainframeMate (`MultiProviderEmbeddingClient#embedOpenAIBatch`) | nur als `ARRAY_UNVERIFIED`-Modus, Standard ist ein Request je Text |

## Wissensmodell und Index (AP7, AP8, AP9)

| Teil | Quelle | Geändert |
|---|---|---|
| Chunker entlang Überschriften und Sätzen mit Token-Budget | corenth (`NlpTextChunker`, `LexicalChunkingConfig`), askai-java8 (`PassageSegmentation`: Struktur als harte Grenze) | Satz-Overlap, deterministische Chunk-IDs, `textWithHeading()`; Unicode-Klassifizierung JDK-abhängig (offen) |
| `KnowledgeResourceId` als stabile URI mit Schema je Quelle | corenth (`VirtualResourceRef`, `BookmarkUri`, `ResourceScheme`) | als Wertobjekt ohne corenth-Schemaliste |
| `KnowledgeDocument` NFC-normalisiert mit SHA-256, `KnowledgeRevision` | neu | – |
| `KnowledgeIndexPort`, Einträge, Treffer, Abfragen | askai-java8 (`SemanticKnowledgeIndex`, `PassageIndexDocument`, `PassageSearchHit`, `PassageTextQuery`, `PassageSemanticQuery`) | mit Domain-Typen statt loser Strings; Namespace je `EmbeddingModelIdentity`; additiv `resourceIds`, `revisionOf` (Contract-Commit #28) |
| `LuceneKnowledgeIndex`: `text/<fingerprint>/` (BM25) und `vectors/<fingerprint>.vec` (Cosinus, linearer Scan) | askai-java8 (`CompositeSemanticKnowledgeIndex`, `LuceneTextPassageIndex`, `FileVectorPassageIndex`), Feldnamen aus corenth `LuceneLexicalIndex`, Lucene-Erfahrung aus MainframeMate | einziger öffentlicher Typ, Rollback, `rebuild`, eine Instanz je Verzeichnis, fremde Embedding-Welt abgelehnt |

## RAG-Orchestrierung (AP10)

| Teil | Quelle | Geändert |
|---|---|---|
| `IndexKnowledgeUseCase`, Stufen `DISCOVERY/LOADING/EMBEDDING/INDEXING`, `IndexingReport` | askai-java8 (`SourceProcessingWorker`, `SourceProcessingStage`) | seriell und blockierend; Skip über Revision, Prune nach vollständigem Lauf (AP20 #32) |
| Hybride Suche Volltext plus Cosinus | MainframeMate `HybridRetriever` | Reciprocal Rank Fusion (k=60) statt `mergeAndScore` und Reranker, da BM25- und Cosine-Scores nicht vergleichbar sind; Degradation mit `RetrievalWarning` |
| Kontextaufbau mit Budget | MainframeMate `RagContextBuilder` | nummerierte, zitierbare Quellen, Rahmenmarker neutralisiert, Schätz-Tokenizer |
| `RagChatUseCase`, `ChatService.sendWithContext` | neu | Kontext als System-Anteil nur für den Turn |

## Quellen (AP11, AP12, AP15)

| Teil | Quelle | Geändert |
|---|---|---|
| `KnowledgeSourcePort` (`discover`/`load`/`discoverLinks`), `SourceScope` (Startpunkte, Tiefe, Höchstzahl) | corenth `ResourceScheme`, Parameter aus MainframeMate `WikiSourceScanner` | Port ohne Adapter-DTOs, Ausnahmearten `KnowledgeSourceException` |
| MediaWiki über Action-API | MainframeMate `JwbfWikiContentService` (Abfragen, `CookieManager` je Site), `HtmlPostProcessor` (HTML → Text), `WikiSourceScanner` (Breitensuche, `wiki://site/Titel`) | ohne JWBF, direkt über `HttpURLConnection`; Zugangsdaten per Callback und gelöscht (MainframeMate hielt sie verschlüsselt in den Settings); IDs `wiki:<siteKey>/<Titel>` |
| Confluence REST (Endpunkte, `expand`-Felder, Paging über `_links.next`, Windows-MY-mTLS) | MainframeMate `ConfluenceRestClient`, `ConfluenceConnectionConfig`, `HtmlTextExtractor` | Secret je Port-Aufruf statt in der Konfiguration; zusätzlich Bearer/PAT (UNVERIFIED) und PKCS12 mit Passwort aus KeePass; keine Weiterleitungen; Transport-Naht `ConfluenceHttpTransport` |

## Sicherheit (AP13, AP14)

| Teil | Quelle | Geändert |
|---|---|---|
| `SecretRef`, `SecretMaterial` (char[], `close()` löscht), `SecretProvider.withSecret` | corenth `adyton` (Secret-Material verlässt den Aufruf nicht) | `SecretBoundaryRules` als Architekturtest |
| KeePassRPC: SRP-Pairing, AES-verschlüsseltes JSON-RPC, Key-Challenge-Response, Lesen per Titel | MainframeMate `KeePassProvider`, `KeePassRpcClient`, `KeePassRpcPairingDialog` | Kryptografie unverändert; Pairing per Callback, `KeePassPairingKeyStore` (Datei oder Speicher), eine Verbindung je Aufruf, nichts geloggt; Lookup-Naht nach corenth `KeePassRpcSecretMaterialProvider` |
| Fake-KeePassRPC-Server in Tests | neu (PR #25) | wegen eines Java-WebSocket-Fehlers (verlorenes `OP_WRITE`, 1.5.2 bis 1.5.7) |

## ACP (AP16, AP17)

| Teil | Quelle | Geändert |
|---|---|---|
| `acp-client-api` (15 Typen: Lebenszyklen, `AcpStates`, `PromptDispatcher`, `Redaction`) | askai-java8 `acp-client-api` | Paket, `toString()` ohne Secrets, Wire-Reihenfolge der Updates |
| `SolonAcpAgentConnector` über acp-sdk 3.10.1 (STDIO) | askai-java8 `acp-solon-client` | URLs nur Schema/Host/Port in Ausgaben, Listener-Thread, Reflection auf ein SDK-Feld (offen: SDK-Version ohne Reflection) |
| Demo-Agent als Fat-Jar | askai-java8 `acp-demo-agent` (`DemoAcpAgentMain`) | Testhaken `slow`/`crash`/`count`/`hang`, gedrosselt (PR #26) |

## MCP (AP18, AP19, AP20)

| Teil | Quelle | Geändert |
|---|---|---|
| `mcp-runtime-api` (`McpServerRegistry`, `McpToolContribution`, `McpToolClient` …) | askai-java8 `mcp-runtime-api` | `shutdown()`, `listTools()`, `McpToolCallException`, Namensregel, In-Process-Fixtures |
| `SolonMcpServerRuntime`, `SolonMcpToolClientFactory`: nur 127.0.0.1, Pfad `/mcp/<id>/<Token>` | askai-java8 `mcp-solon-runtime` | kein statischer Port-Zustand, Tool-Fehler als `isError`, Token-Vergleich in konstanter Zeit, `stopSharedServer()` |
| Werkzeugkatalog als Fabrik, Fehler als Ergebnis, Ziel vor dem Aufruf auflösen | askai-java8 `ResearchBotDirectoryTools`, `ResearchBotSessionTools` | `KnowledgeMcpTools` nur über Use Cases |
| Parameter `query`/`max_results`/`source_ids`, Snippet-Grenze, Gesamtgrenze 20.000 Zeichen | MainframeMate `SearchIndexTool`, `ReadChunksTool` | "Der Index führt": nur indexierte Dokumente, Refresh mit Skip und Prune |

## Oberfläche und Komposition (AP4, AP21, AP22, AP23)

| Teil | Quelle | Geändert |
|---|---|---|
| Comic-Komponenten (`comic-controls`) | askai-java8 | ohne flexmark (Markdown-Rendering entfällt), keine Abhängigkeiten |
| Research-Tokens und Pillen (`ResearchUiPalette`, `ResearchUiMetrics`, `ResearchUiPainter`, `ResearchUiTypography`, `ResearchIconButton`, `ResearchPillButton`, `ResearchPillDropdown`), `ComicSplitPane`, `ComicSearchBar`, `ComicHoverMenu`, `ComicOverlayPanel`, `StrokeIcon`/`ComposerIcons`, `ComposerButton`/`ComposerToggleButton` | askai-java8 Zweig `arch` (`comic-controls`, `askai-app` `ui`) | Paket `ui.comic.*`; Typografie-Cache als Holder (keine veränderlichen statischen Felder); `ComicSearchBar` ohne statische Höhe; Pfeil-Logik der Reiterleiste ohne Flackern am Ende; Pille und Composer-Knöpfe per Tab erreichbar (Fokusring, Pfeiltasten an der Pille), ein Klick nimmt dem Editor den Fokus nicht |
| Fenster-✕, Ziehen, Vergrößern (`ComicWindowCloseButton`, `ComicWindowDragger`, `ComicWindowResizer`) | askai-java8 `arch` `ComicOverlayPanel.CloseButton`, `AskAiFrame` | Malerei des ✕ einmal in `ComicWindowCloseButton`; `CloseButton` erbt davon |
| Arbeitsfläche, Drawer, Composer (`ChatWorkspacePanel`, `ChatSidebarPanel`, `SidebarTabRibbon`, `ChatHistoryRow`, `ChatComposerPanel`) | askai-java8 `arch` `ChatWorkspacePanel`, `ChatSidebarPanel`, `SidebarTabRibbon`, `ChatComposerPanel` | ohne Sessions-Liste und Plugins: Modus-Pille Chat/Agent statt Chat-Tabs, Drawer-Seiten Chats und Wissensquellen, Composer mit RAG-Pille und Senden/Stop |
| `ChatShellModel` ohne Swing, `ChatServiceBinding` | neu | Streaming-Deltas gebündelt (ein UI-Update je 30 ms, AP22) |
| Agent-Session-Anbindung, MCP-Endpoint je Agentenprozess | askai-java8 `AcpResearchSessionBackend` (dort `ASKAI_*`-Umgebung) | `ENTERPRISE_AI_MCP_*`, Token nur in der Composition Root |
| Properties-Konfiguration im Benutzerverzeichnis, Pfad-Override, unveränderliche Snapshots, Proxy-Modi | askai-java8 `AppConfigurationRepository`, `AskAiPaths`, `ProxyConfiguration` | Schlüsselnamen neu, keine Secrets in der Datei |
| Proxy-Auflösung und TLS-Vertrauen (`app.net.HttpRoutes`, `app.net.TrustPolicy`) | Bibliotheken aresstack/win-proxy-java 0.2.0 und win-trust-java 0.1.0, Oberfläche nach askai-java8 ProxyPanel, Einbindung wie corenth `WinProxyPlatformProxyRouteResolver` und askai-java8 `ProxyConfiguration` | Alle Modi der Bibliothek außer dem veralteten Legacy-Modus; Ergebnis je Ziel-Host gecacht; NOT_IMPLEMENTED und ERROR werden als nicht verfügbare Route gemeldet, ohne Rückfall auf DIRECT |
| Settings-Schlüssel für Proxy, Timeouts, mTLS, KeePass-Verdrahtung mit Pairing-Dialog | MainframeMate `Settings`, `KeePassProvider`, `KeePassRpcPairingDialog`, `MvsBrowser`-Proxy | API-Key als `SecretRef` statt verschlüsselt in den Settings |
| Shutdown-Reihenfolge, Hintergrund-Indexierung beim Start | MainframeMate `MainFrame`/`Main`, `IndexingService`; askai-java8 (Semantic Index beim Start) | `ShutdownSequence` mit fünf Stufen, `StartupIndexing` abschaltbar |
| Composition Root als einziger Ort für Adapterkonstruktoren | corenth | `CompositionRootBoundaryTest` |

## Neue Architekturentscheidungen (nicht aus einem Referenz-Repository)

- Genau eine Backend-Art (OpenAI-/GPT-kompatible Enterprise-API), kein Multi-Provider, keine Provider-Enums im
  Kern (Nachtrag zum Auftrag). MainframeMates Provider-Umschaltung wurde deshalb nicht übernommen.
- `chat-openai` und `embedding-openai` sind unabhängig und teilen keinen Transport.
- API-Key je Anfrage über adaptereigene Nähte (`TokenSource`, `BearerTokenSource`), hinter denen
  `SecretProvider.withSecret` steht; kein Cache.
- Reciprocal Rank Fusion statt Score-Normalisierung; Kontext als System-Anteil nur für den Turn.
- "Der Index führt": Werkzeuge liefern nur indexierte Dokumente (Entscheidungskarte an Angelo, empfohlene
  Option umgesetzt; andere Wahl erfordert Rückbau in AP20).
- MCP-Endpoint erreicht den Agenten über Umgebungsvariablen, nicht über ACP `session/new`.
- Flaky Tests werden robust gemacht, nie abgeschwächt; Solon-Tests mit eigener JVM je Klasse.
- Architekturregeln werden für Tests nicht geöffnet (AP25 Slice G nutzt einen Testagenten im Testcode).

## Offen

- Ob `comic-controls` außer flexmark weitere Teile der askai-Vorlage weglässt, ist nicht dokumentiert.
- Welche Teile von corenth `pinakes` als Vorbild für `knowledge-lucene` dienten, nennt der Code nur für die
  Feldnamen (`LuceneLexicalIndex`); mehr ist nicht belegt.
- Der Reflection-Zugriff auf ein Feld des acp-sdk (AP17) hängt an Version 3.10.1.
