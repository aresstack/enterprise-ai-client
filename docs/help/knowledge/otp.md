---
id: knowledge.otp
title: Einmalcode (OTP) bei geschützten Dateien
keywords: Einmalcode, OTP, OTP-Code, One-Time-Code, Zwei-Faktor, 2FA, zweiter Faktor, zusätzliche Anmeldung, Zusätzliche Anmeldung erforderlich, Code erneut senden, Bestätigen, E-Mail-Code, Confluence, Anhang, Bild, Download, draw.io, Original, Gerendert, Sitzung, Cookie, Sprachmodell, Datenschutz, one-time password, two-factor, second factor
---

# Einmalcode (OTP) bei geschützten Dateien

Manche Confluence-Server verlangen beim Herunterladen von Dateien eine zusätzliche Anmeldung mit einem Einmalcode, der meist per E-Mail kommt. Dieses Kapitel beschreibt genau, wann der Enterprise AI Client nach diesem Code fragt, wo die Abfrage erscheint, was mit dem Code geschieht und warum er nie an das Sprachmodell geht. Es richtet sich an alle, die eine solche Confluence-Quelle nutzen. Die Einrichtung der Quelle steht in [Confluence als Wissensquelle](help:knowledge.confluence).

## Wann fragt der Client nach einem Einmalcode?

Nur in einem eng begrenzten Fall:

