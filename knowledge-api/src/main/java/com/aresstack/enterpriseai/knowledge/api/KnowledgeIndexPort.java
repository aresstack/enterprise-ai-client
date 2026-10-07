package com.aresstack.enterpriseai.knowledge.api;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Neutraler Port für die durchsuchbare Projektion der Wissensbasis: Volltext- und semantische Suche hinter einer
 * Schnittstelle, ohne Lucene- oder Vektorstore-Typen.
 *
 * <p>Der Index ist ausschließlich eine <b>wiederherstellbare Projektion</b>. Kanonisch sind die
 * {@code KnowledgeDocument}s der Quellen samt ihrer berechneten Chunks und Vektoren; {@link #rebuild} baut den
 * Index daraus vollständig neu auf. Kein Aufrufer darf Daten nur im Index halten.
 *
 * <p>Namespaces: Jeder Eintrag gehört über die {@link EmbeddingModelIdentity} seines Vektors zu genau einem
 * Namespace. Suchen laufen immer in genau einem Namespace; Vektoren verschiedener Welten werden nie verglichen.
 *
 * <p>Vertrag für jede Implementierung (geprüft durch {@code KnowledgeIndexPortContractTest} in den Testfixtures):
 * <ul>
 *   <li>Schreiben ist idempotent je Chunk-ID und Namespace; erneutes Indexieren erzeugt keine Duplikate.</li>
 *   <li>Änderungen sind nach Rückkehr der Methode sichtbar (kein separates Commit).</li>
 *   <li>Trefferlisten sind nach {@link KnowledgeSearchHit#byRelevance()} geordnet und höchstens
 *       {@code maxResults} lang; ein leerer oder unbekannter Namespace liefert eine leere Liste.</li>
 *   <li>Fehler beim Lesen/Schreiben werden als {@link KnowledgeIndexException} gemeldet.</li>
 * </ul>
 *
 * <p>Design übernommen aus askai-java8 {@code SemanticKnowledgeIndex}; Projekt-Scoping entfällt (eine Instanz je
 * Wissensbasis), Captures heißen hier Ressourcen.
 */
public interface KnowledgeIndexPort {

    /** Upsert in die jeweiligen Namespaces, idempotent je Chunk-ID. */
    void index(Collection<KnowledgeIndexEntry> entries);

    /**
     * Ersetzt alle Chunks einer Ressource im Namespace {@code space} atomar durch {@code entries} (leer = nur
     * entfernen). So verdrängt eine neue Revision die alte, statt dass verwaiste Chunks liegen bleiben.
     *
     * @throws IllegalArgumentException wenn ein Eintrag zu einer anderen Ressource oder einem anderen Namespace gehört
     */
    void replace(EmbeddingModelIdentity space, KnowledgeResourceId resourceId, Collection<KnowledgeIndexEntry> entries);

    /** Volltextsuche (BM25) in einem Namespace. */
    List<KnowledgeSearchHit> keywordSearch(KnowledgeKeywordQuery query);

    /** Exakte Cosine-Suche in dem Namespace des Anfragevektors. */
    List<KnowledgeSearchHit> semanticSearch(KnowledgeSemanticQuery query);

    /**
     * Ressourcen einer Quelle, von denen im Namespace {@code space} mindestens ein Chunk liegt – die Grundlage, um
     * verschwundene Seiten aus dem Index zu entfernen oder zu prüfen, ob ein Dokument indexiert ist. Unveränderliche
     * Menge; leer für unbekannte Namespaces oder Quellen. Spiegelt {@link #replace}, {@link #remove} und
     * {@link #removeSource} sofort wider.
     */
    Set<KnowledgeResourceId> resourceIds(EmbeddingModelIdentity space, KnowledgeSourceId sourceId);

    /**
     * Die im Namespace {@code space} gespeicherte Revision einer Ressource, damit ein Index-Lauf unveränderte
     * Ressourcen überspringen kann. Leer, wenn kein Chunk der Ressource im Namespace liegt – oder wenn ihre Chunks
     * verschiedene Revisionen tragen (nur über teilweise {@link #index}-Aufrufe möglich); dann gilt die Ressource
     * als nicht sauber indexiert und ist neu zu indexieren.
     */
    Optional<KnowledgeRevision> revisionOf(EmbeddingModelIdentity space, KnowledgeResourceId resourceId);

    /** Entfernt alle Chunks der Ressource aus allen Namespaces. */
    void remove(KnowledgeResourceId resourceId);

    /** Entfernt alle Chunks einer Quelle aus allen Namespaces (z. B. wenn die Quelle aus der Konfiguration fällt). */
    void removeSource(KnowledgeSourceId sourceId);

    /**
     * Verwirft den gesamten Index (alle Namespaces) und baut ihn aus {@code entries} neu auf – Wiederherstellung
     * nach Verlust, Beschädigung oder Schemawechsel, ohne neu zu embedden. {@code rebuild(leer)} leert den Index.
     */
    void rebuild(Collection<KnowledgeIndexEntry> entries);
}
