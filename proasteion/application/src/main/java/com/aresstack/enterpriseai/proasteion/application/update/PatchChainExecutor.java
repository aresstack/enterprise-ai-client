package com.aresstack.enterpriseai.proasteion.application.update;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Applies a {@link PatchPlan} (#105). Every step writes into a hidden staging directory next to the target, in the
 * same file system, and {@link JarPatcher} checks its content digest and manifest before the next step reads it.
 * Only after the last step is the result renamed to {@code enterprise-ai-client-<target>.jar}. The running jar is
 * only read; staging is removed after success, failure or cancellation, so no partial or intermediate jar is left.
 */
public final class PatchChainExecutor {

    /** Progress of a run and the cancel request, checked between steps. */
    public interface Progress {
        /** Step {@code number} of {@code total} starts. */
        void step(int number, int total, JarPatch patch);

        /** True when the user asked to stop; the run ends at the next step boundary. */
        boolean cancelRequested();
    }

    private static final String STAGING_PREFIX = ".enterprise-ai-client-update-";

    private final JarPatcher patcher;

    public PatchChainExecutor(JarPatcher patcher) {
        this.patcher = patcher;
    }

    /**
     * Builds the plan's target from {@code runningJar} into {@code directory}. An existing target file is replaced
     * only with {@code replaceExisting}; that decision is made before anything is written.
     *
     * @return the published jar
     */
    public File execute(PatchPlan plan, File runningJar, File directory, boolean replaceExisting, Progress progress)
            throws JarPatchException {
        File target = plan.targetFile(directory);
        if (sameFile(target, runningJar)) {
            throw new JarPatchException("Die Zieldatei wäre die laufende Jar-Datei: " + target.getName());
        }
        if (target.exists() && !replaceExisting) {
            throw new JarPatchException("Die Datei " + target.getName() + " gibt es schon.");
        }
        JarIdentity actual = JarIdentity.of(runningJar);
        if (!actual.matches(plan.start().version(), plan.start().contentSha256())) {
            throw new JarPatchException("Die gestartete Jar-Datei ist nicht mehr die, für die das Update geplant "
                    + "wurde (" + actual + " statt " + plan.start() + ").");
        }
        File staging = null;
        try {
            staging = Files.createTempDirectory(directory.toPath(), STAGING_PREFIX).toFile();
            File input = runningJar;
            List<JarPatch> steps = plan.steps();
            for (int i = 0; i < steps.size(); i++) {
                if (progress.cancelRequested()) {
                    throw new JarPatchException("Abgebrochen vor Schritt " + (i + 1) + " von " + steps.size()
                            + "; es wurde keine neue Version angelegt.");
                }
                JarPatch step = steps.get(i);
                progress.step(i + 1, steps.size(), step);
                File output = new File(staging, "step-" + (i + 1) + "-" + step.targetVersion() + ".jar");
                patcher.apply(input, step, output);
                if (input != runningJar && !input.delete()) {
                    input.deleteOnExit();
                }
                input = output;
            }
            if (progress.cancelRequested()) {
                throw new JarPatchException("Abgebrochen vor dem Ablegen; es wurde keine neue Version angelegt.");
            }
            publish(input, target, replaceExisting);
            return target;
        } catch (IOException ex) {
            throw new JarPatchException("Das Update konnte nicht geschrieben werden: " + ex.getMessage(), ex);
        } finally {
            deleteTree(staging);
        }
    }

    private static void publish(File staged, File target, boolean replaceExisting) throws IOException {
        if (!replaceExisting && target.exists()) {
            throw new FileAlreadyExistsException(target.getName());
        }
        StandardCopyOption[] atomic = replaceExisting
                ? new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING}
                : new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE};
        try {
            Files.move(staged.toPath(), target.toPath(), atomic);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(staged.toPath(), target.toPath(), replaceExisting
                    ? new StandardCopyOption[]{StandardCopyOption.REPLACE_EXISTING} : new StandardCopyOption[0]);
        }
    }

    private static void deleteTree(File directory) {
        if (directory == null || !directory.exists()) {
            return;
        }
        File[] children = directory.listFiles();
        if (children != null) {
            for (File child : children) {
                if (!child.delete()) {
                    child.deleteOnExit();
                }
            }
        }
        if (!directory.delete()) {
            directory.deleteOnExit();
        }
    }

    private static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException ex) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }
}
