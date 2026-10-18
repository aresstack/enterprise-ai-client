package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentException;
import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentTextExtractor;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.browser.api.BrowserAction;
import com.aresstack.enterpriseai.browser.api.BrowserActResult;
import com.aresstack.enterpriseai.browser.api.BrowserControl;
import com.aresstack.enterpriseai.browser.api.BrowserControls;
import com.aresstack.enterpriseai.browser.api.BrowserDocument;
import com.aresstack.enterpriseai.browser.api.BrowserException;
import com.aresstack.enterpriseai.browser.api.BrowserLink;
import com.aresstack.enterpriseai.browser.api.BrowserLinks;
import com.aresstack.enterpriseai.browser.api.BrowserPage;
import com.aresstack.enterpriseai.browser.api.BrowserProbe;
import com.aresstack.enterpriseai.browser.api.BrowserTab;
import com.aresstack.enterpriseai.browser.api.ChatBrowser;
import com.aresstack.enterpriseai.browser.api.ChatBrowserPort;
import com.aresstack.enterpriseai.proasteion.application.browser.WebExplorationProgress;
import com.aresstack.enterpriseai.proasteion.application.browser.WebExplorationRun;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * The browser tools of one chat (#141): {@value #TABS}, {@value #NAVIGATE}, {@value #READ}, {@value #LINKS},
 * {@value #FOLLOW}, {@value #BACK}, {@value #DOCUMENT}, {@value #CONTROLS}, {@value #ACT}, {@value #MARK} and {@value #CLOSE}. Every call records into the chat's
 * {@link WebExplorationRun}, the state behind the progress display. Links are followed by their snapshot id, never by a guessed
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
    public static final String MARK = "browser_mark";
    public static final String CONTROLS = "browser_controls";
    public static final String DOCUMENT = "browser_document";
    /** Address endings of links that lead to documents rather than pages. */
    private static final List<String> DOCUMENT_ENDINGS = Arrays.asList(".pdf", ".doc", ".docx", ".xls", ".xlsx",
            ".ppt", ".pptx", ".odt", ".ods", ".odp", ".rtf", ".txt", ".csv");
    public static final String ACT = "browser_act";
    static final String PARAM_CONTROL = "control";
    static final String PARAM_VALUE = "value";
    static final String PARAM_MAX_CONTROLS = "max_controls";
    static final int DEFAULT_MAX_CONTROLS = 60;
    /** Controls taken after a load to look for the site's search (search fields come first, buttons follow). */
    static final int SEARCH_HINT_CONTROLS = 40;
    private static final List<String> ACT_ACTIONS = Arrays.asList("click", "fill", "submit", "select");
    static final String PARAM_RELEVANT = "relevant";
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
    /** Text of downloaded documents through the document pipeline; {@code null}: no {@value #DOCUMENT}. */
    private final AttachmentTextExtractor documents;
    private final ConcurrentMap<ChatConversationId, WebExplorationRun> runs =
            new ConcurrentHashMap<ChatConversationId, WebExplorationRun>();

    public BrowserMcpTools(ChatBrowserPort browsers) {
        this(browsers, null);
    }

    /** With {@code documents} (may be null) linked PDFs and Office files are read through the document pipeline. */
    public BrowserMcpTools(ChatBrowserPort browsers, AttachmentTextExtractor documents) {
        if (browsers == null) {
            throw new IllegalArgumentException("browsers must not be null");
        }
        this.browsers = browsers;
        this.documents = documents;
    }

    /** The browser tools bound to {@code conversation}. */
    public List<McpToolContribution> contributionsFor(final ChatConversationId conversation) {
        if (conversation == null) {
            throw new IllegalArgumentException("conversation must not be null");
        }
        final ChatBrowser browser = browsers.browserOf(conversation);
        final WebExplorationRun run = runOf(conversation);
        return Collections.unmodifiableList(Arrays.asList(
                tabs(tracked(run, call -> tabs(browser, call))),
                navigate(tracked(run, call -> navigate(browser, run, call))),
                read(tracked(run, call -> read(browser, run, call))),
                links(tracked(run, call -> links(browser, run, call))),
                follow(tracked(run, call -> follow(browser, run, call))),
                back(tracked(run, call -> back(browser, run, call))),
                document(tracked(run, call -> document(browser, documents, run, call))),
                controls(tracked(run, call -> controls(browser, run, call))),
                act(tracked(run, call -> act(browser, run, call))),
                mark(tracked(run, call -> mark(run, call))),
                close(tracked(run, call -> close(browser, run)))));
    }

    /**
     * Starts recording the exploration of {@code conversation} for a new turn; {@code listener} (may be null) gets
     * every change, on the calling tool's thread.
     */
    public WebExplorationProgress beginTurn(ChatConversationId conversation,
                                            Consumer<WebExplorationProgress> listener) {
        return runOf(conversation).begin(listener);
    }

    /** Ends the turn's recording and returns its final state. */
    public WebExplorationProgress endTurn(ChatConversationId conversation) {
        return runOf(conversation).end();
    }

    private WebExplorationRun runOf(ChatConversationId conversation) {
        WebExplorationRun run = runs.get(conversation);
        if (run == null) {
            WebExplorationRun created = new WebExplorationRun();
            run = runs.putIfAbsent(conversation, created);
            if (run == null) {
                run = created;
            }
        }
        return run;
    }

    private static McpToolHandler tracked(final WebExplorationRun run, final McpToolHandler handler) {
        return call -> {
            run.started();
            try {
                return handler.invoke(call);
            } finally {
                run.finished();
            }
        };
    }

    /** The tools with handlers that refuse every call: for listing names, descriptions and schemas only. */
    public static List<McpToolContribution> unbound() {
        McpToolHandler refuse = call -> McpToolResult.error("Browser-Werkzeuge sind nur im Chat verfügbar");
        return Collections.unmodifiableList(Arrays.asList(tabs(refuse), navigate(refuse), read(refuse),
                links(refuse), follow(refuse), back(refuse), document(refuse), controls(refuse), act(refuse), mark(refuse),
                close(refuse)));
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
                        + "page title and the beginning of its text. Opens no new tab. Use it for addresses the user "
                        + "gave or that a page showed; never construct search or subpage addresses yourself: use the "
                        + "site's own search field and links (" + CONTROLS + ", " + ACT + ", " + LINKS + ").",
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
                        + "id | text | address {region}; the region (Navigation, Kopfbereich, Inhalt, Seitenleiste, "
                        + "Fußbereich) tells menus from content. Use it before visiting subpages instead of guessing addresses, then "
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

    private static McpToolContribution document(McpToolHandler handler) {
        return McpToolContribution.of(DOCUMENT,
                "Read a document a page links to (PDF, Word, Excel, PowerPoint, text; marked \"Dokument\" in "
                        + LINKS + ") by its link id, without leaving the page: downloaded with this chat's logins "
                        + "and converted to text like an attachment. Use it instead of " + FOLLOW + " for documents.",
                handler,
                McpToolParameter.string(PARAM_LINK, true, "Link id from " + LINKS + "."),
                McpToolParameter.string(PARAM_TAB, false, "Tab id; default the current tab."),
                McpToolParameter.integer(PARAM_MAX_CHARS, false, "Maximum characters of text (default "
                        + DEFAULT_MAX_CHARS + ")."));
    }

    private static McpToolContribution controls(McpToolHandler handler) {
        return McpToolContribution.of(CONTROLS,
                "List the controls of the page in a tab of this chat's browser that are not plain links: search and "
                        + "text fields, buttons, select boxes, menu entries, tabs and toggles (id | kind | label | "
                        + "value | locator | position), grouped by the form they belong to; * marks mandatory fields. "
                        + "To fill in a mask, find its form here, fill every mandatory field, then press its submit "
                        + "button. Search fields come first: when the site has its own search, prefer it over clicking "
                        + "through menus. Operate a control with " + ACT + ". The ids stay valid until a new page is "
                        + "loaded; after an action that only changed the page (menu opened), call " + CONTROLS
                        + " again to see new entries.",
                handler,
                McpToolParameter.string(PARAM_TAB, false, "Tab id; default the current tab."),
                McpToolParameter.string(PARAM_FILTER, false, "Optional word to keep matching controls only."),
                McpToolParameter.integer(PARAM_MAX_CONTROLS, false, "Maximum controls (default "
                        + DEFAULT_MAX_CONTROLS + ", at most " + BrowserControls.MAX_CONTROLS + ")."));
    }

    private static McpToolContribution act(McpToolHandler handler) {
        return McpToolContribution.of(ACT,
                "Operate a control of the last " + CONTROLS + " list and return the page once it settled (new page "
                        + "loaded or content changed). action: click (button, menu entry, tab, toggle, checkbox), "
                        + "fill (type value into a field), submit (type value into a search field and send it like "
                        + "Enter), select (choose the option named in value). Example: control=\"ctl-1\", "
                        + "action=\"submit\", value=\"Reisekostenabrechnung\".",
                handler,
                McpToolParameter.string(PARAM_CONTROL, true, "Control id from " + CONTROLS + "."),
                McpToolParameter.enumeration(PARAM_ACTION, true, "click, fill, submit or select.", ACT_ACTIONS),
                McpToolParameter.string(PARAM_VALUE, false, "Text for fill and submit, option for select."),
                McpToolParameter.string(PARAM_TAB, false, "Tab id; default the current tab."));
    }

    private static McpToolContribution close(McpToolHandler handler) {
        return McpToolContribution.of(CLOSE,
                "Close this chat's browser when the browsing task is done. Cookies and logins of the chat stay; "
                        + "the next browser tool starts it again.",
                handler);
    }

    private static McpToolContribution mark(McpToolHandler handler) {
        return McpToolContribution.of(MARK,
                "Mark the page currently open in this chat's browser as relevant for the answer (relevant=true, "
                        + "default) or as not relevant (relevant=false). Mark every page whose content you use; "
                        + "the user sees how many relevant pages were used.",
                handler,
                McpToolParameter.bool(PARAM_RELEVANT, false, "true (default) or false."));
    }

    static McpToolResult links(ChatBrowser browser, WebExplorationRun run, McpToolCall call) {
        int max = (int) Math.min(BrowserLinks.MAX_LINKS,
                Math.max(1L, call.getInteger(PARAM_MAX_LINKS, DEFAULT_MAX_LINKS)));
        String filter = blankToNull(call.getString(PARAM_FILTER));
        try {
            BrowserLinks links = browser.links(blankToNull(call.getString(PARAM_TAB)), max, filter);
            Map<String, String> shown = new LinkedHashMap<String, String>();
            for (BrowserLink link : links.links()) {
                shown.put(link.id(), link.url());
            }
            String text = render(links, filter, run);
            run.linksListed(shown, links.total(), filter);
            return McpToolResult.ok(text);
        } catch (BrowserException e) {
            run.failed(null, e.getMessage());
            return McpToolResult.error(e.getMessage());
        }
    }

    static McpToolResult follow(ChatBrowser browser, WebExplorationRun run, McpToolCall call) {
        String link = blankToNull(call.getString(PARAM_LINK));
        if (link == null) {
            return McpToolResult.error("link fehlt; Link-Ids liefert " + LINKS);
        }
        String target = run.linkTarget(link);
        try {
            return opened(browser, run, browser.follow(blankToNull(call.getString(PARAM_TAB)), link), false);
        } catch (BrowserException e) {
            return refused(run, target, e);
        }
    }

    static McpToolResult back(ChatBrowser browser, WebExplorationRun run, McpToolCall call) {
        try {
            return opened(browser, run, browser.back(blankToNull(call.getString(PARAM_TAB))), true);
        } catch (BrowserException e) {
            return refused(run, null, e);
        }
    }

    static McpToolResult document(ChatBrowser browser, AttachmentTextExtractor documents, WebExplorationRun run,
                                  McpToolCall call) {
        if (documents == null) {
            return McpToolResult.error("Dokumente lesen ist in dieser Umgebung nicht verfügbar");
        }
        String link = blankToNull(call.getString(PARAM_LINK));
        if (link == null) {
            return McpToolResult.error("link fehlt; Link-Ids liefert " + LINKS);
        }
        int maxChars = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, call.getInteger(PARAM_MAX_CHARS, DEFAULT_MAX_CHARS)));
        String target = run.linkTarget(link);
        BrowserDocument document;
        try {
            document = browser.download(blankToNull(call.getString(PARAM_TAB)), link);
        } catch (BrowserException e) {
            return refused(run, target, e);
        }
        String text;
        try {
            text = documents.extractText(document.fileName(), document.content());
        } catch (AttachmentException e) {
            run.failed(document.url(), e.getMessage());
            return McpToolResult.error(e.getMessage());
        }
        run.documentRead(document.url(), document.fileName());
        boolean cut = text.length() > maxChars;
        StringBuilder out = new StringBuilder();
        out.append("Dokument: ").append(document.fileName()).append('\n')
                .append("Adresse: ").append(document.url()).append("\n\n")
                .append(cut ? text.substring(0, maxChars) : text);
        if (cut) {
            out.append("\n[… gekürzt, ").append(text.length()).append(" Zeichen insgesamt]");
        }
        return McpToolResult.ok(out.toString());
    }

    /** Whether {@code url} leads to a document ({@value #DOCUMENT}) rather than a page, judged by its path. */
    static boolean isDocument(String url) {
        try {
            String path = new URI(url).getPath();
            String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
            for (String ending : DOCUMENT_ENDINGS) {
                if (lower.endsWith(ending)) {
                    return true;
                }
            }
        } catch (URISyntaxException ignored) {
            // Not a document then.
        }
        return false;
    }

    private static String regionName(String region) {
        if ("nav".equals(region)) {
            return "Navigation";
        }
        if ("header".equals(region)) {
            return "Kopfbereich";
        }
        if ("footer".equals(region)) {
            return "Fußbereich";
        }
        if ("aside".equals(region)) {
            return "Seitenleiste";
        }
        return "main".equals(region) ? "Inhalt" : "";
    }

    static McpToolResult controls(ChatBrowser browser, WebExplorationRun run, McpToolCall call) {
        int max = (int) Math.min(BrowserControls.MAX_CONTROLS,
                Math.max(1L, call.getInteger(PARAM_MAX_CONTROLS, DEFAULT_MAX_CONTROLS)));
        String filter = blankToNull(call.getString(PARAM_FILTER));
        try {
            BrowserControls controls = browser.controls(blankToNull(call.getString(PARAM_TAB)), max, filter);
            Map<String, String> labels = new LinkedHashMap<String, String>();
            for (BrowserControl control : controls.controls()) {
                labels.put(control.id(), control.label());
            }
            run.controlsListed(labels);
            return McpToolResult.ok(render(controls, filter));
        } catch (BrowserException e) {
            run.failed(null, e.getMessage());
            return McpToolResult.error(e.getMessage());
        }
    }

    static McpToolResult act(ChatBrowser browser, WebExplorationRun run, McpToolCall call) {
        String control = blankToNull(call.getString(PARAM_CONTROL));
        if (control == null) {
            return McpToolResult.error("control fehlt; Ids liefert " + CONTROLS);
        }
        String name = call.getString(PARAM_ACTION);
        name = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (!ACT_ACTIONS.contains(name)) {
            return McpToolResult.error("Unbekannte action " + name + "; erlaubt: " + ACT_ACTIONS);
        }
        BrowserAction action = BrowserAction.valueOf(name.toUpperCase(Locale.ROOT));
        String value = call.getString(PARAM_VALUE);
        if (value != null && value.trim().isEmpty() && action != BrowserAction.FILL) {
            value = null;
        }
        String label = run.controlLabel(control);
        String description = describe(action, label == null ? control : label, value);
        try {
            BrowserActResult result = browser.act(blankToNull(call.getString(PARAM_TAB)), control, action, value);
            run.acted(description);
            BrowserPage page = result.page();
            String search = "";
            if (result.navigated()) {
                run.opened(page.url(), page.title(), false);
                search = searchHint(browser, run, page.tabId());
            }
            String outcome = result.navigated() ? "Neue Seite geladen."
                    : result.changed() ? "Die Seite hat sich geändert (gleiche Seite). Neue Einträge zeigt "
                    + CONTROLS + " bzw. " + LINKS + "."
                    : "Keine sichtbare Änderung der Seite.";
            return McpToolResult.ok(description + ". " + outcome + "\n\n" + render(page, DEFAULT_MAX_CHARS)
                    + hints(browser, run, page.tabId()) + search);
        } catch (BrowserException e) {
            return refused(run, null, e);
        }
    }

    /** What the action did, for the progress display and the model ("Gesucht: „Reise“"). */
    static String describe(BrowserAction action, String label, String value) {
        switch (action) {
            case SUBMIT:
                return value == null ? "„" + label + "“ abgeschickt" : "Gesucht: „" + value + "“";
            case FILL:
                return "„" + label + "“ ausgefüllt";
            case SELECT:
                return "„" + value + "“ in „" + label + "“ gewählt";
            default:
                return "„" + label + "“ angeklickt";
        }
    }

    static McpToolResult mark(WebExplorationRun run, McpToolCall call) {
        boolean relevant = call.getBoolean(PARAM_RELEVANT, true);
        if (!run.markCurrent(relevant)) {
            return McpToolResult.error("Keine Seite geöffnet; zuerst eine Seite öffnen");
        }
        return McpToolResult.ok(relevant ? "Seite als relevant markiert." : "Seite als nicht relevant markiert.");
    }

    static McpToolResult close(ChatBrowser browser, WebExplorationRun run) {
        browser.closeBrowser();
        run.challenged("");
        run.closed();
        return McpToolResult.ok("Browser geschlossen. Das Profil des Chats bleibt erhalten.");
    }

    private static McpToolResult opened(ChatBrowser browser, WebExplorationRun run, BrowserPage page, boolean back) {
        run.opened(page.url(), page.title(), back);
        return McpToolResult.ok(render(page, DEFAULT_MAX_CHARS) + hints(browser, run, page.tabId())
                + searchHint(browser, run, page.tabId()));
    }

    /**
     * Points the model to the site's own search on a freshly loaded page, so it types into the field instead of
     * guessing a search address. Takes a small control snapshot (the page is new, so no earlier ids are lost); a page
     * without search or a failing snapshot adds nothing.
     */
    static String searchHint(ChatBrowser browser, WebExplorationRun run, String tabId) {
        BrowserControls controls;
        try {
            controls = browser.controls(tabId, SEARCH_HINT_CONTROLS, null);
        } catch (BrowserException e) {
            return "";
        }
        BrowserControl field = null;
        BrowserControl opener = null;
        for (BrowserControl control : controls.controls()) {
            if (field == null && "search".equals(control.kind())) {
                field = control;
            } else if (opener == null && "button".equals(control.kind())
                    && control.label().toLowerCase(Locale.ROOT).matches(".*(such|search).*")) {
                opener = control;
            }
        }
        if (field == null && opener == null) {
            return "";
        }
        Map<String, String> labels = new LinkedHashMap<String, String>();
        for (BrowserControl control : controls.controls()) {
            labels.put(control.id(), control.label());
        }
        run.controlsNoted(labels);
        if (field != null) {
            return "\n\n[Hinweis: Die Seite hat ein eigenes Suchfeld " + field.id() + " „" + field.label()
                    + "“. Suche damit (" + ACT + " control=\"" + field.id() + "\" action=\"submit\" value=\"…\") "
                    + "statt eine Such-Adresse zu raten.]";
        }
        return "\n\n[Hinweis: Die Seite hat eine Such-Schaltfläche " + opener.id() + " „" + opener.label()
                + "“. Öffne die Suche mit " + ACT + " action=\"click\", danach " + CONTROLS
                + " für das Suchfeld, statt eine Such-Adresse zu raten.]";
    }

    /**
     * What stands between the model and the content (consent banner, captcha, login), as advice appended to a page;
     * records a challenge so the browser stays open for the user. A failing probe adds nothing.
     */
    static String hints(ChatBrowser browser, WebExplorationRun run, String tabId) {
        BrowserProbe probe;
        try {
            probe = browser.probe(tabId);
        } catch (BrowserException e) {
            return "";
        }
        run.challenged(probe.challenge());
        StringBuilder out = new StringBuilder();
        if (probe.consent()) {
            out.append("\n\n[Hinweis: Cookie- bzw. Einwilligungsbanner auf der Seite (Schaltflächen: ")
                    .append(probe.consentOptions()).append("). Wähle mit ").append(CONTROLS).append(" und ")
                    .append(ACT).append(" die sparsamste Option, z. B. „Ablehnen“ oder „Nur notwendige“; stimme nie ")
                    .append("mehr zu als nötig.]");
        }
        if (BrowserProbe.CAPTCHA.equals(probe.challenge())) {
            out.append("\n\n[Hinweis: Prüfseite (Captcha oder Bot-Schutz). Nicht selbst lösen: Sag dem Benutzer, dass ")
                    .append("er sie im sichtbaren Firefox dieses Chats lösen soll, und arbeite danach weiter. Der ")
                    .append("Browser bleibt dafür nach der Antwort offen.]");
        } else if (BrowserProbe.LOGIN.equals(probe.challenge())) {
            out.append("\n\n[Hinweis: Die Seite verlangt eine Anmeldung. Passwörter gibst du nie ein: Bitte den ")
                    .append("Benutzer, sich im sichtbaren Firefox dieses Chats anzumelden; die Anmeldung bleibt im ")
                    .append("Profil des Chats. Der Browser bleibt dafür nach der Antwort offen.]");
        }
        return out.toString();
    }

    /** Whether the browser of {@code conversation} has to stay open after the turn: the user has a challenge to solve. */
    public boolean keepsBrowserOpen(ChatConversationId conversation) {
        return conversation != null && runOf(conversation).challengePending();
    }

    /** A refused target is skipped, any other failure failed; the model gets the reason either way. */
    private static McpToolResult refused(WebExplorationRun run, String target, BrowserException e) {
        if (e.kind() == BrowserException.Kind.BLOCKED) {
            run.skipped(target == null ? e.getMessage() : target);
        } else {
            run.failed(target, e.getMessage());
        }
        return McpToolResult.error(e.getMessage());
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

    static McpToolResult navigate(ChatBrowser browser, WebExplorationRun run, McpToolCall call) {
        String url = blankToNull(call.getString(PARAM_URL));
        if (url == null) {
            return McpToolResult.error("url fehlt");
        }
        String invalid = invalidUrl(url);
        if (invalid != null) {
            return McpToolResult.error(invalid);
        }
        try {
            return opened(browser, run, browser.navigate(blankToNull(call.getString(PARAM_TAB)), url), false);
        } catch (BrowserException e) {
            return refused(run, url, e);
        }
    }

    static McpToolResult read(ChatBrowser browser, WebExplorationRun run, McpToolCall call) {
        int maxChars = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, call.getInteger(PARAM_MAX_CHARS, DEFAULT_MAX_CHARS)));
        try {
            BrowserPage page = browser.read(blankToNull(call.getString(PARAM_TAB)));
            run.opened(page.url(), page.title(), false);
            return McpToolResult.ok(render(page, maxChars) + hints(browser, run, page.tabId()));
        } catch (BrowserException e) {
            run.failed(null, e.getMessage());
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

    private static String render(BrowserLinks links, String filter, WebExplorationRun run) {
        List<BrowserLink> shown = links.links();
        StringBuilder out = new StringBuilder();
        out.append("Tab: ").append(links.tabId()).append('\n')
                .append("Seite: ").append(links.pageTitle()).append(" | ").append(links.pageUrl()).append('\n');
        if (filter == null) {
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
            out.append(" | ").append(link.url());
            String region = regionName(link.region());
            if (!region.isEmpty()) {
                out.append(" {").append(region).append('}');
            }
            if (isDocument(link.url())) {
                out.append(" (Dokument → ").append(DOCUMENT).append(')');
            }
            if (run.wasVisited(link.url())) {
                out.append(" (schon besucht)");
            }
            out.append('\n');
        }
        if (shown.isEmpty()) {
            out.append("Keine Links gefunden.\n");
        }
        return out.toString().trim();
    }

    private static String render(BrowserControls controls, String filter) {
        List<BrowserControl> shown = controls.controls();
        StringBuilder out = new StringBuilder();
        out.append("Tab: ").append(controls.tabId()).append('\n')
                .append("Seite: ").append(controls.pageTitle()).append(" | ").append(controls.pageUrl()).append('\n')
                .append(shown.size()).append(" von ").append(controls.total()).append(" Bedienelementen");
        if (filter != null) {
            out.append(" passend zu \"").append(filter).append('"');
        }
        out.append(" · Ids gelten bis zum Laden einer neuen Seite · * Pflichtfeld\n");
        String form = null;
        for (BrowserControl control : shown) {
            // Controls of one form follow each other on the page; a heading per run shows the form as one mask.
            if (!control.form().equals(form)) {
                form = control.form();
                out.append('\n').append(form.isEmpty() ? "Ohne Formular:" : "Formular „" + form + "“:").append('\n');
            }
            out.append(control.id()).append(" | ").append(control.kind()).append(" | ")
                    .append(control.label().isEmpty() ? "(ohne Beschriftung)" : control.label());
            if (control.required()) {
                out.append(" *");
            }
            if (!control.value().isEmpty()) {
                out.append(" | Wert: ").append(control.value());
            }
            if (!control.options().isEmpty()) {
                out.append(" | Optionen: ").append(control.options());
            }
            if (!control.locator().isEmpty()) {
                out.append(" | ").append(control.locator());
            }
            if (!control.position().isEmpty()) {
                out.append(" | ").append(control.position());
            }
            out.append('\n');
        }
        if (shown.isEmpty()) {
            out.append("Keine Bedienelemente gefunden.\n");
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
