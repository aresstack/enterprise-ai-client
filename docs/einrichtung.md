# Lokale Einrichtung

## Voraussetzungen

- Ein JDK 8 oder neuer. Kompiliert wird immer für Java 8 (`sourceCompatibility 1.8`, auf JDK 9+ zusätzlich
  `--release 8`, damit Java-9+-APIs schon beim Kompilieren scheitern). Die CI baut mit Temurin 8 und 21.
- Gradle wird über den eingecheckten Wrapper geladen (`./gradlew`, Gradle 8.14.3). Gradle 8 ist die letzte Linie,
  die selbst noch auf Java 8 läuft; auf JVMs unter 17 meldet Gradle eine erwartete Deprecation-Warnung.
- Zugriff auf Maven Central für die Abhängigkeiten. Andere Repositories sind durch
  `FAIL_ON_PROJECT_REPOS` in `settings.gradle` ausgeschlossen.
- Für die echte Anwendung: eine erreichbare GPT-kompatible Enterprise-API und ein KeePass 2.x mit
  KeePassRPC-Plugin (siehe [API-Konfiguration](konfiguration-api.md) und
  [KeePass-Konfiguration](konfiguration-keepass.md)). Für Entwicklung und Tests ist beides nicht nötig.

## Bauen und testen

```bash
./gradlew build                        # alle Module kompilieren, alle Tests inklusive Architekturtests
./gradlew :architecture-tests:test     # nur die Architekturregeln
./gradlew :application:test            # Tests eines Moduls
./gradlew :app-swing:test --tests '*RagShellIntegrationTest'
```

Alle Tests laufen ohne Netz und ohne Display: Swing-Tests sind headless, echte Protokollgrenzen werden über
lokale Fake-Server geprüft, der Demo-Agent wird als Kindprozess aus dem gebauten Jar gestartet. Details und
Besonderheiten (eigene JVM je Testklasse in `app-swing` und `mcp-solon-runtime`, optionaler Test gegen ein
echtes KeePass) stehen in der [Testanleitung](tests.md).

## Starten

```bash
./gradlew :app-swing:run                                        # Konfiguration aus dem Benutzerverzeichnis
./gradlew :app-swing:run -Denterpriseai.config=/pfad/zur/datei.properties
./gradlew :app-swing:run -Denterpriseai.home=/pfad/zum/anwendungsverzeichnis
```

