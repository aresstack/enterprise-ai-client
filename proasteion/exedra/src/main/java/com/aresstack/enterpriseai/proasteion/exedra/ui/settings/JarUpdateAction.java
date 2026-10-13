package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.proasteion.application.update.JarIdentity;
import com.aresstack.enterpriseai.proasteion.application.update.JarPatch;
import com.aresstack.enterpriseai.proasteion.application.update.JarPatchException;
import com.aresstack.enterpriseai.proasteion.application.update.PatchChainExecutor;
import com.aresstack.enterpriseai.proasteion.application.update.PatchChainPlanner;
import com.aresstack.enterpriseai.proasteion.application.update.PatchPlan;
import com.aresstack.enterpriseai.proasteion.application.update.RunningJar;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * "Update einspielen …" in Merlin's crystal cave (#92, chains since #105): choose one or more local patches, let
 * {@link PatchChainPlanner} find the chain from the running fat jar, show versions, steps and the collected release
 * notes, confirm, and let {@link PatchChainExecutor} write {@code enterprise-ai-client-<version>.jar} next to the
 * running jar. Planning and writing run on {@code background}; progress ("Schritt 2 von 4: 0.61.0 → 0.62.0") goes to
 * {@code status}, {@link #cancel()} stops at the next step boundary. The running jar is never touched and no restart
 * happens. Swing only shows and asks; every decision about versions and files is in the planner and executor.
 */
final class JarUpdateAction {

    static final String BUTTON_TEXT = "Update einspielen …";
    static final String TITLE = "Update einspielen";

    /** The dialogs of the flow, replaceable in tests. */
    interface Dialogs {
        /** The chosen patch files; empty when cancelled. */
        List<File> choosePatches(Component parent);

        boolean confirm(Component parent, String message);

        void info(Component parent, String message);

        void error(Component parent, String message);
    }

    private final Component parent;
    private final Supplier<RunningJar> runningJar;
    private final PatchChainPlanner planner;
    private final PatchChainExecutor executor;
    private final Executor background;
    private final Dialogs dialogs;
    private final Consumer<Boolean> busy;
    private final Consumer<String> status;
    private volatile boolean cancelRequested;
    private volatile boolean writing;

    JarUpdateAction(Component parent, Supplier<RunningJar> runningJar, PatchChainPlanner planner,
                    PatchChainExecutor executor, Executor background, Dialogs dialogs, Consumer<Boolean> busy,
                    Consumer<String> status) {
        this.parent = parent;
        this.runningJar = runningJar;
        this.planner = planner;
        this.executor = executor;
        this.background = background;
        this.dialogs = dialogs;
        this.busy = busy;
        this.status = status;
    }

    /** Starts the flow; call on the event dispatch thread. */
    void run() {
        RunningJar base = runningJar.get();
        if (!base.isAvailable()) {
            dialogs.info(parent, base.reason());
            return;
        }
        List<File> patches = dialogs.choosePatches(parent);
        if (patches == null || patches.isEmpty()) {
            return;
        }
        busy.accept(true);
        status.accept("Prüfe " + patches.size() + (patches.size() == 1 ? " Update-Paket …" : " Update-Pakete …"));
        background.execute(() -> {
            try {
                PatchPlan plan = planner.plan(JarIdentity.of(base.file()), patches);
                SwingUtilities.invokeLater(() -> confirm(base, plan));
            } catch (JarPatchException | RuntimeException ex) {
                failed(ex);
            }
        });
    }

    /** True while the chain is being written; then the button cancels. */
    boolean isWriting() {
        return writing;
    }

    /** Asks the running chain to stop at the next step boundary. */
    void cancel() {
        cancelRequested = true;
        status.accept("Wird nach dem laufenden Schritt abgebrochen …");
    }

    private void confirm(RunningJar base, PatchPlan plan) {
        busy.accept(false);
        status.accept("");
        File directory = base.file().getAbsoluteFile().getParentFile();
        File target = plan.targetFile(directory);
        if (!dialogs.confirm(parent, summary(plan, target))) {
            return;
        }
        boolean replace = target.exists();
        if (replace && !dialogs.confirm(parent, "Die Datei " + target.getName() + " gibt es schon. Ersetzen?")) {
            return;
        }
        cancelRequested = false;
        writing = true;
        busy.accept(true);
        background.execute(() -> {
            try {
                File created = executor.execute(plan, base.file(), directory, replace,
                        new PatchChainExecutor.Progress() {
                            @Override
                            public void step(int number, int total, JarPatch patch) {
                                SwingUtilities.invokeLater(() -> status.accept(
                                        stepText(number, total, patch)));
                            }

                            @Override
                            public boolean cancelRequested() {
                                return cancelRequested;
                            }
                        });
                SwingUtilities.invokeLater(() -> {
                    writing = false;
                    busy.accept(false);
                    status.accept("");
                    dialogs.info(parent, "Version " + plan.target().version() + " liegt jetzt unter\n"
                            + created.getAbsolutePath() + "\n\nDie laufende Version " + base.version()
                            + " bleibt unverändert. Neue Version selbst starten.");
                });
            } catch (JarPatchException | RuntimeException ex) {
                failed(ex);
            }
        });
    }

    static String stepText(int number, int total, JarPatch patch) {
        return "Schritt " + number + " von " + total + ": " + patch.baseVersion() + " → " + patch.targetVersion();
    }

    static String summary(PatchPlan plan, File target) {
        StringBuilder text = new StringBuilder("Von: ").append(plan.start().version())
                .append("\nNach: ").append(plan.target().version())
                .append("\nSchritte: ").append(plan.steps().size());
        for (JarPatch step : plan.steps()) {
            text.append("\n  ").append(step.baseVersion()).append(" → ").append(step.targetVersion());
        }
        for (String notice : plan.notices()) {
            text.append("\n\nHinweis: ").append(notice);
        }
        if (!plan.unused().isEmpty()) {
            text.append("\n\nNicht benötigt: ");
            for (int i = 0; i < plan.unused().size(); i++) {
                text.append(i == 0 ? "" : ", ").append(plan.unused().get(i).getName());
            }
        }
        text.append("\n\nÄnderungen:\n").append(plan.releaseNotes());
        text.append("\n\nNeue Datei: ").append(target.getAbsolutePath())
                .append("\n\nUpdate-Kette jetzt anwenden?");
        return text.toString();
    }

    private void failed(Exception ex) {
        SwingUtilities.invokeLater(() -> {
            writing = false;
            busy.accept(false);
            status.accept("");
            dialogs.error(parent, "Update nicht eingespielt. Die laufende Version bleibt unverändert.\n\n"
                    + ex.getMessage());
        });
    }

    /** Swing dialogs; the summary sits in a scrollable text area so long release notes stay readable. */
    static final class SwingDialogs implements Dialogs {
        private static final int TEXT_COLUMNS = 60;
        private static final int TEXT_ROWS = 16;

        @Override
        public List<File> choosePatches(Component parent) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(TITLE + " (mehrere Pakete möglich)");
            chooser.setMultiSelectionEnabled(true);
            chooser.setFileFilter(new FileNameExtensionFilter("Update-Paket (*.zip)", "zip"));
            if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
                return Collections.emptyList();
            }
            File[] chosen = chooser.getSelectedFiles();
            if (chosen.length > 0) {
                return Arrays.asList(chosen);
            }
            File single = chooser.getSelectedFile();
            return single == null ? Collections.<File>emptyList() : Collections.singletonList(single);
        }

        @Override
        public boolean confirm(Component parent, String message) {
            return JOptionPane.showConfirmDialog(parent, text(message), TITLE, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;
        }

        @Override
        public void info(Component parent, String message) {
            JOptionPane.showMessageDialog(parent, text(message), TITLE, JOptionPane.INFORMATION_MESSAGE);
        }

        @Override
        public void error(Component parent, String message) {
            JOptionPane.showMessageDialog(parent, text(message), TITLE, JOptionPane.ERROR_MESSAGE);
        }

        private static Component text(String message) {
            JTextArea area = new JTextArea(message);
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setOpaque(false);
            area.setColumns(TEXT_COLUMNS);
            int rows = Math.min(TEXT_ROWS, Math.max(2, message.split("\n", -1).length + 1));
            area.setRows(rows);
            JScrollPane scroll = new JScrollPane(area);
            scroll.setBorder(null);
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.setPreferredSize(new Dimension(area.getPreferredSize().width + 20, area.getPreferredSize().height + 8));
            return scroll;
        }
    }
}
