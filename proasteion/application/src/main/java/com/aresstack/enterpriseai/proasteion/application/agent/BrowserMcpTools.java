package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.browser.api.BrowserException;
import com.aresstack.enterpriseai.browser.api.BrowserLink;
import com.aresstack.enterpriseai.browser.api.BrowserLinks;
import com.aresstack.enterpriseai.browser.api.BrowserPage;
import com.aresstack.enterpriseai.browser.api.BrowserTab;
import com.aresstack.enterpriseai.browser.api.ChatBrowser;
import com.aresstack.enterpriseai.browser.api.ChatBrowserPort;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The browser tools of one chat (#141): {@value #TABS}, {@value #NAVIGATE}, {@value #READ}, {@value #LINKS},
 * {@value #FOLLOW}, {@value #BACK} and {@value #CLOSE}. Links are followed by their snapshot id, never by a guessed
 * address; the ids of a page become invalid with the next navigation. The chat is bound when the
 * tools are built ({@link #contributionsFor}), by the host, never by a model argument: the schemas have no chat id,
 * port, profile path or user context, so a call can only reach the browser of its own chat. Tab ids are the chat-local
 * ids of {@link ChatBrowser}.
 */
public final class BrowserMcpTools {

    public static final String TABS = "browser_tabs";
    public static final String NAVIGATE = "browser_navigate";
    public static final String READ = "browser_read";
    public static final String LINKS = "browser_links";
    public static final String FOLLOW = "browser_follow";
    public static final String BACK = "browser_back";
    public static final String CLOSE = "browser_close";
    static final String PARAM_LINK = "link";
    static final String PARAM_FILTER = "filter";
    static final String PARAM_MAX_LINKS = "max_links";
    static final int DEFAULT_MAX_LINKS = 100;
    static final String PARAM_ACTION = "action";
    static final String PARAM_TAB = "tab";
    static final String PARAM_URL = "url";
    static final String PARAM_MAX_CHARS = "max_chars";
    static final int DEFAULT_MAX_CHARS = 8000;
    private static final List<String> ACTIONS = Arrays.asList("list", "select", "new", "close");

    private final ChatBrowserPort browsers;

    public BrowserMcpTools(ChatBrowserPort browsers) {
        if (browsers == null) {
            throw new IllegalArgumentException("browsers must not be null");
        }
        this.browsers = browsers;
    }

    /** The browser tools bound to {@code conversation}. */
    public List<McpToolContribution> contributionsFor(final ChatConversationId conversation) {
        if (conversation == null) {
            throw new IllegalArgumentException("conversation must not be null");
        }
        final ChatBrowser browser = browsers.browserOf(conversation);
        return Collections.unmodifiableList(Arrays.asList(
                tabs(call -> tabs(browser, call)),
                navigate(call -> navigate(browser, call)),
                read(call -> read(browser, call)),
                links(call -> links(browser, call)),
                follow(call -> follow(browser, call)),
                back(call -> back(browser, call)),
                close(call -> close(browser))));
    }

    /** The tools with handlers that refuse every call: for listing names, descriptions and schemas only. */
    public static List<McpToolContribution> unbound() {
        McpToolHandler refuse = call -> McpToolResult.error("Browser-Werkzeuge sind nur im Chat verfügbar");
        return Collections.unmodifiableList(Arrays.asList(tabs(refuse), navigate(refuse), read(refuse),
                links(refuse), follow(refuse), back(refuse), close(refuse)));
    }

    private static McpToolContribution tabs(McpToolHandler handler) {
        return McpToolContribution.of(TABS,
                "List, select, open or close tabs of this chat's own visible browser. Without action it lists the "
                        + "tabs; the current tab is marked. Example: action=\"select\", tab=\"tab-2\".",
                handler,
                McpToolParameter.enumeration(PARAM_ACTION, false, "list (default), select, new or close.", ACTIONS),
                McpToolParameter.string(PARAM_TAB, false, "Tab id from the list, for select and close."),
                McpToolParameter.string(PARAM_URL, false, "Optional http(s) address for a new tab."));
    }

    private static McpToolContribution navigate(McpToolHandler handler) {
        return McpToolContribution.of(NAVIGATE,
                "Open an http(s) address in this chat's browser (current tab unless tab is given) and return the "
                        + "page title and the beginning of its text. Opens no new tab.",
                handler,
                McpToolParameter.string(PARAM_URL, true, "The http(s) address."),
                McpToolParameter.string(PARAM_TAB, false, "Tab id; default the current tab."));
    }

    private static McpToolContribution read(McpToolHandler handler) {
        return McpToolContribution.of(READ,
                "Read the visible text of a tab in this chat's browser without navigating (current tab unless tab "
                        + "is given).",
                handler,
                McpToolParameter.string(PARAM_TAB, false, "Tab id; default the current tab."),
                McpToolParameter.integer(PARAM_MAX_CHARS, false, "Maximum characters of text (default "
                        + DEFAULT_MAX_CHARS + ")."));
    }

    private static McpToolContribution links(McpToolHandler handler) {
        return McpToolContribution.of(LINKS,
                "List the real links of the page in a tab of this chat's browser (current tab unless tab is given): "
                        + "id | text | address. Use it before visiting subpages instead of guessing addresses, then "
                        + "open a link with " + FOLLOW + ". The ids are valid only for this page: after any "
                        + "navigation call " + LINKS + " again. filter keeps only links whose text, label or "
                        + "address contains the word.",
                handler,
                McpToolParameter.string(PARAM_TAB, false, "Tab id; default the current tab."),
                McpToolParameter.string(PARAM_FILTER, false, "Optional word to keep matching links only."),
                McpToolParameter.integer(PARAM_MAX_LINKS, false, "Maximum links (default " + DEFAULT_MAX_LINKS
                        + ", at most " + BrowserLinks.MAX_LINKS + ")."));
    }

    private static McpToolContribution follow(McpToolHandler handler) {
        return McpToolContribution.of(FOLLOW,
                "Open a link of the last " + LINKS + " list by its id (e.g. link=\"link-7\") in the same tab and "
                        + "return the page title and the beginning of its text. Use " + BACK + " to return.",
                handler,
                McpToolParameter.string(PARAM_LINK, true, "Link id from " + LINKS + "."),
                McpToolParameter.string(PARAM_TAB, false, "Tab id; default the current tab."));
    }

    private static McpToolContribution back(McpToolHandler handler) {
        return McpToolContribution.of(BACK,
                "Go one page back in the history of a tab of this chat's browser and return that page.",
                handler,
                McpToolParameter.string(PARAM_TAB, false, "Tab id; default the current tab."));
    }

    private static McpToolContribution close(McpToolHandler handler) {
        return McpToolContribution.of(CLOSE,
                "Close this chat's browser when the browsing task is done. Cookies and logins of the chat stay; "
                        + "the next browser tool starts it again.",
                handler);
    }

    static McpToolResult links(ChatBrowser browser, McpToolCall call) {
        int max = (int) Math.min(BrowserLinks.MAX_LINKS,
                Math.max(1L, call.getInteger(PARAM_MAX_LINKS, DEFAULT_MAX_LINKS)));
        String filter = blankToNull(call.getString(PARAM_FILTER));
        try {
            BrowserLinks links = browser.links(blankToNull(call.getString(PARAM_TAB)),
                    filter == null ? max : BrowserLinks.MAX_LINKS);
            return McpToolResult.ok(render(links, filter, max));
        } catch (BrowserException e) {
            return McpToolResult.error(e.getMessage());
        }
    }

    static McpToolResult follow(ChatBrowser browser, McpToolCall call) {
        String link = blankToNull(call.getString(PARAM_LINK));
        if (link == null) {
            return McpToolResult.error("link fehlt; Link-Ids liefert " + LINKS);
        }
        try {
            return McpToolResult.ok(render(browser.follow(blankToNull(call.getString(PARAM_TAB)), link),
                    DEFAULT_MAX_CHARS));
        } catch (BrowserException e) {
            return McpToolResult.error(e.getMessage());
        }
    }

    static McpToolResult back(ChatBrowser browser, McpToolCall call) {
        try {
            return McpToolResult.ok(render(browser.back(blankToNull(call.getString(PARAM_TAB))), DEFAULT_MAX_CHARS));
        } catch (BrowserException e) {
            return McpToolResult.error(e.getMessage());
        }
    }

    static McpToolResult close(ChatBrowser browser) {
        browser.closeBrowser();
        return McpToolResult.ok("Browser geschlossen. Das Profil des Chats bleibt erhalten.");
    }

    static McpToolResult tabs(ChatBrowser browser, McpToolCall call) {
        String action = call.getString(PARAM_ACTION);
        action = action == null || action.trim().isEmpty() ? "list" : action.trim().toLowerCase(Locale.ROOT);
        String tab = blankToNull(call.getString(PARAM_TAB));
        try {
            if ("list".equals(action)) {
                return McpToolResult.ok(render(browser.tabs()));
            }
            if ("select".equals(action)) {
                if (tab == null) {
                    return McpToolResult.error("select braucht tab");
                }
                browser.selectTab(tab);
                return McpToolResult.ok(render(browser.tabs()));
            }
            if ("new".equals(action)) {
                String url = blankToNull(call.getString(PARAM_URL));
                if (url != null) {
                    String invalid = invalidUrl(url);
                    if (invalid != null) {
                        return McpToolResult.error(invalid);
                    }
                }
                browser.newTab(url);
                return McpToolResult.ok(render(browser.tabs()));
            }
            if ("close".equals(action)) {
                if (tab == null) {
                    return McpToolResult.error("close braucht tab");
                }
                browser.closeTab(tab);
                return McpToolResult.ok(render(browser.tabs()));
            }
            return McpToolResult.error("Unbekannte action " + action + "; erlaubt: " + ACTIONS);
        } catch (BrowserException e) {
            return McpToolResult.error(e.getMessage());
        }
    }

    static McpToolResult navigate(ChatBrowser browser, McpToolCall call) {
        String url = blankToNull(call.getString(PARAM_URL));
        if (url == null) {
            return McpToolResult.error("url fehlt");
        }
        String invalid = invalidUrl(url);
        if (invalid != null) {
            return McpToolResult.error(invalid);
        }
        try {
            return McpToolResult.ok(render(browser.navigate(blankToNull(call.getString(PARAM_TAB)), url),
                    DEFAULT_MAX_CHARS));
        } catch (BrowserException e) {
            return McpToolResult.error(e.getMessage());
        }
    }

    static McpToolResult read(ChatBrowser browser, McpToolCall call) {
        int maxChars = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, call.getInteger(PARAM_MAX_CHARS, DEFAULT_MAX_CHARS)));
        try {
            return McpToolResult.ok(render(browser.read(blankToNull(call.getString(PARAM_TAB))), maxChars));
        } catch (BrowserException e) {
            return McpToolResult.error(e.getMessage());
        }
    }

    /** {@code null} for an absolute http(s) address, else the reason; no file:, javascript: or data: pages. */
    static String invalidUrl(String url) {
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (("http".equals(scheme) || "https".equals(scheme)) && uri.getHost() != null) {
                return null;
            }
        } catch (URISyntaxException e) {
            return "Ungültige Adresse: " + url;
        }
        return "Nur http- und https-Adressen sind erlaubt: " + url;
    }

    private static String render(List<BrowserTab> tabs) {
        if (tabs.isEmpty()) {
            return "Keine Tabs offen.";
        }
        StringBuilder out = new StringBuilder();
        for (BrowserTab tab : tabs) {
            out.append(tab.isCurrent() ? "* " : "  ").append(tab.id()).append(" | ")
                    .append(tab.title().isEmpty() ? "(ohne Titel)" : tab.title()).append(" | ").append(tab.url())
                    .append('\n');
        }
        return out.append("(* = aktueller Tab)").toString();
    }

    private static String render(BrowserLinks links, String filter, int max) {
        List<BrowserLink> shown = new ArrayList<BrowserLink>();
        String needle = filter == null ? null : filter.toLowerCase(Locale.ROOT);
        for (BrowserLink link : links.links()) {
            if (shown.size() >= max) {
                break;
            }
            if (needle == null || (link.text() + " " + link.label() + " " + link.url()).toLowerCase(Locale.ROOT)
                    .contains(needle)) {
                shown.add(link);
            }
        }
        StringBuilder out = new StringBuilder();
        out.append("Tab: ").append(links.tabId()).append('\n')
                .append("Seite: ").append(links.pageTitle()).append(" | ").append(links.pageUrl()).append('\n');
        if (needle == null) {
            out.append(shown.size()).append(" von ").append(links.total()).append(" Links");
        } else {
            out.append(shown.size()).append(" Links passend zu \"").append(filter).append("\" (von ")
                    .append(links.total()).append(" Links)");
        }
        out.append(" · Ids gelten nur für diese Seite\n\n");
        for (BrowserLink link : shown) {
            out.append(link.id()).append(" | ").append(link.text().isEmpty() ? "(ohne Text)" : link.text());
            if (!link.label().isEmpty()) {
                out.append(" [").append(link.label()).append(']');
            }
            out.append(" | ").append(link.url()).append('\n');
        }
        if (shown.isEmpty()) {
            out.append("Keine Links gefunden.\n");
        }
        return out.toString().trim();
    }

    private static String render(BrowserPage page, int maxChars) {
        String text = page.text();
        boolean cut = text.length() > maxChars;
        StringBuilder out = new StringBuilder();
        out.append("Tab: ").append(page.tabId()).append('\n')
                .append("Titel: ").append(page.title()).append('\n')
                .append("Adresse: ").append(page.url()).append("\n\n")
                .append(cut ? text.substring(0, maxChars) : text);
        if (cut) {
            out.append("\n[… gekürzt, ").append(text.length()).append(" Zeichen insgesamt]");
        }
        return out.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
