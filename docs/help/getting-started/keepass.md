---
id: getting-started.keepass
title: KeePass verbinden
keywords: KeePass, KeePassRPC, KeePass RPC, Plugin, Pairing, Kopplung, koppeln, verbinden, Einmal-Passwort, Pairing-Schlüssel, Authorise a new connection, Passwort-Manager, password manager, Tresor, vault, Secret, API-Key, Eintrag, Einträge, Titel, URL-Feld, Passwortfeld, Benutzername, In KeePass speichern, Port 12546, 127.0.0.1, Mit KeePass verbinden
---

# KeePass verbinden

Der Enterprise AI Client liest alle Zugangsdaten aus KeePass: den API-Key des KI-Dienstes, die Basis-URL, Benutzername und Passwort bzw. Token für Confluence. In keiner Datei des Clients steht ein Passwort. Damit das klappt, koppelst du den Client einmal mit KeePass. Dieses Kapitel erklärt den Schritt „KeePass“ des Einrichtungsassistenten, wie Einträge ausgewählt und ausgelesen werden und wie der Assistent Fehlendes in KeePass einträgt.

## Voraussetzungen

- KeePass 2.x läuft auf demselben Rechner.
- Das Plugin KeePassRPC ist in KeePass installiert. Über dieses Plugin spricht der Client mit KeePass. Standardmäßig lauscht es auf dem Rechner `127.0.0.1` unter Port `12546`.
- Die KeePass-Datenbank ist geöffnet und entsperrt. Das gilt nicht nur für die Einrichtung: Der Client holt den API-Key bei jeder Anfrage neu aus KeePass und merkt ihn sich nicht. KeePass muss also während der Arbeit entsperrt bleiben.

## Schritt für Schritt: KeePass koppeln

Der erste Schritt des Assistenten heißt „KeePass verbinden“. Er zeigt drei Felder, die schon vorbelegt sind und in der Regel passen:

- „Rechner“: `127.0.0.1`
- „Port“: `12546`
- „Name des Clients“: `Enterprise AI Client`. Diesen Namen zeigt KeePass im Kopplungsfenster an.

So gehst du vor:

1. Starte KeePass und entsperre deine Datenbank.
2. Klicke im Assistenten auf „Mit KeePass verbinden“. Die Statuszeile zeigt „Verbinde mit KeePass …“.
3. Beim allerersten Mal öffnet KeePass ein Fenster „Authorise a new connection“ für den Client „Enterprise AI Client“ und zeigt dort ein Einmal-Passwort.
4. Gleichzeitig öffnet der Client das Fenster „KeePass verbinden“ mit der Überschrift „Pairing mit KeePass“. Gib dort im Feld „Einmal-Passwort aus KeePass:“ das Passwort aus dem KeePass-Fenster ein und klicke „Verbinden“.
5. Der Assistent liest danach die Liste deiner Einträge. Hat das geklappt, steht dort „Gekoppelt; N Einträge lesbar.“, und Merlin bestätigt: „Sehr gut. Die Verbindung zu KeePass steht.“

Der Schritt gilt erst als erledigt, wenn die Eintragsliste wirklich gelesen werden konnte. Erst dann wird „Weiter“ aktiv.

## Was „Kopplung“ bedeutet

Bei der Kopplung (englisch Pairing) vereinbaren Client und KeePass einen gemeinsamen Schlüssel. Der Client speichert diesen Kopplungsschlüssel dauerhaft in einer Datei im Anwendungsverzeichnis, die nur für dich lesbar ist. Bei jedem späteren Zugriff weist sich der Client damit aus, ohne dass du erneut ein Einmal-Passwort eingeben musst.

