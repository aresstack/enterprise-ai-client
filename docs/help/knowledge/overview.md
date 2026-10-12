---
id: knowledge.overview
title: Wissensquellen im Überblick
keywords: Wissensquelle, Wissensquellen, Quelle, Quellen, Datenquelle, Drawer, Seitenleiste, Reiter Wissensquellen, Häkchen, anhaken, abwählen, aktivieren, deaktivieren, + Quelle, Quelle hinzufügen, Quelle bearbeiten, Quelle entfernen, löschen, Quellen-Dialog, Quelltyp, Typ, Confluence, MediaWiki, Wiki, Lokale Dateien, FTP, Mainframe, JES, NDV, Natural, Outlook, PST, OST, SharePoint, BetaView, Spaces, Statuszeile, Indexstand, KeePass-Eintrag, Manuell, Erweitert, Pfadpräfix, Adresse, Identität, RAG, knowledge source, source, add source, remove source, enable, disable
---

# Wissensquellen im Überblick

Eine Wissensquelle ist ein angebundenes System, aus dem der Chat und der Agent Wissen holen: ein Confluence, ein MediaWiki, ein Ordner mit Dokumenten, ein Mainframe per FTP und einiges mehr. Dieses Kapitel erklärt, was eine Wissensquelle im Enterprise AI Client ist, wie du den Drawer-Reiter „Wissensquellen“ bedienst und wie du Quellen über den Quellen-Dialog hinzufügst, bearbeitest und entfernst. Es richtet sich an alle, die den Chat mit eigenen Dokumenten nutzen wollen. Die einzelnen Quelltypen haben eigene Kapitel, auf die hier verwiesen wird.

## Was ist eine Wissensquelle?

Ohne Wissensquelle beantwortet das Sprachmodell Fragen nur aus seinem eigenen, allgemeinen Wissen. Mit einer angehakten Wissensquelle sucht der Client vor der Antwort in deinen Quellen, gibt dem Modell die passenden Auszüge mit und hängt die verwendeten Quellen an die Antwort (siehe [Quellenangaben in Antworten](help:chat.citations)).

Wichtig für das Verständnis:

