# Metadaten-Katalog (Facts an Ressourcen)

Issue #106. Der Katalog speichert beliebig viele strukturierte Befunde ("Facts") zu einer Wissensressource,
quellenübergreifend, und beantwortet Abfragen nach Ressource und nach Typ ohne den Lucene-Index. Er ist die
Datengrundlage für spätere Fact Sheets, Glossare, Suchprojektionen und einen Knowledge Graph; Extraktion, Graph
und Oberfläche gehören nicht dazu.

## Module

| Modul | Inhalt |
|---|---|
| `fact-api` (Port) | `FactStorePort` und die Modelltypen; keine Speichertypen |
| `fact-h2` (Adapter) | `H2FactStore`: eingebettete H2-Datenbank, einziges Modul mit H2/JDBC |

Der Kompositionspunkt baut genau einen `H2FactStore` und reicht ihn als `ApplicationPorts.facts()` weiter. Die
Datenbank wird beim ersten Aufruf geöffnet und migriert und beim Beenden mit `SHUTDOWN` geschlossen. Bisher ruft
kein Use Case und keine Oberfläche den Katalog; ein späterer Fact-Sheet-Bereich liest ausschließlich über den Port.

## Schema

| Tabelle | Schlüssel | Inhalt |
|---|---|---|
| `RESOURCE_REF` | `RESOURCE_ID` = `KnowledgeResourceId.value()` | optional `SOURCE_ADDRESS`; nur Referenz für die Integrität, kein zweites Bookmark-System |
| `FACT_TYPE` | `TYPE_ID` (z. B. `ACRONYM`, `BUSINESS_RULE`) | Beschreibung, aktuelle `SCHEMA_VERSION`, optionales JSON-Schema (gespeichert, nicht durchgesetzt) |
| `METADATA_FACT` | `FACT_ID` (UUID) | FK `RESOURCE_ID`, FK `TYPE_ID`, `SCHEMA_VERSION`, `PAYLOAD` (JSON), optional `FACT_ROLE`, Herkunft, Prüfstatus, optionaler Anker, Zeitstempel |
| `FACT_STORE_SCHEMA` | `VERSION` | angewandte Migrationen |

Indizes: `IDX_FACT_RESOURCE_TYPE (RESOURCE_ID, TYPE_ID)` für das Fact Sheet einer Ressource und
`IDX_FACT_TYPE_RESOURCE (TYPE_ID, RESOURCE_ID)` für "alle Facts eines Typs" und "alle Ressourcen mit Facts eines
Typs" (`H2FactStoreTest.bothAccessPathsUseTheirCompositeIndex` prüft die Pläne mit `EXPLAIN`). Ein neuer Typ braucht
keine Tabelle und keinen Index.

Revision, Content-Hash und URL sind nie Teil des Ressourcenschlüssels. Ein Fact merkt sich in seiner Herkunft, gegen
welchen Stand er ermittelt wurde (`SOURCE_VERSION`, `SOURCE_MODIFIED_AT`, `CONTENT_HASH`); `MetadataFact.freshness`
vergleicht mit dem aktuellen Stand und liefert `CURRENT`, `STALE` oder `UNKNOWN`. Ein veralteter Fact bleibt an
seiner Ressource; sein Anker gilt nur, solange er `CURRENT` ist. Der Anker (Abschnitt, Strukturpfad, Zeilen- und
Zeichenbereich, kurzes Zitat bis 1000 Zeichen, Fingerprint) ist vom Bookmark getrennt und optional. Den
Dokumenttext speichert der Katalog nicht.

Typen: dieselbe Version mit demselben Schema ist ein No-op (die Beschreibung darf sich ändern), eine höhere Version
ersetzt die Definition, eine niedrigere Version oder ein anderes Schema unter derselben Version wird mit
`INCOMPATIBLE_SCHEMA` abgelehnt. Facts älterer Schema-Versionen bleiben lesbar; ein Fact darf keine höhere Version als
die registrierte tragen.

Migrationen stehen in `FactSchema.MIGRATIONS`; neue Versionen kommen ans Ende, angewandte werden nie geändert. Jede
Anweisung ist idempotent, weil H2 DDL sofort festschreibt. Eine Datenbank einer neueren Anwendungsversion wird nicht
angefasst, sondern mit `STORAGE` abgelehnt.

## Abfragen

