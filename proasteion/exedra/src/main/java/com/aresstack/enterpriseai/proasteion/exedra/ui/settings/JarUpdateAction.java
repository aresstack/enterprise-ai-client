package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.proasteion.application.update.JarPatchException;
import com.aresstack.enterpriseai.proasteion.application.update.JarPatcher;
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
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * "Update einspielen …" in Merlin's crystal cave: choose a local patch, check it against the running fat jar, show
 * target version and changes, confirm, and write {@code enterprise-ai-client-<version>.jar} next to the running jar.
 * Checking and writing run on {@code background}; the running jar is never touched and no restart happens.
 */
final class JarUpdateAction {

    static final String BUTTON_TEXT = "Update einspielen …";
    static final String TITLE = "Update einspielen";

    /** The dialogs of the flow, replaceable in tests. */
    interface Dialogs {
        File choosePatch(Component parent);

        boolean confirm(Component parent, String message);

        void info(Component parent, String message);

        void error(Component parent, String message);
    }

    private final Component parent;
    private final Supplier<RunningJar> runningJar;
    private final JarPatcher patcher;
    private final Executor background;
    private final Dialogs dialogs;
    private final Consumer<Boolean> busy;

    JarUpdateAction(Component parent, Supplier<RunningJar> runningJar, JarPatcher patcher, Executor background,
                    Dialogs dialogs, Consumer<Boolean> busy) {
        this.parent = parent;
        this.runningJar = runningJar;
        this.patcher = patcher;
        this.background = background;
        this.dialogs = dialogs;
        this.busy = busy;
    }

    /** Starts the flow; call on the event dispatch thread. */
    void run() {
        RunningJar base = runningJar.get();
        if (!base.isAvailable()) {
            dialogs.info(parent, base.reason());
            return;
        }
        File patch = dialogs.choosePatch(parent);
        if (patch == null) {
            return;
        }
        busy.accept(true);
        background.execute(() -> {
            try {
                JarPatcher.Check check = patcher.check(base, patch);
                SwingUtilities.invokeLater(() -> confirm(check));
            } catch (JarPatchException | RuntimeException ex) {
                failed(ex);
            }
        });
    }

    private void confirm(JarPatcher.Check check) {
        busy.accept(false);
        if (!dialogs.confirm(parent, summary(check))) {
            return;
        }
        boolean replace = check.targetExists();
        if (replace && !dialogs.confirm(parent, "Die Datei " + check.target().getName()
                + " gibt es schon. Ersetzen?")) {
            return;
        }
        busy.accept(true);
        background.execute(() -> {
            try {
                File created = patcher.apply(check, replace);
                SwingUtilities.invokeLater(() -> {
                    busy.accept(false);
                    dialogs.info(parent, "Version " + check.patch().targetVersion() + " liegt jetzt unter\n"
                            + created.getAbsolutePath() + "\n\nDie laufende Version " + check.base().version()
                            + " bleibt unverändert. Starte die neue Datei beim nächsten Mal selbst.");
                });
            } catch (JarPatchException | RuntimeException ex) {
                failed(ex);
            }
        });
    }

    static String summary(JarPatcher.Check check) {
        StringBuilder text = new StringBuilder("Update ").append(check.patch().baseVersion()).append(" → ")
                .append(check.patch().targetVersion()).append("\n\n");
        String changes = check.patch().changes();
        text.append("Änderungen:\n").append(changes.isEmpty() ? "(keine Angaben im Update-Paket)" : changes);
        text.append("\n\n").append(check.patch().changedEntries().size()).append(" Dateien neu oder geändert, ")
                .append(check.patch().removedEntries().size()).append(" entfernt.\n");
        text.append("Neue Datei: ").append(check.target().getAbsolutePath()).append("\n\nJetzt erzeugen?");
        return text.toString();
    }

    private void failed(Exception ex) {
        SwingUtilities.invokeLater(() -> {
            busy.accept(false);
            dialogs.error(parent, "Update nicht eingespielt. Die laufende Version bleibt unverändert.\n\n"
                    + ex.getMessage());
        });
    }

    /** Swing dialogs; the summary sits in a scrollable text area so long release notes stay readable. */
    static final class SwingDialogs implements Dialogs {
        private static final int TEXT_COLUMNS = 60;
        private static final int TEXT_ROWS = 16;

        @Override
        public File choosePatch(Component parent) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(TITLE);
            chooser.setFileFilter(new FileNameExtensionFilter("Update-Paket (*.zip)", "zip"));
            return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
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
