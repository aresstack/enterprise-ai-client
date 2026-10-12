package com.aresstack.enterpriseai.astu.acropolis.chat.planning;

import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.LinguisticFrame;

/**
 * Plan the answer before every turn: what did the user most likely want to say and achieve, what does that need, and
 * what is the next step? Returns structured data only, never the answer. May ask a clarifying question when plausible
 * readings lead to substantially different actions; the answering call then does not start.
 */
public interface AnswerPlanningService {

    /**
     * @param linguisticFrame the local analysis of the message, or {@code null} without one
     * @return never {@code null}; {@link AnswerPlan#isKnown()} is {@code false} when no plan was obtained
     */
    AnswerPlan plan(ConversationContext conversationContext, UserMessage userMessage, LinguisticFrame linguisticFrame);
}
