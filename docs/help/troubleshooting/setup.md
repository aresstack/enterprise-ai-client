---
id: troubleshooting.setup
title: Probleme mit dem Einrichtungsassistenten
keywords: Einrichtungsassistent, Einrichtung, Assistent, Setup, Wizard, Merlin, Weiter grau, Weiter bleibt grau, Weiter geht nicht, Knopf deaktiviert, Abschlussprüfung, rot, Fertig fehlt, Fertig grau, Erneut prüfen, Überspringen, Download hängt, Download abbrechen, Spracheingabe installieren, Assistent erneut starten, Einrichtung wiederholen, abgebrochen, erster Start, Erststart, setup wizard, next disabled
---

# Probleme mit dem Einrichtungsassistenten

Der Einrichtungsassistent mit dem Zauberer Merlin führt beim ersten Start durch fünf Schritte: „KeePass“, „KI-Dienst“, „Modelle“, „Confluence“ und „Abschluss“, danach optional „Spracheingabe“. Dieses Kapitel erklärt, warum „Weiter“ grau bleibt, was eine rote Abschlussprüfung bedeutet, was du tust, wenn ein Download hängt, und wie du den Assistenten später erneut startest. Den normalen Ablauf beschreibt [Erste Einrichtung](help:getting-started.setup).

## So prüft der Assistent

Jeder Schritt hat einen Erfüllungsstatus. „Weiter“ wird erst blau und klickbar, wenn der aktuelle Schritt erfüllt ist und gerade keine Abfrage läuft (während einer Abfrage zeigt der Mauszeiger die Sanduhr). Ein Fehler erscheint auf einer Platte mit rotem Rand über den Knöpfen; in den Schritten „KI-Dienst“ und „Confluence“ steht daneben der Knopf „Netzwerkeinstellungen…“.

Unabhängig davon kannst du jederzeit:

- über die Schrittleiste oben auf einen anderen Schritt klicken,
- mit „Überspringen“ (Tooltip „Ohne Prüfung zum nächsten Schritt; Eingaben bleiben erhalten“) weitergehen,
- Merlin anklicken und „Was muss ich hier machen?“ oder „Warum brauche ich das?“ wählen.

Springen und Überspringen ändern nichts daran, ob ein Schritt erfüllt ist. „Fertig“ gibt es erst, wenn die Abschlussprüfung grün ist. Während eine Abfrage oder ein Download läuft, ist die Schrittleiste gesperrt.

## Der Knopf „Weiter“ bleibt grau

Was „erfüllt“ heißt, hängt vom Schritt ab.

### Schritt „KeePass“

Erfüllt ist er, wenn die Eintragsliste aus KeePass gelesen wurde. Die Statuszeile zeigt dann „Gekoppelt; n Einträge lesbar.“.

1. Steht dort „Nicht verbunden.“, lies die rote Platte. Sie beginnt mit „KeePass ist nicht gekoppelt:“ und endet mit „KeePass muss laufen, das Plugin KeePassRPC muss installiert sein, und der Pairing-Schlüssel muss bestätigt werden.“.
2. Starte KeePass 2.x, öffne und entsperre die Datenbank.
3. Prüfe, dass das Plugin KeePassRPC installiert ist. Die Vorgaben „Rechner“ `127.0.0.1` und „Port“ `12546` passen in der Regel.
4. Klicke „Mit KeePass verbinden“. Beim ersten Mal zeigt KeePass ein Fenster „Authorise a new connection“ mit einem Einmal-Passwort; der Client öffnet den Dialog „KeePass verbinden“, in den du es unter „Einmal-Passwort aus KeePass:“ einträgst und mit „Verbinden“ bestätigst.

Details: [KeePass](help:troubleshooting.keepass).

### Schritt „KI-Dienst“

Erfüllt ist er erst nach einem erfolgreichen Verbindungstest **mit** gelesenem API-Key. Die Statuszeile zeigt dann „Der KI-Dienst ist erreichbar; der API-Key wurde gelesen.“.

