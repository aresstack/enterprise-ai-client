# Bekannte Einschränkungen und Verifikationsstand

Diese Seite nennt ehrlich, was nicht geprüft ist (UNVERIFIED), welche Grenzen bewusst gesetzt sind und was
als Restarbeit offen bleibt. Stand: `main` nach AP26 und Vorbereitung der Live-Verifikation, 2026-10-07.

## Nichts gegen echte Systeme getestet

Kein Arbeitspaket hat gegen die echte Enterprise-API, ein echtes MediaWiki, ein echtes Confluence oder ein
echtes KeePass getestet. Alle Tests des normalen Builds laufen gegen lokale Fakes (siehe
[Testanleitung](tests.md)). Der Lauf gegen echte Dienste ist als [Live-Verifikation](live-verifikation.md) in
sieben Stufen vorbereitet (`./gradlew :integration-tests:liveTest -Dlive.stage=N`, sechs Testklassen mit elf
Testfällen): 1 Chat, 2 Embeddings mit einem Text, 3 Embeddings mit Array-Eingabe, 4 tatsächliche
Embedding-Dimension, 5 MediaWiki, 6 KeePassRPC, 7 Confluence über KeePass. Der Auftraggeber führt die Stufen
nacheinander aus; das Ergebnisprotokoll dort hält fest, welche Stufe gelaufen ist. Bis dahin gilt jede Zeile
der folgenden Tabelle unverändert; nach jedem echten Ergebnis wird nur die tatsächlich verifizierte
Fähigkeit aus der Spalte UNVERIFIED gestrichen.

| Bereich | Was belegt ist | Was UNVERIFIED ist |
|---|---|---|
| Chat (`/chat/completions`) | Das Request- und Antwortformat folgt den realen Beobachtungen aus dem Nachtrag zum Auftrag (developer → HTTP 500, `stop` nur als Array, `usage` immer 0, Streaming nur im Body, kein `Accept: text/event-stream`, `[DONE]`), geprüft gegen einen Fake-Server. | Fehler-Bodies außer HTTP 500, `error` im Stream, Stream ohne `[DONE]`, `content_filter`, JSON-Antwort trotz `stream=true`, Verbindungsabbruch; `SEND_AS_DEVELOPER`. |
| Embeddings (`/embeddings`) | OpenAPI-Dokumentation; Adapter gegen Fake-Server. | **Der gesamte Endpunkt ist am realen Backend nicht getestet.** Array-Input nur hinter `embedding.inputMode=ARRAY_UNVERIFIED`; `encoding_format`, `dimensions`, `user`, `usage` werden nicht gesendet und nicht vorausgesetzt; Dimension von `danielheinz/e5-base-sts-en-de` vermutlich 768. |
| MediaWiki | Action-API-Abfragen aus MainframeMate, Fake-Transport, lokaler HTTP-Server. | Login-Varianten, Single Sign-on, Proxy, echtes Firmen-Wiki. |
| Confluence | REST-Endpunkte aus MainframeMate, `FakeConfluence`, lokaler HTTP-Server. | Echte Data-Center-Instanz, Bearer/PAT, Windows-MY (nur unter Windows prüfbar), PKCS12 ohne echte Gegenstelle. |
| KeePassRPC | Protokoll und Kryptografie aus MainframeMate, `FakeKeePassRpcServer`. | Handschlag mit einem echten KeePassRPC-Plugin, Pairing über den Swing-Dialog; `KeePassRpcRealServerIT` und `LiveKeePassIT` (Stufe 6, pairt über den Dialog der Anwendung) sind vorbereitet, aber von niemandem ausgeführt. |
| Proxy | `ProxyPolicy` mit Modi System/keiner/manuell. | Proxy-Authentifizierung wird nicht unterstützt; Verhalten hinter dem Firmen-Proxy ungeprüft. |
| JDK 8 | CI baut und testet auf Temurin 8 und 21. | Lokale Entwicklung fand überwiegend auf neueren JDKs statt. |

## Bewusste Grenzen

- **Unicode im Chunker**: Satz- und Tokengrenzen nutzen die Zeichenklassen des laufenden JDK. Chunks sollen
  unter JDK 8 und neueren JDKs identisch sein; eine eigene Zeichentabelle gibt es nicht. Die Frage an Angelo,
  ob eine feste Tabelle gewünscht ist, ist offen.
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

- `/embeddings` mit Array-Input real testen (Stufe 3 der Live-Verifikation); Ergebnis entscheidet über den
  Standard von `embedding.inputMode`.
- Embedding-Dimension von `danielheinz/e5-base-sts-en-de` erfassen (Stufe 4; 768 ist nur eine Vermutung und
  wird laut Auftraggeber nicht vor dem echten Lauf festgeschrieben).
- Feste Unicode-Zeichentabelle im Chunker gewünscht?
- Entscheidungskarte "Index führt" (empfohlen und umgesetzt) bestätigen; bei anderer Wahl Rückbau in AP20.

## Restarbeit (optional)

- MCP-Endpoint über ACP `session/new` statt Umgebungsvariablen (Vertragsänderung ACP).
- `char[]`-Token im Chat-Adapter.
- Proxy-Authentifizierung.
- Anzeige von Tool-Aufrufen des Agenten in der Oberfläche.
- Ungenutzte Bibliotheken (JWBF, OkHttp) aus dem Versionskatalog entfernen.
