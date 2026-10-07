package com.aresstack.enterpriseai.application.mcp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class KnowledgeToolSettingsTest {

    @Test
    public void defaultsMatchTheDocumentedValues() {
        KnowledgeToolSettings settings = KnowledgeToolSettings.defaults();
        assertEquals(20_000, settings.maxResponseChars());
        assertEquals(600, settings.snippetChars());
        assertEquals(5, settings.defaultMaxResults());
        assertEquals(20, settings.maxFailuresListed());
    }

    @Test
    public void withMethodsReturnChangedCopies() {
        KnowledgeToolSettings defaults = KnowledgeToolSettings.defaults();
        KnowledgeToolSettings changed = defaults.withMaxResponseChars(5_000).withSnippetChars(100)
                .withDefaultMaxResults(3).withMaxFailuresListed(0);
        assertNotSame(defaults, changed);
        assertEquals(20_000, defaults.maxResponseChars());
        assertEquals(5_000, changed.maxResponseChars());
        assertEquals(100, changed.snippetChars());
        assertEquals(3, changed.defaultMaxResults());
        assertEquals(0, changed.maxFailuresListed());
    }

    @Test(expected = IllegalArgumentException.class)
    public void responseLimitBelowMinimumIsRejected() {
        KnowledgeToolSettings.defaults().withMaxResponseChars(KnowledgeToolSettings.MIN_RESPONSE_CHARS - 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void snippetBelowMinimumIsRejected() {
        KnowledgeToolSettings.defaults().withSnippetChars(KnowledgeToolSettings.MIN_SNIPPET_CHARS - 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroDefaultMaxResultsIsRejected() {
        KnowledgeToolSettings.defaults().withDefaultMaxResults(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeFailureListIsRejected() {
        KnowledgeToolSettings.defaults().withMaxFailuresListed(-1);
    }
}
