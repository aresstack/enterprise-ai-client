---
id: settings.models
title: Einstellungen – Reiter „Modelle“
keywords: Modelle, Modell, Chat-Modell, Embedding-Modell, Reranking, TTS, STT, Sprachausgabe, Spracheingabe, Bildverständnis, OCR, Bild-Embeddings, Cloud-Modelle, Lokale Modelle, Modelle aktualisieren, GET /models, Hugging Face, ONNX, installieren, Stimme installieren, Modellverzeichnis, local-models, OpenNLP, Satzerkennung, nicht im Katalog, chat.model, embedding.model, model.tts
---

# Einstellungen – Reiter „Modelle“

Im Reiter „Modelle“ wählst du für jede Funktion des Clients genau ein Modell: für den Chat, für Embeddings, für Reranking, für Sprachausgabe und Spracheingabe und für einige weitere Funktionen. Modelle können aus zwei Quellen kommen: von der Enterprise-API („Cloud-Modelle“) oder als lokal installierte Modelle, die im Client selbst rechnen („Lokale Modelle“). Außerdem installierst, aktualisierst und entfernst du hier lokale Modelle und suchst neue auf dem Hugging Face Hub. Was lokale und Cloud-Modelle grundsätzlich unterscheidet, erklärt [Lokal oder Cloud](help:concepts.local-vs-cloud).

Oben im Reiter stehen zwei Unterreiter: „Cloud-Modelle“ (beim Öffnen gewählt) und „Lokale Modelle“.

## Eine Auswahl je Funktion

Für jede Funktion gibt es genau eine Auswahl. Beide Unterreiter sind nur zwei Sichten auf dieselbe Auswahl: Wählst du unter „Lokale Modelle“ eine lokale Stimme für die Sprachausgabe, ist das Cloud-Modell für die Sprachausgabe automatisch abgewählt, und umgekehrt. In der Liste des jeweils anderen Unterreiters steht dann „– lokales Modell gewählt (Reiter „Lokale Modelle“) –“ bzw. „– Cloud-Modell gewählt (Reiter „Cloud-Modelle“) –“.

Die Funktionen heißen im Dialog:

| Funktion | Schlüssel in der Datei | Unter „Cloud-Modelle“ | Unter „Lokale Modelle“ |
|---|---|---|---|
| „Chat“ | `chat.model` | ja, Pflicht | ja |
| „Embeddings“ | `embedding.model` | ja, Pflicht | ja |
| „Reranking“ | `model.rerank` | ja | nein |
| „Sprachausgabe (TTS)“ | `model.tts` | ja | ja |
| „Spracheingabe (STT)“ | `model.stt` | ja | ja |
| „Bildverständnis“ | `model.vision` | ja | nein |
| „Dokumente / OCR“ | `model.documentOcr` | ja | nein |
| „Bild-Embeddings“ | `model.imageEmbedding` | ja | nein |

Standard ist für alle Funktionen außer Chat und Embeddings „– keine Auswahl –“. Chat- und Embedding-Modell haben keinen Standard; ohne sie lässt sich nicht speichern.

## Unterreiter „Cloud-Modelle“

### Bereich „Modelle je Funktion“

Hinweis im Dialog: „Je Funktion ein Modell der Enterprise-API. Die Listen zeigen nur Modelle, deren gemeldete Fähigkeiten passen. Eine Wahl unter „Lokale Modelle“ ersetzt die Wahl hier und umgekehrt.“

- **Chat und Embeddings** sind Listen, in die du auch tippen kannst. So lässt sich ein Modell auch ohne Verbindung setzen. Das Eingabefeld zeigt die gespeicherte Modellkennung, die aufgeklappte Liste die passenden Modelle des Katalogs. Tooltip: Modellkennung, wie sie der Dienst nennt, eintippen (optional mit Katalog davor, etwa `local:…`) oder aus der Liste wählen.
- **Alle anderen Funktionen** sind reine Auswahllisten mit „– keine Auswahl –“ als erstem Eintrag. Gibt es keine passenden Modelle, ist die Liste gesperrt.

Ein Listeneintrag zeigt den Namen des Modells, in Klammern seine Kennung (wenn sie abweicht), den Katalog und, wenn das Modell Werkzeuge aufrufen kann, „Tool-Calling“. Eine gespeicherte Auswahl, die der Katalog gerade nicht kennt (etwa weil die Quelle nicht erreichbar ist), bleibt erhalten und erscheint als „… (nicht im Katalog)“.

### Bereich „Modellquellen“

