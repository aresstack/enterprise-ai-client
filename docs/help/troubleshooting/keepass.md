---
id: troubleshooting.keepass
title: Probleme mit KeePass
keywords: KeePass, KeePassRPC, Passwortmanager, Tresor, nicht erreichbar, gesperrt, entsperren, Pairing, Kopplung, koppeln, Einmal-Passwort, Authorise a new connection, Pairing-Schlüssel, keepassrpc-pairing.key, Eintrag fehlt, Titel, Passwortfeld leer, API-Key fehlt, URL fehlt, Feld fehlt, Zugriff abgelehnt, widerrufen, Wiederholungen, Timeout, In KeePass prüfen, secret, password manager, pairing failed
---

# Probleme mit KeePass

Der Enterprise AI Client speichert keine Secrets selbst. API-Key, Zugangsdaten für Wissensquellen, Proxy-Anmeldung und Zertifikatspasswörter holt er bei jedem Zugriff aus KeePass 2.x über das Plugin KeePassRPC. Funktioniert diese Verbindung nicht, scheitern Chat, Modellabfrage, Indexierung und Confluence gleichermaßen. Dieses Kapitel hilft, wenn KeePass nicht erreichbar ist, die Kopplung (das Pairing) nicht klappt, ein Eintrag oder ein Feld fehlt oder KeePass nur zeitweise antwortet. Wie du KeePass grundsätzlich einrichtest, steht in [KeePass verbinden](help:getting-started.keepass) und [KeePass einstellen](help:settings.keepass).

## So arbeitet der Client mit KeePass

- KeePassRPC lauscht nur lokal, standardmäßig auf `127.0.0.1`, Port `12546`.
- Beim ersten Zugriff koppelt sich der Client mit KeePass. KeePass zeigt dazu ein Fenster „Authorise a new connection“ mit einem Einmal-Passwort; der Client öffnet den Dialog „KeePass verbinden“ (Überschrift „Pairing mit KeePass“), in den du das Passwort unter „Einmal-Passwort aus KeePass:“ einträgst und mit „Verbinden“ bestätigst.
- Danach legt der Client einen Pairing-Schlüssel ab, standardmäßig dauerhaft in der Datei `keepassrpc-pairing.key` im Anwendungsverzeichnis (nur für dich lesbar). Ein erneutes Pairing ist erst nötig, wenn du die Verbindung in KeePass widerrufst oder die Datei löschst.
- Es gibt keinen Zwischenspeicher für Secrets. Jede Chat-Anfrage, jede Embedding-Anfrage und jeder Quellenzugriff holt das Secret neu. KeePass muss deshalb die ganze Zeit laufen und entsperrt sein.
- Ein Eintrag wird nur über seinen **Titel** gefunden, exakt und mit Groß- und Kleinschreibung. Gesucht wird nicht über URL oder Benutzernamen.

Welche Felder eines Eintrags gebraucht werden:

| Verwendung | Felder |
|---|---|
| KI-Dienst (API-Key) | Passwortfeld = API-Key (ohne „Bearer“), URL-Feld = Basis-URL des KI-Dienstes |
| Confluence | URL, Benutzername und Passwort; ohne Benutzername gilt das Passwortfeld als persönliches Zugriffstoken |
| MediaWiki und andere Quellen | Benutzername und Passwort |
| Proxy-Anmeldung BASIC | Benutzername und Passwort |

## KeePass ist nicht erreichbar

Typische Meldungen, wörtlich:

- „KeePass ist unter 127.0.0.1:12546 nicht erreichbar. Läuft KeePass mit dem Plugin KeePassRPC, und ist die Datenbank entsperrt?“ (bei „In KeePass prüfen“; Adresse je nach Einstellung)
- „KeePass ist nicht erreichbar. Ist KeePass mit dem Plugin KeePassRPC gestartet und die Datenbank geöffnet?“ (Passwortliste, Einrichtungsassistent)
- „KeePass ist nicht gekoppelt: … KeePass muss laufen, das Plugin KeePassRPC muss installiert sein, und der Pairing-Schlüssel muss bestätigt werden.“ (Einrichtungsassistent)
- Im Chat: „Der API-Key konnte nach mehreren Versuchen nicht aus KeePass gelesen werden. KeePassRPC war vorübergehend nicht verfügbar.“

Prüfschritte:

1. Läuft KeePass 2.x? Ist die richtige Datenbank geöffnet und **entsperrt**? Eine gesperrte Datenbank (etwa nach Bildschirmsperre oder Zeitablauf) zählt als nicht erreichbar.
2. Ist das Plugin KeePassRPC installiert und aktiv? Ohne Plugin lauscht nichts auf dem Port.
3. Öffne Einstellungen → „KeePass“. Ist „KeePassRPC verwenden“ angehakt? Stimmen „Host“ (`127.0.0.1`) und „Port“ (`12546`, wie in den Einstellungen des KeePassRPC-Plugins)?
4. Klicke „In KeePass prüfen“. Das Ergebnis nennt den Grund in Klartext.
5. Bleibt es dabei, sieh im Protokoll nach Zeilen mit „KeePass“.

