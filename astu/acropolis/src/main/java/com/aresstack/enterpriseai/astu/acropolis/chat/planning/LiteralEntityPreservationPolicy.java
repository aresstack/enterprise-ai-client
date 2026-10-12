package com.aresstack.enterpriseai.astu.acropolis.chat.planning;

import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.LinguisticFrame;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.TermShape;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Keep abbreviations and identifiers of the original message literally until the organization's sources had their
 * say, whatever the plan read into them. The model may find a better-known abbreviation more plausible and rewrite the
 * one the user said; the client turns that rewrite into an alternative reading (a hypothesis, never searched) and
 * resolves the term as said first ({@link NextAction#RESOLVE_INTERNAL_TERM}).
 *
 * <p>A term counts as said literally when the local analysis found it in the original message, when the plan names it
 * as its literal target or target (said in the message) with the shape of an abbreviation or identifier, or when the message holds a word that the
 * conversation already used as an abbreviation or identifier (a dictation may write it in lower case).
 *
 * <p>The rule applies when the plan
 * <ul>
 *   <li>answers a term the user asked about before and the latest answer did not take up (a correction),</li>
 *   <li>rewrote such a term (it is missing from the interpreted message or was corrected away),</li>
 *   <li>needs the meaning of a term, organizational knowledge, or knowledge about a term that may mean something of its
 *   own in the organization, but plans no search,</li>
 *   <li>asks back instead of looking the term up (an unknown term is a reason to search, not to ask), or</li>
 *   <li>plans the internal search with a query that lost the term (then the term goes in front of the query).</li>
 * </ul>
 * A question about this application and a message that needs no information stay as planned.
 */
public final class LiteralEntityPreservationPolicy {

    /** As {@link #apply(AnswerPlan, LinguisticFrame, ConversationContext)} without conversation evidence. */
    public AnswerPlan apply(AnswerPlan plan, LinguisticFrame frame) {
        return apply(plan, frame, null);
    }

    /** The plan with the literal terms kept, or {@code plan} unchanged when the rule does not apply. */
    public AnswerPlan apply(AnswerPlan plan, LinguisticFrame frame, ConversationContext conversation) {
        if (plan == null || !plan.isKnown() || plan.nextAction() == NextAction.USE_APPLICATION_HELP) {
            return plan;
        }
        List<String> terms = literalTerms(plan, frame, conversation);
        if (terms.isEmpty()) {
            return plan;
        }
        boolean rewritten = false;
        for (String term : terms) {
            rewritten |= !containsTerm(plan.interpretedText(), term) || correctedAway(plan, term);
        }
        // An abbreviation in the planned query that the message never said stands in for the one it did say.
        List<String> substitutes = plan.nextAction() == NextAction.SEARCH_INTERNAL_KNOWLEDGE
                ? substitutes(plan.knowledgeQuery(), plan, terms) : new ArrayList<String>();
        rewritten |= !substitutes.isEmpty();
        if (rewritten) {
            for (String substitute : substitutes(plan.interpretedText(), plan, terms)) {
                add(substitutes, substitute);
            }
        }
        NextAction next = plan.nextAction();
        InformationNeed need = plan.informationNeed();
        boolean stillOpen = stillOpen(terms, conversation);
        if (need == InformationNeed.NO_INFORMATION_NEEDED && !rewritten && !stillOpen) {
            return plan;
        }
        if (next == NextAction.SEARCH_INTERNAL_KNOWLEDGE && !rewritten) {
            boolean queryLostTerm = false;
            for (String term : terms) {
                queryLostTerm |= !containsTerm(plan.knowledgeQuery(), term);
            }
            if (!queryLostTerm) {
                return plan;
            }
            AnswerPlan.Builder kept = plan.toBuilder().knowledgeQuery(query(terms, plan.knowledgeQuery()));
            for (String term : terms) {
                kept.preservedTerm(term);
            }
            return kept.build();
        }
        boolean needsMeaning = need == InformationNeed.TERM_RESOLUTION || need == InformationNeed.INTERNAL_KNOWLEDGE
                || (need != InformationNeed.NO_INFORMATION_NEEDED && plan.isOrganizationSpecificMeaningPossible());
        if (!(rewritten || needsMeaning || stillOpen || next == NextAction.ASK_CLARIFICATION
                || next == NextAction.RESOLVE_INTERNAL_TERM)) {
            return plan;
        }
        AnswerPlan.Builder kept = plan.toBuilder()
                .nextAction(NextAction.RESOLVE_INTERNAL_TERM)
                .clarificationQuestion("")
                .knowledgeQuery(query(terms, ""));
        if (rewritten) {
            // The rewrite becomes a hypothesis: the message stays as said, the other reading is only named.
            kept.interpretedText(plan.originalText()).withoutHints().literalTarget(terms.get(0));
            for (InterpretationHint hint : plan.hints()) {
                if (contains(terms, hint.original())) {
                    kept.alternativeInterpretation(hint.interpretedAs());
                } else {
                    kept.hint(hint);
                }
            }
            for (String substitute : substitutes) {
                kept.alternativeInterpretation(substitute);
            }
            if (!plan.target().isEmpty() && !contains(terms, plan.target())
                    && !containsTerm(plan.originalText(), plan.target())) {
                kept.alternativeInterpretation(plan.target());
            }
        }
        for (String term : terms) {
            kept.preservedTerm(term);
        }
        return kept.build();
    }

    /**
     * Whether the user asked about a term before and the latest answer did not take it up: the message insists on the
     * term (a correction like "I really meant ..."), so its meaning is still open, however the plan labels the message.
     */
    static boolean stillOpen(List<String> terms, ConversationContext conversation) {
        if (conversation == null || conversation.history().isEmpty()) {
            return false;
        }
        List<ChatMessage> history = conversation.history();
        String latestAnswer = "";
        int end = history.size();
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).role() == ChatRole.ASSISTANT) {
                latestAnswer = history.get(i).content();
                end = i;
                break;
            }
        }
        for (String term : terms) {
            if (containsTerm(latestAnswer, term)) {
                continue;
            }
            for (int i = 0; i < end; i++) {
                if (history.get(i).role() == ChatRole.USER && containsTerm(history.get(i).content(), term)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The terms of the original message to keep as said, in the order found. */
    static List<String> literalTerms(AnswerPlan plan, LinguisticFrame frame, ConversationContext conversation) {
        List<String> terms = new ArrayList<String>();
        if (frame != null) {
            for (String candidate : frame.acronymCandidates()) {
                add(terms, candidate);
            }
        }
        for (String literal : new String[]{plan.literalTarget(), plan.target()}) {
            if (!literal.isEmpty() && containsTerm(plan.originalText(), literal)
                    && (TermShape.isAcronymOrIdentifier(literal, false) || plan.targetKind() == TargetKind.ACRONYM
                    || plan.targetKind() == TargetKind.IDENTIFIER)) {
                add(terms, literal);
            }
        }
        if (conversation != null) {
            for (String used : usedTerms(conversation)) {
                if (containsTerm(plan.originalText(), used)) {
                    add(terms, used);
                }
            }
        }
        return terms;
    }

    /** Abbreviations and identifiers of {@code text} that are neither in the message nor a kept term. */
    private static List<String> substitutes(String text, AnswerPlan plan, List<String> terms) {
        List<String> found = new ArrayList<String>();
        for (String word : text.split("[^\\p{L}\\p{N}\\-./_]+")) {
            String token = trimPunctuation(word);
            if (token.length() >= 2 && TermShape.isAcronymOrIdentifier(token, false) && !contains(terms, token)
                    && !containsTerm(plan.originalText(), token)) {
                add(found, token);
            }
        }
        return found;
    }

    /** Abbreviations and identifiers the conversation already used, in their written form. */
    private static List<String> usedTerms(ConversationContext conversation) {
        List<String> used = new ArrayList<String>();
        for (ChatMessage message : conversation.history()) {
            for (String word : message.content().split("[^\\p{L}\\p{N}\\-./_]+")) {
                String token = trimPunctuation(word);
                if (token.length() >= 2 && TermShape.isAcronymOrIdentifier(token, shouting(message.content()))) {
                    add(used, token);
                }
            }
        }
        return used;
    }

    /** Whether a message is written mostly in capitals, so that capitals mark no abbreviation. */
    private static boolean shouting(String text) {
        int letters = 0;
        int capitals = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    capitals++;
                }
            }
        }
        return letters > 0 && capitals * 2 > letters;
    }

    private static String trimPunctuation(String word) {
        int start = 0;
        int end = word.length();
        while (start < end && !Character.isLetterOrDigit(word.charAt(start))) {
            start++;
        }
        while (end > start && !Character.isLetterOrDigit(word.charAt(end - 1))) {
            end--;
        }
        return word.substring(start, end);
    }

    private static void add(List<String> terms, String term) {
        if (term != null && !term.trim().isEmpty() && !contains(terms, term.trim())) {
            terms.add(term.trim());
        }
    }

    private static boolean contains(List<String> terms, String term) {
        for (String known : terms) {
            if (known.equalsIgnoreCase(term.trim())) {
                return true;
            }
        }
        return false;
    }

    /** The original terms first, then whatever the plan's query adds. */
    private static String query(List<String> terms, String planned) {
        StringBuilder query = new StringBuilder();
        for (String term : terms) {
            query.append(query.length() == 0 ? "" : " ").append(term);
        }
        if (planned != null && !planned.trim().isEmpty()) {
            query.append(' ').append(planned.trim());
        }
        return query.toString();
    }

    private static boolean correctedAway(AnswerPlan plan, String term) {
        for (InterpretationHint hint : plan.hints()) {
            if (hint.original().equalsIgnoreCase(term) && !hint.interpretedAs().equalsIgnoreCase(term)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code text} holds {@code term} as a word of its own, ignoring case. */
    public static boolean containsTerm(String text, String term) {
        if (text == null || term == null || term.isEmpty()) {
            return false;
        }
        String haystack = text.toLowerCase(Locale.ROOT);
        String needle = term.toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) {
                return false;
            }
            int end = at + needle.length();
            boolean startsWord = at == 0 || !Character.isLetterOrDigit(haystack.charAt(at - 1));
            boolean endsWord = end == haystack.length() || !Character.isLetterOrDigit(haystack.charAt(end));
            if (startsWord && endsWord) {
                return true;
            }
            from = at + 1;
        }
    }
}
