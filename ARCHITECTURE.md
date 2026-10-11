# Architektur

Modularer Java-8-Desktop-KI-Client in Ports-and-Adapters-Architektur. Diese Datei ist die verbindliche
Arbeitsgrundlage für alle Stränge (A bis H) und für die späteren Integrationspakete. Die Regeln hier sind
in `architecture-tests` ausführbar; was dort nicht geprüft wird, ist hier als Konvention festgehalten.

Einstieg und ausführliche Erklärungen stehen in [README.md](README.md) und unter [docs/](docs/README.md)
(Einrichtung, Modulübersicht, Diagramme, Konfiguration, RAG, ACP, MCP, Tests, Herkunft, Einschränkungen).
Bei Widersprüchen gilt der Code auf `main`, danach diese Datei, danach die Seiten unter `docs/`.

## Die Stadt: Hierarchie und Namen aus corenth

Die Architekturreferenz ist [aresstack/corenth](https://github.com/aresstack/corenth). corenth ist als kleine
griechische Stadt (Korinth) modelliert. Die Metapher ist nicht dekorativ: Die griechischen Namen sind
hierarchische Architekturgrenzen und keine Ersatznamen für technische Module. Der Client übernimmt die
Gradle-Hierarchie aus corenth (`settings.gradle`), und die Pakete folgen dem Pfad
(`com.aresstack.enterpriseai.astu.acropolis.chalcotheca.tamias`). Wo corenth keinen Namen vorgibt, bleibt der
technische Name, aber das Modul liegt dort in der Stadt, wohin es nach seiner Verantwortung gehört
(`proasteion:emporion:holkas:source-confluence`). Erfunden wird kein Name.

```
adyton                                ἄδυτον     Tresor: Zugangsdaten, delegierter Zugriff          ← security-api
astu                                  ἄστυ       Innenstadt: Kernmodell                             ← domain
└── acropolis                         ἀκρόπολις  Burg: Lebenszyklus, Suche, Use Cases               ← application
    └── chalcotheca                   χαλκοθήκη  Bronze-Archiv und Archivschalter                   ← resource-api, knowledge-api, application.resource
        ├── tamias                    ταμίας     Wächter am Archivschalter (ResourceAccessPolicy)   ← application.resource.policy
        ├── anagraphai                ἀναγραφαί  Register: Lucene-Volltext (lexikalisch)            ← knowledge-lucene
        └── pinakes                   πίνακες    Kataloge: Vektorindex, Fusion (KnowledgeIndexPort) ← knowledge-lucene
proasteion                            προάστειον Vorstadt: Ports und Adapter, zeigt nur nach innen
├── application                                  Kompositionspunkt ohne Oberfläche (ADR-0001)       ← app-swing (composition, config, net, security, …)
├── exedra                            ἐξέδρα     Swing-Schale                                       ← app-swing
├── katagogion                        καταγώγιον Herberge: Plugins, Tools, MCP                      ← mcp-runtime-api
│   └── mcp-solon-runtime
├── emporion                          ἐμπόριον   Hafen
│   ├── holkas                        ὁλκάς      Frachtschiff: Connectoren holen Rohressourcen      ← resource-holkas
│   │   └── source-mediawiki, source-confluence, source-localfiles, source-ftp, source-ndv
│   └── deigma                        δεῖγμα     Prüfhalle: Erkennung, flache Extraktion            ← document-api
│       └── document-tika
└── platform
    ├── network                                                                                    ← http-api
    └── security-keepassrpc
```

Regeln aus corenth, die hier gelten: proasteion → astu → adyton-Abstraktionen, nie umgekehrt. Holkas wird nicht
direkt gerufen, sondern über den Archivschalter der chalcotheca, der Tamias jede Operation entscheiden lässt.
Deigma transportiert und indexiert nicht. Exedra bleibt dünn (corenth ADR-0001, `EXEDRA_MUST_STAY_THIN_UI_SHELL`):
`proasteion:application` ist der einzige Kompositionspunkt. Er liest die Konfiguration, setzt Netz, Tresor-Brücken,
Adapter, Index und Use Cases zu `ApplicationPorts` zusammen (`AdapterAssembly`), baut den Graphen
(`CompositionRoot` mit Agent-Host, Vorlader, MCP-Werkzeugen) und startet ihn ohne Oberfläche (`ApplicationStart`:
Netz, Tresor, Settings-Service, Übernahme gespeicherter Einstellungen). exedra baut darauf nur die Swing-Schale
(`ShellAssembly`, Bindings, Statuszeile) und liefert die Dialoge, die application über `UserPrompts` erfragt (Zugang
„Manuell“, KeePass-Pairing als `PairingPrompt`). exedra kennt keinen Adapter. anagraphai ist das lexikalische Register und kennt pinakes
nicht; pinakes hält den Vektorindex und verbindet beide per RRF zum Index-Adapter.

Architekturtests: Ein Modul besitzt die Klassen seines Basispakets ohne die darin geschachtelten Module
(`ArchitectureModule.classes()`). Überdecken dürfen sich Basispakete nur entlang der Stadt
(`ModuleRegistryTest.CITY`). proasteion, emporion und platform sind Grenzmodule ohne Klassen (`ModuleKind.BOUNDARY`).
Ein Port darf einen in ihm liegenden Port sehen (chalcotheca → tamias). Ein Adapter darf ein Nachbar-Register im
selben Elternmodul nutzen (pinakes → anagraphai); das Register setzt dann selbst keinen Port um. application ist der
einzige COMPOSITION_ROOT (ohne Oberfläche); exedra ist UI_SHELL und sieht application, Kern, Ports und
comic-controls, aber keinen Adapter und nicht den Tresor adyton (`CompositionRootBoundaryTest.exedraKnowsNoAdapter`):
Passwortverwaltung, Proben und Zugang „Manuell“ laufen über `proasteion.application.security.KeePassAccess` und
`AskedSecretProvider.CredentialPrompt`; exedra liefert nur die Dialoge.

Noch nicht wie corenth:
- source-api bleibt außerhalb der Stadt: acropolis fragt die Quellen-Ports heute direkt statt über den
  Archivschalter. Läge der Port im Außenring, zeigte acropolis nach außen.
- propylaea (tiefe Quelltext-Analyse) hat im Client kein Gegenstück.
- network-winproxy ist kein eigenes Modul: Die Proxy-Auflösung (`proasteion.application.net.HttpRoutes`) liegt im
  Kompositionspunkt, weil Konfiguration (`NetworkConfig`) und Einstellungen-Dialog den Typ `ProxyMode` aus
  win-proxy-java direkt verwenden; ein eigenes Modul braucht zuerst einen eigenen Proxy-Modus-Typ.
- Keine eigenen Stadt-Namen: chat-*, embedding-*, model-*, speech-*, text-*, acp-*, research-*, comic-controls.

## Schichten und Richtung

```
             exedra  (Swing-Schale) → application (Kompositionspunkt)
            /    |     \
   Adapter       |      comic-controls (Swing/Java2D-Bibliothek)
   (*-openai,    |
    anagraphai, source-*, security-keepassrpc,
    acp-solon-client, mcp-solon-runtime)
            \    |
             acropolis   (Use Cases, Orchestrierung)
                 |
      Ports (*-api)  →  astu   (reine Fachobjekte)
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

- `astu → irgendein anderes Modul` (astu hat keine Abhängigkeiten)
- `*-api → Adapter` und `*-api → acropolis`
- `acropolis → konkreter Adapter` (acropolis kennt nur Ports)
- `Adapter → anderer Adapter`, `Adapter → acropolis`, `Adapter → exedra`
- jede Abhängigkeit auf `exedra`, `acp-demo-agent` oder `architecture-tests`

## Module

Basispaket aller Module: `com.aresstack.enterpriseai`. Jedes Modul besitzt genau ein Basispaket; Klassen eines
Moduls liegen nur dort (geprüft).

| Modul | Rolle | Basispaket (`com.aresstack.enterpriseai.`) | darf sehen | Strang / AP |
|---|---|---|---|---|
| `astu` | DOMAIN | `astu` | – | Kern; Unterpakete je Strang, siehe unten |
| `acropolis` | APPLICATION | `astu.acropolis` | astu, alle `*-api`, chalcotheca, tamias | AP10, AP20, AP21, AP23 (Use Cases) |
| `chat-api` | PORT | `chat.api` | astu | A (AP2) |
| `chat-openai` | ADAPTER | `chat.openai` | astu, chat-api | A (AP3) |
| `embedding-api` | PORT | `embedding.api` | astu | C (AP5) |
| `embedding-openai` | ADAPTER | `embedding.openai` | astu, embedding-api | C (AP6) |
| `chalcotheca` | PORT | `astu.acropolis.chalcotheca` | astu, tamias | D (AP7, AP8): Index-Port; Ressourcen (0.1.16): AcquisitionPort, Bronze, Archivschalter MediatedResourceService |
| `tamias` | PORT | `astu.acropolis.chalcotheca.tamias` | astu | Wächter am Archivschalter: ResourceAccessPolicy, ClientResourceAccessPolicy (0.1.16) |
| `anagraphai` | ADAPTER | `astu.acropolis.chalcotheca.anagraphai` | astu, chalcotheca | D (AP9): lexikalisches Register (Lucene) |
| `pinakes` | ADAPTER | `astu.acropolis.chalcotheca.pinakes` | astu, chalcotheca, anagraphai | D (AP9): Vektorindex und Index-Adapter (RRF) |
| `source-api` | PORT | `source.api` | astu | E (AP11) |
| `source-mediawiki` | ADAPTER | `proasteion.emporion.holkas.mediawiki` | astu, source-api, network | E (AP12) |
| `source-confluence` | ADAPTER | `proasteion.emporion.holkas.confluence` | astu, source-api, adyton, network | F (AP15) |
| `source-localfiles` | ADAPTER | `proasteion.emporion.holkas.localfiles` | astu, source-api, deigma | Dateien (0.1.10) |
| `source-ftp` | ADAPTER | `proasteion.emporion.holkas.ftp` | astu, source-api, adyton | FTP/MVS-Quelle (COBOL in PDS-Membern) und JES-Jobausgaben; Commons Net, Anmeldung über KeePass |
| `source-ndv` | ADAPTER | `proasteion.emporion.holkas.ndv` | astu, source-api, adyton | Natural-Quellen über NDV (NATSPOD/PAL aus MainframeMate, Anmeldung über KeePass) |
| `chalcotheca` | PORT | `resource.api` | domain | Ressourcenschicht aus corenth: AcquisitionPort, Bronze (0.1.16) |
| `holkas` | ADAPTER | `proasteion.emporion.holkas` | astu, chalcotheca, source-api | Holkas-Connectoren über den Quellen (0.1.16) |
| `deigma` | PORT | `document.api` | domain | Dokumente (corenth deigma) |
| `document-tika` | ADAPTER | `proasteion.emporion.deigma.tika` | deigma | Dokumente (Tika) |
| `network` | PORT | `proasteion.platform.network` | astu | N (Netz) |
| `model-api` | PORT | `model.api` | astu | Modellverwaltung |
| `model-kipitz` | ADAPTER | `model.kipitz` | astu, model-api, network, speech-api | Modellverwaltung, Sprachausgabe |
| `model-huggingface` | ADAPTER | `model.huggingface` | astu, model-api, network | Lokale Stimmen installieren und auf dem Hub suchen (huggingface4j, ohne Umwandlung) |
| `speech-api` | PORT | `speech.api` | astu | Sprachausgabe (TTS über die Quelle des gewählten Modells: KIPITZ oder lokale Stimme im Prozess) und Spracheingabe (STT, heute nur lokal im Prozess) |
| `speech-runtime-api` | PORT | `speech.runtime` | astu | Inferenz-Laufzeit lokaler Stimmen (Token-Ids zu Audio), austauschbar |
| `speech-local` | ADAPTER | `speech.local` | astu, speech-api, speech-runtime-api | Lokale Stimmen im Prozess (Piper, VITS aus dem Modellverzeichnis) |
| `speech-onnx` | ADAPTER | `speech.onnx` | astu, speech-runtime-api | Pilot-Backend ONNX Runtime (Java-Bibliothek, kein externes Programm) |
| `speech-opennlp` | ADAPTER | `speech.opennlp` | astu, speech-api | Satz- und Spracherkennung beim Vorlesen mit Apache OpenNLP 1.9.4 (wie askai-java8 arch) |
| `text-runtime-api` | PORT | `text.runtime` | astu | Inferenz-Laufzeit lokaler Text-Modelle (Encoder, Decoder mit KV-Cache, Sprachmodell mit Audio-Eingang), austauschbar |
| `text-local` | ADAPTER | `text.local` | astu, embedding-api, chat-api, text-runtime-api, speech-api | Lokale Embeddings, lokaler Chat und lokale Spracheingabe im Prozess (Tokenizer aus tokenizer.json in Java, Pooling, Chat-Vorlage, Generierung, Log-Mel-Merkmale für Gemma 4) |
| `text-onnx` | ADAPTER | `text.onnx` | astu, text-runtime-api | Pilot-Backend ONNX Runtime für Text-Modelle |
| `adyton` | PORT | `adyton` | astu | F (AP13) |
| `security-keepassrpc` | ADAPTER | `proasteion.platform.security.keepassrpc` | astu, adyton | F (AP14) |
| `acp-client-api` | PORT | `acp.api` | astu | G (AP16) |
| `acp-solon-client` | ADAPTER | `acp.solon` | astu, acp-client-api | G (AP17) |
| `acp-demo-agent` | TEST_FIXTURE | `acp.demo` | – (nur externe Bibliotheken) | G (AP17) |
| `research-agent-acp` | AGENT_PROCESS | `acp.research` | research-domain, research-agent-runtime, katagogion, mcp-solon-runtime | Research |
| `katagogion` | PORT | `proasteion.katagogion` | astu | H (AP18) |
| `mcp-solon-runtime` | ADAPTER | `proasteion.katagogion.solon` | astu, katagogion | H (AP19) |
| `comic-controls` | UI_LIBRARY | `ui.comic` | – | B (AP4) |
| `application` | COMPOSITION_ROOT | `proasteion.application` | Ports, Adapter, acropolis (ohne UI) | ADR-0001 (corenth) |
| `exedra` | UI_SHELL | `proasteion.exedra` | application, astu, acropolis, comic-controls und Ports (chat-api, chalcotheca, source-api, acp-client-api, network, speech-api, model-api); kein Adapter, kein adyton | B (AP4, AP22), AP23 |
| `architecture-tests` | ARCHITECTURE_TESTS | `architecture` (nur Tests) | – (liest kompilierte Klassen) | AP24 |
| `integration-tests` | INTEGRATION_TESTS | `integration` (Produktion nur Anker-Klasse) | – (Testklassenpfad sieht alle Module und Testfixtures) | AP25 |

Abweichungen von der Modulliste des Auftrags, beide ohne Aufweichung der fachlichen Grenzen:

- **`acp-demo-agent`** (zusätzlich): der Demo-Agent aus AP17 ist ein eigener Kindprozess und Testfixture.
  Kein Modul darf von ihm abhängen; `acp-solon-client` startet ihn im Test nur als Jar (wie in askai-java8).
  Er ist neben `exedra` und `research-agent-acp` eines der Module mit einer `main`-Methode.
- **`research-agent-acp`** (Rolle AGENT_PROCESS): der eingebaute Recherche-Agent wie in askai-java8 arch
  (`ResearchAgentMain`) als eigener Prozess über ACP. Der Client startet ihn mit derselben Java-Laufzeit aus
  demselben Fat Jar (`java -cp <jar> com.aresstack.enterpriseai.acp.research.ResearchAgentMain`; exedra
  bindet das Modul nur `runtimeOnly`). Research-Steuerung, Konzeptwerkzeuge und das Hauptmodell
  (`main_model_complete`) erreicht er über den MCP-Endpoint des Hosts; Zugangsdaten bekommt er nie. Er darf
  wie der Demo-Agent `System.getenv` lesen (MCP-Endpoint) und das ACP SDK verwenden.
- **`comic-controls`** (zusätzlich): die generischen Java2D-Comic-Komponenten aus askai-java8
  (`comic-controls`) bleiben eine abhängigkeitsfreie Swing-Bibliothek; die Chat-Shell selbst lebt in
  `exedra`. Damit bleibt die Wiederverwendung sauber getrennt von der Anwendung.

`acp-client-api` und `katagogion` dürfen astu sehen, deklarieren es aber derzeit nicht: Die aus
askai-java8 übernommenen Verträge sind bewusst eigenständig.

## Was wohin gehört

- **astu**: providerneutrale Werttypen, die mehr als ein Port oder Use Case teilt. Für die parallelen
  Stränge ist astu in Unterpakete aufgeteilt; jeder Strang ändert nur sein eigenes:
  `astu.chat` (A), `astu.embedding` (C), `astu.knowledge` (D), `astu.source` (E),
  `astu.security` (F). Das Wurzelpaket `astu` selbst wird nur per Contract-Commit geändert.
- **`*-api`**: Port-Interfaces und port-spezifische Request-/Response-/Listener-Typen. Keine
  Implementierung von Infrastruktur, keine Provider-Typen (OpenAI, Ollama, Lucene, JWBF, KeePass ...).
- **Adapter**: genau eine technische Integration hinter genau einem Port. Technische DTOs, JSON-Mapping,
  HTTP-Clients und Fremdbibliotheken bleiben paketintern im Adapter. Fremdbibliotheken mit
  `implementation` deklarieren, nicht mit `api`, damit sie nicht über den Klassenpfad nach außen sickern.
  Adapter laden keine globalen Settings, sondern bekommen eine unveränderliche Konfiguration per
  Konstruktor. Adapter machen keine UI: Dialoge (z. B. KeePassRPC-Pairing) werden über einen Callback-Port
  angefragt und in `exedra` umgesetzt.
- **acropolis**: Use Cases (Chat, RAG, Indexierung, Retrieval, MCP-Tool-Logik), ausschließlich gegen Ports.
  MCP-Tool-Handler rufen Use Cases auf, nie Adapter.
- **application** (`proasteion:application`): Kompositionspunkt ohne Oberfläche. Nur hier werden Adapter
  instanziiert und per Konstruktor verdrahtet (`AdapterAssembly`, `CompositionRoot`, `ApplicationStart`).
- **exedra**: Swing-Schale mit Programmeinstieg (`EnterpriseAiClientMain`); startet application, baut die Schale auf
  dessen Use Cases und liefert die Dialoge (`SwingUserPrompts`). Liest und schreibt die Konfigurationsdatei nur im
  Einstellungen-Dialog.
- **Secrets**: `SecretRef` (loggbar) darf durch Use Cases laufen, Secret-Material nicht. Klartext nur im
  Security-Adapter, im konsumierenden Adapter (z. B. Confluence) und in den Brücken `proasteion.application.security` des
  Kompositionspunkts (API-Key je Anfrage, Wiki-Login), so kurzlebig wie möglich; nie in Logs, Exceptions,
  `toString()`, Chat-Historie oder Indizes.

## Technologiegrenzen

| Technologie | erkannt an | erlaubt nur in |
|---|---|---|
| Swing/AWT | `javax.swing..`, `java.awt..` | exedra, comic-controls |
| Lucene | `org.apache.lucene..` | anagraphai |
| ACP SDK, Reactor | `com.agentclientprotocol..`, `reactor..` | acp-solon-client, acp-demo-agent, research-agent-acp |
| Solon | `org.noear.solon..` | acp-solon-client, acp-demo-agent, mcp-solon-runtime |
| Solon MCP / MCP SDK | `org.noear.solon.ai.mcp..`, `io.modelcontextprotocol..` | mcp-solon-runtime |
| JWBF | `net.sourceforge.jwbf..` | source-mediawiki |
| KeePassRPC-Transport | `org.java_websocket..` | security-keepassrpc |
| HTTP | `java.net.URLConnection`+Unterklassen, `java.net.http..`, `okhttp3..`, `org.apache.http..`, `org.apache.hc..`, `com.sun.net.httpserver..` | nicht im Kern |

Zusätzlich gilt für den Kern (astu, acropolis, alle `*-api`) eine Positivliste: erlaubt sind nur
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
| `ClassBoundaryTest.*` | Modulmatrix auf Klassenebene, Kern-Positivliste, AP24-Technologietabelle für den Kern, Technologiegrenzen, keine Singletons / kein nicht-finales `static` / keine nicht-privaten `static final` Arrays, Collections, Maps, Atomics oder StringBuilder, `main` nur in exedra und acp-demo-agent |
| `ModuleRegistryTest.*` | die Registry selbst: keine Zyklen, Schichtung, AP24-Verbotskanten bleiben verboten, Pakete disjunkt |
| `AgentModeBoundaryTest.*` | AP21: Chat-Pfad ohne ACP/MCP/Agent-Modus; ACP-/MCP-Typen nur in `astu.acropolis.agent`, `astu.acropolis.mcp`, `proasteion.application.agent`, `proasteion.application.composition` und der Schale (`proasteion.exedra.agent`); Agent-Use-Case sieht nur den ACP-Port; `proasteion.exedra.ui.agent` ist reine Oberfläche |
| `RagBoundaryTest.*` | AP10: `astu.acropolis.rag` sieht nur Chat-Use-Case, Embedding- und Index-Port; `astu.acropolis.knowledge` nur Source-, Embedding- und Index-Port; der Chat-Pfad kennt beides nicht |
| `McpKnowledgeToolsBoundaryTest.*` | AP20: `astu.acropolis.mcp` sieht nur die Use-Case-Pakete `astu.acropolis.rag` und `astu.acropolis.knowledge`, Knowledge-Domain, aus `source.api` allein `KnowledgeSourceException` (Fehlerart) und den MCP-Port-Vertrag; Index-, Embedding- und Quell-Port (samt `SourceScope`) nur über Use Cases; kein Adapter, kein Chat, kein ACP, kein Security-Typ; Use Cases und Chat-Pfad kennen die Werkzeuge nicht |
| `CompositionRootBoundaryTest.*` | AP23: Konstruktoren von Adaptern (Klassen eines Adaptermoduls, die einen Port implementieren) werden außerhalb der Adaptermodule nur in `proasteion.application.composition` aufgerufen; exedra hängt an keinem Adapter und nicht an holkas, deigma, tamias, anagraphai, pinakes (corenth `EXEDRA_MUST_STAY_THIN_UI_SHELL`) |
| `SecretBoundaryTest.*` | AP13/AP23: Secret-Material nur in `adyton`, `security-keepassrpc`, `source-confluence`, `source-ftp`, `source-ndv` und, paketgenau, in `proasteion.application.security` (Brücken des Kompositionspunkts); nie in Feldern |
| `RulesDetectViolationsTest.*` | Selbsttest: absichtliche Verstöße (Fixtures) werden erkannt, ein neutraler Domain-Wert nicht |
| `CoreNamingTest.*` | Nachtrag 1/19: keine Provider-Namen (OpenAI, Ollama, Claude, llama.cpp ...) und keine Multi-Provider-Abstraktion in Klassennamen oder Enum-Konstanten des Kerns |
| `TestCodeIsolationTest.*` | Produktionscode kennt weder JUnit/ArchUnit/Mockito noch Testfixture-Klassen oder Testpakete (`testing`, `testkit`, `fake`); keine `testFixtures(...)` in einer Produktionskonfiguration |
| `AdapterSignatureTest.*` | öffentliche Signaturen (Supertypen, Felder, Konstruktoren, Methoden, Typargumente) aller Adapter zeigen keine Bibliotheks- oder Transporttypen; keine Fremdbibliothek mit `api` deklariert |
| `LoggingBoundaryTest.*` | Kern und comic-controls loggen nicht; kein Logging-Framework (Adapter höchstens `java.util.logging`); `System.out`/`System.err` und `printStackTrace` nur im Demo-Agenten |
| `CompositionRootTest.*` | `System.getenv`, Preferences und `Properties.load` nur in application, exedra und acp-demo-agent; kein `ServiceLoader`; jeder Adapter implementiert ein Interface seines Ports |
| `UiTechnologyTest.*` | Nachtrag 3: kein JavaFX, SWT, Servlet oder Web-Framework im Produktionscode |
| `HardcodedConfigurationTest.*` | Nachtrag 1/5/18: im Konstantenpool der Produktionsklassen keine URL mit fremdem Host, kein Modellname, kein API-Key-/Bearer-Literal, keine Bind-All-Adresse `0.0.0.0` |
| `Java8CompatibilityTest.*` | jeder Kompilierschritt jedes Moduls (auch Tests und Testfixtures) zielt auf Java 8 (`--release 8` bzw. Target 1.8); alle Klassendateien tragen Bytecode-Major 52 |
| `SettingsGradleTest.*` | `include`-Zeilen in `settings.gradle` ↔ `ModuleRegistry` ↔ Gradle-Build-Modell |
| `TechnologyRulesDetectViolationsTest`, `ModuleMatrixDetectViolationsTest`, `ExistingRulesDetectViolationsTest` | Gegenbeispiele (AP24): jede Zeile der Technologietabelle, jede verbotene Modulkante und jede inline definierte Strang-Regel wird gegen ein absichtlich verstoßendes Fixture rot; Bibliotheks-Stubs mit echten Paketnamen liegen unter `src/test/java/<bibliothek>/archstub` |

Gegenbeispiele liegen ausschließlich im Testcode von `architecture-tests` unter `<Modulpaket>.archfixture..`
(z. B. `astu.archfixture.tech.LuceneInDomain`) und werden nie in die Produktionsläufe importiert. Regeln, die als
Fabrik vorliegen (`ArchitectureRules`, `*Rules`), werden direkt auf die Fixtures angewendet; Regeln, die in einer
Testmethode stehen, führt `ExistingRuleProbe` unverändert aus und ersetzt nur ihre Eingabe. Wer eine Regel ergänzt,
legt ihr Gegenbeispiel daneben.

### Neues Modul aufnehmen (z. B. `source-sharepoint`, `source-files`)

1. `include` in `settings.gradle`, `build.gradle` mit Abhängigkeiten nur in erlaubter Richtung.
2. Eintrag in `architecture-tests/.../ModuleRegistry.java`: Name, Basispaket, Rolle, Strang, erlaubte
   Abhängigkeiten. Bei neuer Fremdtechnologie auch `Technology` und `ArchitectureRules.confinedTechnologies()`.
3. Zeile in der Modultabelle oben.
4. Neue Knowledge Sources implementieren `source-api` und brauchen keine Änderung an Kern oder acropolis.

Ohne Schritt 2 wird `everyGradleModuleIsRegistered` rot.

Die Modul-Anker-Klassen (`*Module.java`) halten leere Module kompilierbar und scanbar. Sie dürfen gelöscht
werden, sobald das Modul eigene Produktionsklassen in seinem Basispaket hat.

## Agent-Modus (AP21)

Der normale Chat (UI → `ChatService` → `ChatCompletionPort`) läuft vollständig ohne ACP und MCP. Der
Agent-Modus ist ein getrennter Zusatz: UI → `AgentService` (`astu.acropolis.agent`) → ACP-Port → Agent-Prozess.

- `AgentService` hält Prozess, ACP-Verbindung und eine Session, höchstens einen Auftrag gleichzeitig, und ein
  eigenes Transkript. Gestartet wird über `AgentLauncher`, den die Composition Root implementiert
  (`proasteion.application.agent.AcpAgentLauncher`); Endpoint-Tokens und Launch-Umgebung laufen nie durch den Use Case.
- MCP für den Agenten: je Agentenprozess ein frisch registrierter Endpoint (neuer Token), übergeben als
  Umgebungsvariablen `ENTERPRISE_AI_MCP_*` (`proasteion.application.agent.AgentMcpEnvironment`); beim Schließen von Verbindung
  oder Agent-Modus wird er abgemeldet.
- Oberfläche: `proasteion.exedra.ui.workspace.ChatWorkspacePanel` zeigt Chat oder Agent als Karte, gewählt über die
  Modus-Pille `☰ [ Chat ▾ ]`/`[ Agent ▾ ]` neben dem Hamburger (Presentation-Model `proasteion.exedra.ui.agent.ShellModeModel`);
  jede Karte ist eine eigene `ChatShellPanel` mit eigenem Model. Die Agent-Karte baut `proasteion.exedra.agent.AgentModeAssembly`.

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
- Drawer (`proasteion.exedra.ui.sidebar`): Seite „Chats“ mit Suchleiste, „+ Neuer Chat“ (eröffnet eine neue Unterhaltung am
  `ChatService` und schließt die bisherige; im Agent-Modus beendet es die ACP-Session über
  `AgentService.endSession()`; nicht während einer Antwort) und Zeilen je Ansicht; Seite
  „Wissensquellen“ listet `sources` (Häkchen, Indexstand, ⟳, ✎-Dialog, „+ Quelle“, ✕ je Zeile). Weitere Seiten
  kommen als `ChatSidebarTab` dazu. Der Fuß von `ChatSidebarPanel` liegt außerhalb der Seiten und trägt auf jedem
  Reiter das Zahnrad für die Einstellungen (dort Reiter „Index“ mit `knowledge.indexDirectory`).
- Antworten des Assistenten sind Markdown-Blasen wie in askai-java8 `arch` (`proasteion.exedra.ui.chat.AssistantMarkdownBubble`
  mit `proasteion.exedra.ui.markdown.MarkdownMessageView`): flexmark 0.62.2 (Tabellen, Autolinks, Durchstreichen) wird auf native
  Swing-Komponenten abgebildet (Überschriften, Listen, Zitate, Codeblöcke mit Kopieraktion, Tabellen, Links über
  `DesktopLinkOpener`); `mermaid`-Zäune rendert `mermaid-java` (GraalJS + Batik, ohne Browser, ohne Netz) abseits des
  EDT zu Bildern, ein Klick öffnet den zoombaren `MermaidViewerDialog`. Eine einzelne äußere ```markdown-Umhüllung
  der Antwort fällt weg (`MarkdownResponseNormalizer`). Nutzer-, Hinweis- und Fehlerblasen bleiben
  `SpeechBubblePanel`; beide erfüllen `TranscriptBubble` (comic-controls bleibt abhängigkeitsfrei).
- Anhänge im Lesebereich (`proasteion.exedra.ui.reader`): Liefert die Quelle eine `PageView` mit Anhängen, zeigt die
  Reader-Leiste eine Büroklammer mit Zähler (ohne Anhänge ausgeblendet); ein Klick öffnet das Popover
  `ReaderAttachmentList` (Name, Typ, Größe), ein Eintrag das vorhandene Comic-Overlay mit `ImageSlideshowPanel` im
  Anhangsmodus: Bilder und SVG wie bisher, PDF über PDFBox mit Seiten und Zoom, Text/JSON/XML als Text, Office über
  die Vorschau der Quelle (DOCUMENT, POSTER, THUMBNAIL), sonst Name, Typ und Größe; Speichern und Öffnen der
  Originaldatei. Die Bytes holt `KnowledgeReadingController` über `BrowseKnowledgeSourceUseCase` vom
  `PageViewSource`-Port; die Oberfläche spricht nie selbst mit Confluence.
- Fehler des KI-Dienstes bleiben Sprechblasen in Blasengeometrie: Überschrift sichtbar, „Technische Ursache“
  und „Hinweis“ hinter „Details anzeigen“ (`SpeechBubblePanel.setDetails`).
- Einstellungen-Dialog: ebenfalls rahmenlos (Überschrift zieht, ✕ bricht ab), Reiter als Pillen (KI-Dienst,
  KeePass, Netzwerk & Agent; Quellen und Index-Einstellungen liegen in der Drawer-Seite „Wissensquellen“).
- Abnahme-Bilder: `./gradlew :proasteion:exedra:runUiScreenshots --args="<Verzeichnis>"` rendert A–J headless (G1/G2 Reiter des Einstellungen-Dialogs, I Quellen-Dialog, J Index-Dialog).

## RAG und Indexierung (AP10)

Der normale Chat bleibt unverändert; RAG legt sich von außen darum. Einstiegspunkte in `acropolis`:

- `astu.acropolis.knowledge.IndexKnowledgeUseCase`: Quelle → discover → load → `KnowledgeChunker` → `EmbeddingPort`
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
- `astu.acropolis.rag.RetrieveKnowledgeUseCase`: Volltext und Cosine im Namespace der konfigurierten
  `EmbeddingModelIdentity`, Fusion per Reciprocal Rank Fusion (`RetrievalSettings`); fällt ein Pfad aus, liefert
  der andere mit Warnung. Für die semantische Suche wird eine Anfrage über `MAX_QUERY_TOKENS` an einer Wortgrenze
  gekürzt; die Grenze ist `PassageSegmentation.MAX_PASSAGE_TOKENS` (350 Wörter und Symbole, derselbe Zähler wie für
  die Passagen, keine Modell-Token), die Volltextsuche bekommt die ganze Anfrage. Ausgefallene Suchpfade und
  Kürzungen meldet ein `RetrievalObserver`; die Composition Root schreibt sie mit `LoggingRetrievalObserver` ins
  Protokoll (Pfad, Fehlerart, Meldung des Ports, Tokenzahlen, nie die Anfrage), für Chat-RAG und
  `knowledge_search` gleich. Der Hinweis in der Antwortblase bleibt davon unberührt.
- Embedding-Überlast: `OpenAiCompatibleEmbeddingAdapter` wiederholt einen Request bei HTTP 429 oder 503 nach 500 ms
  und 1500 ms (höchstens drei Versuche), bevor der Fehler gilt. Das gilt für jeden Aufruf des Ports, also auch für
  Vorladung und Indexierung (ein überlasteter Batch dauert bis zu 2 s länger); einen Vorrang der Frage vor der
  Vorladung gibt es nicht, die Wiederholung sorgt nur dafür, dass beide durchkommen. Fehlermeldungen des Adapters
  nennen den Pfad des Endpunkts (`/v1/embeddings`), nicht den Host.
- `astu.acropolis.rag.PromptContextAssembler`: nummerierter Kontextblock in Fusionsreihenfolge bis Token-Budget
  oder Quellenzahl (`ContextSettings`).
- `astu.acropolis.rag.RagChatUseCase`: RAG aus = `ChatService.send`; RAG an = Kontext über
  `ChatService.sendWithContext` als System-Anteil nur für diesen Turn, in der Historie bleibt nur die
  Nutzerfrage. Quellen stehen in `RagChatTurn.sources()`.

## MCP-Wissenswerkzeuge (AP20)

Die Wissensfunktionen stehen einem Agenten über MCP zur Verfügung, ausschließlich über Application-Use-Cases:

```
MCP-Client → McpServerRegistry (mcp-solon-runtime) → McpToolContribution (acropolis.mcp)
          → RetrieveKnowledgeUseCase / LoadKnowledgeDocumentUseCase / RefreshKnowledgeSourceUseCase
          → KnowledgeIndexPort / EmbeddingPort / KnowledgeSourcePort → Adapter
```

- `astu.acropolis.mcp.KnowledgeMcpTools(retrieval, documents, refresh, settings)` liefert drei Contributions:
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
- Quellen: `astu.acropolis.knowledge.KnowledgeSourceCatalog` aus `KnowledgeSourceRegistration` (Port + `SourceScope`),
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
Bindings in `proasteion.exedra.chat`, die AP23 in der Composition Root verdrahtet.

- `proasteion.exedra.chat.RagChatBinding` ersetzt `ChatServiceBinding` als `ChatShellActions`: RAG aus = `RagChatUseCase`
  mit `RagOptions.disabled()` (exakt der bisherige Weg); RAG an = Suche auf dem Arbeits-Executor, solange zeigt
  die leere Antwortblase eine Aktivität ("Wissen wird gesucht …"); danach hängen die Quellen als
  `proasteion.exedra.ui.chat.SourceReference` (Nummer, Titel, Überschrift, Ort, Stand, Score, Ränge) an der Antwort, Warnungen
  und ein Ausfall der Suche werden eine eigene Hinweis-Blase (`TranscriptEntry.Author.NOTICE`). Stop während
  der Suche wird gemerkt und bricht den Turn ab, sobald er existiert; die Nutzerfrage bleibt in der Historie.
- `proasteion.exedra.chat.KnowledgeIndexingBinding` treibt `IndexKnowledgeUseCase` auf dem Arbeits-Executor und meldet
  Fortschritt, Ergebnis und Abbruch an `proasteion.exedra.ui.chat.KnowledgeStatusModel`; die Statuszeile
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
- Pflichttest `proasteion.exedra.chat.RagShellIntegrationTest`: Shell → Bindings → Use Cases → echte Adapter
  `OpenAiCompatibleChatAdapter`/`OpenAiCompatibleEmbeddingAdapter` gegen lokale Fake-HTTP-Server für
  `/chat/completions` und `/embeddings` (`proasteion.exedra.chat.fakeapi`) → `LuceneKnowledgeIndex` im temporären
  Verzeichnis, `InMemoryKnowledgeSource` als Quelle. Lokaler Start: `./gradlew :proasteion:exedra:runRagDemo`.

## Composition Root und Konfiguration (AP23)

`proasteion:application` ist die einzige Composition Root (corenth ADR-0001). `EnterpriseAiClientMain` (exedra)
öffnet die Protokolldatei (`AppLogFile`, `java.util.logging` rollierend unter `<Anwendungsverzeichnis>/logs/`), lädt
die Konfiguration (Erststart: Einrichtungsassistent `ui.setup.SetupWizardPanel`), ruft `ApplicationStart.start` (Netz, Tresor, Adapter, Graph, Übernahme
gespeicherter Einstellungen), bindet die Sprache, registriert den Shutdown-Hook und zeigt das Fenster.

```
enterprise-ai-client.properties ──AppConfigLoader──▶ AppConfig (Snapshots, ohne Secrets)
        │                                              │
        ▼                                              ▼
TrustPolicy (SSLSocketFactory je Verb.)   AdapterAssembly ──▶ ApplicationPorts (Chat, Embedding, Index, Quellen,
NetworkServices (HttpRoutes je Ziel, TLS je Verbindung)
                                                            SecretProvider, AgentBackend, Schließreihenfolge)
                                                            │
                                       CompositionRoot ◀────┘  Use Cases (AP2/AP10/AP20), Vorlader, MCP-Werkzeuge,
                                            │                  AgentService mit AcpAgentLauncher (AP21), ShutdownSequence
                              (application) │
                              ─ ─ ─ ─ ─ ─ ─ ┼ ─ ─ ─ ─ ─ ─ ─
                              (exedra)      ▼
                                       ShellAssembly ──▶ ChatShellPanel + RagChatBinding, Statuszeile, Agent-Ansicht, JFrame
```

- **Pakete**: `proasteion.exedra.config` (Snapshots `AppConfig`, `ChatConfig`, `EmbeddingConfig`, `KnowledgeConfig`,
  `SourceConfig`, `KeePassConfig`, `NetworkConfig`, `AgentConfig`; `AppConfigLoader`, `AppPaths`), `proasteion.exedra.net`
  (`HttpRoutes`, `NetworkServices`, `TrustPolicy`, `ConnectionDiagnosis`), `proasteion.exedra.security` (Brücken zum Security-Port,
  `FilePairingKeyStore`, `SwingPairingCallback`),
  `proasteion.exedra.ui.security` (`KeePassPairingDialog`, reine Oberfläche), `proasteion.exedra.ui.settings` (Einstellungen-Dialog,
  reine Oberfläche über dem Formular `SettingsForm` und dem Vertrag `SettingsDialogActions`; dazu `SourceDialog`
  über `SourceForm`/`SourceActions` für die Drawer-Seite „Wissensquellen“), `proasteion.exedra.ui.workspace`
  (`ChatWorkspacePanel` mit Hamburger, Modus-Pille und Drawer, `ShellFrame` rahmenloses Hauptfenster,
  `WorkspaceActions`, `KnowledgeSourceItem`), `proasteion.exedra.ui.sidebar` (`ChatSidebarPanel`, `SidebarTabRibbon`,
  `ChatHistoryRow`, `ChatSidebarTab`; Ports aus askai-java8 `arch`), `proasteion.exedra.settings`
  (`SettingsMapper`, `FileSettingsActions`; `ConfigurationFile` und `FileSourceActions` liegen in
  `proasteion.application.settings`, weil der Start die Quellenadressen abgleicht;
  `ConfigurationStartup`, `ConfigurationCheck`:
  Dialog ↔ Datei ↔ `AppConfigLoader`; `ConnectionProbe` und `ConnectionChecker`: der Verbindungstest des Dialogs
  über `proasteion.application.net`), `proasteion.exedra.assembly` (`ShellAssembly`, `SettingsAssembly`,
  `KeePassSecretChecker`, `ServiceConnectionChecker`), dazu im Kompositionspunkt `proasteion.application.composition`
  (`AdapterAssembly`, `ApplicationPorts`, `CompositionRoot`, `ApplicationStart`, `ShutdownSequence`,
  `StartupNotices`, `LoggingKnowledgeSource` als Protokollhülle um jede Wissensquelle), `proasteion.application.agent`
  (`AcpAgentLauncher`, `ResearchAgentHost`, `ResearchConceptEditing`, Research-Werkzeuge) und
  `proasteion.application.speech` (`NlpModels`, `IndexSentenceSegmenter`).
- **Konfiguration**: eine Properties-Datei im Benutzerverzeichnis (`~/.enterprise-ai-client/` bzw.
  `%APPDATA%`, überschreibbar mit `-Denterpriseai.home` und `-Denterpriseai.config`), eingebaute Defaults
  (AP10-Retrieval/Kontext, Adapter-Timeouts), Fehlermeldungen nennen Schlüssel und Erwartung, nie den Wert;
  unbekannte Schlüssel werden als Warnung gemeldet. Fehlt die Datei, öffnet mit Oberfläche der
  Einrichtungsassistent (`ConfigurationStartup` mit `SetupUi`; fünf Schritte KeePass, KI-Dienst, Modelle, Confluence,
  Abschlussprüfung mit neun Punkten über `FileSetupActions`, Stand `setup.completed` im `ApplicationStateService`;
  Fehlertexte als Klartext über `SettingsMapper.plain`, ohne Properties-Schlüssel); erst „Fertig“ schreibt die Datei
  aus der Vorlage `enterprise-ai-client.example.properties`, dann startet die Anwendung ohne Neustart (headless:
  Vorlage, Hinweis und Exit-Code 2). Eine fehlerhafte Datei öffnet den Einstellungen-Dialog. Der Dialog (Zahnrad im
  Fuß der Drawer-Seite „Chats“) schreibt nur seine Schlüssel zeilenschonend in dieselbe Datei; Änderungen gelten
  ohne Neustart ([Einrichtung](docs/einrichtung.md#einstellungen-dialog)). Modellnamen, Dimension (e5-base
  vermutlich 768, UNVERIFIED), Proxy und Quellen sind reine Konfiguration.
- **Secrets**: in der Datei stehen nur `SecretRef`s (Titel des KeePass-Eintrags): `chat.apiKeyRef`,
  `embedding.apiKeyRef`, `source.<n>.credentialRef`, `…clientCertificate.keyStorePasswordRef`.
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
- **Netz**: `HttpRoutes` implementiert den Port `HttpRoutePort` (Modul `network`) mit win-proxy-java 0.2.0: Route je Ziel, Cache, harter Timeout, nie auf dem EDT, NOT_IMPLEMENTED/ERROR als nicht verfügbare Route statt DIRECT. Jeder Adapter (Chat, Embeddings, MediaWiki, Confluence) übergibt Route, `SSLSocketFactory` (win-trust-java 0.1.0) und User-Agent je `HttpURLConnection`; kein globaler `ProxySelector`. Confluence zusätzlich
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
- **Werkzeuge**: Der `ToolCatalog` (Composition Root) ist die eine Werkzeug-Registry für Chat, Agent,
  Recherche-Agent und den Reiter „Tools“. Aktiv ist, was nicht in `tools.disabled` steht (kommagetrennt, bei jedem
  Aufruf frisch aus den Einstellungen); abgeschaltete Werkzeuge bleiben registriert. Nach dem Speichern berechnen
  die `PhaseToolSetController` des Recherche-Agenten und eines eigenen Agent-Kommandos ihren Satz neu und
  veröffentlichen ihn per `updateTools` am laufenden Endpoint (`tools/list_changed`); der Chat fragt den Katalog je
  Anfrage, und eine laufende Werkzeug-Runde prüft ihn je Modellaufruf (`ToolRegistry` mit Aktiv-Prüfung): ein
  abgeschaltetes Werkzeug wird nicht mehr angeboten, ein schon angefragter Aufruf mit `Fehler:` abgelehnt.
- **Shutdown** (`ShutdownSequence`, idempotent, Fenster-Schließen und JVM-Shutdown-Hook): laufende Chat-Turns
  abbrechen → Agent-Modus beenden (`KnowledgeMcpTools.shutdown()`, `AgentService.close()`, Endpoint abgemeldet)
  → Ports schließen (`SolonMcpServerRuntime.shutdown()` +
  `stopSharedServer()`, dann `LuceneKnowledgeIndex.close()`) → Executor stoppen.
- **Indexierung**: Beim Start wird nichts indexiert; die Suche füllt den Index mit ihren Treffern, ganze Quellen
  indexiert ⟳ („Jetzt indexieren“) im Drawer. `knowledge.indexOnStartup` ist Altbestand und wird ignoriert.
- **Tests**: `ApplicationCompositionTest` baut den ganzen Graphen headless mit Fakes (Chat, Embedding, Index,
  Quelle, In-Process-MCP-Registry, Fake-ACP-Connector) und prüft Indexierung, Chat- und RAG-Roundtrip durch die
  Shell, Agent-Werkzeuge und Shutdown-Reihenfolge; `AdapterAssemblyTest` baut die echten Adapter aus der
  Beispielkonfiguration ohne Netz; dazu `AppConfigLoaderTest`, `FilePairingKeyStoreTest`,
  `SecretBackedTokenSourcesTest`, `HttpRoutesTest` (lokales PAC-Skript mit
  Sentinel-Proxy, Cache, Rückfall), `TrustPolicyTest` (lokaler HTTPS-Server mit selbstsigniertem Zertifikat),
  `ConnectionDiagnosisTest`, `AppLogFileTest`, `ShutdownSequenceTest`.
  Architekturregel `CompositionRootBoundaryTest`.
- **Start**: `./gradlew :proasteion:exedra:run` (optional `-Denterpriseai.config=<Datei>`); Demos mit Fakes:
  `runChatDemo`, `runRagDemo`, `runAgentDemo`.

## Integrations- und Vertical-Slice-Tests (AP25)

`integration-tests` ist das zweite Modul ohne Produktionscode (Rolle `INTEGRATION_TESTS`, von keinem Modul
referenziert). Sein Test-Klassenpfad sieht alle Module einschließlich `exedra` sowie die Testfixtures der
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
  Fat Jar von `exedra` (`gradle/fat-jar.gradle`, `projectVersion` aus `gradle.properties`) und veröffentlicht es:
  auf `main` als Release `v<version>`, auf anderen Branches als rollierenden Snapshot (docs/einrichtung.md).

## Zusammenarbeit der Stränge

| Strang | Module | APs |
|---|---|---|
| A | chat-api, chat-openai, `astu.chat` | AP2 → AP3 |
| B | exedra, comic-controls | AP4 |
| C | embedding-api, embedding-openai, `astu.embedding` | AP5 → AP6 |
| D | chalcotheca, anagraphai, `astu.knowledge` | AP7 → AP8 → AP9 |
| E | source-api, source-mediawiki, `astu.source` | AP11 → AP12 |
| F | adyton, security-keepassrpc, source-confluence, `astu.security` | AP13 → AP14 → AP15 |
| G | acp-client-api, acp-solon-client, acp-demo-agent | AP16 → AP17 |
| H | katagogion, mcp-solon-runtime | AP18 → AP19 |

Danach: AP10 (acropolis, braucht A + C + D), AP20 (acropolis + MCP), AP21 (A + G + H), AP22 (A + B +
AP10), AP23 (exedra), AP24 wächst mit jedem Modul, AP25 (integration-tests), AP26.

Regeln:

- **Contracts zuerst.** Ports (`*-api`) und domain-Typen werden zuerst festgelegt und gemergt, Adapter
  bauen darauf auf.
- **Öffentliche Ports nicht eigenmächtig ändern**, sobald ein anderer Strang darauf aufbaut. Nötige
  Änderungen werden abgestimmt und als kleiner, eigener Contract-Commit/PR umgesetzt.
- **Nur im eigenen Modulbereich arbeiten.** `settings.gradle`, Root-`build.gradle`, `ModuleRegistry` und
  das Wurzelpaket von `astu` sind gemeinsame Verträge und werden nur per Contract-Commit geändert.
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
| Modulschnitt `acp-client-api` / `acp-solon-client` / `acp-demo-agent`, `katagogion` / `mcp-solon-runtime`, `comic-controls`; JUnit 4.13.2; Versionen acp-sdk/solon 3.10.1, Lucene 8.11.3, slf4j-nop | Miguel0888/askai-java8 |
| Versionen Gson 2.10.1, OkHttp 4.9.3, jsoup 1.17.2, JWBF 3.1.1, Java-WebSocket 1.5.2 | Miguel0888/MainframeMate (`proasteion.exedra`, `wiki-integration`) |
| MCP-Werkzeugkatalog als Fabrik von `McpToolContribution`s mit Fehlern als Ergebnis statt Exception und Auflösung des Ziels vor dem Aufruf (`astu.acropolis.mcp`) | Miguel0888/askai-java8 (`ResearchBotDirectoryTools`, `ResearchBotSessionTools`) |
| Parameter `query`/`maxResults`/`sources`, Snippet-Grenze und Gesamtgrenze 20.000 Zeichen der Wissenswerkzeuge | Miguel0888/MainframeMate (`SearchIndexTool`, `ReadChunksTool`) |
| Konfiguration als Properties-Datei im Benutzerverzeichnis mit Pfad-Override per System-Property, unveränderliche Snapshots, Proxy-Modi System/keiner/manuell (`proasteion.exedra.config`, `proasteion.exedra.net`) | Miguel0888/askai-java8 (`AppConfigurationRepository`, `AskAiPaths`, `ProxyConfiguration`) |
| Proxy-Auflösung (`app.net.HttpRoutes`, alle Modi) | Bibliothek aresstack/win-proxy-java 0.2.0 und win-trust-java 0.1.0 (wie in corenth `network-winproxy` und askai-java8 `ProxyConfiguration`); Cache je Host und Rückfall auf Systemeinstellungen sind neu |
| Settings-Schlüssel für Proxy, Timeouts, mTLS (Windows-MY-Alias), KeePassRPC-Verdrahtung mit Pairing-Dialog und Zugangsdaten je Aufruf (`proasteion.exedra.security`, `proasteion.exedra.ui.security`) | Miguel0888/MainframeMate (`Settings`, `KeePassProvider`, `KeePassRpcPairingDialog`, `MvsBrowser`-Proxy) |
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

## Lokale Stimmen im Prozess (`speech-local`, `speech-onnx`)

Lokale Stimmen laufen im Java-8-Client selbst, ohne zweiten Prozess, ohne Java 21 und ohne externes Programm.
`LocalVoiceStore` erkennt Stimmen je Ordner im Modellverzeichnis über `VoiceFormatReader` (Piper `<name>.onnx` +
`<name>.onnx.json`, oder VITS im Hugging-Face-Layout), `VoiceTextEncoder` macht daraus Token-Ids (Piper: Phonem-Ids
aus `phoneme_id_map`; `phoneme_type text` direkt, `espeak` nur Deutsch über den regelbasierten `GermanPhonemizer`
als Näherung an espeak-ng), `LocalSpeechRuntime` (Port `speech-runtime-api`) rechnet. `OnnxSpeechRuntime` in
`speech-onnx` (ONNX Runtime als Java-Bibliothek, native Bibliotheken im Jar) ist nur das Pilot-Backend; ONNX-Typen
bleiben in diesem Modul, eine eigene Inferenz-Engine ersetzt es dort.
