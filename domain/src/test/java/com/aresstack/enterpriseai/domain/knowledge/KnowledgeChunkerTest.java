package com.aresstack.enterpriseai.domain.knowledge;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class KnowledgeChunkerTest {

    private static final KnowledgeResource RESOURCE = KnowledgeResource
            .builder(KnowledgeResourceId.of("wiki:intranet/Test"), KnowledgeSourceId.of("wiki-intranet"))
            .title("Test").build();

    private final KnowledgeTokenCounter counter = KnowledgeTokenCounter.wordsAndSymbols();

    private static List<KnowledgeChunk> chunk(String text, int maxTokens, int overlapSentences) {
        return new KnowledgeChunker(KnowledgeChunkingPolicy.of(maxTokens, overlapSentences))
                .chunk(KnowledgeDocument.of(RESOURCE, text));
    }

    private static String sentences(String prefix, int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(prefix).append(' ').append(i).append(" enthält genau sechs Wörter.");
        }
        return text.toString();
    }

    @Test
    public void emptyAndBlankDocumentsYieldNoChunks() {
        assertTrue(chunk("", 50, 1).isEmpty());
        assertTrue(chunk("  \n\n \t ", 50, 1).isEmpty());
        assertTrue(chunk(null, 50, 1).isEmpty());
    }

    @Test
    public void shortTextIsOneChunkWithStableIdentity() {
        List<KnowledgeChunk> chunks = chunk("Kurzer Text. Nur zwei Sätze.", 50, 1);

        assertEquals(1, chunks.size());
        KnowledgeChunk chunk = chunks.get(0);
        assertEquals("Kurzer Text. Nur zwei Sätze.", chunk.text());
        assertEquals("wiki:intranet/Test#chunk-0", chunk.id().value());
        assertEquals(RESOURCE.id(), chunk.resourceId());
        assertEquals(RESOURCE.sourceId(), chunk.sourceId());
        assertEquals(0, chunk.ordinal());
        assertTrue(chunk.headingPath().isEmpty());
        assertEquals(counter.count(chunk.text()), chunk.tokenCount());
    }

    @Test
    public void longTextIsSplitAtSentenceBoundariesWithinBudget() {
        // 30 Sätze à 7 Tokens (6 Wörter + Punkt), Budget 30 => 4 Sätze je Chunk ohne Overlap.
        String text = sentences("Satz", 30);
        List<KnowledgeChunk> chunks = chunk(text, 30, 0);

        assertEquals(8, chunks.size());
        StringBuilder rejoined = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            assertEquals(i, chunk.ordinal());
            assertTrue("Budget überschritten: " + chunk, chunk.tokenCount() <= 30);
            assertTrue("Chunk endet nicht am Satzende: " + chunk.text(), chunk.text().endsWith("Wörter."));
            assertTrue("Chunk beginnt nicht am Satzanfang: " + chunk.text(), chunk.text().startsWith("Satz "));
            if (rejoined.length() > 0) {
                rejoined.append(' ');
            }
            rejoined.append(chunk.text());
        }
        assertEquals("ohne Overlap ergibt die Folge der Chunks den Text", text, rejoined.toString());
    }

    @Test
    public void overlapRepeatsTheLastSentencesOfThePreviousChunk() {
        List<KnowledgeChunk> chunks = chunk(sentences("Satz", 12), 30, 1);

        assertTrue(chunks.size() > 1);
        for (int i = 1; i < chunks.size(); i++) {
            List<String> previous = new SentenceSplitter().split(chunks.get(i - 1).text());
            List<String> current = new SentenceSplitter().split(chunks.get(i).text());
            assertEquals("Chunk " + i + " beginnt mit dem letzten Satz des Vorgängers",
                    previous.get(previous.size() - 1), current.get(0));
            assertTrue("jeder Chunk bringt mindestens einen neuen Satz", current.size() >= 2);
            assertTrue(chunks.get(i).tokenCount() <= 30);
        }
        assertTrue(chunks.get(chunks.size() - 1).text().endsWith("Satz 12 enthält genau sechs Wörter."));
    }

    @Test
    public void overlapOfTwoSentencesAndNoOverlap() {
        List<KnowledgeChunk> two = chunk(sentences("Satz", 12), 30, 2);
        List<String> first = new SentenceSplitter().split(two.get(0).text());
        List<String> second = new SentenceSplitter().split(two.get(1).text());
        assertEquals(first.subList(first.size() - 2, first.size()), second.subList(0, 2));

        List<KnowledgeChunk> none = chunk(sentences("Satz", 12), 30, 0);
        List<String> seen = new ArrayList<String>();
        for (KnowledgeChunk chunk : none) {
            for (String sentence : new SentenceSplitter().split(chunk.text())) {
                assertFalse("ohne Overlap kein Satz doppelt: " + sentence, seen.contains(sentence));
                seen.add(sentence);
            }
        }
        assertEquals(12, seen.size());
    }

    @Test
    public void paragraphsArePackedTogetherAndKeepTheirBlankLine() {
        String text = "Erster Absatz, Satz eins. Satz zwei.\n\nZweiter Absatz\nmit Zeilenumbruch im Satz.\n\n\nDritter.";
        List<KnowledgeChunk> chunks = chunk(text, 100, 1);

        assertEquals(1, chunks.size());
        assertEquals("Erster Absatz, Satz eins. Satz zwei.\n\nZweiter Absatz mit Zeilenumbruch im Satz.\n\nDritter.",
                chunks.get(0).text());
    }

    @Test
    public void paragraphBoundaryIsPreservedWhenPackingAcrossChunks() {
        String text = sentences("Alpha", 3) + "\n\n" + sentences("Beta", 3);
        List<KnowledgeChunk> chunks = chunk(text, 30, 0);

        assertEquals(2, chunks.size());
        assertEquals(sentences("Alpha", 3) + "\n\n" + "Beta 1 enthält genau sechs Wörter.", chunks.get(0).text());
        assertEquals("Beta 2 enthält genau sechs Wörter. Beta 3 enthält genau sechs Wörter.", chunks.get(1).text());
    }

    @Test
    public void headingsAreHardBoundariesAndFormTheHeadingPath() {
        String text = "Einleitung ohne Überschrift.\n"
                + "# Installation\n"
                + "Allgemeine Hinweise.\n\n"
                + "## Linux\n"
                + "Paket installieren.\n"
                + "### Debian ###\n"
                + "apt verwenden.\n"
                + "## Windows\n"
                + "Installer starten.\n"
                + "# Betrieb\n"
                + "Dienst starten.";
        List<KnowledgeChunk> chunks = chunk(text, 100, 1);

        assertEquals(6, chunks.size());
        assertEquals(Collections.<String>emptyList(), chunks.get(0).headingPath());
        assertEquals("Einleitung ohne Überschrift.", chunks.get(0).text());
        assertEquals(Collections.singletonList("Installation"), chunks.get(1).headingPath());
        assertEquals("Allgemeine Hinweise.", chunks.get(1).text());
        assertEquals(Arrays.asList("Installation", "Linux"), chunks.get(2).headingPath());
        assertEquals(Arrays.asList("Installation", "Linux", "Debian"), chunks.get(3).headingPath());
        assertEquals(Arrays.asList("Installation", "Windows"), chunks.get(4).headingPath());
        assertEquals(Collections.singletonList("Betrieb"), chunks.get(5).headingPath());
        assertEquals("Dienst starten.", chunks.get(5).text());

        assertEquals("Installation > Linux > Debian", chunks.get(3).headingLine());
        assertEquals("Installation > Linux > Debian\n\napt verwenden.", chunks.get(3).textWithHeading());
        assertEquals(counter.count(chunks.get(3).textWithHeading()), chunks.get(3).tokenCount());
    }

    @Test
    public void headingCountsAgainstTheBudgetAndOverlapStaysInsideItsSection() {
        String text = "# Kapitel mit Titel\n" + sentences("Satz", 8) + "\n# Anderes\n" + sentences("Neu", 2);
        List<KnowledgeChunk> chunks = chunk(text, 30, 1);

        for (KnowledgeChunk chunk : chunks) {
            assertTrue("Budget inkl. Überschrift: " + chunk, chunk.tokenCount() <= 30);
        }
        KnowledgeChunk firstOfOther = null;
        for (KnowledgeChunk chunk : chunks) {
            if (chunk.headingPath().equals(Collections.singletonList("Anderes"))) {
                firstOfOther = chunk;
                break;
            }
        }
        assertEquals("kein Overlap über eine Überschrift hinweg",
                sentences("Neu", 2), firstOfOther.text());
    }

    @Test
    public void headingWithoutBodyProducesNoChunk() {
        List<KnowledgeChunk> chunks = chunk("# Leer\n## Unterkapitel\nInhalt.", 50, 1);

        assertEquals(1, chunks.size());
        assertEquals(Arrays.asList("Leer", "Unterkapitel"), chunks.get(0).headingPath());
    }

    @Test
    public void listItemsStayOnTheirOwnLines() {
        String text = "Schritte:\n\n- Erstens das Paket laden\n- Zweitens\n  mit Fortsetzung\n1. Drittens prüfen";
        List<KnowledgeChunk> chunks = chunk(text, 100, 1);

        assertEquals(1, chunks.size());
        assertEquals("Schritte:\n\n- Erstens das Paket laden\n- Zweitens mit Fortsetzung\n1. Drittens prüfen",
                chunks.get(0).text());
    }

    @Test
    public void listDirectlyAfterTextWithoutBlankLineStartsItsOwnBlock() {
        List<KnowledgeChunk> chunks = chunk("Schritte:\n- Eins\n- Zwei", 100, 1);

        assertEquals("Schritte:\n\n- Eins\n- Zwei", chunks.get(0).text());
    }

    @Test
    public void chunkerFingerprintIncludesPolicyAndCounter() {
        assertEquals("chunker-v1;maxTokens=350;overlapSentences=1;counter=words-and-symbols-v1",
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()).fingerprint());
        KnowledgeTokenCounter characters = new KnowledgeTokenCounter() {
            @Override
            public int count(String text) {
                return text.length();
            }

            @Override
            public String id() {
                return "characters-v1";
            }
        };
        assertEquals("chunker-v1;maxTokens=350;overlapSentences=1;counter=characters-v1",
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults(), characters).fingerprint());
    }

    @Test
    public void codeBlocksAreNeverSplitIntoSentences() {
        String text = "Beispiel:\n\n```java\nString s = \"a. B\";\n\nreturn s.trim();\n```\n\nDanach Text.";
        List<KnowledgeChunk> chunks = chunk(text, 100, 0);

        assertEquals(1, chunks.size());
        assertEquals("Beispiel:\n\nString s = \"a. B\";\nreturn s.trim();\n\nDanach Text.", chunks.get(0).text());
    }

    @Test
    public void oversizedSentenceIsSplitAtWordBoundaries() {
        StringBuilder sentence = new StringBuilder("Anfang");
        for (int i = 0; i < 40; i++) {
            sentence.append(" wort").append(i);
        }
        sentence.append('.');
        List<KnowledgeChunk> chunks = chunk(sentence.toString(), 10, 1);

        assertTrue(chunks.size() >= 4);
        StringBuilder rejoined = new StringBuilder();
        for (KnowledgeChunk chunk : chunks) {
            assertTrue("Budget: " + chunk, chunk.tokenCount() <= 10);
            if (rejoined.length() > 0) {
                rejoined.append(' ');
            }
            rejoined.append(chunk.text());
        }
        assertTrue(rejoined.toString().startsWith("Anfang wort0 wort1"));
        assertTrue(rejoined.toString().endsWith("wort39."));
    }

    @Test
    public void unicodeUmlautsAreKeptCountedAndNormalized() {
        // "Größe" einmal vorkomponiert, einmal als o + U+0308 (NFD).
        String decomposed = "Größe";
        String text = "Äpfel und Öl kosten 3 €. Über die " + decomposed + " entscheidet ÜBERALL der Kunde. "
                + "Ça marche – naïve Grüße! Ende.";
        List<KnowledgeChunk> chunks = chunk(text, 12, 0);

        StringBuilder all = new StringBuilder();
        for (KnowledgeChunk chunk : chunks) {
            all.append(chunk.text()).append('|');
            assertTrue(chunk.tokenCount() <= 12);
        }
        assertEquals("Äpfel und Öl kosten 3 €.|Über die Größe entscheidet ÜBERALL der Kunde.|"
                + "Ça marche – naïve Grüße! Ende.|", all.toString());
        assertTrue("NFC: kein kombinierendes Zeichen mehr", all.indexOf("̈") < 0);
        assertEquals(7, counter.count("Äpfel und Öl kosten 3 €."));
        assertEquals(1, counter.count(decomposed));
    }

    @Test
    public void windowsLineEndingsGiveTheSameChunksAsUnixLineEndings() {
        String unix = "# Titel\nErster Satz.\n\nZweiter Absatz.";
        assertEquals(chunk(unix, 50, 1), chunk(unix.replace("\n", "\r\n"), 50, 1));
    }

    @Test
    public void chunkingIsDeterministic() {
        String text = "# A\n" + sentences("Satz", 20) + "\n\n## B\n- eins\n- zwei\n\n" + sentences("Mehr", 7);
        List<KnowledgeChunk> first = chunk(text, 25, 1);
        List<KnowledgeChunk> second = chunk(text, 25, 1);

        assertEquals(first, second);
        for (int i = 0; i < first.size(); i++) {
            assertEquals(KnowledgeChunkId.of(RESOURCE.id(), i), first.get(i).id());
        }
    }

    @Test
    public void customTokenCounterDrivesTheBudget() {
        KnowledgeTokenCounter characters = new KnowledgeTokenCounter() {
            @Override
            public int count(String text) {
                return text.length();
            }
        };
        List<KnowledgeChunk> chunks = new KnowledgeChunker(KnowledgeChunkingPolicy.of(15, 0), characters)
                .chunk(KnowledgeDocument.of(RESOURCE, "Eins zwei. Drei vier. Fünf sechs."));

        assertEquals(Arrays.asList("Eins zwei.", "Drei vier.", "Fünf sechs."),
                Arrays.asList(chunks.get(0).text(), chunks.get(1).text(), chunks.get(2).text()));
    }

    @Test
    public void separatorsCountAgainstTheBudgetForCustomCounters() {
        KnowledgeTokenCounter characters = new KnowledgeTokenCounter() {
            @Override
            public int count(String text) {
                return text.length();
            }
        };
        List<KnowledgeChunk> chunks = new KnowledgeChunker(KnowledgeChunkingPolicy.of(14, 0), characters)
                .chunk(KnowledgeDocument.of(RESOURCE, "Abcdef. Ghijkl.\n\n# Kopf\nAbcdefg."));

        for (KnowledgeChunk chunk : chunks) {
            assertTrue("Budget inkl. Trenner und Überschrift: " + chunk, chunk.tokenCount() <= 14);
            assertEquals(characters.count(chunk.textWithHeading()), chunk.tokenCount());
        }
        assertEquals(Arrays.asList("Abcdef.", "Ghijkl.", "Abcdefg."),
                Arrays.asList(chunks.get(0).text(), chunks.get(1).text(), chunks.get(2).text()));
    }

    @Test
    public void oversizedHeadingsAreShortenedToHalfTheBudget() {
        String text = "# Sehr langer Oberbegriff mit vielen Woertern\n"
                + "## Unterkapitel mit ebenfalls langem Titel hier\n"
                + "Inhalt.";
        List<KnowledgeChunk> chunks = chunk(text, 10, 0);

        assertEquals(1, chunks.size());
        assertEquals("äußere Überschrift entfällt, innere wird gekürzt",
                Collections.singletonList("Unterkapitel mit ebenfalls langem Titel"), chunks.get(0).headingPath());
        assertEquals("Inhalt.", chunks.get(0).text());
        assertTrue(chunks.get(0).tokenCount() <= 10);

        List<KnowledgeChunk> nested = chunk("# A\n## Langer Unterabschnitt mit Titel\nText hier.", 10, 0);
        assertEquals(Collections.singletonList("Langer Unterabschnitt mit Titel"), nested.get(0).headingPath());
    }

    @Test
    public void headingWhoseFirstWordExceedsHalfTheBudgetIsDropped() {
        KnowledgeTokenCounter characters = new KnowledgeTokenCounter() {
            @Override
            public int count(String text) {
                return text.length();
            }
        };
        List<KnowledgeChunk> chunks = new KnowledgeChunker(KnowledgeChunkingPolicy.of(8, 0), characters)
                .chunk(KnowledgeDocument.of(RESOURCE, "# !!!!!!!!!!!!!!!!!!!!\nBody. Text."));

        assertEquals(Arrays.asList("Body.", "Text."), Arrays.asList(chunks.get(0).text(), chunks.get(1).text()));
        for (KnowledgeChunk chunk : chunks) {
            assertTrue("Überschrift entfällt: " + chunk, chunk.headingPath().isEmpty());
            assertTrue(chunk.tokenCount() <= 8);
        }
    }

    @Test
    public void policyValidatesAndDescribesItself() {
        assertEquals("chunker-v1;maxTokens=350;overlapSentences=1", KnowledgeChunkingPolicy.defaults().fingerprint());
        try {
            KnowledgeChunkingPolicy.of(KnowledgeChunkingPolicy.MIN_MAX_TOKENS - 1, 0);
            fail();
        } catch (IllegalArgumentException expected) {
            // erwartet
        }
        try {
            KnowledgeChunkingPolicy.of(100, -1);
            fail();
        } catch (IllegalArgumentException expected) {
            // erwartet
        }
    }
}
