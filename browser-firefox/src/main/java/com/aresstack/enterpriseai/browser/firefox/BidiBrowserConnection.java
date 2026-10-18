package com.aresstack.enterpriseai.browser.firefox;

import com.aresstack.enterpriseai.browser.api.BrowserAction;
import com.aresstack.enterpriseai.browser.api.BrowserException;
import com.aresstack.enterpriseai.browser.api.BrowserProbe;
import com.aresstack.enterpriseai.browser.api.engine.BrowserConnection;
import com.aresstack.enterpriseai.browser.api.engine.RawActOutcome;
import com.aresstack.enterpriseai.browser.api.engine.RawControl;
import com.aresstack.enterpriseai.browser.api.engine.RawLink;
import com.aresstack.enterpriseai.browser.api.engine.RawPage;
import com.aresstack.enterpriseai.browser.api.engine.RawTab;
import com.google.gson.JsonArray;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.bund.zrb.WDWebSocketImpl;
import de.bund.zrb.WebDriver;
import de.bund.zrb.command.response.WDBrowsingContextResult;
import de.bund.zrb.type.browsingContext.WDBrowsingContext;
import de.bund.zrb.type.browsingContext.WDInfo;
import de.bund.zrb.type.browsingContext.WDReadinessState;
import de.bund.zrb.type.script.WDEvaluateResult;
import de.bund.zrb.type.script.WDPrimitiveProtocolValue;
import de.bund.zrb.type.script.WDRemoteValue;
import de.bund.zrb.type.script.WDTarget;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One WebDriver BiDi connection (one WebSocket, one session) to a Firefox of this application, through WD4J.
 * Opening a connection starts no process and opens no tab; closing it leaves the browser running.
 */
final class BidiBrowserConnection implements BrowserConnection {

