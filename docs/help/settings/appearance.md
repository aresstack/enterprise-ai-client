---
id: settings.appearance
title: Einstellungen – Reiter „Darstellung“
keywords: Darstellung, Aussehen, Farben, Sprechblasen, Blasenfarbe, Hintergrund, Schrift, Schriftfarbe, Kontrast, Lesbarkeit, Vorschau, Standard wiederherstellen, Hex, RGB, Farbwähler, Theme, Appearance, Colors, Bubble, chat.bubble.user.background, chat.bubble.assistant.background
since: 0.45.0
---

# Einstellungen – Reiter „Darstellung“

Im Reiter „Darstellung“ wählst du die Farben der Sprechblasen im Chat-Verlauf: je eine Hintergrund- und eine Schriftfarbe für deine eigenen Nachrichten und für die Antworten des Assistenten. Eine Vorschau zeigt sofort, wie beides aussieht, und eine Kontrastwarnung macht schwer lesbare Kombinationen sichtbar. Die Farben gelten unabhängig vom hellen oder dunklen Thema und nach dem Speichern sofort.

Der Reiter hat eine Bereich „Sprechblasen“. Der Hinweis darin lautet: „Farben der Sprechblasen im Verlauf, unabhängig vom Hell-/Dunkel-Thema. Für gute Lesbarkeit etwa weiße Blasen mit schwarzer Schrift wählen; Seiten und Schwänze der Blasen bleiben erhalten. Gilt nach dem Speichern sofort, auch für Antworten mit Überschriften, Listen, Tabellen und Code.“

## Die vier Farbfelder

| Zeile | Feld | Standard | Wirkung |
|---|---|---|---|
| „Eigene Nachrichten“ | „Hintergrund“ | `#1676D2` (Blau) | Fläche deiner Sprechblasen, rechts im Verlauf |
| „Eigene Nachrichten“ | „Schrift“ | `#FFFFFF` (Weiß) | Text deiner Nachrichten, einschließlich Kopfzeile und Zeitstempel |
| „Assistent“ | „Hintergrund“ | `#15827A` (Petrol) | Fläche der Antwortblasen, links im Verlauf |
| „Assistent“ | „Schrift“ | `#FFFFFF` (Weiß) | Text der Antworten, auch in Überschriften, Listen, Tabellen, Zitaten und Code |

Jedes Feld besteht aus einem Farbfeld zum Anklicken und einem Textfeld für den Farbwert.

### Farbwert eintippen

Das Textfeld erwartet die Form `#RRGGBB`, also sechs hexadezimale Ziffern. Das `#` darfst du weglassen, Groß- und Kleinschreibung spielt keine Rolle. Beispiele: `#FFFFFF` für Weiß, `#000000` für Schwarz.

Ist die Eingabe keine Farbe, bekommt das Feld einen roten Rahmen und den Tooltip „Keine Farbe: erwartet wird #RRGGBB, z. B. #FFFFFF für Weiß“. Beim Speichern erscheint dann eine Warnung, zum Beispiel „Eigene Nachrichten, Hintergrund: Ungültiger Farbwert: erwartet wird #RRGGBB (z. B. #FFFFFF). Gespeichert wird die zuletzt gültige Farbe.“ Das Speichern scheitert also nicht; es bleibt die letzte gültige Farbe.

### Farbe auswählen

Ein Klick auf das Farbfeld öffnet eine kleine Auswahl mit 18 Vorschlägen: Weiß, mehrere Grautöne, Schwarz, die bisherigen Blasenfarben und einige Akzentfarben. Über „Weitere Farben …“ öffnest du einen vollständigen Farbwähler. Die gewählte Farbe erscheint sofort im Textfeld und in der Vorschau.

## Vorschau und Kontrastwarnung

Unter den Feldern zeigt die „Vorschau“ zwei Sprechblasen in den gewählten Farben:

