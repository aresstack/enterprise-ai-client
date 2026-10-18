package com.aresstack.enterpriseai.browser.api;

import java.util.List;

/**
 * The browser of one chat. Operations act on the current tab unless a chat-local tab id is given. Reading
 * operations may be retried once after a reconnect; tab creation and closing are never repeated blindly.
 */
public interface ChatBrowser {

    /** All tabs of this chat's browser; exactly one is marked current once a tab exists. */
    List<BrowserTab> tabs() throws BrowserException;

    /** Makes {@code tabId} the current tab (and brings it to the front). */
    BrowserTab selectTab(String tabId) throws BrowserException;

    /** Opens a new tab, makes it current and navigates it to {@code url} if not {@code null}. */
    BrowserTab newTab(String url) throws BrowserException;

    /** Closes {@code tabId}; if it was current, another tab becomes current. */
    void closeTab(String tabId) throws BrowserException;

    /** Navigates {@code tabId} (or the current tab if {@code null}) and returns the loaded page. */
    BrowserPage navigate(String tabId, String url) throws BrowserException;

    /** Reads {@code tabId} (or the current tab if {@code null}) without navigating. */
    BrowserPage read(String tabId) throws BrowserException;

    /**
     * The links of {@code tabId} (or the current tab if {@code null}) as a new snapshot: only real anchors of the
     * rendered page, absolute http(s) addresses, de-duplicated, at most {@code maxLinks}. Replaces the previous
     * snapshot of the tab.
     */
    BrowserLinks links(String tabId, int maxLinks) throws BrowserException;

    /**
     * Opens the link {@code linkId} of the tab's current link snapshot. The address passes the access guard like any
     * navigation. Fails with {@code INVALID_REQUEST} when the id is unknown or the tab left the snapshot's page.
     */
    BrowserPage follow(String tabId, String linkId) throws BrowserException;

    /** Goes one step back in the tab's history; the reached address passes the access guard again. */
    BrowserPage back(String tabId) throws BrowserException;

    /**
     * Ends this chat's browser process and connection; the profile stays. The next operation starts the browser
     * again with the same profile. Does nothing when no browser runs.
     */
    void closeBrowser();

    /** Whether a browser process of this chat runs right now (as far as the runtime knows). */
    boolean isRunning();
}
