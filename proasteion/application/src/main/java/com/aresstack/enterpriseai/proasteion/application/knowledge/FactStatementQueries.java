package com.aresstack.enterpriseai.proasteion.application.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.ParticipantRole;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.TermNormalizer;
import com.aresstack.enterpriseai.astu.json.Json;
import com.aresstack.enterpriseai.astu.json.JsonException;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.fact.api.ArgumentCondition;
import com.aresstack.enterpriseai.fact.api.FactId;
import com.aresstack.enterpriseai.fact.api.FactStoreException;
import com.aresstack.enterpriseai.fact.api.FactStorePort;
import com.aresstack.enterpriseai.fact.api.MetadataFact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Role questions over the stored events of issue #108, answered by the argument index and never by text search:
 * "who does {@code predicate} to {@code term}" finds the events with that predicate and that term in the known role
 * and reads the wanted role from exactly those events. A term that appears in a document in another role (the receipt
 * that is checked) is therefore never returned as the actor of a different event (the payment that is released).
 */
public final class FactStatementQueries {

    /** One participant of a matching event, with the event's evidence. */
    public static final class Answer {
        private final FactId event;
        private final KnowledgeResourceId resource;
        private final String surfaceForm;
        private final String normalized;
        private final String status;
        private final List<String> qualifiers;
        private final String evidence;

        Answer(FactId event, KnowledgeResourceId resource, String surfaceForm, String normalized, String status,
               List<String> qualifiers, String evidence) {
            this.event = event;
            this.resource = resource;
            this.surfaceForm = surfaceForm;
            this.normalized = normalized;
            this.status = status;
            this.qualifiers = qualifiers;
            this.evidence = evidence;
        }

        public FactId event() {
            return event;
        }

        public KnowledgeResourceId resource() {
            return resource;
        }

        /** The participant as written in the sentence. */
        public String surfaceForm() {
            return surfaceForm;
        }

        public String normalized() {
            return normalized;
        }

        /** Extraction status of the event (EXTRACTED, MODEL_VALIDATED, UNCERTAIN, CONFIRMED …). */
        public String status() {
            return status;
        }

        /** NEGATED, MODAL, CONDITIONAL: the sentence states the event only with this restriction. */
        public List<String> qualifiers() {
            return qualifiers;
        }

        /** The sentence (or a window of it) the event was read from. */
        public String evidence() {
            return evidence;
        }

        @Override
        public String toString() {
            return surfaceForm + " (" + status + (qualifiers.isEmpty() ? "" : ", " + qualifiers) + ")";
        }
    }

    private final FactStorePort facts;

    public FactStatementQueries(FactStorePort facts) {
        if (facts == null) {
            throw new IllegalArgumentException("facts must not be null");
        }
        this.facts = facts;
    }

    /**
     * Participants in {@code wanted} role of the events with {@code predicateLemma} whose {@code knownRole} is
     * {@code knownLemma} ({@code null}: any). Lemmas are normalized like stored lemma candidates. Rejected events are
     * left out.
     */
    public List<Answer> participants(String predicateLemma, ParticipantRole wanted, ParticipantRole knownRole,
                                     String knownLemma) {
        if (predicateLemma == null || wanted == null) {
            throw new IllegalArgumentException("predicate and wanted role must not be null");
        }
        List<ArgumentCondition> conditions = new ArrayList<ArgumentCondition>();
        conditions.add(ArgumentCondition.value("PREDICATE", TermNormalizer.fromLemma(predicateLemma)));
        if (knownRole != null && knownLemma != null) {
            conditions.add(ArgumentCondition.value(knownRole.name(), TermNormalizer.fromLemma(knownLemma)));
        }
        List<MetadataFact> events;
        try {
            events = facts.factsWithArguments(FactCatalogStatements.EVENT, conditions);
        } catch (FactStoreException failed) {
            throw new IllegalStateException("metadata catalog: " + failed.reason());
        }
        List<Answer> answers = new ArrayList<Answer>();
        for (MetadataFact event : events) {
            Map<String, Object> payload;
            try {
                payload = Json.parseObject(event.payload().json());
            } catch (JsonException unreadable) {
                continue;
            }
            String status = Json.string(payload, "status");
            if ("REJECTED".equals(status)) {
                continue;
            }
            List<String> qualifiers = strings(Json.array(payload, "qualifiers"));
            for (Object element : Json.array(payload, "participants")) {
                if (!(element instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> participant = (Map<String, Object>) element;
                if (wanted.name().equals(Json.string(participant, "role"))) {
                    answers.add(new Answer(event.id(), event.resourceId(), Json.string(participant, "surfaceForm"),
                            Json.string(participant, "normalized"), status, qualifiers,
                            Json.string(payload, "evidence")));
                }
            }
        }
        return Collections.unmodifiableList(answers);
    }

    private static List<String> strings(List<Object> values) {
        List<String> strings = new ArrayList<String>();
        for (Object value : values) {
            if (value instanceof String) {
                strings.add((String) value);
            }
        }
        return Collections.unmodifiableList(strings);
    }
}
