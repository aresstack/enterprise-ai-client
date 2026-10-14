/**
 * Text mining on documents (issue #107): small, source-neutral extractors that turn the canonical text of a
 * {@link com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument} into structured findings with provenance
 * (character spans in that text). Findings are kept apart from any display: a reader may mark them, a
 * {@link com.aresstack.enterpriseai.astu.acropolis.textmining.TextFindingSink} may store them. The first extractor
 * finds acronyms, technical identifiers and the long forms a document states itself; nothing is taken from model
 * knowledge and nothing is merged across documents.
 */
package com.aresstack.enterpriseai.astu.acropolis.textmining;
