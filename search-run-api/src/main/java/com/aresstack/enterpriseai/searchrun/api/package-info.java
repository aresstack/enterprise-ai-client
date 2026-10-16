/**
 * Port for the persistent knowledge search run log (#153). A {@link com.aresstack.enterpriseai.searchrun.api.KnowledgeSearchRun}
 * records what one live search of the internal knowledge sources actually received; its
 * {@link com.aresstack.enterpriseai.searchrun.api.KnowledgeCandidate}s are immutable discovery facts identified by
 * {@code sourceId + resourceId}, never by a URL. Everything that happens to a candidate later (scored, loaded, deepened,
 * judged relevant, used) is a separate, append-only {@link com.aresstack.enterpriseai.searchrun.api.CandidateEvent}.
 * A candidate is not a source and never becomes a citation by being recorded.
 */
package com.aresstack.enterpriseai.searchrun.api;
