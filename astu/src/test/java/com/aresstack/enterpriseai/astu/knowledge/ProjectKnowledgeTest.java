package com.aresstack.enterpriseai.astu.knowledge;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProjectKnowledgeTest {

    @Test
    public void projectSourceAndResourceBelongTogether() {
        KnowledgeResourceId file = ProjectKnowledge.resource("p-1", "att-0000000a");
        assertEquals("chatproject:p-1/att-0000000a", file.value());
        assertEquals(ProjectKnowledge.source("p-1"), ProjectKnowledge.sourceOf(file));
        assertEquals("chatproject://p-1", ProjectKnowledge.source("p-1").value());
        assertTrue(ProjectKnowledge.isProjectResource(file));
        assertNull(ProjectKnowledge.sourceOf(KnowledgeResourceId.of("wiki:Drucker")));
    }

    @Test
    public void projectContentIsVisibleOnlyToItsOwnProject() {
        SourceAddress own = ProjectKnowledge.source("p-1");
        KnowledgeResourceId file = ProjectKnowledge.resource("p-1", "att-0000000a");
        assertTrue(ProjectKnowledge.isVisible(file, own));
        assertFalse(ProjectKnowledge.isVisible(file, ProjectKnowledge.source("p-2")));
        assertFalse(ProjectKnowledge.isVisible(file, null));
        assertFalse(ProjectKnowledge.isVisible(KnowledgeResourceId.of("chatproject:kaputt"), own));
        assertTrue(ProjectKnowledge.isVisible(KnowledgeResourceId.of("wiki:Drucker"), null));
        assertTrue(ProjectKnowledge.isVisible(SourceAddress.of("wiki://intern"), null));
        assertFalse(ProjectKnowledge.isVisible(own, null));
    }
}
