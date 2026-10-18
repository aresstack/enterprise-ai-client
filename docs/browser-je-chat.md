# Browser je Chat (#141)

Jeder Chat bekommt einen eigenen, sichtbaren Browser mit **persistentem Profil**. Profil und Benutzerzustand gehören
dem Chat; Browserprozess und Automatisierungsverbindung sind austauschbare Laufzeitressourcen.

## Module

| Modul | Rolle |
|---|---|
| `browser-api` | Port `ChatBrowserPort` / `ChatBrowser` (Tabs, Navigation, Lesen) und Engine-SPI (`BrowserEngine`, `BrowserInstance`, `BrowserConnection`) |
| `browser-runtime` | `ChatBrowserRuntime`: Profil je Chat (`BrowserProfileStore`), Lazy-Start, ein Start/Reconnect je Chat, Recovery, chatlokale Tab-IDs |
| `proasteion:application` | `BrowserMcpTools` (`browser_tabs`, `browser_navigate`, `browser_read`, `browser_links`, `browser_follow`, `browser_back`, `browser_mark`, `browser_close`), im `ToolCatalog` gelistet und abschaltbar, je Chat gebunden |
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
8. Wie in askai bleibt Firefox nach getaner Arbeit nicht offen: jede Antwort, der die Browser-Werkzeuge angeboten
   wurden, beendet am Ende (fertig, Fehler oder Abbruch) den Browser des Chats. `browser_close` beendet ihn sofort.
   Das Profil bleibt, der nächste Werkzeugaufruf startet ihn neu (Cookies, Logins, Consent bleiben erhalten).
   `follow` und `back` werden nach einer Recovery nicht wiederholt (Linkliste und Verlauf gelten dann nicht mehr).

## Fortschritt im Chat

Jede Antwort mit Browser-Werkzeugen zeichnet ihre Web-Exploration in einem `WebExplorationRun` auf (application,
je Chat, je Antwort neu): entdeckt, geprüft (dem Modell gezeigte Links), besucht, übersprungen (Richtlinie/Benutzer),
fehlgeschlagen, relevant (`browser_mark`, gilt als verwendet), nach kanonischer Adresse. Die Browserkarte
(`ChatBrowserCard`) zeigt diesen Zustand in einer Wolke, die sich in place aktualisiert, statt einer Wolke je
Aufruf, und platzt am Ende zu „Browserrecherche abgeschlossen · 14 Seiten besucht · 37 Links geprüft · 6 relevante
Seiten verwendet“. `browser_links` markiert schon besuchte Ziele mit „(schon besucht)“.

## Linknavigation

`browser_links` liefert die echten Anker der gerenderten Seite (`a[href]`, `area[href]`) als Snapshot:
`link-N | Text [title/aria-label] | absolute Adresse`, nur http(s), ohne `mailto:`/`javascript:` und ohne reine
Sprungmarken auf derselben Seite, je Adresse einmal, höchstens `max_links` (Standard 100, höchstens 300), optional
mit `filter` (vor der Begrenzung angewendet). `browser_follow(link)` öffnet einen Link dieses Snapshots im selben
Tab, `browser_back` öffnet die Seite, die der Tab vor der letzten Navigation über die Werkzeuge zeigte. Die Ids
gelten nur für dieses Dokument: jede Navigation des Tabs, auch ein Klick oder Neuladen des Benutzers im sichtbaren
Firefox, macht sie ungültig (Adresse plus Dokumentkennung), das Modell ruft dann `browser_links` erneut auf. Das
Ziel von `follow` und `back` geht **vor** dem Laden durch die Zugriffsrichtlinie, die erreichte Adresse nach
Weiterleitungen erneut; ein gesperrtes Ziel wird nie geladen bzw. endet auf `about:blank`. Den eigenen Verlauf des
Browsers (der vom Benutzer geöffnete Seiten enthalten kann) spielt `back` bewusst nicht ab.

### Regionen und Dokumente

Jeder Link trägt die Landmarke, in der er steht (`{Navigation}`, `{Kopfbereich}`, `{Inhalt}`, `{Seitenleiste}`,
`{Fußbereich}`; nächstes `nav`/`header`/`main`/`aside`/`footer`/`article` bzw. ARIA-Rolle), damit das Modell Menüs von
Inhalt unterscheidet. Links auf Dokumente (`.pdf`, Office, `.txt`, `.csv`) sind als „Dokument → browser_document“
markiert: `browser_document(link)` lädt die Datei **in der Seite** (mit Cookies und Anmeldung des Chats, nur was die
Seite selbst laden dürfte, also keine fremden Origins), höchstens 10 MB, und liest den Text über dieselbe
Dokument-Pipeline wie Anhänge (Tika). Die Seite bleibt dabei offen, die Link-Ids gelten weiter. Ziel und erreichte
Adresse gehen durch die Zugriffsrichtlinie; ein gelesenes Dokument zählt als besucht und kann mit `browser_mark` als
relevant markiert werden.

## Bedienelemente und Seitensuche

