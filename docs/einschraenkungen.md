# Bekannte Einschränkungen und Verifikationsstand

Diese Seite nennt ehrlich, was nicht geprüft ist (UNVERIFIED), welche Grenzen bewusst gesetzt sind und was
als Restarbeit offen bleibt. Stand: `main` nach AP25, 2026-10-07.

## Nichts gegen echte Systeme getestet

Kein Arbeitspaket hat gegen die echte Enterprise-API, ein echtes MediaWiki, ein echtes Confluence oder ein
echtes KeePass getestet. Alle Tests des normalen Builds laufen gegen lokale Fakes (siehe
[Testanleitung](tests.md)). AP25 hat mit `./gradlew :integration-tests:liveTest` einen Lauf gegen echte Dienste
vorbereitet (fünf Testklassen mit sechs Testfällen, Parameter in `integration-tests/README.md`); er ist von
niemandem ausgeführt worden.
Seine Array-Probe für `/embeddings` würde die offene Frage an Strang C beantworten.

| Bereich | Was belegt ist | Was UNVERIFIED ist |
|---|---|---|
| Chat (`/chat/completions`) | Das Request- und Antwortformat folgt den realen Beobachtungen aus dem Nachtrag zum Auftrag (developer → HTTP 500, `stop` nur als Array, `usage` immer 0, Streaming nur im Body, kein `Accept: text/event-stream`, `[DONE]`), geprüft gegen einen Fake-Server. | Fehler-Bodies außer HTTP 500, `error` im Stream, Stream ohne `[DONE]`, `content_filter`, JSON-Antwort trotz `stream=true`, Verbindungsabbruch; `SEND_AS_DEVELOPER`. |
| Embeddings (`/embeddings`) | OpenAPI-Dokumentation; Adapter gegen Fake-Server. | **Der gesamte Endpunkt ist am realen Backend nicht getestet.** Array-Input nur hinter `embedding.inputMode=ARRAY_UNVERIFIED`; `encoding_format`, `dimensions`, `user`, `usage` werden nicht gesendet und nicht vorausgesetzt; Dimension von `danielheinz/e5-base-sts-en-de` vermutlich 768. |
| MediaWiki | Action-API-Abfragen aus MainframeMate, Fake-Transport, lokaler HTTP-Server. | Login-Varianten, Single Sign-on, Proxy, echtes Firmen-Wiki. |
| Confluence | REST-Endpunkte aus MainframeMate, `FakeConfluence`, lokaler HTTP-Server. | Echte Data-Center-Instanz, Bearer/PAT, Windows-MY (nur unter Windows prüfbar), PKCS12 ohne echte Gegenstelle. |
| KeePassRPC | Protokoll und Kryptografie aus MainframeMate, `FakeKeePassRpcServer`. | Handschlag mit einem echten KeePassRPC-Plugin, Pairing über den Swing-Dialog; `KeePassRpcRealServerIT` und `LiveKeePassIT` sind vorbereitet, aber von niemandem ausgeführt. |
| Proxy | `ProxyPolicy` mit Modi System/keiner/manuell. | Proxy-Authentifizierung wird nicht unterstützt; Verhalten hinter dem Firmen-Proxy ungeprüft. |
| JDK 8 | CI baut und testet auf Temurin 8 und 21. | Lokale Entwicklung fand überwiegend auf neueren JDKs statt. |

## Bewusste Grenzen

- **Unicode im Chunker**: Satz- und Tokengrenzen nutzen seit `chunker-v2` den festen, versionierten
  Zeichenklassen-Vertrag `UnicodeClasses` (`unicode-classes-v1`, `domain.knowledge`) statt der Unicode-Daten
  des laufenden JDK; `UnicodeClassesTest` friert die vollständige Tabelle über einen Hash ein und läuft in der CI
  auf JDK 8 und 21 mit denselben Erwartungswerten, `KnowledgeBoundaryTest` verbietet `Character.isLetter`,
  `getType`, `BreakIterator` u. ä. außerhalb von `UnicodeClasses`. Der Vertrag ist bewusst klein: fein aufgelöst
  sind ASCII, Latein, Griechisch, Kyrillisch, kombinierende Zeichen, Interpunktion, Symbole, Emoji, CJK-Satzzeichen
  und Voll-/Halbbreitformen; alle anderen Schriften gelten blockweise als Buchstaben (ihre Satzzeichen und Ziffern
  werden nicht erkannt), Ziffern sind nur ASCII und Vollbreite, Großbuchstaben werden nur für Latein, Griechisch,
  Kyrillisch, Deseret, Osage und Adlam erkannt. Bestehende Indizes bleiben lesbar, die Chunks ändern sich aber bei
  Texten mit Zeichen außerhalb von ASCII/Latein nur im Detail; die Index-Revision bleibt gleich, eine Neuindexierung
  ist optional. Restabhängigkeit vom JDK: die NFC-Normalisierung in `KnowledgeDocument` (`java.text.Normalizer`)
  für Zeichen, die erst nach Unicode 6 (JDK 8) hinzukamen.
