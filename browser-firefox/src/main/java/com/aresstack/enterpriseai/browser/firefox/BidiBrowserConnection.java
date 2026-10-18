package com.aresstack.enterpriseai.browser.firefox;

import com.aresstack.enterpriseai.browser.api.BrowserException;
import com.aresstack.enterpriseai.browser.api.engine.BrowserConnection;
import com.aresstack.enterpriseai.browser.api.engine.RawLink;
import com.aresstack.enterpriseai.browser.api.engine.RawPage;
import com.aresstack.enterpriseai.browser.api.engine.RawTab;
import com.google.gson.JsonArray;
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