    /** Url, title and visible text in one evaluation, as a JSON array. */
    static final String READ_SCRIPT = "JSON.stringify([location.href, document.title, "
            + "document.body ? document.body.innerText : ''])";
    static final int MAX_RAW_LINKS = 2000;
    /**
     * The anchors of the rendered page (document order, at most {@value #MAX_RAW_LINKS}) as a JSON array of
     * [text, resolved href, title, aria-label]; {@code a.href} is already absolute. Only plain values leave the page.
     */
    static final String LINKS_SCRIPT = "JSON.stringify(Array.prototype.slice.call("
            + "document.querySelectorAll('a[href], area[href]'), 0, " + MAX_RAW_LINKS + ").map(function (a) {"
            + " var t = (a.innerText || a.textContent || '').replace(/\\s+/g, ' ').trim();"
            + " if (!t) { var img = a.querySelector('img[alt]'); t = img ? img.getAttribute('alt') : ''; }"
            + " return [t, String(a.href || ''), a.getAttribute('title') || '', a.getAttribute('aria-label') || '']; }))";
    /** A random id stored on the window: it lives exactly as long as the document. */
    static final String DOCUMENT_TOKEN_SCRIPT = "window.__enterpriseAiDocument || (window.__enterpriseAiDocument = "
            + "String(Date.now()) + '-' + Math.random().toString(36).slice(2))";
    static final int MAX_RAW_CONTROLS = 400;
    /**
     * The visible, enabled controls (document order, at most {@value #MAX_RAW_CONTROLS}) as a JSON array of
     * [ref, kind, label, value, options, form target]. Each element gets a {@code data-eai-control} attribute with a
     * fresh ref, so {@link #ACT_FUNCTION} finds it again while it is on the page. Password and hidden fields and real
     * links (those belong to the link list) are left out; only plain values leave the page.
     */
    static final String CONTROLS_SCRIPT = "(function () {"
            + " var gen = Date.now().toString(36) + Math.random().toString(36).slice(2, 6), out = [];"
            + " var els = document.querySelectorAll('input, textarea, select, button, summary, [contenteditable=\"true\"],"
            + " [role=button], [role=menuitem], [role=tab], [role=searchbox], [role=combobox], [role=switch],"
            + " [role=checkbox], [aria-expanded], [onclick]');"
            + " function flat(t) { return (t || '').replace(/\\s+/g, ' ').trim(); }"
            + " for (var i = 0; i < els.length && out.length < " + MAX_RAW_CONTROLS + "; i++) {"
            + "  var e = els[i], tag = e.tagName.toLowerCase(), type = (e.getAttribute('type') || '').toLowerCase();"
            + "  var role = (e.getAttribute('role') || '').toLowerCase();"
            + "  if (tag === 'input' && (type === 'hidden' || type === 'password')) continue;"
            + "  if (tag === 'a' && !/^\\s*(javascript:|#|$)/i.test(e.getAttribute('href') || '')) continue;"
            + "  if (e.disabled || e.getAttribute('aria-disabled') === 'true') continue;"
            + "  var r = e.getBoundingClientRect(); if (r.width === 0 && r.height === 0) continue;"
            + "  var hint = ((e.getAttribute('name') || '') + ' ' + (e.id || '') + ' ' + (e.getAttribute('placeholder') || '')"
            + "   + ' ' + (e.getAttribute('aria-label') || '')).toLowerCase();"
            + "  var kind;"
            + "  if (tag === 'select') kind = 'select';"
            + "  else if (tag === 'input' && (type === 'checkbox' || type === 'radio')) kind = type;"
            + "  else if (e.hasAttribute('aria-expanded') || tag === 'summary') kind = 'toggle';"
            + "  else if (tag === 'button' || (tag === 'input' && /^(submit|button|reset|image)$/.test(type))) kind = 'button';"
            + "  else if (type === 'search' || role === 'searchbox' || ((tag === 'input' || tag === 'textarea' || role === 'combobox')"
            + "   && /such|search|query|recherch|^q\\b|\\bq$/.test(hint))) kind = 'search';"
            + "  else if (tag === 'input' || tag === 'textarea' || e.isContentEditable || role === 'combobox') kind = 'input';"
            + "  else if (role === 'menuitem') kind = 'menu';"
            + "  else if (role === 'tab') kind = 'tab';"
            + "  else if (role === 'checkbox' || role === 'switch') kind = 'checkbox';"
            + "  else kind = 'button';"
            + "  var label = e.getAttribute('aria-label') || '';"
            + "  if (!label && e.labels && e.labels.length) label = e.labels[0].innerText;"
            + "  if (!label) label = e.getAttribute('placeholder') || e.getAttribute('title') || '';"
            + "  if (!label && (kind === 'button' || kind === 'menu' || kind === 'tab' || kind === 'toggle' || kind === 'checkbox'))"
            + "   label = e.innerText || e.value || e.getAttribute('alt') || '';"
            + "  if (!label) label = e.getAttribute('name') || e.id || '';"
            + "  var value = '';"
            + "  if (kind === 'checkbox' || kind === 'radio') value = (e.checked || e.getAttribute('aria-checked') === 'true') ? 'an' : 'aus';"
            + "  else if (kind === 'toggle') value = e.getAttribute('aria-expanded') === 'true' || e.parentElement && e.parentElement.open ? 'offen' : 'zu';"
            + "  else if (kind === 'select') value = e.selectedIndex >= 0 ? e.options[e.selectedIndex].text : '';"
            + "  else if (kind === 'search' || kind === 'input') value = e.isContentEditable ? e.innerText : (e.value || '');"
            + "  var options = kind === 'select' ? Array.prototype.slice.call(e.options, 0, 30)"
            + "   .map(function (o) { return flat(o.text); }).join(' | ') : '';"
            + "  var form = e.form || (e.closest ? e.closest('form') : null);"
            + "  var target = form && (kind === 'search' || kind === 'input' || kind === 'button') ? String(form.action || '') : '';"
            + "  var ref = gen + '-' + out.length; e.setAttribute('data-eai-control', ref);"
            + "  out.push([ref, kind, flat(label), flat(value).slice(0, 200), options, target]); }"
            + " return JSON.stringify(out); })()";
    /** Starts counting DOM mutations of the document once; the count lives in window.__enterpriseAiMutations. */
    static final String OBSERVE = " if (!window.__enterpriseAiObserver && window.MutationObserver) {"
            + "  window.__enterpriseAiMutations = window.__enterpriseAiMutations || 0;"
            + "  window.__enterpriseAiObserver = new MutationObserver(function (m) { window.__enterpriseAiMutations += m.length; });"
            + "  window.__enterpriseAiObserver.observe(document.documentElement,"
            + "   {subtree: true, childList: true, attributes: true, characterData: true}); }";
    /**
     * Performs an action on a tagged control; arguments are (ref, action, value). Before acting it starts counting
     * DOM mutations, so {@link #STATE_SCRIPT} can tell when a single page app has settled. Returns ok, missing,
     * disabled, nofield or nooption.
     */
    static final String ACT_FUNCTION = "(function (ref, action, value) {"
            + " var e = document.querySelector('[data-eai-control=\"' + ref + '\"]');"
            + " if (!e) return 'missing';"
            + " if (e.disabled || e.getAttribute('aria-disabled') === 'true') return 'disabled';"
            + " window.__enterpriseAiMutations = 0;" + OBSERVE
            + " try { e.scrollIntoView({block: 'center'}); } catch (x) { }"
            + " function fire(type) { e.dispatchEvent(new Event(type, {bubbles: true})); }"
            + " if (action === 'fill' || action === 'submit') {"
            + "  var field = e.tagName === 'INPUT' || e.tagName === 'TEXTAREA' || e.isContentEditable;"
            + "  if (!field) { if (action === 'fill') return 'nofield'; e.click(); return 'ok'; }"
            + "  e.focus();"
            + "  if (value !== null) {"
            + "   if (e.isContentEditable) { e.textContent = value; } else {"
            + "    var proto = e.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;"
            + "    var d = Object.getOwnPropertyDescriptor(proto, 'value');"
            + "    if (d && d.set) { d.set.call(e, value); } else { e.value = value; } }"
            + "   fire('input'); fire('change'); }"
            + "  if (action === 'submit') {"
            + "   var init = {key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true};"
            + "   var go = e.dispatchEvent(new KeyboardEvent('keydown', init));"
            + "   e.dispatchEvent(new KeyboardEvent('keypress', init));"
            + "   e.dispatchEvent(new KeyboardEvent('keyup', init));"
            + "   if (go && e.form) { if (e.form.requestSubmit) { e.form.requestSubmit(); } else { e.form.submit(); } } }"
            + "  return 'ok'; }"
            + " if (action === 'select') {"
            + "  if (e.tagName !== 'SELECT') return 'nofield';"
            + "  var wanted = String(value || '').toLowerCase().trim(), hit = null;"
            + "  for (var i = 0; i < e.options.length && !hit; i++) {"
            + "   var o = e.options[i]; if (o.text.trim().toLowerCase() === wanted || o.value.toLowerCase() === wanted) hit = o; }"
            + "  for (var j = 0; j < e.options.length && !hit; j++) {"
            + "   if (e.options[j].text.toLowerCase().indexOf(wanted) >= 0) hit = e.options[j]; }"
            + "  if (!hit) return 'nooption';"
            + "  e.value = hit.value; fire('input'); fire('change'); return 'ok'; }"
            + " e.click(); return 'ok'; })";
    /** Document token, readiness and the mutation count since the last action, as a JSON array. */
    static final String STATE_SCRIPT = "(function () {" + OBSERVE + " return JSON.stringify([(" + DOCUMENT_TOKEN_SCRIPT
            + "), document.readyState, window.__enterpriseAiMutations || 0]); })()";
    /**
     * Consent banner and challenge of the page as a JSON array [consent, button labels, challenge]. A consent banner
     * is a visible element named or labelled like one (cookie, consent, Einwilligung; common banner ids) whose text
     * speaks of cookies or consent; a challenge is a known bot check (captcha frames, interstitial titles) or a
     * visible password field.
     */
    static final String PROBE_SCRIPT = "(function () {"
            + " function shown(e) { var r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; }"
            + " var consent = false, labels = [];"
            + " var boxes = document.querySelectorAll('#onetrust-banner-sdk, #CybotCookiebotDialog, #usercentrics-root,"
            + " [id*=cookie i], [class*=cookie i], [id*=consent i], [class*=consent i], [aria-label*=cookie i],"
            + " [aria-label*=consent i], [role=dialog], [aria-modal=true]');"
            + " for (var i = 0; i < boxes.length && !consent; i++) {"
            + "  var b = boxes[i]; if (!shown(b)) continue;"
            + "  var t = (b.innerText || '').slice(0, 3000);"
            + "  if (!/cookie|einwillig|consent|zustimm|datenschutz|tracking/i.test(t)) continue;"
            + "  var buttons = b.querySelectorAll('button, [role=button], input[type=button], input[type=submit], a');"
            + "  for (var j = 0; j < buttons.length && labels.length < 6; j++) {"
            + "   var l = (buttons[j].innerText || buttons[j].value || '').replace(/\\s+/g, ' ').trim();"
            + "   if (l && l.length < 60 && shown(buttons[j])) labels.push(l); }"
            + "  consent = labels.length > 0; if (!consent) labels = []; }"
            + " var challenge = '';"
            + " if (document.querySelector('iframe[src*=recaptcha], iframe[src*=hcaptcha], iframe[src*=turnstile],"
            + " iframe[src*=\"challenges.cloudflare\"], #challenge-form, #cf-challenge-running, .g-recaptcha, .h-captcha')"
            + "  || /just a moment|attention required|verify you are human|sicherheitsüberprüfung|bist du ein mensch/i"
            + "  .test(document.title)) challenge = 'captcha';"
            + " else { var pw = document.querySelectorAll('input[type=password]');"
            + "  for (var k = 0; k < pw.length; k++) { if (shown(pw[k])) { challenge = 'login'; break; } } }"
            + " return JSON.stringify([consent, labels.join(' | '), challenge]); })()";
    /** Upper bound for waiting after a load until scripts stopped changing the page. */
    static final long LOAD_SETTLE_MILLIS = 5000L;
    /** Upper bound for waiting until the page settled after an action. */
    static final long SETTLE_TIMEOUT_MILLIS = 10000L;
    /** The document counts as settled after this long without a DOM change. */
    static final long QUIET_MILLIS = 600L;
    private static final long POLL_MILLIS = 100L;
    private static final Gson GSON = new Gson();
    private static final double CONNECT_TIMEOUT_MILLIS = 30000.0;