- Der Einrichtungsassistent legt den Schlüssel immer dauerhaft ab.
- Jeder Zugriff öffnet eine eigene, kurze Verbindung zu KeePass und schließt sie wieder.
- Widerrufst du die Kopplung in KeePass (in den Einstellungen des KeePassRPC-Plugins), lehnt KeePass den gespeicherten Schlüssel ab. Der Client verwirft ihn dann und fragt genau einmal nach einer neuen Kopplung.
- In den Einstellungen (Reiter „KeePass“) kannst du unter „Pairing-Schlüssel“ statt „Datei im Anwendungsverzeichnis (dauerhaft)“ auch „Nur im Speicher (bei jedem Start neu pairen)“ wählen. Siehe [KeePass-Einstellungen](help:settings.keepass).

## Einträge auswählen

In den Schritten „KI-Dienst“ und „Confluence“ wählst du jeweils einen KeePass-Eintrag. Das Auswahlfeld „KeePass-Eintrag“ enthält die Titel aller Einträge deiner Datenbank. Du kannst auch einen Titel eintippen.

- Der Titel muss exakt übereinstimmen. Gesucht wird nur über den Titel, nicht über URL oder Benutzername.
- Gibt es einen Eintrag mit dem eingetippten Titel nicht, behandelt der Assistent ihn als neuen Eintrag und legt ihn auf Wunsch in KeePass an (siehe unten).
- Der Assistent schlägt einen Eintrag vor. Für den KI-Dienst: den schon konfigurierten Titel, sonst einen eindeutigen Titel, der „Enterprise AI“ enthält, sonst „Enterprise AI API“. Für Confluence: einen eindeutigen Eintrag, dessen Titel oder URL „confluence“ enthält, sonst „Confluence“.

Springst du über die Schrittleiste direkt zu „KI-Dienst“ oder „Confluence“, ohne vorher „Mit KeePass verbinden“ geklickt zu haben, liest der Assistent die Eintragsliste dort selbst nach. Scheitert das, steht in der Statuszeile „Die KeePass-Einträge sind nicht lesbar (…); bitte zuerst im Schritt „KeePass“ verbinden.“ Ein Eintrag gilt in diesem Zustand nie fälschlich als „neu“.

## Welche Felder automatisch befüllt werden

Sobald du einen vorhandenen Eintrag wählst, liest der Assistent ihn und übernimmt, was darin steht:

| Wofür | Feld im KeePass-Eintrag | Bedeutung |
|---|---|---|
| KI-Dienst | Passwortfeld | API-Key |
| KI-Dienst | URL-Feld | Basis-URL des KI-Dienstes |
| Confluence | URL-Feld | Adresse deiner Confluence-Instanz |
| Confluence | Benutzername | mit Benutzername: Anmeldung mit Passwort; ohne: Anmeldung mit persönlichem Zugriffstoken |
| Confluence | Passwortfeld | Passwort bzw. persönliches Zugriffstoken |
| Confluence | Zusatzfeld `MM_CertAlias` | Alias des Client-Zertifikats, falls eines nötig ist |

Der Assistent zeigt dir nur, ob etwas vorhanden ist, nie das Secret selbst. Beim KI-Dienst lautet die Statuszeile etwa „API-Key: vorhanden.  Basis-URL: https://ki.example.org/v1.“ Bei Confluence zeigen Zeilen mit Haken oder Kreuz, ob URL, Benutzername und Passwort bzw. Token vorhanden sind. Ist alles da, startet der Verbindungstest beim KI-Dienst sofort von selbst; bei Confluence dann, wenn auch schon ein Zertifikat im Eintrag steht.

## Fehlendes nach KeePass schreiben

Fehlt in einem Eintrag etwas, musst du nicht zu KeePass wechseln. Der Assistent zeigt genau die fehlenden Felder und schreibt sie für dich in KeePass:

