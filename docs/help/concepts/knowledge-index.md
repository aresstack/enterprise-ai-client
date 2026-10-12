---
id: concepts.knowledge-index
title: Wissensindex und Suche
keywords: Index, Wissensindex, Volltextsuche, semantische Suche, hybride Suche, BM25, Lucene, Embedding, Embeddings, Vektor, Cosinus, Passagen, Abschnitte, Chunks, Rohindex, Aufbereitung, Reciprocal Rank Fusion, Namensraum, Embedding-Modell wechseln, neu indexieren, Indexverzeichnis, warum Embedding-Modell, semantic search, vector search, RAG
---

# Wissensindex und Suche

Damit Chat und Agent in deinen Wissensquellen schnell das Passende finden, legt die Anwendung auf deinem Rechner einen **Wissensindex** an. Er verbindet zwei Suchverfahren: die **Volltextsuche** nach Wörtern und die **semantische Suche** nach Bedeutung. Dieses Kapitel erklärt, wie der Index aufgebaut ist, was Passagen und Embeddings sind, warum ein Embedding-Modell nötig ist und was passiert, wenn du das Embedding-Modell wechselst. Es richtet sich an alle, die verstehen wollen, warum eine Suche etwas findet oder nicht findet.

## Wozu der Index da ist

Wissensquellen wie ein Confluence unter `confluence.example.org` oder ein Wiki unter `wiki.example.org` haben eigene Suchfunktionen, aber diese finden nur Wörter und sind oft langsam. Der lokale Index ergänzt sie:

- Er hält Seiten, die schon einmal geladen wurden, in einer Form bereit, die in Millisekunden durchsucht werden kann.
- Er zerlegt Seiten in kleinere Abschnitte (**Passagen**), sodass eine Antwort genau den passenden Absatz zitieren kann statt einer ganzen Seite.
- Er findet über Embeddings auch Texte, die dieselbe Sache mit anderen Worten beschreiben.

Der Index ist eine **wiederherstellbare Kopie**: Maßgeblich bleiben immer die Seiten in den Wissensquellen. Geht der Index verloren, baut ihn die Anwendung durch erneutes Laden wieder auf.

Er liegt standardmäßig im Ordner `index` im Anwendungsverzeichnis. Einen anderen Ordner stellst du unter Einstellungen → „Index“ → „Indexverzeichnis (optional)“ ein (`knowledge.indexDirectory`).

## Zwei Stufen: Rohindex und Aufbereitung

Jede geladene Seite durchläuft zwei Stufen, die unabhängig voneinander arbeiten:

1. **Rohindex (sofort)**: Sobald eine Seite geladen ist, landet ihr Text im Rohindex (Unterordner `raw`). Damit ist sie sofort im Volltext durchsuchbar. Der Chat wartet nur auf diese Stufe. Aus einer noch nicht aufbereiteten Seite nimmt er die am besten passenden Textfenster (Standard: 2 Fenster zu je 700 Zeichen, einstellbar unter „Textfenster je geladener Seite“ und „Zeichen je Textfenster“).
2. **Aufbereitung (im Hintergrund)**: Danach zerlegt die Anwendung die Seite in Passagen, berechnet für jede Passage ein Embedding und legt beides im Index ab. Das läuft parallel für mehrere Seiten, mit niedrigerer Priorität, und hält den Chat nie auf. Ist eine Seite in derselben Fassung schon aufbereitet, liefert die Suche ihre Passagen statt der Textfenster.

Wie viel gleichzeitig läuft, begrenzen Einstellungen in der Konfigurationsdatei: `knowledge.raw.workers` (Schreiber in den Rohindex, Standard 2), `knowledge.enrichment.workers` (Seiten in Aufbereitung, Standard 4) und `embedding.maxConcurrency` (gleichzeitige Embedding-Aufrufe der Aufbereitung, Standard 4). Die Embedding-Anfrage für eine Chat-Frage geht an dieser Grenze vorbei und wartet nie hinter der Hintergrundarbeit.

## Wie Seiten in den Index kommen

- **Durch Suchen**: Jede Suche im Chat, in der Suchleiste oder durch den Agenten fragt die angehakten Quellen live ab. Die Treffer werden im Hintergrund vorgeladen und indexiert, ab dem gewählten bzw. obersten sichtbaren Treffer (Einstellungen → „Netzwerk & Agent“ → „Vorabladen (Prefetch)“).
- **Durch Lesen**: Öffnest du eine Seite im Lesebereich oder liest das Modell sie mit `knowledge_read`, wird sie sofort geladen und indexiert, nie hinter dem Vorladen.
- **Durch vollständiges Indexieren**: „Jetzt indexieren“ an einer Quelle im Drawer-Reiter „Wissensquellen“ geht die Quelle in ihrem eingestellten Umfang (Startpunkte, Tiefe, Höchstzahl) durch. Unveränderte Seiten werden anhand ihrer Version übersprungen, verschwundene Seiten nach einem vollständigen Lauf entfernt.

