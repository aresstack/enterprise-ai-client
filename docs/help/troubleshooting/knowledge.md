---
id: troubleshooting.knowledge
title: Probleme mit Wissensquellen und Index
keywords: Wissensquelle, Wissensbasis, findet nichts, keine Treffer, Keine passenden Abschnitte, Wissenssuche ausgefallen, PDF, PDF wird nicht gefunden, Office, Tika, Index leer, Noch nicht indexiert, Indexierung, dauert lange, Jetzt indexieren, fehlgeschlagen, Live-Suche, angehakt, Volltext, semantisch, Embedding, Aufbereitung, Rohindex
---

# Probleme mit Wissensquellen und Index

Der Chat beantwortet Fragen aus deinen Wissensquellen, indem er vorher in einem lokalen Index sucht, und zwar im Volltext und semantisch. Findet er nichts, kann das an der Quelle selbst, am Index, an der Frage oder an den Einstellungen liegen. Dieses Kapitel hilft, wenn eine Quelle nichts findet, eine PDF-Datei nicht auftaucht, der Index leer bleibt oder die Indexierung sehr lange dauert. Wie Quellen und Index zusammenarbeiten, erklärt [Der Wissensindex](help:concepts.knowledge-index); das Einrichten von Quellen steht in [Wissensquellen](help:knowledge.overview).

## Wie Quellen in den Index kommen

Es gibt zwei Arten von Quellen, und sie füllen den Index unterschiedlich:

- **Quellen mit Vollindexierung**: Lokale Dateien, FTP, JES, NDV, Outlook, SharePoint und BetaView. Sie werden nur eingelesen, wenn du in ihrer Zeile auf „Jetzt indexieren“ (⟳) klickst. Beim Programmstart wird nichts indexiert. Neue oder geänderte Dateien kommen erst nach dem nächsten Lauf in den Index.
- **Live-Quellen**: Confluence und MediaWiki. Sie werden nicht vorab komplett eingelesen. Bei jeder Frage fragt der Client sie live ab („Wird bei der Suche live gefragt; Treffer kommen in den Index“) und übernimmt die gefundenen Seiten in den Index. Sie wachsen also mit der Nutzung.

Geladene Seiten landen sofort im Rohindex und sind dort im Volltext durchsuchbar. Die Zerlegung in Abschnitte und die Embeddings für die semantische Suche entstehen danach im Hintergrund.

## Die Quelle findet nichts

Typische Anzeichen: die Hinweiszeile „Keine passenden Abschnitte in der Wissensbasis gefunden. Die Antwort entstand ohne Kontext.“, „Keine Treffer“ im Suchfeld des Drawers oder eine Antwort ohne „Quellen (n)“ darunter.