- **MCP-Endpoint per Umgebung**: Der Agentenprozess erhält Endpoint-ID, URL, Transport und Token über
  `ENTERPRISE_AI_MCP_*`, nicht über ACP `session/new` (dort geht eine leere `mcpServers`-Liste). Eine Übergabe
  über ACP wäre eine Vertragsänderung in `acp-client-api` und `acp-solon-client`.
- **Slice G**: Der Demo-Agent ruft selbst keine MCP-Werkzeuge auf. Den echten Werkzeugaufruf durch einen Agenten
  beweist `SliceGAgentMcpTest` mit dem Testagenten `KnowledgeDemoAgentMain` (Testcode von `integration-tests`,
  eigener Kindprozess, Endpoint aus `ENTERPRISE_AI_MCP_*`). Der Testagent beantwortet nur `search_knowledge` und
  ist kein Sprachmodell; belegt ist die Kette, nicht die Qualität einer Agentenantwort.
- **Slice-Tests**: Die Fake-Embeddings hashen Texte; Rangfolgen des Semantikpfads sind in den Tests nicht
  fachlich aussagekräftig. Die Fake-Server nutzen den JDK-HttpServer ohne TLS; Proxy und mTLS werden nur in
  Unit-Tests von `app-swing` geprüft. Der Start des Testagenten über ein Pathing-Jar ist unter Windows
  ungetestet (CI ist Ubuntu).
- **Secrets**: Der Chat-Adapter nimmt den Token als `String`, der bis zur Garbage Collection lebt; eine
  `char[]`-Variante wäre eine Vertragsänderung. Kein Cache für Secrets, also ein KeePassRPC-Aufruf je Anfrage.
- **RAG**: Token-Budget ist eine Schätzung ohne Modell-Tokenizer; Overlap-Chunks können doppelt zitiert werden;
  Hinweis-Blasen erscheinen hinter der Antwort; ein Backend, das vor dem Stop fertig wird, gewinnt.
- **Agent-Modus**: kein automatischer Neustart eines beendeten Agenten; Tool-Updates werden in der Oberfläche
  nicht angezeigt; Prozessende wird erst beim nächsten Request oder Timeout erkannt.
- **Quellen**: keine Anhänge im Wiki, keine Blogposts in Confluence, Confluence Cloud nicht Ziel; ein Wechsel
  von `siteKey` oder Embedding-Modell bedeutet Neuindexierung.
- **Bibliotheken**: JWBF 3.1.1 und OkHttp 4.9.3 stehen im Versionskatalog, werden aber nicht verwendet.
- **Kein Logging-Framework**: Außerhalb des Demo-Agenten gibt es keine Konsolenausgabe; Fehler landen in
  `java.util.logging` der Anwendung bzw. in der Oberfläche.

## Übergangsklassen

`domain.DomainModule` und `application.ApplicationModule` waren Modul-Anker aus AP1 ohne Verwendung in
Produktions- oder Testcode; der Aufräum-PR von AP26 hat sie nach dem Merge von AP25 entfernt (vorher erneut
geprüft: keine Referenz außer der eigenen Definition, vollständiger Build mit Architekturtests grün). Weitere
tote Übergangsklassen sind nicht bekannt; die Ankerklassen der Module ohne Produktionscode (`architecture-tests`
ohne, `integration-tests` mit `IntegrationTestsModule`) bleiben, weil das Build-Modell der Architekturtests sie
braucht.

## Offene Fragen an den Auftraggeber

- `/embeddings` mit Array-Input real testen; Ergebnis entscheidet über den Standard von `embedding.inputMode`.
- Embedding-Dimension von `danielheinz/e5-base-sts-en-de` bestätigen (vermutlich 768).
- Entscheidungskarte "Index führt" (empfohlen und umgesetzt) bestätigen; bei anderer Wahl Rückbau in AP20.

## Restarbeit (optional)

- MCP-Endpoint über ACP `session/new` statt Umgebungsvariablen (Vertragsänderung ACP).
- `char[]`-Token im Chat-Adapter.
- Proxy-Authentifizierung.
- Anzeige von Tool-Aufrufen des Agenten in der Oberfläche.
- Ungenutzte Bibliotheken (JWBF, OkHttp) aus dem Versionskatalog entfernen.
