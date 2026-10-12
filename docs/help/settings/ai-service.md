---
id: settings.ai-service
title: Einstellungen – Reiter „KI-Dienst“
keywords: KI-Dienst, Enterprise-API, API, Basis-URL, Base URL, baseUrl, API-Key, API Key, Token, KeePass-Eintrag, Zugang, Manuell, System-Prompt, Systemprompt, Embeddings, Embedding-Dimension, Dimension, Verbindung testen, In KeePass prüfen, GET /models, OpenAI-kompatibel, Erweitert, chat.baseUrl, chat.apiKeyRef, chat.systemPrompt
---

# Einstellungen – Reiter „KI-Dienst“

Der Reiter „KI-Dienst“ legt fest, mit welchem KI-Dienst der Client spricht und wie er sich dort anmeldet. Gemeint ist die Enterprise-API im OpenAI-kompatiblen Format, die Chat-Antworten und Embeddings liefert. Hier stehen der Zugang (über KeePass oder manuell), der KeePass-Eintrag mit API-Key und Adresse, der System-Prompt, die Angaben für Embeddings und der Knopf „Verbindung testen“. Die Modelle selbst wählst du nicht hier, sondern im Reiter [Modelle](help:settings.models).

Du öffnest den Reiter über das Zahnrad in der Seitenleiste; er ist die erste Kategorie und beim Öffnen des Dialogs schon ausgewählt (siehe [Der Dialog „Einstellungen“ im Überblick](help:settings.overview)).

## Bereich „Chat“

Über den Feldern steht der Hinweis: „Die Enterprise-API im OpenAI-kompatiblen Format. Mit KeePass genügt der Eintrag: API-Key (Passwortfeld) und Basis-URL (URL-Feld, meist auf /v1) stehen dort. Die Modelle wählt der Reiter „Modelle“.“

### Zugang

- **Beschriftung:** „Zugang“, Auswahlliste mit „KeePass“ und „Manuell“.
- **Standard:** „KeePass“.
- **Bedeutung:** Bei „KeePass“ holt der Client API-Key und Adresse aus einem KeePass-Eintrag. Bei „Manuell“ trägst du die Adresse hier ein; den API-Key fragt das Programm beim ersten Zugriff ab und speichert ihn nicht.
- **Wirkung:** Je nach Wahl zeigt der Reiter andere Felder. Beim Umschalten wandert eine bereits eingetragene Adresse mit: bei „Manuell“ in das Pflichtfeld „Basis-URL *“, bei „KeePass“ in das Überschreibungsfeld unter „Erweitert“.

In der Konfigurationsdatei steht bei „KeePass“ der Titel des Eintrags unter `chat.apiKeyRef`, bei „Manuell“ ein interner Verweis, der „beim ersten Gebrauch fragen“ bedeutet. Ein Präfix wie `keepass:` musst du nie selbst tippen.

### KeePass-Eintrag

- **Beschriftung:** „KeePass-Eintrag“, nur sichtbar bei Zugang „KeePass“.
- **Bedeutung:** Der Titel des KeePass-Eintrags, dessen Passwortfeld den API-Key und dessen URL-Feld die Basis-URL enthält (Tooltip: „Eintrag mit dem API-Key im Passwortfeld und der Basis-URL im URL-Feld“).
- **Bedienung:** Die Liste klappt die Titel aller Einträge auf, die KeePass liefert. Dafür verbindet sich der Dialog beim Öffnen im Hintergrund mit KeePass; ist noch kein Pairing vorhanden, kann dabei der Pairing-Dialog erscheinen. Du kannst den Titel auch frei eintippen, etwa wenn KeePass gerade gesperrt ist.
- **Vorauswahl:** Ist noch nichts eingetragen und gibt es genau einen Eintrag, dessen Titel einen der üblichen Namen wie „Enterprise AI“ oder „KI-Dienst“ enthält, wählt der Dialog ihn vor. Beim allerersten Start schlägt das Programm den Titel „Enterprise AI API“ vor.
- **Knopf „Neu“** (mit Plus-Symbol) rechts daneben: öffnet den Dialog „Neuen Eintrag anlegen“ der Passwortverwaltung mit vorbelegter Kategorie. Nach „OK“ legt er den Eintrag sofort in KeePass an und wählt ihn hier aus. Die Felder dieses Dialogs beschreibt [KeePass](help:settings.keepass).

### In KeePass prüfen

Unter dem Eintrag steht der Knopf „In KeePass prüfen“ (Tooltip: „Verbindet sich mit KeePassRPC und sucht den Eintrag mit diesem Titel“). Während der Prüfung steht daneben „Prüfe … (bei Bedarf öffnet sich der Pairing-Dialog)“, der Knopf ist gesperrt.

