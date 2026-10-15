package com.aresstack.enterpriseai.astu.acropolis.chat.project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The project context valid for exactly one new turn of a chat: which project (if any), its instructions and
 * memories, and the scope under which knowledge context may be cached or restored. Resolved per turn, never stored.
 *
 * <p>The scope changes with every move of the chat (project and move generation), so a context that was valid
 * under an earlier assignment never matches again, also after moving back. An {@linkplain #unresolved() unresolved}
 * context fails closed: no project information and a scope that matches nothing.
 */
public final class EffectiveProjectContext {

    /** Scope of a chat that was never assigned to a project (also every chat saved before projects existed). */
    public static final String NONE_SCOPE = "none";

    private final String scope;
    private final String projectId;
    private final String projectName;
    private final String instructions;
    private final List<String> memories;
    private final boolean resolved;

    private EffectiveProjectContext(String scope, String projectId, String projectName, String instructions,
                                    List<String> memories, boolean resolved) {
        this.scope = scope;
        this.projectId = projectId;
        this.projectName = projectName;
        this.instructions = instructions == null ? "" : instructions.trim();
        List<String> kept = new ArrayList<String>();
        for (String memory : memories) {
            if (memory != null && !memory.trim().isEmpty()) {
                kept.add(memory.trim());
            }
        }
        this.memories = Collections.unmodifiableList(kept);
        this.resolved = resolved;
    }

    /** A chat without project that was never moved. */
    public static EffectiveProjectContext none() {
        return new EffectiveProjectContext(NONE_SCOPE, null, "", "", Collections.<String>emptyList(), true);
    }

    /** The assignment could not be determined: no project information, and a scope that matches no other. */
    public static EffectiveProjectContext unresolved() {
        return new EffectiveProjectContext("", null, "", "", Collections.<String>emptyList(), false);
    }

    /** A chat without project after {@code generation} moves (generation 0 is {@link #none()}). */
    static EffectiveProjectContext withoutProject(long generation) {
        return generation == 0L ? none() : new EffectiveProjectContext(NONE_SCOPE + "@" + generation, null, "", "",
                Collections.<String>emptyList(), true);
    }

    static EffectiveProjectContext of(ChatProjectRecord project, long generation) {
        return new EffectiveProjectContext(project.getId() + "@" + generation, project.getId(), project.getName(),
                project.getInstructions(), project.getMemories(), true);
    }

    /**
     * The scope key; two contexts allow the same reuse of knowledge context only if {@link #sameScope} holds.
     * Empty for an unresolved context.
     */
    public String scope() {
        return scope;
    }

    /** Whether knowledge context saved or cached under {@code otherScope} may be reused now (fail closed). */
    public boolean sameScope(String otherScope) {
        String other = otherScope == null || otherScope.isEmpty() ? NONE_SCOPE : otherScope;
        return resolved && scope.equals(other);
    }

    /** The project id, or {@code null} without project. */
    public String projectId() {
        return projectId;
    }

    public String projectName() {
        return projectName;
    }

    public String instructions() {
        return instructions;
    }

    public List<String> memories() {
        return memories;
    }

    public boolean isResolved() {
        return resolved;
    }

    /**
     * The system part for this turn ({@code null} when the project has neither instructions nor memories, or without
     * project). Names the project so the model can tell project rules from the general system prompt.
     */
    public String systemContext() {
        if (projectId == null || instructions.isEmpty() && memories.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        text.append("Dieser Chat gehört zum Projekt \"").append(projectName).append("\".");
        if (!instructions.isEmpty()) {
            text.append("\n\nProjektanweisungen (gelten für jede Antwort in diesem Projekt):\n").append(instructions);
        }
        if (!memories.isEmpty()) {
            text.append("\n\nProjekterinnerungen:");
            for (String memory : memories) {
                text.append("\n- ").append(memory.replace('\n', ' '));
            }
        }
        return text.toString();
    }
}
