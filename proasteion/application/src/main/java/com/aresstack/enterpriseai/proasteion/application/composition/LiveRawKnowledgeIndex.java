package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawKnowledgeDocument;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawKnowledgeHit;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawKnowledgeIndexPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawKnowledgeQuery;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.pinakes.LuceneKnowledgeIndex;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.net.NetworkServices;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Der Rohindex (Lucene, {@code <Indexverzeichnis>/raw}), der ein geändertes Indexverzeichnis ohne Neustart übernimmt
 * wie {@link LiveKnowledgeIndex}: {@link #apply} öffnet ihn im neuen Verzeichnis und schließt den alten.
 */
public final class LiveRawKnowledgeIndex implements RawKnowledgeIndexPort, ApplicationPorts.Reconfigurable,
        Closeable {

    private static final Logger LOG = Logger.getLogger(LiveRawKnowledgeIndex.class.getName());

    private Path directory;
    private volatile RawKnowledgeIndexPort current;

    LiveRawKnowledgeIndex(Path directory) {
        this.directory = directory;
        this.current = LuceneKnowledgeIndex.openRaw(directory);
    }

    @Override
    public void apply(AppConfig config, NetworkServices network) {
        Path next = config.knowledge().indexDirectory();
        RawKnowledgeIndexPort previous;
        synchronized (this) {
            if (next == null || next.equals(directory)) {
                return;
            }
            previous = current;
            current = LuceneKnowledgeIndex.openRaw(next);
            directory = next;
        }
        LOG.info("Rohindex jetzt unter " + next);
        close(previous);
    }

    @Override
    public void close() {
        close(current);
    }

    private static void close(RawKnowledgeIndexPort index) {
        if (index instanceof Closeable) {
            try {
                ((Closeable) index).close();
            } catch (java.io.IOException e) {
                LOG.warning("Rohindex ließ sich nicht schließen: " + e.getMessage());
            }
        }
    }

    @Override
    public void put(RawKnowledgeDocument document) {
        current.put(document);
    }

    @Override
    public List<RawKnowledgeHit> search(RawKnowledgeQuery query) {
        return current.search(query);
    }

    @Override
    public Optional<RawKnowledgeDocument> get(KnowledgeResourceId id) {
        return current.get(id);
    }

    @Override
    public Optional<String> contentHashOf(KnowledgeResourceId id) {
        return current.contentHashOf(id);
    }

    @Override
    public Optional<KnowledgeRevision> revisionOf(KnowledgeResourceId id) {
        return current.revisionOf(id);
    }

    @Override
    public void remove(KnowledgeResourceId id) {
        current.remove(id);
    }

    @Override
    public void removeSource(SourceAddress sourceId) {
        current.removeSource(sourceId);
    }

    @Override
    public String toString() {
        return "LiveRawKnowledgeIndex[" + directory + "]";
    }
}
