---
id: concepts.local-vs-cloud
title: Lokale Modelle und KI-Dienst
keywords: lokal, Cloud, KI-Dienst, Enterprise-API, OpenAI-kompatibel, ONNX, ONNX Runtime, lokale Modelle, Cloud-Modelle, Modellverzeichnis, local-models, Hugging Face, offline, im Prozess, was läuft wo, Chat-Modell, Embedding-Modell, Sprachausgabe, Spracheingabe, TTS, STT, Stimme, Piper, VITS, Gemma, local vs cloud, on-premise
---

# Lokale Modelle und KI-Dienst

Der Enterprise AI Client kann KI-Modelle auf zwei Wegen nutzen: über einen **KI-Dienst**, den die Anwendung über das Netz anspricht, oder als **lokales Modell**, das direkt im Programm auf deinem Rechner rechnet. Dieses Kapitel erklärt den Unterschied, welche Funktionen auf welchem Weg laufen können, was dabei wo passiert und wie du die Wahl triffst. Es hilft dir einzuschätzen, welche Daten das Gerät verlassen und warum ein lokales Modell langsamer sein kann.

## Die zwei Wege im Überblick

| | KI-Dienst | Lokales Modell |
|---|---|---|
| Wo gerechnet wird | auf dem Server des KI-Dienstes | auf deinem Rechner, im Programm selbst |
| Schnittstelle | OpenAI-kompatible API (`/chat/completions`, `/embeddings`, `/models`, `/audio/speech`) | ONNX Runtime im Prozess, ohne externes Programm |
| Zugang | Basis-URL und API-Key (aus KeePass oder „Manuell“) | Modelldateien im Modellverzeichnis |
| Netz nötig | ja, für jede Anfrage | nur zum Herunterladen des Modells |
| Einstellungen | Einstellungen → „KI-Dienst“ und „Modelle“ → „Cloud-Modelle“ | Einstellungen → „Modelle“ → „Lokale Modelle“ |

Die Oberfläche spricht beim KI-Dienst auch von der Enterprise-API. Gemeint ist ein Dienst im OpenAI-kompatiblen Format, den deine Organisation betreibt, zum Beispiel unter `https://ki.example.org/v1`.

## Was der KI-Dienst übernimmt

Über den KI-Dienst laufen standardmäßig:

- **Chat**: jede Antwort im normalen Chat, die Nebenanfragen des Chats (prüfen, ob die gefundenen Treffer die Frage beantworten, Treffer bewerten, Anschlussfragen zu Zitaten vorschlagen) und alle Modellanfragen des eingebauten Recherche-Agenten.
- **Embeddings**: die Zahlenvektoren für die semantische Suche, sowohl für die Passagen beim Aufbereiten als auch für jede Suchanfrage.
- **Modellliste**: „Modelle aktualisieren“ im Reiter „Modelle“ fragt `GET /models` ab.
- **Sprachausgabe**, wenn ein TTS-Modell unter „Cloud-Modelle“ gewählt ist (Anfrage an `/audio/speech`). Hinweis: Dieser Weg ist mit den TTS-Modellen des Dienstes noch nicht praktisch geprüft.
- **Bildverständnis und Texterkennung** (Kategorien „Bildverständnis“ und „Dokumente / OCR“), wenn dafür Modelle gewählt sind.

Jede einzelne Anfrage an den KI-Dienst wird bei vorübergehenden Fehlern (Verbindungsfehler, Zeitüberschreitung, HTTP 429 oder 5xx) automatisch wiederholt, solange noch kein Antworttext sichtbar ist. Wie oft, stellst du unter Einstellungen → „Netzwerk & Agent“ → „Zeitlimits und Wiederholungen“ ein. Proxy, TLS-Zertifikate und User-Agent gelten für alle Anfragen an den Dienst, siehe [Netzwerk](help:settings.network).

## Was lokal laufen kann

Unter Einstellungen → „Modelle“ → „Lokale Modelle“ wählst du für vier Funktionen ein installiertes Modell:

| Funktion | Lokales Format | Hinweis |
|---|---|---|
| Chat | Decoder als ONNX-Export mit Zwischenspeicher (KV-Cache), `config.json`, `tokenizer.json` | es läuft immer nur eine Antwort gleichzeitig, weitere warten |
| Embeddings | ONNX-Export mit `config.json` und `tokenizer.json` | ein Wechsel bedeutet Neuindexieren |
| Sprachausgabe (TTS) | Piper-Stimme (`.onnx` mit `.onnx.json`) oder VITS | deutsche Aussprache regelbasiert |
| Spracheingabe (STT) | Gemma-Export mit Audio-Eingang | der KI-Dienst bietet keine Spracheingabe |

Alles rechnet die ONNX Runtime auf dem Prozessor, im Programm selbst. Es wird kein zusätzliches Programm gestartet, und Tokenizer sowie Audio-Aufbereitung sind in Java umgesetzt. Ein lokales Modell wird erst bei der ersten Anfrage geladen; die erste Antwort dauert deshalb länger.

Die **Spracheingabe** läuft ausschließlich lokal. Für alle anderen Funktionen gilt: genau eine Auswahl je Funktion. Eine Wahl unter „Lokale Modelle“ ersetzt die Wahl unter „Cloud-Modelle“ und umgekehrt.

