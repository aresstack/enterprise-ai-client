---
id: knowledge.other-sources
title: Weitere Wissensquellen (Dateien, Mainframe, Outlook, SharePoint, BetaView)
keywords: Lokale Dateien, Ordner, Verzeichnis, Dateisystem, PDF, Word, Excel, Office, Markdown, HTML, Apache Tika, FTP, Mainframe, MVS, z/OS, Dataset, PDS, Member, COBOL, Copybook, JCL, JES, Job, Jobausgabe, SYSPRINT, Spool, NDV, Natural, Natural Development Server, Bibliothek, Outlook, PST, OST, Postfach, E-Mail, Mail, SharePoint, Dokumentbibliothek, Site, NTLM, Kerberos, Windows-Anmeldung, BetaView, Report, Liste, Favorit, Favoriten-ID, Zeichensatz, Encoding, ISO-8859-1, Jetzt indexieren, Vollindexierung, files, mainframe, mailbox, document library
---

# Weitere Wissensquellen (Dateien, Mainframe, Outlook, SharePoint, BetaView)

Neben Confluence und MediaWiki kann der Enterprise AI Client weitere Systeme als Wissensquelle lesen: lokale Ordner, Mainframe-Datasets per FTP, JES-Jobausgaben, Natural-Quellen über NDV, Outlook-Postfachdateien, SharePoint-Bibliotheken und das BetaView-Dokumentarchiv. Dieses Kapitel beschreibt je Typ, was er liest, welche Angaben und welche Anmeldung er braucht und wo seine Grenzen liegen. Es richtet sich an alle, die solche Quellen im Chat nutzen wollen. Hinzufügen, Bearbeiten und Entfernen im Allgemeinen erklärt [Wissensquellen im Überblick](help:knowledge.overview).

## Was alle diese Typen gemeinsam haben

- **Keine eigene Suche, darum zuerst indexieren.** Diese Systeme bieten dem Client keine Volltextsuche an. Der Chat und die Suchleiste finden deshalb nur, was vorher in den lokalen Index gelesen wurde. Nach dem Anlegen steht in der Statuszeile „Noch nicht indexiert“.
- **Vollindexierung auf Knopfdruck.** Der Kreispfeil „Jetzt indexieren“ in der Quellenzeile liest die ganze Quelle im eingestellten Umfang. Fortschritt und Abbruch stehen in der Statuszeile über dem Eingabefeld; Einzelheiten in [Suche, Vorladen und Indexierung](help:knowledge.indexing).
- **Neu indexieren, wenn sich etwas geändert hat.** Ein erneuter Lauf überspringt unveränderte Einträge und entfernt nach einem vollständigen Lauf, was es in der Quelle nicht mehr gibt.
- **Nur lesen.** Der Client schreibt nie etwas in diese Systeme zurück.
- **Lesebereich.** Ein Klick auf die Quellenzeile listet ihre Einträge (höchstens 200); ein Klick auf einen Eintrag zeigt dessen Text. Bilder und Anhänge zeigt der Lesebereich bei diesen Typen nicht.
- **Zugangsdaten.** Wo eine Anmeldung nötig ist, stehen „Zugang“ („KeePass“ oder „Manuell“) und „KeePass-Eintrag“ im Dialog. Bei „KeePass“ kann die Adresse (Host oder URL) aus dem Eintrag kommen; das Feld steht dann unter „Erweitert“ mit dem Zusatz „(sonst aus KeePass)“.
- **Höchstzahlen.** Jeder Typ hat unter „Erweitert“ eine Obergrenze je Lauf. Ist sie erreicht, liest der Lauf nicht weiter; erhöhe sie bei Bedarf.

## Lokale Dateien

**Was es kann:** „Ein Verzeichnis, rekursiv; PDF, Office, Text, Markdown und HTML über Apache Tika“. Gut für Handbücher, Konzepte und Exporte, die auf deinem Rechner oder einem Netzlaufwerk liegen.

**Einrichten:**

1. „+ Quelle“, Typ „Lokale Dateien“.
2. **„Verzeichnis“ ***: „Ordner mit den Dokumenten, z. B. C:\Daten\Handbuch“. Der Knopf „Verzeichnis wählen …“ öffnet die Ordnerauswahl.
3. Unter „Erweitert“: „Startpunkte“ („Kommagetrennte Unterordner (. = alles)“), „Tiefe“ (Vorgabe 20, „Wie viele Verzeichnisebenen durchsucht werden“), „Höchstzahl Dateien (optional)“ (leer = 5000), „Größte Datei in MB (optional)“ (leer = 50).
4. Speichern, dann den Kreispfeil „Jetzt indexieren“.

**Anmeldung:** keine. Der Client liest mit deinen Dateirechten.

**Grenzen:** Versteckte Dateien, Office-Sperrdateien (Name beginnt mit „~$“), Dateien über der Größengrenze und Dateien, deren Typ nicht als Dokument erkannt wird, werden übersprungen. Ob eine Datei geändert wurde, erkennt der Client an Änderungszeit und Größe.

