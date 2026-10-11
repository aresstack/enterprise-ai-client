package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;
import com.aresstack.enterpriseai.research.runtime.team.ChatMessage;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Gliederung, Dokument und Nachfrage des Agenten über Fake-Werkzeuge des Hosts und ein Fake-Modell. */
public class ResearchWritingTest {

    private static final String PAGE = "confluence:wiki/page/1001";
    private static final String PASSAGE = PAGE + "@cap1#p0@aaaaaaaa-bbbbbbbb";
    private static final String HIT = "passage: " + PASSAGE + "\nresource: " + PAGE + "\ntitle: IAR Übersicht\n"
            + "Überblick: IAR bündelt die Auswertung der Anträge.\n\n";

    /** Werkzeuge mit festen Antworten; jeder Aufruf wird mitgeschrieben. */
    private static final class Host implements ToolInvoker {
        final Map<String, LinkedList<String>> answers = new HashMap<String, LinkedList<String>>();
        final List<String> calls = new ArrayList<String>();
        final Map<String, Map<String, Object>> lastArgs = new HashMap<String, Map<String, Object>>();

        Host answer(String tool, String... texts) {
            LinkedList<String> queue = new LinkedList<String>();
            for (String text : texts) {
                queue.add(text);
            }
            answers.put(tool, queue);
            return this;
        }

        @Override
        public String call(String toolName, Map<String, Object> args) {
            calls.add(toolName);
            lastArgs.put(toolName, args);
            LinkedList<String> queue = answers.get(toolName);
            if (queue == null || queue.isEmpty()) {
                return "OK";
            }
            return queue.size() == 1 ? queue.getFirst() : queue.removeFirst();
        }
    }

    private static final class Model implements MainModelChat {
        final LinkedList<String> answers = new LinkedList<String>();

        Model(String... texts) {
            for (String text : texts) {
                answers.add(text);
            }
        }

        @Override
        public MainModelChatResult complete(List<ChatMessage> messages, double temperature, int maxOutputTokens) {
            return MainModelChatResult.ok(answers.size() == 1 ? answers.getFirst() : answers.removeFirst());
        }

        @Override
        public String modelName() {
            return "fake";
        }
    }

    private static final class Out implements ResearchAgentTurns.Output {
        final List<String> lines = new ArrayList<String>();

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void message(String text) {
            lines.add(text);
        }
    }

    @Test
    public void outlineIsBuiltFromConceptSourcesAndPassagesAndProposed() throws Exception {
        Host host = new Host().answer("research_sources", PAGE + "\tIAR Übersicht\t")
                .answer("research_outline", "").answer("research_search_plan", "IAR\nIAR Zweck")
                .answer("project_search", HIT);
        String reply = new ResearchWriting(host, new Model("## 1. Was ist IAR\n- Zweck\n\n## 2. Offene Punkte"))
                .outline("IAR\n- Zweck", "", "");
        assertTrue(host.calls.contains("outline_propose"));
        String outline = (String) host.lastArgs.get("outline_propose").get("outline");
        assertTrue(outline, outline.startsWith("# Recherchebericht"));
        assertTrue(reply, reply.contains("Gliederung freigeben"));
    }

    @Test
    public void documentCarriesPassageMarkersAndTheirResources() throws Exception {
        Host host = new Host().answer("research_outline",
                "# IAR\n\n## 1. Was ist IAR\n- Zweck\n\n## 2. Offene Punkte und fehlende Informationen\n")
                .answer("project_search", HIT);
        new ResearchWriting(host, new Model("IAR bündelt die Auswertung der Anträge. [P1]",
                "- Termine fehlen in den Quellen")).document(new Out());
        String json = (String) host.lastArgs.get("document_save").get("document_json");
        assertTrue(json, json.contains("[[" + PASSAGE + "]]"));
        assertTrue(json, json.contains("\"" + PASSAGE + "\":\"" + PAGE + "\""));
        assertTrue(json, json.contains("Termine fehlen"));
    }

    @Test
    public void followUpSearchesTheSourcesWhenTheProjectCorpusIsNotEnough() throws Exception {
        Host host = new Host().answer("project_search", "", HIT)
                .answer("knowledge_search", "[1] IAR Planung\nid: confluence:wiki/page/1002\nurl: \n"
                        + "Meilensteine der Einführung")
                .answer("knowledge_read", "Meilensteine: Pilot im Frühjahr.")
                .answer("project_source_add", "source_id=confluence:wiki/page/1002");
        Out out = new Out();
        String answer = new ResearchWriting(host, new Model("Der Pilot ist für das Frühjahr geplant. [P1]"))
                .answer("Welche Meilensteine hat IAR?", true, out);
        assertTrue(host.calls.contains("knowledge_search"));
        assertEquals("confluence:wiki/page/1002", host.lastArgs.get("project_source_add").get("capture_id"));
        assertTrue(answer, answer.contains("Frühjahr geplant. [1]"));
        // die eben gelesene Seite steht vorn und trägt den Beleg
        assertTrue(answer, answer.contains("[1] IAR Planung"));
    }

    /** Der Host mit Zwischenstand: document_section_save speichert, document_draft liefert die Kapitel zurück. */
    private static final class DraftHost implements ToolInvoker {
        final List<String> calls = new ArrayList<String>();
        final List<String[]> sections = new ArrayList<String[]>();
        String document;

