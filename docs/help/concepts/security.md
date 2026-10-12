---
id: concepts.security
title: Sicherheit
keywords: Sicherheit, security, Secrets, Passwörter, API-Key, KeePass, KeePassRPC, Pairing, Zugangsdaten, Konfigurationsdatei, keine Passwörter, Manuell, MCP, 127.0.0.1, localhost, Token, Werkzeuge nur lesend, Agentenprozess, Agent-Kommando, Proxy-Passwort, Client-Zertifikat, TLS, Einmalcode, Schwärzung, sicher
---

# Sicherheit

Dieses Kapitel erklärt, wie der Enterprise AI Client mit Zugangsdaten umgeht, warum in der Konfigurationsdatei keine Passwörter stehen, wie der Werkzeug-Endpunkt für Agenten abgesichert ist, was die Werkzeuge dürfen und was nicht, und welche Rolle der Agentenprozess spielt. Es richtet sich an Benutzer, die wissen wollen, worauf sie sich verlassen können, und an Administratoren, die die Anwendung bewerten. Welche Inhalte an den KI-Dienst gehen, steht in [Datenschutz: welche Daten wohin gehen](help:concepts.privacy).

## Zugangsdaten liegen in KeePass

Alle Geheimnisse, also der API-Key des KI-Dienstes, Passwörter für Wissensquellen, das Proxy-Passwort und das Passwort eines Client-Zertifikats, liegen in deiner **KeePass**-Datenbank. Die Anwendung holt sie über das KeePassRPC-Plugin, und zwar bei jedem Gebrauch neu:

- In der Konfigurationsdatei steht nur der **Titel des KeePass-Eintrags**, zum Beispiel `chat.apiKeyRef=keepass:Enterprise AI API`. Der Titel ist kein Geheimnis.
- Die Anwendung hält Zugangsdaten nur für die Dauer eines Aufrufs im Speicher und legt keinen Zwischenspeicher dafür an. Jede Anfrage fragt KeePass erneut.
- Beim ersten Zugriff koppelt sich die Anwendung mit KeePass (Pairing): KeePass zeigt einen Code, den du im Dialog der Anwendung eingibst. Den dabei entstehenden Kopplungsschlüssel legt die Anwendung standardmäßig in einer Datei ab, die nur für dich lesbar ist (`security.keepass.pairingKeyStore=file`). Mit `memory` wird nach jedem Start neu gekoppelt.
- KeePassRPC wird standardmäßig unter `127.0.0.1` angesprochen, also nur auf deinem Rechner.

Einrichtung und Bedienung: [KeePass einrichten](help:getting-started.keepass) und [Einstellungen: KeePass](help:settings.keepass).

### Zugang „Manuell“

Statt KeePass kannst du im Reiter „KI-Dienst“ und bei Wissensquellen den Zugang „Manuell“ wählen. Dann fragt die Anwendung API-Key bzw. Benutzer und Passwort beim ersten Gebrauch je Sitzung ab und hält die Antwort **nur im Arbeitsspeicher**. Gespeichert wird nichts davon; nach einem Neustart fragt sie erneut.

## Keine Passwörter in der Konfigurationsdatei

Die Konfigurationsdatei (`enterprise-ai-client.properties`) ist bewusst frei von Geheimnissen:

- Sie enthält Adressen, Modellnamen, Einstellungen und Verweise auf KeePass-Einträge, aber keine Passwörter, Tokens oder API-Keys.
- Die Anwendung lehnt Adressen ab, in denen Zugangsdaten stecken (etwa `https://benutzer:passwort@wiki.example.org`): Der Start meldet dann „darf keine Zugangsdaten enthalten (SecretRef verwenden)“.
- Der Einstellungen-Dialog schreibt nie ein Passwort oder einen API-Key in die Datei. Wo er ein Passwort entgegennimmt, etwa beim Anlegen eines Eintrags über den Knopf „Neu“ (mit Pluszeichen) neben der Eintragsauswahl, speichert er es direkt in KeePass; in der Datei landet nur der Titel des Eintrags.
- Ist KeePass ausgeschaltet (`security.keepass.enabled=false`), scheitern alle Zugriffe auf Geheimnisse mit einer verständlichen Meldung, statt auf einen unsicheren Weg auszuweichen.

