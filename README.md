# enterprise-ai-client

Modularer Java-8-Desktop-KI-Client (Swing/Java2D, Comic-Stil) in Ports-and-Adapters-Architektur:
OpenAI-kompatibler Chat mit Streaming, Embeddings, Lucene-BM25 plus Cosine-Retrieval (RAG), Wissensquellen
MediaWiki und Confluence, KeePassRPC als Security-Adapter, ACP-Agent-Modus und MCP-Tools.

Stand: AP1–AP23. Alle Module sind implementiert und in `app-swing` zu einer startbaren Anwendung verdrahtet;
die Architekturregeln sind als Tests aktiv. Modulschnitt, Abhängigkeitsrichtung und Arbeitsregeln:
[ARCHITECTURE.md](ARCHITECTURE.md).

## Bauen

Benötigt ein JDK 8 oder neuer (kompiliert wird immer für Java 8).

```bash
./gradlew build                       # alle Module, alle Tests inkl. Architekturtests
./gradlew :architecture-tests:test    # nur die Architekturregeln
```

## Starten

```bash
./gradlew :app-swing:run                                   # Konfiguration aus ~/.enterprise-ai-client/
./gradlew :app-swing:run -Denterpriseai.config=/pfad/app.properties
```

Beim ersten Start legt die Anwendung die kommentierte Vorlage `enterprise-ai-client.properties` im
Benutzerverzeichnis (`~/.enterprise-ai-client/`, unter Windows `%APPDATA%\.enterprise-ai-client\`) ab.
Pflicht sind Basis-URL und Modell des Chat-Dienstes, Modell und Dimension der Embeddings sowie der Titel des
KeePass-Eintrags mit dem API-Key; Secrets stehen nie in der Datei, sondern kommen über KeePassRPC. Ohne KeePass
startet die Anwendung und meldet, dass Secrets fehlen. Alle Schlüssel sind in der Vorlage beschrieben
(`app-swing/src/main/resources/com/aresstack/enterpriseai/app/config/enterprise-ai-client.example.properties`).

Demos ohne Backend: `./gradlew :app-swing:runChatDemo`, `:app-swing:runRagDemo`, `:app-swing:runAgentDemo`.
