package com.aresstack.enterpriseai.astu.acropolis.textmining;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an {@link AcronymAnalysis} into storable findings: one {@code ACRONYM_CANDIDATE} per acronym (role
 * {@code MENTIONS}), one {@code ACRONYM_DEFINITION} per distinct long form a document states (role {@code DEFINES})
 * and one {@code TECHNICAL_IDENTIFIER_CANDIDATE} per identifier. Positions are character offsets in the canonical text
 * of the analysed version ({@code KnowledgeDocument.text()}); they are only valid for that version.
 */
public final class AcronymFindings {

    public static final String ACRONYM_CANDIDATE = "ACRONYM_CANDIDATE";
    public static final String ACRONYM_DEFINITION = "ACRONYM_DEFINITION";
    public static final String TECHNICAL_IDENTIFIER_CANDIDATE = "TECHNICAL_IDENTIFIER_CANDIDATE";
    public static final String MENTIONS = "MENTIONS";
    public static final String DEFINES = "DEFINES";
    /** Most positions kept per finding; the count is always complete. */
    public static final int MAX_POSITIONS = 50;
    /** Version of the payload fields below. */
    public static final int SCHEMA_VERSION = 1;

    private AcronymFindings() {
    }

    /** The types these findings use. */
    public static List<TextFindingType> types() {
        return Collections.unmodifiableList(Arrays.asList(
                new TextFindingType(ACRONYM_CANDIDATE, "Spelled like an acronym in this resource; meaning unknown "
                        + "unless the same resource states a long form.", SCHEMA_VERSION),
                new TextFindingType(ACRONYM_DEFINITION, "Long form of an acronym stated literally in this resource "
                        + "(e.g. long form (SHORT)); valid for this resource only.", SCHEMA_VERSION),
                new TextFindingType(TECHNICAL_IDENTIFIER_CANDIDATE, "Token spelled like a technical identifier "
                        + "(letters and digits mixed, underscores).", SCHEMA_VERSION)));
    }

    public static List<TextFinding> of(AcronymAnalysis analysis, String text) {
        List<TextFinding> findings = new ArrayList<TextFinding>();
        for (AcronymFinding finding : analysis.findings()) {
            if (finding.kind() == TermKind.ACRONYM) {
                findings.add(candidate(finding, text));
                Map<String, AcronymDefinition> distinct = new LinkedHashMap<String, AcronymDefinition>();
                Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
                for (AcronymDefinition definition : finding.definitions()) {
                    String key = definition.normalizedLongForm();
                    if (!distinct.containsKey(key)) {
                        distinct.put(key, definition);
                        counts.put(key, 0);
                    }
                    counts.put(key, counts.get(key) + 1);
                }
                for (Map.Entry<String, AcronymDefinition> entry : distinct.entrySet()) {
                    findings.add(definition(finding, entry.getValue(), counts.get(entry.getKey()),
                            distinct.size() > 1, text));
                }
            } else {
                findings.add(identifier(finding, text));
            }
        }
        return findings;
    }

    private static TextFinding candidate(AcronymFinding finding, String text) {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("shortForm", finding.shortForm());
        fields.put("normalized", finding.normalized());
        fields.put("surfaceForms", finding.surfaceForms());
        fields.put("kind", finding.kind().name());
        fields.put("status", finding.status().name());
        fields.put("occurrences", finding.occurrences().size());
        fields.put("positions", positions(finding.occurrences()));
        fields.put("longForms", finding.longForms());
        fields.put("conflictingLongForms", finding.hasConflictingLongForms());
        TextSpan first = finding.occurrences().get(0);
        return new TextFinding(ACRONYM_CANDIDATE, MENTIONS, fields, first, first.of(text));
    }

    private static TextFinding definition(AcronymFinding finding, AcronymDefinition definition, int count,
                                          boolean conflicting, String text) {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("shortForm", definition.shortForm());
        fields.put("normalized", finding.normalized());
        fields.put("longForm", definition.longForm());
        fields.put("normalizedLongForm", definition.normalizedLongForm());
        fields.put("pattern", definition.pattern().name());
        fields.put("status", FindingStatus.EVIDENCED.name());
        fields.put("statements", count);
        fields.put("conflictingLongForms", conflicting);
        return new TextFinding(ACRONYM_DEFINITION, DEFINES, fields, definition.span(),
                definition.sentenceSpan().of(text));
    }

    private static TextFinding identifier(AcronymFinding finding, String text) {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("identifier", finding.shortForm());
        fields.put("normalized", finding.normalized());
        fields.put("status", FindingStatus.CANDIDATE.name());
        fields.put("occurrences", finding.occurrences().size());
        fields.put("positions", positions(finding.occurrences()));
        TextSpan first = finding.occurrences().get(0);
        return new TextFinding(TECHNICAL_IDENTIFIER_CANDIDATE, MENTIONS, fields, first, first.of(text));
    }

    private static List<List<Integer>> positions(List<TextSpan> spans) {
        List<List<Integer>> positions = new ArrayList<List<Integer>>();
        for (TextSpan span : spans) {
            if (positions.size() == MAX_POSITIONS) {
                break;
            }
            positions.add(Arrays.asList(span.start(), span.end()));
        }
        return positions;
    }
}
