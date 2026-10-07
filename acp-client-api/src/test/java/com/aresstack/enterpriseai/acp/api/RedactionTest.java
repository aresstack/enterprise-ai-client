package com.aresstack.enterpriseai.acp.api;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Neither the token nor the URL path of an MCP endpoint ever shows up in printable output. */
public class RedactionTest {

    private static final String TOKEN = "tKn-9f8e7d6c5b4a";
    private static final String ENDPOINT_URL = "http://127.0.0.1:43210/mcp/knowledge/" + TOKEN;

    private static void assertSafe(String text) {
        assertFalse(text, text.contains(TOKEN));
        assertFalse(text, text.contains("/mcp"));
        assertFalse(text, text.contains("knowledge/"));
    }

    @Test
    public void descriptorShowsOnlySchemeHostAndPort() {
        String text = new AcpEndpointDescriptor("knowledge", ENDPOINT_URL, "streamable", TOKEN).toString();
        assertSafe(text);
        assertTrue(text, text.contains("url=http://127.0.0.1:43210/<redacted>"));
        assertTrue(text, text.contains("token=<redacted>"));
    }

    @Test
    public void tokenInQueryFragmentOrUserInfoIsMasked() {
        for (String url : Arrays.asList(
                "http://127.0.0.1:43210/?token=" + TOKEN,
                "http://127.0.0.1:43210#" + TOKEN,
                "http://user:" + TOKEN + "@127.0.0.1:43210")) {
            String shown = Redaction.url(url);
            assertFalse(url + " -> " + shown, shown.contains(TOKEN));
            assertEquals("http://127.0.0.1:43210/<redacted>", shown);
        }
    }

    @Test
    public void urlWithoutPathStaysReadable() {
        assertEquals("http://127.0.0.1:43210", Redaction.url("http://127.0.0.1:43210"));
        assertEquals("http://127.0.0.1:43210", Redaction.url("http://127.0.0.1:43210/"));
        assertEquals("https://example.org", Redaction.url("https://example.org"));
    }

    @Test
    public void missingOrUnparsableUrlNeverLeaksItsContent() {
        assertEquals("<none>", Redaction.url(null));
        assertEquals("<none>", Redaction.url(""));
        assertEquals("<redacted>", Redaction.url("not a url " + TOKEN));
        assertEquals("<redacted>", Redaction.url("/mcp/knowledge/" + TOKEN));
        assertEquals("<redacted>", Redaction.url("http://[broken/" + TOKEN));
    }

    @Test
    public void launchSpecMasksUrlsInArgumentsAndHidesEnvValues() {
        AgentLaunchSpec spec = new AgentLaunchSpec("java",
                Arrays.asList("-jar", "agent.jar", "--mcp=" + ENDPOINT_URL, ENDPOINT_URL),
                Collections.singletonMap("MCP_URL", ENDPOINT_URL));
        String text = spec.toString();
        assertSafe(text);
        assertTrue(text, text.contains("--mcp=http://127.0.0.1:43210/<redacted>"));
        assertTrue(text, text.contains("agent.jar"));
        assertTrue(text, text.contains("MCP_URL"));
    }
}
