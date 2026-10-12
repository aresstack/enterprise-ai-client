---
id: troubleshooting.network
title: Probleme mit Netzwerk, Proxy und Zertifikaten
keywords: Netzwerk, Proxy, PAC, Proxy auflösen, Proxy-Route, 407, Proxy-Anmeldung, TLS, Zertifikat, PKIX, Zertifikatsfehler, Windows-Zertifikatsspeicher, CA-Datei, Zeitüberschreitung, Timeout, nicht erreichbar, Verbindung abgelehnt, Hostname, DNS, HTTP 401, HTTP 403, HTTP 429, HTTP 500, HTTP 502, HTTP 503, Wiederholung, Retry, HTTPS-Verbindung testen, Verbindung testen
---

# Probleme mit Netzwerk, Proxy und Zertifikaten

Fast alle Verbindungen des Clients laufen über HTTPS: zum KI-Dienst, zu Confluence, MediaWiki und anderen Quellen. Im Firmennetz liegen dazwischen meist ein Proxy und eine eigene Zertifizierungsstelle. Dieses Kapitel hilft, wenn der KI-Dienst oder eine Quelle „nicht erreichbar“ ist, ein Zertifikat abgelehnt wird, Anfragen in eine Zeitüberschreitung laufen oder der Dienst mit HTTP 401, 403, 429 oder 5xx antwortet. Es erklärt außerdem, welche Fehler der Client selbst wiederholt und welche nicht. Die Einstellungen selbst beschreibt [Netzwerk einstellen](help:settings.network).

## Zuerst: die zwei Testknöpfe

Bevor du einzelne Einstellungen änderst, lass den Client selbst nachsehen.

1. Einstellungen → „KI-Dienst“ → Bereich „Verbindung“ → „Verbindung testen“ (gleicher Knopf im Reiter „Technische Details“). Der Test arbeitet die Schritte „API-Key“, „Proxy-Route“, „HTTPS-Verbindung“, „GET /models“ und „Embedding-Modell“ nacheinander ab. Jeder Schritt bekommt ein Zeichen: ✓ in Ordnung, ! Hinweis, ✗ Fehler, – übersprungen. Darunter steht „Verbindung in Ordnung.“, „Verbindung steht; bitte die Hinweise beachten.“ oder „Verbindung fehlgeschlagen; der rote Schritt nennt die Ursache.“
2. Einstellungen → „Netzwerk & Agent“ → Bereich „Proxy resolution“ → „Proxy auflösen“ zeigt Schritt für Schritt, wie die Proxy-Route bestimmt wird (PAC-Adresse, Skript, Ergebnis). „HTTPS-Verbindung testen“ baut danach eine echte Verbindung zur „Test URL“ auf und schreibt „Testing HTTPS …“, „Route: …“, „TLS trust: …“, „TLS handshake OK (…)“ und „Result: HTTP … (User-Agent: …, ohne API-Key)“ ins Ausgabefeld.

Ist keine „Test URL“ eingetragen, verwendet der Test die Basis-URL des KI-Dienstes. Fehlt beides, erscheint „ERROR: keine Test URL (Test URL oder Basis-URL des KI-Dienstes eintragen)“.

Beide Tests lesen die Einstellungen aus dem offenen Dialog, auch wenn du sie noch nicht gespeichert hast. Der Test geht ohne Chat-Anfrage und ohne API-Key in der Ausgabe.

## Proxy

Standardmäßig ermittelt der Client die Proxy-Route wie Windows: Er fragt per PowerShell die PAC-Adresse ab, wertet das PAC-Skript aus und nimmt den dort genannten Proxy (Modus „PAC_URL_POWERSHELL“). Andere Modi wählst du unter „Mode“: „DISABLED“ (direkt), „MANUAL_PROXY“ (Host und Port unter „Manual host“ und „Manual port“), „WINDOWS_STATIC_PROXY“, „PAC_URL_MANUAL“ (PAC-Adresse selbst eintragen), „PAC_URL_WSCRIPT“ und „PAC_URL_WINDOWS_SETTINGS“.

