---
id: chat.quotes
title: Textstelle zitieren und nachfragen
keywords: zitieren, Zitat, Textstelle, Passage, markieren, nachfragen, Rückfrage, Anschlussfrage, Folgefrage, Vorschläge, gelbe Vorschläge, Zitat-Chip, Anführungszeichen, auf Antwort beziehen, quote, reply to, follow-up, selection
since: 0.40.0
---

# Textstelle zitieren und nachfragen

Oft willst du nicht zur ganzen Antwort nachfragen, sondern zu einem bestimmten Satz oder Begriff darin. Mit „Zitieren“ markierst du eine Stelle in einer Antwort des Assistenten und nimmst sie in deine nächste Nachricht mit. Der Assistent weiß dann genau, worauf sich deine Frage bezieht, und schlägt dir passende Anschlussfragen vor. Dieses Kapitel erklärt die Bedienung, was im Hintergrund passiert und wo die Grenzen liegen.

## Eine Stelle zitieren: Schritt für Schritt

1. Markiere in einer Antwortblase des Assistenten mit der Maus den Text, um den es dir geht, zum Beispiel einen Fachbegriff oder einen Satz.
2. Über der Markierung erscheint eine kleine dunkle Pille mit Anführungszeichen und der Aufschrift **„Zitieren“** (Tooltip „Markierte Passage für die nächste Nachricht zitieren“). Ist über der Markierung kein Platz, erscheint sie darunter.
3. Klicke auf „Zitieren“. Die Markierung verschwindet, und im Composer erscheint ein **Zitat-Chip**: ein Anführungszeichen-Symbol, der Anfang der Passage in einer Zeile und ein Kreuz zum Entfernen. Fährst du mit der Maus über den Chip, zeigt der Tooltip die Passage ausführlicher („Zitat: „…““).
4. Der Cursor steht im Eingabefeld. Schreibe deine Frage, zum Beispiel „Was ist damit genau gemeint?“, und sende sie.

Über dem Composer steht, solange Zitate vorgemerkt sind, etwa „1 Zitat vorgemerkt“ oder zusammen mit Dateien „1 Anhang, 2 Zitate vorgemerkt“.

Gut zu wissen:

- Zitieren geht nur aus **Antworten des Assistenten**, nicht aus deinen eigenen Nachrichten, Fehlerblasen oder Hinweiszeilen.
- Rollst du den Verlauf, bevor du klickst, verschwindet die Pille; markiere dann erneut.
- Du kannst mehrere Stellen nacheinander zitieren, auch aus verschiedenen Antworten. Dieselbe Stelle derselben Antwort wird nur einmal vorgemerkt.
- Mit dem Kreuz am Chip nimmst du ein Zitat wieder heraus.
- Der Text im Eingabefeld bleibt beim Zitieren unverändert.

## Vorschläge für Anschlussfragen

Kurz nachdem du eine Stelle zitiert hast, erscheinen über dem Eingabefeld meist drei bis fünf **gelbe Vorschläge**, also kurze Fragen, die du zu dieser Passage stellen könntest. Sie richten sich nach dem Inhalt:

- zu einem einzelnen Begriff etwa nach Bedeutung, Abkürzung oder Gemeintem,
- zu einem fachlichen Satz nach Gründen, Ablauf, Zusammenhängen oder Folgen,
- zu einer Behauptung nach Beleg, Quelle, Geltung oder Ausnahmen.

Ein Klick auf einen Vorschlag (Tooltip „In das Eingabefeld übernehmen“) **sendet nichts**. Er schreibt die Frage ins Eingabefeld: Ist es leer, steht danach genau diese Frage darin; steht dort schon Text, wird die Frage in einer neuen Zeile angehängt. Du kannst sie vor dem Senden noch ändern.

Die Vorschläge gelten immer nur für das **zuletzt** zitierte Zitat. Entfernst du dieses Zitat oder sendest du die Nachricht, verschwinden sie.