Ohne Gradle, mit dem fertigen Fat Jar (siehe [Fat Jar, Version und Releases](#fat-jar-version-und-releases)):

```bash
java -jar enterprise-ai-client-<version>.jar
java -Denterpriseai.config=/pfad/zur/datei.properties -jar enterprise-ai-client-<version>.jar
java -Denterpriseai.home=/pfad/zum/anwendungsverzeichnis -jar enterprise-ai-client-<version>.jar
```

Einstiegspunkt ist `com.aresstack.enterpriseai.app.EnterpriseAiClientMain` (Modul `app-swing`). Ablauf beim
Start: Protokolldatei öffnen, Konfiguration beschaffen (Datei laden; fehlt sie oder lädt sie nicht, öffnet sich
der Einstellungen-Dialog), Vertrauensregel (TLS) und Proxy-Regel installieren, Adapter bauen, Graphen
komponieren, Shutdown-Hook registrieren, Fenster zeigen, Hintergrund-Indexierung starten.

### Anwendungsverzeichnis und Konfigurationsdatei

| Was | Wo |
|---|---|
| Anwendungsverzeichnis | `~/.enterprise-ai-client/` (Linux, macOS), `%APPDATA%\.enterprise-ai-client\` (Windows); überschreibbar mit `-Denterpriseai.home=<Verzeichnis>` |
| Konfigurationsdatei | `<Anwendungsverzeichnis>/enterprise-ai-client.properties`; überschreibbar mit `-Denterpriseai.config=<Datei>` |
| Lucene-Index und Vektoren | `<Anwendungsverzeichnis>/index/` (Schlüssel `knowledge.indexDirectory`) |
| KeePassRPC-Pairing-Schlüssel | `<Anwendungsverzeichnis>/keepassrpc-pairing.key` (Schlüssel `security.keepass.pairingKeyFile`) |
| Protokolldateien | `<Anwendungsverzeichnis>/logs/enterprise-ai-client.<n>.log` (rollierend, drei Dateien à 2 MB, `java.util.logging` ab INFO; enthält Start, Konfiguration ohne Secrets, Vertrauensquellen, Proxy-Regel samt Route zum KI-Dienst, jeden fehlgeschlagenen Chat-Aufruf und jeden fehlgeschlagenen Zugriff auf eine Wissensquelle mit Stacktrace sowie je nicht indexierbarer Seite den Grund) |

Beim ersten Start ohne Datei öffnet die Anwendung den [Einstellungen-Dialog](#einstellungen-dialog) mit leeren
Pflichtfeldern (der KeePass-Titel ist mit `keepass:Enterprise AI API` vorgeschlagen); „Speichern“ schreibt die
kommentierte Vorlage mit den eingetragenen Werten als `enterprise-ai-client.properties` an genau diesen Pfad, dann
startet sie. „Beenden“ im Dialog schreibt keine Datei und endet mit Exit-Code 2; der nächste Start öffnet den
Dialog erneut (die Vorlage allein wäre ladbar und würde sonst mit Beispiel-Adressen starten). Ohne Display
(headless, etwa `smokeStartFatJar`) gibt es keinen Dialog: die Vorlage wird angelegt, der Hinweis geloggt,
Exit-Code 2. Eine vorhandene Datei mit Fehlern öffnet den Dialog mit ihren Werten
und den Problemen (headless: Meldung und Exit-Code 2). Die Vorlage selbst liegt im Repository unter
`app-swing/src/main/resources/com/aresstack/enterpriseai/app/config/enterprise-ai-client.example.properties`
und beschreibt jeden Schlüssel. Pflicht sind:

```properties
# Optional: Fenstertitel
#ui.windowTitle=Enterprise AI Client
chat.baseUrl=https://ki.intern.example/v1
chat.model=openai/gpt-oss-120b
chat.apiKeyRef=keepass:Enterprise AI API
embedding.model=danielheinz/e5-base-sts-en-de
embedding.dimension=768
```

Die Datei enthält keine Secrets. `chat.apiKeyRef` ist der Titel des KeePass-Eintrags, dessen Passwortfeld den
API-Key trägt. Fehler in der Datei werden gesammelt gemeldet und nennen Schlüssel und Erwartung, nie den Wert;
unbekannte Schlüssel erzeugen eine Warnung. Die Vorlage hat `sources=` leer und die Beispielquellen (Wiki,
Confluence) auskommentiert; ohne Quellen gibt es nur Chat, und die Statuszeile meldet keine fehlgeschlagene
Indexierung gegen Beispielhosts. Proxy (Standard `AUTO`: PAC-Skript des Unternehmens wie im Browser, sonst
Systemeinstellungen) und TLS-Vertrauen stehen unter `network.*`
([API-Konfiguration](konfiguration-api.md#netzwerk-network)).

### Einstellungen-Dialog

Das Zahnrad im Fuß der Drawer-Seite „Chats“ (Hamburger ☰ links oben) öffnet den Dialog, den auch der erste Start
zeigt. Er
bearbeitet dieselbe Datei; niemand muss sie von Hand ausfüllen.

| Reiter | Schlüssel |
|---|---|
| KI-Dienst | `chat.baseUrl`, `chat.apiKeyRef` (mit „In KeePass prüfen“), `chat.systemPrompt`; `embedding.baseUrl`, `embedding.dimension`, `embedding.apiKeyRef` |
| KeePass | `security.keepass.enabled`, `host`, `port`, `clientDisplayName`, `pairingKeyStore`; „In KeePass prüfen“ mit dem Eintrag des API-Keys |
| Netzwerk & Agent | `ui.windowTitle`; Proxy-Auflösung wie AskAI (`network.proxy.mode` mit den Modi von win-proxy-java, PAC-URL/Ermittlungsskript, Host/Port, Test-URL, Timeout, „Proxy auflösen“, „HTTPS-Verbindung testen“); TLS-Quellen (`network.tls.*`); User-Agent, Prefer IPv6, Proxy-Anmeldung NONE/BASIC (KeePass-Eintrag); Agent-Modus. „Verbindung testen“ steht im Reiter KI-Dienst. |
| Modelle | je Kategorie ein Modell: `chat.model`, `embedding.model`, `model.rerank`, `model.tts`, `model.stt`, `model.vision`, `model.documentOcr`, `model.imageEmbedding` (lokale mit Präfix `local:`); „Modelle aktualisieren“; lokaler Sidecar `models.local.java`, `models.local.sidecarJar`, `models.local.modelRoot` |

- **Wissensquellen und Index** haben keinen Reiter: die Drawer-Seite „Wissensquellen“ verwaltet `sources` und je
  Quelle `source.<id>.type`, API-/Basis-URL, `credentialRef`, `startPoints`, `maxDepth`, `maxResources`, MediaWiki
  `siteKey`, `displayName`, `requiresLogin`, Confluence `searchSpaceKeys`, `includeAttachments` (Häkchen, ⟳, ✎,
  „+ MediaWiki“/„+ Confluence“) und über „Index …“ `knowledge.indexDirectory` (mit Verzeichnisauswahl) und
  `knowledge.indexOnStartup`. Beides schreibt in dieselbe Datei; Änderungen am Index gelten beim nächsten Start.
- **Prüfen** läuft durch denselben Loader wie der Start und baut wie dieser die TLS-Vertrauensregel (eine
  fehlende oder leere CA-Datei fällt also hier auf, nicht erst beim nächsten Start): Speichern geht nur ohne
  Probleme; Probleme stehen unter den Reitern mit Feldname und Schlüssel, der betroffene Reiter wird gewählt.
- **Speichern** schreibt nur die Schlüssel des Dialogs und lässt alles andere stehen: Kommentare, Reihenfolge,
  Feineinstellungen (Timeouts, Retrieval, Kontext, Client-Zertifikat, Wiki-Namensräume). Ein vorhandener
  Schlüssel wird in seiner Zeile ersetzt, ein auskommentierter (`#schlüssel=…`) an seiner Stelle aktiviert,
  ein neuer unter der Überschrift `# --- Vom Einstellungen-Dialog ergänzt ---` angehängt. Geleerte Felder und
  entfernte Quellen werden auskommentiert, nicht gelöscht. Die Datei wird atomar ersetzt.
- **In KeePass prüfen** löst den Eintrag mit den KeePass-Einstellungen des Entwurfs auf, pairt bei Bedarf über
  den Pairing-Dialog (der Pairing-Schlüssel landet in der konfigurierten Datei und gilt dann auch für den
  Start) und meldet nur, ob der Eintrag existiert und sein Passwortfeld gefüllt ist. Das Secret selbst verlässt
  `app.security` nicht; der API-Key lässt sich im Dialog nicht eintippen (bewusst, siehe
  [KeePass-Konfiguration](konfiguration-keepass.md)).
- **Verbindung zum KI-Dienst prüfen** (Reiter „Netzwerk & Agent“) geht mit dem aktuellen Entwurf, auch
  ungespeichert, denselben Weg wie der Start und zeigt jeden Schritt einzeln, grün, orange (Hinweis), rot
  (Ursache) oder grau (Information): die **Proxy-Route** für `chat.baseUrl` (bei AUTO samt PAC-Auswertung; ohne
  PAC-Ergebnis ein Hinweis), die **Namensauflösung** des Hosts, der tatsächlich angesprochen wird (bei einer
  Proxy-Route der Proxy, denn den Zielhost löst dann der Proxy auf), der **API-Key** aus KeePass (pairt bei Bedarf;
  schlägt das fehl, läuft der Test ohne Key weiter), **Verbindung und TLS** mit der Vertrauensregel des Entwurfs
  (Cipher, Serverzertifikat, befragte Vertrauensquellen) und **GET /models**, dessen Modellliste mit `chat.model`
  verglichen wird (HTTP 401 ohne Key gilt als erreichbar, mit Key als abgelehnter Key; 404 ist ein Hinweis zur
  Basis-URL). Der API-Key erscheint in keiner Zeile; dieselben Zeilen stehen im Protokoll unter
  `Verbindungstest, …`. Der Test ändert nichts an der laufenden Anwendung.
- **Wirksamkeit**: Die laufende Anwendung ist mit der alten Konfiguration gebaut. Nach dem Speichern bietet sie
  an, sich zu beenden; die Änderungen gelten beim nächsten Start.

### Erster Chat

1. Konfiguration ausfüllen, KeePass mit dem Eintrag für den API-Key entsperren, Anwendung starten.
2. Beim ersten Secret-Zugriff öffnet sich der Pairing-Dialog: KeePass zeigt ein Einmal-Passwort, das im Dialog
   eingegeben wird. Der Pairing-Schlüssel wird danach in der Datei abgelegt (Rechte nur für den Besitzer), ein
   erneutes Pairing ist erst nach Widerruf in KeePass nötig.
3. Frage in das Eingabefeld, Enter oder "Send". Die Antwort streamt in die Sprechblase; "Stop" bricht ab, die
   Frage bleibt in der Historie.
4. Scheitert die Anfrage, zeigt die rote Blase die Einordnung ("Der KI-Dienst ist nicht erreichbar."), darunter
   `Technische Ursache:` und meist einen `Hinweis:`; der Stacktrace steht im Protokoll. Die häufigsten Ursachen
   (Zertifikat nicht vertraut, Proxy, KeePass) und ihre Abhilfe:
   [Fehlersuche](konfiguration-api.md#fehlersuche-der-ki-dienst-ist-nicht-erreichbar).

### Quelle indexieren und RAG verwenden

1. Quellen unter `sources=` und `source.<id>.*` konfigurieren ([MediaWiki](konfiguration-mediawiki.md),
   [Confluence](konfiguration-confluence.md)).
2. Mit `knowledge.indexOnStartup=true` (Standard) indexiert die Anwendung beim Start alle Quellen nacheinander
   im Hintergrund. Die Statuszeile über der Eingabezeile zeigt Fortschritt ("Indexierung: k von N Seiten · Titel")
   und Ergebnis ("Wissensbasis: n Seiten, m Abschnitte"); ein Knopf bricht ab.
3. Den Schalter "RAG" in der Eingabezeile einschalten. Vor jeder Antwort sucht die Anwendung in der Wissensbasis
   (Volltext plus semantisch), gibt dem Modell nummerierte Auszüge als Kontext mit und hängt die Quellen
   einklappbar unter die Antwort. Hinweise (keine Treffer, ein Suchpfad ausgefallen) erscheinen als eigene
   Blase. Ablauf im Detail: [RAG-Datenfluss](rag-datenfluss.md).

Eine manuelle Neuindexierung aus der Oberfläche gibt es derzeit nicht; ein erneuter Start indexiert neu, ein
Agent kann `refresh_knowledge_source` aufrufen ([MCP](mcp.md)).

### Agent-Modus starten

1. `agent.enabled=true` setzen und Kommando samt Argumenten des ACP-Agenten konfigurieren, zum Beispiel den
   Demo-Agenten:

   ```properties
   agent.enabled=true
   agent.command=java
   agent.args=-jar "/pfad/zu/acp-demo-agent-all.jar"
   ```

   Das Jar entsteht mit `./gradlew :acp-demo-agent:demoAgentJar` unter `acp-demo-agent/build/libs/`.
2. Die Modus-Pille neben dem Hamburger bietet „Agent“ an. Der erste Auftrag in der Agent-Ansicht startet den
   Agentenprozess,
   öffnet eine ACP-Session und übergibt ihm per Umgebungsvariablen `ENTERPRISE_AI_MCP_*` einen eigenen
   MCP-Endpoint mit den Wissenswerkzeugen. Beim Beenden wird der Endpoint abgemeldet und der Token ungültig.
   Erklärung: [ACP](acp.md), [MCP](mcp.md).

### Sprachausgabe (Vorlesen)

Oben rechts über dem Verlauf liegt der Play/Pause-Orb (wie in askai-java8): Play liest die letzte Antwort vor und
bleibt aktiv, jede neue Antwort wird dann automatisch vorgelesen, bis Pause es beendet. Mit „Neue Antworten
automatisch vorlesen“ (Einstellungen → Sprachausgabe) ist der Orb schon beim Start aktiv. Vorgelesen wird mit dem Modell der Kategorie **TTS**
(Einstellungen → Modelle, Schlüssel `model.tts`; genau eine Auswahl, entweder im Unterreiter „Cloud-Modelle“ oder
„Lokale Modelle“, eine Wahl im einen wählt im anderen ab), gesprochen über die Quelle dieses Modells: ein TTS-Modell
der Enterprise-API über `POST <chat.baseUrl>/audio/speech` (noch ungetestet; das Modell bestimmt die Stimme, der
optionale Dateischlüssel `speech.voice` wird nur mitgeschickt, falls gesetzt), ein lokales über den optionalen
Java-21-Sidecar. Ohne TTS-Modell, oder bei einem lokalen Modell ohne Java 21 und Sidecar, bleibt der Orb
deaktiviert und nennt im Tooltip den Grund. Für eine lokale Stimme:

1. `local-model-runtime-sidecar-<version>.zip` aus dem Release entpacken.
2. Das Zip neben dem Client-Jar (oder im Anwendungs- bzw. Modellverzeichnis) entpacken; das Sidecar-Jar wird dort
   automatisch gefunden (`models.local.sidecarJar` übersteuert, passende Version bevorzugt). Java 21
   wird automatisch gefunden (`JAVA_HOME`, `PATH`, Program Files je Hersteller, `%USERPROFILE%\.jdks`, Scoop;
   exakt Java 21 vor der kleinsten höheren Version) und in `models.local.java` gespeichert; das Dropdown
   „Java-Runtime für Sidecar“ zeigt alle Funde, ältere Versionen ausgegraut, „Neu suchen“ sucht erneut. Beim Start
   wird eine gespeicherte Wahl nur geprüft; gesucht wird nur, wenn sie fehlt oder ungültig ist.
3. Eine VITS-Stimme im ONNX-Format (Hugging-Face-Layout: `config.json` mit `"model_type": "vits"`, `vocab.json`,
   optional `tokenizer_config.json`, `onnx/model.onnx` oder `model.onnx`; z. B. ein ONNX-Export von MMS-TTS Deutsch)
   als eigenen Ordner unter das Modellverzeichnis legen (`models.local.modelRoot`, Standard
   `<Anwendungsverzeichnis>/local-models`). Der Ordnername ist der Modellname. Es wird nichts heruntergeladen.
   Alternativ: Einstellungen → Modelle → „Lokale Modelle“ → „Lokale Stimmen“ → „Installieren“ lädt eine der
   angebotenen Stimmen; „Entfernen“ löscht sie wieder.
4. Einstellungen → Modelle → „Lokale Modelle“ → TTS: die Stimme wählen, speichern, neu starten.

`speech.readAloud.autoStart=true` liest neue Antworten automatisch vor (Standard aus). Spracheingabe (Mikrofon,
Audiodatei) gehört nicht zum Enterprise-Client.

## Demos ohne Backend

Die Demos liegen im Testumfang von `app-swing` und brauchen weder Enterprise-API noch KeePass:

```bash
./gradlew :app-swing:runChatDemo     # Comic-Chat-Shell gegen FakeChatCompletionPort
./gradlew :app-swing:runRagDemo      # Shell mit RAG: echte Adapter gegen lokale Fake-HTTP-Server, Lucene im Temp-Verzeichnis
./gradlew :app-swing:runAgentDemo    # Chat (Fake) plus Agent-Modus mit dem Demo-Agenten als Kindprozess
```

In der RAG-Demo simuliert "fehler" im Text einen HTTP 500, "langsam" lässt die Antwort hängen, bis Stop
gedrückt wird; `--args="--screenshot datei.png"` schreibt die Shell ohne Fenster als PNG. In der Agent-Demo
lässt "slow" im Auftrag den Agenten streamen, bis Stop gedrückt wird.

## Fat Jar, Version und Releases

### Fat Jar bauen

```bash
./gradlew :app-swing:fatJar            # app-swing/build/libs/enterprise-ai-client-<version>-SNAPSHOT.jar
./gradlew :app-swing:verifyFatJar      # Manifest, Vollständigkeit, ServiceLoader-Dateien, Hauptklasse ladbar
./gradlew :app-swing:smokeStartFatJar  # headless starten ohne Konfiguration: Vorlage angelegt, Exit-Code 2
./gradlew build                        # enthält alle drei (fatJar über assemble, die Prüfungen über check)
```

Das Fat Jar enthält `app-swing` und alle Laufzeitabhängigkeiten (eigene Module, Lucene, Solon, Jackson, Gson,
jsoup, Java-WebSocket, ACP-SDK, win-proxy-java mit GraalJS für PAC-Proxyskripte; mit GraalJS wächst das Jar von
rund 16 auf rund 42 MB). Es entsteht ohne Zusatz-Plugin aus einer eigenen Jar-Task in
`gradle/fat-jar.gradle` (eingebunden von `app-swing/build.gradle`), damit der Build auf JDK 8 wie auf JDK 21
läuft. Dabei gilt:

- `META-INF/services/*` aller Jars werden zusammengeführt. Das ist nötig, weil Jackson `JsonFactory` und
  `ObjectCodec` in zwei Jars registriert und Lucene seine Codecs, das MCP-SDK seinen JSON-Mapper über
  `ServiceLoader` findet; `verifyFatJar` prüft, dass jeder Eintrag der Einzeljars im zusammengeführten Jar steht.
- Signaturdateien, `INDEX.LIST` und `module-info.class` fremder Jars bleiben draußen; `Multi-Release: true`
  lässt JDK 9+ die `META-INF/versions`-Klassen von Lucene, Jackson und Reactor wie bei den Einzeljars nutzen.
- Das Manifest trägt `Main-Class`, `Implementation-Title`, `Implementation-Version`, `Implementation-Vendor-Id`
  und `Git-Commit` (aus `-PbuildCommit=<sha>`, sonst `git rev-parse HEAD`, sonst `unknown`). Anzeigen:
  `unzip -p enterprise-ai-client-<version>.jar META-INF/MANIFEST.MF`.
- Das Jar wird reproduzierbar geschrieben (feste Zeitstempel, feste Reihenfolge).

### Version

Die Versionsnummer steht als `projectVersion=MAJOR.MINOR.PATCH` in `gradle.properties` (Schlüsselname wie in
den anderen aresstack-Repositories; `-PprojectVersion=…` überschreibt sie). Lokal und auf allen Branches außer
`main` baut Gradle `<version>-SNAPSHOT`; mit `-Prelease=true` entfällt das Suffix (so baut die CI auf `main`).
Vor einem Release wird nur diese Zeile erhöht und gemergt.

### Releases und Snapshots (CI)

`.github/workflows/release.yml` läuft bei jedem Push auf jeden Branch, auf dem die Datei liegt (und manuell über
`workflow_dispatch`), baut mit JDK 8 (`./gradlew build`, also inklusive aller Tests und der Fat-Jar-Prüfungen)
und veröffentlicht nur ein grün gebautes Jar:

| Push auf | Version | Veröffentlichung |
|---|---|---|
| `main` | `<version>` | Tag `v<version>` und GitHub-Release `enterprise-ai-client <version>` mit dem Jar und generierten Release-Notes, sofern das Tag noch nicht existiert. Existiert es (Version nicht erhöht), gibt es kein Release, nur das Workflow-Artefakt und eine Warnung im Lauf. |
| anderer Branch | `<version>-SNAPSHOT` | Pre-Release `Snapshot <branch> (<version>-SNAPSHOT)` unter dem Tag `snapshot-<branch>-<hash>` (Sonderzeichen außer `. _ -` werden zu `-`; `<hash>` sind die ersten acht Hex-Zeichen von SHA-256 des Branchnamens, damit etwa `feature/foo` und `feature-foo` getrennte Tags haben). Jeder Push verschiebt das Tag auf den neuen Commit und ersetzt das Jar; der Link des Pre-Release bleibt gleich. Wird der Branch gelöscht, entfernt der Workflow Pre-Release und Tag; ein noch laufender Snapshot-Lauf wird vorher abgewartet. |

In beiden Fällen liegt das Jar zusätzlich als Workflow-Artefakt `enterprise-ai-client-<version>` am Lauf. Wie
die anderen aresstack-Repositories (corenth, keepassrpc-java) veröffentlicht der Workflow über die `gh`-CLI nur
mit `GITHUB_TOKEN` (`contents: write`) und ohne Organisations-Secrets; Tags und Releases, die er anlegt, lösen
keine weiteren Läufe aus. Auf Branches bricht ein neuer Push einen noch laufenden Snapshot-Lauf ab, auf `main`
laufen die Läufe nacheinander. Snapshots sind zum Ausprobieren gedacht, nicht für den produktiven Einsatz.

## IDE

Das Projekt ist ein normales Gradle-Multiprojekt und lässt sich in IntelliJ IDEA oder Eclipse über
`settings.gradle` importieren. Zu beachten:

- Das Projekt-SDK muss Java 8 Bytecode erzeugen; mit einem neueren JDK sorgt `--release 8` dafür. Die IDE-Java-
  Sprachstufe sollte auf 8 stehen, damit keine `var`, `List.of` oder Lambdas mit neueren APIs entstehen.
- Die Roundtrip-Tests brauchen das Demo-Agent-Jar und die System-Properties aus den `build.gradle`-Dateien
  (`acp.demo.agent.jar`, `acp.roundtrip.required`). Am einfachsten laufen diese Tests über Gradle; in der IDE
  überspringen sie sich ohne Jar.
- `architecture-tests` liest ein von Gradle erzeugtes Build-Modell (`build/architecture/build-model.properties`);
  auch diese Tests laufen über Gradle.

## Continuous Integration

`.github/workflows/build.yml` baut bei jedem Push auf `main` und jedem Pull Request mit `./gradlew build` auf
JDK 8 und JDK 21 (`fail-fast: false`, Gradle-Cache); seit dem Fat Jar umfasst das auch `fatJar`, `verifyFatJar`
und `smokeStartFatJar`. Beide Läufe müssen grün sein, bevor gemergt wird. `.github/workflows/release.yml`
baut bei jedem Push auf jeden Branch das Fat Jar mit JDK 8 und veröffentlicht es als Release (`main`) oder
rollierenden Snapshot (siehe [Releases und Snapshots](#releases-und-snapshots-ci)).
