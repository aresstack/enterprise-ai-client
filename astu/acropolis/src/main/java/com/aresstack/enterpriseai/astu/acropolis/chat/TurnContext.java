package com.aresstack.enterpriseai.astu.acropolis.chat;

import com.aresstack.enterpriseai.astu.acropolis.chat.planning.AnswerPlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the client tells the model for one turn besides the history and the unchanged user message (#126): the
 * dynamic turn data in fixed {@link Section sections} and, kept apart, the technical client rules (tool routing).
 *
 * <p>The system message is composed in one place only, {@link #compose(String)}, in a fixed order that does not depend
 * on the order the parts were added in:
 * <ol>
 *   <li>dynamic turn data, section by section in {@link Section} order,</li>
 *   <li>the editable system prompt of the conversation,</li>
 *   <li>the client rules, last and marked as taking precedence, so a freely edited prompt cannot switch them off by
 *   accident.</li>
 * </ol>
 * Lives for one turn; never stored in the history. Immutable.
 */
public final class TurnContext {

    /** Header in front of the client rules in the composed system message. */
    static final String RULES_HEADER = "[Technische Regeln des Clients – gelten vorrangig vor dem System-Prompt]";

    /** The dynamic parts of a turn, in the order they are composed. */
    public enum Section {
        /** Instructions and memories of the chat project (#143). */
        PROJECT,
        /** The answer plan, or else the local analysis, or the note on a dictated message. */
        TURN_READING,
        /** Knowledge retrieved for this turn (RAG). */
        KNOWLEDGE,
        /** Notes about the retrieval: gaps, the searches made. */
        RETRIEVAL_NOTES,
        /** The attachments of the chat. */
        ATTACHMENTS,
        /** Bookmarked pages. */
        BOOKMARKS,
        /** Referenced earlier chats. */
        REFERENCES,
        /** Quoted passages of earlier answers. */
        QUOTES,
        /** Context a caller handed in as plain text without saying what it is. */
        ADDITIONAL
    }

    /**
     * Turns the answer plan of the turn into a routing rule (direct answer, application help, research), so the tool
     * choice follows the plan consistently. Implemented where the offered tools are known.
     */
    public interface PlanRouting {
        /** @return the rule for this plan, or {@code null} for none */
        String rule(AnswerPlan plan);
    }

    private final Map<Section, List<String>> sections;
    private final List<String> rules;
    private final PlanRouting routing;

    private TurnContext(Map<Section, List<String>> sections, List<String> rules) {
        this(sections, rules, null);
    }

    private TurnContext(Map<Section, List<String>> sections, List<String> rules, PlanRouting routing) {
        this.sections = sections;
        this.rules = rules;
        this.routing = routing;
    }

    /** This context with {@code routing} for {@link #routed(AnswerPlan)}; {@code null} removes it. */
    public TurnContext withPlanRouting(PlanRouting routing) {
        return new TurnContext(sections, rules, routing);
    }

    /**
     * This context with the routing rule for {@code plan} added to the client rules; unchanged without a plan, an
     * unknown plan, or without routing.
     */
    public TurnContext routed(AnswerPlan plan) {
        if (routing == null || plan == null || !plan.isKnown()) {
            return this;
        }
        return withRule(routing.rule(plan));
    }

    public static TurnContext empty() {
        return new TurnContext(new EnumMap<Section, List<String>>(Section.class), Collections.<String>emptyList());
    }

    /** A context with {@code text} as {@link Section#ADDITIONAL}; {@code null} or blank gives the empty one. */
    public static TurnContext of(String text) {
        return empty().with(Section.ADDITIONAL, text);
    }

    /** This context with {@code text} appended to {@code section}; {@code null} or blank changes nothing. */
    public TurnContext with(Section section, String text) {
        if (section == null) {
            throw new IllegalArgumentException("section must not be null");
        }
        if (isBlank(text)) {
            return this;
        }
        Map<Section, List<String>> copy = new EnumMap<Section, List<String>>(Section.class);
        for (Map.Entry<Section, List<String>> entry : sections.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        List<String> parts = new ArrayList<String>(sectionParts(section));
        parts.add(text.trim());
        copy.put(section, Collections.unmodifiableList(parts));
        return new TurnContext(copy, rules, routing);
    }

    /** This context with {@code text} appended to the client rules; {@code null} or blank changes nothing. */
    public TurnContext withRule(String text) {
        if (isBlank(text)) {
            return this;
        }
        List<String> copy = new ArrayList<String>(rules);
        copy.add(text.trim());
        return new TurnContext(sections, Collections.unmodifiableList(copy), routing);
    }

    /** The parts of {@code section}, in the order they were added. */
    public List<String> sectionParts(Section section) {
        List<String> parts = sections.get(section);
        return parts == null ? Collections.<String>emptyList() : parts;
    }

    /** The client rules, in the order they were added. */
    public List<String> rules() {
        return rules;
    }

    public boolean isEmpty() {
        return sections.isEmpty() && rules.isEmpty();
    }

    /** The dynamic turn data in section order, or {@code null} without any. */
    public String dynamicText() {
        StringBuilder text = new StringBuilder();
        for (Section section : Section.values()) {
            for (String part : sectionParts(section)) {
                append(text, part);
            }
        }
        return text.length() == 0 ? null : text.toString();
    }

    /** The client rules under their header, or {@code null} without any. */
    public String rulesText() {
        if (rules.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder(RULES_HEADER);
        for (String rule : rules) {
            text.append('\n').append(rule);
        }
        return text.toString();
    }

    /**
     * The system message of the turn: dynamic data, then {@code systemPrompt}, then the client rules.
     *
     * @param systemPrompt the editable system prompt of the conversation, or {@code null}
     * @return the composed text, or {@code null} when there is nothing at all
     */
    public String compose(String systemPrompt) {
        StringBuilder text = new StringBuilder();
        append(text, dynamicText());
        append(text, isBlank(systemPrompt) ? null : systemPrompt);
        append(text, rulesText());
        return text.length() == 0 ? null : text.toString();
    }

    private static void append(StringBuilder text, String part) {
        if (part == null) {
            return;
        }
        if (text.length() > 0) {
            text.append("\n\n");
        }
        text.append(part);
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    @Override
    public String toString() {
        String composed = compose(null);
        return composed == null ? "" : composed;
    }
}
