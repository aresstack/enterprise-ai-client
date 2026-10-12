---
id: settings.speech
title: Einstellungen – Reiter „Sprachausgabe“
keywords: Sprachausgabe, Vorlesen, TTS, Text-to-Speech, Stimme, Sprecher, Voice, Stimme testen, Stimme wechseln, Name des Assistenten, Anzeigename, Merlins Tochter, Merlin, Merlins Stimme, automatisch vorlesen, Absätze, Sätze, Worker, Synthese, lokale Stimme, Piper, Orpheus, Qwen3-TTS, chat.assistant.displayName, speech.voices, setup.merlin.ttsModel
---

# Einstellungen – Reiter „Sprachausgabe“

Im Reiter „Sprachausgabe“ legst du fest, wie der Client Antworten vorliest und mit welchem Namen der Chat-Assistent im Verlauf erscheint. Oben steht der Name des Assistenten, darunter das TTS-Modell mit seiner Stimme und dem Knopf „Stimme testen“, dann das Verhalten beim Vorlesen und zuletzt die eigene Stimme von Merlin, dem Begleiter des Einrichtungsassistenten. Wie das Vorlesen im Chat bedient wird (der Play/Pause-Knopf über dem Verlauf), beschreibt [Antworten vorlesen](help:chat.read-aloud).

Der Reiter hat vier Bereiche: „Assistent“, „Modell und Stimme“, „Sprachausgabe“ und „Merlin“.

## Bereich „Assistent“: der Name des Assistenten

- **Beschriftung:** „Name“, daneben der Knopf „Standard“ (Tooltip: „Den Standardnamen wieder einsetzen“).
- **Standard:** „Merlins Tochter“.
- **Bedeutung:** Der Name steht über jeder Antwort im Verlauf, im Zitat und in den Exporten als Word- oder Markdown-Datei (Tooltip: „Steht über jeder Antwort im Verlauf und in Exporten (chat.assistant.displayName); leer = Standard“).
- **Vorschau:** Eine kleine Sprechblase mit dem Satz „Hallo! Was kann ich für dich tun?“ zeigt den Namen sofort beim Tippen. Derselbe Name erscheint auch gleich in der Vorschau des Reiters [Darstellung](help:settings.appearance).
- **Wirkung:** Der Name ist nur eine Beschriftung. Das Modell erfährt ihn nicht, und ein Wechsel der Stimme ändert ihn nie. Der Hinweis im Dialog sagt dazu: „Der Name gehört dir: ein Wechsel der Stimme unter „Modelle“ ändert ihn nicht. Merlin im Einrichtungsassistenten bleibt, wer er ist.“
- **In der Datei:** Nur ein eigener Name wird unter `chat.assistant.displayName` gespeichert. Steht im Feld der Standardname oder ist es leer, entfernt der Dialog den Schlüssel.

## Bereich „Modell und Stimme“

Hinweis im Dialog: „Modelle mit mehreren Sprechern wählen ohne feste Stimme bei jedem Absatz selbst, dann wechselt die Stimme mitten in der Antwort. Die Stimme gilt je Modell: beim Wechsel des Modells erscheint dessen eigene Stimme.“

### Sprachausgabe (TTS)

Die Auswahlliste „Sprachausgabe (TTS)“ ist dieselbe Auswahl wie im Reiter [Modelle](help:settings.models) unter „Cloud-Modelle“: sie zeigt die TTS-Modelle der Enterprise-API. Standard ist „– keine Auswahl –“. Hast du unter „Modelle“ → „Lokale Modelle“ eine lokale Stimme gewählt, steht hier „– lokales Modell gewählt (Reiter „Lokale Modelle“) –“. Beim ersten Anzeigen des Reiters fragt der Dialog die Modellquellen im Hintergrund ab, damit die Liste gefüllt ist.

### Stimme

