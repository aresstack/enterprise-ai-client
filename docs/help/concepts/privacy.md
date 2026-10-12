---
id: concepts.privacy
title: Datenschutz: welche Daten wohin gehen
keywords: Datenschutz, Privatsphäre, privacy, Daten, was wird gesendet, was bleibt lokal, KI-Dienst, Cloud, Embeddings, Chat-Verlauf, Chatverlauf speichern, Chats löschen, Protokoll, Log, Logdatei, Export, Chat exportieren, Schwärzung, geschwärzt, REDACTED, Anhänge, Bilder, Sprachausgabe, Spracheingabe, DSGVO, vertraulich
---

# Datenschutz: welche Daten wohin gehen

Dieses Kapitel beschreibt nüchtern, welche Daten der Enterprise AI Client an den KI-Dienst schickt, was nur auf deinem Rechner bleibt, was im Protokoll steht, wie gespeicherte Chats aufgebaut sind und was beim Export geschwärzt wird. Es hilft dir einzuschätzen, welche Inhalte du dem Chat anvertrauen kannst, und zeigt, wie du mit lokalen Modellen die Datenflüsse weiter einschränkst. Für Passwörter und Zugangsdaten siehe zusätzlich [Sicherheit](help:concepts.security).

## Die Beteiligten

- **Dein Rechner** mit der Anwendung, dem Wissensindex, den gespeicherten Chats und gegebenenfalls lokalen Modellen.
- **Die Wissensquellen** (zum Beispiel Confluence unter `confluence.example.org`, ein Wiki unter `wiki.example.org`, Dateien, SharePoint). Die Anwendung liest dort mit deinen Zugangsdaten. Was du dort nicht sehen darfst, sieht auch die Anwendung nicht.
- **Der KI-Dienst**, der Chat-, Embedding- und gegebenenfalls Sprach- und Bildmodelle bereitstellt. Er wird von deiner Organisation betrieben und über die Basis-URL im Reiter „KI-Dienst“ angesprochen.
- **Der Hugging Face Hub**, nur wenn du dort lokale Modelle herunterlädst.

Die Anwendung schickt keine Nutzungsstatistik und keine Telemetrie an Dritte.

## Was an den KI-Dienst geht

Nur wenn die jeweilige Funktion beim KI-Dienst läuft (und nicht lokal), gehen dorthin:

| Anlass | Inhalt |
|---|---|
| Chat-Antwort | System-Prompt, der bisherige Verlauf der Unterhaltung, deine Nachricht und der Kontextblock mit den gefundenen Auszügen aus deinen Wissensquellen |
| Werkzeugaufrufe im Chat | die Ergebnisse der Werkzeuge, also Suchtreffer, gelesene Seiten und gelesene Abschnitte von Anhängen |
| Nebenanfragen des Chats | Frage, Teile des Verlaufs und Kandidatentexte, wenn das Modell prüft, ob Treffer ausreichen, oder Treffer bewertet; zitierte Stellen, wenn es Anschlussfragen vorschlägt |
| Embeddings | die Texte der Passagen beim Aufbereiten und jede Suchanfrage |
| Agent-Modus | alles, was der Agent dem Modell schickt: deine Aufträge, Konzept, gelesene Passagen, Gliederung, Kapiteltexte; dazu der Text angehängter Dateien (bis 30.000 Zeichen je Datei) |
| Lesebereich | bei Bedarf Texte zum Benennen von Themenabschnitten einer Seite und, wenn du eine Bildbeschreibung anforderst, das Bild |
| Recherche-Quellen | nach jeder übernommenen Quelle bis zu acht Bilder der Seite, wenn Modelle für „Bildverständnis“ bzw. „Dokumente / OCR“ gewählt sind |
| Sprachausgabe | der vorzulesende Text, wenn ein TTS-Modell des KI-Dienstes gewählt ist |
| Modellliste | nur die Abfrage `GET /models`, ohne Inhalte |

Bei jeder Anfrage sendet die Anwendung den API-Key und einen User-Agent (Standard `EnterpriseAiClient/<Version>`). Ist in der Konfigurationsdatei `chat.user` gesetzt, geht dieser Wert als Benutzerkennung mit.

### Was im Chat nicht automatisch mitgeht

- **Anhänge im normalen Chat**: Das Modell erfährt zunächst nur Kennung, Dateiname und Größe. Den Inhalt liest es erst, wenn es dafür ein Werkzeug aufruft, und dann nur die angefragten Abschnitte.
- **Kontextblöcke früherer Antworten**: Gespeichert werden nur die Kennungen der verwendeten Quellen, nicht ihr Text. Spätere Fragen bekommen Auszüge nur, wenn die Suche sie erneut liefert.

## Was lokal bleibt

- **Spracheingabe**: Sie läuft immer lokal. Deine Aufnahme verlässt den Rechner nicht.
- **Lokale Modelle**: Ist ein Chat-, Embedding- oder TTS-Modell unter „Lokale Modelle“ gewählt, rechnet dein Rechner, und die entsprechenden Inhalte gehen nicht an den KI-Dienst. Siehe [Lokale Modelle und KI-Dienst](help:concepts.local-vs-cloud).
- **Wissensindex**: Rohtexte, Passagen und Vektoren der geladenen Seiten im Indexverzeichnis (Standard `<Anwendungsverzeichnis>/index`).
- **Gespeicherte Chats** im Ordner `chats` neben der Konfigurationsdatei, Research-Projekte im Ordner `research`, Favoriten (`favorites.json`), Seitennavigation (`navigation`), Bildbeschreibungen (`image-descriptions`).
- **Konfiguration, Anwendungszustand und Protokoll** im Anwendungsverzeichnis.
- **Der Agentenprozess** läuft auf deinem Rechner; sein Werkzeug-Endpunkt ist nur lokal erreichbar.

