---
id: getting-started.models
title: Modelle auswählen
keywords: Modelle, Modell, Modellauswahl, Chat-Modell, Embedding-Modell, Embeddings, Vektor, Vektorlänge, Dimension, Embedding-Dimension, Dimension ermitteln, Modelle abfragen, automatische Wahl, vorgewählt, Sprachausgabe-Modell, TTS, Text-to-Speech, Stimme, Spracheingabe-Modell, STT, Speech-to-Text, Diktat, lokales Modell, Cloud-Modell, GET /models, model, embedding.dimension, LLM, Sprachmodell
---

# Modelle auswählen

Der Enterprise AI Client arbeitet mit mehreren Modellen, je nach Aufgabe ein anderes: ein Chat-Modell formuliert die Antworten, ein Embedding-Modell macht deine Wissensquellen inhaltlich durchsuchbar, ein Sprachausgabe-Modell liest Antworten vor und ein Spracheingabe-Modell wandelt Gesprochenes in Text. Dieses Kapitel erklärt, welche davon du im Einrichtungsassistenten festlegst, wie die automatische Vorauswahl funktioniert, was die Embedding-Dimension ist und wo du die übrigen Modelle später einstellst.

## Welche Modelle es gibt

| Modell | Wofür | Wo festgelegt |
|---|---|---|
| Chat-Modell | Antworten im Chat, Merlins freie Antworten | Assistent, Schritt „Modelle“; später Einstellungen → Modelle |
| Embedding-Modell | Wissenssuche nach Bedeutung (semantische Suche) | Assistent, Schritt „Modelle“; später Einstellungen → Modelle |
| Sprachausgabe (TTS) | Vorlesen von Antworten | Einstellungen → Modelle bzw. Sprachausgabe |
| Spracheingabe (STT) | Diktieren ins Eingabefeld | Assistent, letzter Schritt (optional); später Einstellungen → Modelle → „Lokale Modelle“ |

Pflicht für den Betrieb sind nur Chat-Modell, Embedding-Modell und Embedding-Dimension. Sprachausgabe und Spracheingabe sind Zusatzfunktionen.

## Schritt „Modelle“ im Assistenten

Der Schritt zeigt die Felder „Chat-Modell“, „Embedding-Modell“, „Embedding-Dimension“ und „Sprachausgabe“ sowie die Knöpfe „Dimension ermitteln“ und „Modelle abfragen“. Der Erklärtext sagt: „Die Liste kommt vom KI-Dienst; je Art ist ein Modell vorausgewählt. Die Vektorlänge des Embedding-Modells ermittelt ein kleiner Test-Aufruf. Sprachausgabe ist optional.“

