---
id: chat.app-help
title: Den Assistenten zum Programm fragen
keywords: Programmhilfe, Hilfe, eingebaute Hilfe, Produkthilfe, Bedienung, wie geht, wie kann ich, wo finde ich, Einstellung finden, Frage zum Programm, Handbuch, Dokumentation, app_help_search, app_help_read, Anwendungshilfe prüfen, Liest Programmhilfe, offline, help, how to, documentation, manual
---

# Den Assistenten zum Programm fragen

Der Assistent kennt nicht nur deine Wissensquellen, sondern auch dieses Handbuch. Fragst du ihn etwas zur Bedienung des Enterprise AI Client, etwa „Wie ändere ich die Stimme der Sprachausgabe?“, schlägt er in der eingebauten Programmhilfe nach und antwortet auf dieser Grundlage. Die Programmhilfe gehört zur installierten Version, liegt im Programm selbst und braucht weder Netz noch Embedding-Modell noch eine Wissensquelle. Dieses Kapitel erklärt, wie du sie nutzt, was im Hintergrund passiert und wo die Grenzen liegen.

## Wozu die Programmhilfe da ist

Ein Sprachmodell weiß von sich aus nichts über die Menüs, Reiter und Knöpfe dieses Programms. Ohne Nachschlagen würde es raten und dabei womöglich Einstellungen erfinden, die es gar nicht gibt. Die Programmhilfe gibt ihm stattdessen die Kapitel dieses Handbuchs, also genau die Texte, die du auch hier liest.

Typische Fragen, die der Assistent damit beantworten kann:

- „Wie exportiere ich einen Chat als Word-Dokument?“
- „Wo stelle ich den Proxy ein?“
- „Was bedeutet Score in der Quellenliste?“
- „Warum ist der Mikrofonknopf grau?“
- „Wie richte ich eine Confluence-Quelle ein?“

## So fragst du: Schritt für Schritt

1. Schreibe deine Frage zum Programm in den Composer, am besten direkt und konkret: „Wie …“, „Wo finde ich …“, „Was bedeutet …“, „Warum …“.
2. Nenne, wenn du sie kennst, die Begriffe aus der Oberfläche: den Namen eines Reiters („Sprachausgabe“), die Aufschrift eines Knopfes („Chat exportieren …“) oder den Wortlaut einer Meldung. Das trifft die passenden Kapitel am besten.
3. Sende die Frage.
4. Während der Antwort siehst du Wolken wie bei anderen Werkzeugen:
   - „Anwendungshilfe prüfen“ mit dem Suchbegriff,
   - „Liest Programmhilfe“ mit dem gelesenen Kapitel.
5. Die Antwort beruht auf den gelesenen Kapiteln. Folge den beschriebenen Schritten im Programm.

**Tipp:** Frag direkt nach dem Programm, etwa „Wie stelle ich ein, dass Antworten automatisch vorgelesen werden?“. Je näher deine Frage an der Bedienung ist, desto eher greift der Assistent zur Programmhilfe statt zu deinen Wissensquellen.

Fragen zur Programmhilfe kannst du auf Deutsch oder Englisch stellen; das Handbuch selbst ist deutsch.

## Was im Hintergrund passiert

Der Assistent bekommt für Programmfragen zwei Werkzeuge:

- **`app_help_search`** durchsucht die eingebaute Hilfe nach den Wörtern deiner Frage und liefert die passendsten Kapitel und Abschnitte mit Titel, Kennung, einem kurzen Auszug und einem Relevanzwert. Findet die Suche nichts, liefert sie dem Modell das Inhaltsverzeichnis aller Kapitel, damit es mit anderen Begriffen weitersuchen kann.
- **`app_help_read`** liest ein Kapitel ganz oder nur einen Abschnitt daraus. Dabei erfährt das Modell auch die installierte Programmversion und, falls angegeben, seit welcher Version eine Funktion existiert.

Der Standard-System-Prompt weist das Modell an, Fragen zu Bedienung, Funktionen, Einstellungen, Einrichtung oder Fehlern des Clients mit diesen Werkzeugen zu beantworten, zuerst suchen, dann lesen, statt sich auf Annahmen über Menüs zu verlassen.

### Eigenschaften der Programmhilfe

- **Versionsgebunden**: Die Kapitel werden beim Bau des Programms eingebunden. Die Hilfe beschreibt also immer genau die Version, die du gerade benutzt, und ändert sich erst mit einem Update.
- **Offline**: Suche und Lesen laufen vollständig im Programm. Es geht dafür nichts ins Netz; nur die Frage selbst und die gelesenen Hilfetexte gehen wie jede Unterhaltung an das Chat-Modell.
- **Ohne Embedding-Modell**: Die Suche ist eine eigene, einfache Stichwortsuche mit Relevanzbewertung, kein semantischer Index.
- **Ohne Wissensquelle**: Die Programmhilfe ist kein Teil deiner Wissensquellen, erscheint nicht im Reiter „Wissensquellen“ und muss nicht indexiert werden.
- **Nur lesend**: Die Werkzeuge liefern ausschließlich Text. Der Assistent kann damit **keine Einstellungen ändern**, keine Knöpfe drücken und nichts installieren. Die beschriebenen Schritte führst du selbst aus.

Die Antwortlänge der Hilfewerkzeuge folgt denselben Grenzen wie die der Wissenswerkzeuge.

## Abgrenzung zu deinen Wissensquellen

