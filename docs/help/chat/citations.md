---
id: chat.citations
title: Quellenangaben in Antworten
keywords: Quellen, Quellenangabe, Quellenliste, Zitat, Beleg, Fußnote, Nummer, [1], Quellen (n), Score, Volltext, Semantik, Rang, Ort, Stand, Version, seltsame Zeichen, komische Klammern, 【, 】, †, Dolch, Trainingsformat, gpt-oss, citation, sources, references, footnote
---

# Quellenangaben in Antworten

Wenn der Assistent auf deine Wissensquellen zurückgreift, zeigt der Chat, worauf sich eine Antwort stützt. Dieses Kapitel erklärt die Nummern wie „[1]“ im Antworttext, die aufklappbare Quellenliste unter der Antwort, was die Angaben darin bedeuten und warum in manchen Antworten seltsame Zeichen wie „【…】“ auftauchen. Es richtet sich an alle, die Antworten prüfen oder belegen müssen.

## Woher die Quellen kommen

Sind im Drawer unter „Wissensquellen“ Quellen angehakt, sucht der Client vor der Antwort in diesen Quellen (zu erkennen an der gelben Recherchekarte „Recherche in den Wissensquellen“). Die passendsten Abschnitte gibt er dem Modell als nummerierten Kontextblock mit: Abschnitt [1], [2] und so weiter, jeweils mit Titel, Überschrift, Ort und Stand. Dazu bekommt das Modell die Anweisung, die Auszüge vorrangig zu verwenden, die genutzten Quellen mit ihrer Nummer zu nennen (zum Beispiel „[1]“) und zu sagen, wenn die Auszüge die Frage nicht beantworten.

Die Nummern im Antworttext schreibt also das Modell selbst. Die Nummern in der Quellenliste unter der Antwort vergibt der Client. Beide gehören zusammen: „[2]“ im Text meint den Eintrag „[2]“ in der Liste.

Wie die Suche im Einzelnen arbeitet (Volltext, Semantik, Bewertung der Treffer), steht in [Wie der Wissensindex funktioniert](help:concepts.knowledge-index).

## Die Quellenliste unter einer Antwort

Hat eine Antwort Quellen, steht direkt unter ihrer Blase eine schmale Platte mit petrolfarbenem Streifen und dem Schalter **„Quellen (n)“**, wobei n die Zahl der Quellen ist (Tooltip „Quellen der Antwort ein- oder ausblenden“).

1. Klicke auf „Quellen (n)“. Die Liste klappt auf.
2. Jede Quelle hat zwei Zeilen:
   - fett: „[1] Titel – Überschrift“, also die Nummer, der Titel der Seite oder des Dokuments und, falls vorhanden, die Überschriftenkette des Abschnitts (zum Beispiel „Installation > Linux“),
   - darunter klein: „Ort: … · Stand: … · Score 0.123 · Volltext #3 · Semantik #1“.
3. Ein weiterer Klick auf „Quellen (n)“ klappt die Liste wieder zu.

Die zweite Zeile ist markierbar: Du kannst den Ort (die Adresse der Seite) mit der Maus auswählen und kopieren, um ihn im Browser zu öffnen oder weiterzugeben.

### Was die Angaben bedeuten

- **Ort**: die aufrufbare Adresse der Quelle, zum Beispiel `https://wiki.example.org/...`; nennt die Quelle keine Adresse, steht dort ihre interne Kennung. Zugangsdaten stehen nie darin.
- **Stand**: Version und/oder Änderungszeitpunkt der Seite zum Zeitpunkt der Indexierung, etwa „Version 42, 2026-10-01 08:00“. Fehlt die Angabe, ist der Stand unbekannt. Ist die Seite inzwischen geändert worden, kann die Antwort auf einem älteren Stand beruhen.
- **Score**: ein zusammengeführter Relevanzwert. Er ist **nur innerhalb einer Antwort** vergleichbar; zwischen verschiedenen Antworten sagt er nichts.
- **Volltext #n**: Rang des Abschnitts in der Stichwortsuche. Fehlt die Angabe, hat die Stichwortsuche ihn nicht gefunden.
- **Semantik #n**: Rang in der Bedeutungssuche über Embeddings. Fehlt die Angabe, hat die semantische Suche ihn nicht gefunden.

Ein Abschnitt mit beiden Rängen wurde von beiden Suchwegen gefunden; das ist meist ein gutes Zeichen.

### Quellen bleiben beim Chat

