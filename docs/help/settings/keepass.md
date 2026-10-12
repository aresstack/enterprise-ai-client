---
id: settings.keepass
title: Einstellungen – Reiter „KeePass“
keywords: KeePass, KeePassRPC, KeePass RPC, Passwort, Passwörter, Passwortverwaltung, Pairing, Kopplung, Einmal-Passwort, Einmalcode, Pairing-Schlüssel, Host, Port, 12546, Anzeigename, In KeePass prüfen, Eintrag anlegen, Eintrag bearbeiten, Eintrag löschen, Tresor, Vault, Secrets, password manager, security.keepass
---

# Einstellungen – Reiter „KeePass“

Der Enterprise AI Client speichert keine Passwörter und keinen API-Key in seiner Konfigurationsdatei. Alle Geheimnisse liegen in KeePass 2.x, und der Client holt sie über das Plugin KeePassRPC jeweils nur für den Moment, in dem er sie braucht. Im Reiter „KeePass“ stellst du ein, wie der Client KeePass erreicht und wo er den Schlüssel der Kopplung (Pairing) ablegt. Außerdem enthält der Reiter eine vollständige Passwortverwaltung, mit der du KeePass-Einträge ansehen, anlegen, bearbeiten und löschen kannst, ohne KeePass selbst zu öffnen.

Wie du KeePass zum ersten Mal verbindest, beschreibt [KeePass verbinden](help:getting-started.keepass).

## Bereich „KeePassRPC“

Oben steht der Hinweis: „Secrets stehen nie in der Konfigurationsdatei. API-Key und Zugangsdaten liegen in KeePass 2.x mit dem Plugin KeePassRPC; die Anwendung holt sie je Anfrage aus dem entsperrten Tresor. Beim ersten Zugriff zeigt KeePass ein Einmal-Passwort, das im Pairing-Dialog eingegeben wird.“

### KeePassRPC verwenden

- **Beschriftung:** Häkchen „KeePassRPC verwenden“.
- **Standard:** an.
- **Bedeutung und Wirkung:** Aus bedeutet, dass die Anwendung ohne Secrets startet; jede Anfrage, die einen Key oder ein Passwort braucht, scheitert dann mit einem Authentifizierungsfehler (so steht es auch im Tooltip). Sind trotzdem KeePass-Einträge eingetragen, meldet das Programm beim Laden einen Hinweis, welche Zugriffe scheitern werden. Schalte es nur aus, wenn du wirklich ohne KeePass arbeiten willst, etwa mit Zugang „Manuell“ im Reiter [KI-Dienst](help:settings.ai-service).

### Host

- **Beschriftung:** „Host“.
- **Standard:** `127.0.0.1`.
- **Bedeutung:** Rechner, auf dem KeePassRPC lauscht. KeePassRPC lauscht nur lokal; ändere den Wert nur, wenn du genau weißt, warum.

### Port

- **Beschriftung:** „Port“.
- **Standard:** `12546` (die Voreinstellung des KeePassRPC-Plugins).
- **Erlaubt:** 1 bis 65535.
- **Wirkung:** Muss mit dem Port übereinstimmen, der in den Einstellungen des KeePassRPC-Plugins steht.

### Anzeigename beim Pairing

- **Beschriftung:** „Anzeigename beim Pairing“.
- **Standard:** „Enterprise AI Client“.
- **Bedeutung:** Der Name, den KeePass in seinem Dialog „Authorise a new connection“ zeigt, wenn sich der Client zum ersten Mal meldet. So erkennst du in KeePass, wer da um Zugriff bittet.

### Pairing-Schlüssel

- **Beschriftung:** „Pairing-Schlüssel“ (Tooltip: „Wo der Schlüssel des Pairings abgelegt wird“), Auswahl aus:
  - „Datei im Anwendungsverzeichnis (dauerhaft)“: **Standard.** Der Schlüssel liegt in der Datei `keepassrpc-pairing.key` im Anwendungsverzeichnis, mit Dateirechten nur für dich. Ein erneutes Pairing ist erst nötig, wenn du die Verbindung in KeePass widerrufst.
  - „Nur im Speicher (bei jedem Start neu pairen)“: Der Schlüssel lebt nur, solange das Programm läuft. Nach jedem Start fragt KeePass erneut nach dem Einmal-Passwort.

### In KeePass prüfen