Die Datei darfst du also weitergeben oder sichern, ohne Passwörter preiszugeben. Sie enthält aber Adressen deiner internen Systeme. Mehr: [Die Konfigurationsdatei](help:settings.config-file).

## Werkzeug-Endpunkt nur lokal

Agenten erreichen die Werkzeuge der Anwendung über einen kleinen Server für das Model Context Protocol (MCP). Er ist so abgesichert:

- Er lauscht **ausschließlich auf 127.0.0.1** auf einem freien Port. Andere Rechner im Netz erreichen ihn nicht.
- Jeder Agentenprozess bekommt einen **eigenen Endpunkt** mit einem zufälligen, nicht erratbaren Zugangsschlüssel (192 Bit) im Pfad der Adresse. Ein falscher Schlüssel trifft keinen Endpunkt.
- Adresse und Schlüssel bekommt der Agent beim Start über Umgebungsvariablen, nie als Text im Gespräch. Sie gelten als Geheimnis und erscheinen weder im Protokoll noch im Chat-Verlauf, im Index oder in Fehlermeldungen.
- Endet die Agentensitzung („+ Neuer Chat“, anderer Chat, Programmende), wird der Endpunkt abgemeldet und der Schlüssel damit ungültig.
- Fehler eines Werkzeugs erreichen den Agenten nur als knappe Meldung, ohne technische Einzelheiten wie Pfade.

Der normale Chat braucht diesen Server nicht; er ruft die Werkzeuge direkt in der Anwendung auf.

## Was die Werkzeuge dürfen

Die Werkzeuge sind so gebaut, dass Chat und Agent **nichts in deinen Systemen verändern**:

- `knowledge_search`, `knowledge_read` und `knowledge_attachments` lesen nur aus den Wissensquellen und dem lokalen Index. Es gibt kein Werkzeug, das Seiten in Confluence, im Wiki, auf SharePoint oder in Dateien anlegt, ändert oder löscht.
- Als Nebenwirkung legt die Anwendung gelesene Seiten im **lokalen** Index ab; das ändert nichts an der Quelle.
- `read_attachment` und `search_attachment` lesen nur die Kopien der Dateien, die du dem Chat angehängt hast.
- Die Hilfe-Werkzeuge `app_help_search` und `app_help_read` lesen nur die mitgelieferte Programmhilfe.
- Die Steuerwerkzeuge des Recherche-Agenten schreiben nur in das **Research-Projekt** des Chats auf deinem Rechner (Konzept, Quellenverweise, Gliederung, Dokument). Jedes prüft beim Aufruf, ob die aktuelle Phase das erlaubt.
- Es gibt keine Werkzeuge, die Programme ausführen, beliebige Dateien lesen oder schreiben oder Netzadressen aufrufen.
- Abgewählte Wissensquellen werden von `knowledge_search` nicht durchsucht.

Mit dem Reiter „Tools“ schaltest du einzelne Werkzeuge zusätzlich ab, siehe [Werkzeuge von Chat und Agent](help:agent.tools).

## Der Agentenprozess

### Eingebauter Recherche-Agent

- Er läuft als **eigener Prozess** mit derselben Java-Laufzeit und aus demselben Programmpaket wie die Anwendung; es wird kein fremdes Programm gestartet.
- Er bekommt **keine Zugangsdaten**: kein API-Key, keine Passwörter. Modellanfragen stellt die Anwendung für ihn (Werkzeug `main_model_complete`), mit ihren eigenen Netz- und KeePass-Einstellungen.
- Er spricht mit der Anwendung nur über die Standard-Ein- und -Ausgabe (Steuerung) und über seinen lokalen Werkzeug-Endpunkt. Seine Arbeitszeilen gehen ins Protokoll der Anwendung.

