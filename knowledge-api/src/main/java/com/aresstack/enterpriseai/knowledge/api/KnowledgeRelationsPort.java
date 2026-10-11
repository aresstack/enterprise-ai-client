package com.aresstack.enterpriseai.knowledge.api;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkId;

import java.util.List;

/**
 * Port für die Nachbarschaft eines Chunks im Wissensbestand: ähnliche Absätze, Nachbarabsätze, gleicher
 * Abschnitt, Elternseite, Verweise. Getrennt vom {@link KnowledgeIndexPort}, damit Volltext/Vektorsuche nicht mit
 * Graph-Semantik vermischt werden; darunter kann eine aus dem Index abgeleitete Implementierung liegen (heute) oder
 * später ein Kantenspeicher, ohne dass der RAG-Use-Case davon etwas weiß.
 *
 * <p>Vertrag: Der Ausgangschunk selbst ist nie Ziel; jedes Ziel erscheint höchstens einmal (die erste Kante in der
 * Reihenfolge von {@link KnowledgeRelationKind} gewinnt); je Art höchstens {@link KnowledgeRelationQuery#maxPerKind()}
 * Kanten, {@code SIMILAR_TO} absteigend nach Score. Ein unbekannter Chunk liefert eine leere Liste. Lesefehler
 * werden als {@link KnowledgeIndexException} gemeldet.
 */
public interface KnowledgeRelationsPort {

    List<KnowledgeRelation> neighbors(KnowledgeChunkId chunk, KnowledgeRelationQuery query);
}
