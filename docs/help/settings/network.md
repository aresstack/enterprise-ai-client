---
id: settings.network
title: Einstellungen – Reiter „Netzwerk & Agent“
keywords: Netzwerk, Proxy, PAC, WPAD, Proxy-Skript, PowerShell, VBScript, Proxy auflösen, HTTPS-Verbindung testen, TLS, Zertifikat, CA-Datei, cacerts, Windows-ROOT, User-Agent, Proxy-Anmeldung, BASIC, 407, IPv6, Timeout, Zeitlimit, Wiederholungen, Retry, Agent, Agent-Modus, ACP, Kommando, Recherche-Budget, Vorabladen, Prefetch, Fenstertitel, network.proxy.mode
---

# Einstellungen – Reiter „Netzwerk & Agent“

Der Reiter „Netzwerk & Agent“ sammelt alles, was den Weg ins Netz und das Verhalten im Hintergrund bestimmt: den Fenstertitel, die Proxy-Auflösung, das Vertrauen in TLS-Zertifikate, User-Agent und Proxy-Anmeldung, Zeitlimits und automatische Wiederholungen, den Agent-Modus und das Vorabladen von Suchtreffern. Einige Beschriftungen der Proxy-, TLS- und HTTP-Felder sind bewusst englisch. Brauchst du den Reiter nur, weil der KI-Dienst nicht erreichbar ist, lies zuerst den Abschnitt [Typische Probleme](help:settings.network#typische-probleme).

Der Reiter besteht aus sieben Bereiche: „Allgemein“, „Proxy resolution“, „TLS certificate trust“, „HTTP client & proxy authentication“, „Zeitlimits und Wiederholungen“, „Agent-Modus (ACP)“ und „Vorabladen (Prefetch)“.

## Bereich „Allgemein“

- **„Fenstertitel“** (Tooltip: „Titel des Hauptfensters“). Standard: „Enterprise AI Client“. Wirkt nach dem Speichern sofort auf den Titel des Hauptfensters.

## Bereich „Proxy resolution“

Der Client löst für jedes Ziel einzeln auf, ob er direkt oder über einen Proxy verbindet, und gibt diese Route jeder Verbindung mit. Er verwendet dafür die Bibliothek win-proxy-java. Kann eine Route nicht ermittelt werden, verbindet der Client nicht und fällt nie still auf eine direkte Verbindung zurück.

### Mode

Auswahlliste mit den Modi der Bibliothek. **Standard: `PAC_URL_POWERSHELL`.**

| Modus | Bedeutung |
|---|---|
| `DISABLED` | Kein Proxy, immer direkt. |
| `MANUAL_PROXY` | Fester Proxy aus „Manual host“ und „Manual port“; PAC-Felder werden ignoriert. |
| `WINDOWS_STATIC_PROXY` | Fester Proxy aus den Windows-Einstellungen. |
| `PAC_URL_MANUAL` | Du trägst die Adresse des PAC- bzw. WPAD-Skripts direkt ins Feld „PAC URL discovery script“ ein (http, https oder file). |
| `PAC_URL_POWERSHELL` | Ein PowerShell-Skript ermittelt die PAC-Adresse; das Standardskript liest `AutoConfigURL` aus den Windows-Einstellungen. |
| `PAC_URL_WSCRIPT` | Wie oben, aber mit einem VBScript. |
| `PAC_URL_WINDOWS_SETTINGS` | Die PAC-Adresse kommt aus den Windows-Proxy-Einstellungen. |
| `WINDOWS_NATIVE_PROXY_SETTINGS`, `WINDOWS_NATIVE_ROUTE_RESOLVER` | Auflösung über Windows-eigene Mechanismen. Hinweis: Das genaue Verhalten bestimmt die Bibliothek win-proxy-java; es ist hier nicht für alle Umgebungen geprüft. |

Beim Wechsel des Modus schreibt der Dialog eine kurze Erklärung ins Protokollfeld unter den Knöpfen, zum Beispiel „MANUAL_PROXY: enter proxy host and port; PAC fields are ignored.“ Ältere Modusnamen aus früheren Dateien (`NONE`, `MANUAL`, `SYSTEM`, `AUTO`) werden gelesen und beim Speichern durch die neuen ersetzt; ein unbekannter Name wird zu `PAC_URL_POWERSHELL`.

### Die Felder

| Feld | Bedeutung | Standard | Wann aktiv |
|---|---|---|---|
| „Test URL“ | Ziel für „Proxy auflösen“ und „HTTPS-Verbindung testen“ | leer = Basis-URL des KI-Dienstes plus `/models` | immer |
| „PAC URL discovery script“ | Je nach Modus das PowerShell-Skript, das VBScript oder die PAC-Adresse | Bei den Skript-Modi das Standardskript der Bibliothek | bei `PAC_URL_POWERSHELL`, `PAC_URL_WSCRIPT`, `PAC_URL_MANUAL` |
| „Manual host“ | Rechnername des Proxys | leer | nur bei `MANUAL_PROXY`, dort Pflicht |
| „Manual port“ | Port des Proxys | leer | nur bei `MANUAL_PROXY`, dort Pflicht |
| „Resolve timeout (ms)“ | Harte Obergrenze je Auflösung (Skript, PAC-Download, Auswertung) | leer = 45000 | immer |

Das Feld „PAC URL discovery script“ merkt sich je Modus seinen eigenen Text: wechselst du von PowerShell zu VBScript und zurück, steht dein PowerShell-Text wieder da. Gleicht das Skript dem Standardskript der Bibliothek, schreibt der Dialog es nicht in die Datei; leer heißt dort „Standardskript“.

### Die Knöpfe

- **„Proxy auflösen“** prüft nur die Proxy- bzw. PAC-Auflösung der Test-URL mit den Werten des Entwurfs: keine Namensauflösung des Ziels, kein TLS, keine Anmeldung.
- **„HTTPS-Verbindung testen“** löst dieselbe Route auf und baut zusätzlich eine echte HTTPS-Verbindung mit den TLS-Quellen des Entwurfs auf; dabei wird die Zertifikatskette geprüft.
- **„Reset default“** setzt Proxy, TLS-Häkchen und HTTP-Felder auf die Vorgaben der Bibliothek zurück: Modus `PAC_URL_POWERSHELL`, Standardskript, leere Test-URL, Host, Port und Timeout, alle drei TLS-Quellen an, User-Agent leer, Proxy-Anmeldung `NONE`, IPv6 aus. Das Feld „CA-Datei (optional)“, die Zeitlimits, der Agent und das Vorabladen bleiben unverändert.

Darunter steht ein Protokollfeld, in das die Tests ihre Zeilen schreiben. Während ein Test läuft, sind beide Testknöpfe gesperrt. Die Tests ändern nichts an der laufenden Anwendung.

## Bereich „TLS certificate trust“

Hier legst du fest, welchen Zertifizierungsstellen der Client bei HTTPS vertraut (über die Bibliothek win-trust-java).

| Feld | Standard | Bedeutung |
|---|---|---|
| „Use JVM default cacerts“ | an | Der mitgelieferte Truststore von Java. |
| „Use Windows-ROOT store“ | an | Die vertrauenswürdigen Stammzertifizierungsstellen von Windows. |
| „Use Windows Root/Intermediate stores“ | an | Zusätzlich die Zwischenzertifizierungsstellen von Windows. |
| „CA-Datei (optional)“ mit „Wählen …“ | leer | Eine PEM- oder DER-Datei mit weiteren CA-Zertifikaten, etwa dem Zertifikat eines Firmen-Proxys. „Wählen …“ öffnet „CA-Datei wählen (PEM oder DER)“. |

Die Windows-Quellen gelten nur unter Windows. Eine eingetragene CA-Datei, die fehlt oder leer ist, meldet schon die Prüfung beim Speichern.

## Bereich „HTTP client & proxy authentication“

- **„User-Agent“** mit Knopf „Use browser UA“: der User-Agent aller HTTP-Aufrufe. Leer gilt `EnterpriseAiClient/<Version>`. „Use browser UA“ trägt die Kennung eines Firefox-Browsers ein, für Proxys oder Server, die nur Browser durchlassen.
- **„Proxy auth mode“**: `NONE` (Standard) oder `BASIC`.
- **„Proxy credentials (BASIC, KeePass entry)“**: nur bei `BASIC` bearbeitbar und dann Pflicht. Hier steht der Titel eines KeePass-Eintrags, mit dem Benutzernamen im Benutzerfeld und dem Passwort im Passwortfeld. Benutzer und Passwort selbst gehören nie in die Konfiguration. Eine integrierte Windows-Anmeldung am Proxy wird nicht unterstützt.
- **„Prefer IPv6 (needs restart; use when IPv4 egress is broken)“**: Standard aus. Wirkt als einzige Einstellung des Dialogs erst nach einem Neustart.

## Bereich „Zeitlimits und Wiederholungen“

Hinweis im Dialog: Vorübergehende Fehler (Verbindung, Zeitüberschreitung, HTTP 429 oder 5xx, KeePass kurz nicht erreichbar) werden automatisch wiederholt; jede weitere Pause ist 2,5-mal so lang wie die vorige. Falsche Zugangsdaten, fehlende Einträge und abgelehnte Anfragen werden nie wiederholt.

| Feld | Standard | Erlaubt | Bedeutung |
|---|---|---|---|
| „Chat: Verbindungs-Timeout (ms)“ | 10000 | 0–3600000, 0 = ohne Frist | Frist für den Verbindungsaufbau zum KI-Dienst |
| „Chat: Lese-Timeout (ms)“ | 120000 | 0–3600000 | Frist zwischen zwei Datenstücken der Antwort |
| „Chat: Wiederholungen“ | 2 | 0–10 | Je Modellanfrage, nur solange noch kein Antworttext sichtbar ist; Werkzeuge laufen nie doppelt |
| „Chat: Pause vor Wiederholung (ms)“ | 500 | 0–30000 | Pause vor der ersten Wiederholung |
| „KeePassRPC: Timeout (ms)“ | 15000 | 1–3600000 | Frist je Zugriff auf KeePass |
| „KeePassRPC: Wiederholungen“ | 2 | 0–10 | Nur wenn KeePass nicht antwortet, nie bei fehlendem Eintrag |
| „KeePassRPC: Pause vor Wiederholung (ms)“ | 300 | 0–30000 | Pausen also 300 ms, dann 750 ms |
| „Wissensquellen: Wiederholungen“ | 2 | 0–10 | Suche und Seitenabruf, auch die Wissenssuche des Agenten |
| „Wissensquellen: Pause vor Wiederholung (ms)“ | 500 | 0–30000 | Pause vor der ersten Wiederholung |

Unter den Feldern steht: „Im ungünstigsten Fall wartet ein Zugriff bis zu (Wiederholungen + 1) × Timeout.“ Erhöhe Zeitlimits deshalb nur, wenn ein Dienst wirklich langsam antwortet. Ein leeres Feld bedeutet den Standard.

## Bereich „Agent-Modus (ACP)“

| Feld | Standard | Bedeutung |
|---|---|---|
| „Agent-Modus aktivieren“ | aus | Tooltip: „Reiter „Agent“ anzeigen“. An bedeutet: der Agent-Modus startet das unten eingetragene Programm als eigenen Agenten. |
| „Kommando“ | leer | Das Programm, zum Beispiel `java`. Pflicht, wenn der Agent-Modus aktiviert ist. |
| „Argumente“ | leer | Shell-ähnlich, Anführungszeichen erlaubt, zum Beispiel `-jar "C:/Pfad/agent.jar"`. |
| „Timeout je Anfrage (Sekunden)“ | 30 | Wartezeit auf Antworten des Agenten (1–86400). |
| „Recherche-Budget (Seiten)“ | 4 | Höchstzahl vollständig gelesener Seiten je Agent-Recherche (0–10000, 0 = unbegrenzt). |

Hinweis: Nach dem aktuellen Stand des Programms arbeitet der Agent-Modus ohne eingeschaltetes eigenes Kommando mit dem eingebauten Recherche-Agenten; das Häkchen schaltet auf dein eigenes Agentenprogramm um. Ein geändertes Kommando gilt ab der nächsten Agent-Sitzung. Das Recherche-Budget gilt nur für den Agenten; wie viele Quellen eine normale Chat-Antwort nutzt, stellst du im Reiter [Index](help:settings.index) ein. Mehr zum Agenten: [Der Agent-Modus](help:agent.overview).

## Bereich „Vorabladen (Prefetch)“

Hinweis im Dialog: Suchtreffer werden im Hintergrund vorgeladen und indexiert, ab dem gewählten bzw. obersten sichtbaren Treffer. Ein Klick oder das Lesewerkzeug lädt sofort, nie hinter dem Vorladen. Das Recherche-Budget des Agenten bleibt davon unberührt.

| Feld | Standard | Erlaubt | Auswahl |
|---|---|---|---|
| „Max. Seiten vorladen“ | 100 | 1–1000 | 20, 50, 100, 200, 500 oder eigener Wert |
| „Parallele Requests“ | 4 | 1–8 | 1 bis 8 |
| „Max. Cache-Größe (MB)“ | 50 | 1–500 | 10, 50, 100, 200, 500 oder eigener Wert |

## Was in die Datei geschrieben wird

Die Zeitlimits stehen wie in der Beispielkonfiguration in der Datei. Wiederholungen, Pausen, das Recherche-Budget und die Vorlade-Werte schreibt der Dialog nur, wenn sie vom Standard abweichen; sonst entfernt er den Schlüssel, und der Standard gilt. Proxy-Ausnahmen (`network.proxy.nonProxyHosts`) und einige Agent-Feineinstellungen haben kein Feld und bleiben in der Datei, wie sie sind. Siehe [Die Konfigurationsdatei](help:settings.config-file).

## Typische Probleme

### „UnknownHostException“ oder der KI-Dienst ist nicht erreichbar

Meist verlangt das Netz einen Proxy. Lass den Modus auf `PAC_URL_POWERSHELL` und klicke „Proxy auflösen“. Findet das Skript keine PAC-Adresse, obwohl der Browser einen Proxy nutzt, versuche `PAC_URL_WINDOWS_SETTINGS` oder `PAC_URL_WSCRIPT`, trag die PAC-Adresse aus den Internetoptionen mit `PAC_URL_MANUAL` ein oder als letzten Schritt `MANUAL_PROXY` mit Host und Port.

### „PKIX path building failed“ bei „HTTPS-Verbindung testen“

Java vertraut dem Serverzertifikat nicht, typisch bei Firmen-Proxys mit TLS-Prüfung oder internen Zertifizierungsstellen. Lass beide Windows-Häkchen an. Hilft das nicht, exportiere die ausstellende Stelle als PEM und trag sie unter „CA-Datei (optional)“ ein.

### Der Proxy antwortet mit 407

Der Proxy verlangt eine Anmeldung. Stell „Proxy auth mode“ auf `BASIC` und trag einen KeePass-Eintrag mit Benutzer und Passwort ein.

### Antworten brechen nach langer Wartezeit ab

Prüfe „Chat: Lese-Timeout (ms)“. Weitere Hilfe: [Netzwerkprobleme](help:troubleshooting.network) und [Probleme mit dem Agenten](help:troubleshooting.agent).
