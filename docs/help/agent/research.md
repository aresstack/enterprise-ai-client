---
id: agent.research
title: Recherche mit dem Agenten
keywords: Recherche, Recherche-Agent, Research, Phasen, Phase wählen, Konzept, Quellen, Gliederung, Dokument, Weidezaun, Scope, Rechercheumfang, Ausschlüsse, Außerhalb des Scopes, Wolken, Recherchekarte, Fortschritt, Recherche starten, Gliederung erstellen, Dokument schreiben, Play, gelbe Suchvorschläge, Konzeptkarten, Research-Projekt, Belege, Zwischenstand, research:, recherche:
---

# Recherche mit dem Agenten

Der eingebaute Recherche-Agent führt dich in vier Phasen von einer groben Idee zu einem belegten Dokument: Im **Konzept** klärst du, was recherchiert werden soll, in **Quellen** sucht der Agent selbstständig passende Seiten, in **Gliederung** entsteht das Inhaltsverzeichnis und in **Dokument** schreibt er die Kapitel, jede Aussage mit Beleg. Dieses Kapitel beschreibt den Ablauf Schritt für Schritt, den Weidezaun (den Rechercheumfang), die vier Reiter im Drawer und die Recherchekarte. Voraussetzung ist der Modus „Agent“ mit dem eingebauten Agenten, siehe [Agent-Modus im Überblick](help:agent.overview).

## Das Research-Projekt

Jeder Chat hat genau ein Research-Projekt. Es enthält das Konzept, den Weidezaun, die übernommenen Quellen, die Gliederung und das Dokument. Das Projekt liegt als Ordner im Unterordner `research` neben deiner Konfigurationsdatei und wird mit dem Chat gespeichert, geöffnet und gelöscht:

- „+ Neuer Chat“ beginnt ein neues, leeres Projekt.
- Öffnest du einen gespeicherten Chat, lädt die Anwendung sein Projekt mit Phase, Konzept, Quellen, Gliederung und Dokument.
- Löschst du einen Chat, wird er bis zum Programmende nur vorgemerkt und kann wiederhergestellt werden; erst beim Beenden verschwinden Chat, Anhänge und Projekt.

Das Projekt hält von den Quellen **nur Verweise** (Kennung, Adresse, Version, Prüfsumme), nie Textkopien. Den Text liest der Agent bei Bedarf aus dem Wissensindex.

## Die drei Bedienebenen

Die Recherche wird über drei voneinander getrennte Ebenen gesteuert. Keine schaltet die andere um.

1. **Die Phase** wählst nur du, im Composer über das Auswahlfeld „Phase: …“. Erlaubt ist jede Reihenfolge, auch zurück (Konzept → Quellen → Konzept → Dokument). Die Phase bestimmt, welche Werkzeuge der Agent bekommt. Weder der Agent noch ein Reiter noch ein Knopf wechselt sie.
2. **Die Reiter** „Konzept“, „Quellen“, „Gliederung“ und „Dokument“ im Drawer zeigen die Ergebnisse. Du kannst jederzeit jeden Reiter ansehen, unabhängig von der Phase.
3. **Die Play-Aktion** unten in den Reitern „Quellen“ („Recherche starten“), „Gliederung“ („Gliederung erstellen“) und „Dokument“ („Dokument schreiben“) ist ein ausdrücklicher Arbeitsauftrag. Sie ist nur aktiv, wenn genau ihre Phase gewählt ist und der Agent gerade nicht arbeitet. Deaktiviert nennt ihr Tooltip die Phase, die du dafür wählen musst, etwa „Phase ‚Quellen‘ auswählen, um die Recherche zu starten.“

Während der Agent arbeitet, lässt sich die Phase nicht wechseln („Die Phase lässt sich wechseln, sobald der Agent mit dem Auftrag fertig ist.“).

## Phase 1: Konzept

In der Phase „Konzept“ beschreibst du im Chat, was du wissen willst. Der Agent baut daraus das **Konzept**: einen Baum aus Karten mit Themen und Unterthemen.

So gehst du vor:

