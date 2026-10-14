package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import com.aresstack.enterpriseai.astu.json.Json;
import com.aresstack.enterpriseai.astu.json.JsonException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Evaluation of the sentence analysis against annotated sentences (issue #108): which mentions, events, roles,
 * properties and orders a sentence states, and which role claims it must never yield ("the receipt releases the
 * payment"). Terms are compared as search values ({@link TermNormalizer#fromLemma}); a role may name alternatives
 * ({@code RECIPIENT|AFFECTED_OBJECT}) where German leaves it open. The built-in set ({@link #builtIn()}) is invented;
 * real documents are evaluated outside the repository with the same format.
 */
public final class StatementEvaluation {

    /** Resource with the built-in annotated sentences. */
    static final String BUILT_IN = "evaluation-de.json";

    /** One annotated sentence. */
    public static final class Case {
        private final String id;
        private final List<String> phenomena;
        private final String sentence;
        private final List<GoldEvent> events;
        private final Set<String> relations;
        private final Set<String> orders;
        private final Set<String> mentions;
        private final List<String> forbidden;

        Case(String id, List<String> phenomena, String sentence, List<GoldEvent> events, Set<String> relations,
             Set<String> orders, Set<String> mentions, List<String> forbidden) {
            this.id = id;
            this.phenomena = phenomena;
            this.sentence = sentence;
            this.events = events;
            this.relations = relations;
            this.orders = orders;
            this.mentions = mentions;
            this.forbidden = forbidden;
        }

        public String id() {
            return id;
        }

        public List<String> phenomena() {
            return phenomena;
        }

        public String sentence() {
            return sentence;
        }

        List<GoldEvent> events() {
            return events;
        }

        Set<String> relations() {
            return relations;
        }

        Set<String> orders() {
            return orders;
        }

        Set<String> mentions() {
            return mentions;
        }

        List<String> forbidden() {
            return forbidden;
        }
    }

    /** An expected event: predicate, role arguments and qualifiers. */
    static final class GoldEvent {
        final String predicate;
        final List<GoldArgument> arguments;
        final Set<String> qualifiers;

        GoldEvent(String predicate, List<GoldArgument> arguments, Set<String> qualifiers) {
            this.predicate = predicate;
            this.arguments = arguments;
            this.qualifiers = qualifiers;
        }
    }

    /** An expected argument; {@code roles} holds the accepted alternatives. */
    static final class GoldArgument {
        final Set<ParticipantRole> roles;
        final String term;

        GoldArgument(Set<ParticipantRole> roles, String term) {
            this.roles = roles;
            this.term = term;
        }

        boolean matches(ParticipantRole role, String value) {
            return roles.contains(role) && term.equals(value);
        }
    }

    /** Counts of one evaluation run. */
    public static final class Report {
        private final Map<String, int[]> counts = new TreeMap<String, int[]>();
        private final List<String> forbiddenHits = new ArrayList<String>();
        private final Set<String> missed = new LinkedHashSet<String>();
        private int cases;
        private int failedCases;

        private void count(String metric, int truePositives, int predicted, int expected) {
            int[] value = counts.get(metric);
            if (value == null) {
                value = new int[3];
                counts.put(metric, value);
            }
            value[0] += truePositives;
            value[1] += predicted;
            value[2] += expected;
        }

        public int cases() {
            return cases;
        }

        /** Cases whose analysis failed (model unreachable, unreadable answer). */
        public int failedCases() {
            return failedCases;
        }

        /** Precision of {@code metric} (mentions, events, roles, relations, orders, qualifiers); 1 if nothing found. */
        public double precision(String metric) {
            int[] value = counts.get(metric);
            return value == null || value[1] == 0 ? 1.0 : (double) value[0] / value[1];
        }

        /** Recall of {@code metric}; 1 if nothing was expected. */
        public double recall(String metric) {
            int[] value = counts.get(metric);
            return value == null || value[2] == 0 ? 1.0 : (double) value[0] / value[2];
        }

        /** Role claims the sentences rule out, as "case: predicate ROLE=term". */
        public List<String> forbiddenHits() {
            return Collections.unmodifiableList(forbiddenHits);
        }

        /** Role claims not in the annotation (false event assignments). */
        public int wrongRoleClaims() {
            int[] value = counts.get("roles");
            return value == null ? 0 : value[1] - value[0];
        }

        /** Ids of cases with a missed or wrong role. */
        public Set<String> casesWithRoleErrors() {
            return Collections.unmodifiableSet(missed);
        }

        @Override
        public String toString() {
            StringBuilder text = new StringBuilder("Satzanalyse: " + cases + " Sätze, " + failedCases
                    + " fehlgeschlagen\n");
            for (Map.Entry<String, int[]> entry : counts.entrySet()) {
                int[] value = entry.getValue();
                text.append(String.format(Locale.ROOT, "  %-11s Präzision %.2f  Recall %.2f  (%d richtig, %d "
                                + "gefunden, %d erwartet)%n", entry.getKey(), precision(entry.getKey()),
                        recall(entry.getKey()), value[0], value[1], value[2]));
            }
            text.append("  falsche Rollen: ").append(wrongRoleClaims()).append(", verbotene Aussagen: ")
                    .append(forbiddenHits.size()).append(forbiddenHits.isEmpty() ? "" : " " + forbiddenHits)
                    .append('\n');
            text.append("  Sätze mit Rollenfehlern: ").append(missed);
            return text.toString();
        }
    }

    private StatementEvaluation() {
    }

    /** The invented annotated sentences shipped with the analysis. */
    public static List<Case> builtIn() {
        InputStream in = StatementEvaluation.class.getResourceAsStream(BUILT_IN);
        if (in == null) {
            throw new IllegalStateException("built-in evaluation set missing");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                bytes.write(buffer, 0, read);
            }
            return parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("built-in evaluation set unreadable", e);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // Nothing left to read.
            }
        }
    }

    /**
     * Read annotated sentences: {@code {"cases": [{"id", "phenomena", "sentence", "events": [{"predicate", "args":
     * ["ROLE=term", "ROLE|ROLE=term"], "q": ["NEGATED"]}], "relations": ["subject=value"], "order": ["before<after"],
     * "mentions": ["term"], "forbidden": ["predicate ROLE=term"]}]}}.
     *
     * @throws IllegalArgumentException if the set is malformed
     */
    public static List<Case> parse(String json) {
        Map<String, Object> root;
        try {
            root = Json.parseObject(json);
        } catch (JsonException e) {
            throw new IllegalArgumentException("evaluation set is no JSON object: " + e.getMessage(), e);
        }
        List<Case> cases = new ArrayList<Case>();
        Set<String> ids = new LinkedHashSet<String>();
        for (Object entry : Json.array(root, "cases")) {
            if (!(entry instanceof Map)) {
                throw new IllegalArgumentException("case is no object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) entry;
            String id = Json.string(object, "id");
            String sentence = Json.string(object, "sentence");
            if (id == null || sentence == null || !ids.add(id)) {
                throw new IllegalArgumentException("case without id or sentence, or duplicate id: " + id);
            }
            List<GoldEvent> events = new ArrayList<GoldEvent>();
            Set<String> mentions = new LinkedHashSet<String>(terms(Json.array(object, "mentions")));
            for (Object event : Json.array(object, "events")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> fields = event instanceof Map ? (Map<String, Object>) event : null;
                String predicate = TermNormalizer.fromLemma(Json.string(fields, "predicate"));
                if (predicate.isEmpty()) {
                    throw new IllegalArgumentException(id + ": event without predicate");
                }
                List<GoldArgument> arguments = new ArrayList<GoldArgument>();
                for (String argument : strings(Json.array(fields, "args"))) {
                    GoldArgument parsed = argument(id, argument);
                    arguments.add(parsed);
                    mentions.add(parsed.term);
                }
                Set<String> qualifiers = new LinkedHashSet<String>(strings(Json.array(fields, "q")));
                for (String qualifier : qualifiers) {
                    if (!Arrays.asList("NEGATED", "MODAL", "CONDITIONAL").contains(qualifier)) {
                        throw new IllegalArgumentException(id + ": unknown qualifier " + qualifier);
                    }
                }
                events.add(new GoldEvent(predicate, arguments, qualifiers));
            }
            Set<String> relations = new LinkedHashSet<String>();
            for (String relation : strings(Json.array(object, "relations"))) {
                String[] parts = pair(id, relation, "=");
                String subject = TermNormalizer.fromLemma(parts[0]);
                relations.add(subject + "=" + parts[1]);
                mentions.add(subject);
            }
            Set<String> orders = new LinkedHashSet<String>();
            for (String order : strings(Json.array(object, "order"))) {
                String[] parts = pair(id, order, "<");
                orders.add(TermNormalizer.fromLemma(parts[0]) + "<" + parts[1]);
            }
            List<String> forbidden = new ArrayList<String>();
            for (String claim : strings(Json.array(object, "forbidden"))) {
                int space = claim.indexOf(' ');
                if (space < 0) {
                    throw new IllegalArgumentException(id + ": forbidden claim needs 'predicate ROLE=term'");
                }
                GoldArgument argument = argument(id, claim.substring(space + 1));
                for (ParticipantRole role : argument.roles) {
                    forbidden.add(TermNormalizer.fromLemma(claim.substring(0, space)) + " " + role + "="
                            + argument.term);
                }
            }
            cases.add(new Case(id, strings(Json.array(object, "phenomena")), sentence,
                    Collections.unmodifiableList(events), relations, orders, mentions, forbidden));
        }
        return Collections.unmodifiableList(cases);
    }

    /**
     * Analyse every case with {@code analysis} and compare. A case whose analysis throws or reports failed units
     * counts as failed and with all its expectations missed.
     */
    public static Report evaluate(List<Case> cases, Function<Case, DocumentStatements> analysis) {
        Report report = new Report();
        for (Case gold : cases) {
            report.cases++;
            DocumentStatements found;
            try {
                found = analysis.apply(gold);
            } catch (RuntimeException failed) {
                found = null;
            }
            if (found == null || found.failedUnits() > 0) {
                report.failedCases++;
                found = null;
            }
            compare(gold, found, report);
        }
        return report;
    }

    private static void compare(Case gold, DocumentStatements found, Report report) {
        List<Mention> mentions = found == null ? Collections.<Mention>emptyList() : found.mentions();
        List<Event> events = found == null ? Collections.<Event>emptyList() : found.events();
        List<Relation> relations = found == null ? Collections.<Relation>emptyList() : found.relations();

        Set<String> foundMentions = new LinkedHashSet<String>();
        for (Mention mention : mentions) {
            foundMentions.add(mention.normalized());
        }
        report.count("mentions", intersection(foundMentions, gold.mentions()), foundMentions.size(),
                gold.mentions().size());

        List<GoldEvent> openEvents = new ArrayList<GoldEvent>(gold.events());
        int eventHits = 0;
        int qualifierHits = 0;
        for (Event event : events) {
            GoldEvent match = null;
            for (GoldEvent candidate : openEvents) {
                if (candidate.predicate.equals(event.normalized())) {
                    match = candidate;
                    break;
                }
            }
            if (match != null) {
                openEvents.remove(match);
                eventHits++;
                if (qualifiers(event.context()).equals(match.qualifiers)) {
                    qualifierHits++;
                }
            }
        }
        report.count("events", eventHits, events.size(), gold.events().size());
        report.count("qualifiers", qualifierHits, eventHits, eventHits);

        List<String[]> open = new ArrayList<String[]>();
        List<GoldArgument> openArguments = new ArrayList<GoldArgument>();
        int expectedRoles = 0;
        for (GoldEvent event : gold.events()) {
            for (GoldArgument argument : event.arguments) {
                open.add(new String[]{event.predicate});
                openArguments.add(argument);
                expectedRoles++;
            }
        }
        int predictedRoles = 0;
        int roleHits = 0;
        boolean roleError = false;
        for (Event event : events) {
            for (Participant participant : event.participants()) {
                predictedRoles++;
                String term = participant.mention().normalized();
                int hit = -1;
                for (int i = 0; i < openArguments.size(); i++) {
                    if (open.get(i)[0].equals(event.normalized())
                            && openArguments.get(i).matches(participant.role(), term)) {
                        hit = i;
                        break;
                    }
                }
                if (hit >= 0) {
                    open.remove(hit);
                    openArguments.remove(hit);
                    roleHits++;
                } else {
                    roleError = true;
                }
                String claim = event.normalized() + " " + participant.role() + "=" + term;
                if (gold.forbidden().contains(claim)) {
                    report.forbiddenHits.add(gold.id() + ": " + claim);
                }
            }
        }
        report.count("roles", roleHits, predictedRoles, expectedRoles);
        if (roleError || roleHits < expectedRoles) {
            report.missed.add(gold.id());
        }

        Set<String> foundRelations = new LinkedHashSet<String>();
        Set<String> foundOrders = new LinkedHashSet<String>();
        for (Relation relation : relations) {
            if (relation.kind() == RelationKind.BEFORE) {
                foundOrders.add(relation.subjectEvent().normalized() + "<" + relation.objectEvent().normalized());
            } else {
                foundRelations.add(relation.subjectMention().normalized() + "=" + relation.normalized());
            }
        }
        report.count("relations", intersection(foundRelations, gold.relations()), foundRelations.size(),
                gold.relations().size());
        report.count("orders", intersection(foundOrders, gold.orders()), foundOrders.size(), gold.orders().size());
    }

    private static Set<String> qualifiers(StatementContext context) {
        Set<String> qualifiers = new LinkedHashSet<String>();
        if (context.negation() != null) {
            qualifiers.add("NEGATED");
        }
        if (context.modality() != null) {
            qualifiers.add("MODAL");
        }
        if (context.condition() != null) {
            qualifiers.add("CONDITIONAL");
        }
        return qualifiers;
    }

    private static int intersection(Set<String> found, Set<String> expected) {
        int hits = 0;
        for (String value : found) {
            if (expected.contains(value)) {
                hits++;
            }
        }
        return hits;
    }

    private static GoldArgument argument(String id, String text) {
        String[] parts = pair(id, text, "=");
        Set<ParticipantRole> roles = EnumSet.noneOf(ParticipantRole.class);
        for (String role : parts[0].split("\\|")) {
            ParticipantRole parsed = ParticipantRole.parse(role.trim());
            if (parsed == null) {
                throw new IllegalArgumentException(id + ": unknown role " + role);
            }
            roles.add(parsed);
        }
        return new GoldArgument(roles, parts[1]);
    }

    /** "left=right" split at {@code separator}: the left side as written, the right side as search value. */
    private static String[] pair(String id, String text, String separator) {
        int at = text.indexOf(separator);
        if (at <= 0 || at == text.length() - 1) {
            throw new IllegalArgumentException(id + ": expected 'a" + separator + "b' but got '" + text + "'");
        }
        String left = text.substring(0, at).trim();
        String right = TermNormalizer.fromLemma(text.substring(at + 1));
        return new String[]{left, right};
    }

    private static List<String> terms(List<Object> values) {
        List<String> terms = new ArrayList<String>();
        for (String value : strings(values)) {
            terms.add(TermNormalizer.fromLemma(value));
        }
        return terms;
    }

    private static List<String> strings(List<Object> values) {
        List<String> strings = new ArrayList<String>();
        for (Object value : values) {
            if (value instanceof String) {
                strings.add((String) value);
            }
        }
        return Collections.unmodifiableList(strings);
    }
}
