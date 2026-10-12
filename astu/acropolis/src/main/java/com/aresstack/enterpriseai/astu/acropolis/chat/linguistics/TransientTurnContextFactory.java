package com.aresstack.enterpriseai.astu.acropolis.chat.linguistics;

import com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Render a {@link LinguisticFrame} as the block the chat model reads with the current turn only, as a system part
 * next to the stored conversation: {@code <client-turn-analysis>}, marked as written by the client, compact, and
 * never part of the user's message, the history, the transcript or an export.
 */
public final class TransientTurnContextFactory {

    static final String OPEN = "<client-turn-analysis>";
    static final String CLOSE = "</client-turn-analysis>";

    /** Messages with more tokens are listed without their tags; the analysis would cost more than it says. */
    static final int MAX_LISTED_TOKENS = 40;

    /** The block for this turn. */
    public String create(LinguisticFrame frame) {
        StringBuilder block = new StringBuilder(OPEN).append('\n');
        block.append("Vom Client automatisch erzeugte Analyse der aktuellen Nutzernachricht, nicht vom Nutzer "
                + "geschrieben. Hinweise, keine Wahrheit.\n");
        block.append("source: ").append(frame.source() == MessageOrigin.SPEECH_TRANSCRIPT ? "SPEECH" : "TEXT")
                .append('\n');
        if (frame.source().modelNote() != null) {
            block.append("source-note: ").append(frame.source().modelNote()).append('\n');
        }
        if (!frame.language().isEmpty()) {
            block.append("language: ").append(frame.language()).append('\n');
        }
        if (frame.sentenceCount() > 0) {
            block.append("sentences: ").append(frame.sentenceCount()).append('\n');
        }
        block.append("sentence-type-hint: ").append(frame.sentenceType()).append('\n');
        if (frame.isTagged()) {
            if (frame.tokens().size() <= MAX_LISTED_TOKENS) {
                block.append("tokens: ").append(tokens(frame.tokens())).append('\n');
            }
            block.append("syntax: interrogative=").append(frame.hasInterrogative())
                    .append(", verb=").append(frame.hasVerb())
                    .append(", finite-verb=").append(frame.hasFiniteVerb())
                    .append(", imperative-possible=").append(frame.isImperativePossible()).append('\n');
        }
        list(block, "acronym-candidates", frame.acronymCandidates());
        list(block, "uncertain-tokens", frame.uncertainTokens());
        list(block, "suspected-tokens", frame.suspectedTokens());
        list(block, "diagnostics", frame.diagnostics());
        if (frame.diagnostics().contains(Diagnostic.POSSIBLE_ASR_ERROR)) {
            block.append("note: Die Spracheingabe scheint syntaktisch fehlerhaft zu sein. Ein verdächtiges Token steht "
                    + "ungefähr dort, wo ein Prädikat erwartet würde, oder wurde unsicher erkannt. Prüfe zuerst eine "
                    + "plausible Fehlerkennung, bevor du es als Eigennamen behandelst oder danach recherchierst.\n");
        }
        return block.append(CLOSE).toString();
    }

    private static String tokens(List<LinguisticToken> tokens) {
        StringBuilder text = new StringBuilder();
        for (LinguisticToken token : tokens) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(token.text()).append('/').append(token.tag());
            if (token.isUncertain()) {
                text.append(String.format(Locale.ROOT, "(%.2f)", token.probability()));
            }
        }
        return text.toString();
    }

    private static void list(StringBuilder block, String name, Collection<?> values) {
        if (values.isEmpty()) {
            return;
        }
        block.append(name).append(": ");
        boolean first = true;
        for (Object value : values) {
            if (!first) {
                block.append(", ");
            }
            block.append(value);
            first = false;
        }
        block.append('\n');
    }
}
