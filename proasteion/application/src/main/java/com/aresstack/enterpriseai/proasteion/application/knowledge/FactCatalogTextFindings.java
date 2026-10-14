package com.aresstack.enterpriseai.proasteion.application.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.textmining.TextFinding;
import com.aresstack.enterpriseai.astu.acropolis.textmining.TextFindingSink;
import com.aresstack.enterpriseai.astu.acropolis.textmining.TextFindingType;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceRef;
import com.aresstack.enterpriseai.fact.api.ExtractionRun;
import com.aresstack.enterpriseai.fact.api.FactAnchor;
import com.aresstack.enterpriseai.fact.api.FactPayload;
import com.aresstack.enterpriseai.fact.api.FactProvenance;
import com.aresstack.enterpriseai.fact.api.FactRole;
import com.aresstack.enterpriseai.fact.api.FactStoreException;
import com.aresstack.enterpriseai.fact.api.FactStorePort;
import com.aresstack.enterpriseai.fact.api.FactType;
import com.aresstack.enterpriseai.fact.api.FactTypeId;
import com.aresstack.enterpriseai.fact.api.MetadataFact;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps text findings (issue #107) as facts in the global metadata catalog of issue #106: one fact per finding at the
 * stable resource id, the finding type registered at runtime, payload as JSON, provenance with extractor version,
 * revision and content hash, the place as anchor. Each run is recorded with its facts, also when it found nothing.
 * Store failures leave as {@link IllegalStateException} naming only the reason, never document text.
 */
public final class FactCatalogTextFindings implements TextFindingSink {

    private final FactStorePort facts;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    /** Types registered by this sink in this run (registration is idempotent; this only saves the calls). */
    private final Set<String> registered = ConcurrentHashMap.newKeySet();

    public FactCatalogTextFindings(FactStorePort facts) {
        if (facts == null) {
            throw new IllegalArgumentException("facts must not be null");
        }
        this.facts = facts;
    }

    /**
     * Ask the recorded run, not the facts: reviewed findings outlive their run on purpose, and a document without
     * findings has none.
     */
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
                        List<TextFindingType> types, List<TextFinding> findings) {
        try {
            for (TextFindingType type : types) {
                if (registered.add(type.id())) {
                    facts.registerType(FactType.of(FactTypeId.of(type.id()), type.description(),
                            type.schemaVersion(), null));
                }
            }
            facts.registerResource(resource.resourceId(), resource.sourceId());
            FactProvenance provenance = FactProvenance.automatic(extractorId, extractorVersion)
                    .withSourceRevision(resource.revision()).withContentHash(resource.contentHash());
            List<MetadataFact> stored = new ArrayList<MetadataFact>();
            for (TextFinding finding : findings) {
                stored.add(fact(resource, finding, schemaVersion(types, finding.type()), provenance));
            }
            facts.replaceAutomaticFacts(ExtractionRun.of(resource.resourceId(), provenance), stored);
        } catch (FactStoreException failed) {
            registered.clear(); // a failed registration is tried again next time
            throw problem(failed);
        }
    }

    private MetadataFact fact(KnowledgeResourceRef resource, TextFinding finding, int schemaVersion,
                              FactProvenance provenance) {
        Map<String, Object> fields = finding.fields();
        MetadataFact.Builder fact = MetadataFact.builder(resource.resourceId(), FactTypeId.of(finding.type()),
                FactPayload.json(gson.toJson(fields)), provenance).schemaVersion(schemaVersion);
        if (!finding.role().isEmpty()) {
            fact.role(FactRole.of(finding.role()));
        }
        if (finding.span() != null) {
            fact.anchor(FactAnchor.builder().characters(finding.span().start(), finding.span().end())
                    .quote(finding.quote()).build());
        } else if (!finding.quote().isEmpty()) {
            fact.anchor(FactAnchor.builder().quote(finding.quote()).build());
        }
        return fact.build();
    }

    private static int schemaVersion(List<TextFindingType> types, String type) {
        for (TextFindingType candidate : types) {
            if (candidate.id().equals(type)) {
                return candidate.schemaVersion();
            }
        }
        throw new IllegalArgumentException("finding type not declared: " + type);
    }

    private static IllegalStateException problem(FactStoreException failed) {
        return new IllegalStateException("metadata catalog: " + failed.reason());
    }
}
