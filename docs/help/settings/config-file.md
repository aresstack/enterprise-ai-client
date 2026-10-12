---
id: settings.config-file
title: Die Konfigurationsdatei
keywords: Konfigurationsdatei, Konfiguration, Properties, enterprise-ai-client.properties, Einstellungsdatei, Anwendungsverzeichnis, APPDATA, Benutzerverzeichnis, Beispielkonfiguration, Vorlage, Schlüssel, Secret-Referenz, KeePass-Verweis, Standardwerte, Defaults, Pflichtfelder, enterpriseai.home, enterpriseai.config, config file, settings file
---

# Die Konfigurationsdatei

Alle Einstellungen des Clients stehen in einer einzigen Textdatei, der Konfigurationsdatei `enterprise-ai-client.properties`. Der Dialog Einstellungen liest und schreibt sie für dich; du kannst sie aber auch mit einem Texteditor ansehen und bearbeiten. Dieses Kapitel erklärt, wo die Datei liegt, wie sie aufgebaut ist, welche Schlüssel wichtig sind, warum keine Secrets darin stehen und warum viele Standardwerte gar nicht in der Datei auftauchen.

## Wo liegt die Datei?

Die Datei liegt im Anwendungsverzeichnis:

- **Windows:** `%APPDATA%\.enterprise-ai-client\enterprise-ai-client.properties`
- **andere Systeme:** `~/.enterprise-ai-client/enterprise-ai-client.properties` im Benutzerverzeichnis

Zwei Startparameter verlegen sie:

- `-Denterpriseai.home=<Verzeichnis>` verlegt das ganze Anwendungsverzeichnis.
- `-Denterpriseai.config=<Datei>` nennt nur eine andere Konfigurationsdatei.

Den tatsächlich benutzten Pfad zeigt der Reiter [Technische Details](help:settings.technical-details) in der Zeile „Konfiguration: …“. Dort öffnet „Einstellungsordner öffnen“ auch den Ordner.

Im Anwendungsverzeichnis liegen außerdem:

| Eintrag | Inhalt |
|---|---|
| `index` | der Wissensindex, solange kein anderes Indexverzeichnis eingestellt ist |
| `logs` | das Protokoll, `enterprise-ai-client.0.log` bis `.2.log` |
| `keepassrpc-pairing.key` | der Pairing-Schlüssel für KeePassRPC |
| `model-catalog.properties` | zwischengespeicherte Modellkataloge |
| `application-state.json` | Programmzustand, etwa ob die Einrichtung abgeschlossen ist |
| `local-models` | lokal installierte Modelle, etwa Stimmen |

## Wie entsteht die Datei?

Beim ersten Start gibt es noch keine Datei. Der Einrichtungsassistent führt dich durch die nötigen Angaben und schreibt beim Klick auf „Fertig“ eine Datei, die auf einer kommentierten Vorlage beruht. Die Vorlage erklärt jeden Abschnitt in Kommentarzeilen; Beispielblöcke für Wissensquellen sind auskommentiert, damit der Client nicht versucht, Beispielhosts zu erreichen. Siehe [Einrichtung](help:getting-started.setup).

Die Datei ist UTF-8-kodiert. Zeilen mit `#` am Anfang sind Kommentare.

## Beispielkonfiguration

Ein gekürztes Beispiel mit den wichtigsten Schlüsseln (Werte nur zur Veranschaulichung):

```properties
chat.baseUrl=https://ki.example.org/v1
chat.model=openai/gpt-oss-120b
chat.apiKeyRef=keepass:Enterprise AI API
chat.connectTimeoutMillis=10000
chat.readTimeoutMillis=120000

embedding.model=danielheinz/e5-base-sts-en-de
embedding.dimension=768

retrieval.maxResults=10
context.maxTokens=1500
context.maxSources=6

security.keepass.enabled=true
security.keepass.host=127.0.0.1
security.keepass.port=12546
security.keepass.timeoutMillis=15000
security.keepass.pairingKeyStore=file

network.proxy.mode=PAC_URL_POWERSHELL
network.proxy.auth.mode=NONE
network.tls.useJvmDefault=true
network.tls.useWindowsRoot=true
network.tls.useWindowsCaStores=true

agent.enabled=false
```