1. Lies die Zeile unter „KeePass-Eintrag“. Mögliche Texte:
   - „Die KeePass-Einträge sind nicht lesbar …; bitte zuerst im Schritt „KeePass“ verbinden.“ → Schritt „KeePass“ erledigen.
   - „Der Eintrag „…“ fehlt; bitte Basis-URL und API-Key angeben, er wird in KeePass angelegt.“ → Basis-URL und API-Key eintragen und „In KeePass speichern“.
   - „API-Key: fehlt.“ oder „Basis-URL: fehlt.“ → das Fehlende eintragen und „In KeePass speichern“. Fehlen beide Angaben beim Speichern, meldet der Assistent „Für den Eintrag werden Basis-URL und API-Key gebraucht.“.
2. Ist der Eintrag vollständig, startet der Test von selbst. Sonst klicke „Verbindung testen“. Steht „Im KeePass-Eintrag fehlen noch API-Key oder Basis-URL.“ auf der Platte, ist der Eintrag noch unvollständig.
3. Bei „Verbindung fehlgeschlagen.“ nennt die Platte je fehlgeschlagenem Schritt eine Zeile, etwa „Proxy-Route: …“, „HTTPS-Verbindung: …“ oder „GET /models: HTTP 401: Der Dienst lehnt den API-Key ab. …“.
4. Wichtig: Auch ein Hinweis beim Schritt „API-Key“ (etwa „… Der Aufruf geht ohne API-Key weiter.“) zählt im Assistenten als Fehler. Ohne lesbaren API-Key wird „Weiter“ nicht frei.
5. Proxy oder Zertifikat sind schuld? Klicke „Netzwerkeinstellungen…“, passe Proxy bzw. Zertifikate an und bestätige. Der Assistent testet danach automatisch erneut. Details: [Netzwerk](help:troubleshooting.network).

### Schritt „Modelle“

Erfüllt ist er, wenn Chat-Modell und Embedding-Modell eingetragen sind **und** die Embedding-Dimension eine ganze Zahl größer als 0 ist.

