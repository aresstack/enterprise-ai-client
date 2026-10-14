package com.aresstack.enterpriseai.proasteion.application.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.textmining.TextSpan;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.DocumentStatements;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.Event;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.GroundedText;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.Mention;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.Participant;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.Relation;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementContext;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementSink;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.TermNormalizer;
import com.aresstack.enterpriseai.astu.json.Json;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceRef;
import com.aresstack.enterpriseai.fact.api.ArgumentProjection;
import com.aresstack.enterpriseai.fact.api.ArgumentRule;
import com.aresstack.enterpriseai.fact.api.ExtractionRun;
import com.aresstack.enterpriseai.fact.api.FactAnchor;
import com.aresstack.enterpriseai.fact.api.FactId;
import com.aresstack.enterpriseai.fact.api.FactPayload;
import com.aresstack.enterpriseai.fact.api.FactProvenance;
import com.aresstack.enterpriseai.fact.api.FactStoreException;
import com.aresstack.enterpriseai.fact.api.FactStorePort;
import com.aresstack.enterpriseai.fact.api.FactType;
import com.aresstack.enterpriseai.fact.api.FactTypeId;
import com.aresstack.enterpriseai.fact.api.MetadataFact;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps the statements of issue #108 as facts in the metadata catalog of issue #106: one {@code MENTION} fact per
 * mention, one {@code EVENT} per event, one {@code RELATION} per relation, all at the stable resource id with
 * provenance (extractor version, revision, content hash) and the place as anchor. Events and relations refer to the
 * concrete mention facts by id; the types' argument projections make predicate, roles, terms and status searchable
 * in the catalog. A document's statements are replaced in one transaction. Store failures leave as
 * {@link IllegalStateException} naming only the reason, never document text.
 */
public final class FactCatalogStatements implements StatementSink {

    public static final FactTypeId MENTION = FactTypeId.of("MENTION");
    public static final FactTypeId EVENT = FactTypeId.of("EVENT");
    public static final FactTypeId RELATION = FactTypeId.of("RELATION");
    /** Schema version of all three payloads. */
    static final int SCHEMA_VERSION = 1;
    /** Longest evidence sentence kept in a payload: a short proof, never a copy of the page. */
    static final int MAX_EVIDENCE_CHARS = 300;

    /** Searchable arguments: the term of a mention. */
    static final ArgumentProjection MENTION_ARGUMENTS = ArgumentProjection.of(
            ArgumentRule.fixed("normalized", "TERM", null, "value"),
            ArgumentRule.fixed("status", "STATUS", null, null));
    /** Searchable arguments: predicate, every participant (referring to its mention), status and qualifiers. */
    static final ArgumentProjection EVENT_ARGUMENTS = ArgumentProjection.of(
            ArgumentRule.fixed("predicate.normalized", "PREDICATE", null, "value"),
            ArgumentRule.byRoleField("participants", "role", "mention", "normalized").referencing(MENTION),
            ArgumentRule.fixed("status", "STATUS", null, null),
            ArgumentRule.fixed("qualifiers", "QUALIFIER", null, null));
    /** Searchable arguments: kind, subject and object (mention or event), value, status and qualifiers. */
    static final ArgumentProjection RELATION_ARGUMENTS = ArgumentProjection.of(
            ArgumentRule.fixed("kind", "PROPERTY", null, null),
            ArgumentRule.fixed("subject", "SUBJECT", "fact", "normalized"),
            ArgumentRule.fixed("object", "OBJECT", "fact", "normalized"),
            ArgumentRule.fixed("value.normalized", "VALUE", null, "value"),
            ArgumentRule.fixed("status", "STATUS", null, null),
            ArgumentRule.fixed("qualifiers", "QUALIFIER", null, null));

    private final FactStorePort facts;
    private volatile boolean registered;

    public FactCatalogStatements(FactStorePort facts) {
        if (facts == null) {
            throw new IllegalArgumentException("facts must not be null");
        }
        this.facts = facts;
    }

    /** The three types with their argument projections. */
    public static List<FactType> types() {
        return Arrays.asList(
                FactType.of(MENTION, "Concrete occurrence of a noun group in one document version (#108); "
                        + "no concept identity", SCHEMA_VERSION, null, MENTION_ARGUMENTS),
                FactType.of(EVENT, "Action described in one sentence with participant roles (#108)",
                        SCHEMA_VERSION, null, EVENT_ARGUMENTS),
                FactType.of(RELATION, "Grounded property of a mention or order of two events (#108)",
                        SCHEMA_VERSION, null, RELATION_ARGUMENTS));
    }

