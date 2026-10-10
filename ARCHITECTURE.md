# Architektur

Modularer Java-8-Desktop-KI-Client in Ports-and-Adapters-Architektur. Diese Datei ist die verbindliche
Arbeitsgrundlage für alle Stränge (A bis H) und für die späteren Integrationspakete. Die Regeln hier sind
in `architecture-tests` ausführbar; was dort nicht geprüft wird, ist hier als Konvention festgehalten.

Einstieg und ausführliche Erklärungen stehen in [README.md](README.md) und unter [docs/](docs/README.md)
(Einrichtung, Modulübersicht, Diagramme, Konfiguration, RAG, ACP, MCP, Tests, Herkunft, Einschränkungen).
Bei Widersprüchen gilt der Code auf `main`, danach diese Datei, danach die Seiten unter `docs/`.

## Schichten und Richtung

```
             app-swing  (UI + einzige Composition Root)
            /    |     \
   Adapter       |      comic-controls (Swing/Java2D-Bibliothek)
   (*-openai,    |
    knowledge-lucene, source-*, security-keepassrpc,
    acp-solon-client, mcp-solon-runtime)
            \    |
             application   (Use Cases, Orchestrierung)
                 |
      Ports (*-api)  →  domain   (reine Fachobjekte)
```

Erlaubt ist ausschließlich die Richtung von außen nach innen:

```
UI / Adapter
     ↓
Application
     ↓
Domain + Ports
```

Verboten sind insbesondere:

- `domain → irgendein anderes Modul` (domain hat keine Abhängigkeiten)
- `*-api → Adapter` und `*-api → application`
- `application → konkreter Adapter` (application kennt nur Ports)
- `Adapter → anderer Adapter`, `Adapter → application`, `Adapter → app-swing`
- jede Abhängigkeit auf `app-swing`, `acp-demo-agent` oder `architecture-tests`

## Module

Basispaket aller Module: `com.aresstack.enterpriseai`. Jedes Modul besitzt genau ein Basispaket; Klassen eines
Moduls liegen nur dort (geprüft).

| Modul | Rolle | Basispaket (`com.aresstack.enterpriseai.`) | darf sehen | Strang / AP |
|---|---|---|---|---|
| `domain` | DOMAIN | `domain` | – | Kern; Unterpakete je Strang, siehe unten |
| `application` | APPLICATION | `application` | domain, alle `*-api` | AP10, AP20, AP21, AP23; Tamias und Chalcotheca (`application.resource`, 0.1.16) |
| `chat-api` | PORT | `chat.api` | domain | A (AP2) |
| `chat-openai` | ADAPTER | `chat.openai` | domain, chat-api | A (AP3) |
| `embedding-api` | PORT | `embedding.api` | domain | C (AP5) |
| `embedding-openai` | ADAPTER | `embedding.openai` | domain, embedding-api | C (AP6) |
| `knowledge-api` | PORT | `knowledge.api` | domain | D (AP7, AP8) |
| `knowledge-lucene` | ADAPTER | `knowledge.lucene` | domain, knowledge-api | D (AP9) |
| `source-api` | PORT | `source.api` | domain | E (AP11) |
| `source-mediawiki` | ADAPTER | `source.mediawiki` | domain, source-api, http-api | E (AP12) |
| `source-confluence` | ADAPTER | `source.confluence` | domain, source-api, security-api, http-api | F (AP15) |
| `source-localfiles` | ADAPTER | `source.localfiles` | domain, source-api, document-api | Dateien (0.1.10) |
| `source-ftp` | ADAPTER | `source.ftp` | domain, source-api, security-api | FTP/MVS-Quelle (COBOL in PDS-Membern) und JES-Jobausgaben; Commons Net, Anmeldung über KeePass |
| `source-ndv` | ADAPTER | `source.ndv` | domain, source-api, security-api | Natural-Quellen über NDV (NATSPOD/PAL aus MainframeMate, Anmeldung über KeePass) |
| `resource-api` | PORT | `resource.api` | domain | Ressourcenschicht aus corenth: AcquisitionPort, Bronze (0.1.16) |
| `resource-holkas` | ADAPTER | `resource.holkas` | domain, resource-api, source-api | Holkas-Connectoren über den Quellen (0.1.16) |
| `document-api` | PORT | `document.api` | domain | Dokumente (corenth deigma) |
| `document-tika` | ADAPTER | `document.tika` | document-api | Dokumente (Tika) |
| `http-api` | PORT | `http.api` | domain | N (Netz) |
| `model-api` | PORT | `model.api` | domain | Modellverwaltung |
| `model-kipitz` | ADAPTER | `model.kipitz` | domain, model-api, http-api, speech-api | Modellverwaltung, Sprachausgabe |
| `model-sidecar` | ADAPTER | `model.sidecar` | domain, model-api, speech-api, chat-api, embedding-api | Modellverwaltung, lokaler Chat/Embeddings (`/api/chat`, `/api/embed`), Sprachausgabe |
| `model-huggingface` | ADAPTER | `model.huggingface` | domain, model-api, http-api | Stimmen für den Sidecar installieren (huggingface4j, kuratiert) |
| `speech-api` | PORT | `speech.api` | domain | Sprachausgabe (TTS über die Quelle des gewählten Modells: KIPITZ oder lokaler Sidecar) |
| `security-api` | PORT | `security.api` | domain | F (AP13) |
| `security-keepassrpc` | ADAPTER | `security.keepassrpc` | domain, security-api | F (AP14) |
| `acp-client-api` | PORT | `acp.api` | domain | G (AP16) |
| `acp-solon-client` | ADAPTER | `acp.solon` | domain, acp-client-api | G (AP17) |
| `acp-demo-agent` | TEST_FIXTURE | `acp.demo` | – (nur externe Bibliotheken) | G (AP17) |
| `mcp-runtime-api` | PORT | `mcp.api` | domain | H (AP18) |
| `mcp-solon-runtime` | ADAPTER | `mcp.solon` | domain, mcp-runtime-api | H (AP19) |
| `comic-controls` | UI_LIBRARY | `ui.comic` | – | B (AP4) |
| `app-swing` | COMPOSITION_ROOT | `app` | alles außer acp-demo-agent und architecture-tests | B (AP4, AP22), AP23 |
| `architecture-tests` | ARCHITECTURE_TESTS | `architecture` (nur Tests) | – (liest kompilierte Klassen) | AP24 |
| `integration-tests` | INTEGRATION_TESTS | `integration` (Produktion nur Anker-Klasse) | – (Testklassenpfad sieht alle Module und Testfixtures) | AP25 |

Abweichungen von der Modulliste des Auftrags, beide ohne Aufweichung der fachlichen Grenzen:

- **`acp-demo-agent`** (zusätzlich): der Demo-Agent aus AP17 ist ein eigener Kindprozess und Testfixture.
  Kein Modul darf von ihm abhängen; `acp-solon-client` startet ihn im Test nur als Jar (wie in askai-java8).
  Er ist neben `app-swing` das einzige Modul mit einer `main`-Methode.
- **`comic-controls`** (zusätzlich): die generischen Java2D-Comic-Komponenten aus askai-java8
  (`comic-controls`) bleiben eine abhängigkeitsfreie Swing-Bibliothek; die Chat-Shell selbst lebt in
  `app-swing`. Damit bleibt die Wiederverwendung sauber getrennt von der Anwendung.

`acp-client-api` und `mcp-runtime-api` dürfen domain sehen, deklarieren es aber derzeit nicht: Die aus
askai-java8 übernommenen Verträge sind bewusst eigenständig.

