---
id: troubleshooting.chat
title: Probleme im Chat
keywords: Chat, keine Antwort, Antwort bricht ab, abgebrochen, Stop, hängt, Antwort wird erstellt, seltsame Zeichen, Umlaute, Zeichensalat, Zitat, Quellenangabe, [1], Quellen, Markdown, Werkzeuge, Tools, keine Werkzeuge, knowledge_search, Werkzeugrunden, Fehlerblase, Die Nachricht konnte nicht gesendet werden, Unerwartete Antwort, stream ended
---

# Probleme im Chat

Dieses Kapitel hilft bei Problemen, die im Chatfenster selbst sichtbar werden: Es kommt keine Antwort, eine Antwort bricht mittendrin ab, der Text enthält merkwürdige Zeichen oder Quellenangaben sehen anders aus als erwartet, oder der Assistent benutzt keine Werkzeuge bzw. behauptet, er habe keine. Für Fehler beim Verbindungsaufbau siehe [Netzwerk](help:troubleshooting.network), für Probleme mit dem API-Key [KeePass](help:troubleshooting.keepass). Die Bedienung des Chats beschreibt [Der Chat](help:chat.overview).

## Es kommt keine Antwort

Nach „Senden“ zeigt der Composer „Antwort wird erstellt …“, und der Knopf „Stop“ wird aktiv. Was danach passiert, hängt vom Fall ab.

### Eine rote Fehlerblase erscheint

Die Blase nennt in der ersten Zeile die Art des Fehlers. Klicke „Details anzeigen“: Darunter stehen „Technische Ursache: …“ und meist ein „Hinweis: …“ mit dem nächsten Schritt. Die wichtigsten Überschriften:

- „Anmeldung am KI-Dienst fehlgeschlagen.“: API-Key fehlt oder wird abgelehnt, siehe [KeePass](help:troubleshooting.keepass).
- „Der API-Key konnte nach mehreren Versuchen nicht aus KeePass gelesen werden. KeePassRPC war vorübergehend nicht verfügbar.“: KeePass entsperren, Frage erneut senden.
- „Der KI-Dienst ist nicht erreichbar.“ / „Der KI-Dienst ist ausgelastet. Bitte später erneut versuchen.“ / „Fehler im KI-Dienst.“: siehe [Netzwerk](help:troubleshooting.network).
- „Der KI-Dienst hat die Anfrage abgelehnt.“: Der Dienst versteht die Anfrage nicht, etwa weil das Modell falsch geschrieben ist, die Anfrage zu groß ist oder ein lokales Modell nicht lädt ([Modelle](help:troubleshooting.models)).
- „Unerwartete Antwort des KI-Dienstes.“: Der Dienst antwortet in einem Format, das der Client nicht lesen kann. Die technische Ursache lautet dann etwa „unexpected stream content“, „response is not valid JSON“ oder „response contains no choices“. Meist stimmt die Basis-URL nicht oder ein Proxy liefert eine eigene Seite aus.
- „Die Nachricht konnte nicht gesendet werden.“: Der Client hat die Nachricht gar nicht erst losgeschickt, zum Beispiel weil ein Anhang nicht abgelegt werden konnte. Der Grund steht dahinter oder im Protokoll unter „Nachricht nicht gestartet“.

### Es passiert lange nichts

1. Warte zunächst ab. Große Modelle brauchen für die erste Ausgabe manchmal mehrere Sekunden. Läuft vorher eine Wissenssuche, siehst du eine Recherchekarte mit „Durchsucht Wissensquellen“.
2. Vorübergehende Fehler werden still wiederholt, bevor die Fehlerblase erscheint (Standard zwei Wiederholungen mit wachsender Pause). Im Protokoll steht dann „Modellanfrage vorübergehend gescheitert (…); Wiederholung n von m in X ms“.
3. Die Wartezeit auf Daten ist durch „Chat: Lese-Timeout (ms)“ begrenzt (Standard 120000, also zwei Minuten), siehe Einstellungen → „Netzwerk & Agent“ → „Zeitlimits und Wiederholungen“.
4. Mit „Stop“ (Tooltip „Antwort abbrechen“) beendest du das Warten jederzeit.
5. Nutzt du ein lokales Chat-Modell: Es rechnet nur eine Antwort gleichzeitig und ist auf der CPU langsam.

### Die Antwort ist leer