1. Wähle im Composer „Phase: Konzept“ (beim neuen Projekt ist sie schon gewählt).
2. Beschreibe dein Vorhaben in normalen Sätzen, zum Beispiel „Ich brauche einen Überblick über den Freigabeprozess für Schnittstellen“.
3. Stellst du eine konkrete Frage oder bittest ausdrücklich um eine Suche („Suche in Confluence nach …“), recherchiert der Agent sofort: Er sucht in den angehakten Wissensquellen, liest die besten Seiten, übernimmt sie als Quellen ins Projekt und antwortet mit Quellennummern. Erst danach ergänzt er das Konzept, und zwar nur aus dem, was er tatsächlich gelesen hat.
4. Nach einer solchen Recherche können über dem Composer **gelbe Suchvorschläge** erscheinen. Ein Klick darauf sucht genau diese Anfrage; danach zeigt der Vorschlag einen Haken („schon gesucht“) oder bietet die Wiederholung an. Ein Vorschlag wechselt nie die Phase und startet keine autonome Recherche. Vorschläge entstehen nur aus gelesenen Seiten; was sich dort nicht belegen lässt, wird verworfen.

### Der Reiter „Konzept“

Der Reiter zeigt das Konzept als Kartenbaum und ist zugleich dein Editor:

- „Tree“ zeigt die Karten als Baum. Über jeder Karte liegen Aktionen: Stift-Symbol zum Umbenennen, ✕ zum Löschen (eine Karte mit Unterkarten erst nach Rückfrage), Plus-Symbol für eine neue Unterkarte. Blattkarten lassen sich verschieben.
- „JSON“ zeigt das Konzept als Text zum direkten Bearbeiten. „Save“ übernimmt die Änderung (ungültiger Text wird abgelehnt), „Discard“ verwirft sie.
- ◀ und ▶ blättern durch frühere Fassungen; eine ältere Fassung wird mit „Save“ wieder zur aktuellen.
- ⟳ lädt die Ansicht neu, das Suchfeld „Search concept…“ filtert Karten nach Namen.
- Ein ungespeicherter Text bleibt stehen, auch wenn der Agent im Hintergrund das Konzept ändert.

Ausgeschlossene Themen erscheinen im Baum abgeblendet. Ohne Konzept steht dort der Hinweis, dass du im Chat beschreiben sollst, was recherchiert werden soll.

## Der Weidezaun (Rechercheumfang)

Der Weidezaun legt fest, was zur Recherche gehört und was nicht. Er besteht aus zwei Arten von Pfosten:

- **Innen**: die Karten des Konzepts und die vom Agenten erfassten Facetten deines Vorhabens.
- **Außen**: die **Ausschlüsse**, also Themen, die ausdrücklich nicht dazugehören.

Die Ausschlüsse siehst du als Wolken oben im Verlauf (Tooltip „Außerhalb des Scopes“), solange du im Modus „Agent“ mit dem eingebauten Agenten bist. So bearbeitest du sie:

1. Klicke auf die Wolke „+ Hinzufügen“, gib das Thema ein und bestätige. In der Phase „Konzept“ ist diese Wolke immer da, auch ohne bisherige Ausschlüsse.
2. Mit ✕ an einer Wolke entfernst du einen Ausschluss.
3. Viele Wolken klappen zu „+ N weitere“; ein Klick klappt sie auf.

Auch der Agent kann im Gespräch Ausschlüsse vorschlagen. Steht ein ausgeschlossenes Thema schon als Karte im Konzept, fragt er nach, ob die Karte entfernt werden soll; ein eindeutiges Ja oder Nein auf diese Frage verarbeitet die Anwendung direkt. Ein Vorschlag, der die Prüfung nicht besteht, wird nicht übernommen; du siehst dann einen Hinweis wie „Der Rechercheumfang wurde NICHT aktualisiert …“ oder „Ausschluss nicht gespeichert: …“.

### Wie der Zaun wirkt

Bei jeder Recherche (autonomer Lauf wie direkte Suche) misst die Anwendung die Textausschnitte der Treffer **semantisch** gegen die Pfosten. Nur was klar außerhalb liegt, fällt heraus; im Zweifel bleibt ein Treffer drin. Dafür braucht die Anwendung ein funktionierendes **Embedding-Modell** (siehe [Wissensindex und Suche](help:concepts.knowledge-index)). Gibt es Ausschlüsse, aber das Embedding-Modell ist nicht nutzbar, sucht der Lauf nicht stillschweigend ohne Zaun weiter, sondern endet mit „Weidezaun nicht prüfbar“. Ohne Ausschlüsse filtert der Zaun nichts.