## FTP (Mainframe)

**Was es kann:** „COBOL- und andere Textquellen aus MVS-Datasets (PDS-Member) oder Dateien eines FTP-Servers“. Jedes Member erscheint als Codeblock unter seinem Datasetnamen.

**Einrichten:**

1. „+ Quelle“, Typ „FTP (Mainframe)“.
2. **„Host“ ***: „Name oder Adresse des FTP-Servers, ohne ftp://“, zum Beispiel `mainframe.example.org`.
3. **„KeePass-Eintrag“**: „Eintrag mit Benutzer, Passwort und Adresse des FTP-Servers; leer = anonym“.
4. **„Startpunkte“ ***: „Kommagetrennt: Datasets/PDS wie HLQ.COBOL.SRC oder Member wie HLQ.COBOL.SRC(PGM1)“.
5. Unter „Erweitert“: „Port“ (21), „Tiefe“ (Vorgabe 1; 0 = nur die Member direkt im Startpunkt), „Höchstzahl Member (optional)“ (leer = 2000), „Zeichensatz“ (Vorgabe ISO-8859-1).

**Grenzen:** Die Übertragung läuft als Text. Stimmen Umlaute oder Sonderzeichen nicht, passe den „Zeichensatz“ an. Der Client verwendet klassisches FTP; eine verschlüsselte Variante (FTPS) ist im aktuellen Stand nicht vorgesehen.

## JES-Jobs (Mainframe)

**Was es kann:** „Ausgaben von JCL-Jobs (JCL, Systemmeldungen, SYSPRINT) über die JES-Schnittstelle des z/OS-FTP-Servers“. Jeder Job ist ein Eintrag mit seiner gesamten Ausgabe.

**Einrichten:**

1. „+ Quelle“, Typ „JES-Jobs (Mainframe)“.
2. **„Host“ *** und **„KeePass-Eintrag“ *** (hier Pflicht: „Eintrag mit Benutzer, Passwort und Adresse des FTP-Servers“).
3. Unter „Erweitert“: „Jobnamen“ („Kommagetrennte Filter, z. B. * oder PAY*“), „Besitzer (optional)“ („Jobs dieses Besitzers, * = alle; leer = eigene“), „Status“ („OUTPUT (fertige Jobs), ALL, ACTIVE oder INPUT“, Vorgabe OUTPUT), „Höchstzahl Jobs (optional)“ (leer = 200), „Zeichensatz“, „Port“.

**Grenzen:** Die Ausgabe eines fertigen Jobs ändert sich nicht; ein Job wird deshalb nur einmal gelesen. Jobs, die das System aus dem Spool löscht, verschwinden nach dem nächsten vollständigen Lauf auch aus dem Index.

## Natural (NDV)

**Was es kann:** „Natural-Quellen (Programme, Subprogramme, Copycodes ...) aus Bibliotheken eines NDV-Servers“. Jedes Objekt erscheint als Codeblock unter „BIBLIOTHEK/OBJEKT.Endung“.

**Einrichten:**

1. „+ Quelle“, Typ „Natural (NDV)“.
2. **„Host“ ***: „Name oder Adresse des NDV-Servers“.
3. **„KeePass-Eintrag“ ***: Pflicht, „Eintrag mit Benutzer, Passwort und Adresse des NDV-Servers“.
4. **„Bibliotheken“ ***: „Kommagetrennt, optional mit Filter: MYLIB oder MYLIB/CUST*“.
5. Unter „Erweitert“: „Port“ (Vorgabe 2700), „Höchstzahl Objekte (optional)“ (leer = 5000).

**Anmeldung:** Benutzer und Passwort holt der Client nur beim Verbinden; die Verbindung wird für weitere Zugriffe wiederverwendet.

## Outlook (PST/OST)

**Was es kann:** „E-Mails aus lokalen Outlook-Postfachdateien (.pst, .ost); jede Nachricht ist ein Dokument“. Gelesen werden Betreff, Absender, Empfänger, Datum, Ordner und der Text der Nachricht.

**Einrichten:**

1. „+ Quelle“, Typ „Outlook (PST/OST)“.
2. **„Postfach“ ***: „Ordner mit .pst/.ost-Dateien oder eine einzelne Postfachdatei“. „Verzeichnis wählen …“ wählt einen Ordner; den Pfad einer einzelnen Datei trägst du von Hand ein.
3. Unter „Erweitert“: „Ordner im Postfach“ („Kommagetrennt, z. B. /Posteingang (. = alles)“), „Tiefe“ (Vorgabe 15), „Höchstzahl Nachrichten (optional)“ (leer = 5000).

**Anmeldung:** keine; der Client liest die Datei direkt.

