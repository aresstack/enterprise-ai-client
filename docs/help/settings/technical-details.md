---
id: settings.technical-details
title: Einstellungen – Reiter „Technische Details“
keywords: Technische Details, Diagnose, Protokoll, Log, Logdatei, Logs, Stacktrace, Konfiguration prüfen, Verbindung testen, Einstellungsordner öffnen, Aktualisieren, Einrichtungsassistent erneut starten, Merlin, Maskottchen, Java-Version, Fehlersuche, Support, Diagnostics, Logfile, enterprise-ai-client.0.log
---

# Einstellungen – Reiter „Technische Details“

Der Reiter „Technische Details“ ist der Ort für die Fehlersuche. Er zeigt, welche Konfigurationsdatei und welches Protokoll der Client benutzt, welche Java-Version läuft, und darunter das Ende des Protokolls. Über eine Knopfleiste prüfst du die Angaben des Dialogs, testest die Verbindung zum KI-Dienst, öffnest den Einstellungsordner oder startest den Einrichtungsassistenten erneut. Unten rechts sitzt Merlin, der Begleiter aus dem Einrichtungsassistenten.

## Die Knopfleiste

Oben im Reiter stehen die Knöpfe in dieser Reihenfolge:

| Knopf | Tooltip | Wirkung |
|---|---|---|
| „Aktualisieren“ | – | Liest das Protokoll neu ein und zeigt den aktuellen Stand. |
| „Einrichtungsassistent erneut starten“ | „Merlin führt noch einmal durch die Einrichtung“ | Verwirft die Änderungen im Dialog ohne zu speichern und öffnet den Einrichtungsassistenten. |
| „Konfiguration prüfen“ | „Prüft die Angaben dieses Dialogs, ohne zu speichern“ | Prüft den aktuellen Entwurf auf Vollständigkeit und gültige Werte. |
| „Verbindung testen“ | „Weg zum KI-Dienst mit den Angaben dieses Dialogs prüfen“ | Prüft Schritt für Schritt, ob der KI-Dienst erreichbar ist. |
| „Einstellungsordner öffnen“ | „Ordner mit Konfiguration und Protokoll öffnen“ | Öffnet das Anwendungsverzeichnis im Dateimanager deines Systems. |

Das Ergebnis von „Konfiguration prüfen“ und „Verbindung testen“ erscheint in einem Statusfeld direkt unter der Leiste.

### Konfiguration prüfen

Der Knopf prüft den Entwurf so, wie „Speichern“ es tun würde, schreibt aber nichts. Ist alles gültig, steht dort „Konfiguration in Ordnung.“ Sonst steht „Konfiguration unvollständig:“ und darunter je ein Punkt pro Problem, zum Beispiel ein fehlendes Chat-Modell. Die Meldungen nennen das Feld und was erwartet wird, nie den Inhalt eines Secrets.

### Verbindung testen

Der Test nutzt die Angaben des Dialogs, auch wenn du sie noch nicht gespeichert hast. Während er läuft, steht „Verbindungstest läuft …“ im Statusfeld. Danach folgt je Schritt eine Zeile „OK – <Schritt>: <Detail>“ oder „Fehler – <Schritt>: <Detail>“ und am Ende „Verbindung in Ordnung.“ oder „Verbindungstest fehlgeschlagen.“ Derselbe Test steht auch im Reiter [KI-Dienst](help:settings.ai-service); die Zeilen landen zusätzlich im Protokoll.

### Einrichtungsassistent erneut starten

Dieser Knopf erscheint nur, wenn du den Dialog über das Zahnrad im Hauptfenster geöffnet hast; dann steht unten im Dialog auch der Knopf „Einrichtungsassistent“, der dasselbe tut. Beide schließen den Dialog, ohne zu speichern, und öffnen den Assistenten, in dem Merlin dich noch einmal durch die Einrichtung führt. Willst du Änderungen behalten, speichere vorher. Wie der Assistent abläuft, beschreibt [Einrichtung](help:getting-started.setup).

## Der Textbereich: Pfade, Java und Protokoll

Unter der Leiste steht ein schreibgeschützter Textbereich in Festbreitenschrift. Oben stehen drei Zeilen:

- „Konfiguration: …“ mit dem Pfad der Konfigurationsdatei,
- „Protokoll: …“ mit dem Pfad der aktuellen Protokolldatei, also `logs/enterprise-ai-client.0.log` im Anwendungsverzeichnis,
- „Java: …“ mit Version und Hersteller der laufenden Java-Umgebung.

