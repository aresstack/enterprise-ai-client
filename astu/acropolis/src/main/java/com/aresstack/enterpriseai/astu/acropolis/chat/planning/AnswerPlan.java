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
    private final TargetKind targetKind;
    private final InformationNeed informationNeed;
    private final boolean organizationSpecificMeaningPossible;
    private final boolean likelySpeechRecognitionError;
    private final Ambiguity ambiguity;
    private final double confidence;
    private final String knowledgeQuery;
    private final NextAction nextAction;
    private final String clarificationQuestion;
    private final List<InterpretationHint> hints;
    private final String raw;

    private AnswerPlan(Builder b) {
        this.known = b.known;
        this.originalText = b.originalText == null ? "" : b.originalText;
        this.interpretedText = b.interpretedText == null || b.interpretedText.trim().isEmpty()
                ? this.originalText : b.interpretedText.trim();
        this.source = b.source == null ? MessageOrigin.TYPED : b.source;
        this.intent = b.intent == null ? UserIntent.UNKNOWN : b.intent;
        this.target = b.target == null ? "" : b.target.trim();
        this.targetKind = b.targetKind == null ? TargetKind.UNKNOWN : b.targetKind;
        this.informationNeed = b.informationNeed == null ? InformationNeed.UNKNOWN : b.informationNeed;
        this.organizationSpecificMeaningPossible = b.organizationSpecificMeaningPossible;
        this.likelySpeechRecognitionError = b.likelySpeechRecognitionError;
        this.ambiguity = b.ambiguity == null ? Ambiguity.UNKNOWN : b.ambiguity;
        this.confidence = Math.max(0, Math.min(1, b.confidence));
        this.knowledgeQuery = b.knowledgeQuery == null ? "" : b.knowledgeQuery.trim();
        this.nextAction = b.nextAction == null ? NextAction.UNKNOWN : b.nextAction;
        this.clarificationQuestion = b.clarificationQuestion == null ? "" : b.clarificationQuestion.trim();
        this.hints = Collections.unmodifiableList(new ArrayList<InterpretationHint>(b.hints));
        this.raw = b.raw == null ? "" : b.raw;
    }

    public static Builder builder(String originalText, MessageOrigin source) {
        return new Builder(originalText, source);
    }

    /** No reading obtained (step off, request failed, verdict unreadable). */
    public static AnswerPlan unknown(String originalText, MessageOrigin source) {
        return new Builder(originalText, source).build();
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

    public Ambiguity ambiguity() {
        return ambiguity;
    }

    /** 0 to 1. */
    public double confidence() {
        return confidence;
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

    @Override
    public String toString() {
        return "AnswerPlan[known=" + known + ", interpreted=" + interpretedText + ", intent=" + intent
                + ", target=" + target + " (" + targetKind + "), need=" + informationNeed + ", orgSpecific="
                + organizationSpecificMeaningPossible + ", asr=" + likelySpeechRecognitionError + ", ambiguity="
                + ambiguity + ", confidence=" + confidence + ", query=" + knowledgeQuery + ", next=" + nextAction + ", clarification=" + clarificationQuestion
                + ", hints=" + hints + "]";
    }

    /** Collects the reading. */
    public static final class Builder {
        private boolean known;
        private final String originalText;
        private final MessageOrigin source;
        private String interpretedText;
        private UserIntent intent;
        private String target;
        private TargetKind targetKind;
        private InformationNeed informationNeed;
        private boolean organizationSpecificMeaningPossible;
        private boolean likelySpeechRecognitionError;
        private Ambiguity ambiguity;
        private double confidence;
        private String knowledgeQuery;
        private NextAction nextAction;
        private String clarificationQuestion;
        private final List<InterpretationHint> hints = new ArrayList<InterpretationHint>();
        private String raw;

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

        public Builder ambiguity(Ambiguity value) {
            ambiguity = value;
            return this;
        }

        public Builder confidence(double value) {
            confidence = value;
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

        public AnswerPlan build() {
            return new AnswerPlan(this);
        }
    }
}
