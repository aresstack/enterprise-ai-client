---
id: chat.read-aloud
title: Antworten vorlesen lassen
keywords: vorlesen, Vorlesen, Sprachausgabe, Text-to-Speech, TTS, Stimme, Sprecher, Lautsprecher, anhören, Play, Pause, Orb, Kugel, stoppen, Stopp, automatisch vorlesen, Stimme wechseln, Stimme testen, Einheit der Synthese, Worker, Absätze, Sätze, read aloud, speech output, voice, speaker
---

# Antworten vorlesen lassen

Der Enterprise AI Client kann die Antworten des Assistenten vorlesen. Du startest und stoppst das Vorlesen mit dem runden Play/Pause-Knopf (dem „Orb“) oben rechts über dem Verlauf. Dieses Kapitel erklärt die Bedienung, wie du die Stimme wählst, was beim Vorlesen im Hintergrund passiert und was du tun kannst, wenn nichts zu hören ist. Voraussetzung ist ein eingerichtetes Sprachausgabe-Modell (TTS-Modell).

## Vorlesen starten und stoppen

1. Oben rechts über dem Verlauf, neben der Bildlaufleiste, sitzt der Orb.
2. Klicke darauf (Tooltip „Letzte Antwort vorlesen (neue Antworten werden automatisch vorgelesen)“). Die letzte vollständige Antwort wird sofort vorgelesen.
3. Der Orb **bleibt danach aktiv**: Jede neue Antwort, die du ab jetzt bekommst, wird automatisch vorgelesen, sobald sie fertig ist.
4. Ein erneuter Klick (Tooltip „Vorlesen pausieren“) beendet die laufende Ausgabe sofort und schaltet das automatische Vorlesen ab.

„Pausieren“ bedeutet hier Anhalten: Die Ausgabe merkt sich keine Stelle. Klickst du wieder auf Play, beginnt die letzte Antwort von vorn.

Im Tooltip steht in Klammern immer auch die aktuelle Stimme, etwa „(Stimme: Modellname / Sprecher (Katalog))“, oder, wenn Vorlesen nicht möglich ist, der Grund.

Was nicht vorgelesen wird:

- eine Antwort, während sie noch entsteht; vorgelesen wird erst die fertige Antwort,
- abgebrochene und fehlgeschlagene Antworten,
- Antworten aus einem gespeicherten Chat, den du gerade öffnest (beim Laden wird nie von selbst vorgelesen; mit Play kannst du die letzte Antwort aber anhören),
- deine eigenen Nachrichten, Hinweiszeilen und Wolken.

Beginnst du einen neuen Chat oder öffnest du einen anderen, verstummt eine laufende Ausgabe sofort. Kommt eine neue Antwort, während noch eine ältere gesprochen wird, endet die ältere und die neue beginnt.

## Seiten im Lesebereich vorlesen

Unten im Lesebereich liegt ein eigener Play-Knopf, daneben der Regler für die Sprechgeschwindigkeit.

1. Play liest die Seite des aktiven Reiters vor, ohne Bilder und Diagramme.
2. Ein Klick während der Ausgabe pausiert sie an der aktuellen Stelle (Tooltip „Vorlesen pausieren“).
3. Play setzt sie genau dort fort (Tooltip „Vorlesen fortsetzen“), nicht wieder am Seitenanfang. Das geht beliebig oft hintereinander.
4. Ein Doppelklick auf eine Textstelle liest ab dort neu, auch während der Pause.

Wechselst du den Reiter, schließt du die Seite, bekommt sie neuen Inhalt oder liest der Chat etwas vor, endet das Vorlesen der Seite, und die gemerkte Stelle verfällt.

Der Regler stellt die Sprechgeschwindigkeit von 0,5× bis 2,0× ein, in Schritten von 5 %; der Wert steht daneben (etwa „1,25×“). Die Geschwindigkeit ändert nur das Tempo, nicht die Tonhöhe. Sie wirkt sofort, auch mitten im Satz und während einer Pause, und gilt für jedes TTS-Modell, weil der Client sie bei der Wiedergabe umsetzt. Sie gilt für die gemeinsame Sprachausgabe, also auch für das Vorlesen im Chat, und bleibt beim Wechsel des TTS-Modells erhalten. Nach einem Neustart beginnt sie wieder bei 1,0×.