`browser_controls` liefert die sichtbaren, aktivierten Bedienelemente der Seite, die keine reinen Links sind:
`ctl-N | Art | Beschriftung | Wert | Optionen`, Art `search`, `input`, `button`, `select`, `checkbox`, `radio`,
`menu`, `tab` oder `toggle`; Suchfelder stehen vorne, damit das Modell die Suche der Website nutzt, statt sich durch
Menüs zu klicken. Passwort- und versteckte Felder werden nie geliefert. `browser_act(control, action, value)`
führt `click`, `fill`, `submit` (Wert eintragen und wie Enter absenden) oder `select` aus und wartet, bis die Seite
zur Ruhe gekommen ist: ein neues Dokument ist vollständig geladen, oder das alte hat sich 600 ms nicht mehr
verändert (DOM-Beobachtung für Single-Page-Apps), höchstens 10 s. Das Ergebnis sagt, ob eine neue Seite geladen
wurde oder sich die Seite nur geändert hat (z. B. Menü aufgeklappt, Treffer nachgeladen). Die Ids gelten, bis ein
neues Dokument geladen wird; ein inzwischen entferntes Element meldet sich als nicht mehr vorhanden. Ein
deklariertes Formularziel geht **vor** dem Absenden durch die Zugriffsrichtlinie, die danach erreichte Adresse
erneut (gesperrt → `about:blank`); Ziele, die erst ein Skript der Seite bestimmt, sind nur nachträglich prüfbar.
Eine Aktion, die eine neue Seite lädt, zählt für `browser_back` wie eine Navigation. In der Browserrecherche-Karte
erscheinen die Schritte als „Gesucht: „…““, „„Menü“ angeklickt“ usw.

## Seitenbereitschaft, Einwilligungsbanner und Prüfseiten

Nach jeder Navigation wartet der Adapter über „Dokument vollständig“ hinaus, bis die Seite 600 ms lang nicht mehr
verändert wurde (höchstens 5 s): Intranets und Single-Page-Apps rendern ihren Inhalt oft erst nach dem Load-Ereignis.
Ersetzt eine Umleitung per Skript oder Formular (SAML/SSO-Rundreise) das angeforderte Laden, meldet Firefox
`NS_BINDING_ABORTED`; das gilt nicht als Fehler: der Adapter wartet bis zu 15 s, bis der Tab wieder auf dem Origin
der angeforderten Adresse ruhig ist, und liest dann die Zielseite (bleibt er auf einer Anmeldeseite, wird diese nach
Ablauf genommen und als Anmeldung erkannt). Jede geöffnete oder gelesene Seite wird danach geprüft (`ChatBrowser#probe`, ohne die Seite zu verändern):

- **Einwilligungsbanner** (Cookie/Consent, übliche Banner-Ids, sichtbar und mit Schaltflächen): Die Werkzeugantwort
  nennt die Schaltflächen und weist das Modell an, mit `browser_controls`/`browser_act` die sparsamste Option zu wählen
  („Ablehnen“, „Nur notwendige“). Automatisch zugestimmt wird nie.
- **Prüfseite** (Captcha-Frames, Bot-Schutz-Titel wie „Just a moment…“) und **Anmeldung** (sichtbares Passwortfeld):
  Das Modell löst sie nicht und gibt nie Passwörter ein, sondern bittet den Benutzer, das im sichtbaren Firefox des
  Chats zu tun. Die Karte zeigt „Prüfseite: bitte im Firefox lösen“ bzw. „Anmeldung nötig …“, und der Browser bleibt
  nach dieser Antwort **offen** (Zusammenfassung „… · Browser bleibt offen (Anmeldung)“). Die nächste Antwort, die
  keine offene Prüfseite mehr hinterlässt, schließt ihn wieder; `browser_close` schließt ihn sofort.

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

## Zugriffsrichtlinie

Eine globale Richtlinie (Einstellungen → Browser, Schlüssel `browser.access.*`) gilt für alle Chat-Browser. Sie
wird in der Browser-Anwendungsschicht durchgesetzt (`ChatBrowserRuntime` ruft vor jeder Navigation den
`BrowserAccessGuard`, umgesetzt von `BrowserAccessService`), nicht im Modell-Prompt.

- **Modi:** `browser.access.mode=block` (Standard) – nur Hosts der Whitelist sind ohne Rückfrage erreichbar;
  `allow` – alles ist erreichbar außer den Hosts der Blacklist. Mit leerer Whitelist und Modus `block` wird
  also jede Website erst nach Rückfrage geöffnet.
- **Regeln** (`browser.access.allow`, `browser.access.block`, durch Komma getrennt): `example.org` = genau
  dieser Host, `*.example.org` = alle Subdomains (nicht `example.org` selbst). Hosts werden kleingeschrieben,
  ohne abschließenden Punkt verglichen; Umlaut-Domains nur in Punycode-Schreibweise (`xn--…`), Adressen mit Nicht-ASCII-Host werden gesperrt; Einträge mit Schema, Pfad, Port oder Leerzeichen
  werden ignoriert. `evil-example.org` passt weder auf `example.org` noch auf `*.example.org`.
- **Die Blacklist gewinnt immer**, auch im Modus `allow` und auch gegen einen Whitelist-Eintrag.
- Nur `http`/`https` wird geprüft; `about:blank` und ähnliche interne Seiten sind immer erlaubt.
- **Weiterleitungen:** Nach dem Laden wird die endgültige Adresse erneut geprüft. Ist sie gesperrt, landet der
  Tab auf `about:blank` und das Werkzeug meldet die Sperre (Fehlerart `BLOCKED`, wird nie wiederholt).
- **Rückfrage bei unbekannten Hosts** (Modus `block`): Im Verlauf erscheint eine Blase mit vier Antworten:
  *Einmal erlauben* (nur diese Navigation), *Immer erlauben* (Host wird exakt, ohne Platzhalter, in die
  Whitelist geschrieben), *Immer verbieten* (Host kommt in die Blacklist) und *Abbrechen*. Keine Antwort
  (Zeitüberschreitung), ein abgebrochener Turn oder fehlende Oberfläche gelten als nicht freigegeben.

Noch nicht gefiltert werden Anfragen, die eine erlaubte Seite selbst an Drittanbieter stellt (Skripte, Bilder,
XHR). Das folgt später über das Abfangen von Netzwerkanfragen per WebDriver BiDi (`network.addIntercept`).

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
