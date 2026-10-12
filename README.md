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
./gradlew :proasteion:exedra:run        # Anwendung starten
./gradlew :proasteion:exedra:fatJar     # nur das Fat Jar: exedra/build/libs/enterprise-ai-client-<version>-SNAPSHOT.jar
```

1. **Bauen**: `./gradlew build`. Ohne Netzwerk zu Maven Central schlägt der erste Lauf fehl; einfach
   wiederholen, sobald die Abhängigkeiten geladen sind.
2. **Konfigurieren**: Der erste Start öffnet den Einstellungen-Dialog (Navigationsliste links wie askai arch:
   KI-Dienst, KeePass, Netzwerk & Agent, Modelle, Sprachausgabe mit „Neue Antworten automatisch vorlesen“,
   Technische Details mit Pfaden und dem Ende des Protokolls). Pflicht sind Basis-URL und Modell des Chat-Dienstes, Modell und Dimension der Embeddings
   sowie der Titel des KeePass-Eintrags mit dem API-Key; „In KeePass prüfen“ testet den Eintrag sofort (bei
   Bedarf mit Pairing). Speichern schreibt die kommentierte Datei `enterprise-ai-client.properties` im
   Anwendungsverzeichnis (`~/.enterprise-ai-client/`, unter Windows `%APPDATA%\.enterprise-ai-client\`), danach
   startet die Anwendung. Später öffnet das Zahnrad unten rechts im Drawer (Hamburger ☰ links oben, auf jedem Reiter)
   denselben Dialog; Änderungen gelten sofort, ohne Neustart. Das Indexverzeichnis steht dort im Reiter „Index“;
   Wissensquellen verwaltet die Drawer-Seite „Wissensquellen“ in derselben Datei. Secrets stehen nie in der Datei;
   sie kommen zur Laufzeit aus KeePass (Plugin
   KeePassRPC). Die Datei lässt sich weiterhin von Hand pflegen (Feineinstellungen wie Timeouts stehen nur
   dort). Alternativ: `-Denterpriseai.config=/pfad/zur/datei`.
3. **Starten**: `./gradlew :proasteion:exedra:run` oder `java -jar enterprise-ai-client-<version>.jar`. Ohne erreichbares
   KeePass startet die Anwendung trotzdem und meldet, welche Secrets fehlen; Anfragen scheitern dann mit einem
   Authentifizierungsfehler.
4. **Chatten**: Frage eintippen, Senden (Enter; Umschalt+Enter für eine neue Zeile). Antworten streamen und
   erscheinen als Markdown (Überschriften, Listen, Tabellen, Code mit Kopierknopf, Links; Tabellenzellen zeigen Links,
   Fett/Code und das Inline-HTML der Modelle wie `<ul><li>` und `<br>` formatiert; Mermaid-Diagramme als
   Bild, ein Klick vergrößert), „Stop“ im Composer bricht ab; der System-Prompt kommt aus `chat.systemPrompt`. „+ Neuer Chat“ im Drawer beginnt eine
   neue Unterhaltung. Das Fenster ist rahmenlos: die Kopfzeile zieht, der Rand vergrößert, das ✕ schließt.
5. **Quelle anbinden**: Eine Quelle unter `sources` und `source.<n>.*` eintragen (MediaWiki oder
   Confluence). Indexiert wird nie beim Start, sondern wie in MainframeMate über die Suche: Drawer-Suche,
   Chat-Frage und das Agenten-Werkzeug `knowledge_search` fragen die angehakten Quellen live, laden höchstens
   einige Treffer (Agent: `agent.tools.maxPages`, Standard 4, 0 = unbegrenzt) und indexieren sie nur, wenn
   Revision oder Inhalts-Hash sich geändert haben; `knowledge_read` und das Öffnen im Lesebereich nehmen das
   gelesene Dokument ebenso auf. „Alles indexieren“ auf Knopfdruck gibt es nur bei NDV, FTP/JES und Dateien.
   **„+ Quelle“**: Der Drawer-Reiter „Wissensquellen“ hat genau einen Knopf zum Hinzufügen. Welche Quelltypen
   der Dialog anbietet und welche Felder sie haben, beschreibt jeder Adapter selbst über den Port
   `KnowledgeSourceProvider` (`source-api`, nach corenth); der Use Case `KnowledgeSourceManagement` prüft,
   speichert und entfernt. Das ✕ in einer Zeile entfernt die Quelle nach kurzer Rückfrage: sie verschwindet sofort,
   ihre Zeilen in der Datei werden auskommentiert und ihr Index wird gelöscht. Gespeichert wird weiter als
   `source.<n>.type` und `source.<n>.*`; bestehende Dateien laden unverändert.
   Dokumente liest der Agent über die Ressourcenschicht aus corenth: Tamias prüft den Zugriff,
   Chalcotheca hält die Kopie, Holkas holt sie über den Connector der Quelle (`wiki`, `confluence`, `file`); beim
   Entfernen einer Quelle werden auch ihre Archiveinträge zurückgezogen.
   **Lokale Dateien**: „+ Quelle“ mit Typ „Lokale Dateien“ legt eine Quelle `type=files` an
   („Verzeichnis wählen …“); das Verzeichnis wird rekursiv gelesen. Erkennung und Extraktion stammen aus corenth
   `deigma` (Module `deigma` und `document-tika`, Quelle `source-localfiles`): Text und Markdown direkt,
   PDF, Word, Excel, PowerPoint, OpenDocument, RTF, HTML und Mails über Apache Tika 2.9.1 wie in MainframeMate.
   Die Tika-Parser vergrößern das Fat Jar deutlich.
6. **RAG verwenden**: Einen RAG-Schalter gibt es nicht (wie in askai-java8 arch). Sind Wissensquellen
   konfiguriert, sucht die Anwendung vor jeder Antwort in den angehakten Quellen; die Antwort trägt die verwendeten
   Quellen, Hinweise erscheinen als eigene Blase. Der Composer zeigt wie arch links das Chat-Modell (Klick zeigt die
   Chat-Modelle des Modellkatalogs, Kategorie CHAT; die Wahl gilt ab der nächsten Nachricht und wird als `chat.model`
   gespeichert, „Modelle verwalten …“ öffnet die Einstellungen) und den Denkaufwand („Denken: Standard/niedrig/mittel/hoch“, gesendet als `reasoning_effort` bzw.
   `reasoning.effort`, gegen das Gateway UNVERIFIED), rechts Büroklammer und Senden/Stop; dazwischen eine Statuszeile.
7. **Dateianhänge und Tool-Calls**: Die Büroklammer im Composer hängt Dateien an (Chips mit ✕ über dem Editor,
   nach dem Senden als Chips unter der Nutzerblase). Unterhaltungen mit Anhängen, Seiten oder angehakter
   Wissensquelle laufen über `POST <chat.baseUrl>/chat/completions` mit `tools`, gestreamt: `ChatCompletionsToolLoop`
   fragt jede Runde gestreamt, die `tool_calls` setzt der Chat-Adapter aus den Stream-Deltas zusammen. Mit angehakter Quelle oder Seiten bekommt das Modell den
   Wissens-Werkzeugsatz des Agenten (`knowledge_search` über die angehakten Quellen, `knowledge_read`) aus dem
   `ToolCatalog`, auch ohne Seite; im Reiter „Tools“ abgeschaltete Werkzeuge (`tools.disabled`) fehlen ohne Neustart,
   auch in einer schon laufenden Runde ab dem nächsten Modellaufruf. Das Modell bekommt nur Kennung (`att-…`) und Namen der Anhänge
   und holt sich den Inhalt selbst über die Werkzeuge `read_attachment` und `search_attachment`; der Client führt
   sie lokal aus (Text über `document-tika`, also Apache Tika) und schickt nur das Ergebnis zurück
   (Aufruf als `assistant` mit `tool_calls`, Ergebnis als `role=tool` mit `tool_call_id`, höchstens 8 Runden, kein
   `tool_choice`). Die Dateien liegen
   unter `chats/<chatId>/` neben der Konfigurationsdatei; `chat.tools.enabled=true` nutzt den Werkzeugpfad
   für jede Frage. Gegen das echte Gateway UNVERIFIED.
   Modellauswahl: Nach „Verbindung testen“ (Einstellungen → KI-Dienst) zeigen Auswahllisten unter Chat- und
   Embedding-Modell die Modelle aus `GET /models`, getrennt nach `capabilities`; „(Tool-Calling)“ markiert Modelle mit
   `tool_calling: true`, die Anhänge brauchen. Eine Wahl füllt nur das Textfeld, gespeichert wird wie bisher.
   **Chat-Historie** (aus askai-java8 arch): Jeder Chat wird nach jeder Nachricht als `chats/<chatId>.json` neben
   der Konfigurationsdatei gespeichert, seine Anhänge im Ordner `chats/<chatId>/`. Der Drawer-Reiter „Chats“
   listet die gespeicherten Chats unter HEUTE, GESTERN, LETZTE 7 TAGE und ÄLTER; ein Klick öffnet einen Chat mit
   Verlauf und Anhang-Chips (das Modell bekommt den Verlauf wieder, die Werkzeuge finden die Anhänge), das
   `…`-Menü einer Zeile (oder Rechtsklick) benennt ihn um („Umbenennen …“, der Titel bleibt dann fest) oder löscht
   ihn ohne Rückfrage: die Zeile bleibt als „Gelöscht“ mit ↩ stehen und holt ihn bis zum Beenden zurück,
   erst dann gehen Nachrichten und Anhänge endgültig. Agent-Unterhaltungen werden
   nicht gespeichert.
8. **Agent-Modus**: `agent.enabled=true` und `agent.command`/`agent.args` auf einen ACP-fähigen Agenten setzen
   (zum Ausprobieren der Demo-Agent aus `./gradlew :acp-demo-agent:demoAgentJar`). Die Modus-Pille „Agent“ neben
   dem Hamburger wechselt in die Agent-Ansicht; der erste Auftrag startet
   den Prozess; er erhält die Wissenswerkzeuge über einen MCP-Endpoint, der nur für diesen Prozess gilt.

Demos ohne Enterprise-API und ohne KeePass: `./gradlew :proasteion:exedra:runChatDemo` (Chat gegen einen Fake-Port),
`:proasteion:exedra:runRagDemo` (echte Chat- und Embedding-Adapter gegen lokale Fake-HTTP-Server, Lucene-Index im
Temp-Verzeichnis), `:proasteion:exedra:runAgentDemo` (Chat gegen Fake-Port plus Demo-Agent als Kindprozess).

## Modellverwaltung

Der Reiter „Modelle“ im Einstellungen-Dialog wählt je Funktion genau ein Modell: Chat, Embeddings, Reranking,
Sprachausgabe, Spracheingabe, Bildverständnis, Dokumente/OCR und Bild-Embeddings. Die Liste kommt im Hintergrund
aus `GET /models` des KIPITZ-Dienstes, lokal kommen installierte Stimmen dazu; eingeordnet wird nur nach den gelieferten Metadaten (Capabilities, Modalitäten), nie
nach Namen. Gespeichert wird `chat.model`, `embedding.model` und `model.<funktion>`, lokale Modelle mit dem Präfix
`local:`. Eine gespeicherte Auswahl bleibt erhalten, auch wenn die Quelle gerade nicht erreichbar ist; der letzte
Stand liegt in `model-catalog.properties`. Funktionen ohne passendes Modell zeigen „kein Modell verfügbar“.

## Abhängigkeitsrichtung

```
exedra  ──►  comic-controls
   │
   ├──►  acropolis  ──►  chat-api, embedding-api, chalcotheca, source-api,
   │          │             adyton, acp-client-api, katagogion
   │          └────────────────────────────────────────────────────────────►  astu
   │                                                                            ▲
   └──►  Adapter  ──►  ihr Port-Modul  ─────────────────────────────────────────┘
          chat-openai → chat-api            source-mediawiki  → source-api
          embedding-openai → embedding-api  source-confluence → source-api + adyton
          anagraphai → chalcotheca  security-keepassrpc → adyton
          acp-solon-client → acp-client-api mcp-solon-runtime → katagogion

