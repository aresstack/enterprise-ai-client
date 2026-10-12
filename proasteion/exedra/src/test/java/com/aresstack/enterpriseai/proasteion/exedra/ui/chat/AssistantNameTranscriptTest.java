package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Der Anzeigename über den Antworten: gesetzt, sofort übernommen, Rolle und Nutzerkopf unverändert. */
public class AssistantNameTranscriptTest {

    @Test
    public void headersUseTheConfiguredName() {
        ChatShellModel model = new ChatShellModel(() -> 0L);
        model.addUserMessage("x");
        TranscriptEntry answer = model.beginAssistantMessage();
        assertEquals("Merlins Tochter", ChatTranscriptPanel.header(answer, "Merlins Tochter"));
        model.cancelAssistantMessage();
        assertEquals("Merlins Tochter" + ChatTranscriptPanel.CANCELLED_SUFFIX,
                ChatTranscriptPanel.header(answer, "Merlins Tochter"));
        assertEquals(TranscriptEntry.Author.ASSISTANT, answer.getAuthor());
    }

    @Test
    public void renamingRelabelsExistingAnswersButNotTheUser() throws Exception {
        Edt.run(new Runnable() {
            @Override
            public void run() {
                ChatShellModel model = new ChatShellModel(() -> 0L);
                ChatTranscriptPanel transcript = new ChatTranscriptPanel(model, ComicPalette.defaultPalette(),
                        BubblePalette.windowsPhoneInspired());
                TranscriptEntry question = model.addUserMessage("Wie heißt du?");
                TranscriptEntry answer = model.beginAssistantMessage();
                model.appendAssistantDelta("Hallo!");
                model.completeAssistantMessage();
                assertEquals(ChatTranscriptPanel.ASSISTANT_HEADER, transcript.headerFor(answer.getId()));

                transcript.setAssistantName("  Merlins Tochter ");
                assertEquals("Merlins Tochter", transcript.assistantName());
                assertEquals("Merlins Tochter", transcript.headerFor(answer.getId()));
                assertEquals(ChatTranscriptPanel.USER_HEADER, transcript.headerFor(question.getId()));

                TranscriptEntry next = model.beginAssistantMessage();
                assertEquals("neue Antworten tragen den Namen", "Merlins Tochter", transcript.headerFor(next.getId()));

                transcript.setAssistantName("");
                assertEquals(ChatTranscriptPanel.ASSISTANT_HEADER, transcript.headerFor(answer.getId()));
            }
        });
    }
}
