package com.aresstack.enterpriseai.astu.acropolis.passage;

import com.aresstack.enterpriseai.astu.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.astu.embedding.EmbeddingVector;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link TopicClusterer} und {@link VectorMath} wie in askai-java8 arch ({@code ResearchMethodologyVerticalSliceTest},
 * {@code LiveOutlineProjectionBuilderTest}): inhaltsgleiche Passagen landen in einem Thema, verschiedene getrennt,
 * deterministisch; Vektoren verschiedener Modelle werden nie verglichen.
 */
public class TopicClustererTest {

    private final VocabularyEmbeddingPort vocabulary = new VocabularyEmbeddingPort("drucker", "toner", "urlaub",
            "antrag");

    private TopicClusterer.Member member(String id, String text) {
        return new TopicClusterer.Member(id, text, vocabulary.vectorFor(text));
    }

    private List<TopicClusterer.Member> corpus() {
        return Arrays.asList(
                member("p1", "Der Drucker braucht Toner. Toner liegt im Schrank."),
                member("p2", "Urlaub beantragt man mit dem Antrag. Der Antrag geht an das Personal."),
                member("p3", "Ist der Toner leer, meldet der Drucker einen Fehler."),
                member("p4", "Für den Urlaub reicht ein Antrag zwei Wochen vorher."));
    }

    @Test
    public void groupsPassagesOfTheSameTopic() {
        List<TopicClusterer.Topic> topics = new TopicClusterer().cluster(corpus());
        assertEquals(2, topics.size());
        assertEquals(Arrays.asList("p1", "p3"), sorted(topics.get(0).memberIds()));
        assertEquals(Arrays.asList("p2", "p4"), sorted(topics.get(1).memberIds()));
        assertEquals("Thema heißt nach seinem ersten Mitglied", "p1", topics.get(0).topicId());
        assertTrue(topics.get(0).confidence() > 0.9);
    }

    @Test
    public void titleIsTheFirstSentenceOfTheRepresentative() {
        TopicClusterer.Topic topic = new TopicClusterer().cluster(corpus()).get(0);
        String representative = topic.representativeIds().get(0);
        assertTrue(Arrays.asList("p1", "p3").contains(representative));
        assertTrue(topic.suggestedTitle().equals("Der Drucker braucht Toner")
                || topic.suggestedTitle().equals("Ist der Toner leer, meldet der Drucker einen Fehler"));
    }

    @Test
    public void clusteringIsDeterministic() {
        List<String> first = describe(new TopicClusterer().cluster(corpus()));
        for (int i = 0; i < 5; i++) {
            assertEquals(first, describe(new TopicClusterer().cluster(corpus())));
        }
    }

    @Test
    public void vectorsOfDifferentModelsAreNeverCompared() {
        EmbeddingVector a = EmbeddingVector.of(EmbeddingModelIdentity.of("a", 2), new float[]{1f, 0f});
        EmbeddingVector b = EmbeddingVector.of(EmbeddingModelIdentity.of("b", 2), new float[]{1f, 0f});
        try {
            VectorMath.mean(Arrays.asList(a, b));
            fail("mean über zwei Modelle");
        } catch (IllegalArgumentException expected) {
            // wie arch: harter Fehler, kein Rauschen
        }
        try {
            VectorMath.cosine(a, b);
            fail("cosine über zwei Modelle");
        } catch (RuntimeException expected) {
            // EmbeddingWorldMismatchException
        }
        assertEquals(1.0, VectorMath.cosine(a, a), 1e-9);
    }

    private static List<String> sorted(List<String> ids) {
        List<String> copy = new ArrayList<String>(ids);
        java.util.Collections.sort(copy);
        return copy;
    }

    private static List<String> describe(List<TopicClusterer.Topic> topics) {
        List<String> out = new ArrayList<String>();
        for (TopicClusterer.Topic topic : topics) {
            out.add(topic.topicId() + topic.memberIds() + topic.representativeIds() + topic.suggestedTitle());
        }
        return out;
    }
}