## Automatisch vorlesen ab Programmstart

Unter Einstellungen → „Sprachausgabe“ → Bereich „Sprachausgabe“ gibt es das Häkchen **„Neue Antworten automatisch vorlesen“**. Ist es gesetzt, ist der Orb schon beim Start aktiv, und jede neue Antwort wird ohne Klick vorgelesen. Mit einem Klick auf den Orb schaltest du das für die laufende Sitzung trotzdem ab.

Für Fortgeschrittene: Schlüssel `speech.readAloud.autoStart` (Standard aus).

## Stimme wählen und testen

Das Modell wählst du unter Einstellungen → „Modelle“, die Stimme unter Einstellungen → „Sprachausgabe“ im Bereich „Stimme“:

1. Wähle unter „Modelle“ in der Kategorie „Sprachausgabe (TTS)“ das **TTS-Modell**. Nur dort wird es gewählt; „Sprachausgabe“ zeigt es nur an. Es gibt zwei Arten:
   - Modelle des KI-Dienstes (aus dem Modellkatalog des Servers),
   - **lokale Stimmen**, die im Client selbst laufen. Sie installierst du unter „Modelle“ → „Lokale Modelle“.
2. Wähle im Feld **„Stimme“** den Sprecher. Die Liste zeigt die Sprecher, die für dieses Modell bekannt sind; du kannst den Namen auch eintippen, so wie der Dienst ihn nennt. Lokale Stimmen haben genau einen Sprecher, das Feld ist dann gesperrt („Lokale Stimme: genau ein Sprecher.“). Zu manchen Sprechern zeigt der Dialog einen Hinweis wie „Stimme … : weiblich“.
3. Klicke auf **„Stimme testen“**. Der Knopf zeigt „Spreche …“, und du hörst einen kurzen Satz mit genau diesem Modell und dieser Stimme, noch bevor du speicherst. Danach steht dort „Gesprochen.“ oder der Grund, warum es nicht ging.
4. Klicke auf „Speichern“. Die neue Stimme gilt **sofort**, ohne Neustart.

### Stimme wechseln

Die Stimme gehört **zum Modell**: Für jedes TTS-Modell merkt sich das Programm einen eigenen Sprecher. Wechselst du das Modell, erscheint dessen gespeicherte oder Standardstimme. Der Grund: Modelle mit mehreren Sprechern würden ohne feste Stimme bei jedem Absatz selbst wählen, und die Stimme wechselte dann mitten in der Antwort. Deshalb spricht der Client einen Vorlesevorgang immer mit genau einem Modell und einer Stimme, auch für Abschnitte, die er als Englisch erkennt.

Speicherst du ein anderes TTS-Modell, während der Orb aktiv ist, endet die laufende Ausgabe. Ist die neue Stimme nicht verfügbar, fällt der Orb auf Play zurück und ist ausgegraut.

Der Anzeigename des Assistenten („Merlins Tochter“) hängt nicht an der Stimme; ein Stimmwechsel ändert ihn nie. Auch Merlin im Einrichtungsassistenten hat eine eigene, davon unabhängige Stimme (Bereich „Merlin“ im selben Reiter).

Mehr zu allen Feldern in [Sprachausgabe einstellen](help:settings.speech).

## Was beim Vorlesen im Hintergrund passiert

1. **Aufbereiten**: Die Antwort ist Markdown. Vor dem Sprechen fallen Codeblöcke und Diagramme weg, Links werden zu ihrem Linktext, nackte Webadressen werden ausgelassen, Formatierungszeichen (Sternchen, Rauten, Tabellenstriche, Aufzählungszeichen) verschwinden.
2. **Teilen**: Der Text wird in Stücke geteilt, je nach Einstellung „Einheit der Synthese“:
   - **„Absätze“** (Standard): bester Kompromiss aus Klang und Wartezeit,
   - **„Sätze“**: das erste Stück klingt früher, aber abgehackter,
   - **„Ganze Antwort“**: klingt am natürlichsten, beginnt aber erst nach der ganzen Synthese.
   Sehr lange Absätze werden zusätzlich an Satzgrenzen geteilt, damit kein Stück zu groß wird. Satzgrenzen erkennt der Client mit Sprachmodellen für die Textanalyse (Einstellungen → „Modelle“ → NLP); ohne sie schneidet er einfach an Satzzeichen.
