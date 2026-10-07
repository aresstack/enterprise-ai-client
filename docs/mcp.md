# MCP: Werkzeuge für Agenten

MCP (Model Context Protocol) ist das Protokoll, über das ein Agent **Werkzeuge** der Anwendung aufruft. Die
Anwendung ist dabei der MCP-Server; der Agentenprozess (ACP, siehe [ACP](acp.md)) ist der Client. Werkzeuge
greifen ausschließlich über Application-Use-Cases und Ports auf Wissen zu, nie direkt auf Confluence,
MediaWiki oder den Index.

## Module

```
mcp-runtime-api    frameworkfreie Verträge (keine Solon-/MCP-SDK-Typen)
      ↑
mcp-solon-runtime  Adapter: MCP Streamable HTTP über org.noear solon / solon-boot-jdkhttp / solon-ai-mcp 3.10.1
application.mcp    die Wissenswerkzeuge als McpToolContribution (nur Use Cases)
```

Beide Module stammen aus askai-java8 (`mcp-runtime-api`, `mcp-solon-runtime`); keine eigene
MCP-Wire-Implementierung. Geändert: `McpServerRegistry.shutdown()`, `McpToolClient.listTools()`,
`McpToolCallException` als eigene Klasse, Tool-Namensregel `[A-Za-z0-9_.-]{1,128}` geprüft, kein statischer
Port-Zustand in der Runtime, Tool-Fehler als MCP-`isError`-Ergebnis statt Exception, Token-Vergleich in
konstanter Zeit.

### Verträge in `mcp-runtime-api`

| Seite | Typen |
|---|---|
| Server | `McpServerRegistry`: `registerEndpoint(McpEndpointDefinition)` → `McpEndpointHandle` (ID plus Token), `updateTools(handle, Collection<McpToolContribution>)`, `unregisterEndpoint(handle)`, `endpointUrl(handle)`, `toolNames(handle)`, `shutdown()` |
| Werkzeug | `McpToolContribution` (Name, Beschreibung, Parameter, Handler), `McpToolParameter` mit `McpToolType` (STRING, INTEGER, BOOLEAN, ENUM; keine Arrays), `McpToolHandler.handle(McpToolCall)` → `McpToolResult` (Text oder `error`) |
| Client | `McpToolClientFactory.connect(url)` → `McpToolClient` mit `listTools()` und `call(name, arguments)`; `McpToolCallException` unterscheidet nicht erreichbaren Endpoint und Tool-Fehler |

Alle Methoden mit `McpEndpointHandle` prüfen Endpoint-ID **und** Token; ein unbekannter, abgemeldeter oder
gefälschter Handle wird still ignoriert und löst keine Exception aus, die Token tragen könnte.

## Sicherheitsregeln (aus askai-java8 übernommen)

- Der Server bindet ausschließlich an `127.0.0.1` auf einem freien Port; die Architekturregel
  `HardcodedConfigurationTest` verbietet `0.0.0.0` im Bytecode.
- Jeder logische Endpoint bekommt einen eigenen Pfad `/mcp/<id>/<token>` mit einem nicht erratbaren Token
  (192 Bit aus `SecureRandom`); ein falscher Token trifft keine Route (HTTP 404).
- Abmelden stoppt die Route und invalidiert damit den Token. Beim Ende des Agent-Modus oder des
  Agentenprozesses wird der Endpoint abgemeldet.
- Tokens und Endpoint-URLs sind Secret-Material (die URL trägt den Token im Pfad): nie loggen, nie in
  Exceptions, `toString()`, Chat-Historie oder Indizes. `McpEndpointHandle.toString()` maskiert den Token.
- Handler-Fehler erreichen den Client nur als "Tool failed." bzw. als knappe Fehlermeldung ohne Stacktrace.
- `shutdown()` ist idempotent.

Solon ist prozessglobal: Die erste Registrierung bootet den Solon-Server einmal je JVM, alle
`SolonMcpServerRuntime`-Instanzen teilen ihn, `shutdown()` entfernt nur die eigenen Endpoints. Den Server selbst
gibt `SolonMcpServerRuntime.stopSharedServer()` beim endgültigen Beenden der Anwendung frei (sonst halten
Nicht-Daemon-Threads die JVM am Leben). Deshalb laufen die Tests von `mcp-solon-runtime` und `app-swing` mit
einer eigenen JVM je Testklasse (`forkEvery = 1`).

## Die Wissenswerkzeuge (`application.mcp.KnowledgeMcpTools`)

