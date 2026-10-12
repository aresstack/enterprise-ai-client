---
id: troubleshooting.overview
title: Fehlersuche: So gehst du vor
keywords: Fehlersuche, Fehler, Problem, geht nicht, funktioniert nicht, kaputt, Fehlermeldung, rote Blase, Fehlerblase, Details anzeigen, Technische Ursache, Hinweis, Protokoll, Logdatei, Log, enterprise-ai-client.0.log, Technische Details, Konfiguration prüfen, Verbindung testen, Einstellungsordner öffnen, Support, Supportanfrage, Ticket, Hilfe anfordern, troubleshooting, error, logfile, stacktrace, debug
---

# Fehlersuche: So gehst du vor

Dieses Kapitel beschreibt das allgemeine Vorgehen, wenn im Enterprise AI Client etwas nicht klappt: wo die Fehlermeldung steht, wie du an die technischen Details und an die Protokolldatei kommst, wie du die Konfiguration prüfst und was du bei einer Supportanfrage angibst, ohne Passwörter oder API-Keys preiszugeben. Die folgenden Kapitel der Fehlersuche behandeln dann einzelne Bereiche (Einrichtung, KeePass, Netzwerk, Modelle, Chat, Wissensquellen, Confluence, Sprache, Agent). Lies dieses Kapitel zuerst, wenn du nicht weißt, wo du anfangen sollst.

## Die Fehlerblase im Chat lesen

Scheitert eine Chat-Anfrage, erscheint statt der Antwort eine rote Sprechblase. Sie ist dreiteilig aufgebaut:

1. **Die Überschrift** ordnet den Fehler ein. Mögliche Überschriften sind wörtlich:
   - „Der KI-Dienst ist nicht erreichbar.“ (Verbindung, Zeitüberschreitung, abgebrochener Datenstrom)
   - „Anmeldung am KI-Dienst fehlgeschlagen.“ (API-Key fehlt, ist falsch oder wurde abgelehnt)
   - „Der API-Key konnte nach mehreren Versuchen nicht aus KeePass gelesen werden. KeePassRPC war vorübergehend nicht verfügbar.“
   - „Der KI-Dienst ist ausgelastet. Bitte später erneut versuchen.“ (HTTP 429)
   - „Der KI-Dienst hat die Anfrage abgelehnt.“ (etwa HTTP 400, 404 oder 422, aber auch ein lokales Modell, das nicht lädt)
   - „Fehler im KI-Dienst.“ (HTTP 5xx)
   - „Unerwartete Antwort des KI-Dienstes.“ (Antwort in einem Format, das der Client nicht versteht)

   Kennt der Client einen HTTP-Status, hängt er ihn an, etwa „Fehler im KI-Dienst. (Status 503)“.
2. **Technische Ursache:** die Kette der technischen Meldungen in einer Zeile, ohne Paketnamen und höchstens 600 Zeichen lang. Alles, was wie ein Bearer-Token aussieht, wird als „Bearer ***“ maskiert.
3. **Hinweis:** der nächste sinnvolle Schritt, wenn der Client die Ursache erkennt (Zertifikat, Proxy, Namensauflösung, Zeitüberschreitung, KeePass). Nicht jede Blase hat einen Hinweis.

„Technische Ursache“ und „Hinweis“ sind eingeklappt. Klicke in der Blase auf „Details anzeigen“, um sie zu sehen, und auf „Details ausblenden“, um sie wieder zu verbergen.

Neben der roten Fehlerblase gibt es kursive **Hinweiszeilen** der Anwendung, etwa „Keine passenden Abschnitte in der Wissensbasis gefunden. Die Antwort entstand ohne Kontext.“. Das ist kein Fehler des KI-Dienstes, sondern eine Information über die Wissenssuche (siehe [Wissensquellen finden nichts](help:troubleshooting.knowledge)).

### Welche Überschrift führt wohin?

| Überschrift beginnt mit | Weiter in |
|---|---|
| „Der KI-Dienst ist nicht erreichbar.“ | [Netzwerk, Proxy und Zertifikate](help:troubleshooting.network) |
| „Anmeldung am KI-Dienst fehlgeschlagen.“ | [KeePass](help:troubleshooting.keepass), bei HTTP 401/403 auch [Netzwerk](help:troubleshooting.network) |
| „Der API-Key konnte nach mehreren Versuchen nicht …“ | [KeePass](help:troubleshooting.keepass) |
| „Der KI-Dienst ist ausgelastet.“, „Fehler im KI-Dienst.“ | [Netzwerk](help:troubleshooting.network) (Abschnitt zu 429 und 5xx) |
| „Der KI-Dienst hat die Anfrage abgelehnt.“ | [Modelle](help:troubleshooting.models) |
| „Unerwartete Antwort des KI-Dienstes.“ | [Chat](help:troubleshooting.chat) |

## Technische Details in den Einstellungen

Die Einstellungen enthalten einen eigenen Reiter für die Fehlersuche:

