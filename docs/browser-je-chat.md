# Browser je Chat (#141)

Jeder Chat bekommt einen eigenen, sichtbaren Browser mit **persistentem Profil**. Profil und Benutzerzustand gehören
dem Chat; Browserprozess und Automatisierungsverbindung sind austauschbare Laufzeitressourcen.

## Module

| Modul | Rolle |
|---|---|
| `browser-api` | Port `ChatBrowserPort` / `ChatBrowser` (Tabs, Navigation, Lesen) und Engine-SPI (`BrowserEngine`, `BrowserInstance`, `BrowserConnection`) |
| `browser-runtime` | `ChatBrowserRuntime`: Profil je Chat (`BrowserProfileStore`), Lazy-Start, ein Start/Reconnect je Chat, Recovery, chatlokale Tab-IDs |
| `proasteion:application` | `BrowserMcpTools` (`browser_tabs`, `browser_navigate`, `browser_read`), im `ToolCatalog` gelistet und abschaltbar, je Chat gebunden |
| `browser-firefox` | `FirefoxEngine`: sichtbarer Firefox auf dem Chat-Profil, freier Loopback-Port, Warten auf die BiDi-Bereitschaftszeile; Verbindung über WD4J (unverändert übernommen, siehe `browser-firefox/VENDORED.md`), direkter WebSocket ohne Geckodriver |

## Begriffe

- **Firefox-Profil**: persistenter Ordner eines Chats (`<App-Verzeichnis>/browser-profiles/<chatId>`).
- **BiDi `userContext`**: Cookie-/Storage-Kontext innerhalb eines laufenden Browsers. Nicht dasselbe wie das Profil.
- **BiDi `browsingContext`**: ein Tab oder Frame. Das Modell sieht nie dessen ID, nur chatlokale IDs wie `tab-2`.
- **BiDi `sessionId`**: eine Automatisierungssitzung auf einer Verbindung.
- CDP `BrowserContext` entspricht grob dem BiDi-`userContext`, nicht dem `browsingContext`.

## Lebenszyklus

1. Der erste Browser-Werkzeugaufruf eines Chats startet den Browser (nicht schon das Öffnen der Chatliste).
2. Eine gesunde Verbindung wird wiederverwendet: kein neuer Prozess, keine neue Session, kein neuer Tab.
3. Verbindung verloren, Prozess lebt: Neuverbindung an denselben Prozess; Tabs und aktueller Tab bleiben.
4. Prozess beendet (vom Benutzer geschlossen, abgestürzt): normaler Start mit demselben Profil auf einem freien
   Loopback-Port, dann Warten auf echte BiDi-Bereitschaft. **Nicht** mit `-silent` starten: ein kalt mit `-silent`
   gestarteter Firefox öffnet den BiDi-Port nicht. `-silent` unterdrückt nur den Zusatztab, wenn eine laufende
   Instanz desselben Profils erneut aufgerufen wird; das tut die Laufzeit nie.
5. Alle Aufrufe eines Chats laufen nacheinander; gleichzeitige Erstaufrufe starten genau einen Browser.
6. Lesende Aufrufe (`tabs`, `read`, `navigate`, `select`) werden nach erfolgreicher Recovery einmal wiederholt;
   Tab anlegen und schließen (und später Klicks, Formulare) **nicht**, weil sie vor dem Abbruch schon gewirkt haben
   können.
7. Chat schließen beendet Prozess und Verbindung, das Profil bleibt; Chat löschen löscht auch das Profil.

## Persistenz

Persistente Cookies, Consent-Entscheidungen, Site-Storage und Einstellungen liegen im Profil und bleiben über
Prozess- und App-Neustarts erhalten. Session-Cookies und offene Tabs bleiben nur, soweit Firefox selbst sie
wiederherstellt.

## Schalter im Composer

Unten im Composer hat jeder Chat zwei Schalter, gespeichert mit dem Chat:

- **Browser** (Globus, Standard aus): nur wenn er an ist, bekommen der Chat und sein Agent `browser_tabs`,
  `browser_navigate` und `browser_read`. Ausschalten beendet nichts und löscht kein Profil; Firefox startet erst
  beim ersten Aufruf. Ohne Firefox oder mit abgeschalteten Browser-Werkzeugen im Reiter „Tools“ ist der Schalter grau.
- **Wissensquellen** (Buch, Standard an): aus heißt für den Turn keine automatische Wissenssuche und keine
  `knowledge_*`-Werkzeuge, auch nicht die des Projekts. Anhänge und Erinnerungen bleiben.

Sind beide an, sind beide Schalter orange markiert: Inhalte aus Wissensquellen können in Browseraufrufe einfließen.
Ein Umschalten gilt ab dem nächsten Turn. Eine Rückfrage vor Seitenaufrufen gibt es nicht; die Freigaben aus #146
bleiben für spätere schreibende Browseraktionen vorgesehen.

## Sicherheit

- Die Chat-Identität kommt aus dem Chat (die Werkzeuge werden je Unterhaltung gebunden), nie aus Modellargumenten.
  Die Werkzeugschemas haben keine Parameter für Chat-ID, Port, Profilpfad oder `userContext`.
- Chatlokale Tab-IDs: Ein Chat kann Tabs eines anderen Chats weder auflisten noch ansprechen.
- Nur `http`/`https`-Adressen; kein `file:`, `javascript:`, `about:`.
- Profilordner nur mit einfachen Chat-IDs (`[A-Za-z0-9_-]`) unterhalb des Profilwurzelordners.
- Der Debugging-Port bindet nur an Loopback und wird dem Modell nie genannt; Cookies und Tokens werden nicht
  protokolliert.

## Firefox finden und starten

- Firefox kommt aus `-Denterpriseai.firefox=<Pfad>` oder vom üblichen Installationsort (Windows: `Program Files`,
  macOS: `/Applications`, Linux: `PATH`). Ohne Firefox gibt es keine Browser-Werkzeuge.
- Startparameter: `-profile <Profilordner> -no-remote --remote-debugging-port=<freier Port>`. `-no-remote` verhindert,
  dass ein Aufruf in einem schon laufenden Firefox landet und dort nur einen Tab öffnet.
- Bereit ist Firefox erst, wenn er `WebDriver BiDi listening on ws://127.0.0.1:<port>` meldet.
- Beim ersten Start schreibt der Client eine `user.js` mit ruhigen Voreinstellungen (keine Willkommensseite, keine
  Standardbrowser-Frage); danach gehört sie dem Benutzer.
- Ein Profil, das ein anderes Programmfenster schon benutzt (Sperrdatei `.enterprise-ai-browser.lock`), wird nicht
  ein zweites Mal geöffnet.

## Tests

- `browser-runtime`: Laufzeit mit Fake-Engine (Lazy-Start, Reconnect, Recovery, Nebenläufigkeit, Isolation).
- `browser-firefox`: Profilsperre, Voreinstellungen; `TwoFirefoxChatsIT` mit zwei echten Firefox-Instanzen nur auf
  Anforderung: `./gradlew :browser-firefox:test -Denterpriseai.firefox.it=true`.
- Manueller Smoke-Test unter Windows: im Chat `browser_navigate` aufrufen, Firefox von Hand schließen, erneut
  `browser_read` aufrufen. Erwartet: Firefox startet mit demselben Profil neu, Cookies und Consent bleiben.