- links eine Antwort mit dem Text „So sieht eine Antwort aus.“, darüber der Name des Assistenten. Der Name folgt live dem Feld „Name“ im Reiter [Sprachausgabe](help:settings.speech); ist es leer, steht dort „Assistent“.
- rechts eine eigene Nachricht mit dem Text „Und so sehen meine aus.“ und der Kopfzeile „Du“.

Ist der Kontrast zwischen Hintergrund und Schrift zu gering, erscheint darunter in roter, fetter Schrift „Der Kontrast zwischen Hintergrund und Schrift ist sehr gering (eigene Nachrichten).“ bzw. „… (Assistent).“ Der Client misst dafür das Kontrastverhältnis nach den WCAG-Regeln für Barrierefreiheit und warnt unterhalb eines Verhältnisses von 3:1. Die Warnung ist nur ein Hinweis: speichern kannst du trotzdem.

### Standard wiederherstellen

Der Knopf „Standard wiederherstellen“ (Tooltip: „Blau/Weiß für eigene Nachrichten und Petrol/Weiß für den Assistenten“) setzt alle vier Felder auf die Standardfarben zurück. Gespeichert wird erst mit „Speichern“.

## Schritt für Schritt: helle Blasen mit dunkler Schrift

1. Öffne Einstellungen → „Darstellung“.
2. Klicke bei „Assistent“ auf das Farbfeld „Hintergrund“ und wähle Weiß, oder tippe `#FFFFFF`.
3. Setze bei „Assistent“ die „Schrift“ auf Schwarz (`#000000`).
4. Wiederhole das für „Eigene Nachrichten“, zum Beispiel mit einem hellen Grau als Hintergrund.
5. Prüfe die Vorschau; erscheint keine Kontrastwarnung, ist die Kombination gut lesbar.
6. Klicke „Speichern“. Der Verlauf übernimmt die Farben sofort.

## Was im Hintergrund passiert

Die Farben stehen in vier Schlüsseln der Konfigurationsdatei: `chat.bubble.user.background`, `chat.bubble.user.foreground`, `chat.bubble.assistant.background` und `chat.bubble.assistant.foreground`. Entspricht eine Farbe dem Standard, entfernt der Dialog den Schlüssel; in der Datei stehen also nur Abweichungen. Steht in der Datei ein unlesbarer Wert, zeigt das Feld ihn rot an, und der Verlauf benutzt bis zur Korrektur die Standardfarbe. Siehe [Die Konfigurationsdatei](help:settings.config-file).

Form und Größe der Blasen ändern sich nicht: Seiten und Schwänze bleiben erhalten, nur die Farben wechseln.

## Grenzen

- Einstellbar sind nur die Farben der Sprechblasen im Chat-Verlauf, nicht Schriftart, Schriftgröße oder die Farben des übrigen Fensters.
- Merlins Sprechblasen im Einrichtungsassistenten und im Reiter „Technische Details“ behalten ihre eigenen Farben; sie folgen diesen Einstellungen nicht.
- Hinweis: Wie Hervorhebungen in Codeblöcken mit sehr ungewöhnlichen Farben wirken, ist nicht für alle Kombinationen geprüft. Kontrolliere bei kräftigen Farben eine Antwort mit Code.

## Typische Probleme

### Die Schrift ist kaum lesbar

Achte auf die Kontrastwarnung unter der Vorschau. Wähle helle Schrift auf dunklem Grund oder umgekehrt, oder klicke „Standard wiederherstellen“.

### Meine Farbe wurde nicht übernommen

Vermutlich war der Wert ungültig, das Feld hatte einen roten Rahmen. Tippe den Wert als `#RRGGBB` ein und speichere erneut.

### Wo ändere ich den Namen über den Antworten?

Nicht hier, sondern im Reiter [Sprachausgabe](help:settings.speech) im Bereich „Assistent“. Einen Überblick über alle Reiter gibt [Einstellungen im Überblick](help:settings.overview).
