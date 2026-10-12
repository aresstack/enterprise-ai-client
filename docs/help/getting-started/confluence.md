---
id: getting-started.confluence
title: Confluence im Assistenten einrichten
keywords: Confluence, Confluence Data Center, Wiki, Wissensquelle, Quelle, Spaces, Space, KeePass-Eintrag, Confluence-URL, Benutzername, Passwort, Token, persönliches Zugriffstoken, Personal Access Token, PAT, Client-Zertifikat, Zertifikat wählen, Ohne Zertifikat fortfahren, mTLS, Windows-Zertifikatspeicher, Haken, Kreuz, Zugangsdaten ändern, Verbindung testen, optional, überspringen, später einrichten
---

# Confluence im Assistenten einrichten

Im vierten Schritt des Einrichtungsassistenten richtest du Confluence als Wissensquelle ein. Danach kann der Chat in deinem Confluence suchen und Antworten mit Quellen belegen. Der Schritt ist optional: Wer Confluence nicht nutzt, überspringt ihn und kann es später jederzeit nachholen. Die Zugangsdaten liegen wie alle Secrets in KeePass; der Assistent liest sie dort, zeigt dir nur, was vorhanden ist, und schreibt Fehlendes für dich hinein.

## Was du brauchst

- Die Adresse deiner Confluence-Instanz inklusive Kontextpfad, zum Beispiel `https://confluence.example.org/confluence`. Unterstützt wird Confluence Data Center (die selbst betriebene Variante), nicht Confluence Cloud.
- Entweder Benutzername und Passwort oder ein persönliches Zugriffstoken.
- Falls dein Confluence es verlangt: ein Client-Zertifikat im Windows-Zertifikatspeicher.
- Eine funktionierende Kopplung mit KeePass, siehe [KeePass verbinden](help:getting-started.keepass).

## Anmeldung mit Passwort oder Token

Welche Anmeldung der Client verwendet, entscheidet allein der Benutzername im KeePass-Eintrag:

- **Mit Benutzername:** Anmeldung mit Benutzername und Passwort.
- **Ohne Benutzername:** Das Passwortfeld gilt als persönliches Zugriffstoken.

Hinweis: Die Anmeldung per Token ist im Code vorgesehen, aber noch nicht gegen eine echte Confluence-Instanz geprüft.

## Schritt für Schritt

