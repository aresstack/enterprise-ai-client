---
id: knowledge.favorites
title: Favoriten
keywords: Favoriten, Favorit, Stern, merken, Lesezeichen, Bookmark, Ordner, Unterordner, umbenennen, entfernen, löschen, Entf, Drag and Drop, ziehen, sortieren, Favoriten durchsuchen, filtern, favorites.json, Reiter Favoriten, nicht angebunden, favorites, bookmarks, folders
---

# Favoriten

Mit Favoriten merkst du dir Dokumente und ganze Quellen, die du immer wieder brauchst, und öffnest sie später mit einem Klick im Lesebereich. Du ordnest sie in Ordnern und Unterordnern, sortierst sie per Ziehen und filterst sie über ein Suchfeld. Dieses Kapitel zeigt, wie du Favoriten anlegst, ordnest und entfernst und wo sie gespeichert werden. Wie der Lesebereich funktioniert, steht in [Der Lesebereich](help:knowledge.reader).

## Der Reiter „Favoriten“

Öffne den Drawer (Knopf „Chats und Wissensquellen“) und wähle den Reiter „Favoriten“ mit dem gelben Stern. Der Chatbereich wird dabei zum Lesebereich.

Oben steht die Überschrift „FAVORITEN“ und das Suchfeld „Favoriten durchsuchen…“. Darunter folgt der Baum aus Ordnern und Favoriten.

- **Ein Klick** auf einen Favoriten zeigt ihn als Vorschau im Lesebereich.
- **Ein Doppelklick** öffnet ihn und heftet ihn als Reiter an.
- Ein Klick auf eine freie Fläche hebt die Auswahl auf.

Solange es keine Favoriten gibt, steht dort: „Noch keine Favoriten. Der Stern im Lesebereich oder an einem Treffer merkt eine Quelle oder ein Dokument; Rechtsklick legt Ordner an.“

## Einen Favoriten anlegen

Überall, wo ein Stern zu sehen ist, kannst du merken:

1. **An einem Treffer** der Suchleiste „Quellen durchsuchen…“: der Stern rechts in der Trefferzeile.
2. **An einem Eintrag** in der Übersicht einer Quelle im Lesebereich.
3. **Am Reiter** eines geöffneten Dokuments: Stern „Als Favorit merken“.

Ein gefüllter Stern bedeutet: schon gemerkt. Ein erneuter Klick („Aus den Favoriten entfernen“) nimmt den Favoriten wieder heraus.

### In welchem Ordner landet ein neuer Favorit?

- Ist im Reiter „Favoriten“ ein Ordner ausgewählt, landet der Favorit in diesem Ordner.
- Ist ein Favorit ausgewählt, landet der neue im selben Ordner.
- Ist nichts ausgewählt, landet er auf der obersten Ebene.

Der neue Favorit wird danach ausgewählt. Merkst du also mehrere Dokumente hintereinander, landen sie im selben Ordner.

Jedes Dokument kann nur einmal Favorit sein. Merkst du es erneut, entsteht kein Duplikat.

## Ordner anlegen und verwalten

Alle Befehle erreichst du per Rechtsklick:

- **Auf einer freien Fläche**: „Neuer Ordner“ legt einen Ordner auf der obersten Ebene an. Der Client fragt nach „Name des neuen Ordners:“.
- **Auf einem Ordner**: „Neuer Unterordner“, „Umbenennen“ (Abfrage „Neuer Name:“) und „Entfernen“.
- **Auf einem Favoriten**: „Entfernen“.

Die Taste Entf entfernt den ausgewählten Eintrag ebenfalls.

Enthält ein Ordner noch etwas, fragt der Client nach: „Ordner „X“ mit n Favoriten und allen Unterordnern entfernen?“. Bestätigst du, sind Ordner, Unterordner und alle darin liegenden Favoriten weg. Die Dokumente in der Quelle bleiben natürlich unberührt.

Ordner dürfen gleich heißen, auch auf derselben Ebene. Gib ihnen trotzdem unterscheidbare Namen, damit du sie findest.

## Ordnen per Ziehen

Mit der Maus ordnest du Favoriten und Ordner um:

1. Ziehe einen Eintrag **auf einen Ordner**, um ihn hineinzulegen.
2. Ziehe ihn **zwischen zwei Zeilen**, um ihn genau dort einzusortieren.
3. Ziehe ihn **auf eine freie Fläche**, um ihn ans Ende der obersten Ebene zu legen.

Ein Ordner lässt sich nicht in sich selbst oder in einen seiner Unterordner ziehen. Solange ein Suchtext im Filterfeld steht, ist das Ziehen abgeschaltet; leere zuerst das Suchfeld.

## Favoriten durchsuchen

Tippe in „Favoriten durchsuchen…“ („Favoriten nach Titel, Adresse und Ordner filtern“). Der Baum zeigt nur noch passende Einträge; ein Treffer im Ordnernamen zeigt dessen Inhalt. Passt nichts, steht „Keine passenden Favoriten“. Leere das Feld, um wieder alles zu sehen.

## Wo die Favoriten gespeichert werden

Favoriten liegen in der Datei `favorites.json` neben der Konfigurationsdatei im Einstellungsordner. Den Ordner öffnest du unter Einstellungen mit „Einstellungsordner öffnen“; Einzelheiten in [Die Konfigurationsdatei](help:settings.config-file).

- Jede Änderung wird sofort gespeichert. Der Client schreibt erst in eine Zwischendatei und ersetzt dann die alte, damit bei einem Absturz keine halbe Datei entsteht.
- Kann die Datei nicht geschrieben werden (etwa wegen fehlender Rechte), steht ein Eintrag im Protokoll. Deine Favoriten gelten dann bis zum Beenden des Programms, gehen danach aber verloren.
- Ist die Datei beschädigt, startet der Client ohne Favoriten statt mit einem Fehler. Bewahre in diesem Fall eine Kopie der Datei auf, bevor du neue Favoriten anlegst, denn die nächste Änderung überschreibt sie.
- Eine Datei im älteren Format (eine einfache Liste ohne Ordner) wird nicht übernommen.

Ein Favorit speichert Titel, Adresse und die Quelle, zu der er gehört, nicht den Inhalt des Dokuments. Beim Öffnen liest der Client das Dokument immer frisch aus der Quelle.

## Typische Probleme

### Ein Favorit zeigt „Die Quelle „…“ ist nicht angebunden.“

Die Quelle, aus der der Favorit stammt, wurde entfernt oder hat eine neue Identität bekommen, etwa durch eine geänderte Adresse. Lege die Quelle wieder an; stimmt die Identität nicht mehr, merke das Dokument neu und entferne den alten Favoriten.

### Ein Favorit zeigt „Gibt es in der Quelle nicht (mehr).“

Das Dokument wurde in der Quelle gelöscht oder verschoben. Suche es erneut und merke es neu.

### Die Favoriten sind nach einem Neustart weg

Prüfe im Protokoll, ob das Schreiben von `favorites.json` gescheitert ist, und ob der Einstellungsordner beschreibbar ist. Siehe auch [Fehlersuche bei Wissensquellen](help:troubleshooting.knowledge).

### Ich kann nichts verschieben

Steht ein Text im Suchfeld „Favoriten durchsuchen…“? Dann ist Ziehen gesperrt. Leere das Feld.
