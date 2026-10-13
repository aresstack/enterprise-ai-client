---
id: agent.tools
title: Werkzeuge von Chat und Agent
keywords: Werkzeuge, Tools, Tool-Calling, Funktionsaufrufe, function calling, knowledge_search, knowledge_read, read_attachment, search_attachment, app_help_search, app_help_read, Hilfe-Werkzeuge, Reiter Tools, Werkzeug abschalten, tools.disabled, Pflichtwerkzeug, Werkzeugrunden, chat.tools.maxRounds, MCP, Model Context Protocol, Endpoint, 127.0.0.1, Welche Tools hast du, Recherche-Werkzeuge, research_status, main_model_complete
---

# Werkzeuge von Chat und Agent

Werkzeuge sind Funktionen der Anwendung, die das KI-Modell oder der Agent selbst aufrufen kann, etwa „suche in den Wissensquellen“ oder „lies diese Seite“. Dieses Kapitel zählt auf, welche Werkzeuge es gibt, wer sie bekommt (Chat, Agent, Recherche-Agent), wie du einzelne Werkzeuge im Reiter „Tools“ abschaltest und wie der Agent technisch an die Werkzeuge kommt. Es ist für alle gedacht, die verstehen wollen, warum der Chat sucht oder nicht sucht, oder die ein Werkzeug gezielt sperren möchten.

## Eine Liste für alle

Die Anwendung führt genau **eine** zentrale Liste aller Werkzeuge. Chat, Agent und der Reiter „Tools“ lesen nur aus dieser Liste. Jedes Werkzeug hat dort:

- einen **Namen** (zum Beispiel `knowledge_search`) und eine Beschreibung, die auch das Modell sieht,
- eine **Herkunft** (wer es beiträgt, etwa die Wissenswerkzeuge, die Dateianhänge oder der Recherche-Agent),
- die **Kanäle**, in denen es angeboten wird: „Chat“, „Agent“ (jeder Agent, auch ein eigenes Agent-Kommando) oder „Recherche-Agent“ (nur der eingebaute Agent),
- einen Zustand: aktiv oder abgeschaltet.

Weil es nur eine Liste gibt, ist `knowledge_search` im Chat exakt dasselbe Werkzeug wie beim Agenten: dieselbe Beschreibung, dieselben Parameter, dasselbe Verhalten.

## Die Wissenswerkzeuge: knowledge_search und knowledge_read

Diese beiden Werkzeuge stehen in den Kanälen „Chat“ und „Agent“. Sie sind bewusst klein gehalten.

### knowledge_search

Durchsucht die Wissensquellen (zum Beispiel ein Confluence unter `confluence.example.org` oder ein Wiki unter `wiki.example.org`) und den lokalen Wissensindex.

- Parameter: `query` (Pflicht, Stichworte oder ein Satz), `limit` (optional, wie viele Treffer).
- Ohne `limit` kommen 5 Treffer; nach oben begrenzt die Einstellung „Max. Retrieval-Kandidaten“ (Reiter „Index“) die Zahl.
- Durchsucht werden **nur die angehakten Quellen** im Drawer-Reiter „Wissensquellen“. Ist keine angehakt, antwortet das Werkzeug, dass keine Quelle aktiv ist, und fordert dazu auf, eine anzuhaken.
- Es fragt die Quellen live ab und sucht gleichzeitig im Index (Volltext und semantisch). Live-Treffer, die noch nicht gelesen wurden, sind als solche gekennzeichnet. Die Treffer werden im Hintergrund vorgeladen und indexiert, ohne dass die Suche darauf wartet.
- Jeder Treffer nennt Titel, Kennung (`id:`), Adresse, Quelle und einen Textausschnitt. Ist eine Quelle nicht erreichbar oder der Index nicht durchsuchbar, steht ein Hinweis in der Antwort, die übrigen Treffer kommen trotzdem.

### knowledge_read

Liest ein einzelnes Dokument vollständig.

- Parameter: `id` (Pflicht). Erlaubt sind die Kennung eines Suchtreffers, die Kennung einer Passage oder das Lesezeichen einer Seite, die du dem Chat hinzugefügt hast.
- Zuerst antwortet der Index. Nur wenn die Seite unbekannt ist oder sich geändert hat, wird die Quelle gefragt; danach wird die Seite indexiert.
- Die Antwort enthält Titel, Kennung, Adresse, Quelle, Version und die Passagen der Seite mit ihren Kennungen. Bei einer Passagen-Kennung kommen diese Passage und ihre direkten Nachbarn.
- Fehlerfälle werden verständlich gemeldet: Seite nicht gefunden, Zugriff verweigert, Quelle gerade nicht erreichbar.