Kein lokales Modell gibt es für Reranking, Bildverständnis, Texterkennung und Bild-Embeddings.

### Lokale Modelle installieren

1. Öffne Einstellungen → „Modelle“ → „Lokale Modelle“.
2. Unter „Vorschläge“ stehen empfohlene Modelle (deutsche Stimmen und ein Modell für die Spracheingabe), unter „Hugging Face durchsuchen“ findest du weitere. Angeboten werden nur Modelle, die der Client ohne Umwandlung laden kann.
3. Installiere das Modell. Die Dateien werden vom Hugging Face Hub geladen, geprüft (Prüfsumme) und im Modellverzeichnis abgelegt. Dieser Download geht über die Netzwerkeinstellungen (Proxy, TLS) der Anwendung.
4. Installieren wählt nichts aus: Wähle das Modell danach oben unter „Lokale Modelle je Funktion“.

Das Modellverzeichnis ist standardmäßig `<Anwendungsverzeichnis>/local-models`. Unter „Modellverzeichnis (optional)“ kannst du einen anderen Ordner angeben; in der Konfigurationsdatei heißt der Schlüssel `models.local.modelRoot`. Ausführlich: [Modelle einrichten](help:getting-started.models) und [Einstellungen: Modelle](help:settings.models).

## Was wo passiert: drei Beispiele

### Eine Frage im normalen Chat mit KI-Dienst

1. Die Anwendung sucht live in den angehakten Wissensquellen und im lokalen Index. Für die semantische Suche wird deine Frage in einen Vektor umgerechnet (Embedding, beim KI-Dienst oder lokal).
2. Die gefundenen Auszüge, der System-Prompt, der bisherige Verlauf und deine Frage gehen an das Chat-Modell des KI-Dienstes.
3. Die Antwort kommt gestreamt zurück und wird lokal gespeichert.

### Dieselbe Frage mit lokalem Chat- und Embedding-Modell

Die Wissensquellen werden weiterhin über das Netz abgefragt (Confluence, Wiki usw.), aber weder Frage noch Auszüge gehen an den KI-Dienst. Suche, Embedding und Antwort rechnet dein Rechner. Das ist deutlich langsamer, besonders bei langen Antworten, und es läuft nur eine Generierung gleichzeitig.

### Der eingebaute Recherche-Agent

Der Agent läuft als eigener Prozess auf deinem Rechner. Für jede Modellanfrage ruft er die Anwendung auf, und die Anwendung schickt die Anfrage an das im Composer gewählte Chat-Modell, also an den KI-Dienst oder an das lokale Modell. Der Agentenprozess selbst spricht nie mit dem KI-Dienst und kennt keinen API-Key.

## Ohne Neustart umschalten

Änderungen im Reiter „Modelle“ gelten nach dem Speichern sofort. Laufende Anfragen enden noch auf dem alten Modell, die nächste nimmt das neue. Eine Ausnahme mit Folgen ist das Embedding-Modell: Ein anderes Embedding-Modell arbeitet in einem eigenen Bereich des Index, die Wissensquellen müssen dafür neu aufbereitet werden. Warum, erklärt [Wissensindex und Suche](help:concepts.knowledge-index).

Ist ein lokales Embedding-Modell gewählt, aber nicht nutzbar (etwa weil Dateien fehlen), schickt die Anwendung die Texte **nicht** ersatzweise an den KI-Dienst. Die semantische Suche scheitert dann mit einem Hinweis auf Einstellungen → „Modelle“ → „Lokale Modelle“, die Volltextsuche funktioniert weiter.

## Grenzen

- Lokale Modelle rechnen nur auf dem Prozessor. Große Chat-Modelle sind entsprechend langsam und brauchen viel Arbeitsspeicher.
- Nur Formate ohne Umwandlung werden unterstützt (siehe Tabelle oben).
- Der Embedding-Endpunkt und die Sprachausgabe des KI-Dienstes sind gegen einen echten Dienst noch nicht vollständig geprüft. Hinweis: Das Verhalten hängt vom Server ab und ist nicht für alle Installationen geprüft. Mehr in [Bekannte Einschränkungen](help:concepts.limitations).

## Typische Probleme

### „Lokales Embedding-Modell … nicht nutzbar“

Das gewählte lokale Modell fehlt im Modellverzeichnis oder ist unvollständig. Prüfe unter „Lokale Modelle“ → „Installiert“, ob es aufgeführt ist, und ob „Modellverzeichnis (optional)“ auf den richtigen Ordner zeigt.

### Die erste lokale Antwort dauert sehr lange

Das Modell wird beim ersten Gebrauch geladen. Bei weiteren Anfragen entfällt das.

### Die Modellliste unter „Cloud-Modelle“ ist leer

„Modelle aktualisieren“ fragt den KI-Dienst ab. Ist er nicht erreichbar, bleiben die zuletzt bekannten Modelle stehen. Prüfe die Verbindung, siehe [Probleme mit Modellen](help:troubleshooting.models).

## Verwandte Kapitel

- [Einstellungen: KI-Dienst](help:settings.ai-service)
- [Einstellungen: Modelle](help:settings.models)
- [Datenschutz: welche Daten wohin gehen](help:concepts.privacy)
- [Wissensindex und Suche](help:concepts.knowledge-index)
