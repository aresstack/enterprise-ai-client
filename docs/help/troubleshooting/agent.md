---
id: troubleshooting.agent
title: Probleme mit dem Agenten
keywords: Agent, Agent-Modus, Recherche-Agent, startet nicht, Der Agent konnte nicht gestartet werden, keine Sitzung, unerwartet beendet, hängt, arbeitet noch, Stop, Werkzeug fehlt, Pflichtwerkzeug, Tools, Seitenbudget, Seitenbudget erreicht, Recherche-Budget, Recherche starten, ACP, Kommando, Timeout
---

# Probleme mit dem Agenten

Im Agent-Modus arbeitet ein eigener Prozess neben der Anwendung: entweder der eingebaute Recherche-Agent oder ein eigenes Agent-Kommando, das über ACP angesprochen wird. Weil dabei ein zweites Programm läuft, gibt es eigene Fehlerbilder. Dieses Kapitel hilft, wenn der Agent nicht startet, scheinbar hängt, ein Werkzeug fehlt oder eine Recherche wegen des Seitenbudgets endet. Wie der Agent-Modus grundsätzlich funktioniert, steht in [Agent-Modus im Überblick](help:agent.overview).

## Welcher Agent läuft?

Das entscheidet Einstellungen → „Netzwerk & Agent“ → Bereich „Agent-Modus (ACP)“:

- „Agent-Modus aktivieren“ **aus** (Standard): Der eingebaute Recherche-Agent arbeitet. Er startet als eigener Java-Prozess mit derselben Java-Laufzeit und aus demselben Programmpaket wie die Anwendung. Seine Protokollzeilen stehen im Protokoll der Anwendung mit dem Präfix „research-agent: “.
- „Agent-Modus aktivieren“ **an**, mit „Kommando“ und „Argumente“: Dein eigenes Agent-Kommando arbeitet.

Die Modus-Pille oben links zeigt per Tooltip „Normaler Chat mit dem KI-Modell“ bzw. „Aufträge an den externen Agenten (ACP) schicken“. Steht dort „Kein Agent konfiguriert“, steht der Agent-Modus in dieser Sitzung nicht zur Verfügung.

Änderungen an diesen Einstellungen gelten ab der **nächsten Agentensitzung**, also nach „+ Neuer Chat“ oder beim Öffnen eines anderen Chats, nicht für eine schon laufende Sitzung.

## Der Agent startet nicht

Mögliche Meldungen in der Antwortblase:

- „Der Agent konnte nicht gestartet werden.“: Der Prozess ließ sich nicht starten.
- „Der Agent hat keine Sitzung eröffnet.“: Der Prozess läuft, hat aber den ACP-Handschlag nicht abgeschlossen.
- „Der Agent hat den Auftrag nicht abgeschlossen.“: Der Auftrag ist gescheitert; das ist auch die Meldung für einen sonst nicht zugeordneten Fehler.

Diese Meldungen sind absichtlich knapp; Einzelheiten aus dem Agentenprozess stehen nur im Protokoll.

### Beim eingebauten Recherche-Agenten

1. Öffne Einstellungen → „Technische Details“ und suche im Protokoll nach „research-agent“ und nach der Uhrzeit des Auftrags.
2. Der Agent braucht dasselbe Java wie die Anwendung. Wurde die Anwendung auf ungewöhnliche Weise gestartet (etwa aus entpackten Klassen statt aus dem Programmpaket), kann der Start scheitern.
3. Hat eine Sicherheitssoftware das Starten eines zweiten Java-Prozesses blockiert? Dann steht im Protokoll meist ein Fehler beim Starten des Prozesses.
4. Steht „Agent-Modus aktivieren“ versehentlich an, aber ohne „Kommando“, meldet die Konfigurationsprüfung „agent.command“ „fehlt (Pflicht bei agent.enabled=true)“. Häkchen entfernen oder Kommando eintragen.

### Bei einem eigenen Agent-Kommando

1. Prüfe „Kommando“: Ist das Programm unter diesem Namen bzw. Pfad auffindbar? Bei Zweifel den vollständigen Pfad eintragen.
2. Prüfe „Argumente“: Sie werden wie eine Shell-Zeile zerlegt; Pfade mit Leerzeichen gehören in Anführungszeichen, zum Beispiel `-jar "C:/Pfad mit Leerzeichen/agent.jar"`.
3. Starte das Kommando probehalber in einer Konsole. Es muss ACP über Standard-Ein- und -Ausgabe sprechen und darf auf der Standardausgabe nichts anderes schreiben.
4. Braucht der Agent zum Start länger, erhöhe „Timeout je Anfrage (Sekunden)“ (Standard 30).
5. Neuen Chat beginnen („+ Neuer Chat“), damit die geänderte Einstellung greift.

Hinweis: Das Zusammenspiel mit fremden ACP-Agenten ist nicht für alle Installationen geprüft.

## Der Agent hängt

### „Der Agent arbeitet noch am vorherigen Auftrag.“

Es läuft immer höchstens ein Auftrag je Chat. Warte, bis der laufende Auftrag fertig ist, oder brich ihn mit dem Stopp-Knopf im Composer ab. Die Blase endet, sobald der Agent den Abbruch bestätigt hat. Auch die Phase lässt sich während eines Auftrags nicht wechseln: „Die Phase lässt sich wechseln, sobald der Agent mit dem Auftrag fertig ist.“

### Lange keine sichtbare Bewegung

