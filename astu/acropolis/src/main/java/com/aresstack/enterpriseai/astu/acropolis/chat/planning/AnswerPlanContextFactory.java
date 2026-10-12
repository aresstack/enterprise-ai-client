package com.aresstack.enterpriseai.astu.acropolis.chat.planning;

import com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin;

import java.util.Locale;

/**
 * Render an {@link AnswerPlan} for the answering call as {@code <client-answer-plan>}: the original
 * message and the interpretation side by side, never the interpretation in place of the message. Sent with the current
 * turn only; never stored, shown or exported.
 */
public final class AnswerPlanContextFactory {

    static final String OPEN = "<client-answer-plan>";
    static final String CLOSE = "</client-answer-plan>";

    /** The block for this turn, with the guidance the confidence calls for under {@code policy}. */
    public String create(AnswerPlan turn, AnswerPlanningPolicy policy) {
        StringBuilder block = new StringBuilder(OPEN).append('\n');
        block.append("Vom Client vorab geplant, nicht vom Nutzer geschrieben. Die Nachricht des Nutzers ist das "
                + "Original; die Interpretation ist eine Lesart, der nächste Schritt eine Empfehlung. Entscheide selbst "
                + "über die beste Aktion: direkt antworten, Programmhilfe, Wissenssuche oder nachfragen.\n");
        block.append("original-user-message: ").append(oneLine(turn.originalText())).append('\n');
        block.append("source: ").append(turn.source() == MessageOrigin.SPEECH_TRANSCRIPT ? "SPEECH" : "TEXT")
                .append('\n');
        if (turn.isReworded()) {
            block.append("likely-intended-meaning: ").append(oneLine(turn.interpretedText())).append('\n');
        }
        block.append("likely-asr-error: ").append(turn.isLikelySpeechRecognitionError() ? "yes" : "no").append('\n');
        for (InterpretationHint hint : turn.hints()) {
            block.append("correction: ").append(hint).append('\n');
        }
        if (turn.referencesPreviousAnswer()) {
            block.append("refers-to-previous-answer: yes\n");
        }
        block.append("intent: ").append(turn.intent()).append('\n');
        if (!turn.target().isEmpty()) {
            block.append("target: ").append(oneLine(turn.target())).append(" (").append(turn.targetKind())
                    .append(")\n");
        }
        block.append("information-need: ").append(turn.informationNeed()).append('\n');
        if (turn.isOrganizationSpecificMeaningPossible()) {
            block.append("organization-specific-meaning-possible: yes\n");
        }
        block.append("ambiguity: ").append(turn.ambiguity()).append('\n');
        if (turn.nextAction() != NextAction.UNKNOWN) {
            block.append("planned-next-action: ").append(turn.nextAction()).append('\n');
        }
        block.append("confidence: ").append(String.format(Locale.ROOT, "%.2f", turn.confidence())).append('\n');
        block.append("guidance: ").append(guidance(turn, policy)).append('\n');
        return block.append(CLOSE).toString();
    }

    static String guidance(AnswerPlan turn, AnswerPlanningPolicy policy) {
        AnswerPlanningPolicy limits = policy == null ? AnswerPlanningPolicy.defaults() : policy;
        if (turn.confidence() >= limits.useAtLeast()) {
            return turn.isReworded()
                    ? "Verwende die Interpretation für die Bearbeitung. Weicht sie vom Original ab, sag kurz, wie du "
                    + "die Nachricht verstanden hast, damit der Nutzer widersprechen kann."
                    : "Verwende die Interpretation für die Bearbeitung.";
        }
        if (turn.confidence() >= limits.askBelow()) {
            return "Nutze die Interpretation nur als Hinweis und nenne kurz deine Lesart. Führe auf ihrer Grundlage "
                    + "keine riskante oder nicht umkehrbare Aktion aus.";
        }
        return "Die Nachricht ist unsicher verstanden. Frag kurz und konkret nach, was gemeint war, statt eine Lesart "
                + "als Tatsache anzunehmen.";
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
