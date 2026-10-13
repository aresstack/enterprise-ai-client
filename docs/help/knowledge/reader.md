---
id: knowledge.reader
title: Der Lesebereich
keywords: Lesebereich, Reader, Dokumentansicht, Vorschau, anheften, Stecknadel, Reiter, Tab, Gruppe, Tabgruppe, Farbe, Stern, Favorit, Im Dokument suchen, Strg+F, Suchleiste, Treffer, Seiteninformationen, Adresse kopieren, Im Standardbrowser öffnen, Zurück, Vor, Navigation, Kapitel, Wolken, Unterabschnitte, Bilder, Diashow, Zoom, Bild speichern, Beschreibung, draw.io, Gerendert, Original, Zum aktuellen Chat hinzufügen, Link, interner Link, Confluence-Link, reader, document view, preview, pin, tabs
---

# Der Lesebereich

Der Lesebereich zeigt Seiten und Dokumente aus deinen Wissensquellen direkt im Enterprise AI Client, ohne Browser. Du öffnest dort Treffer, Favoriten und Einträge einer Quelle, heftest wichtige Dokumente als Reiter an, suchst im Text, springst über die Kapitelnavigation und siehst bei Confluence-Seiten Bilder und Anhänge. Dieses Kapitel beschreibt alle Bedienelemente. Wie Anhänge funktionieren, steht in [Anhänge von Wissensquellen](help:knowledge.attachments).

## Den Lesebereich öffnen

Der Lesebereich ersetzt den Chatbereich. Wechselst du im Drawer auf den Reiter „Wissensquellen“ oder „Favoriten“, wird der Chatbereich zum Lesebereich. Darin öffnest du Dokumente so:

- **Ein Klick** auf einen Treffer der Suchleiste, einen Favoriten oder eine Quellenzeile zeigt eine **Vorschau**.
- **Ein Doppelklick** öffnet das Dokument und heftet es sofort an.

Ein Klick auf eine Quellenzeile listet die Einträge der Quelle (höchstens 200). Meldet die Quelle keine, steht dort „Diese Quelle meldet keine Einträge.“ Ist noch nichts gewählt, steht im Lesebereich „Eine Quelle, einen Treffer oder einen Favoriten links anklicken: ein Klick zeigt die Vorschau, ein Doppelklick heftet an.“ Während des Ladens steht „Lade …“.

Zurück zum Chat kommst du über den Drawer-Reiter „Chats“, einen Klick auf einen gespeicherten Chat oder „Neuen Chat beginnen“. Die angehefteten Reiter bleiben dabei erhalten.

## Reiter: Vorschau und angeheftete Dokumente

Jedes Dokument hat einen Reiter oben. Ein Reiter enthält den Titel, eine Stecknadel, einen Stern und ein Kreuz zum Schließen.

### Vorschau und Anheften

Es gibt immer nur **einen** Vorschau-Reiter. Sein Titel ist kursiv, der Hinweis lautet „Vorschau – mit der Stecknadel anheften“. Klickst du einen anderen Treffer an, ersetzt er die Vorschau.

Heften kannst du auf drei Wegen:

1. Klick auf die Stecknadel („Anheften (bleibt offen, bis du ihn schließt)“).
2. Doppelklick auf den Reiter oder auf den Treffer im Drawer.
3. Rechtsklick auf den Reiter → „Anheften“.

Ein angehefteter Reiter bleibt offen, bis du ihn mit dem Kreuz oder über „Schließen“ schließt.

### Reiter ordnen und gruppieren

- Ziehe Reiter mit der Maus an eine andere Stelle.
- Rechtsklick auf einen Reiter → „Neue Gruppe…“ legt eine Gruppe an. Im Dialog „Neue Gruppe“ gibst du einen „Name (darf leer bleiben)“ ein und wählst eine „Farbe“: Blau, Lila, Türkis, Orange, Gelb, Rosa, Grün, Grau oder Rot. Bestätige mit „OK“.
- „Zu Gruppe hinzufügen“ und „Aus Gruppe entfernen“ ordnen weitere Reiter zu.
- Rechtsklick auf die Gruppe bietet „Gruppe bearbeiten…“, „Farbe“, „Gruppe einklappen“ beziehungsweise „Gruppe ausklappen“, „Gruppe auflösen“ (Reiter bleiben offen) und „Gruppe schließen“ (schließt alle Reiter der Gruppe).

