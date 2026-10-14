---
id: settings.index
title: Einstellungen – Reiter „Index“
keywords: Index, Indexverzeichnis, Lucene, Wissenssuche, Retrieval, RAG, Kandidaten, Volltext, BM25, semantisch, Max. Quellen, Kontextbudget, Tokens, Mindestzahl Quellen, Nachrecherche, Live-Suche, Reranking, Textfenster, Rohindex, Aufbereitung, Worker, Embedding-Batchgröße, Embedding-Parallelität, Limits, Grenzen, knowledge.indexDirectory, context.maxSources, retrieval.maxResults
---

# Einstellungen – Reiter „Index“

Der Reiter „Index“ bestimmt, wo der Client seinen Wissensindex ablegt und wie die Wissenssuche einer normalen Chat-Antwort arbeitet: wie viele Treffer sie sammelt, wie viele Quellen höchstens in eine Antwort gelangen, wie groß der Kontext sein darf, wann nachgesucht wird und wie viel parallel im Hintergrund aufbereitet wird. Die Standardwerte passen für die meisten Fälle; ändere sie nur, wenn du weißt, was du erreichen willst. Wie der Index grundsätzlich aufgebaut ist, erklärt [Der Wissensindex](help:concepts.knowledge-index).

Der Reiter hat drei Bereiche: „Index“, „Wissenssuche im Chat“ und „Laden und Aufbereitung“. Alle Werte gelten nach dem Speichern ohne Neustart, die Werte der Wissenssuche ab der nächsten Frage.

## Bereich „Index“: das Indexverzeichnis

Hinweis im Dialog: „Der Lucene-Index mit Volltext und Vektoren. Leer: Verzeichnis „index“ im Anwendungsverzeichnis.“

- **Beschriftung:** „Indexverzeichnis (optional)“, daneben „Wählen …“.
- **Standard:** leer = Ordner `index` im Anwendungsverzeichnis.
- **Bedeutung:** Das Verzeichnis für den Lucene-Index und die Vektoren. Dort liegt auch der Rohindex (Unterordner `raw`).
- **Bedienung:** „Wählen …“ öffnet „Indexverzeichnis wählen“, in dem du nur Ordner auswählen kannst. Du kannst den Pfad auch eintippen. Leerst du das Feld, gilt wieder das Standardverzeichnis.
- **Wirkung:** Nach dem Speichern verwendet das Programm das neue Verzeichnis ohne Neustart. Ein neues, leeres Verzeichnis bedeutet einen leeren Index: die Suche füllt ihn nach und nach mit ihren Treffern, oder du indexierst eine Quelle ganz (siehe [Indexieren](help:knowledge.indexing)). Der alte Index bleibt, wo er war.

Ein Problem mit dem Indexverzeichnis führt beim Speichern direkt auf diesen Reiter.

## Bereich „Wissenssuche im Chat“

Der Hinweis im Dialog fasst den Ablauf zusammen: Die Suche liefert höchstens „Max. Retrieval-Kandidaten“; das Modell bewertet sie in einem Aufruf gegen die Frage und nimmt die kleinste Menge, die reicht, höchstens „Max. Quellen“. Reicht nichts, lädt die Live-Suche weitere Seiten oder der Chat sucht genauer nach; danach antwortet er mit dem Vorhandenen und nennt die Lücke. Das Recherche-Budget des Agenten im Reiter [Netzwerk & Agent](help:settings.network) ist davon getrennt.

| Feld | Standard | Erlaubt | Bedeutung |
|---|---|---|---|
| „Volltext-Kandidaten“ | 20 | 1–1000 | Treffer der Volltextsuche (BM25) je Suche vor der Zusammenführung |
| „Semantische Kandidaten“ | 20 | 1–1000 | Treffer der semantischen Suche (Ähnlichkeit der Vektoren) je Suche vor der Zusammenführung |
| „Max. Retrieval-Kandidaten“ | 10 | 1–1000 | So viele zusammengeführte Treffer bewertet das Modell je Runde |
| „Max. Quellen im Antwortkontext“ | 6 | 1–1000 | Harte Obergrenze: höchstens so viele Quellen gelangen in die Antwort |
| „Kontextbudget (Tokens)“ | 1500 | 1–1000000 | Geschätzte Tokens für den ganzen Kontextblock; passt ein weiterer Auszug nicht hinein, endet der Block |
| „Mindestzahl unabhängiger Quellen“ | 1 | 0–20 | So viele verschiedene Seiten (nicht Abschnitte derselben Seite) müssen die Antwort stützen; 0 = keine Mindestzahl |
| „Max. Nachrecherchen“ | 2 | 0–5 | So oft sucht der Chat mit einer genaueren Anfrage weiter; 0 = keine automatische Nachrecherche |
| „Live-Suche: erste Seiten“ | 1 | 0–100 | Auf so viele geladene Live-Treffer wartet die erste Bewertung einer Suche |
| „Live-Suche: max. abzuwartende Seiten“ | 4 | 0–100 | Auf höchstens so viele Live-Treffer wartet eine Suche, wenn die Belege nicht reichen; das Vorladen im Hintergrund läuft unabhängig weiter |
| „Live-Suche: Treffer je Quelle“ | 10 | 1–100 | So viele Treffer holt die Live-Suche je angehakter Quelle |
| „Textfenster je geladener Seite“ | 2 | 1–10 | Die Bewertung sieht je noch nicht aufbereiteter Seite so viele passende Textfenster statt der ganzen Seite |
| „Zeichen je Textfenster“ | 700 | 100–10000 | Länge eines solchen Textfensters |

„Live-Suche: erste Seiten“ darf nicht größer sein als „Live-Suche: max. abzuwartende Seiten“; sonst meldet die Prüfung beim Speichern einen Fehler.

