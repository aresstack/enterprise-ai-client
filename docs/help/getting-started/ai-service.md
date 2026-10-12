---
id: getting-started.ai-service
title: KI-Dienst einrichten
keywords: KI-Dienst, KI Dienst, Enterprise-API, API, OpenAI-kompatibel, Basis-URL, Base URL, baseUrl, Endpoint, API-Key, API Key, Token, Bearer, Schlüssel, KeePass-Eintrag, Enterprise AI API, Verbindung testen, Verbindungstest, connection test, GET /models, Proxy, Zertifikat, TLS, HTTPS, nicht erreichbar, In KeePass speichern, Netzwerkeinstellungen
---

# KI-Dienst einrichten

Der KI-Dienst ist der Server, der die Antworten im Chat erzeugt und Texte für die Wissenssuche in Vektoren umrechnet. Der Enterprise AI Client spricht dafür mit einer internen Enterprise-API im OpenAI-kompatiblen Format. Im Schritt „KI-Dienst“ des Einrichtungsassistenten legst du fest, welcher KeePass-Eintrag die Zugangsdaten dazu enthält, und prüfst, ob der Dienst erreichbar ist. Dieses Kapitel richtet sich an alle, die den Client zum ersten Mal einrichten oder den KI-Dienst wechseln wollen.

## Was du brauchst

- Die **Basis-URL** des KI-Dienstes, meist eine Adresse, die auf `/v1` endet, zum Beispiel `https://ki.example.org/v1`. An diese Adresse hängt der Client selbst `chat/completions`, `embeddings` und `models` an.
- Einen **API-Key** für diesen Dienst.
- Einen **KeePass-Eintrag**, in dem beides steht: der API-Key im Passwortfeld, die Basis-URL im URL-Feld. Fehlt der Eintrag oder etwas darin, kann der Assistent ihn anlegen bzw. ergänzen.
- Eine funktionierende Kopplung mit KeePass, siehe [KeePass verbinden](help:getting-started.keepass).

## Schritt für Schritt