Die Prüfung benutzt die KeePass-Einstellungen des aktuellen, noch nicht gespeicherten Entwurfs, koppelt bei Bedarf neu und meldet nur, ob der Eintrag existiert und sein Passwortfeld gefüllt ist. Der Key selbst wird nie angezeigt, auch nicht seine Länge. Grün heißt gefunden, Rot heißt nicht gefunden, nicht erreichbar oder abgebrochen. Ist KeePassRPC ausgeschaltet, lautet die Meldung „KeePassRPC ist ausgeschaltet; ohne KeePass gibt es keinen API-Key.“ Ist kein Titel eingetragen: „Bitte zuerst den Titel des KeePass-Eintrags eintragen.“

### Basis-URL bei „Manuell“

- **Beschriftung:** „Basis-URL *“ (Pflichtfeld), nur sichtbar bei Zugang „Manuell“.
- **Bedeutung:** Die Adresse der Enterprise-API, zum Beispiel `https://ki.example.org/v1`.
- **Regeln:** Eine absolute http- oder https-Adresse ohne Zugangsdaten, Query oder Fragment, meist bis `/v1`. Sie darf nicht schon auf einen Endpunkt wie `/chat/completions`, `/embeddings` oder `/models` enden; diese Pfade hängt der Client selbst an.

### Erweitert: Basis-URL überschreiben

Bei Zugang „KeePass“ gibt es den aufklappbaren Bereich „Erweitert“ (Tooltip: „Weitere Angaben; leer lassen genügt in der Regel“). Darin steht:

- **Beschriftung:** „Basis-URL (sonst aus KeePass)“.
- **Standard:** leer.
- **Wirkung:** Leer gilt die URL aus dem URL-Feld des KeePass-Eintrags. Ein Wert hier überschreibt sie. Es gelten dieselben Regeln wie oben.

### System-Prompt

- **Beschriftung:** „System-Prompt“, mehrzeiliges Feld (Tooltip: „Erste Nachricht jeder Konversation; leer = Standard“).
- **Standard:** ein mitgelieferter deutscher Prompt. Er beginnt mit „Du bist ein hilfreicher Assistent. Antworte auf Deutsch, sofern der Benutzer keine andere Sprache verlangt.“ und bittet das Modell unter anderem, verfügbare Werkzeuge selbstständig zu nutzen, bei internen Begriffen die Wissensquellen zu bevorzugen, bei Lücken gezielt nachzusuchen und keine Quellen, Werkzeugaufrufe oder Fähigkeiten zu erfinden.
- **Wirkung:** Der Text geht jeder Anfrage des normalen Chats voran. Leerst du das Feld, gilt wieder der Standard. Änderungen gelten nach dem Speichern ohne Neustart.

## Bereich „Embeddings“

Hinweis im Dialog: „Embeddings vektorisieren die Wissensbasis. Basis-URL und KeePass-Eintrag dürfen leer bleiben, dann gelten die Werte des Chats.“ Embeddings braucht der Client für die semantische Suche im Index (siehe [Der Wissensindex](help:concepts.knowledge-index)).

| Feld | Bedeutung | Standard | Wirkung |
|---|---|---|---|
| „Basis-URL (optional)“ | Adresse für den Embedding-Endpunkt | leer = wie Chat | Nur nötig, wenn Embeddings über eine andere Adresse laufen. Gleiche Regeln wie die Chat-Adresse. |
| „Dimension“ | Länge der Vektoren, die der Dienst liefert (ganze Zahl) | keiner, Pflichtangabe | Erlaubt sind 1 bis 65536. Ohne Wert lässt sich nicht speichern. |
| „KeePass-Eintrag (optional)“ | Titel des KeePass-Eintrags mit dem Key für Embeddings | leer = wie Chat | Nur nötig, wenn Embeddings einen eigenen Key brauchen. |

Zur Dimension: Sie ist die Erwartung des Clients. Liefert der Dienst einheitlich eine andere Länge, übernimmt der Client die gelieferte und schreibt beide Werte ins Protokoll; trag dann den gemessenen Wert hier ein, damit der Hinweis verschwindet. Der Einrichtungsassistent misst die Dimension mit einem Probe-Embedding. Das Embedding-Modell selbst wählst du im Reiter [Modelle](help:settings.models).

## Bereich „Verbindung“: Verbindung testen

Der Knopf „Verbindung testen“ (Tooltip: „Prüft mit dem aktuellen Entwurf API-Key aus KeePass, Proxy-Route, HTTPS-Verbindung, GET /models mit HTTP-Status sowie Chat- und Embedding-Modell“) prüft den ganzen Weg zum KI-Dienst, ohne dass du speichern musst. Er arbeitet mit den Werten, die gerade im Dialog stehen, auch aus anderen Reitern (etwa Proxy und TLS aus „Netzwerk & Agent“).

