# enterprise-ai-client

Modularer Java-8-Desktop-KI-Client (Swing/Java2D im Comic-Stil) in Ports-and-Adapters-Architektur für eine
interne, OpenAI-/GPT-kompatible Enterprise-API: Chat mit Streaming, Embeddings, hybrides Retrieval (Lucene
BM25 plus Cosinus, RAG), Wissensquellen MediaWiki und Confluence, Secrets über KeePassRPC, Agent-Modus über
ACP und Wissenswerkzeuge über MCP.

Stand: alle Arbeitspakete bis AP24 sind auf `main`; die Anwendung ist vollständig aus der Composition Root
verdrahtet, die Architekturregeln laufen als Tests. Verbindliche Architekturgrundlage ist
[ARCHITECTURE.md](ARCHITECTURE.md); die ausführliche Dokumentation liegt unter [docs/](docs/README.md).

## Schnellstart

Voraussetzung: ein JDK 8 oder neuer (kompiliert wird immer für Java 8; die CI baut mit JDK 8 und 21).

**Fertiges Jar**: Unter [Releases](https://github.com/aresstack/enterprise-ai-client/releases) liegt zu jeder
Release-Version (`v<version>`, aus `main`) und zu jedem seither gepushten Branch (Pre-Release `snapshot-<branch>-<hash>`,
bei jedem Push ersetzt) das lauffähige Fat Jar `enterprise-ai-client-<version>.jar`. Start ohne Gradle:

```bash
java -jar enterprise-ai-client-<version>.jar
```

**Aus dem Quellcode**:

```bash
./gradlew build                 # alle Module, alle Tests inklusive Architektur- und Slice-Tests; baut und prüft auch das Fat Jar
./gradlew :app-swing:run        # Anwendung starten
./gradlew :app-swing:fatJar     # nur das Fat Jar: app-swing/build/libs/enterprise-ai-client-<version>-SNAPSHOT.jar
```

1. **Bauen**: `./gradlew build`. Ohne Netzwerk zu Maven Central schlägt der erste Lauf fehl; einfach
   wiederholen, sobald die Abhängigkeiten geladen sind.
2. **Konfigurieren**: Der erste Start legt die kommentierte Vorlage `enterprise-ai-client.properties` im
   Anwendungsverzeichnis an (`~/.enterprise-ai-client/`, unter Windows `%APPDATA%\.enterprise-ai-client\`)
   und beendet sich. Pflicht sind Basis-URL und Modell des Chat-Dienstes, Modell und Dimension der
   Embeddings sowie der Titel des KeePass-Eintrags mit dem API-Key. Secrets stehen nie in der Datei; sie kommen
   zur Laufzeit aus KeePass (Plugin KeePassRPC). Alternativ: `-Denterpriseai.config=/pfad/zur/datei`.
3. **Starten**: `./gradlew :app-swing:run` oder `java -jar enterprise-ai-client-<version>.jar`. Ohne erreichbares
   KeePass startet die Anwendung trotzdem und meldet, welche Secrets fehlen; Anfragen scheitern dann mit einem
   Authentifizierungsfehler.
4. **Chatten**: Frage eintippen, Senden. Antworten streamen, ein Turn lässt sich abbrechen; der System-Prompt
   kommt aus `chat.systemPrompt`.
5. **Quelle indexieren**: Eine Quelle unter `sources` und `source.<id>.*` eintragen (MediaWiki oder
   Confluence). Mit `knowledge.indexOnStartup=true` (Standard) indexiert die Anwendung beim Start im
   Hintergrund; die Statuszeile zeigt den Fortschritt und bietet Abbrechen an. Alternativ stößt ein Agent
   `refresh_knowledge_source` an.
6. **RAG verwenden**: Den Schalter „RAG“ neben dem Eingabefeld einschalten. Vor der Antwort sucht die
   Anwendung im Index; die Antwort trägt die verwendeten Quellen, Hinweise erscheinen als eigene Blase.
7. **Agent-Modus**: `agent.enabled=true` und `agent.command`/`agent.args` auf einen ACP-fähigen Agenten setzen
   (zum Ausprobieren der Demo-Agent aus `./gradlew :acp-demo-agent:demoAgentJar`). Der Reiter „Agent“ startet
   den Prozess; er erhält die Wissenswerkzeuge über einen MCP-Endpoint, der nur für diesen Prozess gilt.

Demos ohne Enterprise-API und ohne KeePass: `./gradlew :app-swing:runChatDemo` (Chat gegen einen Fake-Port),
`:app-swing:runRagDemo` (echte Chat- und Embedding-Adapter gegen lokale Fake-HTTP-Server, Lucene-Index im
Temp-Verzeichnis), `:app-swing:runAgentDemo` (Chat gegen Fake-Port plus Demo-Agent als Kindprozess).

## Abhängigkeitsrichtung

```
app-swing  ──►  comic-controls
   │
   ├──►  application  ──►  chat-api, embedding-api, knowledge-api, source-api,
   │          │             security-api, acp-client-api, mcp-runtime-api
   │          └────────────────────────────────────────────────────────────►  domain
   │                                                                            ▲
   └──►  Adapter  ──►  ihr Port-Modul  ─────────────────────────────────────────┘
          chat-openai → chat-api            source-mediawiki  → source-api
          embedding-openai → embedding-api  source-confluence → source-api + security-api
          knowledge-lucene → knowledge-api  security-keepassrpc → security-api
          acp-solon-client → acp-client-api mcp-solon-runtime → mcp-runtime-api

Pfeile zeigen "darf verwenden". domain kennt niemanden. Die Ports chat-, embedding-, knowledge-, source- und
security-api kennen nur domain; acp-client-api und mcp-runtime-api sind eigenständig. application kennt domain
und alle Ports. Ein Adapter kennt seinen Port, domain und seine Bibliothek; source-confluence zusätzlich
security-api. app-swing ist der einzige Ort, an dem Adapter konstruiert werden.
```

Swing nur in `app-swing` und `comic-controls`; Bibliotheken nur in ihrem Adapter; kein Logging-Framework, keine
Konsolenausgabe außerhalb des Demo-Agenten; keine fremden Hostnamen, Modellnamen oder Schlüssel als Literale im
Code. Die vollständigen Regeln prüft `./gradlew :architecture-tests:test`.

## Dokumentation

| Thema | Seite |
|---|---|
| Bauen, erster Start, Konfigurationsdatei, IDE, Fat Jar und Releases | [docs/einrichtung.md](docs/einrichtung.md) |
| Module, Pakete, Bibliotheken, Testfixtures | [docs/module.md](docs/module.md) |
| Schichten, Modulgraph, Laufzeitsicht, Regeln | [docs/architektur.md](docs/architektur.md) |
| Chat- und Embedding-Endpunkt, Proxy, beobachtetes Serververhalten | [docs/konfiguration-api.md](docs/konfiguration-api.md) |
| KeePassRPC, Pairing, Secret-Referenzen | [docs/konfiguration-keepass.md](docs/konfiguration-keepass.md) |
| MediaWiki als Wissensquelle | [docs/konfiguration-mediawiki.md](docs/konfiguration-mediawiki.md) |
| Confluence Data Center als Wissensquelle | [docs/konfiguration-confluence.md](docs/konfiguration-confluence.md) |
| Indexierung, hybride Suche, Kontext, „Der Index führt“ | [docs/rag-datenfluss.md](docs/rag-datenfluss.md) |
| Agent-Modus über ACP | [docs/acp.md](docs/acp.md) |
| Wissenswerkzeuge über MCP | [docs/mcp.md](docs/mcp.md) |
| Tests, Fixtures, umgebungsabhängige Läufe, CI | [docs/tests.md](docs/tests.md) |
| Herkunft aus askai-java8, MainframeMate, corenth | [docs/herkunft.md](docs/herkunft.md) |
| Bekannte Einschränkungen und UNVERIFIED-Punkte | [docs/einschraenkungen.md](docs/einschraenkungen.md) |

Architekturgrundlage und Arbeitsregeln der Stränge: [ARCHITECTURE.md](ARCHITECTURE.md).
