package com.aresstack.enterpriseai.astu.acropolis.speech;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Markdown headings and list items keep the blank lines between them, so PARAGRAPHS chunking sees each section. */
public class SpeechTextTest {

    static final String NUMBERED_SECTIONS = "Einleitung.\n\n"
            + "### 1. **Fragen beantworten**\n"
            + "* **Allgemeinwissen** und anderes.\n"
            + "* **Fachwissen** und anderes.\n\n"
            + "### 2. **Dokumente**\n"
            + "* **Durchsuchen** von Anhängen.\n\n"
            + "### 3. **Wissensdatenbank**\n"
            + "* **Interne Quellen** durchsuchen.";

    @Test
    public void markdownListsKeepTheirParagraphBoundariesForReadAloud() {
        String plain = SpeechText.plainText(NUMBERED_SECTIONS);

        String[] paragraphs = plain.split("\\n\\s*\\n");
        assertEquals(plain, 4, paragraphs.length);
        assertEquals("Einleitung.", paragraphs[0]);
        assertEquals("1. Fragen beantworten\nAllgemeinwissen und anderes.\nFachwissen und anderes.", paragraphs[1]);
        assertEquals("2. Dokumente\nDurchsuchen von Anhängen.", paragraphs[2]);
        assertEquals("3. Wissensdatenbank\nInterne Quellen durchsuchen.", paragraphs[3]);
    }

    @Test
    public void windowsLineEndsAndTableRulesNeverMergeParagraphs() {
        String markdown = "Vorher.\r\n\r\n- Punkt eins\r\n- Punkt zwei\r\n\r\n| A | B |\n|---|---|\n| 1 | 2 |\n\nNachher.";

        List<String> chunks = SpeechText.chunks(SpeechText.plainText(markdown), SpeechChunking.PARAGRAPHS);

        assertEquals(chunks.toString(), 4, chunks.size());
        assertEquals("Vorher.", chunks.get(0));
        assertTrue(chunks.get(1), chunks.get(1).startsWith("Punkt eins"));
        assertTrue(chunks.get(2), chunks.get(2).contains("1 2"));
        assertEquals("Nachher.", chunks.get(3));
    }
}
