---
id: troubleshooting.confluence
title: Probleme mit Confluence
keywords: Confluence, Data Center, Anhänge fehlen, Anhang, Bild, PDF-Anhang, Zugriff verweigert, HTTP 401, HTTP 403, Weiterleitung, nicht angemeldet, Einmalcode, OTP, Zwei-Faktor, Zusätzliche Anmeldung erforderlich, Code erneut senden, Zertifikat, Client-Zertifikat, mTLS, Windows-Zertifikatsspeicher, Spaces, Token, persönliches Zugriffstoken, confluence-failed-download
---

# Probleme mit Confluence

Confluence Data Center ist eine Live-Quelle: Der Client fragt es bei jeder Frage über die REST-API ab und übernimmt die gefundenen Seiten in den Index. Dieses Kapitel hilft, wenn die Verbindung grundsätzlich steht, aber Anhänge oder Bilder fehlen, wenn Confluence den Zugriff verweigert, wenn ein Einmalcode (OTP) verlangt wird oder wenn ein Client-Zertifikat Probleme macht. Das Einrichten beschreiben [Confluence anbinden](help:getting-started.confluence) und [Confluence als Wissensquelle](help:knowledge.confluence). Unterstützt wird nur Confluence Data Center bzw. Server, nicht Confluence Cloud.

## Zuerst: Verbindung prüfen

Der Einrichtungsassistent und die Passwortverwaltung prüfen einen Confluence-Eintrag, indem sie die Liste der Spaces abrufen. Das Ergebnis lautet entweder „Verbindung erfolgreich: n Spaces sichtbar.“ (mit Zertifikat: „Verbindung erfolgreich (Zertifikat „…“): n Spaces sichtbar.“) oder „Verbindung fehlgeschlagen: Spaces: …“ mit dem Grund.

Die Fehlermeldungen der Quelle beginnen immer mit dem angefragten Teil (zum Beispiel „Spaces“, eine Seite oder ein Anhang), dann folgen ein Doppelpunkt und der Grund. In der Tabelle steht dafür „…“:

| Meldung | Bedeutung |
|---|---|
| „…: Zugriff verweigert (HTTP 401)“ / „(HTTP 403)“ | Anmeldung falsch oder Rechte fehlen |
| „…: Weiterleitung (HTTP 3xx), vermutlich nicht angemeldet“ | Confluence schickt auf die Anmeldeseite |
| „…: nicht gefunden (HTTP 404)“ | Seite oder Anhang existiert nicht (mehr) oder Basis-URL falsch |
| „…: Confluence antwortet mit HTTP 429“ / „HTTP 5xx“ | überlastet; wird automatisch wiederholt |
| „…: Confluence nicht erreichbar (…)“ | Netz, Proxy oder Zertifikat |
| „…: unerwartete Antwort HTTP n“ | sonstiger Status |
| „Anmeldedaten für Confluence-Quelle … nicht verfügbar (…)“ | KeePass liefert den Eintrag nicht |

Bei „nicht erreichbar“ hilft [Netzwerk](help:troubleshooting.network), bei „Anmeldedaten … nicht verfügbar“ [KeePass](help:troubleshooting.keepass).

## Verbindung ok, aber Anhänge fehlen

Seiten werden gefunden, aber Anhänge (PDF, Office, Bilder) tauchen in Antworten nicht auf oder lassen sich nicht öffnen. Dafür gibt es mehrere Gründe.

### Anhänge werden standardmäßig nicht indexiert

Die Quelle nimmt Anhänge nur mit, wenn das in der Konfiguration eingeschaltet ist (Standard: aus). Auch dann gelangen nur Textformate in den Index, also Text, JSON und XML, bis zu 5 MB je Anhang. Für PDF- und Office-Anhänge steht im Protokoll „Anhang … hat keinen Textinhalt (…)“; sie werden also nicht durchsucht. Im Leser kannst du sie trotzdem öffnen.

### Die Datei braucht eine eigene Anmeldung

Viele Confluence-Installationen schützen Dateien (Anhänge, Bilder) strenger als die REST-API, etwa mit einem Einmalcode. Dann ist die REST-Verbindung in Ordnung, der Download liefert aber eine Anmeldeseite statt der Datei. Erkennungszeichen:

