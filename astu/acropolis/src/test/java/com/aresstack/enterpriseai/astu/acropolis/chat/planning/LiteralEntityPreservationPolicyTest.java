package com.aresstack.enterpriseai.astu.acropolis.chat.planning;

import com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.LinguisticFrame;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class LiteralEntityPreservationPolicyTest {

    private final LiteralEntityPreservationPolicy policy = new LiteralEntityPreservationPolicy();
    private final LinguisticFrame qzx = LinguisticFrame.builder(MessageOrigin.SPEECH_TRANSCRIPT)
            .acronymCandidate("QZX").build();

    /** The model finds a better-known abbreviation more plausible: that becomes a hypothesis, the original is resolved. */
    @Test
    public void aRewrittenAbbreviationIsSearchedAsSpoken() {
        AnswerPlan plan = plan("QZX").interpretedText("Was ist eine API?").target("API", TargetKind.ACRONYM)
                .informationNeed(InformationNeed.GENERAL_KNOWLEDGE).nextAction(NextAction.ANSWER_DIRECTLY)
                .hint(new InterpretationHint("QZX", "API", "bekannter")).utteranceConfidence(0.4).build();

        AnswerPlan kept = policy.apply(plan, qzx);

        assertEquals(NextAction.RESOLVE_INTERNAL_TERM, kept.nextAction());
        assertTrue(kept.resolvesTerm());
        assertEquals("QZX", kept.resolvedTerm());
        assertEquals("QZX", kept.knowledgeQuery());
        assertEquals("QZX", kept.interpretedText());
        assertEquals("QZX", kept.literalTarget());
        assertTrue("the rewrite is no correction any more", kept.hints().isEmpty());
        assertEquals(Collections.singletonList("API"), kept.alternativeInterpretations());
        assertEquals(Collections.singletonList("QZX"), kept.preservedTerms());
    }

    /** An unknown abbreviation is a reason to search, not to ask back. */
    @Test
    public void anAbbreviationIsSearchedInsteadOfAskingBack() {
        AnswerPlan plan = plan("Was ist QZX").interpretedText("Was ist QZX?")
                .informationNeed(InformationNeed.TERM_RESOLUTION).nextAction(NextAction.ASK_CLARIFICATION)
                .clarificationQuestion("Was meinst du?").build();

        AnswerPlan kept = policy.apply(plan, qzx);

        assertEquals(NextAction.RESOLVE_INTERNAL_TERM, kept.nextAction());
        assertEquals("", kept.clarificationQuestion());
        assertEquals("QZX", kept.knowledgeQuery());
        assertEquals("Was ist QZX?", kept.interpretedText());
    }

    /** A term whose meaning is needed is looked up, even when the plan would answer directly. */
    @Test
    public void aTermThatNeedsItsMeaningIsSearched() {
        AnswerPlan plan = plan("Was heißt QZX?").informationNeed(InformationNeed.TERM_RESOLUTION)
                .nextAction(NextAction.ANSWER_DIRECTLY).utteranceConfidence(0.9).build();

        assertEquals(NextAction.RESOLVE_INTERNAL_TERM, policy.apply(plan, qzx).nextAction());
    }

    /** A planned query that lost the original term gets it back in front. */
    @Test
    public void aPlannedQueryKeepsTheOriginalTermFirst() {
        AnswerPlan plan = plan("Wofür steht QZX bei der Abrechnung?")
                .informationNeed(InformationNeed.INTERNAL_KNOWLEDGE).nextAction(NextAction.SEARCH_INTERNAL_KNOWLEDGE)
                .knowledgeQuery("Abrechnung Schnittstelle").utteranceConfidence(0.9).build();

        assertEquals("QZX Abrechnung Schnittstelle", policy.apply(plan, qzx).knowledgeQuery());
    }

    @Test
    public void aPlannedQueryWithTheTermStaysAsPlanned() {
        AnswerPlan plan = plan("Wofür steht QZX?").informationNeed(InformationNeed.TERM_RESOLUTION)
                .nextAction(NextAction.SEARCH_INTERNAL_KNOWLEDGE).knowledgeQuery("QZX Bedeutung").build();

        assertSame(plan, policy.apply(plan, qzx));
    }

    /** A statement about a term needs no information: nothing is searched. */
    @Test
    public void aStatementStaysAsPlanned() {
        AnswerPlan plan = plan("QZX ist ein super Tool.").informationNeed(InformationNeed.NO_INFORMATION_NEEDED)
                .nextAction(NextAction.ANSWER_DIRECTLY).build();

        assertSame(plan, policy.apply(plan, qzx));
    }

    @Test
    public void aQuestionAboutThisApplicationStaysAsPlanned() {
        AnswerPlan plan = plan("Wo stelle ich QZX ein?").interpretedText("Wo stelle ich die Adresse ein?")
                .informationNeed(InformationNeed.APPLICATION_HELP).nextAction(NextAction.USE_APPLICATION_HELP)
                .build();

        assertSame(plan, policy.apply(plan, qzx));
    }

    @Test
    public void withoutAnAbbreviationInTheOriginalNothingChanges() {
        AnswerPlan plan = plan("Was ist eine API?").interpretedText("Was ist eine Schnittstelle?")
                .informationNeed(InformationNeed.GENERAL_KNOWLEDGE).nextAction(NextAction.ANSWER_DIRECTLY).build();

        assertSame(plan, policy.apply(plan, LinguisticFrame.builder(MessageOrigin.TYPED).build()));
        assertSame(plan, policy.apply(plan, null));
    }

    /** A dictation may write an abbreviation in lower case: the conversation already used it, so it is kept as said. */
    @Test
    public void anAbbreviationTheConversationAlreadyUsedCountsInLowerCase() {
        ConversationContext conversation = ConversationContext.of(Arrays.asList(ChatMessage.user("Was ist QZX?"),
                ChatMessage.assistant("Zu QZX gibt es zwei Seiten.")));
        AnswerPlan plan = plan("und was macht qzx genau").interpretedText("Und was macht eine API genau?")
                .informationNeed(InformationNeed.GENERAL_KNOWLEDGE).nextAction(NextAction.ANSWER_DIRECTLY)
                .hint(new InterpretationHint("qzx", "API", "bekannter")).build();

        AnswerPlan kept = policy.apply(plan, LinguisticFrame.builder(MessageOrigin.SPEECH_TRANSCRIPT).build(),
                conversation);

        assertEquals(NextAction.RESOLVE_INTERNAL_TERM, kept.nextAction());
        assertEquals("QZX", kept.resolvedTerm());
        assertEquals(Collections.singletonList("API"), kept.alternativeInterpretations());
    }

    /** The plan's literal target with the shape of an abbreviation counts like a candidate of the analysis. */
    @Test
    public void theLiteralTargetOfThePlanIsKept() {
        AnswerPlan plan = plan("Was ist KLV").interpretedText("Was ist KLV?").target("KLV", TargetKind.ACRONYM)
                .literalTarget("KLV").informationNeed(InformationNeed.TERM_RESOLUTION)
                .nextAction(NextAction.ASK_CLARIFICATION).clarificationQuestion("Welches KLV?").build();

        AnswerPlan kept = policy.apply(plan, null);

        assertEquals(NextAction.RESOLVE_INTERNAL_TERM, kept.nextAction());
        assertEquals("KLV", kept.resolvedTerm());
    }

    @Test
    public void theQuestionWithoutAHitNamesTheAlternativesAsQuestions() {
        AnswerPlan plan = plan("QZX").nextAction(NextAction.RESOLVE_INTERNAL_TERM).literalTarget("QZX")
                .alternativeInterpretation("API").alternativeInterpretation("KLV").build();

        assertEquals("Ich habe in den internen Quellen nichts zu „QZX“ gefunden. Meintest du vielleicht „API“ oder "
                + "„KLV“, oder steht „QZX“ für etwas anderes?", UnresolvedTermQuestion.of(plan));
        assertEquals("Ich habe in den internen Quellen nichts zu „QZX“ gefunden. Was ist mit „QZX“ gemeint?",
                UnresolvedTermQuestion.of(plan("QZX").nextAction(NextAction.RESOLVE_INTERNAL_TERM)
                        .literalTarget("QZX").build()));
    }

    @Test
    public void containsTermMatchesWholeWordsOnly() {
        assertTrue(LiteralEntityPreservationPolicy.containsTerm("Was ist qzx?", "QZX"));
        assertEquals(false, LiteralEntityPreservationPolicy.containsTerm("QZXA Bedeutung", "QZX"));
    }

    private static AnswerPlan.Builder plan(String original) {
        return AnswerPlan.builder(original, MessageOrigin.SPEECH_TRANSCRIPT).known(true);
    }
}