## Wichtige Schlüssel

### Pflichtangaben

Ohne diese Schlüssel startet der Client nicht vollständig, und der Dialog lässt dich nicht speichern:

- `chat.baseUrl`: die Basis-URL des KI-Dienstes. Sie darf leer sein, wenn der KeePass-Eintrag des API-Keys die URL liefert; siehe [KI-Dienst](help:settings.ai-service).
- `chat.model`: das Chat-Modell.
- `chat.apiKeyRef`: der Verweis auf den KeePass-Eintrag mit dem API-Key.
- `embedding.model` und `embedding.dimension`: das Embedding-Modell und die erwartete Dimension. Liefert der Dienst eine andere Dimension, übernimmt der Client sie beim ersten Aufruf und vermerkt das im Protokoll.

### Weitere Gruppen

| Präfix | Bereich | Reiter |
|---|---|---|
| `chat.` | KI-Dienst, Zeitlimits, Wiederholungen, Werkzeugrunden, Name des Assistenten, Blasenfarben | [KI-Dienst](help:settings.ai-service), [Tools](help:settings.tools), [Sprachausgabe](help:settings.speech), [Darstellung](help:settings.appearance) |
| `embedding.` | Embedding-Modell und Aufrufe | [KI-Dienst](help:settings.ai-service), [Index](help:settings.index) |
| `model.`, `models.local.` | Modelle je Kategorie, lokale Modelle | [Modelle](help:settings.models) |
| `knowledge.`, `retrieval.`, `context.` | Index, Wissenssuche, Aufbereitung, Vorabladen | [Index](help:settings.index), [Netzwerk & Agent](help:settings.network) |
| `security.keepass.` | Verbindung zu KeePassRPC | [KeePass](help:settings.keepass) |
| `network.` | Proxy, HTTP, TLS | [Netzwerk & Agent](help:settings.network) |
| `agent.` | Agent-Modus und Recherche-Budget | [Netzwerk & Agent](help:settings.network) |
| `speech.`, `setup.merlin.` | Stimmen je Modell, Merlins Stimme | [Sprachausgabe](help:settings.speech) |
| `tools.disabled` | abgeschaltete Werkzeuge | [Tools](help:settings.tools) |
| `source.<n>.` | Wissensquellen | Drawer-Reiter „Wissensquellen“, siehe [Wissensquellen im Überblick](help:knowledge.overview) |

## Keine Secrets in der Datei

In die Datei gehören keine API-Keys und keine Passwörter. Sie liegen in KeePass; die Datei enthält nur einen Verweis auf den Titel des KeePass-Eintrags, zum Beispiel:

```properties
chat.apiKeyRef=keepass:Enterprise AI API
network.proxy.auth.credentialRef=keepass:Firmen-Proxy
```

Das Präfix `keepass:` ist optional; der Rest ist der Titel des Eintrags. Der Client holt das Secret erst, wenn er es braucht, über KeePassRPC. Fehlt KeePass, scheitern Anfragen mit einer verständlichen Meldung; ein Wert aus der Datei wird nie als Secret benutzt. Auch das Protokoll und die Fehlermeldungen beim Prüfen nennen nur Schlüssel und Erwartungen, nie Werte. Hintergrund: [KeePass einrichten](help:getting-started.keepass) und [Sicherheit](help:concepts.security).

## Warum fehlen Standardwerte in der Datei?

Der Dialog schreibt viele Schlüssel nur, wenn sie vom Standard abweichen. Steht dort der Standard, entfernt er den Schlüssel; dann gilt der Standard des Programms, auch wenn sich dieser in einer neuen Version ändert. Das betrifft unter anderem:

- den Namen des Assistenten („Merlins Tochter“),
- die Farben der Sprechblasen,
- das Verhalten beim Vorlesen (automatisch aus, Absätze, 2 Worker) und Merlins Stimme,
- die Stimmen je Modell, solange du keine ausdrücklich gewählt hast,
- die Werte für das Vorabladen und die Wiederholungen,
- das Recherche-Budget des Agenten und das Reranking (an).