- Seit 0.49.0 nimmt es auch die Kennung eines Confluence-Anhangs (aus `knowledge_attachments`). Dann lädt es die Datei, macht daraus Text (Dokumente über die Textextraktion, Bilder über die Bildbeschreibung) und indexiert sie wie eine Seite. Verlangt Confluence einen Einmalcode, fragt der Client im Chat danach ([Einmalcode](help:knowledge.otp#einmalcode-im-chat)).

### knowledge_attachments

Listet die Anhänge einer Seite, ohne sie zu laden (seit 0.49.0).

- Parameter: `id` (Pflicht), die Kennung einer Seite wie bei `knowledge_read`.
- Die Antwort nennt je Anhang Name, Typ, Größe, Version und Kennung. Mit der Kennung liest `knowledge_read` den Anhang.
- Anhänge kennen heute nur Confluence-Quellen. Mehr dazu: [Anhänge von Wissensquellen](help:knowledge.attachments).

### knowledge_resolve_url

Ordnet eine Webadresse, die du nennst (etwa einen Link auf eine Confluence-Seite), den angebundenen Quellen zu, ohne zu suchen (seit 0.62.0).

- Parameter: `url` (Pflicht, vollständig mit `http://` oder `https://`), `source` (optional, nur diese Quelle fragen).
- Gefragt werden nur Quellen, deren Adressmuster passt; jede prüft selbst, ob die Adresse zu ihr gehört (bei Confluence Schema, Host, Port und Kontextpfad der eingestellten Adresse).
- Die Antwort nennt alle Kandidaten mit Quelle, Kennung, Bookmark und gegebenenfalls Abschnitt; das Werkzeug wählt nie selbst. Gelesen wird danach mit `knowledge_read`.
- Ohne Kandidat gehört die Adresse zu keiner Quelle. Eine Adresse ohne `http://`/`https://` wird nicht ergänzt.
- Der Recherche-Agent nutzt es, wenn er eine genannte Adresse öffnen soll, die er nicht aus einer Suche kennt.

Ein Werkzeug zum Neuindexieren ganzer Quellen gibt es für das Modell nicht. Vollständig indexieren ist eine Funktion der Oberfläche („Jetzt indexieren“ an der Quelle), siehe [Indexierung](help:knowledge.indexing).

### Grenzen der Antworten

Die Antworten der Wissenswerkzeuge sind begrenzt, damit sie das Kontextfenster des Modells nicht sprengen. Die Grenzen stehen in der Konfigurationsdatei und gelten für Chat und Agent gleichermaßen:

| Schlüssel | Bedeutung | Standard |
|---|---|---|
| `agent.tools.maxResponseChars` | Zeichen je Werkzeugantwort; Längeres wird gekürzt und markiert | 20000 |
| `agent.tools.snippetChars` | Zeichen je Textausschnitt eines Treffers | 600 |
| `agent.tools.defaultMaxResults` | Treffer ohne Angabe von `limit` | 5 |

## Die Anhang-Werkzeuge: read_attachment und search_attachment

Hängst du im normalen Chat Dateien an, steht ihr Inhalt nicht im Verlauf. Das Modell erfährt nur Kennung, Dateiname und Größe jeder Datei und liest sie bei Bedarf selbst:

- `read_attachment` liest den Text einer angehängten Datei (PDF, Word, Excel, PowerPoint, HTML, E-Mail, Text). Lange Dateien kommen abschnittsweise; der nächste Abschnitt über den Parameter `offset`.
- `search_attachment` sucht einen Begriff in den Anhängen und liefert die passenden Absätze.

Diese Werkzeuge gibt es nur im Kanal „Chat“. Im Agent-Modus geht der Text jeder angehängten Datei stattdessen direkt mit der Nachricht an den Agenten (höchstens 30.000 Zeichen je Datei). Mehr zu Anhängen: [Dateien anhängen](help:chat.attachments).

## Die Hilfe-Werkzeuge: app_help_search und app_help_read

Die Programmhilfe, die du gerade liest, wird mit der Anwendung ausgeliefert. Zwei Werkzeuge machen sie für das Modell nutzbar:

- `app_help_search` durchsucht die mitgelieferte Programmhilfe,
- `app_help_read` liest ein Kapitel der mitgelieferten Programmhilfe.

Beide arbeiten nur lesend, ohne Netz und ohne Embedding-Modell. So kann der Chat Fragen zum Programm selbst beantworten, etwa „Wo stelle ich die Stimme ein?“. Ob und in welchem Kanal sie angeboten werden, zeigt der Reiter „Tools“. Wie du den Chat zum Programm befragst, steht in [Den Chat zum Programm fragen](help:chat.app-help).

## Die Werkzeuge des Recherche-Agenten

Der eingebaute Recherche-Agent bekommt zusätzlich zu `knowledge_search` und `knowledge_read` eigene Steuerwerkzeuge (Kanal „Recherche-Agent“). Du rufst sie nie selbst auf, siehst sie aber als gelbe Wolken im Verlauf und als Einträge im Reiter „Tools“. Welche angeboten werden, hängt von der **Phase** ab, die du im Composer gewählt hast; bei jedem Phasenwechsel bekommt der Agent sofort den passenden Satz.

| Gruppe | Beispiele | Wann |
|---|---|---|
| Steuerung und Zustand | `research_status`, `research_turn_begin`, `chat_history`, `research_scope_fence` | immer |
| Hauptmodell | `main_model_complete`: die Anwendung stellt die Modellanfrage für den Agenten | immer |
| Konzept lesen | `concept_read` | immer |
| Konzept bearbeiten | `concept_add`, `concept_add_cards`, `concept_rename`, `concept_move_leaf`, `exclude_topic`, `resolve_concept_conflict`, `offer_searches`, `scope_probe` | nur in der Phase „Konzept“ |
| Quellen übernehmen | `source_accept`, `source_park`, `source_passages`, `source_images` | nur in der Phase „Quellen“ |
| Lauf melden | `research_progress`, `research_run_finished` | nur während die Phase „Quellen“ läuft |
| Weidezaun | `search_scope_begin`, `search_scope_evaluate`, `search_scope_end` | immer |
| Projekt durchsuchen | `project_search`, `project_source_add`, `research_sources` | immer |
| Gliederung | `research_outline`, `outline_propose` (Vorschlag nur in der Phase „Gliederung“) | je nach Phase |
| Dokument | `research_document`, `document_draft`, `document_section_save`, `document_save` (Speichern nur in der Phase „Dokument“) | je nach Phase |

Sichtbarkeit ist keine Erlaubnis: Jedes schreibende Werkzeug prüft beim Aufruf noch einmal, ob die aktuelle Phase es zulässt, und lehnt sonst ab.

### Pflichtwerkzeuge

Alle Werkzeuge, deren Name mit `research_` beginnt, und `main_model_complete` sind **Pflichtwerkzeuge**. Ohne sie startet der Recherche-Agent nicht oder kann keinen Auftrag steuern. Im Reiter „Tools“ lassen sie sich nicht abwählen; der Tooltip sagt „Pflichtwerkzeug: ohne es startet bzw. arbeitet der Agent nicht; es lässt sich nicht abschalten“. Ein Eintrag in `tools.disabled` wird für sie ignoriert.

## Werkzeuge im Reiter „Tools“ abschalten

1. Öffne Einstellungen → „Tools“.
2. Im Abschnitt „Tools“ stehen alle registrierten Werkzeuge mit Häkchen, Name, Beschreibung, Herkunft („Herkunft: …“) und Verfügbarkeit (zum Beispiel „Chat + Agent“).
3. Entferne das Häkchen bei dem Werkzeug, das nicht mehr angeboten werden soll.
4. Speichern.

Der Hinweis im Reiter beschreibt die Wirkung genau: „Ohne Häkchen wird ein Werkzeug weder dem Chat noch einem Agenten angeboten; es bleibt registriert. Gilt nach dem Speichern sofort.“ Im Einzelnen:

- Ein laufender Agent bekommt den geänderten Werkzeugsatz sofort mitgeteilt.
- Der Chat fragt die Liste bei jeder Modellanfrage neu. Ein abgeschaltetes Werkzeug fehlt ab dem nächsten Modellaufruf; ein schon angefragter Aufruf wird abgelehnt.
- In der Konfigurationsdatei stehen abgeschaltete Werkzeuge kommagetrennt unter `tools.disabled`, zum Beispiel `tools.disabled=knowledge_search`. Nicht genannte Werkzeuge sind aktiv.

Schaltest du `knowledge_search` ab, sucht das Modell nicht mehr selbst. Bittest du den eingebauten Agenten dann ausdrücklich um eine Suche oder klickst einen gelben Suchvorschlag an, sagt er dir, dass er nicht suchen kann und dass du die Werkzeuge im Reiter „Tools“ prüfen sollst, statt stumm ohne Quellen zu antworten.

### Werkzeugrunden im Chat

Im selben Reiter steht im Abschnitt „Werkzeugrunden im Chat“ das Feld „Max. Werkzeugrunden“ (`chat.tools.maxRounds`, 1 bis 32, Standard 8). Es begrenzt, wie oft das Modell im normalen Chat für eine Antwort Werkzeuge aufrufen darf. Verlangt es danach noch ein Werkzeug, antwortet es in einer letzten Runde ohne Werkzeuge aus den bisherigen Ergebnissen. Für den Agenten gilt diese Grenze nicht; dort begrenzt das Seitenbudget den Recherche-Lauf (siehe [Agent-Modus im Überblick](help:agent.overview)).

## Wann der normale Chat Werkzeuge benutzt

Der Chat schickt dem Modell Werkzeuge mit, wenn die Unterhaltung Dateianhänge oder hinzugefügte Seiten hat oder eine Wissensquelle angehakt ist. Mit `chat.tools.enabled=true` in der Konfigurationsdatei geschieht das bei jeder Frage. Unabhängig davon sucht der Chat bei angehakten Quellen vor der Antwort selbst in den Wissensquellen; die Werkzeuge erlauben dem Modell, gezielt nachzusuchen oder eine Seite ganz zu lesen.

### „Welche Tools hast du?“

Fragst du den Assistenten nach seinen eigenen Werkzeugen oder Fähigkeiten, beantwortet die Anwendung das ohne Modellaufruf aus den tatsächlich angebotenen Werkzeugen dieser Anfrage und dem gewählten Modell. So nennt die Antwort nur, was wirklich verfügbar ist.

## Wie der Agent an die Werkzeuge kommt

Die Anwendung stellt ihre Werkzeuge über das Model Context Protocol (MCP) bereit. Dafür läuft in der Anwendung ein kleiner Werkzeug-Server:

- Er ist **nur unter 127.0.0.1** erreichbar, also nur auf deinem eigenen Rechner, nie aus dem Netz.
- Jeder Agentenprozess bekommt einen eigenen Endpunkt mit einem frischen, nicht erratbaren Zugangsschlüssel. Ohne diesen Schlüssel antwortet der Server nicht.
- Der Agent erfährt Adresse und Schlüssel über Umgebungsvariablen (`ENTERPRISE_AI_MCP_*`) beim Start, nie als Text in einer Nachricht.
- Endet die Agentensitzung („+ Neuer Chat“, Chat wechseln, Programmende), wird der Endpunkt abgemeldet und der Schlüssel ungültig.

Der normale Chat braucht diesen Server nicht; er ruft dieselben Werkzeuge direkt in der Anwendung auf. Hintergründe stehen in [Sicherheit](help:concepts.security).

## Typische Probleme

### Der Chat sucht nicht in den Wissensquellen

- Ist im Drawer-Reiter „Wissensquellen“ mindestens eine Quelle angehakt?
- Ist `knowledge_search` im Reiter „Tools“ aktiv?
- Weitere Prüfschritte: [Probleme mit dem Chat](help:troubleshooting.chat).

### Im Reiter „Tools“ fehlen Werkzeuge

Der Hinweis „Keine Werkzeuge registriert. Die Übersicht füllt sich, sobald die Anwendung läuft.“ erscheint, solange die Anwendung noch nicht vollständig gestartet ist. Werkzeuge des Recherche-Agenten, die nur in bestimmten Phasen gelten, erscheinen in der Liste mit dem Satz der aktuellen Phase.

### Ein Häkchen lässt sich nicht entfernen

Es ist ein Pflichtwerkzeug des Recherche-Agenten (siehe oben).

## Verwandte Kapitel

- [Agent-Modus im Überblick](help:agent.overview)
- [Recherche mit dem Agenten](help:agent.research)
- [Einstellungen: Tools](help:settings.tools)
- [Wissensindex und Suche](help:concepts.knowledge-index)
