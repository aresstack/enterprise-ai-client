package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import com.aresstack.enterpriseai.astu.acropolis.textmining.TextSpan;

import java.util.List;

/**
 * Whether the words claimed as an event's predicate are a verb in their sentence (issue #108). Backed by a
 * part-of-speech model when one is installed; without it the answer is unknown, never a guess.
 */
public interface PredicateCheck {

    /**
     * @param predicate the verb span first, then a separated particle if any
     * @return {@code TRUE}/{@code FALSE}, or {@code null} when no model can tell
     */
    Boolean isVerbal(SentenceText sentence, List<TextSpan> predicate);

    /** Identifies the method in the extractor version ({@code pos-de} or {@code none}). */
    String id();

    /** No part-of-speech model: every answer is unknown. */
    static PredicateCheck none() {
        return new PredicateCheck() {
            @Override
            public Boolean isVerbal(SentenceText sentence, List<TextSpan> predicate) {
                return null;
            }

            @Override
            public String id() {
                return "none";
            }
        };
    }
}
