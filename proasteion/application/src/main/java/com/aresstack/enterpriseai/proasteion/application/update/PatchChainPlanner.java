package com.aresstack.enterpriseai.proasteion.application.update;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the chain of incremental patches from the running jar (#105). It reads every offered file, ignores file
 * names and selection order, and links steps by version <em>and</em> content digest: the first step must start at
 * the running jar's identity, every further one at its predecessor's target. Nothing is written.
 *
 * <p>It refuses instead of guessing: an unreadable or invalid file, two different steps from the same jar, a step
 * for the right version but other content, or no step from the running jar at all. Offered versions above the end
 * of the chain stay unapplied and the plan says which link is missing.
 */
public final class PatchChainPlanner {

    /** The chain from {@code running} through the {@code offered} files. */
    public PatchPlan plan(JarIdentity running, List<File> offered) throws JarPatchException {
        if (offered.isEmpty()) {
            throw new JarPatchException("Es wurde kein Update-Paket ausgewählt.");
        }
        List<JarPatch> patches = new ArrayList<JarPatch>();
        List<File> unused = new ArrayList<File>();
        readAll(offered, patches, unused);

        List<JarPatch> steps = new ArrayList<JarPatch>();
        JarIdentity current = running;
        while (true) {
            JarPatch next = next(current, patches, steps.isEmpty());
            if (next == null) {
                break;
            }
            steps.add(next);
            patches.remove(next);
            current = next.target();
        }
        if (steps.isEmpty()) {
            throw new JarPatchException(nothingFits(running, patches));
        }
        List<String> notices = new ArrayList<String>();
        for (JarPatch left : patches) {
            unused.add(left.file());
            if (ReleaseVersions.compare(left.baseVersion(), current.version()) > 0) {
                notices.add("Version " + left.targetVersion() + " (" + left.file().getName()
                        + ") ist nicht erreichbar: es fehlt das Update " + current.version() + " → "
                        + left.baseVersion() + ".");
            }
        }
        return new PatchPlan(running, steps, unused, notices);
    }

    private static void readAll(List<File> offered, List<JarPatch> patches, List<File> unused)
            throws JarPatchException {
        List<String> problems = new ArrayList<String>();
        for (File file : offered) {
            JarPatch patch;
            try {
                patch = JarPatch.read(file);
            } catch (JarPatchException ex) {
                problems.add(ex.getMessage());
                continue;
            }
            JarPatch same = null;
            for (JarPatch known : patches) {
                if (known.sameStep(patch)) {
                    same = known;
                }
            }
            if (same == null) {
                patches.add(patch);
            } else {
                // Two files for the very same step (same versions and content digests): one is enough.
                unused.add(file);
            }
        }
        if (!problems.isEmpty()) {
            throw new JarPatchException("Nicht jedes ausgewählte Update-Paket ist gültig:\n- "
                    + String.join("\n- ", problems));
        }
    }

    /** The one step from {@code current}; refuses ambiguity and steps for this version with other content. */
    private static JarPatch next(JarIdentity current, List<JarPatch> patches, boolean first)
            throws JarPatchException {
        List<JarPatch> fitting = new ArrayList<JarPatch>();
        List<JarPatch> sameVersion = new ArrayList<JarPatch>();
        for (JarPatch patch : patches) {
            if (current.matches(patch.baseVersion(), patch.baseContentSha256())) {
                fitting.add(patch);
            } else if (current.version().equals(patch.baseVersion())) {
                sameVersion.add(patch);
            }
        }
        if (fitting.size() > 1) {
            StringBuilder text = new StringBuilder("Von Version " + current.version()
                    + " aus gibt es mehrere verschiedene Updates; welches gilt, ist nicht eindeutig:");
            for (JarPatch patch : fitting) {
                text.append("\n- ").append(patch).append(" (").append(patch.file().getName()).append(')');
            }
            throw new JarPatchException(text.toString());
        }
        if (fitting.isEmpty() && !sameVersion.isEmpty()) {
            JarPatch other = sameVersion.get(0);
            throw new JarPatchException(first
                    ? "Das Update " + other + " (" + other.file().getName() + ") passt nicht zur gestarteten "
                    + "Jar-Datei: Version " + current.version() + " stimmt, ihr Inhalt aber nicht (Inhaltsprüfsumme "
                    + JarIdentity.shortSha(current.contentSha256()) + " statt "
                    + JarIdentity.shortSha(other.baseContentSha256()) + ")."
                    : "Die Kette bricht bei Version " + current.version() + " ab: das Update " + other + " ("
                    + other.file().getName() + ") erwartet einen anderen Inhalt, als der vorige Schritt erzeugt.");
        }
        return fitting.isEmpty() ? null : fitting.get(0);
    }

    private static String nothingFits(JarIdentity running, List<JarPatch> patches) {
        JarPatch lowest = null;
        for (JarPatch patch : patches) {
            if (ReleaseVersions.compare(patch.baseVersion(), running.version()) > 0
                    && (lowest == null || ReleaseVersions.compare(patch.baseVersion(), lowest.baseVersion()) < 0)) {
                lowest = patch;
            }
        }
        if (lowest == null) {
            return "Keines der Update-Pakete führt über die gestartete Version " + running.version()
                    + " hinaus; sie sind für ältere Versionen.";
        }
        return "Kein Update-Paket beginnt bei der gestarteten Version " + running.version()
                + ". Das erste angebotene beginnt bei " + lowest.baseVersion() + "; es fehlt das Update "
                + running.version() + " → " + lowest.baseVersion() + ".";
    }
}
