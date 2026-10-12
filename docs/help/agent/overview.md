---
id: agent.overview
title: Agent-Modus im Überblick
keywords: Agent, Agent-Modus, Agentenmodus, Modus-Pille, Chat oder Agent, umschalten, Recherche-Agent, eingebauter Agent, eigener Agent, Agent-Kommando, ACP, Agent Client Protocol, MCP, Seitenbudget, Recherche-Budget, agent.enabled, agent.command, agent.research.maxPages, Agent startet nicht, Agent arbeitet noch, research agent, agent mode
---

# Agent-Modus im Überblick

Der Agent-Modus ist die zweite Arbeitsweise desselben Chats: Statt dass das KI-Modell direkt antwortet, bekommt ein Agent deinen Auftrag, plant selbst, sucht in den Wissensquellen, liest Seiten und meldet dir unterwegs, was er tut. Dieses Kapitel erklärt, wie du zwischen Chat und Agent umschaltest, welcher Agent arbeitet (der eingebaute Recherche-Agent oder ein eigenes Agent-Kommando), worin sich der Agent vom normalen Chat unterscheidet und was das Seitenbudget bedeutet. Es richtet sich an alle, die mehr wollen als eine einzelne Antwort, zum Beispiel eine gründliche Recherche mit Gliederung und belegtem Dokument.

## Was der Agent-Modus ist

Im normalen Chat stellst du eine Frage, die Anwendung sucht passende Auszüge in den angehakten Wissensquellen und das KI-Modell antwortet in einer Sprechblase. Im Agent-Modus läuft dagegen ein eigener Agentenprozess neben der Anwendung. Er bekommt deinen Auftrag, entscheidet selbst, welche Werkzeuge er benutzt, und kann mehrere Schritte hintereinander ausführen: suchen, Treffer lesen, Seiten als Quellen übernehmen, Zwischenergebnisse ablegen.

Wichtig für das Verständnis:

- Es ist **ein Chat mit zwei Fähigkeiten**. Die Modus-Pille schaltet nur um, wer antwortet. Verlauf, Eingabefeld (Composer), Anhänge und gewähltes Modell bleiben dieselben. Abgeschlossene Agenten-Antworten gehören zum Verlauf des Chats und werden mit ihm gespeichert.
- Zu jedem Chat gehört genau eine Agentensitzung und, beim eingebauten Agenten, genau ein **Research-Projekt** (Konzept, Quellen, Gliederung, Dokument). „+ Neuer Chat“ beendet die Agentensitzung und beginnt ein neues, leeres Projekt. Öffnest du einen gespeicherten Chat, öffnet sich sein Projekt in einer frischen Agentensitzung.
- Der Agent bekommt seine Werkzeuge über einen eigenen, nur lokal erreichbaren Werkzeug-Endpunkt der Anwendung (MCP). Welche Werkzeuge das sind, steht in [Werkzeuge von Chat und Agent](help:agent.tools).

## Zwischen Chat und Agent umschalten

1. Neben dem Hamburger-Knopf oben links steht die Modus-Pille. Sie zeigt den aktuellen Modus, also „Chat“ oder „Agent“.
2. Klicke auf die Pille und wähle „Agent“. Der Tooltip erklärt die beiden Einträge: „Normaler Chat mit dem KI-Modell“ bzw. Aufträge an den Agenten.
3. Schreibe deinen Auftrag wie gewohnt in den Composer und sende ihn ab.

Der erste Auftrag startet den Agentenprozess. Das kann einige Sekunden dauern; die Antwortblase zeigt so lange einen Platzhalter. Weitere Aufträge laufen in derselben Sitzung, der Agent behält also seinen Kontext.

Was sich beim Umschalten sichtbar ändert (nur beim eingebauten Recherche-Agenten):

- Im Composer erscheint ein drittes Auswahlfeld „Phase: …“ (zum Beispiel „Phase: Konzept“).
- Im Drawer (Hamburger) kommen die Reiter „Konzept“, „Quellen“, „Gliederung“ und „Dokument“ hinzu.
- Über dem Composer können gelbe Suchvorschläge stehen, und oben im Verlauf können Wolken mit Ausschlüssen erscheinen („Außerhalb des Scopes“).

Diese Bedienelemente erklärt [Recherche mit dem Agenten](help:agent.research). Schaltest du zurück auf „Chat“, verschwinden sie wieder; der Verlauf bleibt.

### Während der Agent arbeitet

Es läuft immer höchstens ein Auftrag. Solange der Agent arbeitet, lehnt die Anwendung einen weiteren Auftrag mit dem Hinweis „Der Agent arbeitet noch am vorherigen Auftrag.“ ab. Mit dem Stopp-Knopf brichst du den laufenden Auftrag ab; die Blase endet, sobald der Agent den Abbruch bestätigt hat.

## Eingebauter Recherche-Agent oder eigenes Agent-Kommando

Welcher Agent arbeitet, entscheidet eine einzige Einstellung unter Einstellungen → „Netzwerk & Agent“ im Abschnitt „Agent-Modus (ACP)“:

| Einstellung | Wer arbeitet |
|---|---|
| „Agent-Modus aktivieren“ **aus** (Standard, `agent.enabled=false`) | der **eingebaute Recherche-Agent** |
| „Agent-Modus aktivieren“ **an**, dazu „Kommando“ und „Argumente“ | dein **eigenes Agent-Kommando** |

Der Modus „Agent“ in der Pille steht also auch ohne eigene Einstellung zur Verfügung: Dann arbeitet der eingebaute Recherche-Agent.

### Der eingebaute Recherche-Agent

Der eingebaute Agent wird mit der Anwendung ausgeliefert. Er startet als eigener Java-Prozess mit derselben Java-Laufzeit aus demselben Programmpaket; du musst nichts installieren. Er arbeitet in vier Phasen (Konzept, Quellen, Gliederung, Dokument), legt alles im Research-Projekt des Chats ab und ist auf Recherchen in deinen Wissensquellen zugeschnitten.

Eigenschaften:

- Er benutzt das **Chat-Modell**, das im Composer gewählt ist, und den dort eingestellten Denkaufwand („Denken: …“). Die Anfragen an das Modell schickt nicht der Agentenprozess selbst, sondern die Anwendung für ihn. Der Agentenprozess bekommt deshalb weder API-Key noch Passwörter.
- Er bekommt zusätzlich zu den Wissenswerkzeugen eigene Steuerwerkzeuge, die je nach Phase wechseln.
- Eine einzelne Anfrage an ihn darf lange dauern (bis zu 30 Minuten), weil ein Recherche-Lauf viele Seiten lesen kann.

### Ein eigenes Agent-Kommando

Wenn du einen anderen Agenten benutzen willst, der das Agent Client Protocol (ACP) über die Standard-Ein- und -Ausgabe spricht:

1. Öffne Einstellungen → „Netzwerk & Agent“.
2. Setze das Häkchen „Agent-Modus aktivieren“.
3. Trage unter „Kommando“ das Programm ein, zum Beispiel `java`.
4. Trage unter „Argumente“ die Argumente ein. Sie werden wie eine Shell-Zeile zerlegt: Leerzeichen trennen, Anführungszeichen fassen zusammen, zum Beispiel `-jar "C:/Pfad/agent.jar"`.
5. Unter „Timeout je Anfrage (Sekunden)“ legst du fest, wie lange die Anwendung auf eine Antwort des Agenten wartet (Standard 30).
6. Speichern.

Die Änderung gilt ohne Neustart der Anwendung ab der **nächsten Agentensitzung**, also nach „+ Neuer Chat“ oder wenn du einen anderen Chat öffnest.

Ein eigenes Agent-Kommando bekommt die Werkzeuge des Kanals „Agent“ (heute die Wissenswerkzeuge `knowledge_search`, `knowledge_read` und `knowledge_attachments` sowie die Programmhilfe), nicht aber die Steuerwerkzeuge des Recherche-Agenten. Phasenauswahl, Reiter „Konzept“ bis „Dokument“, gelbe Suchvorschläge und Wolkenhimmel gibt es dann nicht. Das Modell wählt ein eigener Agent selbst; die Modellauswahl im Composer betrifft ihn nicht.

Hinweis: Ein eigener Agent ist ein Programm, das mit deinen Benutzerrechten läuft. Trage nur Programme ein, denen du vertraust. Mehr dazu in [Sicherheit](help:concepts.security).

## Unterschied zum normalen Chat

| | Normaler Chat | Agent-Modus |
|---|---|---|
| Wer antwortet | das KI-Modell direkt | ein Agentenprozess, der das Modell benutzt |
| Wissenssuche | die Anwendung sucht vorab automatisch in den angehakten Quellen und prüft, ob die Treffer reichen | der Agent entscheidet selbst, wann und wonach er sucht |
| Werkzeuge | `knowledge_search`, `knowledge_read`, `knowledge_attachments`, Anhang-Werkzeuge, Programmhilfe, begrenzt durch „Max. Werkzeugrunden“ | Wissenswerkzeuge plus (eingebauter Agent) Steuerwerkzeuge je Phase |
| Anhänge | das Modell liest sie bei Bedarf mit Werkzeugen | der Text jeder Datei geht direkt mit der Nachricht an den Agenten (höchstens 30.000 Zeichen je Datei) |
| Seiten aus dem Lesebereich | als Lesezeichen des Chats | als Zeile „Seite: Titel (Lesezeichen)“; der Agent liest sie selbst |
| Ergebnis | eine Antwort mit Quellen | Antworten, dazu ein Research-Projekt mit Konzept, Quellen, Gliederung und Dokument |
| Begrenzung | Quellen im Antwortkontext (Reiter „Index“) | Seitenbudget je Recherche-Lauf (siehe unten) |

