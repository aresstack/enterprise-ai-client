---
id: chat.export
title: Chat exportieren
keywords: Export, exportieren, Chat exportieren, speichern unter, Datei, PDF, pdf, JSON, Markdown, md, Word, docx, Bild, Bilder, Diagramm, Mermaid, Word-Dokument, Bericht, weitergeben, archivieren, Fehlerbericht, Schwärzung, geschwärzt, REDACTED, Passwort, Token, Dateiname, Endung, Dateiendung, export, save as, redaction
---

# Chat exportieren

Mit „Chat exportieren …“ speicherst du einen Chat als eigene Datei, etwa um ein Ergebnis weiterzugeben, eine Unterhaltung zu archivieren oder einen Fehler nachvollziehbar zu melden. Zur Wahl stehen PDF, ein Word-Dokument, Markdown und JSON. Vor dem Schreiben schwärzt der Client Zugangsdaten wie Passwörter und Tokens. Dieses Kapitel erklärt die Bedienung, den Inhalt der vier Formate, die Schwärzung und wie Dateiname und Endung entstehen.

## Einen Chat exportieren: Schritt für Schritt

1. Öffne im Drawer den Reiter „Chats“ (siehe [Chat-Verlauf](help:chat.history)).
2. Klicke mit der rechten Maustaste auf die Zeile des Chats, oder fahre mit der Maus darüber und klicke auf das „…“ rechts.
3. Wähle „Chat exportieren …“.
4. Es öffnet sich ein Speichern-Dialog mit dem Titel „Chat exportieren“. Es ist derselbe Dialog wie beim Exportieren einer Seite im Lesebereich oder im Seitenbaum. Wähle unten im Feld für den Dateityp eines der Formate:
   - „PDF-Dokument (*.pdf)“ (voreingestellt),
   - „Word-Dokument (*.docx)“,
   - „Markdown (*.md)“,
   - „JSON (*.json)“.
5. Wähle den Ordner, passe bei Bedarf den vorgeschlagenen Dateinamen an und klicke auf „Speichern“.
6. Gibt es die Datei schon, fragt das Programm „… gibt es schon. Ersetzen?“. Mit „Ja“ wird sie überschrieben, mit „Nein“ passiert nichts.

Eine Erfolgsmeldung gibt es nicht; die Datei liegt danach im gewählten Ordner. Nur bei einem Fehler erscheint ein Fenster „Export fehlgeschlagen“. Konnte ein Bild oder Diagramm nicht übernommen werden, wird die Datei trotzdem gespeichert; an seiner Stelle steht „[Abbildung nicht verfügbar: …]“, und ein Fenster nennt die fehlenden Abbildungen.

Gut zu wissen:

