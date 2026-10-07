package com.aresstack.enterpriseai.mcp.api;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Validierung, Unveränderlichkeit und Secret-Hygiene der Port-Werttypen. */
public class McpValueTypesTest {

    private static final McpToolHandler NOOP = new McpToolHandler() {
        @Override
        public McpToolResult invoke(McpToolCall call) {
            return McpToolResult.ok("");
        }
    };

    @Test
    public void endpointDefinitionTrimsIdAndFallsBackToItAsDisplayName() {
        McpEndpointDefinition definition = new McpEndpointDefinition("  knowledge  ", null);
        assertEquals("knowledge", definition.getEndpointId());
        assertEquals("knowledge", definition.getDisplayName());
        assertEquals("Wissen", new McpEndpointDefinition("knowledge", "Wissen").getDisplayName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void endpointDefinitionRejectsBlankId() {
        new McpEndpointDefinition(" ", "x");
    }

    @Test
    public void endpointHandleMasksItsToken() {
        McpEndpointHandle handle = new McpEndpointHandle("knowledge", "s3cr3t-token-value");
        assertEquals("s3cr3t-token-value", handle.getToken());
        assertFalse(handle.toString().contains("s3cr3t"));
        assertTrue(handle.toString().contains("knowledge"));
    }

    @Test
    public void toolContributionValidatesAndCopiesParameters() {
        List<McpToolParameter> parameters = new java.util.ArrayList<McpToolParameter>();
        parameters.add(McpToolParameter.string("query", true, "Suchtext"));
        McpToolContribution tool = new McpToolContribution(" search ", null, parameters, NOOP);
        parameters.add(McpToolParameter.integer("limit", false, "max"));

        assertEquals("search", tool.getName());
        assertEquals("", tool.getDescription());
        assertEquals(1, tool.getParameters().size());
        try {
            tool.getParameters().add(McpToolParameter.bool("x", false, ""));
            fail("parameters must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // erwartet
        }
    }

    @Test
    public void toolContributionRejectsInvalidInput() {
        assertRejected(new Runnable() {
            @Override
            public void run() {
                McpToolContribution.of("", "d", NOOP);
            }
        });
        assertRejected(new Runnable() {
            @Override
            public void run() {
                McpToolContribution.of("t", "d", null);
            }
        });
        for (final String invalid : new String[] {"with space", "ümlaut", "semi;colon", repeat('a', 129)}) {
            assertRejected(new Runnable() {
                @Override
                public void run() {
                    McpToolContribution.of(invalid, "d", NOOP);
                }
            });
        }
        assertEquals(repeat('a', 128), McpToolContribution.of(repeat('a', 128), "d", NOOP).getName());
        assertEquals("search_knowledge.v-2", McpToolContribution.of("search_knowledge.v-2", "d", NOOP).getName());
        assertRejected(new Runnable() {
            @Override
            public void run() {
                McpToolContribution.of("t", "d", NOOP,
                        McpToolParameter.string("a", true, ""), McpToolParameter.integer("a", false, ""));
            }
        });
    }

    @Test
    public void parametersCarryTypeAndEnumValues() {
        McpToolParameter mode = McpToolParameter.enumeration("mode", false, "Suchmodus",
                Arrays.asList("keyword", "semantic", "hybrid"));
        assertEquals(McpToolType.ENUM, mode.getType());
        assertFalse(mode.isRequired());
        assertEquals(Arrays.asList("keyword", "semantic", "hybrid"), mode.getEnumValues());
        assertEquals(McpToolType.INTEGER, McpToolParameter.integer("n", true, null).getType());
        assertEquals(McpToolType.BOOLEAN, McpToolParameter.bool("b", true, null).getType());
        assertRejected(new Runnable() {
            @Override
            public void run() {
                McpToolParameter.enumeration("mode", true, "", Collections.<String>emptyList());
            }
        });
    }

    @Test
    public void toolCallGettersAreTolerant() {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("text", "Grüße");
        args.put("limit", "7");
        args.put("double", 3.0d);
        args.put("textDouble", "4.0");
        args.put("bad", "seven");
        args.put("flag", "TRUE");
        args.put("flagObj", Boolean.FALSE);
        args.put("notAFlag", "yes");
        McpToolCall call = new McpToolCall("search", args);
        args.clear();

        assertEquals("Grüße", call.getString("text"));
        assertNull(call.getString("missing"));
        assertEquals(7L, call.getInteger("limit", 0));
        assertEquals(3L, call.getInteger("double", 0));
        assertEquals(4L, call.getInteger("textDouble", 0));
        assertEquals(5L, call.getInteger("bad", 5));
        assertEquals(5L, call.getInteger("missing", 5));
        assertTrue(call.getBoolean("flag", false));
        assertFalse(call.getBoolean("flagObj", true));
        assertTrue(call.getBoolean("notAFlag", true));
        assertEquals("arguments are copied", 8, call.getArguments().size());
    }

    @Test
    public void toolCallToStringOmitsArgumentValues() {
        McpToolCall call = new McpToolCall("echo", Collections.<String, Object>singletonMap("text", "geheim"));
        assertFalse(call.toString().contains("geheim"));
        assertTrue(call.toString().contains("text"));
        assertEquals("", new McpToolCall(null, null).getToolName());
        assertTrue(new McpToolCall(null, null).getArguments().isEmpty());
    }

    @Test
    public void toolResultDistinguishesOkAndError() {
        assertFalse(McpToolResult.ok("x").isError());
        assertTrue(McpToolResult.error("x").isError());
        assertEquals("", McpToolResult.ok(null).getText());
        assertFalse(McpToolResult.ok("geheim").toString().contains("geheim"));
    }

    @Test
    public void toolCallExceptionKeepsItsCategory() {
        assertTrue(new McpToolCallException("down", true).isEndpointUnavailable());
        assertFalse(new McpToolCallException("tool", false, new IllegalStateException()).isEndpointUnavailable());
    }

    private static String repeat(char c, int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append(c);
        }
        return text.toString();
    }

    private static void assertRejected(Runnable action) {
        try {
            action.run();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // erwartet
        }
    }
}