Wichtig: Lässt sich die Route nicht bestimmen, versucht der Client **keine** Verbindung und fällt auch nicht still auf eine direkte Verbindung zurück. Die Modi „WINDOWS_NATIVE_…“ sind in dieser Version nicht umgesetzt und führen immer zu diesem Fall.

### „Die Proxy-Route konnte nicht bestimmt werden“

Hinweis in der Fehlerblase: „Die Proxy-Route konnte nicht bestimmt werden (NOT_IMPLEMENTED oder ERROR der Auflösung); es wurde keine Verbindung versucht. Unter Netzwerk „Proxy auflösen“ zeigt jeden Schritt (PAC-Adresse, Skript, Ergebnis); Modus, PAC-Adresse oder Ermittlungsskript anpassen.“

1. „Proxy auflösen“ klicken und den ersten fehlerhaften Schritt lesen.
2. Liefert PowerShell keine PAC-Adresse, „PAC_URL_MANUAL“ wählen und die Adresse des PAC-Skripts selbst eintragen, oder „MANUAL_PROXY“ mit Host und Port.
3. Dauert die Auflösung zu lange, „Resolve timeout (ms)“ erhöhen.
4. „Reset default“ stellt das Ermittlungsskript wieder her, falls es verändert wurde.

### Der Proxy lehnt ab oder verlangt eine Anmeldung (HTTP 407)

- „Der Proxy verlangt eine Anmeldung. Unter Netzwerk die Proxy-Anmeldung BASIC mit einem KeePass-Eintrag (Benutzername und Passwort) einstellen (…); eine Windows-integrierte Anmeldung (NTLM/Kerberos) unterstützt die Anwendung nicht.“
- „Der Proxy hat die Verbindung zum Dienst abgelehnt. Proxy-Einstellung prüfen (…); „Proxy auflösen“ unter Netzwerk zeigt die Route.“

Vorgehen bei 407:

1. Lege in KeePass einen Eintrag mit Benutzername und Passwort für den Proxy an.
2. Bereich „HTTP client & proxy authentication“: „Proxy auth mode“ auf „BASIC“, unter „Proxy credentials (BASIC, KeePass entry)“ den Titel des Eintrags.
3. Speichern und das Programm **neu starten**. Der Test meldet ausdrücklich „ERROR: Der Proxy verlangt eine Anmeldung (Proxy auth mode BASIC; gilt nach Neustart).“

Verlangt der Proxy nur NTLM oder Kerberos, kommst du mit dem Client nicht durch; frage nach einem Proxy mit Basic-Anmeldung oder einer Ausnahme.

Lehnt der Proxy ab, obwohl die Route stimmt, prüfe, ob das Ziel direkt erreicht werden müsste (interne Server). Solche Hosts gehören in die Ausnahmeliste des Proxys.

## Zertifikate und TLS

### „Java vertraut dem Zertifikat des Servers nicht“

Das ist der häufigste Fehler im Firmennetz. Ursache ist fast immer ein Proxy, der HTTPS mit einem eigenen Firmenzertifikat neu signiert, oder ein älteres Java ohne passende Stammzertifizierungsstelle.

1. Bereich „TLS certificate trust“ öffnen. Unter Windows sollten „Use Windows-ROOT store“ und „Use Windows Root/Intermediate stores“ angehakt sein (Standard). Dann vertraut der Client denselben Zertifikaten wie Windows und PowerShell.
2. Hilft das nicht oder läuft der Client nicht unter Windows: das Zertifikat der Firmen-CA als PEM-Datei besorgen und unter „CA-Datei (optional)“ mit „Wählen …“ eintragen.
3. „HTTPS-Verbindung testen“: Die Zeile „TLS trust: …“ zeigt, welche Quellen geladen wurden; „TLS handshake OK (…)“ bestätigt den Erfolg.

Meldet der Test „TLS-Vertrauensquellen nicht nutzbar: …“ oder „TLS-Vertrauensregel nicht baubar: …“, ist eine der Quellen kaputt, etwa eine unlesbare CA-Datei. Prüfe Pfad und Format (PEM, Text beginnend mit „-----BEGIN CERTIFICATE-----“).

### „Die TLS-Verhandlung mit dem Server ist gescheitert“