    private final WDWebSocketImpl socket;
    private final WebDriver driver;
    private final FirefoxInstance instance;
    private volatile boolean disconnected;

    private BidiBrowserConnection(WDWebSocketImpl socket, WebDriver driver, FirefoxInstance instance) {
        this.socket = socket;
        this.driver = driver;
        this.instance = instance;
    }

    static BidiBrowserConnection open(int port, FirefoxInstance instance) throws BrowserException {
        WDWebSocketImpl socket = null;
        try {
            socket = new WDWebSocketImpl(URI.create("ws://127.0.0.1:" + port + "/session"), CONNECT_TIMEOUT_MILLIS);
            if (!socket.isConnected()) {
                throw new BrowserException(BrowserException.Kind.CONNECTION_LOST,
                        "Keine Verbindung zu Firefox (WebDriver BiDi)");
            }
            WebDriver driver = new WebDriver(socket);
            driver.connect("firefox");
            return new BidiBrowserConnection(socket, driver, instance);
        } catch (BrowserException e) {
            closeQuietly(socket);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closeQuietly(socket);
            throw new BrowserException(BrowserException.Kind.CONNECTION_LOST, "Verbindung unterbrochen", e);
        } catch (Exception e) {
            closeQuietly(socket);
            throw new BrowserException(instance.isAlive() ? BrowserException.Kind.CONNECTION_LOST
                    : BrowserException.Kind.BROWSER_CLOSED, "Verbindung zu Firefox fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isOpen() {
        return !disconnected && socket.isConnected();
    }

    @Override
    public List<RawTab> tabs() throws BrowserException {
        check();
        try {
            WDBrowsingContextResult.GetTreeResult tree = driver.browsingContext().getTree((WDBrowsingContext) null, 0L);
            List<RawTab> tabs = new ArrayList<RawTab>();
            for (WDInfo info : tree.getContexts()) {
                String id = info.getContext().value();
                tabs.add(new RawTab(id, info.getUrl(), title(id)));
            }
            return tabs;
        } catch (RuntimeException e) {
            throw classify(e);
        }
    }

    @Override
    public String createTab() throws BrowserException {
        check();
        try {
            return driver.browsingContext().create().getContext();
        } catch (RuntimeException e) {
            throw classify(e);
        }
    }

    @Override
    public void activate(String contextId) throws BrowserException {
        check();
        try {
            driver.browsingContext().activate(contextId);
        } catch (RuntimeException e) {
            throw classify(e);
        }
    }

    @Override
    public void close(String contextId) throws BrowserException {
        check();
        try {
            driver.browsingContext().close(contextId);
        } catch (RuntimeException e) {
            throw classify(e);
        }
    }

    @Override
    public void navigate(String contextId, String url) throws BrowserException {
        check();
        try {
            driver.browsingContext().navigate(url, contextId, WDReadinessState.COMPLETE);
        } catch (RuntimeException e) {
            throw classify(e);
        }
        // Complete is not ready: intranets and single page apps render their content after the load event.
        settle(contextId, null, LOAD_SETTLE_MILLIS);
    }

    @Override
    public RawPage read(String contextId) throws BrowserException {
        check();
        try {
            JsonArray values = JsonParser.parseString(evaluate(contextId, READ_SCRIPT)).getAsJsonArray();
            return new RawPage(values.get(0).getAsString(), values.get(1).getAsString(), values.get(2).getAsString());
        } catch (BrowserException e) {
            throw e;
        } catch (RuntimeException e) {
            throw classify(e);
        }
    }

    @Override
    public List<RawLink> links(String contextId) throws BrowserException {
        check();
        try {
            JsonArray values = JsonParser.parseString(evaluate(contextId, LINKS_SCRIPT)).getAsJsonArray();
            List<RawLink> links = new ArrayList<RawLink>(values.size());
            for (JsonElement value : values) {
                JsonArray link = value.getAsJsonArray();
                links.add(new RawLink(link.get(0).getAsString(), link.get(1).getAsString(), link.get(2).getAsString(),
                        link.get(3).getAsString()));
            }
            return links;
        } catch (BrowserException e) {
            throw e;
        } catch (RuntimeException e) {
            throw classify(e);
        }
    }

    @Override
    public List<RawControl> controls(String contextId) throws BrowserException {
        check();
        try {
            JsonArray values = JsonParser.parseString(evaluate(contextId, CONTROLS_SCRIPT)).getAsJsonArray();
            List<RawControl> controls = new ArrayList<RawControl>(values.size());
            for (JsonElement value : values) {
                JsonArray c = value.getAsJsonArray();
                controls.add(new RawControl(c.get(0).getAsString(), c.get(1).getAsString(), c.get(2).getAsString(),
                        c.get(3).getAsString(), c.get(4).getAsString(), c.get(5).getAsString()));
            }
            return controls;
        } catch (BrowserException e) {
            throw e;
        } catch (RuntimeException e) {
            throw classify(e);
        }
    }

    @Override
    public RawActOutcome act(String contextId, String ref, BrowserAction action, String value)
            throws BrowserException {
        check();
        String before = documentToken(contextId);
        String script = ACT_FUNCTION + "(" + GSON.toJson(ref) + ", "
                + GSON.toJson(action.name().toLowerCase(Locale.ROOT)) + ", " + GSON.toJson(value) + ")";
        String answer;
        try {
            answer = evaluate(contextId, script);
        } catch (BrowserException | RuntimeException e) {
            // A submit can replace the document before the evaluation answers; whether the action worked shows in
            // the settled state below. A browser or connection that is gone still fails there.
            check();
            answer = "ok";
        }
        if ("missing".equals(answer)) {
            throw new BrowserException(BrowserException.Kind.INVALID_REQUEST,
                    "Das Bedienelement ist nicht mehr auf der Seite; browser_controls erneut aufrufen");
        }
        if ("disabled".equals(answer)) {
            throw new BrowserException(BrowserException.Kind.INVALID_REQUEST, "Das Bedienelement ist deaktiviert");
        }
        if ("nofield".equals(answer)) {
            throw new BrowserException(BrowserException.Kind.INVALID_REQUEST,
                    "Diese Aktion passt nicht zum Bedienelement (fill nur für Eingabefelder, select nur für Auswahllisten)");
        }
        if ("nooption".equals(answer)) {
            throw new BrowserException(BrowserException.Kind.INVALID_REQUEST,
                    "Keine passende Option: " + value);
        }
        return settle(contextId, before, SETTLE_TIMEOUT_MILLIS);
    }

    /**
     * Waits until a new document is complete and quiet, or the old one had no DOM change for {@link #QUIET_MILLIS};
     * at most {@code timeoutMillis}, after which the page is taken as it is. {@code before} is the document token
     * before an action, {@code null} after a navigation (then only quietness counts).
     */
    private RawActOutcome settle(String contextId, String before, long timeoutMillis) throws BrowserException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        String last = null;
        long quietSince = System.currentTimeMillis();
        boolean navigated = false;
        boolean changed = false;
        while (System.currentTimeMillis() < deadline) {
            pause();
            JsonArray state;
            try {
                state = JsonParser.parseString(evaluate(contextId, STATE_SCRIPT)).getAsJsonArray();
            } catch (BrowserException | RuntimeException e) {
                // Between two documents there is nothing to evaluate in; only a gone browser ends the wait.
                check();
                continue;
            }
            String token = state.get(0).getAsString();
            boolean complete = "complete".equals(state.get(1).getAsString());
            navigated |= before != null && !token.equals(before);
            changed |= state.get(2).getAsLong() > 0;
            String now = state.toString();
            if (!now.equals(last)) {
                last = now;
                quietSince = System.currentTimeMillis();
            } else if (complete && System.currentTimeMillis() - quietSince >= QUIET_MILLIS) {
                break;
            }
        }
        return new RawActOutcome(navigated, changed);
    }

    private static void pause() throws BrowserException {
        try {
            Thread.sleep(POLL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BrowserException(BrowserException.Kind.FAILED, "Warten auf die Seite unterbrochen", e);
        }
    }

    @Override
    public BrowserProbe probe(String contextId) throws BrowserException {
        check();
        try {
            JsonArray values = JsonParser.parseString(evaluate(contextId, PROBE_SCRIPT)).getAsJsonArray();
            return new BrowserProbe(values.get(0).getAsBoolean(), values.get(1).getAsString(),
                    values.get(2).getAsString());
        } catch (BrowserException e) {
            throw e;
        } catch (RuntimeException e) {
            throw classify(e);
        }
    }

    @Override
    public String documentToken(String contextId) throws BrowserException {
        check();
        try {
            return evaluate(contextId, DOCUMENT_TOKEN_SCRIPT);
        } catch (BrowserException e) {
            throw e;
        } catch (RuntimeException e) {
            throw classify(e);
        }
    }

    @Override
    public void disconnect() {
        disconnected = true;
        try {
            driver.close();
        } catch (RuntimeException ignored) {
            // Closing anyway.
        }
        closeQuietly(socket);
    }

    private String title(String contextId) {
        try {
            return evaluate(contextId, "document.title");
        } catch (BrowserException | RuntimeException e) {
            // A tab that is still loading or shows an error page has no readable title; the list stays usable.
            return "";
        }
    }

    private String evaluate(String contextId, String script) throws BrowserException {
        WDEvaluateResult result = driver.script().evaluate(script,
                new WDTarget.ContextTarget(new WDBrowsingContext(contextId)), true);
        if (!(result instanceof WDEvaluateResult.WDEvaluateResultSuccess)) {
            throw new BrowserException(BrowserException.Kind.FAILED, "Seite konnte nicht gelesen werden");
        }
        WDRemoteValue value = ((WDEvaluateResult.WDEvaluateResultSuccess) result).getResult();
        if (value instanceof WDPrimitiveProtocolValue.StringValue) {
            return ((WDPrimitiveProtocolValue.StringValue) value).getValue();
        }
        return "";
    }

    private void check() throws BrowserException {
        if (!instance.isAlive()) {
            throw new BrowserException(BrowserException.Kind.BROWSER_CLOSED, "Firefox wurde geschlossen");
        }
        if (!isOpen()) {
            throw new BrowserException(BrowserException.Kind.CONNECTION_LOST, "Verbindung zu Firefox verloren");
        }
    }

    /** Process gone beats connection gone beats protocol errors. */
    private BrowserException classify(RuntimeException e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (!instance.isAlive()) {
            return new BrowserException(BrowserException.Kind.BROWSER_CLOSED, "Firefox wurde geschlossen", e);
        }
        if (!socket.isConnected()) {
            return new BrowserException(BrowserException.Kind.CONNECTION_LOST, "Verbindung zu Firefox verloren", e);
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("no such frame") || lower.contains("invalid argument")) {
            return new BrowserException(BrowserException.Kind.INVALID_REQUEST, message, e);
        }
        if (lower.contains("timeout")) {
            return new BrowserException(BrowserException.Kind.TIMEOUT, "Firefox hat nicht rechtzeitig geantwortet", e);
        }
        return new BrowserException(BrowserException.Kind.FAILED, message, e);
    }

    private static void closeQuietly(WDWebSocketImpl socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (RuntimeException ignored) {
                // Already closed.
            }
        }
    }
}
