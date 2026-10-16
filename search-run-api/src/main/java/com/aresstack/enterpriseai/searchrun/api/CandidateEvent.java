package com.aresstack.enterpriseai.searchrun.api;

import java.time.Instant;

/** An append-only event about one candidate of a run; never changes the candidate itself. */
public final class CandidateEvent {

    private static final int MAX_DETAIL = 1000;

    private final SearchRunId run;
    private final int candidateNumber;
    private final CandidateEventKind kind;
    private final Instant at;
    private final String detail;
    private final Double score;

    /**
     * @param detail optional short text (e.g. why loading failed); no content of the resource
     * @param score  optional score of an evaluation; {@code null} when the event has none
     */
    public CandidateEvent(SearchRunId run, int candidateNumber, CandidateEventKind kind, Instant at, String detail,
                          Double score) {
        if (run == null || kind == null || at == null) {
            throw new IllegalArgumentException("run, kind and at must not be null");
        }
        if (candidateNumber < 1) {
            throw new IllegalArgumentException("candidate number must be at least 1: " + candidateNumber);
        }
        if (score != null && (score.isNaN() || score.isInfinite())) {
            throw new IllegalArgumentException("score must be finite");
        }
        String text = detail == null || detail.trim().isEmpty() ? null : detail.trim();
        this.run = run;
        this.candidateNumber = candidateNumber;
        this.kind = kind;
        this.at = at;
        this.detail = text != null && text.length() > MAX_DETAIL ? text.substring(0, MAX_DETAIL) : text;
        this.score = score;
    }

    public SearchRunId run() {
        return run;
    }

    public int candidateNumber() {
        return candidateNumber;
    }

    public CandidateEventKind kind() {
        return kind;
    }

    public Instant at() {
        return at;
    }

    /** Short text or {@code null}. */
    public String detail() {
        return detail;
    }

    /** Score or {@code null}. */
    public Double score() {
        return score;
    }

    @Override
    public String toString() {
        return "CandidateEvent{" + run + "#" + candidateNumber + ", " + kind + "}";
    }
}