Bricht das Modell ab, weil seine Längenbegrenzung erreicht ist, liefert der Dienst unter Umständen keinen Text. Hinweis: Einen eigenen Hinweis für diesen Fall zeigt die Oberfläche nicht; ob das bei jedem Dienst so ist, ist nicht für alle Installationen geprüft. Ist in der Konfigurationsdatei `chat.maxTokens` gesetzt, erhöhe den Wert oder entferne ihn.

## Die Antwort bricht ab

- **Du hast „Stop“ gedrückt**: Die Blase behält den bisherigen Text und zeigt „ · abgebrochen“. Das ist kein Fehler.
- **Die Verbindung ist mitten im Text abgerissen**: Es erscheint „Der KI-Dienst ist nicht erreichbar.“ mit der technischen Ursache „stream ended before [DONE]“ und dem Hinweis „Der Server hat die Antwort beendet, ohne sie abzuschließen (Verbindung unterbrochen oder Proxy puffert den Stream). Erneut versuchen; bleibt es dabei, chat.readTimeoutMillis prüfen.“ Hat der Dienst schon Text geschickt, wiederholt der Client die Anfrage nicht automatisch, damit kein doppelter Text entsteht. Sende die Frage erneut.
- **Der Text endet mitten im Satz ohne Fehler**: Das Modell hat sein Längenlimit erreicht. Bitte im nächsten Schritt mit „Bitte fortsetzen“ um den Rest oder erhöhe `chat.maxTokens`.
- **Die Werkzeugrunden sind aufgebraucht**: Verlangt das Modell nach „Max. Werkzeugrunden“ (Standard 8) noch weitere Werkzeuge, bekommt es eine letzte Runde ohne Werkzeuge und muss aus den bisherigen Ergebnissen antworten. Liefert es auch dann nichts, erscheint „Unerwartete Antwort des KI-Dienstes.“ mit der Ursache „das Modell hat nach N Werkzeugrunden auch in der Abschlussrunde ohne Werkzeuge keine Antwort gegeben“. Stelle die Frage enger oder erhöhe „Max. Werkzeugrunden“ im Reiter „Tools“.

Proxys, die Antworten puffern, statt sie durchzureichen, lassen den Text oft am Stück statt Wort für Wort erscheinen oder brechen lange Antworten ab. Das liegt nicht am Client.

## Seltsame Zeichen oder ungewohntes Zitatformat

### Umlaute oder Sonderzeichen sind kaputt

Der Client sendet und liest immer UTF-8. Erscheinen trotzdem Zeichen wie „Ã¤“ statt „ä“, kommen sie meist schon so vom Dienst oder aus der Quelle (etwa eine falsch kodierte Datei im Index). Prüfe:

1. Tritt es nur bei Antworten mit Wissensquellen auf? Dann öffne die Quelle über die Quellenliste im Leser und prüfe, ob dort dieselben Zeichen stehen.
2. Tritt es auch ohne Quellen auf, gib die technische Ursache und die Uhrzeit an den Betreiber des Dienstes.

### Die Antwort steht in einem Code-Kasten