1. Der eingebaute Recherche-Agent darf für einen Auftrag bis zu 30 Minuten brauchen, weil ein Recherche-Lauf viele Seiten liest. Die Recherchekarte zeigt den Fortschritt, etwa „Seiten: n / Budget“.
2. Der Agent nutzt das im Composer gewählte Chat-Modell. Ist der KI-Dienst langsam oder ausgelastet, wartet auch der Agent. Prüfe das mit einer normalen Chat-Frage.
3. Die Wissenssuche des Agenten wiederholt vorübergehende Fehler nach derselben Regel wie die Wissensquellen („Wissensquellen: Wiederholungen“). Langsame Quellen verlängern jeden Schritt.
4. Hilft nichts: Stopp drücken, „+ Neuer Chat“ und den Auftrag neu stellen.

### „Der Agent wurde unerwartet beendet. Der nächste Auftrag startet ihn neu.“

Der Agentenprozess ist abgestürzt oder wurde beendet. Sende den Auftrag einfach noch einmal; der Client startet den Agenten dann neu. Einen automatischen Neustart ohne neuen Auftrag gibt es nicht. Ein beendeter Prozess wird außerdem erst beim nächsten Auftrag oder nach Ablauf der Wartezeit bemerkt; bis dahin sieht es aus, als hinge er. Die Ursache steht im Protokoll.

„Der Agent-Modus ist beendet.“ erscheint, wenn die Sitzung bereits geschlossen wurde, etwa beim Wechsel des Chats. Ein neuer Auftrag in einem aktiven Chat öffnet eine neue Sitzung.

## Ein Werkzeug fehlt

Der Agent bekommt nur Werkzeuge, die im Reiter „Tools“ aktiv sind: „Ohne Häkchen wird ein Werkzeug weder dem Chat noch einem Agenten angeboten; es bleibt registriert.“

1. Einstellungen → „Tools“: Ist das Werkzeug angehakt? Die Spalte zur Verfügbarkeit zeigt, ob es für Chat, Agent oder nur den Recherche-Agenten gedacht ist.
2. Änderungen gelten nach dem Speichern sofort, auch für laufende Agenten; sie bekommen den neuen Werkzeugsatz mitgeteilt.
3. Ein eigenes Agent-Kommando bekommt nur die Werkzeuge des Kanals „Agent“ (Wissenssuche und Seite lesen), nicht die Steuerwerkzeuge des Recherche-Agenten.
4. Pflichtwerkzeuge lassen sich nicht abschalten (Tooltip „Pflichtwerkzeug: ohne es startet bzw. arbeitet der Agent nicht; es lässt sich nicht abschalten“).
5. Die Wissenswerkzeuge brauchen etwas zum Durchsuchen: Hake im Drawer unter „Wissensquellen“ mindestens eine Quelle an.

Lehnt der Client während eines Laufs einen Werkzeugaufruf ab, weil du das Werkzeug gerade abgeschaltet hast, ist das gewollt. Hinweis: Die Oberfläche zeigt nicht jede Werkzeugänderung im laufenden Agenten sichtbar an.

## Das Seitenbudget ist erschöpft

Der eingebaute Recherche-Agent liest pro Lauf höchstens so viele Seiten vollständig, wie das „Recherche-Budget (Seiten)“ erlaubt (Einstellungen → „Netzwerk & Agent“ → „Agent-Modus (ACP)“, Standard 4, 0 = unbegrenzt). Das Vorladen von Treffern im Hintergrund zählt nicht dazu. Dieses Budget ist unabhängig von den Einstellungen im Reiter „Index“.

Am Ende eines Laufs nennt der Agent den Grund, zum Beispiel „Recherche beendet: n Quellen übernommen, m Seiten gelesen (Seitenbudget erreicht).“ Mögliche Gründe:

| Grund | Bedeutung |
|---|---|
| „genug Belege“ | Ziel erreicht |
| „Seitenbudget erreicht“ | Recherche-Budget (Seiten) aufgebraucht |
| „Quellenbudget erreicht“, „Werkzeugbudget erreicht“, „Zeitbudget erreicht“ | weitere eingebaute Grenzen |
| „keine weiteren passenden Treffer“ | Quellen geben nichts mehr her |
| „abgebrochen“ | du hast gestoppt |
| „Wissenssuche nicht erreichbar“, „zu viele Fehler bei der Suche“ | technischer Abbruch |

Was du tun kannst:

1. „Recherche-Budget (Seiten)“ erhöhen und speichern. Der Wert gilt ab dem nächsten Start des Agentenprozesses, also nach „+ Neuer Chat“ oder einem Neustart des Agenten.
2. Im Reiter „Quellen“ des Drawers mit „Recherche starten“ einen weiteren Lauf anstoßen.
3. Bei einem Fehler lautet die Meldung „Die Quellensuche ist fehlgeschlagen (…). Die bisher übernommenen Quellen bleiben im Projekt; mit „Recherche starten“ im Reiter „Quellen“ starte ich neu.“ Bei einem Abbruch durch dich: „Die Quellensuche ist angehalten (…). Mit „Recherche starten“ im Reiter „Quellen“ geht sie weiter; …“.

Hinweis: Ob ein höheres Budget in einer laufenden Agentensitzung ohne neuen Chat wirkt, ist nicht für alle Installationen geprüft; beginne sicherheitshalber einen neuen Chat.

Mehr zur Recherche in [Recherche mit dem Agenten](help:agent.research), zu den Werkzeugen in [Werkzeuge von Chat und Agent](help:agent.tools).

## Grenzen

- Kein automatischer Neustart eines abgestürzten Agenten ohne neuen Auftrag.
- Höchstens ein Auftrag je Chat gleichzeitig.
- Ein eigenes Agent-Kommando bekommt keine Recherche-Steuerung, keine Phasen und keine Reiter „Konzept“ bis „Dokument“.
