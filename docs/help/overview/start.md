---
id: overview.start
title: Willkommen im Enterprise AI Client
keywords: Überblick, Einstieg, Hilfe, Handbuch, Inhaltsverzeichnis, was kann das Programm, Funktionen, Start, overview, help, manual
---

# Willkommen im Enterprise AI Client

Der Enterprise AI Client ist ein Desktop-Programm für Gespräche mit einem KI-Modell, das dein eigenes Wissen kennt. Du stellst im Chat eine Frage; das Programm sucht dazu in den Wissensquellen, die du angebunden hast (zum Beispiel Confluence, ein MediaWiki oder einen Ordner mit Dateien), und gibt dem Modell die passenden Textstellen mit. So entstehen Antworten, die sich auf deine Unterlagen stützen und ihre Quellen nennen. Dieses Kapitel ist die Landkarte der eingebauten Hilfe: Es erklärt die wichtigsten Begriffe und sagt, in welchem Kapitel du weiterliest.

## Was das Programm kann

- **Chat mit einem KI-Modell.** Antworten erscheinen Wort für Wort, während das Modell schreibt. Frühere Gespräche bleiben im Verlauf erhalten. Siehe [Der Chat im Überblick](help:chat.overview) und [Chat-Verlauf](help:chat.history).
- **Antworten aus deinen Wissensquellen.** Angehakte Quellen werden bei jeder Frage durchsucht; der Assistent kann außerdem selbst nachsuchen und Seiten lesen. Siehe [Wissensquellen im Überblick](help:knowledge.overview).
- **Dateien im Chat.** Du kannst Dokumente an eine Nachricht hängen und Fragen dazu stellen. Siehe [Dateien im Chat verwenden](help:chat.attachments).
- **Agent-Modus für größere Recherchen.** Ein Recherche-Agent plant, sammelt Quellen, baut eine Gliederung und schreibt ein Dokument. Siehe [Agent-Modus](help:agent.overview).
- **Vorlesen und Diktieren.** Antworten lassen sich vorlesen; Fragen kannst du, je nach Einrichtung, auch sprechen. Siehe [Antworten vorlesen](help:chat.read-aloud) und [Spracheingabe](help:chat.dictation).
- **Export.** Ein Gespräch lässt sich als Datei speichern, auf Wunsch mit geschwärzten Angaben. Siehe [Chat exportieren](help:chat.export).
- **Diese Hilfe.** Der Assistent kann in diesem Handbuch nachschlagen, wenn du ihn nach dem Programm fragst. Siehe [Die eingebaute Programmhilfe](help:chat.app-help).

## Die wichtigsten Begriffe

**KI-Dienst.** Der Server, der das Sprachmodell bereitstellt. Der Client spricht ihn über eine OpenAI-kompatible Schnittstelle an. Adresse und Zugang trägst du im Einrichtungsassistenten oder unter Einstellungen → KI-Dienst ein. Siehe [KI-Dienst einstellen](help:settings.ai-service).

**Modelle.** Das Programm benutzt bis zu vier Arten von Modellen: ein Chat-Modell (schreibt die Antworten), ein Embedding-Modell (macht Texte für die Bedeutungssuche vergleichbar), ein Modell für die Sprachausgabe und eines für die Spracheingabe. Siehe [Modelle auswählen](help:settings.models) und [Lokal oder beim KI-Dienst](help:concepts.local-vs-cloud).

**Wissensquelle.** Ein angebundener Bestand an Dokumenten, etwa ein Confluence-Bereich. Wissensquellen verwaltest du im Seitenbereich im Reiter „Wissensquellen“. Nur angehakte Quellen werden durchsucht.

**Index.** Das lokale Verzeichnis, in dem das Programm gelesene Dokumente in Abschnitte zerlegt und durchsuchbar ablegt. Es verbindet klassische Volltextsuche mit Bedeutungssuche. Siehe [Wie der Wissensindex funktioniert](help:concepts.knowledge-index).

**KeePass.** Passwörter und API-Schlüssel speichert das Programm nicht selbst, sondern liest sie aus deiner KeePass-Datenbank. Siehe [KeePass verbinden](help:getting-started.keepass) und [Sicherheit](help:concepts.security).

**Merlin und „Merlins Tochter“.** Merlin ist der Zauberer, der dich durch den Einrichtungsassistenten begleitet; du findest ihn später unter Einstellungen → Technische Details wieder. „Merlins Tochter“ ist der voreingestellte Anzeigename des Chat-Assistenten. Den Namen kannst du ändern; er ist nur eine Beschriftung. Siehe [Einrichtungsassistent](help:getting-started.setup).

## Wo du anfängst

1. Beim ersten Start führt dich der Einrichtungsassistent durch KeePass, KI-Dienst, Modelle und auf Wunsch Confluence: [Einrichtungsassistent](help:getting-started.setup).
2. Danach stellst du deine erste Frage: [Die erste Frage](help:getting-started.first-chat).
3. Wissensquellen fügst du hinzu, wann immer du willst: [Wissensquellen im Überblick](help:knowledge.overview).

## Die Hilfe nach Bereichen

- **Erste Schritte:** [Einrichtungsassistent](help:getting-started.setup), [KeePass verbinden](help:getting-started.keepass), [KI-Dienst](help:getting-started.ai-service), [Modelle](help:getting-started.models), [Confluence einrichten](help:getting-started.confluence), [Die erste Frage](help:getting-started.first-chat).
- **Chat:** [Überblick](help:chat.overview), [Verlauf](help:chat.history), [Dateianhänge](help:chat.attachments), [Quellenangaben](help:chat.citations), [Zitieren und nachfragen](help:chat.quotes), [Vorlesen](help:chat.read-aloud), [Spracheingabe](help:chat.dictation), [Export](help:chat.export), [Programmhilfe](help:chat.app-help).
- **Wissensquellen:** [Überblick](help:knowledge.overview), [Confluence](help:knowledge.confluence), [MediaWiki](help:knowledge.mediawiki), [Weitere Quellen](help:knowledge.other-sources), [Indexierung](help:knowledge.indexing), [Lesebereich](help:knowledge.reader), [Favoriten](help:knowledge.favorites), [Anhänge in Quellen](help:knowledge.attachments), [Einmalcode](help:knowledge.otp).
- **Agent:** [Agent-Modus](help:agent.overview), [Werkzeuge](help:agent.tools), [Recherche-Ablauf](help:agent.research).
- **Einstellungen:** [Überblick](help:settings.overview) und je ein Kapitel pro Reiter, dazu [Die Konfigurationsdatei](help:settings.config-file).
- **Fehlerbehebung:** [Vorgehen bei Problemen](help:troubleshooting.overview) und Kapitel zu Einrichtung, KeePass, Netzwerk, Modellen, Chat, Wissensquellen, Confluence, Sprache und Agent.
- **Hintergrund:** [Lokal oder beim KI-Dienst](help:concepts.local-vs-cloud), [Wissensindex](help:concepts.knowledge-index), [Datenschutz](help:concepts.privacy), [Sicherheit](help:concepts.security), [Bekannte Grenzen](help:concepts.limitations).

## Für welche Version diese Hilfe gilt

Die Hilfe wird mit dem Programm ausgeliefert und beschreibt genau die installierte Version. Fragst du den Assistenten nach einer Einstellung, liest er dieses Handbuch und nicht eine Online-Anleitung für eine andere Version. Hinweise wie „seit 0.46.0“ in einzelnen Kapiteln nennen die Version, ab der eine Funktion so funktioniert.