Beim Programmstart wird nichts indexiert. Einzelheiten: [Indexierung](help:knowledge.indexing).

## Passagen

Eine Passage ist ein zusammenhängender Abschnitt einer Seite, meist einige Absätze zu einem Thema. Die Zerlegung folgt zuerst der Struktur der Seite (Überschriften), dann den Satzgrenzen und erkennt Themenwechsel. Für die Satzgrenzen nutzt sie das Satzmodell der eingestellten Sprache (`knowledge.passages.language`, `de` oder `en`), das du unter Einstellungen → „Modelle“ im Abschnitt „Text- und Sprachanalyse (OpenNLP)“ (oder im letzten Schritt des Einrichtungsassistenten) installierst; ohne dieses Modell arbeitet sie regelbasiert.

Jede Passage hat eine eigene Kennung, die auch die Fassung der Seite enthält. Ändert sich die Seite, entstehen neue Passagen mit neuen Kennungen. Fragt jemand eine alte Passage an, sagt `knowledge_read`, dass sie zu einer älteren Fassung gehört, und liefert die aktuellen Passagen.

## Die zwei Suchverfahren

### Volltextsuche

Die Volltextsuche (Verfahren BM25) findet Passagen, in denen die Wörter deiner Anfrage vorkommen, und gewichtet seltene Wörter höher. Sie ist schnell, braucht kein Modell und findet Fachbegriffe, Abkürzungen und Kennungen zuverlässig, etwa einen Programmnamen oder eine Fehlernummer. Sie findet aber nichts, was mit anderen Worten beschrieben ist.

### Semantische Suche

Für die semantische Suche rechnet ein **Embedding-Modell** jeden Text in einen Zahlenvektor um. Texte mit ähnlicher Bedeutung bekommen ähnliche Vektoren, auch wenn sie keine gemeinsamen Wörter haben. Deine Frage wird ebenso umgerechnet, und die Anwendung sucht die Passagen, deren Vektoren am nächsten liegen (Cosinus-Ähnlichkeit). So findet „Wie beantrage ich Urlaub?“ auch eine Seite „Abwesenheiten melden“.

### Zusammenführung

Beide Verfahren liefern je bis zu 20 Kandidaten (Einstellungen → „Index“: „Volltext-Kandidaten“, „Semantische Kandidaten“). Die Anwendung führt sie über ihre **Ränge** zusammen (Reciprocal Rank Fusion), nicht über ihre Punktwerte, weil die Werte beider Verfahren nicht vergleichbar sind. Übrig bleiben höchstens „Max. Retrieval-Kandidaten“ (Standard 10). Zusätzlich kann die Anwendung Nachbarn guter Treffer hinzunehmen: Nachbarabsätze, denselben Abschnitt, die Elternseite und sehr ähnliche Absätze.

Im normalen Chat bewertet danach das Chat-Modell die Kandidaten gegen deine Frage und nimmt die kleinste ausreichende Menge, höchstens „Max. Quellen im Antwortkontext“ (Standard 6). Wie das im Chat abläuft, erklärt [Quellen und Zitate](help:chat.citations).

## Warum ein Embedding-Modell nötig ist

Ohne Embedding-Modell gibt es nur die Volltextsuche. Das Embedding-Modell wird gebraucht für:

- die **semantische Suche** im Chat, in der Suchleiste und für `knowledge_search`,
- das **Aufbereiten** der Seiten (jede Passage bekommt ihren Vektor),
- die **ähnlichen Absätze** in der Nachbarschaft guter Treffer,
- den **Weidezaun** des Recherche-Agenten, der Treffer semantisch gegen Konzept und Ausschlüsse misst (siehe [Recherche mit dem Agenten](help:agent.research)).

Fällt das Embedding-Modell aus (Dienst nicht erreichbar, lokales Modell fehlt), arbeitet die Suche mit der Volltextsuche allein weiter und zeigt einen Hinweis, dass ein Suchpfad ausgefallen ist. Fallen beide Pfade aus, meldet der Chat, dass die Wissenssuche gescheitert ist.

