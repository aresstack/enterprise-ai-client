package com.aresstack.enterpriseai.astu.acropolis.chat.linguistics;

import com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The form of one user message: hints for the chat model, not its meaning. Lives for one turn only; it is never stored
 * with the conversation, shown in the transcript or exported.
 */
public final class LinguisticFrame {

    private final MessageOrigin source;
    private final String language;
    private final int sentenceCount;
    private final SentenceType sentenceType;
    private final List<LinguisticToken> tokens;
    private final boolean tagged;
    private final boolean interrogative;
    private final boolean verb;
    private final boolean finiteVerb;
    private final boolean imperativePossible;
    private final List<String> acronymCandidates;
    private final List<String> uncertainTokens;
    private final List<String> suspectedTokens;
    private final Set<Diagnostic> diagnostics;

    private LinguisticFrame(Builder builder) {
        this.source = builder.source;
        this.language = builder.language == null ? "" : builder.language;
        this.sentenceCount = builder.sentenceCount;
        this.sentenceType = builder.sentenceType == null ? SentenceType.UNKNOWN : builder.sentenceType;
        this.tokens = Collections.unmodifiableList(new ArrayList<LinguisticToken>(builder.tokens));
        this.tagged = builder.tagged;
        this.interrogative = builder.interrogative;
        this.verb = builder.verb;
        this.finiteVerb = builder.finiteVerb;
        this.imperativePossible = builder.imperativePossible;
        this.acronymCandidates = Collections.unmodifiableList(new ArrayList<String>(builder.acronymCandidates));
        this.uncertainTokens = Collections.unmodifiableList(new ArrayList<String>(builder.uncertainTokens));
        this.suspectedTokens = Collections.unmodifiableList(new ArrayList<String>(builder.suspectedTokens));
        this.diagnostics = Collections.unmodifiableSet(builder.diagnostics.isEmpty()
                ? EnumSet.noneOf(Diagnostic.class) : EnumSet.copyOf(builder.diagnostics));
    }

    public static Builder builder(MessageOrigin source) {
        return new Builder(source);
    }

    /** Typed or dictated. */
    public MessageOrigin source() {
        return source;
    }

    /** {@code de}, {@code en}, or {@code ""} when unknown. */
    public String language() {
        return language;
    }

    /** Number of sentences, {@code 0} when not determined. */
    public int sentenceCount() {
        return sentenceCount;
    }

    public SentenceType sentenceType() {
        return sentenceType;
    }

    /** The tokens with part-of-speech tags; empty without a part-of-speech model. */
    public List<LinguisticToken> tokens() {
        return tokens;
    }

    /** Whether a part-of-speech model tagged the message; without it the structural hints below are unknown. */
    public boolean isTagged() {
        return tagged;
    }

    public boolean hasInterrogative() {
        return interrogative;
    }

    public boolean hasVerb() {
        return verb;
    }

    public boolean hasFiniteVerb() {
        return finiteVerb;
    }

    public boolean isImperativePossible() {
        return imperativePossible;
    }

    /** Words spelled like acronyms or identifiers (capitals, digits); whether they are ambiguous is not known. */
    public List<String> acronymCandidates() {
        return acronymCandidates;
    }

    /** Words tagged with a probability below one half. */
    public List<String> uncertainTokens() {
        return uncertainTokens;
    }

    /** Words that may be speech recognition errors. */
    public List<String> suspectedTokens() {
        return suspectedTokens;
    }

    public Set<Diagnostic> diagnostics() {
        return diagnostics;
    }

    @Override
    public String toString() {
        return "LinguisticFrame[source=" + source + ", language=" + language + ", sentences=" + sentenceCount
                + ", type=" + sentenceType + ", tokens=" + tokens + ", interrogative=" + interrogative + ", verb="
                + verb + ", finiteVerb=" + finiteVerb + ", imperative=" + imperativePossible + ", acronyms="
                + acronymCandidates + ", uncertain=" + uncertainTokens + ", suspected=" + suspectedTokens
                + ", diagnostics=" + diagnostics + "]";
    }

    /** Collects the hints. */
    public static final class Builder {
        private final MessageOrigin source;
        private String language;
        private int sentenceCount;
        private SentenceType sentenceType;
        private final List<LinguisticToken> tokens = new ArrayList<LinguisticToken>();
        private boolean tagged;
        private boolean interrogative;
        private boolean verb;
        private boolean finiteVerb;
        private boolean imperativePossible;
        private final List<String> acronymCandidates = new ArrayList<String>();
        private final List<String> uncertainTokens = new ArrayList<String>();
        private final List<String> suspectedTokens = new ArrayList<String>();
        private final Set<Diagnostic> diagnostics = EnumSet.noneOf(Diagnostic.class);

        private Builder(MessageOrigin source) {
            this.source = source == null ? MessageOrigin.TYPED : source;
        }

        public Builder language(String value) {
            language = value;
            return this;
        }

        public Builder sentenceCount(int value) {
            sentenceCount = Math.max(0, value);
            return this;
        }

        public Builder sentenceType(SentenceType value) {
            sentenceType = value;
            return this;
        }

        public Builder tokens(List<LinguisticToken> value) {
            tokens.clear();
            tokens.addAll(value);
            tagged = !value.isEmpty();
            return this;
        }

        public Builder interrogative(boolean value) {
            interrogative = value;
            return this;
        }

        public Builder verb(boolean value) {
            verb = value;
            return this;
        }

        public Builder finiteVerb(boolean value) {
            finiteVerb = value;
            return this;
        }

        public Builder imperativePossible(boolean value) {
            imperativePossible = value;
            return this;
        }

        public Builder acronymCandidate(String word) {
            add(acronymCandidates, word);
            return this;
        }

        public Builder uncertainToken(String word) {
            add(uncertainTokens, word);
            return this;
        }

        public Builder suspectedToken(String word) {
            add(suspectedTokens, word);
            return this;
        }

        public Builder diagnostic(Diagnostic value) {
            diagnostics.add(value);
            return this;
        }

        private static void add(List<String> words, String word) {
            if (word != null && !words.contains(word)) {
                words.add(word);
            }
        }

        public LinguisticFrame build() {
            return new LinguisticFrame(this);
        }
    }
}
