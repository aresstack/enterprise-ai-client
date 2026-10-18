package com.aresstack.enterpriseai.browser.runtime;

import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.browser.api.BrowserAccessGuard;
import com.aresstack.enterpriseai.browser.api.BrowserAction;
import com.aresstack.enterpriseai.browser.api.BrowserActResult;
import com.aresstack.enterpriseai.browser.api.BrowserControl;
import com.aresstack.enterpriseai.browser.api.BrowserControls;
import com.aresstack.enterpriseai.browser.api.BrowserException;
import com.aresstack.enterpriseai.browser.api.BrowserLink;
import com.aresstack.enterpriseai.browser.api.BrowserLinks;
import com.aresstack.enterpriseai.browser.api.BrowserPage;
import com.aresstack.enterpriseai.browser.api.BrowserTab;
import com.aresstack.enterpriseai.browser.api.ChatBrowser;
import com.aresstack.enterpriseai.browser.api.ChatBrowserPort;
import com.aresstack.enterpriseai.browser.api.engine.BrowserConnection;
import com.aresstack.enterpriseai.browser.api.engine.BrowserEngine;
import com.aresstack.enterpriseai.browser.api.engine.BrowserInstance;
import com.aresstack.enterpriseai.browser.api.engine.RawActOutcome;
import com.aresstack.enterpriseai.browser.api.engine.RawControl;
import com.aresstack.enterpriseai.browser.api.engine.RawLink;
import com.aresstack.enterpriseai.browser.api.engine.RawPage;
import com.aresstack.enterpriseai.browser.api.engine.RawTab;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * One browser per chat over a {@link BrowserEngine}.
 *
 * <ul>
 *     <li>Lazy: the process starts on the first operation of the chat, not when the chat list opens.</li>
 *     <li>Healthy connection: reused as is, no new process and no new session.</li>
 *     <li>Lost connection, process alive: reconnect to the same process; tab count and focus stay.</li>
 *     <li>Process gone (closed by the user, crashed): normal start on the same profile.</li>
 *     <li>All operations of a chat are serialized, so concurrent first calls start exactly one browser;
 *         different chats never share a lock, a process or a tab.</li>
 *     <li>Reading operations are retried once after recovery; tab creation and closing are not.</li>
 *     <li>Every navigation passes the {@link BrowserAccessGuard} before it reaches the engine, and the final
 *         address after redirects passes it again; a blocked redirect target leaves the tab on about:blank.
 *         The guard is called while holding only the per-chat slot monitor (it may wait for the user), so
 *         other chats and the UI are never blocked by it. Blocked calls are never retried.</li>
 *     <li>Links are snapshots per tab: {@link ChatBrowser#links} numbers the anchors of the current page, and every
 *         navigation of the tab (navigate, follow, back, closing, a restart, a page change or reload by the user)
 *         invalidates them. {@link ChatBrowser#follow} only opens addresses of that snapshot, through the guard.</li>
 *     <li>{@link ChatBrowser#back} opens the page the tab showed before the last navigation through this runtime,
 *         through the guard before loading; the browser's own history is not replayed.</li>
 *     <li>Controls are snapshots per tab as well: {@link ChatBrowser#controls} numbers fields, buttons and menus of
 *         the current document; loading a new document invalidates them, a single page app may keep them as long as
 *         the element stays. {@link ChatBrowser#act} admits a form's declared target before submitting and the
 *         address reached afterwards again; an action that loads a new page counts as a navigation for back.</li>
 *     <li>{@link ChatBrowser#closeBrowser} ends process and connection but keeps the slot: the next operation starts
 *         the browser again on the same profile.</li>
 * </ul>
 */
public final class ChatBrowserRuntime implements ChatBrowserPort {

    private final BrowserEngine engine;
    private final BrowserProfileStore profiles;
    private final BrowserAccessGuard guard;
    private static final int MAX_LINK_TEXT = 160;
    private static final int MAX_HISTORY = 50;

    private final ConcurrentMap<ChatConversationId, Slot> slots = new ConcurrentHashMap<ChatConversationId, Slot>();

    public ChatBrowserRuntime(BrowserEngine engine, BrowserProfileStore profiles) {
        this(engine, profiles, BrowserAccessGuard.allowAll());
    }

    public ChatBrowserRuntime(BrowserEngine engine, BrowserProfileStore profiles, BrowserAccessGuard guard) {
        if (engine == null || profiles == null || guard == null) {
            throw new IllegalArgumentException("engine, profiles and guard must not be null");
        }
        this.engine = engine;
        this.profiles = profiles;
        this.guard = guard;
    }

    @Override
    public ChatBrowser browserOf(ChatConversationId conversation) {
        if (conversation == null) {
            throw new IllegalArgumentException("conversation must not be null");
        }
        Slot slot = slots.get(conversation);
        if (slot == null) {
            Slot created = new Slot(conversation);
            slot = slots.putIfAbsent(conversation, created);
            if (slot == null) {
                slot = created;
            }
        }
        return slot;
    }

    @Override
    public void release(ChatConversationId conversation) {
        Slot slot = conversation == null ? null : slots.remove(conversation);
        if (slot != null) {
            slot.shutdown();
        }
    }

    @Override
    public void deleteProfile(ChatConversationId conversation) throws BrowserException {
        release(conversation);
        profiles.delete(conversation);
    }

    @Override
    public void releaseAll() {
        for (ChatConversationId conversation : new ArrayList<ChatConversationId>(slots.keySet())) {
            release(conversation);
        }
    }

    private interface Operation<T> {
        T run(BrowserConnection connection) throws BrowserException;
    }

    /** The browser of one chat; every method holds the slot's monitor. */
    private final class Slot implements ChatBrowser {

        private final ChatConversationId conversation;
        private BrowserInstance instance;
        private BrowserConnection connection;
        /** Raw context id to chat-local tab id; valid as long as the process lives. */
        private final Map<String, String> tabIds = new LinkedHashMap<String, String>();
        private int nextTab = 1;
        private String currentRaw;
        private boolean closed;
        /** Raw context id to the tab's current link snapshot. */
        private final Map<String, LinkSnapshot> snapshots = new HashMap<String, LinkSnapshot>();
        /** Raw context id to the tab's current control snapshot. */
        private final Map<String, ControlSnapshot> controlSnapshots = new HashMap<String, ControlSnapshot>();
        /** Raw context id to the pages the tab showed before, newest first (targets of back). */
        private final Map<String, java.util.Deque<String>> history = new HashMap<String, java.util.Deque<String>>();

        Slot(ChatConversationId conversation) {
            this.conversation = conversation;
        }

        @Override
        public synchronized List<BrowserTab> tabs() throws BrowserException {
            return call(this::listTabs, true);
        }

        @Override
        public synchronized BrowserTab selectTab(final String tabId) throws BrowserException {
            return call(connection -> {
                String raw = rawOf(connection, tabId);
                connection.activate(raw);
                currentRaw = raw;
                return tabOf(connection, raw);
            }, true);
        }

        @Override
        public synchronized BrowserTab newTab(final String url) throws BrowserException {
            if (url != null) {
                guard.admit(conversation, url);
            }
            return call(connection -> {
                String raw = connection.createTab();
                idOf(raw);
                currentRaw = raw;
                if (url != null) {
                    connection.navigate(raw, url);
                    checkFinal(connection, raw, connection.read(raw).url());
                }
                return tabOf(connection, raw);
            }, false);
        }

        @Override
        public synchronized void closeTab(final String tabId) throws BrowserException {
            call(connection -> {
                String raw = rawOf(connection, tabId);
                connection.close(raw);
                tabIds.remove(raw);
                snapshots.remove(raw);
                controlSnapshots.remove(raw);
                history.remove(raw);
                if (raw.equals(currentRaw)) {
                    currentRaw = null;
                }
                return null;
            }, false);
        }

        @Override
        public synchronized BrowserPage navigate(final String tabId, final String url) throws BrowserException {
            guard.admit(conversation, url);
            return call(connection -> {
                String raw = tabId == null ? currentOrNew(connection) : rawOf(connection, tabId);
                snapshots.remove(raw);
                remember(connection, raw);
                connection.navigate(raw, url);
                BrowserPage page = page(connection, raw);
                checkFinal(connection, raw, page.url());
                return page;
            }, true);
        }

        @Override
        public synchronized BrowserPage read(final String tabId) throws BrowserException {
            return call(connection -> {
                String raw = tabId == null ? currentOrFail(connection) : rawOf(connection, tabId);
                return page(connection, raw);
            }, true);
        }

        @Override
        public synchronized BrowserLinks links(final String tabId, final int maxLinks, final String filter)
                throws BrowserException {
            final String needle = filter == null || filter.trim().isEmpty() ? null
                    : filter.trim().toLowerCase(Locale.ROOT);
            final int limit = Math.max(1, Math.min(BrowserLinks.MAX_LINKS, maxLinks));
            return call(connection -> {
                String raw = tabId == null ? currentOrFail(connection) : rawOf(connection, tabId);
                RawTab tab = rawTab(connection, raw);
                String document = connection.documentToken(raw);
                List<RawLink> anchors = connection.links(raw);
                String page = withoutFragment(tab.url());
                Map<String, String[]> usable = new LinkedHashMap<String, String[]>();
                for (RawLink anchor : anchors) {
                    String url = usableUrl(anchor.href());
                    if (url == null || withoutFragment(url).equals(page)) {
                        continue;
                    }
                    String text = clip(anchor.text());
                    String label = clip(anchor.title().isEmpty() ? anchor.ariaLabel() : anchor.title());
                    if (needle != null && !(text + " " + label + " " + url).toLowerCase(Locale.ROOT).contains(needle)) {
                        continue;
                    }
                    String[] known = usable.get(url);
                    if (known == null) {
                        usable.put(url, new String[]{text, label.equals(text) ? "" : label});
                    } else if (known[0].isEmpty()) {
                        known[0] = text;
                    }
                }
                LinkSnapshot snapshot = new LinkSnapshot(page, document);
                List<BrowserLink> links = new ArrayList<BrowserLink>(Math.min(limit, usable.size()));
                for (Map.Entry<String, String[]> entry : usable.entrySet()) {
                    if (links.size() >= limit) {
                        break;
                    }
                    String id = "link-" + (links.size() + 1);
                    snapshot.urls.put(id, entry.getKey());
                    links.add(new BrowserLink(id, entry.getValue()[0], entry.getKey(), entry.getValue()[1]));
                }
                snapshots.put(raw, snapshot);
                return new BrowserLinks(idOf(raw), tab.url(), tab.title(), links, usable.size());
            }, true);
        }

        @Override
        public synchronized BrowserPage follow(final String tabId, final String linkId) throws BrowserException {
            return call(connection -> {
                String raw = tabId == null ? currentOrFail(connection) : rawOf(connection, tabId);
                LinkSnapshot snapshot = snapshots.get(raw);
                if (snapshot == null) {
                    throw new BrowserException(BrowserException.Kind.INVALID_REQUEST,
                            "Keine gültige Linkliste für diesen Tab; zuerst browser_links aufrufen");
                }
                if (!withoutFragment(rawTab(connection, raw).url()).equals(snapshot.pageUrl)
                        || !connection.documentToken(raw).equals(snapshot.document)) {
                    snapshots.remove(raw);
                    throw new BrowserException(BrowserException.Kind.INVALID_REQUEST,
                            "Die Seite hat sich seit browser_links geändert; die Link-Ids gelten nicht mehr. "
                                    + "browser_links erneut aufrufen");
                }
                String url = linkId == null ? null : snapshot.urls.get(linkId.trim());
                if (url == null) {
                    throw new BrowserException(BrowserException.Kind.INVALID_REQUEST, "Unbekannte Link-Id " + linkId
                            + "; Link-Ids gelten nur für die zuletzt mit browser_links gelieferte Liste");
                }
                guard.admit(conversation, url);
                snapshots.remove(raw);
                remember(connection, raw);
                connection.navigate(raw, url);
                BrowserPage page = page(connection, raw);
                checkFinal(connection, raw, page.url());
                return page;
            }, false);
        }

        @Override
        public synchronized BrowserPage back(final String tabId) throws BrowserException {
            return call(connection -> {
                String raw = tabId == null ? currentOrFail(connection) : rawOf(connection, tabId);
                java.util.Deque<String> past = history.get(raw);
                if (past == null || past.isEmpty()) {
                    throw new BrowserException(BrowserException.Kind.INVALID_REQUEST,
                            "Keine vorherige Seite in diesem Tab");
                }
                // The target is known before loading, so it passes the guard first like every other navigation;
                // the browser's own history (which may contain pages the user opened) is never replayed unchecked.
                String url = past.peek();
                guard.admit(conversation, url);
                past.pop();
                snapshots.remove(raw);
                connection.navigate(raw, url);
                BrowserPage page = page(connection, raw);
                checkFinal(connection, raw, page.url());
                return page;
            }, false);
        }

        @Override
        public synchronized BrowserControls controls(final String tabId, final int maxControls, final String filter)
                throws BrowserException {
            final String needle = filter == null || filter.trim().isEmpty() ? null
                    : filter.trim().toLowerCase(Locale.ROOT);
            final int limit = Math.max(1, Math.min(BrowserControls.MAX_CONTROLS, maxControls));
            return call(connection -> {
                String raw = tabId == null ? currentOrFail(connection) : rawOf(connection, tabId);
                RawTab tab = rawTab(connection, raw);
                String document = connection.documentToken(raw);
                List<RawControl> search = new ArrayList<RawControl>();
                List<RawControl> rest = new ArrayList<RawControl>();
                for (RawControl control : connection.controls(raw)) {
                    String label = clip(control.label());
                    if (needle != null && !(label + " " + control.kind() + " " + control.value())
                            .toLowerCase(Locale.ROOT).contains(needle)) {
                        continue;
                    }
                    ("search".equals(control.kind()) ? search : rest).add(control);
                }
                search.addAll(rest);
                ControlSnapshot snapshot = new ControlSnapshot(document);
                List<BrowserControl> controls = new ArrayList<BrowserControl>(Math.min(limit, search.size()));
                for (RawControl control : search) {
                    if (controls.size() >= limit) {
                        break;
                    }
                    String id = "ctl-" + (controls.size() + 1);
                    String target = usableUrl(control.target());
                    snapshot.controls.put(id, new RawControl(control.ref(), control.kind(), control.label(),
                            control.value(), control.options(), target == null ? "" : target));
                    controls.add(new BrowserControl(id, control.kind(), clip(control.label()), clip(control.value()),
                            control.options(), target));
                }
                controlSnapshots.put(raw, snapshot);
                return new BrowserControls(idOf(raw), tab.url(), tab.title(), controls, search.size());
            }, true);
        }

        @Override
        public synchronized BrowserActResult act(final String tabId, final String controlId,
                                                 final BrowserAction action, final String value)
                throws BrowserException {
            if (action == null) {
                throw new BrowserException(BrowserException.Kind.INVALID_REQUEST, "Aktion fehlt");
            }
            if ((action == BrowserAction.FILL || action == BrowserAction.SELECT) && value == null) {
                throw new BrowserException(BrowserException.Kind.INVALID_REQUEST,
                        action == BrowserAction.FILL ? "fill braucht einen Wert" : "select braucht eine Option");
            }
            return call(connection -> {
                String raw = tabId == null ? currentOrFail(connection) : rawOf(connection, tabId);
                ControlSnapshot snapshot = controlSnapshots.get(raw);
                if (snapshot == null) {
                    throw new BrowserException(BrowserException.Kind.INVALID_REQUEST,
                            "Keine gültige Liste der Bedienelemente für diesen Tab; zuerst browser_controls aufrufen");
                }
                if (!connection.documentToken(raw).equals(snapshot.document)) {
                    controlSnapshots.remove(raw);
                    throw new BrowserException(BrowserException.Kind.INVALID_REQUEST,
                            "Seit browser_controls wurde eine neue Seite geladen; die Ids gelten nicht mehr. "
                                    + "browser_controls erneut aufrufen");
                }
                RawControl control = controlId == null ? null : snapshot.controls.get(controlId.trim());
                if (control == null) {
                    throw new BrowserException(BrowserException.Kind.INVALID_REQUEST, "Unbekannte Id " + controlId
                            + "; Ids gelten nur für die zuletzt mit browser_controls gelieferte Liste");
                }
                // A form that declares where it submits to is vetted before anything is sent there; script driven
                // targets are only known afterwards and pass the guard as the final address.
                if (!control.target().isEmpty() && (action == BrowserAction.SUBMIT
                        || (action == BrowserAction.CLICK && "button".equals(control.kind())))) {
                    guard.admit(conversation, control.target());
                }
                String before = rawTab(connection, raw).url();
                RawActOutcome outcome = connection.act(raw, control.ref(), action, value);
                // Same document or a new one: the page's links may have changed either way.
                snapshots.remove(raw);
                BrowserPage page = page(connection, raw);
                if (outcome.navigated()) {
                    controlSnapshots.remove(raw);
                }
                if (outcome.navigated() || !withoutFragment(page.url()).equals(withoutFragment(before))) {
                    push(raw, before);
                    checkFinal(connection, raw, page.url());
                }
                return new BrowserActResult(page, outcome.navigated(), outcome.changed());
            }, false);
        }

        @Override
        public synchronized void closeBrowser() {
            dropConnection();
            stopInstance();
        }

        @Override
        public synchronized boolean isRunning() {
            return instance != null && instance.isAlive();
        }

        /** Remembers the page the tab shows now as the target of a later {@link #back}. */
        private void remember(BrowserConnection connection, String raw) throws BrowserException {
            push(raw, rawTab(connection, raw).url());
        }

        private void push(String raw, String url) {
            if (usableUrl(url) == null) {
                return;
            }
            java.util.Deque<String> past = history.get(raw);
            if (past == null) {
                past = new java.util.ArrayDeque<String>();
                history.put(raw, past);
            }
            if (!url.equals(past.peek())) {
                past.push(url);
            }
            while (past.size() > MAX_HISTORY) {
                past.removeLast();
            }
        }

        private RawTab rawTab(BrowserConnection connection, String raw) throws BrowserException {
            for (RawTab tab : connection.tabs()) {
                if (tab.contextId().equals(raw)) {
                    return tab;
                }
            }
            throw new BrowserException(BrowserException.Kind.INVALID_REQUEST, "Der Tab ist nicht mehr offen");
        }

        /** Admits the address reached after redirects; a refused one leaves the tab on about:blank. */
        private void checkFinal(BrowserConnection connection, String raw, String finalUrl) throws BrowserException {
            try {
                guard.admit(conversation, finalUrl);
            } catch (BrowserException e) {
                if (e.kind() == BrowserException.Kind.BLOCKED) {
                    connection.navigate(raw, "about:blank");
                }
                throw e;
            }
        }

        synchronized void shutdown() {
            closed = true;
            dropConnection();
            stopInstance();
        }

        private <T> T call(Operation<T> operation, boolean repeatable) throws BrowserException {
            if (closed) {
                throw new BrowserException(BrowserException.Kind.INVALID_REQUEST, "Der Browser dieses Chats ist beendet");
            }
            ensureConnected();
            try {
                return operation.run(connection);
            } catch (BrowserException first) {
                if (!first.isRecoverable()) {
                    throw first;
                }
                recover(first);
                if (!repeatable) {
                    throw new BrowserException(first.kind(), "Verbindung zum Browser verloren; die Aktion wurde nicht "
                            + "wiederholt, weil sie schon gewirkt haben kann. Bitte Zustand mit browser_tabs prüfen.",
                            first);
                }
                ensureConnected();
                try {
                    return operation.run(connection);
                } catch (BrowserException second) {
                    if (second.isRecoverable()) {
                        recover(second);
                    }
                    throw second;
                }
            }
        }

        private void ensureConnected() throws BrowserException {
            if (connection != null && connection.isOpen()) {
                return;
            }
            dropConnection();
            if (instance != null && instance.isAlive()) {
                try {
                    connection = instance.connect();
                    return;
                } catch (BrowserException e) {
                    if (!e.isRecoverable()) {
                        throw e;
                    }
                    // Reconnect failed although the process seemed alive: fall through to a restart.
                }
            }
            stopInstance();
            instance = engine.start(profiles.profileOf(conversation));
            try {
                connection = instance.connect();
            } catch (BrowserException e) {
                stopInstance();
                throw e;
            }
        }

        private void recover(BrowserException failure) {
            dropConnection();
            if (failure.kind() == BrowserException.Kind.BROWSER_CLOSED) {
                stopInstance();
            }
        }

        private void dropConnection() {
            if (connection != null) {
                connection.disconnect();
                connection = null;
            }
        }

        private void stopInstance() {
            if (instance != null) {
                instance.stop();
                instance = null;
            }
            // New process, new raw context ids: the old tab ids no longer mean anything.
            tabIds.clear();
            snapshots.clear();
            controlSnapshots.clear();
            history.clear();
            currentRaw = null;
        }

        private List<BrowserTab> listTabs(BrowserConnection connection) throws BrowserException {
            List<RawTab> raw = sync(connection);
            List<BrowserTab> tabs = new ArrayList<BrowserTab>(raw.size());
            for (RawTab tab : raw) {
                tabs.add(new BrowserTab(idOf(tab.contextId()), tab.title(), tab.url(),
                        tab.contextId().equals(currentRaw)));
            }
            return tabs;
        }

        /** Reads the tree, forgets closed tabs, numbers new ones and settles the current tab. */
        private List<RawTab> sync(BrowserConnection connection) throws BrowserException {
            List<RawTab> raw = connection.tabs();
            Set<String> present = new HashSet<String>();
            for (RawTab tab : raw) {
                present.add(tab.contextId());
                idOf(tab.contextId());
            }
            tabIds.keySet().retainAll(present);
            if (currentRaw == null || !present.contains(currentRaw)) {
                currentRaw = raw.isEmpty() ? null : raw.get(0).contextId();
            }
            return raw;
        }

        private String idOf(String raw) {
            String id = tabIds.get(raw);
            if (id == null) {
                id = "tab-" + nextTab++;
                tabIds.put(raw, id);
            }
            return id;
        }

        private String rawOf(BrowserConnection connection, String tabId) throws BrowserException {
            sync(connection);
            for (Map.Entry<String, String> entry : tabIds.entrySet()) {
                if (entry.getValue().equals(tabId)) {
                    return entry.getKey();
                }
            }
            throw new BrowserException(BrowserException.Kind.INVALID_REQUEST,
                    "Unbekannter Tab " + tabId + "; aktuelle Tabs liefert browser_tabs");
        }

        private String currentOrFail(BrowserConnection connection) throws BrowserException {
            sync(connection);
            if (currentRaw == null) {
                throw new BrowserException(BrowserException.Kind.INVALID_REQUEST,
                        "Kein Tab offen; zuerst browser_navigate aufrufen");
            }
            return currentRaw;
        }

        /** The current tab; only a browser without any tab gets a new one. */
        private String currentOrNew(BrowserConnection connection) throws BrowserException {
            sync(connection);
            if (currentRaw == null) {
                currentRaw = connection.createTab();
                idOf(currentRaw);
            }
            return currentRaw;
        }

        private BrowserTab tabOf(BrowserConnection connection, String raw) throws BrowserException {
            for (BrowserTab tab : listTabs(connection)) {
                if (tab.id().equals(tabIds.get(raw))) {
                    return tab;
                }
            }
            return new BrowserTab(idOf(raw), "", "", raw.equals(currentRaw));
        }

        private BrowserPage page(BrowserConnection connection, String raw) throws BrowserException {
            RawPage page = connection.read(raw);
            return new BrowserPage(idOf(raw), page.url(), page.title(), page.text());
        }
    }

    /** The link ids of one page of one tab. */
    private static final class LinkSnapshot {

        final String pageUrl;
        final String document;
        final Map<String, String> urls = new HashMap<String, String>();

        LinkSnapshot(String pageUrl, String document) {
            this.pageUrl = pageUrl;
            this.document = document;
        }
    }

    /** The controls of one document of one tab: snapshot id to the engine's control (target already vetted as url). */
    private static final class ControlSnapshot {

        final String document;
        final Map<String, RawControl> controls = new HashMap<String, RawControl>();

        ControlSnapshot(String document) {
            this.document = document;
        }
    }

    /** The address if it is an absolute http(s) address with a host, else {@code null} (mailto:, javascript:, ...). */
    static String usableUrl(String href) {
        if (href == null || href.trim().isEmpty()) {
            return null;
        }
        try {
            URI uri = new URI(href.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) {
                return null;
            }
            return uri.toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    static String withoutFragment(String url) {
        if (url == null) {
            return "";
        }
        int hash = url.indexOf('#');
        return hash < 0 ? url : url.substring(0, hash);
    }

    private static String clip(String text) {
        String flat = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return flat.length() <= MAX_LINK_TEXT ? flat : flat.substring(0, MAX_LINK_TEXT - 1) + "…";
    }
}
