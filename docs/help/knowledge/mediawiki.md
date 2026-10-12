---
id: knowledge.mediawiki
title: MediaWiki als Wissensquelle
keywords: MediaWiki, Wiki, Intranet-Wiki, api.php, API-URL, Startpunkte, Startseite, Seitentitel, Tiefe, Links, Linkebenen, Höchstzahl Seiten, Anmeldung erforderlich, requiresLogin, Login, Bot-Passwort, clientlogin, anonym, KeePass, Anzeigename, displayName, Namensraum, linkNamespaces, Weiterleitung, Volltextsuche, Suche, Wikipedia, wiki source, login, start pages
---

# MediaWiki als Wissensquelle

Dieses Kapitel erklärt, wie du ein MediaWiki, etwa ein Intranet-Wiki, als Wissensquelle anbindest: welche Angaben der Quellen-Dialog braucht, wie die Anmeldung funktioniert, wie der Chat im Wiki sucht und welche Grenzen es gibt. Es richtet sich an alle, die Inhalte eines Wikis im Chat verwenden möchten. Die allgemeine Bedienung des Reiters „Wissensquellen“ steht in [Wissensquellen im Überblick](help:knowledge.overview).

## Was die MediaWiki-Quelle kann

- **Live-Suche im Wiki**: Bei einer Frage im Chat oder über „Quellen durchsuchen…“ fragt der Client die Volltextsuche des Wikis selbst. Die Treffer bringen einen kurzen Auszug mit.
- **Seiten lesen**: Der Client holt die gerenderte Seite und wandelt sie in Text mit Überschriften um. Weiterleitungen löst er auf; eine Weiterleitungsseite erscheint unter dem Titel ihrer Zielseite.
- **Index wächst mit der Nutzung**: Jede gefundene und gelesene Seite landet im lokalen Index. Eine Vollindexierung auf Knopfdruck gibt es für MediaWiki nicht; der Kreispfeil „Jetzt indexieren“ bleibt grau. Die Statuszeile zeigt zu Beginn „Wird bei der Suche live gefragt; Treffer kommen in den Index“.
- **Übersicht im Lesebereich**: Ein Klick auf die Quellenzeile listet die Seiten ab deinen Startpunkten, entlang der Wiki-Links bis zur eingestellten Tiefe (höchstens 200 Einträge in der Übersicht).

Der Client liest nur; er bearbeitet keine Wiki-Seiten.

## Voraussetzungen

- Die Adresse der Wiki-Schnittstelle `api.php`, zum Beispiel `https://wiki.example.org/w/api.php`. Die Basisadresse des Wikis genügt meist auch; der Client leitet die `api.php` daraus ab.
- Für ein geschütztes Wiki ein KeePass-Eintrag mit Benutzername, Passwort und URL. Viele Wikis erlauben die Anmeldung über die Schnittstelle nur mit einem Bot-Passwort; frage im Zweifel den Wiki-Betrieb.
- Ein oder mehrere Seitentitel als Startpunkte, zum Beispiel „Hauptseite“ oder „Handbuch“.

## Einrichten

1. Öffne den Drawer, Reiter „Wissensquellen“, und klicke „+ Quelle“.
2. Wähle bei „Typ“ den Eintrag „MediaWiki“. Die Beschreibung lautet „Seiten einer MediaWiki-Site über die api.php, ab Startseiten entlang der Wiki-Links“.
3. **„Zugang“**: „KeePass“ oder „Manuell“.
4. **„KeePass-Eintrag“** (bei „KeePass“): Hinweis „Eintrag mit Benutzername, Passwort und URL des Wikis; leer = anonym“. Für ein öffentliches Wiki lässt du ihn leer.
5. **„API-URL“**: Hinweis „Die api.php der MediaWiki-Installation, z. B. …/w/api.php“. Bei „KeePass“ steht das Feld unter „Erweitert“ als „API-URL (sonst aus KeePass)“; leer gilt die URL aus dem Eintrag.
6. **„Startpunkte“ ***: „Kommagetrennte Seitentitel“, etwa `Hauptseite, Handbuch`.
7. Unter „Erweitert“ bei Bedarf:
   - **„Tiefe“** (Vorgabe 1): „Wie viele Linkebenen ab den Startpunkten verfolgt werden (0 = nur Startpunkte)“.
   - **„Höchstzahl Seiten (optional)“**: „Obergrenze je Lauf; leer = Standard“ (Standard 500).
   - **„Anmeldung erforderlich“**: „Vor dem Lesen anmelden (Standard: ja, wenn ein KeePass-Eintrag gesetzt ist)“.
   - **„Anzeigename (optional)“**: „Name der Quelle in Quellenangaben“.
8. Klicke „Speichern“. Die Quelle erscheint sofort angehakt in der Liste.

## Anmeldung

Ist „Anmeldung erforderlich“ aktiv (Standard bei gesetztem KeePass-Eintrag), meldet sich der Client vor dem ersten Lesen an:

1. Er holt Benutzername und Passwort erst in diesem Moment aus KeePass (bei „Manuell“ aus dem Abfragedialog).
2. Er meldet sich über die Wiki-Schnittstelle an; klappt das klassische Verfahren nicht, versucht er die zweite Anmeldeart des Wikis.
3. Danach löscht er die Zugangsdaten aus dem Speicher. Die Wiki-Sitzung (Cookies) hält er nur im Speicher, je Wiki getrennt.
4. Läuft die Sitzung ab und lehnt das Wiki einen Abruf ab, meldet er sich genau einmal neu an.

Gibt es keine Zugangsdaten, liest der Client anonym weiter; verweigert das Wiki dann den Zugriff, meldet die Quelle „Zugriff verweigert“.

Hinweis: Single Sign-on und besondere Anmeldeverfahren einzelner Wikis sind nicht geprüft. Das Verhalten hängt vom Server ab.

## Was im Hintergrund passiert

- Suche: Im Chat holt die Live-Suche je angehakter Quelle bis zu zehn Treffer (einstellbar unter Einstellungen → Index, Feld „Live-Suche: Treffer je Quelle“); die Suchleiste des Reiters „Wissensquellen“ zeigt ebenfalls bis zu zehn Treffer je Quelle. Der Client lädt die Treffer und legt sie sofort in den Volltext-Rohindex; Passagen und Embeddings entstehen danach im Hintergrund. Einzelheiten: [Suche, Vorladen und Indexierung](help:knowledge.indexing).
- Revision: Für jede Seite merkt sich der Client Änderungszeit und Revisionsnummer. Eine unveränderte Seite muss nicht neu aufbereitet werden.
- Ressourcenkennung: Jede Seite bekommt eine feste Kennung aus Wiki-Adresse und Seitentitel. Damit erkennen Favoriten und Quellenangaben die Seite wieder.

## Grenzen

- **Keine Bilder und Anhänge**: Der Lesebereich zeigt Wiki-Seiten als Text mit Überschriften, ohne Bilder. Dateien und Bilder des Wikis werden nicht indexiert.
- **Keine Vollindexierung**: Was nie gesucht oder gelesen wurde, steht nicht im Index. Für die Antworten im Chat ist das meist kein Nachteil, weil die Live-Suche bei jeder Frage läuft.
- **Umzug des Wikis**: Ändert sich die API-URL, ist das eine neue Quelle mit neuer Identität; die alten Indexeinträge werden entfernt, und Favoriten auf die alte Adresse finden ihre Quelle nicht mehr.
- **Nicht gegen jedes Wiki geprüft**: Der Adapter ist mit Testservern geprüft; Anmeldevarianten, Single Sign-on und Proxy-Konstellationen einzelner Firmen-Wikis nicht.

## Typische Probleme

### Die Suche meldet „Zugriff verweigert“

1. Ist ein KeePass-Eintrag gewählt und stimmt das Passwort? Melde dich testweise im Browser an.
2. Verlangt das Wiki ein Bot-Passwort für die Anmeldung über die Schnittstelle? Dann gehört das Bot-Passwort samt Benutzername (etwa „Name@Bot“) in den KeePass-Eintrag.
3. Ist „Anmeldung erforderlich“ unter „Erweitert“ abgeschaltet, obwohl das Wiki nur angemeldet lesbar ist?

### „nicht erreichbar“ oder „unerwartete Antwort“

„nicht erreichbar“ bedeutet Netzwerk, Zeitüberschreitung oder Serverfehler; prüfe Proxy und VPN ([Fehlersuche Netzwerk](help:troubleshooting.network)). „unerwartete Antwort“ bedeutet meist eine falsche API-URL (eine normale Webseite statt der `api.php`) oder eine vorgeschaltete Anmeldeseite.

### Beim Speichern: „MediaWiki-Einstellung ungültig (Adresse)“

Die API-URL ist keine gültige http- oder https-Adresse, oder sie enthält Anfrageparameter oder Benutzerangaben. Gib sie ohne `?…` und ohne `#…` an.

### Die Übersicht im Lesebereich ist leer oder zu klein

Prüfe die Startpunkte (genaue Seitentitel, Groß- und Kleinschreibung wie im Wiki) und die „Tiefe“. Bei Tiefe 0 erscheinen nur die Startseiten selbst.

Weitere Prüfschritte stehen in [Fehlersuche bei Wissensquellen](help:troubleshooting.knowledge).

## Für Fortgeschrittene

Nur in der Konfigurationsdatei (Block mit `type=mediawiki`) stehen:

- `linkNamespaces`: Namensräume, deren Links beim Auflisten verfolgt werden, kommagetrennt (`0` Artikel, `4` Projekt). Nur ganze Zahlen sind erlaubt, sonst meldet die Prüfung „Namensräume müssen ganze Zahlen sein“.
- `connectTimeoutMillis` (Standard 15000) und `readTimeoutMillis` (Standard 30000).
- `userAgent`: eigene Kennung gegenüber dem Wiki.
