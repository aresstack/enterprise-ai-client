---
id: knowledge.attachments
title: Anhänge von Wissensquellen
keywords: Anhang, Anhänge, Dateianhang, Büroklammer, Confluence-Anhang, PDF, Word, Excel, PowerPoint, Office, Bild, SVG, Textdatei, Vorschau, Speichern, Öffnen, Originaldatei, Standardprogramm, passwortgeschützt, Anhang speichern, includeAttachments, maxAttachmentBytes, durchsuchen, indexieren, attachments, preview, download, open file
---

# Anhänge von Wissensquellen

Viele Confluence-Seiten haben Dateianhänge: PDFs, Office-Dokumente, Bilder oder Textdateien. Dieses Kapitel erklärt, wie du Anhänge im Lesebereich ansiehst, speicherst und mit dem passenden Programm öffnest, und welche Anhänge der Chat durchsuchen kann. Kurz vorweg: Ansehen kannst du fast alles, durchsuchen nur textartige Anhänge, und das auch nur, wenn es eingeschaltet ist. Für Anhänge, die eine zusätzliche Anmeldung verlangen, lies [Einmalcode (OTP)](help:knowledge.otp).

## Welche Quellen Anhänge haben

- **Confluence**: Anhänge werden im Lesebereich angezeigt; textartige Anhänge können zusätzlich in den Index.
- **MediaWiki**: Dateien und Bilder des Wikis werden weder angezeigt noch indexiert.
- **Outlook (PST/OST)**: Die Anhänge von E-Mails werden nicht gelesen; nur der Text der Nachricht kommt in den Index.
- **Lokale Dateien und SharePoint**: Hier sind PDFs und Office-Dateien selbst die Dokumente. Sie werden als ganze Dateien gelesen und durchsucht, nicht als Anhang. Siehe [Weitere Wissensquellen](help:knowledge.other-sources).
- **FTP, JES, NDV, BetaView**: Diese Quellen kennen keine Anhänge.

Der Rest dieses Kapitels betrifft Confluence.

## Anhänge im Lesebereich ansehen

1. Öffne eine Confluence-Seite im Lesebereich ([Der Lesebereich](help:knowledge.reader)).
2. Hat die Seite Anhänge, erscheint unten in der Werkzeugleiste eine Büroklammer mit der Anzahl, Tooltip „n Anhänge“.
3. Klicke die Büroklammer. Eine Liste zeigt je Anhang Symbol, Dateinamen und „Typ · Größe“. Sind es mehr als acht, kannst du in der Liste scrollen.
4. Klicke einen Anhang. Er öffnet sich als Ansicht über dem Lesebereich; während des Ladens steht „Lade Anhang …“.
5. Esc schließt die Ansicht.

Der Client listet bis zu 500 Anhänge je Seite. Im Lesebereich lädt er Dateien bis 100 MB. Die Liste zeigt alle Anhänge, unabhängig davon, ob sie für den Index eingeschaltet sind. Lässt sich die Anhangsliste nicht lesen, bleibt die Seite trotzdem lesbar, nur ohne Büroklammer.

### Wie die Ansicht je Dateityp aussieht

- **Bilder** (auch SVG): wie in der Diashow, mit Zoom und Verschieben.
- **PDF**: Seite für Seite. Mit den Pfeilen oder den Pfeiltasten blätterst du; beim Vergrößern wird die Seite schärfer neu gezeichnet. Ein geschütztes PDF meldet „Das PDF ist mit einem Passwort geschützt“.
- **Text** (etwa .txt, .csv, .json, .xml, .log): in Festbreitenschrift, bis zu zwei Millionen Zeichen.
- **Office** (Word, Excel, PowerPoint): als Vorschaubild, das Confluence selbst erzeugt. Während des Ladens steht „Lade Vorschau …“. Hinweis: Ob und in welcher Qualität eine Vorschau entsteht, hängt vom Confluence-Server ab.
- **Andere Dateitypen**: „Für diesen Dateityp gibt es keine Vorschau.“ Speichern und Öffnen funktionieren trotzdem.

## Anhänge speichern und öffnen

In jeder Ansicht gibt es zwei Knöpfe:

- **„Speichern“** („Originaldatei speichern“) öffnet den Dialog „Anhang speichern“. Nach dem Speichern steht „Datei gespeichert: …“.
- **„Öffnen“** („Originaldatei mit dem Standardprogramm öffnen“) legt eine temporäre Kopie an und öffnet sie mit dem Programm, das dein Betriebssystem für diesen Typ vorsieht. Die Kopie wird beim Beenden des Clients gelöscht. Klappt das nicht, steht „Öffnen fehlgeschlagen: …“.

Beide Knöpfe verwenden die Originaldatei, nicht die Vorschau. Möchtest du ein geöffnetes Dokument bearbeiten und behalten, speichere es vorher mit „Speichern“ an einem eigenen Ort, denn die temporäre Kopie verschwindet.

