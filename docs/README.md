# Dokumentation

Einstieg für Entwicklerinnen und Entwickler, die den Enterprise AI Client bauen, konfigurieren, starten oder
erweitern wollen. Die Architekturgrundlage bleibt [ARCHITECTURE.md](../ARCHITECTURE.md) im Wurzelverzeichnis;
diese Seiten erklären, ordnen ein und zeigen Beispiele. Bei Widersprüchen gilt der Code auf `main`, danach
ARCHITECTURE.md.

| Seite | Inhalt |
|---|---|
| [Einrichtung](einrichtung.md) | JDK, Build, erster Start, Konfigurationsdatei, Demos ohne Backend, IDE |
| [Modulübersicht](module.md) | Alle Gradle-Module mit Rolle, Paket, Inhalt, Bibliotheken und Testfixtures |
| [Architektur](architektur.md) | Schichten, Abhängigkeitsrichtung, Diagramme, Regeln und wie sie geprüft werden |
| [API-Konfiguration](konfiguration-api.md) | Chat- und Embedding-Endpunkt der Enterprise-API, beobachtetes Serververhalten |
| [KeePass-Konfiguration](konfiguration-keepass.md) | KeePassRPC, Pairing, Secret-Referenzen, Betrieb ohne KeePass |
| [MediaWiki-Konfiguration](konfiguration-mediawiki.md) | Wiki als Wissensquelle: Startseiten, Crawl, Login |
| [Confluence-Konfiguration](konfiguration-confluence.md) | Confluence Data Center als Wissensquelle, Zugangsdaten, mTLS |
| [RAG-Datenfluss](rag-datenfluss.md) | Indexierung, hybride Suche, Kontextaufbau, "Der Index führt" |
| [ACP](acp.md) | Agent Client Protocol: Agent-Modus, Module, Lebenszyklus, Demo-Agent |
| [MCP](mcp.md) | Model Context Protocol: Endpoints, Tokens, Wissenswerkzeuge, Sicherheitsregeln |
| [Tests](tests.md) | Testanleitung: Befehle, Fakes und Fixtures, umgebungsabhängige Tests, CI |
| [Live-Verifikation](live-verifikation.md) | Lauf gegen die echten Dienste in sieben Stufen: Voraussetzungen, Kommandos, Rückmeldung, Ergebnisprotokoll |
| [Herkunft](herkunft.md) | Was aus askai-java8, MainframeMate und corenth übernommen wurde und was neu ist |
| [Einschränkungen](einschraenkungen.md) | Bekannte Grenzen, UNVERIFIED-Punkte und offene Arbeiten |

Konventionen dieser Dokumentation: Prosa auf Deutsch, Bezeichner (Klassen, Schlüssel, Module) auf Englisch
wie im Code. Diagramme sind Mermaid-Blöcke, die GitHub direkt rendert. Beispielwerte verwenden ausschließlich
Platzhalter-Hosts unter `.example`; echte Hostnamen, Tokens und Zugangsdaten gehören weder in die Doku noch
in den Code.
