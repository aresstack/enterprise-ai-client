---
id: troubleshooting.models
title: Probleme mit Modellen
keywords: Modell, Modellliste, Modell fehlt, GET /models, Modelle aktualisieren, Chat-Modell, Embedding-Modell, Embedding-Dimension, Dimension, 768, Modellwechsel, neu indexieren, Namensraum, lokales Modell, ONNX, ONNX Runtime, lädt nicht, Lokale Modelle, Cloud-Modelle, capabilities, model not found
---

# Probleme mit Modellen

Der Client arbeitet mit zwei Arten von Modellen: einem Chat-Modell, das antwortet, und einem Embedding-Modell, das Texte für die semantische Suche in Zahlenvektoren übersetzt. Beide können vom KI-Dienst kommen („Cloud-Modelle“) oder lokal im Client laufen („Lokale Modelle“). Dieses Kapitel hilft, wenn ein Modell nicht in der Liste auftaucht, die Embedding-Dimension nicht passt, nach einem Modellwechsel die Wissenssuche nichts mehr findet oder ein lokales Modell nicht lädt. Grundlagen stehen in [Modelle einstellen](help:settings.models).

## Das Modell steht nicht in der Liste

Die Listen im Reiter „Modelle“ → „Cloud-Modelle“ entstehen aus der Antwort des KI-Dienstes auf GET /models. Steht dort „Noch keine Modellliste. „Modelle aktualisieren“ fragt GET /models ab.“, wurde noch nichts abgefragt.

1. Einstellungen → „Modelle“ → „Modelle aktualisieren“. Während der Abfrage steht „Frage Modellquellen ab …“.
2. Scheitert die Abfrage, steht der Grund dort, zum Beispiel:
   - „GET /models: HTTP 401“ oder „HTTP 403“: API-Key prüfen ([KeePass](help:troubleshooting.keepass)).
   - „GET /models nicht erreichbar (…)“: Netz oder Proxy ([Netzwerk](help:troubleshooting.network)).
   - „GET /models: Antwort nicht lesbar (…)“: Der Dienst liefert kein OpenAI-kompatibles Format; Basis-URL prüfen (endet meist auf „/v1“).
   - „API-Key nicht verfügbar (…)“: KeePass liefert den Key nicht.
3. Kommt die Liste, aber dein Modell fehlt: Die Listen zeigen bewusst nur Modelle, deren gemeldete Fähigkeiten passen („Die Listen zeigen nur Modelle, deren gemeldete Fähigkeiten passen.“). Ein Modell, das der Dienst nur als Embedding-Modell meldet, erscheint nicht unter Chat und umgekehrt. Meldet der Dienst gar keine Fähigkeiten, erscheint das Modell in beiden Listen.
4. Du kannst den Modellnamen in den Feldern trotzdem selbst eintippen. Er muss genau so geschrieben sein, wie der Dienst ihn kennt.
5. Prüfe mit „Verbindung testen“ (Reiter „KI-Dienst“): Fehlt das Modell beim Dienst, meldet der Schritt „GET /models“ „„…“ fehlt (chat.model prüfen). Verfügbar: …“ bzw. der Schritt „Embedding-Modell“ „„…“ fehlt (embedding.model prüfen). Verfügbar: …“. Die Liste dahinter zeigt die exakten Namen.

Steht im Auswahlfeld „– keine Auswahl –“, ist kein Modell gewählt. „– lokales Modell gewählt (Reiter „Lokale Modelle“) –“ bedeutet, dass ein lokales Modell aktiv ist.

## Die Embedding-Dimension passt nicht

Jedes Embedding-Modell erzeugt Vektoren einer festen Länge, der Dimension (z. B. 768 oder 1024). Der Client muss sie kennen, weil der Index danach aufgebaut ist.

### Die Dimension fehlt

Startet der Client mit dem Problem „embedding.dimension“ „fehlt (Pflichtangabe, z. B. 768)“, trage im Reiter „KI-Dienst“ unter „Embeddings“ das Feld „Dimension“ ein. Den richtigen Wert nennt die Dokumentation des Modells oder der Betreiber.

### Der Dienst liefert eine andere Länge

Stimmt die eingestellte Dimension nicht mit dem überein, was der Dienst tatsächlich liefert, bricht der Client nicht ab. Er übernimmt die gelieferte Länge und schreibt eine Warnung ins Protokoll:

„Embedding-Dimension: konfiguriert X, vom Dienst geliefert Y (Modell …); übernommen. Index und Suche nutzen ab jetzt den Namensraum …; „embedding.dimension“ in den Einstellungen auf Y setzen.“

1. Im Protokoll nach „Embedding-Dimension“ suchen.
2. Den Wert Y unter „Dimension“ eintragen und speichern.
3. Quellen mit Vollindexierung neu indexieren ([Indexierung](help:knowledge.indexing)).

