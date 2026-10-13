---
id: troubleshooting.speech
title: Probleme mit Vorlesen und Spracheingabe
keywords: Vorlesen, Sprachausgabe, TTS, stumm, kein Ton, Orb, Stimme, Stimme wechselt, Sprecher, Stopp, anhalten, pausieren, lokale Stimme, Stimme fehlt, Merlin, Spracheingabe, Diktieren, STT, Mikrofon, Kein Mikrofon gefunden, Erkenne Sprache, Gemma, Audioausgabegerät, Strg+Umschalt+M
---

# Probleme mit Vorlesen und Spracheingabe

Der Client kann Antworten vorlesen (Sprachausgabe) und gesprochene Fragen in Text umwandeln (Spracheingabe). Beides hängt von Modellen ab, die du erst auswählen bzw. installieren musst, und vom Audiogerät des Rechners. Dieses Kapitel hilft, wenn das Vorlesen stumm bleibt, die Stimme plötzlich wechselt, sich das Vorlesen nicht anhalten lässt, eine lokale Stimme fehlt oder die Spracheingabe nicht funktioniert. Die Bedienung steht in [Vorlesen](help:chat.read-aloud), [Diktieren](help:chat.dictation) und [Sprache einstellen](help:settings.speech).

## Das Vorlesen bleibt stumm

Der runde Play/Pause-Orb über dem Verlauf zeigt per Tooltip, warum er nicht spielt. „Letzte Antwort vorlesen (neue Antworten werden automatisch vorgelesen)“ heißt: bereit. „Vorlesen nicht verfügbar“ nennt in Klammern den Grund.

### Kein Modell gewählt oder Stimme nicht installiert

- „Sprachausgabe aus: kein TTS-Modell gewählt (Einstellungen → Modelle)“: Wähle im Reiter „Modelle“ ein TTS-Modell. Ohne TTS-Modell bleibt der Orb aus.
- „Sprachausgabe aus: die Quelle des gewählten TTS-Modells ist nicht verfügbar (lokale Stimme installiert? Einstellungen → Modelle → Lokale Modelle)“: Das gewählte Modell gehört zu einer Quelle, die gerade nicht da ist, meist eine nicht installierte lokale Stimme.
- „Lokale Stimme „…“ ist nicht installiert oder nicht lesbar (Modelle → Lokale Modelle).“: Stimme unter „Lokale Modelle“ installieren oder neu installieren.

### Fehler beim Vorlesen

Scheitert das Vorlesen einer Antwort, erscheint der Dialog „Vorlesen“ mit „Die Antwort konnte nicht vorgelesen werden.“, darunter die Ursache und die verwendete Stimme. Häufige Ursachen:

- „Sprachausgabe der Enterprise-API: HTTP n“ oder „Sprachausgabe der Enterprise-API nicht erreichbar (…)“: Das TTS-Modell kommt vom KI-Dienst, und der antwortet nicht wie erwartet. Bei 401/403 den API-Key prüfen ([KeePass](help:troubleshooting.keepass)), bei „nicht erreichbar“ das Netz ([Netzwerk](help:troubleshooting.network)).
- „API-Key nicht verfügbar (…)“: KeePass liefert den Key nicht.
- „Der Text enthält nichts, was die Stimme sprechen kann.“: Die Antwort besteht etwa nur aus Code oder Zeichen, die die lokale Stimme nicht aussprechen kann.
- „Text zu lang für eine Äußerung (n Token).“: Ein Stück ist für die lokale Stimme zu lang. Stelle unter „Einheit der Synthese“ von „Ganze Antwort“ auf „Absätze“ oder „Sätze“.
- „Kein Audioausgabegerät verfügbar“: Java findet keinen Lautsprecher. Kopfhörer oder Lautsprecher anschließen, in Windows das Standardgerät prüfen und das Programm neu starten.
- „Audioformat nicht lesbar“: Der Dienst liefert ein Format, das der Client nicht abspielen kann.

### Schnelltest

1. Einstellungen → „Sprachausgabe“ → Bereich „Stimme“ → „Stimme testen“. Während des Tests steht „Spreche …“, danach „Gesprochen.“.
2. Klappt der Test, aber nicht das Vorlesen im Chat, prüfe, ob du die Einstellungen gespeichert hast; der Test nutzt den ungespeicherten Entwurf.
3. Erscheint „Test konnte nicht gestartet werden: …“, steht die Ursache dahinter.

Hinweis: Die Sprachausgabe über die Enterprise-API (Endpunkt für Sprachsynthese) ist nicht für alle Installationen geprüft. Ob dein KI-Dienst sie anbietet, zeigt die Modellliste.

## Die Stimme wechselt plötzlich

Manche TTS-Modelle haben mehrere Sprecher. Ist keine feste Stimme eingestellt, wählt das Modell bei jedem Absatz selbst, und die Stimme wechselt mitten in der Antwort. Die Oberfläche sagt dazu: „Modelle mit mehreren Sprechern wählen ohne feste Stimme bei jedem Absatz selbst, dann wechselt die Stimme mitten in der Antwort.“

1. Einstellungen → „Sprachausgabe“ → „Stimme“ → Feld „Stimme“: einen Sprecher aus der Liste wählen oder eintippen.
2. Speichern und mit „Stimme testen“ prüfen.

Die Stimme gilt je Modell. Wechselst du das TTS-Modell, erscheint dessen eigene Stimme. Lokale Stimmen haben genau einen Sprecher („Lokale Stimme: genau ein Sprecher.“) und wechseln nie.