In beiden Modi siehst du, woran gearbeitet wird: Jeder Werkzeugaufruf erscheint als gelbe Gedankenwolke mit Titel und Eingabe, die mit ihrer Ergebniszeile endet; Überlegungen des Agenten stehen in einer grünen Wolke „Überlegungen“. Suchläufe zeigt eine Recherchekarte mit Suchanfragen, Treffern und gelesenen Seiten.

## Das Seitenbudget

Das Seitenbudget begrenzt, wie viele Seiten der eingebaute Recherche-Agent in einem Recherche-Lauf **vollständig liest**. Gezählt wird jede Seite, die er mit `knowledge_read` öffnet. Eine Suche zählt nicht als Seite. Das Vorladen von Suchtreffern im Hintergrund verbraucht nie Budget.

Einstellen:

1. Einstellungen → „Netzwerk & Agent“, Abschnitt „Agent-Modus (ACP)“.
2. Feld „Recherche-Budget (Seiten)“: 0 bis 10000, Standard 4. **0 bedeutet unbegrenzt.**
3. Speichern. Der Wert wird dem Agentenprozess beim Start mitgegeben; er gilt also ab der nächsten Agentensitzung (zum Beispiel nach „+ Neuer Chat“).

In der Konfigurationsdatei heißt der Schlüssel `agent.research.maxPages`. Ältere Dateien mit `agent.tools.maxPages` behalten ihren Wert, bis `agent.research.maxPages` gesetzt ist.

Das Seitenbudget ist **unabhängig** von den Grenzen des normalen Chats (Einstellungen → „Index“, zum Beispiel „Max. Quellen im Antwortkontext“). Die Recherchekarte zeigt den Verbrauch als „Seiten: 2 / 4“. Ist das Budget erschöpft, endet der Lauf mit dem Grund „Seitenbudget erreicht“.

Neben dem Seitenbudget hat ein Recherche-Lauf eingebaute Grenzen, die du in den Einstellungen nicht änderst: in dieser Version höchstens 30 Werkzeugaufrufe, 8 übernommene Quellen, 3 Fehler in Folge und 10 Minuten je Lauf. Endet ein Lauf deshalb, nennt die Zusammenfassung den Grund, etwa „Werkzeugbudget erreicht“ oder „Zeitbudget erreicht“.

## Typische Probleme

### Der Agent antwortet „Der Agent konnte nicht gestartet werden.“

- Mit eigenem Agent-Kommando: Prüfe „Kommando“ und „Argumente“ unter Einstellungen → „Netzwerk & Agent“. Lässt sich das Programm mit genau diesen Angaben in einer Eingabeaufforderung starten? Pfade mit Leerzeichen gehören in Anführungszeichen.
- Der Agent muss ACP über Standard-Ein- und -Ausgabe sprechen. Ein Programm, das etwas anderes auf die Standardausgabe schreibt, scheitert beim Verbindungsaufbau.
- Schau ins Protokoll unter `<Anwendungsverzeichnis>/logs/enterprise-ai-client.0.log`. Weitere Prüfschritte: [Probleme mit dem Agenten](help:troubleshooting.agent).

### „Der Agent wurde unerwartet beendet. Der nächste Auftrag startet ihn neu.“

Der Agentenprozess ist während des Auftrags weggefallen. Es gibt keinen automatischen Neustart; der nächste Auftrag startet einen neuen Prozess in einer neuen Sitzung. Der bisherige Gesprächskontext des Agenten ist dann verloren, das Research-Projekt (Konzept, Quellen, Gliederung, Dokument) bleibt erhalten.

### Der Agent reagiert lange nicht

Ein beendeter Agentenprozess wird erst beim nächsten Auftrag oder nach Ablauf der Wartezeit erkannt. Beim eigenen Agenten ist das „Timeout je Anfrage (Sekunden)“, beim eingebauten Agenten bis zu 30 Minuten je Anfrage. Ein laufender Recherche-Lauf ist dagegen kein Hänger: Die Recherchekarte zeigt dann, welche Seite gerade gelesen wird. Mit dem Stopp-Knopf brichst du ab.

### Der Agent sagt, er könne nicht in den Wissensquellen suchen

Der eingebaute Agent meldet das, wenn `knowledge_search` nicht angeboten wird. Prüfe Einstellungen → „Tools“: Sind `knowledge_search` und `knowledge_read` angehakt? Prüfe außerdem im Drawer-Reiter „Wissensquellen“, ob mindestens eine Quelle angehakt ist. Siehe [Werkzeuge von Chat und Agent](help:agent.tools).

### Die Reiter „Konzept“, „Quellen“ usw. fehlen

Sie erscheinen nur im Modus „Agent“ und nur mit dem eingebauten Recherche-Agenten. Ist „Agent-Modus aktivieren“ mit einem eigenen Kommando eingeschaltet, gibt es sie nicht.

## Verwandte Kapitel

- [Werkzeuge von Chat und Agent](help:agent.tools)
- [Recherche mit dem Agenten](help:agent.research)
- [Probleme mit dem Agenten](help:troubleshooting.agent)
- [Der normale Chat](help:chat.overview)
- [Sicherheit](help:concepts.security)
