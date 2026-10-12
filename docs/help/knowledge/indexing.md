---
id: knowledge.indexing
title: Suche, Vorladen und Indexierung
keywords: Index, Indexierung, Jetzt indexieren, Kreispfeil, Vollindexierung, Rohindex, Volltext, BM25, Lucene, Aufbereitung, Passagen, Abschnitte, Embeddings, Embedding-Modell, Live-Suche, Suche, Quellen durchsuchen, Vorladen, Prefetch, Cache, Statuszeile, Abbrechen, Indexverzeichnis, Seiten im Index, Wissensbasis, fehlgeschlagen, neu indexieren, Satzmodell, Reranking, indexing, search, full-text, passages, prefetch
---

# Suche, Vorladen und Indexierung

Dieses Kapitel erklärt, wie der Enterprise AI Client in deinen Wissensquellen sucht, warum er Seiten im Voraus lädt und was mit „Index“ gemeint ist. Du erfährst, wann eine Quelle live gefragt wird, wann nur der lokale Index zählt, wie du eine Vollindexierung startest und abbrichst und was die Meldungen in der Statuszeile bedeuten. Die Bedienung des Reiters „Wissensquellen“ steht in [Wissensquellen im Überblick](help:knowledge.overview), der Hintergrund zum Index in [Der Wissensindex](help:concepts.knowledge-index).

## Was „Index“ hier heißt

Der Index ist eine lokale Kopie der gelesenen Inhalte auf deinem Rechner. Er besteht aus zwei Schichten:

1. **Rohindex (Volltext):** Sobald eine Seite oder Datei gelesen ist, kommt ihr Text in einen Volltextindex. Er ist sofort durchsuchbar, nach Wörtern gewichtet (BM25). Der Chat wartet bei einer Frage nur auf diese Schicht.
2. **Aufbereitung:** Im Hintergrund zerlegt der Client jeden Text in **Passagen**, berechnet für jede Passage ein Embedding mit dem eingestellten Embedding-Modell und baut die Kapitel-Navigation für den Lesebereich. Mit den Embeddings findet der Chat auch Stellen, die sinngemäß passen, ohne dieselben Wörter zu benutzen.

Die Aufbereitung gehört immer zu einem Embedding-Modell. Wechselst du das Modell unter Einstellungen → „Modelle“, gilt der bisherige Stand nicht mehr, und die Inhalte müssen neu aufbereitet werden.

„n Seiten im Index“ in einer Quellenzeile zählt die Einträge dieser Quelle, die für das aktuelle Embedding-Modell fertig aufbereitet sind. Die Statuszeile meldet nach einem Lauf zusätzlich die Zahl der „Abschnitte“, also der Passagen.

### Was eine Passage ist

Eine Passage ist ein Textstück von höchstens etwa 350 Tokens. Der Client teilt zuerst an der Struktur des Dokuments (Überschriften, Absätze, Codeblöcke) und dann an Satzgrenzen. Für die Satzgrenzen nutzt er ein Satzmodell, das du unter Einstellungen → „Modelle“ im Bereich NLP auswählst; ohne Satzmodell teilt er nach festen Regeln. Benachbarte Passagen überlappen um einen Satz, damit kein Zusammenhang an der Schnittkante verloren geht.

## Wie gesucht wird

### Live-Suche

Quellen mit eigener Suche (Confluence und MediaWiki) fragt der Client bei jeder Suche direkt. Gefundene Seiten lädt er, legt sie sofort in den Rohindex und gibt sie zur Aufbereitung weiter. Dadurch wächst der Index mit der Nutzung, ohne dass du etwas tun musst.

Im Chat lädt die Live-Suche zunächst die ersten Treffer und wartet höchstens auf einige weitere; die übrigen laufen im Hintergrund weiter. Die Werte stehen unter Einstellungen → „Index“ im Bereich „Wissenssuche im Chat“: „Live-Suche: erste Seiten“ (Vorgabe 1), „Live-Suche: max. abzuwartende Seiten“ (Vorgabe 4) und „Live-Suche: Treffer je Quelle“ (Vorgabe 10).

### Suche nur im Index

