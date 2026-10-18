package com.aresstack.enterpriseai.browser.runtime;

import com.aresstack.enterpriseai.browser.api.BrowserException;
import com.aresstack.enterpriseai.browser.api.engine.BrowserConnection;
import com.aresstack.enterpriseai.browser.api.engine.BrowserEngine;
import com.aresstack.enterpriseai.browser.api.engine.BrowserInstance;
import com.aresstack.enterpriseai.browser.api.engine.RawPage;
import com.aresstack.enterpriseai.browser.api.engine.RawTab;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Fake browser engine: one fake process per start, one opening tab like a normal Firefox start. */
final class FakeEngine implements BrowserEngine {

    final List<Path> starts = new CopyOnWriteArrayList<Path>();
    final List<FakeProcess> processes = new CopyOnWriteArrayList<FakeProcess>();
    volatile long startDelayMillis;
    /** Requested url to the url the fake server redirects to. */
    final Map<String, String> redirects = new java.util.concurrent.ConcurrentHashMap<String, String>();
    /** Every url handed to a connection's navigate, in order. */
    final List<String> navigations = new CopyOnWriteArrayList<String>();

    @Override
    public BrowserInstance start(Path profileDirectory) throws BrowserException {
        starts.add(profileDirectory);
        if (startDelayMillis > 0) {
            try {
                Thread.sleep(startDelayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        FakeProcess process = new FakeProcess(this, processes.size());
        processes.add(process);
        return process;
    }

    FakeProcess last() {
        return processes.get(processes.size() - 1);
    }

    static final class FakeProcess implements BrowserInstance {

        final FakeEngine engine;
        final int number;
        final Map<String, String[]> tabs = new LinkedHashMap<String, String[]>();
        final AtomicInteger connects = new AtomicInteger();
        final List<String> activated = new CopyOnWriteArrayList<String>();
        volatile boolean alive = true;
        volatile FakeConnection connection;
        private int nextContext = 1;

        FakeProcess(FakeEngine engine, int number) {
            this.engine = engine;
            this.number = number;
            createTab();
        }

        synchronized String createTab() {
            String id = "ctx-" + number + "-" + nextContext++;
            tabs.put(id, new String[]{"about:blank", ""});
            return id;
        }

        /** The user closes the browser window. */
        void closeByUser() {
            alive = false;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public BrowserConnection connect() throws BrowserException {
            if (!alive) {
                throw new BrowserException(BrowserException.Kind.BROWSER_CLOSED, "gone");
            }
            connects.incrementAndGet();
            connection = new FakeConnection(this);
            return connection;
        }

        @Override
        public void stop() {
            alive = false;
        }
    }

    static final class FakeConnection implements BrowserConnection {

        final FakeProcess process;
        volatile boolean open = true;

        FakeConnection(FakeProcess process) {
            this.process = process;
        }

        private void check() throws BrowserException {
            if (!process.alive) {
                open = false;
                throw new BrowserException(BrowserException.Kind.BROWSER_CLOSED, "BROWSER_CLOSED");
            }
            if (!open) {
                throw new BrowserException(BrowserException.Kind.CONNECTION_LOST, "socket closed");
            }
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public List<RawTab> tabs() throws BrowserException {
            check();
            synchronized (process) {
                List<RawTab> list = new ArrayList<RawTab>();
                for (Map.Entry<String, String[]> tab : process.tabs.entrySet()) {
                    list.add(new RawTab(tab.getKey(), tab.getValue()[0], tab.getValue()[1]));
                }
                return list;
            }
        }

        @Override
        public String createTab() throws BrowserException {
            check();
            return process.createTab();
        }

        @Override
        public void activate(String contextId) throws BrowserException {
            check();
            process.activated.add(contextId);
        }

        @Override
        public void close(String contextId) throws BrowserException {
            check();
            synchronized (process) {
                process.tabs.remove(contextId);
            }
        }

        @Override
        public void navigate(String contextId, String url) throws BrowserException {
            check();
            process.engine.navigations.add(url);
            String target = process.engine.redirects.containsKey(url) ? process.engine.redirects.get(url) : url;
            synchronized (process) {
                process.tabs.put(contextId, new String[]{target, "Title of " + target});
            }
        }

        @Override
        public RawPage read(String contextId) throws BrowserException {
            check();
            synchronized (process) {
                String[] tab = process.tabs.get(contextId);
                return new RawPage(tab[0], tab[1], "Text of " + tab[0]);
            }
        }

        @Override
        public void disconnect() {
            open = false;
        }
    }
}