Vollständiger Hinweis: „Die TLS-Verhandlung mit dem Server ist gescheitert (Protokollversion oder Cipher-Suite). Java aktualisieren; TLS 1.3 gibt es in Java 8 erst ab Update 261.“ Das ist kein Einstellungsfehler im Client; das mitgelieferte bzw. verwendete Java ist zu alt für den Server.

Zertifikatsprobleme mit Client-Zertifikaten (Anmeldung per Zertifikat bei Confluence) behandelt [Confluence](help:troubleshooting.confluence).

## Nicht erreichbar und Zeitüberschreitung

Im Chat steht dann „Der KI-Dienst ist nicht erreichbar.“ Der Hinweis darunter unterscheidet:

- **Hostname nicht auflösbar**: „Der Hostname lässt sich nicht auflösen. Hinter einem Firmen-Proxy muss die Proxy-Route den Proxy nennen …“. Hinter einem Proxy löst oft nur der Proxy Namen auf; eine Route „DIRECT“ führt dann genau zu diesem Fehler. Sonst Schreibweise der Basis-URL prüfen.
- **Verbindung abgelehnt**: „Die Verbindung wird abgelehnt oder blockiert (Firewall, falscher Port, Proxy nötig). Basis-URL und Proxy-Einstellung (…) prüfen.“
- **Zeitüberschreitung**: „Zeitüberschreitung: Server oder Proxy antworten nicht. Netzverbindung und Proxy prüfen (die Route steht im Protokoll unter "Proxy-Route"); …“
- **Abgebrochener Stream**: „Der Server hat die Antwort beendet, ohne sie abzuschließen (Verbindung unterbrochen oder Proxy puffert den Stream). Erneut versuchen; …“

Prüfschritte:

1. Im Protokoll nach „Proxy-Route“ suchen: Die beim Start ermittelte Route steht dort.
2. „Verbindung testen“ laufen lassen; der erste rote Schritt zeigt, wo es hängt.
3. Bei Zeitüberschreitungen die Fristen anpassen: Einstellungen → „Netzwerk & Agent“ → „Zeitlimits und Wiederholungen“: „Chat: Verbindungs-Timeout (ms)“ (Standard 10000) und „Chat: Lese-Timeout (ms)“ (Standard 120000). Lange Antworten großer Modelle brauchen mehr Lesezeit.
4. Funktioniert IPv4 im Netz nicht, aber IPv6 schon, hilft „Prefer IPv6 (needs restart; use when IPv4 egress is broken)“ (nach Neustart).

## HTTP-Statuscodes und Wiederholungen

Antwortet der Dienst mit einem Fehlerstatus, zeigt die Blase eine Überschrift mit „(Status N)“ und unter „Details anzeigen“ die technische Ursache „HTTP N: …“ (ein mitgeschickter API-Key ist dort als „Bearer ***“ maskiert).

| Status | Überschrift in der Fehlerblase | Wird automatisch wiederholt? |
|---|---|---|
| 401, 403 | „Anmeldung am KI-Dienst fehlgeschlagen.“ | nein |
| 407 (Proxy) | Anmeldung am Proxy, siehe oben | nein |
| 429 | „Der KI-Dienst ist ausgelastet. Bitte später erneut versuchen.“ | ja |
| 500–599 | „Fehler im KI-Dienst.“ | ja |
| andere (400, 404, 413 …) | „Der KI-Dienst hat die Anfrage abgelehnt.“ | nein |
| keine Antwort (Netz, Zeitüberschreitung) | „Der KI-Dienst ist nicht erreichbar.“ | ja |
| unverständliche Antwort | „Unerwartete Antwort des KI-Dienstes.“ | nein |

So funktionieren die Wiederholungen beim Chat:

- Standard sind zwei Wiederholungen („Chat: Wiederholungen“), die erste nach 500 ms („Chat: Pause vor Wiederholung (ms)“). Jede weitere Pause ist 2,5-mal so lang, höchstens 30 Sekunden; mehr als 10 Wiederholungen lassen sich nicht einstellen.
- Wiederholt wird nur, solange noch kein Antworttext angekommen ist. Bricht ein Stream mitten im Text ab, siehst du den Fehler sofort. Werkzeuge werden nie doppelt ausgeführt.
- Im Protokoll steht je Versuch „Modellanfrage vorübergehend gescheitert (…); Wiederholung n von m in X ms“.

