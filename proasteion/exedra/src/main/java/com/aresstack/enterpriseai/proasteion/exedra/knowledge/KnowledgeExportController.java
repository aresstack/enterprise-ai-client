package com.aresstack.enterpriseai.proasteion.exedra.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.ExportDocumentNode;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ExportIncompleteException;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeExportUseCase;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.proasteion.exedra.document.ExportFormat;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.NavigationNode;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs a page export (#115) from the shell: asks for the target file first (title as file name, replacing an existing
 * file only after confirmation), then loads the export tree with {@link KnowledgeExportUseCase} and writes it with the
 * writer of the chosen {@link ExportFormat}, both on the work executor, while a progress window counts the pages and
 * offers to cancel. The file is written next to the target and moved into place at the end, so a failed or cancelled
 * export leaves nothing half written; a page that could not be loaded fails the whole export with its name.
 */
public final class KnowledgeExportController {

    private static final Logger LOG = Logger.getLogger(KnowledgeExportController.class.getName());
    static final String DIALOG_TITLE = "Exportieren";
    static final String INCOMPLETE = "Der Export wurde nicht gespeichert, weil nicht alle Seiten geladen werden "
            + "konnten:";
    static final String WRITE_FAILED = "Der Export konnte nicht gespeichert werden: ";
    /** How many missing pages the message lists before it only counts the rest. */
    private static final int LISTED_FAILURES = 10;

    /** What the controller shows; a Swing implementation by default, fakes in tests. Calls on the EDT. */
    public interface Dialogs {
        /** Return the confirmed target file, or {@code null} if the user cancelled. */
        File chooseTarget(Component parent, String suggestedName, ExportFormat format);

        /** Show that the export of {@code title} runs; the handle is updated and closed on the EDT. */
        ProgressView showProgress(Component parent, String title, Runnable cancel);

        void showProblem(Component parent, String message);
    }

    /** A running export as the user sees it. */
    public interface ProgressView {
        void update(int loaded, int found, String page);

        void close();
    }

    /** Builds the export tree on the work thread. */
    interface TreeLoader {
        ExportDocumentNode load(KnowledgeExportUseCase.Progress progress, AtomicBoolean cancelled)
                throws ExportIncompleteException;
    }

    private final KnowledgeExportUseCase exports;
    private final Executor uiExecutor;
    private final Executor workExecutor;
    private final Dialogs dialogs;

    public KnowledgeExportController(KnowledgeExportUseCase exports, Executor uiExecutor, Executor workExecutor) {
        this(exports, uiExecutor, workExecutor, new SwingDialogs());
    }

    KnowledgeExportController(KnowledgeExportUseCase exports, Executor uiExecutor, Executor workExecutor,
                              Dialogs dialogs) {
        if (exports == null || uiExecutor == null || workExecutor == null || dialogs == null) {
            throw new IllegalArgumentException("all arguments are required");
        }
        this.exports = exports;
        this.uiExecutor = uiExecutor;
        this.workExecutor = workExecutor;
        this.dialogs = dialogs;
    }

    /** Export {@code node} with all pages below it; on the EDT. */
    public void exportSubtree(Component parent, final KnowledgeSourcePort source, final NavigationNode node,
                              ExportFormat format) {
        run(parent, node.title(), format, (progress, cancelled) -> exports.subtree(source, node, progress,
                cancelled::get));
    }

    /** Export the single page {@code page}, never its children; on the EDT. */
    public void exportPage(Component parent, final KnowledgeSourcePort source, final KnowledgeResourceId page,
                           final String title, ExportFormat format) {
        run(parent, title, format, (progress, cancelled) -> exports.page(source, page, title, progress,
                cancelled::get));
    }

    void run(final Component parent, String title, final ExportFormat format, final TreeLoader loader) {
        final File target = dialogs.chooseTarget(parent, ExportFileName.of(title), format);
        if (target == null) {
            return;
        }
        final AtomicBoolean cancelled = new AtomicBoolean();
        final ProgressView view = dialogs.showProgress(parent, title, () -> cancelled.set(true));
        try {
            workExecutor.execute(() -> work(parent, target, format, loader, cancelled, view));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Export: Hintergrundarbeit abgelehnt", e);
            view.close();
            dialogs.showProblem(parent, WRITE_FAILED + "gerade nicht möglich.");
        }
    }

