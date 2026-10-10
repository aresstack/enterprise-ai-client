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

Proxy-Auflösung über `com.aresstack:win-proxy-java` 0.2.0, TLS-Vertrauen über `com.aresstack:win-trust-java`
0.1.0. Felder und Modi entsprechen dem ProxyPanel von AskAI; die Modusnamen sind die Enum-Werte der Bibliothek.

```properties
network.proxy.mode=PAC_URL_POWERSHELL
#network.proxy.host=proxy.intern.example
#network.proxy.port=8080
#network.proxy.pacUrl=http://wpad.intern.example/wpad.dat
#network.proxy.pacDiscoveryScript=
#network.proxy.testUrl=
#network.proxy.resolveTimeoutMillis=45000
#network.proxy.nonProxyHosts=*.intern.example
network.proxy.auth.mode=NONE
#network.proxy.auth.credentialRef=keepass:Firmen-Proxy
#network.http.userAgent=
network.http.preferIPv6=false
network.tls.useJvmDefault=true
network.tls.useWindowsRoot=true
network.tls.useWindowsCaStores=true
#network.tls.caCertificatesFile=C:/Zertifikate/firmen-ca.pem
```

| Schlüssel | Bedeutung |
|---|---|
| `network.proxy.mode` | `DISABLED`, `MANUAL_PROXY`, `WINDOWS_STATIC_PROXY`, `PAC_URL_MANUAL`, `PAC_URL_POWERSHELL` (Standard), `PAC_URL_WSCRIPT`, `PAC_URL_WINDOWS_SETTINGS`, `WINDOWS_NATIVE_PROXY_SETTINGS`, `WINDOWS_NATIVE_ROUTE_RESOLVER`. Alte Namen werden gelesen und beim Speichern ersetzt: `NONE` → `DISABLED`, `MANUAL` → `MANUAL_PROXY`, `SYSTEM` → `WINDOWS_STATIC_PROXY`, `AUTO` → `PAC_URL_POWERSHELL` (mit `pacUrl`: `PAC_URL_MANUAL`). |
| `network.proxy.host`, `port` | Pflicht bei `MANUAL_PROXY`. |
| `network.proxy.pacUrl` | Pflicht bei `PAC_URL_MANUAL` (`http`, `https` oder `file`). |
| `network.proxy.pacDiscoveryScript` | `PAC_URL_POWERSHELL`/`PAC_URL_WSCRIPT`: Skript, das die PAC-URL ausgibt; leer = Standardskript der Bibliothek (liest `AutoConfigURL`). |
| `network.proxy.testUrl` | Ziel für „Proxy auflösen“ und „HTTPS-Verbindung testen“; leer = `<chat.baseUrl>/models`. |
| `network.proxy.resolveTimeoutMillis` | Harte Obergrenze je Auflösung (Standard 45000). |
| `network.proxy.nonProxyHosts` | Hosts ohne Proxy (`*`-Muster); Loopback geht nie über einen Proxy. |
| `network.proxy.auth.mode`, `credentialRef` | `NONE` oder `BASIC`; bei `BASIC` Benutzer und Passwort aus dem genannten KeePass-Eintrag, nie aus dieser Datei. |
| `network.http.userAgent` | User-Agent aller Aufrufe; leer = `EnterpriseAiClient/<Version>`. |
| `network.http.preferIPv6` | IPv6 bevorzugen (wirkt nach Neustart). |
| `network.tls.useJvmDefault`, `useWindowsRoot`, `useWindowsCaStores` | Vertrauensquellen: JVM-`cacerts`, Windows-ROOT, Windows Root+Intermediate (Windows-Quellen nur unter Windows). Der alte Schalter `useWindowsCertificateStore` gilt für beide Windows-Quellen, solange die neuen fehlen. |
| `network.tls.caCertificatesFile` | Weitere CA-Zertifikate (PEM oder DER). |

`HttpRoutes` (Port `HttpRoutePort` aus `http-api`) löst die Route je Ziel (`scheme://host:port`) auf, außerhalb
des EDT, mit hartem Timeout und Cache (Erfolg 10 min, Fehler 1 min). Jede `HttpURLConnection` von Chat,
Embeddings, MediaWiki und Confluence bekommt die Route und die `SSLSocketFactory` aus win-trust-java einzeln;
es gibt keinen globalen `ProxySelector` und keine globale `SSLSocketFactory`. `NOT_IMPLEMENTED` und `ERROR` werden
als nicht verfügbare Route gemeldet; die Anwendung verbindet dann nicht und fällt nie still auf `DIRECT` zurück.
Der Start wartet nicht auf die Auflösung. Für `BASIC` installiert die Anwendung einen `Authenticator`, der nur
Proxy-Anfragen beantwortet.

## Fehlersuche: "Der KI-Dienst ist nicht erreichbar."