## Phase 2: Quellen

In der Phase „Quellen“ sucht der Agent selbstständig nach Seiten, die zum Konzept passen.

1. Wähle „Phase: Quellen“.
2. Öffne im Drawer den Reiter „Quellen“ und klicke unten auf „Recherche starten“. Alternativ schreibst du im Chat `recherche:` (oder `research:`) gefolgt vom Auftrag.
3. Der Agent leitet Suchanfragen aus Konzept und Weidezaun ab, durchsucht die angehakten Wissensquellen, liest die vielversprechendsten Treffer und übernimmt passende Seiten als Quellen. Nicht passende Seiten verwirft er, interessante Randtreffer merkt er vor.
4. Am Ende fasst er im Chat zusammen, wie viele Seiten er gelesen und welche Quellen er übernommen hat, und nennt den Grund für das Ende, etwa „genug Belege“, „Seitenbudget erreicht“ oder „keine weiteren passenden Treffer“.

Fehlt noch jeder Auftrag (kein Konzept, keine Beschreibung), startet der Lauf nicht; der Agent bittet dich, im Chat zu beschreiben, was recherchiert werden soll, oder im Konzept mindestens eine Karte anzulegen. Einzelne Fragen beantwortet er auch ohne Konzept direkt aus den Wissensquellen.

Wie viele Seiten ein Lauf liest, begrenzt das **Seitenbudget** (Standard 4, siehe [Agent-Modus im Überblick](help:agent.overview)). Seiten, die in früheren Läufen des Projekts schon gelesen wurden, liest ein Folgelauf nicht erneut. Ein weiterer Klick auf „Recherche starten“ sucht zusätzliche Seiten.

Brichst du den Lauf mit dem Stopp-Knopf ab, ist die Quellensuche angehalten; „Recherche starten“ setzt sie fort. Scheitert der Lauf (zum Beispiel „Wissenssuche nicht erreichbar“), bleiben die bisher übernommenen Quellen im Projekt.

Nach jeder übernommenen Quelle wertet die Anwendung im Hintergrund bis zu acht Bilder der Seite aus (Texterkennung und Bildbeschreibung), sofern in Einstellungen → „Modelle“ Modelle für „Dokumente / OCR“ bzw. „Bildverständnis“ gewählt sind.

### Der Reiter „Quellen“

Er listet die übernommenen Quellen als nummerierte Plaketten mit Titel und Adresse. Darüber steht der Stand des Laufs, etwa „Lese ‚Betriebshandbuch‘ · 2 Seiten gelesen, 1 Quelle übernommen“ oder „Recherche beendet (Seitenbudget erreicht): 4 Seiten gelesen, 3 Quellen übernommen.“, dazu gegebenenfalls „N Treffer vorgemerkt“. Ein Klick auf eine Quelle öffnet sie im Lesebereich.

## Die Recherchekarte

Jede Suche des Agenten, ob autonomer Lauf, direkte Frage oder gelber Suchvorschlag, erscheint im Verlauf als **eine** Recherchekarte, die sich laufend aktualisiert:

- Titel: „Recherche in Confluence“ (oder einer anderen Quelle) bzw. „Recherche in den Wissensquellen“.
- „Suche nach: …“ mit allen Suchanfragen.
- „Wissensquelle: …“, „Treffer: …“ (nicht beim autonomen Lauf).
- „Seiten: 2 / 4“: gelesene Seiten gegen das Seitenbudget, dazu ein Fortschrittsbalken.
- „Quellen: N übernommen“.
- „Aktuell: …“: die Seite, die gerade gelesen wird.
- „Zuletzt:“ die letzten verarbeiteten Seiten, etwa „übernommen: …“, „nicht passend: …“, „nicht lesbar: …“.

Am Ende schließt die Karte mit der Bilanz, zum Beispiel „abgeschlossen: 4 Seiten gelesen, 12 Treffer, 3 Quellen übernommen“. Daneben stehen gelbe Wolken für einzelne Werkzeugaufrufe und grüne Wolken „Überlegungen“.

