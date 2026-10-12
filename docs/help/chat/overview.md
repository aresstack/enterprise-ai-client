---
id: chat.overview
title: Der Chat im Überblick
keywords: Chat, Hauptfenster, Verlauf, Composer, Eingabefeld, Nachricht senden, Senden, Stop, Stopp, abbrechen, Streaming, Sprechblase, Blase, Antwort, Merlins Tochter, Anzeigename, Assistent, Ressourcen, Verweis, Drag and Drop, ziehen, Recherchekarte, Werkzeugwolke, Wolke, Fehler, Fehlerblase, Details anzeigen, Wiederholung, Retry, kopieren, Denkaufwand, Modell wählen, chat window, send, stop, bubble, error
---

# Der Chat im Überblick

Dieses Kapitel erklärt das Hauptfenster mit dem Chat: wo du schreibst, wie Antworten erscheinen, was die Wolken und Karten unter einer Antwort bedeuten und was passiert, wenn etwas schiefgeht. Es richtet sich an alle, die den Enterprise AI Client täglich benutzen. Einzelne Funktionen wie Dateianhänge, Quellenangaben, Zitieren, Vorlesen, Diktat und Export haben eigene Kapitel, auf die hier verwiesen wird.

## Aufbau des Hauptfensters

Das Fenster besteht aus drei Bereichen:

- **Kopfzeile** ganz oben: links der Hamburger-Knopf (drei Striche) mit dem Tooltip „Chats und Wissensquellen“, daneben die Modus-Pille („Chat“ bzw. „Agent“, sofern ein Agent eingerichtet ist), in der Mitte still der Fenstertitel aus der Konfiguration, rechts das Schließen-Kreuz. Über die Kopfzeile ziehst du das Fenster.
- **Drawer** links (eingeklappt, bis du ihn öffnest): Fährst du mit der Maus über den Hamburger, klappen Reiterleiste und Drawer auf; ein Klick rastet sie ein, ein weiterer Klick löst die Rastung. Ohne Rastung faltet der Drawer kurz nach dem Verlassen wieder weg. Er enthält die Reiter „Chats“, „Wissensquellen“ und „Favoriten“; im Fuß des Drawers sitzt das Zahnrad für die Einstellungen. Breite und Rastung merkt sich das Programm über einen Neustart hinweg. Mehr zum Reiter „Chats“ steht in [Chat-Verlauf und gespeicherte Chats](help:chat.history).
- **Chatbereich** rechts: oben der Verlauf mit den Sprechblasen, darunter gegebenenfalls eine Statuszeile der Wissensbasis (zum Beispiel der Fortschritt einer Indexierung mit einem Knopf „Abbrechen“), ganz unten der Composer, also die abgerundete Eingabefläche.

Ein leerer Chat zeigt mittig die Zeile „Neue Unterhaltung. Nachricht unten eingeben und Enter drücken.“

## Der Composer: Nachricht schreiben

Der Composer ist eine einzige abgerundete Fläche. Oben darin stehen vorgemerkte Anhänge, Seiten, Verweise und Zitate als kleine Chips, darunter das Eingabefeld mit dem Platzhalter „Nachricht…“, ganz unten eine Fußzeile mit Knöpfen.

Bedienung:

1. Klicke irgendwo in die Fläche; der Cursor springt ins Eingabefeld. Die Fläche wird weiß und bekommt einen blauen Rand, solange das Feld den Fokus hat.
2. Schreibe deine Frage. **Enter** sendet, **Umschalt+Enter** fügt eine neue Zeile ein.
3. Alternativ klickst du auf „Senden“ (blau, rechts unten).

Die Fußzeile enthält von links nach rechts:

- **Modellknopf**: zeigt das aktuelle Chat-Modell (ohne Auswahl „Modell“). Ein Klick öffnet die Liste der Chat-Modelle aus dem Modellkatalog; das gewählte ist mit einem Haken markiert. Am Ende steht „Modelle verwalten …“, das die Einstellungen öffnet. Wurden noch keine Modelle abgefragt, steht dort „Noch keine Chat-Modelle abgefragt“. Siehe [Modelle einstellen](help:settings.models).
- **Denkaufwand**: „Denken: Standard“, „Denken: niedrig“, „Denken: mittel“, „Denken: hoch“. Wirkt nur bei Modellen, die das unterstützen; die Wahl gilt ab der nächsten Nachricht.
- **„Ressourcen n“**: die Übersicht aller Dateien, Seiten und verwendeten Quellen dieses Chats, siehe [unten](help:chat.overview#ressourcen-und-verweise).
- im Agent-Modus mit eingebautem Recherche-Agenten zusätzlich **„Phase: …“** (siehe [Der Agent](help:agent.overview)).
- rechts: der **Pegelbalken** (nur während einer Aufnahme), die **Büroklammer** „Dateien anhängen“, das **Mikrofon** „Diktieren (Strg+Umschalt+M)“ (nur wenn eine Spracheingabe eingebunden ist) und genau **ein** Hauptknopf: „Senden“ oder „Stop“.

Über der Fläche steht eine Statuszeile, etwa „Antwort wird erstellt …“, „2 Anhänge vorgemerkt“ oder „1 Anhang, 2 Zitate vorgemerkt“. Mit dem kleinen Kreuz blendest du eine Meldung aus, bis eine andere Art Meldung kommt.

### Der Knopf „Senden“ bleibt grau

„Senden“ ist nur aktiv, wenn das Eingabefeld Text enthält (Leerzeichen allein zählen nicht) und gerade keine Antwort läuft. Eine Nachricht nur aus Anhängen ohne Text geht nicht; schreib wenigstens einen kurzen Satz dazu, zum Beispiel „Fasse die Datei zusammen.“

## Senden, Stoppen und Streaming

Nach dem Senden erscheint deine Nachricht sofort rechts als blaue Blase mit dem Kopf „Du“ und der Uhrzeit. Danach beginnt die Antwort. Sie wird **gestreamt**: Der Text kommt Stück für Stück an und erscheint laufend in der Antwortblase. Damit das Fenster auch bei langen Antworten flüssig bleibt, bündelt das Programm die ankommenden Stücke und rendert die Blase nur in kurzen Abständen neu. Diagramme (Mermaid) werden erst gezeichnet, wenn die Antwort fertig ist.

Solange eine Antwort läuft:

- wird aus „Senden“ der rote Knopf **„Stop“** (Tooltip „Antwort abbrechen“),
- sind Büroklammer, Denkaufwand und „Ressourcen“ gesperrt,
- lässt sich kein anderer Chat öffnen und kein neuer beginnen („+ Neuer Chat“ ist grau).

Ein Klick auf „Stop“ bricht die Antwort ab. Was bis dahin angekommen war, bleibt stehen; der Kopf der Blase lautet dann zum Beispiel „Merlins Tochter · abgebrochen“. Deine Frage bleibt im Verlauf. Läuft gerade noch die Wissenssuche, zeigt die Recherchekarte „Wird abgebrochen …“, die restlichen Suchschritte entfallen.

Der Verlauf rollt automatisch mit, solange du unten bist. Rollst du nach oben, um etwas nachzulesen, bleibt die Ansicht stehen.

## Sprechblasen und was sie bedeuten

- **Deine Nachrichten**: rechts, standardmäßig blau mit weißer Schrift, Kopf „Du“. Anhänge, Seiten, Verweise und Zitate stehen als Chips direkt darunter.
- **Antworten**: links, standardmäßig petrolfarben, Kopf mit dem Anzeigenamen des Assistenten (siehe nächster Abschnitt). Antworten werden als Markdown dargestellt: Überschriften, Listen, Tabellen, Codeblöcke, anklickbare Links (sie öffnen sich im Standardbrowser) und Mermaid-Diagramme. Ein Klick auf ein Diagramm öffnet einen Betrachter über dem Verlauf; das Kreuz oben rechts oder Esc schließt ihn.
- **Fehler**: links in Rot, Kopf „Fehler“, siehe [unten](help:chat.overview#fehlerblase-mit-details).
- **Hinweise des Programms**: keine Blase, sondern eine zentrierte, kursive Zeile, zum Beispiel „Keine passenden Abschnitte in der Wissensbasis gefunden. Die Antwort entstand ohne Kontext.“

Jede Blase hat in der Kopfzeile einen kleinen Kopierknopf (Tooltip „Nachricht kopieren“). Er legt den ursprünglichen Text in die Zwischenablage, bei Antworten den Markdown-Quelltext, ohne Kopf, Zeitstempel und Quellenliste; bei einem Fehler zusätzlich die vollständige Fehlermeldung. Danach zeigt er kurz einen Haken mit „Kopiert“.

Die Farben der eigenen Blasen und der Antwortblasen stellst du unter Einstellungen → „Darstellung“ ein; sie gelten nach dem Speichern sofort. Fehler, Hinweise und Wolken behalten ihre Farben. Siehe [Darstellung](help:settings.appearance).

## Der Anzeigename „Merlins Tochter“

Über jeder Antwort steht der Name des Assistenten. Ohne eigene Einstellung heißt er **„Merlins Tochter“**: Merlin ist der Zauberer, der dich durch den Einrichtungsassistenten führt, und seine Tochter übernimmt danach den Chat. Der Name ist reine Beschriftung:

- Das Sprachmodell erfährt ihn nicht; er ändert nichts an den Antworten.
- Ein Wechsel der Stimme der Sprachausgabe ändert ihn nicht.
- Er erscheint auch im Zitat-Export, im Markdown- und im Word-Export.

Ändern kannst du ihn unter Einstellungen → „Sprachausgabe“ ganz oben im Bereich „Assistent“, Feld „Name“. Daneben stellt „Standard“ den Standardnamen wieder her, und eine kleine Vorschau-Blase zeigt, wie es aussieht. Ein leeres Feld gilt beim Speichern als Standard. Nach dem Speichern werden auch die bereits sichtbaren Antworten sofort umbenannt.

Für Fortgeschrittene: In der Konfigurationsdatei heißt der Schlüssel `chat.assistant.displayName`.

## Ressourcen und Verweise

Der Knopf „Ressourcen n“ in der Fußzeile zählt alles, was schon zum aktuellen Chat gehört: angehängte **Dateien**, hinzugefügte **Seiten** aus dem Lesebereich und die von der Wissenssuche **verwendeten Quellen**. Ohne Ressourcen ist er grau.

Ein Klick öffnet die Übersicht, gruppiert nach „DATEIEN“, „SEITEN“ und „VERWENDETE QUELLEN“. Ein Klick auf einen Eintrag öffnet nichts und lädt nichts erneut hoch: Er legt nur einen **Verweis-Chip** mit dem Symbol der Art (Büroklammer, Dokument, Lupe) in den Composer. Mit der nächsten Nachricht erfährt das Modell dann ausdrücklich, dass du dich auf genau diese Ressource beziehst, samt ihrer Kennung; den Inhalt liest es bei Bedarf mit seinen Werkzeugen nach. Das Kreuz am Chip nimmt nur den Bezug zurück, die Ressource bleibt im Chat. Bereits vorgemerkte Einträge sind in der Übersicht ausgegraut.

Beispiel: Du hast vor zehn Nachrichten ein Handbuch angehängt und fragst jetzt „Was steht dort zu Kapitel 4?“. Wähle das Handbuch unter „Ressourcen“ aus, damit klar ist, welche Datei gemeint ist.

Seiten fügst du so hinzu: Im Lesebereich (Reiter „Wissensquellen“ oder „Favoriten“) Rechtsklick auf den Reiter einer Seite → „Zum aktuellen Chat hinzufügen“. Dann liegt ein Seiten-Chip mit dem Titel im Composer; das Modell bekommt nur Titel und Adresse (Bookmark) und liest die Seite bei Bedarf selbst.

## Dateien per Drag & Drop

Du kannst Dateien aus dem Explorer direkt auf den Composer ziehen. Solange Dateien darüber schweben, zeigt die Fläche einen gestrichelten blauen Rand. Beim Loslassen werden sie genauso vorgemerkt wie über die Büroklammer. Gezogen wird immer als Kopie, das Original im Explorer bleibt, wo es ist. Ordner werden übergangen, Text, den du ins Eingabefeld ziehst, wird wie gewohnt als Text eingefügt. Während eine Antwort läuft, nimmt der Composer keine Dateien an. Einzelheiten in [Dateien anhängen](help:chat.attachments).

## Aktivitäten unter der Frage: Recherchekarte und Werkzeugwolken

Während der Assistent arbeitet, zeigt der Verlauf, was gerade passiert, und zwar nicht in der Antwortblase, sondern in eigenen Wolken:

- **Recherchekarte** (gelbe Wolke „Recherche in den Wissensquellen“): erscheint, wenn Wissensquellen angehakt sind. Zuerst prüft das Modell, ob deine Nachricht überhaupt Wissen aus den Quellen braucht („Prüfe, ob eine Suche nötig ist …“). Bei einem Gruß, einer Frage an den Assistenten selbst oder zur Bedienung des Programms, einer Korrektur oder Beschwerde wird nicht gesucht; die Karte endet dann mit „keine Suche nötig“. Sonst sucht es mit einer kurzen, aus deiner Nachricht gebildeten Suchanfrage. Sie zählt live mit, zum Beispiel „Suche 1 / 3: „Begriff““, „Treffer: 11“, „Prüfe Relevanz von 14 Treffern …“, „2 geeignete Quellen (von 14 Treffern)“, „Evidenz ausreichend“ oder „Kontext reicht noch nicht, weitere Suche.“, dazu einen Fortschrittsbalken über die Suchen. Bei einer Live-Suche in den Quellen zeigt sie etwa „10 Treffer in den Quellen“ und „Lade 1/4 …“. Sobald die Antwort beginnt, platzt die Wolke und hinterlässt eine schmale Ergebniszeile, zum Beispiel „abgeschlossen: 2 Suchen, 11 Treffer, 6 Quellen im Kontext“. Fielen alle Suchen aus, endet sie rot mit „Wissenssuche ausgefallen“.
- **Werkzeugwolken**: Jeder Werkzeugaufruf des Modells bekommt eine eigene gelbe Wolke, etwa „Durchsucht Wissensquellen“ mit dem Suchbegriff, „Liest Seite“ mit dem Seitentitel, „Liest Anhang“ oder „Durchsucht Anhang“ mit Dateiname bzw. Suchbegriff, „Legt Anhang ab“ beim Hochladen neuer Dateien. Andere Werkzeuge heißen „Werkzeug“ plus Name. Ist der Aufruf fertig, platzt die Wolke und bleibt als Ergebniszeile stehen; schlug er fehl, ist die Zeile rot und endet auf „fehlgeschlagen“.
- **Gedankenwolken** (grün) zeigen Denkschritte; sie verschwinden nach dem Ende.

Die Antwortblase selbst erscheint erst, wenn der erste Antworttext ankommt. Die Wolken gehören zum laufenden Durchgang und werden nicht im gespeicherten Chat abgelegt; nach dem Wiederöffnen eines Chats siehst du sie nicht mehr. Für die Fehlersuche stehen die Werkzeugaufrufe aber im JSON-Export, siehe [Chat exportieren](help:chat.export). Mehr zur Suche in [Wie der Wissensindex funktioniert](help:concepts.knowledge-index).

Nach der Antwort kann eine Hinweiszeile folgen, zum Beispiel „Die Volltextsuche ist ausgefallen; es wurde nur semantisch gesucht.“ oder „Die Wissenssuche ist ausgefallen. Die Antwort entstand ohne Kontext aus der Wissensbasis.“ Sie sagt dir, ob die Antwort auf deiner Wissensbasis beruht oder nicht.

## Wiederholungen bei vorübergehenden Fehlern

Bricht die Verbindung zum KI-Dienst kurz ab, läuft eine Zeitüberschreitung ab, ist der Dienst ausgelastet (HTTP 429) oder meldet er einen Serverfehler (HTTP 5xx), wiederholt der Client die einzelne Modellanfrage automatisch, bevor er aufgibt. Standard sind **2 Wiederholungen**, die erste nach 500 ms, jede weitere 2,5-mal so lang (also 500 ms, dann 1250 ms). Davon merkst du meist nur eine kurze Verzögerung.

Wichtig dabei:

- Wiederholt wird nur, solange von dieser Anfrage noch **kein Antworttext sichtbar** ist. Danach würde ein Neustart Text doppelt zeigen; der Fehler geht dann unverändert an dich.
- Werkzeuge laufen durch eine Wiederholung **nie doppelt**; es wird genau dieselbe Anfrage mit denselben bisherigen Werkzeugergebnissen erneut gesendet.
- **Nie** wiederholt werden: falsche Anmeldedaten (401/403), eine vom Dienst abgelehnte Anfrage und ein API-Key, der nicht aus KeePass gelesen werden konnte (dafür hat KeePass schon eigene Wiederholungen).
- „Stop“ beendet auch eine laufende Pause zwischen zwei Versuchen sofort.

Einstellen kannst du das unter Einstellungen → „Netzwerk & Agent“ mit „Chat: Wiederholungen“ (0 bis 10, 0 = nie) und „Chat: Pause vor Wiederholung (ms)“. Für Fortgeschrittene: `chat.retryCount` und `chat.retryDelayMillis` in der Konfigurationsdatei.

## Fehlerblase mit Details

Scheitert eine Antwort endgültig, erscheint links eine rote Blase mit dem Kopf „Fehler“. Sie zeigt zuerst nur eine kurze Überschrift, zum Beispiel:

- „Der KI-Dienst ist nicht erreichbar.“
- „Anmeldung am KI-Dienst fehlgeschlagen. (Status 401)“
- „Der KI-Dienst ist ausgelastet. Bitte später erneut versuchen.“
- „Der KI-Dienst hat die Anfrage abgelehnt.“
- „Fehler im KI-Dienst.“
- „Der API-Key konnte nach mehreren Versuchen nicht aus KeePass gelesen werden. KeePassRPC war vorübergehend nicht verfügbar.“
- „Die Nachricht konnte nicht gesendet werden.“

Mit **„Details anzeigen“** klappst du in der Blase die technische Ursache („Technische Ursache: …“) und, wenn bekannt, einen Hinweis zur Abhilfe („Hinweis: …“, etwa zu Zertifikat, Proxy oder KeePass) auf; „Details ausblenden“ klappt sie wieder zu. Zugangsdaten stehen nie darin. Der Kopierknopf der Fehlerblase kopiert die komplette Meldung samt Details, praktisch für eine Rückfrage beim Support.

Fehlgeschlagene Antworten werden nicht im gespeicherten Chat abgelegt. Deine Frage bleibt erhalten; du kannst sie einfach erneut senden.

### Typische Prüfschritte bei Fehlern

1. Klappe „Details anzeigen“ auf und lies die technische Ursache.
2. Bei „nicht erreichbar“: Netzwerk und Proxy prüfen, siehe [Netzwerkprobleme](help:troubleshooting.network). In den Einstellungen gibt es unter „Technische Details“ den Knopf „Verbindung testen“.
3. Bei Anmeldefehlern: KeePass-Eintrag und API-Key prüfen, siehe [Probleme mit KeePass](help:troubleshooting.keepass).
4. Bei „ausgelastet“: kurz warten und erneut senden.
5. Weitere Fälle in [Probleme im Chat](help:troubleshooting.chat).

## Grenzen

- Es läuft immer nur eine Antwort zugleich; während sie läuft, kannst du nicht senden, keinen anderen Chat öffnen und keinen neuen beginnen.
- Wolken und fehlgeschlagene Antworten werden nicht gespeichert.
- Ob überhaupt in der Wissensbasis gesucht wird, hängt von den Häkchen im Reiter „Wissensquellen“ ab; einen eigenen Schalter dafür gibt es im Composer nicht. Small Talk und Fragen an den Assistenten selbst lösen keine Suche aus.
- Fragen zum Programm selbst beantwortet der Assistent aus der eingebauten Hilfe, siehe [Die eingebaute Hilfe](help:chat.app-help).