Hinweis im Dialog: „Prüfen verbindet sich mit diesen Einstellungen, pairt bei Bedarf und sucht den Eintrag aus „KI-Dienst → KeePass-Eintrag mit API-Key“. Der Key selbst wird nicht angezeigt.“

Der Knopf „In KeePass prüfen“ verwendet also Host, Port und Ablage aus diesem Reiter, aber den Eintragstitel aus dem Reiter „KI-Dienst“. Während der Prüfung steht „Prüfe … (bei Bedarf öffnet sich der Pairing-Dialog)“. Das Ergebnis ist grün (Eintrag gefunden, Passwortfeld gefüllt) oder rot mit dem Grund. Ein bei der Prüfung entstandener Pairing-Schlüssel wird gemäß deiner Ablage-Einstellung gespeichert und gilt danach auch für den normalen Betrieb.

## Bereich „Passwörter“

Der Bereich „Passwörter“ zeigt alle Einträge aus KeePass in einer Tabelle. Die Überschrift nennt die Anzahl, etwa „Passwörter  (12 Einträge)“. Geladen wird erst, wenn der Bereich zum ersten Mal sichtbar wird; dabei steht kurz „Lade Einträge aus KeePass …“ darunter.

### Spalten der Tabelle

„Netzwerk“ (Intern oder Extern), „Kategorie“, „ID“ (der Titel des Eintrags in KeePass), „Anzeigename“, „Benutzername“, „Passwort“, „URL“, dann fünf Häkchen-Spalten „Login“, „Proxy“, „Auto-Idx“, „PW-Speicher“, „Session“ und ganz rechts eine Spalte mit einem Augen-Symbol. Die Tabelle lässt sich per Klick auf die Spaltenköpfe sortieren.

Passwörter sind zunächst als Punkte verborgen. Ein Klick auf das Auge in einer Zeile liest genau dieses eine Passwort aus KeePass und zeigt es; ein zweiter Klick verbirgt es wieder.

### Knöpfe unter der Tabelle

- **„Alle Passwörter anzeigen“** (Häkchen): liest alle Passwörter einzeln und zeigt sie; abwählen verbirgt sie wieder.
- **„Kopieren“**: kopiert das Passwort des ausgewählten Eintrags in die Zwischenablage.
- **„Neu“**: öffnet „Neuen Eintrag anlegen“.
- **„Bearbeiten“**: öffnet „Eintrag bearbeiten“ für den ausgewählten Eintrag. Ein Doppelklick auf eine Zeile tut dasselbe.
- **„Löschen“**: fragt „Eintrag „…“ wirklich löschen? Diese Aktion kann nicht rückgängig gemacht werden.“ und löscht nach „Löschen“ den Eintrag in KeePass.

Ohne ausgewählte Zeile meldet der Dialog „Bitte zuerst einen Eintrag auswählen.“ Fehler (KeePass aus, Datenbank gesperrt, Zugriff abgelehnt) erscheinen rot unter der Tabelle, etwa „Fehler beim Laden der Passwort-Einträge: …“.

### Der Dialog „Neuen Eintrag anlegen“ bzw. „Eintrag bearbeiten“

| Feld | Bedeutung |
|---|---|
| „Kategorie:“ | Auswahl aus „Mainframe“, „Wiki“, „SP“, „Confluence“, auch frei eintippbar. Vorbelegt mit „Mainframe“ bzw. mit der passenden Kategorie, wenn du über „Neu“ an einem Feld kommst. |
| „Netzwerk:“ | „Extern“ (Standard) oder „Intern“. |
| „ID:“ | Titel des Eintrags in KeePass; muss eindeutig sein und darf nicht leer sein. Beim Bearbeiten nicht änderbar. Genau diesen Titel trägst du in den Einstellungen als KeePass-Eintrag ein. |
| „Anzeigename:“ | Freier Name zur Anzeige. |
| „Benutzername:“ | Benutzername für Quellen mit Anmeldung. |
| „Passwort:“ | Das Geheimnis (beim KI-Dienst: der API-Key). „anzeigen“ blendet es im Klartext ein. |
| „URL:“ | Adresse. Bei „Wiki“, „SP“ und „Confluence“ Pflicht („URL: *“). Beim KI-Dienst steht hier die Basis-URL. |
| „Zertifikat verwenden“ und „Zertifikat:“ | Nur bei Kategorie „Confluence“: Client-Zertifikat aus dem Windows-Zertifikatspeicher, gewählt über „Zertifikat…“. |
| „Optionen:“ | „Login erforderlich“ und „PW speichern“ sind bei neuen Einträgen an, „Proxy verwenden“, „Auto-Index“ und „Session-Cache“ aus. |