        @Override
        public String call(String toolName, Map<String, Object> args) {
            calls.add(toolName);
            if ("research_outline".equals(toolName)) {
                return "# IAR\n\n## 1. Zweck\n- Wozu\n\n## 2. Ziele\n- Was\n\n## 3. Planung\n- Wann\n\n"
                        + "## 4. Beteiligte\n- Wer\n";
            }
            if ("project_search".equals(toolName)) {
                return HIT;
            }
            if ("document_section_save".equals(toolName)) {
                int index = ((Integer) args.get("index")).intValue();
                while (sections.size() > index) {
                    sections.remove(sections.size() - 1);
                }
                sections.add(new String[]{(String) args.get("heading"), (String) args.get("text"),
                        (String) args.get("passages_json")});
                return "OK";
            }
            if ("document_draft".equals(toolName)) {
                StringBuilder json = new StringBuilder("{\"sections\":[");
                for (int i = 0; i < sections.size(); i++) {
                    json.append(i == 0 ? "" : ",").append("{\"heading\":")
                            .append(ResearchHostControl.quote(sections.get(i)[0])).append(",\"text\":")
                            .append(ResearchHostControl.quote(sections.get(i)[1])).append(",\"passages\":")
                            .append(sections.get(i)[2]).append('}');
                }
                return json.append("]}").toString();
            }
            if ("document_save".equals(toolName)) {
                document = (String) args.get("document_json");
            }
            return "OK";
        }
    }

    /** Ein Modell, das beim n-ten Aufruf scheitert und sonst „Kapiteltext k [P1]“ liefert. */
    private static final class FailingModel implements MainModelChat {
        final int failAt;
        int calls;

        FailingModel(int failAt) {
            this.failAt = failAt;
        }

        @Override
        public MainModelChatResult complete(List<ChatMessage> messages, double temperature, int maxOutputTokens) {
            calls++;
            if (calls == failAt) {
                return MainModelChatResult.failure(MainModelChatResult.Status.TIMEOUT, "Zeitüberschreitung");
            }
            String user = messages.get(messages.size() - 1).getContent();
            int start = user.indexOf("ABSCHNITT: ");
            String heading = start < 0 ? "?" : user.substring(start + 11, user.indexOf('\n', start));
            return MainModelChatResult.ok("Text zu " + heading + ". [P1]");
        }

        @Override
        public String modelName() {
            return "fake";
        }
    }

    @Test
    public void aFailingChapterKeepsTheEarlierOnesAndTheNextRunResumesThere() throws Exception {
        DraftHost host = new DraftHost();
        Out out = new Out();
        String reply = new ResearchWriting(host, new FailingModel(3)).document(out);

        assertEquals("Kapitel 1 und 2 sind gespeichert", 2, host.sections.size());
        assertTrue(host.sections.get(0)[1], host.sections.get(0)[1].contains("Text zu 1. Zweck. [[" + PASSAGE + "]]"));
        assertEquals("kein unvollständiges Dokument", null, host.document);
        assertTrue(reply, reply.contains("Kapitel 3 („3. Planung“) konnte nicht geschrieben werden"));
        assertTrue(reply, reply.contains("Kapitel 1–2 sind gespeichert"));

        FailingModel second = new FailingModel(-1);
        Out resumed = new Out();
        String done = new ResearchWriting(host, second).document(resumed);
        assertEquals("nur Kapitel 3 und 4 werden neu geschrieben", 2, second.calls);
        assertTrue(resumed.lines.toString(), resumed.lines.get(0).contains("Ich setze bei Kapitel 3 fort"));
        assertTrue(done, done.contains("ist fertig (4 Abschnitte)"));
        assertTrue(host.document, host.document.contains("Text zu 1. Zweck."));
        assertTrue(host.document, host.document.contains("Text zu 3. Planung."));
        assertTrue(host.document, host.document.contains("\"" + PASSAGE + "\":\"" + PAGE + "\""));
    }

    @Test
    public void aQuestionTheCorpusAnswersNeedsNoKnowledgeSearch() throws Exception {
        Host host = new Host().answer("project_search", HIT);
        String answer = new ResearchWriting(host, new Model("IAR bündelt die Auswertung. [P1]"))
                .answer("Was ist IAR?", true, new Out());
        assertTrue(answer, answer.contains("Auswertung. [1]"));
        assertTrue(host.calls.toString(), !host.calls.contains("knowledge_search"));
    }

    @Test
    public void aChapterWithoutPassagesSearchesTheKnowledgeSourcesAndAddsTheirPages() throws Exception {
        Host host = new Host().answer("research_outline", "# IAR\n\n## 1. Planung\n- Meilensteine\n")
                .answer("project_search", "", HIT)
                .answer("knowledge_search", "[1] IAR Planung\nid: " + PAGE + "\nurl: \nMeilensteine")
                .answer("knowledge_read", "Meilensteine: Pilot im Frühjahr.")
                .answer("project_source_add", "source_id=" + PAGE);
        new ResearchWriting(host, new Model("Der Pilot läuft im Frühjahr. [P1]")).document(new Out());
        assertEquals("Planung", host.lastArgs.get("knowledge_search").get("query"));
        assertEquals(PAGE, host.lastArgs.get("project_source_add").get("capture_id"));
        String json = (String) host.lastArgs.get("document_save").get("document_json");
        assertTrue(json, json.contains("[[" + PASSAGE + "]]"));
    }
}