Im Hintergrund stellt der Client dafür eine kleine Nebenanfrage an das Chat-Modell (mit dem im Composer gewählten Modell und Denkaufwand): Er schickt die Passage und ihr Umfeld aus der Antwort und bittet um kurze Fragen aus deiner Sicht. Diese Nebenanfrage nutzt keine Werkzeuge, erscheint nicht im Verlauf und gehört nicht zum Gesprächsgedächtnis des Chats. Fällt sie aus oder liefert das Modell nichts Brauchbares, gibt es eben keine Vorschläge; das Zitat selbst funktioniert trotzdem.

## Was beim Senden passiert

- Im Verlauf steht deine Nachricht wie getippt. Darunter erscheinen die Zitat-Chips, damit später nachvollziehbar ist, worauf du dich bezogen hast.
- Das Modell bekommt **für genau diese eine Nachricht** zusätzlich einen Hinweis: „Der Nutzer bezieht sich auf folgende Passage aus einer früheren Assistentenantwort: „…“. Die aktuelle Nutzernachricht bezieht sich insbesondere auf diese Passage.“ Bei mehreren Zitaten werden sie nummeriert aufgezählt.
- Der Hinweis wird nicht in den Gesprächsverlauf geschrieben; spätere Fragen sehen ihn nicht mehr, nur deine Frage und die Antwort darauf.
- Das funktioniert auf jedem Weg: im normalen Chat, mit Wissenssuche, mit Dateianhängen und im Agent-Modus (dort geht der Hinweis vor deiner Nachricht an den Agenten).

Ein Zitat ist weder ein Dateianhang noch eine Wissensquelle. Es löst keine Suche aus und lädt nichts hoch; es lenkt nur die Aufmerksamkeit des Modells.

## Zitate im gespeicherten Chat und im Export

Zitate werden mit deiner Nachricht gespeichert: der zitierte Text, der Zeitpunkt der zitierten Antwort und, wenn bekannt, die Position im Antworttext. Öffnest du den Chat später wieder, stehen die Zitat-Chips wieder unter deiner Nachricht.

Im Export erscheinen sie ebenfalls: im Markdown als Zeile „Zitat: „…““, im Word-Dokument im Abschnitt „Zitate“ unter der Nachricht, im JSON mit allen gespeicherten Feldern. Siehe [Chat exportieren](help:chat.export).

## Beispiel

Der Assistent hat geantwortet: „Die Freigabe erfolgt im Vier-Augen-Prinzip über das Änderungsboard.“ Du markierst „Änderungsboard“ und klickst auf „Zitieren“. Als Vorschläge erscheinen etwa „Wer sitzt im Änderungsboard?“ und „Wie oft tagt das Änderungsboard?“. Du klickst auf den ersten Vorschlag, ergänzt „und wer leitet es?“ und sendest. Das Modell weiß, dass sich deine Frage auf genau diesen Begriff in genau dieser Antwort bezieht.

## Typische Fragen

### Die Pille „Zitieren“ erscheint nicht

1. Markierst du Text in einer **Antwort** des Assistenten? In deinen eigenen Blasen gibt es kein Zitieren.
2. Ist wirklich Text markiert (nicht nur ein Klick)? Ziehe mit gedrückter Maustaste über die Stelle.
3. Hast du nach dem Markieren gerollt? Dann markiere erneut.

### Es erscheinen keine Vorschläge

Die Vorschläge kommen aus einer eigenen Anfrage an das Modell. Ist der KI-Dienst gerade langsam oder nicht erreichbar oder liefert das Modell kein verwertbares Ergebnis, bleiben sie aus. Das Zitat kannst du trotzdem benutzen; schreib deine Frage einfach selbst. Bei dauerhaften Verbindungsproblemen hilft [Probleme im Chat](help:troubleshooting.chat).

### Der Chip zeigt nur den Anfang meines Zitats

Der Chip ist bewusst kurz. Das Modell bekommt die vollständig markierte Passage, nicht nur den sichtbaren Anfang.

## Grenzen

- Zitiert werden kann nur Text, der in der Antwortblase markierbar ist.
- Die Vorschläge kosten eine zusätzliche, kleine Modellanfrage.
- Die Vorschläge sind Anregungen des Modells; sie können unpassend sein.
- Wie stark das Modell den Zitat-Hinweis beachtet, hängt vom Modell ab.

Mehr zum Chat allgemein: [Der Chat im Überblick](help:chat.overview).