## Was wohin gehört

- **domain**: providerneutrale Werttypen, die mehr als ein Port oder Use Case teilt. Für die parallelen
  Stränge ist domain in Unterpakete aufgeteilt; jeder Strang ändert nur sein eigenes:
  `domain.chat` (A), `domain.embedding` (C), `domain.knowledge` (D), `domain.source` (E),
  `domain.security` (F). Das Wurzelpaket `domain` selbst wird nur per Contract-Commit geändert.
- **`*-api`**: Port-Interfaces und port-spezifische Request-/Response-/Listener-Typen. Keine
  Implementierung von Infrastruktur, keine Provider-Typen (OpenAI, Ollama, Lucene, JWBF, KeePass ...).
- **Adapter**: genau eine technische Integration hinter genau einem Port. Technische DTOs, JSON-Mapping,
  HTTP-Clients und Fremdbibliotheken bleiben paketintern im Adapter. Fremdbibliotheken mit
  `implementation` deklarieren, nicht mit `api`, damit sie nicht über den Klassenpfad nach außen sickern.
  Adapter laden keine globalen Settings, sondern bekommen eine unveränderliche Konfiguration per
  Konstruktor. Adapter machen keine UI: Dialoge (z. B. KeePassRPC-Pairing) werden über einen Callback-Port
  angefragt und in `app-swing` umgesetzt.
- **application**: Use Cases (Chat, RAG, Indexierung, Retrieval, MCP-Tool-Logik), ausschließlich gegen Ports.
  MCP-Tool-Handler rufen Use Cases auf, nie Adapter.
- **app-swing**: Swing-Oberfläche und die einzige Composition Root (`EnterpriseAiClientMain`). Nur hier werden
  Konfiguration gelesen, Adapter instanziiert und per Konstruktor verdrahtet.
- **Secrets**: `SecretRef` (loggbar) darf durch Use Cases laufen, Secret-Material nicht. Klartext nur im
  Security-Adapter, im konsumierenden Adapter (z. B. Confluence) und in den Brücken `app.security` der
  Composition Root (API-Key je Anfrage, Wiki-Login), so kurzlebig wie möglich; nie in Logs, Exceptions,
  `toString()`, Chat-Historie oder Indizes.

## Technologiegrenzen

| Technologie | erkannt an | erlaubt nur in |
|---|---|---|
| Swing/AWT | `javax.swing..`, `java.awt..` | app-swing, comic-controls |
| Lucene | `org.apache.lucene..` | knowledge-lucene |
| ACP SDK, Reactor | `com.agentclientprotocol..`, `reactor..` | acp-solon-client, acp-demo-agent |
| Solon | `org.noear.solon..` | acp-solon-client, acp-demo-agent, mcp-solon-runtime |
| Solon MCP / MCP SDK | `org.noear.solon.ai.mcp..`, `io.modelcontextprotocol..` | mcp-solon-runtime |
| JWBF | `net.sourceforge.jwbf..` | source-mediawiki |
| KeePassRPC-Transport | `org.java_websocket..` | security-keepassrpc |
| HTTP | `java.net.URLConnection`+Unterklassen, `java.net.http..`, `okhttp3..`, `org.apache.http..`, `org.apache.hc..`, `com.sun.net.httpserver..` | nicht im Kern |

Zusätzlich gilt für den Kern (domain, application, alle `*-api`) eine Positivliste: erlaubt sind nur
`java.lang..`, `java.util..`, `java.io..`, `java.nio..`, `java.time..`, `java.math..`, `java.text..`,
`java.security..`, `java.net.URI`, `java.net.URISyntaxException` und eigene Projekttypen. Kernmodule
dürfen in Gradle keine externe Bibliothek deklarieren (Testabhängigkeiten ausgenommen).

## Architekturtests (`architecture-tests`)

Die Tests laufen mit `./gradlew build` bzw. `./gradlew :architecture-tests:test`. Gradle schreibt dafür
ein Build-Modell (Module, deklarierte Produktionsabhängigkeiten, Klassenverzeichnisse), die Tests
importieren die kompilierten Produktionsklassen aller Module mit ArchUnit.

| Test | prüft |
|---|---|
| `BuildModelTest.everyGradleModuleIsRegistered` | jedes Gradle-Modul ist in `ModuleRegistry` eingetragen |
| `BuildModelTest.everyRegisteredModuleExists` | Registry und `settings.gradle` stimmen überein |
| `BuildModelTest.declaredProjectDependenciesFollowTheAllowedDirection` | `project(...)`-Abhängigkeiten in `api`/`implementation`/`compileOnly`/`runtimeOnly`/`annotationProcessor` |
| `BuildModelTest.coreModulesDeclareNoExternalLibraries` | kein Framework in domain/application/`*-api` deklariert |
| `BuildModelTest.confinedLibrariesAreDeclaredOnlyInTheirModules` | begrenzte Bibliotheken (Tabelle oben) auch ohne Klassenreferenz, z. B. `runtimeOnly`, nur im erlaubten Modul |
| `BuildModelTest.everyModuleHasClassesOnlyInItsOwnBasePackage` | Klasse ↔ Modul eindeutig, kein Modul leer |
| `ClassBoundaryTest.*` | Modulmatrix auf Klassenebene, Kern-Positivliste, AP24-Technologietabelle für den Kern, Technologiegrenzen, keine Singletons / kein nicht-finales `static` / keine nicht-privaten `static final` Arrays, Collections, Maps, Atomics oder StringBuilder, `main` nur in app-swing und acp-demo-agent |
| `ModuleRegistryTest.*` | die Registry selbst: keine Zyklen, Schichtung, AP24-Verbotskanten bleiben verboten, Pakete disjunkt |
| `AgentModeBoundaryTest.*` | AP21: Chat-Pfad ohne ACP/MCP/Agent-Modus; ACP-/MCP-Typen nur in `application.agent`, `application.mcp`, `app.agent` und der Composition Root; Agent-Use-Case sieht nur den ACP-Port; `app.ui.agent` ist reine Oberfläche |
| `RagBoundaryTest.*` | AP10: `application.rag` sieht nur Chat-Use-Case, Embedding- und Index-Port; `application.knowledge` nur Source-, Embedding- und Index-Port; der Chat-Pfad kennt beides nicht |
| `McpKnowledgeToolsBoundaryTest.*` | AP20: `application.mcp` sieht nur die Use-Case-Pakete `application.rag` und `application.knowledge`, Knowledge-Domain, aus `source.api` allein `KnowledgeSourceException` (Fehlerart) und den MCP-Port-Vertrag; Index-, Embedding- und Quell-Port (samt `SourceScope`) nur über Use Cases; kein Adapter, kein Chat, kein ACP, kein Security-Typ; Use Cases und Chat-Pfad kennen die Werkzeuge nicht |
| `CompositionRootBoundaryTest.*` | AP23: Konstruktoren von Adaptern (Klassen eines Adaptermoduls, die einen Port implementieren) werden außerhalb der Adaptermodule nur in `app.composition` aufgerufen; Wert- und Konfigurationstypen der Adaptermodule bleiben überall baubar |
| `SecretBoundaryTest.*` | AP13/AP23: Secret-Material nur in `security-api`, `security-keepassrpc`, `source-confluence`, `source-ftp`, `source-ndv` und, paketgenau, in `app.security` (Brücken der Composition Root); nie in Feldern |
| `RulesDetectViolationsTest.*` | Selbsttest: absichtliche Verstöße (Fixtures) werden erkannt, ein neutraler Domain-Wert nicht |
| `CoreNamingTest.*` | Nachtrag 1/19: keine Provider-Namen (OpenAI, Ollama, Claude, llama.cpp ...) und keine Multi-Provider-Abstraktion in Klassennamen oder Enum-Konstanten des Kerns |
| `TestCodeIsolationTest.*` | Produktionscode kennt weder JUnit/ArchUnit/Mockito noch Testfixture-Klassen oder Testpakete (`testing`, `testkit`, `fake`); keine `testFixtures(...)` in einer Produktionskonfiguration |
| `AdapterSignatureTest.*` | öffentliche Signaturen (Supertypen, Felder, Konstruktoren, Methoden, Typargumente) aller Adapter zeigen keine Bibliotheks- oder Transporttypen; keine Fremdbibliothek mit `api` deklariert |
| `LoggingBoundaryTest.*` | Kern und comic-controls loggen nicht; kein Logging-Framework (Adapter höchstens `java.util.logging`); `System.out`/`System.err` und `printStackTrace` nur im Demo-Agenten |
| `CompositionRootTest.*` | `System.getenv`, Preferences und `Properties.load` nur in app-swing und acp-demo-agent; kein `ServiceLoader`; jeder Adapter implementiert ein Interface seines Ports |
| `UiTechnologyTest.*` | Nachtrag 3: kein JavaFX, SWT, Servlet oder Web-Framework im Produktionscode |
| `HardcodedConfigurationTest.*` | Nachtrag 1/5/18: im Konstantenpool der Produktionsklassen keine URL mit fremdem Host, kein Modellname, kein API-Key-/Bearer-Literal, keine Bind-All-Adresse `0.0.0.0` |
| `Java8CompatibilityTest.*` | jeder Kompilierschritt jedes Moduls (auch Tests und Testfixtures) zielt auf Java 8 (`--release 8` bzw. Target 1.8); alle Klassendateien tragen Bytecode-Major 52 |
| `SettingsGradleTest.*` | `include`-Zeilen in `settings.gradle` ↔ `ModuleRegistry` ↔ Gradle-Build-Modell |
| `TechnologyRulesDetectViolationsTest`, `ModuleMatrixDetectViolationsTest`, `ExistingRulesDetectViolationsTest` | Gegenbeispiele (AP24): jede Zeile der Technologietabelle, jede verbotene Modulkante und jede inline definierte Strang-Regel wird gegen ein absichtlich verstoßendes Fixture rot; Bibliotheks-Stubs mit echten Paketnamen liegen unter `src/test/java/<bibliothek>/archstub` |