- **Knopf „Modelle aktualisieren“** (Tooltip: „Fragt mit dem aktuellen Entwurf GET /models der Enterprise-API ab“). Während der Abfrage heißt er „Frage Modellquellen ab …“ und ist gesperrt.
- **Statuszeilen** darunter: je Quelle eine Zeile, mit Haken, wenn sie erreichbar war, oder mit Ausrufezeichen und dem Grund, wenn nicht. Gab es noch nie eine Abfrage: „Noch keine Modellliste. „Modelle aktualisieren“ fragt GET /models ab.“

Die Listen kommen zuerst aus dem Zwischenspeicher der letzten Abfrage (Datei `model-catalog.properties` im Anwendungsverzeichnis). Beim ersten Anzeigen des Reiters fragt der Dialog die Quellen einmal im Hintergrund neu ab, ebenso beim ersten Anzeigen des Reiters „Sprachausgabe“. Die Abfrage nutzt den aktuellen Entwurf, also auch ungespeicherte Änderungen an Adresse, KeePass oder Proxy. Fehlen beim Erststart nur Chat- oder Embedding-Modell, fragt sie trotzdem ab, damit die Liste bei der Wahl helfen kann.

## Unterreiter „Lokale Modelle“

Lokale Modelle rechnen im Client selbst über ONNX Runtime, ohne externes Programm und ohne Umwandlung. Der Unterreiter hat sechs Bereiche.

### Bereich „Lokale Modelle je Funktion“

Hinweis: „Installierte Modelle je Funktion, gerechnet im Client über ONNX Runtime; eine Wahl hier ersetzt die Wahl unter „Cloud-Modelle“ und umgekehrt. Ein anderes Embedding-Modell heißt: Wissensquellen neu indexieren.“

Hier stehen Auswahllisten für „Chat“, „Embeddings“, „Sprachausgabe (TTS)“ und „Spracheingabe (STT)“. Sie zeigen nur installierte lokale Modelle, mit dem Zusatz „Lokal“. Gespeichert wird ein lokales Modell mit dem Präfix `local:` und dem Ordnernamen, etwa `local:<ordner>`.

### Bereich „Installiert“

Hinweis: „Installierte Modelle im Modellverzeichnis, geladen vom Hugging Face Hub ohne Umwandlung. Sie laufen im Client selbst (ONNX Runtime), ohne externes Programm. Installieren wählt nichts aus: gewählt wird oben je Funktion.“

Je installiertem Modell eine Zeile mit Namen, dem Stand „installiert“ und zwei Knöpfen:

- **„Aktualisieren“** lädt die Dateien neu vom Hugging Face Hub; die Auswahl bleibt.
- **„Entfernen“** löscht die Dateien aus dem Modellverzeichnis. War das Modell irgendwo gewählt, wird diese Auswahl geleert.

Ohne installierte Modelle steht „Noch keine lokalen Modelle installiert.“ Konnten die Angebote nicht ermittelt werden, steht „Die lokalen Modelle ließen sich nicht ermitteln.“ mit dem Grund; beim nächsten Anzeigen versucht der Dialog es erneut.

### Bereich „Vorschläge“

Hinweis: „Vorgeschlagene Modelle zum Installieren über die Netzwerkeinstellungen: deutsche Stimmen (Aussprache regelbasiert) und Gemma 4 E2B für die Spracheingabe. Installiert stehen sie oben.“ Je Vorschlag gibt es „Installieren“. Sind alle installiert: „Alle Vorschläge sind installiert.“

### Bereich „Hugging Face durchsuchen“

Eine Zeile mit der Auswahl der Funktion („Alle Funktionen“, „Chat“, „Embeddings“, „Sprachausgabe (TTS)“, „Spracheingabe (STT)“), einem Suchfeld und dem Knopf „Suchen“. Leer sucht nach den beliebtesten Modellen. Das Suchfeld merkt sich deine letzten Suchbegriffe; ein Begriff aus der Aufklappliste sucht sofort. Beispiele aus dem Tooltip: „thorsten“, „multilingual-e5“, „qwen instruct“.

Angeboten werden nur Modelle, die der Client ohne Umwandlung laden kann:

- **Sprachausgabe:** Piper-Stimmen (`.onnx` mit `.onnx.json`, deutsch oder mit Text-Eingabe) oder VITS-Modelle (`config.json`, `vocab.json`, `onnx/model.onnx`).
- **Embeddings:** ONNX-Export mit `config.json` und `tokenizer.json` (WordPiece, BPE oder Unigram).
- **Chat:** Decoder-ONNX-Export mit KV-Cache (`past_key_values`), `config.json` und `tokenizer.json`.
- **Spracheingabe:** Gemma-Export mit Audio-Eingang (`audio_encoder`, `embed_tokens`, `decoder_model_merged`).

