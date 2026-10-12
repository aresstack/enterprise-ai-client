---
id: chat.history
title: Chat-Verlauf und gespeicherte Chats
keywords: Verlauf, Historie, Chat-Historie, gespeicherte Chats, alte Chats, Chats, Drawer, Seitenleiste, Neuer Chat, neuen Chat beginnen, Chat öffnen, umbenennen, Titel ändern, löschen, wiederherstellen, rückgängig, Papierkorb, suchen, filtern, speichern, Speicherort, chats Ordner, JSON, history, sidebar, rename, delete, restore
---

# Chat-Verlauf und gespeicherte Chats

Der Enterprise AI Client speichert jede Unterhaltung automatisch. Dieses Kapitel zeigt, wie du über den Reiter „Chats“ im Drawer einen neuen Chat beginnst, alte Chats wiederfindest, öffnest, umbenennst und löschst, und wo die Daten auf deinem Rechner liegen. Es ist für alle gedacht, die mit mehreren Themen parallel arbeiten oder eine frühere Antwort wiederfinden wollen.

## Den Reiter „Chats“ öffnen

1. Fahre mit der Maus über den Hamburger-Knopf oben links (Tooltip „Chats und Wissensquellen“). Reiterleiste und Drawer klappen auf.
2. Klicke auf den Hamburger, damit der Drawer offen bleibt (eingerastet). Ohne Klick faltet er kurz nach dem Verlassen wieder weg.
3. Wähle in der Reiterleiste „Chats“, falls ein anderer Reiter aktiv ist.

Im Reiter siehst du oben den Knopf „+ Neuer Chat“, darüber ein Suchfeld „Chats durchsuchen…“ und darunter die Liste der Chats. Ob der Drawer eingerastet ist und wie breit er ist, merkt sich das Programm für den nächsten Start.

## Aufbau der Liste

Die Liste ist in Gruppen geteilt:

- **AKTIV**: der Chat, in den du gerade schreibst (und, falls vorhanden, eine eigene Zeile für den Agenten). Als Titel steht dort deine erste Nachricht oder der Titel, den du vergeben hast; solange noch nichts geschrieben ist, „Neuer Chat“. Darunter steht zum Beispiel „Chat · 4 Nachrichten“ oder „Chat · noch keine Nachrichten“, rechts die Uhrzeit der letzten Nachricht.
- **HEUTE**, **GESTERN**, **LETZTE 7 TAGE**, **ÄLTER**: die gespeicherten Chats, nach dem Zeitpunkt ihrer letzten Änderung, neueste zuerst. Leere Gruppen fehlen. Jede Zeile zeigt Titel, „Chat · n Nachrichten“, bei Bedarf „· 1 Anhang“ bzw. „· n Anhänge“, und rechts die Uhrzeit (heute) oder das Datum (ältere).

Ein grüner Punkt vor einem Titel heißt: In diesem Chat läuft gerade eine Verarbeitung. Der gewählte Chat ist hellblau hinterlegt und links blau markiert.

Gibt es keine Chats, steht dort „Keine Chats“; findet die Suche nichts, „Keine passenden Chats“.

### Chats durchsuchen

