/**
 * Object- and event-centred statements from sentences (issue #108): {@link
 * com.aresstack.enterpriseai.astu.acropolis.textmining.statements.Mention}s of noun groups at their place in one
 * document version, {@link com.aresstack.enterpriseai.astu.acropolis.textmining.statements.Relation}s (properties,
 * order of events) and {@link com.aresstack.enterpriseai.astu.acropolis.textmining.statements.Event}s with participant
 * roles. An exchangeable {@link com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementExtractor}
 * proposes candidates per paragraph; {@link
 * com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementValidator} keeps only what is grounded in
 * the text (word spans, one sentence, a verbal predicate); an optional yes/no check confirms or rejects each claim.
 * Nothing is merged across documents, nothing is guessed for open pronouns, and a proposed base form stays a
 * proposal.
 */
package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;
