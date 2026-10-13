package com.aresstack.enterpriseai.proasteion.exedra.ui.markdown;

import com.aresstack.enterpriseai.astu.acropolis.speech.SpeechChunking;
import com.aresstack.enterpriseai.astu.acropolis.speech.SpeechText;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Vorlesen spricht, was die Anzeige zeigt: dieselbe Markdown-Deutung, Tabellenzeilen als Sätze, nichts Unsichtbares
 * (Issue 88).
 */
public class MarkdownSpeechTest {

    @Test
    public void tableRowsBecomeSentencesWithTheirColumnHeaders() {
        String spoken = MarkdownSpeech.spokenText("Die Vorsysteme:\n\n| Element | Bedeutung |\n|---|---|\n"
                + "| NIZZA | Vorsystem |\n| REST API | Schnittstelle |\n\nDanach folgt Text.");
        assertEquals("Die Vorsysteme:\n\nElement: NIZZA, Bedeutung: Vorsystem.\n"
                + "Element: REST API, Bedeutung: Schnittstelle.\n\nDanach folgt Text.", spoken);
        assertFalse(spoken, spoken.contains("|"));
        assertFalse(spoken, spoken.contains("---"));
    }

    @Test
    public void hiddenMarkupIsNeverSpoken() {
        String spoken = MarkdownSpeech.spokenText("Erster Satz.<!-- 12345 versteckt -->\n\n"
                + "<!-- Kommentarblock 678 -->\n\nPreis &amp; Leistung&nbsp;sind gut. "
                + "Ein <span title=\"999\">Wort</span>​ mit­ Trenn﻿zeichen.\n\n"
                + "Siehe [Handbuch](https://example.invalid/seite?id=4711) und https://example.invalid/42.\n\n"
                + "```java\nint x = 31337;\n```\n\n```mermaid\ngraph TD; A-->B\n```\n\n[ref]: https://example.invalid/7");
        assertFalse(spoken, spoken.contains("12345"));
        assertFalse(spoken, spoken.contains("678"));
        assertFalse(spoken, spoken.contains("999"));
        assertFalse(spoken, spoken.contains("4711"));
        assertFalse(spoken, spoken.contains("42"));
        assertFalse(spoken, spoken.contains("31337"));
        assertFalse(spoken, spoken.contains("graph"));
        assertFalse(spoken, spoken.contains("&amp;"));
        assertFalse(spoken, spoken.contains("<"));
        assertTrue(spoken, spoken.contains("Preis & Leistung sind gut."));
        assertTrue(spoken, spoken.contains("Ein Wort mit Trennzeichen."));
        assertTrue(spoken, spoken.contains("Siehe Handbuch und"));
        for (char c : spoken.toCharArray()) {
            assertFalse("unsichtbares Zeichen U+" + Integer.toHexString(c), Character.getType(c) == Character.FORMAT);
        }
    }

    @Test
    public void anAnswerWrappedInAMarkdownFenceIsReadLikeItIsShown() {
        String spoken = MarkdownSpeech.spokenText("```markdown\n## Ergebnis\n\nDer Dienst läuft.\n```");
        assertEquals("Ergebnis.\nDer Dienst läuft.", spoken);
    }

    @Test
    public void headingsListsAndParagraphsKeepTheirBoundaries() {
        String spoken = MarkdownSpeech.spokenText("# Betrieb\nErster Absatz\nmit Umbruch.\n\n- Punkt eins\n"
                + "- Punkt **zwei**\n  1. Unterpunkt\n\n> Zitat.\n\n---\n\nEnde");
        assertEquals("Betrieb.\nErster Absatz mit Umbruch.\n\nPunkt eins.\nPunkt zwei.\nUnterpunkt.\n\nZitat."
                + "\n\nEnde", spoken);
        List<String> paragraphs = SpeechText.chunks(spoken, SpeechChunking.PARAGRAPHS);
        assertEquals(4, paragraphs.size());
        assertEquals("Betrieb. Erster Absatz mit Umbruch.", paragraphs.get(0));
    }

    @Test
    public void pictographsDoNotReachTheVoice() {
        String spoken = MarkdownSpeech.spokenText("Status ✅ fertig → weiter 🚀️.");
        assertEquals("Status fertig weiter.", spoken);
    }

    @Test
    public void plainTextStaysAsItIs() {
        // Der Lesebereich gibt nach einem Doppelklick schon gesprochenen Text weiter: er bleibt Wort für Wort gleich.
        assertEquals("Betrieb. Erster Absatz.\n\nElement: NIZZA, Bedeutung: Vorsystem.",
                MarkdownSpeech.spokenText("Betrieb.\nErster Absatz.\n\nElement: NIZZA, Bedeutung: Vorsystem."));
        assertEquals("", MarkdownSpeech.spokenText(null));
        assertEquals("", MarkdownSpeech.spokenText(" \n "));
    }
}