Gegenbeispiele liegen ausschließlich im Testcode von `architecture-tests` unter `<Modulpaket>.archfixture..`
(z. B. `domain.archfixture.tech.LuceneInDomain`) und werden nie in die Produktionsläufe importiert. Regeln, die als
Fabrik vorliegen (`ArchitectureRules`, `*Rules`), werden direkt auf die Fixtures angewendet; Regeln, die in einer
Testmethode stehen, führt `ExistingRuleProbe` unverändert aus und ersetzt nur ihre Eingabe. Wer eine Regel ergänzt,
legt ihr Gegenbeispiel daneben.

### Neues Modul aufnehmen (z. B. `source-sharepoint`, `source-files`)

1. `include` in `settings.gradle`, `build.gradle` mit Abhängigkeiten nur in erlaubter Richtung.
2. Eintrag in `architecture-tests/.../ModuleRegistry.java`: Name, Basispaket, Rolle, Strang, erlaubte
   Abhängigkeiten. Bei neuer Fremdtechnologie auch `Technology` und `ArchitectureRules.confinedTechnologies()`.
3. Zeile in der Modultabelle oben.
4. Neue Knowledge Sources implementieren `source-api` und brauchen keine Änderung an Kern oder application.

Ohne Schritt 2 wird `everyGradleModuleIsRegistered` rot.

Die Modul-Anker-Klassen (`*Module.java`) halten leere Module kompilierbar und scanbar. Sie dürfen gelöscht
werden, sobald das Modul eigene Produktionsklassen in seinem Basispaket hat.

## Agent-Modus (AP21)

Der normale Chat (UI → `ChatService` → `ChatCompletionPort`) läuft vollständig ohne ACP und MCP. Der
Agent-Modus ist ein getrennter Zusatz: UI → `AgentService` (`application.agent`) → ACP-Port → Agent-Prozess.

- `AgentService` hält Prozess, ACP-Verbindung und eine Session, höchstens einen Auftrag gleichzeitig, und ein
  eigenes Transkript. Gestartet wird über `AgentLauncher`, den die Composition Root implementiert
  (`app.agent.AcpAgentLauncher`); Endpoint-Tokens und Launch-Umgebung laufen nie durch den Use Case.
- MCP für den Agenten: je Agentenprozess ein frisch registrierter Endpoint (neuer Token), übergeben als
  Umgebungsvariablen `ENTERPRISE_AI_MCP_*` (`app.agent.AgentMcpEnvironment`); beim Schließen von Verbindung
  oder Agent-Modus wird er abgemeldet.
- Oberfläche: `app.ui.workspace.ChatWorkspacePanel` zeigt Chat oder Agent als Karte, gewählt über die
  Modus-Pille `☰ [ Chat ▾ ]`/`[ Agent ▾ ]` neben dem Hamburger (Presentation-Model `app.ui.agent.ShellModeModel`);
  jede Karte ist eine eigene `ChatShellPanel` mit eigenem Model. Die Agent-Karte baut `app.agent.AgentModeAssembly`.

## Oberfläche (askai-java8 `arch`)

Die Oberfläche folgt dem Design des Zweigs `arch` von askai-java8; `comic-controls` trägt die dort generischen
Teile als Ports (`theme.ResearchUiPalette`/`ResearchUiMetrics`/`ResearchUiPainter`/`ResearchUiTypography`,
`control.ResearchIconButton`/`ResearchPillButton`/`ResearchPillDropdown`/`ComicSplitPane`/`ComicSearchBar`/
`ComicHoverMenu`/`ComicOverlayPanel`/`ComposerButton`/`ComposerToggleButton`, `paint.StrokeIcon`/`ComposerIcons`),
dazu die Fensterstücke `ComicWindowCloseButton` (das ✕ aus `ComicOverlayPanel.CloseButton`, einmal gezeichnet),
`ComicWindowDragger` und `ComicWindowResizer`. Farben, Radien und Höhen kommen aus den Research-Tokens; eine
eigene Palette gibt es nicht.

```
┌────────────────────────────────────────────────┐
│ ☰ [ Chat ▾ ] ‹Chats│Wissensquellen›  Titel   ✕ │  Kopfzeile = Zieh-Fläche des rahmenlosen Fensters
├────────────┬───────────────────────────────────┤
│ Drawer     │ Chat- oder Agent-Ansicht          │  ComicSplitPane; Drawer öffnet beim Überfahren des
│ Suche      │   Transkript (Sprechblasen)       │  Hamburgers, rastet per Klick ein
│ + Neuer    │   Statuszeile der Wissensbasis    │
│   Chat     │ ┌───────────────────────────────┐ │
│ Chat-Zeilen│ │ Nachricht…                    │ │  ein Composer: randloser Editor, RAG-Pille,
│        ⚙   │ │ [RAG]              [➤ Senden] │ │  Senden in Ruhe / Stop während der Antwort
└────────────┴─┴───────────────────────────────┴─┘
```