- Du kannst jeden gespeicherten Chat exportieren, auch einen, der gerade nicht geöffnet ist. Der aktive Chat hat den Menüeintrag, sobald er Nachrichten hat.
- Der Export **liest nur**: Chat, Anhänge und (im Agent-Modus) das Recherche-Projekt bleiben unverändert.
- Grundlage ist immer der **gespeicherte** Stand, nicht das, was gerade auf dem Bildschirm steht. Eine Antwort, die gerade noch entsteht, ist noch nicht im Export (siehe [Was nicht im Export steht](help:chat.export#was-nicht-im-export-steht)).
- Die Datei wird zuerst als Zwischendatei geschrieben und erst am Ende an ihren Platz gelegt. Bricht der Export ab, bleibt keine halbe Datei zurück.

## Dateiname und Endung

### Der Vorschlag

Der Dialog schlägt einen Namen aus dem Chat-Titel und dem aktuellen Zeitpunkt vor:

- Leerzeichen und Sonderzeichen im Titel werden zu Unterstrichen, mehrere hintereinander zu einem.
- Der Titelteil wird auf 60 Zeichen gekürzt.
- Dahinter folgen Datum und Uhrzeit im Format Jahr-Monat-Tag_StundeMinute.
- Ist der Titel leer, steht dort „Chat“.

Aus dem Chat „Wie stelle ich einen Urlaubsantrag?“ wird so zum Beispiel `Wie_stelle_ich_einen_Urlaubsantrag_2026-10-12_0343`. Umlaute und andere Buchstaben bleiben erhalten.

Der Vorschlag hat **keine Endung**. Das ist Absicht.

### Die Endung bestimmt der Dateityp

Die Endung ergibt sich erst beim Speichern aus dem gewählten Dateityp:

- Tippst du keine Endung, wird die passende angehängt: Aus `Bericht` wird mit Word `Bericht.docx`.
- Tippst du eine der Export-Endungen (`.pdf`, `.docx`, `.md`, `.json`, gleich in welcher Schreibweise), die nicht zum Dateityp passt, wird sie ersetzt: `Bericht.json` mit dem Dateityp Word wird zu `Bericht.docx`, `Bericht.PDF` zu `Bericht.docx`.
- Jede andere Endung bleibt Teil des Namens, und die richtige kommt dahinter: `notiz.txt` mit dem Dateityp Word wird zu `notiz.txt.docx`, `Bericht v1.2` zu `Bericht v1.2.docx`.

So entsteht nie ein Word-Dokument mit einer fremden Endung. Maßgeblich ist also der Dateityp im Dialog, nicht die getippte Endung. Wechselst du den Dateityp nach dem Tippen, gilt der zuletzt gewählte.

## Die vier Formate

### PDF: der Gesprächsbericht zum Lesen und Drucken

Das PDF ist der voreingestellte Dateityp. Es enthält denselben Bericht wie die Markdown-Datei, sauber gesetzt: oben der Chat-Titel, dann Chat-ID und Zeitpunkte, je Nachricht eine Überschrift mit Autor und Zeitpunkt, darunter der formatierte Text mit Listen, Tabellen und Codeblöcken sowie Anhänge, Seiten, Zitate, Werkzeugaufrufe und Quellen. Bilder aus dem Nachrichtentext und Mermaid-Diagramme erscheinen als Grafik (siehe [Bilder und Diagramme](help:chat.export#bilder-und-diagramme)). Grundlage ist derselbe geschwärzte Stand wie bei allen anderen Formaten.

### JSON: vollständig und maschinenlesbar

JSON ist das maßgebliche Format. Es enthält alles, was zum Chat gespeichert ist, und eignet sich für Fehlerberichte und die Weiterverarbeitung. Oben stehen:

- eine Formatkennung (`enterprise-ai-client-chat-export`) und eine Formatversion,
- der Exportzeitpunkt, die Programmversion und der Anzeigename des Assistenten zum Exportzeitpunkt,
- der Chat mit Kennung, Titel, Erstellungs- und Änderungszeit, Zahl der Nachrichten und Anhänge,
- die Nachrichten.

Je Nachricht findest du unter anderem: laufende Nummer, Zeitstempel, die Rolle (Nutzer, Assistent oder Hinweis), den Zustand (vollständig oder abgebrochen), den Text als Markdown sowie die Anhänge, Seiten, Zitate, Verweise, Quellen und Werkzeugaufrufe. Ergebnisse von Werkzeugaufrufen sind auf ihren Anfang gekürzt; die volle Länge steht daneben.

Bei einem Agent-Chat kommt das Recherche-Projekt dazu: Phase, Verlauf der Phasen, Quellen, vorgemerkte Treffer, Konzept, Gliederung und Dokument. Projektdateien bis 4 MB stehen mit Inhalt im Export, größere nur mit ihrer Größe.

Ein Abschnitt „notPersisted“ erklärt ausdrücklich, was der Client grundsätzlich nicht speichert, damit niemand Lücken für einen Fehler hält. Das Feld „redactions“ nennt die Zahl der geschwärzten Werte.

### Markdown: dasselbe, lesbar

Die Markdown-Datei ist dieselbe Sicht in lesbarer Form:

- oben der Titel als Überschrift, dann Chat-ID, „Erstellt“, „Geändert“ und „Exportiert“ (bei Agent-Chats auch die Phase),
- je Nachricht eine Überschrift wie „3. Nutzer · Zeitstempel“, bei Antworten mit dem Namen des Assistenten statt „Nutzer“, bei Programmhinweisen „Hinweis“,
- darunter der Text und Zeilen wie „Anhang: …“, „Seite: …“, „Zitat: „…““, „Werkzeug: …“ und „Quelle [n]: …“,
- bei Agent-Chats am Ende die „Quellen des Research-Projekts“.

### Word: der Gesprächsbericht

Das Word-Dokument ist ein formatierter Bericht zum Lesen und Weitergeben:

- Oben stehen der Chat-Titel und die Zeile „Exportiert am … um …“.
- Jede Nachricht hat einen farbigen Kopf mit „Du“, dem Namen des Assistenten oder „Hinweis“ und der Uhrzeit; abgebrochene Antworten sind als „abgebrochen“ gekennzeichnet.
- Der Markdown-Text wird in echte Word-Formatierung übertragen: Überschriften, Listen, Fettdruck, Tabellen, Codeblöcke. Bilder und Mermaid-Diagramme erscheinen als Grafik.
- Unter einer Nachricht stehen, soweit vorhanden, die Abschnitte „Anhänge“, „Verwendete Seiten“, „Verweise“, „Zitate“ und „Quellen“ (mit Abschnitt, Ort und Stand).
- Am Ende folgt „Ressourcen des Chats“ mit den Dateien, Seiten und verwendeten Quellen des ganzen Chats, jeweils nur einmal aufgeführt.

Werkzeugaufrufe stehen nicht im Word-Dokument, nur im JSON (und als Kurzzeile im Markdown). Für eine technische Fehleranalyse ist deshalb JSON die bessere Wahl.

## Bilder und Diagramme

PDF und Word übernehmen Abbildungen als echte Grafik an ihrer Stelle im Text, auf die Seitenbreite verkleinert, mit dem Alternativtext als Bildunterschrift:

- Mermaid-Diagramme werden mit demselben Renderer gezeichnet wie im Chat. Im JSON und im Markdown bleibt der Mermaid-Quelltext erhalten.
- Bilder, die im Nachrichtentext selbst stecken (eingebettete `data:`-Bilder in PNG, JPEG, GIF oder SVG), werden eingebettet.
- Bilder mit einer fremden Internetadresse lädt der Export **nicht** herunter. An ihrer Stelle steht „[Abbildung nicht verfügbar: …]“, und das Hinweisfenster nennt sie.

Beim Export einer Seite aus dem Lesebereich oder dem Seitenbaum gilt dasselbe für die Bilder der Seite: Sie werden über die Anmeldung der Quelle geladen (wie in der Leseansicht) und in PDF und Word eingebettet; in Markdown stehen sie als eingebettete PNG-Daten in der Datei, damit sie auch offline und ohne Zugang zur Quelle sichtbar sind. Verlangt die Quelle für Dateien einen Einmalcode, öffne vorher im Lesebereich ein Bild und bestätige den Code.

## Schwärzung von Zugangsdaten

Ein Chat kann Geheimnisse enthalten, etwa wenn ein Werkzeugaufruf eine Adresse mit Anmeldedaten enthielt oder jemand einen Schlüssel in den Chat kopiert hat. Bevor der Export geschrieben wird, ersetzt der Client solche Werte durch `[REDACTED]`. Das gilt für alle vier Formate; PDF, Markdown und Word werden aus demselben geschwärzten Stand erzeugt wie das JSON.

Geschwärzt wird auf zwei Wegen:

1. **Felder mit verdächtigem Namen** verlieren ihren Wert ganz. Gemeint sind Namen, die Wörter wie password, passwort, secret, token, apikey, authorization, cookie, otp, credential oder passphrase enthalten.
2. **Bekannte Muster in jedem Text** werden ersetzt, der übrige Text bleibt stehen:
   - Header-Zeilen wie „Authorization: …“, „Proxy-Authorization: …“, „Cookie: …“ oder API-Key-Header (der Name bleibt, der Wert geht),
   - „Bearer“ gefolgt von einem Token,
   - Sitzungs-Cookies als Name-Wert-Paar, etwa JSESSIONID,
   - Parameter wie password=, token=, api_key=, otp= oder session= in Adressen und Konfigurationszeilen, etwa in `https://wiki.example.org/login?token=…`,
   - Felder wie "password": "…" in JSON-artigem Text,
   - Schlüssel im Stil „sk-…“.

Das Feld „redactions“ im JSON sagt, wie viele Werte ersetzt wurden.

Hinweis: Die Schwärzung arbeitet mit festen Namen und Mustern. Ein Passwort, das du als normalen Satz in eine Nachricht geschrieben hast („Mein Passwort ist …“), erkennt sie nicht. Lies einen Export durch, bevor du ihn weitergibst. Fachliche Inhalte, Namen und interne Adressen werden nicht geschwärzt. Mehr dazu in [Datenschutz](help:concepts.privacy).

## Was nicht im Export steht

- Laufende und fehlgeschlagene Antworten: Sie werden nie gespeichert. Das Fehlerfeld einer Nachricht ist deshalb immer leer.
- Werkzeug- und Gedankenwolken: Sie gehören nur zum Lauf. Die Werkzeugaufrufe einer fertigen Antwort stehen aber im JSON.
- Quellen und Werkzeugaufrufe sehr alter Antworten, die vor deren Speicherung entstanden sind.
- Die Dateien selbst: Anhänge erscheinen nur mit ihrem Namen, nicht mit ihrem Inhalt.

## Typische Probleme

### „Chat exportieren …“ fehlt im Menü

Den Eintrag gibt es nur, wenn die Chat-Historie verfügbar ist, und beim aktiven Chat erst, wenn er Nachrichten hat. Ein neuer, leerer Chat lässt sich nicht exportieren.

### „Der Chat ist nicht (mehr) gespeichert.“

Der Chat ist nicht als Datei vorhanden, etwa weil er gelöscht wurde oder seine Datei nicht lesbar ist. Prüfe im Reiter „Chats“, ob er dort als „Gelöscht“ markiert ist, und stelle ihn gegebenenfalls wieder her.

### „Der Chat konnte nicht exportiert werden: …“

Das Schreiben ist gescheitert. Typische Ursachen: Der Zielordner ist schreibgeschützt, die Datei ist in einem anderen Programm geöffnet (bei Word häufig) oder der Speicherplatz fehlt. Schließe die Datei in Word und versuche es erneut, oder wähle einen anderen Ordner.

### Die Datei hat eine doppelte Endung

Du hast eine fremde Endung wie „.txt“ getippt. Sie bleibt Teil des Namens, die Endung des Dateityps kommt dahinter. Lass die Endung beim Tippen einfach weg.

### Im PDF oder Word-Dokument fehlt ein Bild

An seiner Stelle steht „[Abbildung nicht verfügbar: …]“, und nach dem Speichern nennt ein Fenster den Grund: Das Bild hatte eine fremde Internetadresse (wird nie geladen), seine Daten waren nicht lesbar, oder das Mermaid-Diagramm ließ sich nicht zeichnen (dann steht sein Quelltext im Codeblock).

Weitere Hilfe in [Probleme im Chat](help:troubleshooting.chat).

## Grenzen

- Export immer nur eines Chats; kein Sammelexport.
- Kein Import: Ein exportierter Chat lässt sich nicht wieder einlesen.
- Die Schwärzung erkennt nur bekannte Muster.
- Die Formatierung im PDF und im Word-Dokument ist fest vorgegeben.
