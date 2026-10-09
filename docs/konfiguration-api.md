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
# AUTO (PAC-Skript, sonst Systemeinstellungen), SYSTEM, NONE oder MANUAL
network.proxy.mode=AUTO
#network.proxy.pacUrl=http://wpad.intern.example/wpad.dat
#network.proxy.pacDiscovery=WINDOWS_SETTINGS
#network.proxy.host=proxy.intern.example
#network.proxy.port=8080
network.proxy.nonProxyHosts=*.intern.example
# Vertrauensquellen für HTTPS: der JVM-Truststore immer, dazu unter Windows der Windows-Zertifikatspeicher
# und/oder eine Datei mit CA-Zertifikaten (PEM mit einem oder mehreren Zertifikaten, oder DER)
network.tls.useWindowsCertificateStore=true
#network.tls.caCertificatesFile=C:/Zertifikate/firmen-ca.pem
```

| Schlüssel | Bedeutung |
|---|---|
| `network.proxy.mode` | `AUTO` (Standard): wie Browser und PowerShell auf demselben Rechner. Gibt es ein PAC-/WPAD-Proxyskript (Adresse aus `network.proxy.pacUrl` oder aus den Windows-Einstellungen), entscheidet das Skript je Ziel; liefert es keine Entscheidung (keine PAC-Adresse bekannt, Skript nicht ladbar, kein Windows), gelten die Systemeinstellungen wie bei `SYSTEM`, mit Grund im Protokoll. `SYSTEM`: nur die Proxy-Einstellungen des Betriebssystems, unter Windows der fest eingetragene Proxy der Internetoptionen mit Ausnahmen (Java 8 selbst wertet weder PAC-Skripte noch WPAD aus); sind `http.proxyHost`/`https.proxyHost` als JVM-Properties gesetzt, gelten diese. Für `AUTO` und `SYSTEM` setzt die Anwendung beim Start `java.net.useSystemProxies=true`, sofern die Property nicht schon gesetzt ist. `NONE`: immer direkt. `MANUAL`: `network.proxy.host` und `network.proxy.port`. |
| `network.proxy.pacUrl` | Nur `AUTO`: Adresse des PAC-Skripts (`http`, `https` oder `file`), wenn sie nicht aus den Windows-Einstellungen kommen soll; damit funktioniert PAC auch unter Linux und macOS. |
| `network.proxy.pacDiscovery` | Nur `AUTO` ohne `pacUrl`: `WINDOWS_SETTINGS` (Standard) liest die PAC-Adresse per `reg.exe` aus Benutzer- und Richtlinien-Hives, dem Verbindungs-Blob und dem WPAD-Flag, ohne PowerShell; `POWERSHELL` fragt sie mit einem PowerShell-Einzeiler ab, falls `reg.exe` gesperrt ist. |
| `network.proxy.nonProxyHosts` | Hosts ohne Proxy, Muster wie bei `http.nonProxyHosts` (`*.intern.example`). Loopback geht nie über einen Proxy. |
| `network.tls.useWindowsCertificateStore` | `true` (Standard): unter Windows gelten zusätzlich die vertrauenswürdigen Stammzertifikate des Windows-Zertifikatspeichers (`Windows-ROOT`). Damit akzeptiert die Anwendung dieselben Server wie Browser und PowerShell, insbesondere hinter einem Firmen-Proxy mit TLS-Inspektion oder bei einer internen CA. Außerhalb von Windows ohne Wirkung (Hinweis im Protokoll). |
| `network.tls.caCertificatesFile` | Datei mit weiteren CA-Zertifikaten (PEM mit einem oder mehreren `BEGIN CERTIFICATE`-Blöcken oder ein einzelnes DER-Zertifikat), für Linux/macOS oder wenn der Windows-Speicher nicht reicht. Eine fehlende, leere oder unlesbare Datei ist ein Konfigurationsfehler beim Start. |

`ProxyPolicy` installiert die Proxy-Regel als JVM-`ProxySelector` (Chat- und MediaWiki-Adapter über
`HttpURLConnection`) und übergibt sie als expliziten `Proxy` an Embedding- und Confluence-Adapter.
Proxy-Authentifizierung wird nicht unterstützt; die Einstellung gilt prozessweit.

Das PAC-Skript wertet `PacProxyRoutes` mit der Bibliothek `com.aresstack:win-proxy-java` (0.1.0-beta.4, Java 8,
GraalJS; dieselbe wie in MainframeMate, corenth und askai-java8) aus: Adresse ermitteln, Skript laden,
`FindProxyForURL(url, host)` ausführen. Die Anwendung speichert das Ergebnis je Ziel-Host zehn Minuten (die
erste Auswertung dauert rund eine Sekunde, weitere Millisekunden), Fehler eine Minute; ein Fehler steht mit
technischem Grund (`pac-url-not-found`, `pac-download-failed`, `pac-evaluation-failed`) im Protokoll und führt
auf die Systemeinstellungen zurück, nie still. Beim Start steht im Protokoll die Zeile
`Route zum KI-Dienst <host>: …` mit dem Ergebnis für `chat.baseUrl`, zum Beispiel
`PROXY proxy.intern.example:8080 (resolved) laut PAC-Skript aus den Windows-Einstellungen (reg.exe)` oder
`ERROR (pac-url-not-found): … es gelten die Systemeinstellungen; Systemeinstellungen: direkt`.

`TrustPolicy` baut aus den Vertrauensquellen einen gemeinsamen Trust-Manager (ein Serverzertifikat gilt, wenn
eine Quelle es akzeptiert) und installiert ihn als Standard-`SSLSocketFactory` für `HttpsURLConnection`. Das
gilt für alle Adapter ohne eigenen SSL-Kontext: Chat, Embeddings, MediaWiki und Confluence ohne
Client-Zertifikat. Bekannte Grenze: Confluence **mit** Client-Zertifikat (`source.confluence.clientCertificate.*`)
baut einen eigenen SSL-Kontext und prüft Serverzertifikate dort weiterhin nur gegen den JVM-Truststore. Die
Quellen und Hinweise stehen beim Start im Protokoll (`Vertrauensquellen: JVM-Truststore, Windows-Zertifikatspeicher (n Zertifikate)`).

## Fehlersuche: "Der KI-Dienst ist nicht erreichbar."

Scheitert eine Chat-Anfrage, zeigt die Fehlerblase drei Zeilen: die Einordnung (`Der KI-Dienst ist nicht
erreichbar.`, `Anmeldung am KI-Dienst fehlgeschlagen.`, …), `Technische Ursache:` mit der Ausnahmekette ohne
Paketnamen (Tokens werden maskiert) und, wenn die Ursache bekannt ist, `Hinweis:` mit dem nächsten Schritt.
Der vollständige Stacktrace steht in der Protokolldatei `<Anwendungsverzeichnis>/logs/enterprise-ai-client.0.log`
([Einrichtung](einrichtung.md#anwendungsverzeichnis-und-konfigurationsdatei)); ihr Anfang nennt Java-Version,
Betriebssystem, die geladene Konfiguration (ohne Secrets), die Vertrauensquellen, die Proxy-Regel und die
Zeile `Route zum KI-Dienst <host>: …` mit dem Proxy-Ergebnis für `chat.baseUrl`.

Schneller als die Protokolldatei ist der Knopf **„Verbindung zum KI-Dienst prüfen“** im Einstellungen-Dialog
(Reiter „Netzwerk & Agent“, [Einrichtung](einrichtung.md#einstellungen-dialog)): Er geht mit dem aktuellen Entwurf
Proxy-Route, Namensauflösung, API-Key aus KeePass, TLS-Handshake und `GET /models` Schritt für Schritt durch und
zeigt beim ersten roten Schritt dieselbe technische Ursache und denselben Hinweis wie die Tabelle unten, ohne dass
man die Datei speichern oder die Anwendung neu starten muss.

| Technische Ursache (Auszug) | Bedeutung | Abhilfe |
|---|---|---|
| `SSLHandshakeException … PKIX path building failed … unable to find valid certification path` | Java vertraut dem Serverzertifikat nicht. Java bringt einen eigenen Truststore mit und nutzt den des Betriebssystems nicht von selbst; PowerShell, Browser und `curl` auf demselben Rechner funktionieren deshalb trotzdem. Typisch: Firmen-Proxy mit TLS-Inspektion oder interne CA; ein Java 8 vor Update 141 kennt außerdem die Let's-Encrypt-Wurzel (ISRG Root X1) nicht. | Unter Windows `network.tls.useWindowsCertificateStore=true` lassen (Standard) und die App neu starten. Sonst die ausstellende CA als PEM exportieren und `network.tls.caCertificatesFile` setzen. Altes Java aktualisieren (`java -version`). |
| `SSLException … handshake_failure`, `protocol_version`, `no cipher suites in common` | TLS-Version oder Cipher passt nicht; sehr altes Java. | Java aktualisieren. |
| `UnknownHostException` | Der Hostname ist nicht auflösbar, meist weil das Netz einen Proxy verlangt, den Java nicht nutzt. | `network.proxy.mode=AUTO` (Standard) wertet das PAC-Skript des Unternehmens aus; die Zeile `Route zum KI-Dienst` im Protokoll zeigt, ob ein Proxy gefunden wurde. Steht dort `pac-url-not-found`, obwohl der Browser einen Proxy nutzt: `network.proxy.pacDiscovery=POWERSHELL` versuchen oder die PAC-Adresse aus den Internetoptionen ("Skript für automatische Konfiguration") in `network.proxy.pacUrl` eintragen; zuletzt `MANUAL` mit Host und Port. |
| `Unable to tunnel through proxy. Proxy returns "HTTP/1.1 407 …"` | Der Proxy verlangt eine Anmeldung. | Nicht unterstützt; ein Proxy ohne Anmeldung oder eine Ausnahme für den Host ist nötig. |
| `Unable to tunnel through proxy. Proxy returns "HTTP/1.1 403 …"` (oder 5xx) | Der Proxy lehnt den Host ab oder erreicht ihn nicht. | Freigabe für den Host beim Proxy-Betreiber. |
| `SocketTimeoutException: connect timed out` | Keine Antwort vom Server oder Proxy (Firewall, falscher Port). | Erreichbarkeit prüfen; `chat.connectTimeoutMillis` nur erhöhen, wenn der Dienst wirklich langsam antwortet. |
| `ConnectException: Connection refused` | Nichts hört auf dem Port. | `chat.baseUrl` (Host, Port, `https`) prüfen. |
| `token source failed: SecretAccessException` (Zeile 1: Anmeldung fehlgeschlagen) | Der API-Key kam nicht aus KeePass. | KeePass gestartet und entsperrt, Eintrag mit genau dem Titel aus `chat.apiKeyRef`, Pairing bestätigt; der Grund steht im Protokoll ([KeePass](konfiguration-keepass.md)). |
| `HTTP 401` / `HTTP 403` | Der Server lehnt den Key ab. | Passwortfeld des KeePass-Eintrags prüfen (nur der Key, ohne `Bearer`). |
| `stream ended before [DONE]` | Die Antwort wurde abgebrochen (Verbindung, puffernder Proxy). | Erneut versuchen; bleibt es dabei, `chat.readTimeoutMillis` prüfen. |

Zum Vergleich mit einem PowerShell-Test: `Invoke-RestMethod` nutzt den Windows-Zertifikatspeicher und die
Proxy-Einstellungen von Windows einschließlich PAC-Skript; Java 8 tut beides nur mit den Schlüsseln oben
(`network.tls.*` und `network.proxy.mode=AUTO`).

## Was die Anwendung bei fehlendem Secret tut

Ist KeePass deaktiviert oder der Eintrag nicht lesbar, startet die Anwendung trotzdem. Jede Chat-Anfrage
scheitert dann mit einem Authentifizierungsfehler in der Sprechblase, der Grund steht im Log; bei der
Indexierung zählt die Ressource als fehlgeschlagen (`EMBEDDING: AUTHENTICATION …`), der Lauf schließt regulär
ab. Details: [KeePass-Konfiguration](konfiguration-keepass.md).