1. Öffne den Drawer über den Hamburger-Knopf oben links und klicke im Fuß des Drawers auf das Zahnrad („Einstellungen“).
2. Wähle links den Reiter „Technische Details“.
3. Oben steht, welche Konfigurationsdatei geladen ist („Konfiguration: …“), wo das Protokoll liegt („Protokoll: …“) und welche Java-Version läuft („Java: …“).
4. Darunter siehst du das Ende der Protokolldatei (die letzten 64 KB). „Aktualisieren“ liest sie neu ein, zum Beispiel direkt nachdem ein Fehler aufgetreten ist.

In der Leiste darüber findest du außerdem:

- „Einrichtungsassistent erneut starten“: Merlin führt dich noch einmal durch KeePass, KI-Dienst, Modelle und Confluence (siehe [Einrichtung](help:troubleshooting.setup)).
- „Konfiguration prüfen“: prüft die Angaben des Dialogs, ohne zu speichern. Ergebnis ist „Konfiguration in Ordnung.“ oder „Konfiguration unvollständig:“ mit einer Liste der Probleme.
- „Verbindung testen“: derselbe Verbindungstest wie im Reiter „KI-Dienst“, hier als Textzeilen „OK – …“ bzw. „Fehler – …“ und am Ende „Verbindung in Ordnung.“ oder „Verbindungstest fehlgeschlagen.“.
- „Einstellungsordner öffnen“: öffnet den Ordner mit Konfiguration und Protokollen im Dateimanager. Kann das System keinen Ordner öffnen, zeigt ein Hinweis den Pfad zum Kopieren.

## Die Protokolldatei

Alles, was in der Oberfläche nur kurz erscheint, steht ausführlich im Protokoll:

- Ort: `<Anwendungsverzeichnis>/logs/enterprise-ai-client.0.log`. Das Anwendungsverzeichnis ist unter Windows `%APPDATA%\.enterprise-ai-client\`, unter Linux und macOS `~/.enterprise-ai-client/`.
- Das Protokoll rolliert: höchstens drei Dateien (`enterprise-ai-client.0.log` bis `.2.log`) mit je etwa 2 MB. Die neueste ist immer die mit der `0`.
- Es wird ab der Stufe INFO geschrieben, in UTF-8.

Was du dort findest:

- den Start mit Java-Version und Betriebssystem („Enterprise AI Client startet: Java …“),
- die geladene Konfiguration ohne Secrets („Konfiguration aus …“),
- die Proxy-Route je Ziel („Proxy-Route … (Modus): …“),
- jeden fehlgeschlagenen Chat-Aufruf mit vollständigem Stacktrace („Chat-Anfrage fehlgeschlagen (Art): …“),
- automatische Wiederholungen („Modellanfrage vorübergehend gescheitert (…); Wiederholung 1 von 2 in 500 ms“),
- fehlgeschlagene Zugriffe auf Wissensquellen und je nicht indexierbarer Seite den Grund,
- die Zeilen der Abschlussprüfung des Einrichtungsassistenten („Einrichtung: …“),
- Diagnosezeilen der Sprachausgabe („TTS request: …“) und der Spracheingabe („Spracheingabe: … ms Audio in … ms erkannt“).

API-Keys, Passwörter, Einmalcodes und Cookie-Werte stehen nie im Protokoll. Für Confluence legt der Client bei bestimmten Fehlern zusätzlich bis zu fünf Dateien `confluence-failed-download-<Zeitstempel>.html` im selben Ordner ab (siehe [Confluence](help:troubleshooting.confluence)); darin sind die Werte von Eingabefeldern durch `***` ersetzt.

### So findest du die passende Stelle

1. Notiere dir die Uhrzeit, zu der der Fehler auftrat.
2. Öffne „Technische Details“ und klicke „Aktualisieren“, oder öffne `enterprise-ai-client.0.log` in einem Texteditor.
3. Suche nach `SEVERE` und `WARNING` in der Nähe dieser Uhrzeit, oder nach einem Stichwort aus der Fehlermeldung (zum Beispiel „Chat-Anfrage fehlgeschlagen“, „Indexierung von“, „KeePass“, „[Confluence]“, „Vorlesen“).
4. Die Zeilen direkt davor zeigen oft die Ursache, etwa eine Proxy-Route oder einen KeePass-Fehler.

## Konfiguration prüfen

Viele Probleme kommen von einer unvollständigen oder ungültigen Einstellung. So prüfst du sie:

1. Öffne die Einstellungen (Zahnrad) und dort „Technische Details“ → „Konfiguration prüfen“. Ohne Befund steht dort „Konfiguration in Ordnung.“.
2. Meldet die Prüfung Probleme, nennt jede Zeile das betroffene Feld und meist den Schlüssel der Datei, zum Beispiel „embedding.dimension“ mit „fehlt (Pflichtangabe, z. B. 768)“. Werte werden nie angezeigt.
3. Beim Klick auf „Speichern“ läuft dieselbe Prüfung. Gespeichert wird nur ohne Probleme; der betroffene Reiter wird dann gewählt, und unten erscheint eine rote Platte mit den Problemen.
4. Teste danach die Verbindung: Reiter „KI-Dienst“ → „Verbindung testen“. Jeder Schritt erscheint einzeln mit ✓ (in Ordnung), ! (Hinweis), ✗ (Fehler) oder – (Information). Der rote Schritt nennt die Ursache.

Gespeicherte Änderungen gelten sofort, ohne Neustart. Einzige Ausnahme ist das Häkchen „Prefer IPv6 (needs restart; use when IPv4 egress is broken)“ im Reiter „Netzwerk & Agent“, das erst nach einem Neustart wirkt.

Startet das Programm mit einer fehlerhaften Datei, öffnet sich statt des Hauptfensters der Einstellungen-Dialog mit den Problemen. Brichst du ihn ab („Beenden“ bzw. „Abbrechen“), beendet sich das Programm; im Protokoll steht dann „Die Konfiguration unter … wurde nicht korrigiert.“.

### Für Fortgeschrittene: die Datei selbst

Die Konfiguration steht in `enterprise-ai-client.properties` im Anwendungsverzeichnis. Sie enthält keine Secrets; API-Key und Passwörter liegen ausschließlich in KeePass. Du kannst die Datei mit einem Texteditor öffnen. Der Einstellungen-Dialog ändert beim Speichern nur seine eigenen Schlüssel und lässt Kommentare und Feineinstellungen stehen. Unbekannte Schlüssel erzeugen beim Laden nur eine Warnung. Mehr dazu unter [Konfigurationsdatei](help:settings.config-file).

## Was du bei einer Supportanfrage angibst

Je genauer die Angaben, desto schneller die Hilfe. Gib an:

1. **Was du getan hast**: welche Aktion (Frage gesendet, „Verbindung testen“, Quelle indexiert, Vorlesen gestartet …) und ob der Fehler jedes Mal auftritt.
2. **Die Überschrift der Meldung wörtlich**, zum Beispiel „Der KI-Dienst ist nicht erreichbar.“, dazu die aufgeklappten Zeilen „Technische Ursache:“ und „Hinweis:“.
3. **Das Ergebnis von „Verbindung testen“**: alle Zeilen, besonders die rote.
4. **Die Programmversion**: Sie steht im Dateinamen des Programms (`enterprise-ai-client-<Version>.jar`). Die Java-Version steht oben in „Technische Details“.
5. **Den passenden Ausschnitt aus dem Protokoll**: einige Zeilen vor und nach dem Fehler, mit Uhrzeit.

### Was du nie mitschickst

- keinen API-Key, kein Passwort, keinen Personal Access Token, keinen Einmalcode,
- nicht die KeePass-Datenbank und nicht die Datei `keepassrpc-pairing.key` aus dem Anwendungsverzeichnis,
- keinen Screenshot des Reiters „KeePass“ mit eingeblendeten Passwörtern („Alle Passwörter anzeigen“ vorher abschalten).

Das Protokoll und die Konfigurationsdatei enthalten von sich aus keine Secrets. Sie können aber interne Hostnamen, Benutzernamen, Seitentitel oder Pfade enthalten. Lies den Ausschnitt vor dem Versenden durch und ersetze Interna, wenn das in deinem Haus nötig ist (zum Beispiel `confluence.example.org` statt des echten Hosts).

## Häufige erste Schritte

Bevor du tiefer suchst, helfen oft diese Prüfungen:

1. **Läuft KeePass, ist die Datenbank entsperrt?** Fast alle Zugriffe holen den API-Key bei jeder Anfrage neu aus KeePass.
2. **Verbindung testen** im Reiter „KI-Dienst“ ausführen und den ersten roten Schritt lesen.
3. **Erneut senden**: Viele Fehler sind vorübergehend. Der Client wiederholt Verbindungsfehler, Zeitüberschreitungen, HTTP 429 und 5xx bereits automatisch (siehe [Netzwerk](help:troubleshooting.network)).
4. **Einrichtungsassistent erneut starten**, wenn du nicht sicher bist, ob KeePass-Eintrag, Modelle und Dimension noch stimmen.
5. **Protokoll lesen**, wenn die Meldung in der Oberfläche auf „(Protokoll)“ verweist, zum Beispiel „Nicht lesbar (Protokoll).“.

## Grenzen

- Der Client ist gegen lokale Testserver geprüft. Das Verhalten gegen echte Server (Enterprise-API, Confluence, KeePass, Firmen-Proxy) ist nicht für alle Installationen geprüft; manche Fehlerbilder hängen vom Server ab.
- Nicht jeder Fehler hat einen Hinweis. Ohne Hinweis bleiben „Technische Ursache“ und das Protokoll die wichtigsten Quellen.
- Das Protokoll ist auf drei Dateien begrenzt. Bei viel Betrieb sind ältere Einträge schnell überschrieben; sichere den Ausschnitt deshalb bald nach dem Fehler.
