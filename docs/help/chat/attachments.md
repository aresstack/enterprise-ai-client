---
id: chat.attachments
title: Dateien an den Chat anhängen
keywords: Anhang, Anhänge, Datei anhängen, Dateianhang, Büroklammer, hochladen, Upload, PDF, Word, docx, Excel, xlsx, PowerPoint, pptx, HTML, E-Mail, msg, eml, Text, Markdown, Drag and Drop, ziehen, Dateigröße, 50 MB, read_attachment, search_attachment, Datei lesen, Datei durchsuchen, attachment, file upload, paperclip
---

# Dateien an den Chat anhängen

Du kannst dem Assistenten eigene Dateien geben, etwa ein PDF-Handbuch, eine Excel-Liste oder eine E-Mail, und Fragen dazu stellen. Dieses Kapitel erklärt, wie du Dateien anhängst, welche Formate funktionieren, wie der Assistent die Dateien liest und durchsucht und wo die Grenzen liegen. Dateianhänge sind etwas anderes als Wissensquellen: Ein Anhang gehört zu genau einem Chat, eine Wissensquelle steht allen Chats zur Verfügung (siehe [Wissensquellen](help:knowledge.overview)).

## Dateien anhängen: Schritt für Schritt

### Über die Büroklammer

1. Klicke im Composer rechts unten auf die Büroklammer (Tooltip „Dateien anhängen“).
2. Im Dateidialog „Dateien anhängen“ wählst du eine oder mehrere Dateien aus (Strg- oder Umschalt-Klick für mehrere). Ordner lassen sich nicht wählen. Der Dialog merkt sich den zuletzt benutzten Ordner.
3. Die Dateien erscheinen als Chips über dem Eingabefeld, jeweils mit Dateinamen (lange Namen gekürzt) und einem Kreuz zum Entfernen. Fährst du mit der Maus über den Namen, siehst du den vollständigen Pfad.
4. Schreibe deine Frage, zum Beispiel „Fasse das angehängte Protokoll in fünf Punkten zusammen.“, und sende sie.

Über dem Composer steht, solange Dateien vorgemerkt sind, etwa „2 Anhänge vorgemerkt“; der Tooltip der Büroklammer sagt „Dateien anhängen (2 vorgemerkt)“.

### Per Drag & Drop

Ziehe Dateien aus dem Explorer auf den Composer. Solange sie darüber schweben, ist der Rand gestrichelt blau. Beim Loslassen werden sie genauso vorgemerkt wie über die Büroklammer. Es wird immer kopiert, nie verschoben; die Originaldatei bleibt, wo sie ist. Ordner werden übergangen.

### Gut zu wissen