1. Wechsle zum Schritt „Confluence“. Im Feld „KeePass-Eintrag“ ist ein Eintrag vorgeschlagen: ein eindeutiger Eintrag, dessen Titel oder URL „confluence“ enthält, sonst der Titel „Confluence“. Wähle bei Bedarf einen anderen oder tippe einen neuen Titel ein.
2. Der Assistent liest den Eintrag („Lese den Eintrag …“). Was dann erscheint, hängt davon ab, wie vollständig der Eintrag ist ([siehe unten](help:getting-started.confluence#haken-und-kreuz-je-angabe)).
3. Lege fest, ob ein Client-Zertifikat nötig ist: „Zertifikat wählen…“ öffnet die Auswahl aus dem Windows-Zertifikatspeicher; brauchst du keines, hake „Ohne Zertifikat fortfahren“ an. Eines von beiden ist Pflicht.
4. Klicke „Verbindung testen“. Fehlende oder geänderte Angaben werden zuerst in KeePass gespeichert („Speichere den Eintrag in KeePass …“), dann ruft der Assistent die Spaces ab („Teste Confluence (Spaces abrufen) …“).
5. Bei Erfolg steht dort zum Beispiel „Verbindung erfolgreich: 12 Spaces sichtbar.“ bzw. mit Zertifikat „Verbindung erfolgreich (Zertifikat „…“): 12 Spaces sichtbar.“ Jetzt wird „Weiter“ aktiv.

Ist der Eintrag vollständig und steht schon ein Zertifikat darin, testet der Assistent sofort von selbst, ohne dass du klicken musst.

## Haken und Kreuz je Angabe

Bei einem vorhandenen Eintrag zeigt der Schritt keine Eingabefelder, sondern Statuszeilen mit Haken oder Kreuz. Secrets werden dabei nie angezeigt:

- URL: „URL vorhanden: https://confluence.example.org/confluence“ (Haken) oder „URL fehlt: bitte unten eintragen, sie wird im KeePass-Eintrag gespeichert“ (Kreuz).
- Benutzername: „Benutzername vorhanden“ oder „Kein Benutzername: Anmeldung mit persönlichem Zugriffstoken“. Beides ist in Ordnung und bekommt einen Haken.
- Passwort bzw. Token: „Passwort bzw. Token vorhanden“ bzw. „Token vorhanden“ (Haken) oder „Passwort bzw. Token fehlt: bitte unten eintragen, es wird im KeePass-Eintrag gespeichert“ (Kreuz).

Daraus ergeben sich drei Fälle:

- **Vollständiger Eintrag** (URL und Passwort bzw. Token vorhanden): Die Statuszeile sagt „Vorhandener Eintrag; die Zugangsdaten stehen in KeePass.“ Du musst nichts eintippen; getestet wird mit den Werten aus KeePass.
- **Unvollständiger Eintrag:** „Im Eintrag fehlt noch etwas; bitte ergänzen, es wird beim Testen in KeePass gespeichert.“ Es erscheinen nur die Felder für das Fehlende, etwa nur „Confluence-URL“ oder nur „Passwort oder Token“.
- **Neuer Titel:** „Neuer Eintrag: URL und Passwort bzw. Token angeben; er wird beim Testen in KeePass angelegt.“ Es erscheinen alle Felder: „Confluence-URL“, „Benutzername“ und „Passwort oder Token“.

## Zugangsdaten ändern

Willst du bei einem vollständigen Eintrag etwas ändern (anderes Passwort, neue Adresse, Wechsel von Passwort auf Token), klicke „Zugangsdaten ändern“. Dann erscheinen alle Felder mit den Werten aus KeePass, außer dem Passwort: Das Feld „Passwort oder Token“ bleibt leer, darunter steht „Passwort bzw. Token steht in KeePass; nur zum Ändern eintragen.“ Lässt du es leer, bleibt das gespeicherte Passwort erhalten.

Gespeichert wird beim nächsten Klick auf „Verbindung testen“, und zwar nur, wenn sich wirklich etwas geändert hat (URL, Benutzername, ein neu eingetipptes Passwort oder das Zertifikat). Zusatzfelder des Eintrags bleiben erhalten. Jede Änderung an den Feldern macht einen schon erfolgreichen Test ungültig; teste danach erneut.

## Das Client-Zertifikat

Manche Confluence-Installationen verlangen, dass sich der Rechner zusätzlich mit einem Zertifikat ausweist.

- „Zertifikat wählen…“ zeigt eine Tabelle der Zertifikate aus dem Windows-Zertifikatspeicher mit Alias, Subject, „Gültig bis“ und Status. Mit „Auch abgelaufene Zertifikate anzeigen“ siehst du auch abgelaufene. Gelesen werden nur die öffentlichen Zertifikate, nie private Schlüssel.
- Außerhalb von Windows bleibt diese Tabelle leer. Dann hake „Ohne Zertifikat fortfahren“ an.
- Der gewählte Alias wird im KeePass-Eintrag gespeichert (Zusatzfeld `MM_CertAlias`) und beim nächsten Mal von dort gelesen.

## Optional: Confluence überspringen

Nutzt du kein Confluence, klicke „Überspringen“ (oder in Merlins Menü „Diesen Schritt überspringen“). Merlin weist beim Betreten des Schritts selbst darauf hin. Folgen:

- Die Abschlussprüfung legt keine Confluence-Quelle an und prüft Confluence nicht; die beiden Confluence-Punkte entfallen.
- Am Ende steht „Betriebsbereit. Confluence ist übersprungen und lässt sich später in der Seitenleiste unter Wissensquellen einrichten.“

Als übersprungen gilt Confluence auch, wenn du den Schritt zwar ausgefüllt, aber nie erfolgreich getestet hast.

Später einrichten: In der Seitenleiste die Seite „Wissensquellen“ öffnen und „+ Quelle“ klicken, siehe [Confluence als Wissensquelle](help:knowledge.confluence). Alternativ den Einrichtungsassistenten erneut starten, siehe [Der Einrichtungsassistent](help:getting-started.setup).

## Was in der Abschlussprüfung passiert

Hast du Confluence erfolgreich getestet, legt die Abschlussprüfung eine Wissensquelle mit dem Namen „Confluence“ an und schaltet sie ein. Sie verweist auf den KeePass-Eintrag, die Adresse und das Zertifikat (oder ausdrücklich keines). Zwei Prüfpunkte kommen hinzu:

- „Confluence-Zugang funktioniert“: erneuter Test mit den Zugangsdaten aus KeePass.
- „Confluence-Quelle ist gespeichert und aktiviert“.

Die Quelle hat keine festen Startpunkte. Alle Spaces, die dein Benutzer sehen darf, stehen danach unter „Wissensquellen“ zur Auswahl, jeder mit eigenem Häkchen. Neue Spaces sind von selbst angehakt; nimm Häkchen weg, wenn ein Space nicht durchsucht werden soll. Beim Start wird nichts indexiert: Die Suche im Chat füllt den Index nach und nach, das vollständige Indexieren startest du bei Bedarf selbst. Siehe [Indexierung](help:knowledge.indexing).

## Grenzen

- Nur Confluence Data Center, nicht Confluence Cloud.
- Client-Zertifikate aus dem Windows-Zertifikatspeicher gibt es nur unter Windows.
- Hinweis: Der Assistent ist gegen eine simulierte Confluence-Instanz geprüft. Anmeldeverfahren wie Single Sign-on oder ein zusätzlicher Einmalcode verhalten sich je nach Installation unterschiedlich. Einmalcodes fragt der Client erst beim Öffnen von Bildern und Anhängen ab, nicht bei der Einrichtung, siehe [Einmalcode](help:knowledge.otp).

## Typische Probleme

Die Meldungen erscheinen in der Statuszeile oder in der roten Fehlerplatte des Schritts „Confluence“.

### „Bitte ein Client-Zertifikat wählen oder ausdrücklich ohne Zertifikat fortfahren.“

Du hast „Verbindung testen“ geklickt, ohne über das Zertifikat entschieden zu haben. Wähle eines oder hake „Ohne Zertifikat fortfahren“ an.

### „Für Confluence werden ein Titel für den KeePass-Eintrag, die URL und Passwort bzw. Token gebraucht.“

Eine dieser Angaben fehlt. Trage sie in die angezeigten Felder ein.

### „Verbindung fehlgeschlagen: …“

Die Ursache steht dahinter. Häufig: falsche Adresse (Kontextpfad fehlt, etwa `/confluence`), falsches Passwort, abgelaufenes Token, fehlendes Zertifikat oder ein Proxy. Bei Netzwerkproblemen hilft der Knopf „Netzwerkeinstellungen…“ in der Fehlerplatte. Siehe [Confluence-Probleme](help:troubleshooting.confluence).

### „Der Eintrag ließ sich nicht in KeePass speichern: …“

KeePass ist gesperrt oder KeePassRPC lehnt den Schreibzugriff ab. Entsperre KeePass und versuche es erneut, siehe [KeePass-Probleme](help:troubleshooting.keepass).