- Im Leser: „Für dieses Bild ist eine zusätzliche Anmeldung (Einmalcode) erforderlich.“ oder das Panel „Zusätzliche Anmeldung erforderlich“, siehe [unten](help:troubleshooting.confluence#ein-einmalcode-otp-wird-verlangt).
- Im Protokoll: „[Confluence] …: HTML statt Datei untersucht: status=…“.
- Im Protokollverzeichnis liegen Dateien „confluence-failed-download-….html“. Sie enthalten die Seite, die Confluence statt der Datei geschickt hat, plus Status, Adresse ohne Parameter, Weiterleitungen und die Namen (nicht die Werte) der gesendeten Anmeldeangaben. Pro Programmlauf werden höchstens fünf solche Dateien geschrieben.

Prüfe diese HTML-Datei, bevor du sie weitergibst: Sie kann Namen oder interne Adressen enthalten.

### draw.io-Diagramme

Bei draw.io-Zeichnungen zeigt der Leser standardmäßig „Gerendert“, also die Zeichnung aus ihren Diagrammdaten, ohne Dateidownload. „Original“ zeigt die PNG-Vorschau aus Confluence; das ist ein Dateidownload und kann einen Einmalcode verlangen. Dann heißt die Schaltfläche „Original (Anmeldung nötig)“, bei anderen Problemen „Original (nicht ladbar)“. „Zurück zu Gerendert“ wechselt zurück. Fehlt dir nur die Original-Vorschau, bleib einfach bei „Gerendert“.

### Bilder sind zu groß

Bilder über der Größengrenze für Anhänge werden nicht geladen; der Grund lautet dann „Bild größer als … Bytes (Grenze maxAttachmentBytes)“.

## Zugriff verweigert

1. **Welcher Eintrag?** Im Dialog der Quelle steht unter „KeePass-Eintrag“ der Titel. Leer bedeutet anonymen Zugriff; viele Installationen zeigen anonym nichts.
2. **Benutzername oder Token?** Steht im KeePass-Eintrag ein Benutzername, meldet sich der Client mit Benutzername und Passwort an (Basic). Ohne Benutzername nutzt er das Passwortfeld als persönliches Zugriffstoken. Ein Token mit eingetragenem Benutzernamen führt deshalb zu 401.
3. **Passwort geändert?** Ein abgelaufenes Passwort oder Token zeigt sich als 401. Neues Passwort in KeePass eintragen; der Client liest es beim nächsten Zugriff ohne Neustart.
4. **Rechte**: 403 bei einzelnen Seiten oder Spaces heißt meist, dass dein Konto sie nicht sehen darf. Das ist kein Fehler des Clients. Im Leser steht dann „Zugriff verweigert.“
5. **Weiterleitung (3xx)**: Oft verlangt ein vorgeschaltetes Anmeldesystem eine Browser-Anmeldung, die die REST-API nicht kann. Prüfe, ob die „Basis-URL“ stimmt (ohne „/rest“, mit „https“) und ob eine Anmeldung per Zertifikat nötig ist.
6. **https erzwungen**: Mit KeePass-Eintrag sendet der Client Zugangsdaten nur über https. Bei einer „http“-Adresse meldet die Konfigurationsprüfung „Confluence-Einstellung ungültig (mit KeePass-Eintrag nur https, Space-Schlüssel prüfen)“.

Nach 401 oder 403 wiederholt der Client nicht automatisch; er hält sich an die Regel, dass falsche Zugangsdaten nie wiederholt werden.

## Ein Einmalcode (OTP) wird verlangt

Manche Installationen verlangen zusätzlich einen Einmalcode per E-Mail, meist nur für Dateien. Der Client erkennt die Einmalcode-Seite und zeigt im Leser das Panel „Zusätzliche Anmeldung erforderlich“:

1. Confluence schickt den Code; das Panel zeigt „Ein Einmalcode wurde per E-Mail versendet.“ oder den Text von Confluence.
2. Code im Feld „OTP-Code:“ eingeben und „Bestätigen“ klicken.
3. Wird der Code abgelehnt: „Der Code wurde nicht angenommen.“ Code prüfen oder „Code erneut senden“ klicken (während des Sendens „Wird gesendet …“).
4. Nach erfolgreicher Bestätigung lädt der Leser die Datei neu. Weitere Dateien laden ohne neue Abfrage, solange die Sitzung gilt. Läuft sie ab, steht im Protokoll „Einmalcode-Sitzung abgelaufen, neue Bestätigung nötig“.

Mögliche Fehler:

- „Confluence verlangt einen Einmalcode (OTP), die Seite hat aber kein bekanntes Formular dafür (…)“: Die Einmalcode-Seite deiner Installation hat ein Format, das der Client nicht kennt. Dateien sind dann über den Client nicht erreichbar.
- „Diese Einmalcode-Seite kann keinen neuen Code senden“: „Code erneut senden“ geht hier nicht; den zuletzt erhaltenen Code verwenden.
- „Neuer Einmalcode nicht angefordert: …“, „Einmalcode nicht gesendet (…)“, „Senden fehlgeschlagen (Protokoll).“: Senden hat nicht geklappt; Protokoll lesen.

Wichtig:

- Die Indexierung und die Suche im Hintergrund fragen nie nach einem Code. Dateien hinter einem Einmalcode fehlen dort einfach.
- Die Sitzung lebt nur im Arbeitsspeicher. Nach einem Neustart wird der Code erneut verlangt.

Hinweis: Das Einmalcode-Verfahren ist nicht für alle Confluence-Installationen geprüft. Mehr zur Bedienung in [Einmalcodes](help:knowledge.otp).

## Zertifikate

### Server-Zertifikat

Lehnt Java das Zertifikat des Confluence-Servers ab, gilt dasselbe wie beim KI-Dienst: Windows-Zertifikatsspeicher nutzen oder eine CA-Datei eintragen, siehe [Netzwerk](help:troubleshooting.network).

### Client-Zertifikat (mTLS)

Verlangt Confluence eine Anmeldung per Zertifikat, wählst du es im Assistenten mit „Zertifikat wählen…“ bzw. im Dialog der Quelle unter „Zertifikat“ aus dem Windows-Zertifikatsspeicher („Zertifikat aus dem Windows-Zertifikatsspeicher auswählen:“). Abgelaufene Zertifikate sind ausgeblendet; „Auch abgelaufene Zertifikate anzeigen“ blendet sie ein. „Ohne Zertifikat fortfahren“ verzichtet darauf.

Fehler und Prüfschritte:

1. „Windows-Zertifikatsspeicher nicht lesbar“: Der Speicher ist nur unter Windows verfügbar. Unter anderen Systemen geht das nur über eine Schlüsseldatei in der Konfigurationsdatei.
2. „keine Zertifikatskette für Alias '…'“: Das gewählte Zertifikat ist nicht mehr im Speicher oder hat keinen privaten Schlüssel. Neu auswählen.
3. „KeePass-Eintrag für das Client-Zertifikat nicht lesbar: …“: Bei einer Schlüsseldatei liegt deren Passwort in KeePass; den Eintrag prüfen.
4. Die Konfigurationsprüfung meldet „fehlt, obwohl ein Client-Zertifikat konfiguriert ist“: Zu einer Schlüsseldatei fehlt der KeePass-Eintrag für ihr Passwort.
5. Im Protokoll zeigt „[Confluence] …: Kein Zertifikat konfiguriert — ohne mTLS“, dass die Quelle ohne Zertifikat arbeitet.

Den Alias des Zertifikats kann der Assistent im KeePass-Eintrag ablegen (Zusatzfeld), damit er ihn beim nächsten Mal vorschlägt.

## Grenzen

- Nur Confluence Data Center bzw. Server, kein Confluence Cloud.
- PDF- und Office-Anhänge werden nicht durchsucht, nur im Leser angezeigt.
- Kein Einmalcode im Hintergrund; die OTP-Sitzung überlebt keinen Neustart.
- Hinweis: Das Verhalten gegenüber konkreten Confluence-Installationen (Anmeldesysteme, Einmalcode-Seiten) ist nicht für alle Installationen geprüft.
