package com.aresstack.enterpriseai.application.mcp;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ToolTextTest {

    @Test
    public void truncateKeepsShortTextAndMarksLongText() {
        assertEquals("kurz", ToolText.truncate("kurz", 10));
        assertEquals("", ToolText.truncate(null, 10));

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            text.append('x');
        }
        String cut = ToolText.truncate(text.toString(), 200);

        assertTrue(cut, cut.length() <= 200);
        assertTrue(cut, cut.matches("x+\n… \\[gekürzt: \\d+ Zeichen ausgelassen]"));
        int kept = cut.indexOf('\n');
        assertEquals(cut, "\n… [gekürzt: " + (500 - kept) + " Zeichen ausgelassen]", cut.substring(kept));
    }

    @Test
    public void truncateFitsEvenWhenTheLimitIsTiny() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            text.append('y');
        }
        String cut = ToolText.truncate(text.toString(), 50);
        assertTrue(cut, cut.length() <= 50);
        assertTrue(cut, cut.contains("gekürzt"));
    }

    @Test
    public void truncateAndSnippetNeverSplitASurrogatePair() {
        String emoji = "a😀"; // 😀
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            text.append(emoji);
        }
        String snippet = ToolText.snippet(text.toString(), 10);
        String truncated = ToolText.truncate(text.toString(), 100);
        assertValidSurrogates(snippet);
        assertValidSurrogates(truncated);
        assertTrue(snippet, snippet.endsWith(" …"));
        assertTrue(snippet, snippet.length() <= 10);
    }

    @Test
    public void oneLineReplacesControlCharactersAndTrims() {
        assertEquals("Titel mit  Umbruch", ToolText.oneLine("  Titel mit\n Umbruch\t", 100));
        assertEquals("Titel …", ToolText.oneLine("Titel der zu lang ist", 7));
    }

    @Test
    public void revisionFormatsTimestampAndVersion() {
        assertEquals("unbekannt", ToolText.revision(KnowledgeRevision.unknown()));
        assertEquals("unbekannt", ToolText.revision(null));
        assertEquals("Version 7", ToolText.revision(KnowledgeRevision.version("7")));
        assertEquals("2024-01-02T03:04:05Z", ToolText.revision(KnowledgeRevision.modifiedAt(
                Instant.parse("2024-01-02T03:04:05Z"))));
        assertEquals("2024-01-02T03:04:05Z, Version r12", ToolText.revision(KnowledgeRevision.of(
                Instant.parse("2024-01-02T03:04:05Z"), "r12")));
    }

    @Test
    public void scoreUsesFourDecimalsWithDot() {
        assertEquals("0.0328", ToolText.score(0.03278688));
        assertEquals("-1.0000", ToolText.score(-1));
    }

    private static void assertValidSurrogates(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                assertTrue("High-Surrogate ohne Partner bei " + i, i + 1 < text.length()
                        && Character.isLowSurrogate(text.charAt(i + 1)));
                i++;
            } else {
                assertTrue("verwaister Low-Surrogate bei " + i, !Character.isLowSurrogate(c));
            }
        }
    }
}
