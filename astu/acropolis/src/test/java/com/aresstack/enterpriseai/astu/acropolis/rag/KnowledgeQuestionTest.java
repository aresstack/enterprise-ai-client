package com.aresstack.enterpriseai.astu.acropolis.rag;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KnowledgeQuestionTest {

    @Test
    public void questionsAboutTheAssistantItselfAreRecognized() {
        for (String message : new String[] {"Kannst Du auch Recherchieren? Hast Du MCP Tools? Welche?",
                "Welche Tools hast du?", "Hast du MCP-Tools?", "Kannst du recherchieren?", "Kannst du Dateien lesen?",
                "Welche Wissensquellen kannst du durchsuchen?", "Welche Funktionen hast du?",
                "Welches Modell verwendest du?", "Kannst du Confluence durchsuchen?", "Was kannst du alles?",
                "Hast du Zugriff auf das Internet?", "What tools do you have?"}) {
            assertTrue(message, KnowledgeQuestion.isAssistantCapability(message));
        }
    }

    @Test
    public void subjectMatterIsNoQuestionAboutTheAssistant() {
        for (String message : new String[] {"Was ist IAR?", "kannst du mir was über iar finden",
                "Welche Tools gibt es für das Deployment?", "Hast du Infos zur Kündigungsfrist?",
                "Welche MCP Tools nutzt IAR?", "Kannst du die Seite zu IAR lesen?", "Wie geht es dir?",
                "Schreib mir ein Gedicht"}) {
            assertFalse(message, KnowledgeQuestion.isAssistantCapability(message));
        }
    }
}
