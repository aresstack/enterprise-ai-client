---
id: settings.overview
title: Der Dialog „Einstellungen“ im Überblick
keywords: Einstellungen, Settings, Optionen, Konfiguration, Zahnrad, Einstellungsdialog, Speichern, Abbrechen, Einstellungsordner öffnen, Offline-Paket erstellen, Offline-Paket, Offline-Installation, ohne Netz, Reiter, Kategorien, ohne Neustart, Probleme, Bitte korrigieren, Erststart, erster Start, Preferences, Options, Escape
---

# Der Dialog „Einstellungen“ im Überblick

Der Dialog „Einstellungen“ ist die zentrale Stelle, an der du alles einstellst, was der Enterprise AI Client dauerhaft wissen muss: den KI-Dienst, KeePass, Netzwerk und Proxy, die Modelle, die Sprachausgabe, den Index, die Werkzeuge und die Darstellung. Er bearbeitet die Konfigurationsdatei des Programms, sodass du sie nie von Hand ausfüllen musst. Dieses Kapitel erklärt, wie du den Dialog öffnest, wie Speichern und Abbrechen funktionieren, was „Einstellungsordner öffnen“ tut und welche Änderungen sofort gelten. Die einzelnen Reiter beschreiben eigene Kapitel, die am Ende verlinkt sind.

## Den Dialog öffnen

Es gibt mehrere Wege in den Dialog:

