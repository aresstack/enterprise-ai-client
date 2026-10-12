---
id: getting-started.first-chat
title: Die erste Frage stellen
keywords: erster Chat, erste Frage, Frage stellen, Nachricht senden, Senden, Enter, Stop, Eingabefeld, Chat starten, Wissensquellen anhaken, Häkchen, Quelle auswählen, Spaces, RAG, Wissenssuche, Internes Wissen durchsuchen, Planen der Antwort, Recherchekarte, Quellen, Belege, Merlins Tochter, Antwort, Fehlerblase, Neuer Chat, Seitenleiste, Drawer, getting started, first chat
---

# Die erste Frage stellen

Nach dem Einrichtungsassistenten öffnet sich das Hauptfenster mit einem leeren Chat. Dieses Kapitel zeigt, wie du deine erste Frage stellst, wie du festlegst, in welchen Wissensquellen der Chat suchen soll, und was zwischen deiner Frage und der Antwort passiert. Es richtet sich an alle, die den Client gerade eingerichtet haben und loslegen wollen.

## Das Hauptfenster auf einen Blick

- In der Mitte steht der Chatverlauf. Ein neuer Chat zeigt den Hinweis „Neue Unterhaltung. Nachricht unten eingeben und Enter drücken.“
- Unten liegt das Eingabefeld mit dem Platzhalter „Nachricht…“. Links in seiner Fußzeile zeigt ein Knopf das aktuelle Chat-Modell, daneben die Auswahl des Denkaufwands (nur für Modelle, die ihn unterstützen). Rechts liegen Büroklammer (Dateien anhängen), Mikrofon (Diktieren) und der Hauptknopf „Senden“, der während einer Antwort zu „Stop“ wird.
- Über das Menü-Symbol oben links klappst du die Seitenleiste auf. Sie hat die Seiten „Chats“ (mit „+ Neuer Chat“, deinen gespeicherten Chats und dem Zahnrad für die Einstellungen), „Wissensquellen“ und „Favoriten“.

## Schritt für Schritt: die erste Frage

1. Stelle sicher, dass KeePass läuft und entsperrt ist. Der Client holt den API-Key bei jeder Anfrage frisch aus KeePass.
2. Klicke in das Eingabefeld und schreibe deine Frage.
3. Drücke Enter oder klicke „Senden“. Umschalt+Enter fügt stattdessen eine neue Zeile ein.
4. Deine Frage erscheint im Verlauf. Die Antwort läuft Stück für Stück in die Antwortblase ein, überschrieben mit dem Namen des Chat-Assistenten, standardmäßig „Merlins Tochter“.
5. Willst du eine laufende Antwort abbrechen, klicke „Stop“. Deine Frage bleibt im Verlauf stehen.

Sollte sich beim ersten Zugriff auf KeePass das Fenster „KeePass verbinden“ öffnen, ist noch keine gültige Kopplung gespeichert. Gib das Einmal-Passwort ein, das KeePass anzeigt, siehe [KeePass verbinden](help:getting-started.keepass).

Ohne angehakte Wissensquelle ist das ein normaler Chat: Das Modell antwortet aus seinem allgemeinen Wissen und aus dem, was du ihm im Chat mitgibst.

## Wissensquellen anhaken

Damit der Chat in deinem Wissen sucht, etwa im Confluence, das du im Assistenten eingerichtet hast, muss die Quelle angehakt sein:

1. Klappe die Seitenleiste auf und wähle die Seite „Wissensquellen“.
2. Jede Quelle hat links ein Häkchen. Angehakt heißt: „der Chat durchsucht diese Quelle“. Unten auf der Seite steht zur Erinnerung „Der Chat durchsucht nur angehakte Quellen.“
3. Bei Confluence kannst du die Spaces aufklappen und für jeden Space einzeln festlegen, ob er durchsucht wird. Neue Spaces sind von selbst angehakt. Die Zeile der Quelle zeigt, wie viele gewählt sind, etwa „alle 12“ oder „3 von 12“.

Die Häkchen gelten sofort, ohne Neustart, und der Client merkt sie sich über den Neustart hinaus. Hast du Confluence im Assistenten eingerichtet, ist die Quelle schon angehakt. Gibt es noch keine Quelle, steht dort „Noch keine Wissensquelle. „+ Quelle“ legt eine an.“ Mehr: [Wissensquellen im Überblick](help:knowledge.overview).

## Was nach dem Senden passiert

Sobald mindestens eine Quelle angehakt ist, sucht der Client vor der Antwort in deinem Wissen. Ausgenommen sind Small Talk und Fragen an den Assistenten selbst (etwa „Welche Werkzeuge hast du?“); solche Fragen beantwortet der Client aus dem, was ihm tatsächlich zur Verfügung steht.