- `ShellFrame`: `setUndecorated(true)`, Tintenrand (`ComicBorder.windowBorder`) als Greifzone zum Vergrößern,
  runde Ecken wie der Windows-11-Rahmen des askai-Fensters (`ComicWindowShape`: Fensterform und Kontur mit
  `ResearchUiMetrics.RADIUS_WINDOW`, maximiert eckig; ebenso der Einstellungen-Dialog),
  Kopfzeile zieht, Doppelklick maximiert und stellt die vorherige Größe wieder her (eigene Buchführung in
  `ComicWindowDragger`, nicht über `setExtendedState`; die Taskleiste bleibt frei), Ziehen am maximierten Fenster
  stellt es unter dem Zeiger wieder her, ✕ löst `WINDOW_CLOSING` aus. Headless-Start und Smoke-Test berühren
  das Fenster nicht (`ShellAssembly.createShell` baut nur die Arbeitsfläche; `ShellFrame.content` rendert sie
  ohne Fenster).
- Drawer (`app.ui.sidebar`): Seite „Chats“ mit Suchleiste, „+ Neuer Chat“ (eröffnet eine neue Unterhaltung am
  `ChatService` und schließt die bisherige; im Agent-Modus beendet es die ACP-Session über
  `AgentService.endSession()`; nicht während einer Antwort), Zeilen je Ansicht und Zahnrad für die Einstellungen; Seite
  „Wissensquellen“ listet `sources` (Häkchen, Indexstand, ⟳, ✎-Dialog, „+ Quelle“, ✕ je Zeile) und öffnet
  unten mit „Index …“ den Index-Dialog (`knowledge.indexDirectory`, `knowledge.indexOnStartup`; gilt beim nächsten
  Start). Weitere Seiten kommen als `ChatSidebarTab` dazu.
- Antworten des Assistenten sind Markdown-Blasen wie in askai-java8 `arch` (`app.ui.chat.AssistantMarkdownBubble`
  mit `app.ui.markdown.MarkdownMessageView`): flexmark 0.62.2 (Tabellen, Autolinks, Durchstreichen) wird auf native
  Swing-Komponenten abgebildet (Überschriften, Listen, Zitate, Codeblöcke mit Kopieraktion, Tabellen, Links über
  `DesktopLinkOpener`); `mermaid`-Zäune rendert `mermaid-java` (GraalJS + Batik, ohne Browser, ohne Netz) abseits des
  EDT zu Bildern, ein Klick öffnet den zoombaren `MermaidViewerDialog`. Eine einzelne äußere ```markdown-Umhüllung
  der Antwort fällt weg (`MarkdownResponseNormalizer`). Nutzer-, Hinweis- und Fehlerblasen bleiben
  `SpeechBubblePanel`; beide erfüllen `TranscriptBubble` (comic-controls bleibt abhängigkeitsfrei).
- Fehler des KI-Dienstes bleiben Sprechblasen in Blasengeometrie: Überschrift sichtbar, „Technische Ursache“
  und „Hinweis“ hinter „Details anzeigen“ (`SpeechBubblePanel.setDetails`).
- Einstellungen-Dialog: ebenfalls rahmenlos (Überschrift zieht, ✕ bricht ab), Reiter als Pillen (KI-Dienst,
  KeePass, Netzwerk & Agent; Quellen und Index-Einstellungen liegen in der Drawer-Seite „Wissensquellen“).
- Abnahme-Bilder: `./gradlew :app-swing:runUiScreenshots --args="<Verzeichnis>"` rendert A–J headless (G1/G2 Reiter des Einstellungen-Dialogs, I Quellen-Dialog, J Index-Dialog).

## RAG und Indexierung (AP10)

Der normale Chat bleibt unverändert; RAG legt sich von außen darum. Einstiegspunkte in `application`:

- `application.knowledge.IndexKnowledgeUseCase`: Quelle → discover → load → `KnowledgeChunker` → `EmbeddingPort`
  (Batches) → `KnowledgeIndexPort.replace` je Ressource. Seriell, Fehler je Ressource im `IndexingReport`,
  Abbruch über `IndexingListener`. Der Index führt (AP20-Folge, Contract-Commit D): Eine Ressource, deren bekannte
  Discovery-Revision der gespeicherten (`KnowledgeIndexPort.revisionOf`) gleicht, wird übersprungen
  (`UNCHANGED`, weder geladen noch vektorisiert; unbekannte Revision gilt als verändert). Am Ende eines
  vollständigen `indexSource`-Laufs werden Ressourcen der Quelle, die der Index noch kennt
  (`KnowledgeIndexPort.resourceIds`), die Discovery aber nicht mehr geliefert hat, aus dem eigenen Namespace
  entfernt (`PRUNED`, leeres `replace`; andere Embedding-Welten bleiben unberührt); ein abgebrochener Lauf (auch
  ein Abbruch während der Bereinigung) oder eine gescheiterte Discovery entfernt nichts weiter, eine leere
  gelungene Discovery räumt die Quelle leer. `indexResources` überspringt nichts und bereinigt nicht anhand der
  Discovery; wie bisher entfernt es nur die Chunks einer genannten Ressource, die beim Laden `NOT_FOUND` meldet
  oder leer ist.
- `application.rag.RetrieveKnowledgeUseCase`: Volltext und Cosine im Namespace der konfigurierten
  `EmbeddingModelIdentity`, Fusion per Reciprocal Rank Fusion (`RetrievalSettings`); fällt ein Pfad aus, liefert
  der andere mit Warnung.
- `application.rag.PromptContextAssembler`: nummerierter Kontextblock in Fusionsreihenfolge bis Token-Budget
  oder Quellenzahl (`ContextSettings`).
- `application.rag.RagChatUseCase`: RAG aus = `ChatService.send`; RAG an = Kontext über
  `ChatService.sendWithContext` als System-Anteil nur für diesen Turn, in der Historie bleibt nur die
  Nutzerfrage. Quellen stehen in `RagChatTurn.sources()`.

## MCP-Wissenswerkzeuge (AP20)

Die Wissensfunktionen stehen einem Agenten über MCP zur Verfügung, ausschließlich über Application-Use-Cases:

```
MCP-Client → McpServerRegistry (mcp-solon-runtime) → McpToolContribution (application.mcp)
          → RetrieveKnowledgeUseCase / LoadKnowledgeDocumentUseCase / RefreshKnowledgeSourceUseCase
          → KnowledgeIndexPort / EmbeddingPort / KnowledgeSourcePort → Adapter
