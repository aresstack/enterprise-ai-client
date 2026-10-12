---
id: chat.dictation
title: Spracheingabe und Diktat
keywords: Diktat, diktieren, Spracheingabe, Spracherkennung, Mikrofon, sprechen statt tippen, Speech-to-Text, STT, Abschrift, Aufnahme, Strg+Umschalt+M, Signalschwelle, Pegel, Rauschen, Erneut, Erkennung abbrechen, verwerfen, dictation, voice input, microphone, transcription
---

# Spracheingabe und Diktat

Statt deine Frage zu tippen, kannst du sie diktieren. Der Mikrofonknopf im Composer nimmt deine Stimme auf, ein lokales Spracherkennungsmodell wandelt sie in Text um, und der Text landet im Eingabefeld, wo du ihn prüfen und senden kannst. Die Erkennung läuft vollständig auf deinem Rechner; deine Aufnahme verlässt ihn nicht. Dieses Kapitel erklärt Voraussetzungen, Bedienung, was im Hintergrund passiert und was bei Problemen hilft.

## Voraussetzungen

- Ein **Mikrofon**, das dein Betriebssystem als Aufnahmegerät kennt.
- Ein installiertes **lokales Spracherkennungsmodell** (STT-Modell). Der KI-Dienst selbst bietet keine Spracherkennung an; diktiert wird ausschließlich mit einem Modell, das im Client läuft.

Das Modell richtest du so ein:

1. Öffne Einstellungen → „Modelle“ → „Lokale Modelle“ und installiere dort das Spracherkennungsmodell.
2. Wähle es im Reiter „Modelle“ in der Kategorie „Spracheingabe (STT)“ aus und speichere.

Alternativ bietet der Einrichtungsassistent beim ersten Start die lokale Spracheingabe als optionalen Schritt an. Mehr dazu in [Modelle einrichten](help:getting-started.models) und [Modelle einstellen](help:settings.models).

Hinweis: Das mitgelieferte Spracherkennungsmodell ist ein größeres Audiomodell (aus der Gemma-Familie) und braucht entsprechend Arbeitsspeicher. Die genaue Download-Größe und die Mindestausstattung sind hier nicht festgehalten; der Reiter „Lokale Modelle“ zeigt die Angaben zum Modell.

## Diktieren: Schritt für Schritt

1. Setze den Cursor im Eingabefeld an die Stelle, an der der Text stehen soll. Markierst du Text, wird er durch das Diktat ersetzt.
2. Klicke auf den **Mikrofonknopf** im Composer (Tooltip „Diktieren (Strg+Umschalt+M)“) oder drücke **Strg+Umschalt+M**.
3. Die Aufnahme läuft: Der Knopf wird orange und zeigt ein Stopp-Quadrat. Im Composer erscheinen eine Wellenform deiner Stimme, ein Pegelbalken und die Statuszeile „● Aufnahme m:ss“ mit der Laufzeit.
4. Sprich deine Frage in normalem Tempo.
5. Klicke erneut auf den Knopf (oder drücke wieder Strg+Umschalt+M). Die Aufnahme endet, die Statuszeile zeigt „Erkenne Sprache …“.
6. Nach der Erkennung steht der Text an der Cursorposition, mit passenden Leerzeichen zum Text davor und danach. Die Statuszeile meldet „Abschrift eingefügt. Text prüfen und senden.“
7. Korrigiere den Text bei Bedarf und klicke auf „Senden“.

### Diktieren und gleich senden

Klickst du während der Aufnahme direkt auf „Senden“ oder drückst du Enter, endet die Aufnahme, die Sprache wird erkannt, und die Nachricht geht anschließend ohne weiteren Klick hinaus. Die Statuszeile zeigt dabei „Erkenne Sprache … danach wird gesendet“. Das ist praktisch für kurze Fragen, nimmt dir aber die Gelegenheit, den Text vorher zu prüfen.

### Abbrechen und verwerfen

- **Esc** während der Aufnahme verwirft sie („Diktat verworfen.“).
- Während der Erkennung wird der Knopf zu einem Kreuz (Tooltip „Erkennung abbrechen“). Ein Klick darauf oder Esc bricht ab („Erkennung abgebrochen.“); es wird kein Text eingefügt.
- Auch das kleine Kreuz in der Statuszeile verwirft die Aufnahme.

## Die Signalschwelle

In der Wellenform liegt eine rote Linie, die **Signalschwelle**. Alles unterhalb davon gilt als Rauschen, alles darüber als Sprache. Du kannst die Linie mit der Maus nach oben oder unten ziehen:

- In einer lauten Umgebung (Lüfter, Gespräche im Raum) ziehst du sie höher, damit Hintergrundgeräusche nicht als Sprache gelten.
- Bei einem leisen Mikrofon oder leiser Stimme ziehst du sie tiefer.

Der Pegelbalken zeigt dir laufend, wie laut das ankommende Signal ist. Liegt deine Stimme deutlich über der Linie, passt die Einstellung.

## Was im Hintergrund passiert

