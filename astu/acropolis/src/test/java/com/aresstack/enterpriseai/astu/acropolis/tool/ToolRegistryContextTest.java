package com.aresstack.enterpriseai.astu.acropolis.tool;

import com.aresstack.enterpriseai.chat.api.ToolDefinition;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/** Issue #118: a successful tool names the resources it delivered before its result is reported; a failure none. */
public class ToolRegistryContextTest {

    @Test
    public void contextComesBeforeResultAndOnlyOnSuccess() {
        final List<String> events = new ArrayList<String>();
        ToolActivityListener activity = new ToolActivityListener() {
            @Override
            public void onToolCall(String toolName) {
                events.add("call:" + toolName);
            }

            @Override
            public void onToolContext(String toolName, List<ToolContextResource> resources) {
                for (ToolContextResource resource : resources) {
                    events.add("context:" + resource.sourceId() + "/" + resource.resourceId());
                }
            }

            @Override
            public void onToolResult(String toolName, boolean succeeded) {
                events.add("result:" + succeeded);
            }
        };
        ToolRegistry registry = new ToolRegistry(Arrays.asList(
                new ContextTool("read", "Inhalt"), new ContextTool("broken", ToolRegistry.ERROR_PREFIX + " kaputt")));

        assertEquals("Inhalt", registry.execute("read", "{}", activity));
        assertEquals(Arrays.asList("call:read", "context:wiki/wiki:A", "result:true"), events);

        events.clear();
        registry.execute("broken", "{}", activity);
        assertEquals(Arrays.asList("call:broken", "result:false"), events);
    }

    private static final class ContextTool implements AiTool {

        private final String name;
        private final String text;

        ContextTool(String name, String text) {
            this.name = name;
            this.text = text;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ToolDefinition definition() {
            return ToolDefinition.function(name, "test", "{\"type\":\"object\",\"properties\":{}}");
        }

        @Override
        public String execute(String arguments) {
            return text;
        }

        @Override
        public ToolOutput executeWithContext(String arguments) {
            return new ToolOutput(text, Collections.singletonList(
                    new ToolContextResource("wiki", "wiki:A", "A", "https://wiki.example.test/A", null)));
        }
    }
}