1. **Zahnrad in der Seitenleiste:** Öffne mit dem Hamburger-Symbol links oben die Seitenleiste. Im Fuß der Seite „Chats“ sitzt ein Zahnrad mit dem Tooltip „Einstellungen“. Ein Klick öffnet den Dialog auf dem ersten Reiter „KI-Dienst“.
2. **Modellwahl im Eingabefeld:** Ein Klick auf den Modellnamen im Eingabebereich öffnet ein Menü mit den abgefragten Chat-Modellen. Ganz unten steht „Modelle verwalten …“; auch das öffnet den Einstellungen-Dialog (ebenfalls auf dem Reiter „KI-Dienst“, die Modelle findest du dann links unter „Modelle“).
3. **Aus dem Einrichtungsassistenten:** Im Schritt zu Confluence bietet Merlin den Knopf „Netzwerkeinstellungen…“ an. Er öffnet den Dialog direkt auf dem Reiter „Netzwerk & Agent“. In diesem Fall schreibt „Speichern“ nicht sofort in die Datei, sondern übergibt die Werte an den Assistenten; geschrieben wird erst am Ende der Einrichtung.
4. **Beim Start mit fehlerhafter Datei:** Lässt sich eine vorhandene Konfigurationsdatei nicht laden, öffnet das Programm den Dialog automatisch mit ihren Werten und listet die Probleme auf (siehe [unten](help:settings.overview#typische-probleme)).

Der Dialog ist modal: solange er offen ist, bedienst du das Hauptfenster nicht. Er hat keinen normalen Fensterrahmen, sondern die Comic-Kontur des Hauptfensters. Du verschiebst ihn, indem du die Überschrift „Einstellungen“ ziehst, und vergrößerst ihn am Rand. Die Startgröße ist 800 × 680 Pixel, kleiner als 640 × 520 wird er nicht.

## Aufbau des Dialogs

- **Oben** steht die Überschrift „Einstellungen“ und darunter ein Hinweis. Im normalen Betrieb lautet er: „Gespeicherte Änderungen gelten sofort, ohne Neustart.“ und „Mit KeePass stehen API-Key und URL im Eintrag; hier steht nur sein Titel.“ Rechts oben sitzt das Comic-✕ zum Schließen.
- **Links** steht die Liste der Kategorien (Reiter) in dieser Reihenfolge: „KI-Dienst“, „KeePass“, „Netzwerk & Agent“, „Modelle“, „Sprachausgabe“, „Index“, „Tools“, „Darstellung“, „Technische Details“.
- **Rechts** zeigt der Dialog den Inhalt der gewählten Kategorie, gegliedert in farbig abgesetzte Bereiche. Lange Seiten scrollen senkrecht.
- **Unten** erscheint bei Bedarf eine rote Platte mit Problemen und ganz unten die Fußzeile: links „Einstellungsordner öffnen“ und „Offline-Paket erstellen …“, rechts „Einrichtungsassistent“ (nur wenn du den Dialog aus dem Hauptfenster geöffnet hast), „Abbrechen“ und „Speichern“.

Einige Reiter laden beim ersten Anzeigen Daten im Hintergrund nach: „Modelle“ fragt die Modellquellen ab, „Sprachausgabe“ ebenfalls (für die TTS-Liste), „Tools“ liest bei jedem Anzeigen die aktuelle Werkzeugliste, und „Technische Details“ liest bei jedem Anzeigen das Protokoll neu.

## Speichern

„Speichern“ (Tooltip: „Konfiguration schreiben; gilt sofort“) geht in drei Schritten vor:

1. **Prüfen:** Der Entwurf aus allen Reitern wird mit der vorhandenen Datei zusammengeführt und durch dieselbe Prüfung geschickt, die auch beim Programmstart läuft. Dazu gehört auch die Vertrauensregel für TLS; eine fehlende oder leere CA-Datei fällt also schon hier auf.
2. **Schreiben:** Nur wenn die Prüfung keine Probleme findet, schreibt der Dialog die Datei. Er ändert dabei nur die Schlüssel, die er selbst verwaltet. Kommentare, Reihenfolge und alle Feineinstellungen, für die es kein Feld gibt, bleiben unverändert. Die Datei wird atomar ersetzt, sodass nie eine halb geschriebene Datei entsteht.
3. **Übernehmen:** Danach schließt der Dialog, und das Programm lädt die Einstellungen im Hintergrund neu.

Wissensquellen schreibt der Dialog nie. Sie werden in der Seitenleiste unter „Wissensquellen“ verwaltet; der Dialog trägt sie nur unverändert mit. So kann eine fehlerhafte Quelle beim Speichern anderer Einstellungen nicht versehentlich umgeschrieben werden. Mehr dazu in [Wissensquellen im Überblick](help:knowledge.overview).

### Probleme beim Speichern

Findet die Prüfung Probleme, bleibt der Dialog offen. Unter den Reitern erscheint eine rote Bereich „Bitte korrigieren:“ mit einer Zeile je Problem. Jede Zeile nennt den Feldnamen, in Klammern den Schlüssel der Konfigurationsdatei und die Erwartung, zum Beispiel „Embedding-Dimension (embedding.dimension): fehlt (Pflichtangabe, z. B. 768)“. Werte selbst werden nie angezeigt.

Der Dialog springt außerdem zum Reiter des ersten Problems: Schlüssel unter `security.` führen zu „KeePass“, `network.`, `agent.` und `ui.` zu „Netzwerk & Agent“, Chat-Modell, Embedding-Modell und `model.` zu „Modelle“, das Indexverzeichnis zu „Index“. Alle übrigen Probleme (auch die Limits der Wissenssuche) zeigen den Reiter „KI-Dienst“; such das Feld dann anhand des genannten Feldnamens.

Scheitert das Schreiben selbst (zum Beispiel weil der Ordner schreibgeschützt ist), steht in der roten Bereich „Speichern fehlgeschlagen:“ mit dem technischen Grund.

## Abbrechen und Schließen

„Abbrechen“ (Tooltip: „Änderungen verwerfen“) schließt den Dialog, ohne etwas zu schreiben. Dasselbe passiert mit dem ✕ rechts oben (Tooltip „Abbrechen“), mit der Escape-Taste und beim Schließen des Fensters. Alles, was du seit dem Öffnen geändert hast, ist dann verworfen.

Ausnahme: Einträge, die du im Reiter „KeePass“ unter „Passwörter“ oder über „Neu“ neben einem KeePass-Eintrag anlegst, änderst oder löschst, schreibt der Dialog sofort in KeePass. Abbrechen nimmt sie nicht zurück.

## Einstellungsordner öffnen

Der Knopf „Einstellungsordner öffnen“ links in der Fußzeile (Tooltip: „Den Ordner mit Konfiguration und Protokollen im Dateimanager öffnen“) öffnet den Ordner, in dem die Konfigurationsdatei liegt, im Dateimanager deines Systems. Dort liegen auch die Protokolle (Unterordner `logs`), der Index (Unterordner `index`, sofern du kein anderes Indexverzeichnis eingestellt hast) und der Pairing-Schlüssel für KeePass.

- Fehlt der Ordner, legt der Dialog ihn an.
- Kann das System keine Ordner öffnen oder schlägt das Öffnen fehl, erscheint ein Hinweis mit dem Grund und dem vollständigen Pfad in einem Textfeld, aus dem du ihn kopieren kannst.
- Derselbe Knopf steht noch einmal im Reiter „Technische Details“.

Wo genau der Ordner liegt, beschreibt [Die Konfigurationsdatei](help:settings.config-file).

## Offline-Paket erstellen

Der Knopf „Offline-Paket erstellen …“ direkt rechts neben „Einstellungsordner öffnen“ (Tooltip: „Packt alle installierten lokalen KI- und Sprachmodelle in ein ZIP für eine Offline-Installation.“) packt alles, was auf diesem Rechner lokal installiert ist, in ein einziges ZIP: die OpenNLP-Modelle der Textanalyse und alle vollständig installierten lokalen Modelle (Spracheingabe, Sprachausgabe, Embedding- und Chat-Modelle), je Modell den ganzen Ordner aus dem Modellverzeichnis. Halb geladene Downloads kommen nicht hinein.

1. Klicke „Offline-Paket erstellen …“. Ein Speichern-Dialog schlägt den Ordner des Jars und den Namen `enterprise-ai-client-resources-yyyyMMdd-HHmmss.zip` vor (Datum und Uhrzeit).
2. Während des Packens zeigt ein dünner Balken in der Fußzeile die Datei und den Fortschritt. Das kleine Kreuz daneben bricht ab; dann bleibt keine Datei zurück.
3. Danach steht neben dem Knopf, was im Paket ist und wie groß es ist.

Das ZIP wird nicht komprimiert (Modelle lassen sich kaum packen, so geht es schneller) und erst unter seinem Namen sichtbar, wenn es vollständig ist. Ein Manifest im ZIP nennt Größe und SHA-256 jeder Datei.

**Auf dem Zielrechner:** Lege das ZIP neben das Jar (oder ins Anwendungsverzeichnis). Der Einrichtungsassistent und Einstellungen → Modelle → „Lokale Modelle“ zeigen dann bei den enthaltenen Modellen „Im Offline-Paket“, und „Installieren“ bzw. „Alles installieren“ entpackt sie ohne Netz. Jede Datei wird dabei gegen Größe und SHA-256 aus dem Manifest geprüft. Auch Modelle, die du auf dem Quellrechner frei gesucht hast, erscheinen so. Ein so installiertes Modell verhält sich wie ein heruntergeladenes: „Aktualisieren“ lädt es neu vom Hub, „Entfernen“ löscht es. Liegen mehrere Pakete im Ordner, gilt das mit dem größten Namen, also das neueste.

## Der Knopf „Einrichtungsassistent“

Wenn du den Dialog über das Zahnrad geöffnet hast, steht rechts in der Fußzeile zusätzlich „Einrichtungsassistent“ (Tooltip: „Den Einrichtungsassistenten erneut öffnen (KeePass, KI-Dienst, Modelle, Confluence)“). Er wirkt wie „Abbrechen“: der Dialog schließt ohne zu speichern, danach öffnet sich Merlins Einrichtungsassistent als Overlay über dem Hauptfenster. Speichere also vorher, wenn du Änderungen behalten willst. Der Assistent selbst ist in [Einrichtung mit Merlin](help:getting-started.setup) beschrieben.

## Wirkung ohne Neustart

Nach dem Speichern lädt das Programm die Datei neu und übernimmt die Werte, ohne dass du es neu starten musst. Das gilt unter anderem für den KI-Dienst (Adresse, KeePass-Eintrag, Modelle), Proxy, TLS und User-Agent, die KeePass-Anbindung, die Proxy-Anmeldung, das Indexverzeichnis, den System-Prompt, den Fenstertitel, Sprachausgabe und Spracheingabe, die Werkzeuge, die Blasenfarben und die Limits der Wissenssuche (ab der nächsten Frage). Ein geändertes Agent-Kommando gilt ab der nächsten Agent-Sitzung.

Es gibt zwei Dinge, die du beachten musst:

- **„Prefer IPv6“** im Reiter „Netzwerk & Agent“ liest Java nur beim Start; diese Einstellung wirkt erst nach einem Neustart.
- **Ein anderes Embedding-Modell** gilt sofort, nutzt aber einen eigenen Bereich im Index. Indexiere deine Wissensquellen danach neu, sonst findet die semantische Suche nichts. Siehe [Indexieren](help:knowledge.indexing).

Kann das Programm die gespeicherten Einstellungen nicht übernehmen, erscheint die Meldung „Einstellungen sind gespeichert, konnten aber nicht übernommen werden: …“; der bisherige Stand bleibt dann in Kraft.

## Beim ersten Start

Beim allerersten Start gibt es noch keine Konfigurationsdatei. Dann öffnet sich nicht der Einstellungen-Dialog, sondern Merlins Einrichtungsassistent. Erst sein „Fertig“ schreibt die Datei; brichst du ab, wird nichts geschrieben, und der nächste Start öffnet den Assistenten erneut. Siehe [Einrichtung mit Merlin](help:getting-started.setup).

Der Dialog kennt zusätzlich eine eigene Erststart-Fassung: Dort heißt der Hinweis oben „Willkommen. Für den ersten Start fehlen noch der KeePass-Eintrag mit API-Key und URL sowie die Embedding-Dimension (Reiter KI-Dienst) und Chat- und Embedding-Modell (Reiter Modelle).“, „Speichern“ schreibt die Konfiguration und startet die Anwendung, und statt „Abbrechen“ steht ein roter Knopf „Beenden“ („Ohne Konfiguration beenden“). Hinweis: Im normalen Programmstart mit Bildschirm übernimmt der Einrichtungsassistent diese Aufgabe; die Erststart-Fassung des Dialogs ist nur ein Rückfall und in den üblichen Abläufen nicht zu sehen.

Öffnet sich der Dialog beim Start wegen einer fehlerhaften Datei, beendet „Abbrechen“ den Start: das Programm meldet, dass die Konfiguration nicht korrigiert wurde, und läuft nicht weiter.

## Die Reiter im Einzelnen

- [KI-Dienst](help:settings.ai-service): Zugang zur Enterprise-API, KeePass-Eintrag, System-Prompt, Embeddings, „Verbindung testen“.
- [KeePass](help:settings.keepass): KeePassRPC, Pairing, „In KeePass prüfen“, Passwortverwaltung.
- [Netzwerk & Agent](help:settings.network): Fenstertitel, Proxy, TLS, HTTP, Zeitlimits und Wiederholungen, Agent-Modus, Vorabladen.
- [Modelle](help:settings.models): ein Modell je Funktion, Cloud-Modelle und lokale Modelle.
- [Sprachausgabe](help:settings.speech): Name des Assistenten, TTS-Modell und Stimme, Vorlesen, Merlins Stimme.
- [Index](help:settings.index): Indexverzeichnis und Grenzen der Wissenssuche.
- [Tools](help:settings.tools): Werkzeuge an- und abschalten, Werkzeugrunden.
- [Darstellung](help:settings.appearance): Farben der Sprechblasen.
- [Technische Details](help:settings.technical-details): Protokoll, Konfiguration prüfen, Merlin.
- [Die Konfigurationsdatei](help:settings.config-file): wo sie liegt und was darin steht.

## Typische Probleme

### „Speichern“ tut scheinbar nichts

Schau unter die Reiter: Steht dort die rote Bereich „Bitte korrigieren:“, hat die Prüfung etwas gefunden, und es wurde nichts gespeichert. Korrigiere die genannten Felder und speichere erneut. Ein schneller Weg, ohne zu speichern zu prüfen, ist „Konfiguration prüfen“ im Reiter „Technische Details“.

### Meine Änderungen sind nach dem Schließen weg

Du hast vermutlich mit ✕, Escape oder „Abbrechen“ geschlossen, oder den Knopf „Einrichtungsassistent“ benutzt. Alle diese Wege verwerfen den Entwurf. Nur „Speichern“ schreibt.

### Die Einstellungen öffnen sich nicht, stattdessen kommt eine Fehlermeldung

Ist die Konfigurationsdatei nicht lesbar (etwa gesperrt oder ohne Leserechte), meldet das Programm „Die Konfiguration unter … ist nicht lesbar“ und öffnet den Dialog nicht. Prüfe die Datei und ihre Rechte; den Pfad nennt die Meldung. Siehe auch [Fehlersuche bei der Einrichtung](help:troubleshooting.setup).