1. Die Listen kommen aus der Modellliste des KI-Dienstes; je Art ist schon ein Modell vorausgewählt ([Regel](help:getting-started.models#die-automatische-wahl)). „Modelle abfragen“ holt die Liste neu. Steht dort „Keine Modelle gefunden; die Namen lassen sich auch eintragen.“, kannst du die Namen direkt in die Felder tippen.
2. Meldet den Bereich „Die Modellliste ist nicht abrufbar: …“, liegt meist ein Verbindungs- oder Anmeldeproblem vor; gehe zurück zu „KI-Dienst“.
3. Nach der Wahl des Embedding-Modells misst der Assistent die Dimension mit einem Test-Aufruf („Ermittle die Dimension mit einem Test-Aufruf …“). Gelingt das, steht „Ermittelt: n Werte je Vektor.“ da.
4. Scheitert die Messung, erscheint „Nicht automatisch ermittelbar; bitte die Dimension eintragen.“ und im Bereich „Der Embedding-Test ist fehlgeschlagen: … Die Dimension kann von Hand eingetragen werden.“. Trage dann die Zahl ein, die dein KI-Dienst für dieses Modell nennt, oder klicke „Dimension ermitteln“ für einen neuen Versuch.

Details: [Modelle](help:troubleshooting.models).

### Schritt „Confluence“

Confluence ist optional. Willst du es nicht einrichten, klicke „Überspringen“. Erfüllt ist der Schritt nur nach einem erfolgreichen „Verbindung testen“ **und** wenn entweder ein Client-Zertifikat gewählt oder „Ohne Zertifikat fortfahren“ angehakt ist.

1. Erscheint „Bitte ein Client-Zertifikat wählen oder ausdrücklich ohne Zertifikat fortfahren.“, wähle mit „Zertifikat wählen…“ ein Zertifikat oder hake „Ohne Zertifikat fortfahren“ an.
2. Erscheint „Für Confluence werden ein Titel für den KeePass-Eintrag, die URL und Passwort bzw. Token gebraucht.“, fehlt eine dieser Angaben.
3. Jede Änderung an URL, Benutzer, Passwort oder Zertifikat macht einen erfolgreichen Test ungültig. Teste nach jeder Änderung erneut.

Details: [Confluence](help:troubleshooting.confluence).

## Die Abschlussprüfung ist rot

Im Schritt „Abschluss“ speichert der Assistent die Einstellungen („Speichere und prüfe …“) und prüft dann bis zu neun Punkte. Die Punkte hängen nicht voneinander ab; ein fehlendes Modell färbt nur seinen eigenen Punkt und „Konfiguration lässt sich vollständig laden“ rot. Jeder Punkt hat einen Haken oder ein rotes Kreuz; fährst du mit der Maus über eine Zeile, zeigt der Tooltip die vollständige Begründung.

| Punkt | Typische Begründung bei Rot | Was zu tun ist |
|---|---|---|
| „KeePass ist gekoppelt“ | Meldung von KeePass, etwa „KeePass ist nicht erreichbar. …“ | KeePass starten und entsperren, Schritt „KeePass“ |
| „KI-Dienst ist erreichbar“ | „Konfiguration unvollständig: …“ oder die fehlgeschlagenen Schritte des Verbindungstests | Schritt „KI-Dienst“, Netzwerk |
| „API-Key kann gelesen werden“ | „kein KeePass-Eintrag für den KI-Dienst“ oder „Eintrag „…“ gefunden, aber das Passwortfeld ist leer. Bitte den API-Key dort eintragen.“ | API-Key im KeePass-Eintrag ergänzen |
| „Chat-Modell ist gesetzt“ | „kein Chat-Modell gewählt“ | Schritt „Modelle“ |
| „Embedding-Modell ist gesetzt“ | „kein Embedding-Modell gewählt“ | Schritt „Modelle“ |
| „Embedding-Dimension ist bekannt“ | „unbekannt“ | Dimension eintragen |
| „Confluence-Zugang funktioniert“ | „im KeePass-Eintrag fehlt Passwort bzw. Token“, „Netzwerkeinstellungen ungültig“ oder der Fehler der Verbindung | Schritt „Confluence“ |
| „Confluence-Quelle ist gespeichert und aktiviert“ | „gespeichert, aber ausgeschaltet“, „nicht in der Konfiguration gefunden“, „Noch nicht gespeichert, weil die Grundeinstellungen unvollständig sind.“ (dann zuerst die anderen roten Punkte beheben) | Quelle unter Wissensquellen anhaken |
| „Konfiguration lässt sich vollständig laden“ | „Angaben unvollständig: …“, „Konfigurationsdatei nicht schreibbar (…)“ | fehlende Angaben ergänzen, Schreibrechte des Anwendungsverzeichnisses prüfen |

Die beiden Confluence-Punkte entfallen, wenn du Confluence übersprungen hast. Dann lautet die grüne Meldung „Betriebsbereit. Confluence ist übersprungen und lässt sich später in der Seitenleiste unter Wissensquellen einrichten.“, sonst „Alles betriebsbereit.“.

So gehst du vor:

1. Lies die erste rote Zeile und den Tooltip.
2. Klicke in der Schrittleiste auf den passenden Schritt, behebe das Problem dort und kehre über die Schrittleiste zu „Abschluss“ zurück. Beim Betreten startet die Prüfung neu.
3. Alternativ: das Problem außerhalb beheben (etwa KeePass entsperren) und „Erneut prüfen“ klicken.
4. Steht dort „Die Prüfung lieferte kein Ergebnis.“ oder „Abschlussprüfung“ mit „abgebrochen (…)“ bzw. „konnte nicht gestartet werden“, sieh im Protokoll nach (Zeilen „Einrichtung: …“ und „Abschlussprüfung der Einrichtung abgebrochen“).

## Der Download hängt oder soll abgebrochen werden

Nach bestandener Abschlussprüfung bietet der Schritt „Spracheingabe“ ein lokales Modell für die Spracheingabe an. Das Modell ist groß (mehrere GB). Während des Downloads zeigt der Assistent eine Fortschrittsleiste und Zeilen wie „Datei 2 von 5: … – 37 % von 1234,5 MB“.

1. **Läuft er noch?** Ändert sich die Prozentzahl oder die Dateinummer, arbeitet der Download. Bei unbekannter Dateigröße läuft die Leiste ohne Prozentangabe hin und her; dann steht nur die bisher geladene Menge in MB da.
2. **Abbrechen:** Klicke das kleine Kreuz rechts neben dem Fortschritt (Tooltip „Download abbrechen“). Der Transfer endet wirklich, es erscheint „Download abgebrochen. Das Modell wurde nicht installiert.“ „Überspringen“ und das Schließen des Assistenten brechen ebenso ab. Ein späteres „Installieren“ setzt den angefangenen Download fort.
3. **Fehlgeschlagen:** Es erscheint „Installation fehlgeschlagen.“ und im Bereich „Das Modell ließ sich nicht installieren: … Erneut versuchen oder überspringen.“. Klicke „Erneut versuchen“.
4. **Keine Angebote:** „Die Angebote für die Spracheingabe ließen sich nicht ermitteln: … „Erneut versuchen“ lädt sie neu; Überspringen geht immer.“ bedeutet meist, dass der Zugriff auf den Hugging Face Hub über Proxy oder Zertifikat scheitert. Steht „Kein lokales Modell für die Spracheingabe angeboten; bitte überspringen.“, gibt es nichts zu installieren.
5. Das Modell lässt sich später unter Einstellungen → Modelle → „Lokale Modelle“ installieren (siehe [Spracheingabe](help:troubleshooting.speech)).

Die Grundeinrichtung ist zu diesem Zeitpunkt schon gespeichert; ein Problem mit der Spracheingabe gefährdet sie nicht.

## Den Assistenten erneut starten

Du kannst den Assistenten jederzeit wieder öffnen, um Einstellungen zu reparieren:

1. Öffne die Einstellungen über das Zahnrad im Fuß des Drawers.
2. Klicke unten auf „Einrichtungsassistent“ (Tooltip „Den Einrichtungsassistenten erneut öffnen (KeePass, KI-Dienst, Modelle, Confluence)“) oder im Reiter „Technische Details“ auf „Einrichtungsassistent erneut starten“.
3. Der Einstellungen-Dialog schließt sich wie bei „Abbrechen“; der Assistent erscheint als Overlay über dem Antwortfenster und übernimmt die bisherigen Werte.
4. „Abbrechen“, das ✕ oben rechts oder die Escape-Taste schließen ihn wieder.

Merlin begrüßt dich bei diesem späteren Aufruf als alten Bekannten; die Übergabe an „Merlins Tochter“ am Ende spielt nur beim ersten erfolgreichen Abschluss.

### Was passiert beim Abbrechen?

- **Beim ersten Start, vor der Abschlussprüfung:** Es wird keine Konfiguration geschrieben, und das Programm beendet sich ohne weiteres Fenster. Im Protokoll steht „Die Einrichtung wurde abgebrochen (…). Der nächste Start öffnet den Einrichtungsassistenten erneut.“.
- **Nach der Abschlussprüfung, aber vor „Fertig“:** Die Datei ist bereits geschrieben, die Einrichtung gilt aber als unterbrochen. Der nächste Start öffnet den Assistenten erneut, mit den gespeicherten Werten.
- **Beim späteren Aufruf aus den Einstellungen:** Die laufende Anwendung bleibt unverändert, außer was die Abschlussprüfung bereits gespeichert hat.

## Grenzen

- Der Assistent zeigt nie Schlüssel der Konfigurationsdatei, sondern nur Feldnamen.
- Merlins „Eigene Frage stellen …“ geht erst, wenn der KI-Dienst verbunden und ein Chat-Modell gewählt ist.
- Merlins Stimme ist unabhängig von der Sprachausgabe des Chats. Ohne verfügbare Stimme spricht er nur in Sprechblasen.
- Das Verhalten gegen echte KeePass-, KI-Dienst- und Confluence-Installationen ist nicht für alle Umgebungen geprüft.
