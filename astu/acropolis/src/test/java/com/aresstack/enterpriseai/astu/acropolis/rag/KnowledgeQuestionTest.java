package com.aresstack.enterpriseai.astu.acropolis.rag;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KnowledgeQuestionTest {

    @Test
    public void smallTalkNeverSearches() {
        for (String message : new String[] {"Hi", "hi!", "Hallo", "Hallo :)", "Danke", "Danke dir!", "Vielen Dank",
                "ok", "Okay.", "Guten Morgen", "Wie geht's?", "Wie geht es dir?", "Tschüss, bis später", "👍",
                "Super, danke!", "thanks"}) {
            assertTrue(message, KnowledgeQuestion.isSmallTalk(message));
            assertFalse(message, KnowledgeQuestion.wantsRetrieval(message));
        }
    }

    @Test
    public void recognizableQuestionsSearchEvenWithTools() {
        for (String message : new String[] {"Wie lange ist die Kündigungsfrist?", "Was ist IAR",
                "Suche Seiten zu SonarQube.", "Erkläre mir den Build von IAR", "kannst du mir was über iar finden",
                "Gibt es eine Doku zum Deployment", "IAR Installation?", "What is the release process"}) {
            assertFalse(message, KnowledgeQuestion.isSmallTalk(message));
            assertTrue(message, KnowledgeQuestion.wantsRetrieval(message));
        }
    }

    @Test
    public void correctionsFollowUpsAndRequestsSearchToo() {
        // Aus einem echten Verlauf: ohne Fragewort am Anfang antwortete das Modell aus eigenem Wissen.
        for (String message : new String[] {"an kannst du mir sicherlich sagen was iar ist",
                "das ist nicht was ich suche", "IAR Installation", "Danke, und jetzt die Installation von IAR",
                "Schreib mir ein Gedicht über Herbst"}) {
            assertFalse(message, KnowledgeQuestion.isSmallTalk(message));
            assertTrue(message, KnowledgeQuestion.wantsRetrieval(message));
        }
    }

    @Test
    public void questionsAboutTheAssistantItselfNeverSearch() {
        for (String message : new String[] {"Kannst Du auch Recherchieren? Hast Du MCP Tools? Welche?",
                "Welche Tools hast du?", "Hast du MCP-Tools?", "Kannst du recherchieren?", "Kannst du Dateien lesen?",
                "Welche Wissensquellen kannst du durchsuchen?", "Welche Funktionen hast du?",
                "Welches Modell verwendest du?", "Kannst du Confluence durchsuchen?", "Was kannst du alles?",
                "Hast du Zugriff auf das Internet?", "What tools do you have?"}) {
            assertEquals(message, KnowledgeQuestion.Kind.ASSISTANT_CAPABILITY, KnowledgeQuestion.classify(message));
            assertFalse(message, KnowledgeQuestion.wantsRetrieval(message));
        }
    }

    @Test
    public void subjectMatterKeepsAQuestionAKnowledgeQuestion() {
        for (String message : new String[] {"Was ist IAR?", "kannst du mir was über iar finden",
                "Welche Tools gibt es für das Deployment?", "Hast du Infos zur Kündigungsfrist?",
                "Welche MCP Tools nutzt IAR?", "Kannst du die Seite zu IAR lesen?"}) {
            assertEquals(message, KnowledgeQuestion.Kind.KNOWLEDGE_QUESTION, KnowledgeQuestion.classify(message));
            assertTrue(message, KnowledgeQuestion.wantsRetrieval(message));
        }
        assertEquals(KnowledgeQuestion.Kind.SMALL_TALK, KnowledgeQuestion.classify("Wie geht es dir?"));
        assertEquals(KnowledgeQuestion.Kind.OTHER, KnowledgeQuestion.classify("Schreib mir ein Gedicht"));
    }
}
