package com.aresstack.enterpriseai.astu.acropolis.chat.planning;

import com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The plan for answering the current message: what the user most likely wanted to say and achieve, what information
 * that needs, and the next step (answer, application help, search, or a clarifying question). Proposes a kind of
 * action, never a tool call. Lives for one turn only: the conversation stores the original message, never the plan.
 */
public final class AnswerPlan {

    private final boolean known;
    private final String originalText;
    private final String interpretedText;
    private final MessageOrigin source;
    private final UserIntent intent;
    private final String target;
    private final String literalTarget;
    private final TargetKind targetKind;
    private final InformationNeed informationNeed;
    private final boolean organizationSpecificMeaningPossible;
    private final boolean likelySpeechRecognitionError;
    private final boolean referencesPreviousAnswer;
    private final Ambiguity ambiguity;
    private final double utteranceConfidence;
    private final double resolutionConfidence;
    private final List<String> alternativeInterpretations;
    private final String knowledgeQuery;
    private final NextAction nextAction;
    private final String clarificationQuestion;
    private final List<InterpretationHint> hints;
    private final String raw;
    private final String failure;
    private final List<String> preservedTerms;

    private AnswerPlan(Builder b) {
        this.known = b.known;
        this.originalText = b.originalText == null ? "" : b.originalText;
        this.interpretedText = b.interpretedText == null || b.interpretedText.trim().isEmpty()
                ? this.originalText : b.interpretedText.trim();
        this.source = b.source == null ? MessageOrigin.TYPED : b.source;
        this.intent = b.intent == null ? UserIntent.UNKNOWN : b.intent;
        this.target = b.target == null ? "" : b.target.trim();
        this.literalTarget = b.literalTarget == null ? "" : b.literalTarget.trim();
        this.targetKind = b.targetKind == null ? TargetKind.UNKNOWN : b.targetKind;
        this.informationNeed = b.informationNeed == null ? InformationNeed.UNKNOWN : b.informationNeed;
        this.organizationSpecificMeaningPossible = b.organizationSpecificMeaningPossible;
        this.likelySpeechRecognitionError = b.likelySpeechRecognitionError;
        this.referencesPreviousAnswer = b.referencesPreviousAnswer;
        this.ambiguity = b.ambiguity == null ? Ambiguity.UNKNOWN : b.ambiguity;
        this.utteranceConfidence = Math.max(0, Math.min(1, b.utteranceConfidence));
        this.resolutionConfidence = Math.max(0, Math.min(1, b.resolutionConfidence));
        this.alternativeInterpretations = Collections.unmodifiableList(
                new ArrayList<String>(b.alternativeInterpretations));
        this.knowledgeQuery = b.knowledgeQuery == null ? "" : b.knowledgeQuery.trim();
        this.nextAction = b.nextAction == null ? NextAction.UNKNOWN : b.nextAction;
        this.clarificationQuestion = b.clarificationQuestion == null ? "" : b.clarificationQuestion.trim();
        this.hints = Collections.unmodifiableList(new ArrayList<InterpretationHint>(b.hints));
        this.raw = b.raw == null ? "" : b.raw;
        this.failure = b.failure == null ? "" : b.failure;
        this.preservedTerms = Collections.unmodifiableList(new ArrayList<String>(b.preservedTerms));
    }

    /** A builder holding this plan, to change single fields. */
    public Builder toBuilder() {
        Builder copy = new Builder(originalText, source)
                .known(known)
                .interpretedText(interpretedText)
                .intent(intent)
                .target(target, targetKind)
                .literalTarget(literalTarget)
                .informationNeed(informationNeed)
                .organizationSpecificMeaningPossible(organizationSpecificMeaningPossible)
                .likelySpeechRecognitionError(likelySpeechRecognitionError)
                .referencesPreviousAnswer(referencesPreviousAnswer)
                .ambiguity(ambiguity)
                .utteranceConfidence(utteranceConfidence)
                .resolutionConfidence(resolutionConfidence)
                .knowledgeQuery(knowledgeQuery)
                .nextAction(nextAction)
                .clarificationQuestion(clarificationQuestion)
                .raw(raw)
                .failure(failure);
        for (InterpretationHint hint : hints) {
            copy.hint(hint);
        }
        for (String term : preservedTerms) {
            copy.preservedTerm(term);
        }
        for (String alternative : alternativeInterpretations) {
            copy.alternativeInterpretation(alternative);
        }
        return copy;
    }

    public static Builder builder(String originalText, MessageOrigin source) {
        return new Builder(originalText, source);
    }

