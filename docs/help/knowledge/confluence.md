---
id: knowledge.confluence
title: Confluence als Wissensquelle
keywords: Confluence, Confluence Data Center, Wiki, Spaces, Space, Bereich, Basis-URL, REST, CQL, Suche, KeePass, KeePass-Eintrag, Benutzer, Passwort, Token, Personal Access Token, PAT, Bearer, Basic, anonym, Zertifikat, Client-Zertifikat, mTLS, Windows-Zertifikatspeicher, MM_CertAlias, MM_RequiresLogin, Zertifikat verwenden, Anhang, Anhänge, Bilder, draw.io, Einmalcode, OTP, Zugriff verweigert, nicht erreichbar, unerwartete Antwort, SSO, Anmeldeseite, Proxy, https, Confluence Cloud, Blog, confluence source, client certificate, spaces, access denied
---

# Confluence als Wissensquelle

Dieses Kapitel beschreibt, wie du eine Confluence-Instanz als Wissensquelle anbindest: was du dafür brauchst, wie du sie im Quellen-Dialog einrichtest, wie die Anmeldung über KeePass und ein optionales Client-Zertifikat funktioniert, wie Suche, Spaces und Anhänge arbeiten und was bei typischen Fehlern zu prüfen ist. Es richtet sich an alle, die ihr Firmen-Confluence im Chat nutzen wollen. Die allgemeine Bedienung des Reiters „Wissensquellen“ steht in [Wissensquellen im Überblick](help:knowledge.overview).

## Voraussetzungen

- **Confluence Data Center** (selbst betrieben) mit erreichbarer REST-API. Confluence Cloud ist nicht Ziel dieses Adapters; Folgeadressen akzeptiert der Client nur unterhalb des eigenen Kontextpfads unter `/rest/api/`.
- **Die Basis-URL** deiner Instanz einschließlich Kontextpfad, zum Beispiel `https://confluence.example.org/confluence`, ohne `/rest` am Ende.
- **Ein KeePass-Eintrag** mit Benutzername und Passwort (oder nur einem Token) und am besten der URL. Ohne Eintrag liest der Client anonym, was meist nur bei offenen Instanzen funktioniert. KeePass richtest du in [KeePass einrichten](help:getting-started.keepass) ein.
- **https**: Mit Zugangsdaten verlangt der Client eine https-Adresse. Eine http-Adresse mit KeePass-Eintrag lehnt die Prüfung ab.
- **Optional ein Client-Zertifikat** im Windows-Zertifikatspeicher, wenn deine Confluence eines verlangt (mTLS).
- **Netzwerk**: Proxy und Vertrauensquellen kommen aus den Einstellungen (siehe [Netzwerk und Proxy](help:settings.network)).

Hinweis: Der Adapter ist gegen Testserver geprüft, nicht gegen jede echte Installation. Insbesondere die Anmeldung nur mit Token und Single Sign-on hängen vom Server ab und sind nicht für alle Installationen geprüft.

## Einrichten

Am bequemsten richtest du Confluence im Einrichtungsassistenten ein (siehe [Confluence im Einrichtungsassistenten](help:getting-started.confluence)). Später oder für eine zweite Instanz geht es so:

1. Öffne den Drawer, Reiter „Wissensquellen“, und klicke „+ Quelle“.
2. Wähle bei „Typ“ den Eintrag „Confluence“. Die Beschreibung lautet: „Alle Spaces von Confluence Data Center über die REST-API; welche durchsucht werden, hakt man unter Wissensquellen an“.
3. **„Titel“**: der Name der Zeile unter Wissensquellen, Vorgabe „Confluence“. Gib einen eigenen Titel, wenn du mehrere Confluence-Instanzen anbindest.
4. **„Zugang“**: „KeePass“ (empfohlen) oder „Manuell“.
5. **„KeePass-Eintrag“**: wähle den Eintrag aus der Liste. Der Hinweis lautet „Eintrag mit Benutzername, Passwort und URL von Confluence; leer = anonym“.
6. **„Basis-URL“**: Bei „KeePass“ steht das Feld unter „Erweitert“ als „Basis-URL (sonst aus KeePass)“ und darf leer bleiben, wenn die URL im Eintrag steht; der Dialog übernimmt sie dann. Bei „Manuell“ ist sie eine Pflichtangabe oben.
7. **„Zertifikat“**: siehe [unten](help:knowledge.confluence#client-zertifikat).
8. Klicke „Speichern“. Die Quelle erscheint sofort in der Liste, mit der Statuszeile „Wird bei der Suche live gefragt; Treffer kommen in den Index“.

Startpunkte gibt es im Dialog nicht: Confluence liest alle Spaces, die du unter Wissensquellen angehakt lässt.

## Anmeldung und Zugangsdaten aus KeePass

So meldet sich der Client bei Confluence an:

- **Benutzername und Passwort im Eintrag**: Anmeldung mit Benutzer und Passwort (HTTP Basic), wie im Vorgänger MainframeMate.
- **Nur Passwortfeld, kein Benutzername**: Das Passwortfeld gilt als Personal Access Token und wird als Bearer-Token gesendet. Hinweis: Diese Variante ist nicht gegen eine echte Instanz geprüft.
- **Kein Eintrag**: anonym.
- **Zusatzfeld `MM_RequiresLogin=false` im Eintrag**: Der Client sendet keine Anmeldung, sondern verlässt sich nur auf das Client-Zertifikat.

Die Zugangsdaten holt der Client bei jedem Vorgang (eine Suche, ein Seitenabruf) einmal aus KeePass und vergisst sie danach wieder. Sie stehen nie in der Konfigurationsdatei und nicht im Protokoll. Bei „Manuell“ fragt ein Dialog Benutzer und Passwort beim ersten Zugriff ab; die Eingabe gilt bis zum Beenden.

Ist KeePass kurz nicht erreichbar, meldet die Quelle „nicht erreichbar“ statt „Zugriff verweigert“; so unterscheidest du einen KeePass-Ausfall von falschen Zugangsdaten.

## Client-Zertifikat

Manche Confluence-Instanzen verlangen zusätzlich ein persönliches Zertifikat (mTLS). Im Dialog gibt es dafür:

- das Häkchen **„Zertifikat verwenden“** (Vorgabe: an),
- ein schreibgeschütztes Feld mit dem Alias des gewählten Zertifikats,
- den Knopf **„Zertifikat…“** (mit Schloss-Symbol). Er öffnet „Zertifikat auswählen“ mit der Liste „Zertifikat aus dem Windows-Zertifikatsspeicher auswählen:“ und den Spalten „Alias“, „Subject“, „Gültig bis“ und „Status“. „Auch abgelaufene Zertifikate anzeigen“ blendet abgelaufene ein.

Was dabei gilt:

1. Ist das Feld leer und steht im KeePass-Eintrag das Zusatzfeld `MM_CertAlias`, belegt der Dialog das Feld damit vor; beim Verbindungsaufbau wird es ebenfalls verwendet.
2. Beim Speichern schreibt der Client den gewählten Alias zusätzlich als `MM_CertAlias` in den KeePass-Eintrag, wie MainframeMate.
3. Ist „Zertifikat verwenden“ an, aber kein Zertifikat gewählt, fragt „Hinweis: Kein Zertifikat“ nach: „Ohne Zertifikat speichern“ oder „Zurück zum Bearbeiten“.
4. Nimmst du das Häkchen weg, verbindet sich der Client ohne Zertifikat, auch wenn der KeePass-Eintrag einen Alias trägt.

Der Windows-Zertifikatspeicher steht nur unter Windows zur Verfügung. Das Zertifikat wird erst beim ersten Verbindungsaufbau geladen, nicht beim Start. Bilder auf fremden Hosts werden nie mit dem Zertifikat abgerufen.

## Spaces auswählen

Die Zeile einer Confluence-Quelle hat die Zeile „Spaces“ mit einem Pfeil.

1. Klicke auf „Spaces“. Beim ersten Aufklappen lädt der Client die Liste aller Spaces im Hintergrund („Spaces werden geladen …“).
2. Jeder Space hat ein Häkchen und seinen Namen („Schlüssel – Name“). Das Häkchen legt nur fest, ob der Space durchsucht wird.
3. Fährst du über den Namen, wird er zum Link; ein Klick öffnet die Startseite des Space im Lesebereich.
4. Neue Spaces sind automatisch angehakt.

Die Auswahl gilt ab der nächsten Suche, ohne Neustart, und wird im Anwendungszustand gemerkt, nicht in der Konfigurationsdatei. Abgehakte Spaces schließt der Client in der Suche aus. Scheitert das Laden, steht in Rot „Nicht ladbar: …“; klappe die Liste dann erneut auf.

## Suche und Lesen

Confluence ist eine Quelle mit eigener Suche. Fragst du im Chat etwas oder suchst über „Quellen durchsuchen…“, sendet der Client eine Volltextsuche an Confluence (nur Seiten, keine Blogbeiträge), lädt die Treffer und legt sie in den lokalen Index. Unveränderte Seiten (gleiche Version) lädt er nicht erneut. Was danach mit den Seiten passiert, erklärt [Suche, Vorladen und Indexierung](help:knowledge.indexing).

Eine Vollindexierung auf Knopfdruck gibt es für Confluence nicht; der Kreispfeil bleibt grau. Der Index wächst mit jeder Suche, jeder gelesenen Seite und jedem Lesezugriff des Agenten.

Im Lesebereich zeigt Confluence seine Seiten im Original: HTML mit Bildern an ihrer Stelle, draw.io-Diagramme als gerenderte Zeichnung, Anhänge hinter der Büroklammer. Siehe [Lesebereich und Dokumentansicht](help:knowledge.reader).

## Anhänge

- **Im Lesebereich** siehst du alle Anhänge einer Seite, auch PDFs und Office-Dateien, und kannst sie öffnen, speichern und ansehen. Siehe [Anhänge von Wissensquellen](help:knowledge.attachments).
- **Im Index und damit für den Chat** landen Anhänge nur, wenn in der Konfigurationsdatei `includeAttachments=true` gesetzt ist, und auch dann nur textartige Anhänge (Text, JSON, XML). PDFs und Office-Dateien werden nicht durchsucht.
- **Einmalcode**: Sperrt ein Zwei-Faktor-Plugin die Downloads, erscheint beim Öffnen einer Datei im Lesebereich eine Code-Abfrage. Siehe [Einmalcode (OTP) bei geschützten Dateien](help:knowledge.otp).

## Typische Probleme

### Die Statuszeile oder die Suche meldet „Zugriff verweigert“

Confluence hat die Anmeldung abgelehnt (HTTP 401 oder 403) oder auf eine Anmeldeseite umgeleitet. Prüfe:

1. Stimmen Benutzername und Passwort im KeePass-Eintrag? Melde dich testweise im Browser an.
2. Ist das Konto gesperrt oder das Passwort abgelaufen?
3. Verlangt die Instanz ein Client-Zertifikat, das nicht gewählt oder abgelaufen ist?
4. Nutzt die Instanz Single Sign-on? Dann kann die Anmeldung mit Benutzer und Passwort an der REST-API scheitern.

### „nicht erreichbar“

Netzwerk, Zeitüberschreitung, HTTP 429 oder 5xx, oder KeePass hat nicht geantwortet. Der Client wiederholt solche Fehler automatisch (Einstellungen → Netzwerk & Agent → „Zeitlimits und Wiederholungen“, Felder „Wissensquellen: Wiederholungen“ und „Wissensquellen: Pause vor Wiederholung (ms)“). Prüfe Proxy und VPN, siehe [Fehlersuche Netzwerk](help:troubleshooting.network).

### „unerwartete Antwort“

Statt JSON kam eine HTML-Seite, meist eine Anmelde- oder SSO-Seite eines vorgeschalteten Systems, oder die Basis-URL ist falsch (zum Beispiel mit `/rest` am Ende oder ohne Kontextpfad).

### Beim Speichern: „Confluence-Einstellung ungültig (mit KeePass-Eintrag nur https, Space-Schlüssel prüfen)“

Die Basis-URL beginnt mit `http://`, obwohl ein KeePass-Eintrag gewählt ist, oder sie ist keine gültige Adresse. Nimm die https-Adresse.

### Bilder fehlen: „Bild liegt nicht unter der eingestellten Confluence-Adresse …“

Der Client lädt nur Bilder unter der eingestellten Basis-URL, nie von fremden Hosts. Nennt die Meldung eine andere Adresse derselben Confluence (anderer Hostname, anderer Kontextpfad), stelle die Basis-URL auf die Adresse um, die Confluence selbst verwendet.

### Wo stehen die Details?

Im Protokollordner „logs“ im Anwendungsverzeichnis (der Knopf „Einstellungsordner öffnen“ im Fuß der Einstellungen öffnet den Ordner der Konfigurationsdatei, in dem „logs“ standardmäßig liegt) stehen Zeilen mit „[Confluence]“. Kommt statt einer Datei eine HTML-Seite, legt der Client bis zu fünf solcher Antworten je Quelle und Programmlauf als `confluence-failed-download-…html` dort ab; Eingabewerte sind darin durch Sterne ersetzt, Passwörter und Cookie-Werte stehen nie darin. Weitere Prüfschritte: [Fehlersuche Confluence](help:troubleshooting.confluence).

## Für Fortgeschrittene

Einige Schlüssel gibt es nur in der Konfigurationsdatei (Block `source.N.*` mit `type=confluence`):

- `startPoints`, `maxDepth`, `maxResources`: statt aller Spaces nur ab bestimmten Spaces (`space:DEV`) oder Seiten-IDs.
- `searchSpaceKeys`: Suche auf diese Spaces beschränken.
- `includeAttachments` (Standard `false`), `maxAttachmentBytes` (Standard 5 MB für den Index).
- `pageSize` (Standard 50), `connectTimeoutMillis` (15000), `readTimeoutMillis` (60000).
- `allowInsecureHttp=true`: http mit Zugangsdaten zulassen, nur für Testumgebungen.
- `clientCertificate.keyStoreFile` und `clientCertificate.keyStorePasswordRef`: Client-Zertifikat aus einer PKCS12-Datei statt aus dem Windows-Speicher; der Alias ist dann ebenfalls Pflicht, das Passwort liegt in KeePass.