| | Programmhilfe | Wissensquellen |
|---|---|---|
| Inhalt | dieses Handbuch | deine Wikis, Confluence-Bereiche, Ordner |
| Werkzeuge | `app_help_search`, `app_help_read` | `knowledge_search`, `knowledge_read`, `knowledge_attachments` |
| Einrichtung | keine, fest eingebaut | Quelle anlegen und indexieren |
| Suche | Stichwortsuche im Programm | Volltext und Semantik im Wissensindex |
| Quellenliste unter der Antwort | nein | ja, bei automatischer Suche |

Fragst du etwas Fachliches („Wie läuft die Freigabe im Änderungsboard?“), sucht der Assistent in deinen Wissensquellen. Fragst du zur Bedienung, schlägt er in der Programmhilfe nach. Siehe [Wissensquellen](help:knowledge.overview) und [Quellenangaben](help:chat.citations).

## Die Programmhilfe abschalten

Die beiden Werkzeuge stehen unter Einstellungen → „Tools“ in der Liste wie jedes andere Werkzeug, mit der Herkunft „Produkthilfe“.

1. Öffne Einstellungen → „Tools“.
2. Entferne das Häkchen bei `app_help_search` und/oder `app_help_read`.
3. Klicke auf „Speichern“. Die Änderung gilt sofort ab der nächsten Anfrage.

Ohne Häkchen wird ein Werkzeug weder dem Chat noch einem Agenten angeboten; es bleibt aber registriert und lässt sich jederzeit wieder einschalten. Ist die Programmhilfe abgeschaltet, antwortet der Assistent auf Programmfragen nur noch aus allgemeinem Wissen und kann dabei falsch liegen. Mehr in [Werkzeuge einstellen](help:settings.tools).

## Wann die Hilfe zum Zug kommt

Die Programmhilfe steht im Chat und im Agent-Modus zur Verfügung. Im Chat bietet der Client Werkzeuge allerdings nicht bei jeder Nachricht an, sondern nur, wenn er die Nachricht über seine Werkzeug-Schleife schickt.

Wann der Chat die Hilfe anbietet: Die beiden Hilfewerkzeuge sind bei jeder Nachricht dabei, die über die Werkzeuge läuft. Das ist der Fall, sobald mindestens eine Wissensquelle angehakt ist, der Chat Dateianhänge oder Seiten aus dem Lesebereich enthält oder in der Konfigurationsdatei `chat.tools.enabled=true` steht. Ist nichts davon gegeben, läuft der Chat ganz ohne Werkzeuge und das Modell kann nicht nachschlagen. Erscheint bei deiner Frage keine Wolke „Anwendungshilfe prüfen“, hake eine Wissensquelle an oder setze `chat.tools.enabled=true` und frag erneut.

Außerdem gilt: Ob und wann das Modell ein Werkzeug aufruft, entscheidet es selbst. Leistungsfähige Modelle tun das zuverlässig, schwächere vergessen es manchmal.

## Typische Probleme

### Der Assistent erfindet Menüs oder Einstellungen

1. Erschien eine Wolke „Anwendungshilfe prüfen“? Wenn nein, hat er nicht nachgeschlagen. Bitte ausdrücklich: „Schau in der Programmhilfe nach: Wie …?“
2. Sind `app_help_search` und `app_help_read` unter Einstellungen → „Tools“ aktiv?
3. Hast du im Feld „System-Prompt“ (Einstellungen → „KI-Dienst“) einen eigenen Prompt eingetragen? Ein leeres Feld bedeutet den Standard-Prompt mit der Anweisung zur Programmhilfe; ein eigener Prompt ersetzt ihn. Siehe [KI-Dienst einstellen](help:settings.ai-service).
4. Unterstützt das gewählte Chat-Modell Werkzeugaufrufe? Probiere testweise ein anderes Modell.

### Der Assistent findet nichts zu meiner Frage

Formuliere mit Begriffen aus der Oberfläche, etwa dem Namen eines Reiters, einem Knopf oder dem Wortlaut einer Meldung. Die Suche ist stichwortbasiert; Umschreibungen trifft sie schlechter als die genauen Begriffe.

### Die Antwort passt nicht zu meinem Programm

Die Hilfe gehört zur installierten Version; beim Lesen eines Kapitels erfährt das Modell diese Version und, falls angegeben, seit wann eine Funktion existiert. Beschreibt die Antwort etwas, das du nicht siehst, frag nach: „Seit welcher Version gibt es das?“ Möglicherweise hat das Modell auch ein unpassendes Kapitel gelesen; nenne dann den genauen Begriff aus der Oberfläche. Hinweis: Fehlt die Programmhilfe beim Start ganz, etwa weil sie nicht geladen werden konnte, bietet der Client die beiden Werkzeuge nicht an; das Protokoll im Einstellungsordner nennt dann den Grund.

Weitere Fälle in [Probleme im Chat](help:troubleshooting.chat).

## Grenzen

- Die Hilfe kennt nur, was in diesem Handbuch steht. Für Fragen, die es nicht beantwortet, gibt es keine Garantie.
- Sie liefert nur Text; der Assistent ändert keine Einstellungen.
- Sie kennt nur die installierte Version, keine Neuerungen späterer Versionen.
- Hilfekapitel erscheinen nicht in der Quellenliste unter der Antwort.
- Ob das Modell nachschlägt, liegt beim Modell.