### Was der Test der Reihe nach prüft

1. **Konfiguration:** Zuerst geht der Entwurf durch die Prüfung. Ist er ungültig, endet der Test sofort mit einem roten Schritt „Konfiguration“.
2. **API-Key:** Der Key wird aus KeePass gelesen; bei Bedarf öffnet sich der Pairing-Dialog. Schlägt das fehl, läuft der Test ohne Key weiter.
3. **Proxy-Route:** Welcher Weg (direkt oder über welchen Proxy) für `<Basis-URL>/models` gilt.
4. **HTTPS-Verbindung:** Aufbau der verschlüsselten Verbindung mit den Vertrauensquellen des Entwurfs.
5. **GET /models:** Abfrage der Modellliste mit HTTP-Status und Abgleich mit dem gewählten Chat-Modell.
6. **Embedding-Modell:** Abgleich des gewählten Embedding-Modells mit der Liste.

Jeder Schritt erscheint als eigene Zeile, sobald er feststeht: grün mit Haken (in Ordnung), orange mit Ausrufezeichen (Hinweis), rot mit Kreuz (fehlgeschlagen) oder grau (Information). Ein roter Schritt beendet den Test. Neben dem Knopf steht am Ende eine Zusammenfassung: „Verbindung in Ordnung.“, „Verbindung steht; bitte die Hinweise beachten.“ oder „Verbindung fehlgeschlagen; der rote Schritt nennt die Ursache.“

Der API-Key erscheint in keiner Zeile. Dieselben Schritte stehen zusätzlich im Protokoll. Der Test ändert nichts an der laufenden Anwendung. Denselben Test gibt es im Reiter [Technische Details](help:settings.technical-details), dort als Textzeilen.

## Was im Hintergrund passiert

Der Client speichert nie den API-Key, sondern nur den Titel des KeePass-Eintrags. Bei jeder Chat- und jeder Embedding-Anfrage holt er den Key frisch aus dem entsperrten KeePass, verwendet ihn für genau diese Anfrage und verwirft ihn danach. Deshalb muss KeePass während der Arbeit entsperrt bleiben. Bei Zugang „KeePass“ ohne eigene Basis-URL kommt auch die Adresse bei jedem Start aus dem Eintrag; die Konfigurationsdatei bleibt dann an dieser Stelle leer. Mehr dazu in [Sicherheit](help:concepts.security).

## Grenzen

- Der Client unterstützt genau eine Art von Dienst: eine OpenAI-kompatible API mit `/chat/completions`, `/embeddings` und `/models`. Eine Auswahl verschiedener Anbieter gibt es nicht.
- Einen API-Key kannst du in diesem Reiter nicht eintippen. Bei „KeePass“ steht er im Eintrag, bei „Manuell“ fragt das Programm ihn zur Laufzeit ab und vergisst ihn wieder.
- „In KeePass prüfen“ sagt nur, ob der Eintrag da und gefüllt ist, nicht ob der Dienst den Key akzeptiert. Das zeigt erst „Verbindung testen“ im Schritt „GET /models“.

## Typische Probleme

### „Verbindung testen“ bleibt beim Schritt „API-Key“ rot

Prüfe, ob KeePass läuft und entsperrt ist, ob der Titel exakt stimmt (Groß- und Kleinschreibung, Leerzeichen) und ob das Pairing bestätigt ist. „In KeePass prüfen“ gibt dazu eine genauere Meldung. Siehe [Probleme mit KeePass](help:troubleshooting.keepass).

### Rot bei „HTTPS-Verbindung“ oder „Proxy-Route“

Meist ein Firmen-Proxy oder ein Zertifikat, dem Java nicht vertraut. Stell im Reiter [Netzwerk & Agent](help:settings.network) den Proxy-Modus ein und prüfe die TLS-Quellen; „Proxy auflösen“ und „HTTPS-Verbindung testen“ dort zeigen Details. Siehe [Netzwerkprobleme](help:troubleshooting.network).

### „GET /models“ meldet HTTP 401 oder 403

Der Dienst lehnt den Key ab. Im Passwortfeld des KeePass-Eintrags darf nur der Key stehen, ohne das Wort „Bearer“. Ein HTTP 404 ist meist ein Hinweis, dass die Basis-URL nicht stimmt (etwa fehlendes `/v1`).

### Speichern meldet „darf nicht auf einen Endpunktpfad enden“

Du hast eine vollständige Endpunktadresse eingetragen, etwa mit `/chat/completions` am Ende. Kürze sie auf die Basis, meist bis `/v1`.

### Speichern meldet, die Embedding-Dimension fehle

Trag die Vektorlänge des Embedding-Modells ein, zum Beispiel 768. Kennst du sie nicht, lass sie vom Einrichtungsassistenten messen oder frag die Betreiber des Dienstes.
