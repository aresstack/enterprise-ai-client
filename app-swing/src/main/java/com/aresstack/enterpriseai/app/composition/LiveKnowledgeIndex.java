package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.net.NetworkServices;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexPort;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeKeywordQuery;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSearchHit;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSemanticQuery;
import com.aresstack.enterpriseai.knowledge.lucene.LuceneKnowledgeIndex;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Der Wissensindex, der ein geändertes Indexverzeichnis ohne Neustart übernimmt: {@link #apply} öffnet den Index
 * im neuen Verzeichnis und schließt den alten; jeder Aufruf geht an den aktuellen.
 */
final class LiveKnowledgeIndex implements KnowledgeIndexPort, ApplicationPorts.Reconfigurable, Closeable {

    private static final Logger LOG = Logger.getLogger(LiveKnowledgeIndex.class.getName());

    private Path directory;
    private volatile LuceneKnowledgeIndex current;

    LiveKnowledgeIndex(Path directory) {
        this.directory = directory;
        this.current = new LuceneKnowledgeIndex(directory);
    }

    @Override
    public void apply(AppConfig config, NetworkServices network) {
        Path next = config.knowledge().indexDirectory();
        LuceneKnowledgeIndex previous;
        synchronized (this) {
            if (next == null || next.equals(directory)) {
                return;
            }
            previous = current;
            current = new LuceneKnowledgeIndex(next);
            directory = next;
        }
        LOG.info("Wissensindex jetzt unter " + next);
        previous.close();
    }

    @Override
    public void close() {
        current.close();
    }

    @Override
    public void index(Collection<KnowledgeIndexEntry> entries) {
        current.index(entries);
    }

    @Override
    public void replace(EmbeddingModelIdentity space, KnowledgeResourceId resourceId,
                        Collection<KnowledgeIndexEntry> entries) {
        current.replace(space, resourceId, entries);
    }

    @Override
    public List<KnowledgeSearchHit> keywordSearch(KnowledgeKeywordQuery query) {
        return current.keywordSearch(query);
    }

    @Override
    public List<KnowledgeSearchHit> semanticSearch(KnowledgeSemanticQuery query) {
        return current.semanticSearch(query);
    }

    @Override
    public Set<KnowledgeResourceId> resourceIds(EmbeddingModelIdentity space, KnowledgeSourceId sourceId) {
        return current.resourceIds(space, sourceId);
    }

    @Override
    public Optional<KnowledgeRevision> revisionOf(EmbeddingModelIdentity space, KnowledgeResourceId resourceId) {
        return current.revisionOf(space, resourceId);
    }

    @Override
    public Optional<KnowledgeIndexEntry> entry(EmbeddingModelIdentity space, KnowledgeChunkId chunkId) {
        return current.entry(space, chunkId);
    }

    @Override
    public List<KnowledgeIndexEntry> entriesOf(EmbeddingModelIdentity space, KnowledgeResourceId resourceId) {
        return current.entriesOf(space, resourceId);
    }

    @Override
    public void remove(KnowledgeResourceId resourceId) {
        current.remove(resourceId);
    }

    @Override
    public void removeSource(KnowledgeSourceId sourceId) {
        current.removeSource(sourceId);
    }

    @Override
    public void rebuild(Collection<KnowledgeIndexEntry> entries) {
        current.rebuild(entries);
    }

    @Override
    public String toString() {
        return "LiveKnowledgeIndex[" + directory + "]";
    }
}