1. **Recherche:** Unter deiner Frage erscheint zuerst „Planen der Antwort …“; sieht der Plan eine Suche vor, folgt die Karte „Internes Wissen durchsuchen“. Sie zeigt live, was gerade passiert, zum Beispiel „Live-Suche in den Quellen …“, „Volltextsuche …“, „Semantische Suche …“, „Prüfe Evidenz …“, die gestellten Suchanfragen und die Zahl der Treffer.
2. **Live-Suche und Index:** Der Client fragt die angehakten Quellen direkt ab, lädt passende Seiten und legt sie sofort im lokalen Index ab. Zusätzlich sucht er im Index im Volltext und nach Bedeutung (über das Embedding-Modell). Die aufwendigere Aufbereitung der Seiten läuft danach im Hintergrund weiter und macht spätere Suchen besser.
3. **Bewertung:** Das Chat-Modell prüft in einem eigenen kleinen Aufruf, welche Treffer zur Frage passen und ob sie reichen. Reicht es nicht, sucht der Client mit einer genaueren Anfrage weiter, aber nur in begrenztem Umfang.
4. **Antwort:** Das Modell bekommt die passenden Auszüge nummeriert als Kontext und antwortet. Die Karte verschwindet, sobald die Antwort beginnt.
5. **Quellen:** Unter der Antwort hängen die verwendeten Quellen, einklappbar. Daran siehst du, worauf sich die Antwort stützt. Siehe [Quellenangaben](help:chat.citations).

Stellst du eine Anschlussfrage, prüft der Client zuerst, ob die schon gefundenen Quellen reichen. Dann steht in der Karte etwa „Vorhandene Quellen werden verwendet (…)“; reichen sie nicht, folgt eine gezielte Nachrecherche.

Gibt es Probleme bei der Suche, erscheint ein eigener Hinweis im Verlauf, zum Beispiel:

- „Keine passenden Abschnitte in der Wissensbasis gefunden. Die Antwort entstand ohne Kontext.“
- „Die semantische Suche ist ausgefallen; es wurde nur im Volltext gesucht.“
- „Die Wissenssuche ist ausgefallen. Die Antwort entstand ohne Kontext aus der Wissensbasis.“

Lies diese Hinweise: Sie sagen dir, ob die Antwort wirklich auf deinem Wissen beruht.

## Gut zu wissen für den Anfang

- **Beim Start wird nichts indexiert.** Der Index füllt sich mit dem, was deine Fragen finden. Willst du eine Quelle vollständig im Index haben, nutze „Jetzt indexieren“ an der Quelle. Fortschritt und Stand zeigt die Statuszeile über dem Eingabefeld, etwa „Indexierung: 12 von 80 Seiten · …“ und danach „Wissensbasis: …“. Siehe [Indexierung](help:knowledge.indexing).
- **Die erste Frage kann länger dauern**, weil Seiten erst geladen werden müssen. Spätere Fragen zum selben Thema sind meist schneller.
- **Konkrete Fragen funktionieren besser.** Nenne Systemnamen, Begriffe und Abkürzungen, wie sie in deinem Confluence stehen.
- **Ein neuer Chat für ein neues Thema:** „+ Neuer Chat“ in der Seitenleiste beginnt eine frische Unterhaltung. Frühere Chats findest du unter „Chats“, siehe [Chatverlauf](help:chat.history).
- **Vorlesen und Diktieren:** Ist ein Sprachausgabe-Modell gewählt, kann der Knopf über dem Verlauf Antworten vorlesen; mit einem Spracheingabe-Modell diktierst du über das Mikrofon. Siehe [Antworten vorlesen](help:chat.read-aloud) und [Diktieren](help:chat.dictation).
- **Fragen zum Programm selbst** kannst du ebenfalls stellen, siehe [Hilfe im Chat](help:chat.app-help).

## Grenzen

- Der Chat durchsucht nur angehakte Quellen und nur Inhalte, die dein Benutzer dort sehen darf.
- Wie gut die Antwort ist, hängt vom Chat-Modell und von den gefundenen Auszügen ab. Prüfe wichtige Aussagen an den Quellen.
- Die Zahl der Suchrunden und Quellen je Antwort ist begrenzt (einstellbar unter Einstellungen → Index). Findet der Client nach dieser Grenze nicht genug, sagt die Antwort das offen, statt Details zu erfinden; ganz ausschließen lässt sich das bei einem Sprachmodell aber nicht. Siehe [Einschränkungen](help:concepts.limitations).

## Typische Probleme

Was bei den ersten Fragen typischerweise schiefgeht und wie du es prüfst.

### Eine rote Blase statt einer Antwort

Die rote Blase nennt in der ersten Zeile die Einordnung, etwa „Der KI-Dienst ist nicht erreichbar.“ oder „Anmeldung am KI-Dienst fehlgeschlagen.“, darunter „Technische Ursache:“ und meist einen „Hinweis:“ mit dem nächsten Schritt. Häufig ist KeePass gesperrt, ein Zertifikat wird nicht vertraut oder der Proxy blockiert. Teste unter Einstellungen → Technische Details mit „Verbindung testen“. Siehe [Probleme im Chat](help:troubleshooting.chat) und [Netzwerkprobleme](help:troubleshooting.network).

### Die Antwort nennt keine Quellen

Prüfe, ob unter „Wissensquellen“ mindestens eine Quelle (und bei Confluence der passende Space) angehakt ist. Ohne angehakte Quelle wird nicht gesucht. Steht ein Hinweis wie „Keine passenden Abschnitte … gefunden“ im Verlauf, formuliere die Frage konkreter. Siehe [Probleme mit Wissensquellen](help:troubleshooting.knowledge).

### Die Antwort dauert sehr lange

Bei der ersten Suche werden Seiten aus der Quelle geladen; die Recherchekarte zeigt, woran gerade gearbeitet wird. Mit „Stop“ brichst du ab. Bleibt der Chat dauerhaft langsam, prüfe die Netzwerkverbindung zur Quelle und zum KI-Dienst.