Nach einer Leerzeile folgt das Protokoll. Ist es größer als 64 KB, siehst du nur das Ende; davor steht dann „… (gekürzt auf die letzten 64 KB)“. Fehlt die Datei, steht „Keine Protokolldatei vorhanden.“, kann sie nicht gelesen werden, „Protokoll nicht lesbar: …“ mit dem Grund. Der Bereich wird bei jedem Öffnen des Reiters neu geladen, zwischendurch mit „Aktualisieren“.

Du kannst den Text markieren und kopieren, etwa um ihn einer Fehlermeldung beizulegen. Prüfe vorher, ob er Angaben enthält, die du nicht weitergeben willst, zum Beispiel Namen deiner Wissensquellen.

## Was im Protokoll steht

Der Client schreibt ein rollierendes Protokoll: drei Dateien zu je höchstens 2 MB, die älteste wird überschrieben. Protokolliert wird ab der Stufe INFO, unter anderem:

- der Programmstart und die geladene Konfiguration ohne Secrets,
- die Quellen der vertrauenswürdigen Zertifikate,
- die Proxy-Regel und der gewählte Weg einer Verbindung,
- fehlgeschlagene Chat-Aufrufe und Zugriffe auf Wissensquellen, mit Stacktrace,
- die Zeilen des Verbindungstests,
- Zeilen der Sprachausgabe je vorgelesenem Stück, ohne Text und ohne Key.

API-Keys und Passwörter erscheinen nie im Protokoll. Hintergrund zur Sicherheit: [Sicherheit](help:concepts.security).

## Merlin in der Ecke

Unten rechts steht Merlin mit einer Sprechblase, Überschrift „Merlin“, und dem Satz „Ich wusste, dass wir uns wiedersehen.“ Er ist hier nur Begleiter: er hat kein Menü und reagiert nicht auf Klicks. Alle Aktionen liegen in der Knopfleiste oben. Ist der Reiter zu schmal, blendet der Client Merlin ganz aus, statt ihn abzuschneiden; vergrößere den Dialog, wenn du ihn sehen willst.

## Schritt für Schritt: einen Fehler eingrenzen

1. Öffne Einstellungen → „Technische Details“.
2. Klicke „Konfiguration prüfen“. Meldet sie Probleme, behebe sie in den genannten Reitern.
3. Klicke „Verbindung testen“ und lies, welcher Schritt mit „Fehler“ endet.
4. Wiederhole die Aktion, die nicht klappt, etwa eine Chat-Frage, und klicke danach „Aktualisieren“.
5. Lies die letzten Zeilen des Protokolls. Meist nennt eine Zeile mit Stacktrace die Ursache.
6. Brauchst du die ganze Datei, öffne mit „Einstellungsordner öffnen“ den Ordner und dort `logs`.

## Grenzen

- Der Reiter zeigt nur die letzten 64 KB der aktuellen Protokolldatei; ältere Einträge findest du in den Dateien `enterprise-ai-client.1.log` und `enterprise-ai-client.2.log`.
- Die Protokollstufe lässt sich im Dialog nicht ändern.
- „Einstellungsordner öffnen“ braucht einen Dateimanager, den das System öffnen kann. Fehlt der Ordner, legt der Client ihn an. Kann das System keine Ordner öffnen, zeigt ein Hinweis den Grund und darunter „Der Einstellungsordner liegt hier:“ mit dem Pfad zum Kopieren. Wo der Ordner liegt, steht auch in [Die Konfigurationsdatei](help:settings.config-file).

## Typische Probleme

### Hier steht „Keine Protokolldatei vorhanden.“

Der Client konnte im Anwendungsverzeichnis kein Protokoll anlegen, oder es wurde gelöscht. Prüfe, ob du in das Verzeichnis schreiben darfst.

### Ich finde den Knopf „Einrichtungsassistent erneut starten“ nicht

Der Dialog wurde nicht über das Zahnrad geöffnet, etwa beim Start mit einer fehlerhaften Konfiguration. Schließe ihn und öffne ihn über das Zahnrad erneut.

### Wo ist die Diagnose?

Einen eigenen Knopf „Diagnose“ gibt es nicht. Die Diagnose besteht aus „Konfiguration prüfen“, „Verbindung testen“ und dem Protokoll. Weitere Hilfe: [Fehlerbehebung im Überblick](help:troubleshooting.overview).