Alle anderen Quelltypen (lokale Dateien, FTP, JES, NDV, Outlook, SharePoint, BetaView) haben keine Suche, die der Client nutzen kann. Für sie zählt nur, was bereits im Index steht. Ist eine solche Quelle noch nicht indexiert, findet der Chat darin nichts.

### Die Suchleiste „Quellen durchsuchen…“

Im Reiter „Wissensquellen“ durchsucht die Suchleiste alle angehakten Quellen gleichzeitig (Enter startet, Esc leert das Feld). Je Quelle zeigt sie bis zu zehn Treffer: Live-Treffer und Treffer aus dem Index derselben Quelle zusammen. Oben steht eine Zusammenfassung wie „Suche in n Quellen …“, „n Treffer in m Quellen“, „Keine Treffer“ oder „Keine Quelle angehakt“. Konnte eine Quelle nicht gefragt werden, steht bei ihr in Rot zum Beispiel „nicht erreichbar“, „Zugriff verweigert“, „unerwartete Antwort“ oder „Suche fehlgeschlagen (Protokoll)“.

## Vorladen (Prefetch)

Damit ein Klick auf einen Treffer schnell wirkt, lädt der Client Seiten im Voraus in einen Zwischenspeicher:

- Nach einer Suche in der Suchleiste lädt er die Trefferliste von oben her.
- Klickst oder wählst du einen Treffer, lädt er ab dieser Zeile weiter.
- Scrollst du, lädt er ab der obersten sichtbaren Zeile.

Ein Klick hat immer Vorrang: Die angeklickte Seite kommt aus dem Zwischenspeicher oder wird sofort geholt, nie hinter den vorausgeladenen Seiten. Nach höchstens 30 Sekunden gibt der Lesebereich auf. Vorgeladene und angeklickte Seiten werden anschließend indexiert.

Die Grenzen stellst du unter Einstellungen → „Netzwerk & Agent“ im Bereich „Vorabladen (Prefetch)“ ein: „Max. Seiten vorladen“ (Vorgabe 100), „Parallele Requests“ (Vorgabe 4) und „Max. Cache-Größe (MB)“ (Vorgabe 50). Senke die Werte, wenn eine Quelle empfindlich auf viele Abrufe reagiert.

## Vollindexierung auf Knopfdruck

Für Quellen ohne eigene Suche liest die Vollindexierung die ganze Quelle im eingestellten Umfang (Startpunkte, Tiefe, Höchstzahl).

### So startest du sie

1. Öffne den Drawer, Reiter „Wissensquellen“.
2. Klicke in der Zeile der Quelle auf den Kreispfeil „Jetzt indexieren“.
3. Verfolge den Fortschritt in der Statuszeile über dem Eingabefeld des Chats.

Der Kreispfeil ist nur aktiv, wenn

- der Quelltyp eine Vollindexierung kennt (nicht bei Confluence und MediaWiki, die live gefragt werden),
- die Quelle eingeschaltet ist und
- gerade kein anderer Lauf läuft. Es läuft immer nur eine Indexierung zur Zeit.

Beim Programmstart indexiert der Client nichts von selbst. Auch das Speichern einer Quelle startet keinen Lauf.

### Was die Statuszeile meldet

- Beim Start: „Indexierung von … …“.
- Während des Laufs: „Indexierung: k von N Seiten · Titel“ mit dem gerade gelesenen Eintrag.
- Am Ende: „Wissensbasis: n Seiten, m Abschnitte (Stand HH:mm)“, gegebenenfalls ergänzt um „, x fehlgeschlagen“.
- Ließ sich die Quelle gar nicht lesen: „Indexierung von … fehlgeschlagen: Die Quelle konnte nicht gelesen werden (Ursache im Protokoll).“
- Konnte der Lauf nicht beginnen: „Indexierung konnte nicht gestartet werden.“

Die Quellenzeile zeigt danach den Stand, etwa „Stand HH:mm, n fehlgeschlagen (Protokoll)“ oder „Indexierung HH:mm fehlgeschlagen: …“.

### Abbrechen

