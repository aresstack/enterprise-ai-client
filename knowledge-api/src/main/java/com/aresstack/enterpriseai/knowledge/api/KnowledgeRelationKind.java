package com.aresstack.enterpriseai.knowledge.api;

/**
 * Art einer Beziehung zwischen zwei Chunks des Wissensindex. Beziehungen sind typisierte Kanten, keine
 * Subklassen-Hierarchie (Vorbild corenth {@code CodeRelation(kind, from, to)}); neue Arten sind additiv.
 */
public enum KnowledgeRelationKind {

    /** Semantisch ähnlich: Cosine der gespeicherten Chunk-Vektoren über der Schwelle; trägt den Score. */
    SIMILAR_TO,

    /** Ressource des Ziels ist ein Verweisziel der Ausgangsressource (Wiki-/Confluence-Link). */
    LINKS_TO,

    /** Ziel ist der erste Chunk der Elternressource ({@code KnowledgeResource.parentId()}). */
    PARENT,

    /** Ziel ist der folgende Chunk derselben Ressource ({@code ordinal + 1}). */
    NEXT,

    /** Ziel ist der vorige Chunk derselben Ressource ({@code ordinal - 1}). */
    PREVIOUS,

    /** Ziel steht in derselben Ressource unter derselben Überschriftenkette ({@code headingPath}). */
    SAME_SECTION
}
