package com.aresstack.enterpriseai.proasteion.application.chat;

import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.proasteion.application.composition.AppHelpMcpTools;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ToolUsePolicyTest {

    @Test
    public void noToolsNoRules() {
        assertNull(ToolUsePolicy.context(Collections.<String>emptyList()));
        assertNull(ToolUsePolicy.context(null));
    }

    @Test
    public void helpComesBeforeKnowledgeSearchWhenBothAreOffered() {
        String rules = ToolUsePolicy.context(Arrays.asList(KnowledgeMcpTools.KNOWLEDGE_SEARCH,
                KnowledgeMcpTools.KNOWLEDGE_READ, AppHelpMcpTools.APP_HELP_SEARCH, AppHelpMcpTools.APP_HELP_READ));
        assertTrue(rules, rules.startsWith(ToolUsePolicy.GENERAL));
        assertTrue(rules, rules.contains(ToolUsePolicy.HELP_FIRST));
        assertTrue(rules, rules.contains(ToolUsePolicy.KNOWLEDGE));
        assertTrue(rules, rules.indexOf(ToolUsePolicy.HELP_FIRST) < rules.indexOf(ToolUsePolicy.KNOWLEDGE));
    }

    @Test
    public void onlyOfferedToolsAreNamed() {
        String rules = ToolUsePolicy.context(Arrays.asList(AppHelpMcpTools.APP_HELP_SEARCH,
                AppHelpMcpTools.APP_HELP_READ));
        assertTrue(rules, rules.contains(AppHelpMcpTools.APP_HELP_SEARCH));
        assertFalse(rules, rules.contains(KnowledgeMcpTools.KNOWLEDGE_SEARCH));

        String attachmentsOnly = ToolUsePolicy.context(Collections.singletonList("read_attachment"));
        assertTrue(attachmentsOnly, attachmentsOnly.equals(ToolUsePolicy.GENERAL));
    }
}
