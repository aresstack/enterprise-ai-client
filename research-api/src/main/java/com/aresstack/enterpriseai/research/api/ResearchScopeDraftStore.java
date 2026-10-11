package com.aresstack.enterpriseai.research.api;

import com.aresstack.enterpriseai.research.domain.scope.ResearchScopeDraft;

import java.io.IOException;

/**
 * Port der versionierten Ablage des {@link ResearchScopeDraft} (in askai-java8 die Klasse
 * {@code FileResearchScopeDraftStore}, hier hinter einem Port, damit der {@code ResearchScopeCoordinator} keinen
 * Adapter kennt). Die Ablage führt den Revisionszähler: {@link #save} speichert immer die NÄCHSTE Revision.
 */
public interface ResearchScopeDraftStore {

    /** Typisiert, wirft nie: ein beschädigter Entwurf muss BLOCKIEREN, nicht still leer starten. */
    ScopeDraftLoadResult load();

    /** Speichert die nächste Revision und liefert den Entwurf genau so, wie er abgelegt wurde. */
    ResearchScopeDraft save(ResearchScopeDraft draft) throws IOException;
}
