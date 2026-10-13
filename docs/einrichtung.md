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
./gradlew :acropolis:test            # Tests eines Moduls
./gradlew :proasteion:exedra:test --tests '*RagShellIntegrationTest'
```

Alle Tests laufen ohne Netz und ohne Display: Swing-Tests sind headless, echte Protokollgrenzen werden über
lokale Fake-Server geprüft, der Demo-Agent wird als Kindprozess aus dem gebauten Jar gestartet. Details und
Besonderheiten (eigene JVM je Testklasse in `exedra` und `mcp-solon-runtime`, optionaler Test gegen ein
echtes KeePass) stehen in der [Testanleitung](tests.md).

## Starten

```bash
./gradlew :proasteion:exedra:run                                        # Konfiguration aus dem Benutzerverzeichnis
./gradlew :proasteion:exedra:run -Denterpriseai.config=/pfad/zur/datei.properties
./gradlew :proasteion:exedra:run -Denterpriseai.home=/pfad/zum/anwendungsverzeichnis
```

Ohne Gradle, mit dem fertigen Fat Jar (siehe [Fat Jar, Version und Releases](#fat-jar-version-und-releases)):

```bash
java -jar enterprise-ai-client-<version>.jar
java -Denterpriseai.config=/pfad/zur/datei.properties -jar enterprise-ai-client-<version>.jar
java -Denterpriseai.home=/pfad/zum/anwendungsverzeichnis -jar enterprise-ai-client-<version>.jar
```

Einstiegspunkt ist `com.aresstack.enterpriseai.proasteion.exedra.EnterpriseAiClientMain` (Modul `exedra`). Ablauf beim
Start: Protokolldatei öffnen, Konfiguration beschaffen (Datei laden; fehlt sie, öffnet sich der
[Einrichtungsassistent](#einrichtungsassistent-erststart), lädt sie nicht, der Einstellungen-Dialog), Vertrauensregel
(TLS) und Proxy-Regel installieren, Adapter bauen, Graphen komponieren, Shutdown-Hook registrieren, Fenster zeigen.
Beim Start wird nichts indexiert (siehe [Quelle indexieren](#quelle-indexieren-und-rag-verwenden)).

### Anwendungsverzeichnis und Konfigurationsdatei

| Was | Wo |
|---|---|
| Anwendungsverzeichnis | `~/.enterprise-ai-client/` (Linux, macOS), `%APPDATA%\.enterprise-ai-client\` (Windows); überschreibbar mit `-Denterpriseai.home=<Verzeichnis>` |
| Konfigurationsdatei | `<Anwendungsverzeichnis>/enterprise-ai-client.properties`; überschreibbar mit `-Denterpriseai.config=<Datei>` |
| Lucene-Index und Vektoren | `<Anwendungsverzeichnis>/index/` (Schlüssel `knowledge.indexDirectory`) |
| KeePassRPC-Pairing-Schlüssel | `<Anwendungsverzeichnis>/keepassrpc-pairing.key` (Schlüssel `security.keepass.pairingKeyFile`) |
| Protokolldateien | `<Anwendungsverzeichnis>/logs/enterprise-ai-client.<n>.log` (rollierend, drei Dateien à 2 MB, `java.util.logging` ab INFO; enthält Start, Konfiguration ohne Secrets, Vertrauensquellen, Proxy-Regel samt Route zum KI-Dienst, jeden fehlgeschlagenen Chat-Aufruf und jeden fehlgeschlagenen Zugriff auf eine Wissensquelle mit Stacktrace sowie je nicht indexierbarer Seite den Grund) |

Beim ersten Start ohne Datei öffnet die Anwendung den [Einrichtungsassistenten](#einrichtungsassistent-erststart);
erst sein „Fertig“ schreibt die kommentierte Vorlage mit den eingetragenen Werten als
`enterprise-ai-client.properties` an genau diesen Pfad, dann startet die Anwendung ohne Neustart. Abbrechen schreibt
keine Datei; der nächste Start öffnet den Assistenten erneut (die Vorlage allein wäre ladbar und würde sonst mit
Beispiel-Adressen starten). Ohne Display (headless, etwa `smokeStartFatJar`) gibt es keinen Assistenten: die
Vorlage wird angelegt, der Hinweis geloggt, Exit-Code 2. Eine vorhandene Datei mit Fehlern öffnet den
[Einstellungen-Dialog](#einstellungen-dialog) mit ihren Werten und den Problemen (headless: Meldung und Exit-Code 2). Die Vorlage selbst liegt im Repository unter
`proasteion/application/src/main/resources/com/aresstack/enterpriseai/proasteion/application/config/enterprise-ai-client.example.properties`
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
unbekannte Schlüssel erzeugen eine Warnung. Die Vorlage hat keine aktiven Quellen, die Beispielquellen (Wiki,
Confluence) auskommentiert; ohne Quellen gibt es nur Chat, und die Statuszeile meldet keine fehlgeschlagene
Indexierung gegen Beispielhosts. Proxy (Standard `AUTO`: PAC-Skript des Unternehmens wie im Browser, sonst
Systemeinstellungen) und TLS-Vertrauen stehen unter `network.*`
([API-Konfiguration](konfiguration-api.md#netzwerk-network)).

### Einrichtungsassistent (Erststart)

Fehlt die Konfigurationsdatei, führt ein Assistent im Comic-Design durch fünf Schritte; Properties-Schlüssel zeigt
er nirgends, auch nicht in Fehlermeldungen (dort stehen die Feldnamen, z. B. „Chat-Modell: fehlt“):

1. **KeePass verbinden**: Host, Port und Client-Name sind vorbelegt; „Mit KeePass verbinden“ pairt über den
   bekannten Pairing-Dialog (der Pairing-Schlüssel liegt immer in der Datei) und gilt erst als erledigt, wenn die
   Eintragsliste lesbar ist.
2. **KI-Dienst**: wählt den KeePass-Eintrag vor (konfigurierter Titel, sonst ein eindeutiger Titel mit „enterprise
   ai“, sonst „Enterprise AI API“), liest Basis-URL und API-Key daraus und testet die Verbindung. Fehlt etwas,
   lassen sich Basis-URL und API-Key eintragen und „In KeePass speichern“; der API-Key landet nie in der
   Konfigurationsdatei, `chat.baseUrl` bleibt leer und kommt beim Start aus dem Eintrag.
3. **Modelle**: Chat- und Embedding-Modell aus `GET /models`, ohne Klick vorausgewählt: eine noch angebotene Wahl
   bleibt, sonst das Vorzugsmodell aus `setup-models.properties` (neben `PreferredModels`, Vorgabe
   `openai/gpt-oss-120b` bzw. `danielheinz/e5-base-sts-en-de`), sonst genau ein Modell, dessen Kennung den Teil nach
   dem letzten „/“ enthält, sonst das erste angebotene. Gleich danach misst ein Probe-Embedding die Dimension;
   „Dimension ermitteln“ ist nur noch die manuelle Wiederholung. Die Zeile „Sprachausgabe“ wählt ebenso das
   TTS-Modell des Chats (`model.tts`, Vorgabe `Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice`, sonst ein eindeutiger Treffer mit
   „qwen3-tts“ und „customvoice“, sonst das erste TTS-Modell); Merlins eigene Stimme (`setup.merlin.*`) bleibt davon
   unberührt. Die Zeile „Bildmodell“ wählt nach derselben Reihenfolge das Modell für Bildbeschreibungen
   (`model.vision`, etwa für Bildanhänge aus Confluence; Vorgabe `Qwen/Qwen2.5-VL-72B-Instruct`, sonst ein eindeutiger
   Treffer mit „qwen“ und „vl“, sonst das erste Vision-Modell). Beide Zeilen sind optional.
4. **Confluence** (optional): KeePass-Eintrag mit URL, Benutzer und Passwort bzw. Token, optional ein
   Client-Zertifikat aus dem Windows-Zertifikatspeicher; Netzwerkeinstellungen über den Netzwerk-Reiter des
   Einstellungen-Dialogs. Ein vollständiger Eintrag (URL und Passwort bzw. Token) verlangt keine Eingabe: der
   Schritt zeigt nur, was vorhanden ist (ohne Secrets), und testet mit den Werten aus KeePass; bei einem
   unvollständigen Eintrag fragt er nur das Fehlende ab (etwa die URL), bei einem neuen Titel alles; „Verbindung
   testen“ schreibt es in den KeePass-Eintrag (URL ins Standardfeld, Zusatzfelder bleiben). „Zugangsdaten ändern“
   zeigt alle Felder. Springt man über die Schrittleiste an
   „Mit KeePass verbinden“ vorbei, lesen „KI-Dienst“ und „Confluence“ die Eintragsliste selbst nach.
5. **Abschlussprüfung**: schreibt Konfiguration und Confluence-Quelle und prüft neun Punkte (KeePass gekoppelt,
   KI-Dienst erreichbar, API-Key lesbar, Chat-Modell, Embedding-Modell, Dimension, Confluence-Zugang, Quelle
   gespeichert und aktiviert, Konfiguration lädt vollständig). Die Punkte hängen nicht voneinander ab: „KI-Dienst
   erreichbar“ läuft wie der Test in Schritt 2 mit Platzhaltern für fehlende Modelle, „API-Key lesbar“ liest den
   gewählten KeePass-Eintrag direkt; ein fehlendes Modell färbt nur seinen eigenen Punkt und „Konfiguration lädt“
   rot. Solange die Grundeinstellungen unvollständig sind, schreibt die Prüfung nichts, und die Quelle meldet „Noch
   nicht gespeichert, weil die Grundeinstellungen unvollständig sind.“ „Fertig“ gibt es erst, wenn alle grün sind. Ist
   Confluence übersprungen, entfallen Quelle und die beiden Confluence-Punkte; Confluence lässt sich später in der
   Seitenleiste unter Wissensquellen einrichten.

Der Stand steht als `setup.completed` im Anwendungszustand (`application-state.json`): fehlt die Datei, öffnet sich
immer der Assistent und der Wert wird `false`; nur ein erfolgreiches „Fertig“ setzt `true`. Eine vorhandene Datei
ohne den Schlüssel ist eine bestehende Installation und startet wie bisher; eine Datei mit `false` (Einrichtung
unterbrochen) öffnet den Assistenten mit ihren Werten. Später öffnet der Knopf „Einrichtungsassistent“ im
Einstellungen-Dialog ihn erneut, als Overlay über dem Antwortfenster.

### Einstellungen-Dialog

Das Zahnrad im Fuß der Drawer-Seite „Chats“ (Hamburger ☰ links oben) öffnet den Dialog; ein Start mit fehlerhafter
Datei zeigt ihn ebenfalls. Er bearbeitet dieselbe Datei; niemand muss sie von Hand ausfüllen.

| Reiter | Schlüssel |
|---|---|
| KI-Dienst | `chat.baseUrl`, `chat.apiKeyRef` (mit „In KeePass prüfen“), `chat.systemPrompt`; `embedding.baseUrl`, `embedding.dimension`, `embedding.apiKeyRef` |
| KeePass | `security.keepass.enabled`, `host`, `port`, `clientDisplayName`, `pairingKeyStore`; „In KeePass prüfen“ mit dem Eintrag des API-Keys |
| Netzwerk & Agent | `ui.windowTitle`; Proxy-Auflösung wie AskAI (`network.proxy.mode` mit den Modi von win-proxy-java, PAC-URL/Ermittlungsskript, Host/Port, Test-URL, Timeout, „Proxy auflösen“, „HTTPS-Verbindung testen“); TLS-Quellen (`network.tls.*`); User-Agent, Prefer IPv6, Proxy-Anmeldung NONE/BASIC (KeePass-Eintrag); Agent-Modus. „Verbindung testen“ steht im Reiter KI-Dienst. |
| Modelle | je Kategorie ein Modell: `chat.model`, `embedding.model`, `model.rerank`, `model.tts`, `model.stt`, `model.vision`, `model.documentOcr`, `model.imageEmbedding` (lokale mit Präfix `local:`); „Modelle aktualisieren“; Modellverzeichnis lokaler Stimmen `models.local.modelRoot` |
| Index | `knowledge.indexDirectory` (mit „Wählen …“); leer heißt `index` im Anwendungsverzeichnis |
| Tools | alle registrierten Werkzeuge (Name, Beschreibung, Herkunft, Verfügbarkeit Chat/Agent/Recherche-Agent) mit Häkchen „aktiv“; abgeschaltete stehen in `tools.disabled` (kommagetrennt, fehlt = alle aktiv) und gelten ohne Neustart: ein laufender Agent bekommt den neuen Satz sofort (`tools/list_changed`), eine laufende Chat-Runde bietet ein abgeschaltetes Werkzeug ab dem nächsten Modellaufruf nicht mehr an und lehnt einen schon angefragten Aufruf ab |

- **Wissensquellen** haben keinen Reiter: die Drawer-Seite „Wissensquellen“ verwaltet die Liste `source.<n>.*`, je
  Quelle `source.<n>.type`, API-/Basis-URL, `credentialRef`, `startPoints`, `maxDepth`, `maxResources`, MediaWiki
  `siteKey`, `displayName`, `requiresLogin`, Confluence `searchSpaceKeys`, `includeAttachments` (Häkchen, ⟳, ✎,
  „+ Quelle“) in derselben Datei.
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
  `proasteion.exedra.security` nicht; der API-Key lässt sich im Dialog nicht eintippen (bewusst, siehe
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
- **Wirksamkeit**: Änderungen gelten sofort, ohne Neustart (wie der `SettingsHelper` in MainframeMate). Nach dem
  Speichern lädt der `SettingsService` die Datei neu; Werte werden über ihn frisch gelesen statt beim Start
  kopiert, und wo ein Wert in ein gebautes Objekt geht, übernimmt ein Beobachter ihn: Chat- und Embedding-Adapter
  samt Modellauswahl, API-Key-Eintrag und Basis-URL, Proxy/PAC/TLS/User-Agent, KeePass-Anbindung,
  Proxy-Anmeldung, Indexverzeichnis, System-Prompt, Fenstertitel, Sprachaus- und -eingabe, Agent-Kommando
  (ab der nächsten Agent-Sitzung). Ein anderes Embedding-Modell nutzt seinen eigenen Namensraum im Index, also
  danach neu indexieren. Einzige Ausnahme: „IPv6 bevorzugen“ liest die JVM nur beim Start.

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

1. Quellen im Reiter „Wissensquellen“ anlegen oder als Liste `source.<n>.*` konfigurieren ([MediaWiki](konfiguration-mediawiki.md),
   [Confluence](konfiguration-confluence.md)).
2. Beim Start wird nichts indexiert: Die Suche füllt den Index mit ihren Treffern, ⟳ („Jetzt indexieren“) an einer
   Quelle indexiert sie ganz. Die Statuszeile über der Eingabezeile zeigt Fortschritt ("Indexierung: k von N Seiten · Titel")
   und Ergebnis ("Wissensbasis: n Seiten, m Abschnitte"); ein Knopf bricht ab.
3. Den Schalter "RAG" in der Eingabezeile einschalten. Vor jeder Antwort sucht die Anwendung in der Wissensbasis
   (Volltext plus semantisch), gibt dem Modell nummerierte Auszüge als Kontext mit und hängt die Quellen
   einklappbar unter die Antwort. Hinweise (keine Treffer, ein Suchpfad ausgefallen) erscheinen als eigene
   Blase. Ablauf im Detail: [RAG-Datenfluss](rag-datenfluss.md).

Neu indexiert wird über ⟳ („Jetzt indexieren“) an der Quelle oder über die Suche; ein Neustart indexiert nichts.

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
der Enterprise-API über `POST <chat.baseUrl>/audio/speech` (noch ungetestet; mit fester Stimme, siehe unten), eine
lokale Stimme im Client selbst
(ONNX Runtime im Prozess, kein externes Programm, kein Java 21). Ohne TTS-Modell bleibt der Orb deaktiviert und nennt
im Tooltip den Grund. Für eine lokale Stimme:

1. Einstellungen → Modelle → „Lokale Modelle“: unter „Lokale Stimmen“ eine vorgeschlagene Stimme installieren oder
   unter „Hugging Face durchsuchen“ suchen und einen Treffer installieren. Angeboten werden nur Stimmen, die der
   Client ohne Umwandlung lädt: Piper (`<name>.onnx` + `<name>.onnx.json`, deutsch oder `phoneme_type text`) oder
   VITS im Hugging-Face-Layout (`config.json` mit `"model_type": "vits"`, `vocab.json`, `onnx/model.onnx` oder
   `model.onnx`). Geladen wird über die Netzwerkeinstellungen ins Modellverzeichnis (`models.local.modelRoot`,
   Standard `<Anwendungsverzeichnis>/local-models`); Größe und SHA-256 werden gegen die Hub-Metadaten geprüft.
   „Aktualisieren“ lädt eine Stimme neu, „Entfernen“ löscht sie. Eine Stimme kann auch von Hand als eigener Ordner
   unter das Modellverzeichnis gelegt werden; der Ordnername ist der Modellname.
2. Einstellungen → Modelle → „Lokale Modelle“ → TTS: die Stimme wählen, speichern, neu starten.

### Lokale Embeddings

Embedding-Modelle laufen ebenfalls im Client über ONNX Runtime (kein externes Programm, keine Umwandlung). Unter
Einstellungen → Modelle → „Lokale Modelle“ → „Hugging Face durchsuchen“ erscheinen Embedding-Modelle mit ONNX-Export
(`onnx/model.onnx` oder eine quantisierte Variante, dazu `config.json` und `tokenizer.json`). Der Tokenizer aus
`tokenizer.json` (WordPiece, BPE, Unigram) läuft in reinem Java; Pooling und Normalisierung kommen aus `modules.json`
und `1_Pooling/config.json`, fehlen sie, gilt Mittelwert mit L2-Normalisierung. Nach dem Installieren unter „Lokale
Modelle“ → Embeddings wählen, speichern, neu starten und die Wissensquellen neu indexieren: ein anderes Modell ist eine
andere Embedding-Welt. Präfixe wie `query:`/`passage:` (E5) setzt der Client nicht.

### Lokaler Chat

Chat-Modelle laufen ebenso im Client über die ONNX-Runtime-Bibliothek für Java (onnxruntime-genai wird nicht
gebraucht: die Generierungsschleife mit KV-Cache steht im Client). Die Hub-Suche bietet Decoder-Exporte mit
`past_key_values` an (etwa `onnx/model_q4.onnx` aus transformers.js-Exporten), dazu `config.json`, `tokenizer.json` und
`tokenizer_config.json`. Die Chat-Vorlage (`chat_template` bzw. `chat_template.jinja`) rendert ein kleiner
Jinja-Interpreter in reinem Java; lehnt sie eine System-Nachricht ab, wandert der System-Text vor die erste
Nutzernachricht, fehlt sie, gilt ChatML. Ohne Angaben antwortet das Modell gierig, sonst nach Temperatur, Top-p und
Top-k bzw. `generation_config.json`. Installierte Chat-Modelle stehen im Composer als `local:<ordner>` und unter
„Lokale Modelle“ → Chat; es rechnet immer nur eine Antwort gleichzeitig. Werkzeug-Chat (Anhänge lesen) läuft nur über
die Enterprise-API. Mit echten Hub-Modellen ist das ungeprüft.

Über dem Suchfeld wählt „Alle Funktionen“, „Chat“, „Embeddings“, „Sprachausgabe (TTS)“ oder „Spracheingabe (STT)“,
wonach gesucht wird;
Treffer zeigen ihre Funktion vorn im Namen. Installierte Modelle stehen danach unter „Lokale Modelle“ in der Zeile ihrer
Funktion zur Wahl.

`speech.readAloud.autoStart=true` liest neue Antworten automatisch vor (Standard aus).
`speech.readAloud.chunking` legt die Einheit je Synthese fest wie in askai arch: `PARAGRAPHS` (Standard, Absätze),
`SENTENCES` (Sätze) oder `ANSWER` (ganze Antwort); `speech.readAloud.synthesisWorkers` (1–8, Standard 2) ist die Zahl
paralleler Synthesen. Beides steht unter Einstellungen → Sprachausgabe. Das erste Stück rechnet allein und klingt,
sobald es fertig ist; die übrigen rechnen währenddessen, gespielt wird lückenlos über eine offene Audioausgabe.
`speech.readAloud.synthesisAttempts` (1–5, Standard 2) ist die Zahl der Versuche je Stück: scheitert ein Stück auch
beim letzten, endet das Vorlesen dort mit der Meldung, welches Stück fehlt, statt es zu überspringen.

**Stimme je Modell.** TTS-Modelle mit mehreren Sprechern (Orpheus, Qwen3-TTS) wählen ohne feste Stimme bei jeder
Anfrage selbst; beim absatzweisen Vorlesen klang dann jeder Absatz womöglich anders. Unter Einstellungen →
Sprachausgabe steht deshalb die Stimme des Modells (das Modell selbst wird nur unter „Modelle“ gewählt): die Liste
zeigt die bekannten Sprecher des Modells, eintippen geht auch, „Stimme testen“ spricht einen kurzen Satz mit dem
Entwurf auf demselben Weg wie das Vorlesen. Die Stimme gilt je Modell (Schlüssel `speech.voices`, z. B.
`kipitz:orpheus-tts-3b-de=Thomas;kipitz:qwen3-tts=Vivian`); wer das Modell wechselt, bekommt dessen eigene Stimme. Ist
für ein Modell keine gespeichert, gilt die Standardstimme aus `tts-voices.properties` (Orpheus: `Jana`, Qwen3-TTS:
`Vivian`; die Namen sind gegen die Enterprise-API ungeprüft). Lokale Stimmen haben genau einen Sprecher. Es gibt genau
eine Stimmquelle: der TTS-Adapter hat keine eigene Stimme mehr. Der frühere Schlüssel `speech.voice` wird beim Laden
als Stimme des gewählten Modells übernommen, wenn er zu ihm passt (beim nächsten Speichern steht er in
`speech.voices` und fällt selbst weg); passt er nicht, bleibt er unbenutzt stehen.

Vorgelesen wird, was die Anzeige zeigt: derselbe Markdown-Parser wie die Darstellung, Code- und Diagrammblöcke,
HTML-Kommentare und Tags, Linkziele und nackte URLs fallen weg, Entities werden zu Zeichen, unsichtbare Zeichen
(Null-Breite, weiche Trennung) sowie Pfeile, Rahmenlinien und Emojis entfallen. Eine Tabellenzeile wird als Satz
„Spalte: Wert, Spalte: Wert.“ gesprochen, Überschriften und Listenpunkte enden als Satz.

Ein Vorlesevorgang spricht immer mit dem einen TTS-Modell und der einen Stimme, die beim Start der Antwort galten; jedes Stück geht
mit dieser Kennung an den Dienst, auch bei mehreren Workern. Wird in den Einstellungen ein anderes Modell gespeichert,
endet die laufende Ausgabe sofort (kein weiterer Absatz der alten Stimme), die neue Stimme spricht ab der nächsten
Antwort bzw. dem nächsten Play. Je Stück stehen zwei Zeilen im Protokoll, ohne Text und ohne API-Key:

```
TTS request: generation=3 chunk=2/5 catalog=kipitz model=<modell> voice=<stimme> worker=enterprise-ai-tts-worker-3-2 chars=412
TTS HTTP request: POST audio/speech catalog=kipitz model=<modell> voice=<stimme> response_format=wav worker=enterprise-ai-tts-worker-3-2 chars=412 text=<sha256-präfix>
TTS HTTP response: model=<modell> worker=enterprise-ai-tts-worker-3-2 status=200 millis=2140 bytes=391244 contentType=audio/wav servedBy=none
TTS audio: generation=3 chunk=2/5 model=<modell> voice=<stimme> worker=enterprise-ai-tts-worker-3-2 wav=391244 bytes, 24000 Hz, 1 ch, 16 bit
```

`worker` verbindet die Zeilen eines Stücks. `servedBy` nennt Antwort-Header des Servers, die ein Modell oder Backend
benennen (sonst `none`); Abtastrate und Kanäle zeigen, ob zwei Stücke vom selben Modell stammen können. Stehen in
allen `TTS HTTP request`-Zeilen dasselbe `model` und dieselbe `voice` und klingen die Stücke trotzdem verschieden,
antwortet der Server unterschiedlich. Zum Vergleich `speech.readAloud.synthesisWorkers=1` setzen: bleibt die Stimme
dann gleich, wechselt der Server das Modell nur bei gleichzeitigen Anfragen. Derselbe Absatz hat in jedem Lauf
denselben `text`-Fingerabdruck, so lassen sich gleiche Requests über mehrere Läufe vergleichen.

### Spracheingabe (lokal)

Im letzten Schritt des Einrichtungsassistenten steht rechts neben dem Download-Fortschritt ein kleines Kreuz, nur
solange ein Download läuft. Es bricht den echten Transfer ab (beim nächsten empfangenen Datenblock; Verbindung und
Datei werden geschlossen), ebenso „Überspringen“ und das Schließen des Assistenten. Der Abbruch ist kein Fehler:
„Download abgebrochen. Das Modell wurde nicht installiert.“ Die angefangene Datei (`.<id>.download/….part`) bleibt
liegen, ein späteres „Installieren“ setzt sie mit einer Range-Anfrage fort; als installiert erscheint der Ordner erst
nach vollständigem, geprüftem Download. War der Download beim Klick schon fertig, bleibt das Modell installiert.

Der Mikrofon-Knopf im Eingabefeld nimmt über Java Sound auf (Klick: Aufnahme an, zweiter Klick: Aufnahme aus und
erkennen); der erkannte Text landet am Cursor und wird nicht gesendet. Erkannt wird mit dem Modell aus `model.stt`.
Die Enterprise-API meldet bisher kein STT-Modell, deshalb gibt es heute nur lokale Modelle: ein Gemma-4-Modell mit
Audio-Eingang als ONNX-Export (Layout von transformers.js: `audio_encoder`, `embed_tokens`, `decoder_model_merged`,
je bevorzugt `_q4`), gerechnet im Client über ONNX Runtime, ohne externes Programm und ohne Umwandlung. Unter
Einstellungen → Modelle → „Lokale Modelle“ → „Hugging Face durchsuchen“ z. B. nach `gemma-4-E2B` suchen, den Treffer
„Spracheingabe · …“ installieren, oben unter „Spracheingabe (STT)“ wählen und speichern (gilt sofort). Die
Log-Mel-Merkmale rechnet der Client in Java nach `Gemma4AudioFeatureExtractor`; je Durchlauf höchstens 30 s Audio,
längere Aufnahmen gehen in Stücken durch. Das Modell ist groß (mehrere GB Download, mehrere GB Arbeitsspeicher) und
auf der CPU deutlich langsamer als Echtzeit-Diktat; Dauer je Aufnahme steht im Protokoll („Spracheingabe: … ms Audio
in … ms erkannt“).

## Demos ohne Backend

Die Demos liegen im Testumfang von `exedra` und brauchen weder Enterprise-API noch KeePass:

```bash
./gradlew :proasteion:exedra:runChatDemo     # Comic-Chat-Shell gegen FakeChatCompletionPort
./gradlew :proasteion:exedra:runRagDemo      # Shell mit RAG: echte Adapter gegen lokale Fake-HTTP-Server, Lucene im Temp-Verzeichnis
./gradlew :proasteion:exedra:runAgentDemo    # Chat (Fake) plus Agent-Modus mit dem Demo-Agenten als Kindprozess
```

In der RAG-Demo simuliert "fehler" im Text einen HTTP 500, "langsam" lässt die Antwort hängen, bis Stop
gedrückt wird; `--args="--screenshot datei.png"` schreibt die Shell ohne Fenster als PNG. In der Agent-Demo
lässt "slow" im Auftrag den Agenten streamen, bis Stop gedrückt wird.

## Fat Jar, Version und Releases

### Fat Jar bauen

```bash
./gradlew :proasteion:exedra:fatJar            # exedra/build/libs/enterprise-ai-client-<version>-SNAPSHOT.jar
./gradlew :proasteion:exedra:verifyFatJar      # Manifest, Vollständigkeit, ServiceLoader-Dateien, Hauptklasse ladbar
./gradlew :proasteion:exedra:smokeStartFatJar  # headless starten ohne Konfiguration: Vorlage angelegt, Exit-Code 2
./gradlew build                        # enthält alle drei (fatJar über assemble, die Prüfungen über check)
```

Das Fat Jar enthält `exedra` und alle Laufzeitabhängigkeiten (eigene Module, Lucene, Solon, Jackson, Gson,
jsoup, Java-WebSocket, ACP-SDK, win-proxy-java mit GraalJS für PAC-Proxyskripte; mit GraalJS wächst das Jar von
rund 16 auf rund 42 MB). Es entsteht ohne Zusatz-Plugin aus einer eigenen Jar-Task in
`gradle/fat-jar.gradle` (eingebunden von `exedra/build.gradle`), damit der Build auf JDK 8 wie auf JDK 21
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

### Update-Paket (inkrementell, verkettbar)

Neben dem Jar hängt am Release genau ein Update-Paket `enterprise-ai-client-patch-<vorversion>-to-<version>.zip`,
immer nur vom direkten Vorgänger-Release aus; Sprung-Pakete über mehrere Versionen gibt es nicht. Inhalt: die
gegenüber dem Vorgänger neuen und geänderten Jar-Einträge (`files/`), die Liste der entfernten (`removed.txt`), die
Änderungen (`changes.txt`, Commit-Titel seit der Vorversion) und `patch.properties` im Format 2 mit Basis- und
Zielversion und den Inhaltsprüfsummen beider Jars (`baseContentSha256`, `targetContentSha256`: Namen und Bytes
aller Einträge, unabhängig von Kompression, Zeitstempeln und Reihenfolge). Die Datei-Prüfsummen der Release-Jars
stehen nur zur Information darin. Der Erzeuger spielt das Paket probeweise ein und bricht ab, wenn es die
Zielversion nicht reproduziert. Lokal:

```bash
./gradlew :proasteion:exedra:fatJarPatch -PpatchBase=<vorversion.jar> [-PpatchTarget=<neue.jar>] [-PpatchChanges=<datei>]
```

Eingespielt wird in den Einstellungen unter „Merlins Kristallhöhle“ mit „Update einspielen …“. Die Dateiauswahl
nimmt ein oder mehrere Pakete; Dateinamen und Auswahlreihenfolge spielen keine Rolle. Die Anwendung bestimmt das
Jar, aus dem sie läuft (über die Code-Quelle, nicht über einen festen Namen), und sucht von dessen Version und
Inhaltsprüfsumme aus die lückenlose Kette: jeder Schritt muss mit Version und Inhaltsprüfsumme genau dort beginnen,
wo der vorige endet. Ältere oder doppelte Pakete bleiben liegen; fehlt der Anfang oder gibt es zwei verschiedene
Pakete ab derselben Version, lehnt sie ab und sagt, welches Update fehlt bzw. welche Pakete kollidieren. Fehlt ein
Zwischenstück weiter oben, zeigt die Vorschau, bis wohin die Kette reicht und welches Update fehlt. Die Vorschau
nennt Ausgangs- und Zielversion, die Schritte, die gesammelten Änderungen und die neue Datei.

Nach Bestätigung baut der Client die Schritte nacheinander in einem versteckten Arbeitsordner neben dem Jar,
prüft nach jedem Schritt Inhaltsprüfsumme, Manifest-Version und entfernte Einträge und legt erst am Ende
`enterprise-ai-client-<zielversion>.jar` per Umbenennen ab. Der Knopf zeigt dabei „Schritt X von Y: A → B“; ein
Klick bricht nach dem laufenden Schritt ab. Bei Fehler oder Abbruch bleibt keine neue oder halbe Datei zurück, der
Arbeitsordner wird gelöscht. Das laufende Jar wird nie verändert; die neue Datei startet man beim nächsten Mal
selbst. Eine vorhandene Zieldatei ersetzt der Client nur nach Rückfrage. Beim Start aus IDE oder
Klassenverzeichnis erklärt der Knopf, dass es kein Fat Jar als Basis gibt. Prüfsummen sichern, dass Inhalt und
Kette stimmen; sie sagen nichts darüber, wer ein Paket erstellt hat, deshalb nur Pakete aus der bekannten
Release-Quelle wählen. Pakete im alten Format 1 werden nicht mehr gelesen.

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