- **Beschriftung:** „Stimme“, eine Liste, in die du auch tippen kannst.
- **Bedeutung:** Der Sprecher des gewählten TTS-Modells, so wie der Dienst ihn nennt (Tooltip: „… aus der Liste wählen oder eintippen. Lokale Stimmen haben genau einen Sprecher.“).
- **Liste:** Die Liste zeigt die bekannten Sprecher des Modells, die Standardstimme zuerst. Neben dem Knopf „Stimme testen“ steht, ob eine bekannte Stimme männlich oder weiblich ist, etwa „Stimme Thomas: männlich“.
- **Standard:** die für dieses Modell gespeicherte Stimme; ist keine gespeichert, eine früher global eingestellte Stimme, wenn sie zum Modell passt; sonst die Standardstimme der Modellfamilie. Mitgeliefert sind zwei Familien: Orpheus (Standard „Thomas“, außerdem „Max“ und „Jana“) und Qwen3-TTS (Standard „Vivian“, außerdem unter anderem „Serena“, „Ryan“ und „Aiden“). Hinweis: Sprechernamen sind exakt so einzugeben, wie der Dienst sie erwartet, auch Groß- und Kleinschreibung („thomas“ statt „Thomas“ lehnt der Dienst ab). Die Qwen3-TTS-Namen stammen aus der Modellkarte und sind gegen die Enterprise-API nicht geprüft; kennt dein Dienst andere Namen, tipp sie ein.
- **Gesperrt:** wenn kein TTS-Modell gewählt ist oder eine lokale Stimme gewählt ist. Unter dem Feld steht dann „Kein TTS-Modell gewählt.“ bzw. „Lokale Stimme: genau ein Sprecher.“
- **Je Modell:** Jede Eingabe gilt als ausdrückliche Wahl für genau das gewählte Modell. Wechselst du das Modell, erscheint dessen eigene Stimme; wechselst du zurück, ist deine frühere Wahl wieder da. Gespeichert wird das in `speech.voices`.

Warum eine feste Stimme? Modelle mit mehreren Sprechern wählen ohne Vorgabe bei jeder Anfrage selbst. Da der Client Antworten stückweise vorliest, klänge sonst jeder Absatz womöglich anders. Deshalb schickt der Client immer eine Stimme mit.

### Stimme testen

Der Knopf „Stimme testen“ (Tooltip: „Spricht einen kurzen Satz mit Modell und Stimme dieses Entwurfs“) spricht den Satz „Hallo! So klinge ich, wenn ich dir Antworten vorlese.“ mit dem gewählten Modell und der eingetragenen Stimme. Während des Tests heißt der Knopf „Spreche …“. Danach steht daneben „Gesprochen.“ oder der Grund, warum es nicht ging, zum Beispiel „Kein TTS-Modell gewählt.“ oder „Entwurf unvollständig: …“. Der Test nutzt den ungespeicherten Entwurf; du musst also nicht erst speichern.

## Bereich „Sprachausgabe“: Verhalten beim Vorlesen

Hinweis im Dialog: „Ohne TTS-Modell bleibt der Play/Pause-Orb über dem Verlauf aus. Eine lokale Stimme wählst du unter „Modelle“ → „Lokale Modelle“.“

| Feld | Standard | Bedeutung |
|---|---|---|
| „Neue Antworten automatisch vorlesen“ | aus | Der Orb ist schon beim Start aktiv: jede neue Antwort wird sofort vorgelesen. |
| „Einheit der Synthese“ | „Absätze“ | Wie die Antwort zum Vorlesen geteilt wird: „Absätze“, „Sätze“ oder „Ganze Antwort“. |
| „Worker“ | 2 | Parallele Synthesen, die die Stücke im Hintergrund vorbereiten (1 bis 8). |

Unter den Feldern erklärt der Dialog: „Absätze sind der beste Kompromiss aus Klang und Wartezeit: Sätze klingen früher, aber abgehackter, die ganze Antwort klingt am natürlichsten, beginnt aber erst nach der ganzen Synthese.“ Alle Stücke rechnen im Hintergrund, gespielt wird in Lesereihenfolge; das erste klingt, sobald es fertig ist.

Die Standardwerte (aus, Absätze, 2 Worker) schreibt der Dialog nicht in die Datei.

## Lokale Stimmen

Eine lokale Stimme rechnet im Client selbst, ohne Netz und ohne externes Programm. So richtest du sie ein:

1. Öffne den Reiter [Modelle](help:settings.models) und dort den Unterreiter „Lokale Modelle“.
2. Installiere unter „Vorschläge“ eine deutsche Stimme oder such unter „Hugging Face durchsuchen“ mit der Funktion „Sprachausgabe (TTS)“ nach einer Stimme und installiere den Treffer.
3. Wähle die Stimme oben in „Lokale Modelle je Funktion“ in der Zeile „Sprachausgabe (TTS)“ und speichere.

Eine lokale Stimme hat genau einen Sprecher; das Feld „Stimme“ ist dann gesperrt. Der Knopf „Stimme testen“ bleibt bedienbar. Hinweis: Ob der Test eine lokale Stimme abspielen kann, ist nicht für alle Installationen geprüft; meldet er „Die Quelle dieses Modells kann hier nicht sprechen.“, speichere und probiere die Stimme über den Play-Knopf im Chat aus. Ebenso gilt: Ob lokale Stimmen deutsche Texte gut aussprechen, hängt vom Modell ab; die vorgeschlagenen deutschen Stimmen sprechen regelbasiert aus.

## Bereich „Merlin“: Merlins Stimme

Hinweis im Dialog: „Merlin, der Begleiter des Einrichtungsassistenten, hat eine eigene Stimme, unabhängig vom TTS-Modell des Chats. Ist sie nicht verfügbar, zeigt er seine Sätze nur als Sprechblase.“

| Feld | Standard | Bedeutung |
|---|---|---|
| „Stimme von Merlin“ | das Modell `orpheus-tts-3b-de` der Enterprise-API (mit Katalogpräfix) | TTS-Modell als `[Katalog:]Modell` (Schlüssel `setup.merlin.ttsModel`). |
| „Sprecher von Merlin“ | „Thomas“ | Fester Sprecher dieses Modells, damit Merlin immer gleich klingt (Schlüssel `setup.merlin.ttsVoice`). |

Merlin spricht nur mit dieser Stimme, nie mit der Stimme des Chats. Ist sie nicht erreichbar, weicht er nicht auf eine andere Stimme aus, sondern bleibt bei Sprechblasen. Nur Werte, die von der Vorgabe abweichen, landen in der Datei.

## Was im Hintergrund passiert

Ein TTS-Modell der Enterprise-API spricht über den Endpunkt `audio/speech` der Basis-URL. Hinweis: Dieser Weg ist gegen die Enterprise-API noch nicht vollständig geprüft. Ein Vorlesevorgang spricht immer mit dem Modell und der Stimme, die beim Start der Antwort galten. Speicherst du ein anderes Modell, endet die laufende Ausgabe sofort; die neue Stimme spricht ab der nächsten Antwort. Je vorgelesenem Stück stehen Zeilen im Protokoll (ohne Text und ohne Key), mit denen sich prüfen lässt, welches Modell und welche Stimme wirklich angefragt wurden. Die Satzerkennung für die Einheit „Sätze“ nutzt OpenNLP-Modelle, die du unter „Modelle“ → „Lokale Modelle“ installieren kannst; ohne sie teilt der Client an Satzzeichen.

## Grenzen

- Ohne TTS-Modell gibt es kein Vorlesen; der Orb bleibt aus und nennt im Tooltip den Grund.
- Die Liste der Stimmen kennt nur die mitgelieferten Familien Orpheus und Qwen3-TTS. Für andere Modelle bleibt die Liste leer, du kannst den Namen aber eintippen.
- Der Name des Assistenten ändert nichts am Verhalten des Modells.

## Typische Probleme

### Wo stelle ich die Stimme ein?

Hier im Reiter „Sprachausgabe“, Bereich „Modell und Stimme“: erst das TTS-Modell wählen, dann die Stimme, dann „Stimme testen“ und speichern. Eine lokale Stimme wählst du dagegen unter „Modelle“ → „Lokale Modelle“.

### Die Stimme wechselt mitten in der Antwort

Für das Modell ist keine passende Stimme gesetzt, oder der Dienst kennt den Namen nicht. Wähle eine Stimme aus der Liste, teste sie und speichere. Bleibt das Problem, vergleiche im Protokoll die Zeilen der einzelnen Stücke und setze testweise „Worker“ auf 1.

### Das Feld „Stimme“ ist grau

Es ist kein TTS-Modell gewählt oder eine lokale Stimme. Weitere Hilfe: [Probleme mit der Sprachausgabe](help:troubleshooting.speech).