1. Wechsle mit „Weiter“ oder über die Schrittleiste zu „KI-Dienst“.
2. Im Feld „KeePass-Eintrag“ ist schon ein Eintrag vorgeschlagen: der bereits konfigurierte Titel, sonst ein eindeutiger Titel mit „Enterprise AI“, sonst „Enterprise AI API“. Wähle bei Bedarf einen anderen aus der Liste oder tippe einen Titel ein.
3. Der Assistent liest den Eintrag („Lese den Eintrag …“) und zeigt, was vorhanden ist, etwa „API-Key: vorhanden.  Basis-URL: https://ki.example.org/v1.“ Der API-Key selbst wird nie angezeigt.
4. Ist beides vorhanden, startet der Verbindungstest sofort von selbst. Sonst ergänzt du das Fehlende ([siehe unten](help:getting-started.ai-service#fehlendes-ergaenzen)).
5. Ist der Test erfolgreich, steht dort „Der KI-Dienst ist erreichbar; der API-Key wurde gelesen.“ „Weiter“ wird aktiv.

Mit dem Knopf „Verbindung testen“ kannst du den Test jederzeit wiederholen, etwa nachdem du in KeePass etwas geändert hast.

## Fehlendes ergänzen

Fehlen im Eintrag API-Key oder Basis-URL, oder gibt es den Eintrag noch nicht, blendet der Schritt zwei Felder ein:

- „Basis-URL“: die Adresse des Dienstes.
- „API-Key“: der Schlüssel. Das Feld ist verdeckt.

Klicke danach „In KeePass speichern“. Der Assistent schreibt beides in den KeePass-Eintrag (bei einem neuen Titel wird der Eintrag angelegt), liest ihn erneut und testet dann. Ein bereits im Eintrag vorhandener API-Key bleibt erhalten, wenn du das Feld „API-Key“ leer lässt und nur die URL ergänzt.

Der API-Key wird dabei nie in die Konfigurationsdatei geschrieben. Für das Speichern brauchst du beides: Fehlt eines, meldet der Assistent „Für den Eintrag werden Basis-URL und API-Key gebraucht.“

## Was der Verbindungstest prüft

Der Test geht denselben Weg wie die Anwendung später beim Chatten, Schritt für Schritt:

- „API-Key“: Der Key wird aus dem KeePass-Eintrag gelesen. Im Assistenten zählt ein nicht lesbarer oder leerer Key als Fehler.
- „Proxy-Route“: Welcher Weg (direkt oder über einen Proxy) zum Dienst führt. Ist keine Route ermittelbar, wird gar nicht erst verbunden.
- „HTTPS-Verbindung“: Verbindungsaufbau und TLS-Handshake mit den eingestellten Vertrauensquellen für Zertifikate.
- „GET /models“: Der Dienst wird nach seinen Modellen gefragt. Antwortet er mit einer Modellliste, ist er erreichbar und der Key gültig.

Schlägt ein Schritt fehl, steht in der roten Fehlerplatte je fehlgeschlagenem Schritt eine Zeile mit Name und Ursache, etwa „GET /models: HTTP 401: Der Dienst lehnt den API-Key ab. …“. Der API-Key erscheint in keiner Zeile.

## Was nach dem erfolgreichen Test passiert

- Im Hintergrund fragt der Assistent sofort die Modelle des Dienstes ab, damit der nächste Schritt „Modelle“ schon vorbereitet ist. Gibt es je Art nur ein Modell, ist es schon gewählt. Siehe [Modelle auswählen](help:getting-started.models).
- Merlin, der Zauberer des Assistenten, wacht auf: Beim ersten Mal stellt er sich vor und meldet „Ausgezeichnet. Der KI-Dienst antwortet.“ Ab jetzt kannst du ihm auch eigene Fragen stellen, sobald ein Chat-Modell feststeht. Siehe [Der Einrichtungsassistent](help:getting-started.setup).
- In der Konfiguration landet nur der Verweis auf den Eintrag (zum Beispiel `chat.apiKeyRef=keepass:Enterprise AI API`). Bei einer neuen Einrichtung bleibt die Basis-URL in der Datei leer und wird bei jedem Start aus dem URL-Feld des KeePass-Eintrags gelesen. Ändert sich die Adresse, änderst du sie also nur in KeePass.
- Für die Wissenssuche (Embeddings) gelten dieselbe Adresse und derselbe API-Key wie für den Chat, solange du in den Einstellungen nichts anderes einträgst.

## Proxy und Zertifikate: „Netzwerkeinstellungen…“

Scheitert der Test, erscheint in der Fehlerplatte der Knopf „Netzwerkeinstellungen…“. Er öffnet den Einstellungen-Dialog direkt auf dem Reiter „Netzwerk & Agent“. Dort stellst du ein, wie der Proxy ermittelt wird (standardmäßig über das PAC-Skript deines Unternehmens) und welchen Zertifikatsquellen Java vertrauen soll. Übernimmst du die Änderungen, testet der Assistent den KI-Dienst sofort erneut. Gespeichert wird dabei noch nichts; die Netzwerkwerte gehen erst mit der Abschlussprüfung in die Konfiguration.

Mehr dazu: [Netzwerk einstellen](help:settings.network).

## Grenzen

- Unterstützt wird genau eine Art von Dienst: eine OpenAI-kompatible Enterprise-API mit den Endpunkten für Chat, Embeddings und Modellliste. Eine Auswahl verschiedener Anbieter gibt es nicht.
- Der API-Key muss in KeePass liegen. Den manuellen Zugang ohne KeePass (URL in den Einstellungen, der Key wird beim ersten Zugriff abgefragt und nicht gespeichert) bietet nur der Einstellungen-Dialog, nicht der Assistent.
- Steht in der Konfigurationsdatei schon eine eigene Basis-URL, hat sie Vorrang vor dem URL-Feld des KeePass-Eintrags. In den Einstellungen heißt das Feld „Basis-URL (sonst aus KeePass)“ (Einstellungen → KI-Dienst → Erweitert).
- Hinweis: Verhalten und Fehlermeldungen hängen vom Server und vom Firmennetz ab und sind nicht für alle Installationen geprüft.

## Typische Probleme

Die Ursache steht in der roten Fehlerplatte, je fehlgeschlagenem Prüfschritt eine Zeile. Die häufigsten Fälle:

### „Im KeePass-Eintrag fehlen noch API-Key oder Basis-URL.“

Der Test wurde gestartet, bevor beides im Eintrag stand. Trage die fehlenden Werte ein und klicke „In KeePass speichern“.

### „Der Eintrag „…“ ist nicht lesbar“

KeePass ist gesperrt, beendet oder die Kopplung ist ungültig. Entsperre KeePass und verbinde im Schritt „KeePass“ erneut.

### HTTP 401 oder 403 bei „GET /models“

Der Dienst lehnt den Key ab. Im Passwortfeld des KeePass-Eintrags darf nur der Key stehen, ohne vorangestelltes „Bearer“ und ohne Zeilenumbruch.

### HTTP 404 bei „GET /models“

Die Basis-URL stimmt vermutlich nicht. Meist fehlt das `/v1` am Ende oder es ist doppelt.

### Fehler bei „HTTPS-Verbindung“ mit „PKIX path building failed“

Java vertraut dem Zertifikat des Servers nicht, typisch bei einer firmeninternen Zertifizierungsstelle oder einem Proxy mit TLS-Prüfung. Unter Windows sollten die Vertrauensquellen aus dem Windows-Zertifikatspeicher eingeschaltet sein (Standard). Sonst die Zertifizierungsstelle als Datei hinterlegen. Details: [Netzwerkprobleme](help:troubleshooting.network).

### „Proxy-Route“ schlägt fehl oder der Hostname ist unbekannt

Das Netz verlangt einen Proxy, den Java nicht automatisch nutzt. Öffne „Netzwerkeinstellungen…“, wähle einen passenden Proxy-Modus und prüfe dort mit „Proxy auflösen“, ob ein Proxy gefunden wird.

### Proxy verlangt eine Anmeldung (HTTP 407)

Stelle in den Netzwerkeinstellungen die Proxy-Anmeldung auf BASIC mit einem KeePass-Eintrag für Benutzer und Passwort. Integrierte Windows-Anmeldung wird nicht unterstützt.

Weitere Hilfe: [Einstellungen zum KI-Dienst](help:settings.ai-service), [Probleme bei der Einrichtung](help:troubleshooting.setup).