Das Embedding-Modell wählst du unter Einstellungen → „Modelle“ (Kategorie „Embeddings“), entweder beim KI-Dienst oder lokal. In der Konfigurationsdatei stehen `embedding.model` und `embedding.dimension`. Die Dimension ist nur eine Erwartung: Liefert der Dienst eine andere, übernimmt die Anwendung sie beim ersten Aufruf und schreibt beide Werte ins Protokoll. Siehe [Lokale Modelle und KI-Dienst](help:concepts.local-vs-cloud).

## Was beim Wechsel des Embedding-Modells passiert

Vektoren verschiedener Embedding-Modelle sind nicht vergleichbar: Eine Frage, die mit Modell B umgerechnet wird, darf nie gegen Passagen von Modell A gesucht werden. Deshalb trennt der Index die Daten nach **Embedding-Welt**: Jedes Modell (genauer: Modellname, Dimension und einige Merkmale wie lokal oder KI-Dienst, ohne Adresse oder Zugangsdaten) bekommt einen eigenen Namensraum mit eigener Volltext- und Vektorablage.

Wechselst du das Embedding-Modell, gilt nach dem Speichern sofort:

1. Suchen laufen im Namensraum des **neuen** Modells. Dieser ist anfangs leer: Die semantische Suche und die Passagensuche finden zunächst nichts aus bisher aufbereiteten Seiten.
2. Der **Rohindex** hängt nicht vom Embedding-Modell ab. Schon geladene Seiten bleiben im Volltext durchsuchbar, der Chat findet weiter ihre Textfenster.
3. Jede Seite, die wieder geladen wird (durch Suchen, Lesen oder Indexieren), wird für das neue Modell aufbereitet.
4. Die Daten des alten Modells bleiben im Indexverzeichnis liegen, werden aber nicht mehr benutzt.

Empfehlung: Nach einem Wechsel die Wissensquellen mit „Jetzt indexieren“ neu indexieren. Der Reiter „Modelle“ weist darauf hin: „Ein anderes Embedding-Modell heißt: Wissensquellen neu indexieren.“ Dasselbe gilt, wenn sich die Adresse einer Quelle ändert.

## Einstellungen im Reiter „Index“

Die wichtigsten Werte für die normale Chat-Antwort stehen unter Einstellungen → „Index“ und gelten nach dem Speichern ab der nächsten Frage: „Volltext-Kandidaten“, „Semantische Kandidaten“, „Max. Retrieval-Kandidaten“, „Max. Quellen im Antwortkontext“, „Kontextbudget (Tokens)“, „Mindestzahl unabhängiger Quellen“, „Max. Nachrecherchen“ sowie die Werte der Live-Suche und der Textfenster. Das Recherche-Budget des Agenten ist davon getrennt. Ausführlich: [Einstellungen: Index](help:settings.index).

## Grenzen

- Das Kontextbudget in Tokens ist eine Schätzung ohne den Tokenizer des Modells. Wähle es mit Abstand unter dem Kontextfenster des Modells.
- Einen Mindestwert für die semantische Ähnlichkeit gibt es bewusst nicht (`retrieval.minSemanticScore=-1`), weil die Werte je Embedding-Modell verschieden skaliert sind. Ob ein Treffer passt, entscheidet die Bewertung durch das Chat-Modell.
- Ein eigenes Reranking-Modell ist nicht angebunden.
- Weitere Punkte: [Bekannte Einschränkungen](help:concepts.limitations).

## Typische Probleme

### Die Suche findet eine Seite nicht, obwohl sie existiert

- Ist die Quelle im Drawer-Reiter „Wissensquellen“ angehakt?
- Wurde die Seite schon einmal geladen? Neue Seiten kommen erst durch Suchen, Lesen oder „Jetzt indexieren“ in den Index.
- Wurde kürzlich das Embedding-Modell gewechselt? Dann neu indexieren.

### Hinweis, dass ein Suchpfad ausgefallen ist

Meist ist das Embedding-Modell nicht erreichbar. Prüfe die Verbindung zum KI-Dienst bzw. das lokale Embedding-Modell. Die Volltextsuche liefert weiter Ergebnisse. Siehe [Probleme mit Wissensquellen](help:troubleshooting.knowledge).

## Verwandte Kapitel

- [Wissensquellen im Überblick](help:knowledge.overview)
- [Indexierung](help:knowledge.indexing)
- [Einstellungen: Index](help:settings.index)
- [Lokale Modelle und KI-Dienst](help:concepts.local-vs-cloud)