    /** No plan obtained (step off, request failed, verdict unreadable). */
    public static AnswerPlan unknown(String originalText, MessageOrigin source) {
        return new Builder(originalText, source).build();
    }

    /**
     * No plan obtained, with the reason for the diagnostic log.
     *
     * @param failure why there is no plan, without the user's text (e.g. the error kind or the shape of the answer)
     */
    public static AnswerPlan failed(String originalText, MessageOrigin source, String failure) {
        return new Builder(originalText, source).failure(failure).build();
    }

    public boolean isKnown() {
        return known;
    }

    public String originalText() {
        return originalText;
    }

    /** The most likely meant message; the original when nothing was corrected. */
    public String interpretedText() {
        return interpretedText;
    }

    public boolean isReworded() {
        return !interpretedText.equals(originalText.trim());
    }

    public MessageOrigin source() {
        return source;
    }

    public UserIntent intent() {
        return intent;
    }

    /** What the message is about, {@code ""} when nothing specific. */
    public String target() {
        return target;
    }

    /**
     * The target as the user said or wrote it, {@code ""} when nothing specific. Differs from {@link #target()} only
     * when the plan resolved a reference; never a guess at a better-known term.
     */
    public String literalTarget() {
        return literalTarget;
    }

    public TargetKind targetKind() {
        return targetKind;
    }

    public InformationNeed informationNeed() {
        return informationNeed;
    }

    public boolean isOrganizationSpecificMeaningPossible() {
        return organizationSpecificMeaningPossible;
    }

    public boolean isLikelySpeechRecognitionError() {
        return likelySpeechRecognitionError;
    }

    /** Whether the message refers to terms or statements of the latest answer of the assistant. */
    public boolean referencesPreviousAnswer() {
        return referencesPreviousAnswer;
    }

    public Ambiguity ambiguity() {
        return ambiguity;
    }

    /** How sure the plan is about what the user said and meant to say, 0 to 1. */
    public double utteranceConfidence() {
        return utteranceConfidence;
    }

    /** How sure the plan is about what the named term refers to, 0 to 1; low for a term it cannot resolve here. */
    public double resolutionConfidence() {
        return resolutionConfidence;
    }

    /**
     * Other readings of the target the plan considers possible: hypotheses only, never searched automatically and never
     * used in place of what the user said. Named in the question when the literal term resolves to nothing.
     */
    public List<String> alternativeInterpretations() {
        return alternativeInterpretations;
    }

    /** Whether the client resolves {@link #resolvedTerm()} in the organization's sources before answering. */
    public boolean resolvesTerm() {
        return known && nextAction == NextAction.RESOLVE_INTERNAL_TERM && !resolvedTerm().isEmpty();
    }

    /** Whether {@code query} still holds the term to resolve (one of the kept terms, else the literal target). */
    public boolean keepsResolvedTerm(String query) {
        if (preservedTerms.isEmpty()) {
            return LiteralEntityPreservationPolicy.containsTerm(query, literalTarget.isEmpty() ? target : literalTarget);
        }
        for (String term : preservedTerms) {
            if (LiteralEntityPreservationPolicy.containsTerm(query, term)) {
                return true;
            }
        }
        return false;
    }

    /** The term to resolve as said: the kept terms, else the literal target, else the target. */
    public String resolvedTerm() {
        if (!preservedTerms.isEmpty()) {
            StringBuilder terms = new StringBuilder();
            for (String term : preservedTerms) {
                terms.append(terms.length() == 0 ? "" : " ").append(term);
            }
            return terms.toString();
        }
        return literalTarget.isEmpty() ? target : literalTarget;
    }

    /**
     * Keywords for the organization's knowledge sources when the answer needs them, {@code ""} otherwise. A description
     * of the information need, not a tool call: whether and how to search stays with the caller and the chat model.
     */
    public String knowledgeQuery() {
        return knowledgeQuery;
    }

    /** The next step the plan proposes; the answering call may still choose otherwise, except for a clarification. */
    public NextAction nextAction() {
        return nextAction;
    }

    /** The question to ask instead of answering, {@code ""} unless {@link #asksClarification()}. */
    public String clarificationQuestion() {
        return clarificationQuestion;
    }

    /** Whether the plan holds the answer back and asks the user first. */
    public boolean asksClarification() {
        return known && nextAction == NextAction.ASK_CLARIFICATION && !clarificationQuestion.isEmpty();
    }

    /** Possible speech recognition corrections ({@code original} probably meant as {@code interpretedAs}). */
    public List<InterpretationHint> hints() {
        return hints;
    }

    /** The verdict as the model wrote it, for the diagnostic log. */
    public String raw() {
        return raw;
    }

