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
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BrowserMcpToolsTest {

    private final RecordingPort port = new RecordingPort();
    private final BrowserMcpTools tools = new BrowserMcpTools(port);

    @Test
    public void schemasCarryNoChatIdPortOrProfile() {
        for (McpToolContribution tool : BrowserMcpTools.unbound()) {
            for (McpToolParameter parameter : tool.getParameters()) {
                String name = parameter.getName().toLowerCase();
                assertFalse(tool.getName() + "." + name, name.contains("chat") || name.contains("port")
                        || name.contains("profile") || name.contains("context") || name.contains("session"));
            }
        }
    }

    @Test
    public void callsReachOnlyTheBrowserOfTheBoundChat() {
        ChatConversationId a = new ChatConversationId("chat-a");
        ChatConversationId b = new ChatConversationId("chat-b");
        invoke(tools.contributionsFor(a), BrowserMcpTools.NAVIGATE, "url", "https://a.example/");
        invoke(tools.contributionsFor(b), BrowserMcpTools.READ);

        assertEquals(Collections.singletonList("navigate https://a.example/"), port.browser(a).calls);
        assertEquals(Collections.singletonList("read null"), port.browser(b).calls);
    }

    @Test
    public void navigateRejectsNonHttpAddresses() {
        ChatConversationId a = new ChatConversationId("chat-a");
        for (String url : new String[]{"file:///etc/passwd", "javascript:alert(1)", "about:config", "example.org"}) {
            McpToolResult result = invoke(tools.contributionsFor(a), BrowserMcpTools.NAVIGATE, "url", url);
            assertTrue(url, result.isError());
        }
        assertTrue(port.browser(a).calls.isEmpty());
    }

    @Test
    public void tabsListsWithCurrentMarkAndReadIsCut() {
        ChatConversationId a = new ChatConversationId("chat-a");
        McpToolResult list = invoke(tools.contributionsFor(a), BrowserMcpTools.TABS);
        assertTrue(list.getText(), list.getText().contains("* tab-1 | Start | https://start.example/"));

        McpToolResult read = invoke(tools.contributionsFor(a), BrowserMcpTools.READ, "max_chars", "4");
        assertTrue(read.getText(), read.getText().contains("gekürzt"));
    }

    @Test
    public void browserFailureBecomesToolError() {
        ChatConversationId a = new ChatConversationId("chat-a");
        port.browser(a).fail = true;
        McpToolResult result = invoke(tools.contributionsFor(a), BrowserMcpTools.READ);
        assertTrue(result.isError());
        assertEquals("kaputt", result.getText());
    }

    @Test
    public void linksListIdsTextAndAddressAndFilterKeepsMatchesOnly() {
        ChatConversationId a = new ChatConversationId("chat-a");
        McpToolResult all = invoke(tools.contributionsFor(a), BrowserMcpTools.LINKS);
        assertTrue(all.getText(), all.getText().contains("link-1 | Reisekosten | https://intranet.example/reise"));
        assertTrue(all.getText(), all.getText().contains("link-2 | KI [KI-Portal] | https://intranet.example/ki"));
        assertTrue(all.getText(), all.getText().contains("2 von 2 Links"));

        McpToolResult filtered = invoke(tools.contributionsFor(a), BrowserMcpTools.LINKS, "filter", "portal");
        assertTrue(filtered.getText(), filtered.getText().contains("link-1 | KI [KI-Portal]"));
        assertTrue(filtered.getText(), filtered.getText().contains("1 Links passend zu \"portal\""));
        assertEquals("links null " + BrowserMcpTools.DEFAULT_MAX_LINKS + " portal", port.browser(a).calls.get(1));
    }

    @Test
    public void followTakesALinkIdBackAndCloseReachTheBrowser() {
        ChatConversationId a = new ChatConversationId("chat-a");
        assertTrue(invoke(tools.contributionsFor(a), BrowserMcpTools.FOLLOW).isError());
        McpToolResult followed = invoke(tools.contributionsFor(a), BrowserMcpTools.FOLLOW, "link", "link-2");
        assertTrue(followed.getText(), followed.getText().contains("Adresse: https://intranet.example/ki"));
        invoke(tools.contributionsFor(a), BrowserMcpTools.BACK);
        invoke(tools.contributionsFor(a), BrowserMcpTools.CLOSE);

        assertEquals(java.util.Arrays.asList("follow link-2", "back null", "closeBrowser"), port.browser(a).calls);
    }

    private static McpToolResult invoke(List<McpToolContribution> contributions, String name, String... args) {
        Map<String, Object> values = new HashMap<String, Object>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            values.put(args[i], args[i + 1]);
        }
        for (McpToolContribution tool : contributions) {
            if (tool.getName().equals(name)) {
                return tool.getHandler().invoke(new McpToolCall(name, values));
            }
        }
        throw new AssertionError("no tool " + name);
    }

    private static final class RecordingPort implements ChatBrowserPort {

        private final Map<ChatConversationId, RecordingBrowser> browsers =
                new HashMap<ChatConversationId, RecordingBrowser>();

        RecordingBrowser browser(ChatConversationId id) {
            RecordingBrowser browser = browsers.get(id);
            if (browser == null) {
                browser = new RecordingBrowser();
                browsers.put(id, browser);
            }
            return browser;
        }

        @Override
        public ChatBrowser browserOf(ChatConversationId conversation) {
            return browser(conversation);
        }

        @Override
        public void release(ChatConversationId conversation) {
        }

        @Override
        public void deleteProfile(ChatConversationId conversation) {
        }

        @Override
        public void releaseAll() {
        }
    }

    private static final class RecordingBrowser implements ChatBrowser {

        final List<String> calls = new ArrayList<String>();
        boolean fail;

        private void check() throws BrowserException {
            if (fail) {
                throw new BrowserException(BrowserException.Kind.FAILED, "kaputt");
            }
        }

        @Override
        public List<BrowserTab> tabs() throws BrowserException {
            check();
            return Collections.singletonList(new BrowserTab("tab-1", "Start", "https://start.example/", true));
        }

        @Override
        public BrowserTab selectTab(String tabId) throws BrowserException {
            check();
            calls.add("select " + tabId);
            return null;
        }

        @Override
        public BrowserTab newTab(String url) throws BrowserException {
            check();
            calls.add("new " + url);
            return null;
        }

        @Override
        public void closeTab(String tabId) throws BrowserException {
            check();
            calls.add("close " + tabId);
        }

        @Override
        public BrowserPage navigate(String tabId, String url) throws BrowserException {
            check();
            calls.add("navigate " + url);
            return new BrowserPage("tab-1", url, "T", "text");
        }

        @Override
        public BrowserPage read(String tabId) throws BrowserException {
            check();
            calls.add("read " + tabId);
            return new BrowserPage("tab-1", "https://start.example/", "Start", "Lorem ipsum");
        }

        @Override
        public BrowserLinks links(String tabId, int maxLinks, String filter) throws BrowserException {
            check();
            calls.add("links " + tabId + " " + maxLinks + " " + filter);
            BrowserLink ki = new BrowserLink("link-2", "KI", "https://intranet.example/ki", "KI-Portal");
            return filter == null ? new BrowserLinks("tab-1", "https://intranet.example/", "Intranet",
                    java.util.Arrays.asList(new BrowserLink("link-1", "Reisekosten", "https://intranet.example/reise",
                            ""), ki), 2)
                    : new BrowserLinks("tab-1", "https://intranet.example/", "Intranet",
                    java.util.Collections.singletonList(new BrowserLink("link-1", "KI", ki.url(), ki.label())), 1);
        }

        @Override
        public BrowserPage follow(String tabId, String linkId) throws BrowserException {
            check();
            calls.add("follow " + linkId);
            return new BrowserPage("tab-1", "https://intranet.example/ki", "KI", "text");
        }

        @Override
        public BrowserPage back(String tabId) throws BrowserException {
            check();
            calls.add("back " + tabId);
            return new BrowserPage("tab-1", "https://intranet.example/", "Intranet", "text");
        }

        @Override
        public void closeBrowser() {
            calls.add("closeBrowser");
        }

        @Override
        public boolean isRunning() {
            return false;
        }
    }
}
