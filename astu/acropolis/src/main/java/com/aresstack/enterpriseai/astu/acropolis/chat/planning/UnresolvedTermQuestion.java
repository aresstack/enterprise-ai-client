package com.aresstack.enterpriseai.astu.acropolis.chat.planning;

import java.util.List;

/**
 * The question that ends a turn whose term the internal search did not resolve: it says that nothing was found for the
 * term as said and names the other readings of the plan as questions, never as the answer.
 */
public final class UnresolvedTermQuestion {

    private UnresolvedTermQuestion() {
    }

    public static String of(AnswerPlan plan) {
        String term = plan.resolvedTerm();
        StringBuilder question = new StringBuilder("Ich habe in den internen Quellen nichts zu „").append(term)
                .append("“ gefunden.");
        List<String> alternatives = plan.alternativeInterpretations();
        if (alternatives.isEmpty()) {
            return question.append(" Was ist mit „").append(term).append("“ gemeint?").toString();
        }
        question.append(" Meintest du vielleicht ");
        for (int i = 0; i < alternatives.size(); i++) {
            if (i > 0) {
                question.append(i == alternatives.size() - 1 ? " oder " : ", ");
            }
            question.append('„').append(alternatives.get(i)).append('“');
        }
        return question.append(", oder steht „").append(term).append("“ für etwas anderes?").toString();
    }
}
