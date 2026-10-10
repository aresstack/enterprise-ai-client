package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.domain.source.SourceSettings;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.source.api.SourceScope;

import java.util.List;

/**
 * Hüllt den Quellen-Port eines Adapters, damit jede geöffnete Quelle ihre Fehler mit Ursachenkette protokolliert
 * ({@link LoggingKnowledgeSource}); beim Start wie nach „+ Quelle“.
 */
final class LoggingSourceProvider implements KnowledgeSourceProvider {

    private final KnowledgeSourceProvider delegate;

    LoggingSourceProvider(KnowledgeSourceProvider delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    @Override
    public KnowledgeSourceType type() {
        return delegate.type();
    }

    @Override
    public List<String> validate(SourceSettings settings) {
        return delegate.validate(settings);
    }

    @Override
    public SourceScope scope(SourceSettings settings) {
        return delegate.scope(settings);
    }

    @Override
    public KnowledgeSourcePort open(KnowledgeSourceId sourceId, SourceSettings settings) {
        return new LoggingKnowledgeSource(delegate.open(sourceId, settings));
    }

    @Override
    public String toString() {
        return "LoggingSourceProvider[" + delegate.type().id() + "]";
    }
}
