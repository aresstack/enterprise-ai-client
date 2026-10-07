package com.aresstack.enterpriseai.acp.api;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Value types: validation, immutability, defaults and secret-free toString(). */
public class AcpValuesTest {

    @Test
    public void launchSpecRejectsEmptyCommand() {
        for (String command : Arrays.asList(null, "", "   ")) {
            try {
                new AgentLaunchSpec(command, null, null);
                fail("command '" + command + "' must be rejected");
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }

    @Test
    public void launchSpecIsImmutableAndDefensivelyCopied() {
        List<String> args = new ArrayList<String>(Arrays.asList("-jar", "agent.jar"));
        Map<String, String> env = new LinkedHashMap<String, String>();
        env.put("A", "1");
        AgentLaunchSpec spec = new AgentLaunchSpec("java", args, env);
        args.add("--late");
        env.put("B", "2");
        assertEquals(Arrays.asList("-jar", "agent.jar"), spec.getArgs());
        assertEquals(Collections.singletonMap("A", "1"), spec.getEnv());
        try {
            spec.getArgs().add("x");
            fail("args must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
        try {
            spec.getEnv().put("x", "y");
            fail("env must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    @Test
    public void launchSpecNullArgsAndEnvBecomeEmpty() {
        AgentLaunchSpec spec = new AgentLaunchSpec("agent", null, null);
        assertTrue(spec.getArgs().isEmpty());
        assertTrue(spec.getEnv().isEmpty());
    }

    @Test
    public void launchSpecToStringShowsEnvNamesButNeverValues() {
        Map<String, String> env = new LinkedHashMap<String, String>();
        env.put("MCP_TOKEN", "s3cr3t-value");
        String text = new AgentLaunchSpec("java", Arrays.asList("-jar", "a.jar"), env).toString();
        assertTrue(text, text.contains("MCP_TOKEN"));
        assertFalse(text, text.contains("s3cr3t-value"));
    }

    @Test
    public void endpointDescriptorToStringNeverContainsTheToken() {
        AcpEndpointDescriptor withToken =
                new AcpEndpointDescriptor("knowledge", "http://127.0.0.1:1234/mcp", "streamable", "tok-123456");
        assertEquals("tok-123456", withToken.getToken());
        assertFalse(withToken.toString(), withToken.toString().contains("tok-123456"));
        assertTrue(withToken.toString(), withToken.toString().contains("<redacted>"));

        AcpEndpointDescriptor without = new AcpEndpointDescriptor("knowledge", "http://x", "sse", null);
        assertTrue(without.toString(), without.toString().contains("<none>"));
    }

    @Test
    public void updateNormalizesNullKindAndText() {
        AcpUpdate update = new AcpUpdate("s", "p", 7L, null, null);
        assertEquals(AcpUpdate.Kind.OTHER, update.getKind());
        assertEquals("", update.getText());
        assertEquals(7L, update.getSequenceNumber());
        assertEquals("s", update.getSessionId());
        assertEquals("p", update.getPromptId());
    }

    @Test
    public void exceptionCarriesItsPhase() {
        IllegalStateException cause = new IllegalStateException("boom");
        AcpException ex = new AcpException(AcpException.Phase.INITIALIZE, "init failed", cause);
        assertEquals(AcpException.Phase.INITIALIZE, ex.getPhase());
        assertSame(cause, ex.getCause());
    }

    @Test
    public void onlyCancelledCompletedAndFailedAreTerminal() {
        for (AcpPromptState state : AcpPromptState.values()) {
            boolean expected = state == AcpPromptState.CANCELLED || state == AcpPromptState.COMPLETED
                    || state == AcpPromptState.FAILED;
            assertEquals(state.name(), expected, state.isTerminal());
        }
    }
}
