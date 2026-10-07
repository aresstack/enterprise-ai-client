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

Einstiegspunkt ist `com.aresstack.enterpriseai.app.EnterpriseAiClientMain` (Modul `app-swing`). Ablauf beim
Start: Konfiguration laden, Proxy-Regel installieren, Adapter bauen, Graphen komponieren, Shutdown-Hook
registrieren, Fenster zeigen, Hintergrund-Indexierung starten.

### Anwendungsverzeichnis und Konfigurationsdatei

| Was | Wo |
|---|---|
| Anwendungsverzeichnis | `~/.enterprise-ai-client/` (Linux, macOS), `%APPDATA%\.enterprise-ai-client\` (Windows); überschreibbar mit `-Denterpriseai.home=<Verzeichnis>` |
| Konfigurationsdatei | `<Anwendungsverzeichnis>/enterprise-ai-client.properties`; überschreibbar mit `-Denterpriseai.config=<Datei>` |
| Lucene-Index und Vektoren | `<Anwendungsverzeichnis>/index/` (Schlüssel `knowledge.indexDirectory`) |
| KeePassRPC-Pairing-Schlüssel | `<Anwendungsverzeichnis>/keepassrpc-pairing.key` (Schlüssel `security.keepass.pairingKeyFile`) |

Beim ersten Start ohne Datei legt die Anwendung die kommentierte Vorlage
`enterprise-ai-client.example.properties` dort ab, erklärt das in einem Dialog und beendet sich mit Exit-Code 2.
Die Vorlage liegt im Repository unter
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
unbekannte Schlüssel erzeugen eine Warnung.

### Erster Chat

1. Konfiguration ausfüllen, KeePass mit dem Eintrag für den API-Key entsperren, Anwendung starten.
2. Beim ersten Secret-Zugriff öffnet sich der Pairing-Dialog: KeePass zeigt ein Einmal-Passwort, das im Dialog
   eingegeben wird. Der Pairing-Schlüssel wird danach in der Datei abgelegt (Rechte nur für den Besitzer), ein
   erneutes Pairing ist erst nach Widerruf in KeePass nötig.
3. Frage in das Eingabefeld, Enter oder "Send". Die Antwort streamt in die Sprechblase; "Stop" bricht ab, die
   Frage bleibt in der Historie.

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
2. Die Shell bekommt Reiter "Chat" und "Agent". Der erste Auftrag im Reiter "Agent" startet den Agentenprozess,
   öffnet eine ACP-Session und übergibt ihm per Umgebungsvariablen `ENTERPRISE_AI_MCP_*` einen eigenen
   MCP-Endpoint mit den Wissenswerkzeugen. Beim Beenden wird der Endpoint abgemeldet und der Token ungültig.
   Erklärung: [ACP](acp.md), [MCP](mcp.md).

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
JDK 8 und JDK 21 (`fail-fast: false`, Gradle-Cache). Beide Läufe müssen grün sein, bevor gemergt wird.