| Port-Methode | Zugriffspfad |
|---|---|
| `factsOf(resource)`, `factsOf(resource, type)` | `IDX_FACT_RESOURCE_TYPE` |
| `factsOfType(type)`, `resourcesWithType(type)` | `IDX_FACT_TYPE_RESOURCE` |
| `fact(id)`, `save`, `removeFact` | Primärschlüssel |
| `registerType`, `type`, `types` | `FACT_TYPE` |

Felder im JSON-Payload sind nicht indiziert. Fragen wie "alle Ressourcen mit Akronym X" brauchen eine eigene,
normalisierte Suchprojektion (z. B. `FACT_SEARCH_TERM`); sie ist ein späterer Ausbau.

## Schreiben, Ersetzen, Löschen

- Jede Port-Methode ist eine kurze Transaktion auf einer eigenen JDBC-Verbindung. Keine Transaktion bleibt über
  Modell-, Netz- oder Extraktionsarbeit offen; Aufrufer rechnen erst und schreiben dann.
- Ein Fact braucht eine registrierte Ressource (`registerResource`) und einen registrierten Typ; sonst
  `UNKNOWN_RESOURCE` bzw. `UNKNOWN_TYPE`. Ungültiges JSON oder ein zu großer Payload ergibt `INVALID_PAYLOAD`.
- `replaceAutomaticFacts(resource, extractorId, facts)` ersetzt atomar die ungeprüften automatischen Facts eines
  Extractors an einer Ressource. Manuelle Facts, geprüfte Facts (`CONFIRMED`, `REJECTED`) und Facts anderer
  Extractoren bleiben. Scheitert ein neuer Fact, ändert sich nichts.
- Löschen geschieht nur ausdrücklich: `removeFact`, `removeResource` (Ressource mit allen Facts, auch manuellen) und
  `removeSource` (alle mit dieser `SourceAddress` registrierten Ressourcen). Die Fremdschlüssel sind `RESTRICT`,
  nichts kaskadiert. Das Entfernen einer Quelle aus dem Wissensindex (`IndexKnowledgeUseCase.removeSource`) lässt den
  Katalog bewusst unberührt, damit manuelle Arbeit nicht nebenbei verschwindet.

## Nebenläufigkeit

H2 2.x (MVStore) liest auf einem Snapshot und sperrt beim Schreiben Zeilen. Leser warten nie auf Schreiber. Ein
Schreiber wartet höchstens `facts.lockTimeoutMillis` auf eine von einem anderen Schreiber gesperrte Zeile und
scheitert dann mit `LOCK_TIMEOUT`. Verbindungen werden nie zwischen Threads geteilt. Die Methoden blockieren; sie
gehören nicht auf den Swing-EDT.

## Konfiguration

| Schlüssel | Standard | Wirkung |
|---|---|---|
| `facts.directory` | `<Anwendungsverzeichnis>/facts` | Verzeichnis der Datenbank `facts.mv.db`; gilt ab dem nächsten Start |
| `facts.lockTimeoutMillis` | `10000` | Wartezeit eines Schreibers auf eine gesperrte Zeile (0 bis 3600000) |

## Laufzeit und Grenzen

- H2 2.2.224 ist die letzte Linie für Java 8 (2.3.x verlangt Java 11). Eingebettet im Prozess, kein Server, kein
  TCP-Port, keine H2-Konsole. Bekannte Meldungen zu H2 betreffen die Web-Konsole und den TCP-Server; beide werden
  nicht gestartet.
- Nur ein Prozess kann die Datei öffnen (H2-Dateisperre). Eine zweite Instanz der Anwendung erhält beim ersten Zugriff
  `STORAGE`.
- Das JSON-Schema eines Typs wird gespeichert, aber nicht gegen Payloads geprüft.

## Vertraulichkeit

Der Katalog liegt lokal im Anwendungsverzeichnis des Benutzers, wie Index und Vorlader-Cache. Facts erben die
Vertraulichkeit ihrer Quelle: Wer die Datei lesen kann, sieht alle Befunde aller Quellen. Der Katalog prüft keine
Berechtigungen der Quellen; Schreiber legen nur Befunde aus Inhalten ab, die der Benutzer selbst laden durfte. Facts
enthalten keine Zugangsdaten: Ressourcen-IDs und Quellenadressen sind nach `KnowledgeResourceId`/`SourceAddress`
frei von Benutzerangaben, und Payloads dürfen keine Secrets tragen (Secrets nur über KeePass).