Liefert der Dienst innerhalb einer Anfrage Vektoren unterschiedlicher Länge, ist das ein Fehler des Dienstes; die Meldung enthält dann „changed their dimension within one batch“. Das kannst du im Client nicht beheben.

## Nach dem Modellwechsel findet die Wissenssuche nichts mehr

Der semantische Index ist an Embedding-Modell und Dimension gebunden. Wechselst du eines davon, legt der Client einen neuen, zunächst leeren Bereich im Index an (einen eigenen Namensraum). Vektoren eines anderen Modells wären nicht vergleichbar und werden deshalb nicht gemischt. Ein neues Embedding-Modell gilt ohne Neustart. Der neue Bereich füllt sich über die normale Aufbereitung im Hintergrund: Abschnitte, deren Embedding-Modell sich geändert hat, werden beim nächsten Durchlauf neu verarbeitet. Bis dahin fehlen semantische Treffer.

1. Embedding-Modell und Dimension so lassen, wie du sie künftig nutzen willst.
2. Quellen mit Vollindexierung (Dateien, FTP und ähnliche) über „Jetzt indexieren“ (⟳) in der Zeile der Quelle neu einlesen; das beschleunigt das Auffüllen.
3. Confluence und MediaWiki füllen den neuen Bereich nach und nach über die Live-Suche.

Hinweis: Ob nach dem Zurückwechseln auf ein früheres Modell dessen alter Bereich sofort wieder vollständig nutzbar ist, ist nicht für alle Installationen geprüft. Das Chat-Modell kannst du jederzeit wechseln, ohne dass der Index betroffen ist. Hintergründe: [Wissensindex](help:concepts.knowledge-index).

## Ein lokales Modell lädt nicht

Lokale Modelle werden im Reiter „Modelle“ → „Lokale Modelle“ verwaltet („Installieren“, „Entfernen“, „Aktualisieren“, „Suchen“, Filter „Alle Funktionen“). Steht dort „Die lokalen Modelle ließen sich nicht ermitteln.“ oder „Keine im Client ladbaren Modelle gefunden.“, ist die Liste der verfügbaren Modelle nicht abrufbar oder leer; prüfe die Netzverbindung und „Aktualisieren“.

Typische Meldungen beim Laden:

- „Lokales Chat-Modell … nicht nutzbar: …“. Im Chat erscheint dazu die Überschrift „Der KI-Dienst hat die Anfrage abgelehnt.“; die Ursache steht unter „Details anzeigen“.
- „Kein lokales Chat-Modell gewählt“
- „ONNX Runtime lädt … nicht: …“ oder „ONNX Runtime ließ sich nicht starten: …“
- „Lokales Embedding-Modell … nicht lesbar: …“

Prüfschritte:

1. Ist das Modell wirklich installiert (Knopf „Entfernen“ statt „Installieren“)? Ein abgebrochener Download hinterlässt kein nutzbares Modell; neu installieren.
2. Genug Arbeitsspeicher und Plattenplatz? Lokale Modelle sind oft mehrere GB groß.
3. Lautet die Meldung „ONNX Runtime ließ sich nicht starten“, fehlt meist eine Systembibliothek; unter Windows häufig die Visual-C++-Laufzeit. Hinweis: Diese Ursache ist nicht für alle Installationen geprüft.
4. Protokoll nach dem Modellnamen durchsuchen; dort steht die vollständige Ursache.

Zu beachten:

- Ein lokales Modell rechnet nur eine Antwort gleichzeitig; eine zweite Anfrage wartet.
- Werkzeuge (Wissenssuche, Seiten lesen, Anhänge) bietet der Chat nur über den KI-Dienst an. Mit einem lokalen Chat-Modell antwortet der Assistent ohne Werkzeuge. Mehr dazu in [Probleme im Chat](help:troubleshooting.chat).
- Lokale Modelle laufen auf der CPU und sind deutlich langsamer als der KI-Dienst. Vergleich: [Lokal oder Cloud](help:concepts.local-vs-cloud).

## Für Fortgeschrittene

Schlüssel in der Konfigurationsdatei: `chat.model`, `embedding.model`, `embedding.dimension`, `embedding.baseUrl` und `embedding.apiKeyRef` (wenn Embeddings von einem anderen Dienst kommen). Ist für Embeddings eine eigene Basis-URL gesetzt, braucht sie unter Umständen einen eigenen KeePass-Eintrag („KeePass-Eintrag (optional)“ unter „Embeddings“). Eine Basis-URL darf nicht auf einen Endpunktpfad enden; sonst meldet die Konfigurationsprüfung „darf nicht auf einen Endpunktpfad enden (/chat/completions, /embeddings, /models)“.

## Grenzen

- Die Einordnung in Chat- und Embedding-Modelle richtet sich nur nach dem, was der Dienst meldet; Namen werden nicht geraten.
- Ein Modellwechsel beim Embedding erfordert neues Indexieren.
- Hinweis: Welche lokalen Modelle auf schwächeren Rechnern brauchbar laufen, ist nicht für alle Installationen geprüft.