- **Nur bei Confluence-Quellen.** Andere Quelltypen kennen keine Einmalcode-Abfrage.
- **Nur beim Laden von Dateien.** Im Lesebereich zählen dazu Bilder in einer Seite und in der Diashow, Anhänge, Vorschaubilder von Office-Anhängen und die Ansicht „Original“ eines draw.io-Diagramms. Seit 0.49.0 kommt das Lesen eines Anhangs durch den Assistenten im Chat oder Agent-Modus hinzu ([siehe unten](help:knowledge.otp#einmalcode-im-chat)).
- **Nur wenn Confluence statt der Datei eine Seite zur Zwei-Faktor-Anmeldung liefert.** Ob und wann das passiert, legt dein Confluence-Server fest.

Alles andere fragt **nie** nach einem Code: die Suche, das Lesen von Seitentexten, die Indexierung und das Vorladen im Hintergrund. Diese Wege nutzen die Programmierschnittstelle von Confluence, für die die zusätzliche Anmeldung nicht gilt. Hinweis: Ob dein Server auch die Schnittstelle mit einem zweiten Faktor schützt, hängt vom Server ab; im aktuellen Stand ist dafür keine Abfrage vorgesehen.

## Einmalcode im Chat

Liest der Assistent einen Confluence-Anhang (Werkzeug `knowledge_read` mit der Kennung eines Anhangs) und verlangt der Server dafür einen Einmalcode, wartet der Werkzeugaufruf. Über dem Chat-Verlauf erscheint dieselbe Abfrage wie im Lesebereich, mit der Überschrift „Einmalcode für Dateianhänge“ und zusätzlich dem Knopf „Abbrechen“.

- Gibst du den Code ein und bestätigst, geht er direkt an Confluence, und der Client liest den Anhang einmal neu. Der Assistent arbeitet dann mit dem Inhalt weiter.
- Der Code geht nie an das Sprachmodell, nicht in den Verlauf, nicht in die Werkzeugargumente, nicht in einen Export und nicht ins Protokoll.
- „Abbrechen“ beendet die Abfrage. Der Assistent erfährt nur, dass der Anhang nicht gelesen werden konnte. Für dieselbe Quelle fragt der Client erst nach deiner nächsten eigenen Nachricht wieder.
- Scheitert das Senden, steht dort „Senden fehlgeschlagen (Protokoll).“; Details stehen im Protokoll, ohne den Code.

Hinweis: Die Abfrage im Chat ist neu in 0.49.0 und nicht gegen einen echten Confluence-Server mit Einmalcode geprüft.

## So sieht die Abfrage aus

Es öffnet sich kein eigenes Fenster. Die Abfrage erscheint **an der Stelle der Datei**, die du gerade öffnen wolltest, etwa in der Anhangsansicht oder der Diashow.

Sie zeigt:

- die Überschrift „Zusätzliche Anmeldung erforderlich“,
- den Hinweistext deines Servers oder, wenn er keinen mitliefert, „Ein Einmalcode wurde per E-Mail versendet.“, darunter in Klammern den Server,
- das Feld „OTP-Code:“, dessen Eingabe verdeckt angezeigt wird,
- den Knopf „Bestätigen“,
- den Knopf „Code erneut senden“, falls der Server diese Möglichkeit anbietet.

### Schritt für Schritt

1. Öffne den Anhang oder das Bild. Statt der Datei erscheint „Zusätzliche Anmeldung erforderlich“.
2. Hole den Code aus der E-Mail oder dem Kanal, den dein Server nutzt.
3. Tippe ihn in „OTP-Code:“ ein.
4. Klicke „Bestätigen“ oder drücke Enter. Der Knopf zeigt kurz „Wird gesendet …“.
5. Nimmt der Server den Code an, lädt der Client die Datei sofort und zeigt sie an.

Kam keine E-Mail, klicke „Code erneut senden“. Ist der Code falsch oder abgelaufen, erscheint der Hinweis deines Servers in Rot oder „Der Code wurde nicht angenommen.“; gib den neuen Code ein.

### Und danach?

Nach der Bestätigung hält der Client die Sitzung mit Confluence. Weitere Dateien derselben Quelle laden dann ohne erneute Abfrage, solange der Server die Sitzung gelten lässt. Läuft sie ab, erscheint die Abfrage beim nächsten Laden einer Datei wieder. Nach einem Neustart des Clients beginnt alles ohne Sitzung.

Bilder, die in einer Seite schon vor der Bestätigung angefordert wurden, können noch „Bild wird geladen …“ zeigen. Öffne die Seite in diesem Fall erneut.

## draw.io-Diagramme

Diagramme zeigt der Lesebereich zunächst als „Gerendert“. Dafür braucht es keinen Download und keinen Code. Nur wenn du ausdrücklich „Original“ wählst, lädt der Client das gespeicherte Vorschaubild; verlangt Confluence dafür einen Code, erscheint die Abfrage, und daneben der Knopf „Zurück zu Gerendert“.

- Ist die Sitzung schon bestätigt, zeigt der Client gleich das Original.
- Ist sie abgelaufen, fragt er nicht ungefragt nach, sondern bleibt bei „Gerendert“. Der Umschalter zeigt dann „Original (Anmeldung nötig)“.

## Was mit dem Code passiert

Belegbar ist im aktuellen Stand Folgendes:

- **Er geht nur an Confluence.** Der Client schickt den Code an die Adresse, die das Anmeldeformular deines Servers angibt, und nur, wenn sie zum selben Server gehört wie die eingerichtete Quelle. Er nutzt dabei dieselbe Verbindung wie für die Quelle: gleicher Proxy, gleiche Zertifikatsprüfung, gleiches Client-Zertifikat, gleiche Anmeldung.
- **Er geht nicht an das Sprachmodell.** Die Abfrage gehört zum Lesebereich, nicht zum Chat. Der Code wird nie Teil einer Nachricht, eines Prompts oder des Chatverlaufs und erreicht damit kein Sprachmodell, weder lokal noch in der Cloud.
- **Er wird nicht gespeichert.** Nach dem Senden leert der Client das Feld und überschreibt den Code im Speicher. Er landet weder in der Konfigurationsdatei noch in KeePass noch im Protokoll. Ins Protokoll schreibt der Client nur, dass ein Code bestätigt wurde oder nicht, ohne den Code selbst.
- **Die Sitzung bleibt im Speicher.** Die Sitzungscookies, die Confluence nach der Bestätigung setzt, hält der Client nur im Arbeitsspeicher und nur für diese eine Quelle.
- **Chat-Export.** Taucht ein Feld mit einem Namen wie „otp“ trotzdem in einem Chat auf, entfernt der Chat-Export seinen Wert ([Chat exportieren](help:chat.export)).

Wichtig: Tippe einen Einmalcode **nie in das Chat-Eingabefeld**. Was du dort schreibst, geht an das gewählte Sprachmodell. Mehr dazu in [Datenschutz](help:concepts.privacy) und [Sicherheit](help:concepts.security).

## Grenzen

- **Nur bekannte Formulare.** Der Client erkennt die Zwei-Faktor-Seite und liest daraus das Codefeld, versteckte Felder und den Bestätigen-Weg. Findet er kein passendes Formular, meldet die Quelle „Confluence verlangt einen Einmalcode (OTP), die Seite hat aber kein bekanntes Formular dafür“ und behandelt die Datei als „Zugriff verweigert“.
- **Keine automatische Wiederholung.** Bei einer Einmalcode-Anforderung wiederholt der Client den Abruf nie von selbst; es gibt nur den Weg über die Abfrage.
- **Nicht bei der Indexierung.** Läuft eine Indexierung oder Vorladen, wird nie nach einem Code gefragt.
- **Abweichende Verfahren.** Hinweis: Authenticator-Apps, Hardware-Token oder Single Sign-on mit eigener Anmeldeseite funktionieren nur, wenn der Server dafür dasselbe einfache Formular mit einem Codefeld anbietet. Das hängt vom Server ab.

## Typische Probleme

### Es kommt keine E-Mail

Prüfe den Spam-Ordner und klicke „Code erneut senden“, falls angeboten. Fehlt der Knopf, bietet der Server kein erneutes Senden an; schließe die Ansicht und öffne die Datei erneut.

### „Senden fehlgeschlagen (Protokoll).“ oder „Einmalcode nicht gesendet: …“

Der Code konnte Confluence nicht erreichen, etwa wegen Netz, Proxy oder abgelaufener Anmeldeseite. Öffne die Datei erneut, damit eine frische Abfrage erscheint. Netzwerkprüfungen: [Fehlersuche Netzwerk](help:troubleshooting.network).

### Bilder zeigen „Für dieses Bild ist eine zusätzliche Anmeldung (Einmalcode) erforderlich.“

Öffne das Bild per Klick in der Diashow oder einen Anhang der Seite; dort erscheint die Abfrage. Nach der Bestätigung öffne die Seite erneut.

### Die Abfrage erscheint immer wieder

Dein Server lässt die Sitzung nur kurz gelten oder beendet sie bei jedem Abruf. Das lässt sich im Client nicht einstellen. Weitere Prüfschritte: [Fehlersuche bei Confluence](help:troubleshooting.confluence).