1. **Aufnahme**: Der Client nimmt über das Standard-Aufnahmegerät des Systems in Mono mit 16 kHz auf, dem Format, das das Spracherkennungsmodell erwartet.
2. **Zerlegen**: Lange Aufnahmen werden für die Erkennung in Stücke von höchstens 30 Sekunden geteilt und nacheinander erkannt; die Ergebnisse werden zusammengesetzt.
3. **Erkennung**: Das lokale Modell wandelt die Aufnahme in Text um. Das läuft im Programm selbst; es gibt keinen Netzwerkzugriff und keinen Aufruf des KI-Dienstes.
4. **Einfügen**: Der Text wird ins Eingabefeld geschrieben. Gesendet wird er erst, wenn du auf „Senden“ klickst (außer im Fall „Diktieren und gleich senden“).
5. **Als Diktat gekennzeichnet**: Eine Nachricht, deren Text aus der Spracherkennung stammt, geht mit dem Hinweis an das Chat-Modell, dass sie diktiert wurde, auch wenn du die Abschrift vorher von Hand korrigiert hast. Das Modell liest ein unbekanntes Wort dann eher als Erkennungsfehler eines ähnlich klingenden Wortes und sucht nicht danach. Leerst du das Eingabefeld und tippst neu, gilt die Nachricht wieder als getippt. Der Hinweis steht nicht im Verlauf.

Datenschutz: Weil die Erkennung lokal läuft, verlässt die Aufnahme deinen Rechner nicht. Erst der erkannte Text geht beim Senden wie eine getippte Nachricht an das Chat-Modell. Siehe [Lokal oder Cloud](help:concepts.local-vs-cloud) und [Datenschutz](help:concepts.privacy).

## Typische Probleme

### Der Mikrofonknopf ist ausgegraut

Fahre mit der Maus darüber; der Tooltip nennt den Grund:

- „Spracheingabe aus: kein STT-Modell gewählt (Einstellungen → Modelle → Lokale Modelle)“: Installiere und wähle ein Spracherkennungsmodell wie oben beschrieben.
- „Spracheingabe aus: die Quelle des gewählten STT-Modells kann keine Sprache erkennen …“: Das gewählte Modell ist kein lokales Spracherkennungsmodell. Wähle unter „Modelle“ das lokal installierte Modell.

Während eine Antwort läuft, ist Diktieren ebenfalls nicht möglich.

### „Mikrofon nicht verfügbar: …“

Das Betriebssystem stellt kein passendes Aufnahmegerät bereit. Prüfe:

1. Ist ein Mikrofon angeschlossen und in den Systemeinstellungen als Standard-Aufnahmegerät gewählt?
2. Benutzt ein anderes Programm (etwa eine Videokonferenz) das Mikrofon exklusiv? Beende es testweise.
3. Erlaubt dein System Desktop-Programmen den Mikrofonzugriff (Datenschutzeinstellungen von Windows)?

### „Das Modell hat keinen Text geliefert – bitte noch einmal sprechen.“

Die Aufnahme war vermutlich zu leise, zu kurz oder bestand nur aus Rauschen. Sprich näher am Mikrofon, prüfe den Pegelbalken und passe die Signalschwelle an.

### „Spracheingabe fehlgeschlagen: …“

Die Erkennung ist gescheitert; zusätzlich erscheint ein Fenster „Spracheingabe“ mit dem Grund. Neben der Statuszeile gibt es dann den Knopf **„Erneut“** (Tooltip „Letzte Aufnahme erneut erkennen“): Er schickt dieselbe Aufnahme noch einmal durch die Erkennung, ohne dass du neu sprechen musst.

Lautet der Grund „zu wenig Arbeitsspeicher für das Modell“, reicht der Speicher des Programms für das Modell nicht. Schließe andere speicherhungrige Programme, entlade nicht benötigte lokale Modelle und versuche es erneut. Weitere Hilfe in [Probleme mit der Sprache](help:troubleshooting.speech).

### Die Erkennung dauert sehr lange

Die erste Erkennung nach dem Start muss das Modell erst laden. Auch lange Aufnahmen brauchen Zeit, weil jedes 30-Sekunden-Stück einzeln erkannt wird. Diktiere lieber in kürzeren Abschnitten.

### Fachbegriffe werden falsch erkannt

Das ist eine Grenze des Modells. Korrigiere solche Wörter im Eingabefeld vor dem Senden. Übersiehst du einen Fehler, versucht der Assistent die gemeinte Aussage aus dem Satz zu erschließen und fragt bei echter Unklarheit nach („Meinst du …?“).

## Grenzen

- Nur lokale Erkennung; ein Spracherkennungsdienst im Netz wird nicht genutzt.
- Kein Dauerdiktat und kein Sprachbefehl: Jede Aufnahme startest und beendest du selbst.
- Keine Erkennung, während eine Antwort läuft.
- Die Qualität hängt von Mikrofon, Umgebung und Modell ab; Satzzeichen und Fachbegriffe sind nicht immer korrekt.
- Das Modell braucht spürbar Arbeitsspeicher und Rechenzeit.

Das Gegenstück, das Vorlesen von Antworten, beschreibt [Antworten vorlesen lassen](help:chat.read-aloud).
