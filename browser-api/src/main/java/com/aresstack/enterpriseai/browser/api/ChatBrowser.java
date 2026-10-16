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
}
