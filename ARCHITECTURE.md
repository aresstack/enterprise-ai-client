# Architektur

Modularer Java-8-Desktop-KI-Client in Ports-and-Adapters-Architektur. Diese Datei ist die verbindliche
Arbeitsgrundlage für alle Stränge (A bis H) und für die späteren Integrationspakete. Die Regeln hier sind
in `architecture-tests` ausführbar; was dort nicht geprüft wird, ist hier als Konvention festgehalten.

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
| `application` | APPLICATION | `application` | domain, alle `*-api` | AP10, AP20, AP21, AP23 |
| `chat-api` | PORT | `chat.api` | domain | A (AP2) |
| `chat-openai` | ADAPTER | `chat.openai` | domain, chat-api | A (AP3) |
| `embedding-api` | PORT | `embedding.api` | domain | C (AP5) |
| `embedding-openai` | ADAPTER | `embedding.openai` | domain, embedding-api | C (AP6) |
| `knowledge-api` | PORT | `knowledge.api` | domain | D (AP7, AP8) |
| `knowledge-lucene` | ADAPTER | `knowledge.lucene` | domain, knowledge-api | D (AP9) |
| `source-api` | PORT | `source.api` | domain | E (AP11) |
| `source-mediawiki` | ADAPTER | `source.mediawiki` | domain, source-api | E (AP12) |
| `source-confluence` | ADAPTER | `source.confluence` | domain, source-api, security-api | F (AP15) |
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
  Security-Adapter und im konsumierenden Adapter (z. B. Confluence), so kurzlebig wie möglich; nie in Logs,
  Exceptions, `toString()`, Chat-Historie oder Indizes.

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
| `BuildModelTest.declaredProjectDependenciesFollowTheAllowedDirection` | `project(...)`-Abhängigkeiten in `api`/`implementation`/`compileOnly`/`runtimeOnly` |
| `BuildModelTest.coreModulesDeclareNoExternalLibraries` | kein Framework in domain/application/`*-api` deklariert |
| `BuildModelTest.everyModuleHasClassesOnlyInItsOwnBasePackage` | Klasse ↔ Modul eindeutig, kein Modul leer |
| `ClassBoundaryTest.*` | Modulmatrix auf Klassenebene, Kern-Positivliste, AP24-Technologietabelle für den Kern, Technologiegrenzen, keine Singletons / kein nicht-finales `static`, `main` nur in app-swing und acp-demo-agent |
| `ModuleRegistryTest.*` | die Registry selbst: keine Zyklen, Schichtung, AP24-Verbotskanten bleiben verboten, Pakete disjunkt |
| `RulesDetectViolationsTest.*` | Selbsttest: absichtliche Verstöße (Fixtures) werden erkannt, ein neutraler Domain-Wert nicht |

### Neues Modul aufnehmen (z. B. `source-sharepoint`, `source-files`)

1. `include` in `settings.gradle`, `build.gradle` mit Abhängigkeiten nur in erlaubter Richtung.
2. Eintrag in `architecture-tests/.../ModuleRegistry.java`: Name, Basispaket, Rolle, Strang, erlaubte
   Abhängigkeiten. Bei neuer Fremdtechnologie auch `Technology` und `ArchitectureRules.confinedTechnologies()`.
3. Zeile in der Modultabelle oben.
4. Neue Knowledge Sources implementieren `source-api` und brauchen keine Änderung an Kern oder application.

Ohne Schritt 2 wird `everyGradleModuleIsRegistered` rot.

Die Modul-Anker-Klassen (`*Module.java`) halten leere Module kompilierbar und scanbar. Sie dürfen gelöscht
werden, sobald das Modul eigene Produktionsklassen in seinem Basispaket hat.

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
- **CI**: `.github/workflows/build.yml` baut mit JDK 8 und JDK 21.

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
AP10), AP23 (app-swing), AP24 wächst mit jedem Modul, AP25, AP26.

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

| Konzept / Datei | Ursprung |
|---|---|
| Java-8-Multiprojekt, `release 8` auf neueren JDKs, `FAIL_ON_PROJECT_REPOS`, `java-library` | aresstack/corenth (`build.gradle`, `settings.gradle`) |
| Architekturtest-Modul mit expliziter Modulliste, die neue Module erzwingt; ArchUnit 1.4.1; Klassenverzeichnisse per Systemeigenschaft | aresstack/corenth (`architecture-tests`), hier in eine Java-Registry plus Gradle-Build-Modell überführt und um Gradle-Abhängigkeitsprüfungen und Selbsttests erweitert |
| Modulschnitt `acp-client-api` / `acp-solon-client` / `acp-demo-agent`, `mcp-runtime-api` / `mcp-solon-runtime`, `comic-controls`; JUnit 4.13.2; Versionen acp-sdk/solon 3.10.1, Lucene 8.11.3, slf4j-nop | Miguel0888/askai-java8 |
| Versionen Gson 2.10.1, OkHttp 4.9.3, jsoup 1.17.2, JWBF 3.1.1, Java-WebSocket 1.5.2 | Miguel0888/MainframeMate (`app`, `wiki-integration`) |