    private void work(final Component parent, File target, ExportFormat format, TreeLoader loader,
                      AtomicBoolean cancelled, final ProgressView view) {
        String problem = null;
        try {
            ExportDocumentNode tree = loader.load((loaded, found, page) -> uiExecutor.execute(
                    () -> view.update(loaded, found, page)), cancelled);
            if (cancelled.get()) {
                throw new CancellationException("export cancelled");
            }
            save(tree, target, format);
        } catch (CancellationException e) {
            LOG.fine("Export abgebrochen"); // the user asked for it: nothing to report
        } catch (ExportIncompleteException e) {
            problem = incompleteMessage(e.failures());
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Export nach " + target + " fehlgeschlagen", e);
            problem = WRITE_FAILED + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        final String shown = problem;
        uiExecutor.execute(() -> {
            view.close();
            if (shown != null) {
                dialogs.showProblem(parent, shown);
            }
        });
    }

    /** Write next to the target, then move it into place: nothing half written stays behind. */
    static void save(ExportDocumentNode tree, File target, ExportFormat format) throws IOException {
        Path destination = target.toPath().toAbsolutePath();
        Path directory = destination.getParent();
        Path temporary = Files.createTempFile(directory, ".export-", ".part");
        try {
            OutputStream out = Files.newOutputStream(temporary);
            try {
                format.writer().write(tree, out);
            } finally {
                out.close();
            }
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String incompleteMessage(List<String> failures) {
        StringBuilder text = new StringBuilder(INCOMPLETE);
        for (int i = 0; i < failures.size() && i < LISTED_FAILURES; i++) {
            text.append("\n– ").append(failures.get(i));
        }
        if (failures.size() > LISTED_FAILURES) {
            text.append("\n… und ").append(failures.size() - LISTED_FAILURES).append(" weitere");
        }
        return text.toString();
    }

    // ------------------------------------------------------------------ Swing

    /** Save dialog, progress window and messages as in the chat export. */
    static final class SwingDialogs implements Dialogs {
        @Override
        public File chooseTarget(Component parent, String suggestedName, ExportFormat format) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(DIALOG_TITLE);
            chooser.setAcceptAllFileFilterUsed(false);
            chooser.setFileFilter(new FileNameExtensionFilter(format.fileLabel(), format.extension()));
            chooser.setSelectedFile(new File(suggestedName + "." + format.extension()));
            if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
                return null;
            }
            File target = ExportFileName.withExtension(chooser.getSelectedFile(), format);
            if (target.exists() && JOptionPane.showConfirmDialog(parent, target.getName()
                            + " gibt es schon. Ersetzen?", DIALOG_TITLE, JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
                return null;
            }
            return target;
        }

        @Override
        public ProgressView showProgress(Component parent, String title, final Runnable cancel) {
            Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
            final JDialog dialog = new JDialog(owner, DIALOG_TITLE);
            final JLabel heading = new JLabel("Exportiere „" + title + "“");
            final JLabel status = new JLabel("Seiten werden geladen …");
            final JProgressBar bar = new JProgressBar();
            bar.setIndeterminate(true);
            final JButton stop = new JButton("Abbrechen");
            stop.addActionListener(event -> {
                stop.setEnabled(false);
                status.setText("Wird abgebrochen …");
                cancel.run();
            });
            JPanel content = new JPanel(new BorderLayout(0, 8));
            content.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
            JPanel labels = new JPanel(new BorderLayout(0, 4));
            labels.add(heading, BorderLayout.NORTH);
            labels.add(status, BorderLayout.SOUTH);
            content.add(labels, BorderLayout.NORTH);
            content.add(bar, BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            buttons.add(stop);
            content.add(buttons, BorderLayout.SOUTH);
            dialog.setContentPane(content);
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            dialog.pack();
            dialog.setLocationRelativeTo(parent);
            dialog.setVisible(true);
            return new ProgressView() {
                @Override
                public void update(int loaded, int found, String page) {
                    if (!stop.isEnabled()) {
                        return;
                    }
                    bar.setIndeterminate(false);
                    bar.setMaximum(Math.max(found, loaded));
                    bar.setValue(loaded);
                    status.setText(loaded + " / " + found + " Seiten geladen …");
                }

                @Override
                public void close() {
                    dialog.dispose();
                }
            };
        }

        @Override
        public void showProblem(Component parent, String message) {
            JOptionPane.showMessageDialog(parent, message, DIALOG_TITLE, JOptionPane.WARNING_MESSAGE);
        }
    }
}