- Dieselbe Datei wird nur einmal vorgemerkt, auch wenn du sie zweimal wählst.
- Eine Nachricht braucht Text; nur Anhänge ohne Frage lassen sich nicht senden.
- Während eine Antwort läuft, ist die Büroklammer gesperrt und der Composer nimmt keine Dateien an.
- Siehst du gar keine Büroklammer, steht in dieser Sitzung keine Anhangsfunktion zur Verfügung (siehe [Typische Probleme](help:chat.attachments#typische-probleme)).

## Was beim Senden passiert

1. Deine Nachricht erscheint im Verlauf, die Dateinamen stehen als Chips unter deiner Blase.
2. Eine gelbe Wolke „Legt Anhang ab“ mit den Dateinamen zeigt, dass die Dateien gerade abgelegt werden. Jede Datei wird dabei in den Ordner des Chats **kopiert** (im Einstellungsordner unter `chats`, je Anhang ein Unterordner mit einer Kennung wie `att-1a2b3c4d`). Spätere Änderungen an deiner Originaldatei wirken sich deshalb nicht auf den Chat aus.
3. Das Modell erfährt zunächst **nur Kennung, Namen und Größe** der Anhänge, nicht ihren Inhalt. Es entscheidet selbst, ob und welche Datei es liest.
4. Will es eine Datei lesen oder durchsuchen, erscheint je Aufruf eine Wolke „Liest Anhang“ (mit dem Dateinamen) bzw. „Durchsucht Anhang“ (mit dem Suchbegriff).
5. Die Antwort beruht dann auf dem gelesenen Text.

Ab der ersten angehängten Datei läuft der ganze Chat über die Werkzeuge; auch spätere Fragen ohne neuen Anhang können auf die bisherigen Dateien zugreifen. Öffnest du den Chat später wieder, sind die Anhänge noch da.

## Wie der Assistent Dateien liest und durchsucht

Der Assistent hat für Anhänge zwei Werkzeuge:

- **`read_attachment`** liest den Text einer Datei. Lange Dateien kommen abschnittsweise, je Aufruf höchstens rund 40.000 Zeichen; braucht das Modell mehr, ruft es das Werkzeug erneut mit einer Startposition auf. Lässt sich aus einer Datei kein Text gewinnen, bekommt das Modell den Vermerk „(kein Text extrahierbar)“.
- **`search_attachment`** sucht einen Begriff oder eine Wortfolge in einer oder allen Dateien (ohne Unterscheidung von Groß- und Kleinschreibung) und liefert die passenden Absätze mit Absatznummer, höchstens 20 Treffer, jeder auf etwa 600 Zeichen gekürzt. Das ist eine einfache Textsuche, keine semantische Suche: Synonyme oder sinngemäße Treffer findet sie nicht.

Für gezielte Fragen zu großen Dateien nutzt das Modell typischerweise erst die Suche und liest dann gezielt. Wie gründlich es vorgeht, hängt vom Modell ab.

Die Zahl der Werkzeugrunden je Antwort ist begrenzt (Standard 8, einstellbar unter Einstellungen → „Tools“ → „Max. Werkzeugrunden“). Ist die Grenze erreicht, antwortet das Modell in einer letzten Runde ohne Werkzeuge aus dem, was es bis dahin gelesen hat. Siehe [Werkzeuge einstellen](help:settings.tools).

### Datei gezielt ansprechen

Bei mehreren Dateien hilft es, die gemeinte Datei zu nennen („In der Datei Vertrag.pdf: …“) oder sie über den Composer-Knopf „Ressourcen n“ als Verweis auszuwählen. Ein Verweis-Chip sagt dem Modell ausdrücklich, um welche Datei es geht, ohne sie erneut hochzuladen. Siehe [Der Chat im Überblick](help:chat.overview).

## Unterstützte Formate

Für das Herauslesen des Textes nutzt der Client dieselbe Erkennung und dieselben Extraktoren wie die Wissensquelle für lokale Dateien: Markdown und Klartext werden direkt gelesen, alles andere über Apache Tika. Laut Beschreibung des Lesewerkzeugs sind das insbesondere:

- PDF (mit Textebene),
- Word-Dokumente,
- Excel-Tabellen,
- PowerPoint-Präsentationen,
- HTML-Seiten,
- E-Mails,
- Text- und Markdown-Dateien.

Erkennt der Client ein Format nicht, meldet das Werkzeug dem Modell „Format … wird nicht unterstützt“; das Modell sagt dir dann in der Regel, dass es die Datei nicht lesen konnte.

Hinweis: Welche Dateitypen Tika im Einzelfall vollständig liest, hängt von der Datei ab und ist nicht für jede Variante geprüft. Eingescannte PDFs ohne Textebene und reine Bilddateien liefern in der Regel keinen Text; eine Texterkennung (OCR) für Anhänge ist nicht belegt.

## Grenzen

- **Größe**: Eine Datei darf höchstens **50 MB** groß sein. Bei einer größeren Datei scheitert das Senden mit „Die Nachricht konnte nicht gesendet werden.“ und dem Hinweis, dass die Datei größer als 50 MB ist.
- **Nur Text**: Der Assistent sieht den extrahierten Text, nicht das Layout, keine Bilder und keine Diagramme in der Datei.
- **Kein Index**: Anhänge werden nicht in den Wissensindex aufgenommen und nicht semantisch durchsucht; sie gehören nur zu ihrem Chat. Für dauerhaft durchsuchbare Dokumente richte eine Wissensquelle ein, siehe [Weitere Wissensquellen](help:knowledge.other-sources).
- **Modell muss Werkzeuge können**: Anhänge funktionieren über Werkzeugaufrufe. Ein Modell, das keine Werkzeuge beherrscht, kann die Dateien nicht lesen.
- **Abschaltbare Werkzeuge**: Sind `read_attachment` oder `search_attachment` unter Einstellungen → „Tools“ abgeschaltet, kann das Modell die Dateien nicht lesen bzw. nicht durchsuchen.
- **Löschen**: Anhänge werden mit ihrem Chat gelöscht (endgültig beim Beenden des Programms, siehe [Chat-Verlauf](help:chat.history)).

### Anhänge im Agent-Modus

Im Agent-Modus funktioniert das Anhängen ebenfalls, aber anders: Dort wird der Text jeder angehängten Datei gleich beim Senden ausgelesen und zusammen mit deiner Nachricht an den Agenten geschickt. Siehe [Der Agent](help:agent.overview).

## Typische Probleme

### Die Büroklammer fehlt

Die Büroklammer erscheint nur, wenn die Anhangsfunktion beim Start dieser Sitzung eingebunden wurde; sie hängt an der Anbindung des Chat-Modells über den KI-Dienst. Prüfe unter Einstellungen → „KI-Dienst“, ob Adresse und Zugang eingerichtet sind, und starte das Programm danach neu. Siehe [KI-Dienst einstellen](help:settings.ai-service). Hinweis: Unter welchen Bedingungen die Anbindung im Einzelnen fehlt, ist nicht für alle Installationen geprüft; im Zweifel hilft das Protokoll im Einstellungsordner.

### Im Verlauf steht „Dateianhänge sind in dieser Sitzung nicht verfügbar; die Nachricht ging ohne sie hinaus.“

Die Nachricht wurde gesendet, die Dateien aber nicht. Ursache wie oben: Die Werkzeug-Anbindung fehlt in dieser Sitzung.

### Der Assistent sagt, er könne die Datei nicht öffnen

1. Erschien eine Wolke „Liest Anhang“? Wenn nein, hat das Modell es gar nicht versucht. Bitte es ausdrücklich: „Lies die angehängte Datei und …“.
2. Endete die Wolke rot mit „fehlgeschlagen“? Dann war das Format nicht lesbar oder die Datei enthält keinen Text (zum Beispiel ein eingescanntes PDF).
3. Sind die Anhang-Werkzeuge unter Einstellungen → „Tools“ aktiv?
4. Unterstützt das gewählte Chat-Modell Werkzeugaufrufe? Wähle im Modellknopf des Composers testweise ein anderes Modell.

### Die Antwort berücksichtigt nur den Anfang einer langen Datei

Das Modell hat vermutlich nur den ersten Abschnitt gelesen. Stelle eine gezieltere Frage („Was steht im Abschnitt Kündigung?“), damit es sucht, oder bitte es, die Datei vollständig zu lesen. Erhöhe gegebenenfalls „Max. Werkzeugrunden“.

### Fehlermeldung beim Senden: Datei konnte nicht abgelegt werden

Die Datei ließ sich nicht in den Chat-Ordner kopieren, etwa weil sie gerade von einem anderen Programm gesperrt ist oder der Speicherplatz fehlt. Schließe die Datei im anderen Programm und sende erneut. Weitere Hilfe in [Probleme im Chat](help:troubleshooting.chat).