Nach der Suche steht „n ladbare Modelle gefunden.“ oder „Keine im Client ladbaren Modelle gefunden.“ Treffer haben dieselben Knöpfe wie die Vorschläge.

### Installieren und Fortschritt

„Installieren“ lädt die Dateien über die Netzwerkeinstellungen des Entwurfs (Proxy, TLS) ins Modellverzeichnis. Unter der Zeile erscheint je Datei ein Fortschrittsbalken bis 100 Prozent mit „Datei n von m“. Danach meldet die Zeile zum Beispiel „… installiert; wählbar oben unter „Sprachausgabe (TTS)““. Während ein Download läuft, sind alle anderen Knöpfe gesperrt. Installieren wählt nichts aus: wähle das Modell danach im Bereich „Lokale Modelle je Funktion“ und speichere.

### Bereich „Satz- und Spracherkennung (Vorlesen)“

Hinweis: „Vorlesen teilt Antworten wie AskAI mit Apache OpenNLP in Sätze und erkennt die Sprache. Ohne diese Modelle wird an Satzzeichen geteilt.“ Je Modell eine Zeile mit Stand und „Installieren“ (während des Ladens „Lade …“). Größe und SHA-256 werden beim Laden geprüft.

### Bereich „Modellverzeichnis“

- **Beschriftung:** „Modellverzeichnis (optional)“.
- **Standard:** leer = Ordner `local-models` im Anwendungsverzeichnis.
- **Wirkung:** Hier liegen alle lokal installierten Modelle. Änderst du das Verzeichnis, liest der Dialog beim nächsten Anzeigen des Unterreiters den Installationsstand neu. Ein Modell lässt sich auch von Hand als eigener Ordner dort ablegen; der Ordnername ist dann der Modellname.

## Was im Hintergrund passiert

Welche Modelle in welche Funktion passen, entscheidet der Client allein aus den Metadaten, die die Quelle meldet. Die Enterprise-API liefert ihre Modelle über `GET /models`; lokale Modelle meldet das Modellverzeichnis. Nach dem Speichern bindet das Programm Chat, Embeddings, Sprachausgabe und Spracheingabe ohne Neustart an die neue Auswahl. Ein laufendes Vorlesen endet beim Wechsel des TTS-Modells sofort; die neue Stimme spricht ab der nächsten Antwort.

## Grenzen

- Ein anderes Embedding-Modell bildet eine eigene „Embedding-Welt“ im Index: danach müssen die Wissensquellen neu indexiert werden. Siehe [Indexieren](help:knowledge.indexing).
- Ein lokales Chat-Modell rechnet immer nur eine Antwort gleichzeitig. Werkzeug-Chat (etwa Anhänge lesen) läuft nur über die Enterprise-API. Mit echten Modellen vom Hub ist der lokale Chat bisher nicht geprüft.
- Die Enterprise-API meldet bisher kein Modell für die Spracheingabe; dafür gibt es derzeit nur lokale Modelle. Das vorgeschlagene Gemma-Modell ist groß (mehrere GB Download und Arbeitsspeicher) und auf der CPU deutlich langsamer als Echtzeit.
- Das Modell im Eingabebereich des Chats lässt sich auch dort umschalten; „Modelle verwalten …“ führt in diesen Dialog.

## Typische Probleme

### Die Listen sind leer oder gesperrt

Klicke „Modelle aktualisieren“ und lies die Statuszeile darunter. Ein Ausrufezeichen nennt den Grund, etwa einen fehlenden Key oder einen Netzwerkfehler. Prüfe dann im Reiter [KI-Dienst](help:settings.ai-service) „Verbindung testen“. Chat und Embeddings kannst du notfalls von Hand eintippen.

### Neben meinem Modell steht „(nicht im Katalog)“

Die Quelle kennt das Modell gerade nicht, oder sie war bei der letzten Abfrage nicht erreichbar. Die Auswahl bleibt gespeichert. Aktualisiere die Liste; fehlt das Modell weiter, wähle ein anderes.

### Die Installation bricht ab

Die Zeile nennt den Grund („Installation fehlgeschlagen: …“). Meist fehlt der Weg ins Internet; prüfe Proxy und TLS im Reiter [Netzwerk & Agent](help:settings.network). Weitere Hilfe: [Probleme mit Modellen](help:troubleshooting.models).