    /**
     * Abbreviations and identifiers of the original message that the client kept literally for the first internal
     * search, whatever the plan read into them ({@link LiteralEntityPreservationPolicy}); empty when none.
     */
    public List<String> preservedTerms() {
        return preservedTerms;
    }

    /** Why no plan was obtained, {@code ""} for a plan or when the step did not run; never contains the user's text. */
    public String failure() {
        return failure;
    }

    @Override
    public String toString() {
        return "AnswerPlan[known=" + known + ", interpreted=" + interpretedText + ", intent=" + intent
                + ", target=" + target + " (" + targetKind + "), literal=" + literalTarget + ", need=" + informationNeed + ", orgSpecific="
                + organizationSpecificMeaningPossible + ", asr=" + likelySpeechRecognitionError + ", previousAnswer=" + referencesPreviousAnswer + ", ambiguity="
                + ambiguity + ", utterance=" + utteranceConfidence + ", resolution="
                + resolutionConfidence + ", alternatives=" + alternativeInterpretations + ", query=" + knowledgeQuery + ", next=" + nextAction + ", clarification=" + clarificationQuestion
                + ", hints=" + hints + ", preserved=" + preservedTerms + "]";
    }

    /** Collects the reading. */
    public static final class Builder {
        private boolean known;
        private final String originalText;
        private final MessageOrigin source;
        private String interpretedText;
        private UserIntent intent;
        private String target;
        private String literalTarget;
        private TargetKind targetKind;
        private InformationNeed informationNeed;
        private boolean organizationSpecificMeaningPossible;
        private boolean likelySpeechRecognitionError;
        private boolean referencesPreviousAnswer;
        private Ambiguity ambiguity;
        private double utteranceConfidence;
        private double resolutionConfidence;
        private final List<String> alternativeInterpretations = new ArrayList<String>();
        private String knowledgeQuery;
        private NextAction nextAction;
        private String clarificationQuestion;
        private final List<InterpretationHint> hints = new ArrayList<InterpretationHint>();
        private String raw;
        private String failure;
        private final List<String> preservedTerms = new ArrayList<String>();

        private Builder(String originalText, MessageOrigin source) {
            this.originalText = originalText;
            this.source = source;
        }

        public Builder known(boolean value) {
            known = value;
            return this;
        }

        public Builder interpretedText(String value) {
            interpretedText = value;
            return this;
        }

        public Builder intent(UserIntent value) {
            intent = value;
            return this;
        }

        public Builder target(String value, TargetKind kind) {
            target = value;
            targetKind = kind;
            return this;
        }

        public Builder literalTarget(String value) {
            literalTarget = value;
            return this;
        }

        public Builder informationNeed(InformationNeed value) {
            informationNeed = value;
            return this;
        }

        public Builder organizationSpecificMeaningPossible(boolean value) {
            organizationSpecificMeaningPossible = value;
            return this;
        }

        public Builder likelySpeechRecognitionError(boolean value) {
            likelySpeechRecognitionError = value;
            return this;
        }

        public Builder referencesPreviousAnswer(boolean value) {
            referencesPreviousAnswer = value;
            return this;
        }

        public Builder ambiguity(Ambiguity value) {
            ambiguity = value;
            return this;
        }

        public Builder utteranceConfidence(double value) {
            utteranceConfidence = value;
            return this;
        }

        public Builder resolutionConfidence(double value) {
            resolutionConfidence = value;
            return this;
        }

        /** Add another possible reading of the target; blanks and repeats are dropped. */
        public Builder alternativeInterpretation(String value) {
            if (value != null && !value.trim().isEmpty() && !alternativeInterpretations.contains(value.trim())) {
                alternativeInterpretations.add(value.trim());
            }
            return this;
        }

        public Builder knowledgeQuery(String value) {
            knowledgeQuery = value;
            return this;
        }

        public Builder nextAction(NextAction value) {
            nextAction = value;
            return this;
        }

        public Builder clarificationQuestion(String value) {
            clarificationQuestion = value;
            return this;
        }

        public Builder hint(InterpretationHint value) {
            if (value != null) {
                hints.add(value);
            }
            return this;
        }

        public Builder raw(String value) {
            raw = value;
            return this;
        }

        public Builder preservedTerm(String value) {
            if (value != null && !value.trim().isEmpty() && !preservedTerms.contains(value.trim())) {
                preservedTerms.add(value.trim());
            }
            return this;
        }

        public Builder withoutHints() {
            hints.clear();
            return this;
        }

        public Builder failure(String value) {
            failure = value;
            return this;
        }

        public AnswerPlan build() {
            return new AnswerPlan(this);
        }
    }
}