Während eines Laufs steht in der Statuszeile der Knopf „Abbrechen“ („Indexierung nach der aktuellen Seite beenden“). Der Client liest den aktuellen Eintrag noch zu Ende und hört dann auf. Die Statuszeile meldet „Indexierung abgebrochen: k von N Seiten indexiert (Stand …)“, die Quellenzeile „abgebrochen HH:mm“. Was schon gelesen ist, bleibt im Index.

### Was bei einem erneuten Lauf passiert

- Unveränderte Einträge erkennt der Client an ihrer Revision (etwa Änderungszeit und Größe, Job-ID) und überspringt sie.
- Nach einem **vollständigen** Lauf entfernt er Einträge, die es in der Quelle nicht mehr gibt.
- Nach einem Abbruch oder wenn die Quelle nicht lesbar war, entfernt er nichts. So verlierst du keinen Index, weil das Netz kurz weg war.

## Wo der Index liegt und was du einstellen kannst

Unter Einstellungen → „Index“ wählst du im Feld „Indexverzeichnis (optional)“ mit „Wählen …“ einen eigenen Ort. Leer liegt der Index im Ordner „index“ im Anwendungsverzeichnis. Einzelheiten zu allen Feldern stehen in [Einstellungen: Index](help:settings.index). Die wichtigsten:

- **„Wissenssuche im Chat“**: wie viele Volltext- und semantische Kandidaten gesammelt werden, wie viele Quellen höchstens in den Antwortkontext kommen, das „Kontextbudget (Tokens)“ und das Reranking „Treffer gegen die Frage bewerten (Reranking)“.
- **„Laden und Aufbereitung“**: „Rohindex-Worker“ (Vorgabe 2), „Aufbereitungs-Worker“ (4), „Embedding-Parallelität“ (4) und „Embedding-Batchgröße“ (16). Kleinere Werte schonen Rechner und Embedding-Dienst, größere beschleunigen große Läufe.

Fehlgeschlagene Abrufe wiederholt der Client bei vorübergehenden Störungen. Anzahl und Pause stellst du unter Einstellungen → „Netzwerk & Agent“ → „Zeitlimits und Wiederholungen“ ein: „Wissensquellen: Wiederholungen“ (Vorgabe 2) und „Wissensquellen: Pause vor Wiederholung (ms)“ (Vorgabe 500).

## Typische Probleme

### Der Kreispfeil ist grau

Prüfe der Reihe nach: Ist es eine Confluence- oder MediaWiki-Quelle? Dann gibt es keine Vollindexierung, die Quelle wird live gefragt. Ist die Quelle ausgeschaltet? Läuft gerade eine andere Indexierung? Dann warte oder brich sie ab.

### „n Seiten im Index“ bleibt bei 0, obwohl der Lauf fertig ist

Die Aufbereitung läuft noch im Hintergrund oder das Embedding-Modell ist nicht erreichbar. Prüfe unter Einstellungen → „Modelle“, ob ein Embedding-Modell gewählt ist, und sieh im Protokoll nach. Hast du das Embedding-Modell gewechselt, zählt nur noch, was mit dem neuen Modell aufbereitet ist; starte die Indexierung neu.

### Der Chat findet frische Änderungen nicht

Bei Quellen ohne eigene Suche gilt der Stand des letzten Laufs. Klicke den Kreispfeil erneut; unveränderte Einträge werden übersprungen, der Lauf ist daher meist schneller als der erste.

### Die Indexierung ist sehr langsam

Große Quellen, langsame Server und ein entfernter Embedding-Dienst bremsen. Senke die Höchstzahl der Quelle, verkleinere Startpunkte und Tiefe oder passe die Worker-Werte an. Weitere Prüfschritte: [Fehlersuche bei Wissensquellen](help:troubleshooting.knowledge).

## Für Fortgeschrittene

In der Konfigurationsdatei steuern `knowledge.chunk.maxTokens` (Vorgabe 350) die Passagenlänge und `knowledge.passages.language` (`de` oder `en`) die Sprache für die Satzerkennung. Der Schlüssel `knowledge.indexOnStartup` wird im aktuellen Stand nicht ausgewertet. Siehe [Die Konfigurationsdatei](help:settings.config-file).