### Eigenes Agent-Kommando

Mit Einstellungen → „Netzwerk & Agent“ → „Agent-Modus aktivieren“ startest du stattdessen ein Programm deiner Wahl. Beachte:

- Das Programm läuft mit **deinen Benutzerrechten**. Die Anwendung schränkt es nicht weiter ein. Was es außerhalb der Werkzeuge tut (Dateien lesen, ins Netz gehen), liegt allein an dem Programm.
- Trage nur Programme ein, denen du vertraust und deren Herkunft du kennst.
- Es bekommt die Adresse seines Werkzeug-Endpunkts samt Schlüssel und nur die Werkzeuge des Kanals „Agent“ (die Wissenswerkzeuge), keine Zugangsdaten der Anwendung.
- Fehlermeldungen des Agenten zeigt die Anwendung bewusst nur allgemein an („Der Agent konnte nicht gestartet werden.“ usw.), weil sie Pfade oder Umgebungswerte enthalten könnten.

## Netzwerk und Zertifikate

- Verbindungen zum KI-Dienst und zu Wissensquellen prüfen die Zertifikatskette. Vertrauensquellen sind wählbar (Java-Zertifikatsspeicher, Windows-Zertifikatsspeicher, eigene CA-Datei), siehe [Netzwerk](help:settings.network).
- Kann die Proxy-Ermittlung nicht arbeiten, meldet die Anwendung das, statt still eine Direktverbindung zu versuchen.
- Zugangsdaten über unverschlüsseltes `http` sind bei Confluence nur mit ausdrücklicher Entwicklungsoption erlaubt.

## Einmalcodes und Exporte

Fordert eine Wissensquelle beim Öffnen einer Seite oder eines Anhangs einen Einmalcode an, gibst du ihn im Lesebereich ein; siehe [Einmalcode](help:knowledge.otp). Beim Export eines Chats schwärzt die Anwendung API-Keys, Tokens, Cookies, Passwörter und Einmalcodes, siehe [Datenschutz](help:concepts.privacy).

## Grenzen

- Der API-Key liegt während einer Anfrage als Zeichenkette im Arbeitsspeicher, bis die Java-Speicherbereinigung ihn entfernt.
- Der Index und die gespeicherten Chats sind nicht verschlüsselt. Sie liegen in deinem Benutzerverzeichnis und sind so geschützt wie dieses.
- Der Handschlag mit einem echten KeePassRPC-Plugin ist in den automatischen Tests nur gegen eine Nachbildung geprüft. Hinweis: Das Verhalten hängt von der Installation ab und ist nicht für alle Installationen geprüft.
- Weitere Punkte: [Bekannte Einschränkungen](help:concepts.limitations).

## Typische Fragen

### Warum fragt die Anwendung beim Start nach einem KeePass-Code?

Das ist das Pairing mit KeePassRPC. Es ist einmalig, solange der Kopplungsschlüssel in der Datei bleibt. Siehe [Probleme mit KeePass](help:troubleshooting.keepass).

### Kann ein Agent meine Confluence-Seiten ändern?

Nein. Kein Werkzeug der Anwendung schreibt in eine Wissensquelle. Ein eigenes Agent-Kommando ist aber ein eigenständiges Programm; was es ohne die Werkzeuge der Anwendung tut, kontrolliert die Anwendung nicht.

## Verwandte Kapitel

- [Datenschutz: welche Daten wohin gehen](help:concepts.privacy)
- [KeePass einrichten](help:getting-started.keepass)
- [Werkzeuge von Chat und Agent](help:agent.tools)
- [Agent-Modus im Überblick](help:agent.overview)
