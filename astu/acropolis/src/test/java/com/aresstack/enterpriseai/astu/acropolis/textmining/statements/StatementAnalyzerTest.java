package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import com.aresstack.enterpriseai.astu.acropolis.passage.SentenceSegmenter;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StatementAnalyzerTest {

    /** The example of issue #108. Words: 1 Die 2 zuständige 3 Sachbearbeitung 4 prüft 5 den 6 eingereichten 7 Beleg
     * 8 und 9 löst 10 anschließend 11 die 12 Zahlung 13 aus. */
    static final String SENTENCE = "Die zuständige Sachbearbeitung prüft den eingereichten Beleg und löst "
            + "anschließend die Zahlung aus.";

    static final String MENTIONS = "\"mentions\": ["
            + "{\"id\": \"m1\", \"sentence\": 1, \"from\": 1, \"to\": 3, \"text\": \"Die zuständige Sachbearbeitung\", "
            + "\"lemma\": \"Sachbearbeitung\", \"pronoun\": false},"
            + "{\"id\": \"m2\", \"sentence\": 1, \"from\": 5, \"to\": 7, \"text\": \"den eingereichten Beleg\", "
            + "\"lemma\": \"Beleg\", \"pronoun\": false},"
            + "{\"id\": \"m3\", \"sentence\": 1, \"from\": 11, \"to\": 12, \"text\": \"die Zahlung\", "
            + "\"lemma\": \"Zahlung\", \"pronoun\": false}]";

    static final String ANSWER = "{" + MENTIONS + ", \"events\": ["
            + "{\"id\": \"e1\", \"sentence\": 1, \"predicate\": {\"from\": 4, \"to\": 4, \"text\": \"prüft\"}, "
            + "\"particle\": null, \"lemma\": \"prüfen\", \"participants\": [{\"role\": \"ACTOR\", \"mention\": \"m1\"}, "
            + "{\"role\": \"AFFECTED_OBJECT\", \"mention\": \"m2\"}], \"negation\": null, \"modality\": null, "
            + "\"condition\": null},"
            + "{\"id\": \"e2\", \"sentence\": 1, \"predicate\": {\"from\": 9, \"to\": 9, \"text\": \"löst\"}, "
            + "\"particle\": {\"from\": 13, \"to\": 13, \"text\": \"aus\"}, \"lemma\": \"auslösen\", "
            + "\"participants\": [{\"role\": \"ACTOR\", \"mention\": \"m1\"}, {\"role\": \"AFFECTED_OBJECT\", "
            + "\"mention\": \"m3\"}], \"negation\": null, \"modality\": null, \"condition\": null}],"
            + "\"relations\": ["
            + "{\"id\": \"r1\", \"sentence\": 1, \"kind\": \"ATTRIBUTE\", \"subject\": \"m1\", "
            + "\"value\": {\"from\": 2, \"to\": 2, \"text\": \"zuständige\"}, \"lemma\": \"zuständig\"},"
            + "{\"id\": \"r2\", \"sentence\": 1, \"kind\": \"STATE\", \"subject\": \"m2\", "
            + "\"value\": {\"from\": 6, \"to\": 6, \"text\": \"eingereichten\"}, \"lemma\": \"eingereicht\"}],"
            + "\"order\": [{\"before\": \"e1\", \"after\": \"e2\", \"sentence\": 1, "
            + "\"cue\": {\"from\": 10, \"to\": 10, \"text\": \"anschließend\"}}]}";

    private final FakeChatCompletionPort chat = new FakeChatCompletionPort();

    @Test
    public void exampleSentenceYieldsTwoEventsWithTheSameActor() {
        chat.enqueueAnswer("Hier ist das Ergebnis:\n```json\n" + ANSWER + "\n```");
        DocumentStatements statements = analyzer(false).analyze(SENTENCE, settings(false));

        assertEquals(3, statements.mentions().size());
        assertEquals(2, statements.events().size());
        Event check = statements.events().get(0);
        Event release = statements.events().get(1);
        assertEquals("prüfen", check.normalized());
        assertEquals("auslösen", release.normalized());
        assertEquals("löst … aus", release.predicateText());
        assertEquals("sachbearbeitung", only(check.participants(ParticipantRole.ACTOR)).normalized());
        assertEquals("beleg", only(check.participants(ParticipantRole.AFFECTED_OBJECT)).normalized());
        // The shared subject is the same occurrence in both events; the receipt is never an actor of the payment.
        assertTrue(only(check.participants(ParticipantRole.ACTOR))
                == only(release.participants(ParticipantRole.ACTOR)));
        assertEquals("zahlung", only(release.participants(ParticipantRole.AFFECTED_OBJECT)).normalized());
        for (Participant participant : release.participants()) {
            assertFalse(participant.mention().normalized().equals("beleg"));
        }
        assertEquals("Die zuständige Sachbearbeitung", only(check.participants(ParticipantRole.ACTOR))
                .surface().text());
        assertEquals(ExtractionStatus.EXTRACTED, check.status());

        assertEquals(3, statements.relations().size());
        Relation order = statements.relations().get(2);
        assertEquals(RelationKind.BEFORE, order.kind());
        assertTrue(order.subjectEvent() == check && order.objectEvent() == release);
        assertEquals("anschließend", order.literal().text());
        assertEquals("eingereicht", statements.relations().get(1).normalized());
        assertTrue(statements.rejections().isEmpty());
        assertEquals(1, chat.requests().size());
    }

    @Test
    public void wordsNotInTheSentenceAreDiscardedWithEverythingBuiltOnThem() {
        String invented = ANSWER.replace("\"text\": \"die Zahlung\"", "\"text\": \"die Rechnung\"");
        chat.enqueueAnswer(invented);
        DocumentStatements statements = analyzer(false).analyze(SENTENCE, settings(false));

        assertEquals(2, statements.mentions().size());
        assertEquals(1, statements.events().size());
        assertEquals("prüfen", statements.events().get(0).normalized());
        assertEquals(Integer.valueOf(1), statements.rejections().get("words-not-in-sentence"));
        assertEquals(Integer.valueOf(1), statements.rejections().get("unknown-mention"));
        // The order needs the dropped event.
        assertEquals(Integer.valueOf(1), statements.rejections().get("unknown-event"));
    }

    @Test
    public void wrongWordNumbersAreRepairedByTheUniqueText() {
        chat.enqueueAnswer(ANSWER.replace("\"from\": 11, \"to\": 12", "\"from\": 10, \"to\": 11"));
        DocumentStatements statements = analyzer(false).analyze(SENTENCE, settings(false));

        assertEquals(3, statements.mentions().size());
        assertEquals("die Zahlung", statements.mentions().get(2).surface().text());
    }

    @Test
    public void deniedEventIsDroppedTogetherWithItsOrder() {
        chat.enqueueAnswer(ANSWER);
        chat.enqueueAnswer("JA");     // e1
        chat.enqueueAnswer("NEIN");   // e2
        chat.enqueueAnswer("JA");     // r1
        chat.enqueueAnswer("UNSICHER, der Satz ist mehrdeutig"); // r2
        DocumentStatements statements = analyzer(true).analyze(SENTENCE, settings(true));

        assertEquals(1, statements.events().size());
        assertEquals(ExtractionStatus.MODEL_VALIDATED, statements.events().get(0).status());
        assertEquals(2, statements.relations().size());
        assertEquals(ExtractionStatus.MODEL_VALIDATED, statements.relations().get(0).status());
        assertEquals(ExtractionStatus.UNCERTAIN, statements.relations().get(1).status());
        assertEquals(Integer.valueOf(1), statements.rejections().get("check-denied"));
        assertEquals(Integer.valueOf(1), statements.rejections().get("order-without-event"));
        assertEquals(5, chat.requests().size());
        String claim = chat.requests().get(2).messages().get(1).content();
        assertTrue(claim, claim.contains("Handlung: auslösen.") && claim.contains("Wer handelt: Die zuständige"));
    }

    @Test
    public void pronounsStayUnresolvedAndAreNotChecked() {
        String text = SENTENCE + " Sie informiert danach den Antragsteller.";
        // Second sentence: 1 Sie 2 informiert 3 danach 4 den 5 Antragsteller
        chat.enqueueAnswer("{\"mentions\": [{\"id\": \"m1\", \"sentence\": 2, \"from\": 1, \"to\": 1, \"text\": "
                + "\"Sie\", \"pronoun\": true}, {\"id\": \"m2\", \"sentence\": 2, \"from\": 4, \"to\": 5, \"text\": "
                + "\"den Antragsteller\", \"lemma\": \"Antragsteller\"}], \"events\": [{\"id\": \"e1\", "
                + "\"sentence\": 2, \"predicate\": {\"from\": 2, \"to\": 2, \"text\": \"informiert\"}, \"lemma\": "
                + "\"informieren\", \"participants\": [{\"role\": \"ACTOR\", \"mention\": \"m1\"}, {\"role\": "
                + "\"RECIPIENT\", \"mention\": \"m2\"}]}]}");
        DocumentStatements statements = analyzer(true).analyze(text, settings(true));

        assertEquals(ExtractionStatus.UNRESOLVED_REFERENCE, statements.mentions().get(0).status());
        assertNull(statements.mentions().get(0).lemmaCandidate());
        assertEquals(ExtractionStatus.UNRESOLVED_REFERENCE, statements.events().get(0).status());
        assertEquals(1, chat.requests().size());
    }

    @Test
    public void participantFromAnotherSentenceRejectsTheEvent() {
        String text = SENTENCE + " Danach wird informiert.";
        chat.enqueueAnswer("{" + MENTIONS + ", \"events\": [{\"id\": \"e1\", \"sentence\": 2, \"predicate\": "
                + "{\"from\": 3, \"to\": 3, \"text\": \"informiert\"}, \"lemma\": \"informieren\", \"participants\": "
                + "[{\"role\": \"ACTOR\", \"mention\": \"m1\"}]}]}");
        DocumentStatements statements = analyzer(false).analyze(text, settings(false));

        assertTrue(statements.events().isEmpty());
        assertEquals(Integer.valueOf(1), statements.rejections().get("other-sentence"));
    }

    @Test
    public void negationMustBeGroundedAndIsKept() {
        String text = "Die Sachbearbeitung löst die Zahlung nicht aus.";
        // 1 Die 2 Sachbearbeitung 3 löst 4 die 5 Zahlung 6 nicht 7 aus
        String answer = "{\"mentions\": [{\"id\": \"m1\", \"sentence\": 1, \"from\": 1, \"to\": 2, \"text\": "
                + "\"Die Sachbearbeitung\", \"lemma\": \"Sachbearbeitung\"}, {\"id\": \"m2\", \"sentence\": 1, "
                + "\"from\": 4, \"to\": 5, \"text\": \"die Zahlung\", \"lemma\": \"Zahlung\"}], \"events\": [{\"id\": "
                + "\"e1\", \"sentence\": 1, \"predicate\": {\"from\": 3, \"to\": 3, \"text\": \"löst\"}, \"particle\": "
                + "{\"from\": 7, \"to\": 7, \"text\": \"aus\"}, \"lemma\": \"auslösen\", \"participants\": [{\"role\": "
                + "\"ACTOR\", \"mention\": \"m1\"}, {\"role\": \"AFFECTED_OBJECT\", \"mention\": \"m2\"}], "
                + "\"negation\": {\"from\": 6, \"to\": 6, \"text\": \"NEGATION\"}}]}";
        chat.enqueueAnswer(answer.replace("NEGATION", "nicht"));
        Event event = analyzer(false).analyze(text, settings(false)).events().get(0);
        assertEquals("nicht", event.context().negation().text());

        chat.enqueueAnswer(answer.replace("NEGATION", "niemals"));
        DocumentStatements invented = analyzer(false).analyze(text, settings(false));
        assertTrue(invented.events().isEmpty());
    }

    @Test
    public void equalTermsStayTwoMentions() {
        String text = "Die Stelle prüft die Zahlung und bucht die Zahlung.";
        // 1 Die 2 Stelle 3 prüft 4 die 5 Zahlung 6 und 7 bucht 8 die 9 Zahlung
        chat.enqueueAnswer("{\"mentions\": [{\"id\": \"m1\", \"sentence\": 1, \"from\": 4, \"to\": 5, \"text\": "
                + "\"die Zahlung\", \"lemma\": \"Zahlung\"}, {\"id\": \"m2\", \"sentence\": 1, \"from\": 8, \"to\": 9, "
                + "\"text\": \"die Zahlung\", \"lemma\": \"Zahlung\"}]}");
        DocumentStatements statements = analyzer(false).analyze(text, settings(false));

        assertEquals(2, statements.mentions().size());
        assertFalse(statements.mentions().get(0).surface().span()
                .equals(statements.mentions().get(1).surface().span()));
    }

    @Test
    public void unreadableAnswerIsRepairedOnce() {
        chat.enqueueAnswer("Ich habe den Satz analysiert.");
        chat.enqueueAnswer(ANSWER);
        DocumentStatements statements = analyzer(false).analyze(SENTENCE, settings(false));

        assertEquals(2, statements.events().size());
        assertEquals(2, chat.requests().size());
        assertEquals(4, chat.requests().get(1).messages().size());
    }

    @Test
    public void unreadableAfterRepairCountsAsFailedUnit() {
        chat.enqueueAnswer("kein JSON");
        chat.enqueueAnswer("{\"weder\": 1}");
        DocumentStatements statements = analyzer(false).analyze(SENTENCE, settings(false));

        assertEquals(1, statements.failedUnits());
        assertEquals(0, statements.analysedUnits());
    }

    @Test
    public void parseCountsMalformedEntries() {
        StatementCandidates candidates = ChatStatementExtractor.parse("{\"mentions\": [{\"id\": \"m1\"}, 7], "
                + "\"events\": []}");
        assertEquals(2, candidates.malformed());
        try {
            ChatStatementExtractor.parse("[]");
            fail();
        } catch (StatementExtractionException expected) {
            assertFalse(expected.getMessage().contains("[]"));
        }
    }

    @Test
    public void verdictParsing() {
        assertEquals(StatementVerifier.Verdict.YES, ChatStatementVerifier.parse(" Ja."));
        assertEquals(StatementVerifier.Verdict.NO, ChatStatementVerifier.parse("**NEIN**"));
        assertEquals(StatementVerifier.Verdict.UNSURE, ChatStatementVerifier.parse("Vielleicht"));
        assertEquals(StatementVerifier.Verdict.UNSURE, ChatStatementVerifier.parse(null));
    }

    @Test
    public void codeAndTablesAreNoParagraphs() {
        String text = "# Überschrift\n\n| a | b |\n|---|---|\n\n```\nDie Klasse ruft die Methode auf.\n```\n\n"
                + SENTENCE + "\n\nNUR GROSSBUCHSTABEN IN DIESER ZEILE HIER";
        List<ParagraphText> units = ParagraphText.split(text, SentenceSegmenter.splitter(), 1500);
        assertEquals(1, units.size());
        assertEquals(SENTENCE, units.get(0).text());
        assertEquals(text.indexOf(SENTENCE), units.get(0).span().start());
    }

    @Test
    public void versionChangesWithModelAndLimits() {
        StatementAnalyzer analyzer = analyzer(true);
        String version = analyzer.version(settings(true));
        assertTrue(version, version.contains("chat-json/1/test-model") && version.contains("chat-yes-no/1/test-model"));
        assertFalse(version.equals(analyzer.version(new StatementSettings(true, true, 10, 1500))));
        assertFalse(version.equals(analyzer.version(settings(false))));
    }

    private StatementAnalyzer analyzer(boolean verify) {
        ChatStatementExtractor extractor = new ChatStatementExtractor(chat, () -> "test-model");
        ChatStatementVerifier verifier = new ChatStatementVerifier(chat, () -> "test-model", () -> verify);
        return new StatementAnalyzer(SentenceSegmenter.splitter(), extractor, PredicateCheck.none(), verifier);
    }

    private static StatementSettings settings(boolean verify) {
        return new StatementSettings(true, verify, StatementSettings.DEFAULT_MAX_PARAGRAPHS,
                StatementSettings.DEFAULT_MAX_PARAGRAPH_CHARS);
    }

    private static Mention only(List<Mention> mentions) {
        assertEquals(new ArrayList<Mention>(mentions).toString(), 1, mentions.size());
        return mentions.get(0);
    }
}
