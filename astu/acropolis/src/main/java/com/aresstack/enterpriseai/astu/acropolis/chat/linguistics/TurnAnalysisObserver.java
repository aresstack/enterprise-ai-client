package com.aresstack.enterpriseai.astu.acropolis.chat.linguistics;

import com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.AnswerPlan;

/** Learns what the model was told about a message, for a diagnostic log outside the core. */
public interface TurnAnalysisObserver {

    /**
     * @param block the transient block sent with this turn only
     */
    void analyzed(String userText, MessageOrigin origin, LinguisticFrame frame, String block);

    /**
     * The answer planning step read the message as {@code turn}; {@code block} is what the answering call receives, or
     * {@code null} when there was no reading.
     */
    default void planned(String userText, AnswerPlan turn, String block) {
    }

    static TurnAnalysisObserver none() {
        return new TurnAnalysisObserver() {
            @Override
            public void analyzed(String userText, MessageOrigin origin, LinguisticFrame frame, String block) {
            }
        };
    }
}
