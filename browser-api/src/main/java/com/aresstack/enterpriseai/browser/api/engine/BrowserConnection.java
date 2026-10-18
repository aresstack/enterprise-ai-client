package com.aresstack.enterpriseai.browser.api.engine;

import com.aresstack.enterpriseai.browser.api.BrowserException;

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

    /** Navigates and waits for the document to be complete. */
    void navigate(String contextId, String url) throws BrowserException;

    /** Url, title and visible text of the tab. */
    RawPage read(String contextId) throws BrowserException;

    /** The anchors of the rendered page in document order, hrefs resolved against the document. */
    List<RawLink> links(String contextId) throws BrowserException;

    /**
     * An identity of the tab's current document: the same value while the document lives, a new one after any load
     * or reload, also when the address stays the same.
     */
    String documentToken(String contextId) throws BrowserException;

    /** Closes the connection only; the browser keeps running. */
    void disconnect();
}