1. Die Modellliste wird schon nach dem erfolgreichen Verbindungstest im Schritt „KI-Dienst“ im Hintergrund abgefragt. Kommst du direkt in den Schritt, fragt er selbst („Frage die Modelle des KI-Dienstes ab …“).
2. Der Assistent wählt je Art selbst ein Modell ([siehe unten](help:getting-started.models#die-automatische-wahl)). Bietet der Dienst mehrere an, ergänzt die Statuszeile „Vorausgewählt; bei Bedarf ändern.“
3. Passt dir die Wahl nicht, wähle in „Chat-Modell“ das Modell für deine Gespräche und in „Embedding-Modell“ das Modell für die Wissenssuche. Die Felder sind auch eintippbar.
4. Sobald ein Embedding-Modell feststeht, ermittelt der Assistent die Dimension selbst ([siehe unten](help:getting-started.models#die-embedding-dimension)).
5. Sind Chat- und Embedding-Modell gesetzt und die Dimension eine positive ganze Zahl, wird „Weiter“ aktiv.

„Modelle abfragen“ holt die Liste neu, etwa wenn der Dienst inzwischen neue Modelle anbietet.

## Die automatische Wahl

Seit 0.48.2 wählt der Assistent je Art (Chat, Embedding, Sprachausgabe) ohne Klick ein Modell, nach einer festen Reihenfolge:

1. Ist schon ein Modell gewählt und bietet der Dienst es noch an, bleibt es.
2. Sonst das Vorzugsmodell, das mit dem Programm ausgeliefert wird (für Chat ein gpt-oss-120b, für Embeddings ein e5-base-sts-en-de, für die Sprachausgabe ein Qwen3-TTS-CustomVoice), wenn der Dienst genau dieses anbietet.
3. Sonst genau ein angebotenes Modell, dessen Name den Kern des Vorzugsmodells enthält. Passen mehrere, greift diese Regel nicht.
4. Sonst das erste angebotene Modell dieser Art.

Fehlt das Vorzugsmodell, ist das kein Fehler. Die Wahl ist nur ein Vorschlag; du kannst jedes Feld ändern. Gleich nach der Wahl misst der Assistent die Embedding-Dimension.

In die Listen kommen nur Modelle, die der KI-Dienst für diese Art meldet; lokal installierte Modelle stehen hier nicht. Kennt der Dienst die Art eines Modells nicht oder liefert er gar keine Liste, kannst du den Modellnamen auch eintippen, genau so, wie der Dienst ihn erwartet.

## Die Embedding-Dimension

Ein Embedding-Modell verwandelt jeden Text in einen Vektor, eine lange Liste von Zahlen. Die Dimension ist die Länge dieser Liste, zum Beispiel 768. Der Client braucht sie, um den Suchindex passend anzulegen.

Du musst die Zahl nicht kennen. Der Assistent misst sie mit einem kleinen Test-Aufruf an das gewählte Embedding-Modell:

- Während der Messung steht dort „Ermittle die Dimension mit einem Test-Aufruf …“.
- Bei Erfolg trägt er die Zahl ein: „Ermittelt: 768 Werte je Vektor.“
- Wählst du ein anderes Embedding-Modell, misst er automatisch neu. „Dimension ermitteln“ ist nur noch die manuelle Wiederholung für dasselbe Modell.
- Klappt die Messung nicht, steht dort „Nicht automatisch ermittelbar; bitte die Dimension eintragen.“, und die Fehlerplatte nennt den Grund. Dann kannst du die Zahl von Hand eintragen, wenn du sie kennst (etwa aus der Beschreibung des Modells).

Gut zu wissen: Liefert der Dienst später einheitlich eine andere Länge als eingetragen, übernimmt der Client die gelieferte und schreibt beide Werte ins Protokoll. Eine falsche Zahl legt den Client also nicht lahm, sorgt aber für eine Warnung.

## Sprachausgabe-Modell (Vorlesen)

Seit 0.48.2 hat der Schritt „Modelle“ die Zeile „Sprachausgabe“. Sie wählt das Modell, mit dem der Chat Antworten vorliest, nach derselben Regel wie oben vor. Die Zeile ist optional: Ohne Sprachausgabe-Modell bleibt der Vorlese-Knopf über dem Chatverlauf aus; alles andere funktioniert. Merlins eigene Stimme ändert diese Zeile nie.

Später änderst du es so:

- Einstellungen → Modelle → „Cloud-Modelle“: in der Zeile „Sprachausgabe (TTS)“ ein Modell des KI-Dienstes wählen, oder
- Einstellungen → Modelle → „Lokale Modelle“: eine lokale Stimme installieren und wählen. Lokale Stimmen laufen im Client selbst.
- Einstellungen → Sprachausgabe zeigt dasselbe Modell zusammen mit der Stimme. Bei Modellen mit mehreren Sprechern wählst du dort eine feste Stimme, damit nicht jeder Absatz anders klingt; „Stimme testen“ spricht einen kurzen Satz.

Je Funktion gibt es genau eine Auswahl: Eine Wahl unter „Lokale Modelle“ ersetzt die unter „Cloud-Modelle“ und umgekehrt.

Merlin, der Zauberer des Assistenten, hat eine eigene Stimme, unabhängig von diesem Modell. Die Stimme des Chat-Assistenten („Merlins Tochter“) ist dagegen genau dieses Sprachausgabe-Modell. Details: [Sprachausgabe einstellen](help:settings.speech), [Antworten vorlesen](help:chat.read-aloud).

## Spracheingabe-Modell (Diktat)

Der KI-Dienst bietet bisher kein Modell für Spracheingabe an. Deshalb gibt es dafür nur lokale Modelle, die im Client laufen. Der Assistent bietet im optionalen letzten Schritt „Lokale Spracheingabe“ an, eines herunterzuladen und zu wählen. Vorgeschlagen wird ein Gemma-Modell mit Audio-Eingang. Es ist groß (mehrere Gigabyte Download und Arbeitsspeicher) und auf einem normalen Prozessor deutlich langsamer als ein Echtzeit-Diktat.

Ablauf und Download sind im Kapitel [Der Einrichtungsassistent](help:getting-started.setup) beschrieben. Nachholen kannst du das unter Einstellungen → Modelle → „Lokale Modelle“: unter „Vorschläge“ installieren oder unter „Hugging Face durchsuchen“ suchen, danach oben in der Zeile „Spracheingabe (STT)“ wählen. Siehe [Diktieren](help:chat.dictation).

## Modelle später ändern

- **Einstellungen → Modelle**: je Funktion ein Modell, mit „Modelle aktualisieren“ für eine neue Abfrage beim Dienst. Neben Chat, Embeddings, Sprachausgabe und Spracheingabe gibt es dort weitere Funktionen (Reranking, Bildverständnis, Dokumente / OCR, Bild-Embeddings). Siehe [Modelle einstellen](help:settings.models).
- **Im Eingabefeld des Chats** zeigt links ein Knopf das aktuelle Chat-Modell. Ein Klick listet die abgefragten Chat-Modelle; „Modelle verwalten …“ führt zu den Einstellungen.
- **Einrichtungsassistent erneut starten** geht ebenfalls, siehe [Der Einrichtungsassistent](help:getting-started.setup).

Gespeicherte Änderungen gelten sofort, ohne Neustart.

### Achtung beim Wechsel des Embedding-Modells

Ein anderes Embedding-Modell ist eine andere „Vektorwelt“: Vektoren verschiedener Modelle sind nicht vergleichbar. Der Client legt für jedes Modell (mit seiner Dimension) einen eigenen Bereich im Index an. Nach einem Wechsel müssen deine Wissensquellen deshalb neu indexiert werden, damit die semantische Suche wieder Treffer liefert. Siehe [Indexierung](help:knowledge.indexing) und [Der Wissensindex](help:concepts.knowledge-index).

## Was im Hintergrund passiert

- Die Modellliste kommt von der Abfrage `GET /models` des KI-Dienstes. Welche Modelle als Chat- oder Embedding-Modell gelten, richtet sich nach den Fähigkeiten, die der Dienst je Modell meldet.
- Die Messung der Dimension ist ein echter, kleiner Embedding-Aufruf mit deinem API-Key aus KeePass.
- In der Konfigurationsdatei stehen danach `chat.model`, `embedding.model` und `embedding.dimension`, bei der Spracheingabe `model.stt` (lokale Modelle mit dem Präfix `local:`).

## Typische Probleme

Was die Meldungen im Schritt „Modelle“ bedeuten und was du tun kannst.

### „Keine Modelle gefunden; die Namen lassen sich auch eintragen.“

Der Dienst hat keine passenden Modelle gemeldet oder die Liste war nicht abrufbar. Steht zusätzlich „Die Modellliste ist nicht abrufbar: …“ in der Fehlerplatte, prüfe zuerst den Schritt „KI-Dienst“ mit „Verbindung testen“. Kennst du die Modellnamen, trage sie von Hand ein.

### „Der Embedding-Test ist fehlgeschlagen: …“

Das Modell ist kein Embedding-Modell, der Name ist falsch geschrieben oder der Dienst lehnt den Aufruf ab. Wähle ein anderes Modell aus der Liste oder trage die Dimension von Hand ein. Hinweis: Ob der Embedding-Endpunkt deines Dienstes genau so antwortet wie erwartet, ist nicht für alle Installationen geprüft.

### Mein Wunschmodell ist nicht vorgewählt

Das ist Absicht, wenn der Dienst mehrere Modelle dieser Art anbietet. Wähle es aus der Liste.

Weitere Hilfe: [Probleme mit Modellen](help:troubleshooting.models), [Lokal oder Cloud](help:concepts.local-vs-cloud).