Scheitert eine Chat-Anfrage, zeigt die Fehlerblase drei Zeilen: die Einordnung (`Der KI-Dienst ist nicht
erreichbar.`, `Anmeldung am KI-Dienst fehlgeschlagen.`, …), `Technische Ursache:` mit der Ausnahmekette ohne
Paketnamen (Tokens werden maskiert) und, wenn die Ursache bekannt ist, `Hinweis:` mit dem nächsten Schritt.
Der vollständige Stacktrace steht in der Protokolldatei `<Anwendungsverzeichnis>/logs/enterprise-ai-client.0.log`
([Einrichtung](einrichtung.md#anwendungsverzeichnis-und-konfigurationsdatei)); ihr Anfang nennt Java-Version,
Betriebssystem, die geladene Konfiguration (ohne Secrets), die Vertrauensquellen, die Proxy-Regel und je Ziel
die Zeile `Proxy-Route …` mit dem Ergebnis; „Proxy auflösen“ im Reiter Netzwerk zeigt jeden Schritt.

Schneller als die Protokolldatei ist der Knopf **„Verbindung testen“** (Reiter KI-Dienst) im Einstellungen-Dialog
(Reiter „Netzwerk & Agent“, [Einrichtung](einrichtung.md#einstellungen-dialog)): Er geht mit dem aktuellen Entwurf
Proxy-Route, Namensauflösung, API-Key aus KeePass, TLS-Handshake und `GET /models` Schritt für Schritt durch und
zeigt beim ersten roten Schritt dieselbe technische Ursache und denselben Hinweis wie die Tabelle unten, ohne dass
man die Datei speichern oder die Anwendung neu starten muss.

| Technische Ursache (Auszug) | Bedeutung | Abhilfe |
|---|---|---|
| `SSLHandshakeException … PKIX path building failed … unable to find valid certification path` | Java vertraut dem Serverzertifikat nicht. Java bringt einen eigenen Truststore mit und nutzt den des Betriebssystems nicht von selbst; PowerShell, Browser und `curl` auf demselben Rechner funktionieren deshalb trotzdem. Typisch: Firmen-Proxy mit TLS-Inspektion oder interne CA; ein Java 8 vor Update 141 kennt außerdem die Let's-Encrypt-Wurzel (ISRG Root X1) nicht. | Unter Windows `network.tls.useWindowsRoot` und `network.tls.useWindowsCaStores` auf `true` lassen (Standard) und die App neu starten. Sonst die ausstellende CA als PEM exportieren und `network.tls.caCertificatesFile` setzen. Altes Java aktualisieren (`java -version`). |
| `SSLException … handshake_failure`, `protocol_version`, `no cipher suites in common` | TLS-Version oder Cipher passt nicht; sehr altes Java. | Java aktualisieren. |
| `UnknownHostException` | Der Hostname ist nicht auflösbar, meist weil das Netz einen Proxy verlangt, den Java nicht nutzt. | der passende Modus unter `network.proxy.mode` (meist `PAC_URL_POWERSHELL`) wertet das PAC-Skript des Unternehmens aus; „Proxy auflösen“ im Reiter Netzwerk zeigt, ob ein Proxy gefunden wurde. Findet das Skript keine PAC-Adresse, obwohl der Browser einen Proxy nutzt: `PAC_URL_WINDOWS_SETTINGS` oder `PAC_URL_WSCRIPT` versuchen oder die PAC-Adresse aus den Internetoptionen mit `PAC_URL_MANUAL` eintragen; zuletzt `MANUAL_PROXY` mit Host und Port. |
| `Unable to tunnel through proxy. Proxy returns "HTTP/1.1 407 …"` | Der Proxy verlangt eine Anmeldung. | `network.proxy.auth.mode=BASIC` mit einem KeePass-Eintrag (`network.proxy.auth.credentialRef`); integrierte Windows-Anmeldung wird nicht unterstützt. |
| `Unable to tunnel through proxy. Proxy returns "HTTP/1.1 403 …"` (oder 5xx) | Der Proxy lehnt den Host ab oder erreicht ihn nicht. | Freigabe für den Host beim Proxy-Betreiber. |
| `SocketTimeoutException: connect timed out` | Keine Antwort vom Server oder Proxy (Firewall, falscher Port). | Erreichbarkeit prüfen; `chat.connectTimeoutMillis` nur erhöhen, wenn der Dienst wirklich langsam antwortet. |
| `ConnectException: Connection refused` | Nichts hört auf dem Port. | `chat.baseUrl` (Host, Port, `https`) prüfen. |
| `token source failed: SecretAccessException` (Zeile 1: Anmeldung fehlgeschlagen) | Der API-Key kam nicht aus KeePass. | KeePass gestartet und entsperrt, Eintrag mit genau dem Titel aus `chat.apiKeyRef`, Pairing bestätigt; der Grund steht im Protokoll ([KeePass](konfiguration-keepass.md)). |
| `HTTP 401` / `HTTP 403` | Der Server lehnt den Key ab. | Passwortfeld des KeePass-Eintrags prüfen (nur der Key, ohne `Bearer`). |
| `stream ended before [DONE]` | Die Antwort wurde abgebrochen (Verbindung, puffernder Proxy). | Erneut versuchen; bleibt es dabei, `chat.readTimeoutMillis` prüfen. |

Zum Vergleich mit einem PowerShell-Test: `Invoke-RestMethod` nutzt den Windows-Zertifikatspeicher und die
Proxy-Einstellungen von Windows einschließlich PAC-Skript; Java 8 tut beides nur mit den Schlüsseln oben
(`network.tls.*` und `network.proxy.mode`).

## Was die Anwendung bei fehlendem Secret tut

Ist KeePass deaktiviert oder der Eintrag nicht lesbar, startet die Anwendung trotzdem. Jede Chat-Anfrage
scheitert dann mit einem Authentifizierungsfehler in der Sprechblase, der Grund steht im Log; bei der
Indexierung zählt die Ressource als fehlgeschlagen (`EMBEDDING: AUTHENTICATION …`), der Lauf schließt regulär
ab. Details: [KeePass-Konfiguration](konfiguration-keepass.md).