```

- `application.mcp.KnowledgeMcpTools(retrieval, documents, refresh, settings)` liefert drei Contributions:
  `search_knowledge` (`query`, optional `max_results`, `source_ids` als kommagetrennte Quell-IDs; hybride Suche,
  je Treffer Titel, Überschrift, Dokument- und Chunk-ID, Quelle, Ort, Stand, RRF- und Rohscores, Textausschnitt),
  `get_knowledge_document` (`id`, optional `source_id`; nur indexierte Dokumente, vollständiger Text frisch aus der
  Quelle, nicht aus dem Index) und `refresh_knowledge_source` (`source_id`; synchroner Abgleich des Index mit der
  Quelle in ihrem `SourceScope`: Neues und Geändertes indexieren, Unverändertes überspringen, Verschwundenes
  entfernen; Zusammenfassung des `IndexingReport` mit Fehlern je Stufe). Parameter sind flach
  (`McpToolParameter`), weil der MCP-Port keine Array-Typen kennt.
- Ergebnisse sind strukturierter Text; Fehler sind `McpToolResult.error` mit knapper Meldung ohne Stacktrace,
  Zugangsdaten oder Token. Meldungen der Port-Ausnahmen (`KnowledgeIndexException`, `KnowledgeSourceException`,
  `RetrievalWarning`) gelangen nie in eine Werkzeugantwort: Sie sind für Log und Anzeige gedacht und können
  Infrastrukturdaten wie Indexpfade nennen; die Antwort nennt nur Suchpfad bzw. Indexierungsstufe.
  `KnowledgeToolSettings` begrenzt Antwortgröße (Default 20.000 Zeichen, Überschreitung wird gekürzt und
  gekennzeichnet), Snippetlänge, Standard-Trefferzahl und die Zahl aufgezählter Fehler.
- Quellen: `application.knowledge.KnowledgeSourceCatalog` aus `KnowledgeSourceRegistration` (Port + `SourceScope`),
  von der Composition Root befüllt. Die Werkzeuge kennen daraus nur die Quell-IDs; Port und Scope nehmen allein die
  Use Cases in die Hand: `LoadKnowledgeDocumentUseCase(catalog, index, space)` lädt nur Ressourcen, die im
  Namespace der konfigurierten Embedding-Welt indexiert sind, und zwar aus der Quelle, unter der sie indexiert
  sind; alles andere ist `KnowledgeDocumentNotIndexedException`, ohne dass eine Quelle gefragt wird.
  `RefreshKnowledgeSourceUseCase(indexing, catalog)` gleicht eine Quelle anhand ihrer ID in ihrem konfigurierten
  Scope mit dem Index ab.
- Der Index führt (Entscheidung Koordinator, empfohlene Option der Entscheidungskarte an Angelo; Codex P1/P2 aus
  PR #24): Der Agent sieht nur den freigegebenen Korpus, also das, was `IndexKnowledgeUseCase` im konfigurierten
  Scope indexiert hat. Der Scope selbst (Startpunkte, Tiefe, Höchstzahl) ist ohne Crawl nicht auf eine ID
  anwendbar, der Index ist sein Abdruck. Die Composition Root verdrahtet deshalb die indexgeführte Variante; der
  Konstruktor `LoadKnowledgeDocumentUseCase(catalog)` ohne Index (Quellen entscheiden per `UNSUPPORTED`) bleibt
  für den Fall, dass Angelo anders entscheidet.
- Lebenszyklus: Die Composition Root registriert `contributions()` mit `McpServerRegistry.updateTools` am Endpoint
  des Agenten (AP21) und ruft beim Abmelden `KnowledgeMcpTools.shutdown()`; eine laufende Aktualisierung bricht
  dann zwischen zwei Ressourcen ab, neue werden abgewiesen. Je Quelle läuft höchstens eine Aktualisierung.

## RAG in der Shell (AP22)

Die Chat-Shell bleibt ohne Port- und Adaptertypen (`ComicUiBoundaryTest`); RAG erreicht sie nur über
Bindings in `app.chat`, die AP23 in der Composition Root verdrahtet.

- `app.chat.RagChatBinding` ersetzt `ChatServiceBinding` als `ChatShellActions`: RAG aus = `RagChatUseCase`
  mit `RagOptions.disabled()` (exakt der bisherige Weg); RAG an = Suche auf dem Arbeits-Executor, solange zeigt
  die leere Antwortblase eine Aktivität ("Wissen wird gesucht …"); danach hängen die Quellen als
  `app.ui.chat.SourceReference` (Nummer, Titel, Überschrift, Ort, Stand, Score, Ränge) an der Antwort, Warnungen
  und ein Ausfall der Suche werden eine eigene Hinweis-Blase (`TranscriptEntry.Author.NOTICE`). Stop während
  der Suche wird gemerkt und bricht den Turn ab, sobald er existiert; die Nutzerfrage bleibt in der Historie.
- `app.chat.KnowledgeIndexingBinding` treibt `IndexKnowledgeUseCase` auf dem Arbeits-Executor und meldet
  Fortschritt, Ergebnis und Abbruch an `app.ui.chat.KnowledgeStatusModel`; die Statuszeile
  (`KnowledgeStatusBar`) über dem Composer der Chat-Ansicht zeigt den Text und einen Abbrechen-Knopf, der
  `IndexingListener.isCancelled()` bedient.
- Oberfläche: `SourceListPanel` (einklappbare Quellenliste unter der Antwort, Ort unverändert und ohne
  Zugangsdaten), Hinweis-Blase links in der Aktivitätsfarbe, Fehler des KI-Dienstes wie bisher. Modelle
  (`ChatShellModel`, `KnowledgeStatusModel`) bleiben ohne Swing.
- Streaming: `ChatTranscriptPanel` bündelt Deltas (höchstens eine Blasen-Aktualisierung je 30 ms, Abschluss,
  Abbruch, Fehler und Quellen sofort) und hängt Text an, statt ihn neu zu setzen. Die Markdown-Blase der Antwort
  rendert gedrosselt neu (`MarkdownMessageView`: frühestens nach 90 ms, bei teuren Renderings nach dem Dreifachen
  der gemessenen Renderzeit, höchstens 2 s; Mermaid erst nach dem Abschluss); `SpeechBubblePanel`
  (comic-controls) schreibt seine Breiten- und Umbruchmessung über `StreamingTextMeasure` je abgeschlossenem
  Wort und je Zeile fort, auch ohne Zeilenumbrüche. Die Zeit je Delta wächst damit nicht mit der Textlänge
  (`ChatTranscriptStreamingTest`: 20.000 Deltas mit und ohne Zeilenumbrüche, `StreamingTextMeasureTest`).
- Suche und Abbruchwunsch gehören zur jeweiligen Anfrage (`RagChatBinding.Request`); späte Quellen einer schon
  fertigen Antwort stören die nächste Suche nicht. Lehnt der Arbeits-Executor einen Auftrag ab, wird die
  Antwort als gescheitert geschlossen bzw. die Statuszeile zurückgesetzt, damit nichts offen bleibt.
- Pflichttest `app.chat.RagShellIntegrationTest`: Shell → Bindings → Use Cases → echte Adapter
  `OpenAiCompatibleChatAdapter`/`OpenAiCompatibleEmbeddingAdapter` gegen lokale Fake-HTTP-Server für
  `/chat/completions` und `/embeddings` (`app.chat.fakeapi`) → `LuceneKnowledgeIndex` im temporären
  Verzeichnis, `InMemoryKnowledgeSource` als Quelle. Lokaler Start: `./gradlew :app-swing:runRagDemo`.

## Composition Root und Konfiguration (AP23)

`app-swing` ist die einzige Composition Root. `EnterpriseAiClientMain` öffnet die Protokolldatei
(`AppLogFile`, `java.util.logging` rollierend unter `<Anwendungsverzeichnis>/logs/`), lädt die Konfiguration,
installiert Vertrauens- und Proxy-Regel, baut die Adapter, komponiert den Graphen, registriert den
Shutdown-Hook und zeigt das Fenster.

```
enterprise-ai-client.properties ──AppConfigLoader──▶ AppConfig (Snapshots, ohne Secrets)
        │                                              │
        ▼                                              ▼
