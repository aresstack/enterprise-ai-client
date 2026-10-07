# API-Konfiguration: Chat und Embeddings

Produktiv unterstützt der Client genau eine Art von Backend: eine interne, GPT-/OpenAI-kompatible
Enterprise-API mit den Endpunkten `POST <baseUrl>/chat/completions` und `POST <baseUrl>/embeddings`. Es gibt
keine Provider-Auswahl und keine Provider-Enums im Kern; Base-URL, Modellnamen und API-Key sind reine
Konfiguration. Beide Adapter (`chat-openai`, `embedding-openai`) sind unabhängig voneinander und teilen keinen
Transport.

Alle Schlüssel stehen in `enterprise-ai-client.properties` (siehe [Einrichtung](einrichtung.md)); die Vorlage
im Repository kommentiert jeden Wert.

## Chat (`chat.*`)

```properties
# Pflicht
chat.baseUrl=https://ki.intern.example/v1
chat.model=openai/gpt-oss-120b
chat.apiKeyRef=keepass:Enterprise AI API
# Optional
chat.systemPrompt=Du bist ein hilfreicher Assistent. Antworte auf Deutsch.
chat.connectTimeoutMillis=10000
chat.readTimeoutMillis=120000
chat.developerRolePolicy=REJECT
#chat.temperature=0.2
#chat.topP=0.9
#chat.topK=40
#chat.maxTokens=2048
#chat.presencePenalty=0.0
#chat.frequencyPenalty=0.0
#chat.stop=###,ENDE
#chat.user=enterprise-ai-client
```

| Schlüssel | Bedeutung |
|---|---|
| `chat.baseUrl` | Basis-URL; der Adapter hängt `chat/completions` an. |
| `chat.model` | Modellname, wie ihn der Server erwartet (getestet wurde `openai/gpt-oss-120b`). |
| `chat.apiKeyRef` | Titel des KeePass-Eintrags, dessen Passwortfeld den API-Key enthält. Wird je Anfrage über `SecretProvider.withSecret` aufgelöst und als `Authorization: Bearer …` gesendet; kein Feld hält den Key. |
| `chat.systemPrompt` | System-Prompt der Konversation; der `ChatService` hält ihn getrennt von der Historie und stellt ihn jeder Anfrage voran. |
| `chat.developerRolePolicy` | `REJECT` (Standard): Anfragen mit `DEVELOPER`-Nachrichten werden vor dem Senden abgelehnt. `SEND_AS_SYSTEM`: als `system` senden. `SEND_AS_DEVELOPER`: unverändert senden (UNVERIFIED, der getestete Server antwortet mit HTTP 500). |
| `chat.temperature`, `topP`, `topK`, `maxTokens`, `presencePenalty`, `frequencyPenalty`, `user` | Standardparameter je Anfrage; werden nur gesendet, wenn gesetzt. |
| `chat.stop` | Kommagetrennte Stop-Sequenzen; werden immer als JSON-Array gesendet. |

### Beobachtetes Serververhalten

Der Adapter kapselt das real beobachtete Verhalten der Enterprise-API (Nachtrag zum Auftrag, Abschnitte 6 bis
16). Jede Zeile ist in `OpenAiCompatibleChatAdapterTest` gegen einen lokalen Fake-Server festgehalten.

| Beobachtung | Konsequenz im Adapter |
|---|---|
| Rollen `system`, `user`, `assistant` funktionieren; `developer` liefert HTTP 500 | `DeveloperRolePolicy.REJECT` als Standard; nichts wird stillschweigend umgewandelt |
| `Content-Type: application/json; charset=utf-8` nötig für Umlaute | immer so gesendet; Request und Response als UTF-8-Bytes |
| `n` wird akzeptiert, aber ignoriert | wird nie gesendet; gelesen wird nur `choices[0]` |
| `stop` als String wird abgelehnt, als Array akzeptiert; die Sequenz bleibt im Text | immer als Array; der Adapter schneidet nichts ab |
| `usage.*` ist immer 0 | wird gelesen, ein reiner Null-Block gilt als "nicht gemeldet" |
| `message.content` bzw. `delta.content` darf `null` sein | leere Antwort bzw. kein Delta, kein Fehler |
| Streaming nur über `"stream": true` im Body; `?stream=true` wirkungslos | nur im Body |
| `Accept: text/event-stream` wird abgelehnt | wird nie gesendet; der Server liefert trotzdem `data:`-Zeilen bis `data: [DONE]` |
| Chunks sind stark gebatcht, Chunks ohne Text sind normal | Deltas werden in der Reihenfolge der `data:`-Zeilen gemeldet |

