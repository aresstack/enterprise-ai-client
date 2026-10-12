package com.aresstack.enterpriseai.astu.acropolis.chat.linguistics;

import com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin;

/**
 * Describe the form of the current user message as a {@link LinguisticFrame}: source, language, sentence type,
 * tagged tokens, structural hints and diagnostics. Describes only; whether to answer directly, consult the help or
 * search is decided by the chat model, which receives the frame as a hint.
 */
public interface LinguisticAnalyzer {

    /** Analyze with the installed language models; may load them and therefore block briefly. */
    LinguisticFrame analyze(String text, MessageOrigin origin);

    /** Analyze from the spelling alone, without loading any model; safe on the UI thread. */
    LinguisticFrame analyzeWithoutModels(String text, MessageOrigin origin);

    /** No analysis: a frame that only knows the source. */
    static LinguisticAnalyzer none() {
        return new LinguisticAnalyzer() {
            @Override
            public LinguisticFrame analyze(String text, MessageOrigin origin) {
                return LinguisticFrame.builder(origin).build();
            }

            @Override
            public LinguisticFrame analyzeWithoutModels(String text, MessageOrigin origin) {
                return analyze(text, origin);
            }
        };
    }
}