Wechselt die Stimme trotz fester Einstellung zwischen zwei Stücken, klingt also ein Stück wie ein anderer, tieferer Sprecher: Generative Stimmen wie Orpheus tun das bei sehr langen Anfragen (ab etwa 1000 Zeichen) gelegentlich, obwohl dieselbe Stimme angefragt wurde. Dagegen hilft „Fix: Stimme bei langen Abschnitten stabilisieren“ unter „Sprachausgabe“ (Standard an), der Stücke nach ungefähr 500 Zeichen an der nächsten Satzgrenze beendet.

Für die weitere Fehlersuche: „Worker“ auf 1 stellen, damit nur eine Synthese gleichzeitig läuft, und im Protokoll die Zeilen „TTS request:“, „TTS HTTP request:“ und „TTS audio:“ vergleichen. Sie zeigen, welche Stimme bei jedem Stück angefragt wurde.

Merlin, der Begleiter des Einrichtungsassistenten, hat eine eigene Stimme, unabhängig vom Chat (Bereich „Merlin“: „Stimme von Merlin“, „Sprecher von Merlin“). Klingt Merlin anders als das Vorlesen, ist das gewollt. Kann Merlin nicht sprechen, steht im Protokoll „Merlin spricht nicht: …“, und er zeigt seine Sätze nur als Sprechblase.

## Das Vorlesen lässt sich nicht anhalten

- Klicke den Orb erneut (Tooltip „Vorlesen pausieren“). Die Wiedergabe stoppt, ohne dass noch vorbereitete Stücke nachkommen.
- Speicherst du in den Einstellungen ein anderes TTS-Modell, endet eine laufende Ausgabe sofort.
- Liest jede neue Antwort sofort vor, obwohl du das nicht willst: „Neue Antworten automatisch vorlesen“ abschalten.

Hinweis: Ob ein bereits an das Audiogerät übergebenes Stück in jedem Fall sofort verstummt, ist nicht für alle Installationen geprüft.

## Die lokale Stimme fehlt

1. Einstellungen → „Modelle“ → „Lokale Modelle“. Mit „Suchen“ und dem Filter „Alle Funktionen“ findest du verfügbare Stimmen.
2. Erscheint „Die lokalen Modelle ließen sich nicht ermitteln.“, ist der Katalog nicht erreichbar (Netz, Proxy). „Aktualisieren“ versucht es erneut.
3. „Keine im Client ladbaren Modelle gefunden.“: Für den Suchbegriff gibt es nichts, was der Client lokal ausführen kann.
4. Nach „Installieren“ die Stimme im Reiter „Modelle“ als TTS-Modell wählen und speichern.

Ein abgebrochener Download hinterlässt keine nutzbare Stimme. Installiere sie dann erneut.

## Die Spracheingabe funktioniert nicht

Der Mikrofon-Knopf im Composer („Diktieren (Strg+Umschalt+M)“) startet die Aufnahme; ein zweiter Klick („Aufnahme beenden und erkennen“) beendet sie. Danach steht „Erkenne Sprache …“, bei Erfolg „Abschrift eingefügt. Text prüfen und senden.“

### Kein Modell

- „Spracheingabe aus: kein STT-Modell gewählt (Einstellungen → Modelle → Lokale Modelle)“
- „Spracheingabe aus: die Quelle des gewählten STT-Modells kann keine Sprache erkennen …“
- „Spracheingabe-Modell … ist nicht installiert (Einstellungen → Modelle → Lokale Modelle)“

Die Spracheingabe läuft nur mit einem lokalen Modell. Installiere es unter „Lokale Modelle“ und wähle es aus. Das Modell ist mehrere GB groß.

### Mikrofon

- „Kein Mikrofon gefunden“ oder „Mikrofon nicht verfügbar: …“: Mikrofon anschließen, in Windows den Mikrofonzugriff für Desktop-Apps erlauben, kein anderes Programm darf es exklusiv belegen. Danach erneut versuchen.
- Kommt nichts an, obwohl das Mikrofon geht: Die Wellenform zeigt eine Signalschwelle (Tooltip „Signalschwelle – ziehen: …“). Liegt dein Pegel darunter, gilt die Aufnahme als Stille. Ziehe die Schwelle tiefer.

### Erkennung scheitert

- „Das Modell hat keinen Text geliefert – bitte noch einmal sprechen.“: Deutlicher und näher am Mikrofon sprechen.
- „zu wenig Arbeitsspeicher für das Modell“: Andere Programme schließen; das Modell braucht mehrere GB Arbeitsspeicher.
- „Spracheingabe fehlgeschlagen: …“ bzw. der Dialog „Die Spracheingabe ist fehlgeschlagen.“: Die Ursache steht dahinter.
- Mit „Letzte Aufnahme erneut erkennen“ versuchst du dieselbe Aufnahme noch einmal, ohne neu zu sprechen. „Keine Aufnahme zum Wiederholen“ heißt, dass noch keine Aufnahme vorliegt.

### Es dauert sehr lange

Die Erkennung läuft auf der CPU und ist langsam. Aufnahmen gehen in Stücken von höchstens 30 Sekunden durch das Modell; längere Aufnahmen brauchen entsprechend mehrere Durchläufe. Im Protokoll steht, wie lange es gedauert hat: „Spracheingabe: … ms Audio in … ms erkannt“. Sprich lieber in kurzen Abschnitten.

## Grenzen

- Spracheingabe nur lokal, keine Erkennung über den KI-Dienst.
- Die Spracheingabe braucht viel Arbeitsspeicher und ist auf schwachen Rechnern langsam.
- Hinweis: Die Sprachausgabe über die Enterprise-API ist nicht für alle Installationen geprüft.
