package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.proasteion.katagogion.McpContextResource;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.research.runtime.team.ChatMessage;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Pages the Research Agent reads in one {@code knowledge_read_many} bundle after exploring a hit's hierarchy are part
 * of the structured provenance of {@code knowledge_research}, exactly like pages read one by one.
 */
public class ResearchDelegationHierarchyTest {

    private static McpContextResource resource(String id) {
        return new McpContextResource("wiki", id, "Seite " + id, "https://wiki.example/" + id, null, null);
    }

    private static String page(String id) {
        return "title: Seite " + id + "\nid: " + id + "\nurl: https://wiki.example/" + id + "\n\nVault Betrieb " + id
                + ".";
    }

    private final Map<String, McpToolContribution> tools = new HashMap<String, McpToolContribution>();
    private final List<String> calls = new ArrayList<String>();

    private void offer(String name, final McpToolHandler handler) {
        tools.put(name, McpToolContribution.of(name, name, call -> {
            calls.add(call.getToolName());
            return handler.invoke(call);
        }));
    }

    @Test
    public void bundleReadPagesAreStructuredSourcesOfTheResearch() {
        offer("knowledge_tree", call -> McpToolResult.ok("source: wiki\nspace: OPS — Betrieb\n\nparents:\n"
                + "- type: space\n  space: OPS\n  title: Betrieb\n\ncurrent:\n  type: page\n  title: Vault Überblick\n  id: p1\n  children: yes\n\nchildren:\n"
                + "- title: Vault Backup\n  id: c1\n  children: no\n- title: Vault Rufbereitschaft\n  id: c2\n"
                + "  children: no\n"));

        McpToolResult result = research();

        assertFalse(result.getText(), result.isError());
        assertEquals("the page's tree, the top-level pages of its space, one bundled read",
                Arrays.asList("knowledge_search", "knowledge_read", "knowledge_tree", "knowledge_tree",
                        "knowledge_read_many"), calls);
        assertEquals(Arrays.asList("p1", "c1", "c2"), used(result));
        assertEquals("https://wiki.example/c1", result.contextResources().get(1).location());
    }

    @Test
    public void aTopLevelSiblingOfAPageBelowTheSpaceIsReadAndCited() {
        offer("knowledge_tree", call -> McpToolResult.ok(call.getString("space") != null
                ? "source: wiki\nspace: OPS — Betrieb\n\ncurrent:\n  type: space\n  space: OPS\n  title: Betrieb\n\n"
                + "children:\n- title: Vault Überblick\n  id: p1\n  children: no\n- title: Vault Betriebshandbuch\n"
                + "  id: r2\n  children: no\n"
                : "source: wiki\nspace: OPS — Betrieb\n\nparents:\n- type: space\n  space: OPS\n  title: Betrieb\n\n"
                + "current:\n  type: page\n  title: Vault Überblick\n  id: p1\n  children: no\n\nchildren:\nnone\n"));

        McpToolResult result = research();

        assertFalse(result.getText(), result.isError());
        assertEquals(Arrays.asList("knowledge_search", "knowledge_read", "knowledge_tree", "knowledge_tree",
                "knowledge_read_many"), calls);
        assertEquals(Arrays.asList("p1", "r2"), used(result));
    }

    private static List<String> used(McpToolResult result) {
        List<String> used = new ArrayList<String>();
        for (McpContextResource resource : result.contextResources()) {
            used.add(resource.resourceId());
        }
        return used;
    }

    /** Offers search, read and read_many (the tree is offered by the test) and runs one research task. */
    private McpToolResult research() {
        offer("knowledge_search", call -> McpToolResult.ok("Query: Vault\n\n[1] Vault Überblick\nid: p1\n"
                + "source: wiki\nVault wird zentral betrieben.\n"));
        offer("knowledge_read", call -> McpToolResult.ok(page(call.getString("id")),
                Collections.singletonList(resource(call.getString("id")))));
        offer("knowledge_read_many", call -> {
            List<String> ids = call.getStrings("ids");
            StringBuilder out = new StringBuilder();
            List<McpContextResource> context = new ArrayList<McpContextResource>();
            for (int i = 0; i < ids.size(); i++) {
                out.append(i == 0 ? "" : "\n\n---\n\n").append('[').append(i + 1).append('/').append(ids.size())
                        .append("] OK\n").append(page(ids.get(i)));
                context.add(resource(ids.get(i)));
            }
            return McpToolResult.ok(out.toString(), context);
        });
        MainModelChat model = new MainModelChat() {
            final List<String> answers = new ArrayList<String>(Arrays.asList("{\"queries\": [\"Vault\"]}", "NEIN",
                    "Vault wird zentral betrieben [1], mit Backup [2]."));

            @Override
            public MainModelChatResult complete(List<ChatMessage> messages, double temperature, int maxTokens) {
                return answers.isEmpty() ? MainModelChatResult.failure(MainModelChatResult.Status.PROVIDER_FAILURE,
                        "-") : MainModelChatResult.ok(answers.remove(0));
            }

            @Override
            public String modelName() {
                return "scripted";
            }
        };
        ResearchDelegationTool research = new ResearchDelegationTool(tools::get, model);
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("task", "Wie ist der Betrieb von Vault organisiert?");

        return research.invoke(new McpToolCall(ResearchDelegationTool.NAME, args));
    }
}
