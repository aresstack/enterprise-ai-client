---
id: settings.tools
title: Einstellungen – Reiter „Tools“
keywords: Tools, Werkzeuge, Tool, Werkzeug abschalten, Werkzeug deaktivieren, aktiv, Häkchen, Registry, Werkzeugrunden, Tool-Calling, Function Calling, Pflichtwerkzeug, Chat, Agent, Recherche-Agent, Herkunft, Verfügbarkeit, tools.disabled, chat.tools.maxRounds, knowledge_search
---

# Einstellungen – Reiter „Tools“

Der Chat und die Agenten können Werkzeuge (Tools) aufrufen, etwa um in Wissensquellen zu suchen, Seiten oder Dateianhänge zu lesen oder Schritte einer Recherche auszuführen. Der Reiter „Tools“ zeigt alle Werkzeuge, die im Programm registriert sind, und lässt dich jedes einzeln mit dem Häkchen „aktiv“ an- und abschalten. Außerdem legst du hier fest, wie viele Werkzeugrunden eine Chat-Antwort höchstens drehen darf. Was die Werkzeuge im Einzelnen tun, erklärt [Werkzeuge des Agenten](help:agent.tools).

Der Reiter hat zwei Bereiche: oben „Werkzeugrunden im Chat“, darunter „Tools“.

## Bereich „Werkzeugrunden im Chat“

- **Beschriftung:** „Max. Werkzeugrunden“.
- **Standard:** 8.
- **Erlaubt:** 1 bis 32. Eine 0 für „unbegrenzt“ gibt es absichtlich nicht: ein Modell, das immer weiter Werkzeuge verlangt, hinge sonst endlos.
- **Bedeutung:** So viele Werkzeugrunden darf eine Antwort im normalen Chat höchstens drehen. Eine Runde heißt: das Modell verlangt ein oder mehrere Werkzeuge, der Client führt sie aus und gibt die Ergebnisse zurück.
- **Wirkung:** Verlangt das Modell nach der letzten erlaubten Runde noch ein Werkzeug, antwortet es in einer letzten Runde ohne Werkzeuge aus den bisherigen Ergebnissen. Du bekommst also immer eine Antwort, auch wenn das Limit erreicht ist.

Der Wert wird in `chat.tools.maxRounds` gespeichert und steht auch dann in der Datei, wenn er dem Standard entspricht.

## Bereich „Tools“: die Werkzeugliste

Hinweis im Dialog: „Die Werkzeuge der zentralen Registry der Anwendung. Ohne Häkchen wird ein Werkzeug weder dem Chat noch einem Agenten angeboten; es bleibt registriert. Gilt nach dem Speichern sofort.“

Jedes Werkzeug steht in einer eigenen Zeile mit vier Angaben:

1. **Häkchen mit dem Namen** (fett): das Häkchen „aktiv“. Tooltip: „Aktiv: dem Chat bzw. den Agenten anbieten“. Standard: alle Werkzeuge aktiv.
2. **Verfügbarkeit** rechts: wo das Werkzeug angeboten wird, als „Chat“, „Agent“, „Recherche-Agent“ oder eine Kombination wie „Chat + Agent“.
3. **Beschreibung:** der Text, mit dem das Werkzeug sich dem Modell vorstellt.
4. **Herkunft:** eine graue Zeile „Herkunft: …“, die nennt, welcher Teil des Programms das Werkzeug registriert hat, etwa die Wissenswerkzeuge, die Dateianhänge oder der Recherche-Agent je Phase. In Klammern steht dabei ein technischer Name.

Die Liste entsteht allein aus dem, was die Anwendung gerade registriert hat. Ein neues Werkzeug erscheint deshalb ohne weiteres Zutun. Der Reiter liest die Liste bei jedem Anzeigen neu.

### Pflichtwerkzeuge

Manche Werkzeuge braucht ein Agent zwingend. Ihr Häkchen ist gesetzt und grau, es lässt sich nicht abwählen. Tooltip: „Pflichtwerkzeug: ohne es startet bzw. arbeitet der Agent nicht; es lässt sich nicht abschalten“. Ein Pflichtwerkzeug wird nie als abgeschaltet gespeichert, auch wenn es in einer älteren Datei so stand.

### Leere Liste

Steht dort „Keine Werkzeuge registriert. Die Übersicht füllt sich, sobald die Anwendung läuft.“, ist der Dialog ohne laufende Anwendung geöffnet, zum Beispiel beim Start mit einer fehlerhaften Konfigurationsdatei. Öffne den Dialog später über das Zahnrad erneut, dann siehst du die Werkzeuge.

## Schritt für Schritt: ein Werkzeug abschalten

1. Öffne Einstellungen → „Tools“.
2. Such das Werkzeug in der Liste und nimm das Häkchen vor seinem Namen weg.
3. Klicke „Speichern“.

Ab jetzt bietet der Client das Werkzeug weder dem Chat noch einem Agenten an. Zum Wiedereinschalten setzt du das Häkchen und speicherst erneut.

## Was im Hintergrund passiert

Gespeichert wird eine kommagetrennte Liste der abgeschalteten Werkzeugnamen unter `tools.disabled`, zum Beispiel `tools.disabled=knowledge_search`. Fehlt der Schlüssel, sind alle Werkzeuge aktiv. Abgeschaltete Namen, die gerade nicht registriert sind (etwa Werkzeuge einer anderen Recherche-Phase), bleiben in der Liste erhalten.

Die Änderung gilt nach dem Speichern ohne Neustart:

- Ein laufender Agent bekommt den neuen Werkzeugsatz sofort mitgeteilt.
- Eine laufende Chat-Runde bietet ein abgeschaltetes Werkzeug ab dem nächsten Modellaufruf nicht mehr an und lehnt einen schon angefragten Aufruf ab.

Ein abgeschaltetes Werkzeug bleibt registriert; es wird nur nicht mehr angeboten. Die Liste im Reiter zeigt es deshalb weiterhin, nur ohne Häkchen.

## Grenzen

- Abschalten wirkt auf das Angebot an das Modell, nicht auf andere Wege. Ob ein Werkzeug überhaupt benutzt wird, entscheidet das Modell anhand der Frage.
- Schaltest du Wissenswerkzeuge ab, kann der Chat in deinen Wissensquellen nicht mehr gezielt nachsuchen; Antworten stützen sich dann nur auf die automatische Vorabsuche oder auf das allgemeine Wissen des Modells.
- Pflichtwerkzeuge lassen sich nicht abschalten.
- Wie viele Seiten ein Agent liest, regelt nicht dieser Reiter, sondern das Recherche-Budget im Reiter [Netzwerk & Agent](help:settings.network).

## Typische Probleme

### Der Chat sucht nicht mehr in meinen Wissensquellen

Prüfe hier, ob die Wissenswerkzeuge (Verfügbarkeit „Chat“) aktiv sind. Prüfe außerdem, ob die Quellen im Chat angehakt sind; siehe [Wissensquellen im Überblick](help:knowledge.overview).

### Die Antwort endet mit Ergebnissen, aber ohne weitere Suche

Vermutlich ist „Max. Werkzeugrunden“ erreicht. Erhöhe den Wert behutsam, etwa auf 12, und frag erneut.

### Ein Häkchen lässt sich nicht entfernen

Das ist ein Pflichtwerkzeug. Weitere Hilfe zu Agenten: [Probleme mit dem Agenten](help:troubleshooting.agent).
