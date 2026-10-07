# enterprise-ai-client

Modularer Java-8-Desktop-KI-Client (Swing/Java2D, Comic-Stil) in Ports-and-Adapters-Architektur:
OpenAI-kompatibler Chat mit Streaming, Embeddings, Lucene-BM25 plus Cosine-Retrieval (RAG), Wissensquellen
MediaWiki und Confluence, KeePassRPC als Security-Adapter, ACP-Agent-Modus und MCP-Tools.

Stand: Projektgerüst (AP1). Alle Module existieren und kompilieren; die Architekturregeln sind als Tests
aktiv. Modulschnitt, Abhängigkeitsrichtung und Arbeitsregeln: [ARCHITECTURE.md](ARCHITECTURE.md).

## Bauen

Benötigt ein JDK 8 oder neuer (kompiliert wird immer für Java 8).

```bash
./gradlew build                       # alle Module, alle Tests inkl. Architekturtests
./gradlew :architecture-tests:test    # nur die Architekturregeln
```