    /** Ask the recorded run, not the facts: a document without statements has none, and reviewed ones outlive it. */
    @Override
    public boolean isCurrent(KnowledgeResourceRef resource, String extractorId, String extractorVersion) {
        try {
            Optional<ExtractionRun> run = facts.extractionRun(resource.resourceId(), extractorId);
            return run.isPresent() && run.get().covers(extractorVersion, resource.revision(), resource.contentHash());
        } catch (FactStoreException failed) {
            throw problem(failed);
        }
    }

    @Override
    public void replace(KnowledgeResourceRef resource, String extractorId, String extractorVersion,
                        String documentText, DocumentStatements statements) {
        try {
            registerTypes();
            facts.registerResource(resource.resourceId(), resource.sourceId());
            FactProvenance provenance = FactProvenance.automatic(extractorId, extractorVersion)
                    .withSourceRevision(resource.revision()).withContentHash(resource.contentHash());
            List<MetadataFact> batch = new ArrayList<MetadataFact>();
            Map<Mention, FactId> mentionIds = new IdentityHashMap<Mention, FactId>();
            for (Mention mention : statements.mentions()) {
                MetadataFact fact = mention(resource, mention, provenance);
                mentionIds.put(mention, fact.id());
                batch.add(fact);
            }
            Map<Event, FactId> eventIds = new IdentityHashMap<Event, FactId>();
            Map<String, FactId> eventKeys = new LinkedHashMap<String, FactId>();
            for (Event event : statements.events()) {
                MetadataFact fact = event(resource, event, mentionIds, documentText, provenance);
                eventIds.put(event, fact.id());
                eventKeys.put(event.key(), fact.id());
                batch.add(fact);
            }
            for (Relation relation : statements.relations()) {
                batch.add(relation(resource, relation, mentionIds, eventKeys, provenance));
            }
            facts.replaceAutomaticFacts(ExtractionRun.of(resource.resourceId(), provenance), batch);
        } catch (FactStoreException failed) {
            registered = false; // a failed registration is tried again next time
            throw problem(failed);
        }
    }

    private void registerTypes() {
        if (registered) {
            return;
        }
        for (FactType type : types()) {
            facts.registerType(type);
        }
        registered = true;
    }