- **KI-Dienst:** Fehlen API-Key oder Basis-URL, erscheinen die Felder „Basis-URL“ und „API-Key“ und der Knopf „In KeePass speichern“. Bei einem neuen Titel steht dort „Der Eintrag „…“ fehlt; bitte Basis-URL und API-Key angeben, er wird in KeePass angelegt.“ Nach dem Speichern liest der Assistent den Eintrag erneut. Ein schon vorhandener Benutzername und vorhandene Zusatzfelder des Eintrags bleiben erhalten.
- **Confluence:** Bei einem unvollständigen Eintrag fragt der Schritt nur das Fehlende ab (etwa nur die URL), bei einem neuen Titel alles. Gespeichert wird beim Klick auf „Verbindung testen“: zuerst in KeePass, dann wird getestet. Die URL landet im URL-Feld, Zusatzfelder bleiben erhalten. Ein neu angelegter Eintrag bekommt die Kategorie „Confluence“, ein gewähltes Zertifikat steht danach im Zusatzfeld `MM_CertAlias`. Details: [Confluence im Assistenten](help:getting-started.confluence).

Neue Einträge legt KeePassRPC in der aktiven Datenbank an. Lehnt KeePassRPC den Schreibzugriff ab, nennt die Fehlerplatte den Grund („Der Eintrag ließ sich nicht in KeePass speichern: …“).

Hinweis: Das Schreiben in KeePass wurde gegen ein simuliertes KeePassRPC geprüft, aber noch nicht gegen jede echte KeePass-Installation. Prüfe einen neu angelegten Eintrag im Zweifel kurz in KeePass.

## Was im Hintergrund passiert

- In der Konfigurationsdatei steht nur der Titel des Eintrags als Verweis, zum Beispiel `chat.apiKeyRef=keepass:Enterprise AI API`.
- Der Client fordert ein Secret nur für den einzelnen Zugriff an, nutzt es und löscht es danach aus dem Speicher. Es gibt keinen Zwischenspeicher. Bei großen Indexierungen fragt der Client KeePass deshalb sehr oft.
- In das Protokoll schreibt der Client nur Titel und Fehlergründe, nie Passwörter oder Schlüssel.

## Grenzen

- Nur KeePass 2.x mit KeePassRPC wird unterstützt; andere Passwort-Manager nicht.
- Der Titel muss exakt passen. Zwei Einträge mit gleichem Titel sind ungünstig, weil nur über den Titel gesucht wird.
- Ist KeePass gesperrt oder beendet, scheitern Chat-Anfragen mit einem Anmeldefehler.

## Typische Probleme

Die Meldungen stehen in der Statuszeile oder in der roten Fehlerplatte des Schritts. So gehst du sie an.

### „KeePass ist nicht gekoppelt: …“

Der Assistent zeigt danach: „KeePass muss laufen, das Plugin KeePassRPC muss installiert sein, und der Pairing-Schlüssel muss bestätigt werden.“ Prüfe der Reihe nach:

1. Läuft KeePass, und ist die Datenbank entsperrt?
2. Ist das Plugin KeePassRPC in KeePass installiert und aktiv?
3. Stimmen „Rechner“ und „Port“ mit den Einstellungen des Plugins überein?
4. Hast du das Einmal-Passwort im Fenster „KeePass verbinden“ eingegeben und nicht „Abbrechen“ gedrückt?

### „KeePass lehnt den Zugriff ab“

Die Kopplung ist ungültig oder wurde in KeePass widerrufen. Entferne die Verbindung „Enterprise AI Client“ in den Einstellungen von KeePassRPC und klicke erneut „Mit KeePass verbinden“.

### „Kein KeePass-Eintrag mit dem Titel …“

Der Titel stimmt nicht exakt (Leerzeichen, Groß- und Kleinschreibung). Wähle den Eintrag aus der Liste statt ihn einzutippen.

### Das Kopplungsfenster erscheint nicht

KeePass zeigt das Fenster „Authorise a new connection“ nur, wenn KeePassRPC die Verbindung annimmt. Prüfe, ob das KeePass-Fenster hinter anderen Fenstern liegt, und ob „Rechner“ und „Port“ stimmen. Ist schon ein gültiger Kopplungsschlüssel gespeichert, erscheint das Fenster gar nicht, weil keine neue Kopplung nötig ist. Weitere Hilfe: [KeePass-Probleme](help:troubleshooting.keepass), [Sicherheit](help:concepts.security).