Die Quellen werden mit der Antwort gespeichert. Öffnest du den Chat später wieder, steht die Quellenliste wieder unter der Antwort. Außerdem erscheinen alle verwendeten Quellen eines Chats im Composer unter „Ressourcen n“ in der Gruppe „VERWENDETE QUELLEN“; von dort kannst du eine Quelle als Verweis in deine nächste Frage übernehmen („Was steht in dieser Quelle noch zu …?“). Im Export stehen die Quellen je Antwort, im Word-Dokument zusätzlich gesammelt unter „Ressourcen des Chats“. Siehe [Chat exportieren](help:chat.export).

## Was die Quellenliste nicht zeigt

Die Quellenliste zeigt die Abschnitte, die die automatische Wissenssuche des Clients vor der Antwort in den Kontext gegeben hat. Darüber hinaus kann das Modell mit Werkzeugen selbst suchen und Seiten lesen; das siehst du während der Antwort an den Wolken „Durchsucht Wissensquellen“ und „Liest Seite“. Was das Modell auf diesem Weg gelesen hat, steht nicht als nummerierte Quelle in der Liste. Für eine genaue Nachverfolgung enthält der JSON-Export je Antwort alle Werkzeugaufrufe.

Hat eine Antwort keine Quellenliste, hat die automatische Suche nichts beigetragen, etwa weil keine Wissensquelle angehakt war, die Frage Small Talk war oder keine passenden Abschnitte gefunden wurden. Im letzten Fall steht nach der Antwort eine Hinweiszeile wie „Keine passenden Abschnitte in der Wissensbasis gefunden. Die Antwort entstand ohne Kontext.“

## Seltsame Zeichen wie 【…】 in Antworten

Manchmal enthält eine Antwort Zeichenfolgen wie „【1】“, „【3†L5-L9】“ oder „【source】“ mit dicken eckigen Klammern und manchmal einem Dolchzeichen „†“. Das ist kein Fehler des Clients und keine kaputte Darstellung.

**Erklärung:** Einige Sprachmodelle wurden mit einem eigenen Zitierformat trainiert, das aus ihrer ursprünglichen Umgebung stammt (etwa einem eingebauten Browser-Werkzeug, das Fundstellen mit Zeilenangaben markiert). Solche Modelle greifen dieses Format manchmal aus Gewohnheit auf, auch wenn der Client sie ausdrücklich bittet, Quellen als „[1]“ zu nennen. Die Zeichen gehören also zum Modell, nicht zum Programm. Bei Modellen der gpt-oss-Familie ist dieses Verhalten bekannt.

Was das für dich heißt:

- Die Zahl in der Klammer meint in der Regel trotzdem die Quelle mit derselben Nummer in der Quellenliste. „【2†…】“ entspricht dann „[2]“.
- Angaben hinter dem Dolch (zum Beispiel Zeilenbereiche wie „L5-L9“) beziehen sich auf das Trainingsformat und haben im Enterprise AI Client keine Bedeutung. Verlass dich darauf nicht.
- Der Client wandelt diese Zeichen nicht um und macht keine Links daraus.

Was du tun kannst:

1. Ignoriere die Zeichen und nimm die Nummer als Verweis auf die Quellenliste.
2. Bitte das Modell in der Frage: „Nenne Quellen nur in der Form [1].“
3. Für dauerhafte Abhilfe ergänze in den Einstellungen → „KI-Dienst“ im Feld „System-Prompt“ einen entsprechenden Satz. Ein leeres Feld bedeutet den Standard-Prompt. Siehe [KI-Dienst einstellen](help:settings.ai-service).
4. Probiere ein anderes Chat-Modell über den Modellknopf im Composer.

## Quellen prüfen: so gehst du vor

1. Klappe „Quellen (n)“ auf.
2. Suche im Antworttext die Nummer, die eine Aussage belegt.
3. Kopiere den Ort der passenden Quelle und öffne ihn, oder öffne die Seite über den Lesebereich (siehe [Der Lesebereich](help:knowledge.reader)).
4. Prüfe den Stand: Ist die Seite inzwischen neuer, indexiere die Quelle neu (siehe [Indexierung](help:knowledge.indexing)).

## Grenzen

- Das Modell setzt die Nummern selbst. Es kann eine Nummer falsch zuordnen, eine Quelle nicht nennen, die es benutzt hat, oder eine Aussage ohne Beleg machen. Der Client prüft das nicht.
- Die Nummern im Antworttext sind keine Links.
- Die Liste zeigt Abschnitte, nicht ganze Dokumente; mehrere Einträge können zur selben Seite gehören.
- Der Score ist kein Prozentwert und kein Wahrheitsmaß.
- Ältere Chats, die vor der Speicherung von Quellen entstanden sind, haben keine Quellenliste.

Weitere Hilfe bei fehlenden oder unpassenden Quellen findest du in [Probleme mit Wissensquellen](help:troubleshooting.knowledge).