## Phase 3: Gliederung

1. Wähle „Phase: Gliederung“.
2. Klicke im Reiter „Gliederung“ unten auf „Gliederung erstellen“.
3. Der Agent erstellt aus Konzept, Weidezaun und den übernommenen Quellen ein Inhaltsverzeichnis: Kapitel mit Leitfragen.

Änderungen:

- Schreib im Chat, was anders sein soll (zum Beispiel „Kapitel 3 und 4 zusammenfassen“). Der Agent überarbeitet die Gliederung.
- Oder bearbeite sie selbst: „Bearbeiten“ öffnet den Text (Format: `## Kapitel`, darunter `- Leitfrage`), „Speichern“ übernimmt ihn, „Verwerfen“ kehrt zur gespeicherten Fassung zurück. Bearbeiten geht nur in der Phase „Gliederung“; ein ungespeicherter Text bleibt stehen, auch wenn sich im Hintergrund etwas ändert.

Verlässt du die Phase, wird die Gliederung mit ihren Kapiteln festgehalten; sie ist die Grundlage des Dokuments.

## Phase 4: Dokument

1. Wähle „Phase: Dokument“.
2. Klicke im Reiter „Dokument“ unten auf „Dokument schreiben“.
3. Der Agent schreibt Kapitel für Kapitel entlang der Gliederung. Grundlage sind ausschließlich die Passagen der übernommenen Quellen; jede Aussage trägt ihren Beleg.

Jedes fertige Kapitel wird sofort gespeichert. Bricht das Schreiben ab, bleiben die fertigen Kapitel erhalten; der Reiter zeigt dann etwa „Zwischenstand: 2 von 5 Kapiteln gespeichert. Mit „Dokument schreiben“ geht es ab Kapitel 3 weiter.“

Der Reiter zeigt das Dokument formatiert. Jeder Beleg `[n]` ist ein Link auf Quelle n; ein Klick öffnet sie im Lesebereich. Die Statuszeile nennt Abschnitte, Quellen und belegte Aussagen, zum Beispiel „5 Abschnitte, 3 Quellen, 18 belegte Aussagen (Entwurf)“ bzw. „(abgeschlossen)“.

## Fragen in jeder Phase

Nach dem Start der Quellensuche beantwortet der Agent normale Fragen zuerst aus den Quellen des Projekts. Reichen sie nicht, sucht er in den Wissensquellen nach und übernimmt die gelesenen Seiten ebenfalls ins Projekt, mit Recherchekarte und Weidezaun.

## Typische Probleme

### „Recherche starten“ bleibt grau

- Ist im Composer „Phase: Quellen“ gewählt? Der Tooltip des Knopfs sagt, welche Phase fehlt.
- Arbeitet der Agent noch? Dann steht im Tooltip „Der Agent arbeitet noch am vorherigen Auftrag.“
- Dasselbe gilt für „Gliederung erstellen“ und „Dokument schreiben“ in ihren Phasen.

### Die Recherche findet keine Quellen

- Ist im Drawer-Reiter „Wissensquellen“ mindestens eine Quelle angehakt?
- Ist das Konzept zu eng oder sind zu viele Themen ausgeschlossen? Schärfe Konzept oder Weidezaun und starte erneut.
- Ist `knowledge_search` im Reiter „Tools“ aktiv?

### Der Lauf endet mit „Weidezaun nicht prüfbar“

Es gibt Ausschlüsse, aber das Embedding-Modell ist nicht nutzbar. Prüfe in Einstellungen → „Modelle“ das Modell für Embeddings und die Verbindung zum KI-Dienst. Siehe [Probleme mit dem Agenten](help:troubleshooting.agent).

### Die Reiter oder das Phasenfeld fehlen

Sie gibt es nur im Modus „Agent“ mit dem eingebauten Agenten, nicht mit einem eigenen Agent-Kommando.

## Verwandte Kapitel

- [Agent-Modus im Überblick](help:agent.overview)
- [Werkzeuge von Chat und Agent](help:agent.tools)
- [Wissensindex und Suche](help:concepts.knowledge-index)
- [Lesebereich](help:knowledge.reader)
