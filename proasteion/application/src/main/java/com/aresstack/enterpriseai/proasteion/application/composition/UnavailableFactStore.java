package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.fact.api.ExtractionRun;
import com.aresstack.enterpriseai.fact.api.FactId;
import com.aresstack.enterpriseai.fact.api.FactStoreException;
import com.aresstack.enterpriseai.fact.api.FactStorePort;
import com.aresstack.enterpriseai.fact.api.FactType;
import com.aresstack.enterpriseai.fact.api.FactTypeId;
import com.aresstack.enterpriseai.fact.api.MetadataFact;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Stand-in when ports are built without a metadata catalog (tests, reduced graphs): every call fails clearly. */
final class UnavailableFactStore implements FactStorePort {

    @Override
    public void registerType(FactType type) {
        throw unavailable();
    }

    @Override
    public Optional<FactType> type(FactTypeId id) {
        throw unavailable();
    }

    @Override
    public List<FactType> types() {
        throw unavailable();
    }

    @Override
    public void registerResource(KnowledgeResourceId resource, SourceAddress source) {
        throw unavailable();
    }

    @Override
    public boolean isResourceRegistered(KnowledgeResourceId resource) {
        throw unavailable();
    }

    @Override
    public MetadataFact save(MetadataFact fact) {
        throw unavailable();
    }

    @Override
    public Optional<MetadataFact> fact(FactId id) {
        throw unavailable();
    }

    @Override
    public boolean removeFact(FactId id) {
        throw unavailable();
    }

    @Override
    public List<MetadataFact> factsOf(KnowledgeResourceId resource) {
        throw unavailable();
    }

    @Override
    public List<MetadataFact> factsOf(KnowledgeResourceId resource, FactTypeId type) {
        throw unavailable();
    }

    @Override
    public List<MetadataFact> factsOfType(FactTypeId type) {
        throw unavailable();
    }

    @Override
    public Set<KnowledgeResourceId> resourcesWithType(FactTypeId type) {
        throw unavailable();
    }

    @Override
    public int replaceAutomaticFacts(KnowledgeResourceId resource, String extractorId, List<MetadataFact> facts) {
        throw unavailable();
    }

    @Override
    public int replaceAutomaticFacts(ExtractionRun run, List<MetadataFact> facts) {
        throw unavailable();
    }

    @Override
    public Optional<ExtractionRun> extractionRun(KnowledgeResourceId resource, String extractorId) {
        throw unavailable();
    }

    @Override
    public int removeResource(KnowledgeResourceId resource) {
        throw unavailable();
    }

    @Override
    public int removeSource(SourceAddress source) {
        throw unavailable();
    }

    private static FactStoreException unavailable() {
        return new FactStoreException(FactStoreException.Reason.STORAGE, "No metadata catalog is configured");
    }
}
