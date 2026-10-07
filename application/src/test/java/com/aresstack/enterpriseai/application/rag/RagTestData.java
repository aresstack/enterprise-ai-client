package com.aresstack.enterpriseai.application.rag;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingVector;
import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.embedding.api.EmbeddingInputs;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexException;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexPort;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeKeywordQuery;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSearchHit;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSemanticQuery;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Test-Doubles für die RAG-Tests: skriptbarer Embedding-Port und ein Index, der zählt oder ausfällt. */
final class RagTestData {

    private RagTestData() {
    }

    /** Liefert für jeden Text den vorgegebenen Vektor; unbekannte Texte oder {@link #failWith} lösen Fehler aus. */
    static final class ScriptedEmbeddingPort implements EmbeddingPort {

        private final EmbeddingModelIdentity identity;
        private final Map<String, float[]> vectors = new HashMap<String, float[]>();
        private final List<List<String>> calls = new ArrayList<List<String>>();
        private EmbeddingException failure;

        ScriptedEmbeddingPort(EmbeddingModelIdentity identity) {
            this.identity = identity;
        }

        ScriptedEmbeddingPort on(String text, float... values) {
            vectors.put(text, values);
            return this;
        }

        ScriptedEmbeddingPort failWith(EmbeddingException error) {
            this.failure = error;
            return this;
        }

        synchronized List<List<String>> calls() {
            return new ArrayList<List<String>>(calls);
        }

        @Override
        public EmbeddingModelIdentity modelIdentity() {
            return identity;
        }

        @Override
        public synchronized EmbeddingBatch embed(List<String> texts) {
            List<String> inputs = EmbeddingInputs.requireValid(texts);
            if (inputs.isEmpty()) {
                return EmbeddingBatch.empty(identity);
            }
            calls.add(inputs);
            if (failure != null) {
                throw failure;
            }
            List<EmbeddingVector> result = new ArrayList<EmbeddingVector>();
            for (String text : inputs) {
                float[] values = vectors.get(text);
                if (values == null) {
                    throw new EmbeddingException(EmbeddingFailureKind.REJECTED, "kein Vektor für '" + text + "'");
                }
                result.add(EmbeddingVector.of(identity, values));
            }
            return EmbeddingBatch.of(identity, inputs.size(), result);
        }
    }

    /** Leitet an einen Index weiter, zählt Suchen und lässt einzelne Pfade auf Wunsch ausfallen. */
    static final class ObservedIndex implements KnowledgeIndexPort {

        private final KnowledgeIndexPort delegate;
        volatile int keywordSearches;
        volatile int semanticSearches;
        volatile boolean failKeyword;
        volatile boolean failSemantic;

        ObservedIndex(KnowledgeIndexPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public void index(Collection<KnowledgeIndexEntry> entries) {
            delegate.index(entries);
        }

        @Override
        public void replace(EmbeddingModelIdentity space, KnowledgeResourceId resourceId,
                            Collection<KnowledgeIndexEntry> entries) {
            delegate.replace(space, resourceId, entries);
        }

        @Override
        public List<KnowledgeSearchHit> keywordSearch(KnowledgeKeywordQuery query) {
            keywordSearches++;
            if (failKeyword) {
                throw new KnowledgeIndexException("Volltextindex beschädigt");
            }
            return delegate.keywordSearch(query);
        }

        @Override
        public List<KnowledgeSearchHit> semanticSearch(KnowledgeSemanticQuery query) {
            semanticSearches++;
            if (failSemantic) {
                throw new KnowledgeIndexException("Vektorindex nicht lesbar");
            }
            return delegate.semanticSearch(query);
        }

        @Override
        public void remove(KnowledgeResourceId resourceId) {
            delegate.remove(resourceId);
        }

        @Override
        public void removeSource(KnowledgeSourceId sourceId) {
            delegate.removeSource(sourceId);
        }

        @Override
        public void rebuild(Collection<KnowledgeIndexEntry> entries) {
            delegate.rebuild(entries);
        }
    }
}