1. **Ist die Quelle angehakt?** Drawer (Hamburger-Menü) → Reiter „Wissensquellen“. Nur angehakte Quellen werden durchsucht (Tooltip „Angehakt: der Chat durchsucht diese Quelle“). Steht dort „Keine Quelle angehakt“, sucht der Chat nirgends. Bei Confluence müssen außerdem die gewünschten Spaces angehakt sein.
2. **Ist überhaupt etwas im Index?** Unter der Quelle steht ihr Stand, zum Beispiel „n Seiten im Index“ oder „Noch nicht indexiert“. Bei Quellen mit Vollindexierung zuerst „Jetzt indexieren“ klicken.
3. **War es eine Frage?** Grüße und Dank („Hallo“, „Danke, passt“) und Fragen an den Assistenten selbst („Welche Tools hast du?“) lösen bewusst keine Suche aus. Formuliere eine inhaltliche Frage.
4. **Direkt suchen**: Gib im Feld „Quellen durchsuchen…“ im Drawer einen Begriff ein. Während der Suche steht „Suche in … …“. Findet diese Suche etwas, der Chat aber nicht, liegt es an der Bewertung der Treffer (Schritt 6).
5. **Ist eine Suchart ausgefallen?** Die Hinweiszeilen „Die Wissenssuche ist ausgefallen. Die Antwort entstand ohne Kontext aus der Wissensbasis.“, „Die semantische Suche ist ausgefallen; es wurde nur im Volltext gesucht.“ und „Die Volltextsuche ist ausgefallen; es wurde nur semantisch gesucht.“ zeigen technische Fehler. Fällt die semantische Suche aus, liegt es fast immer am Embedding-Modell oder seiner Dimension ([Modelle](help:troubleshooting.models)).
6. **Werden Treffer verworfen?** Das Chat-Modell bewertet die Treffer gegen die Frage und nimmt nur, was die Antwort stützt. Im Reiter „Index“ steuern „Max. Retrieval-Kandidaten“, „Max. Quellen im Antwortkontext“, „Kontextbudget (Tokens)“, „Mindestzahl unabhängiger Quellen“ und „Max. Nachrecherchen“ diesen Schritt. Alle Werte gelten nach dem Speichern ab der nächsten Frage.
7. **Live-Quelle erreichbar?** Bei Confluence und MediaWiki muss die Quelle bei jeder Frage antworten. Fehler stehen im Protokoll; vorübergehende Fehler werden wiederholt („Quelle vorübergehend nicht erreichbar (…); Wiederholung n von m in X ms“).

Nach einem Wechsel des Embedding-Modells fehlen semantische Treffer, bis der neue Bereich des Index gefüllt ist; siehe [Modelle](help:troubleshooting.models).

## Eine PDF-Datei wird nicht gefunden

### In einer Dateiquelle

Die Quelle „Lokale Dateien“ liest PDF, Office, Text, Markdown und HTML über Apache Tika. Prüfe:

1. Liegt die Datei unter dem eingetragenen „Verzeichnis“ und, falls gesetzt, unter einem der „Startpunkte“?
2. Liegt sie tiefer als „Tiefe“ (Standard 20 Ebenen)?
3. Ist sie größer als „Größte Datei in MB (optional)“ (Standard 50)? Größere Dateien werden übersprungen.
4. Hat die Quelle mehr Dateien als „Höchstzahl Dateien (optional)“ (Standard 5000)? Dann endet der Lauf vorher.
5. Beginnt der Dateiname mit „.“ oder „~$“? Solche Dateien (versteckte Dateien, Office-Sperrdateien) werden ausgelassen.
6. Wurde nach dem Ablegen der Datei „Jetzt indexieren“ geklickt?
7. Steht unter der Quelle „Stand HH:mm, n fehlgeschlagen (Protokoll)“? Dann im Protokoll nach dem Dateinamen suchen. Mögliche Gründe sind „… ist größer als … Bytes“, „… nicht lesbar“ oder „…: kein Extraktor für …“.

Hinweis: Wie sich gescannte PDF-Dateien ohne Textschicht verhalten, ist nicht für alle Installationen geprüft. Eine Texterkennung (OCR) ist nicht belegt; solche Dateien liefern vermutlich keinen durchsuchbaren Text.

### Als Anhang in Confluence

Confluence-Anhänge werden standardmäßig nicht indexiert. Ist das Mitnehmen von Anhängen eingeschaltet, gelangen nur Textformate in den Index (Text, JSON, XML). Für PDF- und Office-Anhänge steht im Protokoll „Anhang … hat keinen Textinhalt (…)“. Du kannst solche Anhänge im Leser trotzdem öffnen. Mehr dazu in [Confluence](help:troubleshooting.confluence).

## Der Index bleibt leer

Mögliche Zustände in der Zeile der Quelle:

| Anzeige | Bedeutung und nächster Schritt |
|---|---|
| „Noch nicht indexiert“ | Noch kein Lauf. „Jetzt indexieren“ klicken. |
| „Indexstand wird gelesen …“ | Kurz warten. |
| „Wird bei der Suche live gefragt; Treffer kommen in den Index“ | Live-Quelle; füllt sich mit Fragen. |
| „Abgewählt: wird nicht indexiert und nicht durchsucht“ | Quelle anhaken. |
| „Gespeichert; gilt nach dem nächsten Start“ | Programm neu starten. |
| „Indexierung HH:mm fehlgeschlagen: …“ | Grund lesen; meist Verbindung oder Anmeldung. |
| „Quelle nicht lesbar (Protokoll)“ | Verzeichnis, Server oder Anmeldedaten prüfen. |
| „Fehlerhaft: …“ | Einstellung der Quelle fehlt oder ist falsch; „Bearbeiten“. |
| „Keine Spaces gefunden“ | Confluence: keine sichtbaren Spaces, Rechte prüfen. |
| „Nicht ladbar; bitte erneut aufklappen“ | Zuklappen und wieder aufklappen. |

Weitere Meldungen in der Statusleiste: „Indexierung konnte nicht gestartet werden.“ und „Indexierung von … fehlgeschlagen: Die Quelle konnte nicht gelesen werden (Ursache im Protokoll).“ Bei einer Dateiquelle bedeutet das oft „Verzeichnis nicht gefunden: …“ oder „Verzeichnis nicht lesbar: …“.

Liegt der Index an einem ungewöhnlichen Ort, prüfe im Reiter „Index“ das Feld „Indexverzeichnis (optional)“. Leer bedeutet: Verzeichnis „index“ im Anwendungsverzeichnis. Ein geänderter Pfad zeigt auf einen anderen, eventuell leeren Index.

## Die Indexierung dauert lange

Während eines Laufs zeigt die Statusleiste „Indexierung: k von N Seiten · Titel“ und danach „Wissensbasis: n Seiten, m Abschnitte, k fehlgeschlagen (Stand HH:mm)“.

1. **Abbrechen ist möglich**: Der Knopf „Abbrechen“ (Tooltip „Indexierung nach der aktuellen Seite beenden“) beendet den Lauf sauber. Es erscheint „Indexierung abgebrochen: …“. Bereits eingelesene Seiten bleiben im Index.
2. **Embeddings sind der Engpass**: Jeder Abschnitt braucht einen Embedding-Aufruf. Im Reiter „Index“ steuern „Aufbereitungs-Worker“ (Standard 4), „Embedding-Parallelität“ (Standard 4) und „Embedding-Batchgröße“ (Standard 16) das Tempo. Mehr Parallelität hilft nur, wenn der Dienst sie zulässt; antwortet er dann mit HTTP 429, wird es eher langsamer.
3. **KeePass wird bei jeder Anfrage gefragt**: Ein langsames oder kurzzeitig gesperrtes KeePass bremst den ganzen Lauf ([KeePass](help:troubleshooting.keepass)).
4. **Große Quellen begrenzen**: Bei Dateiquellen „Startpunkte“ auf die wichtigen Unterordner setzen oder „Höchstzahl Dateien (optional)“ senken.
5. **Unveränderte Seiten werden nicht neu verarbeitet**: Ein zweiter Lauf ist deutlich schneller, solange Text, Zerlegung und Embedding-Modell gleich bleiben.

Die Aufbereitung (Abschnitte, Embeddings) läuft nach dem Einlesen im Hintergrund weiter. Der Chat kann die Seiten schon vorher im Volltext nutzen. Weitere Einzelheiten: [Indexierung](help:knowledge.indexing) und [Index einstellen](help:settings.index).

## Grenzen

- Quellen mit Vollindexierung werden nicht automatisch aktualisiert; neue Inhalte erst nach „Jetzt indexieren“.
- Confluence und MediaWiki enthalten im Index nur, was schon einmal gefunden oder geladen wurde.
- Hinweis: Das Verhalten bei sehr großen Quellen (viele zehntausend Dateien) ist nicht für alle Installationen geprüft.
