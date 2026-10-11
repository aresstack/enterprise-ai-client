package com.aresstack.enterpriseai.research.api;

/**
 * Lesender Zugriff auf das Konzeptpapier (Konzeptbaum mit Inseln) eines Research-Projekts, etwa für die
 * Weidezaun-Anker aus dem Konzept. Implementiert in research-concept ({@code ConceptBranchService}).
 */
public interface ResearchConceptReader {

    /** Das aktuelle Konzeptdokument als JSON (leeres Dokument, solange es keins gibt). */
    String currentConceptJson();
}