Embedding-Anfragen haben eine eigene, feste Regel: Bei HTTP 429 oder 503 wird dieselbe Anfrage nach 500 ms und nach 1500 ms wiederholt (höchstens drei Versuche); nennt der Dienst eine Wartezeit („Retry-After“), wartet der Client mindestens so lange. Alle anderen Embedding-Fehler gelten sofort. Außerdem schickt der Client immer nur eine Embedding-Anfrage gleichzeitig, damit Dienste mit „eine Anfrage je Schlüssel“ nichts ablehnen.

Wissensquellen (Confluence, MediaWiki usw.) und die Wissenssuche des Agenten wiederholen nach demselben Muster bei Netzfehlern, Zeitüberschreitung, 429 und 5xx, eingestellt über „Wissensquellen: Wiederholungen“ und „Wissensquellen: Pause vor Wiederholung (ms)“. Protokollzeile: „Quelle vorübergehend nicht erreichbar (…); Wiederholung n von m in X ms“. Zu KeePass siehe [KeePass](help:troubleshooting.keepass).

Die Oberfläche fasst die Regel so zusammen: „Falsche Zugangsdaten, fehlende Einträge und abgelehnte Anfragen werden nie wiederholt.“ Ein 401 oder 403 musst du also selbst beheben, meist am API-Key ([KeePass](help:troubleshooting.keepass)) oder an fehlenden Rechten beim Betreiber des Dienstes.

### Welcher Status bedeutet was?

- **401/403**: Key falsch, abgelaufen oder ohne Berechtigung. „Verbindung testen“ zeigt bei GET /models „HTTP 401: Der Dienst lehnt den API-Key ab. Passwortfeld des KeePass-Eintrags prüfen (vollständiger Key, ohne Leerzeichen).“
- **404 bei GET /models**: „Der Dienst antwortet, kennt aber /models unter dieser Basis-URL nicht. Basis-URL prüfen (endet meist auf /v1); …“. Die Basis-URL darf außerdem nicht auf „/chat/completions“, „/embeddings“ oder „/models“ enden.
- **3xx**: „Umleitung nach …; Basis-URL entsprechend anpassen.“ Oft fehlt ein „/“ oder es steht „http“ statt „https“.
- **429**: Der Dienst drosselt. Nach den automatischen Wiederholungen einige Minuten warten.
- **5xx**: „Serverfehler von Dienst oder Proxy.“ Liegt beim Betreiber oder Proxy; tritt er dauerhaft auf, die Uhrzeit aus dem Protokoll an den Betreiber geben.

## Für Fortgeschrittene

Die zugehörigen Schlüssel der Konfigurationsdatei: `network.proxy.mode`, `network.proxy.nonProxyHosts`, `network.proxy.auth.mode`, `network.proxy.auth.credentialRef`, `network.tls.useWindowsRoot`, `network.tls.useWindowsCaStores`, `network.tls.caCertificatesFile`, `chat.connectTimeoutMillis`, `chat.readTimeoutMillis`, `chat.retryCount` und `chat.retryDelayMillis`. Die Fehlerblase nennt einige davon direkt, damit du sie in der Datei wiederfindest ([Konfigurationsdatei](help:settings.config-file)).

## Grenzen

- Keine Windows-integrierte Proxy-Anmeldung (NTLM, Kerberos).
- Kein stiller Rückfall auf eine direkte Verbindung, wenn die Proxy-Route fehlt.
- Die native Windows-Proxy-Ermittlung („WINDOWS_NATIVE_…“) ist nicht umgesetzt.
- Hinweis: Ob eine geänderte Proxy-Anmeldung ohne Neustart wirkt, ist nicht für alle Installationen geprüft; starte nach dieser Änderung sicherheitshalber neu.
- Hinweis: Das Verhalten gegenüber konkreten Firmen-Proxys und KI-Diensten ist nicht für alle Installationen geprüft.