### Welcher Zugang für Anhänge gilt

Der Client lädt die Datei über dieselbe angemeldete Verbindung wie die Seite: gleicher KeePass-Eintrag, gleicher Proxy, gleiches Client-Zertifikat. Er liest die Angaben des Anhangs (Pfad, Version) jedes Mal frisch aus Confluence. Verlangt Confluence für Downloads eine zusätzliche Anmeldung, erscheint statt der Datei die Abfrage des Einmalcodes; siehe [Einmalcode (OTP)](help:knowledge.otp).

## Werden Anhänge durchsucht?

Es gibt zwei Wege: das Lesen auf Anforderung durch den Assistenten (seit 0.49.0) und die Aufnahme in den Vollindex.

### Auf Anforderung im Chat und im Agent-Modus

Seit 0.49.0 kann der Assistent die Anhänge einer Confluence-Seite selbst lesen, auch PDFs, Office-Dokumente und Bilder:

1. Mit dem Werkzeug `knowledge_attachments` listet er die Anhänge einer Seite: Name, Typ, Größe, Version und Kennung. Geladen wird dabei noch nichts.
2. Mit `knowledge_read` und der Kennung eines Anhangs lädt er die Datei über dieselbe Verbindung wie der Lesebereich und macht daraus Text: Dokumente über die Textextraktion (wie bei Dateien im Chat), Bilder über die Bildbeschreibung eines Bildmodells.
3. Das Ergebnis gehört zur Quelle, nennt die Seite als Eltern und kommt wie eine Seite in den Index. Beim nächsten Mal antwortet der Index. Die Datei selbst wird danach verworfen.

Du musst dafür nichts einschalten. Frag einfach nach dem Inhalt, etwa „Was steht im PDF-Anhang der Seite …?“. Die Größengrenze ist dieselbe wie im Lesebereich. Bilder brauchen ein Modell für Bildbeschreibung beim KI-Dienst; ohne ein solches bleibt ein Bild unbeschrieben. Verlangt Confluence für den Download einen Einmalcode, erscheint die Abfrage über dem Chat-Verlauf, siehe [Einmalcode (OTP)](help:knowledge.otp#einmalcode-im-chat).

### Im Vollindex

Für Fortgeschrittene lässt sich die Aufnahme beim vollständigen Indexieren in der Konfigurationsdatei einschalten, im Block der Confluence-Quelle:

- `includeAttachments=true` nimmt Anhänge mit auf (Vorgabe `false`). Im Quellen-Dialog gibt es dafür kein Feld.
- `maxAttachmentBytes` begrenzt die Größe je Anhang (Vorgabe 5 MB).

Auch dann gilt:

- Nur **textartige** Anhänge kommen in den Index: Text-Dateien, JSON und XML.
- **PDF, Office und Bilder kommen beim Vollindex nicht mit.** Diese liest nur der Assistent auf Anforderung (siehe oben).
- Eingeschaltete Anhänge erscheinen in der Übersicht der Quelle als eigene Einträge unter ihrer Seite und werden wie Seiten beim Lesen oder Vorladen indexiert ([Suche, Vorladen und Indexierung](help:knowledge.indexing)).
- Für die Indexierung fragt der Client nie nach einem Einmalcode.

Alternativ kannst du ein PDF speichern und dem Chat als Datei hinzufügen ([Dateien im Chat](help:chat.attachments)) oder in einen Ordner legen, den eine Quelle „Lokale Dateien“ indexiert.

## Typische Probleme

### Die Büroklammer fehlt

Die Seite hat keine Anhänge, oder die Anhangsliste ließ sich nicht lesen. Im zweiten Fall steht ein Eintrag im Protokoll. Andere Quelltypen als Confluence zeigen nie eine Büroklammer.

### „Lade Anhang …“ endet mit einer Fehlermeldung

Prüfe Netz und Rechte. Ist der Anhang größer als 100 MB, lädt der Lesebereich ihn nicht; öffne ihn dann im Browser über „Seiteninformationen“ → „Im Standardbrowser öffnen“.

### Statt der Datei erscheint „Zusätzliche Anmeldung erforderlich“

Confluence verlangt für Downloads einen Einmalcode. Gib ihn dort ein; siehe [Einmalcode (OTP)](help:knowledge.otp).

### Der Chat kennt den Inhalt eines PDF-Anhangs nicht

Bitte den Assistenten ausdrücklich, die Anhänge der Seite anzusehen. Er braucht dafür Werkzeuge: Die Quelle muss angehakt sein (oder die Seite dem Chat hinzugefügt), und `knowledge_attachments` sowie `knowledge_read` dürfen unter Einstellungen → „Tools“ nicht abgeschaltet sein. Hast du die Einmalcode-Abfrage abgebrochen, fragt der Client erst nach deiner nächsten Nachricht wieder. Weitere Prüfschritte: [Fehlersuche bei Confluence](help:troubleshooting.confluence).
