package com.aresstack.enterpriseai.browser.api.engine;

import com.aresstack.enterpriseai.browser.api.BrowserAction;
import com.aresstack.enterpriseai.browser.api.BrowserException;
import com.aresstack.enterpriseai.browser.api.BrowserProbe;

import java.util.List;

/**
 * One automation connection (for BiDi: one WebSocket with one session). Tabs are addressed by the engine's raw
 * context id. A broken connection reports {@code CONNECTION_LOST}, a vanished browser {@code BROWSER_CLOSED}.
 */
public interface BrowserConnection {

    boolean isOpen();

    /** The top-level tabs in browser order. */
    List<RawTab> tabs() throws BrowserException;

    /** Opens a new tab and returns its raw context id. */
    String createTab() throws BrowserException;

    void activate(String contextId) throws BrowserException;

    void close(String contextId) throws BrowserException;

    /**
     * Navigates and waits for the document to be complete and then quiet (scripts that render the content after the
     * load event, as single page apps do, had their time), within an upper bound.
     */
    void navigate(String contextId, String url) throws BrowserException;

    /** Url, title and visible text of the tab. */
    RawPage read(String contextId) throws BrowserException;

    /** The anchors of the rendered page in document order, hrefs resolved against the document. */
    List<RawLink> links(String contextId) throws BrowserException;

    /**
     * The visible, enabled controls of the rendered page in document order (fields, buttons, select boxes, menu
     * entries, tabs, toggles; no password fields, no plain links), each tagged so {@link #act} can find it again.
     */
    List<RawControl> controls(String contextId) throws BrowserException;

    /**
     * Performs {@code action} on the control {@code ref} and waits until the page settled: a new document is
     * complete, or the document stopped changing. Fails with {@code INVALID_REQUEST} when the control is gone,
     * disabled or does not fit the action (no such option, not a field).
     */
    RawActOutcome act(String contextId, String ref, BrowserAction action, String value) throws BrowserException;

    /**
     * Fetches {@code url} inside the tab's document with its cookies (same rules as a script of the page), at most
     * {@code maxBytes}; fails with {@code FAILED} for HTTP errors, too large or blocked (cross-origin) responses.
     */
    RawDownload fetch(String contextId, String url, int maxBytes) throws BrowserException;

    /** Consent banner and challenge of the rendered page; see {@code ChatBrowser#probe}. */
    BrowserProbe probe(String contextId) throws BrowserException;

    /**
     * An identity of the tab's current document: the same value while the document lives, a new one after any load
     * or reload, also when the address stays the same.
     */
    String documentToken(String contextId) throws BrowserException;

    /** Closes the connection only; the browser keeps running. */
    void disconnect();
}
