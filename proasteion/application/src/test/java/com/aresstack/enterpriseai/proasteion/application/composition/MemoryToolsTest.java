package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MemoryToolsTest {

    @Test
    public void descriptionsRestrictSavingToExplicitRequests() {
        List<McpToolContribution> tools = MemoryTools.forChat(null, "x");
        assertEquals(MemoryTools.SAVE, tools.get(0).getName());
        assertEquals(MemoryTools.SEARCH, tools.get(1).getName());
        assertEquals(MemoryTools.DELETE, tools.get(2).getName());
        String save = tools.get(0).getDescription().toLowerCase(Locale.ROOT);
        assertTrue(save.contains("only when the user explicitly asks"));
        assertTrue(save.contains("never save on your own"));
        assertTrue(save.contains("current chat only"));
        assertTrue(save.contains("secrets"));
        assertTrue(tools.get(2).getDescription().contains("exactly ONE"));
    }

    @Test
    public void toolsWithoutServiceReportUnavailable() {
        McpToolContribution save = MemoryTools.forChat(null, "x").get(0);
        assertTrue(save.getHandler().invoke(new McpToolCall(MemoryTools.SAVE,
                Collections.<String, Object>singletonMap("content", "a"))).isError());
    }
}