Das Anwendungsverzeichnis ist unter Windows `%APPDATA%\.enterprise-ai-client`, sonst `.enterprise-ai-client` im Benutzerverzeichnis. Den Ordner öffnest du über den Knopf „Einstellungsordner öffnen“ unten links in den Einstellungen.

Bedenke: Der Index enthält Kopien von Texten aus deinen Wissensquellen. Wer Zugriff auf dein Benutzerverzeichnis hat, kann sie lesen.

## Der Chat-Verlauf

Jeder Chat wird als Datei `chats/<Kennung>.json` neben der Konfigurationsdatei gespeichert, seine Anhänge als Kopien im Ordner `chats/<Kennung>/`. Gespeichert werden deine Nachrichten, die abgeschlossenen Antworten (auch die des Agenten), die Namen der Anhänge und die Kennungen der verwendeten Quellen. Laufende oder fehlgeschlagene Antworten sowie Werkzeug- und Gedankenwolken werden nicht gespeichert.

Löschen:

1. Öffne den Drawer, Reiter „Chats“.
2. Wähle beim Chat „Chat löschen“.
3. Der Chat ist bis zum Programmende nur vorgemerkt („Gelöscht“) und lässt sich mit „Wiederherstellen“ zurückholen.
4. Beim Beenden der Anwendung werden Chat-Datei, Anhänge und das zugehörige Research-Projekt entfernt.

Mehr: [Chat-Verlauf](help:chat.history).

## Das Protokoll (Log)

Die Anwendung schreibt ein technisches Protokoll nach `<Anwendungsverzeichnis>/logs/enterprise-ai-client.0.log`. Es rolliert über drei Dateien zu je etwa 2 MB, ältere Einträge werden überschrieben.

Im Protokoll stehen:

- die Konfiguration, wobei Zugangsdaten nur als Verweis auf den KeePass-Eintrag erscheinen,
- Verbindungsfehler mit ihrer Ursachenkette, Ergebnisse von Verbindungstests,
- Indexierungsberichte, Adressen und Kennungen von Quellen und Seiten,
- im Agent-Modus die Arbeitszeilen des eingebauten Recherche-Agenten, die zum Beispiel Suchanfragen enthalten können,
- Diagnosedaten zur Sprachausgabe, vom Text aber nur Länge und ein Fingerabdruck.

Nicht ins Protokoll gehören API-Key, Passwörter, Tokens und die Werkzeug-Adresse des Agenten. Die Inhalte deiner Chat-Nachrichten und Antworten schreibt die Anwendung nicht gezielt ins Protokoll; Seitentitel und Suchbegriffe können aber in Meldungen vorkommen. Gib das Protokoll deshalb nur an Personen weiter, die die Inhalte sehen dürfen.

## Exporte mit Schwärzung

Mit „Chat exportieren …“ (Kontextmenü eines Chats im Drawer-Reiter „Chats“) speicherst du einen Chat als „JSON (*.json)“, „Markdown (*.md)“ oder „Word-Dokument (*.docx)“, etwa um einen Fehler nachvollziehbar zu machen.

- Grundlage ist allein der gespeicherte Stand. Bei Agent-Chats kommen die Dateien des Research-Projekts dazu (Phase, Quellen, Konzept, Gliederung, Dokument).
- Was nie gespeichert wird, nennt der Export ausdrücklich, statt es zu erfinden.
- **Vor dem Schreiben wird geschwärzt**: API-Keys, Authorization-Kopfzeilen, Bearer-Tokens, Session-Cookies, Passwörter und Einmalcodes werden durch `[REDACTED]` ersetzt, ebenso Werte von Feldern, deren Name nach Zugangsdaten aussieht (`password`, `token`, `otpCode` …). Der Export zählt, wie oft geschwärzt wurde.
- Der übrige Text bleibt unverändert. Fachliche Inhalte, Namen oder Seitentexte werden **nicht** geschwärzt.

Prüfe einen Export vor der Weitergabe selbst. Details: [Chat exportieren](help:chat.export).

## Daten weiter einschränken

- Wähle lokale Modelle für Chat und Embeddings, wenn Inhalte den Rechner gar nicht verlassen sollen.
- Hake im Drawer-Reiter „Wissensquellen“ nur die Quellen an, die für die Frage nötig sind. Abgewählte Quellen werden weder durchsucht noch an das Modell gegeben.
- Schalte im Reiter „Tools“ Werkzeuge ab, die das Modell nicht benutzen soll, siehe [Werkzeuge von Chat und Agent](help:agent.tools).
- Wähle keine Modelle für „Bildverständnis“ und „Dokumente / OCR“, wenn Bilder nicht an den KI-Dienst gehen sollen.

## Typische Fragen

### Liest der KI-Dienst meine ganzen Wissensquellen?

Nein. Er bekommt nur die Auszüge und Seiten, die für eine Frage gefunden oder gelesen wurden, und die Passagentexte zum Berechnen der Embeddings, sofern das Embedding-Modell beim KI-Dienst läuft.

### Wird meine Spracheingabe irgendwohin geschickt?

Nein, die Spracheingabe rechnet immer lokal. Der erkannte Text wird danach wie eine getippte Nachricht behandelt.

## Verwandte Kapitel

- [Sicherheit](help:concepts.security)
- [Lokale Modelle und KI-Dienst](help:concepts.local-vs-cloud)
- [Chat-Verlauf](help:chat.history)
- [Chat exportieren](help:chat.export)