Tippe in das Feld „Chats durchsuchen…“. Die Liste filtert sofort nach dem **Titel** (Groß-/Kleinschreibung egal). Der Inhalt der Nachrichten wird dabei nicht durchsucht. Benenne wichtige Chats deshalb sprechend um (siehe [unten](help:chat.history#das-menue-einer-chat-zeile)).

## Einen neuen Chat beginnen

Klicke auf „+ Neuer Chat“ (Tooltip „Neuen Chat beginnen“). Der Verlauf wird geleert, eine neue Unterhaltung beginnt, und der bisherige Chat liegt gespeichert in der Liste. Der neue Chat erscheint dort erst, wenn du die erste Nachricht gesendet hast; ein leerer Chat wird nicht gespeichert.

Was beim Wechsel passiert:

- Das Modell beginnt ohne Erinnerung an den vorigen Chat.
- Vorgemerkte Verweise auf Ressourcen des alten Chats verschwinden aus dem Composer; neu vorgemerkte Dateien und Seiten bleiben.
- Eine laufende Sprachausgabe endet.
- Im Agent-Modus endet auch die Agentensitzung; der neue Chat bekommt ein eigenes, leeres Recherche-Projekt.

### „+ Neuer Chat“ ist grau

Solange eine Antwort läuft, ist der Knopf gesperrt. Warte, bis die Antwort fertig ist, oder klicke im Composer auf „Stop“.

## Einen gespeicherten Chat öffnen

Klicke in der Liste auf die Zeile des Chats. Der Chat wird im Chat-Modus geöffnet:

- Der Verlauf zeigt alle gespeicherten Nachrichten mit ihren Anhang-, Seiten-, Verweis- und Zitat-Chips.
- Unter den Antworten steht wieder die aufklappbare Quellenliste „Quellen (n)“ (siehe [Quellenangaben](help:chat.citations)).
- Das Modell bekommt den bisherigen Verlauf (deine Fragen und die vollständigen Antworten) und kann nahtlos weitermachen. Abgebrochene Teilantworten bleiben dabei, wie im laufenden Chat, aus dem Modellverlauf heraus.
- Anhänge liegen unverändert im Ordner des Chats; das Modell findet sie mit seinen Werkzeugen wieder.
- Seiten, die du früher hinzugefügt hast, gehören wieder zur Unterhaltung.
- Der zuletzt verwendete Wissenskontext (welche Abschnitte aus der Wissensbasis benutzt wurden) wird wiederhergestellt, damit eine passende Folgefrage nicht neu suchen muss.

Nicht wiederhergestellt werden die Werkzeug- und Gedankenwolken; sie gehören nur zum Lauf, in dem sie entstanden. Beim Öffnen wird auch nichts automatisch vorgelesen.

Ist der Drawer nicht eingerastet, klappt er nach dem Öffnen weg. Während eine Antwort läuft, lässt sich kein anderer Chat öffnen.

## Das Menü einer Chat-Zeile

Jede gespeicherte Chat-Zeile und die Zeile des aktiven Chats (sobald er Nachrichten hat) hat ein Menü. Du öffnest es mit einem Rechtsklick auf die Zeile oder über das „…“, das rechts erscheint, sobald du mit der Maus über die Zeile fährst. Es hat drei Einträge:

- „Umbenennen …“
- „Chat exportieren …“ (siehe [Chat exportieren](help:chat.export))
- „Chat löschen“

### Chat umbenennen

1. Wähle im Menü „Umbenennen …“.
2. Die Zeile wird zu einem Eingabefeld mit dem bisherigen Titel, der Text ist markiert.
3. Tippe den neuen Titel und drücke **Enter**. **Esc** oder das Kreuz rechts daneben bricht ab. Ein leerer Titel gilt als Abbruch.

Ein von dir vergebener Titel bleibt erhalten, auch wenn du im Chat weiterschreibst. Ohne Umbenennen ist der Titel die erste Nachricht des Chats, auf 80 Zeichen gekürzt und auf eine Zeile zusammengezogen.

### Chat löschen und wiederherstellen

1. Wähle im Menü „Chat löschen“. Es gibt keine Rückfrage.
2. Die Zeile bleibt in der Liste stehen, der Titel ist gedimmt, darunter steht „Gelöscht“, und rechts zeigt ein Rückpfeil (Tooltip „Wiederherstellen (bis zum Neustart)“).
3. Ein Klick auf den Rückpfeil oder der Menüeintrag „Wiederherstellen“ (Rechtsklick auf die Zeile) holt den Chat zurück.

Endgültig gelöscht werden Chat-Datei, Anhänge und (im Agent-Modus) das Recherche-Projekt erst, **wenn du das Programm beendest**. Bis dahin kannst du jederzeit wiederherstellen. Nach dem Neustart ist ein gelöschter Chat weg.

Löschst du den Chat, in dem du gerade bist, wechselt das Programm vorher automatisch in einen neuen, leeren Chat. Während eine Antwort läuft, ist „Chat löschen“ gesperrt.

## Wann wird gespeichert?

Du musst nie selbst speichern. Der aktuelle Chat wird jedes Mal neu geschrieben, wenn eine Zeile im Verlauf fertig ist:

- deine Nachricht (samt den Namen der Anhänge, Seiten, Verweise und Zitate),
- eine fertige oder abgebrochene Antwort, zusammen mit ihren Quellen und den Werkzeugaufrufen (für die Diagnose),
- Hinweiszeilen des Programms,
- der Wissenskontext, sobald er vor der Antwort feststeht (er übersteht so auch eine gescheiterte Antwort und einen Neustart).

Nicht gespeichert werden laufende und fehlgeschlagene Antworten sowie die Werkzeug- und Gedankenwolken. Ein Chat ohne eigene Nachricht wird nicht angelegt.

## Wo werden die Chats gespeichert?

Die Chats liegen im Ordner `chats` neben der Konfigurationsdatei, also im Einstellungsordner. Ohne besondere Startparameter ist das unter Windows `%APPDATA%\.enterprise-ai-client\chats`, auf anderen Systemen `.enterprise-ai-client/chats` im Benutzerverzeichnis. Am schnellsten kommst du hin über Einstellungen → Knopf „Einstellungsordner öffnen“ links in der Fußzeile.

Darin findest du:

- je Chat eine Datei mit der Chat-Kennung und der Endung `.json` (lesbares JSON mit Titel, Zeitstempeln, Nachrichten, Quellen, Werkzeugaufrufen, Wissenskontext),
- je Chat mit Anhängen einen gleichnamigen Ordner; darin je Anhang ein Unterordner `att-` plus acht Zeichen mit einer Kopie der Datei.

Neben dem Ordner `chats` liegen weitere Dateien des Programms, etwa die Favoriten. Die Chat-Dateien enthalten deine Fragen und Antworten im Klartext. Behandle den Ordner entsprechend vertraulich; siehe [Datenschutz](help:concepts.privacy).

Hinweis für Fortgeschrittene: Wird das Programm mit einer anderen Konfigurationsdatei gestartet, liegt der Ordner `chats` neben dieser Datei. Bearbeite die JSON-Dateien nicht von Hand, während das Programm läuft; der nächste Speichervorgang überschreibt sie. Ein Chat, dessen Datei nicht lesbar ist, fehlt einfach in der Liste.

## Typische Fragen

### Mein alter Chat fehlt in der Liste

1. Ist im Suchfeld „Chats durchsuchen…“ noch ein Begriff eingetragen? Leere es.
2. Hattest du den Chat in einer früheren Sitzung gelöscht? Dann ist er nach dem Beenden endgültig entfernt worden.
3. Startest du das Programm mit einer anderen Konfigurationsdatei? Dann liegt ein anderer Ordner `chats` zugrunde.
4. Hattest du in dem Chat überhaupt eine Nachricht gesendet? Leere Chats werden nicht gespeichert.

### Nach dem Öffnen fehlen die Wolken und die Recherchekarte

Das ist so gewollt: Sie zeigen nur den laufenden Vorgang. Die Ergebnisse stehen in der Quellenliste unter der Antwort; die einzelnen Werkzeugaufrufe kannst du über den JSON-Export nachvollziehen.

### Weiß das Modell im neuen Chat noch, was ich vorher gefragt habe?

Nein. Jeder Chat ist eine eigene Unterhaltung. Öffnest du einen gespeicherten Chat, bekommt das Modell dessen Verlauf zurück.

## Grenzen

- Die Suche filtert nur nach Titeln, nicht nach Nachrichteninhalten.
- Löschen ist bis zum Beenden umkehrbar, danach nicht mehr.
- Ein Chat lässt sich nicht duplizieren oder zusammenführen.
- Wolken und fehlgeschlagene Antworten bleiben nicht erhalten.