UNVERIFIED (defensiv behandelt, nicht real getestet): Fehler-Bodies außer HTTP 500, `error`-Objekte innerhalb
eines Streams, ein Stream ohne `[DONE]`, `finish_reason = content_filter`, eine JSON-Antwort trotz
`stream=true`, Verhalten des Servers bei Verbindungsabbruch.

## Embeddings (`embedding.*`)

```properties
# Standard: chat.baseUrl (der Adapter hängt /embeddings an)
#embedding.baseUrl=https://ki.intern.example/v1
# Pflicht
embedding.model=danielheinz/e5-base-sts-en-de
embedding.dimension=768
# Standard: chat.apiKeyRef
#embedding.apiKeyRef=keepass:Enterprise AI API
embedding.inputMode=SINGLE_STRING
embedding.maxBatchSize=16
embedding.connectTimeoutMillis=10000
embedding.readTimeoutMillis=60000
```

| Schlüssel | Bedeutung |
|---|---|
| `embedding.baseUrl`, `embedding.apiKeyRef` | erben vom Chat, wenn nicht gesetzt (eine Enterprise-API). |
| `embedding.model` | Modellname (bekannt: `danielheinz/e5-base-sts-en-de`). |
| `embedding.dimension` | Erwartete Vektorlänge; der Adapter prüft jede Antwort dagegen. Für e5-base-sts-en-de vermutlich 768 (UNVERIFIED). |
| `embedding.inputMode` | `SINGLE_STRING` (Standard): ein Request je Text mit `"input": "<text>"`. `ARRAY_UNVERIFIED`: ein Request je Teil-Batch mit `"input": ["a", "b", …]`; erst aktivieren, wenn ein praktischer Test bestätigt, dass das Backend Arrays annimmt und je Eingabe einen Eintrag liefert. |
| `embedding.maxBatchSize` | Größe der Teil-Batches im Array-Modus. |

Modell-ID und Dimension bilden zusammen die `EmbeddingModelIdentity`. Ihr SHA-256-Fingerprint ist der Namespace
im Index; Vektoren verschiedener Embedding-Welten werden nie verglichen. Ein Modellwechsel bedeutet deshalb
eine Neuindexierung (alter Namespace bleibt liegen, bis `rebuild` oder ein neues Indexverzeichnis).

### Verifikationsstand

`/embeddings` ist in der OpenAPI dokumentiert, aber am realen Backend noch nicht getestet. Der Adapter setzt
deshalb nur das Minimum voraus: `model` plus ein einzelner String als `input`, Antwort mit `data[].embedding`
als Float-Array; fehlt `index` überall, gilt die Listenreihenfolge. Nicht vorausgesetzt und nicht gesendet:
Array-Input (nur per `ARRAY_UNVERIFIED`), `encoding_format`/base64, `dimensions`, `user`, `usage`. Bei jedem
Fehler wirft der Adapter `EmbeddingException`; es gibt keine Teilergebnisse und keine Null-Vektoren als Ersatz.

## Netzwerk (`network.*`)

```properties
network.proxy.mode=SYSTEM          # SYSTEM (JVM-Proxy-Properties), NONE, MANUAL
#network.proxy.host=proxy.intern.example
#network.proxy.port=8080
network.proxy.nonProxyHosts=*.intern.example
```

`ProxyPolicy` installiert die Regel als JVM-`ProxySelector` (Chat- und MediaWiki-Adapter über
`HttpURLConnection`) und übergibt sie als expliziten `Proxy` an Embedding- und Confluence-Adapter. Loopback
geht nie über einen Proxy. Proxy-Authentifizierung wird nicht unterstützt; die Einstellung gilt prozessweit.

## Was die Anwendung bei fehlendem Secret tut

Ist KeePass deaktiviert oder der Eintrag nicht lesbar, startet die Anwendung trotzdem. Jede Chat-Anfrage
scheitert dann mit einem Authentifizierungsfehler in der Sprechblase, der Grund steht im Log; bei der
Indexierung zählt die Ressource als fehlgeschlagen (`EMBEDDING: AUTHENTICATION …`), der Lauf schließt regulär
ab. Details: [KeePass-Konfiguration](konfiguration-keepass.md).