Andere Werte schreibt der Dialog immer, auch beim Standard: die Zeitlimits, die Grenzen der Wissenssuche, die Werkzeugrunden und Schalter wie `security.keepass.enabled`, die TLS-Schalter, `network.http.preferIPv6` und `agent.enabled`. Fehlt ein Schlüssel in der Datei, gilt immer sein Standardwert.

## Wie der Dialog die Datei schreibt

Beim Speichern prüft der Client zuerst den ganzen Entwurf. Erst wenn er gültig ist, schreibt er die Datei:

- Er ändert nur die Schlüssel, die der Dialog verwaltet. Alle anderen Zeilen und alle Kommentare bleiben unverändert.
- Steht ein Schlüssel schon in der Datei, ersetzt er die Zeile an Ort und Stelle. Steht er dort nur auskommentiert (`#schluessel=`), aktiviert er diese Zeile.
- Neue Schlüssel hängt er am Ende unter der Zeile `# --- Vom Einstellungen-Dialog ergänzt ---` an.
- Leerst du ein Feld, kommentiert er den Schlüssel aus, statt ihn zu löschen.
- Er schreibt zuerst eine neue Datei und ersetzt die alte erst dann. Ein Absturz mitten im Speichern hinterlässt also keine halbe Datei.

Veraltete Schlüssel früherer Versionen entfernt er dabei. Die Wissensquellen (`source.<n>.*`) schreibt der Dialog nie; sie verwaltest du im Drawer-Reiter „Wissensquellen“.

Einige Schlüssel haben kein Feld im Dialog und bleiben beim Speichern unberührt, zum Beispiel weitere Modellparameter wie `chat.temperature`, `chat.tools.enabled`, die Feineinstellungen der Embeddings und Passagen, die Gewichte der Suche, `network.proxy.nonProxyHosts` und Client-Zertifikate einer Quelle. Sie stellst du nur in der Datei ein; die Vorlage erklärt sie in Kommentaren.

## Schritt für Schritt: die Datei von Hand ändern

1. Beende den Client, damit er deine Änderung nicht beim nächsten Speichern überschreibt.
2. Öffne die Datei mit einem Texteditor, der UTF-8 speichert.
3. Ändere oder ergänze die Zeile im Format `schluessel=wert`. Trage keine Secrets ein, nur KeePass-Verweise.
4. Speichere und starte den Client.

Ist die Datei fehlerhaft, öffnet der Client beim Start die Einstellungen und nennt die betroffenen Schlüssel mit der Erwartung, zum Beispiel einen fehlenden Modellnamen. Unbekannte Schlüssel, etwa Tippfehler, erscheinen nur als Warnung. Fehlt die Datei und läuft der Client ohne Oberfläche, etwa ohne Bildschirm, legt er die Vorlage an und beendet sich mit dem Exitcode 2.

## Grenzen

- Die meisten Änderungen gelten nach dem Speichern im Dialog sofort. Eine von Hand geänderte Datei liest der Client erst beim nächsten Start.
- „IPv6 bevorzugen“ wirkt immer erst nach einem Neustart.
- Hinweis: Mehrere gleichzeitig laufende Clients mit derselben Datei sind nicht vorgesehen; was dann passiert, ist nicht geprüft.

## Typische Probleme

### Meine Änderung in der Datei ist weg

Vermutlich lief der Client noch und hat beim Speichern im Dialog seine Werte geschrieben. Beende ihn vor dem Bearbeiten.

### Der Client meldet einen fehlenden Schlüssel

Ergänze den genannten Schlüssel oder öffne Einstellungen; der Dialog springt zum passenden Reiter. Weitere Hilfe: [Probleme bei der Einrichtung](help:troubleshooting.setup).

### Ich finde die Datei nicht

Öffne Einstellungen → „Technische Details“; dort steht der Pfad, und „Einstellungsordner öffnen“ zeigt den Ordner. Unter Windows ist `%APPDATA%` ein versteckter Ordner; tippe den Pfad in die Adresszeile des Explorers.