### Treffer gegen die Frage bewerten (Reranking)

- **Beschriftung:** Häkchen „Treffer gegen die Frage bewerten (Reranking)“.
- **Standard:** an.
- **Bedeutung:** Das Chat-Modell bewertet die Treffer gegen die Frage und wählt die kleinste ausreichende Menge. Die Suche endet, sobald die Belege reichen.
- **Aus:** Der Chat übernimmt die Treffer in der festen Reihenfolge der Suche und sucht bei Bedarf nach. Das spart einen Modellaufruf je Runde, liefert aber oft mehr oder weniger passende Quellen.

## Bereich „Laden und Aufbereitung“

Hinweis im Dialog: Geladene Seiten landen sofort im Rohindex und sind für den Chat nutzbar. Passagen, Embeddings und Navigation entstehen danach im Hintergrund, parallel und ohne den Chat aufzuhalten. Die Zahl gleichzeitig geladener Seiten stellst du im Bereich „Vorabladen (Prefetch)“ im Reiter [Netzwerk & Agent](help:settings.network) ein.

| Feld | Standard | Erlaubt | Bedeutung |
|---|---|---|---|
| „Rohindex-Worker“ | 2 | 1–8 | So viele geladene Seiten werden gleichzeitig in den Rohindex geschrieben |
| „Aufbereitungs-Worker“ | 4 | 1–16 | So viele Seiten werden gleichzeitig in Passagen zerlegt und eingebettet; dieselbe Seite nie doppelt, die neueste Fassung gewinnt |
| „Embedding-Parallelität“ | 4 | 1–32 | Höchstens so viele Embedding-Aufrufe der Aufbereitung laufen gleichzeitig; gilt je Embedding-Modell, getrennt vom Chat |
| „Embedding-Batchgröße“ | 16 | 1–1000 | So viele Texte gehen in einen Embedding-Aufruf |

## Was im Hintergrund passiert

Bei einer normalen Chat-Frage mit angehakten Wissensquellen sucht der Client zweistufig. Zuerst durchsucht er den Index: Volltext und semantische Suche liefern je ihre Kandidaten, die zu einer Liste zusammengeführt werden. Gleichzeitig holt die Live-Suche neue Treffer direkt aus den Quellen; jede geladene Seite kommt sofort in den Rohindex und ist damit per Volltext durchsuchbar, noch bevor Passagen und Embeddings fertig sind. Das Chat-Modell bewertet dann die Kandidaten gegen die Frage. Reichen die Belege nicht, wartet die Suche auf weitere Live-Treffer oder formuliert eine genauere Anfrage, höchstens so oft wie eingestellt. Am Ende kommen die ausgewählten Auszüge, begrenzt durch „Max. Quellen“ und das Kontextbudget, als nummerierte Quellen in die Anfrage. Mehr dazu in [Wissensquellen im Überblick](help:knowledge.overview) und [Quellenangaben](help:chat.citations).

## Was in die Datei geschrieben wird

Anders als viele andere Felder schreibt der Dialog die Werte der Wissenssuche und der Aufbereitung auch dann in die Datei, wenn sie dem Standard entsprechen. Eine Ausnahme ist das Reranking: nur „aus“ wird gespeichert, „an“ entfernt den Schlüssel. Ein leeres Indexverzeichnis entfernt den Schlüssel, damit das Standardverzeichnis gilt. Siehe [Die Konfigurationsdatei](help:settings.config-file).

## Grenzen

- Die Werte gelten für die normale Chat-Antwort. Wie viele Seiten der Agent bei einer Recherche liest, regelt das Recherche-Budget im Reiter „Netzwerk & Agent“.
- Ein größeres Kontextbudget und mehr Quellen machen Antworten nicht automatisch besser: das Modell bekommt mehr Text, die Antwort dauert länger, und das Kontextfenster des Modells setzt eine Grenze.
- Einen Index beim Programmstart aufzubauen gibt es nicht; der Index füllt sich über die Suche oder über das Indexieren einer Quelle.

## Akronyme im Lesebereich

- **Seiten auf Akronyme prüfen**: Aus heißt keine Prüfung, keine Markierung, keine Facts.
- **Akronyme beim Öffnen markieren**: ob die blassrosa Markierung beim Öffnen einer Seite sichtbar ist; der Marker-Knopf im Lesebereich schaltet trotzdem um.
- **Max. Buchstaben eines Akronyms** (Standard 10): längere großgeschriebene Wörter zählen nicht.
- **Großschrift-Zeile ab (%)** (Standard 60) und **ab Wörtern** (Standard 3): Zeilen, die überwiegend in Großbuchstaben stehen, liefern keine Akronyme.

Alle Werte gelten ab der nächsten geöffneten Seite.

## Typische Probleme

### Die Antwort nutzt zu wenige Quellen

Erhöhe „Max. Quellen im Antwortkontext“ und gegebenenfalls „Kontextbudget (Tokens)“. Prüfe auch, ob „Mindestzahl unabhängiger Quellen“ und das Reranking sinnvoll stehen: das Reranking nimmt absichtlich die kleinste Menge, die reicht.

### Antworten dauern sehr lange

Senke „Live-Suche: max. abzuwartende Seiten“ und „Max. Nachrecherchen“. Jede Nachrecherche und jedes Warten auf Live-Treffer kostet Zeit.

### Nach einem Wechsel des Indexverzeichnisses findet die Suche nichts mehr

Das neue Verzeichnis ist leer. Indexiere deine Quellen neu oder stell das alte Verzeichnis wieder ein. Weitere Hilfe: [Probleme mit Wissensquellen](help:troubleshooting.knowledge).