TrustPolicy (SSLSocketFactory je Verb.)   AdapterAssembly ──▶ ApplicationPorts (Chat, Embedding, Index, Quellen,
NetworkServices (HttpRoutes je Ziel, TLS je Verbindung)
                                                            SecretProvider, AgentBackend, Schließreihenfolge)
                                                            │
                                       CompositionRoot ◀────┘  Use Cases (AP2/AP10/AP20), Bindings (AP22),
                                            │                  AgentService mit AcpAgentLauncher (AP21),
                                            │                  StartupIndexing, ShutdownSequence
                                       ShellAssembly ──▶ ChatShellPanel + RagChatBinding, Agent-Ansicht, JFrame
```

- **Pakete**: `app.config` (Snapshots `AppConfig`, `ChatConfig`, `EmbeddingConfig`, `KnowledgeConfig`,
  `SourceConfig`, `KeePassConfig`, `NetworkConfig`, `AgentConfig`; `AppConfigLoader`, `AppPaths`), `app.net`
  (`HttpRoutes`, `NetworkServices`, `TrustPolicy`, `ConnectionDiagnosis`), `app.security` (Brücken zum Security-Port,
  `FilePairingKeyStore`, `SwingPairingCallback`),
  `app.ui.security` (`KeePassPairingDialog`, reine Oberfläche), `app.ui.settings` (Einstellungen-Dialog,
  reine Oberfläche über dem Formular `SettingsForm` und dem Vertrag `SettingsDialogActions`; dazu `SourceDialog`
  über `SourceForm`/`SourceActions` und `IndexDialog` über `IndexForm`/`IndexActions` für die Drawer-Seite
  „Wissensquellen“), `app.ui.workspace`
  (`ChatWorkspacePanel` mit Hamburger, Modus-Pille und Drawer, `ShellFrame` rahmenloses Hauptfenster,
  `WorkspaceActions`, `KnowledgeSourceItem`), `app.ui.sidebar` (`ChatSidebarPanel`, `SidebarTabRibbon`,
  `ChatHistoryRow`, `ChatSidebarTab`; Ports aus askai-java8 `arch`), `app.settings`
  (`ConfigurationFile`, `SettingsMapper`, `FileSettingsActions`, `FileSourceActions`, `FileIndexActions`,
  `ConfigurationStartup`, `ConfigurationCheck`:
  Dialog ↔ Datei ↔ `AppConfigLoader`; `ConnectionProbe` und `ConnectionChecker`: der Verbindungstest des Dialogs
  über `app.net`), `app.knowledge` (`StartupIndexing`), `app.composition`
  (`AdapterAssembly`, `ApplicationPorts`, `CompositionRoot`, `ShellAssembly`, `ShutdownSequence`,
  `StartupNotices`, `LoggingKnowledgeSource` als Protokollhülle um jede Wissensquelle, `SettingsAssembly`,
  `KeePassSecretChecker`, `ServiceConnectionChecker`).
- **Konfiguration**: eine Properties-Datei im Benutzerverzeichnis (`~/.enterprise-ai-client/` bzw.
  `%APPDATA%`, überschreibbar mit `-Denterpriseai.home` und `-Denterpriseai.config`), eingebaute Defaults
  (AP10-Retrieval/Kontext, Adapter-Timeouts), Fehlermeldungen nennen Schlüssel und Erwartung, nie den Wert;
  unbekannte Schlüssel werden als Warnung gemeldet. Fehlt die Datei, legt die Anwendung die kommentierte
  Vorlage `enterprise-ai-client.example.properties` ab und öffnet mit Oberfläche den Einstellungen-Dialog
  (Erststart ohne Handarbeit; headless: Hinweis und Exit-Code 2). Der Dialog (Zahnrad im Fuß der
  Drawer-Seite „Chats“) schreibt nur seine Schlüssel zeilenschonend in dieselbe Datei; Änderungen gelten beim nächsten
  Start. Modellnamen, Dimension (e5-base
  vermutlich 768, UNVERIFIED), Proxy und Quellen sind reine Konfiguration.
- **Secrets**: in der Datei stehen nur `SecretRef`s (Titel des KeePass-Eintrags): `chat.apiKeyRef`,
  `embedding.apiKeyRef`, `source.<id>.credentialRef`, `…clientCertificate.keyStorePasswordRef`.
  `SecretBackedTokenSource` (Chat) und `SecretBackedBearerTokenSource` (Embedding) lösen den API-Key je Anfrage
  mit `SecretProvider.withSecret` auf; das Material lebt nur für den Aufruf, kein Feld hält es. Der
  `SecretProvider` ist `KeePassRpcSecretProvider`; sein Pairing-Callback öffnet modal den Comic-Dialog
  (`SwingPairingCallback` → `KeePassPairingDialog`, headless = Abbruch), der Pairing-Schlüssel liegt in
  `keepassrpc-pairing.key` im Benutzerverzeichnis (`FilePairingKeyStore`, Rechte nur für den Besitzer, atomar
  ersetzt, beim Verwerfen überschrieben); `security.keepass.pairingKeyStore=memory` behält den Schlüssel nur im
  Prozess. Ohne KeePass (`security.keepass.enabled=false`) startet die Anwendung mit einem
  `UnavailableSecretProvider`, zeigt beim Start, dass Secrets fehlen, und jede Anfrage scheitert mit einem
  Authentifizierungsfehler. Bekannte Grenze: der Chat-Adapter verlangt den Token als `String`, der sich nicht
  überschreiben lässt; jede Anfrage holt den Key neu über KeePassRPC (kein Cache erlaubt).
- **Netz**: `HttpRoutes` implementiert den Port `HttpRoutePort` (Modul `http-api`) mit win-proxy-java 0.2.0: Route je Ziel, Cache, harter Timeout, nie auf dem EDT, NOT_IMPLEMENTED/ERROR als nicht verfügbare Route statt DIRECT. Jeder Adapter (Chat, Embeddings, MediaWiki, Confluence) übergibt Route, `SSLSocketFactory` (win-trust-java 0.1.0) und User-Agent je `HttpURLConnection`; kein globaler `ProxySelector`. Confluence zusätzlich
  mit Timeouts und optionalem Client-Zertifikat (Windows-MY per Alias oder PKCS12 mit Passwort über `SecretRef`,
  `ClientCertificateFactory`). `TrustPolicy` (`network.tls.*`) vereint über win-trust-java JVM-Truststore, unter Windows
  Windows-ROOT und Windows Root+Intermediate sowie optional eine CA-Datei zu einem Trust-Manager; die
  `SSLSocketFactory` wird je Verbindung gesetzt, nie global (Confluence mit Client-Zertifikat nutzt denselben
  Trust-Manager). `ConnectionDiagnosis` macht aus
  der Ausnahmekette einer gescheiterten Anfrage die Zeilen `Technische Ursache:` und `Hinweis:` der Fehlerblase
  (Paketnamen entfernt, Tokens maskiert); die Bindings loggen jeden Fehler mit Stacktrace. Kein gemeinsamer
  Transport.
- **Agent-Modus**: `AgentBackend` (ACP-Connector, Startbeschreibung, `SolonMcpServerRuntime`, Endpoint-
  Definition) nur bei `agent.enabled=true`; `CompositionRoot` baut `AcpAgentLauncher` mit den Contributions
  von `KnowledgeMcpTools` (AP20) und `AgentService`. Der MCP-Token entsteht allein im Launcher je
  Agentenprozess und wird nie geloggt. `KnowledgeToolSettings` kommen aus `agent.tools.*`.
- **Shutdown** (`ShutdownSequence`, idempotent, Fenster-Schließen und JVM-Shutdown-Hook): laufende Chat-Turns
  abbrechen → Agent-Modus beenden (`KnowledgeMcpTools.shutdown()`, `AgentService.close()`, Endpoint abgemeldet)
  → Startindexierung abbrechen und abwarten → Ports schließen (`SolonMcpServerRuntime.shutdown()` +
  `stopSharedServer()`, dann `LuceneKnowledgeIndex.close()`) → Executor stoppen.
- **Indexierung**: `knowledge.indexOnStartup=true` indexiert alle konfigurierten Quellen nacheinander über die
  `KnowledgeIndexingBinding` (AP22), sichtbar in der Statuszeile mit Abbrechen-Knopf; Berichte ins Log.
- **Tests**: `ApplicationCompositionTest` baut den ganzen Graphen headless mit Fakes (Chat, Embedding, Index,
  Quelle, In-Process-MCP-Registry, Fake-ACP-Connector) und prüft Indexierung, Chat- und RAG-Roundtrip durch die
  Shell, Agent-Werkzeuge und Shutdown-Reihenfolge; `AdapterAssemblyTest` baut die echten Adapter aus der
  Beispielkonfiguration ohne Netz; dazu `AppConfigLoaderTest`, `FilePairingKeyStoreTest`,
  `SecretBackedTokenSourcesTest`, `HttpRoutesTest` (lokales PAC-Skript mit
  Sentinel-Proxy, Cache, Rückfall), `TrustPolicyTest` (lokaler HTTPS-Server mit selbstsigniertem Zertifikat),
  `ConnectionDiagnosisTest`, `AppLogFileTest`, `ShutdownSequenceTest`, `StartupIndexingTest`.
  Architekturregel `CompositionRootBoundaryTest`.
- **Start**: `./gradlew :app-swing:run` (optional `-Denterpriseai.config=<Datei>`); Demos mit Fakes:
  `runChatDemo`, `runRagDemo`, `runAgentDemo`.

## Integrations- und Vertical-Slice-Tests (AP25)

`integration-tests` ist das zweite Modul ohne Produktionscode (Rolle `INTEGRATION_TESTS`, von keinem Modul
referenziert). Sein Test-Klassenpfad sieht alle Module einschließlich `app-swing` sowie die Testfixtures der
Stränge; damit prüft es die Slices A–G des Auftrags Ende-zu-Ende gegen lokale Fakes auf `127.0.0.1`
(Fake-`/chat/completions` und `/embeddings`, Fake-MediaWiki und -Confluence hinter JDK-HttpServern,
Fake-KeePassRPC über WebSocket, Demo-Agent als Kindprozess, Solon-MCP-Server). Zuordnung Slice → Test,
Entscheidungen und der getrennte Lauf gegen echte Dienste (`liveTest`, standardmäßig aus) stehen in
[integration-tests/README.md](integration-tests/README.md).

Regeln, die daraus folgen:

- **Fakes der Stränge liegen in `src/testFixtures`** ihres Moduls (`java-test-fixtures`), unverändert im
  Paket, `public`. Produktionscode sieht Testfixtures weiterhin nicht (AP24); ein Modul-`test` darf sie
  weiter paketprivat nutzen, weil Fixtures auf dem eigenen Test-Klassenpfad liegen.
- **Slice G braucht keinen MCP im Demo-Agenten.** `acp-demo-agent` bleibt ohne Projektabhängigkeiten; der
  wissensnutzende Testagent `KnowledgeDemoAgentMain` ist Testcode von `integration-tests` und startet als
  Kindprozess über ein Pathing-Jar auf dem Test-Klassenpfad. Er erfährt den Endpoint über
  `ENTERPRISE_AI_MCP_*` (AP21) und spricht MCP nur über `McpToolClientFactory` und `SolonMcpToolClientFactory`.
  Die Übergabe des MCP-Servers per ACP `session/new` bleibt optionale Restarbeit (Contract-Änderung in
  `acp-client-api` und `acp-solon-client`).
- **Solon ist prozessglobal**: `forkEvery 1` für das Modul, `SolonMcpServerRuntime.stopSharedServer()` in
  `@AfterClass` jeder Klasse mit MCP-Server.
- **Sicherheitsregeln gelten in Tests**: Token und Passwörter werden in Sprechblasen, Transkripten,
  Berichten, Ausnahmen, `toString()` und im mitgelesenen STDERR der Agentenprozesse gesucht und dürfen dort
  nicht vorkommen; MCP-Endpoints liegen nur auf `127.0.0.1`.

## Build-Konventionen

- **Java 8**: `sourceCompatibility`/`targetCompatibility` 1.8; auf JDK 9+ zusätzlich `javac --release 8`,
  damit Java-9+-APIs (z. B. `List.of`) schon beim Kompilieren scheitern. Bytecode Major 52.
- **Gradle 8.14.3** (Wrapper eingecheckt). Die letzte Gradle-Linie, die selbst noch auf Java 8 läuft;
  Gradle 9 bräuchte Java 17 zum Ausführen. Gradle meldet auf JVMs unter 17 eine Deprecation-Warnung, die
  bis zu einem Gradle-9-Umstieg erwartet ist.
- **Zentral** im Root-`build.gradle`: Java-Level, Encoding UTF-8, `-Xlint:all`, JUnit 4 für alle Module.
- **Versionen** zentral in `gradle/libs.versions.toml` (Lucene 8.11.3, org.noear 3.10.1 für acp-sdk/solon/
  solon-boot-jdkhttp/solon-ai-mcp, JUnit 4.13.2, ArchUnit 1.4.1 u. a.). Aktiviert wird eine Bibliothek nur
  im `build.gradle` des Moduls, das sie braucht (`implementation libs.lucene.core`). Neue Einträge in der
  Datei sind erlaubt (nur anhängen).
- **Tests**: JUnit 4.13.2 überall (wie askai-java8, dessen ACP-/MCP-/Comic-Tests so ohne Umbau portierbar
  sind); ArchUnit 1.4.1 (Kernbibliothek, wie corenth) nur in `architecture-tests`. Unit-Tests ohne laufende
  externe Dienste; echte Protokollgrenzen über lokale Fake-Server. Lightweight-Swing-Tests laufen headless;
  displayabhängige Tests nutzen `Assume`.
- **CI**: `.github/workflows/build.yml` baut mit JDK 8 und JDK 21. `.github/workflows/release.yml` baut das
  Fat Jar von `app-swing` (`gradle/fat-jar.gradle`, `projectVersion` aus `gradle.properties`) und veröffentlicht es:
  auf `main` als Release `v<version>`, auf anderen Branches als rollierenden Snapshot (docs/einrichtung.md).

## Zusammenarbeit der Stränge

| Strang | Module | APs |
|---|---|---|
| A | chat-api, chat-openai, `domain.chat` | AP2 → AP3 |
| B | app-swing, comic-controls | AP4 |
| C | embedding-api, embedding-openai, `domain.embedding` | AP5 → AP6 |
| D | knowledge-api, knowledge-lucene, `domain.knowledge` | AP7 → AP8 → AP9 |
| E | source-api, source-mediawiki, `domain.source` | AP11 → AP12 |
| F | security-api, security-keepassrpc, source-confluence, `domain.security` | AP13 → AP14 → AP15 |
| G | acp-client-api, acp-solon-client, acp-demo-agent | AP16 → AP17 |
| H | mcp-runtime-api, mcp-solon-runtime | AP18 → AP19 |

Danach: AP10 (application, braucht A + C + D), AP20 (application + MCP), AP21 (A + G + H), AP22 (A + B +
AP10), AP23 (app-swing), AP24 wächst mit jedem Modul, AP25 (integration-tests), AP26.

Regeln:

- **Contracts zuerst.** Ports (`*-api`) und domain-Typen werden zuerst festgelegt und gemergt, Adapter
  bauen darauf auf.
- **Öffentliche Ports nicht eigenmächtig ändern**, sobald ein anderer Strang darauf aufbaut. Nötige
  Änderungen werden abgestimmt und als kleiner, eigener Contract-Commit/PR umgesetzt.
- **Nur im eigenen Modulbereich arbeiten.** `settings.gradle`, Root-`build.gradle`, `ModuleRegistry` und
  das Wurzelpaket von `domain` sind gemeinsame Verträge und werden nur per Contract-Commit geändert.
- **Ein Branch und ein PR je Strang.** Build und Tests (`./gradlew build`, inklusive Architekturtests) sind
  vor dem Merge grün. Wer wartet, verbessert Tests, Doku oder Grenzprüfungen des eigenen Strangs statt
  fremde Module umzubauen.

## Herkunft

Kurzfassung; die ausführliche Tabelle je Arbeitspaket mit den Änderungen gegenüber der Vorlage steht in
[docs/herkunft.md](docs/herkunft.md).

| Konzept / Datei | Ursprung |
|---|---|
| Java-8-Multiprojekt, `release 8` auf neueren JDKs, `FAIL_ON_PROJECT_REPOS`, `java-library` | aresstack/corenth (`build.gradle`, `settings.gradle`) |
| Architekturtest-Modul mit expliziter Modulliste, die neue Module erzwingt; ArchUnit 1.4.1; Klassenverzeichnisse per Systemeigenschaft | aresstack/corenth (`architecture-tests`), hier in eine Java-Registry plus Gradle-Build-Modell überführt und um Gradle-Abhängigkeitsprüfungen und Selbsttests erweitert |
| Modulschnitt `acp-client-api` / `acp-solon-client` / `acp-demo-agent`, `mcp-runtime-api` / `mcp-solon-runtime`, `comic-controls`; JUnit 4.13.2; Versionen acp-sdk/solon 3.10.1, Lucene 8.11.3, slf4j-nop | Miguel0888/askai-java8 |
| Versionen Gson 2.10.1, OkHttp 4.9.3, jsoup 1.17.2, JWBF 3.1.1, Java-WebSocket 1.5.2 | Miguel0888/MainframeMate (`app`, `wiki-integration`) |
| MCP-Werkzeugkatalog als Fabrik von `McpToolContribution`s mit Fehlern als Ergebnis statt Exception und Auflösung des Ziels vor dem Aufruf (`application.mcp`) | Miguel0888/askai-java8 (`ResearchBotDirectoryTools`, `ResearchBotSessionTools`) |
| Parameter `query`/`maxResults`/`sources`, Snippet-Grenze und Gesamtgrenze 20.000 Zeichen der Wissenswerkzeuge | Miguel0888/MainframeMate (`SearchIndexTool`, `ReadChunksTool`) |
| Konfiguration als Properties-Datei im Benutzerverzeichnis mit Pfad-Override per System-Property, unveränderliche Snapshots, Proxy-Modi System/keiner/manuell (`app.config`, `app.net`) | Miguel0888/askai-java8 (`AppConfigurationRepository`, `AskAiPaths`, `ProxyConfiguration`) |
| Proxy-Auflösung (`app.net.HttpRoutes`, alle Modi) | Bibliothek aresstack/win-proxy-java 0.2.0 und win-trust-java 0.1.0 (wie in corenth `network-winproxy` und askai-java8 `ProxyConfiguration`); Cache je Host und Rückfall auf Systemeinstellungen sind neu |
| Settings-Schlüssel für Proxy, Timeouts, mTLS (Windows-MY-Alias), KeePassRPC-Verdrahtung mit Pairing-Dialog und Zugangsdaten je Aufruf (`app.security`, `app.ui.security`) | Miguel0888/MainframeMate (`Settings`, `KeePassProvider`, `KeePassRpcPairingDialog`, `MvsBrowser`-Proxy) |
| Composition Root als einziger Ort für Adapterkonstruktoren, Secret-Material verlässt den Aufruf nicht (`app.composition`, `CompositionRootBoundaryTest`) | aresstack/corenth (Composition Root, `adyton`) |

## Weiterführende Dokumentation

| Seite | Inhalt |
|---|---|
| [docs/einrichtung.md](docs/einrichtung.md) | Bauen, erster Start, Konfigurationsdatei, Demos, IDE, CI |
| [docs/module.md](docs/module.md) | Modulübersicht mit Paketen, Bibliotheken und Testfixtures |
| [docs/architektur.md](docs/architektur.md) | Modulgraph und Laufzeitsicht als Mermaid, Regeln je Testklasse |
| [docs/konfiguration-api.md](docs/konfiguration-api.md), [-keepass](docs/konfiguration-keepass.md), [-mediawiki](docs/konfiguration-mediawiki.md), [-confluence](docs/konfiguration-confluence.md) | Konfiguration je Bereich |
| [docs/rag-datenfluss.md](docs/rag-datenfluss.md) | Indexierung, Retrieval, Kontext, „Der Index führt“ |
| [docs/acp.md](docs/acp.md), [docs/mcp.md](docs/mcp.md) | Agent-Modus und Wissenswerkzeuge |
| [docs/tests.md](docs/tests.md) | Testanleitung |
| [docs/live-verifikation.md](docs/live-verifikation.md) | Live-Verifikation gegen echte Dienste in sieben Stufen (`liveTest`, `-Dlive.stage`, GitHub-Actions-Workflow), Ergebnisprotokoll |
| [docs/herkunft.md](docs/herkunft.md) | Herkunftstabelle |
| [docs/einschraenkungen.md](docs/einschraenkungen.md) | Bekannte Einschränkungen, UNVERIFIED, Restarbeit |

## Lokaler Java-21-Sidecar (`local-model-runtime-sidecar/`)

Eigener Gradle-Build, **nicht** Teil von `settings.gradle`: übernommen aus askai-java8 (Branch `arch`,
`local-model-runtime-sidecar-java21`), läuft als separater Java-21-Prozess auf 127.0.0.1 und ist nur über
`model-sidecar` erreichbar (Katalog `GET /api/tags`, Sprachausgabe `POST /v1/audio/speech`). Für ihn gelten die
Java-8- und Architekturregeln des Clients nicht. Neu gegenüber arch: Sprachausgabe, kein externes Programm.
Schichten im Paket `speech`: `LocalVoiceStore` erkennt Stimmen je Ordner über `VoiceFormatReader`
(Piper `<name>.onnx` + `<name>.onnx.json` aus `rhasspy/piper-voices`, oder VITS im Hugging-Face-Layout),
`VoiceTextEncoder` macht daraus Token-Ids (Piper: Phonem-Ids aus `phoneme_id_map`; `phoneme_type text` direkt,
`espeak` nur Deutsch über den regelbasierten `GermanPhonemizer` als Näherung an espeak-ng, andere Sprachen werden
nicht angeboten), `LocalSpeechRuntime` rechnet. `OnnxSpeechRuntime` (ONNX Runtime als Java-Bibliothek) ist nur das
Pilot-Backend; ONNX-Typen bleiben in dieser Klasse, eine eigene Inferenz-Engine ersetzt sie dort. Der Sidecar lädt
nichts herunter: Stimmen installiert der Client. CI baut ihn im Job `sidecar`
(JDK 21), das Release legt `local-model-runtime-sidecar-<version>.zip` neben das Fat Jar.