**Grenzen:** Ein Ordner wird nur auf seiner obersten Ebene nach Postfachdateien durchsucht, nicht in Unterordnern. Dateianhänge der E-Mails werden nicht gelesen. Hinweis: Ob sich eine Postfachdatei lesen lässt, während Outlook sie geöffnet hat, ist nicht geprüft.

## SharePoint

**Was es kann:** „Dokumente aus den Bibliotheken einer SharePoint-Site (REST-API); PDF, Office und Text über Apache Tika“.

**Einrichten:**

1. „+ Quelle“, Typ „SharePoint“.
2. **„Site-URL“ ***: „Adresse der SharePoint-Site, z. B. …/sites/team“, etwa `https://sharepoint.example.org/sites/team`. Bei „KeePass“ kann sie aus dem Eintrag kommen.
3. **„KeePass-Eintrag“**: „Eintrag mit Benutzername (DOMÄNE\name) und Passwort; leer = Windows-Anmeldung“.
4. Unter „Erweitert“: „Bibliotheken oder Ordner“ („Kommagetrennt relativ zur Site, z. B. Dokumente/Handbuch (. = alle Bibliotheken)“), „Tiefe“ (Vorgabe 10), „Höchstzahl Dateien (optional)“ (leer = 2000), „Größte Datei in MB (optional)“ (leer = 50).

**Anmeldung:** Mit KeePass-Eintrag sendet der Client Benutzer und Passwort als einfache Anmeldung und vergisst sie danach. Ohne Eintrag entscheidet Java selbst; unter Windows ist das in der Regel die Windows-Anmeldung.

**Grenzen:** SharePoint Online mit moderner Anmeldung funktioniert so nicht. Der Ordner „Forms“ einer Bibliothek, Office-Sperrdateien und nicht erkannte Dateitypen werden übersprungen. Je Site eine Quelle. Hinweis: Der Adapter ist nicht gegen ein echtes SharePoint geprüft; Anmeldung und sehr große Bibliotheken hängen vom Server ab.

## BetaView

**Was es kann:** „Listen und Reports aus dem BetaView-Dokumentarchiv: die Ergebnisliste eines Favoriten“. Der Client lädt jedes Dokument im Originalformat und liest den Text daraus.

**Einrichten:**

1. „+ Quelle“, Typ „BetaView“.
2. **„Adresse“ ***: „Weboberfläche von BetaView, z. B. …/betaview/“.
3. **„KeePass-Eintrag“ ***: Pflicht, „Eintrag mit Benutzername und Passwort für BetaView“.
4. **„Favoriten-ID“ ***: „ID des Favoriten, dessen Ergebnisliste gelesen wird (favoriteID in der Adresse)“. Du findest sie in der Adresszeile des Browsers, wenn du den Favoriten in BetaView öffnest.
5. **„Zeitraum in Tagen“**: „Dokumente der letzten n Tage“, Vorgabe 60.
6. Unter „Erweitert“: die Muster „Extension“, „Form“, „Report“ und „Jobname“ (je „* = alle“), „Höchstzahl Dokumente (optional)“ (leer = 200), „Größtes Dokument in MB (optional)“ (leer = 50).

**Anmeldung:** Der Client meldet sich mit den Daten aus KeePass an und hält die Sitzung im Speicher, bis BetaView sie ablehnt; dann meldet er sich einmal neu an. Zugriffe einer Quelle laufen nacheinander, weil BetaView je Sitzung nur ein geöffnetes Dokument führt. Eine Indexierung kann deshalb dauern.

**Grenzen:** Je Favorit eine Quelle; gespeicherte Auswahlen aus anderen Programmen werden nicht gelesen. Hinweis: Der Adapter ist nicht gegen ein echtes BetaView geprüft.

## Typische Probleme

### Der Chat findet nichts, obwohl die Quelle angehakt ist

Steht in der Statuszeile „Noch nicht indexiert“, klicke den Kreispfeil „Jetzt indexieren“ und warte, bis die Statuszeile über dem Eingabefeld „Wissensbasis: … Seiten …“ meldet. Danach findet der Chat die Inhalte.

### „Indexierung … fehlgeschlagen: …“ in der Quellenzeile

Die Quelle ließ sich gar nicht lesen: Host oder Pfad falsch, Anmeldung abgelehnt, Netz nicht erreichbar. Der Text nennt die Ursache gekürzt, das Protokoll vollständig. Prüfe Adresse, KeePass-Eintrag und Erreichbarkeit.

### „Stand …, n fehlgeschlagen (Protokoll)“

Die Quelle ist lesbar, einzelne Einträge aber nicht, etwa beschädigte oder zu große Dateien oder Member ohne Leserecht. Die Gründe stehen je Eintrag im Protokoll. Der Rest ist im Index.

### Falsche Zeichen bei Mainframe-Texten

Ändere unter „Erweitert“ den „Zeichensatz“ und indexiere neu.

Weitere Prüfschritte: [Fehlersuche bei Wissensquellen](help:troubleshooting.knowledge).