    private static MetadataFact mention(KnowledgeResourceRef resource, Mention mention, FactProvenance provenance) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("surfaceForm", mention.surface().text());
        payload.put("lemmaCandidate", lemma(mention.lemmaCandidate()));
        payload.put("normalized", normalized(mention.normalized(), mention.normalization()));
        payload.put("pronoun", mention.pronoun());
        payload.put("status", mention.status().name());
        payload.put("sentence", span(mention.sentence()));
        return MetadataFact.builder(resource.resourceId(), MENTION, FactPayload.json(Json.write(payload)), provenance)
                .schemaVersion(SCHEMA_VERSION)
                .anchor(anchor(mention.surface().span(), mention.surface().text())).build();
    }

    private static MetadataFact event(KnowledgeResourceRef resource, Event event, Map<Mention, FactId> mentionIds,
                                      String documentText, FactProvenance provenance) {
        Map<String, Object> predicate = new LinkedHashMap<String, Object>();
        predicate.put("surfaceForm", event.predicateText());
        List<Object> parts = new ArrayList<Object>();
        for (GroundedText part : event.predicate()) {
            parts.add(grounded(part));
        }
        predicate.put("parts", parts);
        predicate.put("lemmaCandidate", lemma(event.lemmaCandidate()));
        predicate.put("normalized", normalized(event.normalized(), event.normalization()));
        predicate.put("verbal", event.verbal());
        List<Object> participants = new ArrayList<Object>();
        for (Participant participant : event.participants()) {
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put("role", participant.role().name());
            entry.put("mention", mentionIds.get(participant.mention()).toString());
            entry.put("surfaceForm", participant.mention().surface().text());
            entry.put("normalized", participant.mention().normalized());
            participants.add(entry);
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("predicate", predicate);
        payload.put("participants", participants);
        putContext(payload, event.context());
        payload.put("status", event.status().name());
        payload.put("sentence", span(event.sentence()));
        String evidence = evidence(documentText, event.sentence(), event.predicate().get(0).span());
        payload.put("evidence", evidence);
        return MetadataFact.builder(resource.resourceId(), EVENT, FactPayload.json(Json.write(payload)), provenance)
                .schemaVersion(SCHEMA_VERSION).anchor(anchor(event.sentence(), evidence)).build();
    }

    private static MetadataFact relation(KnowledgeResourceRef resource, Relation relation,
                                         Map<Mention, FactId> mentionIds, Map<String, FactId> eventKeys,
                                         FactProvenance provenance) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("kind", relation.kind().name());
        if (relation.subjectMention() != null) {
            payload.put("subject", reference(mentionIds.get(relation.subjectMention()),
                    relation.subjectMention().surface().text(), relation.subjectMention().normalized()));
            payload.put("object", null);
        } else {
            payload.put("subject", reference(eventKeys.get(relation.subjectEvent().key()),
                    relation.subjectEvent().predicateText(), relation.subjectEvent().normalized()));
            payload.put("object", reference(eventKeys.get(relation.objectEvent().key()),
                    relation.objectEvent().predicateText(), relation.objectEvent().normalized()));
        }
        Map<String, Object> value = grounded(relation.literal());
        value.put("lemmaCandidate", lemma(relation.lemmaCandidate()));
        value.put("normalized", normalized(relation.normalized(), relation.normalization()));
        payload.put("value", value);
        putContext(payload, relation.context());
        payload.put("status", relation.status().name());
        payload.put("sentence", span(relation.sentence()));
        return MetadataFact.builder(resource.resourceId(), RELATION, FactPayload.json(Json.write(payload)),
                provenance).schemaVersion(SCHEMA_VERSION)
                .anchor(anchor(relation.literal().span(), relation.literal().text())).build();
    }

    private static Map<String, Object> reference(FactId id, String surface, String normalized) {
        if (id == null) {
            throw new IllegalStateException("statement refers to a statement that is not stored");
        }
        Map<String, Object> reference = new LinkedHashMap<String, Object>();
        reference.put("fact", id.toString());
        reference.put("surfaceForm", surface);
        reference.put("normalized", normalized);
        return reference;
    }

    private static void putContext(Map<String, Object> payload, StatementContext context) {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("negation", context.negation() == null ? null : grounded(context.negation()));
        fields.put("modality", context.modality() == null ? null : grounded(context.modality()));
        fields.put("condition", context.condition() == null ? null : grounded(context.condition()));
        payload.put("context", fields);
        List<Object> qualifiers = new ArrayList<Object>();
        if (context.negation() != null) {
            qualifiers.add("NEGATED");
        }
        if (context.modality() != null) {
            qualifiers.add("MODAL");
        }
        if (context.condition() != null) {
            qualifiers.add("CONDITIONAL");
        }
        payload.put("qualifiers", qualifiers);
    }

    private static Map<String, Object> lemma(String lemma) {
        if (lemma == null) {
            return null;
        }
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("value", lemma);
        value.put("status", TermNormalizer.LEMMA_STATUS);
        return value;
    }

    private static Map<String, Object> normalized(String value, String method) {
        Map<String, Object> normalized = new LinkedHashMap<String, Object>();
        normalized.put("value", value);
        normalized.put("method", method);
        return normalized;
    }

    private static Map<String, Object> grounded(GroundedText text) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("surfaceForm", text.text());
        value.put("start", text.span().start());
        value.put("end", text.span().end());
        return value;
    }

    private static Map<String, Object> span(TextSpan span) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("start", span.start());
        value.put("end", span.end());
        return value;
    }

    /** The sentence as short evidence: whole if short, else a window around {@code focus}. */
    static String evidence(String documentText, TextSpan sentence, TextSpan focus) {
        if (documentText == null || sentence.end() > documentText.length()) {
            return "";
        }
        String whole = sentence.of(documentText).replaceAll("\\s+", " ").trim();
        if (whole.length() <= MAX_EVIDENCE_CHARS) {
            return whole;
        }
        int center = Math.max(0, focus.start() - sentence.start());
        int from = Math.max(0, Math.min(center - MAX_EVIDENCE_CHARS / 2, whole.length() - MAX_EVIDENCE_CHARS));
        return "…" + whole.substring(from, from + MAX_EVIDENCE_CHARS).trim() + "…";
    }

    private static FactAnchor anchor(TextSpan span, String quote) {
        String text = quote == null ? "" : quote.trim();
        if (text.length() > MAX_EVIDENCE_CHARS) {
            text = text.substring(0, MAX_EVIDENCE_CHARS);
        }
        return FactAnchor.builder().characters(span.start(), span.end()).quote(text).build();
    }

    private static IllegalStateException problem(FactStoreException failed) {
        return new IllegalStateException("metadata catalog: " + failed.reason());
    }
}
