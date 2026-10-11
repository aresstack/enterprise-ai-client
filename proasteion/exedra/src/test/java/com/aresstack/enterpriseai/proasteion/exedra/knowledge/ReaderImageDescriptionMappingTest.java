package com.aresstack.enterpriseai.proasteion.exedra.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.image.ImageAnalysis;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderImageDescription;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Die Abbildung der Bildbeschreibung im Lesebereich: Text, kein Bildmodell, leere Antwort, Fehler. */
public class ReaderImageDescriptionMappingTest {

    private static final String SRC = "https://confluence.invalid/download/attachments/7/a.png";

    @Test
    public void textBecomesTheDescription() {
        ReaderImageDescription shown = KnowledgeReadingController.describeOne(
                () -> new ImageAnalysis("att1", "Bild", "2", "", "Ein Diagramm."), SRC);
        assertTrue(shown.available());
        assertEquals("Ein Diagramm.", shown.text());
    }

    @Test
    public void withoutVisionModelTheSettingsAreNamed() {
        ReaderImageDescription shown = KnowledgeReadingController.describeOne(() -> null, SRC);
        assertFalse(shown.available());
        assertEquals(KnowledgeReadingController.NO_VISION_MODEL, shown.problem());
    }

    @Test
    public void emptyAnswerAndFailureAreHints() {
        assertEquals(KnowledgeReadingController.EMPTY_DESCRIPTION, KnowledgeReadingController.describeOne(
                () -> new ImageAnalysis("att1", "Bild", "2", "", ""), SRC).problem());
        assertEquals(KnowledgeReadingController.DESCRIPTION_FAILED, KnowledgeReadingController.describeOne(() -> {
            throw new IllegalStateException("Modell antwortet nicht");
        }, SRC).problem());
    }
}
