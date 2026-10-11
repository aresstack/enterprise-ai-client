package com.aresstack.enterpriseai.acp.research;

import com.agentclientprotocol.sdk.spec.AcpSchema;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Die Recherche-Karte hat kein ACP-{@code ToolKind}: sie reist als {@code other} mit ihrer Art in {@code _meta}. */
public class ResearchToolCallWireTest {

    @Test
    public void theResearchCardCarriesItsKindInMeta() {
        AcpSchema.ToolCall card = ResearchAgentMain.toolCall("research-1", "Recherche in Confluence", "research", "IAR");
        assertEquals(AcpSchema.ToolKind.OTHER, card.kind());
        assertEquals("research", card.meta().get(ResearchAgentMain.META_TOOL_KIND));
    }

    @Test
    public void standardKindsNeedNoMeta() {
        AcpSchema.ToolCall search = ResearchAgentMain.toolCall("s1", "Suche", "search", "IAR");
        assertEquals(AcpSchema.ToolKind.SEARCH, search.kind());
        assertNull(search.meta());
    }
}
