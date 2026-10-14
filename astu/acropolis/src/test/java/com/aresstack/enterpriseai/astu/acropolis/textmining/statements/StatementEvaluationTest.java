package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import com.aresstack.enterpriseai.astu.acropolis.passage.SentenceSegmenter;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StatementEvaluationTest {

    private static final List<String> REQUIRED_PHENOMENA = Arrays.asList("active", "passive", "coordination",
            "separable", "adjective", "dative", "preposition", "subordinate", "relative-clause", "negation",
            "modality", "condition", "pronoun", "multiword", "order", "subject-not-actor");

    @Test
    public void builtInSetHasFiftySentencesCoveringThePhenomena() {
        List<StatementEvaluation.Case> cases = StatementEvaluation.builtIn();
        assertEquals(50, cases.size());
        Set<String> covered = new LinkedHashSet<String>();
        for (StatementEvaluation.Case gold : cases) {
            covered.addAll(gold.phenomena());
            assertTrue(gold.id(), gold.sentence().endsWith("."));
        }
        assertTrue(covered.toString(), covered.containsAll(REQUIRED_PHENOMENA));
    }

    @Test
    public void everyAnnotatedTermStemIsInItsSentence() {
        for (StatementEvaluation.Case gold : StatementEvaluation.builtIn()) {
            String sentence = gold.sentence().toLowerCase(Locale.ROOT);
            for (String term : gold.mentions()) {
                for (String word : term.split(" ")) {
                    String stem = word.length() > 4 ? word.substring(0, 4) : word;
                    assertTrue(gold.id() + ": " + term, sentence.contains(stem));
                }
            }
        }
    }

    @Test
    public void correctAnalysisScoresFull() {
        FakeChatCompletionPort chat = new FakeChatCompletionPort();
        chat.enqueueAnswer(StatementAnalyzerTest.ANSWER);
        StatementEvaluation.Report report = StatementEvaluation.evaluate(firstCase(), analysis(chat));

        assertEquals(1.0, report.precision("roles"), 0.0);
        assertEquals(1.0, report.recall("roles"), 0.0);
        assertEquals(1.0, report.recall("events"), 0.0);
        assertEquals(1.0, report.recall("relations"), 0.0);
        assertEquals(1.0, report.recall("orders"), 0.0);
        assertEquals(1.0, report.recall("mentions"), 0.0);
        assertEquals(1.0, report.precision("qualifiers"), 0.0);
        assertTrue(report.forbiddenHits().isEmpty());
        assertEquals(0, report.wrongRoleClaims());
        assertTrue(report.toString(), report.toString().contains("roles"));
    }

    @Test
    public void receiptAsActorOfThePaymentIsAForbiddenClaim() {
        FakeChatCompletionPort chat = new FakeChatCompletionPort();
        chat.enqueueAnswer(StatementAnalyzerTest.ANSWER.replace(
                "\"participants\": [{\"role\": \"ACTOR\", \"mention\": \"m1\"}, {\"role\": \"AFFECTED_OBJECT\", "
                        + "\"mention\": \"m3\"}]",
                "\"participants\": [{\"role\": \"ACTOR\", \"mention\": \"m2\"}, {\"role\": \"AFFECTED_OBJECT\", "
                        + "\"mention\": \"m3\"}]"));
        StatementEvaluation.Report report = StatementEvaluation.evaluate(firstCase(), analysis(chat));

        assertEquals(Collections.singletonList("s01: auslösen ACTOR=beleg"), report.forbiddenHits());
        assertEquals(1, report.wrongRoleClaims());
        assertEquals(0.75, report.recall("roles"), 0.0);
        assertEquals(Collections.singleton("s01"), report.casesWithRoleErrors());
    }

    @Test
    public void failedAnalysisMissesEverything() {
        StatementEvaluation.Report report = StatementEvaluation.evaluate(firstCase(), gold -> {
            throw new IllegalStateException("unreachable");
        });
        assertEquals(1, report.failedCases());
        assertEquals(0.0, report.recall("roles"), 0.0);
    }

    @Test
    public void malformedSetsAreRejected() {
        for (String json : Arrays.asList("[]", "{\"cases\": [{\"id\": \"a\"}]}",
                "{\"cases\": [{\"id\": \"a\", \"sentence\": \"x.\", \"events\": [{\"predicate\": \"p\", "
                        + "\"args\": [\"WHO=x\"]}]}]}",
                "{\"cases\": [{\"id\": \"a\", \"sentence\": \"x.\", \"forbidden\": [\"ACTOR=x\"]}]}")) {
            try {
                StatementEvaluation.parse(json);
                fail(json);
            } catch (IllegalArgumentException expected) {
                // Rejected as it should be.
            }
        }
    }

    private static List<StatementEvaluation.Case> firstCase() {
        return StatementEvaluation.builtIn().subList(0, 1);
    }

    private static java.util.function.Function<StatementEvaluation.Case, DocumentStatements> analysis(
            FakeChatCompletionPort chat) {
        StatementAnalyzer analyzer = new StatementAnalyzer(SentenceSegmenter.splitter(),
                new ChatStatementExtractor(chat, () -> "test-model"), PredicateCheck.none(), null);
        return gold -> analyzer.analyze(gold.sentence(), new StatementSettings(true, false, 40, 1500));
    }
}