### Weitere Befehle am Reiter

- **Stern**: „Als Favorit merken“ oder „Aus den Favoriten entfernen“. Siehe [Favoriten](help:knowledge.favorites).
- **„Zum aktuellen Chat hinzufügen“**: legt einen Verweis auf das Dokument (Titel und Adresse) als Chip in das Eingabefeld des aktuellen Chats und wechselt zum Chat. Den Text der Seite fügt der Befehl nicht ein; der Chat liest ihn bei Bedarf selbst aus der Quelle.
- **„Bilder (n)“**: öffnet die Bilder der Seite in der Diashow (nur bei Seiten mit Bildern).

## Die Werkzeugleiste unten

### Im Dokument suchen

1. Klicke die Lupe („Im Dokument suchen (Strg+F)“) oder drücke Strg+F.
2. Tippe in das Feld „Im Dokument suchen…“. Groß- und Kleinschreibung spielt keine Rolle.
3. Alle Treffer werden gelb markiert, der aktuelle orange. Ein Zähler zeigt die Position.
4. Enter oder „Nächster Treffer (Enter)“ springt weiter, Shift+Enter oder „Vorheriger Treffer (Shift+Enter)“ zurück.
5. Esc oder „Suche schließen (Esc)“ beendet die Suche.

Jeder Reiter hat seine eigene Suche.

### Seiteninformationen

Das Info-Symbol („Seiteninformationen“) zeigt Titel und Adresse des Dokuments. Dort stehen „Adresse kopieren“ und „Im Standardbrowser öffnen“; Letzteres gibt es nur für http- und https-Adressen.

### Zurück und Vor

„Zurück (Maustaste Zurück)“ und „Vor (Maustaste Vor)“ wechseln wie im Browser zwischen den zuletzt angezeigten Reitern; sie sind grau, wenn es keinen Schritt gibt. Die Seitentasten einer Maus funktionieren ebenso.

## Links in einer Seite

Ein Klick auf einen Link in einer Confluence-Seite entscheidet so:

- Ein Link auf eine andere Seite derselben Confluence (gleiches Schema, gleicher Host, gleicher Port und unter dem eingestellten Kontextpfad, etwa `/confluence`) öffnet die Seite **im Lesebereich**, als Vorschau wie ein Treffer. Mit Zurück kommst du zur vorigen Seite. Zeigt der Link auf einen Abschnitt (`…#Abschnitt`), rollt die Seite nach dem Laden dorthin.
- Ein Link auf einen Abschnitt derselben Seite (`#Abschnitt`) rollt nur.
- Alles andere öffnet wie bisher der Browser bzw. das Mailprogramm: fremde Websites, `mailto:`, dieselbe Domain mit anderem Kontextpfad, Anhänge, Blogbeiträge und Seiten, die Confluence nicht findet.

Erkannt werden Seitenadressen mit Seiten-ID (`pages/viewpage.action?pageId=…`, `spaces/…/pages/…`), Kurzlinks (`/x/…`) und Adressen mit Space und Titel (`display/SPACE/Titel`); bei diesen fragt der Client Confluence nach der Seite, bevor sie sich öffnet.

## Kapitelnavigation

Über dem Text steht eine Navigationsleiste, in der die Hauptkapitel als Reihe von Wolken erscheinen. Das aktive Kapitel steht in der Mitte.

- Ein Klick auf eine Wolke springt zum Kapitel.
- Das Mausrad über der Leiste verschiebt die Reihe.
- Der Pfeil an einer Wolke zeigt die Unterabschnitte („Unterabschnitte des Kapitels zeigen“ / „Unterabschnitte einklappen“).
- Lange Namen werden mit „…“ gekürzt; der Tooltip zeigt den vollen Namen.
- Kursive Namen in gestrichelter Wolke sind „automatisch erkannter Abschnitt“: Der Client hat dort einen Themenwechsel erkannt, obwohl das Dokument keine Überschrift hat.