Ist „KeePassRPC verwenden“ abgeschaltet, startet die Anwendung ohne Secrets. Dann meldet „In KeePass prüfen“ „KeePassRPC ist ausgeschaltet; ohne KeePass gibt es keinen API-Key.“, und jede Anfrage, die ein Secret braucht, scheitert mit „Anmeldung am KI-Dienst fehlgeschlagen.“.

### Beim Start: Basis-URL nicht lesbar

Steht die Basis-URL des KI-Dienstes nur im KeePass-Eintrag, braucht schon der Start KeePass. Läuft KeePass dann nicht, öffnet sich der Einstellungen-Dialog mit dem Problem „Basis-URL des KI-Dienstes: aus dem KeePass-Eintrag „…“ nicht lesbar (…). Läuft KeePass mit KeePassRPC? Sonst die URL unter Einstellungen → KI-Dienst → Erweitert eintragen.“. Starte KeePass, entsperre die Datenbank und speichere den Dialog erneut, oder trage die Basis-URL unter „▸ Erweitert“ im Feld „Basis-URL (sonst aus KeePass)“ ein.

## Die Kopplung (Pairing) klappt nicht

1. **Kein Fenster in KeePass?** Das Fenster „Authorise a new connection“ erscheint nur, wenn KeePassRPC die Verbindung annimmt. Prüfe die Schritte unter [KeePass ist nicht erreichbar](help:troubleshooting.keepass#keepass-ist-nicht-erreichbar). KeePass zeigt im Fenster den Namen aus dem Feld „Anzeigename beim Pairing“ (Standard „Enterprise AI Client“).
2. **Einmal-Passwort abgetippt, aber nichts passiert?** Gib das Passwort genau so ein, wie KeePass es zeigt, und klicke „Verbinden“. Ein Klick auf „Abbrechen“ führt zu „Das Pairing wurde abgebrochen.“ bzw. „Das KeePassRPC-Pairing wurde abgebrochen.“.
3. **„KeePass lehnt den Zugriff ab. Das Pairing ist ungültig oder wurde in KeePass widerrufen; bitte die Verbindung in KeePassRPC entfernen und erneut prüfen.“** Der gespeicherte Schlüssel passt nicht mehr. Entferne in den KeePassRPC-Optionen von KeePass die Verbindung des Clients und klicke danach erneut „In KeePass prüfen“ bzw. „Mit KeePass verbinden“. Der Client verwirft einen abgelehnten Schlüssel selbst und koppelt genau einmal neu.
4. **Muss ich bei jedem Start neu koppeln?** Dann steht unter „Pairing-Schlüssel“ „Nur im Speicher (bei jedem Start neu pairen)“. Stelle auf „Datei im Anwendungsverzeichnis (dauerhaft)“ und speichere. Der Einrichtungsassistent stellt immer „dauerhaft“ ein.
5. **Kopplung komplett neu beginnen:** Programm beenden, `keepassrpc-pairing.key` im Anwendungsverzeichnis löschen, in KeePass die Verbindung entfernen, Programm starten und neu koppeln.

Hinweis: KeePassRPC lehnt Verbindungen ohne erlaubten Absender (Origin) stillschweigend ab. Der Client meldet sich mit dem Origin `chrome-extension://enterpriseaiclient` an. Ändere den Schlüssel `security.keepass.origin` nur, wenn dein KeePassRPC anders eingestellt ist. Der Handschlag mit einem echten KeePassRPC-Plugin ist nicht in allen Umgebungen geprüft.

## Der Eintrag fehlt

Meldungen:

- „Kein KeePass-Eintrag mit dem Titel „…“. Der Titel muss genau übereinstimmen.“
- „Kein KeePass-Eintrag mit diesem Titel.“
- Im Assistenten: „Der Eintrag „…“ fehlt; bitte Basis-URL und API-Key angeben, er wird in KeePass angelegt.“
- In der Abschlussprüfung: „kein KeePass-Eintrag für den KI-Dienst“

Prüfschritte:

1. Welcher Titel ist eingestellt? Einstellungen → „KI-Dienst“ → „KeePass-Eintrag“. Für Quellen steht er im Dialog der Quelle unter „KeePass-Eintrag“.
2. Vergleiche ihn Zeichen für Zeichen mit dem Titel in KeePass: Leerzeichen am Ende, Bindestriche, Groß- und Kleinschreibung. Das Präfix `keepass:` in der Konfigurationsdatei gehört nicht zum Titel.
3. Liegt der Eintrag in einer anderen, gerade nicht geöffneten Datenbank?
4. Lege den Eintrag bei Bedarf direkt im Client an: Einstellungen → „KeePass“ → Bereich „Passwörter“ → „Neu“, oder im Einrichtungsassistenten mit „In KeePass speichern“.

Der Assistent wählt beim KI-Dienst den eingestellten Titel, sonst einen eindeutigen Titel, der „Enterprise AI“ enthält, sonst „Enterprise AI API“. Gibt es mehrere passende Einträge, wählt er keinen davon automatisch.

## Ein Feld im Eintrag fehlt

### Passwortfeld (API-Key) leer

- „Eintrag „…“ gefunden, aber das Passwortfeld ist leer. Bitte den API-Key dort eintragen.“ (In KeePass prüfen, Abschlussprüfung)
- Beim Verbindungstest: „API-Key: Eintrag „…“ gefunden, aber das Passwortfeld ist leer; der Aufruf geht ohne API-Key weiter.“

Trage den API-Key ins Passwortfeld ein, nur den Key selbst, ohne „Bearer“ und ohne Leerzeichen davor oder dahinter. Lehnt der Dienst den Key danach ab, meldet „Verbindung testen“ „GET /models: HTTP 401: Der Dienst lehnt den API-Key ab. Passwortfeld des KeePass-Eintrags prüfen (vollständiger Key, ohne Leerzeichen).“.

### URL-Feld leer

Fehlt die Basis-URL im Eintrag, zeigt der Assistent eine Zeile wie „API-Key: vorhanden.  Basis-URL: fehlt.“, meldet „Im KeePass-Eintrag fehlen noch API-Key oder Basis-URL.“ und bietet das Feld „Basis-URL“ an; „In KeePass speichern“ schreibt sie in den Eintrag, Zusatzfelder bleiben erhalten. Beim Start lautet das Problem „Basis-URL des KI-Dienstes: Der KeePass-Eintrag „…“ hat keine URL. Bitte dort eintragen oder unter Einstellungen → KI-Dienst → Erweitert angeben.“.

### Confluence: Passwort, Benutzer oder URL fehlt

Der Assistent zeigt den Stand ohne Secrets, zum Beispiel „URL fehlt: bitte unten eintragen, sie wird im KeePass-Eintrag gespeichert“ oder „Passwort bzw. Token fehlt: …“, und fragt nur das Fehlende ab. „Kein Benutzername: Anmeldung mit persönlichem Zugriffstoken“ ist kein Fehler: Ohne Benutzername meldet sich der Client mit dem Passwortfeld als Token an. Details: [Confluence](help:troubleshooting.confluence).

## KeePass antwortet nur manchmal: Wiederholungen

Antwortet KeePassRPC kurzzeitig nicht (Zeitüberschreitung, kurz abgebrochene Verbindung), wiederholt der Client den Lesezugriff automatisch. Standard sind zwei Wiederholungen, die erste nach 300 ms, die zweite nach 750 ms. Nie wiederholt werden ein fehlender Eintrag, ein abgelehnter Zugriff und ein abgebrochenes Pairing; eine Wiederholung löst auch kein neues Pairing aus. Schreibende Zugriffe (Eintrag speichern, löschen) laufen genau einmal.

Erst wenn alle Versuche scheitern, erscheint im Chat „Der API-Key konnte nach mehreren Versuchen nicht aus KeePass gelesen werden. KeePassRPC war vorübergehend nicht verfügbar.“ mit dem Hinweis „KeePassRPC hat auch nach den eingestellten Wiederholungen nicht geantwortet; der API-Key selbst ist nicht als falsch erkannt. Läuft KeePass, ist die Datenbank entsperrt? Dann die Frage erneut senden. …“. Diese Fehler werden vom Chat selbst nicht noch einmal wiederholt.

So passt du die Wiederholungen an:

1. Einstellungen → „Netzwerk & Agent“ → Bereich „Zeitlimits und Wiederholungen“.
2. „KeePassRPC: Timeout (ms)“ (Standard 15000), „KeePassRPC: Wiederholungen“ (0–10, Standard 2), „KeePassRPC: Pause vor Wiederholung (ms)“ (0–30000, Standard 300). Jede weitere Pause ist 2,5-mal so lang wie die vorige.
3. Speichern. Die Werte gelten ohne Neustart.

Im ungünstigsten Fall wartet ein Zugriff bis zu (Wiederholungen + 1) × Timeout. Bei großen Indexierungen ist das spürbar, weil jede Anfrage KeePass fragt.

## Für Fortgeschrittene

Die Schlüssel in der Konfigurationsdatei: `security.keepass.enabled`, `security.keepass.host`, `security.keepass.port`, `security.keepass.timeoutMillis`, `security.keepass.retryCount`, `security.keepass.retryDelayMillis`, `security.keepass.setupRecheckCount`, `security.keepass.setupRecheckDelayMillis` (Wiederprüfung im Einrichtungsassistenten nach einem kurzen Ausfall), `security.keepass.pairingKeyStore` (`file` oder `memory`) und `security.keepass.pairingKeyFile`. Die Referenz auf einen Eintrag ist sein Titel, optional mit Präfix `keepass:`, etwa `chat.apiKeyRef=keepass:Enterprise AI API`.

## Grenzen

- Den API-Key kannst du im Einstellungen-Dialog nicht eintippen; er kommt ausschließlich aus KeePass. Der Einrichtungsassistent und den Bereich „Passwörter“ schreiben ihn direkt in den KeePass-Eintrag.
- Eine Windows-integrierte Anmeldung ersetzt KeePass nicht.
- Pairing über ein echtes KeePassRPC-Plugin ist nicht in allen Umgebungen geprüft.
