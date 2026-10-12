---
id: concepts.limitations
title: Bekannte Einschränkungen
keywords: Einschränkungen, Grenzen, bekannte Probleme, known issues, limitations, nicht getestet, ungetestet, UNVERIFIED, geht nicht, fehlt, Confluence Cloud, Blogposts, Wiki-Anhänge, Agent Neustart, Neustart nötig, IPv6, Token-Budget, Embedding-Dimension, Sprachausgabe ungetestet, lokale Modelle langsam, Proxy, PAC, KeePassRPC
---

# Bekannte Einschränkungen

Dieses Kapitel sammelt ehrlich, was der Enterprise AI Client in dieser Version nicht kann, was bewusst begrenzt ist und was noch nicht gegen echte Systeme geprüft wurde. Es hilft dir, ein überraschendes Verhalten einzuordnen, bevor du lange nach einem Fehler suchst. Wenn ein Problem hier beschrieben ist, steht meist dabei, wie du damit umgehst.

## Noch nicht gegen echte Systeme geprüft

Die automatischen Tests der Anwendung laufen gegen Nachbildungen der Dienste auf dem eigenen Rechner. Gegen echte Installationen ist ein Lauf vorbereitet, aber nicht für alle Teile durchgeführt. Hinweis: Das Verhalten hängt vom Server ab und ist nicht für alle Installationen geprüft. Betroffen sind vor allem:

| Bereich | Was offen ist |
|---|---|
| Chat beim KI-Dienst | ungewöhnliche Fehlerantworten (außer HTTP 500), Fehler mitten im Antwortstrom, Inhaltsfilter, abgebrochene Verbindungen |
| Embeddings beim KI-Dienst | der Endpunkt `/embeddings` als Ganzes; mehrere Texte je Anfrage (`embedding.inputMode=ARRAY_UNVERIFIED`) nur nach eigenem Test einschalten; die tatsächliche Dimension des voreingestellten Modells (768 ist eine Vermutung, die Anwendung übernimmt die gelieferte) |
| Sprachausgabe beim KI-Dienst | der Endpunkt `/audio/speech` mit den TTS-Modellen des Dienstes |
| MediaWiki | Anmeldevarianten, Single Sign-on, Betrieb hinter einem Proxy |
| Confluence | echte Data-Center-Installation, Anmeldung per Personal Access Token, Client-Zertifikate aus dem Windows-Zertifikatsspeicher |
| KeePassRPC | der Handschlag mit einem echten KeePassRPC-Plugin |
| Proxy und TLS | Firmen-Proxy mit PAC/WPAD, Windows-Zertifikatsspeicher, Proxy-Anmeldung; die Modi `WINDOWS_NATIVE_*` sind in der verwendeten Proxy-Bibliothek noch nicht umgesetzt und melden das |

Gebaut und getestet wird mit Java 8 und Java 21.

## Agent-Modus

- **Kein automatischer Neustart**: Fällt der Agentenprozess weg, startet erst dein nächster Auftrag einen neuen, in einer neuen Sitzung. Der Gesprächskontext des Agenten ist dann verloren; das Research-Projekt bleibt.
- **Spät erkanntes Prozessende**: Ein beendeter Agent fällt erst beim nächsten Auftrag oder nach Ablauf der Wartezeit auf (eigener Agent: „Timeout je Anfrage (Sekunden)“, Standard 30; eingebauter Agent: bis zu 30 Minuten je Anfrage).
- **Ein Auftrag zur Zeit**: Während der Agent arbeitet, lassen sich weder ein weiterer Auftrag senden noch die Phase wechseln.
- **Einstellungen gelten ab der nächsten Agentensitzung**: Agent-Kommando, Argumente und Seitenbudget werden beim Start des Agentenprozesses übergeben. Nach einer Änderung „+ Neuer Chat“ oder einen anderen Chat öffnen.
- **Werkzeug-Endpunkt über Umgebungsvariablen**: Ein eigener Agent erfährt seinen Werkzeug-Endpunkt aus den Variablen `ENTERPRISE_AI_MCP_*`, nicht über das Protokoll beim Anlegen der Sitzung. Ein Agent, der nur den Protokollweg kennt, sieht die Werkzeuge nicht.
- **Eigener Agent ohne Recherche-Oberfläche**: Phasen, die Reiter „Konzept“ bis „Dokument“, gelbe Suchvorschläge und der Wolkenhimmel gibt es nur mit dem eingebauten Recherche-Agenten.
- **Feste Grenzen des Recherche-Laufs**: Neben dem einstellbaren Seitenbudget gelten in dieser Version feste Grenzen (30 Werkzeugaufrufe, 8 übernommene Quellen, 3 Fehler in Folge, 10 Minuten je Lauf).
- **Weidezaun braucht Embeddings**: Mit Ausschlüssen und ohne nutzbares Embedding-Modell endet ein Recherche-Lauf mit „Weidezaun nicht prüfbar“.
- **Rangfolge im Recherche-Lauf**: Der Lauf ordnet Treffer nach Volltext-Relevanz; ein eigenes Reranking-Modell ist nicht angebunden.

## Chat und Wissenssuche