3. **Synthese**: Alle Stücke gehen sofort an einen kleinen Pool paralleler Synthesen (Einstellung „Worker“, 1 bis 8, Standard 2). Gespielt wird strikt in Lesereihenfolge; das erste Stück klingt, sobald es fertig ist, während die nächsten schon im Hintergrund entstehen.
4. **Wiedergabe**: über das Standard-Audiogerät deines Rechners.

Bei einem Modell des KI-Dienstes geht dafür der Text der Antwort an den Server; bei einer lokalen Stimme bleibt alles auf deinem Rechner. Siehe [Lokal oder Cloud](help:concepts.local-vs-cloud).

Für Fortgeschrittene: `speech.readAloud.chunking` (PARAGRAPHS, SENTENCES, ANSWER), `speech.readAloud.synthesisWorkers` (1–8), `model.tts` und `speech.voices` in der Konfigurationsdatei. Je Stück schreibt das Programm eine Diagnosezeile ins Protokoll („TTS request: …“, „TTS audio: …“) mit Modell, Stimme und Länge, nie mit dem Text.

## Typische Probleme

### Der Orb ist ausgegraut

Fahre mit der Maus darüber; der Tooltip nennt den Grund:

- „Sprachausgabe aus: kein TTS-Modell gewählt (Einstellungen → Modelle)“: Wähle unter Einstellungen → „Sprachausgabe“ oder „Modelle“ ein TTS-Modell und speichere.
- „Sprachausgabe aus: die Quelle des gewählten TTS-Modells ist nicht verfügbar …“: Bei einer lokalen Stimme prüfe unter „Modelle“ → „Lokale Modelle“, ob sie installiert ist.

### Es erscheint die Meldung „Die Antwort konnte nicht vorgelesen werden.“

Ein Fenster „Vorlesen“ nennt den Grund und die verwendete Stimme. Häufige Ursachen: Der KI-Dienst ist nicht erreichbar, das Modell kennt den eingetragenen Sprecher nicht, oder es gibt kein Audiogerät. Prüfe mit „Stimme testen“ in den Einstellungen, ob Modell und Stimme zusammenpassen. Je Vorlesevorgang erscheint höchstens eine solche Meldung.

### Nichts zu hören, aber keine Fehlermeldung

1. Ist der Orb aktiv (Tooltip „Vorlesen pausieren“)?
2. Gibt es überhaupt eine fertige Antwort? Abgebrochene Antworten werden nicht gelesen.
3. Lautstärke und Ausgabegerät des Systems prüfen.
4. Bei „Ganze Antwort“ beginnt die Ausgabe erst nach der kompletten Synthese; bei langen Antworten dauert das.

### Die Stimme wechselt mitten in der Antwort

Bei neueren Versionen spricht ein Vorgang immer mit einer festen Stimme. Trage im Feld „Stimme“ ausdrücklich einen Sprecher ein und speichere.

Weitere Fälle in [Probleme mit der Sprache](help:troubleshooting.speech).

## Grenzen

- Vorgelesen wird nur die jeweils letzte fertige Antwort; einzelne ältere Antworten lassen sich nicht gezielt anwählen.
- Kein Fortsetzen an der unterbrochenen Stelle.
- Codeblöcke, Diagramme und Webadressen werden nicht gesprochen; Tabellen werden ohne Struktur gelesen.
- Englische Passagen spricht dieselbe (meist deutsche) Stimme.
- Die Qualität hängt vom gewählten Modell ab.

Zur Spracheingabe, dem Gegenstück zum Vorlesen, siehe [Spracheingabe und Diktat](help:chat.dictation).