Unten stehen „OK“ (schreibt sofort in KeePass, die Meldung „Speichere in KeePass …“ erscheint) und „Abbrechen“; bei „Confluence“ zusätzlich „Verbindung testen“, der die Verbindung mit URL, Benutzer, Passwort und Zertifikat prüft, ohne zu speichern.

Hinweis: Die fünf Optionen stammen aus dem Eintragsformat von MainframeMate und werden als Zusatzfelder im KeePass-Eintrag gespeichert. Welche davon der Enterprise AI Client selbst auswertet, ist nicht für alle Optionen belegt; für den KI-Dienst zählen Passwortfeld und URL.

## Was im Hintergrund passiert

Beim ersten Zugriff baut der Client eine Kopplung mit KeePassRPC auf. KeePass zeigt dazu ein Einmal-Passwort, das du im Pairing-Dialog des Clients eintippst. Danach sind beide Seiten über den Pairing-Schlüssel verbunden. Jede spätere Abfrage nutzt eine eigene kurze, verschlüsselte Verbindung und liest den Eintrag über seinen Titel. Es gibt keinen Zwischenspeicher: jede Chat- und jede Embedding-Anfrage holt den Key neu. Lehnt KeePass den gespeicherten Schlüssel ab (weil du die Verbindung in KeePass widerrufen hast), verwirft der Client ihn und koppelt genau einmal neu.

In der Passwortverwaltung liest der Client Passwörter erst beim Anzeigen oder Kopieren einzeln und überschreibt sie beim Verbergen im Speicher. Wechselst du den Reiter, werden alle angezeigten Passwörter wieder verborgen.

## Weitere Schlüssel für Fortgeschrittene

Einige KeePass-Einstellungen haben kein Feld in diesem Reiter, sondern stehen nur in der Konfigurationsdatei: `security.keepass.origin` (Standard `chrome-extension://enterpriseaiclient`, muss von KeePassRPC akzeptiert werden), `security.keepass.clientId` (Standard `EnterpriseAiClient`) und `security.keepass.pairingKeyFile` (eigener Pfad für die Schlüsseldatei). Zeitlimit und Wiederholungen für KeePass stellst du im Reiter [Netzwerk & Agent](help:settings.network) unter „Zeitlimits und Wiederholungen“ ein. Siehe auch [Die Konfigurationsdatei](help:settings.config-file).

## Grenzen

- Der Client spricht nur KeePass 2.x mit dem Plugin KeePassRPC. Andere Passwortmanager werden nicht unterstützt.
- Der Eintragstitel muss exakt stimmen; der Client sucht nicht über URL oder Benutzername.
- KeePass muss während der Arbeit laufen und entsperrt sein.
- Änderungen in der Passwortverwaltung gehen sofort an KeePass; „Abbrechen“ im Einstellungen-Dialog macht sie nicht rückgängig.

## Typische Probleme

### Die Tabelle bleibt leer und rot steht ein Fehler darunter

KeePass läuft nicht, die Datenbank ist gesperrt, das Plugin KeePassRPC fehlt oder der Port stimmt nicht. Starte und entsperre KeePass, prüfe den Port in den Plugin-Einstellungen und wechsle einmal den Reiter, damit die Liste neu geladen wird. Ist „KeePassRPC verwenden“ aus, meldet die Verwaltung, dass KeePassRPC ausgeschaltet ist.

### Warum will KeePass jedes Mal einen Einmalcode?

Unter „Pairing-Schlüssel“ ist „Nur im Speicher (bei jedem Start neu pairen)“ gewählt, oder die Schlüsseldatei lässt sich nicht schreiben. Wähle „Datei im Anwendungsverzeichnis (dauerhaft)“ und speichere.

### Der Pairing-Dialog erscheint nicht

Prüfe in KeePass, ob ein Dialog „Authorise a new connection“ im Hintergrund wartet. Ohne Bildschirm (headless) gibt es keinen Pairing-Dialog; der Zugriff gilt dann als abgebrochen. Mehr Hilfe: [Probleme mit KeePass](help:troubleshooting.keepass).