```
MCP-Client → McpServerRegistry (mcp-solon-runtime) → McpToolContribution (application.mcp)
          → RetrieveKnowledgeUseCase / LoadKnowledgeDocumentUseCase / RefreshKnowledgeSourceUseCase
          → KnowledgeIndexPort / EmbeddingPort / KnowledgeSourcePort → Adapter
```

| Werkzeug | Eingaben (flach) | Ausgabe |
|---|---|---|
| `search_knowledge` | `query` (Pflicht), `max_results` (optional, Obergrenze `retrieval.maxResults`), `source_ids` (optional, kommagetrennte Quell-IDs) | hybride Suche (Volltext plus Cosine, Reciprocal Rank Fusion); je Treffer Titel, Überschrift, Dokument- und Chunk-ID, Quelle, Ort, Stand, Scores und Ränge beider Pfade, Textausschnitt; Hinweis, wenn ein Suchpfad ausgefallen ist |
| `get_knowledge_document` | `id` (Pflicht, `<schema>:<id>`), `source_id` (optional) | nur **indexierte** Dokumente; vollständiger, aktueller Text frisch aus der Quelle (nicht aus dem Index), bei Überlänge gekürzt und gekennzeichnet |
| `refresh_knowledge_source` | `source_id` (Pflicht) | synchroner Abgleich der Quelle in ihrem konfigurierten Scope mit dem Index: Neues und Geändertes indexieren, Unverändertes überspringen (Revision), Verschwundenes entfernen; Zusammenfassung des `IndexingReport` mit Fehlern je Stufe; je Quelle höchstens ein Lauf gleichzeitig |

Ergebnisse sind strukturierter deutscher Text. Fehler sind `McpToolResult.error` mit knapper Meldung;
Meldungen der Port-Ausnahmen (`KnowledgeIndexException`, `KnowledgeSourceException`, `RetrievalWarning`)
gelangen nie in eine Antwort, weil Adapter dort Infrastrukturdaten wie Indexpfade nennen dürfen. Grenzen aus
`agent.tools.*`: Zeichen je Antwort (Standard 20.000), Zeichen je Ausschnitt (600), Treffer ohne Angabe (5),
aufgezählte Fehler je Aktualisierung (20).

**Der Index führt**: Der Agent sieht nur den freigegebenen Korpus, also das, was die Indexierung im
konfigurierten Scope aufgebaut hat. Ein Dokument ist erst nach Indexierung lesbar, leere Seiten nie; eine neue
Seite wird erst nach `refresh_knowledge_source` sichtbar. Hintergrund und Konsequenzen:
[RAG-Datenfluss](rag-datenfluss.md#der-index-führt).

Parameter sind flach (`source_ids` als kommagetrennte Zeichenkette), weil der MCP-Port keine Array-Typen kennt.

## Lebenszyklus in der Anwendung

1. Bei `agent.enabled=true` baut `AdapterAssembly` eine `SolonMcpServerRuntime`; `CompositionRoot` erzeugt die
   `KnowledgeMcpTools` aus den Use Cases und übergibt ihre `contributions()` dem `AcpAgentLauncher`.
2. Je Agentenprozess registriert der Launcher einen Endpoint (`agent.mcpEndpointId`, `agent.mcpDisplayName`)
   mit frischem Token, bestückt ihn mit den Werkzeugen und legt ihn als `ENTERPRISE_AI_MCP_*` in die
   Umgebung des Prozesses ([ACP](acp.md)).
3. Beim Ende des Agent-Modus: `KnowledgeMcpTools.shutdown()` (eine laufende Aktualisierung bricht zwischen zwei
   Ressourcen ab, neue werden abgewiesen), `AgentService.close()`, Endpoint abgemeldet.
4. Beim Beenden der Anwendung: `SolonMcpServerRuntime.shutdown()` und `stopSharedServer()`, danach wird der
   Lucene-Index geschlossen (`ShutdownSequence`).

Für Tests ohne Netzwerk stehen in den Testfixtures von `mcp-runtime-api` die In-Process-Referenzen
`InProcessMcpServerRegistry` und `InProcessMcpToolClientFactory` bereit; `KnowledgeMcpToolsRoundTripTest` in
`application` und `ApplicationCompositionTest` in `app-swing` nutzen sie. Der Roundtrip über den echten
Solon-Transport läuft in `SolonMcpRoundTripTest` (Testwerkzeuge `ping`, `echo`) und `AgentModeRoundTripTest`.
