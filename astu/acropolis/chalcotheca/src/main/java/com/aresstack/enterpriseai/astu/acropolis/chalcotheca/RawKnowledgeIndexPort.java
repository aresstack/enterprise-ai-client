package com.aresstack.enterpriseai.astu.acropolis.chalcotheca;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;

import java.util.List;
import java.util.Optional;

/**
 * Rohindex: geladene Dokumente als Ganzes, sofort lexikalisch (BM25) durchsuchbar, ohne Passagen und ohne Embeddings.
 * Er ist der schnelle Vorderpfad der Chat-Recherche (Quelle laden, Rohindex, Textfenster, Evidenzbewertung) und ein
 * lokaler Cache der geladenen Seiten; die semantische Aufbereitung im {@link KnowledgeIndexPort} läuft unabhängig.
 *
 * <p>Ein Treffer ({@link RawKnowledgeHit}) bringt nicht das ganze Dokument mit, sondern die Textfenster, die zur
 * Anfrage am besten passen ({@link RawKnowledgeQuery#windowsPerDocument()}). Je Ressource gibt es genau ein Dokument;
 * {@link #put} ersetzt es. Adapter sind threadsicher.
 */
public interface RawKnowledgeIndexPort {

    /** Nimmt das Dokument auf oder ersetzt das vorhandene derselben Ressource; danach sofort durchsuchbar. */
    void put(RawKnowledgeDocument document);

    /** Dokumente zur Anfrage, absteigend nach BM25, je mit den passendsten Textfenstern. */
    List<RawKnowledgeHit> search(RawKnowledgeQuery query);

    /** Das gespeicherte Dokument der Ressource. */
    Optional<RawKnowledgeDocument> get(KnowledgeResourceId id);

    /** Inhalts-Hash des gespeicherten Dokuments, ohne den Text zu laden; leer, wenn unbekannt. */
    Optional<String> contentHashOf(KnowledgeResourceId id);

    /** Revision des gespeicherten Dokuments, ohne den Text zu laden; leer, wenn unbekannt. */
    Optional<KnowledgeRevision> revisionOf(KnowledgeResourceId id);

    void remove(KnowledgeResourceId id);

    void removeSource(SourceAddress sourceId);
}
