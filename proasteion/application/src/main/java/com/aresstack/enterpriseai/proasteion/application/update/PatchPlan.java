package com.aresstack.enterpriseai.proasteion.application.update;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The ordered, unambiguous chain of patches from the running jar to the highest reachable version, as
 * {@link PatchChainPlanner} found it. A single step is a chain of one.
 */
public final class PatchPlan {

    private final JarIdentity start;
    private final List<JarPatch> steps;
    private final List<File> unused;
    private final List<String> notices;

    PatchPlan(JarIdentity start, List<JarPatch> steps, List<File> unused, List<String> notices) {
        this.start = start;
        this.steps = Collections.unmodifiableList(new ArrayList<JarPatch>(steps));
        this.unused = Collections.unmodifiableList(new ArrayList<File>(unused));
        this.notices = Collections.unmodifiableList(new ArrayList<String>(notices));
    }

    /** The running jar the first step starts from. */
    public JarIdentity start() {
        return start;
    }

    /** What the last step produces. */
    public JarIdentity target() {
        return steps.get(steps.size() - 1).target();
    }

    public List<JarPatch> steps() {
        return steps;
    }

    /** Offered files that are not part of the chain (older versions, duplicates of a step). */
    public List<File> unused() {
        return unused;
    }

    /** Why a higher offered version is not reached (a missing link), for the preview; empty when nothing is. */
    public List<String> notices() {
        return notices;
    }

    /** Release notes of every step, newest last, each under its version. */
    public String releaseNotes() {
        StringBuilder text = new StringBuilder();
        for (JarPatch step : steps) {
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(step.targetVersion()).append(":\n")
                    .append(step.changes().isEmpty() ? "(keine Angaben im Update-Paket)" : step.changes());
        }
        return text.toString();
    }

    /** {@code enterprise-ai-client-<target>.jar} in {@code directory}. */
    public File targetFile(File directory) {
        return new File(directory, JarPatcher.JAR_PREFIX + target().version() + ".jar");
    }
}
