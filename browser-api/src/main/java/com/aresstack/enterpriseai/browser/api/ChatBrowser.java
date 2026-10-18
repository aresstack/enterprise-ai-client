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
     * rendered page, absolute http(s) addresses, de-duplicated, and when {@code filter} is not {@code null} only links
     * whose text, label or address contains it (ignoring case), at most {@code maxLinks}. Replaces the previous
     * snapshot of the tab.
     */
    BrowserLinks links(String tabId, int maxLinks, String filter) throws BrowserException;

    /**
     * Opens the link {@code linkId} of the tab's current link snapshot. The address passes the access guard like any
     * navigation. Fails with {@code INVALID_REQUEST} when the id is unknown or the tab left the snapshot's page.
     */
    BrowserPage follow(String tabId, String linkId) throws BrowserException;

    /**
     * Returns to the page the tab showed before its last navigation through this browser. The address passes the
     * access guard before it is opened and again after redirects.
     */
    BrowserPage back(String tabId) throws BrowserException;

    /**
     * The controls of {@code tabId} (or the current tab if {@code null}) as a new snapshot: search and text fields,
     * buttons, select boxes, menu entries, tabs and toggles that are visible and enabled; search fields first. When
     * {@code filter} is not {@code null} only controls whose label, kind or value contains it (ignoring case), at most
     * {@code maxControls}. Replaces the previous control snapshot of the tab.
     */
    BrowserControls controls(String tabId, int maxControls, String filter) throws BrowserException;

    /**
     * Performs {@code action} on the control {@code controlId} of the tab's control snapshot and returns the page once
     * it settled. A form's declared target passes the access guard before submitting, and the address reached
     * afterwards passes it again. Fails with {@code INVALID_REQUEST} when the id is unknown, a new document was loaded
     * since the snapshot, or the element is gone.
     */
    BrowserActResult act(String tabId, String controlId, BrowserAction action, String value) throws BrowserException;

    /**
     * Looks for what keeps the content of {@code tabId} (or the current tab if {@code null}) out of reach: a consent
     * banner or a challenge (captcha, login) the user has to solve in the visible browser. Does not change the page.
     */
    BrowserProbe probe(String tabId) throws BrowserException;

    /**
     * Ends this chat's browser process and connection; the profile stays. The next operation starts the browser
     * again with the same profile. Does nothing when no browser runs.
     */
    void closeBrowser();

    /** Whether a browser process of this chat runs right now (as far as the runtime knows). */
    boolean isRunning();
}
