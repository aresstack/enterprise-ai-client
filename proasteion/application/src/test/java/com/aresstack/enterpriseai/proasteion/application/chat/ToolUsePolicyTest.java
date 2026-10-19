package com.aresstack.enterpriseai.proasteion.application.chat;

import com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.AnswerPlan;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.InformationNeed;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.NextAction;
import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchDelegationTool;
import com.aresstack.enterpriseai.proasteion.application.composition.AppHelpMcpTools;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
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

    /** Knowledge search takes precedence over a public meaning for terms that may be internal, and says the gap. */
    @Test
    public void possiblyInternalTermsGoToTheKnowledgeSources() {
        String rules = ToolUsePolicy.context(Collections.singletonList(KnowledgeMcpTools.KNOWLEDGE_SEARCH));
        assertTrue(rules, rules.contains("nicht eindeutig aufgelöst ist und organisationsspezifisch sein könnte"));
        assertTrue(rules, rules.contains("ersetzt die Suche nicht"));
        assertTrue(rules, rules.contains("<client-turn-analysis> als Hinweis"));
        assertTrue(rules, rules.contains("nie stillschweigend"));
    }

    private static final List<String> ALL = Arrays.asList(AppHelpMcpTools.APP_HELP_SEARCH,
            AppHelpMcpTools.APP_HELP_READ, ResearchDelegationTool.NAME, "read_attachment");

    private static AnswerPlan plan(InformationNeed need, NextAction next) {
        return AnswerPlan.builder("x", MessageOrigin.TYPED).known(true).informationNeed(need).nextAction(next).build();
    }

    private static String route(InformationNeed need, NextAction next, List<String> offered) {
        return ToolUsePolicy.routing(offered).rule(plan(need, next));
    }

    @Test
    public void applicationHelpGoesToTheBuiltInHelpNotToResearch() {
        String rule = route(InformationNeed.APPLICATION_HELP, NextAction.UNKNOWN, ALL);
        assertEquals(ToolUsePolicy.ROUTE_HELP, rule);
        assertFalse(rule, rule.contains(ResearchDelegationTool.NAME));
        assertEquals(ToolUsePolicy.ROUTE_HELP, route(InformationNeed.UNKNOWN, NextAction.USE_APPLICATION_HELP, ALL));
    }

    @Test
    public void internalKnowledgeAndTermsGoToResearch() {
        assertEquals(ToolUsePolicy.ROUTE_RESEARCH, route(InformationNeed.INTERNAL_KNOWLEDGE, NextAction.UNKNOWN, ALL));
        assertEquals(ToolUsePolicy.ROUTE_RESEARCH,
                route(InformationNeed.TERM_RESOLUTION, NextAction.RESOLVE_INTERNAL_TERM, ALL));
        assertEquals(ToolUsePolicy.ROUTE_RESEARCH,
                route(InformationNeed.GENERAL_KNOWLEDGE, NextAction.SEARCH_INTERNAL_KNOWLEDGE, ALL));
    }

    @Test
    public void explicitSearchWithoutResearchFallsBackToKnowledgeSearch() {
        assertEquals(ToolUsePolicy.ROUTE_SEARCH, route(InformationNeed.INTERNAL_KNOWLEDGE, NextAction.UNKNOWN,
                Collections.singletonList(KnowledgeMcpTools.KNOWLEDGE_SEARCH)));
    }

    @Test
    public void generalQuestionsAndSmallTalkStartNoSearch() {
        assertEquals(ToolUsePolicy.ROUTE_DIRECT, route(InformationNeed.NO_INFORMATION_NEEDED, NextAction.UNKNOWN, ALL));
        assertEquals(ToolUsePolicy.ROUTE_DIRECT, route(InformationNeed.GENERAL_KNOWLEDGE, NextAction.UNKNOWN, ALL));
        assertEquals(ToolUsePolicy.ROUTE_DIRECT,
                route(InformationNeed.TERM_RESOLUTION, NextAction.ANSWER_DIRECTLY, ALL));
    }

    @Test
    public void clarificationUnknownPlansAndMissingToolsGiveNoRoute() {
        assertNull(route(InformationNeed.TERM_RESOLUTION, NextAction.ASK_CLARIFICATION, ALL));
        assertNull(route(InformationNeed.UNKNOWN, NextAction.UNKNOWN, ALL));
        assertNull(ToolUsePolicy.routing(ALL).rule(AnswerPlan.unknown("x", MessageOrigin.TYPED)));
        assertNull(route(InformationNeed.APPLICATION_HELP, NextAction.UNKNOWN,
                Collections.singletonList("read_attachment")));
        assertNull(route(InformationNeed.GENERAL_KNOWLEDGE, NextAction.UNKNOWN, Collections.<String>emptyList()));
    }

    @Test
    public void browserRulePrefersParallelReadingOnlyWhenOffered() {
        String rules = ToolUsePolicy.context(Arrays.asList(
                com.aresstack.enterpriseai.proasteion.application.agent.BrowserMcpTools.OPEN_MANY,
                com.aresstack.enterpriseai.proasteion.application.agent.BrowserMcpTools.FOLLOW));
        org.junit.Assert.assertTrue(rules, rules.contains("parallel mit browser_open_many"));
        String without = ToolUsePolicy.context(Arrays.asList(KnowledgeMcpTools.KNOWLEDGE_SEARCH));
        org.junit.Assert.assertFalse(without, without.contains("browser_open_many"));
    }
}