Manche Modelle packen ihre ganze Antwort in einen Block „```markdown“. Der Client entfernt diese eine Umhüllung und zeigt den Inhalt als formatierten Text. Andere Codeblöcke bleiben Code. Mermaid-Diagramme werden als Bild gezeigt. Wie der Text dargestellt wird, stellst du unter [Darstellung](help:settings.appearance) ein.

### Was bedeuten [1], [2] …?

Antwortet der Assistent mit Material aus deinen Wissensquellen, bekommt er die gefundenen Abschnitte nummeriert und soll seine Quellen mit dieser Nummer nennen, etwa „[1]“. Unter der Antwort erscheint dazu „Quellen (n)“ (Tooltip „Quellen der Antwort ein- oder ausblenden“). Aufgeklappt steht jede Quelle als „[n] Titel“ mit Stand, Score und Rang in Volltext und Semantik. Fehlen Nummern im Text, obwohl Quellen angezeigt werden, hat das Modell die Anweisung nicht befolgt; die Quellenliste stimmt trotzdem. Mehr dazu in [Quellenangaben](help:chat.citations).

### Zitate aus früheren Antworten

Markierst du Text in einer Antwort und klickst „Zitieren“, erscheint im Composer ein Chip „Zitat“. Der zitierte Text geht als Zusatzkontext mit der nächsten Nachricht hinaus, nicht als sichtbarer Teil deiner Frage. Siehe [Zitate](help:chat.quotes).

### Hinweiszeilen über der Antwort

Kursive Infozeilen ohne Blase sind Hinweise des Clients, keine Fehler des Modells. Beispiele:

- „Die Wissenssuche ist ausgefallen. Die Antwort entstand ohne Kontext aus der Wissensbasis.“
- „Keine passenden Abschnitte in der Wissensbasis gefunden. Die Antwort entstand ohne Kontext.“
- „Die Volltextsuche ist ausgefallen; es wurde nur semantisch gesucht.“ bzw. „Die semantische Suche ist ausgefallen; es wurde nur im Volltext gesucht.“
- „Dateianhänge sind in dieser Sitzung nicht verfügbar; die Nachricht ging ohne sie hinaus.“

Zur Wissenssuche siehe [Wissensquellen](help:troubleshooting.knowledge).

## Werkzeuge werden nicht benutzt

Der Chat bietet dem Modell nur dann Werkzeuge an, wenn es etwas dafür gibt:

- die Unterhaltung hat Dateianhänge,
- es wurden Seiten zum Chat hinzugefügt,
- im Seitenmenü ist mindestens eine Wissensquelle angehakt,
- oder in der Konfigurationsdatei steht `chat.tools.enabled=true` (Standard: aus); dann bei jeder Frage.

Ist nichts davon der Fall, läuft ein normaler Chat ohne Werkzeuge. Die Programmhilfe wird bei jeder Runde mit Werkzeugen mit angeboten („Schlägt in der Programmhilfe nach“).

Prüfschritte:

1. Öffne den Drawer (Hamburger-Menü), Reiter „Wissensquellen“: Ist die gewünschte Quelle angehakt (Tooltip „Angehakt: der Chat durchsucht diese Quelle“)?
2. Einstellungen → „Tools“: Ist das Werkzeug aktiv? „Ohne Häkchen wird ein Werkzeug weder dem Chat noch einem Agenten angeboten; es bleibt registriert.“ Die Spalte zur Verfügbarkeit zeigt, ob es im Chat, im Agenten oder nur im Recherche-Agenten angeboten wird.
3. Läuft ein lokales Chat-Modell? Werkzeuge gibt es nur über den KI-Dienst.
4. Unterstützt das gewählte Modell Werkzeugaufrufe? Manche Modelle ignorieren angebotene Werkzeuge. Probiere ein anderes Chat-Modell.

Benutzt das Modell ein Werkzeug, siehst du eine Wolke wie „Durchsucht Wissensquellen“, „Liest Seite“ oder „Liest Anhang“, bei Fehlern mit „fehlgeschlagen“.

Die automatische Wissenssuche vor der Antwort ist davon unabhängig: Bei angehakter Quelle sucht der Client selbst, bevor das Modell antwortet. Grüße und Dank („Hallo“, „Danke, super“) und Fragen an den Assistenten selbst lösen bewusst keine Suche aus.

## Der Assistent sagt, er habe keine Werkzeuge

Fragst du den Assistenten nach sich selbst, etwa „Welche Tools hast du?“ oder „Welches Modell verwendest du?“, antwortet nicht das Modell, sondern der Client. Er nennt, was in diesem Moment tatsächlich angeboten wird. Ohne Werkzeuge lautet die Antwort:

„In diesem Chat stehen mir gerade keine Werkzeuge zur Verfügung. Ist im Seitenmenü eine Wissensquelle angehakt, kann ich darin suchen und Seiten lesen.“

Darauf folgt „Es antwortet das im Composer gewählte Chat-Modell **…**.“

Diese Antwort stimmt also für die aktuelle Situation. Hake eine Wissensquelle an oder füge einen Anhang hinzu und frage erneut; dann erscheint „In diesem Chat stehen mir gerade diese Werkzeuge zur Verfügung:“ mit der Liste.

Behauptet das Modell dagegen in einer normalen Antwort, es könne nicht suchen, obwohl Werkzeuge angeboten werden, ist das eine Einschätzung des Modells. Prüfe die Punkte unter [Werkzeuge werden nicht benutzt](help:troubleshooting.chat#werkzeuge-werden-nicht-benutzt) und formuliere den Auftrag ausdrücklich („Suche in den Wissensquellen nach …“).

## Grenzen

- Eine Antwort, die mitten im Text abbricht, wird nicht automatisch wiederholt.
- Hinweis: Ob ein vom Dienst gefilterter Inhalt eine eigene Meldung bekommt, ist nicht für alle Installationen geprüft.
- Werkzeuge im Chat nur über den KI-Dienst, nicht mit lokalen Chat-Modellen.