- **Token-Budget geschätzt**: Das „Kontextbudget (Tokens)“ ist eine Schätzung ohne den Tokenizer des Modells. Lass Abstand zum Kontextfenster des Modells.
- **Doppelte Zitate**: Überlappende Abschnitte derselben Seite können im Kontext zweimal vorkommen und so doppelt zitiert werden.
- **Reihenfolge der Hinweise**: Hinweis-Blasen (zum Beispiel „Suchpfad ausgefallen“) erscheinen hinter der Antwort, nicht davor.
- **Stopp kommt zu spät**: Ist der KI-Dienst schon fertig, wenn du auf Stopp drückst, steht die Antwort vollständig da.
- **Kein Mindestwert für semantische Treffer**: Ob ein Treffer passt, entscheidet die Bewertung durch das Chat-Modell, nicht ein fester Schwellwert.
- **Kein Reranking-Modell**: Die Kategorie „Reranking“ lässt sich wählen, ein solches Modell wird aber nicht benutzt.
- **Werkzeugrunden begrenzt**: Nach „Max. Werkzeugrunden“ (Standard 8) antwortet das Modell ohne weitere Werkzeuge aus dem, was es hat.

## Wissensquellen und Index

- **Confluence**: Ziel ist Confluence Server bzw. Data Center; Confluence Cloud wird nicht unterstützt. Blogposts werden nicht gelesen.
- **MediaWiki**: Anhänge von Wiki-Seiten werden nicht gelesen.
- **Neuindexieren nötig** nach einem Wechsel des Embedding-Modells oder der Adresse einer Quelle. Siehe [Wissensindex und Suche](help:concepts.knowledge-index).
- **Beim Start wird nichts indexiert**: Neue Seiten kommen durch Suchen, Lesen oder „Jetzt indexieren“ in den Index.
- **Satzgrenzen in anderen Schriften**: Die Zerlegung in Abschnitte erkennt Satzzeichen und Ziffern fein nur für lateinische, griechische und kyrillische Schrift sowie einige weitere Zeichengruppen. In anderen Schriften werden Sätze nur grob getrennt.
- **Vollständiges Indexieren** wird beim Beenden höchstens etwa 10 Sekunden abgewartet und dann abgebrochen; ein Abbruch entfernt nichts aus dem Index.

## Modelle

- **Lokale Modelle rechnen nur auf dem Prozessor** und sind entsprechend langsamer; ein lokales Chat-Modell erzeugt immer nur eine Antwort gleichzeitig.
- **Nur Formate ohne Umwandlung**: ONNX-Exporte mit passenden Tokenizer-Dateien, Piper- oder VITS-Stimmen, Gemma mit Audio-Eingang für die Spracheingabe.
- **Spracheingabe nur lokal**: Der KI-Dienst bietet keine Spracheingabe an.
- **Lokal nur vier Funktionen**: Chat, Embeddings, Sprachausgabe, Spracheingabe. Bildverständnis und Texterkennung gibt es nur beim KI-Dienst.

Mehr: [Lokale Modelle und KI-Dienst](help:concepts.local-vs-cloud).

## Einstellungen

- **Fast alles ohne Neustart**: Gespeicherte Änderungen gelten sofort. Einzige Ausnahme: „Prefer IPv6“ wirkt erst nach einem Neustart, weil Java diesen Wert nur vor der ersten Namensauflösung liest.
- **Nicht alles steht im Dialog**: Manche Feineinstellungen gibt es nur in der Konfigurationsdatei, zum Beispiel `chat.tools.enabled`, die Größe der Abschnitte (`knowledge.chunk.*`), die Gewichte der Suchzusammenführung (`retrieval.keywordWeight`, `retrieval.semanticWeight`, `retrieval.rankConstant`) und die Grenzen der Wissenswerkzeuge (`agent.tools.*`). Der Dialog übernimmt solche Werte beim Speichern unverändert. Siehe [Die Konfigurationsdatei](help:settings.config-file).

## Sicherheit und Protokoll

- **Keine Zwischenspeicherung von Zugangsdaten**: Jede Anfrage fragt KeePass erneut. Ist KeePass gesperrt oder nicht erreichbar, scheitern Anfragen einzeln.
- **API-Key im Speicher**: Während einer Anfrage liegt der API-Key als Zeichenkette im Arbeitsspeicher, bis die Java-Speicherbereinigung ihn entfernt.
- **Keine Konsolenausgabe**: Meldungen stehen in der Oberfläche und im Protokoll unter `<Anwendungsverzeichnis>/logs/`, nicht in einer Konsole. Kann das Protokollverzeichnis nicht angelegt werden, startet die Anwendung trotzdem und sagt das.

Mehr: [Sicherheit](help:concepts.security).

## Was tun, wenn ein Problem hier nicht steht

1. Schau ins Protokoll `<Anwendungsverzeichnis>/logs/enterprise-ai-client.0.log`. Fehlerblasen nennen eine kurze Ursache, das Protokoll die vollständige Ursachenkette.
2. Nutze die Prüfungen im Einstellungen-Dialog, etwa die Verbindungsprüfung im Reiter „KI-Dienst“.
3. Lies das passende Kapitel unter [Fehlerbehebung](help:troubleshooting.overview).

## Verwandte Kapitel

- [Fehlerbehebung im Überblick](help:troubleshooting.overview)
- [Agent-Modus im Überblick](help:agent.overview)
- [Wissensindex und Suche](help:concepts.knowledge-index)
- [Lokale Modelle und KI-Dienst](help:concepts.local-vs-cloud)
