package com.aresstack.enterpriseai.speech.opennlp;

import com.aresstack.enterpriseai.speech.api.WordClass;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PosTagsTest {

    @Test
    public void mapsSttsTags() {
        assertEquals(WordClass.INTERROGATIVE, PosTags.wordClass("PWS"));
        assertEquals(WordClass.VERB_FINITE, PosTags.wordClass("VAFIN"));
        assertEquals(WordClass.VERB_IMPERATIVE, PosTags.wordClass("VVIMP"));
        assertEquals(WordClass.VERB_NONFINITE, PosTags.wordClass("VVINF"));
        assertEquals(WordClass.NOUN, PosTags.wordClass("NN"));
        assertEquals(WordClass.PROPER_NOUN, PosTags.wordClass("NE"));
        assertEquals(WordClass.INTERJECTION, PosTags.wordClass("ITJ"));
        assertEquals(WordClass.PUNCTUATION, PosTags.wordClass("$."));
        assertEquals(WordClass.OTHER, PosTags.wordClass("ART"));
    }

    @Test
    public void mapsPennAndUniversalTags() {
        assertEquals(WordClass.VERB_FINITE, PosTags.wordClass("VBZ"));
        assertEquals(WordClass.PROPER_NOUN, PosTags.wordClass("NNP"));
        assertEquals(WordClass.INTERROGATIVE, PosTags.wordClass("WRB"));
        assertEquals(WordClass.PROPER_NOUN, PosTags.wordClass("PROPN"));
        assertEquals(WordClass.OTHER, PosTags.wordClass(null));
    }

    @Test
    public void withoutPartOfSpeechModelNoWordClasses() {
        OpenNlpTextAnalysisEngine engine = OpenNlpTextAnalysisEngine.load(null,
                Collections.<String, java.nio.file.Path>emptyMap(),
                Collections.singletonMap("de", Paths.get("no-tokenizer.bin")),
                Collections.singletonMap("de", Paths.get("no-pos.bin")));
        assertFalse(engine.tokenizes("de"));
        assertFalse(engine.tagsWordClasses("de"));
        assertTrue(engine.tokens("Was ist QZX?", "de").isEmpty());
        assertTrue(engine.tag(Arrays.asList("Was", "ist", "QZX", "?"), "de").isEmpty());
        assertEquals(2, engine.problems().size());
    }
}
