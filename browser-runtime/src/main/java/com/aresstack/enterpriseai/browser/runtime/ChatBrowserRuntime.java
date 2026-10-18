package com.aresstack.enterpriseai.browser.runtime;

import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.browser.api.BrowserAccessGuard;
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
 *         navigation of the tab (navigate, follow, back, closing, a restart, a page change by the user) invalidates
 *         them. {@link ChatBrowser#follow} only opens addresses of that snapshot, through the guard.</li>
 *     <li>{@link ChatBrowser#closeBrowser} ends process and connection but keeps the slot: the next operation starts
 *         the browser again on the same profile.</li>
 * </ul>
 */
public final class ChatBrowserRuntime implements ChatBrowserPort {

    private final BrowserEngine engine;
    private final BrowserProfileStore profiles;
    private final BrowserAccessGuard guard;
    private static final int MAX_LINK_TEXT = 160;

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
        public synchronized BrowserLinks links(final String tabId, final int maxLinks) throws BrowserException {
            final int limit = Math.max(1, Math.min(BrowserLinks.MAX_LINKS, maxLinks));
            return call(connection -> {
                String raw = tabId == null ? currentOrFail(connection) : rawOf(connection, tabId);
                RawTab tab = rawTab(connection, raw);
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
                    String[] known = usable.get(url);
                    if (known == null) {
                        usable.put(url, new String[]{text, label.equals(text) ? "" : label});
                    } else if (known[0].isEmpty()) {
                        known[0] = text;
                    }
                }
                LinkSnapshot snapshot = new LinkSnapshot(page);
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
                if (!withoutFragment(rawTab(connection, raw).url()).equals(snapshot.pageUrl)) {
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
                snapshots.remove(raw);
                connection.back(raw);
                BrowserPage page = page(connection, raw);
                checkFinal(connection, raw, page.url());
                return page;
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
        final Map<String, String> urls = new HashMap<String, String>();

        LinkSnapshot(String pageUrl) {
            this.pageUrl = pageUrl;
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
