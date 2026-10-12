---
id: getting-started.setup
title: Der Einrichtungsassistent
keywords: Einrichtungsassistent, Einrichtung, Assistent, Setup, Setup-Wizard, Wizard, Erststart, erster Start, Ersteinrichtung, Merlin, Zauberer, Merlins Tochter, Übergabe, Schritte, Phasen, Schrittleiste, Überspringen, Weiter, Zurück, Fertig, Abbrechen, Abschlussprüfung, Spracheingabe, Download, Modell installieren, erneut starten, neu einrichten, onboarding, first run
---

# Der Einrichtungsassistent

Der Einrichtungsassistent führt dich beim ersten Start Schritt für Schritt durch alles, was der Enterprise AI Client zum Arbeiten braucht: die Verbindung zu KeePass, den KI-Dienst, die Modelle, optional Confluence als Wissensquelle und am Ende eine Prüfung, ob wirklich alles funktioniert. Begleitet wirst du dabei von Merlin, einem Zauberer mit Sprechblase und, wenn verfügbar, eigener Stimme. Dieses Kapitel beschreibt den Assistenten als Ganzes; die einzelnen Schritte haben eigene Kapitel. Du kannst den Assistenten später jederzeit wieder aufrufen, etwa um den KeePass-Eintrag oder die Modelle zu wechseln.

## Wann der Assistent erscheint

Der Assistent öffnet sich automatisch, wenn es noch keine Konfigurationsdatei gibt, also beim allerersten Start. Er erscheint als eigenes, rahmenloses Fenster im Comic-Stil mit der Überschrift „Willkommen beim Enterprise AI Client: Einrichtung“. Am abgedunkelten Rand kannst du das Fenster mit der Maus verschieben.

Ob die Einrichtung abgeschlossen ist, merkt sich der Client getrennt von der Konfigurationsdatei. Daraus ergeben sich drei Fälle:

- Es gibt keine Konfigurationsdatei: Der Assistent öffnet sich immer.
- Die Einrichtung wurde begonnen, aber nicht mit „Fertig“ (bzw. „Überspringen“ im letzten Schritt) beendet: Beim nächsten Start öffnet sich der Assistent erneut, und zwar mit den Werten, die schon gespeichert sind.
- Die Einrichtung ist abgeschlossen oder es handelt sich um eine ältere, bestehende Installation: Der Client startet direkt.

Ohne Bildschirm (etwa auf einem Server ohne grafische Oberfläche) gibt es keinen Assistenten. Dann legt der Client nur eine kommentierte Vorlage der Konfigurationsdatei an und beendet sich mit einem Hinweis.

## Die Schritte im Überblick

Oben im Assistenten steht die Schrittleiste mit sechs Phasen:

1. „KeePass“: Verbindung zu KeePass herstellen, siehe [KeePass verbinden](help:getting-started.keepass).
2. „KI-Dienst“: KeePass-Eintrag des KI-Dienstes wählen und die Verbindung testen, siehe [KI-Dienst einrichten](help:getting-started.ai-service).
3. „Modelle“: Chat-Modell, Embedding-Modell und Embedding-Dimension, siehe [Modelle auswählen](help:getting-started.models).
4. „Confluence“: optional Confluence als Wissensquelle, siehe [Confluence im Assistenten](help:getting-started.confluence).
5. „Abschluss“: die Abschlussprüfung, [siehe unten](help:getting-started.setup#die-abschlusspruefung).
6. „Spracheingabe“: optional ein lokales Modell für die Spracheingabe, [siehe unten](help:getting-started.setup#lokale-spracheingabe-installieren).

Jeder Schritt zeigt eine Überschrift, einen kurzen Erklärtext, die Eingabefelder und darunter Statuszeilen. Geht etwas schief, erscheint unten eine rot markierte Fehlerplatte mit der Ursache. In den Schritten „KI-Dienst“ und „Confluence“ steht in dieser Fehlerplatte zusätzlich der Knopf „Netzwerkeinstellungen…“, mit dem du Proxy und Zertifikate anpassen kannst, ohne den Assistenten zu verlassen.

Properties-Schlüssel der Konfigurationsdatei zeigt der Assistent nirgends, auch nicht in Fehlermeldungen. Dort stehen immer die Feldnamen, die du auf dem Bildschirm siehst.

## Bedienung: Weiter, Zurück, Überspringen, Abbrechen

Unten im Assistenten liegen die Knöpfe:

- „Weiter“ geht zum nächsten Schritt, aber nur, wenn der aktuelle Schritt erfüllt ist. Erfüllt heißt: KeePass ist gekoppelt und die Eintragsliste lesbar; der Verbindungstest zum KI-Dienst war erfolgreich; Chat-Modell, Embedding-Modell und eine positive Dimension stehen fest; der Confluence-Test war erfolgreich und ein Zertifikat ist gewählt oder „Ohne Zertifikat fortfahren“ angehakt; die Abschlussprüfung ist komplett grün.
- „Zurück“ geht einen Schritt zurück.
- „Überspringen“ steht in jedem Schritt und geht ohne Prüfung zum nächsten. Der Tooltip sagt es genau: „Ohne Prüfung zum nächsten Schritt; Eingaben bleiben erhalten“. Ein übersprungener Schritt gilt nicht als erledigt.
- „Abbrechen“ beendet den Assistenten ohne Abschluss. Das Gleiche bewirken das ✕ oben rechts und die Taste Escape.

Du kannst außerdem direkt auf einen Namen in der Schrittleiste klicken (Tooltip „Zu „…“ springen“). Auch das springt ohne Prüfung; deine Eingaben bleiben stehen. Erledigte Schritte sind in der Leiste markiert.

Solange eine Abfrage läuft (KeePass wird gelesen, die Verbindung getestet, Modelle abgefragt, ein Download läuft), sind Springen und „Zurück“ gesperrt. So landet das Ergebnis einer Abfrage nie im falschen Schritt. Der Mauszeiger zeigt dann eine Sanduhr.

### Der Knopf „Weiter“ bleibt grau

Das heißt immer: Der aktuelle Schritt ist noch nicht erfüllt. Prüfe die Statuszeilen und die rote Fehlerplatte. Typische Gründe:

- Im Schritt „KeePass“ wurde „Mit KeePass verbinden“ noch nicht erfolgreich ausgeführt.
- Im Schritt „KI-Dienst“ fehlt im KeePass-Eintrag API-Key oder Basis-URL, oder „Verbindung testen“ ist noch nicht grün.
- Im Schritt „Modelle“ ist die Embedding-Dimension leer oder keine positive ganze Zahl.
- Im Schritt „Confluence“ ist weder ein Client-Zertifikat gewählt noch „Ohne Zertifikat fortfahren“ angehakt.

Wenn du einen Schritt bewusst auslassen willst (etwa Confluence), nimm „Überspringen“.

## Merlin, der Zauberer

In jedem Schritt steht Merlin im freien Platz unter den Feldern, je Schritt mit einem passenden Gegenstand (Schlüssel, Kristallkugel, Karten, Buch, Haken, Mikrofon). Neben ihm erscheint seine Sprechblase. Merlin ist bewusst ein Begleiter mit festen Texten: Was er zu den Schritten sagt, ist im Programm hinterlegt und stimmt deshalb immer, unabhängig vom KI-Modell.

So verhält er sich:

- Solange der KI-Dienst noch nicht verbunden ist, ist Merlin weitgehend still. Nach einer erfolgreichen KeePass-Kopplung bestätigt er das kurz in seiner Sprechblase.
- Sobald der Verbindungstest zum KI-Dienst klappt, stellt er sich vor („Ich bin Merlin, dein persönlicher Assistent …“) und meldet, dass der KI-Dienst antwortet.
- Ab dann sagt er beim Betreten jedes Schritts einen kurzen Satz und reagiert auf Fehler mit einer kurzen Ermutigung. Die technische Fehlermeldung selbst steht immer in der roten Fehlerplatte, nie in seiner Sprechblase.

Passt Merlin in einem Schritt nicht vollständig in den freien Platz (kleines Fenster, lange Fehlermeldung), wird er nicht angezeigt. Er wird nie abgeschnitten und liegt nie über Feldern. Vergrößere das Fenster, wenn du ihn vermisst.

### Merlin anklicken

Ein Klick auf Merlin öffnet ein Menü:

- „Was muss ich hier machen?“: eine feste Anleitung zum aktuellen Schritt.
- „Warum brauche ich das?“: eine feste Begründung, wozu der Schritt dient.
- „Diesen Schritt überspringen“: wie der Knopf „Überspringen“.
- „Eigene Frage stellen …“: öffnet unter der Sprechblase ein Feld „Frag Merlin …“. Enter schickt die Frage ab, Merlin zeigt „Moment, ich denke kurz nach …“ und antwortet dann.

Die eigene Frage geht erst, wenn der KI-Dienst verbunden und ein Chat-Modell gewählt ist (der Menüpunkt sagt das im Tooltip). Die Frage wird als einzelner Aufruf an das gewählte Chat-Modell geschickt, ohne Werkzeuge und ohne Verlauf. Merlin bekommt dafür nur seine Rolle, den aktuellen Schritt und die festen Hinweise zu diesem Schritt mit, keine Zugangsdaten. Antwortet das Modell nicht, sagt Merlin, dass ihm gerade nichts einfällt.

### Merlins Stimme

Merlin hat eine eigene Stimme, getrennt von der Stimme, mit der der Chat Antworten vorliest. Vorgegeben ist ein Sprachausgabe-Modell des KI-Dienstes mit einem festen männlichen Sprecher. Einstellbar ist das unter Einstellungen → Sprachausgabe im Bereich „Merlin“ mit den Feldern „Stimme von Merlin“ und „Sprecher von Merlin“, siehe [Sprachausgabe einstellen](help:settings.speech).

Ist diese Stimme nicht erreichbar (der KI-Dienst bietet das Modell nicht an, keine Verbindung), spricht Merlin nicht, sondern zeigt seine Sätze nur als Sprechblase. Es gibt keinen Rückfall auf eine andere Stimme. Der Assistent hängt nie von der Sprachausgabe ab.

Beim allerersten Start gibt es anfangs noch keinen KI-Dienst, also auch keine Stimme: Merlin zeigt bis dahin nur Sprechblasen. Sobald der Test des KI-Dienstes erfolgreich war, holt er sich seine eigene Stimme und spricht ab dann hörbar, beginnend mit seiner Erfolgsmeldung. Rufst du den Assistenten später aus den Einstellungen auf, spricht Merlin von Anfang an mit seiner Stimme, sofern sie verfügbar ist.

## Die Abschlussprüfung

Wenn du den Schritt „Abschluss“ betrittst, speichert der Assistent die Konfiguration und prüft sie sofort. Der API-Key landet dabei nie in der Konfigurationsdatei. Hast du Confluence erfolgreich getestet, wird zusätzlich eine Wissensquelle „Confluence“ angelegt und aktiviert. Die Statuszeile zeigt währenddessen „Speichere und prüfe …“.

Geprüft werden bis zu neun Punkte, jeder mit Haken oder Kreuz. Seit 0.48.2 hängen die Punkte nicht voneinander ab: Ein fehlendes Modell färbt nur seinen eigenen Punkt und „Konfiguration lässt sich vollständig laden“ rot, nicht auch noch „KI-Dienst ist erreichbar“. Der API-Key wird direkt aus dem gewählten KeePass-Eintrag gelesen.

- „KeePass ist gekoppelt“ (mit Anzahl der lesbaren Einträge)
- „KI-Dienst ist erreichbar“
- „API-Key kann gelesen werden“
- „Chat-Modell ist gesetzt“
- „Embedding-Modell ist gesetzt“
- „Embedding-Dimension ist bekannt“
- „Confluence-Zugang funktioniert“ (nur, wenn Confluence eingerichtet wurde)
- „Confluence-Quelle ist gespeichert und aktiviert“ (nur, wenn Confluence eingerichtet wurde)
- „Konfiguration lässt sich vollständig laden“

Solange die Grundeinstellungen unvollständig sind, schreibt die Prüfung nichts. Die Confluence-Quelle meldet dann „Noch nicht gespeichert, weil die Grundeinstellungen unvollständig sind.“; das ist kein Confluence-Fehler.

Ein rotes Kreuz trägt in Klammern einen gekürzten Grund; der vollständige Grund steht im Tooltip der Zeile und in der Fehlerplatte. Ist alles grün, steht dort „Alles betriebsbereit.“ bzw. bei übersprungenem Confluence „Betriebsbereit. Confluence ist übersprungen und lässt sich später in der Seitenleiste unter Wissensquellen einrichten.“

Ist etwas rot: Klicke in der Schrittleiste auf den betroffenen Schritt, korrigiere ihn, komm zurück und klicke „Erneut prüfen“. Erst wenn alles grün ist, geht es mit „Weiter“ zum letzten Schritt.

## Lokale Spracheingabe installieren

Der letzte Schritt „Lokale Spracheingabe“ ist optional. Hier kannst du ein Modell installieren, mit dem du dem Client Fragen diktieren kannst, ohne dass deine Stimme deinen Rechner verlässt. Das Modell wird einmal heruntergeladen und läuft danach im Client selbst.

1. Beim Betreten sucht der Assistent passende Modelle („Suche lokale Modelle für die Spracheingabe …“). In der Liste „Modell“ stehen zuerst bereits installierte Modelle (mit dem Zusatz „(installiert)“), dann Vorschläge zum Herunterladen.
2. Wähle ein Modell. Ist es noch nicht installiert, klicke „Installieren“. Ein Fortschrittsbalken und die Statuszeile zeigen den Download, etwa „Datei 2 von 5: … – 40 % von 1200,0 MB“.
3. Nach der Installation ist das neue Modell automatisch gewählt. „Fertig“ setzt es als Modell für die Spracheingabe und schließt die Einrichtung ab.

Wichtig zu wissen:

- Die vorgeschlagenen Modelle sind groß (mehrere Gigabyte Download und Arbeitsspeicher). Ein Download kann lange dauern.
- Während des Downloads sind „Zurück“, die Schrittleiste, die Modellauswahl und „Fertig“ gesperrt. Rechts neben dem Fortschritt steht ein kleines Kreuz (Tooltip „Download abbrechen“), nur solange ein Download läuft. Es bricht den Transfer wirklich ab („Breche den Download ab …“, danach „Download abgebrochen. Das Modell wurde nicht installiert.“). Das gilt seit 0.48.2 ebenso für „Überspringen“ und das Schließen des Assistenten.
- Ein Abbruch ist kein Fehler. Die angefangene Datei bleibt liegen; ein späteres „Installieren“ setzt den Download dort fort. Als installiert gilt das Modell erst nach vollständigem, geprüftem Download. War der Download beim Klick schon fertig, bleibt das Modell installiert.
- Scheitert die Installation, nennt die Fehlerplatte den Grund, und „Erneut versuchen“ startet sie neu. Lassen sich die Angebote gar nicht ermitteln, lädt „Erneut versuchen“ die Liste neu.
- Der Knopf „Abbrechen“ ist in diesem Schritt ausgeblendet. Nimm hier „Überspringen“, wenn du ohne Spracheingabe fertig werden willst. Das ✕ und Escape brechen den Assistenten dagegen ab, und beim Erststart öffnet er sich dann beim nächsten Start erneut.
- „Überspringen“ geht in diesem Schritt erst, wenn die Abschlussprüfung bestanden ist. Die Grundeinrichtung ist zu diesem Zeitpunkt schon gespeichert.

Die Spracheingabe lässt sich jederzeit später nachholen, unter Einstellungen → Modelle → „Lokale Modelle“. Dort kannst du auch lokale Stimmen für die Sprachausgabe herunterladen. Details: [Modelle einstellen](help:settings.models) und [Diktieren](help:chat.dictation).

## Die Übergabe an Merlins Tochter

Beim ersten erfolgreichen Abschluss der Einrichtung spielt eine kurze Übergabe: Merlin übergibt an seine Tochter, den Chat-Assistenten, der dir ab jetzt antwortet. Die Zeilen erscheinen nacheinander in der Sprechblase, jeweils mit dem Namen des Sprechers im Kopf der Blase:

- Merlin: „Damit wären wir fertig. Ab hier übernimmt meine Tochter.“
- Der Chat-Assistent: „Danke, Dad. Du wirst jetzt nicht mehr gebraucht. Wir haben jetzt KI.“
- Merlin: „Natürlich, mein Kind.“
- Merlin: „Ruf mich einfach, wenn die KI nicht weiterweiß.“

Merlin spricht mit seiner eigenen Stimme, der Chat-Assistent mit der Stimme, die für das Vorlesen von Antworten gewählt ist, und unter seinem Anzeigenamen (Standard „Merlins Tochter“). Die nächste Zeile kommt erst, wenn die vorige zu Ende gesprochen ist. Fehlt eine Stimme, bleibt die Zeile eine Lesepause lang stehen (etwa 60 Millisekunden je Zeichen, mindestens 1,8 Sekunden). Nach der letzten Zeile schließt sich der Assistent und das Hauptfenster öffnet sich.

Während der Übergabe sind alle Knöpfe gesperrt. Die Übergabe spielt nur beim ersten erfolgreichen Abschluss, nicht bei jedem späteren Aufruf aus den Einstellungen.

## Den Assistenten erneut starten

Du kannst den Assistenten jederzeit wieder öffnen:

- Einstellungen öffnen (Zahnrad im Fuß der Seite „Chats“ in der Seitenleiste, die du über das Menü-Symbol oben links aufklappst) und unten auf „Einrichtungsassistent“ klicken, oder
- in den Einstellungen den Reiter „Technische Details“ wählen und „Einrichtungsassistent erneut starten“ klicken.

Der Einstellungen-Dialog wird dabei wie mit „Abbrechen“ verworfen; ungespeicherte Änderungen darin gehen also verloren. Der Assistent legt sich als Overlay über das ganze Hauptfenster, egal ob gerade Chat, Agent oder Lesebereich vorne liegt. Er startet mit den Werten deiner Konfigurationsdatei, Merlin begrüßt dich mit „Da bist du ja wieder.“ statt sich neu vorzustellen.

Abbrechen schließt nur das Overlay; die Anwendung läuft unverändert weiter. „Fertig“ speichert, übernimmt die Einstellungen sofort ohne Neustart und bindet eine neu eingerichtete Confluence-Quelle gleich an.

Zum Nachsehen ohne Assistent: Im Reiter „Technische Details“ findest du auch „Konfiguration prüfen“, „Verbindung testen“ und „Einstellungsordner öffnen“, siehe [Technische Details](help:settings.technical-details).

## Was im Hintergrund passiert

- Der Assistent schreibt die Konfigurationsdatei im Anwendungsverzeichnis (unter Linux und macOS `~/.enterprise-ai-client/`, unter Windows `%APPDATA%\.enterprise-ai-client\`). Geschrieben wird erst in der Abschlussprüfung. Brichst du vorher ab, entsteht keine Datei.
- Secrets (API-Key, Confluence-Passwort oder -Token) stehen nie in der Datei, sondern nur in KeePass. In der Datei stehen nur die Titel der KeePass-Einträge.
- Der Kopplungsschlüssel für KeePass wird immer dauerhaft in einer Datei im Anwendungsverzeichnis abgelegt, damit du nicht bei jedem Start neu koppeln musst.
- Der Stand der Einrichtung steht im Anwendungszustand unter `setup.completed`. Nur ein erfolgreicher Abschluss setzt ihn auf `true`.
- Die Abschlussprüfung schreibt jeden Prüfpunkt ins Protokoll (Zeilen „Einrichtung: …“), ohne Secrets.

## Typische Probleme

Die häufigsten Fragen zum Assistenten und wie du sie prüfst.

### Der Assistent öffnet sich bei jedem Start wieder

Die Einrichtung wurde nicht abgeschlossen: Du hast abgebrochen, das ✕ geklickt oder Escape gedrückt, bevor „Fertig“ bzw. im letzten Schritt „Überspringen“ ausgeführt wurde. Gehe den Assistenten bis zum Ende durch. Deine gespeicherten Werte sind schon vorausgefüllt.

### Nach „Abbrechen“ startet die Anwendung nicht

Beim Erststart heißt Abbrechen: Es gibt (noch) keine fertige Einrichtung, deshalb startet das Hauptfenster nicht. Starte den Client neu und schließe den Assistenten ab.

### Die Abschlussprüfung bleibt rot

Lies den Grund im Tooltip der roten Zeile. Häufig: KeePass ist inzwischen gesperrt, der KI-Dienst ist nicht erreichbar (Proxy, Zertifikat) oder die Dimension fehlt. Hilfe: [Probleme bei der Einrichtung](help:troubleshooting.setup), [Netzwerkprobleme](help:troubleshooting.network), [KeePass-Probleme](help:troubleshooting.keepass).

### Merlin spricht nicht

Das ist kein Fehler: Ohne erreichbare Stimme bleibt es bei Sprechblasen. Beim Erststart spricht er erst nach dem erfolgreichen Test des KI-Dienstes. Prüfe sonst „Stimme von Merlin“ unter Einstellungen → Sprachausgabe und ob der KI-Dienst dieses Modell anbietet. Siehe [Probleme mit der Sprachausgabe](help:troubleshooting.speech).

### „Eigene Frage stellen …“ ist ausgegraut

Merlin kann frei nur antworten, wenn der KI-Dienst verbunden und ein Chat-Modell gewählt ist. Schließe zuerst die Schritte „KI-Dienst“ und „Modelle“ ab.