- **Der Client liest nur.** Eine Wissensquelle wird nie verändert; der Client sucht, lädt und zeigt an.
- **Die Quelle bleibt das Original.** Was der Client zusätzlich lokal ablegt, ist ein Index: eine durchsuchbare Kopie der gelesenen Seiten auf deinem Rechner. Er lässt sich jederzeit aus der Quelle neu aufbauen. Was „Index“ genau heißt, erklärt [Suche, Vorladen und Indexierung](help:knowledge.indexing).
- **Eine Quelle hat genau eine Identität.** Sie ergibt sich aus dem Typ und der Adresse (zum Beispiel der Basis-URL von Confluence oder dem Verzeichnis bei lokalen Dateien). Einen frei vergebenen Namen als Schlüssel gibt es nicht. Eine zweite Quelle mit derselben Adresse lehnt der Dialog mit „Diese Wissensquelle ist bereits vorhanden.“ ab.
- **Zugangsdaten stehen nicht in der Konfiguration.** Benutzer und Passwort kommen aus KeePass oder werden bei „Manuell“ beim ersten Zugriff abgefragt (siehe [unten](help:knowledge.overview#der-quellen-dialog)).

## Der Reiter „Wissensquellen“ im Drawer

Fahre mit der Maus über den Hamburger-Knopf links oben (Tooltip „Chats und Wissensquellen“); Reiterleiste und Drawer klappen auf, ein Klick rastet sie ein. Wähle den Reiter „Wissensquellen“. Von oben nach unten siehst du:

1. **Die Suchleiste** mit dem Platzhalter „Quellen durchsuchen…“. Sie durchsucht alle angehakten Quellen gleichzeitig (Enter startet, Esc leert). Die Treffer erscheinen statt der Quellenliste; Einzelheiten in [Suche, Vorladen und Indexierung](help:knowledge.indexing).
2. **Den Knopf „+ Quelle“** (Tooltip „Eine Wissensquelle hinzufügen (Wiki, Confluence, Dateien ...)“). Er öffnet den Quellen-Dialog.
3. **Die Überschrift „WISSENSQUELLEN“** und darunter je Quelle eine Zeile. Ohne Quellen steht dort „Noch keine Wissensquelle. „+ Quelle“ legt eine an.“
4. **Ganz unten den Hinweis** „Der Chat durchsucht nur angehakte Quellen.“

### Aufbau einer Quellenzeile

Jede Zeile hat links ein Häkchen und rechts drei kleine Knöpfe. Dazwischen stehen drei Textzeilen:

- **Titel**: bei Confluence der Titel aus dem Dialog (Vorgabe „Confluence“), bei allen anderen Typen die Adresse der Quelle.
- **Typ und Umfang**, zum Beispiel „MediaWiki · https://wiki.example.org/w/api.php“ oder „Lokale Dateien · C:\Daten\Handbuch“.
- **Statuszeile** mit dem Indexstand oder einem Problem. Rot bedeutet: hier stimmt etwas nicht.

Bei Confluence kommt eine vierte Zeile „Spaces“ mit einem kleinen Pfeil hinzu, etwa „Spaces · alle 12“ oder „Spaces · 3 von 12“. Ein Klick klappt die Liste der Spaces auf; dort legst du je Space per Häkchen fest, ob er durchsucht wird. Mehr dazu in [Confluence als Wissensquelle](help:knowledge.confluence).

Die drei Knöpfe rechts:

| Symbol | Tooltip | Wirkung |
|---|---|---|
| Kreispfeil | „Jetzt indexieren“ | liest die ganze Quelle in den Index; nur bei Typen, die das anbieten |
| Stift | „Bearbeiten“ | öffnet den Quellen-Dialog mit den Werten dieser Quelle |
| Kreuz | „Quelle entfernen“ | entfernt die Quelle nach kurzer Rückfrage |

Ein Klick auf den Text einer Zeile zeigt die Quelle im Lesebereich (Tooltip „Im Lesebereich zeigen (Doppelklick heftet an)“): Der Lesebereich listet dann bis zu 200 Einträge der Quelle in ihrem eingestellten Umfang. Siehe [Lesebereich und Dokumentansicht](help:knowledge.reader).

### Typische Statustexte

- „Wird bei der Suche live gefragt; Treffer kommen in den Index“: Confluence oder MediaWiki, noch nichts im Index. Das ist normal und kein Fehler.
- „Noch nicht indexiert“: ein Typ mit Vollindexierung (zum Beispiel lokale Dateien), der noch nie mit dem Kreispfeil gelesen wurde.
- „34 Seiten im Index · Stand 14:12“: so viele Seiten sind für das aktuelle Embedding-Modell aufbereitet, dazu das Ergebnis des letzten Laufs dieser Sitzung.
- „Wird indexiert …“: ein Lauf dieser Quelle läuft gerade.
- „Abgewählt: wird nicht indexiert und nicht durchsucht“: das Häkchen ist aus.
- „Gespeichert; gilt nach dem nächsten Start“: die Quelle steht in der Datei, konnte aber jetzt nicht angebunden werden.
- „Fehlerhaft: …“: die Einstellungen der Quelle sind unvollständig oder ungültig; der Text nennt das erste Problem.

## Anhaken und abwählen

Das Häkchen entscheidet, ob der Chat eine Quelle benutzt. Es wirkt sofort und ohne Neustart und wird dauerhaft in der Konfigurationsdatei gespeichert.

- **Angehakt** (Tooltip „Angehakt: der Chat durchsucht diese Quelle“): Der Chat sucht vor jeder inhaltlichen Frage in dieser Quelle, die Suchleiste des Reiters ebenfalls.
- **Abgewählt** (Tooltip „Abgewählt: der Chat durchsucht diese Quelle nicht“): Die Zeile steht gedämpft, der Chat und die Suchleiste lassen sie aus, und „Jetzt indexieren“ ist gesperrt.

Einen eigenen RAG-Schalter im Chat gibt es nicht: Sobald mindestens eine Quelle angehakt ist, sucht der Chat. Ist keine angehakt, ist der Chat ein normaler Chat ohne Wissenssuche.

Anhaken allein indexiert nichts. Der Index wächst mit den Treffern der Suche und mit den Seiten, die du liest; ganze Quellen liest nur der Kreispfeil.

## Der Quellen-Dialog

„+ Quelle“ öffnet den Dialog mit der Überschrift „Quelle hinzufügen“, der Stift öffnet ihn als „Quelle „…“ bearbeiten (Typ)“. Oben steht der Hinweis „Gespeichert wird in die Konfigurationsdatei; danach wird die Quelle indexiert.“ und „Mit KeePass genügt der Eintrag: Benutzer, Passwort und URL stehen dort.“

Darunter liegt den Bereich „Quelle“ mit diesen Feldern:

1. **„Typ“**: Beim Hinzufügen eine Auswahlliste der verfügbaren Quelltypen; ein Wechsel baut die Felder neu auf. Beim Bearbeiten steht der Typ fest und lässt sich nicht ändern. Unter dem Typ erklärt eine Zeile, was dieser Typ liest.
2. **„Zugang“** (nur bei Typen mit Anmeldung): „KeePass“ oder „Manuell“. Bei „KeePass“ stehen Benutzer, Passwort und URL im KeePass-Eintrag. Bei „Manuell“ gibst du die Adresse im Dialog an, und die Zugangsdaten fragt der Client beim ersten Zugriff ab; die Eingabe gilt nur für die laufende Sitzung und wird nicht gespeichert.
3. **„KeePass-Eintrag“** (nur bei „KeePass“): eine aufklappbare Liste der Eintragstitel aus KeePass, in die du auch frei schreiben kannst, falls KeePass gerade nicht erreichbar ist. Gibt es genau einen passenden Eintrag, ist er vorgewählt. Der Knopf „Neu“ legt einen neuen KeePass-Eintrag an. Siehe [KeePass einrichten](help:getting-started.keepass).
4. **Die Pflichtangaben des Typs**, mit Sternchen „ *“ markiert, jeweils mit einer kurzen Erklärung darunter.
5. **„Erweitert“** (Tooltip „Weitere Angaben; leer lassen genügt in der Regel“): klappt seltener gebrauchte Felder auf, zum Beispiel Tiefe, Höchstzahlen und Zeichensatz. Hier steht auch das schreibgeschützte Feld „Pfadpräfix“, das technische Präfix des Quelltyps. Bei „KeePass“ steht die Adresse ebenfalls hier, mit dem Zusatz „(sonst aus KeePass)“: Lässt du sie leer, nimmt die Quelle die URL aus dem KeePass-Eintrag; ein eingetragener Wert hat Vorrang.

Unten stehen „Abbrechen“ (Tooltip „Änderungen verwerfen“) und „Speichern“ (Tooltip „Quelle prüfen, speichern und indexieren“). Esc und das Kreuz in der Überschrift brechen ebenfalls ab.

### Was beim Speichern passiert

1. Der Client prüft die Angaben. Fehlt etwas, erscheint eine rote Bereich „Bitte korrigieren:“ mit den Problemen, und der Dialog bleibt offen.
2. Aus Typ und Adresse entsteht die Identität der Quelle.
3. Die Quelle wird in die Konfigurationsdatei geschrieben und sofort angebunden, ohne Neustart. Sie erscheint in der Liste, und der Chat kann sie benutzen.

Hinweis: Trotz des Satzes im Dialog startet nach dem Speichern keine Vollindexierung. Bei Confluence und MediaWiki füllt die Suche den Index; bei den übrigen Typen startest du die Vollindexierung mit dem Kreispfeil. Siehe [Suche, Vorladen und Indexierung](help:knowledge.indexing).

Änderst du beim Bearbeiten die Adresse, ist das eine neue Identität: Die alte Quelle wird abgelöst und ihr Index zurückgezogen.

## Eine Quelle entfernen

1. Klicke in der Zeile auf das Kreuz („Quelle entfernen“).
2. Die Rückfrage „Quelle „…“ entfernen?“ erklärt: „Die Quelle verschwindet aus der Liste und aus der Konfiguration, ihr Index wird gelöscht. Die Zeilen in der Datei werden nur auskommentiert.“
3. „Entfernen“ bestätigt, „Abbrechen“ oder Esc lässt alles, wie es ist.

Die Zeile verschwindet sofort; Datei und Index räumt der Client im Hintergrund auf. Schlägt das fehl, steht „Entfernen fehlgeschlagen (…); bitte erneut versuchen“ in der Zeile. Während eine Quelle indexiert wird, lässt sie sich nicht entfernen.

## Welche Quelltypen es gibt

Die Auswahl im Dialog stammt aus den eingebauten Adaptern. Im aktuellen Stand sind es:

| Typ im Dialog | Was er liest | Suche im Chat | Kapitel |
|---|---|---|---|
| „MediaWiki“ | Seiten eines Wikis ab Startseiten entlang der Wiki-Links | live im Wiki | [MediaWiki](help:knowledge.mediawiki) |
| „Confluence“ | alle Spaces einer Confluence Data Center | live in Confluence | [Confluence](help:knowledge.confluence) |
| „Lokale Dateien“ | ein Verzeichnis mit PDF, Office, Text, Markdown, HTML | im Index | [Weitere Quellen](help:knowledge.other-sources) |
| „FTP (Mainframe)“ | Textquellen aus MVS-Datasets oder Dateien eines FTP-Servers | im Index | [Weitere Quellen](help:knowledge.other-sources) |
| „JES-Jobs (Mainframe)“ | Ausgaben von JCL-Jobs über die JES-Schnittstelle | im Index | [Weitere Quellen](help:knowledge.other-sources) |
| „Natural (NDV)“ | Natural-Quellen aus Bibliotheken eines NDV-Servers | im Index | [Weitere Quellen](help:knowledge.other-sources) |
| „Outlook (PST/OST)“ | E-Mails aus lokalen Postfachdateien | im Index | [Weitere Quellen](help:knowledge.other-sources) |
| „SharePoint“ | Dokumente aus den Bibliotheken einer Site | im Index | [Weitere Quellen](help:knowledge.other-sources) |
| „BetaView“ | Listen und Reports aus der Ergebnisliste eines Favoriten | im Index | [Weitere Quellen](help:knowledge.other-sources) |

„Live“ heißt: Der Client fragt die Suche der Quelle selbst. „Im Index“ heißt: Diese Typen haben keine eigene Suche; der Client findet nur, was vorher mit „Jetzt indexieren“ gelesen wurde.

## Typische Probleme

### „+ Quelle“ ist ausgegraut

Hinzufügen, Bearbeiten und Entfernen brauchen eine Konfigurationsdatei. Läuft der Client ohne sie, kannst du Quellen nur an- und abwählen. Richte den Client über den Einrichtungsassistenten ein (siehe [Einrichtung](help:getting-started.setup)).

### Der Kreispfeil „Jetzt indexieren“ ist grau

Prüfe der Reihe nach: Ist die Quelle angehakt? Ist sie Confluence oder MediaWiki (dort gibt es keine Vollindexierung, das ist so gewollt)? Läuft gerade eine andere Indexierung? Steht in der Statuszeile „Gespeichert; gilt nach dem nächsten Start“ oder „Fehlerhaft: …“?

### Der Chat findet nichts aus meiner Quelle

Prüfe das Häkchen, die Statuszeile und bei Confluence die Spaces. Bei Typen ohne Live-Suche muss die Quelle vorher indexiert sein. Weitere Prüfschritte stehen in [Fehlersuche bei Wissensquellen](help:troubleshooting.knowledge).

## Für Fortgeschrittene

Jede Quelle steht in der Konfigurationsdatei als Block mit gleicher Nummer, etwa `source.1.type=confluence`, `source.1.baseUrl=…` und `source.1.enabled=true`. Die Nummer ist nur die Position in der Liste. Die Adresse (`source.1.address`) setzt der Client selbst. Schlüssel, die der Dialog nicht zeigt (Zeitlimits, einige Feineinstellungen), bleiben beim Speichern unverändert. Wo die Datei liegt, steht in [Die Konfigurationsdatei](help:settings.config-file).