### Woher die Kapitel kommen

Zuerst zeigt die Leiste die Überschriften des Dokuments. Sobald die Passagen dieser Version aufbereitet sind ([Suche, Vorladen und Indexierung](help:knowledge.indexing)), ersetzt der Client sie durch eine inhaltliche Gliederung. Die Namen der Abschnitte formuliert das gewählte Chat-Modell; dazu schickt der Client je Abschnitt höchstens 700 Zeichen Text an dieses Modell. Ohne Chat-Modell verwendet er Namen aus der Textanalyse. Fehlt das Satzmodell, scheitern die Embeddings oder ist das Dokument nicht indexiert, bleiben die Original-Überschriften. Die Gliederung wird je Dokumentversion gespeichert und gilt auch nach einem Neustart.

## Bilder

Bilder zeigt der Lesebereich nur bei Confluence-Seiten. Alle anderen Quellen erscheinen als Text mit Überschriften und Codeblöcken.

### Bilder in der Seite

Bilder werden nachgeladen; bis dahin steht „Bild wird geladen …“, bei Fehlern „Bild nicht ladbar“. Bilder von fremden Servern lädt der Client nie, nur Bilder der Confluence-Quelle selbst.

### Die Diashow

Ein Klick auf ein Bild (oder „Bilder (n)“ im Reiter-Menü) öffnet die Diashow über dem Lesebereich:

- Pfeiltasten links und rechts blättern.
- Mausrad zoomt, Ziehen verschiebt; „+“, „−“ und „0“ zoomen per Tastatur, „0“ setzt zurück.
- Unten steht „n / m — Titel (B × H) | Zoom: x %“.
- Der Knopf oben rechts speichert das Bild („Bild gespeichert: …“).
- Animierte GIFs laufen ab.
- Esc schließt die Diashow.

Mit dem Umschalter „Bild“ / „Beschreibung“ lässt du dir ein Bild beschreiben. Die Beschreibung erzeugt das Modell für Bildverständnis, das du unter Einstellungen → „Modelle“ wählst. Ist keines gewählt, erscheint „Kein Modell für Bildverständnis gewählt (Einstellungen → Modelle).“, während der Arbeit „Beschreibung wird erzeugt …“. Das Bild geht dabei an dieses Modell; beachte [Datenschutz](help:concepts.privacy).

### draw.io-Diagramme

Bei draw.io-Diagrammen gibt es „Gerendert“ und „Original“. „Gerendert“ (Standard) zeichnet das Diagramm aus seinen Daten, ohne eine Datei herunterzuladen. „Original“ zeigt das gespeicherte Vorschaubild; ist dafür eine Anmeldung nötig, steht „Original (Anmeldung nötig)“, scheitert das Laden „Original (nicht ladbar)“. Gespeichert wird als SVG oder PNG. Zur zusätzlichen Anmeldung siehe [Einmalcode (OTP)](help:knowledge.otp).

## Typische Probleme

Statt des Dokuments steht eine Meldung:

- **„Gibt es in der Quelle nicht (mehr).“** Die Seite wurde gelöscht oder verschoben. Suche erneut.
- **„Zugriff verweigert.“** Prüfe Rechte und KeePass-Eintrag der Quelle.
- **„Die Quelle ist gerade nicht erreichbar.“** oder **„Gerade nicht ladbar.“** Netz, VPN oder Server; später erneut versuchen.
- **„Diese Adresse gehört nicht zu der Quelle.“** Der Verweis zeigt auf einen anderen Server als die Quelle.
- **„Unerwartete Antwort der Quelle (Protokoll).“** und **„Nicht lesbar (Protokoll).“** Die Ursache steht im Protokoll.
- **„Die Quelle „…“ ist nicht angebunden.“** Der Favorit oder Verweis gehört zu einer Quelle, die entfernt wurde. Lege sie neu an.

Weitere Prüfschritte: [Fehlersuche bei Wissensquellen](help:troubleshooting.knowledge).