Pfeile zeigen "darf verwenden". astu kennt niemanden. Die Ports chat-, embedding-, knowledge-, source- und
adyton kennen nur astu; acp-client-api und katagogion sind eigenständig. acropolis kennt astu
und alle Ports. Ein Adapter kennt seinen Port, astu und seine Bibliothek; source-confluence zusätzlich
adyton. exedra ist der einzige Ort, an dem Adapter konstruiert werden.
```

Swing nur in `exedra` und `comic-controls`; Bibliotheken nur in ihrem Adapter; kein Logging-Framework, keine
Konsolenausgabe außerhalb des Demo-Agenten; keine fremden Hostnamen, Modellnamen oder Schlüssel als Literale im
Code. Die vollständigen Regeln prüft `./gradlew :architecture-tests:test`.

## Dokumentation

| Thema | Seite |
|---|---|
| Bauen, erster Start, Konfigurationsdatei, IDE, Fat Jar und Releases | [docs/einrichtung.md](docs/einrichtung.md) |
| Module, Pakete, Bibliotheken, Testfixtures | [docs/module.md](docs/module.md) |
| Schichten, Modulgraph, Laufzeitsicht, Regeln | [docs/architektur.md](docs/architektur.md) |
| Chat- und Embedding-Endpunkt, Proxy und TLS-Vertrauen, Fehlersuche "nicht erreichbar", beobachtetes Serververhalten | [docs/konfiguration-api.md](docs/konfiguration-api.md) |
| KeePassRPC, Pairing, Secret-Referenzen | [docs/konfiguration-keepass.md](docs/konfiguration-keepass.md) |
| MediaWiki als Wissensquelle | [docs/konfiguration-mediawiki.md](docs/konfiguration-mediawiki.md) |
| Confluence Data Center als Wissensquelle | [docs/konfiguration-confluence.md](docs/konfiguration-confluence.md) |
| SharePoint-Dokumentbibliotheken als Wissensquelle | [docs/konfiguration-sharepoint.md](docs/konfiguration-sharepoint.md) |
| BetaView-Dokumentarchiv als Wissensquelle | [docs/konfiguration-betaview.md](docs/konfiguration-betaview.md) |
| Indexierung, hybride Suche, Kontext, „Der Index führt“ | [docs/rag-datenfluss.md](docs/rag-datenfluss.md) |
| Agent-Modus über ACP | [docs/acp.md](docs/acp.md) |
| Wissenswerkzeuge über MCP | [docs/mcp.md](docs/mcp.md) |
| Tests, Fixtures, umgebungsabhängige Läufe, CI | [docs/tests.md](docs/tests.md) |
| Live-Verifikation gegen die echten Dienste in sieben Stufen | [docs/live-verifikation.md](docs/live-verifikation.md) |
| Herkunft aus askai-java8, MainframeMate, corenth | [docs/herkunft.md](docs/herkunft.md) |
| Bekannte Einschränkungen und UNVERIFIED-Punkte | [docs/einschraenkungen.md](docs/einschraenkungen.md) |

Architekturgrundlage und Arbeitsregeln der Stränge: [ARCHITECTURE.md](ARCHITECTURE.md).
