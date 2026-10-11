package com.aresstack.enterpriseai.astu.acropolis.rag;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KnowledgeQuestionTest {

    @Test
    public void smallTalkNeverSearches() {
        for (String message : new String[] {"Hi", "hi!", "Hallo", "Hallo :)", "Danke", "Danke dir!", "Vielen Dank",
                "ok", "Okay.", "Guten Morgen", "Wie geht's?", "Wie geht es dir?", "Tschüss, bis später", "👍",
                "Super, danke!", "thanks"}) {
            assertTrue(message, KnowledgeQuestion.isSmallTalk(message));
            assertFalse(message, KnowledgeQuestion.wantsRetrieval(message, true));
            assertFalse(message, KnowledgeQuestion.wantsRetrieval(message, false));
        }
    }

    @Test
    public void recognizableQuestionsSearchEvenWithTools() {
        for (String message : new String[] {"Wie lange ist die Kündigungsfrist?", "Was ist IAR",
                "Suche Seiten zu SonarQube.", "Erkläre mir den Build von IAR", "kannst du mir was über iar finden",
                "Gibt es eine Doku zum Deployment", "IAR Installation?", "What is the release process"}) {
            assertFalse(message, KnowledgeQuestion.isSmallTalk(message));
            assertTrue(message, KnowledgeQuestion.wantsRetrieval(message, true));
            assertTrue(message, KnowledgeQuestion.wantsRetrieval(message, false));
        }
    }

    @Test
    public void otherMessagesLeaveTheSearchToTheModelWhenItHasTheTools() {
        for (String message : new String[] {"Schreib mir ein Gedicht über Herbst", "IAR Installation",
                "Danke, und jetzt die Installation von IAR"}) {
            assertFalse(message, KnowledgeQuestion.isSmallTalk(message));
            assertFalse(message, KnowledgeQuestion.wantsRetrieval(message, true));
            assertTrue("ohne Werkzeuge ist die Vorabsuche der einzige Weg: " + message,
                    KnowledgeQuestion.wantsRetrieval(message, false));
        }
    }
}
